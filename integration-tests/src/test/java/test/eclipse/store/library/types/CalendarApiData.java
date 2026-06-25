package test.eclipse.store.library.types;

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

import org.junit.jupiter.api.Assertions;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CalendarApiData implements BinaryHandlerTestData {

    TimeZone timeZone = TimeZone.getDefault();
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

    // corner-case / extra fields
    Instant instantMin;
    Instant instantEpoch;
    LocalDate leapDay;
    ZonedDateTime dstTransition;
    TimeZone timeZoneExtreme;
    Duration negativeDuration;
    Period largePeriod;
    OffsetDateTime offsetPlus14;
    OffsetDateTime offsetMinus12;
    Clock systemClock;
    Year distantYear;


    @Override
    public BinaryHandlerTestData fillSampleData() {

        this.timeZone = TimeZone.getTimeZone("Europe/Copenhagen");

        LocalDateTime  datetime = LocalDateTime.of(2020, 5, 24, 14, 0);
        Instant instant = ZonedDateTime.of(datetime,ZoneId.of("UTC")).toInstant();
        this.clock = Clock.fixed(instant, ZoneId.of("UTC"));

        this.duration = Duration.ofDays(10);
        this.instant = Instant.MAX;
        this.localDate = LocalDate.now();
        this.localDateTime = LocalDateTime.of(2020, 10, 9, 11, 52, 41);
        this.localTime = LocalTime.of(8, 53, 50, 410420);
        this.monthDay = MonthDay.now();
        this.offsetDateTime = OffsetDateTime.of(2020, 11, 21, 11, 52, 12, 450450, ZoneOffset.UTC);
        this.offsetTime = OffsetTime.of(12, 31, 50, 740740, ZoneOffset.UTC);
        this.period = Period.ofDays(7);
        this.year = Year.now();
        this.yearMonth = YearMonth.now();
        this.zonedDateTime = ZonedDateTime.of(2020,12,5,11,23,50,898989,ZoneId.of("UTC"));
        this.zoneId = ZoneId.of("UTC");
        this.zoneOffset = ZoneOffset.ofHours(5);
        this.dayOfWeek = DayOfWeek.FRIDAY;
        this.month = Month.DECEMBER;

        // corner-case initializations
        this.instantMin = Instant.MIN;
        this.instantEpoch = Instant.EPOCH;
        this.leapDay = LocalDate.of(2000, 2, 29);
        // choose a DST transition time (may be adjusted by zone rules)
        this.dstTransition = ZonedDateTime.of(LocalDateTime.of(2020,3,29,2,30), ZoneId.of("Europe/Copenhagen"));
        this.timeZoneExtreme = TimeZone.getTimeZone("Pacific/Kiritimati"); // UTC+14
        this.negativeDuration = Duration.ofHours(-5);
        this.largePeriod = Period.ofMonths(18);
        this.offsetPlus14 = OffsetDateTime.of(2020,1,1,12,0,0,0, ZoneOffset.ofHours(14));
        this.offsetMinus12 = OffsetDateTime.of(2020,1,1,12,0,0,0, ZoneOffset.ofHours(-12));
        this.systemClock = Clock.system(ZoneId.of("UTC"));
        this.distantYear = Year.of(9999);

        return this;
    }

    @Override
    public BinaryHandlerTestData updateSampleData() {
        // mutate some values to ensure update detection
        this.timeZone = TimeZone.getTimeZone("UTC");
        this.clock = Clock.system(ZoneId.of("UTC"));
        this.duration = this.duration.plusDays(5);
        this.instant = Instant.parse("1970-01-01T00:00:00Z");
        this.localDate = this.localDate.plusDays(1);
        this.localDateTime = this.localDateTime.plusHours(1);
        this.localTime = this.localTime.plusMinutes(3);
        this.offsetDateTime = this.offsetDateTime.plusDays(1);
        this.period = this.period.plusMonths(1);
        this.year = this.year.plusYears(1);
        this.yearMonth = this.yearMonth.plusMonths(1);
        this.zonedDateTime = this.zonedDateTime.plusDays(2);
        this.zoneId = ZoneId.of("UTC");
        this.zoneOffset = ZoneOffset.ofHours(0);
        this.dayOfWeek = this.dayOfWeek.plus(1);
        this.month = this.month.plus(1);

        // update corner-cases
        this.instantMin = this.instantMin.plusSeconds(1);
        this.instantEpoch = this.instantEpoch.plusSeconds(3600);
        this.leapDay = this.leapDay.plusYears(4); // next leap
        this.dstTransition = this.dstTransition.plusDays(365);
        this.timeZoneExtreme = TimeZone.getTimeZone("Pacific/Midway"); // UTC-11
        this.negativeDuration = this.negativeDuration.minusHours(5); // more negative
        this.largePeriod = this.largePeriod.minusMonths(3);
        this.offsetPlus14 = this.offsetPlus14.minusHours(1);
        this.offsetMinus12 = this.offsetMinus12.plusHours(1);
        this.systemClock = Clock.systemDefaultZone();
        this.distantYear = this.distantYear.minusYears(1);

        return this;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        CalendarApiData copy = (CalendarApiData) o;
        assertAll(
                () -> assertEquals(this.getTimeZone().toZoneId(), copy.getTimeZone().toZoneId(), "TimeZone"),
				() -> {
					if (this.getClock() == null) {
						Assertions.assertNull(copy.getClock(), "Clock");
					} else {
						assertEquals(this.getClock().toString(), copy.getClock().toString(), "Clock");
					}
				},
                () -> assertEquals(this.getDuration(), copy.getDuration(), "Duration"),
                () -> assertEquals(this.getInstant(), copy.getInstant(), "Instant"),
                () -> assertEquals(this.getLocalDate(), copy.getLocalDate(), "LocalDate"),
                () -> assertEquals(this.getLocalDateTime(), copy.getLocalDateTime(), "LocalDateTime"),
                () -> assertEquals(this.getLocalTime(), copy.getLocalTime(), "LocalTime"),
                () -> assertEquals(this.getMonthDay(), copy.getMonthDay(), "MonthDay"),
                () -> assertEquals(this.getOffsetDateTime(), copy.getOffsetDateTime(), "OffsetDateTime"),
                () -> assertEquals(this.getOffsetTime(), copy.getOffsetTime(), "OffsetTime"),
                () -> assertEquals(this.getPeriod(), copy.getPeriod(), "Period"),
                () -> assertEquals(this.getYear(), copy.getYear(), "Year"),
                () -> assertEquals(this.getYearMonth(), copy.getYearMonth(), "YearMonth"),
                () -> assertEquals(this.getZonedDateTime(), copy.getZonedDateTime(), "ZoneDateTime"),
                () -> assertEquals(this.getZoneId(), copy.getZoneId(), "ZoneId"),
                () -> assertEquals(this.getZoneOffset(), copy.getZoneOffset(), "ZoneOffset"),
                () -> assertEquals(this.getDayOfWeek(), copy.getDayOfWeek(), "DayOfTheWeek"),
                () -> assertEquals(this.getMonth(), copy.getMonth(), "Month")
        );

        // corner-case verifications with guards
        if (this.instantMin == null) {
            Assertions.assertNull(copy.instantMin);
        } else {
            assertEquals(this.instantMin, copy.instantMin, "instantMin");
        }

        if (this.instantEpoch == null) {
            Assertions.assertNull(copy.instantEpoch);
        } else {
            assertEquals(this.instantEpoch, copy.instantEpoch, "instantEpoch");
        }

        if (this.leapDay == null) {
            Assertions.assertNull(copy.leapDay);
        } else {
            assertEquals(this.leapDay, copy.leapDay, "leapDay");
        }

        if (this.dstTransition == null) {
            Assertions.assertNull(copy.dstTransition);
        } else {
            assertEquals(this.dstTransition.toInstant(), copy.dstTransition.toInstant(), "dstTransition instant parity");
            assertEquals(this.dstTransition.getZone(), copy.dstTransition.getZone(), "dstTransition zone parity");
        }

        if (this.timeZoneExtreme == null) {
            Assertions.assertNull(copy.timeZoneExtreme);
        } else {
            assertEquals(this.timeZoneExtreme.toZoneId(), copy.timeZoneExtreme.toZoneId(), "timeZoneExtreme");
        }

        if (this.negativeDuration == null) {
            Assertions.assertNull(copy.negativeDuration);
        } else {
            assertEquals(this.negativeDuration, copy.negativeDuration, "negativeDuration");
        }

        if (this.largePeriod == null) {
            Assertions.assertNull(copy.largePeriod);
        } else {
            assertEquals(this.largePeriod, copy.largePeriod, "largePeriod");
        }

        if (this.offsetPlus14 == null) {
            Assertions.assertNull(copy.offsetPlus14);
        } else {
            assertEquals(this.offsetPlus14, copy.offsetPlus14, "offsetPlus14");
        }

        if (this.offsetMinus12 == null) {
            Assertions.assertNull(copy.offsetMinus12);
        } else {
            assertEquals(this.offsetMinus12, copy.offsetMinus12, "offsetMinus12");
        }

        if (this.systemClock == null) {
            Assertions.assertNull(copy.systemClock);
        } else {
            // compare string form to avoid implementation-specific equality
            assertEquals(this.systemClock.toString(), copy.systemClock.toString(), "systemClock");
        }

        if (this.distantYear == null) {
            Assertions.assertNull(copy.distantYear);
        } else {
            assertEquals(this.distantYear, copy.distantYear, "distantYear");
        }
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public Clock getClock() {
        return clock;
    }

    public Duration getDuration() {
        return duration;
    }

    public Instant getInstant() {
        return instant;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public LocalDateTime getLocalDateTime() {
        return localDateTime;
    }

    public LocalTime getLocalTime() {
        return localTime;
    }

    public MonthDay getMonthDay() {
        return monthDay;
    }

    public OffsetDateTime getOffsetDateTime() {
        return offsetDateTime;
    }

    public OffsetTime getOffsetTime() {
        return offsetTime;
    }

    public Period getPeriod() {
        return period;
    }

    public Year getYear() {
        return year;
    }

    public YearMonth getYearMonth() {
        return yearMonth;
    }

    public ZonedDateTime getZonedDateTime() {
        return zonedDateTime;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public ZoneOffset getZoneOffset() {
        return zoneOffset;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public Month getMonth() {
        return month;
    }

    // getters for corner-case fields
    public Instant getInstantMin() { return instantMin; }
    public Instant getInstantEpoch() { return instantEpoch; }
    public LocalDate getLeapDay() { return leapDay; }
    public ZonedDateTime getDstTransition() { return dstTransition; }
    public TimeZone getTimeZoneExtreme() { return timeZoneExtreme; }
    public Duration getNegativeDuration() { return negativeDuration; }
    public Period getLargePeriod() { return largePeriod; }
    public OffsetDateTime getOffsetPlus14() { return offsetPlus14; }
    public OffsetDateTime getOffsetMinus12() { return offsetMinus12; }
    public Clock getSystemClock() { return systemClock; }
    public Year getDistantYear() { return distantYear; }
 }
