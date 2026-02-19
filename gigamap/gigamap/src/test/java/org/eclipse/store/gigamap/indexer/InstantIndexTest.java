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
import org.eclipse.store.gigamap.types.IndexerInstant;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class InstantIndexTest
{
    @TempDir
    Path tempDir;

    private InstantPersonIndex instantPersonIndex = new InstantPersonIndex();

    @Test
    void nullTests()
    {
        GigaMap<InstantPerson> map = prepageGigaMap();

        assertEquals(1, map.query(instantPersonIndex.is((Instant) null)).count());

        assertThrows(IllegalArgumentException.class, () -> map.query(instantPersonIndex.before(null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(instantPersonIndex.beforeEqual(null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(instantPersonIndex.after(null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(instantPersonIndex.afterEqual(null)).count());

        assertThrows(IllegalArgumentException.class, () -> map.query(instantPersonIndex.between(null, null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(instantPersonIndex.between(toInstant(2021, 1, 1, 12, 0, 0), null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(instantPersonIndex.between(null, toInstant(2021, 1, 1, 12, 0, 0))).count());

    }

    @Test
    void between()
    {
        GigaMap<InstantPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(instantPersonIndex.between(toInstant(2021, 1, 1, 12, 0, 0), toInstant(2021, 1, 1, 14, 0, 0))).count();
            assertEquals(3, count);
            long count1 = map.query(instantPersonIndex.between(toInstant(2021, 1, 1, 12, 0, 0), toInstant(2021, 1, 1, 14, 0, 0))).count();
            assertEquals(3, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            long count = newMap.query(instantPersonIndex.between(toInstant(2021, 1, 1, 12, 0, 0), toInstant(2021, 1, 1, 14, 0, 0))).count();
            assertEquals(3, count);
            long count1 = newMap.query(instantPersonIndex.between(toInstant(2021, 1, 1, 12, 0, 0), toInstant(2021, 1, 1, 14, 0, 0))).count();
            assertEquals(3, count1);
            long count2 = newMap.query(instantPersonIndex.between(toInstant(2021, 1, 1, 13, 0, 0), toInstant(2021, 1, 1, 14, 0, 0))).count();
            assertEquals(2, count2);
        }
    }

    @Test
    void after()
    {
        GigaMap<InstantPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(instantPersonIndex.after(toInstant(2021, 1, 1, 12, 0, 0))).count();
            assertEquals(9, count);
            long count1 = map.query(instantPersonIndex.after(toInstant(2021, 1, 1, 13, 0, 0))).count();
            assertEquals(8, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            long count = newMap.query(instantPersonIndex.after(toInstant(2021, 1, 1, 12, 0, 0))).count();
            assertEquals(9, count);
            long count1 = newMap.query(instantPersonIndex.after(toInstant(2021, 1, 1, 13, 0, 0))).count();
            assertEquals(8, count1);
        }
    }

    @Test
    void before()
    {
        GigaMap<InstantPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(instantPersonIndex.before(toInstant(2021, 1, 1, 12, 0, 1))).count();
            assertEquals(1, count);
            long count1 = map.query(instantPersonIndex.before(toInstant(2021, 1, 1, 13, 0, 0))).count();
            assertEquals(1, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            long count = newMap.query(instantPersonIndex.before(toInstant(2021, 1, 1, 12, 0, 1))).count();
            assertEquals(1, count);
            long count1 = newMap.query(instantPersonIndex.before(toInstant(2021, 1, 1, 13, 0, 0))).count();
            assertEquals(1, count1);
        }
    }

    @Test
    void isSecond()
    {
        GigaMap<InstantPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(instantPersonIndex.isSecond(0)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            long count = newMap.query(instantPersonIndex.isSecond(0)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isMinute()
    {
        GigaMap<InstantPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(instantPersonIndex.isMinute(0)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            long count = newMap.query(instantPersonIndex.isMinute(0)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isHour()
    {
        GigaMap<InstantPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(instantPersonIndex.isHour(12)).count();
            assertEquals(1, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            long count = newMap.query(instantPersonIndex.isHour(12)).count();
            assertEquals(1, count);
        }
    }

    @Test
    void isTime()
    {
        GigaMap<InstantPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(instantPersonIndex.isTime(12, 0, 0)).count();
            assertEquals(1, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            long count = newMap.query(instantPersonIndex.isTime(12, 0, 0)).count();
            assertEquals(1, count);
        }
    }

    @Test
    void isDay()
    {
        GigaMap<InstantPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(instantPersonIndex.isDay(1)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            long count = newMap.query(instantPersonIndex.isDay(1)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isMonth()
    {
        GigaMap<InstantPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(instantPersonIndex.isMonth(1)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            long count = newMap.query(instantPersonIndex.isMonth(1)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isYear()
    {
        GigaMap<InstantPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(instantPersonIndex.isYear(2021)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            long count = newMap.query(instantPersonIndex.isYear(2021)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isDate()
    {
        GigaMap<InstantPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(instantPersonIndex.isDate(2021, 1, 1)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            long count = newMap.query(instantPersonIndex.isDate(2021, 1, 1)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isDateTime()
    {
        GigaMap<InstantPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(instantPersonIndex.isDateTime(2021, 1, 1, 12, 0, 0)).count();
            assertEquals(1, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            long count = newMap.query(instantPersonIndex.isDateTime(2021, 1, 1, 12, 0, 0)).count();
            assertEquals(1, count);
        }

    }

    @Test
    void instantPersonTest()
    {
        GigaMap<InstantPerson> map = GigaMap.New();

        BitmapIndices<InstantPerson> bitmap = map.index().bitmap();
        bitmap.add(instantPersonIndex);

        //generate some data
        InstantPerson person1 = new InstantPerson("Alice", toInstant(2000, 1, 1, 0, 0, 0));
        InstantPerson person2 = new InstantPerson("Bob", toInstant(1990, 1, 1, 0, 0, 0));
        InstantPerson person3 = new InstantPerson("Charlie", toInstant(1980, 1, 1, 0, 0, 0));
        map.addAll(person1, person2, person3);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.query(instantPersonIndex.is(toInstant(2000, 1, 1, 0, 0, 0))).forEach(person -> assertEquals(toInstant(2000, 1, 1, 0, 0, 0), person.getTimestamp()));
            map.query(instantPersonIndex.is(toInstant(1990, 1, 1, 0, 0, 0))).forEach(person -> assertEquals(toInstant(1990, 1, 1, 0, 0, 0), person.getTimestamp()));

            map.query(instantPersonIndex.is(toInstant(2000, 1, 1, 0, 0, 0))).and(instantPersonIndex.is(toInstant(1990, 1, 1, 0, 0, 0)))
                    .forEach(person -> fail("Should not be reached"));

            List<InstantPerson> personList = map.query(instantPersonIndex.is(toInstant(2000, 1, 1, 0, 0, 0))).or(instantPersonIndex.is(toInstant(1990, 1, 1, 0, 0, 0)))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            newMap.query(instantPersonIndex.is(toInstant(2000, 1, 1, 0, 0, 0))).forEach(person -> assertEquals(toInstant(2000, 1, 1, 0, 0, 0), person.getTimestamp()));
            newMap.query(instantPersonIndex.is(toInstant(1990, 1, 1, 0, 0, 0))).forEach(person -> assertEquals(toInstant(1990, 1, 1, 0, 0, 0), person.getTimestamp()));

            newMap.query(instantPersonIndex.is(toInstant(2000, 1, 1, 0, 0, 0))).and(instantPersonIndex.is(toInstant(1990, 1, 1, 0, 0, 0)))
                    .forEach(person -> fail("Should not be reached"));

            List<InstantPerson> personList = newMap.query(instantPersonIndex.is(toInstant(2000, 1, 1, 0, 0, 0))).or(instantPersonIndex.is(toInstant(1990, 1, 1, 0, 0, 0)))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }
    }

    @Test
    void beforeEqualTest()
    {
        GigaMap<InstantPerson> map = GigaMap.New();

        BitmapIndices<InstantPerson> bitmap = map.index().bitmap();
        bitmap.add(instantPersonIndex);

        //generate some data
        InstantPerson person1 = new InstantPerson("Alice", toInstant(2000, 1, 1, 0, 0, 0));
        InstantPerson person2 = new InstantPerson("Bob", toInstant(1990, 1, 1, 0, 0, 0));
        InstantPerson person3 = new InstantPerson("Charlie", toInstant(1980, 1, 1, 0, 0, 0));
        map.addAll(person1, person2, person3);

        List<InstantPerson> list = map.query(instantPersonIndex.beforeEqual(toInstant(1990, 1, 1, 0, 0, 0))).toList();
        assertEquals(2, list.size());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            List<InstantPerson> list1 = newMap.query(instantPersonIndex.beforeEqual(toInstant(1990, 1, 1, 0, 0, 0))).toList();
            assertEquals(2, list1.size());
            list1.forEach(person -> assertNotEquals("Alice", person.name));
        }
    }

    @Test
    void afterEqualTest()
    {
        GigaMap<InstantPerson> map = GigaMap.New();

        BitmapIndices<InstantPerson> bitmap = map.index().bitmap();
        bitmap.add(instantPersonIndex);

        //generate some data
        InstantPerson person1 = new InstantPerson("Alice", toInstant(2000, 1, 1, 0, 0, 0));
        InstantPerson person2 = new InstantPerson("Bob", toInstant(1990, 1, 1, 0, 0, 0));
        InstantPerson person3 = new InstantPerson("Charlie", toInstant(1980, 1, 1, 0, 0, 0));
        map.addAll(person1, person2, person3);

        List<InstantPerson> list = map.query(instantPersonIndex.afterEqual(toInstant(1990, 1, 1, 0, 0, 0))).toList();
        assertEquals(2, list.size());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<InstantPerson> newMap = (GigaMap<InstantPerson>) manager.root();
            List<InstantPerson> list1 = newMap.query(instantPersonIndex.afterEqual(toInstant(1990, 1, 1, 0, 0, 0))).toList();
            assertEquals(2, list1.size());
            list1.forEach(person -> assertNotEquals("Charlie", person.name));
        }
    }

    private GigaMap<InstantPerson> prepageGigaMap()
    {
        GigaMap<InstantPerson> map = GigaMap.New();

        BitmapIndices<InstantPerson> bitmap = map.index().bitmap();
        bitmap.add(instantPersonIndex);

        InstantPerson person1 = new InstantPerson("Alice", toInstant(2021, 1, 1, 12, 0, 0));
        InstantPerson person2 = new InstantPerson("Bob", toInstant(2021, 1, 1, 13, 0, 0));
        InstantPerson person3 = new InstantPerson("Charlie", toInstant(2021, 1, 1, 14, 0, 0));
        InstantPerson person4 = new InstantPerson("David", toInstant(2021, 1, 1, 15, 0, 0));
        InstantPerson person5 = new InstantPerson("Eve", toInstant(2021, 1, 1, 16, 0, 0));
        InstantPerson person6 = new InstantPerson("Frank", toInstant(2021, 1, 1, 17, 0, 0));
        InstantPerson person7 = new InstantPerson("Grace", toInstant(2021, 1, 1, 18, 0, 0));
        InstantPerson person8 = new InstantPerson("Hank", toInstant(2021, 1, 1, 19, 0, 0));
        InstantPerson person9 = new InstantPerson("Ivy", toInstant(2021, 1, 1, 20, 0, 0));
        InstantPerson person10 = new InstantPerson("Jack", toInstant(2021, 1, 1, 21, 0, 0));
        InstantPerson person11 = new InstantPerson("Karl", null);

        map.addAll(person1, person2, person3, person4, person5, person6, person7, person8, person9, person10, person11);

        return map;
    }

    private static Instant toInstant(int year, int month, int day, int hour, int minute, int second)
    {
        return LocalDateTime.of(year, month, day, hour, minute, second).toInstant(ZoneOffset.UTC);
    }

    private static class InstantPersonIndex extends IndexerInstant.Abstract<InstantPerson>
    {

        @Override
        protected Instant getInstant(InstantPerson entity)
        {
            return entity.getTimestamp();
        }
    }

    private static class InstantPerson
    {
        private final String name;
        private final Instant timestamp;

        public InstantPerson(String name, Instant timestamp)
        {
            this.name = name;
            this.timestamp = timestamp;
        }

        public String name()
        {
            return this.name;
        }

        public Instant getTimestamp()
        {
            return this.timestamp;
        }
    }
}
