/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package flarcher.log.access.read;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Single-threaded task that reads the access log file.
 */
public class AccessLogReadTask implements Runnable {

	/**
	 * @param listeners         Listeners called each time a new line has been parsed. They are called in the reader's
	 *                          thread, so they can have a significant impact on the reading throughput.
	 * @param parser			Parser function
	 * @param accessLogFilePath The path of the access log file.
	 * @param idleWaitMillis    Minimum milliseconds count spent when waiting for new lines (only in case when the
	 *                          reader reached the last line).
	 */
	public AccessLogReadTask(
			List<Consumer<AccessLogLine>> listeners,
			Function<String, AccessLogLine> parser,
			Path accessLogFilePath,
			Runnable isWaiting,
			long idleWaitMillis) {
		this.listeners = Collections.unmodifiableList(listeners);
		this.accessLogFilePath = Objects.requireNonNull(accessLogFilePath);
		this.idleWaitMillis = idleWaitMillis;
		this.parser = Objects.requireNonNull(parser);
		this.isWaiting = Objects.requireNonNull(isWaiting);
	}

	private final Function<String, AccessLogLine> parser;
	private final long idleWaitMillis;
	private final List<Consumer<AccessLogLine>> listeners;
	private final Path accessLogFilePath;
	private final AtomicBoolean running = new AtomicBoolean(false); // Will be updated concurrently
	private final Runnable isWaiting;

	@Override
	public void run() {
		Thread currentThread = Thread.currentThread();
		currentThread.setName("Reader"); // Quite convenient when debugging :P
		BufferedReader reader;
		try {
			reader = Files.newBufferedReader(accessLogFilePath, StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open " + accessLogFilePath, e);
		}
		running.set(true); // Let's go!
		try {
			while (running.get()) {
				String line;
				try {
					while ((line = reader.readLine()) != null) {
						AccessLogLine parsed = parser.apply(line);
						if (parsed != null) { // Garbage or blank line ?
							// Note: listeners are called from this thread,
							// so their implementations have a big impact on the throughput
							listeners.forEach(listener -> listener.accept(parsed));
						}
						// We need to check after some reading because it can happen that the process is late
						// and do not get into the wait until a long time
						if (!running.get()) {
							return;
						}
						// We should detect thread interruption in this processing part also
						if (currentThread.isInterrupted()) {
							running.set(false);
							return; // No need for an exception
						}
					}
				} catch (IOException e) {
					running.set(false);
					throw new IllegalStateException("Error while reading " + accessLogFilePath, e);
				}

				// We processed all incoming input and should wait for the next lines
				try {
					isWaiting.run();
					Thread.sleep(idleWaitMillis);
				} catch (InterruptedException e) {
					currentThread.interrupt(); // In case it came from anywhere else
					running.set(false);
					break; // No need for an exception
				}
			}
		}
		finally {
			assert !running.get(); // Invariant
			try {
				reader.close();
			}
			catch (IOException e) {
				//TODO: add a warning
				//throw new IllegalStateException("Unable to close " + accessLogFilePath, e);
			}
		}
	}

	/**
	 * Stops the reading.
	 * The effect is not immediate and the delay can be at least of {@link #idleWaitMillis} plus the sum of listeners
	 * processing times.
	 */
	public void requestStop() {
		running.set(false);
	}

	public boolean isRunning() {
		return running.get();
	}
}
