/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package flarcher.log.access;

import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TimeBucketsTest {

	private static class TimeBoundInfo implements TimeBound {

		public TimeBoundInfo(int count, long millis) {
			this.count = count;
			this.millis = millis;
		}

		private final int count;
		private final long millis;

		@Override
		public long getTimeInMillis() {
			return millis;
		}
	}

	private static class InfoAggregate implements Consumer<TimeBoundInfo>, AutoCloseable {

		private int sum = 0;

		@Override
		public void accept(TimeBoundInfo timeBoundInfo) {
			sum += timeBoundInfo.count;
		}

		@Override
		public void close() { }
	}

	private static Supplier<InfoAggregate> FACTORY = InfoAggregate::new;

	private static BinaryOperator<InfoAggregate> REDUCER = (l, r) -> {
			InfoAggregate n = new InfoAggregate();
			n.sum += l.sum;
			n.sum += r.sum;
			return n;
		};

	private static long nowBucketized(Duration bucketDuration) {
		return (System.currentTimeMillis() / bucketDuration.toMillis()) * bucketDuration.toMillis();
	}

	@Test
	public void singleEntry() {

		TimeBuckets<TimeBoundInfo, InfoAggregate> buckets = new TimeBuckets<>(FACTORY, REDUCER,
				Duration.ofMillis(10));

		int value = 42;
		long now = System.currentTimeMillis();
		TimeBoundInfo info = new TimeBoundInfo(value, now);
		buckets.accept(info);

		InfoAggregate reducedValue = buckets.reduceLatest(now, Duration.ofMillis(1));
		Assert.assertNotNull(reducedValue);
		Assert.assertEquals(value, reducedValue.sum);
	}

	@Test
	public void manyEntries_excludeEarliest() {

		Duration bucketDuration = Duration.ofMillis(10);
		TimeBuckets<TimeBoundInfo, InfoAggregate> buckets = new TimeBuckets<>(FACTORY, REDUCER,
				bucketDuration);

		long now = (System.currentTimeMillis() / bucketDuration.toMillis()) * bucketDuration.toMillis();
		buckets.accept(new TimeBoundInfo(42, now));
		buckets.accept(new TimeBoundInfo(13, now + 5));
		buckets.accept(new TimeBoundInfo(20, now + 7));
		buckets.accept(new TimeBoundInfo(5, now + 12)); // Excluded

		InfoAggregate reducedValue = buckets.reduceLatest(now, Duration.ofMillis(10));
		Assert.assertNotNull(reducedValue);
		Assert.assertEquals(75L, reducedValue.sum);
	}

	@Test
	public void manyEntries_excludeOldest() {

		Duration bucketDuration = Duration.ofMillis(10);
		TimeBuckets<TimeBoundInfo, InfoAggregate> buckets = new TimeBuckets<>(FACTORY, REDUCER,
				bucketDuration);

		long now = nowBucketized(bucketDuration);
		buckets.accept(new TimeBoundInfo(5, now - 12)); // Excluded
		buckets.accept(new TimeBoundInfo(20, now - 7));
		buckets.accept(new TimeBoundInfo(13, now - 5));
		buckets.accept(new TimeBoundInfo(42, now));

		InfoAggregate reducedValue = buckets.reduceLatest(now, Duration.ofMillis(10));
		Assert.assertNotNull(reducedValue);
		Assert.assertEquals(75L, reducedValue.sum);
	}

	@Test
	public void cleaning() {

		Duration bucketDuration = Duration.ofMillis(10);
		TimeBuckets<TimeBoundInfo, InfoAggregate> buckets = new TimeBuckets<>(FACTORY, REDUCER,
				bucketDuration);

		int value = 42;
		long now = System.currentTimeMillis();
		TimeBoundInfo info = new TimeBoundInfo(value, now);
		buckets.accept(info);

		buckets.cleanUpOldest(now + (2 * bucketDuration.toMillis()), bucketDuration);
		InfoAggregate reducedValue = buckets.reduceLatest(now, Duration.ofMillis(1));
		Assert.assertNotNull(reducedValue);
		Assert.assertEquals(0, reducedValue.sum);
	}

	@Test
	public void addManyAndClean() {

		Duration bucketDuration = Duration.ofMillis(10);
		TimeBuckets<TimeBoundInfo, InfoAggregate> buckets = new TimeBuckets<>(FACTORY, REDUCER,
				bucketDuration);

		long now = nowBucketized(bucketDuration);
		buckets.accept(new TimeBoundInfo(5, now - 12)); // Excluded & cleaned-up
		buckets.accept(new TimeBoundInfo(20, now - 7));
		buckets.accept(new TimeBoundInfo(13, now - 5));
		buckets.accept(new TimeBoundInfo(42, now));

		// Resulting buckets: [42, 33, (absent)]

		Duration requestDuration = Duration.ofMillis(10);
		List<InfoAggregate> reducedValues = buckets.reduceLatestAndClean(now, Collections.singletonList(requestDuration));
		Assert.assertEquals(1, reducedValues.size());
		InfoAggregate reducedValue = reducedValues.get(0);
		Assert.assertNotNull(reducedValue);
		Assert.assertEquals(42 + 13 + 20, reducedValue.sum);

		reducedValue = buckets.reduceLatest(now - requestDuration.toMillis(), requestDuration);
		Assert.assertNotNull(reducedValue);
		Assert.assertEquals(13 + 20, reducedValue.sum);

		reducedValue = buckets.reduceLatest(now - (2 * requestDuration.toMillis()), requestDuration);
		Assert.assertNotNull(reducedValue);
		Assert.assertEquals(0, reducedValue.sum);
	}

}
