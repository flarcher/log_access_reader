/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */
package name.larcher.fabrice.access_log_reader;

import name.larcher.fabrice.access_log_reader.config.Argument;
import name.larcher.fabrice.access_log_reader.config.Configuration;
import name.larcher.fabrice.access_log_reader.read.AccessLogParser;
import name.larcher.fabrice.access_log_reader.read.AccessLogReader;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Program entry point.
 */
public class Main {

	public static void main(String[] args) {

		// Read the configuration
		Configuration configuration = new Configuration(args);

		// Initializing tasks and listeners

		AccessLogReader reader = new AccessLogReader(
				Collections.singletonList(line -> System.out.println("hit")),
				new AccessLogParser(configuration.getArgument(Argument.DATE_TIME_FORMAT)),
				Paths.get(configuration.getArgument(Argument.ACCESS_LOG_FILE_LOCATION)),
				Long.valueOf(configuration.getArgument(Argument.READ_IDLE_MILLIS)));

		// Starting the engine...
		Thread.UncaughtExceptionHandler ueh = (Thread t, Throwable e) -> {
			// TODO
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
			awaitTermination(executorService, true);
		}
	}

	private static void awaitTermination(ExecutorService executorService, boolean force) {
		if (   executorService.isTerminated()
		   || (executorService.isShutdown() && !force)) {
			return;
		}
		else if (force) {
			executorService.shutdownNow();
		}
		else {
			try {
				executorService.shutdown();
				executorService.awaitTermination(3, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				executorService.shutdownNow();
			}
		}
	}
}