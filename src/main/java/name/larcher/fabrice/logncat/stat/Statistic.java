/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.stat;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Comparator;

/**
 * Statistics aggregated over time about access logs.
 */
public interface Statistic {

	ScopedStatistic overall();

	Collection<ScopedStatistic> topSections();

	void clear();

	Comparator<ScopedStatistic> sectionComparator();

	/**
	 * @return false if not implemented
	 */
	void add(Statistic other);

	/**
	 * Describes metrics provided for a given context/scope.
	 */
	interface ScopedStatistic {

		/**
		 * @return The section of {@code null} if the scope is global.
		 */
		@Nullable
		default String getSection() {
			return null;
		}

		int requestCount();

		/**
		 * @return false if not implemented
		 */
		void add(ScopedStatistic other);

	}

}
