/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.stat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;

@Immutable
public class StatisticContext {

	@FunctionalInterface
	public interface StatisticListener {

		void accept(
			StatisticContext context,
			String date,
			Statistic value);
	}

	public StatisticContext(
			@Nullable Duration duration,
			int topSectionCount,
			Comparator<Statistic.ScopedStatistic> sectionComparator,
			StatisticListener listener) {

		this.duration = duration;
		this.topSectionCount = topSectionCount;
		this.listener = Objects.requireNonNull(listener);
		this.sectionComparator = Objects.requireNonNull(sectionComparator);
	}

	private final Duration duration;
	private final int topSectionCount;
	private final Comparator<Statistic.ScopedStatistic> sectionComparator;
	private final StatisticListener listener;

	@Nullable
	public Duration getDuration() {
		return duration;
	}

	public int getTopSectionCount() {
		return topSectionCount;
	}

	public Comparator<Statistic.ScopedStatistic> getSectionComparator() {
		return sectionComparator;
	}

	public void notify(String date, Statistic statistic) {
		listener.accept(this, date, statistic);
	}
}
