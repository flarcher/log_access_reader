/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.display;

import name.larcher.fabrice.logncat.alert.AlertState;
import name.larcher.fabrice.logncat.stat.StatisticContext;
import name.larcher.fabrice.logncat.stat.Statistic;
import name.larcher.fabrice.logncat.stat.StatisticTimeBucketsFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A task that display the latest information about access log lines.
 */
public class DisplayTask implements Runnable {

	public DisplayTask(
			Statistic overallStats,
			StatisticTimeBucketsFactory.StatisticTimeBuckets statsBuckets,
			Function<Instant, String> contextFunction,
			Clock clock) {

		this.overallStats = Objects.requireNonNull(overallStats);
		this.contextFunction = Objects.requireNonNull(contextFunction);
		this.timeBuckets = Objects.requireNonNull(statsBuckets);
		this.clock = Objects.requireNonNull(clock);
	}

	private final Statistic overallStats;
	private final StatisticTimeBucketsFactory.StatisticTimeBuckets timeBuckets;
	private final Function<Instant, String> contextFunction;
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

	public void setOverallStats(StatisticContext context) {
		this.overallStatsContext = context;
	}

	public void setLatestStats(List<StatisticContext> latestStatsContexts) {
		latestStatsByDuration = latestStatsContexts.stream().collect(
			Collectors.toMap(StatisticContext::getDuration, Function.identity()));
		addDurations(latestStatsContexts.stream()
			.map(StatisticContext::getDuration)
			.collect(Collectors.toList()));
	}

	public void setAlertStates(List<AlertState<?>> alertStates) {
		alertStatesByDuration = alertStates.stream().collect(
				Collectors.groupingBy(AlertState::getDuration, Collectors.toList()));
		addDurations(alertStates.stream()
				.map(AlertState::getDuration)
				.collect(Collectors.toList()));
	}

	@Override
	public void run() {
		Instant instant = clock.instant();
		if (instant == null) {  // Can be null without traffic
			Printer.noLine(); // Waiting for input
		}
		else {
			long instantMillis = instant.toEpochMilli();
			String date = contextFunction.apply(instant);

			// Overall stats (do not need "latest" data)
			overallStatsContext.notify(date, overallStats);

			List<? extends Statistic> latestStatisticsList = timeBuckets.reduceLatest(instantMillis, allDurations);
			for (int i = 0; i < allDurations.size(); i++) {
				Duration duration = allDurations.get(i);
				Statistic stats = latestStatisticsList.get(i);

				// Latest stats
				StatisticContext latestStats = latestStatsByDuration.get(duration);
				if (latestStats != null) {
					latestStats.notify(date, stats);
				}

				// Alerting
				List<AlertState<?>> alertStates = alertStatesByDuration.get(duration);
				if (alertStates != null) {
					alertStates.forEach(
						// Will potentially notify listeners
						alertState -> alertState.check(stats, instantMillis));
				}
			}
		}
	}
}
