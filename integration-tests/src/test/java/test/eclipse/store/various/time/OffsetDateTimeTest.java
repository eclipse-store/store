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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OffsetDateTimeTest
{
    @TempDir
    Path tempDir;

    @Test
    void offsetDateTimeStoreAndReload()
    {
        OffsetDateTime odt = OffsetDateTime.of(2020,1,1,1,1,0,0, ZoneOffset.ofHours(1));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(odt, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            OffsetDateTime loaded = (OffsetDateTime) storageManager.root();

            assertEquals(odt, loaded, "OffsetDateTime should be equal after storing and reloading");
        }
    }

    @Test
    void offsetDateTimeUpdateApiBehavior()
    {
        OffsetDateTime odt = OffsetDateTime.of(2020,1,1,1,1,0,0, ZoneOffset.ofHours(1));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(odt, tempDir)) {
        }

        OffsetDateTime odt2 = OffsetDateTime.MIN;
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(odt2, tempDir)) {

            assertEquals(odt, odt2, "OffsetDateTime should be equal after storing and reloading");
        }
    }

    @Test
    void saveOffsetDateTimeDataAndReload()
    {
        OffsetDateTime odt = OffsetDateTime.of(2020,1,1,1,1,0,0, ZoneOffset.ofHours(1));

        OffsetDateTimeData root = new OffsetDateTimeData(odt);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        OffsetDateTimeData loadedRoot = new OffsetDateTimeData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(odt, loadedRoot.getValue(), "OffsetDateTimeData should be equal after storing and reloading");
        }
    }

    private static class OffsetDateTimeData
    {
        private OffsetDateTime value;

        public OffsetDateTimeData(OffsetDateTime value)
        {
            this.value = value;
        }

        public OffsetDateTimeData()
        {
        }

        public OffsetDateTime getValue()
        {
            return value;
        }

        public void setValue(OffsetDateTime value)
        {
            this.value = value;
        }
    }
}
