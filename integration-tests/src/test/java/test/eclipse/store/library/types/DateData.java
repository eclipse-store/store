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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DateData implements BinaryHandlerTestData {
    private Date date;

    // ===== proposed edge-cases (review & cherry-pick) =====
    // java.util.Date is backed by a single long (millis since 1970-01-01T00:00:00 UTC). The handler
    // should preserve that long verbatim — including extreme boundaries, sub-second component, and
    // pre-epoch (negative) values. Equality is checked via getTime() (long-on-long); identity is not
    // preserved across round-trip and is not asserted.
    private Date epoch;
    private Date longMin;
    private Date longMax;
    private Date preEpoch;
    private Date subSecond;
    private Date farPast;
    private Date farFuture;

    public DateData() {
        try {
            date = new SimpleDateFormat("dd/MM/yyyy").parse("31/12/1998");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DateData fillSampleData() {
        String sDate1 = "31/12/2020";
        try {
            date = new SimpleDateFormat("dd/MM/yyyy").parse(sDate1);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        // ===== proposed edge-cases =====
        epoch = new Date(0L);
        longMin = new Date(Long.MIN_VALUE);
        longMax = new Date(Long.MAX_VALUE);
        preEpoch = buildDate(1900, Calendar.JUNE, 15, 12, 0, 0, 0);
        subSecond = new Date(buildDate(2024, Calendar.MARCH, 10, 14, 30, 45, 0).getTime() + 999L);
        farPast = buildDate(1000, Calendar.JANUARY, 1, 0, 0, 0, 0);
        farFuture = buildDate(9999, Calendar.DECEMBER, 31, 23, 59, 59, 999);

        return this;
    }

    Date getDate() {
        return date;
    }

    // ===== proposed edge-cases — getters =====

    public Date getEpoch() {
        return epoch;
    }

    public Date getLongMin() {
        return longMin;
    }

    public Date getLongMax() {
        return longMax;
    }

    public Date getPreEpoch() {
        return preEpoch;
    }

    public Date getSubSecond() {
        return subSecond;
    }

    public Date getFarPast() {
        return farPast;
    }

    public Date getFarFuture() {
        return farFuture;
    }

    private static Date buildDate(int year, int month, int day, int hour, int minute, int second, int millis) {
        Calendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(year, month, day, hour, minute, second);
        c.set(Calendar.MILLISECOND, millis);
        return c.getTime();
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        DateData copy = (DateData) o;
        assertAll("Date tests",
                () -> assertEquals(this.getDate().getTime(), copy.getDate().getTime()),

                // ===== proposed edge-case verifications =====
                // Date round-trip is long-on-long via getTime().
                () -> {
                    if (this.getEpoch() != null) {
                        assertEquals(0L, copy.getEpoch().getTime(), "epoch (1970-01-01T00:00:00.000 UTC)");
                    } else {
                        assertNull(copy.getEpoch());
                    }
                },
                () -> {
                    if (this.getLongMin() != null) {
                        assertEquals(Long.MIN_VALUE, copy.getLongMin().getTime(), "Long.MIN_VALUE millis");
                    } else {
                        assertNull(copy.getLongMin());
                    }
                },
                () -> {
                    if (this.getLongMax() != null) {
                        assertEquals(Long.MAX_VALUE, copy.getLongMax().getTime(), "Long.MAX_VALUE millis");
                    } else {
                        assertNull(copy.getLongMax());
                    }
                },
                () -> {
                    if (this.getPreEpoch() != null) {
                        assertEquals(this.getPreEpoch().getTime(), copy.getPreEpoch().getTime(), "pre-epoch (negative millis)");
                    } else {
                        assertNull(copy.getPreEpoch());
                    }
                },
                () -> {
                    if (this.getSubSecond() != null) {
                        assertEquals(this.getSubSecond().getTime(), copy.getSubSecond().getTime(), "millisecond precision (xxx.999)");
                    } else {
                        assertNull(copy.getSubSecond());
                    }
                },
                () -> {
                    if (this.getFarPast() != null) {
                        assertEquals(this.getFarPast().getTime(), copy.getFarPast().getTime(), "far past (year 1000)");
                    } else {
                        assertNull(copy.getFarPast());
                    }
                },
                () -> {
                    if (this.getFarFuture() != null) {
                        assertEquals(this.getFarFuture().getTime(), copy.getFarFuture().getTime(), "far future (year 9999)");
                    } else {
                        assertNull(copy.getFarFuture());
                    }
                }
        );
    }
}
