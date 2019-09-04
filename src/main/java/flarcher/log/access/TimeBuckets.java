/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package flarcher.log.access;

import flarcher.log.access.stat.Statistic;

import javax.annotation.concurrent.ThreadSafe;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Handles a two-steps aggregation so that we can retrieve the last metrics for a duration many times in this duration.
 *
 * Hold some guarantee about predictable memory usage without assumption about the load.
 *
 * Is thread-safe so that {@link #accept(TimeBound)} can be called from one thread, and the other methods from another.
 *
 * @param <T> Any type holding time-bound metrics implementing {@link TimeBound}.
 * @param <A> An aggregate of {@code <T>} over a short period of time called {@literal a time bucket}. It can be
 *           {@link Statistic} for example.
 */
@ThreadSafe
public class TimeBuckets<
			T extends TimeBound,
			A extends Consumer<T> & AutoCloseable
		>
		implements Consumer<T> {

	/**
	 * @param reducer        Same idiom as for {@link java.util.stream.Stream#reduce(Object, BinaryOperator)}. The
	 *                       reducer should not care about time precedence between buckets metrics.
	 * @param factory        Creates a metric instance {@code i} so that: {@code reducer.apply(i, i).equals(i)}.
	 * @param bucketDuration The duration of a single bucket.
	 */
	public TimeBuckets(
			Supplier<A> factory,
			BinaryOperator<A> reducer,
			Duration bucketDuration) {

		this.metricReducer = Objects.requireNonNull(reducer);
		this.metricFactory = Objects.requireNonNull(factory);
		this.bucketDurationMillis = bucketDuration.toMillis();
		if (bucketDurationMillis <= 0 ) {
			throw new IllegalArgumentException("Non-positive bucket duration");
		}
		// We use reversed chronological order because new entries are young
		this.buckets = new ConcurrentSkipListMap<>(
			Comparator.<Long> naturalOrder().reversed());
	}

	private final Supplier<A> metricFactory;
	private final BinaryOperator<A> metricReducer;

	private final long bucketDurationMillis;

	/**
	 * Contains aggregates by timestamps divided by {@link #bucketDurationMillis}.
	 */
	private final ConcurrentSkipListMap<Long, A> buckets;

	public int getBucketCount() {
		return buckets.size();
	}

	@Override
	public final void accept(T t) {
		long key = t.getTimeInMillis() / bucketDurationMillis;
		buckets.compute(key, (k, v) -> {
			if (v == null) {
				v = metricFactory.get();
			}
			v.accept(t);
			return v;
		});
	}

	private void cleanAggregate(A aggregate) {
		try {
			aggregate.close();
		}
		catch (@SuppressWarnings("unused") Exception e) {}
	}

	/**
	 * Removes oldest entries that are linked ot a time previous to the given time range.
	 * @param untilMillis Time end of the time range in millis.
	 * @param duration Duration of the time range.
	 */
	public final void cleanUpOldest(long untilMillis, Duration duration) {
		long keyLimit = (untilMillis - duration.toMillis()) / bucketDurationMillis;
		Iterator<Map.Entry<Long, A>> reversedIterator = buckets.descendingMap().entrySet().iterator();
		while (reversedIterator.hasNext()) {
			Map.Entry<Long, A> entry = reversedIterator.next();
			if (entry.getKey() < keyLimit) {
				cleanAggregate(entry.getValue());
				reversedIterator.remove();
			}
			else {
				break; // Because it is sorted
			}
		}
	}

	/**
	 * Computes aggregated metrics for a duration bigger that {@link #bucketDurationMillis}.
	 * @param untilMillis End instant of the time frame.
	 * @param duration Duration of the time frame.
	 * @return The reduced metrics over the time frame.
	 */
	public final A reduceLatest(long untilMillis, Duration duration) {
		long sinceKey = (untilMillis - duration.toMillis()) / bucketDurationMillis;
		long untilKey = untilMillis / bucketDurationMillis;
		A reduced = metricFactory.get();
		Iterator<Map.Entry<Long, A>> iterator = buckets.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Long, A> entry = iterator.next();
			if (entry.getKey() > untilKey) {
				// Too young for the time frame (the value would be used later)
			}
			else if (entry.getKey() >= sinceKey) {
				reduced = metricReducer.apply(reduced, entry.getValue());
			}
			else {
				// Too old; since it is sorted, we can stop there
				break;
			}
		}
		return reduced;
	}

	/**
	 * Does the equivalent of several calls to {@link #reduceLatest(long, Duration)} more efficiently,
	 * and also does a cleaning (like {@link #cleanUpOldest(long, Duration)}) according to the greatest
	 * duration provided in the input list.
	 *
	 * @param untilMillis End instant of all the time frames.
	 * @param durations   Durations of the time frames. They must be sorted from the shortest range to the greatest.
	 * @return The Reduced metrics over the time frame in the order of the given durations.
	 */
	public final List<A> reduceLatestAndClean(long untilMillis, List<Duration> durations) {

		if (durations == null || durations.isEmpty()) {
			throw new IllegalArgumentException();
		}

		long untilKey = untilMillis / bucketDurationMillis;
		List<A> reducedValues = IntStream.range(0, durations.size())
				.mapToObj(index -> metricFactory.get())
				.collect(Collectors.toList());
		Duration greatestDuration = durations.stream()
				.max(Comparator.naturalOrder())
				.get();
		long oldestKey = (untilMillis - greatestDuration.toMillis()) / bucketDurationMillis;

		Iterator<Map.Entry<Long, A>> iterator = buckets.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Long, A> entry = iterator.next();
			if (entry.getKey() > untilKey) {
				// Too young for the time frame (the value would be used later)
			}
			else if (entry.getKey() >= oldestKey) {
				// Iterating over durations
				for (int i = 0; i < durations.size(); i++) {
					Duration duration = durations.get(i);
					A reducedValue = reducedValues.get(i);
					if (duration == greatestDuration /* Is always considered */
						|| entry.getKey() >= ((untilMillis - duration.toMillis()) / bucketDurationMillis)) {

						reducedValues.set(i, metricReducer.apply(reducedValue, entry.getValue()));
					}
				}
			}
			else {
				// Too old for being used by any duration -> cleaning it up
				cleanAggregate(entry.getValue());
				iterator.remove();
			}
		}
		return reducedValues;
	}
}
