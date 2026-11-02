package test.eclipse.store.handler.other;

/*-
 * #%L
 * EclipseStore Integration Tests
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.*;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import test.eclipse.store.handler.AbstractHandlerTest;
import test.eclipse.store.handler.BinaryHandlerTestData;

class CalendarTest extends AbstractHandlerTest<CalendarTest.CalendarData>
{

    CalendarTest()
    {
        super(CalendarData.class);
    }

    @Override
    public void proveResult(CalendarData original, CalendarData copy)
    {
        assertAll(
                () -> assertEquals(original.getTimeZone(), copy.getTimeZone()),
                () -> assertEquals(original.getCalendar(), copy.getCalendar()),
                () -> assertEquals(original.getClock(), copy.getClock()),
                () -> assertEquals(original.getDuration(), copy.getDuration()),
                () -> assertEquals(original.getInstant(), copy.getInstant()),
                () -> assertEquals(original.getLocalDate(), copy.getLocalDate()),
                () -> assertEquals(original.getLocalDateTime(), copy.getLocalDateTime()),
                () -> assertEquals(original.getLocalTime(), copy.getLocalTime()),
                () -> assertEquals(original.getMonthDay(), copy.getMonthDay()),
                () -> assertEquals(original.getOffsetDateTime(), copy.getOffsetDateTime()),
                () -> assertEquals(original.getOffsetTime(), copy.getOffsetTime()),
                () -> assertEquals(original.getPeriod(), copy.getPeriod()),
                () -> assertEquals(original.getYear(), copy.getYear()),
                () -> assertEquals(original.getYearMonth(), copy.getYearMonth()),
                () -> assertEquals(original.getZonedDateTime(), copy.getZonedDateTime()),
                () -> assertEquals(original.getZoneId(), copy.getZoneId()),
                () -> assertEquals(original.getZoneOffset(), copy.getZoneOffset()),
                () -> assertEquals(original.getDayOfWeek(), copy.getDayOfWeek()),
                () -> assertEquals(original.getMonth(), copy.getMonth())
        );
    }

    public static class CalendarData implements BinaryHandlerTestData
    {

        TimeZone timeZone = TimeZone.getDefault();
        Calendar calendar;
        Clock clock;
        Duration duration;
        Instant instant;
        LocalDate localDate;
        LocalDateTime localDateTime;
        LocalTime localTime;
        MonthDay monthDay;
        OffsetDateTime offsetDateTime;
        OffsetTime offsetTime;
        Period period;
        Year year;
        YearMonth yearMonth;
        ZonedDateTime zonedDateTime;
        ZoneId zoneId;
        ZoneOffset zoneOffset;

        DayOfWeek dayOfWeek;
        Month month;

        @Override
        public void fillSampleData()
        {
            timeZone = TimeZone.getTimeZone("Europe/Copenhagen");
            calendar = new GregorianCalendar();
            clock = Clock.systemUTC();
            duration = Duration.ofDays(10);
            instant = Instant.MAX;
            localDate = LocalDate.now();
            localDateTime = LocalDateTime.now();
            localTime = LocalTime.now();
            monthDay = MonthDay.now();
            offsetDateTime = OffsetDateTime.now();
            offsetTime = OffsetTime.now();
            period = Period.ofDays(7);
            year = Year.now();
            yearMonth = YearMonth.now();
            zonedDateTime = ZonedDateTime.now();
            zoneId = ZoneId.systemDefault();
            zoneOffset = ZoneOffset.ofHours(5);
            dayOfWeek = DayOfWeek.FRIDAY;
            month = Month.DECEMBER;
        }

        TimeZone getTimeZone()
        {
            return timeZone;
        }

        Calendar getCalendar()
        {
            return calendar;
        }

        Clock getClock()
        {
            return clock;
        }

        Duration getDuration()
        {
            return duration;
        }

        Instant getInstant()
        {
            return instant;
        }

        LocalDate getLocalDate()
        {
            return localDate;
        }

        LocalDateTime getLocalDateTime()
        {
            return localDateTime;
        }

        LocalTime getLocalTime()
        {
            return localTime;
        }

        MonthDay getMonthDay()
        {
            return monthDay;
        }

        OffsetDateTime getOffsetDateTime()
        {
            return offsetDateTime;
        }

        OffsetTime getOffsetTime()
        {
            return offsetTime;
        }

        Period getPeriod()
        {
            return period;
        }

        Year getYear()
        {
            return year;
        }

        YearMonth getYearMonth()
        {
            return yearMonth;
        }

        ZonedDateTime getZonedDateTime()
        {
            return zonedDateTime;
        }

        ZoneId getZoneId()
        {
            return zoneId;
        }

        ZoneOffset getZoneOffset()
        {
            return zoneOffset;
        }

        DayOfWeek getDayOfWeek()
        {
            return dayOfWeek;
        }

        Month getMonth()
        {
            return month;
        }

    }

}
