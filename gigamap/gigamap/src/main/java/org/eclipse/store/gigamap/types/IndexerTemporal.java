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
 * An interface for defining operations and conditions on temporal data indexed by keys of type {@code K},
 * which produce results based on instances of type {@code T}, a subtype of {@link Temporal}.
 * Subclasses of this interface can specify particular types of temporal values such as dates, times, or durations.
 *
 * @param <E> the type of the entities being indexed
 * @param <K> the type of the indexing key
 * @param <T> the type of the temporal value, which must extend {@link Temporal}
 */
public interface IndexerTemporal<E, K, T extends Temporal> extends Indexer<E, K>
{
	/**
	 * Creates a condition which checks if the key is equal to a given point in time.
	 *
	 * @param other the value to check against, may be null to match entities with a null key
	 * @return a new condition
	 *
	 * @implNote Temporal indexers are composite indexers whose key type is {@code Object[]}. Invoking
	 * the generic {@link IndexIdentifier#is(Object)} through a raw or {@code Object}-typed
	 * {@link IndexIdentifier} reference passes the logical value where an {@code Object[]} composite
	 * key is expected and throws {@link ClassCastException} in the synthetic bridge method. Always use
	 * this typed {@code is(T)} overload (and the typed {@link #not(Temporal)} / {@link #in(Temporal...)}
	 * / {@link #notIn(Temporal...)} / {@link IndexIdentifier#isNull()} / {@link IndexIdentifier#notNull()}
	 * methods) rather than the generic {@code Object[]}-keyed API.
	 */
	public <S extends E> Condition<S> is(T other);

	/**
	 * Creates a condition which checks if the key is not equal to a given point in time.
	 * Entities with a null key are included (a null key is "not equal" to any concrete value).
	 *
	 * @param other the value to check against, may be null (equivalent to {@link IndexIdentifier#notNull()})
	 * @return a new negated condition
	 */
	public default <S extends E> Condition<S> not(final T other)
	{
		return new Condition.Not<>(this.is(other));
	}

	/**
	 * Creates a condition which checks if the key is equal to any of the given points in time.
	 *
	 * @param others the values to check against, not null and not empty
	 * @return a new condition representing the containment check
	 */
	@SuppressWarnings("unchecked")
	public default <S extends E> Condition<S> in(final T... others)
	{
		if(others == null || others.length == 0)
		{
			throw new IllegalArgumentException("others must not be null or empty");
		}
		Condition<S> result = this.is(others[0]);
		for(int i = 1; i < others.length; i++)
		{
			result = result.or(this.is(others[i]));
		}
		return others.length > 1 ? result.complete() : result;
	}

	/**
	 * Creates a condition which checks if the key is not equal to any of the given points in time.
	 * Entities with a null key are included unless {@code null} is among the given values.
	 *
	 * @param others the values to check against, not null and not empty
	 * @return a new negated condition representing the containment check
	 */
	@SuppressWarnings("unchecked")
	public default <S extends E> Condition<S> notIn(final T... others)
	{
		return new Condition.Not<>(this.in(others));
	}

	/**
	 * Creates a condition which checks if the key is before a given point in time.
	 *
	 * @param boundExclusive the exclusive upper bound to check against, not null
	 * @return a new condition representing the "before" comparison
	 */
	public <S extends E> Condition<S> before(T boundExclusive);
	
	/**
	 * Creates a condition which checks if the key is before or equal to a given point in time.
	 *
	 * @param boundInclusive the inclusive upper bound to check against, not null
	 * @return a new condition representing the "before or equal to" comparison
	 */
	public <S extends E> Condition<S> beforeEqual(T boundInclusive);
	
	/**
	 * Creates a condition which checks if the key is after a given point in time.
	 *
	 * @param boundExclusive the exclusive lower bound to check against, not null
	 * @return a new condition representing the "after" comparison
	 */
	public <S extends E> Condition<S> after(T boundExclusive);
	
	/**
	 * Creates a condition which checks if the key is after or equal to a given point in time.
	 *
	 * @param boundInclusive the inclusive lower bound to check against, not null
	 * @return a new condition representing the "after or equal to" comparison
	 */
	public <S extends E> Condition<S> afterEqual(T boundInclusive);
	
	/**
	 * Creates a condition which checks if the key is within a specified range of time, inclusive.
	 *
	 * @param startInclusive the inclusive start of the range, not null
	 * @param endInclusive the inclusive end of the range, not null
	 * @return a new condition representing the "between" comparison
	 */
	public <S extends E> Condition<S> between(final T startInclusive, final T endInclusive);
	
	
	class Validators
	{
		protected static void validateSecond(final int second)
		{
			if(second < 0 || second > 59)
			{
				throw new IllegalArgumentException("second out of range (0-59): " + second);
			}
		}
		
		protected static void validateMinute(final int minute)
		{
			if(minute < 0 || minute > 59)
			{
				throw new IllegalArgumentException("minute out of range (0-59): " + minute);
			}
		}
		
		protected static void validateHour(final int hour)
		{
			if(hour < 0 || hour > 23)
			{
				throw new IllegalArgumentException("hour out of range (0-23): " + hour);
			}
		}
		
		protected static void validateDay(final int day)
		{
			if(day < 1 || day > 31)
			{
				throw new IllegalArgumentException("day out of range (1-31): " + day);
			}
		}
		
		protected static void validateMonth(final int month)
		{
			if(month < 1 || month > 12)
			{
				throw new IllegalArgumentException("month out of range (1-12): " + month);
			}
		}
		
	}
	
}

