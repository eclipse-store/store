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
import java.util.function.IntPredicate;


/**
 * Provides various indexing methods for working with keys representing date values.
 * This interface extends the functionality of {@link IndexerTemporal}, allowing the creation of conditions
 * that operate on specific date components such as year, month, and day.
 *
 * @param <E> the entity type
 * @param <K> the key type
 * @param <T> the temporal type, representing date values
 *
 * @see IndexerTemporal
 */
public interface IndexerDate<E, K, T extends Temporal> extends IndexerTemporal<E, K, T>
{
	/**
	 * Creates a condition based on a custom year predicate.
	 *
	 * @param yearPredicate the predicate to evaluate year values
	 * @return a new condition that evaluates the provided year values against the predicate
	 */
	public <S extends E> Condition<S> isYear(IntPredicate yearPredicate);
	
	/**
	 * Creates a condition based on a custom month predicate.
	 *
	 * @param monthPredicate the predicate to evaluate month values
	 * @return a new condition that evaluates the provided month values against the predicate
	 */
	public <S extends E> Condition<S> isMonth(IntPredicate monthPredicate);
	
	/**
	 * Creates a condition based on a custom day predicate.
	 *
	 * @param dayPredicate the predicate to evaluate day values
	 * @return a new condition that evaluates the provided day values against the predicate
	 */
	public <S extends E> Condition<S> isDay(IntPredicate dayPredicate);
	
	/**
	 * Creates a condition which checks if the key's date matches the specified year, month, and day values.
	 *
	 * @param year the year to compare to
	 * @param month the month to compare to
	 * @param day the day to compare to
	 * @return a new condition that evaluates if the date matches the specified year, month, and day
	 */
	public <S extends E> Condition<S> isDate(int year, int month, int day);
	
	/**
	 * Creates a condition which checks if the key's year is equal to the specified value.
	 *
	 * @param year the year to compare to
	 * @return a new condition that evaluates if the year matches the specified value
	 */
	public <S extends E> Condition<S> isYear(int year);
	
	/**
	 * Creates a condition which checks if the key's month is equal to the specified value.
	 *
	 * @param month the month to compare to, where January is 1 and December is 12
	 * @return a new condition that evaluates if the month matches the specified value
	 */
	public <S extends E> Condition<S> isMonth(int month);
	
	/**
	 * Creates a condition that verifies whether the key's day matches the specified value.
	 *
	 * @param day the day to compare to, where valid values range from 1 to 31 depending on the month and year
	 * @return a new condition that evaluates if the day matches the specified value
	 */
	public <S extends E> Condition<S> isDay(int day);
	
	/**
	 * Creates a condition that checks if the year of the key's date is a leap year.
	 *
	 * @return a new condition that evaluates whether the year is a leap year
	 */
	public <S extends E> Condition<S> isLeapYear();
	
}
