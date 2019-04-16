/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat;

import javax.annotation.concurrent.NotThreadSafe;
import java.time.Duration;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Handles a two-steps aggregation so that we can retrieve the last metrics for a duration many times in this duration.
 *
 * Hold some guarantee some predictable memory consumption without depending on the load.
 *
 * @param <T> Any type holding time-bound metrics implementing {@link TimeBound}.
 * @param <A> An aggregate of {@code <T>} over a short period of time called {@literal a time bucket}. It can be
 *           {@link name.larcher.fabrice.logncat.stat.Statistic} for example.
 */
@NotThreadSafe
public class TimeBuckets<T extends TimeBound, A extends Consumer<T>>
	implements Consumer<T> {

	/**
	 * @param reducer        Same idiom as for {@link java.util.stream.Stream#reduce(Object, BinaryOperator)}. The
	 *                       reducer should not care about time precedence between buckets metrics.
	 * @param identity       Same idiom as for {@link java.util.stream.Stream#reduce(Object, BinaryOperator)}. This
	 *                       identity instance must always be defined so that:
	 *                       {@code reducer.apply(identity, identity).equals(identity)}. It should also never
	 *                       change after been used as a argument of {@code reducer.apply(.,.)}.
	 * @param bucketDuration The duration of a single bucket.
	 */
	public TimeBuckets(
			Function<T, A> factory,
			BinaryOperator<A> reducer,
			A identity,
			Duration bucketDuration) {

		this.metricReducer = Objects.requireNonNull(reducer);
		this.identityMetric = Objects.requireNonNull(identity);
		this.metricFactory = Objects.requireNonNull(factory);
		this.bucketDurationMillis = bucketDuration.toMillis();
		if (bucketDurationMillis <= 0 ) {
			throw new IllegalArgumentException("Non-positive bucket duration");
		}
		// We use reversed chronological order because new entries are young
		this.buckets = new ConcurrentSkipListMap<>(
			Comparator.<Long> naturalOrder().reversed());
	}

	private final Function<T, A> metricFactory;
	private final BinaryOperator<A> metricReducer;
	private final A identityMetric;

	private final long bucketDurationMillis;

	/**
	 * Contains aggregates by timestamps divided by {@link #bucketDurationMillis}.
	 */
	private final ConcurrentSkipListMap<Long, A> buckets;

	@Override
	public final void accept(T t) {
		long key = t.getTimeInMillis() / bucketDurationMillis;
		buckets.compute(key, (k, v) -> {
			if (v == null) {
				return metricFactory.apply(t);
			}
			else {
				v.accept(t);
				return v;
			}
		});
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
	 * @return The reducer metrics over the time frame.
	 */
	public final A reduceLatest(long untilMillis, Duration duration) {
		long sinceKey = (untilMillis - duration.toMillis()) / bucketDurationMillis;
		long untilKey = untilMillis / bucketDurationMillis;
		A reduced = identityMetric;
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
}
