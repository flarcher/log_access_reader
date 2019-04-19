/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.display;

import name.larcher.fabrice.logncat.read.AccessLogLine;
import name.larcher.fabrice.logncat.stat.Statistic;
import name.larcher.fabrice.logncat.stat.StatisticTimeBucketsFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A task that display the latest information about access log lines.
 */
public class DisplayTask implements Runnable {

	private final static ZoneId ZONE_ID = ZoneId.systemDefault();

	public DisplayTask(
			long statsDisplayPeriodMillis,
			Statistic overallStats,
			StatisticTimeBucketsFactory.StatisticTimeBuckets statsBuckets,
			Supplier<AccessLogLine> latestLogLineSupplier) {

		this.overallStats = Objects.requireNonNull(overallStats);
		this.latestStatsBuckets = Objects.requireNonNull(statsBuckets);
		this.latestLogLineSupplier = Objects.requireNonNull(latestLogLineSupplier);
		this.statsDisplayPeriodMillis = statsDisplayPeriodMillis;
	}

	private final Statistic overallStats;
	private final StatisticTimeBucketsFactory.StatisticTimeBuckets latestStatsBuckets;
	private final Supplier<AccessLogLine> latestLogLineSupplier;
	private final long statsDisplayPeriodMillis;

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

	//--- Internal mutable attributes
	private Instant printInstant = null;

	@Override
	public void run() {
		Thread.currentThread().setName("Display");
		AccessLogLine latestLogLine = this.latestLogLineSupplier.get();
		if (latestLogLine == null) {  // Can be null without traffic
			Printer.noLine(); // Waiting for input
		}
		else {

			if (printInstant == null) {
				printInstant = latestLogLine.getInstant();
			}
			else {
				long latestRecordMillis = latestLogLine.getTimeInMillis();
				long plusDelayMillis = printInstant.toEpochMilli() + statsDisplayPeriodMillis;
				printInstant = Instant.ofEpochMilli(Math.max(latestRecordMillis, plusDelayMillis));
			}

			String date = DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.ofInstant(printInstant, ZONE_ID));

			Printer.printStats(overallStats, date, null, topSectionCount);

			List<? extends Statistic> latestStatisticsList = latestStatsBuckets.reduceLatest(printInstant.toEpochMilli(), latestStatsDurations);
			for (int i = 0; i < latestStatsDurations.size(); i++) {
				Duration duration = latestStatsDurations.get(i);
				Statistic stats = latestStatisticsList.get(i);
				Printer.printStats(stats, date, duration, topSectionCount);
			}
		}
	}
}
