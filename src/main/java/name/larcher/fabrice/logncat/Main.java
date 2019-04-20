/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */
package name.larcher.fabrice.logncat;

import name.larcher.fabrice.logncat.alert.AlertCheckingTask;
import name.larcher.fabrice.logncat.alert.AlertConfig;
import name.larcher.fabrice.logncat.config.Argument;
import name.larcher.fabrice.logncat.config.Configuration;
import name.larcher.fabrice.logncat.config.DurationConverter;
import name.larcher.fabrice.logncat.display.DisplayTask;
import name.larcher.fabrice.logncat.display.Printer;
import name.larcher.fabrice.logncat.read.AccessLogLine;
import name.larcher.fabrice.logncat.read.AccessLogParser;
import name.larcher.fabrice.logncat.read.AccessLogReadTask;
import name.larcher.fabrice.logncat.stat.ScopedStatisticComparators;
import name.larcher.fabrice.logncat.stat.Statistic;
import name.larcher.fabrice.logncat.stat.StatisticAggregator;
import name.larcher.fabrice.logncat.stat.StatisticTimeBucketsFactory;

import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.*;
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

		// Chosen comparator for "TOP sections"
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

		//--- Initializing watching tasks

		Clock clockForStats = new ReaderClock(timeZone, displayRefreshDuration, latestLogLineConsumer);
		DisplayTask displayTask = new DisplayTask(overallStats, buckets, printer, clockForStats);
		displayTask.setTopSectionCount(topSectionCount);
		displayTask.setLatestStatsDurations(Collections.singletonList(latestStatsDuration));

		Clock clockForAlerts = new ReaderClock(timeZone, displayRefreshDuration, latestLogLineConsumer);
		// > This map can contain many more alert configurations and related durations ...
		Map<AlertConfig<?>, List<Duration>> alerts = Collections.singletonMap(
				throughputAlertConfig, Collections.singletonList(alertingDuration));
		AlertCheckingTask alertCheckingTask = new AlertCheckingTask(buckets, alerts, clockForAlerts);

		//--- Starting the engine...
		Printer.printBeforeRun(displayRefreshDuration);
		ScheduledExecutorService executorService = createExecutorService(2);
		try {
			// Stats printing
			long statsDisplayPeriodMillis = displayRefreshDuration.toMillis();
			/*executorService.scheduleAtFixedRate(displayTask,
					statsDisplayPeriodMillis,
					statsDisplayPeriodMillis, TimeUnit.MILLISECONDS);*/
			// Alerting
			long alertingDurationMillis = alertingDuration.toMillis();
			executorService.scheduleAtFixedRate(alertCheckingTask,
					Math.min(statsDisplayPeriodMillis, alertingDurationMillis),
					statsDisplayPeriodMillis, TimeUnit.MILLISECONDS);
			// Reader
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

	private static ScheduledExecutorService createExecutorService(int taskCount) {
		Thread.currentThread().setName("main");
		Thread.UncaughtExceptionHandler ueh = (Thread t, Throwable e) -> {
				// TODO: use some logging
				System.err.println("Error from thread " + t.getName() + ": " + e.getMessage());
				e.printStackTrace(System.err);
			};
		return Executors.newScheduledThreadPool(taskCount,
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