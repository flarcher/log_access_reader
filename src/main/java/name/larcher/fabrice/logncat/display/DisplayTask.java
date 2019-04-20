/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.display;

import name.larcher.fabrice.logncat.stat.Statistic;
import name.larcher.fabrice.logncat.stat.StatisticTimeBucketsFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A task that display the latest information about access log lines.
 */
public class DisplayTask implements Runnable {

	public DisplayTask(
			Statistic overallStats,
			StatisticTimeBucketsFactory.StatisticTimeBuckets statsBuckets,
			Printer printer,
			Clock clock) {

		this.overallStats = Objects.requireNonNull(overallStats);
		this.latestStatsBuckets = Objects.requireNonNull(statsBuckets);
		this.clock = Objects.requireNonNull(clock);
		this.printer = Objects.requireNonNull(printer);
	}

	private final Statistic overallStats;
	private final StatisticTimeBucketsFactory.StatisticTimeBuckets latestStatsBuckets;
	private final Clock clock;
	private final Printer printer;

	//--- Display attributes (can change from one display to another)
	private List<Duration> latestStatsDurations = Collections.emptyList();
	private int topSectionCount = 1;

	//--- Setters (not thread safe!)

	public void setLatestStatsDurations(List<Duration> latestStatsDurations) {
		this.latestStatsDurations = latestStatsDurations;
	}

	public void setTopSectionCount(int topSectionCount) {
		this.topSectionCount = topSectionCount;
	}

	@Override
	public void run() {
		Thread.currentThread().setName("Display");
		Instant printInstant = clock.instant();
		if (printInstant == null) {  // Can be null without traffic
			Printer.noLine(); // Waiting for input
		}
		else {
			String date = printer.formatInstant(printInstant);
			printer.printStats(overallStats, date, null, topSectionCount);

			List<? extends Statistic> latestStatisticsList = latestStatsBuckets.reduceLatest(printInstant.toEpochMilli(), latestStatsDurations);
			for (int i = 0; i < latestStatsDurations.size(); i++) {
				Duration duration = latestStatsDurations.get(i);
				Statistic stats = latestStatisticsList.get(i);
				printer.printStats(stats, date, duration, topSectionCount);
			}
		}
	}
}
