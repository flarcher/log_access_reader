/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.alert;

import name.larcher.fabrice.logncat.stat.Statistic;

import javax.annotation.concurrent.Immutable;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Alert configuration.
 *
 * Is immutable.
 * Is part of the {@literal Flyweight} pattern used with {@link AlertState} and {@link AlertEvent}.
 *
 * @param <M> The type of the metric used for the related predicate.
 *           This is also the type used by the related {@link AlertEvent}.
 */
@Immutable
public class AlertConfig<M> {

	public AlertConfig(
			BiFunction<Statistic, Duration, M> extractor,
			Predicate<M> predicate,
			String description,
			Consumer<AlertEvent<M>> listener) {

		this.predicate = Objects.requireNonNull(predicate);
		this.description = Objects.requireNonNull(description);
		this.listener = Objects.requireNonNull(listener);
		this.extractor = Objects.requireNonNull(extractor);
	}

	private final BiFunction<Statistic, Duration, M> extractor;
	private final Predicate<M> predicate;
	private final String description;
	private final Consumer<AlertEvent<M>> listener;

	public BiFunction<Statistic, Duration, M> getExtractor() {
		return extractor;
	}

	public Predicate<M> getPredicate() {
		return predicate;
	}

	public String getDescription() {
		return description;
	}

	public Consumer<AlertEvent<M>> getListener() {
		return listener;
	}

	// Generated

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		AlertConfig<?> that = (AlertConfig<?>) o;
		return extractor.equals(that.extractor) &&
				predicate.equals(that.predicate);
	}

	@Override
	public int hashCode() {
		return Objects.hash(extractor, predicate);
	}
}
