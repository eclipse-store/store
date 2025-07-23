package org.eclipse.store.gigamap.indexer;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerLong;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class LongIndexTest
{
    @TempDir
    Path tempDir;
    private LongPersonIndex longPersonIndex = new LongPersonIndex();

    @Test
    void nullValuesTest()
    {
        GigaMap<LongPerson> map = prepageGigaMap();
        map.add(new LongPerson("David", null));

        assertEquals(0, map.query(longPersonIndex.lessThan(null)).count());
        assertEquals(0, map.query(longPersonIndex.lessThan(1L)).count());

        assertEquals(0, map.query(longPersonIndex.lessThanEqual(null)).count());
        assertEquals(0, map.query(longPersonIndex.lessThanEqual(1L)).count());

        //greaterThan
        assertEquals(0, map.query(longPersonIndex.greaterThan(null)).count());
        assertEquals(3, map.query(longPersonIndex.greaterThan(1L)).count());

        //greaterThanEqual
        assertEquals(0, map.query(longPersonIndex.greaterThanEqual(null)).count());
        assertEquals(3, map.query(longPersonIndex.greaterThanEqual(1L)).count());

        //between
        assertEquals(0, map.query(longPersonIndex.between(null, null)).count());
        assertEquals(0, map.query(longPersonIndex.between(null, 1L)).count());
        assertEquals(0, map.query(longPersonIndex.between(1L, null)).count());

    }

    @Test
    void between()
    {
        GigaMap<LongPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(longPersonIndex.between(200L, 300L)).count();
            assertEquals(2, count);
            map.query(longPersonIndex.between(200L, 300L)).forEach(longPerson -> assertTrue(longPerson.getSalary() >= 200L && longPerson.getSalary() <= 300L));
            long count1 = map.query(longPersonIndex.between(100L, 200L)).count();
            assertEquals(2, count1);
            map.query(longPersonIndex.between(100L, 200L)).forEach(longPerson -> assertTrue(longPerson.getSalary() >= 100L && longPerson.getSalary() <= 200L));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LongPerson> newMap = (GigaMap<LongPerson>) manager.root();
            long count = newMap.query(longPersonIndex.between(200L, 300L)).count();
            assertEquals(2, count);
            newMap.query(longPersonIndex.between(200L, 300L)).forEach(longPerson -> assertTrue(longPerson.getSalary() >= 200L && longPerson.getSalary() <= 300L));
            long count1 = newMap.query(longPersonIndex.between(100L, 200L)).count();
            assertEquals(2, count1);
            newMap.query(longPersonIndex.between(100L, 200L)).forEach(longPerson -> assertTrue(longPerson.getSalary() >= 100L && longPerson.getSalary() <= 200L));

            long count2 = newMap.query(longPersonIndex.between(50L, 40L)).count();
            assertEquals(0, count2);
            newMap.query(longPersonIndex.between(50L, 40L)).forEach((person) -> fail("Should not be reached"));

        }
    }

    @Test
    void greaterThanEqual()
    {
        GigaMap<LongPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(longPersonIndex.greaterThanEqual(200L)).count();
            assertEquals(2, count);
            map.query(longPersonIndex.greaterThanEqual(200L)).forEach(longPerson -> assertTrue(longPerson.getSalary() >= 200L));
            long count1 = map.query(longPersonIndex.greaterThanEqual(100L)).count();
            assertEquals(3, count1);
            map.query(longPersonIndex.greaterThanEqual(100L)).forEach(longPerson -> assertTrue(longPerson.getSalary() >= 100L));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LongPerson> newMap = (GigaMap<LongPerson>) manager.root();
            long count = newMap.query(longPersonIndex.greaterThanEqual(200L)).count();
            assertEquals(2, count);
            newMap.query(longPersonIndex.greaterThanEqual(200L)).forEach(longPerson -> assertTrue(longPerson.getSalary() >= 200L));
            long count1 = newMap.query(longPersonIndex.greaterThanEqual(100L)).count();
            assertEquals(3, count1);
            newMap.query(longPersonIndex.greaterThanEqual(100L)).forEach(longPerson -> assertTrue(longPerson.getSalary() >= 100L));
        }
    }

    @Test
    void greaterThan()
    {
        GigaMap<LongPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(longPersonIndex.greaterThan(100L)).count();
            assertEquals(2, count);
            map.query(longPersonIndex.greaterThan(100L)).forEach(longPerson -> assertTrue(longPerson.getSalary() > 100L));
            long count1 = map.query(longPersonIndex.greaterThan(200L)).count();
            assertEquals(1, count1);
            long count2 = map.query(longPersonIndex.greaterThan(300L)).count();
            assertEquals(0, count2);
            map.query(longPersonIndex.greaterThan(300L)).forEach((longPersonIndex) -> fail("Should not be reached"));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LongPerson> newMap = (GigaMap<LongPerson>) manager.root();
            long count = newMap.query(longPersonIndex.greaterThan(100L)).count();
            assertEquals(2, count);
            newMap.query(longPersonIndex.greaterThan(100L)).forEach(longPerson -> assertTrue(longPerson.getSalary() > 100L));
            newMap.query(longPersonIndex.greaterThan(300L)).forEach((longPersonIndex) -> fail("Should not be reached"));
        }
    }

    @Test
    void lessThanEqual()
    {
        GigaMap<LongPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(longPersonIndex.lessThanEqual(200L)).count();
            assertEquals(2, count);
            map.query(longPersonIndex.lessThanEqual(200L)).forEach(longPerson -> assertTrue(longPerson.getSalary() <= 200L));
            long count1 = map.query(longPersonIndex.lessThanEqual(100L)).count();
            assertEquals(1, count1);
            map.query(longPersonIndex.lessThanEqual(100L)).forEach(longPerson -> assertTrue(longPerson.getSalary() <= 100L));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LongPerson> newMap = (GigaMap<LongPerson>) manager.root();
            long count = newMap.query(longPersonIndex.lessThanEqual(200L)).count();
            assertEquals(2, count);
            newMap.query(longPersonIndex.lessThanEqual(200L)).forEach(longPerson -> assertTrue(longPerson.getSalary() <= 200L));
            long lessThan1 = newMap.query(longPersonIndex.lessThanEqual(100L)).count();
            assertEquals(1, lessThan1);
            newMap.query(longPersonIndex.lessThanEqual(100L)).forEach(longPerson -> assertTrue(longPerson.getSalary() <= 100L));
        }
    }

    @Test
    void lessThan()
    {
        GigaMap<LongPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(longPersonIndex.lessThan(200L)).count();
            assertEquals(1, count);
            map.query(longPersonIndex.lessThan(200L)).forEach(longPerson -> assertTrue(longPerson.getSalary() < 200L));
            long count1 = map.query(longPersonIndex.lessThan(100L)).count();
            assertEquals(0, count1);
            map.query(longPersonIndex.lessThan(100L)).forEach(person -> fail("Should not be reached"));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LongPerson> newMap = (GigaMap<LongPerson>) manager.root();
            long count = newMap.query(longPersonIndex.lessThan(200L)).count();
            assertEquals(1, count);
            newMap.query(longPersonIndex.lessThan(200L)).forEach(longPerson -> assertTrue(longPerson.getSalary() < 200L));
            newMap.query(longPersonIndex.lessThan(100L)).forEach(longPersonIndex -> fail("Should not be reached"));
        }
    }

    @Test
    void longPersonTest()
    {
        GigaMap<LongPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.query(longPersonIndex.is(100L)).forEach(longPerson -> assertEquals(100L, longPerson.getSalary()));
            map.query(longPersonIndex.is(200L)).forEach(longPerson -> assertEquals(200L, longPerson.getSalary()));

            map.query(longPersonIndex.is(100L)).and(longPersonIndex.is(200L))
                    .forEach(longPerson -> fail("Should not be reached"));

            List<LongPerson> personList = map.query(longPersonIndex.is(100L)).or(longPersonIndex.is(200L))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LongPerson> newMap = (GigaMap<LongPerson>) manager.root();
            newMap.query(longPersonIndex.is(100L)).forEach(longPerson -> assertEquals(100L, longPerson.getSalary()));
            newMap.query(longPersonIndex.is(200L)).forEach(longPerson -> assertEquals(200L, longPerson.getSalary()));

            newMap.query(longPersonIndex.is(100L)).and(longPersonIndex.is(200L))
                    .forEach(longPerson -> fail("Should not be reached"));

            List<LongPerson> personList = newMap.query(longPersonIndex.is(100L)).or(longPersonIndex.is(200L))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }
    }

    private GigaMap<LongPerson> prepageGigaMap()
    {
        GigaMap<LongPerson> map = GigaMap.New();

        BitmapIndices<LongPerson> bitmap = map.index().bitmap();
        bitmap.add(longPersonIndex);

        //generate some data
        LongPerson person1 = new LongPerson("Alice", 100L);
        LongPerson person2 = new LongPerson("Bob", 200L);
        LongPerson person3 = new LongPerson("Charlie", 300L);
        map.addAll(person1, person2, person3);
        return map;
    }

    private static class LongPersonIndex extends IndexerLong.Abstract<LongPerson>
    {
        @Override
        protected Long getLong(LongPerson entity)
        {
            return entity.getSalary();
        }
    }

    private static class LongPerson
    {
        private final String name;
        private final Long salary;

        public LongPerson(String name, Long salary)
        {
            this.name = name;
            this.salary = salary;
        }

        public String name()
        {
            return this.name;
        }

        public Long getSalary()
        {
            return this.salary;
        }
    }
}
