/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.display;

import com.googlecode.lanterna.*;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import name.larcher.fabrice.logncat.DurationConverter;
import name.larcher.fabrice.logncat.alert.AlertEvent;
import name.larcher.fabrice.logncat.stat.Statistic;
import name.larcher.fabrice.logncat.stat.StatisticContext;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * Handle the user interface from the terminal.
 */
public class Console implements Closeable {

	public Console(Printer printer) {
		this.printer = Objects.requireNonNull(printer);
		try {
			Terminal terminal = new DefaultTerminalFactory().createTerminal();
			screen = new TerminalScreen(terminal);
			tg = screen.newTextGraphics();
		} catch (IOException e) {
			throw handleIOException(e);
		}
	}

	public synchronized void init(Duration refreshPeriodMillis, Runnable onQuit) {
		this.onQuit = Objects.requireNonNull(onQuit);
		this.refreshPeriodMillis = Objects.requireNonNull(refreshPeriodMillis);

		try {
			screen.startScreen();
		} catch (IOException e) {
			throw handleIOException(e);
		}
	}

	// Construction variables
	private final Printer printer;
	private final Screen screen;
	private final TextGraphics tg;

	// Init variables
	private Runnable onQuit;
	private Duration refreshPeriodMillis;

	// Mutable state
	private int nextRow = 0;
	private final Deque<AlertEvent<?>> alertsDeque = new ConcurrentLinkedDeque<>(); // Used as a LIFO queue

	public synchronized void beforePrint(Instant instant) {
		screen.clear();
		tg.clearModifiers();
		nextRow = 0;
		// Top of screen
		for (String line : Printer.printBeforeRun(refreshPeriodMillis)) {
			tg.putString(0, nextRow++, line);
		}
		tg.putString(4, nextRow++, "Latest statistics @ " + printer.formatInstant(instant));
		// ... stats are printed from calls to `onStat`
	}

	public synchronized void afterPrint(Instant instant) {
		// ...
		printAlertHistory();
		// Bottom of screen

		try {
			screen.refresh();
		}
		catch (IOException e) {
			throw handleIOException(e);
		}
	}

	public synchronized void empty() {
		tg.putString(0, nextRow++, "Waiting for input");
	}

	private static final TextColor RECTANGLE_FOREGROUND_COLOR = new TextColor.Indexed(242);
	private static final TextColor RECTANGLE_BACKGROUND_COLOR = new TextColor.RGB(0,0,0);
	private static final int RECTANGLE_WIDTH = 80;
	private static final int METRICS_WIDTH = 25;

	public synchronized void onStat(StatisticContext context, Statistic stats) {

		Duration duration = context.getDuration();
		List<Map.Entry<String, ? extends Statistic.ScopedStatistic>> sectionStats = stats.topSections()
				.stream()
				.limit(context.getTopSectionCount())
				.collect(Collectors.toList());

		tg.drawRectangle(
				new TerminalPosition(0, nextRow),
				new TerminalSize(RECTANGLE_WIDTH, sectionStats.size() + 3),
				new TextCharacter('·', RECTANGLE_FOREGROUND_COLOR, RECTANGLE_BACKGROUND_COLOR));
		tg.setModifiers(EnumSet.of(SGR.UNDERLINE, SGR.BOLD));
		tg.putString(4, nextRow, context.isDynamic()
				? "Overall (" + DurationConverter.toString(duration) + ")"
				: "Latest " + DurationConverter.toString(duration));
		tg.clearModifiers();
		tg.putString(RECTANGLE_WIDTH - (2 * METRICS_WIDTH), nextRow, "Count");
		tg.putString(RECTANGLE_WIDTH - METRICS_WIDTH, nextRow, "Bytes");
		nextRow++;

		onScopedStat(null, stats.overall(), duration);
		sectionStats.forEach(entry -> onScopedStat(entry.getKey(), entry.getValue(), duration));

		nextRow++;
	}

	private void onScopedStat(String section, Statistic.ScopedStatistic value, Duration duration) {
		tg.putString(2, nextRow, section == null ? "«total»" : "/" + section);
		tg.putString(RECTANGLE_WIDTH - (2 * METRICS_WIDTH), nextRow, Printer.getValueWithRatio(value.requestCount(), duration));
		tg.putString(RECTANGLE_WIDTH - METRICS_WIDTH, nextRow, Printer.getValueWithRatio(value.weight(), duration));
		nextRow++;
	}

	public void onAlert(AlertEvent<?> event) {
		// We insert as the first so that the latest entries get printed first -> LIFO
		alertsDeque.addFirst(event);
	}

	private void printAlertHistory() {
		tg.setModifiers(EnumSet.of(SGR.BOLD));
		TextColor previousColor = tg.getForegroundColor();
		alertsDeque
			.forEach(event -> {
				String str = printer.printAlert(event);
				tg.setForegroundColor(event.isRaised() ? TextColor.ANSI.RED : TextColor.ANSI.GREEN);
				tg.putString(1, nextRow++, str);
			});
		tg.setForegroundColor(previousColor);
		tg.clearModifiers();
	}

	private static boolean isExitKey(KeyStroke keyStroke) {
		switch (keyStroke.getKeyType()) {
			case Character:
				return String.valueOf(keyStroke.getCharacter()).toLowerCase().charAt(0) == 'q';
			case Escape:
				return true;
		}
		return false;
	}

	public void readInput() {
		KeyStroke key;
		try {
			while ((key = screen.pollInput()) != null) {
				if (isExitKey(key)) {
					close();
				}
			}
		} catch (IOException e) {
			throw handleIOException(e);
		}
	}

	public void destroy() {
		try {
			close();
		} catch (IOException e) {
			throw handleIOException(e);
		}
	}

	@Override
	public synchronized void close() throws IOException {
		screen.clear();
		tg.putString(0, 0, "Exiting...");
		screen.refresh();
		onQuit.run();
		screen.stopScreen();
		screen.close();
	}

	private static RuntimeException handleIOException(IOException e) {
		throw new RuntimeException(e);
	}

}
