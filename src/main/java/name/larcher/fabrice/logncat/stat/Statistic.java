/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.stat;

import java.util.Collection;

/**
 * Statistics aggregated over time about access logs.
 */
public interface Statistic {

	ScopedStatistic overall();

	Collection<ScopedStatistic> topSections();

	void clear();
}
