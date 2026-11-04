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

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.nio.file.Path;

import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PersistenceTest
{


    @Test
    public void storeAndReload(@TempDir final Path path)
    {

        LazyArrayList<String> myLazyList = new LazyArrayList<>(3);
        for (int i = 0; i < 10; i++) {
            myLazyList.add("Entry " + i);
        }

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(myLazyList, path)) {
        }

        LazyArrayList<String> reloadedList;
        try (final EmbeddedStorageManager storageReloaded = EmbeddedStorage.start(path)) {

            reloadedList = (LazyArrayList<String>) storageReloaded.root();

            assertIterableEquals(myLazyList, reloadedList);

            reloadedList.add(7, "AddedEntry");
            storageReloaded.store(reloadedList);
        }


        LazyArrayList<String> reloadedList2;
        try (final EmbeddedStorageManager storageReloaded2 = EmbeddedStorage.start(path)) {

            reloadedList2 = (LazyArrayList<String>) storageReloaded2.root();

            assertIterableEquals(reloadedList, reloadedList2);

            reloadedList2.add("nochnadd");
            reloadedList2.consolidate();
            storageReloaded2.store(reloadedList2);
        }

        try (final EmbeddedStorageManager storageReloaded3 = EmbeddedStorage.start(path)) {
            @SuppressWarnings("unchecked")
            LazyArrayList<String> reloadedList3 = (LazyArrayList<String>) storageReloaded3.root();
            assertIterableEquals(reloadedList2, reloadedList3);

        }

    }

}
