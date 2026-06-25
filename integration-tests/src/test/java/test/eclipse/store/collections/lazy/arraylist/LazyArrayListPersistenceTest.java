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
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LazyArrayListPersistenceTest {


    @Test
    void nullElements(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);
        lazyList.add(null);
        lazyList.add(null);
        lazyList.add(null);
        lazyList.add(null);

        assertThrows(NullPointerException.class, () -> lazyList.addAll(null));

        final ListEntry element7 = lazyList.get(7);
        assertNull(element7);
        lazyList.remove(7);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(path)) {

            storage.setRoot(lazyList);
            storage.storeRoot();
        }

        loadAndCompare(path, lazyList, l -> {
            assertNull(l.get(7));
            assertEquals(10, l.size());
        });
    }

    @Test
    void lastIndexOf(@TempDir final Path path) {

        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);
        lazyList.add(0, new ListEntry("added"));
        lazyList.add(4, new ListEntry("added"));
        lazyList.add(6, new ListEntry("added"));

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {

        }

        loadAndCompare(path, lazyList, l -> {
            assertEquals(6, l.lastIndexOf(new ListEntry("added")));
        });
    }

    @Test
    void lastIndexOfNotFound(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {

        }

        loadAndCompare(path, lazyList, l -> {
            assertEquals(-1, l.lastIndexOf(new ListEntry("added")));
        });
    }


    @Test
    void indexOf(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {
        }

        loadAndCompare(path, lazyList, l -> {
            assertEquals(4, l.indexOf(new ListEntry("Entry-4")));
        });
    }

    @Test
    void indexOfNotFound(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {
        }

        loadAndCompare(path, lazyList, l -> {
            assertEquals(-1, l.indexOf(new ListEntry("not in list")));
        });
    }


    @Test
    void get(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {
        }

        loadAndCompare(path, lazyList, l -> {
            assertEquals(new ListEntry("Entry-4"), l.get(4));
        });
    }

    @Test
    void contains(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {
        }

        loadAndCompare(path, lazyList, l -> {
            assertTrue(l.contains(new ListEntry("Entry-4")), "LazyArrayList does not contain expected object new String(\"Entry-4\")");
        });
    }

    @Test
    void consolidate(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);
        lazyList.add(1, new ListEntry("added"));
        lazyList.add(4, new ListEntry("added"));

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {

            lazyList.iterateLazyReferences(l -> l.clear());

            lazyList.consolidate();
            storage.store(lazyList);
        }

        loadAndCompare(path, lazyList, l -> {
            //after consolidate the list should have 3 segments with 3 elements each;
            assertEquals(3, l.getSegmentCount());
            l.segments()
                    .forEach(s -> {
                        assertEquals(3, s.getSize());
                    });
        });
    }

    @Test
    void clear(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {
            lazyList.iterateLazyReferences(l -> l.clear());
            lazyList.clear();
            storage.store(lazyList);
        }

        loadAndCompare(path, lazyList, l -> {
            assertTrue(l.isEmpty());
        });
    }


    @Test
    void addAllAtIndex(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {
            lazyList.iterateLazyReferences(l -> l.clear());

            final List<ListEntry> newEntries = Arrays.asList(
                    new ListEntry("New entry 1"), new ListEntry("New entry 2"),
                    new ListEntry("New entry 3"), new ListEntry("New entry 4"),
                    new ListEntry("New entry 5"), new ListEntry("New entry 6"));

            lazyList.addAll(4, newEntries);
            storage.store(lazyList);
        }

        loadAndCompare(path, lazyList, l -> {
            assertEquals(13, l.size());
        });
    }

    @Test
    void addAllAtEnd(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {

            lazyList.iterateLazyReferences(l -> l.clear());

            final List<ListEntry> newEntries = Arrays.asList(
                    new ListEntry("New entry 1"), new ListEntry("New entry 2"),
                    new ListEntry("New entry 3"), new ListEntry("New entry 4"),
                    new ListEntry("New entry 5"), new ListEntry("New entry 6"));

            lazyList.addAll(newEntries);
            storage.store(lazyList);
        }

        loadAndCompare(path, lazyList, l -> {
            assertEquals(13, l.size());
        });

    }

    @Test
    void unloadPartialTest(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {


            lazyList.add(new ListEntry("not stored entry"));

            lazyList.iterateLazyReferences(l -> l.clear());

            lazyList.segments()
                    .forEach(s -> {
                        if (s.getOffset() >= 6) {
                            assertTrue(s.isLoaded(), "Expected dirty references NOT to be unloaded!");
                        } else {
                            assertFalse(s.isLoaded(), "Expected non dirty references to be unloaded!");
                        }
                    });

        }

        //The loaded list must not be the same as the modified but not stored one.
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> loadAndCompare(path, lazyList, null));

    }

    @Test
    void unloadAllTest(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 14);
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {

            lazyList.iterateLazyReferences(l -> l.clear());

            lazyList.iterateLazyReferences(l ->
                    assertFalse(l.isLoaded(), "Expected all lazy references to be unloaded!")
            );

        }

        loadAndCompare(path, lazyList, null);
    }

    @Test
    void setElementInList(@TempDir final Path path) {

        final LazyArrayList<ListEntry> lazyList = createLazyList(5, 12);
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {

            lazyList.iterateLazyReferences(l -> l.clear());

            lazyList.set(3, new ListEntry("lazyList.set"));
            storage.store(lazyList);

        }

        loadAndCompare(path, lazyList, l -> {
            assertEquals(new ListEntry("lazyList.set"), l.get(3));
        });
    }

    /**
     * Remove a single element from a persisted list using its index
     *
     * @param path
     */
    @Test
    void removeIndex(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(5, 12);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {

            lazyList.iterateLazyReferences(l -> l.clear());

            lazyList.remove(3);
            storage.store(lazyList);

        }

        loadAndCompare(path, lazyList, null);
    }

    @Test
    void removeObject(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(5, 12);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {
            lazyList.iterateLazyReferences(l -> l.clear());

            lazyList.remove(new ListEntry("Entry-6"));
            storage.store(lazyList);

        }
        loadAndCompare(path, lazyList, null);
    }

    @Test
    void RemoveAll(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {

            lazyList.iterateLazyReferences(l -> l.clear());

            final List<ListEntry> newEntries = Arrays.asList(
                    new ListEntry("Entry-1"),
                    new ListEntry("Entry-3"),
                    new ListEntry("Entry-5"));

            lazyList.removeAll(newEntries);
            storage.store(lazyList);
        }

        loadAndCompare(path, lazyList, l -> {
            assertEquals(4, l.size());
        });
    }

    @Test
    void RemoveIf(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {

            lazyList.iterateLazyReferences(l -> l.clear());

            final List<ListEntry> newEntries = Arrays.asList(
                    new ListEntry("Entry-1"),
                    new ListEntry("Entry-3"),
                    new ListEntry("Entry-5"));

            lazyList.removeIf(newEntries::contains);
            storage.store(lazyList);
        }

        loadAndCompare(path, lazyList, l -> {
            assertEquals(4, l.size());
        });
    }

    @Test
    void RetainAll(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(3, 7);
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {

            lazyList.iterateLazyReferences(l -> l.clear());

            final List<ListEntry> newEntries = Arrays.asList(
                    new ListEntry("Entry-1"),
                    new ListEntry("Entry-3"),
                    new ListEntry("Entry-5"));

            lazyList.retainAll(newEntries);
            storage.store(lazyList);
        }

        loadAndCompare(path, lazyList, l -> {
            assertEquals(3, l.size());
        });
    }

    @Test
    void addToEmptyList(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(5, 12);
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {

        }
        loadAndCompare(path, lazyList, null);
    }

    @Test
    void addAtIndex(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(5, 12);
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {

            lazyList.iterateLazyReferences(l -> l.clear());

            lazyList.add(4, new ListEntry("Entry add at 4"));
            storage.store(lazyList);
        }

        loadAndCompare(path, lazyList, l -> {
            assertEquals(new ListEntry("Entry add at 4"), l.get(4));
        });
    }

    @Test
    void toObjectArray(@TempDir final Path path) {
        final LazyArrayList<ListEntry> lazyList = createLazyList(5, 12);
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {

            lazyList.iterateLazyReferences(l -> l.clear());
            storage.store(lazyList);
        }

        loadAndCompare(path, lazyList, l -> {
            final Object[] array = l.toArray();
            for (int i = 0; i < array.length; i++) {
                assertEquals(lazyList.get(i), array[i]);
            }
        });
    }

    @Test
    void toTypedArray(@TempDir final Path path) {

        final LazyArrayList<ListEntry> lazyList = createLazyList(5, 12);

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {
            lazyList.iterateLazyReferences(l -> l.clear());
            storage.store(lazyList);
        }

        loadAndCompare(path, lazyList, l -> {
            final Object[] array = l.toArray(new ListEntry[0]);
            for (int i = 0; i < array.length; i++) {
                assertEquals(lazyList.get(i), array[i]);
            }
        });
    }

    static LazyArrayList<ListEntry> loadAndCompare(final Path path,
                                                   final LazyArrayList<ListEntry> lazyList,
                                                   final Consumer<LazyArrayList<ListEntry>> f
    ) {

        final LazyArrayList<ListEntry> lazyListReloaded = new LazyArrayList<>();
        try (final EmbeddedStorageManager storageReloaded = EmbeddedStorage.start(lazyListReloaded, path)) {

            assertNotSame(lazyList, lazyListReloaded, "Lists are reference equal");
            assertEquals(lazyList.size(), lazyListReloaded.size(), "Size mismatch!");
            assertEquals(lazyList.getMaxSegmentSize(), lazyListReloaded.getMaxSegmentSize(), "getMaxSegmentSize mismatch!");
            assertEquals(lazyList.getSegmentCount(), lazyListReloaded.getSegmentCount(), "getSegmentCount mismatch!");
            assertIterableEquals(lazyList, lazyListReloaded);

            if (f != null)
                f.accept(lazyListReloaded);
        }

        return lazyListReloaded;
    }

    static LazyArrayList<ListEntry> createLazyList(final int segmentSize, final int entries) {
        final LazyArrayList<ListEntry> lazyList = new LazyArrayList<>(segmentSize);

        for (int i = 0; i < entries; i++) {
            lazyList.add(new ListEntry("Entry-" + i));
        }
        return lazyList;
    }

}
