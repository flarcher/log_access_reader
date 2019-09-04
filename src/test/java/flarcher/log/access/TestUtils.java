/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package flarcher.log.access;

import org.junit.Assert;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface TestUtils {

	static Path getTestResourcePath(String fileName) {
		try {
			return Paths.get(ClassLoader.getSystemResource(fileName).toURI());
		} catch (URISyntaxException e) {
			Assert.fail(e.getMessage());
			return null;
		}
	}
}
