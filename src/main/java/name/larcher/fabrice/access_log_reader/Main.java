/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */
package name.larcher.fabrice.access_log_reader;

import name.larcher.fabrice.access_log_reader.config.Argument;
import name.larcher.fabrice.access_log_reader.config.Configuration;

/**
 * Program entry point.
 */
public class Main {

	public static void main(String[] args) {

		// Read the configuration
		Configuration configuration = new Configuration(args);

	}
}