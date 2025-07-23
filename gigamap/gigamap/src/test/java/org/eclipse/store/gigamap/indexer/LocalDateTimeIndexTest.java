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
import org.eclipse.store.gigamap.types.IndexerLocalDateTime;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class LocalDateTimeIndexTest
{
    @TempDir
    Path tempDir;

    private LocalDateTimePersonIndex localDateTimePersonIndex = new LocalDateTimePersonIndex();

    @Test
    void nullTests()
    {
        GigaMap<LocalDateTimePerson> map = prepageGigaMap();

        assertEquals(1, map.query(localDateTimePersonIndex.is((LocalDateTime) null)).count());

        assertThrows(IllegalArgumentException.class, () -> map.query(localDateTimePersonIndex.before(null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(localDateTimePersonIndex.beforeEqual(null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(localDateTimePersonIndex.after(null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(localDateTimePersonIndex.afterEqual(null)).count());

        assertThrows(IllegalArgumentException.class, () -> map.query(localDateTimePersonIndex.between(null, null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(localDateTimePersonIndex.between(LocalDateTime.of(2021, 1, 1, 12, 0), null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(localDateTimePersonIndex.between(null, LocalDateTime.of(2021, 1, 1, 12, 0))).count());

    }

    @Test
    void between()
    {
        GigaMap<LocalDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDateTimePersonIndex.between(LocalDateTime.of(2021, 1, 1, 12, 0), LocalDateTime.of(2021, 1, 1, 14, 0))).count();
            assertEquals(3, count);
            long count1 = map.query(localDateTimePersonIndex.between(LocalDateTime.of(2021, 1, 1, 12, 0), LocalDateTime.of(2021, 1, 1, 14, 0))).count();
            assertEquals(3, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            long count = newMap.query(localDateTimePersonIndex.between(LocalDateTime.of(2021, 1, 1, 12, 0), LocalDateTime.of(2021, 1, 1, 14, 0))).count();
            assertEquals(3, count);
            long count1 = newMap.query(localDateTimePersonIndex.between(LocalDateTime.of(2021, 1, 1, 12, 0), LocalDateTime.of(2021, 1, 1, 14, 0))).count();
            assertEquals(3, count1);
            long count2 = newMap.query(localDateTimePersonIndex.between(LocalDateTime.of(2021, 1, 1, 13, 0), LocalDateTime.of(2021, 1, 1, 14, 0))).count();
            assertEquals(2, count2);
        }
    }

    @Test
    void after()
    {
        GigaMap<LocalDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDateTimePersonIndex.after(LocalDateTime.of(2021, 1, 1, 12, 0))).count();
            assertEquals(9, count);
            long count1 = map.query(localDateTimePersonIndex.after(LocalDateTime.of(2021, 1, 1, 13, 0))).count();
            assertEquals(8, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            long count = newMap.query(localDateTimePersonIndex.after(LocalDateTime.of(2021, 1, 1, 12, 0))).count();
            assertEquals(9, count);
            long count1 = newMap.query(localDateTimePersonIndex.after(LocalDateTime.of(2021, 1, 1, 13, 0))).count();
            assertEquals(8, count1);
        }
    }

    @Test
    void before()
    {
        GigaMap<LocalDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDateTimePersonIndex.before(LocalDateTime.of(2021, 1, 1, 12, 1))).count();
            assertEquals(1, count);
            long count1 = map.query(localDateTimePersonIndex.before(LocalDateTime.of(2021, 1, 1, 13, 0))).count();
            assertEquals(1, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            long count = newMap.query(localDateTimePersonIndex.before(LocalDateTime.of(2021, 1, 1, 12, 1))).count();
            assertEquals(1, count);
            long count1 = newMap.query(localDateTimePersonIndex.before(LocalDateTime.of(2021, 1, 1, 13, 0))).count();
            assertEquals(1, count1);
        }
    }

    @Test
    void isSecond()
    {
        GigaMap<LocalDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDateTimePersonIndex.isSecond(0)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            long count = newMap.query(localDateTimePersonIndex.isSecond(0)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isMinute()
    {
        GigaMap<LocalDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDateTimePersonIndex.isMinute(0)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            long count = newMap.query(localDateTimePersonIndex.isMinute(0)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isHour()
    {
        GigaMap<LocalDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDateTimePersonIndex.isHour(12)).count();
            assertEquals(1, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            long count = newMap.query(localDateTimePersonIndex.isHour(12)).count();
            assertEquals(1, count);
        }
    }

    @Test
    void isTime()
    {
        GigaMap<LocalDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDateTimePersonIndex.isTime(12, 0, 0)).count();
            assertEquals(1, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            long count = newMap.query(localDateTimePersonIndex.isTime(12, 0, 0)).count();
            assertEquals(1, count);
        }
    }

    @Test
    void isDay()
    {
        GigaMap<LocalDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDateTimePersonIndex.isDay(1)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            long count = newMap.query(localDateTimePersonIndex.isDay(1)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isMonth()
    {
        GigaMap<LocalDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDateTimePersonIndex.isMonth(1)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            long count = newMap.query(localDateTimePersonIndex.isMonth(1)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isYear()
    {
        GigaMap<LocalDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDateTimePersonIndex.isYear(2021)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            long count = newMap.query(localDateTimePersonIndex.isYear(2021)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isDate()
    {
        GigaMap<LocalDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDateTimePersonIndex.isDate(2021, 1, 1)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            long count = newMap.query(localDateTimePersonIndex.isDate(2021, 1, 1)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isDateTime()
    {
        GigaMap<LocalDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDateTimePersonIndex.isDateTime(2021, 1, 1, 12, 0,0)).count();
            assertEquals(1, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            long count = newMap.query(localDateTimePersonIndex.isDateTime(2021, 1, 1, 12, 0,0)).count();
            assertEquals(1, count);
        }

    }

    @Test
    void localDateTimePersonTest()
    {
        GigaMap<LocalDateTimePerson> map = GigaMap.New();

        BitmapIndices<LocalDateTimePerson> bitmap = map.index().bitmap();
        bitmap.add(localDateTimePersonIndex);

        //generate some data
        LocalDateTimePerson person1 = new LocalDateTimePerson("Alice", LocalDateTime.of(2000, 1, 1, 0, 0));
        LocalDateTimePerson person2 = new LocalDateTimePerson("Bob", LocalDateTime.of(1990, 1, 1, 0, 0));
        LocalDateTimePerson person3 = new LocalDateTimePerson("Charlie", LocalDateTime.of(1980, 1, 1, 0, 0));
        map.addAll(person1, person2, person3);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.query(localDateTimePersonIndex.is(LocalDateTime.of(2000, 1, 1, 0, 0))).forEach(localDateTimePerson -> assertEquals(LocalDateTime.of(2000, 1, 1, 0, 0), localDateTimePerson.getBirthday()));
            map.query(localDateTimePersonIndex.is(LocalDateTime.of(1990, 1, 1, 0, 0))).forEach(localDateTimePerson -> assertEquals(LocalDateTime.of(1990, 1, 1, 0, 0), localDateTimePerson.getBirthday()));

            map.query(localDateTimePersonIndex.is(LocalDateTime.of(2000, 1, 1, 0, 0))).and(localDateTimePersonIndex.is(LocalDateTime.of(1990, 1, 1, 0, 0)))
                    .forEach(localDateTimePerson -> fail("Should not be reached"));

            List<LocalDateTimePerson> personList = map.query(localDateTimePersonIndex.is(LocalDateTime.of(2000, 1, 1, 0, 0))).or(localDateTimePersonIndex.is(LocalDateTime.of(1990, 1, 1, 0, 0)))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            newMap.query(localDateTimePersonIndex.is(LocalDateTime.of(2000, 1, 1, 0, 0))).forEach(localDateTimePerson -> assertEquals(LocalDateTime.of(2000, 1, 1, 0, 0), localDateTimePerson.getBirthday()));
            newMap.query(localDateTimePersonIndex.is(LocalDateTime.of(1990, 1, 1, 0, 0))).forEach(localDateTimePerson -> assertEquals(LocalDateTime.of(1990, 1, 1, 0, 0), localDateTimePerson.getBirthday()));

            newMap.query(localDateTimePersonIndex.is(LocalDateTime.of(2000, 1, 1, 0, 0))).and(localDateTimePersonIndex.is(LocalDateTime.of(1990, 1, 1, 0, 0)))
                    .forEach(localDateTimePerson -> fail("Should not be reached"));

            List<LocalDateTimePerson> personList = newMap.query(localDateTimePersonIndex.is(LocalDateTime.of(2000, 1, 1, 0, 0))).or(localDateTimePersonIndex.is(LocalDateTime.of(1990, 1, 1, 0, 0)))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }
    }

    @Test
    void beforeEqualTest()
    {
        GigaMap<LocalDateTimePerson> map = GigaMap.New();

        BitmapIndices<LocalDateTimePerson> bitmap = map.index().bitmap();
        bitmap.add(localDateTimePersonIndex);

        //generate some data
        LocalDateTimePerson person1 = new LocalDateTimePerson("Alice", LocalDateTime.of(2000, 1, 1, 0, 0));
        LocalDateTimePerson person2 = new LocalDateTimePerson("Bob", LocalDateTime.of(1990, 1, 1, 0, 0));
        LocalDateTimePerson person3 = new LocalDateTimePerson("Charlie", LocalDateTime.of(1980, 1, 1, 0, 0));
        map.addAll(person1, person2, person3);

        List<LocalDateTimePerson> list = map.query(localDateTimePersonIndex.beforeEqual(LocalDateTime.of(1990, 1, 1, 0, 0))).toList();
        assertEquals(2, list.size());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            List<LocalDateTimePerson> list1 = newMap.query(localDateTimePersonIndex.beforeEqual(LocalDateTime.of(1990, 1, 1, 0, 0))).toList();
            assertEquals(2, list1.size());
            list1.forEach(localDateTimePerson -> assertNotEquals("Alice", localDateTimePerson.name));
        }
    }

    @Test
    void afterEqualTest()
    {
        GigaMap<LocalDateTimePerson> map = GigaMap.New();

        BitmapIndices<LocalDateTimePerson> bitmap = map.index().bitmap();
        bitmap.add(localDateTimePersonIndex);

        //generate some data
        LocalDateTimePerson person1 = new LocalDateTimePerson("Alice", LocalDateTime.of(2000, 1, 1, 0, 0));
        LocalDateTimePerson person2 = new LocalDateTimePerson("Bob", LocalDateTime.of(1990, 1, 1, 0, 0));
        LocalDateTimePerson person3 = new LocalDateTimePerson("Charlie", LocalDateTime.of(1980, 1, 1, 0, 0));
        map.addAll(person1, person2, person3);

        List<LocalDateTimePerson> list = map.query(localDateTimePersonIndex.afterEqual(LocalDateTime.of(1990, 1, 1, 0, 0))).toList();
        assertEquals(2, list.size());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDateTimePerson> newMap = (GigaMap<LocalDateTimePerson>) manager.root();
            List<LocalDateTimePerson> list1 = newMap.query(localDateTimePersonIndex.afterEqual(LocalDateTime.of(1990, 1, 1, 0, 0))).toList();
            assertEquals(2, list1.size());
            list1.forEach(localDateTimePerson -> assertNotEquals("Charlie", localDateTimePerson.name));
        }
    }

    private GigaMap<LocalDateTimePerson> prepageGigaMap()
    {
        GigaMap<LocalDateTimePerson> map = GigaMap.New();

        BitmapIndices<LocalDateTimePerson> bitmap = map.index().bitmap();
        bitmap.add(localDateTimePersonIndex);

        LocalDateTimePerson person1 = new LocalDateTimePerson("Alice", LocalDateTime.of(2021, 1, 1, 12, 0));
        LocalDateTimePerson person2 = new LocalDateTimePerson("Bob", LocalDateTime.of(2021, 1, 1, 13, 0));
        LocalDateTimePerson person3 = new LocalDateTimePerson("Charlie", LocalDateTime.of(2021, 1, 1, 14, 0));
        LocalDateTimePerson person4 = new LocalDateTimePerson("David", LocalDateTime.of(2021, 1, 1, 15, 0));
        LocalDateTimePerson person5 = new LocalDateTimePerson("Eve", LocalDateTime.of(2021, 1, 1, 16, 0));
        LocalDateTimePerson person6 = new LocalDateTimePerson("Frank", LocalDateTime.of(2021, 1, 1, 17, 0));
        LocalDateTimePerson person7 = new LocalDateTimePerson("Grace", LocalDateTime.of(2021, 1, 1, 18, 0));
        LocalDateTimePerson person8 = new LocalDateTimePerson("Hank", LocalDateTime.of(2021, 1, 1, 19, 0));
        LocalDateTimePerson person9 = new LocalDateTimePerson("Ivy", LocalDateTime.of(2021, 1, 1, 20, 0));
        LocalDateTimePerson person10 = new LocalDateTimePerson("Jack", LocalDateTime.of(2021, 1, 1, 21, 0));
        LocalDateTimePerson person11 = new LocalDateTimePerson("Karl", null);

        map.addAll(person1, person2, person3, person4, person5, person6, person7, person8, person9, person10, person11);

        return map;
    }

    private static class LocalDateTimePersonIndex extends IndexerLocalDateTime.Abstract<LocalDateTimePerson>
    {

        @Override
        protected LocalDateTime getLocalDateTime(LocalDateTimePerson entity)
        {
            return entity.getBirthday();
        }
    }

    private static class LocalDateTimePerson
    {
        private final String name;
        private final LocalDateTime birthday;

        public LocalDateTimePerson(String name, LocalDateTime birthday)
        {
            this.name = name;
            this.birthday = birthday;
        }

        public String name()
        {
            return this.name;
        }

        public LocalDateTime getBirthday()
        {
            return this.birthday;
        }
    }
}
