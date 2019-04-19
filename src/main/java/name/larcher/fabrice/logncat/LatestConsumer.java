/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;

/**
 * Stores the latest entry.
 */
@ThreadSafe
public class LatestConsumer<T extends TimeBound> implements Consumer<T> {

	private final AtomicReference<T> reference = new AtomicReference<>();

	private final BinaryOperator<T> accumulator = (l, r) ->
		r == null
			? l
			: l == null || l.getTimeInMillis() < r.getTimeInMillis()
				? r
				: l;

	@Override
	public void accept(T t) {
		reference.accumulateAndGet(t, accumulator);
	}

	public T getLatest() {
		return reference.get();
	}
}
