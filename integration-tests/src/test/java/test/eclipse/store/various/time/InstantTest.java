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
import java.time.Instant;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class InstantTest
{
    @TempDir
    Path tempDir;

    @Test
    void instantStoreAndReload()
    {
        Instant i = Instant.parse("2020-01-01T01:01:00Z");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(i, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Instant loaded = (Instant) storageManager.root();

            assertEquals(i, loaded, "Instant should be equal after storing and reloading");
        }
    }

    @Test
    void instantUpdateApiBehavior()
    {
        Instant i = Instant.parse("2020-01-01T01:01:00Z");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(i, tempDir)) {
        }

        Instant i2 = Instant.EPOCH;
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(i2, tempDir)) {

            assertEquals(i, i2, "Instant should be equal after storing and reloading");
        }
    }

    @Test
    void saveInstantDataAndReload()
    {
        Instant i = Instant.parse("2020-01-01T01:01:00Z");

        InstantData root = new InstantData(i);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        InstantData loadedRoot = new InstantData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(i, loadedRoot.getValue(), "InstantData should be equal after storing and reloading");
        }
    }

    private static class InstantData
    {
        private Instant value;

        public InstantData(Instant value)
        {
            this.value = value;
        }

        public InstantData()
        {
        }

        public Instant getValue()
        {
            return value;
        }

        public void setValue(Instant value)
        {
            this.value = value;
        }
    }
}
