/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.alert;

import name.larcher.fabrice.logncat.stat.Statistic;
import name.larcher.fabrice.logncat.stat.StatisticTimeBucketsFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A task about checking alert states.
 */
public class AlertCheckingTask implements Runnable {

	public AlertCheckingTask(
			StatisticTimeBucketsFactory.StatisticTimeBuckets latestStatsBuckets,
			Map<AlertConfig<?>, List<Duration>> alertConfigurations,
			Clock clock
	) {
		this(latestStatsBuckets,
			alertConfigurations.entrySet().stream()
				.map(configEntry -> configEntry.getValue().stream()
					.map(duration -> new AlertState<>(configEntry.getKey(), duration))
					.collect(Collectors.toList())
				)
				.flatMap(List::stream)
				.collect(Collectors.toList()),
			clock,
			alertConfigurations.values().stream()
				.flatMap(List::stream)
				.sorted()
				.distinct()
				.collect(Collectors.toList())
		);
	}

	AlertCheckingTask(
			StatisticTimeBucketsFactory.StatisticTimeBuckets latestStatsBuckets,
			List<AlertState<?>> alertStates,
			Clock clock) {

		this(latestStatsBuckets, alertStates, clock,
			alertStates.stream()
					.map(AlertState::getDuration)
					.sorted()
					.distinct()
					.collect(Collectors.toList()));
	}

	private AlertCheckingTask(
			StatisticTimeBucketsFactory.StatisticTimeBuckets latestStatsBuckets,
			List<AlertState<?>> alertStates,
			Clock clock,
			List<Duration> durations) {

		this.latestStatsBuckets = Objects.requireNonNull(latestStatsBuckets);
		this.alertStates = Objects.requireNonNull(alertStates);
		this.clock = Objects.requireNonNull(clock);
		this.durations = Objects.requireNonNull(durations);
		assert alertStates.size() == durations.size();
	}

	private final StatisticTimeBucketsFactory.StatisticTimeBuckets latestStatsBuckets;
	private final List<AlertState<?>> alertStates; // States of alerts
	private final Clock clock;
	private final List<Duration> durations; // Distinct and sorted durations for time buckets request

	@Override
	public void run() {
		Thread.currentThread().setName("Alerting");
		Instant instant = clock.instant();
		if (instant != null) {  // Can be null without traffic

			long instantMillis = instant.toEpochMilli();
			List<? extends Statistic> latestStatisticsList = latestStatsBuckets.reduceLatest(
					instantMillis,
					durations);

			Map<Duration, Statistic> statsByDurations = new HashMap<>(durations.size());
			for (int i=0; i < durations.size(); i++) {
				statsByDurations.put(durations.get(i), latestStatisticsList.get(i));
			}
			alertStates.forEach(alertState -> {
				Statistic stats = statsByDurations.get(alertState.getDuration());
				alertState.check(stats, instantMillis); // Will notify listeners
			});
		}
	}
}
