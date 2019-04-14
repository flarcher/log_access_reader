/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */
package name.larcher.fabrice.logncat;

import name.larcher.fabrice.logncat.config.Argument;
import name.larcher.fabrice.logncat.config.Configuration;
import name.larcher.fabrice.logncat.read.AccessLogParser;
import name.larcher.fabrice.logncat.read.AccessLogReader;
import name.larcher.fabrice.logncat.stat.ScopedStatistic;
import name.larcher.fabrice.logncat.stat.ScopedStatisticComparators;
import name.larcher.fabrice.logncat.stat.StatisticAggregator;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

		List<String> arguments = Arrays.asList(args);
		if (arguments.contains("-h") || arguments.contains("--help")) {
			Printer.printHelp();
			System.exit(0);
		}

		// Read the configuration
		Configuration configuration = new Configuration(arguments);

		// Initializing tasks and listeners
		int topSectionCount = Integer.parseInt(configuration.getArgument(Argument.TOP_SECTION_COUNT));
		Comparator<ScopedStatistic> statsComparator = ScopedStatisticComparators.BY_REQUEST_COUNT;
		StatisticAggregator overallStats = new StatisticAggregator(statsComparator);
		StatisticAggregator recentStats = new StatisticAggregator(statsComparator);

		// TODO: Implement alerts

		AccessLogReader reader = new AccessLogReader(
				Arrays.asList(overallStats, recentStats),
				new AccessLogParser(configuration.getArgument(Argument.DATE_TIME_FORMAT)),
				Paths.get(configuration.getArgument(Argument.ACCESS_LOG_FILE_LOCATION)),
				Long.valueOf(configuration.getArgument(Argument.READ_IDLE_MILLIS)));

		// Starting the engine...
		long mainIdleMillis = Long.valueOf(configuration.getArgument(Argument.MAIN_IDLE_MILLIS));
		long statsRefreshPeriodMillis = Long.valueOf(configuration.getArgument(Argument.STATISTICS_REFRESH_PERIOD_MILLIS));
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
				waitedMillis += mainIdleMillis;
				if (waitedMillis > statsRefreshPeriodMillis) {
					String date = DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now());
					Printer.printStats(overallStats, date, -1L, topSectionCount);
					Printer.printStats(recentStats, date, statsRefreshPeriodMillis, topSectionCount);
					recentStats.clear();
					waitedMillis = 0;
				}
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