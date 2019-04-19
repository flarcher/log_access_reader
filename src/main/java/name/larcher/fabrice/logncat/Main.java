/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */
package name.larcher.fabrice.logncat;

import name.larcher.fabrice.logncat.config.Argument;
import name.larcher.fabrice.logncat.config.Configuration;
import name.larcher.fabrice.logncat.config.DurationConverter;
import name.larcher.fabrice.logncat.read.AccessLogLine;
import name.larcher.fabrice.logncat.read.AccessLogParser;
import name.larcher.fabrice.logncat.read.AccessLogReadTask;
import name.larcher.fabrice.logncat.stat.ScopedStatisticComparators;
import name.larcher.fabrice.logncat.stat.Statistic;
import name.larcher.fabrice.logncat.stat.StatisticAggregator;
import name.larcher.fabrice.logncat.stat.StatisticTimeBucketsFactory;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

		Duration mainIdle = DurationConverter.fromString(
				configuration.getArgument(Argument.MAIN_IDLE_DURATION));
		long mainIdleMillis = mainIdle.toMillis();

		Duration latestStatsDuration = DurationConverter.fromString(
				configuration.getArgument(Argument.STATISTICS_LATEST_DURATION));
		long statsRefreshPeriodMillis = latestStatsDuration.toMillis();
		if (statsRefreshPeriodMillis < mainIdleMillis) {
			badConfiguration("The latest statistics duration " + statsRefreshPeriodMillis
					+ " must be greater than the main loop idle duration " + mainIdleMillis);
			System.exit(1);
			return;
		}

		Duration displayRefreshDuration = DurationConverter.fromString(
				configuration.getArgument(Argument.DISPLAY_PERIOD));
		long statsDisplayPeriodMillis = displayRefreshDuration.toMillis();
		if (statsDisplayPeriodMillis < mainIdleMillis) {
			badConfiguration("The display refresh period " + statsDisplayPeriodMillis
					+ " must be greater than the main loop idle duration " + mainIdleMillis);
			System.exit(1);
			return;
		}

		int topSectionCount = Integer.parseInt(configuration.getArgument(Argument.TOP_SECTION_COUNT));
		int maxSectionCountRatio = Integer.parseInt(configuration.getArgument(Argument.MAX_SECTION_COUNT_RATIO));
		int maxSectionCount = topSectionCount * maxSectionCountRatio;

		//--- Initializing of tasks and listeners
		Comparator<Statistic.ScopedStatistic> statsComparator = ScopedStatisticComparators.COMPARATOR_BY_REQUEST_COUNT;

		LatestConsumer<AccessLogLine> latestLogLineConsumer = new LatestConsumer<>();
		StatisticAggregator overallStats = new StatisticAggregator(statsComparator, maxSectionCount);
		StatisticTimeBucketsFactory.StatisticTimeBuckets buckets = StatisticTimeBucketsFactory.create(statsComparator, mainIdle, maxSectionCount);

		AccessLogReadTask reader = new AccessLogReadTask(
				Arrays.asList(overallStats, buckets, latestLogLineConsumer),
				new AccessLogParser(configuration.getArgument(Argument.DATE_TIME_FORMAT)),
				Paths.get(configuration.getArgument(Argument.ACCESS_LOG_FILE_LOCATION)),
				DurationConverter.fromString(configuration.getArgument(Argument.READ_IDLE_DURATION)).toMillis());

		List<Duration> latestStatsDurations = Collections.singletonList(latestStatsDuration);
		ZoneId zoneId = ZoneId.systemDefault();

		// TODO: Implement alerts

		//--- Starting the engine...
		Printer.printBeforeRun(displayRefreshDuration);
		ExecutorService executorService = createExecutorService();
		try {
			executorService.submit(reader);

			long waitedMillis = 0;
			while(true) { // Main loop
				Thread.sleep(mainIdleMillis);
				waitedMillis += mainIdleMillis;
				if (waitedMillis > statsDisplayPeriodMillis) {

					AccessLogLine latestLogLine = latestLogLineConsumer.getLatest();
					if (latestLogLine != null) { // Can be null without traffic

						Instant instant = latestLogLine.getInstant();
						String date = DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.ofInstant(instant, zoneId));

						Printer.printStats(overallStats, date, null, topSectionCount);

						List<? extends Statistic> latestStatisticsList = buckets.reduceLatest(instant.toEpochMilli(), latestStatsDurations);
						for (int i = 0; i < latestStatsDurations.size(); i++) {
							Duration duration = latestStatsDurations.get(i);
							Statistic stats = latestStatisticsList.get(i);
							Printer.printStats(stats, date, duration, topSectionCount);
						}

						// TODO: test
					}
					else {
						Printer.noLine();
					}

					waitedMillis = 0;
				}
			}
		}
		catch (InterruptedException e) {
			reader.requestStop();
			Thread.currentThread().interrupt();
			awaitTermination(executorService);
		}
		catch (Throwable t) {
			// Robustness
			reader.requestStop();
			t.printStackTrace(System.err); // TODO: use some logging
			awaitTermination(executorService);
		}
	}

	private static void badConfiguration(String reason) {
		// TODO: use some logging
		System.err.println("Bad configuration: " + reason);
		System.exit(1);
	}

	private static ExecutorService createExecutorService() {
		Thread.currentThread().setName("main");
		Thread.UncaughtExceptionHandler ueh = (Thread t, Throwable e) -> {
				// TODO: use some logging
				System.err.println("Error from thread " + t.getName() + ": " + e.getMessage());
				e.printStackTrace(System.err);
			};
		return Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r);
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