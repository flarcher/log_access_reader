/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.config;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
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

	private static Map<String, String> readProperties(String configFileLocation) {
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
		}
		catch (InvalidPathException ipe) {
			throw new IllegalArgumentException("Invalid configuration file path " + configFileLocation, ipe);
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Unable to read properties from file " + configFileLocation, e);
		}
	}

	private static EnumMap<Argument, String> parseCommandLine(List<String> arguments) {
		EnumMap<Argument, String> argsMap = new EnumMap<>(Argument.class);

		Iterator<String> argumentIterator = arguments.iterator();
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
		return argsMap;
	}

	private static final String UNDEFINED_MSG = "Should be defined";

	public Configuration(List<String> arguments) {
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
				List<String> arguments,
				boolean enableValidation,
				@Nullable String defaultConfigFilePath)
			throws IllegalArgumentException {

		// 1st step: we get values from the command arguments
		EnumMap<Argument, String> argsMap = parseCommandLine(arguments);

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
			Argument configFileArgument = Argument.CONFIGURATION_FILE_LOCATION;
			String configFileLocation = argsMap.get(configFileArgument);
			if (configFileLocation == null) {
				if (defaultConfigFilePath == null) {
					defaultConfigFilePath = configFileArgument.getDefaultValue();
				}
				configFileArgument.validate(defaultConfigFilePath).ifPresent(
						str -> { throw new IllegalArgumentException(str);
					});
				// We do not force the default path if the file does not exists
				if (Files.isRegularFile(Paths.get(defaultConfigFilePath))) {
					configFileLocation = defaultConfigFilePath;
					argsMap.put(configFileArgument, defaultConfigFilePath);
				}
			}
			if (configFileLocation != null) {
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
			}
		}

		// 4th step: we use default values
		unresolvedArguments(argsMap)
			.forEach(arg -> argsMap.put(arg, arg.getDefaultValue()));

		// 5th step: We make sure that all argument values are valid
		if (enableValidation) {
			List<String> errorsList = argsMap.entrySet().stream()
					.map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(),
						entry.getValue() == null
							? Optional.of(UNDEFINED_MSG) // Is undefined -> this is an error
							: entry.getKey().validate(entry.getValue()))) // Calls the validate methods
					.filter(entry -> entry.getValue().isPresent()) // If it has an error
					.map(entry ->  // Formatting
							entry.getKey().name() + " (" + entry.getKey().getDescription() + "):\n" +
							entry.getValue().get())
					.collect(Collectors.toList());
			if (!errorsList.isEmpty()) {
				throw new IllegalArgumentException("Invalid argument value(s):\n" +
					String.join("\n", errorsList));
			}
		}

		argumentMap = Collections.unmodifiableMap(argsMap);
	}

	private final Map<Argument, String> argumentMap;

	@Nonnull
	public String getArgument(Argument argument) {
		return argumentMap.get(argument);
	}
}
