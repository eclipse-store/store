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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ConcurrentCollectionsTest
{
    @TempDir
    Path tempDir;

    @Test
    void shouldStoreAndReloadConcurrentHashMap()
    {
        ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
        map.put("k1", "v1");
        map.put("k2", "v2");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, String> loaded = (ConcurrentHashMap<String, String>) storageManager.root();

            assertEquals(map, loaded);
        }
    }

    @Test
    void shouldStoreAndReloadCopyOnWriteArrayList()
    {
        CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
        list.add("a");
        list.add("b");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(list, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            @SuppressWarnings("unchecked")
            List<String> loaded = (List<String>) storageManager.root();

            assertEquals(list, loaded);
        }
    }

    @Test
    void shouldStoreAndReloadAtomicTypes()
    {
        AtomicInteger ai = new AtomicInteger(42);
        AtomicReference<String> ar = new AtomicReference<>("hello");

        AtomicHolder root = new AtomicHolder(ai, ar);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            AtomicHolder loaded = (AtomicHolder) storageManager.root();

            assertEquals(ai.get(), loaded.getAi().get());
            assertEquals(ar.get(), loaded.getAr().get());
        }
    }

    private static class AtomicHolder
    {
        private AtomicInteger ai;
        private AtomicReference<String> ar;

        public AtomicHolder(AtomicInteger ai, AtomicReference<String> ar)
        {
            this.ai = ai;
            this.ar = ar;
        }

        public AtomicHolder()
        {
        }

        public AtomicInteger getAi()
        {
            return ai;
        }

        public AtomicReference<String> getAr()
        {
            return ar;
        }

        public void setAi(AtomicInteger ai)
        {
            this.ai = ai;
        }

        public void setAr(AtomicReference<String> ar)
        {
            this.ar = ar;
        }
    }
}

