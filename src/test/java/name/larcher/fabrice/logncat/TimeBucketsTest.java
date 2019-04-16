/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat;

import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;

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

	private static class InfoAggregate implements Consumer<TimeBoundInfo> {

		private int sum = 0;

		@Override
		public void accept(TimeBoundInfo timeBoundInfo) {
			sum += timeBoundInfo.count;
		}
	}

	private static final InfoAggregate IDENTITY = new InfoAggregate();

	private static Function<TimeBoundInfo, InfoAggregate> FACTORY = tb -> {
			InfoAggregate infoAggregate = new InfoAggregate();
			infoAggregate.accept(tb);
			return infoAggregate;
		};

	private static BinaryOperator<InfoAggregate> REDUCER = (l, r) -> {
			InfoAggregate n = new InfoAggregate();
			n.sum += l.sum;
			n.sum += r.sum;
			return n;
		};

	@Test
	public void singleEntry() {

		TimeBuckets<TimeBoundInfo, InfoAggregate> buckets = new TimeBuckets<>(FACTORY, REDUCER, IDENTITY,
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
	public void manyEntries() {

		Duration bucketDuration = Duration.ofMillis(10);
		TimeBuckets<TimeBoundInfo, InfoAggregate> buckets = new TimeBuckets<>(FACTORY, REDUCER, IDENTITY,
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
	public void cleaning() {

		Duration bucketDuration = Duration.ofMillis(10);
		TimeBuckets<TimeBoundInfo, InfoAggregate> buckets = new TimeBuckets<>(FACTORY, REDUCER, IDENTITY,
				bucketDuration);

		int value = 42;
		long now = System.currentTimeMillis();
		TimeBoundInfo info = new TimeBoundInfo(value, now);
		buckets.accept(info);

		buckets.cleanUpOldest(now + (2 * bucketDuration.toMillis()), bucketDuration);
		InfoAggregate reducedValue = buckets.reduceLatest(now, Duration.ofMillis(1));
		Assert.assertNotNull(reducedValue);
		Assert.assertEquals(IDENTITY.sum, reducedValue.sum);
	}

}
