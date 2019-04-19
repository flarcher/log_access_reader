/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.stat;

import name.larcher.fabrice.logncat.TimeBuckets;
import name.larcher.fabrice.logncat.read.AccessLogLine;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;

/**
 * Creates some {@link StatisticTimeBuckets} (2-step aggregators) for use with {@link Statistic} instances.
 * Hides some implementation details about the use of {@link TimeBuckets}.
 */
public final class StatisticTimeBucketsFactory {

	private StatisticTimeBucketsFactory() {}

	/**
	 * Makes possible to lower the number of instances during the reduce operation.
	 * We should not update original values, but this class is intended to use its mutability.
	 */
	private static class StatisticForReduce extends StatisticAggregator {

		private StatisticForReduce(Comparator<ScopedStatistic> comparator, int maxSectionCount, boolean createdDuringReduce) {
			super(comparator, maxSectionCount);
			this.createdDuringReduce = createdDuringReduce;
		}

		private final boolean createdDuringReduce;
	}

	private static class StaticticReducer implements BinaryOperator<StatisticForReduce> {

		@Override
		public StatisticForReduce apply(StatisticForReduce left, StatisticForReduce right) {

			Comparator<Statistic.ScopedStatistic> sectionComparator = left.sectionComparator();
			assert right.sectionComparator() == sectionComparator;
			int maxSectionCount = left.getMaxSectionCount();
			assert right.getMaxSectionCount() == maxSectionCount;

			if (left.createdDuringReduce) {
				left.add(right);
				return left; // One instance less to create
			} else if (right.createdDuringReduce) {
				right.add(left);
				return right; // One instance less to create
			} else {
				// No update for either 'left' or 'right' since they might be read later
				StatisticForReduce aggr = new StatisticForReduce(sectionComparator, maxSectionCount, true);
				aggr.add(left);
				aggr.add(right);
				return aggr;
			}
		}
	}

	public interface StatisticTimeBuckets extends Consumer<AccessLogLine> {

		List<? extends Statistic> reduceLatest(long untilMillis, List<Duration> requestDurations);

	}

	/**
	 * The statsHolderFactory method that binds the {@link TimeBuckets} with the {@link Statistic} class.
	 * @param comparator Comparator used for comparison between sections/scopes.
	 * @param bucketDuration The duration of the smallest time range (bucket).
	 * @param maxSectionCount Section count limit.
	 * @return A {@link TimeBuckets} like construct that returns {@link Statistic} instances.
	 */
	public static StatisticTimeBuckets create(
			Comparator<Statistic.ScopedStatistic> comparator,
			Duration bucketDuration,
			int maxSectionCount) {

		TimeBuckets<AccessLogLine, StatisticForReduce> buckets = new TimeBuckets<>(
				() -> new StatisticForReduce(comparator, maxSectionCount, false),
				new StaticticReducer(),
				bucketDuration);

		return new StatisticTimeBuckets() {

			@Override
			public List<? extends Statistic> reduceLatest(long untilMillis, List<Duration> requestDurations) {
				return buckets.reduceLatestAndClean(untilMillis, requestDurations);
			}

			@Override
			public void accept(AccessLogLine accessLogLine) {
				buckets.accept(accessLogLine);
			}
		};
	}
}
