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
import java.time.LocalTime;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LocalTimeTest
{
    @TempDir
    Path tempDir;

    @Test
    void localTimeStoreAndReload()
    {
        LocalTime lt = LocalTime.of(1, 1, 0);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(lt, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            LocalTime loaded = (LocalTime) storageManager.root();

            assertEquals(lt, loaded, "LocalTime should be equal after storing and reloading");
        }
    }

    @Test
    void localTimeUpdateApiBehavior()
    {
        LocalTime lt = LocalTime.of(1, 1, 0);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(lt, tempDir)) {
        }

        LocalTime lt2 = LocalTime.MAX;
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(lt2, tempDir)) {

            assertEquals(lt, lt2, "LocalTime should be equal after storing and reloading");
        }
    }

    @Test
    void saveLocalTimeDataAndReload()
    {
        LocalTime lt = LocalTime.of(1, 1, 0);

        LocalTimeData root = new LocalTimeData(lt);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        LocalTimeData loadedRoot = new LocalTimeData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(lt, loadedRoot.getValue(), "LocalTimeData should be equal after storing and reloading");
        }
    }

    private static class LocalTimeData
    {
        private LocalTime value;

        public LocalTimeData(LocalTime value)
        {
            this.value = value;
        }

        public LocalTimeData()
        {
        }

        public LocalTime getValue()
        {
            return value;
        }

        public void setValue(LocalTime value)
        {
            this.value = value;
        }
    }
}
