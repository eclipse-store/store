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

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlCalendarData implements BinaryHandlerTestData {


    Timestamp sqlTimestamp;
    Time sqlTime;
    java.sql.Date date = new Date(1000);

    @Override
    public BinaryHandlerTestData fillSampleData() {
        sqlTimestamp = Timestamp.valueOf(LocalDateTime.of(2020, 10, 9, 11, 52, 41));
        sqlTime = Time.valueOf(LocalTime.of(8, 53, 50, 410420));
        date = new Date(System.currentTimeMillis());
        return this;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        SqlCalendarData copy = (SqlCalendarData) o;
        assertAll(
                () -> assertEquals(this.getSqlTime(), copy.getSqlTime(), "sqlTime"),
                () -> assertEquals(this.getSqlTimestamp(), copy.getSqlTimestamp(), "sqlTimestamp"),
                () -> assertEquals(this.date.toLocalDate(), copy.date.toLocalDate(), "sqlDate")
        );
    }

    public Timestamp getSqlTimestamp() {
        return sqlTimestamp;
    }

    public Time getSqlTime() {
        return sqlTime;
    }

    public Date getDate() {
        return date;
    }
}
