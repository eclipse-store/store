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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerShort;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ShortIndexTest
{
    @TempDir
    Path tempDir;

    ShortPersonIndex shortPersonIndex = new ShortPersonIndex();

    @Test
    void nullValueTest()
    {
        GigaMap<ShortPerson> map = GigaMap.New();
        map.index().bitmap().add(shortPersonIndex);

        ShortPerson person = new ShortPerson("Alice", (short) 30);
        map.add(person);
        ShortPerson person2 = new ShortPerson(null, null);
        map.add(person2);
        assertEquals(0, map.query(shortPersonIndex.lessThan(null)).count());
        assertEquals(0, map.query(shortPersonIndex.lessThanEqual(null)).count());
        assertEquals(0, map.query(shortPersonIndex.greaterThan(null)).count());
        assertEquals(0, map.query(shortPersonIndex.greaterThanEqual(null)).count());
        assertEquals(0, map.query(shortPersonIndex.between(null, (short) 30)).count());
        assertEquals(0, map.query(shortPersonIndex.between((short) 30, null)).count());

    }

    @Test
    void between()
    {
        GigaMap<ShortPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(shortPersonIndex.between((short) 20, (short) 30)).count();
            assertEquals(2, count);
            map.query(shortPersonIndex.between((short) 20, (short) 30)).forEach(shortPerson -> assertTrue(shortPerson.getAge() >= 20 && shortPerson.getAge() <= 30));
            map.query(shortPersonIndex.between((short) 30, (short) 40)).forEach(shortPerson -> assertTrue(shortPerson.getAge() >= 30 && shortPerson.getAge() <= 40));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ShortPerson> newMap = (GigaMap<ShortPerson>) manager.root();
            long count = map.query(shortPersonIndex.between((short) 20, (short) 30)).count();
            assertEquals(2, count);
            newMap.query(shortPersonIndex.between((short) 20, (short) 30)).forEach(shortPerson -> assertTrue(shortPerson.getAge() >= 20 && shortPerson.getAge() <= 30));
            newMap.query(shortPersonIndex.between((short) 30, (short) 40)).forEach(shortPerson -> assertTrue(shortPerson.getAge() >= 30 && shortPerson.getAge() <= 40));
        }
    }

    @Test
    void between_updateApi()
    {
        GigaMap<ShortPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(shortPersonIndex.between((short) 20, (short) 30)).count();
            assertEquals(2, count);
            map.query(shortPersonIndex.between((short) 20, (short) 30)).forEach(shortPerson -> assertTrue(shortPerson.getAge() >= 20 && shortPerson.getAge() <= 30));
            map.query(shortPersonIndex.between((short) 30, (short) 40)).forEach(shortPerson -> assertTrue(shortPerson.getAge() >= 30 && shortPerson.getAge() <= 40));
        }

        GigaMap<ShortPerson> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, tempDir)) {
            long count = map.query(shortPersonIndex.between((short) 20, (short) 30)).count();
            assertEquals(2, count);
            newMap.query(shortPersonIndex.between((short) 20, (short) 30)).forEach(shortPerson -> assertTrue(shortPerson.getAge() >= 20 && shortPerson.getAge() <= 30));
            newMap.query(shortPersonIndex.between((short) 30, (short) 40)).forEach(shortPerson -> assertTrue(shortPerson.getAge() >= 30 && shortPerson.getAge() <= 40));

            newMap.add(new ShortPerson("Alice", (short) 31));
            newMap.store();
        }

        GigaMap<ShortPerson> newMap2 = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap2, tempDir)) {
            long count = newMap2.query(shortPersonIndex.between((short) 20, (short) 31)).count();
            assertEquals(3, count);
            newMap2.query(shortPersonIndex.between((short) 20, (short) 30)).forEach(shortPerson -> assertTrue(shortPerson.getAge() >= 20 && shortPerson.getAge() <= 30));
            newMap2.query(shortPersonIndex.between((short) 30, (short) 40)).forEach(shortPerson -> assertTrue(shortPerson.getAge() >= 30 && shortPerson.getAge() <= 40));
        }
    }

    @Test
    void greaterThanEqual()
    {
        GigaMap<ShortPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(shortPersonIndex.greaterThanEqual((short) 20)).count();
            assertEquals(3, count);
            map.query(shortPersonIndex.greaterThanEqual((short) 20)).forEach(shortPerson -> assertTrue(shortPerson.getAge() >= 20));
            map.query(shortPersonIndex.greaterThanEqual((short) 40)).forEach(shortPerson -> assertTrue(shortPerson.getAge() >= 40));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ShortPerson> newMap = (GigaMap<ShortPerson>) manager.root();
            long count = map.query(shortPersonIndex.greaterThanEqual((short) 20)).count();
            assertEquals(3, count);
            newMap.query(shortPersonIndex.greaterThanEqual((short) 20)).forEach(shortPerson -> assertTrue(shortPerson.getAge() >= 20));
            newMap.query(shortPersonIndex.greaterThanEqual((short) 40)).forEach(shortPerson -> assertTrue(shortPerson.getAge() >= 40));
        }
    }

    @Test
    void greaterThan()
    {
        GigaMap<ShortPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(shortPersonIndex.greaterThan((short) 20)).count();
            assertEquals(2, count);
            map.query(shortPersonIndex.greaterThan((short) 20)).forEach(shortPerson -> assertTrue(shortPerson.getAge() > 20));
            map.query(shortPersonIndex.greaterThan((short) 40)).forEach(shortPerson -> fail("Should not be reached"));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ShortPerson> newMap = (GigaMap<ShortPerson>) manager.root();
            long count = map.query(shortPersonIndex.greaterThan((short) 20)).count();
            assertEquals(2, count);
            newMap.query(shortPersonIndex.greaterThan((short) 20)).forEach(shortPerson -> assertTrue(shortPerson.getAge() > 20));
            newMap.query(shortPersonIndex.greaterThan((short) 40)).forEach(shortPerson -> fail("Should not be reached"));
        }
    }

    @Test
    void lessThanEqual()
    {
        GigaMap<ShortPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(shortPersonIndex.lessThanEqual((short) 30)).count();
            assertEquals(2, count);
            map.query(shortPersonIndex.lessThanEqual((short) 30)).forEach(shortPerson -> assertTrue(shortPerson.getAge() <= 30));
            map.query(shortPersonIndex.lessThanEqual((short) 20)).forEach(shortPerson -> assertTrue(shortPerson.getAge() <= 20));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ShortPerson> newMap = (GigaMap<ShortPerson>) manager.root();
            long count = map.query(shortPersonIndex.lessThanEqual((short) 30)).count();
            assertEquals(2, count);
            newMap.query(shortPersonIndex.lessThanEqual((short) 30)).forEach(shortPerson -> assertTrue(shortPerson.getAge() <= 30));
            newMap.query(shortPersonIndex.lessThanEqual((short) 20)).forEach(shortPerson -> assertTrue(shortPerson.getAge() <= 20));
        }
    }

    @Test
    void lessThan()
    {
        GigaMap<ShortPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(shortPersonIndex.lessThan((short) 30)).count();
            assertEquals(1, count);
            map.query(shortPersonIndex.lessThan((short) 30)).forEach(shortPerson -> assertTrue(shortPerson.getAge() < 30));
            map.query(shortPersonIndex.lessThan((short) 20)).forEach(shortPerson -> fail("Should not be reached"));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ShortPerson> newMap = (GigaMap<ShortPerson>) manager.root();
            long count = map.query(shortPersonIndex.lessThan((short) 30)).count();
            assertEquals(1, count);
            newMap.query(shortPersonIndex.lessThan((short) 30)).forEach(shortPerson -> assertTrue(shortPerson.getAge() < 30));
            newMap.query(shortPersonIndex.lessThan((short) 20)).forEach(shortPerson -> fail("Should not be reached"));
        }
    }

    @Test
    void shortPersonTest()
    {
        GigaMap<ShortPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.query(shortPersonIndex.is((short) 20)).forEach(shortPerson -> assertEquals((short) 20, shortPerson.getAge()));
            map.query(shortPersonIndex.is((short) 30)).forEach(shortPerson -> assertEquals((short) 30, shortPerson.getAge()));

            map.query(shortPersonIndex.is((short) 20)).and(shortPersonIndex.is((short) 30))
                    .forEach(shortPerson -> fail("Should not be reached"));

            List<ShortPerson> personList = map.query(shortPersonIndex.is((short) 20)).or(shortPersonIndex.is((short) 30))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ShortPerson> newMap = (GigaMap<ShortPerson>) manager.root();
            newMap.query(shortPersonIndex.is((short) 20)).forEach(shortPerson -> assertEquals((short) 20, shortPerson.getAge()));
            newMap.query(shortPersonIndex.is((short) 30)).forEach(shortPerson -> assertEquals((short) 30, shortPerson.getAge()));

            newMap.query(shortPersonIndex.is((short) 20)).and(shortPersonIndex.is((short) 30))
                    .forEach(shortPerson -> fail("Should not be reached"));

            List<ShortPerson> personList = newMap.query(shortPersonIndex.is((short) 20)).or(shortPersonIndex.is((short) 30))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }
    }

    private GigaMap<ShortPerson> prepageGigaMap()
    {
        GigaMap<ShortPerson> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(shortPersonIndex);

        ShortPerson person1 = new ShortPerson("Alice", (short) 20);
        ShortPerson person2 = new ShortPerson("Bob", (short) 30);
        ShortPerson person3 = new ShortPerson("Charlie", (short) 40);
        gigaMap.addAll(person1, person2, person3);

        return gigaMap;
    }

    private static class ShortPersonIndex extends IndexerShort.Abstract<ShortPerson>
    {
        @Override
        protected Short getShort(ShortPerson entity)
        {
            return entity.getAge();
        }
    }

    private static class ShortPerson
    {
        private final String name;
        private final Short age;

        public ShortPerson(String name, Short age)
        {
            this.name = name;
            this.age = age;
        }

        public String name()
        {
            return this.name;
        }

        public Short getAge()
        {
            return this.age;
        }
    }
}
