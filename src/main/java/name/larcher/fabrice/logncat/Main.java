/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */
package name.larcher.fabrice.logncat;

import name.larcher.fabrice.logncat.config.Argument;
import name.larcher.fabrice.logncat.config.Configuration;
import name.larcher.fabrice.logncat.read.AccessLogParser;
import name.larcher.fabrice.logncat.read.AccessLogReader;
import name.larcher.fabrice.logncat.stat.ScopedStatisticComparators;
import name.larcher.fabrice.logncat.stat.Statistic;
import name.larcher.fabrice.logncat.stat.StatisticAggregator;
import name.larcher.fabrice.logncat.stat.StatisticTimeBuckets;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
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

		// Handling the asking for help
		List<String> arguments = Arrays.asList(args);
		if (arguments.contains("-h") || arguments.contains("--help")) {
			Printer.printHelp();
			System.exit(0);
			return;
		}

		// Read the configuration
		Configuration configuration;
		try {
			configuration = new Configuration(arguments);
		} catch (IllegalArgumentException e) {
			badConfiguration(e.getMessage());
			System.exit(1);
			return;
		}

		// Configuration checks
		long mainIdleMillis = Long.valueOf(configuration.getArgument(Argument.MAIN_IDLE_MILLIS));
		long statsRefreshPeriodMillis = Long.valueOf(configuration.getArgument(Argument.STATISTICS_LATEST_DURATION_MILLIS));
		if (statsRefreshPeriodMillis < mainIdleMillis) {
			badConfiguration("The latest statistics duration " + statsRefreshPeriodMillis
					+ " must be greater than the main loop idle duration " + mainIdleMillis);
			System.exit(1);
			return;
		}

		// Initializing tasks and listeners
		int topSectionCount = Integer.parseInt(configuration.getArgument(Argument.TOP_SECTION_COUNT));
		Comparator<Statistic.ScopedStatistic> statsComparator = ScopedStatisticComparators.COMPARATOR_BY_REQUEST_COUNT;
		StatisticAggregator overallStats = new StatisticAggregator(statsComparator);
		StatisticAggregator recentStats = new StatisticAggregator(statsComparator);

		// TODO: Implement alerts

		AccessLogReader reader = new AccessLogReader(
				Arrays.asList(overallStats, recentStats),
				new AccessLogParser(configuration.getArgument(Argument.DATE_TIME_FORMAT)),
				Paths.get(configuration.getArgument(Argument.ACCESS_LOG_FILE_LOCATION)),
				Long.valueOf(configuration.getArgument(Argument.READ_IDLE_MILLIS)));

		//TimeBuckets<Statistic> bucketsForLatestStats =
		StatisticTimeBuckets bucketsForLatestStats = new StatisticTimeBuckets(
						statsComparator,
				Duration.ofMillis(mainIdleMillis));

		// Starting the engine...
		Thread.UncaughtExceptionHandler ueh = (Thread t, Throwable e) -> {
			// TODO: use some logging
			System.err.println("Error from thread " + t.getName() + ": " + e.getMessage());
			e.printStackTrace(System.err);
		};
		ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r);
				t.setUncaughtExceptionHandler(ueh);
				return t;
			});
		try {
			Printer.printBeforeRun(statsRefreshPeriodMillis);
			executorService.submit(reader);
			long waitedMillis = 0;
			while(true) {
				Thread.sleep(mainIdleMillis);
				/*bucketsForLatestStats.addTimeBucket(); // TODO
				waitedMillis += mainIdleMillis;
				if (waitedMillis > statsDisplayPeriodMillis) {
					Statistic reducedStats = bucketsForLatestStats.reduceTimeBuckets();// TODO
					String date = DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now());
					Printer.printStats(overallStats, date, -1L, topSectionCount);
					Printer.printStats(recentStats, date, statsRefreshPeriodMillis, topSectionCount);
					recentStats.clear();
					waitedMillis = 0;
				}*/
			}
		}
		catch (InterruptedException e) {
			reader.requestStop();
			awaitTermination(executorService, false);
		} catch (Throwable t) {
			// Robustness
			reader.requestStop();
			t.printStackTrace(System.err); // TODO: use some logging
			awaitTermination(executorService, true);
		}
	}

	private static void badConfiguration(String reason) {
		// TODO: use some logging
		System.err.println("Bad configuration: " + reason);
		System.exit(1);
	}

	private static void awaitTermination(ExecutorService executorService, boolean force) {
		if (!executorService.isTerminated()) {
			if (executorService.isShutdown() && force) {
				executorService.shutdownNow();
			} else {
				try {
					executorService.shutdown();
					executorService.awaitTermination(3, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					executorService.shutdownNow();
				}
			}
		}
	}

}