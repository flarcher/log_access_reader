/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.config;

import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class DurationConverterTest {

	@Test
	public void convertionConsistency() {
		Duration duration = Duration
				.of(2, ChronoUnit.HOURS)
				.plus(34, ChronoUnit.MINUTES)
				.plus(5, ChronoUnit.SECONDS);
		String s = DurationConverter.toString(duration);
		Assert.assertEquals("2h34m5s", s);
		Duration parsedDuration = DurationConverter.fromString(s);
		Assert.assertEquals(duration, parsedDuration);
	}

}
