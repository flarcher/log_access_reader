/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.stat;

import java.util.Comparator;

public interface ScopedStatisticComparators {

	Comparator<Statistic.ScopedStatistic> COMPARATOR_BY_REQUEST_COUNT =
			Comparator.comparing(Statistic.ScopedStatistic::requestCount).reversed();

	// We can define other comparators here

}
