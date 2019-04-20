/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.alert;

import name.larcher.fabrice.logncat.stat.Statistic;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AlertStateTest {

	private static final Duration DURATION = Duration.ofSeconds(1); // We do not care much here

	private AtomicInteger callCount = new AtomicInteger(0);

	private final AlertConfig<Integer> config = new AlertConfig<>(
			(stat, duration) -> stat.overall().requestCount(),
			value -> value > 42,
			"Do not get bigger than 42!",
			alert -> callCount.incrementAndGet()
		);

	@Before
	public void before() {
		callCount.set(0);
	}

	private AlertState<Integer> createState() {
		return new AlertState<>(config, DURATION);
	}

	private Instant previousInstant = Instant.now();

	private Instant getNextInstant() {
		previousInstant = previousInstant.plus(1, ChronoUnit.SECONDS);
		return previousInstant;
	}

	private Statistic createStat(int reqCount) {
		return new Statistic() {

			@Override
			public ScopedStatistic overall() {
				return new ScopedStatistic() {

					@Override
					public int requestCount() {
						return reqCount;
					}

					@Override
					public void add(ScopedStatistic other) {

					}
				};
			}

			@Override
			public Comparator<ScopedStatistic> sectionComparator() {
				return null;
			}

			@Override
			public List<Map.Entry<String, ? extends ScopedStatistic>> topSections() {
				return null;
			}

			@Override
			public void clear() {

			}

			@Override
			public void add(Statistic other) {

			}
		};
	}

	@Test
	public void testBelowThreshold() {
		AlertState<Integer> state = createState();
		Assert.assertFalse(state.isActive());
		Assert.assertEquals(0, callCount.get());
		state.accept(createStat(13), getNextInstant());
		Assert.assertEquals(0, callCount.get());
		state.accept(createStat(25), getNextInstant());
		Assert.assertEquals(0, callCount.get());
		Assert.assertFalse(state.isActive());
	}

	@Test
	public void testRaise() {
		AlertState<Integer> state = createState();
		Assert.assertEquals(0, callCount.get());
		state.accept(createStat(13), getNextInstant());
		Assert.assertEquals(0, callCount.get());
		Assert.assertFalse(state.isActive());
		state.accept(createStat(43), getNextInstant());
		Assert.assertEquals(1, callCount.get());
		Assert.assertTrue(state.isActive());
	}

	@Test
	public void testRelease() {
		AlertState<Integer> state = createState();
		state.accept(createStat(43), getNextInstant());
		Assert.assertEquals(1, callCount.get());
		Assert.assertTrue(state.isActive());
		state.accept(createStat(52), getNextInstant());
		Assert.assertEquals(1, callCount.get());
		Assert.assertTrue(state.isActive());
		state.accept(createStat(20), getNextInstant());
		Assert.assertEquals(2, callCount.get());
		Assert.assertFalse(state.isActive());
	}

	@Test
	public void testRaiseAndRelease() {
		AlertState<Integer> state = createState();
		state.accept(createStat(13), getNextInstant());
		Assert.assertEquals(0, callCount.get());
		Assert.assertFalse(state.isActive());
		state.accept(createStat(43), getNextInstant());
		Assert.assertEquals(1, callCount.get());
		Assert.assertTrue(state.isActive());
		state.accept(createStat(52), getNextInstant());
		Assert.assertEquals(1, callCount.get());
		Assert.assertTrue(state.isActive());
		state.accept(createStat(20), getNextInstant());
		Assert.assertEquals(2, callCount.get());
		Assert.assertFalse(state.isActive());
	}

}
