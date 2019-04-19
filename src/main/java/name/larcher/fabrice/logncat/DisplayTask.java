/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat;

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

public class DisplayTask implements Runnable {

	private final static ZoneId ZONE_ID = ZoneId.systemDefault();

	public DisplayTask(
			long statsDisplayPeriodMillis,
			Statistic overallStats,
			StatisticTimeBucketsFactory.StatisticTimeBuckets buckets,
			LatestConsumer<AccessLogLine> latestLogLineConsumer) {

		this.overallStats = overallStats;
		this.buckets = buckets;
		this.latestLogLineConsumer = latestLogLineConsumer;
		this.statsDisplayPeriodMillis = statsDisplayPeriodMillis;
	}

	private final Statistic overallStats;
	private final StatisticTimeBucketsFactory.StatisticTimeBuckets buckets;
	private final LatestConsumer<AccessLogLine> latestLogLineConsumer;
	private final long statsDisplayPeriodMillis;

	// Display attributes (can change from one display to another)
	private List<Duration> latestStatsDurations = Collections.emptyList();
	private int topSectionCount = 1;

	public void setLatestStatsDurations(List<Duration> latestStatsDurations) {
		this.latestStatsDurations = latestStatsDurations;
	}

	public void setTopSectionCount(int topSectionCount) {
		this.topSectionCount = topSectionCount;
	}

	// Mutable attributes
	private Instant printInstant = null;

	@Override
	public void run() {
		Thread.currentThread().setName("Display");
		AccessLogLine latestLogLine = latestLogLineConsumer.getLatest();
		if (latestLogLine != null) { // Can be null without traffic

			if (printInstant == null) {
				printInstant = latestLogLine.getInstant();
			}
			else {
				long latestRecordMillis = latestLogLine.getTimeInMillis();
				long afterDelayMillis = printInstant.toEpochMilli() + statsDisplayPeriodMillis;
				printInstant = Instant.ofEpochMilli(Math.max(latestRecordMillis, afterDelayMillis));
			}

			String date = DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.ofInstant(printInstant, ZONE_ID));

			Printer.printStats(overallStats, date, null, topSectionCount);

			List<? extends Statistic> latestStatisticsList = buckets.reduceLatest(printInstant.toEpochMilli(), latestStatsDurations);
			for (int i = 0; i < latestStatsDurations.size(); i++) {
				Duration duration = latestStatsDurations.get(i);
				Statistic stats = latestStatisticsList.get(i);
				Printer.printStats(stats, date, duration, topSectionCount);
			}
		}
		else {
			Printer.noLine();
		}
	}
}
