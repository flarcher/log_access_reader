/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.read;

import javax.annotation.concurrent.Immutable;
import java.time.Instant;
import java.util.Objects;

/**
 * Holds meta data of an access log line.
 * Like {@code 127.0.0.1 - frank [09/May/2018:16:00:42 +0000] "POST /api/user HTTP/1.0" 200 34}.
 */
@Immutable
public class AccessLogLine {

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
