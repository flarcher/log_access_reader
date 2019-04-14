/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.stat;

import java.util.function.Consumer;

/**
 * Lists all needed stats.
 * Is needed, so that the metric computation can be delegated to implementations that take advantage of type related
 * optimizations (like the use of a _native type_).
 * @param <T> The value type.
 */
public interface MetricOverTime<T> extends Consumer<T> {

	T average();

	T maximum();

	void clear();
}
