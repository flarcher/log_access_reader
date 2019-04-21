/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Stores the first and latest entries.
 */
@ThreadSafe
class LatestConsumer<T extends TimeBound> implements Consumer<T>, Supplier<T> {

	private final AtomicReference<T> firstRef = new AtomicReference<>();
	private final AtomicReference<T> latestRef = new AtomicReference<>();

	private final BinaryOperator<T> accumulator = (l, r) ->
		r == null
			? l
			: l == null || l.getTimeInMillis() < r.getTimeInMillis()
				? r
				: l;

	@Override
	public void accept(T t) {
		T previous = latestRef.getAndAccumulate(t, accumulator);
		if (previous == null && t != null) {
			firstRef.compareAndSet(null, t);
		}
	}

	public T getFirst() {
		return firstRef.get();
	}

	public T getLatest() {
		return latestRef.get();
	}

	@Override
	public T get() {
		return getLatest();
	}
}
