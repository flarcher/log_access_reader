/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package flarcher.log.access.config;

import flarcher.log.access.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ConfigurationTest {

	@Test
	public void commandLineParse() {
		char flag = 'f';
		String value = "/home/toto/myaccesses.log";
		Argument argument = Argument.ACCESS_LOG_FILE_LOCATION;
		Configuration configuration = new Configuration(
			Arrays.asList("-" + flag, value),
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
				Arrays.asList("-c", TEST_PROPERTY_FILE_PATH),
				false,
				null);
		Assert.assertEquals(TEST_PROPERTY_FILE_VALUE, configuration.getArgument(TEST_PROPERTY_FILE_KEY));
	}

	@Test
	public void configurationFileRead_byDefault() {
		Configuration configuration = new Configuration(Collections.emptyList(), false, TEST_PROPERTY_FILE_PATH);
		Assert.assertEquals(TEST_PROPERTY_FILE_VALUE, configuration.getArgument(TEST_PROPERTY_FILE_KEY));
	}

	@Test
	public void defaultRetrieval() {
		Configuration configuration = new Configuration(Collections.emptyList(), false, null);
		for (Argument arg : Argument.values()) {
			String defaultValue = arg.getDefaultValue();
			Assert.assertNotNull(defaultValue);
			Assert.assertEquals(defaultValue, configuration.getArgument(arg));
		}
	}

	@Test
	public void noCommandFlagConflict() {
		Argument[] values = Argument.values();
		Assert.assertEquals(values.length, Arrays.stream(values)
				.map(Argument::getCommandOption)
				.distinct()
				.count());
	}

	@Test
	public void noEnvParamConflict() {
		Argument[] values = Argument.values();
		Assert.assertEquals(values.length, Arrays.stream(values)
				.map(Argument::getEnvironmentParameter)
				.distinct()
				.count());
	}

	@Test
	public void noPropertyNameConflict() {
		Argument[] values = Argument.values();
		Assert.assertEquals(values.length, Arrays.stream(values)
				.map(Argument::getPropertyName)
				.distinct()
				.count());
	}
}
