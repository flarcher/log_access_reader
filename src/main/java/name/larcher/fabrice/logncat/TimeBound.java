/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package name.larcher.fabrice.logncat;

/**
 * Any time-bound information.
 */
public interface TimeBound {

	/**
	 * @return Timestamp in millis.
	 */
	long getTimeInMillis();
}
