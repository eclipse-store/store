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
import java.time.LocalDateTime;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LocalDateTimeTest
{
    @TempDir
    Path tempDir;

    @Test
    void localDateTimeTest()
    {
        LocalDateTime ldt = LocalDateTime.of(2020, 1, 1, 1, 1, 0);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(ldt, tempDir)) {
        }

        LocalDateTime ldt2 = null;
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            LocalDateTime localDateTime = (LocalDateTime) storageManager.root();

            assertEquals(ldt, localDateTime, "LocalDateTime should be equal after storing and reloading");
        }
    }

    @Test
    void localDateTimeUpdateApiTest()
    {
        LocalDateTime ldt = LocalDateTime.of(2020, 1, 1, 1, 1, 0);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(ldt, tempDir)) {
        }

        LocalDateTime ldt2 = LocalDateTime.MAX;
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(ldt2, tempDir)) {

            assertEquals(ldt, ldt2, "LocalDateTime should be equal after storing and reloading");
        }
    }

    @Test
    void saveLocalDateTimeDataTest()
    {
        LocalDateTime ldt = LocalDateTime.of(2020, 1, 1, 1, 1, 0);

        LocalDateTimeData root = new LocalDateTimeData(ldt);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        LocalDateTimeData loadedRoot = new LocalDateTimeData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(ldt, loadedRoot.getValue(), "LocalDateTimeData should be equal after storing and reloading");
        }
    }

    private static class LocalDateTimeData
    {
        private LocalDateTime value;

        public LocalDateTimeData(LocalDateTime value)
        {
            this.value = value;
        }

        public LocalDateTimeData()
        {
        }

        public LocalDateTime getValue()
        {
            return value;
        }

        public void setValue(LocalDateTime value)
        {
            this.value = value;
        }
    }
}
