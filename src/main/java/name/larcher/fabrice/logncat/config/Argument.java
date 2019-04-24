/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.config;

import name.larcher.fabrice.logncat.DurationConverter;

import java.nio.file.*;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Configuration argument for the program.
 */
public enum Argument {

	CONFIGURATION_FILE_LOCATION("CONFIG_FILE", 'c',
			"Location of the properties configuration file") {

		@Override
		public String getDefaultValue() {
			return Paths.get(getTmpDirectory(), "lnc.properties").toAbsolutePath().toString();
		}

		@Override
		Optional<String> validate(String value) {
			// We check only that the path is well-formed
			// If the files does not exist, then it is ignored and no other configuration will be loaded
			return Optional.ofNullable(getPath(value) != null ? null : "Invalid path " + value);
		}
	},

	ACCESS_LOG_FILE_LOCATION("LOG_FILE", 'f',
			"Location of the HTTP access log file") {

		@Override
		public String getDefaultValue() {
			return Paths.get(getTmpDirectory(), "access.log").toAbsolutePath().toString();
		}

		@Override
		Optional<String> validate(String value) {
			return canRead(value);
		}
	},

	READ_IDLE_DURATION("READ_IDLE", 'w',
			"Maximum idle time in the access log reading loop") {

		@Override
		public String getDefaultValue() {
			return DurationConverter.toString(Duration.ofMillis(10));
		}

		@Override
		Optional<String> validate(String value) {
			return isDuration(value);
		}
	},

	MINIMUM_DURATION("MINIMUM_DURATION", 'm',
			"Minimum duration of statistics aggregation. " +
			"The shorter it is, the bigger will be the memory comsumption but better will be the statistics precision and the alerts responsiveness.") {

		@Override
		public String getDefaultValue() {
			return DurationConverter.toString(Duration.ofMillis(100));
		}

		@Override
		Optional<String> validate(String value) {
			return isDuration(value);
		}
	},

	/**
	 * The value must be compliant with {@link DateTimeFormatter}.
	 * Is not the value of access log configuration {@literal LogFileDateExt}.
	 */
	DATE_TIME_FORMAT("DATE_TIME_FORMAT", 'd',
			"The access log date-time format described with the Java convention (not in the LogFileDateExt format)") {

		@Override
		public String getDefaultValue() {
			return "dd/MMM/yyyy:HH:mm:ss Z";
		}

		@Override
		Optional<String> validate(String value) {
			try {
				DateTimeFormatter.ofPattern(value);
				// We need both the date and the time
				// TODO: check that the format includes the whole date-time information
				return Optional.empty();
			}
			catch (@SuppressWarnings("unused") IllegalArgumentException e) {
				return Optional.of("Invalid date time pattern '" + value + "'. " +
					"See https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#patterns " +
					"for more information about supported patterns.");
			}
		}

	},

	TOP_SECTION_COUNT("TOP_COUNT", 't',
			"Top sections count for display") {

		@Override
		public String getDefaultValue() {
			return String.valueOf(10);
		}

		@Override
		Optional<String> validate(String value) {
			return isPositiveInteger(value);
		}
	},

	MAX_SECTION_COUNT_RATIO("MAX_COUNT_RATIO", 'r',
			"Maximum section count ratio. The result of this value multiplied with the 'top section count' is the " +
			"maximum count of sections held in memory in statistics (technical limit in order to cap memory usage)") {

		@Override
		public String getDefaultValue() {
			return String.valueOf(10);
		}

		@Override
		Optional<String> validate(String value) {
			return isPositiveInteger(value);
		}
	},

	STATISTICS_LATEST_DURATION("STATS_DURATION", 's',
			"Statistics refresh period in millis") {

		@Override
		public String getDefaultValue() {
			return DurationConverter.toString(Duration.ofSeconds(10));
		}

		@Override
		Optional<String> validate(String value) {
			return isDuration(value);
		}
	},

