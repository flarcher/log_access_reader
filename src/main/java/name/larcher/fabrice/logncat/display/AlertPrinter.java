/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.display;

import name.larcher.fabrice.logncat.alert.AlertEvent;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Consumer;

public class AlertPrinter implements Consumer<AlertEvent<?>>, Closeable {

	public AlertPrinter(Printer printer, String streamConfiguration) throws IOException {

		if (streamConfiguration == null || streamConfiguration.isEmpty()) {
			this.stream = System.out;
			this.toBeClosed = false;
		}
		else {
			this.stream = new PrintStream(
					Files.newOutputStream(Paths.get(streamConfiguration),
						StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE),
					false);
			this.toBeClosed = true;
		}
		this.printer = Objects.requireNonNull(printer);
	}

	private final Printer printer;
	private final PrintStream stream;
	private final boolean toBeClosed;

	@Override
	public void accept(AlertEvent<?> alertEvent) {
		stream.println(printer.printAlert(alertEvent));
		if (toBeClosed) {
			stream.flush();
		}
	}

	@Override
	public void close() {
		if (toBeClosed) {
			stream.close();
		}
	}
}

