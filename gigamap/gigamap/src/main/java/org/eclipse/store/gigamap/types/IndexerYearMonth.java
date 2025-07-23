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

import java.time.YearMonth;
import java.time.chrono.IsoChronology;
import java.util.function.IntPredicate;


/**
 * An interface for indexing entities based on YearMonth values.
 * It provides methods to create conditions and perform operations
 * specific to YearMonth values, allowing advanced filtering and
 * comparison functionality.
 *
 * @param <E> the entity type to be indexed
 */
public interface IndexerYearMonth<E> extends IndexerTemporal<E, Object[], YearMonth>
{
	/**
	 * Creates a condition with a custom year predicate.
	 *
	 * @param yearPredicate the custom predicate evaluating a year
	 * @return a new condition based on the provided year predicate
	 */
	public <S extends E> Condition<S> isYear(IntPredicate yearPredicate);
	
	/**
	 * Creates a condition with a custom month predicate.
	 *
	 * @param monthPredicate the custom predicate evaluating a month
	 * @return a new condition based on the provided month predicate
	 */
	public <S extends E> Condition<S> isMonth(IntPredicate monthPredicate);
	
	/**
	 * Creates a condition which checks if the key matches the specified year and month.
	 *
	 * @param year the year to compare to
	 * @param month the month to compare to
	 * @return a new condition based on the provided year and month
	 */
	public <S extends E> Condition<S> isYearMonth(int year, int month);
	
	/**
	 * Creates a condition that checks if the key's year matches the specified year.
	 *
	 * @param year the year to compare to
	 * @return a new condition that evaluates whether the key's year matches the given year
	 */
	public <S extends E> Condition<S> isYear(int year);
	
	/**
	 * Creates a condition that checks if the key's month matches the specified month.
	 *
	 * @param month the month to compare to
	 * @return a new condition that evaluates whether the key's month matches the given month
	 */
	public <S extends E> Condition<S> isMonth(int month);
	
