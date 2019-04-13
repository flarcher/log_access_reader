/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.access_log_reader.config;

import name.larcher.fabrice.access_log_reader.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;

public class ConfigurationTest {

	@Test
	public void commandLineParse() {
		char flag = 'f';
		String value = "/home/toto/myaccesses.log";
		Argument argument = Argument.ACCESS_LOG_FILE_LOCATION;
		Configuration configuration = new Configuration(
			Arrays.asList("-" + flag, value).toArray(new String[0]),
			false,
			null);
		Assert.assertEquals(flag, argument.getCommandOption());
		Assert.assertNotEquals(value, argument.getDefaultValue());
		Assert.assertEquals(value, configuration.getArgument(argument));
	}

	/*@Test
	public void environmentVariableRead() {
		// How to set an environment variable ? (impossible from the application?)
	}*/

	private static final String TEST_PROPERTY_FILE_VALUE = "/home/toto/anyaccessfile.log";
	private static final Argument TEST_PROPERTY_FILE_KEY = Argument.ACCESS_LOG_FILE_LOCATION;
	private static final String TEST_PROPERTY_FILE_PATH = TestUtils.getTestResourcePath("test.properties").toString();

	@Test
	public void configurationFileRead_commandArgument() {
		Configuration configuration = new Configuration(
				Arrays.asList("-c", TEST_PROPERTY_FILE_PATH).toArray(new String[0]),
				false,
				null);
		Assert.assertEquals(TEST_PROPERTY_FILE_VALUE, configuration.getArgument(TEST_PROPERTY_FILE_KEY));
	}

	@Test
	public void configurationFileRead_byDefault() {
		Configuration configuration = new Configuration(new String[0], false, TEST_PROPERTY_FILE_PATH);
		Assert.assertEquals(TEST_PROPERTY_FILE_VALUE, configuration.getArgument(TEST_PROPERTY_FILE_KEY));
	}

	@Test
	public void defaultRetrieval() {
		Configuration configuration = new Configuration(new String[0], false, null);
		for (Argument arg : Argument.values()) {
			Assert.assertEquals(arg.getDefaultValue(), configuration.getArgument(arg));
		}
	}
}