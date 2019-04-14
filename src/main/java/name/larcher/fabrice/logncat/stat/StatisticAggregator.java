/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.stat;

import name.larcher.fabrice.logncat.read.AccessLogLine;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Statistics gathering from access log lines.
 */
public class StatisticAggregator implements Statistic, Consumer<AccessLogLine> {

	/**
	 * @param comparator A comparator for sorting stats.
	 */
	public StatisticAggregator(Comparator<ScopedStatistic> comparator) {
		this.comparator = Objects.requireNonNull(comparator);

		// The map will be called from the rendering thread, so it should be concurrent
		// On the other side, it should not be sorted since values are updated after add
		this.statsBySection = new ConcurrentHashMap<>();
	}

	private final Comparator<ScopedStatistic> comparator;
	private final ScopedStatisticImpl overallStats = new ScopedStatisticImpl(null);

	private final ConcurrentMap<String, ScopedStatisticImpl> statsBySection;

	@Override
	public void clear() {
		overallStats.clear();
		statsBySection.clear();
	}

	@Override
	public ScopedStatistic overall() {
		return overallStats;
	}

	@Override
	public Collection<ScopedStatistic> topSections() {
		Collection<ScopedStatisticImpl> values = statsBySection.values();
		if (values.isEmpty()) {
			return Collections.emptyList();
		}
		PriorityQueue<ScopedStatistic> queue = new PriorityQueue<>(values.size(), comparator);
		queue.addAll(values);
		return queue;
	}

	@Override
	public void accept(AccessLogLine accessLogLine) {
		overallStats.accept(accessLogLine);
		ScopedStatisticImpl scopedStats = statsBySection.computeIfAbsent(
				accessLogLine.getSection(),
				ScopedStatisticImpl::new);
		scopedStats.accept(accessLogLine);
	}

	@ThreadSafe
	private static class ScopedStatisticImpl implements ScopedStatistic, Consumer<AccessLogLine> {

		ScopedStatisticImpl(String section) {
			this.section = section;
		}

		private final String section;
		private AtomicInteger count = new AtomicInteger(0);
		//private MetricOverTime<Integer> throughput = new IntegerMetricOverTime();

		@Nullable
		@Override
		public String getSection() {
			return section;
		}

		@Override
		public int requestCount() {
			return count.get();
		}

		/*@Override
		public MetricOverTime<Integer> throughput() {
			return null;
		}*/

		@Override
		public void accept(AccessLogLine accessLogLine) {
			count.incrementAndGet();
		}

		void clear() {
			count.set(0);
		}
	}
}
