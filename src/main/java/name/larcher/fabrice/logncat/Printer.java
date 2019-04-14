/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat;

import name.larcher.fabrice.logncat.config.Argument;
import name.larcher.fabrice.logncat.stat.ScopedStatistic;
import name.larcher.fabrice.logncat.stat.Statistic;

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

final class Printer {

	private Printer() {} // Utility class

	static String toString(Duration duration) {
		return duration.toString().substring(2).toLowerCase();
	}

	static void printHelp() {
		PrintStream printer = System.out;
		printer.println("LOG'n CAT \uD83D\uDC31");
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

	static void printBeforeRun(long refreshPeriodMillis) {
		PrintStream printer = System.out;
		printer.println("Stats will be printed each " + toString(Duration.ofMillis(refreshPeriodMillis)));
		printer.println("You can quit using <^C> (or sending a kill signal)");
	}

	static void printStats(Statistic stats, String date, long periodInMillis, int topSectionsCount) {

		PrintStream printer = System.out;
		printer.print("[" + date + "] ");
		String prefix = periodInMillis <= 0
				? "Overall"
				: "In last " + toString(Duration.ofMillis(periodInMillis));
		printer.print(prefix);
		printer.print(" | ");
		printer.print(scopedStatsToString(stats.overall()));
		printer.print(" | ");
		if (topSectionsCount > 0) {
			List<ScopedStatistic> topSectionsStats = new ArrayList<>(topSectionsCount);
			int sectionCount = 0;
			for (ScopedStatistic sectionStats : stats.topSections()) {
				topSectionsStats.add(sectionStats);
				sectionCount++;
				if (sectionCount > topSectionsCount) {
					break;
				}
			}
			printer.print(topSectionsStats.stream()
					.map(sectionStat ->
							"{ section: " + sectionStat.getSection()
							+ ", " + scopedStatsToString(sectionStat) + " }")
					.collect(Collectors.joining(", ", "[", "]")));
		}
		printer.println();
	}

	private static String scopedStatsToString(ScopedStatistic scopedStats) {
		return "count: " + scopedStats.requestCount();
	}

}
