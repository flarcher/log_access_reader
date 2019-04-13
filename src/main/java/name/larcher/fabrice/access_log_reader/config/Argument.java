/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.access_log_reader.config;

import java.nio.file.*;

/**
 * All configuration arguments for the program.
 */
public enum Argument {

	CONFIGURATION_FILE_LOCATION("CONFIG_FILE", 'c') {

		@Override
		String getDefaultValue() {
			return Paths.get(getTmpDirectory(), "lnc.properties").toAbsolutePath().toString();
		}

		@Override
		boolean isValid(String value) {
			return canRead(value);
		}
	},

	ACCESS_LOG_FILE_LOCATION("LOG_FILE", 'f') {

		@Override
		String getDefaultValue() {
			return Paths.get(getTmpDirectory(), "access.log").toAbsolutePath().toString();
		}

		@Override
		boolean isValid(String value) {
			return canRead(value);
		}
	},

	;

	private static String getTmpDirectory() {
		return System.getProperty("java.io.tmpdir");
	}

	private static boolean canRead(String filePath) {
		try {
			Path path = Paths.get(filePath);
			return Files.isRegularFile(path) && Files.isReadable(path);
		} catch (InvalidPathException ipe) {
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

	abstract String getDefaultValue();

	abstract boolean isValid(String value);
}
