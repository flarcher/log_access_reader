/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package flarcher.log.access.read;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalQueries;
import java.util.Locale;
import java.util.function.Function;

/**
 * Parses an access log line.
 */
@Immutable
public class AccessLogParser implements Function<String, AccessLogLine> {

	/**
	 * TODO: be able to interpret date time format values of the access log configuration variable LogFileDateExt.
	 *
	 * @param dateTimeFormat A {@link DateTimeFormatter} compatible format. The {@literal LogFileDateExt} access log
	 *                       configuration can define any date-time format, but it uses another syntax
	 *                       that is compliant with the convention of the {@code strftime()} function.
	 */
	public AccessLogParser(String dateTimeFormat) {
		this.dateTimeFormatter = DateTimeFormatter.ofPattern(
				dateTimeFormat, // Comes from the access log configuration
				Locale.ENGLISH  // Is always english for access logs
			);
	}

	private final DateTimeFormatter dateTimeFormatter;

	@Nullable
	private Instant getInstant(String dateStr) {
		// - The date-time strings might refer to localized temporal items (like months).
		// - The date-time strings might refer to calendar relative items like day in month, month, year, ...
		// - The date-time strings might refer to a time zone (as an offset or an ID)
		// => We should use a calendar based parsing
		//	(would be slower than simply reading timestamp counts)
		try {
			LocalDate date = dateTimeFormatter.parse(dateStr, TemporalQueries.localDate());
			LocalTime time = dateTimeFormatter.parse(dateStr, TemporalQueries.localTime());
			ZoneOffset offset = dateTimeFormatter.parse(dateStr, TemporalQueries.offset());
			LocalDateTime ldt = LocalDateTime.of(date, time);
			return ldt.toInstant(offset);
		} catch (@SuppressWarnings("unused") DateTimeParseException dte) {
			return null;
		}
	}

	private static final String UNKNOWN_SECTION = "";

	@Nullable
	private static String getSection(String line, int startIndex) {
		if (line.length() == startIndex) {
			return null;
		}
		int nextIndex = line.indexOf('"', startIndex);
		if (nextIndex < 0 || line.length() == nextIndex + 1) {
			return null;
		}
		// In between lies the HTTP method
		nextIndex = line.indexOf(' ', nextIndex + 1);
		if (nextIndex < 0 || line.length() == nextIndex + 1) {
			return null;
		}
		// We do need the first slash (robustness)
		if (line.charAt(nextIndex + 1) == '/') {
			nextIndex++;
		}
		int previousIndex = nextIndex + 1;
		int nextIndexSlash = line.indexOf('/', nextIndex + 1);
		int nextIndexSpace = line.indexOf(' ', nextIndex + 1);
		if (nextIndexSlash < 0) {
			nextIndex = nextIndexSpace;
		}
		else if (nextIndexSpace < 0) {
			nextIndex = nextIndexSlash;
		}
		else {
			nextIndex = Math.min(nextIndexSlash, nextIndexSpace);
		}
		if (nextIndex < 0) {
			return null;
		}
		return line.substring(previousIndex, nextIndex);
	}

	private int getLength(String line) {
		int lastSpaceIndex = line.lastIndexOf(' ');
		String byteCountStr = line.substring(lastSpaceIndex + 1);
		try {
			return Integer.parseUnsignedInt(byteCountStr);
		}
		catch (NumberFormatException e) {
			return -1; // A negative result means a parsing error
		}
	}

	// Input example: 127.0.0.1 - frank [09/May/2018:16:00:42 +0000] "POST /api/user HTTP/1.0" 200 34
	@Nullable
	@Override
	public AccessLogLine apply(String line) {

		// Index-based parsing is usually faster than the use of a "big" regexp
		// Furthermore, we do not create "lots of strings" and reuse the input string as much as possible

		int dateStartIndex = line.indexOf('[');
		if (dateStartIndex < 0 || line.length() < dateStartIndex + 1) {
			return null;
		}
		dateStartIndex++;
		int dateEndIndex = line.indexOf(']', dateStartIndex);
		if (dateEndIndex < 0) {
			return null;
		}

		String dateStr = line.substring(dateStartIndex, dateEndIndex);
		Instant instant = getInstant(dateStr);
		if (instant == null) {
			// Invalid date-time format
			// Without a timestamp, we can not go further
			return null;
		}

		String section = getSection(line, dateEndIndex + 1);
		if (section == null) {
			// Even if the rest of the line is malformed, we consider the request for an unknown section
			section = UNKNOWN_SECTION;
		}

		int length = getLength(line);
		return new AccessLogLine(instant, section, length);
	}
}