	/**
	 * Creates a condition that evaluates whether a key corresponds to a leap year.
	 *
	 * @return a condition that checks if the key represents a leap year
	 */
	public <S extends E> Condition<S> isLeapYear();
	
	
	/**
	 * Abstract base class for a {@link YearMonth} key {@link Indexer}.
	 *
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends HashingCompositeIndexer.AbstractSingleValueFixedSize<E, YearMonth> implements IndexerYearMonth<E>
	{
		private final static int YEAR_INDEX  = 0;
		private final static int MONTH_INDEX = 1;
				
		protected Abstract()
		{
			super();
		}
		
		protected abstract YearMonth getYearMonth(E entity);
		
		@Override
		protected YearMonth getValue(final E entity)
		{
			return this.getYearMonth(entity);
		}
		
		@Override
		protected int compositeSize()
		{
			return 2;
		}
		
		@Override
		protected void fillCarrier(final YearMonth value, final Object[] carrier)
		{
			carrier[YEAR_INDEX]  = value.getYear();
			carrier[MONTH_INDEX] = value.getMonthValue();
		}
		
		@Override
		public final <S extends E> Condition<S> is(final YearMonth other)
		{
			return other == null
				? this.isNull()
				: this.isValue(other)
			;
		}
		
		@Override
		public <S extends E> Condition<S> isYear(final IntPredicate yearPredicate)
		{
			return this.is(new FieldPredicate(YEAR_INDEX, yearPredicate));
		}
		
		@Override
		public <S extends E> Condition<S> isMonth(final IntPredicate monthPredicate)
		{
			return this.is(new FieldPredicate(MONTH_INDEX, monthPredicate));
		}
		
		@Override
		public final <S extends E> Condition<S> isYearMonth(final int year, final int month)
		{
			Validators.validateMonth(month);
			
			return this.isValue(YearMonth.of(year, month));
		}
		
		@Override
		public final <S extends E> Condition<S> isYear(final int year)
		{
			return this.is(new EqualsFieldPredicate(YEAR_INDEX, year));
		}
		
		@Override
		public final <S extends E> Condition<S> isMonth(final int month)
		{
			Validators.validateMonth(month);
			
			return this.is(new EqualsFieldPredicate(MONTH_INDEX, month));
		}
		
		@Override
		public final <S extends E> Condition<S> isLeapYear()
		{
			return this.is(new FieldPredicate(YEAR_INDEX, IsoChronology.INSTANCE::isLeapYear));
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> before(final YearMonth boundExclusive)
		{
			if(boundExclusive == null)
			{
				throw new IllegalArgumentException("boundExclusive cannot be null");
			}
			
			return (Condition<S>)this.is(
				new FieldPredicate(YEAR_INDEX, year -> year < boundExclusive.getYear())
			).or(
				this.is(
					new EqualsUntilPredicate(YEAR_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(MONTH_INDEX, month -> month < boundExclusive.getMonthValue()))
				)
			);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> beforeEqual(final YearMonth boundInclusive)
		{
			if(boundInclusive == null)
			{
				throw new IllegalArgumentException("boundInclusive cannot be null");
			}
			
			return (Condition<S>)this.is(boundInclusive).or(this.before(boundInclusive));
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> after(final YearMonth boundExclusive)
		{
			if(boundExclusive == null)
			{
				throw new IllegalArgumentException("boundExclusive cannot be null");
			}
			
			return (Condition<S>)this.is(
				new FieldPredicate(YEAR_INDEX, year -> year > boundExclusive.getYear())
			).or(
				this.is(
					new EqualsUntilPredicate(YEAR_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(MONTH_INDEX, month -> month > boundExclusive.getMonthValue()))
				)
			);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> afterEqual(final YearMonth boundInclusive)
		{
			if(boundInclusive == null)
			{
				throw new IllegalArgumentException("boundInclusive cannot be null");
			}
			
			return (Condition<S>)this.is(boundInclusive).or(this.after(boundInclusive));
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> between(final YearMonth startInclusive, final YearMonth endInclusive)
		{
			if(startInclusive == null)
			{
				throw new IllegalArgumentException("startInclusive cannot be null");
			}
			if(endInclusive == null)
			{
				throw new IllegalArgumentException("endInclusive cannot be null");
			}
			
			return (Condition<S>)this.afterEqual(startInclusive).and(this.beforeEqual(endInclusive));
		}
		
		
		static class FieldPredicate implements CompositePredicate<Object[]>
		{
			final int          subKeyPosition;
			final IntPredicate predicate;
			
			FieldPredicate(final int subKeyPosition, final IntPredicate predicate)
			{
				this.subKeyPosition = subKeyPosition;
				this.predicate      = predicate;
			}
			
			@Override
			public boolean setSubKeyPosition(final int subKeyPosition)
			{
				return subKeyPosition == this.subKeyPosition;
			}
			
			@Override
			public boolean test(final Object[] keys)
			{
				return this.test(this.subKeyPosition, keys[this.subKeyPosition]);
			}
			
			@Override
			public boolean test(final int subKeyPosition, final Object subKey)
			{
				return subKeyPosition == this.subKeyPosition && subKey instanceof Integer && this.predicate.test((Integer)subKey);
			}
			
		}
		
		
		static class EqualsFieldPredicate implements CompositePredicate<Object[]>
		{
			final int subKeyPosition;
			final int value;
			
			EqualsFieldPredicate(final int subKeyPosition, final int value)
			{
				this.subKeyPosition = subKeyPosition;
				this.value          = value;
			}
			
			@Override
			public boolean setSubKeyPosition(final int subKeyPosition)
			{
				return subKeyPosition == this.subKeyPosition;
			}
			
			@Override
			public boolean test(final Object[] keys)
			{
				return this.test(this.subKeyPosition, keys[this.subKeyPosition]);
			}
			
			@Override
			public boolean test(final int subKeyPosition, final Object subKey)
			{
				return subKeyPosition == this.subKeyPosition && subKey instanceof Integer && this.value == (Integer)subKey;
			}
			
		}
		
		
		static class EqualsUntilPredicate implements CompositePredicate<Object[]>
		{
			final int maxSubKeyPosition;
			final YearMonth other;
			
			EqualsUntilPredicate(final int maxSubKeyPosition, final YearMonth other)
			{
				this.maxSubKeyPosition = maxSubKeyPosition;
				this.other = other;
			}
			
			@Override
			public boolean setSubKeyPosition(final int subKeyPosition)
			{
				return subKeyPosition <= this.maxSubKeyPosition;
			}
			
			@Override
			public boolean test(final Object[] keys)
			{
				for(int i = 0; i < this.maxSubKeyPosition; i++)
				{
					if(!this.test(i, keys[i]))
					{
						return false;
					}
				}
				return true;
			}
			
			@Override
			public boolean test(final int subKeyPosition, final Object subKey)
			{
				return subKeyPosition <= this.maxSubKeyPosition && subKey instanceof Integer && get(this.other, subKeyPosition) == (Integer)subKey;
			}
			
			static int get(final YearMonth date, final int subKeyPosition)
			{
				switch(subKeyPosition)
				{
					case YEAR_INDEX:
						return date.getYear();
					case MONTH_INDEX:
						return date.getMonthValue();
					default:
						throw new IllegalArgumentException("Invalid subKeyPosition: " + subKeyPosition);
				}
			}
			
		}
		
	}
	
}
