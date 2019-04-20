/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */
package name.larcher.fabrice.logncat;

import name.larcher.fabrice.logncat.alert.AlertConfig;
import name.larcher.fabrice.logncat.alert.AlertState;
import name.larcher.fabrice.logncat.config.Argument;
import name.larcher.fabrice.logncat.config.Configuration;
import name.larcher.fabrice.logncat.config.DurationConverter;
import name.larcher.fabrice.logncat.display.DisplayTask;
import name.larcher.fabrice.logncat.display.Printer;
import name.larcher.fabrice.logncat.read.AccessLogLine;
import name.larcher.fabrice.logncat.read.AccessLogParser;
import name.larcher.fabrice.logncat.read.AccessLogReadTask;
import name.larcher.fabrice.logncat.stat.*;

import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

/**
 * Program entry point.
 */
public class Main {

	public static void main(String[] args) {

		//--- Asking for help?
		List<String> arguments = Arrays.asList(args);
		if (arguments.contains("-h")) {
			Printer.printHelp();
			System.exit(0);
			return;
		}

		//--- Configuration read & checks
		Configuration configuration;
		try {
			configuration = new Configuration(arguments);
		} catch (IllegalArgumentException e) {
			badConfiguration(e.getMessage());
			System.exit(1);
			return;
		}

		ZoneId timeZone = ZoneId.of(configuration.getArgument(Argument.TIME_ZONE));
		Printer printer = new Printer(timeZone);

		Duration mainIdle = DurationConverter.fromString(configuration.getArgument(Argument.MINIMUM_DURATION));
		Duration latestStatsDuration = checkDuration(configuration, Argument.STATISTICS_LATEST_DURATION, mainIdle);
		Duration displayRefreshDuration = checkDuration(configuration, Argument.DISPLAY_PERIOD_DURATION, mainIdle);
		Duration alertingDuration = checkDuration(configuration, Argument.ALERTING_DURATION, mainIdle);

		//--- Statistics specific configuration

		int topSectionCount = Integer.parseInt(configuration.getArgument(Argument.TOP_SECTION_COUNT));
		int maxSectionCountRatio = Integer.parseInt(configuration.getArgument(Argument.MAX_SECTION_COUNT_RATIO));
		int maxSectionCount = topSectionCount * maxSectionCountRatio;

		//--- Alerting specific configuration

		// We basically read the configuration in order to define alerting rules
		int alertReqPerSecThreshold = Integer.parseInt(
				configuration.getArgument(Argument.ALERT_LOAD_THRESHOLD));
		AlertConfig<Integer> throughputAlertConfig = new AlertConfig<>(
				(stats, duration) -> (int) (stats.overall().requestCount() / duration.getSeconds()),
				throughput -> throughput  >= alertReqPerSecThreshold,
				"High traffic generated an alert - hits = %s",
				printer::printAlert);
		// Note: we could create many alert states with various configuration / thresholds / durations ...

		//--- Initializing the reader and its listeners

		// Chosen comparator for "TOP sections" (currently not configurable, but could be ..)
		Comparator<Statistic.ScopedStatistic> statsComparator = ScopedStatisticComparators.COMPARATOR_BY_REQUEST_COUNT;
		// A listener that supplies the latest entry (needed for the clock definition of watching tasks)
		LatestConsumer<AccessLogLine> latestLogLineConsumer = new LatestConsumer<>();
		// Simple single-step aggregation for overall metrics (no consideration about any "duration" of last entries).
		StatisticAggregator overallStats = new StatisticAggregator(statsComparator, maxSectionCount);
		// More complex 2-step aggregation for getting metrics in some "duration of last entries"
		StatisticTimeBucketsFactory.StatisticTimeBuckets buckets = StatisticTimeBucketsFactory.create(statsComparator, mainIdle, maxSectionCount);
		// The reading runnable task
		AccessLogReadTask reader = new AccessLogReadTask(
				Arrays.asList(overallStats, buckets, latestLogLineConsumer), // Listeners
				new AccessLogParser(configuration.getArgument(Argument.DATE_TIME_FORMAT)), // Parser
				Paths.get(configuration.getArgument(Argument.ACCESS_LOG_FILE_LOCATION)), // File location
				DurationConverter.fromString(configuration.getArgument(Argument.READ_IDLE_DURATION)).toMillis());

		//--- Initializing watching task

		// The program's clock that adapts its time according to the input (where each entry is bound to the time)
		Clock clock = new ReaderClock(timeZone, displayRefreshDuration, latestLogLineConsumer);
		// Listener for all statistics to be displayed
		StatisticContext.StatisticListener statsListener = (ctx, date, stats) ->
				printer.printStats(stats, date, ctx.getDuration(), ctx.getTopSectionCount());
		// The watcher task that display the information
		DisplayTask displayTask = new DisplayTask(overallStats, buckets, printer::formatInstant, clock);
		displayTask.setOverallStats(new StatisticContext(null, topSectionCount, statsComparator, statsListener));
		displayTask.setLatestStats(Collections.singletonList(new StatisticContext(latestStatsDuration, topSectionCount, statsComparator, statsListener)));
		displayTask.setAlertStates(Collections.singletonList(new AlertState<>(throughputAlertConfig, alertingDuration)));

		//--- Starting the engine...
		Printer.printBeforeRun(displayRefreshDuration);
		ScheduledExecutorService executorService = createExecutorService();
		try {
			// Stats/alerts printing done on a regular basis
			long displayPeriodMillis = displayRefreshDuration.toMillis();
			long alertingDurationMillis = alertingDuration.toMillis();
			executorService.scheduleAtFixedRate(displayTask,
					Math.min(displayPeriodMillis, alertingDurationMillis),
					displayPeriodMillis, TimeUnit.MILLISECONDS);
			// Reader (always running until the end of the program)
			executorService.submit(reader).get(); // Does not return until any interrupt request
		}
		catch (ExecutionException e) {
			// Is thrown from the 'reader' task
			e.printStackTrace(System.err); // TODO: use some logging
			awaitTermination(executorService);
		}
		catch (InterruptedException e) {
			reader.requestStop();
			Thread.currentThread().interrupt();
			awaitTermination(executorService);
		}
		catch (Throwable t) {
			reader.requestStop();
			t.printStackTrace(System.err); // TODO: use some logging
			awaitTermination(executorService);
		}
	}

