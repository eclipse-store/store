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
import org.eclipse.serializer.collections.lazy.LazySegmentUnloader;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LazyArrayListAutoUnloadTest {


    @Test
    void removedSegments(@TempDir final Path path) {

        LazyArrayList<String> list = new LazyArrayList<>(3, new LazySegmentUnloader.Default(1));
        for (int i = 0; i < 27; i++) {
            list.add("Entry_" + i);
        }

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(list, path)) {


            storage.setRoot(list);
            storage.storeRoot();
            //System.out.println("total segments: " + countSegments(list));
            assertEquals(9, countSegments(list));

            list.remove(0);
            list.remove(0);
            list.remove(0);
            list.remove(0);
            list.remove(0);
            list.remove(0);
            list.get(20);

            storage.store(list);
            //System.out.println("total segment after remove: " + countSegments(list));
            assertEquals(7, countSegments(list));

            //System.out.println("loaded segments after remove: \n" + getLoadedSegments(list).size() + "\n");
            assertEquals(1, getLoadedSegments(list).size());

        }
    }


    @Test
    void devTest(@TempDir final Path path) {

        LazyArrayList<String> list = new LazyArrayList<>(5);

        for (int i = 0; i < 27; i++) {
            list.add("Entry_" + i);
        }

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(list, path)) {

            assertFalse(list.contains("not existing"));
            //System.out.println("loaded segments after contains (not found): \n" + getLoadedSegments(list) + "\n");
            assertLoadedSegment(list);

            assertTrue(list.contains("Entry_18"));
            //System.out.println("loaded segments after contains (found)    : \n" + getLoadedSegments(list) + "\n");
            assertLoadedSegment(list);

            list.add("Entry_28");
            storage.store(list);
            //System.out.println("loaded segments after add                 : \n" + getLoadedSegments(list) + "\n");
            assertLoadedSegment(list);

            list.add(9, "Entry_add_9");
            storage.store(list);
            //System.out.println("\n loaded segments after add index           : \n" + getLoadedSegments(list) + "\n");
            assertLoadedSegment(list);

            list.set(19, "Entry_set_18");
            storage.store(list);
            //System.out.println("loaded segments after set                 : \n" + getLoadedSegments(list) + "\n");
            assertLoadedSegment(list);

            list.get(11);
            storage.store(list);
            //System.out.println("loaded segments after get                 : \n" + getLoadedSegments(list) + "\n");
            assertLoadedSegment(list);

            list.forEach(e -> e.concat("Hallo"));
            storage.store(list);
            //System.out.println("loaded segments after forEach             : \n" + getLoadedSegments(list) + "\n");
            assertLoadedSegment(list);

            list.remove("Entry_13");
            storage.store(list);
            //System.out.println("loaded segments after remove1               : \n" + getLoadedSegments(list) + "\n");
            assertLoadedSegment(list);

            list.remove("Entry_28");
            storage.store(list);
            //System.out.println("loaded segments after remove2               : \n" + getLoadedSegments(list) + "\n");
            assertLoadedSegment(list);

            list.remove(list.size() - 1);
            list.remove(list.size() - 1);
            storage.store(list);
            //System.out.println("loaded segments after remove3               : \n" + getLoadedSegments(list) + "\n");
            assertLoadedSegment(list);

            list.remove(14);
            list.remove(15);
            list.remove(16);
            list.remove(17);
            //System.out.println("loaded segments after remove4              : \n" + getLoadedSegments(list) + "\n");
            storage.store(list);
            //System.out.println("loaded segments after remove4              : \n" + getLoadedSegments(list) + "\n");
            list.get(0);
            //System.out.println("loaded segments after remove4              : \n" + getLoadedSegments(list) + "\n");
            assertLoadedSegment(list);

        }


        //System.out.println("--------------------------------- reloading storage ---------------------------------");

        try (final EmbeddedStorageManager storageReloaded = EmbeddedStorage.start(path)) {

            @SuppressWarnings("unchecked")
            LazyArrayList<String> listReloaded = (LazyArrayList<String>) storageReloaded.root();
            //listReloaded.forEach(e -> System.out.println(e));
            //System.out.println("loaded segments after forEach              : \n" + getLoadedSegments(listReloaded) + "\n");
            assertLoadedSegment(list);

        }
    }


    static void unloadAll(LazyArrayList<?> list) {

        final Iterable<? extends LazyArrayList<?>.Segment> segments = list.segments();
        segments.forEach(LazyArrayList.Segment::unloadSegment);

    }


    void assertLoadedSegment(LazyArrayList<?> list) {

        int expected = 0;

        try {
            final Field unloader = list.getClass()
                    .getDeclaredField("unloader");
            unloader.setAccessible(true);
            final Field load = unloader.get(list)
                    .getClass()
                    .getDeclaredField("desiredLoadCount");
            load.setAccessible(true);
            final Object ul = unloader.get(list);
            expected = load.getInt(ul);

        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }


        assertTrue(getLoadedSegments(list).size() <= expected);
    }

    void assertNoLoadedSegment(LazyArrayList<?> list) {
        assertTrue(getLoadedSegments(list).size() == 0);
    }

    static int countSegments(LazyArrayList<?> list) {

        final AtomicInteger count = new AtomicInteger();

        list.segments()
                .forEach(s -> count.incrementAndGet());

        return count.get();
    }

    static List<LazyArrayList<?>.Segment> getLoadedSegments(LazyArrayList<?> list) {

        final Iterable<? extends LazyArrayList<?>.Segment> segments = list.segments();

        final List<LazyArrayList<?>.Segment> loadedSegments = new ArrayList<>();
        segments.forEach(s -> {
            if (s.isLoaded()) {
                loadedSegments.add(s);
            }
        });

        return loadedSegments;
    }

    static EmbeddedStorageManager startStorage(final Path path) {
        final EmbeddedStorageManager storage = EmbeddedStorage
                .Foundation(path)
                .start();
        return storage;
    }
}
