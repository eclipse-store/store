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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;

public class LocalDateTimeData implements BinaryHandlerTestData
{

    private LocalDateTime value = LocalDateTime.MAX;

    // ===== proposed edge-cases (review & cherry-pick) =====
    // LocalDateTime aggregates LocalDate (year, month, day) and LocalTime (hour, minute, second,
    // nano) — 7 fields total. equals() is value-based across all 7. The probes target the
    // boundary values (MIN/MAX), the nanosecond precision, and proleptic-Gregorian quirks
    // (leap day, negative year).
    private LocalDateTime minLocalDateTime;
    private LocalDateTime maxLocalDateTime;
    private LocalDateTime epochStart;
    private LocalDateTime withNanos;
    private LocalDateTime leapDay;
    private LocalDateTime negativeYear;

    @Override
    public BinaryHandlerTestData fillSampleData()
    {
        this.value = LocalDateTime.of(2020, 1, 1, 1, 1, 0);

        // ===== proposed edge-cases =====
        minLocalDateTime = LocalDateTime.MIN;
        maxLocalDateTime = LocalDateTime.MAX;
        epochStart = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
        withNanos = LocalDateTime.of(2024, 6, 15, 12, 30, 45, 999_999_999);
        leapDay = LocalDateTime.of(2024, 2, 29, 12, 0, 0);
        negativeYear = LocalDateTime.of(-44, 3, 15, 12, 0, 0);

        return this;
    }

    @Override
    public void proveResults(Object o)
    {
        LocalDateTimeData copy = (LocalDateTimeData) o;
        assertAll("LocalDateTime tests",
                () -> assertEquals(this.value, copy.getValue()),

                // ===== proposed edge-case verifications =====
                () -> {
                    if (this.minLocalDateTime != null) {
                        assertEquals(LocalDateTime.MIN, copy.getMinLocalDateTime(), "LocalDateTime.MIN (-999999999-01-01T00:00)");
                    } else {
                        assertNull(copy.getMinLocalDateTime());
                    }
                },
                () -> {
                    if (this.maxLocalDateTime != null) {
                        assertEquals(LocalDateTime.MAX, copy.getMaxLocalDateTime(), "LocalDateTime.MAX (+999999999-12-31T23:59:59.999999999)");
                    } else {
                        assertNull(copy.getMaxLocalDateTime());
                    }
                },
                () -> {
                    if (this.epochStart != null) {
                        assertEquals(LocalDateTime.of(1970, 1, 1, 0, 0, 0), copy.getEpochStart(), "Unix epoch start");
                    } else {
                        assertNull(copy.getEpochStart());
                    }
                },
                () -> {
                    if (this.withNanos != null) {
                        assertEquals(LocalDateTime.of(2024, 6, 15, 12, 30, 45, 999_999_999), copy.getWithNanos(), "nanosecond precision");
                        assertEquals(999_999_999, copy.getWithNanos().getNano(), "nanos preserved exactly");
                    } else {
                        assertNull(copy.getWithNanos());
                    }
                },
                () -> {
                    if (this.leapDay != null) {
                        assertEquals(LocalDateTime.of(2024, 2, 29, 12, 0, 0), copy.getLeapDay(), "leap day 2024-02-29");
                    } else {
                        assertNull(copy.getLeapDay());
                    }
                },
                () -> {
                    if (this.negativeYear != null) {
                        assertEquals(LocalDateTime.of(-44, 3, 15, 12, 0, 0), copy.getNegativeYear(), "negative year (BCE in proleptic Gregorian)");
                        assertEquals(-44, copy.getNegativeYear().getYear(), "year is exactly -44");
                    } else {
                        assertNull(copy.getNegativeYear());
                    }
                }
        );
    }

    public LocalDateTime getValue()
    {
        return value;
    }

    // ===== proposed edge-cases — getters =====

    public LocalDateTime getMinLocalDateTime() {
        return minLocalDateTime;
    }

    public LocalDateTime getMaxLocalDateTime() {
        return maxLocalDateTime;
    }

    public LocalDateTime getEpochStart() {
        return epochStart;
    }

    public LocalDateTime getWithNanos() {
        return withNanos;
    }

    public LocalDateTime getLeapDay() {
        return leapDay;
    }

    public LocalDateTime getNegativeYear() {
        return negativeYear;
    }
}
