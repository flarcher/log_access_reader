/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.access_log_reader.config;

import java.nio.file.*;
import java.time.format.DateTimeFormatter;

/**
 * Configuration argument for the program.
 */
public enum Argument {

	CONFIGURATION_FILE_LOCATION("CONFIG_FILE", 'c') {

		@Override
		public String getDefaultValue() {
			return Paths.get(getTmpDirectory(), "lnc.properties").toAbsolutePath().toString();
		}

		@Override
		boolean isValid(String value) {
			// We check only that the path is well-formed
			// If the files does not exist, then it is ignored and no other configuration will be loaded
			return getPath(value) != null;
		}
	},

	ACCESS_LOG_FILE_LOCATION("LOG_FILE", 'f') {

		@Override
		public String getDefaultValue() {
			return Paths.get(getTmpDirectory(), "access.log").toAbsolutePath().toString();
		}

		@Override
		boolean isValid(String value) {
			return canRead(value);
		}
	},

	READ_IDLE_MILLIS("READ_IDLE_TIME", 'w') {

		@Override
		public String getDefaultValue() {
			return String.valueOf(10L);
		}

		@Override
		boolean isValid(String value) {
			return isPositiveLong(value);
		}
	},

	MAIN_IDLE_MILLIS("MAIN_IDLE_TIME", 'm') {

		@Override
		public String getDefaultValue() {
			return String.valueOf(100L);
		}

		@Override
		boolean isValid(String value) {
			return isPositiveLong(value);
		}
	},

	/**
	 * The value must be compliant with {@link DateTimeFormatter}.
	 * Is not the value of access log configuration {@literal LogFileDateExt}.
	 */
	DATE_TIME_FORMAT("DATE_TIME", 'd') {

		@Override
		public String getDefaultValue() {
			return "dd/MMM/yyyy:HH:mm:ss Z";
		}

		@Override
		boolean isValid(String value) {
			try {
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern(value);
				// We need both the date and the time
				// TODO: check that the format includes the whole date-time information
				return true;
			}
			catch (@SuppressWarnings("unused") IllegalArgumentException e) {
				return false;
			}
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

	private static boolean canRead(String filePath) {
		Path path = getPath(filePath);
		return path != null && Files.isRegularFile(path) && Files.isReadable(path);
	}

	private static boolean isPositiveLong(String longStr) {
		try {
			return Long.valueOf(longStr) > 0;
		}
		catch (@SuppressWarnings("unused") NumberFormatException e) {
			return false;
		}
	}

	/**
	 * @param propertyName The property name used both for environment variables names and configuration file property names.
	 * @param commandOption The command option flag.
	 */
	Argument(String propertyName, char commandOption) {
		this.propertyName = propertyName;
		this.commandOption = commandOption;
	}

	private final String propertyName;
	private final char commandOption;

	String getPropertyName() {
		return propertyName.replaceAll("_", "\\.").toLowerCase();
	}

	String getEnvironmentParameter() {
		return "LNC_" + propertyName.toUpperCase();
	}

	char getCommandOption() {
		return commandOption;
	}

	public abstract String getDefaultValue();

	abstract boolean isValid(String value);
}
