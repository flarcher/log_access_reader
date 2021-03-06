/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package flarcher.log.access;

import java.time.Duration;
import java.time.format.DateTimeParseException;

public interface DurationConverter {

	static String toString(Duration duration) {
		return duration.toString().substring(2).toLowerCase();
	}

	static Duration fromString(String str) {
		try {
			return Duration.parse("PT" + str.trim().toUpperCase());
		} catch (@SuppressWarnings("unused") DateTimeParseException dte) {
			return null;
		}
	}

}
