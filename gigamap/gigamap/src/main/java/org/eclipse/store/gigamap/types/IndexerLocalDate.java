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

import java.time.LocalDate;
import java.time.chrono.IsoChronology;
import java.util.function.IntPredicate;

/**
 * Indexing logic for {@link LocalDate} keys.
 * 
 * @param <E> the entity type
 * 
 * @see IndexerDate
 */
public interface IndexerLocalDate<E> extends IndexerDate<E, Object[], LocalDate>
{

	/**
	 * Abstract base class for a {@link LocalDate} key {@link Indexer}.
	 * 
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends HashingCompositeIndexer.AbstractSingleValueFixedSize<E, LocalDate> implements IndexerLocalDate<E>
	{
		private final static int YEAR_INDEX  = 0;
		private final static int MONTH_INDEX = 1;
		private final static int DAY_INDEX   = 2;
		
		protected Abstract()
		{
			super();
		}
		
		protected abstract LocalDate getLocalDate(E entity);
		
		@Override
		protected LocalDate getValue(final E entity)
		{
			return this.getLocalDate(entity);
		}
		
		@Override
		protected int compositeSize()
		{
			return 3;
		}
		
		@Override
		protected void fillCarrier(final LocalDate value, final Object[] carrier)
		{
			carrier[YEAR_INDEX]  = value.getYear();
			carrier[MONTH_INDEX] = value.getMonthValue();
			carrier[DAY_INDEX]   = value.getDayOfMonth();
		}
		
		@Override
		public final <S extends E> Condition<S> is(final LocalDate other)
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
		public <S extends E> Condition<S> isDay(final IntPredicate dayPredicate)
		{
			return this.is(new FieldPredicate(DAY_INDEX, dayPredicate));
		}
		
		@Override
		public final <S extends E> Condition<S> isDate(final int year, final int month, final int day)
		{
			Validators.validateMonth(month);
			Validators.validateDay(day);
			
			return this.isValue(LocalDate.of(year, month, day));
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
		public final <S extends E> Condition<S> isDay(final int day)
		{
			Validators.validateDay(day);
			
			return this.is(new EqualsFieldPredicate(DAY_INDEX, day));
		}
		
		@Override
		public final <S extends E> Condition<S> isLeapYear()
		{
			return this.is(new FieldPredicate(YEAR_INDEX, IsoChronology.INSTANCE::isLeapYear));
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> before(final LocalDate boundExclusive)
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
			).or(
				this.is(
					new EqualsUntilPredicate(MONTH_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(DAY_INDEX, day -> day < boundExclusive.getDayOfMonth()))
				)
			);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> beforeEqual(final LocalDate boundInclusive)
		{
			if(boundInclusive == null)
			{
				throw new IllegalArgumentException("boundInclusive cannot be null");
			}
			
			return (Condition<S>)this.is(boundInclusive).or(this.before(boundInclusive));
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> after(final LocalDate boundExclusive)
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
			).or(
				this.is(
					new EqualsUntilPredicate(MONTH_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(DAY_INDEX, day -> day > boundExclusive.getDayOfMonth()))
				)
			);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> afterEqual(final LocalDate boundInclusive)
		{
			if(boundInclusive == null)
			{
				throw new IllegalArgumentException("boundInclusive cannot be null");
			}
			
			return (Condition<S>)this.is(boundInclusive).or(this.after(boundInclusive));
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> between(final LocalDate startInclusive, final LocalDate endInclusive)
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
			final LocalDate other;
			
			EqualsUntilPredicate(final int maxSubKeyPosition, final LocalDate other)
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
			
			static int get(final LocalDate date, final int subKeyPosition)
			{
				switch(subKeyPosition)
				{
					case YEAR_INDEX:
						return date.getYear();
					case MONTH_INDEX:
						return date.getMonthValue();
					case DAY_INDEX:
						return date.getDayOfMonth();
					default:
						throw new IllegalArgumentException("Invalid subKeyPosition: " + subKeyPosition);
				}
			}
			
		}
		
	}
	
}
