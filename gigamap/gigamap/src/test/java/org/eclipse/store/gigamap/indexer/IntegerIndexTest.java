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
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class IntegerIndexTest
{
    @TempDir
    Path tempDir;

    private final IntegerPersonIndex integerPersonIndex = new IntegerPersonIndex();


    @Test
    void notIn()
    {
        GigaMap<IntegerPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(integerPersonIndex.notIn(5, 6, 7)).count();
            assertEquals(8, count);
            long count1 = map.query(integerPersonIndex.notIn(5, 6, 7, 8)).count();
            assertEquals(7, count1);
            long count2 = map.query(integerPersonIndex.notIn(5, 6, 7, 8, 9)).count();
            assertEquals(6, count2);
            long count3 = map.query(integerPersonIndex.notIn(5, 6, 7, 8, 9, 10)).count();
            assertEquals(5, count3);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            long count = newMap.query(integerPersonIndex.notIn(5, 6, 7)).count();
            assertEquals(8, count);
            long count1 = newMap.query(integerPersonIndex.notIn(5, 6, 7, 8)).count();
            assertEquals(7, count1);
            long count2 = newMap.query(integerPersonIndex.notIn(5, 6, 7, 8, 9)).count();
            assertEquals(6, count2);
            long count3 = newMap.query(integerPersonIndex.notIn(5, 6, 7, 8, 9, 10)).count();
            assertEquals(5, count3);
        }
    }

    @Test
    void in()
    {
        GigaMap<IntegerPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(integerPersonIndex.in(5, 6, 7)).count();
            assertEquals(3, count);
            long count1 = map.query(integerPersonIndex.in(5, 6, 7, 8)).count();
            assertEquals(4, count1);
            long count2 = map.query(integerPersonIndex.in(5, 6, 7, 8, 9)).count();
            assertEquals(5, count2);
            long count3 = map.query(integerPersonIndex.in(5, 6, 7, 8, 9, 10)).count();
            assertEquals(6, count3);
        }
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            long count = newMap.query(integerPersonIndex.in(5, 6, 7)).count();
            assertEquals(3, count);
            long count1 = newMap.query(integerPersonIndex.in(5, 6, 7, 8)).count();
            assertEquals(4, count1);
            long count2 = newMap.query(integerPersonIndex.in(5, 6, 7, 8, 9)).count();
            assertEquals(5, count2);
            long count3 = newMap.query(integerPersonIndex.in(5, 6, 7, 8, 9, 10)).count();
            assertEquals(6, count3);
        }
    }

    @Test
    void notByExample()
    {
        GigaMap<IntegerPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            IntegerPerson integerPerson = map.get(1);
            long count1 = map.query(integerPersonIndex.notByExample(integerPerson)).count();
            assertEquals(10, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            IntegerPerson integerPerson = newMap.get(1);
            long count1 = newMap.query(integerPersonIndex.notByExample(integerPerson)).count();
            assertEquals(10, count1);
        }
    }

    @Test
    void byExample()
    {
        GigaMap<IntegerPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            IntegerPerson integerPerson = map.get(1);
            long count1 = map.query(integerPersonIndex.byExample(integerPerson)).count();
            assertEquals(1, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            IntegerPerson integerPerson = newMap.get(1);
            long count1 = newMap.query(integerPersonIndex.byExample(integerPerson)).count();
            assertEquals(1, count1);
        }
    }

    @Test
    void unlike()
    {
        GigaMap<IntegerPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            IntegerPerson integerPerson = map.get(1);
            long count1 = map.query(integerPersonIndex.unlike(integerPerson)).count();
            assertEquals(10, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            IntegerPerson integerPerson = newMap.get(1);
            long count1 = newMap.query(integerPersonIndex.unlike(integerPerson)).count();
            assertEquals(10, count1);
        }
    }

    @Test
    void like()
    {
        GigaMap<IntegerPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            IntegerPerson integerPerson = map.get(1);
            long count1 = map.query(integerPersonIndex.like(integerPerson)).count();
            assertEquals(1, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            IntegerPerson integerPerson = newMap.get(1);
            long count1 = newMap.query(integerPersonIndex.like(integerPerson)).count();
            assertEquals(1, count1);
        }
    }

    @Test
    void notNull()
    {
        GigaMap<IntegerPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(integerPersonIndex.notNull()).count();
            assertEquals(11, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            long count = newMap.query(integerPersonIndex.notNull()).count();
            assertEquals(11, count);

        }
    }

    @Test
    void notTest()
    {
        GigaMap<IntegerPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(integerPersonIndex.not(5)).count();
            assertEquals(10, count);
            long count2 = map.query(integerPersonIndex.not(15)).count();
            assertEquals(11, count2);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            long count = newMap.query(integerPersonIndex.not(5)).count();
            assertEquals(10, count);
            long count2 = newMap.query(integerPersonIndex.not(15)).count();
            assertEquals(11, count2);
        }

    }

    @Test
    void isNull()
    {
        GigaMap<IntegerPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(integerPersonIndex.isNull()).count();
            assertEquals(0, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            long count = newMap.query(integerPersonIndex.isNull()).count();
            assertEquals(0, count);

        }

    }

    @Test
    void between()
    {
        GigaMap<IntegerPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(integerPersonIndex.between(5, 8)).count();
            assertEquals(4, count);
            long count1 = map.query(integerPersonIndex.between(4, 8)).count();
            assertEquals(5, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            long count = newMap.query(integerPersonIndex.between(5, 8)).count();
            assertEquals(4, count);
            long count1 = newMap.query(integerPersonIndex.between(4, 8)).count();
            assertEquals(5, count1);
        }
    }

    @Test
    void greaterThanEqual()
    {
        GigaMap<IntegerPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(integerPersonIndex.greaterThanEqual(5)).count();
            assertEquals(6, count);
            long count1 = map.query(integerPersonIndex.greaterThanEqual(4)).count();
            assertEquals(7, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            long count = newMap.query(integerPersonIndex.greaterThanEqual(5)).count();
            assertEquals(6, count);
            long count1 = newMap.query(integerPersonIndex.greaterThanEqual(4)).count();
            assertEquals(7, count1);
        }
    }

    @Test
    void greaterThan()
    {
        GigaMap<IntegerPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(integerPersonIndex.greaterThan(5)).count();
            assertEquals(5, count);
            long count1 = map.query(integerPersonIndex.greaterThan(4)).count();
            assertEquals(6, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            long count = newMap.query(integerPersonIndex.greaterThan(5)).count();
            assertEquals(5, count);
            long count1 = newMap.query(integerPersonIndex.greaterThan(4)).count();
            assertEquals(6, count1);
        }
    }

    @Test
    void lessThanEqual()
    {
        GigaMap<IntegerPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(integerPersonIndex.lessThanEqual(5)).count();
            assertEquals(6, count);
            long count1 = map.query(integerPersonIndex.lessThanEqual(4)).count();
            assertEquals(5, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            long count = newMap.query(integerPersonIndex.lessThanEqual(5)).count();
            assertEquals(6, count);
            long count1 = newMap.query(integerPersonIndex.lessThanEqual(4)).count();
            assertEquals(5, count1);
        }
    }

    @Test
    void lessThan()
    {
        GigaMap<IntegerPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(integerPersonIndex.lessThan(5)).count();
            assertEquals(5, count);
            long count1 = map.query(integerPersonIndex.lessThan(4)).count();
            assertEquals(4, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            long count = newMap.query(integerPersonIndex.lessThan(5)).count();
            assertEquals(5, count);
            long count1 = newMap.query(integerPersonIndex.lessThan(4)).count();
            assertEquals(4, count1);
        }
    }

    @Test
    void integerPersonTest()
    {
        GigaMap<IntegerPerson> map = GigaMap.New();

        BitmapIndices<IntegerPerson> bitmap = map.index().bitmap();
        bitmap.add(integerPersonIndex);

        //generate some data
        IntegerPerson person1 = new IntegerPerson("Alice", 20);
        IntegerPerson person2 = new IntegerPerson("Bob", 30);
        IntegerPerson person3 = new IntegerPerson("Charlie", 40);
        map.addAll(person1, person2, person3);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.query(integerPersonIndex.is(20)).forEach(integerPerson -> assertEquals(20, integerPerson.getAge()));
            map.query(integerPersonIndex.is(30)).forEach(integerPerson -> assertEquals(30, integerPerson.getAge()));

            map.query(integerPersonIndex.is(20)).and(integerPersonIndex.is(30))
                    .forEach(integerPerson -> fail("Should not be reached"));

            List<IntegerPerson> personList = map.query(integerPersonIndex.is(20)).or(integerPersonIndex.is(30))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<IntegerPerson> newMap = (GigaMap<IntegerPerson>) manager.root();
            newMap.query(integerPersonIndex.is(20)).forEach(integerPerson -> assertEquals(20, integerPerson.getAge()));
            newMap.query(integerPersonIndex.is(30)).forEach(integerPerson -> assertEquals(30, integerPerson.getAge()));

            newMap.query(integerPersonIndex.is(20)).and(integerPersonIndex.is(30))
                    .forEach(integerPerson -> fail("Should not be reached"));

            List<IntegerPerson> personList = newMap.query(integerPersonIndex.is(20)).or(integerPersonIndex.is(30))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }
    }

    private GigaMap<IntegerPerson> prepageGigaMap()
    {
        GigaMap<IntegerPerson> map = GigaMap.New();

        BitmapIndices<IntegerPerson> bitmap = map.index().bitmap();
        bitmap.add(integerPersonIndex);

        map.addAll(
                new IntegerPerson("Eliza", 0),
                new IntegerPerson("Alice", 1),
                new IntegerPerson("Bob", 2),
                new IntegerPerson("Charlie", 3),
                new IntegerPerson("David", 4),
                new IntegerPerson("Eve", 5),
                new IntegerPerson("Frank", 6),
                new IntegerPerson("Grace", 7),
                new IntegerPerson("Hank", 8),
                new IntegerPerson("Ivy", 9),
                new IntegerPerson("Jack", 10)
        );

        return map;
    }

    private static class IntegerPersonIndex extends IndexerInteger.Abstract<IntegerPerson>
    {
        @Override
        protected Integer getInteger(IntegerPerson entity)
        {
            return entity.getAge();
        }
    }

    private static class IntegerPerson
    {
        private final String name;
        private final int age;

        public IntegerPerson(String name, int age)
        {
            this.name = name;
            this.age = age;
        }

        public String name()
        {
            return this.name;
        }

        public int getAge()
        {
            return this.age;
        }
    }
}
