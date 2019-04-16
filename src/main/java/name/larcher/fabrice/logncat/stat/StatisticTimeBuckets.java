/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.stat;

import name.larcher.fabrice.logncat.TimeBuckets;
import name.larcher.fabrice.logncat.read.AccessLogLine;

import java.time.Duration;
import java.util.Comparator;
import java.util.function.BinaryOperator;
import java.util.function.Function;

/**
 * A {@link TimeBuckets} that fits for {@link Statistic} 2-step aggregators.
 */
public class StatisticTimeBuckets extends TimeBuckets<AccessLogLine, StatisticTimeBuckets.StatisticForReduce> {

	private static Function<AccessLogLine, StatisticForReduce> factory(Comparator<Statistic.ScopedStatistic> comparator) {
		return line -> {
			StatisticForReduce aggregator = new StatisticForReduce(comparator, false);
			aggregator.accept(line);
			return aggregator;
		};
	}

	/**
	 * Makes possible to lower the number of instances during the reduce operation.
	 * We should not update original values, but this class is intended to use its mutability.
	 */
	public static class StatisticForReduce extends StatisticAggregator {

		private StatisticForReduce(Comparator<ScopedStatistic> comparator, boolean createdDuringReduce) {
			super(comparator);
			this.createdDuringReduce = createdDuringReduce;
		}

		private final boolean createdDuringReduce;
	}

	private static final BinaryOperator<StatisticForReduce> REDUCER = (left, right) ->
		{
			Comparator<Statistic.ScopedStatistic> sectionComparator = left.sectionComparator();
			assert right.sectionComparator() == sectionComparator;
			if (left.createdDuringReduce) {
				left.add(right);
				return left; // One instance less to create
			}
			else if (right.createdDuringReduce) {
				right.add(left);
				return right; // One instance less to create
			}
			else {
				// No update for either 'left' or 'right' since they might be reused for later use
				StatisticForReduce aggr = new StatisticForReduce(sectionComparator, true);
				aggr.add(left);
				aggr.add(right);
				return aggr;
			}
		};

	public StatisticTimeBuckets(Comparator<Statistic.ScopedStatistic> comparator, Duration bucketDuration) {
		super(factory(comparator), REDUCER, new StatisticForReduce(comparator, false), bucketDuration);
	}
}
