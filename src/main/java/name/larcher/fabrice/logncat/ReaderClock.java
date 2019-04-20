/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The {@link Clock} of this application.
 * <p>It goes forward in time depending on both the reading of the access log files and the time advance.</p>
 * <ul>
 * <li>The time would start at the lastInstant of the first read access log line. As long as no input is provided, the returned
 * {@link Instant} of {@link #instant()} will be {@code null}.</li>
 * <li>Then the time advances as usual except when an access log line is more recent; in this case, the time will be set
 * to this access log line's lastInstant.
 * We avoid of being late, so that latest statistics and alerts have always a meaning.</li>
 * </ul>
 */
public class ReaderClock extends Clock {

	/**
	 * @param timeZone Clock's single timezone. Please note that {@link #withZone(ZoneId)} is not implemented.
	 * @param period The period of time between 2 calls to {@link #instant()}.
	 * @param readerTempo Gives the reader's last time bound input in order to have the tempo of the reader.
	 */
	public ReaderClock(
			ZoneId timeZone,
			Duration period,
			Supplier<? extends TimeBound> readerTempo) {
		this.timeZone = Objects.requireNonNull(timeZone);
		this.period = Objects.requireNonNull(period);
		this.readerTempo = Objects.requireNonNull(readerTempo);
	}

	private final ZoneId timeZone;
	private final Duration period;
	private final Supplier<? extends TimeBound> readerTempo;

	@Override
	public ZoneId getZone() {
		return timeZone;
	}

	/**
	 * Not implemented.
	 * @throws UnsupportedOperationException Always.
	 */
	@Override
	public Clock withZone(ZoneId zone) {
		// No need of this method: We stay in the provided time zone.
		throw new UnsupportedOperationException();
	}

	private Instant lastInstant = null;

	@Override
	public Instant instant() {
		TimeBound latestTimeReceived = readerTempo.get();
		if (latestTimeReceived == null) {  // Can be null without traffic
			// Waiting for input -> no lastInstant defined yet
			return null;
		}
		else {
			long lastEntryTimestamp = latestTimeReceived.getTimeInMillis();
			if (lastInstant == null) {
				lastInstant = Instant.ofEpochMilli(lastEntryTimestamp);
			} else {
				long plusDelayMillis = lastInstant.toEpochMilli() + period.toMillis();
				lastInstant = Instant.ofEpochMilli(Math.max(lastEntryTimestamp, plusDelayMillis));
			}
		}
		return lastInstant;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;
		ReaderClock that = (ReaderClock) o;
		return timeZone.equals(that.timeZone) &&
				period.equals(that.period);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), timeZone, period);
	}
}
