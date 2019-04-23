/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.display;

import name.larcher.fabrice.logncat.alert.AlertEvent;
import name.larcher.fabrice.logncat.DurationConverter;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Printing utilities.
 */
public final class Printer {

	public Printer(ZoneId timeZone) {
		this.timeZone = Objects.requireNonNull(timeZone);
	}

	private final ZoneId timeZone;

	static List<String> printBeforeRun(Duration refreshPeriodMillis) {
		return Arrays.asList(
				" You started LOG'n-CAT «^·_·^»", //\uD83D\uDC31",
				" Stats will be printed each " + DurationConverter.toString(refreshPeriodMillis),
				" You can quit by pressing <Escape> or 'q'");
	}

	private static String getSI(long value) {
		long divided;
		char suffix;
		if (value > 1_000_000_000_000L) {
			divided = value / 1_000_000_000_000L;
			suffix = 'T';
		}
		else if (value > 1_000_000_000L) {
			divided = value / 1_000_000_000L;
			suffix = 'G';
		}
		else if (value > 1_000_000L) {
			divided = value / 1_000_000L;
			suffix = 'M';
		}
		else if (value > 1_000L) {
			divided = value / 1_000L;
			suffix = 'K';
		}
		else {
			divided = value;
			suffix = ' ';
		}
		return Long.toString(divided) + suffix;
	}

	private static final int MAX_DECIMAL_COUNT = 2;

	private static String getRatio(long value, long by) {
		double ratio = ((double) value) / by;
		if (ratio < 1_000D) {
			String str = Double.toString(ratio);
			int commaIndex = str.indexOf('.');
			if (commaIndex > 0) {
				int decimalPartLength = str.length() - commaIndex - 1;
				if (decimalPartLength > MAX_DECIMAL_COUNT) {
					str = str.substring(0, str.length() - (decimalPartLength) + MAX_DECIMAL_COUNT);
				}
			}
			return str;
		}
		else {
			return getSI((long) ratio);
		}
	}

	static String getValueWithRatio(long value, Duration duration) {
		return getSI(value) + " (" + getRatio(value, duration.getSeconds()) + "/s)";
	}

	String formatInstant(Instant instant) {
		return DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.ofInstant(instant, timeZone));
	}

	String printAlert(AlertEvent<?> event) {
		String str =
			// Date
			"[" + formatInstant(event.isRaised() ? event.getSince() : event.getUntil()) + "]" +
			// Type
			(event.isRaised() ? "! RAISED ! " : "!RELEASED! ") +
			// “High traffic generated an alert
			"\"" + event.getConfig().getDescription() + "\" " +
			//  - hits = {value}"
			"hits = {" + event.getValueAtSince().toString() + "}";
		if (event.isReleased()) {
			// , since {since}”
			str += " since [" + formatInstant(event.getSince()) + "]";
		}
		return str;
	}
}
