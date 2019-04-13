/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.access_log_reader.read;

import javax.annotation.concurrent.Immutable;
import java.util.function.Function;

/**
 * Parses an access log line.
 */
@Immutable
class AccessLogParser implements Function<String, AccessLogLine> {

	@Override
	public AccessLogLine apply(String s) {
		// TODO
		return new AccessLogLine();
	}
}
