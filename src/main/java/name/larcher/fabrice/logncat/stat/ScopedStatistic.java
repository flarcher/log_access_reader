/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.stat;

import javax.annotation.Nullable;

/**
 * Describes metrics provided for a given context/scope.
 */
public interface ScopedStatistic {

	/**
	 * @return The section of {@code null} if the scope is global.
	 */
	@Nullable String getSection();

	int requestCount();

	//MetricOverTime<Integer> throughput();

}
