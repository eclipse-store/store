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
import java.time.Duration;

import org.eclipse.serializer.persistence.exceptions.PersistenceException;
import org.eclipse.serializer.reflect.XReflect;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.util.RootStorages;

public class DurationTest
{
    @TempDir
    Path tempDir;

    @Test
    void durationStoreAndReload()
    {
        Duration d = Duration.ofHours(5).plusMinutes(10);

        // installed rather than set: where Duration is a value class it cannot be an explicit root
        try (EmbeddedStorageManager storageManager = RootStorages.startWithRoot(tempDir, d)) {
            storageManager.storeRoot();
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Duration loaded = (Duration) storageManager.root();

            assertEquals(d, loaded, "Duration should be equal after storing and reloading");
        }
    }

    @Test
    void durationUpdateApiBehavior()
    {
        Duration d = Duration.ofHours(5).plusMinutes(10);

        // installed rather than set: where Duration is a value class it cannot be an explicit root
        try (EmbeddedStorageManager storageManager = RootStorages.startWithRoot(tempDir, d)) {
            storageManager.storeRoot();
        }

        Duration d2 = Duration.ZERO;
        if (XReflect.isValueClass(Duration.class)) {
            // no identity, so the persisted state cannot be applied to it - the refusal is the contract
            assertThrows(PersistenceException.class, () -> EmbeddedStorage.start(d2, tempDir).shutdown(),
                "a value instance must be refused as an explicit root");
        } else {
            try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(d2, tempDir)) {

                assertEquals(d, d2, "Duration should be equal after storing and reloading");
            }
        }
    }

    @Test
    void saveDurationDataAndReload()
    {
        Duration d = Duration.ofHours(5).plusMinutes(10);

        DurationData root = new DurationData(d);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        DurationData loadedRoot = new DurationData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(d, loadedRoot.getValue(), "DurationData should be equal after storing and reloading");
        }
    }

    private static class DurationData
    {
        private Duration value;

        public DurationData(Duration value)
        {
            this.value = value;
        }

        public DurationData()
        {
        }

        public Duration getValue()
        {
            return value;
        }

        public void setValue(Duration value)
        {
            this.value = value;
        }
    }
}
