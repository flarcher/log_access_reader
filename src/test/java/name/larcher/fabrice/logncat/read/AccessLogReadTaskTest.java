/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.read;

import name.larcher.fabrice.logncat.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class AccessLogReadTaskTest {

	private static final Path TEST_LOG_FILE_PATH = TestUtils.getTestResourcePath("access.log");
	private static final int TEST_LOG_FILE_LINE_COUNT = 4;
	private static final Function<String, AccessLogLine> PARSER = line -> new AccessLogLine(Instant.now(), "", 0);

	@Test
	public void canReadExample() {
		CountDownLatch countDownLatch = new CountDownLatch(TEST_LOG_FILE_LINE_COUNT);
		AccessLogReadTask reader = new AccessLogReadTask(
				Collections.singletonList(line -> countDownLatch.countDown()),
				PARSER,
				TEST_LOG_FILE_PATH,
				100L);
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		Assert.assertFalse(reader.isRunning());
		executorService.submit(reader);
		try {
			Assert.assertTrue(countDownLatch.await(2, TimeUnit.SECONDS));
		} catch (InterruptedException e) {
			Assert.fail();
		}
		Assert.assertTrue(reader.isRunning());
		reader.requestStop();
		Assert.assertFalse(reader.isRunning());
		try {
			executorService.shutdown();
			Assert.assertTrue(executorService.awaitTermination(1, TimeUnit.SECONDS));
		} catch (InterruptedException e) {
			Assert.fail();
		}
	}

	@Test
	public void canInterrupt() {
		Assert.assertTrue(TEST_LOG_FILE_LINE_COUNT > 2); // Prerequisite
		CountDownLatch countDownLatch = new CountDownLatch(2);
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		AtomicInteger counter = new AtomicInteger(0);
		long idleWaitMillis = 100L;
		AccessLogReadTask reader = new AccessLogReadTask(
				Collections.singletonList(line -> {
					if (counter.getAndIncrement() > 0) {
						// The current thread is the reader's thread
						executorService.shutdownNow();
					}
					countDownLatch.countDown();
				}),
				PARSER,
				TEST_LOG_FILE_PATH,
				idleWaitMillis);
		Assert.assertFalse(reader.isRunning());
		executorService.submit(reader);
		try {
			Assert.assertTrue(countDownLatch.await(2, TimeUnit.SECONDS));
			Thread.sleep(idleWaitMillis * 2);
		} catch (InterruptedException e) {
			Assert.fail();
		}
		Assert.assertFalse(reader.isRunning());
		Assert.assertTrue(counter.get() < TEST_LOG_FILE_LINE_COUNT);
		Assert.assertEquals(2, counter.get()); // Not 4
		try {
			executorService.shutdown();
			Assert.assertTrue(executorService.awaitTermination(1, TimeUnit.SECONDS));
		} catch (InterruptedException e) {
			Assert.fail();
		}
	}
}
