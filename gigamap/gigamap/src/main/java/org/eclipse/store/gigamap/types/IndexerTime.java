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
 * Provides indexing logic focusing on time-based key values within an indexed data structure.
 * This interface extends the functionality of {@link IndexerTemporal} and offers methods
 * to create conditions based on hour, minute, and second fields of {@link Temporal} objects.
 *
 * @param <E> the entity type being indexed
 * @param <K> the key type on which indexing is performed
 * @param <T> the temporal type, representing time values
 *
 * @see IndexerTemporal
 */
public interface IndexerTime<E, K, T extends Temporal> extends IndexerTemporal<E, K, T>
{
	/**
	 * Creates a condition with a custom hour predicate.
	 *
	 * @param hourPredicate the custom predicate to evaluate the hour condition
	 * @return a new condition that applies the custom hour predicate
	 */
	public <S extends E> Condition<S> isHour(IntPredicate hourPredicate);
	
	/**
	 * Creates a condition with a custom minute predicate.
	 *
	 * @param minutePredicate the custom predicate to evaluate the minute condition
	 * @return a new condition that applies the custom minute predicate
	 */
	public <S extends E> Condition<S> isMinute(IntPredicate minutePredicate);
	
	/**
	 * Creates a condition with a custom second predicate.
	 *
	 * @param secondPredicate the custom predicate to evaluate the second condition
	 * @return a new condition that applies the custom second predicate
	 */
	public <S extends E> Condition<S> isSecond(IntPredicate secondPredicate);
	
	/**
	 * Creates a condition that checks if the key's time matches the specified hour, minute, and second.
	 *
	 * @param hour the hour to compare to
	 * @param minute the minute to compare to
	 * @param second the second to compare to
	 * @return a condition that checks if the key's time matches the specified values
	 */
	public <S extends E> Condition<S> isTime(int hour, int minute, int second);
	
	/**
	 * Creates a condition that checks if the key's hour is equal to the specified value.
	 *
	 * @param hour the hour to compare to
	 * @return a new condition that checks if the key's hour matches the specified value
	 */
	public <S extends E> Condition<S> isHour(int hour);
	
	/**
	 * Creates a condition that checks if the key's minute is equal to the specified value.
	 *
	 * @param minute the minute to compare to
	 * @return a new condition that checks if the key's minute matches the specified value
	 */
	public <S extends E> Condition<S> isMinute(int minute);
	
	/**
	 * Creates a condition that checks if the key's second is equal to the specified value.
	 *
	 * @param second the second to compare to
	 * @return a new condition that checks if the key's second matches the specified value
	 */
	public <S extends E> Condition<S> isSecond(int second);

}
