/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.stat;

import name.larcher.fabrice.logncat.read.AccessLogLine;

import javax.annotation.concurrent.ThreadSafe;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Statistics gathering from access log lines.
 * Is listening to log line from one thread and returning results to another.
 */
@ThreadSafe
public class StatisticAggregator implements Statistic, Consumer<AccessLogLine> {

	/**
	 * @param comparator A comparator for sorting stats.
	 */
	public StatisticAggregator(Comparator<ScopedStatistic> comparator, int maxSectionCount) {
		this.comparator = Objects.requireNonNull(comparator);

		// The map will be called from the rendering thread, so it should be concurrent
		// It has not to be sorted since values are updated after add
		this.statsBySection = new ConcurrentHashMap<>();
		// Robustness about memory consumption
		this.maxSectionCount = maxSectionCount;
	}

	private final Comparator<ScopedStatistic> comparator;
	private final ScopedStatisticAggregator overallStats = new ScopedStatisticAggregator();
	private final ConcurrentMap<String, ScopedStatisticAggregator> statsBySection;
	private final int maxSectionCount;

	int getMaxSectionCount() {
		return maxSectionCount;
	}

	int getSectionCount() {
		return statsBySection.size();
	}

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
	public List<Map.Entry<String, ? extends ScopedStatistic>> topSections() {
		Collection<ScopedStatisticAggregator> values = statsBySection.values();
		if (values.isEmpty()) {
			return Collections.emptyList();
		}
		Set<Map.Entry<String, ScopedStatisticAggregator>> entries = statsBySection.entrySet();
		List<Map.Entry<String, ? extends ScopedStatistic>> list = new ArrayList<>(entries.size());
		list.addAll(entries);
		list.sort(Comparator.comparing(Map.Entry::getValue, comparator));
		return list;
	}

	private static void warnAboutSectionSkipping(String section) {
		// TODO: warn once about not being able to store a new section information
		System.err.println("Skipped section " + section + " in order to limit memory usage");
	}

	@Override
	public void accept(AccessLogLine accessLogLine) {

		overallStats.accept(accessLogLine);

		String section = accessLogLine.getSection();
		if (statsBySection.size() >= maxSectionCount) {
			ScopedStatisticAggregator scopedStats = statsBySection.get(section);
			if (scopedStats != null) {
				scopedStats.accept(accessLogLine);
			}
			else {
				warnAboutSectionSkipping(section);
			}
		}
		else {
			statsBySection.compute(section, (k, v) -> {
				if (v == null) {
					v = new ScopedStatisticAggregator();
				}
				v.accept(accessLogLine);
				return v;
			});
		}
	}

	@Override
	public void add(Statistic other) {

		overallStats.add(other.overall());

		other.topSections().forEach( otherSectionEntry -> {
			String section = otherSectionEntry.getKey();
			ScopedStatistic otherSectionStats = otherSectionEntry.getValue();
			if (statsBySection.size() >= maxSectionCount) {
				ScopedStatisticAggregator thisSectionStats = statsBySection.get(section);
				if (thisSectionStats != null) {
					thisSectionStats.add(otherSectionStats);
				}
				else {
					warnAboutSectionSkipping(section);
				}
			}
			else {
				statsBySection.compute(section, (k, v) -> {
					if (v == null) {
						v = new ScopedStatisticAggregator();
					}
					v.add(otherSectionStats);
					return v;
				});
			}
		});
	}

	/**
	 * Is listening to log line from one thread and returning results to another.
	 */
	@ThreadSafe
	private static class ScopedStatisticAggregator implements ScopedStatistic, Consumer<AccessLogLine> {

		ScopedStatisticAggregator() {}

		private AtomicInteger count = new AtomicInteger(0);
		private AtomicInteger weight = new AtomicInteger(0);

		@Override
		public int requestCount() {
			return count.get();
		}

		@Override
		public int weight() {
			return weight.get();
		}

		@Override
		public void accept(AccessLogLine accessLogLine) {
			count.incrementAndGet();
			weight.addAndGet(accessLogLine.getLength());
		}

		@Override
		public void add(ScopedStatistic other) {
			count.addAndGet(other.requestCount());
			weight.addAndGet(other.weight());
		}

		void clear() {
			count.set(0);
			weight.set(0);
		}
	}
}
