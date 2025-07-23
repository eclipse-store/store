package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.time.temporal.Temporal;


/**
 * Provides indexing logic focusing on both date and time keys.
 * This interface combines the functionality of {@link IndexerDate} and {@link IndexerTime},
 * offering methods to create conditions based on date-time values for use in indexed data structures.
 *
 * @param <E> the entity type
 * @param <K> the key type
 * @param <T> the temporal type, representing date-time values
 *
 * @see IndexerDate
 * @see IndexerTime
 */
public interface IndexerDateTime<E, K, T extends Temporal> extends IndexerDate<E, K, T>, IndexerTime<E, K, T>
{
	/**
	 * Creates a condition that matches entities based on the specified date and time values.
	 *
	 * @param <S> the type of the entity to be evaluated by the condition
	 * @param year the year of the date-time
	 * @param month the month of the date-time
	 * @param day the day of the date-time
	 * @param hour the hour of the time
	 * @param minute the minute of the time
	 * @param second the second of the time
	 * @return a condition that evaluates entities matching the given date and time values
	 */
	public <S extends E> Condition<S> isDateTime(
		int year, int month, int day,
		int hour, int minute, int second
	);
}
