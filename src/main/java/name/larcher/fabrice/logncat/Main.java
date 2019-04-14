/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */
package name.larcher.fabrice.logncat;


import name.larcher.fabrice.logncat.config.Argument;
import name.larcher.fabrice.logncat.config.Configuration;
import name.larcher.fabrice.logncat.read.AccessLogParser;
import name.larcher.fabrice.logncat.read.AccessLogReader;

import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Program entry point.
 */
public class Main {

	private static void printHelp() {
		PrintStream printer = System.out;
		printer.println("LOG'n CAT \uD83D\uDC31");
		printer.println(" Prints statistics and notifies alerts by reading access log files.");
		printer.println();
		printer.println("Possible arguments are:");
		printer.println();
		Arrays.stream(Argument.values())
				.sorted(Comparator.comparing(Argument::getPropertyName))
				.forEach( arg -> {

			String name = arg.name().replaceAll("_", " ").toLowerCase();
			printer.println("-" + arg.getCommandOption() + " <" + name + ">");

			printer.println("  " + arg.getDescription());

			printer.println("  Can be set using the environment variable " + arg.getEnvironmentParameter());

			printer.println("  Can be set as the property " + arg.getPropertyName() + " in the configuration file");

			printer.println("  The default value is «" + arg.getDefaultValue() + "»");

			printer.println();
		});
	}

	public static void main(String[] args) {

		List<String> arguments = Arrays.asList(args);
		if (arguments.contains("-h") || arguments.contains("--help")) {
			printHelp();
			System.exit(0);
		}

		// Read the configuration
		Configuration configuration = new Configuration(arguments);

		// Initializing tasks and listeners
		// TODO: Implement stats/alerts
		AccessLogReader reader = new AccessLogReader(
				Collections.singletonList(line -> System.out.println("hit")),
				new AccessLogParser(configuration.getArgument(Argument.DATE_TIME_FORMAT)),
				Paths.get(configuration.getArgument(Argument.ACCESS_LOG_FILE_LOCATION)),
				Long.valueOf(configuration.getArgument(Argument.READ_IDLE_MILLIS)));

		// Starting the engine...
		Thread.UncaughtExceptionHandler ueh = (Thread t, Throwable e) -> {
			// TODO: use some logging
			System.err.println("Error from thread " + t.getName() + ": " + e.getMessage());
			e.printStackTrace(System.err);
		};
		ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r);
				t.setUncaughtExceptionHandler(ueh);
				return t;
			});
		try {
			executorService.submit(reader);
			long mainIdleMillis = Long.valueOf(configuration.getArgument(Argument.MAIN_IDLE_MILLIS));
			while(true) {
				Thread.sleep(mainIdleMillis);
			}
		}
		catch (InterruptedException e) {
			reader.requestStop();
			awaitTermination(executorService, false);
		} catch (Throwable t) {
			// Robustness
			reader.requestStop();
			t.printStackTrace(System.err); // TODO: use some logging
			awaitTermination(executorService, true);
		}
	}

	private static void awaitTermination(ExecutorService executorService, boolean force) {
		if (!executorService.isTerminated()) {
			if (executorService.isShutdown() && force) {
				executorService.shutdownNow();
			} else {
				try {
					executorService.shutdown();
					executorService.awaitTermination(3, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					executorService.shutdownNow();
				}
			}
		}
	}
}