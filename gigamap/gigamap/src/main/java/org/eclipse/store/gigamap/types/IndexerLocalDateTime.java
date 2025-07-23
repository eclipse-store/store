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

import java.time.LocalDateTime;
import java.time.chrono.IsoChronology;
import java.util.function.IntPredicate;

/**
 * Indexing logic for {@link LocalDateTime} keys.
 * 
 * @param <E> the entity type
 * 
 * @see IndexerDateTime
 */
public interface IndexerLocalDateTime<E> extends IndexerDateTime<E, Object[], LocalDateTime>
{

	/**
	 * Abstract base class for a {@link LocalDateTime} key {@link Indexer}.
	 *
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends HashingCompositeIndexer.AbstractSingleValueFixedSize<E, LocalDateTime> implements IndexerLocalDateTime<E>
	{
		private final static int YEAR_INDEX   = 0;
		private final static int MONTH_INDEX  = 1;
		private final static int DAY_INDEX    = 2;
		private final static int HOUR_INDEX   = 3;
		private final static int MINUTE_INDEX = 4;
		private final static int SECOND_INDEX = 5;
		
		protected Abstract()
		{
			super();
		}
		
		protected abstract LocalDateTime getLocalDateTime(E entity);
		
		@Override
		protected LocalDateTime getValue(final E entity)
		{
			return this.getLocalDateTime(entity);
		}
		
		@Override
		protected int compositeSize()
		{
			return 6;
		}
		
		@Override
		protected void fillCarrier(final LocalDateTime value, final Object[] carrier)
		{
			carrier[YEAR_INDEX]   = value.getYear();
			carrier[MONTH_INDEX]  = value.getMonthValue();
			carrier[DAY_INDEX]    = value.getDayOfMonth();
			carrier[HOUR_INDEX]   = value.getHour();
			carrier[MINUTE_INDEX] = value.getMinute();
			carrier[SECOND_INDEX] = value.getSecond();
		}
		
		@Override
		public final <S extends E> Condition<S> is(final LocalDateTime other)
		{
			return other == null
				? this.isNull()
				: this.isValue(other)
			;
		}
		
		@Override
		public final <S extends E> Condition<S> isDateTime(
			final int year, final int month, final int day,
			final int hour, final int minute, final int second
		)
		{
			Validators.validateMonth(month);
			Validators.validateDay(day);
			Validators.validateHour(hour);
			Validators.validateMinute(minute);
			Validators.validateSecond(second);
			
			return this.isValue(LocalDateTime.of(year, month, day, hour, minute, second));
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
		public <S extends E> Condition<S> isHour(final IntPredicate hourPredicate)
		{
			return this.is(new FieldPredicate(HOUR_INDEX, hourPredicate));
		}
		
		@Override
		public <S extends E> Condition<S> isMinute(final IntPredicate minutePredicate)
		{
			return this.is(new FieldPredicate(MINUTE_INDEX, minutePredicate));
		}
		
		@Override
		public <S extends E> Condition<S> isSecond(final IntPredicate secondPredicate)
		{
			return this.is(new FieldPredicate(SECOND_INDEX, secondPredicate));
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> isDate(final int year, final int month, final int day)
		{
			Validators.validateMonth(month);
			Validators.validateDay(day);
			
			return (Condition<S>)this.isYear(year).and(this.isMonth(month)).and(this.isDay(day));
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
		public final <S extends E> Condition<S> isTime(final int hour, final int minute, final int second)
		{
			Validators.validateHour(hour);
			Validators.validateMinute(minute);
			Validators.validateSecond(second);
			
			return (Condition<S>)this.isHour(hour).and(this.isMinute(minute)).and(this.isSecond(second));
		}
		
		@Override
		public final <S extends E> Condition<S> isHour(final int hour)
		{
			Validators.validateHour(hour);
			
			return this.is(new EqualsFieldPredicate(HOUR_INDEX, hour));
		}
		
		@Override
		public final <S extends E> Condition<S> isMinute(final int minute)
		{
			Validators.validateMinute(minute);
			
			return this.is(new EqualsFieldPredicate(MINUTE_INDEX, minute));
		}
		
		@Override
		public final <S extends E> Condition<S> isSecond(final int second)
		{
			Validators.validateSecond(second);
			
			return this.is(new EqualsFieldPredicate(SECOND_INDEX, second));
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> before(final LocalDateTime boundExclusive)
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
			).or(
				this.is(
					new EqualsUntilPredicate(DAY_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(HOUR_INDEX, day -> day < boundExclusive.getHour()))
				)
			).or(
				this.is(
					new EqualsUntilPredicate(HOUR_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(MINUTE_INDEX, day -> day < boundExclusive.getMinute()))
				)
			).or(
				this.is(
					new EqualsUntilPredicate(MINUTE_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(SECOND_INDEX, day -> day < boundExclusive.getSecond()))
				)
			);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> beforeEqual(final LocalDateTime boundInclusive)
		{
			if(boundInclusive == null)
			{
				throw new IllegalArgumentException("boundInclusive cannot be null");
			}
			
			return (Condition<S>)this.is(boundInclusive).or(this.before(boundInclusive));
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> after(final LocalDateTime boundExclusive)
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
			).or(
				this.is(
					new EqualsUntilPredicate(DAY_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(HOUR_INDEX, day -> day > boundExclusive.getHour()))
				)
			).or(
				this.is(
					new EqualsUntilPredicate(HOUR_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(MINUTE_INDEX, day -> day > boundExclusive.getMinute()))
				)
			).or(
				this.is(
					new EqualsUntilPredicate(MINUTE_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(SECOND_INDEX, day -> day > boundExclusive.getSecond()))
				)
			);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> afterEqual(final LocalDateTime boundInclusive)
		{
			if(boundInclusive == null)
			{
				throw new IllegalArgumentException("boundInclusive cannot be null");
			}
			
			return (Condition<S>)this.is(boundInclusive).or(this.after(boundInclusive));
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> between(final LocalDateTime startInclusive, final LocalDateTime endInclusive)
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
			final LocalDateTime other;
			
			EqualsUntilPredicate(final int maxSubKeyPosition, final LocalDateTime other)
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
			
			static int get(final LocalDateTime dateTime, final int subKeyPosition)
			{
				switch(subKeyPosition)
				{
					case YEAR_INDEX:
						return dateTime.getYear();
					case MONTH_INDEX:
						return dateTime.getMonthValue();
					case DAY_INDEX:
						return dateTime.getDayOfMonth();
					case HOUR_INDEX:
						return dateTime.getHour();
					case MINUTE_INDEX:
						return dateTime.getMinute();
					case SECOND_INDEX:
						return dateTime.getSecond();
					default:
						throw new IllegalArgumentException("Invalid subKeyPosition: " + subKeyPosition);
				}
			}
			
		}
		
	}
	
}
