/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package flarcher.log.access;

import flarcher.log.access.alert.AlertState;
import flarcher.log.access.stat.Statistic;
import flarcher.log.access.stat.StatisticContext;
import flarcher.log.access.stat.StatisticTimeBucketsFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A task that display the latest information about access log lines.
 */
class WatcherTask implements Runnable {

	WatcherTask(
			Statistic overallStats,
			StatisticTimeBucketsFactory.StatisticTimeBuckets statsBuckets,
			Runnable waiting,
			Consumer<Instant> before,
			Consumer<Instant> after,
			Clock clock) {

		this.overallStats = Objects.requireNonNull(overallStats);
		this.timeBuckets = Objects.requireNonNull(statsBuckets);
		this.waiting = Objects.requireNonNull(waiting);
		this.before = Objects.requireNonNull(before);
		this.after = Objects.requireNonNull(after);
		this.clock = Objects.requireNonNull(clock);
	}

	private final Statistic overallStats;
	private final StatisticTimeBucketsFactory.StatisticTimeBuckets timeBuckets;
	private final Runnable waiting;
	private final Consumer<Instant> before;
	private final Consumer<Instant> after;
	private final Clock clock;

	//--- Display attributes (can change from one display to another)

	/** All durations for a retrieval from the {@link #timeBuckets} */
	private List<Duration> allDurations = new ArrayList<>();

	private StatisticContext overallStatsContext;
	private Map<Duration, StatisticContext> latestStatsByDuration = Collections.emptyMap();
	private Map<Duration, List<AlertState<?>>> alertStatesByDuration = Collections.emptyMap();

	private void addDurations(List<Duration> durations) {
		// We use a Set, in order to avoid duplicates
		Set<Duration> set = new HashSet<>(allDurations);
		set.addAll(durations);
		allDurations = new ArrayList<>(set);
		allDurations.sort(Comparator.naturalOrder());
	}

	//--- Setters (not thread safe!)

	void setOverallStats(StatisticContext context) {
		this.overallStatsContext = context;
	}

	void setLatestStats(List<StatisticContext> latestStatsContexts) {
		latestStatsByDuration = latestStatsContexts.stream().collect(
			Collectors.toMap(StatisticContext::getDuration, Function.identity()));
		addDurations(latestStatsContexts.stream()
			.map(StatisticContext::getDuration)
			.filter(Objects::nonNull)
			.collect(Collectors.toList()));
	}

	void setAlertStates(List<AlertState<?>> alertStates) {
		alertStatesByDuration = alertStates.stream().collect(
				Collectors.groupingBy(AlertState::getDuration, Collectors.toList()));
		addDurations(alertStates.stream()
				.map(AlertState::getDuration)
				.filter(Objects::nonNull)
				.collect(Collectors.toList()));
	}

	@Override
	public void run() {
		Thread.currentThread().setName("watcher");
		Instant instant = clock.instant();
		before.accept(instant);
		if (instant == null) {  // Can be null without traffic
			waiting.run(); // Waiting for input
		}
		else {
			long instantMillis = instant.toEpochMilli();

			// Overall stats (does not need "latest" data)
			overallStatsContext.notify(overallStats);

			List<? extends Statistic> latestStatisticsList = timeBuckets.reduceLatest(instantMillis, allDurations);
			for (int i = 0; i < allDurations.size(); i++) {
				Duration duration = allDurations.get(i);
				Statistic stats = latestStatisticsList.get(i);

				// Latest stats
				StatisticContext latestStats = latestStatsByDuration.get(duration);
				if (latestStats != null) {
					latestStats.notify(stats);
				}

				// Alerting
				List<AlertState<?>> alertStates = alertStatesByDuration.get(duration);
				if (alertStates != null) {
					alertStates.forEach(
						// Will potentially notify listeners depending on the alert states
						alertState -> alertState.check(stats, instantMillis));
				}
			}
		}
		after.accept(instant);
	}
}