	private static Duration checkDuration(Configuration configuration, Argument argument, Duration minimumDuration) {
		Duration configuredDuration = DurationConverter.fromString(configuration.getArgument(argument));
		if (configuredDuration.compareTo(minimumDuration) < 0) {
			badConfiguration("The duration " + argument.name() +
				" having a value " + DurationConverter.toString(configuredDuration)
				+ " must be greater than the main loop idle duration of " + DurationConverter.toString(minimumDuration));
			System.exit(1);
			return null; // Never executed
		}
		return configuredDuration;
	}

	private static void badConfiguration(String reason) {
		// TODO: use some logging
		System.err.println("Bad configuration: " + reason);
		System.exit(1);
	}

	private static ScheduledExecutorService createExecutorService() {
		Thread.currentThread().setName("main");
		Thread.UncaughtExceptionHandler ueh = (Thread t, Throwable e) -> {
				// TODO: use some logging
				System.err.println("Error from thread " + t.getName() + ": " + e.getMessage());
				e.printStackTrace(System.err);
			};
		return Executors.newScheduledThreadPool(2,
			runnable -> {
				Thread t = new Thread(runnable);
				t.setUncaughtExceptionHandler(ueh);
				return t;
			});
	}

	private static void awaitTermination(ExecutorService executorService) {
		if (!executorService.isTerminated()) {
			try {
				executorService.shutdown();
				executorService.awaitTermination(3, TimeUnit.SECONDS);
			} catch (@SuppressWarnings("unsused") InterruptedException e) {
				executorService.shutdownNow();
			}
		}
	}

}