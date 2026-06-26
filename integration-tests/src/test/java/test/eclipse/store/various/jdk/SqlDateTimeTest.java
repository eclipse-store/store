package test.eclipse.store.various.jdk;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SqlDateTimeTest
{
    @TempDir
    Path tempDir;

    @Test
    void sqlDateStoreAndReload()
    {
        Date d = Date.valueOf("2020-01-02");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(d, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Date loaded = (Date) storageManager.root();

            assertEquals(d, loaded, "Date should be equal after storing and reloading");
        }
    }

    @Test
    void sqlDateEpochRoundTrip()
    {
        Date epoch = new Date(0L);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(epoch, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Date loaded = (Date) storageManager.root();

            assertEquals(epoch, loaded, "Epoch date should be equal after storing and reloading");
        }
    }

    @Test
    void sqlDateMinValueRoundTrip()
    {
        Date minDate = new Date(Long.MIN_VALUE);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(minDate, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Date loaded = (Date) storageManager.root();

            assertEquals(minDate, loaded, "Min date should be equal after storing and reloading");
        }
    }

    @Test
    void sqlDateMaxValueRoundTrip()
    {
        Date maxDate = new Date(Long.MAX_VALUE);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(maxDate, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Date loaded = (Date) storageManager.root();

            assertEquals(maxDate, loaded, "Max date should be equal after storing and reloading");
        }
    }

    @Test
    void sqlDateY2KRoundTrip()
    {
        Date y2k = Date.valueOf("2000-01-01");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(y2k, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Date loaded = (Date) storageManager.root();

            assertEquals(y2k, loaded, "Y2K date should be equal after storing and reloading");
        }
    }

    @Test
    void sqlDateLeapDayRoundTrip()
    {
        Date leapDay = Date.valueOf("2020-02-29");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(leapDay, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Date loaded = (Date) storageManager.root();

            assertEquals(leapDay, loaded, "Leap day should be equal after storing and reloading");
        }
    }

    @Test
    void sqlTimeRoundTrip()
    {
        Time time = Time.valueOf("12:34:56");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(time, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Time loaded = (Time) storageManager.root();

            assertEquals(time, loaded, "Time should be equal after storing and reloading");
        }
    }

    @Test
    void sqlTimeMidnightRoundTrip()
    {
        Time midnight = Time.valueOf("00:00:00");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(midnight, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Time loaded = (Time) storageManager.root();

            assertEquals(midnight, loaded, "Midnight time should be equal after storing and reloading");
        }
    }

    @Test
    void sqlTimeEndOfDayRoundTrip()
    {
        Time endOfDay = Time.valueOf("23:59:59");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(endOfDay, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Time loaded = (Time) storageManager.root();

            assertEquals(endOfDay, loaded, "End of day time should be equal after storing and reloading");
        }
    }

    @Test
    void sqlTimestampRoundTrip()
    {
        Timestamp ts = Timestamp.valueOf("1999-12-31 23:59:59.0");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(ts, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Timestamp loaded = (Timestamp) storageManager.root();

            assertEquals(ts, loaded, "Timestamp should be equal after storing and reloading");
        }
    }

    @Test
    void sqlTimestampWithNanosRoundTrip()
    {
        Timestamp ts = Timestamp.valueOf("2020-05-15 10:30:45.123456789");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(ts, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Timestamp loaded = (Timestamp) storageManager.root();

            assertEquals(ts, loaded, "Timestamp with nanos should be equal after storing and reloading");
            assertEquals(ts.getNanos(), loaded.getNanos(), "Nanos should be preserved");
        }
    }

    @Test
    void sqlTimestampEpochRoundTrip()
    {
        Timestamp epoch = new Timestamp(0L);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(epoch, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Timestamp loaded = (Timestamp) storageManager.root();

            assertEquals(epoch, loaded, "Epoch timestamp should be equal after storing and reloading");
        }
    }

    @Test
    void sqlTimestampMinValueRoundTrip()
    {
        Timestamp minTs = new Timestamp(Long.MIN_VALUE);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(minTs, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Timestamp loaded = (Timestamp) storageManager.root();

            assertEquals(minTs, loaded, "Min timestamp should be equal after storing and reloading");
        }
    }

    @Test
    void sqlTimestampMaxNanosRoundTrip()
    {
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        ts.setNanos(999999999);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(ts, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Timestamp loaded = (Timestamp) storageManager.root();
            System.out.println(loaded.getNanos());

            assertEquals(ts, loaded, "Timestamp with max nanos should be equal after storing and reloading");
            assertEquals(999999999, loaded.getNanos(), "Max nanos should be preserved");
        }
    }

    @Test
    void saveSqlDateTimeDataAndReload()
    {
        Timestamp ts = Timestamp.valueOf("1999-12-31 23:59:59.0");

        SqlDateTimeData root = new SqlDateTimeData(ts);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        SqlDateTimeData loadedRoot = new SqlDateTimeData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(ts, loadedRoot.getValue(), "Timestamp in data should be equal after storing and reloading");
        }
    }

    private static class SqlDateTimeData
    {
        private Timestamp value;

        public SqlDateTimeData(Timestamp value)
        {
            this.value = value;
        }

        public SqlDateTimeData()
        {
        }

        public Timestamp getValue()
        {
            return value;
        }

        public void setValue(Timestamp value)
        {
            this.value = value;
        }
    }
}

