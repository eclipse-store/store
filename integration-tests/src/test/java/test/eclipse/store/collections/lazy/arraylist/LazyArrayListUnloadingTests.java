package test.eclipse.store.collections.lazy.arraylist;

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

import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LazyArrayListUnloadingTests {

    @Test
    void UnloadStored(@TempDir final Path path) {

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(path)) {
            LazyArrayList<String> list = createLazyList(22);

            storage.setRoot(list);
            storage.storeRoot();

            list.iterateLazyReferences(l -> l.clear());
            list.iterateLazyReferences(l -> assertFalse(l.isLoaded()));

        }
    }

    @Test
    void UnloadNotStored(@TempDir final Path path) {

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(path)) {
            LazyArrayList<String> list = createLazyList(44);

            storage.setRoot(list);

            list.iterateLazyReferences(l -> l.clear());
            list.iterateLazyReferences(l -> assertTrue(l.isLoaded()));
        }
    }

    @Test
    void UnloadNotStored_reloadedStorage(@TempDir final Path path) {

        try (final EmbeddedStorageManager storageReloaded = createReloadedStorageWithList(path)) {
            @SuppressWarnings("unchecked")
            LazyArrayList<String> listReloaded = (LazyArrayList<String>) storageReloaded.root();
            listReloaded.iterateLazyReferences(l -> assertFalse(l.isLoaded()));

            listReloaded.add("New Entry");

            listReloaded.iterateLazyReferences(l -> l.clear());

            listReloaded.segments()
                    .forEach(s -> {
                        if (s.isModified()) {
                            assertTrue(s.isLoaded(), "expected modified segment to be loaded!");
                        } else {
                            assertFalse(s.isLoaded(), "expected unmodified segment to be unloaded!");
                        }
                    });

            storageReloaded.store(listReloaded);
            listReloaded.iterateLazyReferences(l -> l.clear());
            listReloaded.iterateLazyReferences(l -> assertFalse(l.isLoaded()));
        }
    }


    static EmbeddedStorageManager createReloadedStorageWithList(final Path path) {
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(path)) {
            LazyArrayList<String> list = createLazyList(44);
            storage.setRoot(list);
            storage.storeRoot();
        }
        return EmbeddedStorage.start(path);
    }


    private static LazyArrayList<String> createLazyList(final int size) {
        LazyArrayList<String> list = new LazyArrayList<>();

        for (int i = 0; i < size; i++) {
            list.add("Entry " + i);
        }
        return list;
    }
}
