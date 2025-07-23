package org.eclipse.store.gigamap.query;

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

import org.eclipse.store.gigamap.types.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DateTests
{
	static class DateTimeEntity
	{
		final LocalDateTime dateTime;

		DateTimeEntity(final LocalDateTime dateTime)
		{
			super();
			this.dateTime = dateTime;
		}
		
		@Override
		public String toString()
		{
			return this.dateTime.toString();
		}
	}
	
	static class DateEntity
	{
		final LocalDate date;

		DateEntity(final LocalDate date)
		{
			super();
			this.date = date;
		}
		
		@Override
		public String toString()
		{
			return this.date.toString();
		}
	}
	
	static class TimeEntity
	{
		final LocalTime time;

		TimeEntity(final LocalTime time)
		{
			super();
			this.time = time;
		}
		
		@Override
		public String toString()
		{
			return this.time.toString();
		}
	}

	static class YearMonthEntity
	{
		final YearMonth yearMonth;

		YearMonthEntity(final YearMonth yearMonth)
		{
			super();
			this.yearMonth = yearMonth;
		}
		
		@Override
		public String toString()
		{
			return this.yearMonth.toString();
		}
	}
	
	
	final static IndexerLocalDateTime<DateTimeEntity> dateTimeIndex = new IndexerLocalDateTime.Abstract<>()
	{
		@Override
		protected LocalDateTime getLocalDateTime(final DateTimeEntity entity)
		{
			return entity.dateTime;
		}
	};

	final static IndexerLocalDate<DateEntity> dateIndex = new IndexerLocalDate.Abstract<>()
	{
		@Override
		protected LocalDate getLocalDate(final DateEntity entity)
		{
			return entity.date;
		}
	};
	
	final static IndexerLocalTime<TimeEntity> timeIndex = new IndexerLocalTime.Abstract<>()
	{
		@Override
		protected LocalTime getLocalTime(final TimeEntity entity)
		{
			return entity.time;
		}
	};

	final static IndexerYearMonth<YearMonthEntity> yearMonthIndex = new IndexerYearMonth.Abstract<>()
	{
		@Override
		protected YearMonth getYearMonth(final YearMonthEntity entity)
		{
			return entity.yearMonth;
		}
	};
	
	
	@Test
	void dateTimeTests()
	{
		final GigaMap<DateTimeEntity> map = GigaMap.New();
		
		map.index().bitmap().add(dateTimeIndex);

		map.add(new DateTimeEntity(LocalDateTime.of(-1000,  1, 1, 12, 12, 12)));
		
		map.add(new DateTimeEntity(LocalDateTime.of(2020,  1, 1, 13, 1, 0)));
		map.add(new DateTimeEntity(LocalDateTime.of(2020,  4, 2, 14, 59, 0)));
		map.add(new DateTimeEntity(LocalDateTime.of(2020,  8, 3, 15, 30, 0)));
		map.add(new DateTimeEntity(LocalDateTime.of(2020, 12, 4, 0, 0, 0)));
		
		map.add(new DateTimeEntity(LocalDateTime.of(2021,  1, 10, 13, 1, 0)));
		map.add(new DateTimeEntity(LocalDateTime.of(2021,  4, 11, 14, 59, 0)));
		map.add(new DateTimeEntity(LocalDateTime.of(2021,  8, 12, 15, 30, 0)));
		map.add(new DateTimeEntity(LocalDateTime.of(2021, 12, 13, 0, 0, 0)));
		
		assertEquals(
			1,
			map.query(dateTimeIndex.before(LocalDateTime.of(0, 1, 1, 0, 0))).count()
		);
		assertEquals(
			8,
			map.query(dateTimeIndex.after(LocalDateTime.of(0, 1, 1, 0, 0))).count()
		);
		assertEquals(
			4,
			map.query(dateTimeIndex.between(LocalDateTime.of(2020, 6, 1, 0, 0), LocalDateTime.of(2021, 8, 12, 9, 0))).count()
		);
		assertEquals(
			2,
			map.query(dateTimeIndex.isMonth(8)).count()
		);
		assertEquals(
			5,
			map.query(dateTimeIndex.isYear(year -> year % 2 == 0)).count()
		);
		assertEquals(
			4,
			map.query(dateTimeIndex.isLeapYear()).count()
		);
	}
		
	@Test
	void dateTests()
	{
		final GigaMap<DateEntity>       map     = GigaMap.New();
		
		map.index().bitmap().add(dateIndex);

		map.add(new DateEntity(LocalDate.of(-1000,  1, 1)));
		
		map.add(new DateEntity(LocalDate.of(2020,  1, 1)));
		map.add(new DateEntity(LocalDate.of(2020,  4, 2)));
		map.add(new DateEntity(LocalDate.of(2020,  8, 3)));
		map.add(new DateEntity(LocalDate.of(2020, 12, 4)));
		
		map.add(new DateEntity(LocalDate.of(2021,  1, 10)));
		map.add(new DateEntity(LocalDate.of(2021,  4, 11)));
		map.add(new DateEntity(LocalDate.of(2021,  8, 12)));
		map.add(new DateEntity(LocalDate.of(2021, 12, 13)));
		
		assertEquals(
			1,
			map.query(dateIndex.before(LocalDate.of(0, 1, 1))).count()
		);
		assertEquals(
			8,
			map.query(dateIndex.after(LocalDate.of(0, 1, 1))).count()
		);
		assertEquals(
			5,
			map.query(dateIndex.between(LocalDate.of(2020, 6, 1), LocalDate.of(2021, 8, 12))).count()
		);
		assertEquals(
			2,
			map.query(dateIndex.isMonth(8)).count()
		);
		assertEquals(
			5,
			map.query(dateIndex.isYear(year -> year % 2 == 0)).count()
		);
		assertEquals(
			4,
			map.query(dateIndex.isLeapYear()).count()
		);
	}
		
	@Test
	void timeTests()
	{
		final GigaMap<TimeEntity>       map     = GigaMap.New();
		
		map.index().bitmap().add(timeIndex);

		map.add(new TimeEntity(LocalTime.of(0, 0, 0)));
		map.add(new TimeEntity(LocalTime.of(10, 5, 0)));
		map.add(new TimeEntity(LocalTime.of(13, 1, 10)));
		map.add(new TimeEntity(LocalTime.of(14, 59, 20)));
		map.add(new TimeEntity(LocalTime.of(15, 30, 30)));
		map.add(new TimeEntity(LocalTime.of(23, 59, 56)));
		
		assertEquals(
			2,
			map.query(timeIndex.before(LocalTime.of(12, 0, 0))).count()
		);
		assertEquals(
			4,
			map.query(timeIndex.after(LocalTime.of(12, 0, 0))).count()
		);
		assertEquals(
			3,
			map.query(timeIndex.between(LocalTime.of(12, 0, 0), LocalTime.of(23, 59, 55))).count()
		);
		assertEquals(
			2,
			map.query(timeIndex.isSecond(0)).count()
		);
		assertEquals(
			5,
			map.query(timeIndex.isSecond(second -> second % 10 == 0)).count()
		);
	}
		
	@Test
	void yearMonthTests()
	{
		final GigaMap<YearMonthEntity>       map     = GigaMap.New();
		
		map.index().bitmap().add(yearMonthIndex);

		map.add(new YearMonthEntity(YearMonth.of(-1000,  1)));

		map.add(new YearMonthEntity(YearMonth.of(2020,  1)));
		map.add(new YearMonthEntity(YearMonth.of(2020,  4)));
		map.add(new YearMonthEntity(YearMonth.of(2020,  8)));
		map.add(new YearMonthEntity(YearMonth.of(2020, 12)));
		
		map.add(new YearMonthEntity(YearMonth.of(2021,  1)));
		map.add(new YearMonthEntity(YearMonth.of(2021,  4)));
		map.add(new YearMonthEntity(YearMonth.of(2021,  8)));
		map.add(new YearMonthEntity(YearMonth.of(2021, 12)));
		
		assertEquals(
			1,
			map.query(yearMonthIndex.before(YearMonth.of(0, 1))).count()
		);
		assertEquals(
			8,
			map.query(yearMonthIndex.after(YearMonth.of(0, 1))).count()
		);
		assertEquals(
			5,
			map.query(yearMonthIndex.between(YearMonth.of(2020, 6), YearMonth.of(2021, 8))).count()
		);
		assertEquals(
			2,
			map.query(yearMonthIndex.isMonth(8)).count()
		);
		assertEquals(
			5,
			map.query(yearMonthIndex.isYear(year -> year % 2 == 0)).count()
		);
		assertEquals(
			4,
			map.query(yearMonthIndex.isLeapYear()).count()
		);
	}
	
}
