/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.alert;

import name.larcher.fabrice.logncat.DurationConverter;
import name.larcher.fabrice.logncat.Main;
import name.larcher.fabrice.logncat.TestUtils;
import name.larcher.fabrice.logncat.config.Argument;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainAlertingTest {

	private static final Path TEST_LOG_FILE_PATH = Objects.requireNonNull(
			TestUtils.getTestResourcePath("access_more.log"));

	@BeforeClass
	public static void createAccessLogFile() {
		try {
			accessLogFile = File.createTempFile("access_input_", ".log");
			accessLogFile.deleteOnExit();
			Files.copy(TEST_LOG_FILE_PATH, accessLogFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace(System.err);
			Assert.fail();
		}
	}

	private static File accessLogFile;

	@Before
	public void createAlertLogFile() {
		try {
			alertLogFile = File.createTempFile( "access_alerts_", ".log");
			alertLogFile.deleteOnExit();
		}
		catch (IOException e) {
			e.printStackTrace(System.err);
			Assert.fail();
		}
	}

	private File alertLogFile;

	/**
	 * Test alert sendings.
	 *
	 * @param threshold Throughput threshold in requests per second.
	 * @param duration  Alerting duration.
	 * @param alerts    Expected alert states. {@link true} means that the alert is raised and {@link false} that the alert is released.
	 */
	private void executeAlertingtest(int threshold, Duration duration, boolean[] alerts) {

		int expectedEventCount = alerts.length;

		Main main = new Main();
		CountDownLatch cdl = new CountDownLatch(expectedEventCount);
		ConcurrentLinkedQueue<AlertEvent<?>> alertQueue = new ConcurrentLinkedQueue<>();
		Executors.newSingleThreadScheduledExecutor().submit(() -> {
				main.run(new String[]{
						"-" + Argument.ACCESS_LOG_FILE_LOCATION.getCommandOption(), accessLogFile.getAbsolutePath(),
						"-" + Argument.ALERT_LOAD_THRESHOLD.getCommandOption(), Integer.toString(threshold),
						"-" + Argument.ALERTING_DURATION.getCommandOption(), DurationConverter.toString(duration),
						"-" + Argument.ALERTS_FILE.getCommandOption(), alertLogFile.getAbsolutePath()
				}, alert -> {
					cdl.countDown();
					alertQueue.add(alert);
				},
				false);
			});

		try {
			cdl.await(10, TimeUnit.SECONDS);
			main.stop();
		}
		catch (InterruptedException e) {
			Assert.fail();
		}

		Assert.assertEquals(expectedEventCount, alertQueue.size());
		Iterator<AlertEvent<?>> alertEventIterator = alertQueue.iterator();
		for (int i = 0; i < expectedEventCount; i++) {
			Assert.assertEquals(alerts[i], alertEventIterator.next().isRaised());
		}

		try (BufferedReader reader = Files.newBufferedReader(alertLogFile.toPath())) {
			Assert.assertEquals(expectedEventCount, reader.lines().count());
		}
		catch (IOException e) {
			e.printStackTrace(System.err);
			Assert.fail();
		}
	}

	private static final Duration OVER_ONE_SECOND = Duration.ofSeconds(1);

	/*  The request count per seconds is never reaching 6, so no alert should be raised */
	@Test
	public void noRaise() {
		executeAlertingtest(6, OVER_ONE_SECOND, new boolean[]{});
	}

	/* The request count per seconds reaches 4, but never goes below, so an alert should be raised, but no release event */
	@Test
	public void singleRaise() {
		executeAlertingtest(4, OVER_ONE_SECOND, new boolean[]{ true });
	}

	/* The request count per seconds reaches 2 and then goes below, so two alert events (one raise, one release) are expected */
	@Test
	public void raiseAndRelease() {
		executeAlertingtest(2, OVER_ONE_SECOND, new boolean[]{ true, false });
	}
}
