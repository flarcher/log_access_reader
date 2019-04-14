/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat.stat;

class IntegerMetricOverTime implements MetricOverTime<Integer> {

	IntegerMetricOverTime() {
		clear();
	}

	private int sum;
	private int count;
	private int max;

	@Override
	public void clear() {
		sum = 0;
		count = 0;
		max = -1;
	}

	@Override
	public Integer average() {
		return sum / count;
	}

	@Override
	public Integer maximum() {
		return max;
	}

	@Override
	public void accept(Integer value) {
		count++;
		sum += value;
		max = Math.max(max, value);
	}

}
