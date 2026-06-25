package test.eclipse.store.various.time;

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
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ZonedDateTimeTest
{
    @TempDir
    Path tempDir;

    @Test
    void zonedDateTimeStoreAndReload()
    {
        ZonedDateTime zdt = ZonedDateTime.of(2020,1,1,1,1,0,0, ZoneId.of("Europe/Prague"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(zdt, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            ZonedDateTime loaded = (ZonedDateTime) storageManager.root();

            assertEquals(zdt, loaded, "ZonedDateTime should be equal after storing and reloading");
        }
    }

    @Test
    void zonedDateTimeUpdateApiBehavior()
    {
        ZonedDateTime zdt = ZonedDateTime.of(2020,1,1,1,1,0,0, ZoneId.of("Europe/Prague"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(zdt, tempDir)) {
        }

        ZonedDateTime zdt2 = ZonedDateTime.now(ZoneId.of("UTC"));
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(zdt2, tempDir)) {

            assertEquals(zdt, zdt2, "ZonedDateTime should be equal after storing and reloading");
        }
    }

    @Test
    void saveZonedDateTimeDataAndReload()
    {
        ZonedDateTime zdt = ZonedDateTime.of(2020,1,1,1,1,0,0, ZoneId.of("Europe/Prague"));

        ZonedDateTimeData root = new ZonedDateTimeData(zdt);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        ZonedDateTimeData loadedRoot = new ZonedDateTimeData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(zdt, loadedRoot.getValue(), "ZonedDateTimeData should be equal after storing and reloading");
        }
    }

    private static class ZonedDateTimeData
    {
        private ZonedDateTime value;

        public ZonedDateTimeData(ZonedDateTime value)
        {
            this.value = value;
        }

        public ZonedDateTimeData()
        {
        }

        public ZonedDateTime getValue()
        {
            return value;
        }

        public void setValue(ZonedDateTime value)
        {
            this.value = value;
        }
    }
}
