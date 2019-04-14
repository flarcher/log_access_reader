/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.access_log_reader.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Reads the program configuration from multiple sources and store the resulting configuration in memory.
 */
@Immutable
public final class Configuration {

	/**
	 * Contains single character command-option-codes as keys.
	 */
	private static final Map<String, Argument> COMMAND_FLAG_TO_ARGUMENT = Arrays.stream(Argument.values())
		.collect(Collectors.toMap(
			arg -> String.valueOf(arg.getCommandOption()),
			Function.identity()));

	/**
	 * Contains property names as keys.
	 */
	private static final Map<String, Argument> PROPERTY_TO_ARGUMENT = Arrays.stream(Argument.values())
		.collect(Collectors.toMap(
			Argument::getPropertyName,
			Function.identity()));

	private static final Set<Argument> ALL_ARGUMENTS = EnumSet.allOf(Argument.class);

	private static Set<Argument> unresolvedArguments(Map<Argument, String> argsMap) {
		return ALL_ARGUMENTS.stream()
				.filter(arg -> !argsMap.containsKey(arg))
				.collect(Collectors.toSet());
	}

	private static Map<String, String> readProperties(String configFileLocation) throws IOException {
		try {
			Path configFilePath = Paths.get(configFileLocation);
			Properties properties = new Properties();
			try (BufferedReader reader = Files.newBufferedReader(configFilePath,
					StandardCharsets.ISO_8859_1 /* because of the properties format */)) {
				properties.load(reader);
			} // Java7+ auto-closing `try` block
			return properties.stringPropertyNames().stream()
					.collect(Collectors.toMap(
							Function.identity(),
							properties::getProperty,
							(l, r) -> r // Property overriding
					));
		} catch (InvalidPathException ipe) {
			throw new IllegalArgumentException("Invalid configuration file path " + configFileLocation, ipe);
		}
	}

	public Configuration(String[] arguments) {
		this(arguments, true, null);
	}

	/**
	 * After calling this constructor, all arguments gets a known value or some {@link IllegalArgumentException} is thrown.
	 * It can even validate all the values.
	 *
	 * @param arguments The command line arguments.
	 * @param enableValidation Tells if validation should be done.
	 * @param defaultConfigFilePath Configuration file location that overrides the default path.
	 * @throws IllegalArgumentException If any of all argument can not get a value (even a default value) or has an invalid value.
	 */
	public Configuration(
				String[] arguments,
				boolean enableValidation,
				@Nullable String defaultConfigFilePath)
			throws IllegalArgumentException {

		EnumMap<Argument, String> argsMap = new EnumMap<>(Argument.class);

		// 1st step: we get values from the command arguments
		Iterator<String> argumentIterator = Arrays.asList(arguments).iterator();
		while (argumentIterator.hasNext()) {
			final String commandFlag = argumentIterator.next();
			if (commandFlag.length() != 2 /* The single character plus the dash */) {
				throw new IllegalArgumentException("Invalid flag " + commandFlag);
			}

			Argument arg = COMMAND_FLAG_TO_ARGUMENT.get(commandFlag.substring(1));
			if (arg == null) {
				throw new IllegalArgumentException("Unknown flag " + commandFlag);
			}

			if (!argumentIterator.hasNext()) {
				throw new IllegalArgumentException("No value provided for option " + commandFlag);
			}
			else {
				argsMap.put(arg, argumentIterator.next());
			}
		}

		// 2nd step: we get values from the environment variables
		unresolvedArguments(argsMap)
			.forEach(arg -> {
				try {
					String systemEnvValue = System.getenv(arg.getEnvironmentParameter());
					if (systemEnvValue != null) {
						argsMap.put(arg, systemEnvValue.trim());
					}
				} catch (@SuppressWarnings("unused") SecurityException se) {
					// Ignore
				}
			});

		// 3rd step: we load the undefined values from a configuration file (if any)
		Set<Argument> unresolvedArguments = unresolvedArguments(argsMap);
		if (!unresolvedArguments.isEmpty()) {
			String configFileLocation = argsMap.get(Argument.CONFIGURATION_FILE_LOCATION);
			if (configFileLocation == null && defaultConfigFilePath != null) {
				configFileLocation = defaultConfigFilePath;
				argsMap.put(Argument.CONFIGURATION_FILE_LOCATION, defaultConfigFilePath);
			}
			if (configFileLocation != null) {
				try {
					readProperties(configFileLocation)
						.forEach((key, val) -> {
							Argument argument = PROPERTY_TO_ARGUMENT.get(key);
							if (argument == null) {
								throw new IllegalArgumentException("Unknown property name " + key);
							}
							else if (val != null) {
								argsMap.put(argument, val.trim());
							}
						});
				} catch (IOException e) {
					throw new IllegalArgumentException("Unable to read properties from file " + configFileLocation, e);
				}
			}
		}

		// 4th step: we use default values
		unresolvedArguments(argsMap)
			.forEach(arg -> argsMap.put(arg, arg.getDefaultValue()));

		// 5th step: We make sure that all argument values are valid
		if (enableValidation) {
			Map<Argument, String> invalidPairs = argsMap.entrySet().stream()
				.filter(entry -> entry.getValue() == null || !entry.getKey().isValid(entry.getValue()))
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> {
					String argValue = entry.getValue();
					return argValue != null ? argValue : "null";
				}));
			if (!invalidPairs.isEmpty()) {
				throw new IllegalArgumentException("Invalid argument value(s): " +
					invalidPairs.entrySet().stream()
						.map(entry -> entry.getKey().name() + ": " + entry.getValue())
						.collect(Collectors.joining(", ")));
			}
		}

		argumentMap = Collections.unmodifiableMap(argsMap);
	}

	private final Map<Argument, String> argumentMap;

	public String getArgument(Argument argument) {
		return argumentMap.get(argument);
	}
}
