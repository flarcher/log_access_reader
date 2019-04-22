/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */
package name.larcher.fabrice.logncat;

import name.larcher.fabrice.logncat.alert.AlertConfig;
import name.larcher.fabrice.logncat.alert.AlertEvent;
import name.larcher.fabrice.logncat.alert.AlertState;
import name.larcher.fabrice.logncat.config.Argument;
import name.larcher.fabrice.logncat.config.Configuration;
import name.larcher.fabrice.logncat.display.AlertPrintListener;
import name.larcher.fabrice.logncat.display.Console;
import name.larcher.fabrice.logncat.display.Printer;
import name.larcher.fabrice.logncat.read.AccessLogLine;
import name.larcher.fabrice.logncat.read.AccessLogParser;
import name.larcher.fabrice.logncat.read.AccessLogReadTask;
import name.larcher.fabrice.logncat.stat.*;

import java.io.PrintStream;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Program entry point.
 */
public class Main {

	public static void main(String[] args) {

		//--- Asking for help?
		List<String> arguments = Arrays.asList(args);
		if (arguments.contains("-h")) {
			printHelp();
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

		Duration mainIdle = DurationConverter.fromString(configuration.getArgument(Argument.MINIMUM_DURATION));
		Duration latestStatsDuration = checkDuration(configuration, Argument.STATISTICS_LATEST_DURATION, mainIdle);
		Duration displayRefreshDuration = checkDuration(configuration, Argument.DISPLAY_PERIOD_DURATION, mainIdle);
		Duration alertingDuration = checkDuration(configuration, Argument.ALERTING_DURATION, mainIdle);

		//--- Statistics specific configuration

		int topSectionCount = Integer.parseInt(configuration.getArgument(Argument.TOP_SECTION_COUNT));
		int maxSectionCountRatio = Integer.parseInt(configuration.getArgument(Argument.MAX_SECTION_COUNT_RATIO));
		int maxSectionCount = topSectionCount * maxSectionCountRatio;

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

		//--- Initializing display
		ZoneId timeZone = ZoneId.of(configuration.getArgument(Argument.TIME_ZONE));
		Printer printer = new Printer(timeZone);
		Console console = new Console(printer);

		//--- Alerting specific configuration

		// We basically read the configuration in order to define alerting rules
		AlertPrintListener alertToStdOut = new AlertPrintListener(printer, System.out);
		int alertReqPerSecThreshold = Integer.parseInt(
				configuration.getArgument(Argument.ALERT_LOAD_THRESHOLD));
		Consumer<AlertEvent<Integer>> alertListener = alert -> {
				console.onAlert(alert);
				alertToStdOut.accept(alert);
			};
		AlertConfig<Integer> throughputAlertConfig = new AlertConfig<>(
				(stats, duration) -> (int) (stats.overall().requestCount() / duration.getSeconds()),
				throughput -> throughput  >= alertReqPerSecThreshold,
				"High traffic",
				alertListener);
		// Note: we could create many alert states with various configuration / thresholds / durations ...

		//--- Initializing watching task

		// The program's clock that adapts its time according to the input (where each entry is bound to the time)
		Clock clock = new ReaderClock(timeZone, displayRefreshDuration, latestLogLineConsumer);
		// Listener for all statistics to be displayed
		BiConsumer<StatisticContext, Statistic> statsListener = console::onStat;
		// The watcher task that display the information
		WatcherTask watcherTask = new WatcherTask(
				overallStats, buckets,
				console::empty, console::beforePrint, console::afterPrint,
				clock);
		watcherTask.setOverallStats(StatisticContext.createOverallContext(
				latestLogLineConsumer::getFirst,
				latestLogLineConsumer::getLatest,
				TimeBound::getTimeInMillis,
				topSectionCount, statsComparator, statsListener));
		watcherTask.setLatestStats(Collections.singletonList(StatisticContext.createTimeRangeContext(
				latestStatsDuration, topSectionCount, statsComparator, statsListener)));
		watcherTask.setAlertStates(Collections.singletonList(new AlertState<>(throughputAlertConfig, alertingDuration)));

		//--- Starting the engine...
		ScheduledExecutorService executorService = createExecutorService(3);
		console.init(displayRefreshDuration, () -> {
			reader.requestStop();
			executorService.shutdown();
		});
		try {
			// Stats/alerts printing done on a regular basis
			executorService.scheduleAtFixedRate(watcherTask, 200, displayRefreshDuration.toMillis(), TimeUnit.MILLISECONDS);
			// User's input read
			executorService.scheduleWithFixedDelay(console::readInput, 200, 100, TimeUnit.MILLISECONDS);
			// Reader (always running until the end of the program)
			executorService.submit(reader).get(); // Does not return until any interrupt request
		}
		catch (ExecutionException e) {
			// Is thrown from the 'reader' task
			handleThrowable(e);
			awaitTermination(executorService);
			console.destroy();
		}
		catch (InterruptedException e) {
			reader.requestStop();
			Thread.currentThread().interrupt();
			awaitTermination(executorService);
			console.destroy();
		}
		catch (Throwable t) {
			reader.requestStop();
			handleThrowable(t);
			awaitTermination(executorService);
			console.destroy();
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
		System.err.println("Bad configuration: " + reason); // TODO: use some logging?
		System.exit(1);
	}

	private static void handleThrowable(Throwable t) {
		t.printStackTrace(System.err); // TODO: use some logging?
	}

	private static ScheduledExecutorService createExecutorService(int taskCount) {
		Thread.currentThread().setName("main");
		Thread.UncaughtExceptionHandler ueh = (Thread t, Throwable e) -> {
				handleThrowable(new RuntimeException(
					"Error from thread " + t.getName() + ": " + e.getMessage(), e));
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

	private static void printHelp() {
		PrintStream printer = System.out;
		printer.println("LOG'n-CAT \uD83D\uDC31");
		printer.println(" Prints statistics and notifies alerts by reading access log files.");
		printer.println();
		printer.println("Possible arguments are:");
		printer.println();
		Arrays.stream(Argument.values())
				.sorted(Comparator.comparing(Argument::getPropertyName))
				.forEach( arg -> {
					String name = arg.name().replaceAll("_", " ").toLowerCase();
					printer.println("-" + arg.getCommandOption() + " <" + name + ">");
					printer.println("  " + arg.getDescription());
					printer.println("  Can be set using the environment variable " + arg.getEnvironmentParameter());
					printer.println("  Can be set as the property " + arg.getPropertyName() + " in the configuration file");
					printer.println("  The default value is «" + arg.getDefaultValue() + "»");
					printer.println();
				});
	}

}