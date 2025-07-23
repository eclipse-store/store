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

import java.time.LocalTime;
import java.util.function.IntPredicate;

/**
 * Indexing logic for {@link LocalTime} keys.
 * 
 * @param <E> the entity type
 * 
 * @see IndexerTime
 */
public interface IndexerLocalTime<E> extends IndexerTime<E, Object[], LocalTime>
{

	/**
	 * Abstract base class for a {@link LocalTime} key {@link Indexer}.
	 *
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends HashingCompositeIndexer.AbstractSingleValueFixedSize<E, LocalTime> implements IndexerLocalTime<E>
	{
		private final static int HOUR_INDEX  = 0;
		private final static int MINUTE_INDEX = 1;
		private final static int SECOND_INDEX   = 2;
		
		protected Abstract()
		{
			super();
		}
		
		protected abstract LocalTime getLocalTime(E entity);
		
		@Override
		protected LocalTime getValue(final E entity)
		{
			return this.getLocalTime(entity);
		}
		
		@Override
		protected int compositeSize()
		{
			return 3;
		}
		
		@Override
		protected void fillCarrier(final LocalTime value, final Object[] carrier)
		{
			carrier[HOUR_INDEX]   = value.getHour();
			carrier[MINUTE_INDEX] = value.getMinute();
			carrier[SECOND_INDEX] = value.getSecond();
		}
		
		@Override
		public final <S extends E> Condition<S> is(final LocalTime other)
		{
			return other == null
				? this.isNull()
				: this.isValue(other)
			;
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
		
		@Override
		public final <S extends E> Condition<S> isTime(final int hour, final int minute, final int second)
		{
			Validators.validateHour(hour);
			Validators.validateMinute(minute);
			Validators.validateSecond(second);
			
			return this.isValue(LocalTime.of(hour, minute, second));
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
		public final <S extends E> Condition<S> before(final LocalTime boundExclusive)
		{
			if(boundExclusive == null)
			{
				throw new IllegalArgumentException("boundExclusive cannot be null");
			}
			
			return (Condition<S>)this.is(
				new FieldPredicate(HOUR_INDEX, hour -> hour < boundExclusive.getHour())
			).or(
				this.is(
					new EqualsUntilPredicate(HOUR_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(MINUTE_INDEX, minute -> minute < boundExclusive.getMinute()))
				)
			).or(
				this.is(
					new EqualsUntilPredicate(MINUTE_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(SECOND_INDEX, second -> second < boundExclusive.getSecond()))
				)
			);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> beforeEqual(final LocalTime boundInclusive)
		{
			if(boundInclusive == null)
			{
				throw new IllegalArgumentException("boundInclusive cannot be null");
			}
			
			return (Condition<S>)this.is(boundInclusive).or(this.before(boundInclusive));
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> after(final LocalTime boundExclusive)
		{
			if(boundExclusive == null)
			{
				throw new IllegalArgumentException("boundExclusive cannot be null");
			}
			
			return (Condition<S>)this.is(
				new FieldPredicate(HOUR_INDEX, hour -> hour > boundExclusive.getHour())
			).or(
				this.is(
					new EqualsUntilPredicate(HOUR_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(MINUTE_INDEX, minute -> minute > boundExclusive.getMinute()))
				)
			).or(
				this.is(
					new EqualsUntilPredicate(MINUTE_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(SECOND_INDEX, second -> second > boundExclusive.getSecond()))
				)
			);
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> afterEqual(final LocalTime boundInclusive)
		{
			if(boundInclusive == null)
			{
				throw new IllegalArgumentException("boundInclusive cannot be null");
			}
			
			return (Condition<S>)this.is(boundInclusive).or(this.after(boundInclusive));
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> between(final LocalTime startInclusive, final LocalTime endInclusive)
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
			final LocalTime other;
			
			EqualsUntilPredicate(final int maxSubKeyPosition, final LocalTime other)
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
			
			static int get(final LocalTime date, final int subKeyPosition)
			{
				switch(subKeyPosition)
				{
					case HOUR_INDEX:
						return date.getHour();
					case MINUTE_INDEX:
						return date.getMinute();
					case SECOND_INDEX:
						return date.getSecond();
					default:
						throw new IllegalArgumentException("Invalid subKeyPosition: " + subKeyPosition);
				}
			}
			
		}
		
	}
	
}
