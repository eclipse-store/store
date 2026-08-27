package test.eclipse.store.collections.lazy.arraylist.unload;

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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.eclipse.serializer.collections.lazy.LazySegmentUnloader;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


public class Unloader
{

    @TempDir
    Path location;

    @Test
    void unloadTestTimedUnloader() throws InterruptedException
    {
        LazyArrayList<Person> personList;
        personList = (LazyArrayList<Person>) Generator.generatePersons(8_000, new LazySegmentUnloader.Timed(100));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(personList, location)) {

            List<String> collect = personList.stream()
                    .map(p -> p.getLastname())
                    .collect(Collectors.toList());

            Thread.sleep(210);
            personList.get(0);

            Assertions.assertEquals(1, getLoadedSegments(personList).size());
        }
    }

    @Test
    void unloadTestDefaultUnloader() throws InterruptedException
    {
        LazyArrayList<Person> personList;
        personList = (LazyArrayList<Person>) Generator.generatePersons(8_000, new LazySegmentUnloader.Default());

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(personList, location)) {

            List<String> collect = personList.stream()
                    .map(p -> p.getLastname())
                    .collect(Collectors.toList());

            Thread.sleep(210);
            personList.get(0);

            Assertions.assertEquals(2, getLoadedSegments(personList).size());
        }
    }

    @Test
    void unloadTestNeverUnloader() throws InterruptedException
    {
        LazyArrayList<Person> personList;
        personList = (LazyArrayList<Person>) Generator.generatePersons(8000, new LazySegmentUnloader.Never(), 100);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(personList, location)) {

            List<String> collect = personList.stream()
                    .map(p -> p.getLastname())
                    .collect(Collectors.toList());

            Thread.sleep(100);
            personList.get(0);

            Assertions.assertEquals(8, getLoadedSegments(personList).size());
        }
    }


    static List<LazyArrayList<?>.Segment> getLoadedSegments(LazyArrayList<?> list)
    {

        final Iterable<? extends LazyArrayList<?>.Segment> segments = list.segments();

        final List<LazyArrayList<?>.Segment> loadedSegments = new ArrayList<>();
        segments.forEach(s -> {
            if (s.isLoaded()) {
                loadedSegments.add(s);
            }
        });

        return loadedSegments;
    }

    @Test
    void unloadTestTimedUnloader_parallelStream() throws InterruptedException
    {
        LazyArrayList<Person> personList;
        personList = (LazyArrayList<Person>) Generator.generatePersons(8000, new LazySegmentUnloader.Timed(50), 500);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(personList, location)) {

            List<String> collect = personList.parallelStream()
                    .map(p -> p.getLastname())
                    .collect(Collectors.toList());

            Thread.sleep(110);
            personList.get(0);

            Assertions.assertEquals(1, getLoadedSegments(personList).size());
        }
    }

    @Test
    void streamOf_Test() throws InterruptedException
    {
        LazyArrayList<Person> personList;
        personList = (LazyArrayList<Person>) Generator.generatePersons(800, new LazySegmentUnloader.Timed(100), 100);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(personList, location)) {

            Stream<Person> personStream = personList.stream();

            personStream.forEach(person -> person.getLastname());

            Thread.sleep(210);
            personList.get(0);

            Assertions.assertEquals(1, getLoadedSegments(personList).size());
        }
    }

    @Test
    void shuffle_unloader_test() throws InterruptedException
    {
        LazyArrayList<Person> personList;
        personList = (LazyArrayList<Person>) Generator.generatePersons(1000, new LazySegmentUnloader.Timed(50), 100);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(personList, location)) {

            Collections.shuffle(personList);
            storageManager.store(personList);

            Thread.sleep(120);
            personList.get(0);

            Assertions.assertEquals(1, getLoadedSegments(personList).size());
        }
    }

    @Test
    void manual_unloader_test() throws InterruptedException
    {
        LazyArrayList<Person> personList;
        personList = (LazyArrayList<Person>) Generator.generatePersons(8_000, new LazySegmentUnloader.Timed(50), 500);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(personList, location)) {

            Thread.sleep(110);
            personList.tryUnload(true);

            Assertions.assertEquals(0, getLoadedSegments(personList).size());

            personList.parallelStream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
            Assertions.assertTrue(getLoadedSegments(personList).size() > 0); //some are loaded
            Thread.sleep(110);
            personList.tryUnload(true);
            Assertions.assertEquals(0, getLoadedSegments(personList).size());
        }
    }

    @Test
    void manual_unloader_test_tryUnload_false() throws InterruptedException
    {
        LazyArrayList<Person> personList;
        personList = (LazyArrayList<Person>) Generator.generatePersons(20_000, new LazySegmentUnloader.Default());

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(personList, location)) {

            personList.tryUnload(false);
            Assertions.assertEquals(2, getLoadedSegments(personList).size());
            personList.tryUnload(true);
            Assertions.assertEquals(0, getLoadedSegments(personList).size());
        }
    }
}
