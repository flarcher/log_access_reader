/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.stat;

import java.util.Comparator;

/**
 * Comparators for scoped statistics.
 */
public interface ScopedStatisticComparators {

	Comparator<ScopedStatistic> BY_REQUEST_COUNT =
			Comparator.comparing(ScopedStatistic::requestCount).reversed();

}
