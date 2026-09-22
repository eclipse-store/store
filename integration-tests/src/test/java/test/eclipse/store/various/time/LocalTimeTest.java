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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.LocalTime;

import org.eclipse.serializer.persistence.exceptions.PersistenceException;
import org.eclipse.serializer.reflect.XReflect;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.util.RootStorages;

public class LocalTimeTest
{
    @TempDir
    Path tempDir;

    @Test
    void localTimeStoreAndReload()
    {
        LocalTime lt = LocalTime.of(1, 1, 0);

        // installed rather than set: where LocalTime is a value class it cannot be an explicit root
        try (EmbeddedStorageManager storageManager = RootStorages.startWithRoot(tempDir, lt)) {
            storageManager.storeRoot();
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            LocalTime loaded = (LocalTime) storageManager.root();

            assertEquals(lt, loaded, "LocalTime should be equal after storing and reloading");
        }
    }

    /**
     * Setting an explicit root applies the persisted state to that very instance. An instance without
     * identity cannot receive it, so the two JVMs have different contracts and each is asserted here.
     */
    @Test
    void localTimeUpdateApiBehavior()
    {
        LocalTime lt = LocalTime.of(1, 1, 0);

        try (EmbeddedStorageManager storageManager = RootStorages.startWithRoot(tempDir, lt)) {
            storageManager.storeRoot();
        }

        LocalTime lt2 = LocalTime.MAX;

        if (XReflect.isValueClass(LocalTime.class)) {
            assertThrows(PersistenceException.class, () -> EmbeddedStorage.start(lt2, tempDir).shutdown(),
                "a value instance must be refused as an explicit root");
        } else {
            try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(lt2, tempDir)) {

                assertEquals(lt, lt2, "LocalTime should be equal after storing and reloading");
            }
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
