/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.read;

import name.larcher.fabrice.logncat.TimeBound;

import javax.annotation.concurrent.Immutable;
import java.time.Instant;
import java.util.Objects;

/**
 * Holds meta data of an access log line.
 */
@Immutable
public class AccessLogLine implements TimeBound {

	public AccessLogLine(Instant instant, String section) {
		this.instant = instant;
		this.section = section;
	}

	private final Instant instant;
	private final String section;

	public Instant getInstant() {
		return instant;
	}

	public String getSection() {
		return section;
	}

	@Override
	public long getTimeInMillis() {
		return instant.toEpochMilli();
	}

	//-- Generated

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		AccessLogLine that = (AccessLogLine) o;
		return instant.equals(that.instant) &&
				section.equals(that.section);
	}

	@Override
	public int hashCode() {
		return Objects.hash(instant, section);
	}
}
