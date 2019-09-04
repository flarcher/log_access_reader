/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package flarcher.log.access;

import flarcher.log.access.stat.Statistic;
import flarcher.log.access.stat.StatisticTimeBucketsFactory;

import javax.management.MXBean;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * JMX bean for the application.
 */
public final class Monitoring {

	public static void register(
			StatisticTimeBucketsFactory.StatisticTimeBuckets buckets,
			Supplier<Duration> readTime,
			Duration maxDuration) {

		try {
			ManagementFactory.getPlatformMBeanServer().registerMBean(
					new MetricsGetterIml(buckets, readTime, maxDuration),
					new ObjectName("name.larcher.fabrice.logncat.metrics:type=metrics"));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private Monitoring() {}

	private static class MetricsGetterIml implements MetricsGetter {

		private MetricsGetterIml(StatisticTimeBucketsFactory.StatisticTimeBuckets buckets, Supplier<Duration> readTime, Duration maxDuration) {
			this.buckets = buckets;
			this.readTime = readTime;
			this.maxDuration = maxDuration;
		}

		private final StatisticTimeBucketsFactory.StatisticTimeBuckets buckets;
		private final Supplier<Duration> readTime;
		private final Duration maxDuration;

		@Override
		public int getMaxSectionCount() {
			return StatisticTimeBucketsFactory.MAX_SECTION_COUNT_EVER.get();
		}

		@Override
		public String getReadDuration() {
			return DurationConverter.toString(readTime.get());
		}

		@Override
		public String getLongestWatcherDuration() {
			return DurationConverter.toString(maxDuration);
		}

		@Override
		public int getBucketCount() {
			return buckets.getBucketCount();
		}

	}

	@MXBean
	public interface MetricsGetter {

		/**
		 * @return Maximum section count ever held in an instance of {@link Statistic}.
		 */
		int getMaxSectionCount();

		/**
		 * @return The overall run time in milliseconds.
		 */
		String getReadDuration();

		/**
		 * @return The longest duration configured for watching last accesses related to either stats or alerts.
		 */
		String getLongestWatcherDuration();

		/**
		 * @return The current count of buckets in {@link TimeBuckets}.
		 */
		int getBucketCount();
	}
}
