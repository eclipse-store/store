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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SpliteratorTest
{

    @Test
    void createSpliterator()
    {

        final ArrayList<String> arrayList = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            arrayList.add("entry " + i);
        }

        final Spliterator<String> arrayListSpliterator = arrayList.spliterator();
        final Spliterator<String> splitArrayList = arrayListSpliterator.trySplit();


        final LazyArrayList<String> lazyList = new LazyArrayList<>(4);
        for (int i = 0; i < 20; i++) {
            lazyList.add("entry " + i);
        }

        final Spliterator<String> lazySpliterator = lazyList.segmentSpliterator();
        final Spliterator<String> splitLazy = lazySpliterator.trySplit();
        final Spliterator<String> splitLazy2 = lazySpliterator.trySplit();

        final AtomicInteger counter = new AtomicInteger();
        lazySpliterator.forEachRemaining(s -> counter.getAndAdd(1));
        splitLazy.forEachRemaining(s -> counter.getAndAdd(1));
        splitLazy2.forEachRemaining(s -> counter.getAndAdd(1));


        assertEquals(20, counter.get(), "total iterated elements");
    }

    @Test
    void createSpliteratorRecursive()
    {

        final int maxSegmentSize = 4;
        final int numElements = 20;

        final LazyArrayList<ListEntry> lazyList = new LazyArrayList<>(maxSegmentSize);

        for (int i = 0; i < numElements; i++) {
            lazyList.add(new ListEntry("Entry " + i));
        }

        final List<Spliterator<ListEntry>> spliterators = this.splitAll(lazyList.spliterator());

        assertEquals(5, spliterators.size(), "expected to get one spliterator for each segment (" + numElements / maxSegmentSize + ")");
    }


    @Test
    void lateBinding()
    {

        final int maxSegmentSize = 4;
        final int numElements = 20;

        final LazyArrayList<ListEntry> lazyList = new LazyArrayList<>(maxSegmentSize);

        for (int i = 0; i < numElements - 10; i++) {
            lazyList.add(new ListEntry("Entry " + i));
        }

        final Spliterator<ListEntry> spliterator = lazyList.spliterator();

        for (int i = 10; i < numElements; i++) {
            lazyList.add(new ListEntry("Entry " + i));
        }

        final List<Spliterator<ListEntry>> spliterators = this.splitAll(spliterator);

        assertEquals(5, spliterators.size(), "expected to get one spliterator for each segment (" + numElements / maxSegmentSize + ")");
    }

    @Test
    void readStreamAndCloseTest(@TempDir final Path path)
    {
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(path)) {
            final LazyArrayList<ListEntry> lazyList = Util.createLazyList(3, 25);

            storage.setRoot(lazyList);
            storage.storeRoot();
            lazyList.iterateLazyReferences(l -> l.clear());
            lazyList.iterateLazyReferences(l -> assertFalse(l.isLoaded(), "list segment loaded!"));

            long countFours;
            try (final Stream<ListEntry> stream = lazyList.parallelStream()) {
                countFours = stream.filter(e -> e.id.contains("4"))
                        .count();
            }

            assertEquals(3, countFours);

            AtomicInteger loadedSegments = new AtomicInteger();
            lazyList.iterateLazyReferences(l -> {
                if (l.isLoaded()) loadedSegments.incrementAndGet();
            });
            assertEquals(0, loadedSegments.get());
        }
    }

    @Test
    @Disabled
    void deleteStreamTest(@TempDir final Path path)
    {
        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(path)) {
            final LazyArrayList<ListEntry> lazyList = Util.createLazyList(3, 25);

            storage.setRoot(lazyList);
            storage.storeRoot();
            lazyList.iterateLazyReferences(l -> l.clear());
            lazyList.iterateLazyReferences(l -> assertFalse(l.isLoaded(), "list segment loaded!"));

            {
                final long countTwos = lazyList.parallelStream()
                        .filter(e -> e.id.contains("4"))
                        .count();
                assertEquals(3, countTwos);
            }

            System.gc();

            lazyList.iterateLazyReferences(l -> assertFalse(l.isLoaded(), "list segment loaded!"));
        }
    }


    private List<Spliterator<ListEntry>> splitAll(final Spliterator<ListEntry> spliterator)
    {
        final List<Spliterator<ListEntry>> spliterators = new ArrayList<>();
        final Spliterator<ListEntry> split = spliterator.trySplit();

        if (split != null) {
            spliterators.addAll(this.splitAll(spliterator));
            spliterators.addAll(this.splitAll(split));
        } else {
            spliterators.add(spliterator);
        }

        return spliterators;
    }

}
