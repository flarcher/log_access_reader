/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.alert;

import name.larcher.fabrice.logncat.stat.Statistic;

import javax.annotation.concurrent.NotThreadSafe;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Stores the state related to an alert configuration for a given duration.
 * It should consume the incoming
 *
 * @param <M> The alert metric.
 */
@NotThreadSafe
public class AlertState<M> implements BiConsumer<Statistic, Instant> {

	public AlertState(AlertConfig<M> config, Duration duration) {
		this.config = config;
		this.duration = duration;
	}

	private final AlertConfig<M> config;
	private final Duration duration;

	public Duration getDuration() {
		return duration;
	}

	// State

	private boolean isActive = false;
	private Instant sinceInstant = null;
	private M sinceValue = null;

	// Consumption

	public void check(Statistic statistic, long at) {
		M currentValue = config.getExtractor().apply(statistic, duration);
		boolean eval = config.getPredicate().test(currentValue);
		if (isActive != eval) {
			isActive = eval;
			Instant until;
			if (eval) {
				sinceInstant = Instant.ofEpochMilli(at - duration.toMillis());
				sinceValue = currentValue;
				until = null;
			}
			else {
				until = Instant.ofEpochMilli(at);
				if (sinceInstant == null) { // Can it really happen?
					sinceInstant = until.minus(duration);
				}
				if (sinceValue == null) { // Can it really happen?
					sinceValue = currentValue;
				}
			}
			config.getListener().accept(
				new AlertEvent<>(config, duration, sinceValue, sinceInstant, until));
		}
	}

	@Override
	public void accept(Statistic statistic, Instant instant) {
		check(statistic, instant.toEpochMilli());
	}

	// Generated

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		AlertState<?> that = (AlertState<?>) o;
		return Objects.equals(config, that.config) &&
				Objects.equals(duration, that.duration);
	}

	@Override
	public int hashCode() {
		return Objects.hash(config, duration);
	}

	@Override
	public String toString() {
		return "AlertState{" +
				"config=" + config +
				", duration=" + duration +
				'}';
	}
}
