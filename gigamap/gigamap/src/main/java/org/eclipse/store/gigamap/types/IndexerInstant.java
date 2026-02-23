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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.chrono.IsoChronology;
import java.util.function.IntPredicate;

/**
 * Indexing logic for {@link Instant} keys.
 * <p>
 * The Instant is decomposed into UTC date-time components (year, month, day, hour, minute, second)
 * for indexing. This enables component-level queries ({@code isYear()}, {@code isMonth()}, etc.)
 * as well as range queries ({@code before()}, {@code after()}, {@code between()}).
 * <p>
 * Nanosecond precision is not included in the index. Two Instants that differ only in their
 * nanosecond component will be treated as the same key.
 *
 * @param <E> the entity type
 *
 * @see IndexerDateTime
 */
public interface IndexerInstant<E> extends IndexerDateTime<E, Object[], Instant>
{

	/**
	 * Abstract base class for an {@link Instant} key {@link Indexer}.
	 *
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends HashingCompositeIndexer.AbstractSingleValueFixedSize<E, Instant> implements IndexerInstant<E>
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

		protected abstract Instant getInstant(E entity);

		@Override
		protected Instant getValue(final E entity)
		{
			return this.getInstant(entity);
		}

		@Override
		protected int compositeSize()
		{
			return 6;
		}

		@Override
		protected void fillCarrier(final Instant value, final Object[] carrier)
		{
			final OffsetDateTime utc = value.atOffset(ZoneOffset.UTC);
			carrier[YEAR_INDEX]   = utc.getYear();
			carrier[MONTH_INDEX]  = utc.getMonthValue();
			carrier[DAY_INDEX]    = utc.getDayOfMonth();
			carrier[HOUR_INDEX]   = utc.getHour();
			carrier[MINUTE_INDEX] = utc.getMinute();
			carrier[SECOND_INDEX] = utc.getSecond();
		}

		@Override
		public final <S extends E> Condition<S> is(final Instant other)
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

			return this.isValue(
				OffsetDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.UTC).toInstant()
			);
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
		public final <S extends E> Condition<S> before(final Instant boundExclusive)
		{
			if(boundExclusive == null)
			{
				throw new IllegalArgumentException("boundExclusive cannot be null");
			}

			final OffsetDateTime bound = boundExclusive.atOffset(ZoneOffset.UTC);

			return (Condition<S>)this.is(
				new FieldPredicate(YEAR_INDEX, year -> year < bound.getYear())
			).or(
				this.is(
					new EqualsUntilPredicate(YEAR_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(MONTH_INDEX, month -> month < bound.getMonthValue()))
				)
			).or(
				this.is(
					new EqualsUntilPredicate(MONTH_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(DAY_INDEX, day -> day < bound.getDayOfMonth()))
				)
			).or(
				this.is(
					new EqualsUntilPredicate(DAY_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(HOUR_INDEX, hour -> hour < bound.getHour()))
				)
			).or(
				this.is(
					new EqualsUntilPredicate(HOUR_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(MINUTE_INDEX, minute -> minute < bound.getMinute()))
				)
			).or(
				this.is(
					new EqualsUntilPredicate(MINUTE_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(SECOND_INDEX, second -> second < bound.getSecond()))
				)
			);
		}

		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> beforeEqual(final Instant boundInclusive)
		{
			if(boundInclusive == null)
			{
				throw new IllegalArgumentException("boundInclusive cannot be null");
			}

			return (Condition<S>)this.is(boundInclusive).or(this.before(boundInclusive));
		}

		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> after(final Instant boundExclusive)
		{
			if(boundExclusive == null)
			{
				throw new IllegalArgumentException("boundExclusive cannot be null");
			}

			final OffsetDateTime bound = boundExclusive.atOffset(ZoneOffset.UTC);

			return (Condition<S>)this.is(
				new FieldPredicate(YEAR_INDEX, year -> year > bound.getYear())
			).or(
				this.is(
					new EqualsUntilPredicate(YEAR_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(MONTH_INDEX, month -> month > bound.getMonthValue()))
				)
			).or(
				this.is(
					new EqualsUntilPredicate(MONTH_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(DAY_INDEX, day -> day > bound.getDayOfMonth()))
				)
			).or(
				this.is(
					new EqualsUntilPredicate(DAY_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(HOUR_INDEX, hour -> hour > bound.getHour()))
				)
			).or(
				this.is(
					new EqualsUntilPredicate(HOUR_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(MINUTE_INDEX, minute -> minute > bound.getMinute()))
				)
			).or(
				this.is(
					new EqualsUntilPredicate(MINUTE_INDEX, boundExclusive)
				).and(
					this.is(new FieldPredicate(SECOND_INDEX, second -> second > bound.getSecond()))
				)
			);
		}

		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> afterEqual(final Instant boundInclusive)
		{
			if(boundInclusive == null)
			{
				throw new IllegalArgumentException("boundInclusive cannot be null");
			}

			return (Condition<S>)this.is(boundInclusive).or(this.after(boundInclusive));
		}

		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> between(final Instant startInclusive, final Instant endInclusive)
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
			final Instant other;

			EqualsUntilPredicate(final int maxSubKeyPosition, final Instant other)
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

			static int get(final Instant instant, final int subKeyPosition)
			{
				final OffsetDateTime utc = instant.atOffset(ZoneOffset.UTC);
				switch(subKeyPosition)
				{
					case YEAR_INDEX:
						return utc.getYear();
					case MONTH_INDEX:
						return utc.getMonthValue();
					case DAY_INDEX:
						return utc.getDayOfMonth();
					case HOUR_INDEX:
						return utc.getHour();
					case MINUTE_INDEX:
						return utc.getMinute();
					case SECOND_INDEX:
						return utc.getSecond();
					default:
						throw new IllegalArgumentException("Invalid subKeyPosition: " + subKeyPosition);
				}
			}

		}

	}

}
