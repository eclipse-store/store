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
import java.time.Duration;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DurationTest
{
    @TempDir
    Path tempDir;

    @Test
    void durationStoreAndReload()
    {
        Duration d = Duration.ofHours(5).plusMinutes(10);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(d, tempDir)) {
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

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(d, tempDir)) {
        }

        Duration d2 = Duration.ZERO;
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(d2, tempDir)) {

            assertEquals(d, d2, "Duration should be equal after storing and reloading");
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
