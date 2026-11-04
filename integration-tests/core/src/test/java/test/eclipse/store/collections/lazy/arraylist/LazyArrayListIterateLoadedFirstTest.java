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
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LazyArrayListIterateLoadedFirstTest
{

    @Test
    void concurrentModificationNext(@TempDir final Path path)
    {
        try (final EmbeddedStorageManager storage = createListStorage(path, 5, 8)) {

            @SuppressWarnings("unchecked")
            LazyArrayList<ListEntry> list = (LazyArrayList<ListEntry>) storage.root();

            final Iterator<ListEntry> iter = list.loadedFirstIterator();
            list.add(new ListEntry("unexpected"));

            assertThrows(ConcurrentModificationException.class, () -> iter.next());
        }
    }

    @Disabled
    @Test
    void concurrentModificationRemove(@TempDir final Path path)
    {
        try (final EmbeddedStorageManager storage = createListStorage(path, 5, 8)) {

            @SuppressWarnings("unchecked")
            LazyArrayList<ListEntry> list = (LazyArrayList<ListEntry>) storage.root();

            final Iterator<ListEntry> iter = list.loadedFirstIterator();
            iter.next();
            list.add(new ListEntry("unexpected"));

            assertThrows(ConcurrentModificationException.class, () -> iter.remove());

        }
    }


    @Test
    void emptyList(@TempDir final Path path)
    {
        try (final EmbeddedStorageManager storage = createListStorage(path, 5, 0)) {

            @SuppressWarnings("unchecked")
            LazyArrayList<ListEntry> list = (LazyArrayList<ListEntry>) storage.root();

            final Iterator<ListEntry> iter = list.loadedFirstIterator();
            assertFalse(iter.hasNext());

        }
    }

    @Test
    void RemoveSome(@TempDir final Path path)
    {
        try (final EmbeddedStorageManager storage = createListStorage(path, 3, 67)) {


            final List<ListEntry> referenceList = new ArrayList<>();
            for (int i = 0; i < 67; i++) {
                referenceList.add(new ListEntry("Entry-" + i));
            }
            referenceList.removeIf(e -> e.id.contains("5"));


            @SuppressWarnings("unchecked")
            LazyArrayList<ListEntry> list = (LazyArrayList<ListEntry>) storage.root();

            final Iterator<ListEntry> iter = list.loadedFirstIterator();

            while (iter.hasNext()) {
                final ListEntry item = iter.next();
                if (item.id.contains("5")) iter.remove();
            }

            assertIterableEquals(referenceList, list);

        }
    }

    @Test
    void removeAll(@TempDir final Path path)
    {
        try (final EmbeddedStorageManager storage = createListStorage(path, 3, 7)) {

            @SuppressWarnings("unchecked")
            LazyArrayList<ListEntry> list = (LazyArrayList<ListEntry>) storage.root();

            final Iterator<ListEntry> iter = list.loadedFirstIterator();

            while (iter.hasNext()) {
                ListEntry next = iter.next();
                iter.remove();
            }

            assertEquals(0, list.size());
        }
    }

    @Test
    void iterateAllUnloaded(@TempDir final Path path)
    {
        try (final EmbeddedStorageManager storage = createListStorage(path, 5, 19)) {

            final List<ListEntry> referenceList = new ArrayList<>();
            for (int i = 0; i < 19; i++) {
                referenceList.add(new ListEntry("Entry-" + i));
            }

            @SuppressWarnings("unchecked")
            LazyArrayList<ListEntry> list = (LazyArrayList<ListEntry>) storage.root();

            final Iterator<ListEntry> iter = list.loadedFirstIterator();
            this.checkIter(iter, referenceList);

        }
    }

    @Test
    void iterateAllLoaded(@TempDir final Path path)
    {
        try (final EmbeddedStorageManager storage = createListStorage(path, 5, 19)) {

            final List<ListEntry> referenceList = new ArrayList<>();
            for (int i = 0; i < 19; i++) {
                referenceList.add(new ListEntry("Entry-" + i));
            }

            @SuppressWarnings("unchecked")
            LazyArrayList<ListEntry> list = (LazyArrayList<ListEntry>) storage.root();


            list.iterateLazyReferences(l -> {
                l.get();
                assertTrue(l.isLoaded());
            });


            final Iterator<ListEntry> iter = list.loadedFirstIterator();
            this.checkIter(iter, referenceList);

        }
    }

    @Test
    void iterateSomeLoaded(@TempDir final Path path)
    {
        try (final EmbeddedStorageManager storage = createListStorage(path, 5, 19)) {


            final List<ListEntry> referenceList = new ArrayList<>();
            for (int i = 0; i < 19; i++) {
                referenceList.add(new ListEntry("Entry-" + i));
            }

            @SuppressWarnings("unchecked")
            LazyArrayList<ListEntry> list = (LazyArrayList<ListEntry>) storage.root();
            final AtomicInteger counter = new AtomicInteger();
            list.iterateLazyReferences(l -> {
                if (counter.getAndIncrement() % 2 == 0) {
                    l.get();
                }
            });

            final Iterator<ListEntry> iter = list.loadedFirstIterator();
            this.checkIter(iter, referenceList);
        }
    }

    @Test
    void unloadedAfterIterate(@TempDir final Path path)
    {
        try (final EmbeddedStorageManager storage = createListStorage(path, 5, 19)) {

            final List<ListEntry> referenceList = new ArrayList<>();
            for (int i = 0; i < 19; i++) {
                referenceList.add(new ListEntry("Entry-" + i));
            }

            @SuppressWarnings("unchecked")
            LazyArrayList<ListEntry> list = (LazyArrayList<ListEntry>) storage.root();
            list.iterateLazyReferences(l -> {
                l.get();
                assertTrue(l.isLoaded());
            });

            final Iterator<ListEntry> iter = list.loadedFirstIterator();
            this.checkIter(iter, referenceList);

            AtomicInteger loadedSegments = new AtomicInteger();
            list.iterateLazyReferences(l -> {
                if (l.isLoaded()) loadedSegments.incrementAndGet();
            });
            assertEquals(2, loadedSegments.get());

        }
    }

    void checkIter(final Iterator<?> iter, final List<?> reference)
    {
        while (iter.hasNext()) {
            assertTrue(reference.remove(iter.next()), "Iterator returned element that is not in the reference list!");
        }
        assertTrue(reference.isEmpty(), "reference list should be empty after removing all iterated elements!");
    }

    static EmbeddedStorageManager createListStorage(final Path path, final int segmentSize, final int entries)
    {
        final LazyArrayList<ListEntry> lazyList = createLazyList(segmentSize, entries);
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(lazyList, path)) {

        }

        return EmbeddedStorage.start(path);
    }

    static LazyArrayList<ListEntry> createLazyList(final int segmentSize, final int entries)
    {
        final LazyArrayList<ListEntry> lazyList = new LazyArrayList<>(segmentSize);

        for (int i = 0; i < entries; i++) {
            lazyList.add(new ListEntry("Entry-" + i));
        }
        return lazyList;
    }

}
