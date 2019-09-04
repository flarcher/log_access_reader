/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package flarcher.log.access.stat;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Statistics aggregated over time about access logs.
 */
public interface Statistic extends AutoCloseable {

	ScopedStatistic overall();

	/**
	 * @return The comparator for section top ordering.
	 */
	Comparator<ScopedStatistic> sectionComparator();

	/**
	 * @return Sorted list (according to {@link #sectionComparator()}) of section statistics.
	 */
	List<Map.Entry<String, ? extends ScopedStatistic>> topSections();

	/**
	 * Resets the stats.
	 */
	void clear();

	@Override
	default void close() {
		clear();
	}

	/**
	 * Aggregates statistics of another instance.
	 */
	void add(Statistic other);

	/**
	 * Describes metrics provided for a given context/scope.
	 */
	interface ScopedStatistic {

		int requestCount();

		int weight();

		/**
		 * Aggregates statistics of another instance.
		 */
		void add(ScopedStatistic other);

	}

}
