/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package flarcher.log.access.alert;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * An event for either a raised alert or a released alert.
 * To be used as part of the {@literal Observer} pattern.
 *
 * @param <M> Metric type on which the alert is based.
 */
@Immutable
public class AlertEvent<M> {

	AlertEvent(AlertConfig config, Duration overDuration,
			   M valueAtSince,
			   Instant since, @Nullable Instant until) {
		this.config = Objects.requireNonNull(config);
		this.overDuration = Objects.requireNonNull(overDuration);
		this.since = Objects.requireNonNull(since);
		this.until = until; // Nullable
		this.valueAtSince = Objects.requireNonNull(valueAtSince);
	}

	private final AlertConfig config;
	private final Duration overDuration;
	private final Instant since;
	private final M valueAtSince;

	@Nullable
	private final Instant until;

	//--- Getters

	public AlertConfig getConfig() {
		return config;
	}

	public Duration getOverDuration() {
		return overDuration;
	}

	public boolean isRaised() {
		return until == null;
	}

	public boolean isReleased() {
		return !isRaised();
	}

	public Instant getSince() {
		return since;
	}

	@Nullable
	public Instant getUntil() {
		return until;
	}

	/**
	 * @return The metric value when the alert was triggered first (that is at the time of {@link #getSince()}.
	 */
	public M getValueAtSince() {
		return valueAtSince;
	}

	// Generated

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		AlertEvent<?> that = (AlertEvent<?>) o;
		return config.equals(that.config) &&
				overDuration.equals(that.overDuration) &&
				since.equals(that.since);
	}

	@Override
	public int hashCode() {
		return Objects.hash(config, overDuration, since);
	}
}
