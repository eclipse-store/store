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

import java.time.LocalDate;

public class LocalDateData implements BinaryHandlerTestData
{

    private LocalDate value = LocalDate.MAX;

    // ===== proposed edge-cases (review & cherry-pick) =====
    // LocalDate holds (year, month, day) — proleptic-Gregorian. equals() is value-based on all
    // three. Probes target MIN/MAX year boundaries, the Unix epoch, leap day, and negative year.
    private LocalDate minLocalDate;
    private LocalDate maxLocalDate;
    private LocalDate epochDate;
    private LocalDate leapDay;
    private LocalDate negativeYear;

    @Override
    public BinaryHandlerTestData fillSampleData()
    {
        this.value = LocalDate.of(2020, 1, 1);

        // ===== proposed edge-cases =====
        minLocalDate = LocalDate.MIN;
        maxLocalDate = LocalDate.MAX;
        epochDate = LocalDate.of(1970, 1, 1);
        leapDay = LocalDate.of(2024, 2, 29);
        negativeYear = LocalDate.of(-44, 3, 15);

        return this;
    }

    @Override
    public void proveResults(Object o)
    {
        LocalDateData copy = (LocalDateData) o;
        assertAll("LocalDate tests",
                () -> assertEquals(this.value, copy.getValue()),

                // ===== proposed edge-case verifications =====
                () -> {
                    if (this.minLocalDate != null) {
                        assertEquals(LocalDate.MIN, copy.getMinLocalDate(), "LocalDate.MIN (-999999999-01-01)");
                    } else {
                        assertNull(copy.getMinLocalDate());
                    }
                },
                () -> {
                    if (this.maxLocalDate != null) {
                        assertEquals(LocalDate.MAX, copy.getMaxLocalDate(), "LocalDate.MAX (+999999999-12-31)");
                    } else {
                        assertNull(copy.getMaxLocalDate());
                    }
                },
                () -> {
                    if (this.epochDate != null) {
                        assertEquals(LocalDate.of(1970, 1, 1), copy.getEpochDate(), "Unix epoch date");
                    } else {
                        assertNull(copy.getEpochDate());
                    }
                },
                () -> {
                    if (this.leapDay != null) {
                        assertEquals(LocalDate.of(2024, 2, 29), copy.getLeapDay(), "leap day 2024-02-29");
                    } else {
                        assertNull(copy.getLeapDay());
                    }
                },
                () -> {
                    if (this.negativeYear != null) {
                        assertEquals(LocalDate.of(-44, 3, 15), copy.getNegativeYear(), "negative year (BCE in proleptic Gregorian)");
                        assertEquals(-44, copy.getNegativeYear().getYear(), "year is exactly -44");
                    } else {
                        assertNull(copy.getNegativeYear());
                    }
                }
        );
    }

    public LocalDate getValue()
    {
        return value;
    }

    // ===== proposed edge-cases — getters =====

    public LocalDate getMinLocalDate() {
        return minLocalDate;
    }

    public LocalDate getMaxLocalDate() {
        return maxLocalDate;
    }

    public LocalDate getEpochDate() {
        return epochDate;
    }

    public LocalDate getLeapDay() {
        return leapDay;
    }

    public LocalDate getNegativeYear() {
        return negativeYear;
    }
}
