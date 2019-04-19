/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat;

import name.larcher.fabrice.logncat.config.Argument;
import name.larcher.fabrice.logncat.config.DurationConverter;
import name.larcher.fabrice.logncat.stat.Statistic.ScopedStatistic;
import name.larcher.fabrice.logncat.stat.Statistic;

import java.io.PrintStream;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

final class Printer {

	private Printer() {} // Utility class

	private static String appName() {
		return "LOG'n-CAT \uD83D\uDC31";
	}

	static void printHelp() {
		PrintStream printer = System.out;
		printer.println(appName());
		printer.println(" Prints statistics and notifies alerts by reading access log files.");
		printer.println();
		printer.println("Possible arguments are:");
		printer.println();
		Arrays.stream(Argument.values())
				.sorted(Comparator.comparing(Argument::getPropertyName))
				.forEach( arg -> {

					String name = arg.name().replaceAll("_", " ").toLowerCase();
					printer.println("-" + arg.getCommandOption() + " <" + name + ">");

					printer.println("  " + arg.getDescription());

					printer.println("  Can be set using the environment variable " + arg.getEnvironmentParameter());

					printer.println("  Can be set as the property " + arg.getPropertyName() + " in the configuration file");

					printer.println("  The default value is «" + arg.getDefaultValue() + "»");

					printer.println();
				});
	}

	static void printBeforeRun(Duration refreshPeriodMillis) {
		PrintStream printer = System.out;
		printer.println("You started " + appName());
		printer.println("Stats will be printed each " + DurationConverter.toString(refreshPeriodMillis));
		printer.println("You can quit using <^C> (or sending a kill signal)");
	}

	static void printStats(Statistic stats, String date, Duration period, int topSectionsCount) {

		PrintStream printer = System.out;
		printer.print("[" + date + "] ");
		String prefix = period == null ? "Overall" : ("In last " + DurationConverter.toString(period));
		printer.print(prefix);
		printer.print(" | ");
		printer.print(scopedStatsToString(stats.overall()));
		printer.print(" | ");
		if (topSectionsCount > 0) {
			List<Map.Entry<String, ? extends ScopedStatistic>> topSectionsStats = stats.topSections();
			int sectionCount = Math.min(topSectionsStats.size(), topSectionsCount);
			topSectionsStats = topSectionsStats.subList(0, sectionCount);
			printer.print(topSectionsStats.stream()
					.map(sectionStat ->
							"{ section: " + sectionStat.getKey()
							+ ", " + scopedStatsToString(sectionStat.getValue()) + " }")
					.collect(Collectors.joining(", ", "[", "]")));
		}
		printer.println();
	}

	private static String scopedStatsToString(ScopedStatistic scopedStats) {
		return "count: " + scopedStats.requestCount();
	}

	static void noLine() {
		System.out.println("No line");
	}
}
