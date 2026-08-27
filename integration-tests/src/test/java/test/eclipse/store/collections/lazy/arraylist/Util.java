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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

public class Util
{


    static <T> ImmutablePair<LazyArrayList<T>, EmbeddedStorageManager> storeAndLoadList(LazyArrayList<T> list, Path location)
    {
        try (EmbeddedStorageManager storage = startStorage(list, location)) {

        }

        LazyArrayList<T> copy = new LazyArrayList<>();
        EmbeddedStorageManager storage = startStorage(copy, location);

        return ImmutablePair.of(copy, storage);

    }

    static EmbeddedStorageManager startStorage(Path path)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage
                .Foundation(path)
                .start();
        return storage;
    }

//    static EmbeddedStorageManager startStorage( LazyArrayList root, final Path path) {
//        final EmbeddedStorageManager storage = EmbeddedStorage
//                .Foundation(path)
//                .registerTypeHandler(BinaryHandlerLazyArrayList.New())
//                .start(root);
//        return storage;
//    }

    static <T> EmbeddedStorageManager startStorage(LazyArrayList<T> root, final Path path)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage
                .Foundation(path)
                .start(root);
        return storage;
    }

    static LazyArrayList<ListEntry> createLazyList(final int segmentSize, final int entries)
    {
        final LazyArrayList<ListEntry> lazyList = new LazyArrayList<>(segmentSize);

        for (int i = 0; i < entries; i++) {
            lazyList.add(new ListEntry("Entry-" + i));
        }
        return lazyList;
    }

    static LazyArrayList<ListEntry> loadAndCompare(final LazyArrayList<ListEntry> lazyList, EmbeddedStorageManager storage)
    {

        LazyArrayList<ListEntry> lazyListReloaded;

        lazyListReloaded = (LazyArrayList<ListEntry>) storage.root();

        assertAll("load nad compare list",
                () -> assertEquals(lazyList.size(), lazyListReloaded.size(), "Size mismatch!"),
                () -> assertEquals(lazyList.getMaxSegmentSize(), lazyListReloaded.getMaxSegmentSize(), "getMaxSegmentSize mismatch!"),
                () -> assertEquals(lazyList.getSegmentCount(), lazyListReloaded.getSegmentCount(), "getSegmentCount mismatch!"),
                () -> assertIterableEquals(lazyList, lazyListReloaded)
        );

        return lazyListReloaded;
    }
}
