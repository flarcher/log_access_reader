/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package flarcher.log.access.read;

import flarcher.log.access.TimeBound;

import javax.annotation.concurrent.Immutable;
import java.time.Instant;
import java.util.Objects;

/**
 * Holds meta data of an access log line.
 */
@Immutable
public class AccessLogLine implements TimeBound {

	public AccessLogLine(Instant instant, String section, int length) {
		this.instant = instant;
		this.section = section;
		this.length = length;
	}

	private final Instant instant;
	private final String section;
	private final int length;

	public Instant getInstant() {
		return instant;
	}

	public String getSection() {
		return section;
	}

	/**
	 * @return Content length as a byte count.
	 */
	public int getLength() {
		return length;
	}

	@Override
	public long getTimeInMillis() {
		return instant.toEpochMilli();
	}

	/*-- Generated equals+hashCode
	 * TODO / Note:
	 * To be fixed since the section is not the full URL and the instant precision is not enough.
	 * Indeed, several requests might come in the same second for the same section
	 */

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
