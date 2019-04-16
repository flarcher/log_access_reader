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
	private final ScopedStatisticAggregator overallStats = new ScopedStatisticAggregator(null);
	private final ConcurrentMap<String, ScopedStatisticAggregator> statsBySection;

	@Override
	public Comparator<ScopedStatistic> sectionComparator() {
		return comparator;
	}

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
		Collection<ScopedStatisticAggregator> values = statsBySection.values();
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
		ScopedStatisticAggregator scopedStats = statsBySection.computeIfAbsent(
				accessLogLine.getSection(),
				ScopedStatisticAggregator::new);
		scopedStats.accept(accessLogLine);
	}

	@Override
	public void add(Statistic other) {

		overallStats.add(other.overall());

		other.topSections().forEach( otherSectionStats -> {
				ScopedStatisticAggregator currentStats = statsBySection.get(otherSectionStats.getSection());
				if (currentStats == null) {
					statsBySection.compute(otherSectionStats.getSection(), (k, v) -> {
						if (v == null) {
							if (otherSectionStats instanceof ScopedStatisticAggregator) {
								return (ScopedStatisticAggregator) otherSectionStats;
							}
							else {
								v = new ScopedStatisticAggregator(k);
							}
						}
						v.add(otherSectionStats);
						return v;
					});
				}
			});
	}

	@ThreadSafe
	private static class ScopedStatisticAggregator implements ScopedStatistic, Consumer<AccessLogLine> {

		ScopedStatisticAggregator(String section) {
			this.section = section;
		}

		private final String section;
		private AtomicInteger count = new AtomicInteger(0);

		@Nullable
		@Override
		public String getSection() {
			return section;
		}

		@Override
		public int requestCount() {
			return count.get();
		}

		@Override
		public void accept(AccessLogLine accessLogLine) {
			count.incrementAndGet();
		}

		@Override
		public void add(ScopedStatistic other) {
			count.addAndGet(other.requestCount());
		}

		void clear() {
			count.set(0);
		}
	}
}
