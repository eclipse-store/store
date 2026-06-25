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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class UUIDTest
{
    @TempDir
    Path tempDir;

    @Test
    void uuidStoreAndReload()
    {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(id, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            UUID loaded = (UUID) storageManager.root();

            assertEquals(id, loaded, "UUID should be equal after storing and reloading");
        }
    }

    @Test
    void uuidRandomRoundTrip()
    {
        UUID random = UUID.randomUUID();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(random, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            UUID loaded = (UUID) storageManager.root();

            assertEquals(random, loaded, "Random UUID should be equal after storing and reloading");
        }
    }

    @Test
    void uuidNilRoundTrip()
    {
        UUID nil = new UUID(0L, 0L);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(nil, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            UUID loaded = (UUID) storageManager.root();

            assertEquals(nil, loaded, "Nil UUID should be equal after storing and reloading");
        }
    }

    @Test
    void uuidVersionAndVariantRoundTrip()
    {
        UUID v1 = UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6");
        UUID v4 = UUID.randomUUID();
        List<UUID> root = Arrays.asList(v1, v4);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            @SuppressWarnings("unchecked")
            List<UUID> loaded = (List<UUID>) storageManager.root();

            assertEquals(1, loaded.get(0).version(), "Version 1 should be preserved after reload");
            assertEquals(2, loaded.get(0).variant(), "Variant 2 should be preserved after reload");
            assertEquals(4, loaded.get(1).version(), "Version 4 should be preserved after reload");
            assertEquals(2, loaded.get(1).variant(), "Variant 2 should be preserved after reload");
        }
    }

    @Test
    void uuidNameFromBytesRoundTrip()
    {
        UUID empty = UUID.nameUUIDFromBytes(new byte[0]);
        byte[] longInput = new byte[512];
        for (int i = 0; i < longInput.length; i++) {
            longInput[i] = (byte) i;
        }
        UUID longUuid = UUID.nameUUIDFromBytes(longInput);

        List<UUID> root = Arrays.asList(empty, longUuid);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            @SuppressWarnings("unchecked")
            List<UUID> loaded = (List<UUID>) storageManager.root();

            assertEquals(root, loaded, "nameUUIDFromBytes should round-trip for empty and long inputs");
        }
    }

    @Test
    void uuidFromStringUppercaseRoundTrip()
    {
        UUID upper = UUID.fromString("123E4567-E89B-12D3-A456-426614174000");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(upper, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            UUID loaded = (UUID) storageManager.root();

            assertEquals(upper, loaded, "Uppercase UUID should parse and round-trip");
        }
    }

    @Test
    void uuidBoundaryBitsRoundTrip()
    {
        UUID maxLsb = new UUID(0L, Long.MAX_VALUE);
        UUID minMsb = new UUID(Long.MIN_VALUE, 0L);
        List<UUID> root = Arrays.asList(maxLsb, minMsb);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            @SuppressWarnings("unchecked")
            List<UUID> loaded = (List<UUID>) storageManager.root();

            assertEquals(root, loaded, "Boundary-bit UUIDs should round-trip");
        }
    }

    private static class UUIDData
    {
        private UUID value;

        public UUIDData(UUID value)
        {
            this.value = value;
        }

        public UUIDData()
        {
        }

        public UUID getValue()
        {
            return value;
        }

        public void setValue(UUID value)
        {
            this.value = value;
        }
    }
}
