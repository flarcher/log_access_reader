/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package flarcher.log.access.read;

import flarcher.log.access.config.Argument;
import org.junit.Assert;
import org.junit.Test;

public class AccessLogParserTest {

	private static final AccessLogParser PARSER_WITH_DEFAULTS = new AccessLogParser(
			Argument.DATE_TIME_FORMAT.getDefaultValue());

	private void assertLine(String line, long millis, String section, int length) {
		AccessLogLine stat = PARSER_WITH_DEFAULTS.apply(line);
		Assert.assertEquals(millis, stat.getInstant().toEpochMilli());
		Assert.assertEquals(section, stat.getSection());
		Assert.assertEquals(length, stat.getLength());
	}

	@Test
	public void readLine1WithDefault() {
		assertLine("127.0.0.1 - james [09/May/2018:16:00:39 +0000] \"GET /report HTTP/1.0\" 200 123",
				1525881639000L, "report", 123);
	}

	@Test
	public void readLine2WithDefault() {
		assertLine("127.0.0.1 - jill [09/May/2018:16:00:41 +0000] \"GET /api/user HTTP/1.0\" 200 234",
				1525881641000L, "api", 234);
	}

	@Test
	public void readLine3WithDefault() {
		assertLine("127.0.0.1 - frank [09/May/2018:16:00:42 +0000] \"POST /api/user HTTP/1.0\" 200 34",
				1525881642000L, "api", 34);
	}

	@Test
	public void readLine4WithDefault() {
		assertLine("127.0.0.1 - mary [09/May/2018:16:00:42 +0000] \"POST /api/user HTTP/1.0\" 503 12",
				1525881642000L, "api", 12);
	}

	@Test
	public void noSection() {
		assertLine("127.0.0.1 - mary [09/May/2018:16:00:42 +0000]",
				1525881642000L, "", -1);
	}

	@Test
	public void midnightCrossing() {
		AccessLogLine beforeMidnight = PARSER_WITH_DEFAULTS.apply("127.0.0.1 - mary [09/May/2018:23:50:42 +0000] " +
				"\"POST /api/user HTTP/1.0\" 503 12");
		AccessLogLine afterMidnight = PARSER_WITH_DEFAULTS.apply("127.0.0.1 - mary [10/May/2018:00:02:13 +0000] " +
				"\"POST /api/user HTTP/1.0\" 503 12");
		Assert.assertEquals(691000L, afterMidnight.getInstant().toEpochMilli() - beforeMidnight.getInstant().toEpochMilli());
	}

	@Test
	public void timezoneCrossing() {
		AccessLogLine beforeTZChange = PARSER_WITH_DEFAULTS.apply("127.0.0.1 - mary [09/May/2018:23:50:42 +0000] " +
				"\"POST /api/user HTTP/1.0\" 503 12");
		AccessLogLine afterTZChange = PARSER_WITH_DEFAULTS.apply("127.0.0.1 - mary [10/May/2018:01:02:13 +0100] " +
				"\"POST /api/user HTTP/1.0\" 503 12");
		Assert.assertEquals(691000L, afterTZChange.getInstant().toEpochMilli() - beforeTZChange.getInstant().toEpochMilli());
	}
}
