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

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

import test.eclipse.store.handler.AbstractHandlerTest;
import test.eclipse.store.handler.BinaryHandlerTestData;

class SqlCalendarTest extends AbstractHandlerTest<SqlCalendarTest.CalendarData> {

    SqlCalendarTest() {
        super(CalendarData.class);
    }

    @Override
    public void proveResult(CalendarData original, CalendarData copy) {
        assertAll(
                () -> assertEquals(original.getSqlTime(), copy.getSqlTime(), "sqlTime"),
                () -> assertEquals(original.getSqlTimestamp(), copy.getSqlTimestamp(), "sqlTimestamp"),
                () -> assertEquals(original.date, copy.date, "sqlDate")
        );
    }


    public static class CalendarData implements BinaryHandlerTestData {

        Timestamp sqlTimestamp;
        Time sqlTime;
        java.sql.Date date;

        @Override
        public void fillSampleData() {
            sqlTimestamp = new Timestamp(System.currentTimeMillis());
            sqlTime = new Time(System.currentTimeMillis());
            date = new Date(System.currentTimeMillis());
        }


        Timestamp getSqlTimestamp() {
            return sqlTimestamp;
        }

        Time getSqlTime() {
            return sqlTime;
        }
    }
}
