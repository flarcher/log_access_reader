/*
 * Copyright (c) 2019.
 * Fabrice Larcher
 */

package flarcher.log.access;

/**
 * Any time-bound information.
 */
public interface TimeBound {

	/**
	 * @return Timestamp in millis.
	 */
	long getTimeInMillis();
}
