/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.display;

import name.larcher.fabrice.logncat.alert.AlertEvent;

import java.io.PrintStream;
import java.util.Objects;
import java.util.function.Consumer;

public class AlertPrintListener implements Consumer<AlertEvent<?>> {

	public AlertPrintListener(Printer printer, PrintStream stream) {
		this.printer = Objects.requireNonNull(printer);
		this.stream = Objects.requireNonNull(stream);
	}

	private final Printer printer;
	private final PrintStream stream;

	@Override
	public void accept(AlertEvent<?> alertEvent) {
		stream.println(printer.printAlert(alertEvent));
	}
}

