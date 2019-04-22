/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.stat;

import javax.annotation.concurrent.Immutable;
import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Immutable
public final class StatisticContext {

	private static <I, O> Supplier<O> convertSupplier(Supplier<I> supplier, Function<I, O> function) {
		return () -> {
			I in = supplier.get();
			return in == null ? null : function.apply(in);
		};
	}

	public static <T> StatisticContext createOverallContext(
			Supplier<T> sinceGetter,
			Supplier<T> untilGetter,
			Function<T, Long> toMillis,
			int topSectionCount,
			Comparator<Statistic.ScopedStatistic> sectionComparator,
			BiConsumer<StatisticContext, Statistic> listener) {
		return createOverallContext(
			convertSupplier(sinceGetter, toMillis),
			convertSupplier(untilGetter, toMillis),
			topSectionCount, sectionComparator, listener);
	}

	public static StatisticContext createOverallContext(
			Supplier<Long> sinceMillisGetter,
			Supplier<Long> untilMillisGetter,
			int topSectionCount,
			Comparator<Statistic.ScopedStatistic> sectionComparator,
			BiConsumer<StatisticContext, Statistic> listener) {
		return new StatisticContext(
				() -> {
					Long since = sinceMillisGetter.get();
					if (since == null) {
						return Duration.ZERO;
					}
					Long until = untilMillisGetter.get();
					if (until == null) {
						until = System.currentTimeMillis();
					}
					return Duration.ofMillis(until - since);
				},
				topSectionCount,
				sectionComparator,
				listener,
				true);
	}

	public static StatisticContext createTimeRangeContext(
			Duration duration,
			int topSectionCount,
			Comparator<Statistic.ScopedStatistic> sectionComparator,
			BiConsumer<StatisticContext, Statistic> listener) {
		return new StatisticContext(() -> duration, topSectionCount, sectionComparator, listener, false);
	}

	private StatisticContext(
			Supplier<Duration> durationGetter,
			int topSectionCount,
			Comparator<Statistic.ScopedStatistic> sectionComparator,
			BiConsumer<StatisticContext, Statistic> listener,
			boolean isDynamic) {

		this.durationGetter = Objects.requireNonNull(durationGetter);
		this.topSectionCount = topSectionCount;
		this.listener = Objects.requireNonNull(listener);
		this.sectionComparator = Objects.requireNonNull(sectionComparator);
		this.isDynamic = isDynamic;
	}

	private final Supplier<Duration> durationGetter;
	private final int topSectionCount;
	private final Comparator<Statistic.ScopedStatistic> sectionComparator;
	private final BiConsumer<StatisticContext, Statistic> listener;
	private final boolean isDynamic;

	public boolean isDynamic() {
		return isDynamic;
	}

	public Duration getDuration() {
		return durationGetter.get();
	}

	public int getTopSectionCount() {
		return topSectionCount;
	}

	public Comparator<Statistic.ScopedStatistic> getSectionComparator() {
		return sectionComparator;
	}

	public void notify(Statistic statistic) {
		listener.accept(this, statistic);
	}
}