	DISPLAY_PERIOD_DURATION("DISPLAY_PERIOD_DURATION", 'p',
			"Statistics refresh period in millis") {

		@Override
		public String getDefaultValue() {
			return DurationConverter.toString(Duration.ofSeconds(1));
		}

		@Override
		Optional<String> validate(String value) {
			return isDuration(value);
		}
	},

	ALERTING_DURATION("ALERT_PERIOD", 'a',
			"Time duration over the latest statistics used for checking alerting thresholds") {

		@Override
		public String getDefaultValue() {
			return DurationConverter.toString(Duration.ofMinutes(2));
		}

		@Override
		Optional<String> validate(String value) {
			return isDuration(value);
		}
	},

	ALERT_LOAD_THRESHOLD("ALERT_LOAD_THRESHOLD", 'l',
			"Threshold for raising an alert related to the load. The value is the request count per second.") {

		@Override
		public String getDefaultValue() {
			return Integer.toString(10);
		}

		@Override
		Optional<String> validate(String value) {
			return isPositiveInteger(value);
		}
	},

	TIME_ZONE("TIME_ZONE", 'z',
			"IANA Timezone ID to be used. Uses the system's timezone if not provided.") {

		@Override
		public String getDefaultValue() {
			return ZoneId.systemDefault().getId();
		}

		@Override
		Optional<String> validate(String value) {
			try {
				ZoneId.of(value);
				return Optional.empty();
			}
			catch (@SuppressWarnings("unused") DateTimeException e) {
				return Optional.of("Unknown IANA timezone ID '" + value +"'");
			}
		}
	},

	ALERTS_FILE("ALERTS_FILE", 'o',
			"Location of the alerts file (none by default)") {

		@Override
		public String getDefaultValue() {
			return ""; // Means none
		}

		@Override
		Optional<String> validate(String value) {
			// If empty, we output towards the standard output
			return value.isEmpty() ? Optional.empty() : canRead(value);
		}
	},

	;

	private static String getTmpDirectory() {
		return System.getProperty("java.io.tmpdir");
	}

	private static Path getPath(String filePath) {
		try {
			return Paths.get(filePath);
		} catch (@SuppressWarnings("unused") InvalidPathException ipe) {
			return null;
		}
	}

	private static Optional<String> canRead(String filePath) {
		Path path = getPath(filePath);
		if (path == null) {
			return Optional.of("Invalid path " + filePath);
		}
		if (Files.isDirectory(path)) {
			return Optional.of("A file expected on " + filePath);
		}
		if (!Files.isReadable(path)) {
			return Optional.of("The file " + filePath + " can not be read");
		}
		return Optional.empty();
	}

	private static Optional<String> isPositiveInteger(String intStr) {
		try {
			if (Integer.valueOf(intStr) <= 0) {
				return Optional.of("'" + intStr + "' is not a strictly positive value");
			}
			return Optional.empty();
		}
		catch (@SuppressWarnings("unused") NumberFormatException e) {
			return Optional.of("Invalid number '" + intStr + "'");
		}
	}

	private static Optional<String> isDuration(String durStr) {
		return DurationConverter.fromString(durStr) != null
				? Optional.empty()
				: Optional.of("Invalid duration value '" + durStr + "'. " +
					"Use lowercase letters like 'h', 'm' and 's' for respectively hours, minutes and seconds. " +
					"An example value can be: 2h34m5s");
	}

	/**
	 * @param propertyName The property name used both for environment variables names and configuration file property names.
	 * @param commandOption The command option flag.
	 * @param desc Short description.
	 */
	Argument(String propertyName, char commandOption, String desc) {
		this.propertyName = propertyName;
		this.commandOption = commandOption;
		this.description = desc;
	}

	private final String propertyName;
	private final char commandOption;
	private final String description;

	public String getPropertyName() {
		return propertyName.replaceAll("_", "\\.").toLowerCase();
	}

	public String getEnvironmentParameter() {
		return "LNC_" + propertyName.toUpperCase();
	}

	public char getCommandOption() {
		return commandOption;
	}

	public abstract String getDefaultValue();

	/**
	 * @param value The given argument value.
	 * @return An potential error message.
	 */
	abstract Optional<String> validate(String value);

	public String getDescription() { return description; }
}
