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
import org.eclipse.store.gigamap.types.IndexerZonedDateTime;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ZonedDateTimeIndexTest
{
    @TempDir
    Path tempDir;

    private ZonedDateTimePersonIndex zonedDateTimePersonIndex = new ZonedDateTimePersonIndex();

    @Test
    void nullTests()
    {
        GigaMap<ZonedDateTimePerson> map = prepageGigaMap();

        assertEquals(1, map.query(zonedDateTimePersonIndex.is((ZonedDateTime) null)).count());

        assertThrows(IllegalArgumentException.class, () -> map.query(zonedDateTimePersonIndex.before(null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(zonedDateTimePersonIndex.beforeEqual(null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(zonedDateTimePersonIndex.after(null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(zonedDateTimePersonIndex.afterEqual(null)).count());

        assertThrows(IllegalArgumentException.class, () -> map.query(zonedDateTimePersonIndex.between(null, null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(zonedDateTimePersonIndex.between(toUTC(2021, 1, 1, 12, 0, 0), null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(zonedDateTimePersonIndex.between(null, toUTC(2021, 1, 1, 12, 0, 0))).count());

    }

    @Test
    void between()
    {
        GigaMap<ZonedDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(zonedDateTimePersonIndex.between(toUTC(2021, 1, 1, 12, 0, 0), toUTC(2021, 1, 1, 14, 0, 0))).count();
            assertEquals(3, count);
            long count1 = map.query(zonedDateTimePersonIndex.between(toUTC(2021, 1, 1, 12, 0, 0), toUTC(2021, 1, 1, 14, 0, 0))).count();
            assertEquals(3, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            long count = newMap.query(zonedDateTimePersonIndex.between(toUTC(2021, 1, 1, 12, 0, 0), toUTC(2021, 1, 1, 14, 0, 0))).count();
            assertEquals(3, count);
            long count1 = newMap.query(zonedDateTimePersonIndex.between(toUTC(2021, 1, 1, 12, 0, 0), toUTC(2021, 1, 1, 14, 0, 0))).count();
            assertEquals(3, count1);
            long count2 = newMap.query(zonedDateTimePersonIndex.between(toUTC(2021, 1, 1, 13, 0, 0), toUTC(2021, 1, 1, 14, 0, 0))).count();
            assertEquals(2, count2);
        }
    }

    @Test
    void after()
    {
        GigaMap<ZonedDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(zonedDateTimePersonIndex.after(toUTC(2021, 1, 1, 12, 0, 0))).count();
            assertEquals(9, count);
            long count1 = map.query(zonedDateTimePersonIndex.after(toUTC(2021, 1, 1, 13, 0, 0))).count();
            assertEquals(8, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            long count = newMap.query(zonedDateTimePersonIndex.after(toUTC(2021, 1, 1, 12, 0, 0))).count();
            assertEquals(9, count);
            long count1 = newMap.query(zonedDateTimePersonIndex.after(toUTC(2021, 1, 1, 13, 0, 0))).count();
            assertEquals(8, count1);
        }
    }

    @Test
    void before()
    {
        GigaMap<ZonedDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(zonedDateTimePersonIndex.before(toUTC(2021, 1, 1, 12, 0, 1))).count();
            assertEquals(1, count);
            long count1 = map.query(zonedDateTimePersonIndex.before(toUTC(2021, 1, 1, 13, 0, 0))).count();
            assertEquals(1, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            long count = newMap.query(zonedDateTimePersonIndex.before(toUTC(2021, 1, 1, 12, 0, 1))).count();
            assertEquals(1, count);
            long count1 = newMap.query(zonedDateTimePersonIndex.before(toUTC(2021, 1, 1, 13, 0, 0))).count();
            assertEquals(1, count1);
        }
    }

    @Test
    void isSecond()
    {
        GigaMap<ZonedDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(zonedDateTimePersonIndex.isSecond(0)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            long count = newMap.query(zonedDateTimePersonIndex.isSecond(0)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isMinute()
    {
        GigaMap<ZonedDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(zonedDateTimePersonIndex.isMinute(0)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            long count = newMap.query(zonedDateTimePersonIndex.isMinute(0)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isHour()
    {
        GigaMap<ZonedDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(zonedDateTimePersonIndex.isHour(12)).count();
            assertEquals(1, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            long count = newMap.query(zonedDateTimePersonIndex.isHour(12)).count();
            assertEquals(1, count);
        }
    }

    @Test
    void isTime()
    {
        GigaMap<ZonedDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(zonedDateTimePersonIndex.isTime(12, 0, 0)).count();
            assertEquals(1, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            long count = newMap.query(zonedDateTimePersonIndex.isTime(12, 0, 0)).count();
            assertEquals(1, count);
        }
    }

    @Test
    void isDay()
    {
        GigaMap<ZonedDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(zonedDateTimePersonIndex.isDay(1)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            long count = newMap.query(zonedDateTimePersonIndex.isDay(1)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isMonth()
    {
        GigaMap<ZonedDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(zonedDateTimePersonIndex.isMonth(1)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            long count = newMap.query(zonedDateTimePersonIndex.isMonth(1)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isYear()
    {
        GigaMap<ZonedDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(zonedDateTimePersonIndex.isYear(2021)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            long count = newMap.query(zonedDateTimePersonIndex.isYear(2021)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isDate()
    {
        GigaMap<ZonedDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(zonedDateTimePersonIndex.isDate(2021, 1, 1)).count();
            assertEquals(10, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            long count = newMap.query(zonedDateTimePersonIndex.isDate(2021, 1, 1)).count();
            assertEquals(10, count);
        }
    }

    @Test
    void isDateTime()
    {
        GigaMap<ZonedDateTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(zonedDateTimePersonIndex.isDateTime(2021, 1, 1, 12, 0, 0)).count();
            assertEquals(1, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            long count = newMap.query(zonedDateTimePersonIndex.isDateTime(2021, 1, 1, 12, 0, 0)).count();
            assertEquals(1, count);
        }

    }

    @Test
    void zonedDateTimePersonTest()
    {
        GigaMap<ZonedDateTimePerson> map = GigaMap.New();

        BitmapIndices<ZonedDateTimePerson> bitmap = map.index().bitmap();
        bitmap.add(zonedDateTimePersonIndex);

        //generate some data
        ZonedDateTimePerson person1 = new ZonedDateTimePerson("Alice", toUTC(2000, 1, 1, 0, 0, 0));
        ZonedDateTimePerson person2 = new ZonedDateTimePerson("Bob", toUTC(1990, 1, 1, 0, 0, 0));
        ZonedDateTimePerson person3 = new ZonedDateTimePerson("Charlie", toUTC(1980, 1, 1, 0, 0, 0));
        map.addAll(person1, person2, person3);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.query(zonedDateTimePersonIndex.is(toUTC(2000, 1, 1, 0, 0, 0))).forEach(person -> assertEquals(toUTC(2000, 1, 1, 0, 0, 0), person.getTimestamp()));
            map.query(zonedDateTimePersonIndex.is(toUTC(1990, 1, 1, 0, 0, 0))).forEach(person -> assertEquals(toUTC(1990, 1, 1, 0, 0, 0), person.getTimestamp()));

            map.query(zonedDateTimePersonIndex.is(toUTC(2000, 1, 1, 0, 0, 0))).and(zonedDateTimePersonIndex.is(toUTC(1990, 1, 1, 0, 0, 0)))
                    .forEach(person -> fail("Should not be reached"));

            List<ZonedDateTimePerson> personList = map.query(zonedDateTimePersonIndex.is(toUTC(2000, 1, 1, 0, 0, 0))).or(zonedDateTimePersonIndex.is(toUTC(1990, 1, 1, 0, 0, 0)))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            newMap.query(zonedDateTimePersonIndex.is(toUTC(2000, 1, 1, 0, 0, 0))).forEach(person -> assertEquals(toUTC(2000, 1, 1, 0, 0, 0), person.getTimestamp()));
            newMap.query(zonedDateTimePersonIndex.is(toUTC(1990, 1, 1, 0, 0, 0))).forEach(person -> assertEquals(toUTC(1990, 1, 1, 0, 0, 0), person.getTimestamp()));

            newMap.query(zonedDateTimePersonIndex.is(toUTC(2000, 1, 1, 0, 0, 0))).and(zonedDateTimePersonIndex.is(toUTC(1990, 1, 1, 0, 0, 0)))
                    .forEach(person -> fail("Should not be reached"));

            List<ZonedDateTimePerson> personList = newMap.query(zonedDateTimePersonIndex.is(toUTC(2000, 1, 1, 0, 0, 0))).or(zonedDateTimePersonIndex.is(toUTC(1990, 1, 1, 0, 0, 0)))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }
    }

    @Test
    void beforeEqualTest()
    {
        GigaMap<ZonedDateTimePerson> map = GigaMap.New();

        BitmapIndices<ZonedDateTimePerson> bitmap = map.index().bitmap();
        bitmap.add(zonedDateTimePersonIndex);

        //generate some data
        ZonedDateTimePerson person1 = new ZonedDateTimePerson("Alice", toUTC(2000, 1, 1, 0, 0, 0));
        ZonedDateTimePerson person2 = new ZonedDateTimePerson("Bob", toUTC(1990, 1, 1, 0, 0, 0));
        ZonedDateTimePerson person3 = new ZonedDateTimePerson("Charlie", toUTC(1980, 1, 1, 0, 0, 0));
        map.addAll(person1, person2, person3);

        List<ZonedDateTimePerson> list = map.query(zonedDateTimePersonIndex.beforeEqual(toUTC(1990, 1, 1, 0, 0, 0))).toList();
        assertEquals(2, list.size());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            List<ZonedDateTimePerson> list1 = newMap.query(zonedDateTimePersonIndex.beforeEqual(toUTC(1990, 1, 1, 0, 0, 0))).toList();
            assertEquals(2, list1.size());
            list1.forEach(person -> assertNotEquals("Alice", person.name));
        }
    }

    @Test
    void afterEqualTest()
    {
        GigaMap<ZonedDateTimePerson> map = GigaMap.New();

        BitmapIndices<ZonedDateTimePerson> bitmap = map.index().bitmap();
        bitmap.add(zonedDateTimePersonIndex);

        //generate some data
        ZonedDateTimePerson person1 = new ZonedDateTimePerson("Alice", toUTC(2000, 1, 1, 0, 0, 0));
        ZonedDateTimePerson person2 = new ZonedDateTimePerson("Bob", toUTC(1990, 1, 1, 0, 0, 0));
        ZonedDateTimePerson person3 = new ZonedDateTimePerson("Charlie", toUTC(1980, 1, 1, 0, 0, 0));
        map.addAll(person1, person2, person3);

        List<ZonedDateTimePerson> list = map.query(zonedDateTimePersonIndex.afterEqual(toUTC(1990, 1, 1, 0, 0, 0))).toList();
        assertEquals(2, list.size());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<ZonedDateTimePerson> newMap = (GigaMap<ZonedDateTimePerson>) manager.root();
            List<ZonedDateTimePerson> list1 = newMap.query(zonedDateTimePersonIndex.afterEqual(toUTC(1990, 1, 1, 0, 0, 0))).toList();
            assertEquals(2, list1.size());
            list1.forEach(person -> assertNotEquals("Charlie", person.name));
        }
    }

    @Test
    void differentTimeZonesTest()
    {
        GigaMap<ZonedDateTimePerson> map = GigaMap.New();

        BitmapIndices<ZonedDateTimePerson> bitmap = map.index().bitmap();
        bitmap.add(zonedDateTimePersonIndex);

        // Same absolute point in time, different zones: 2021-01-01T12:00 UTC == 2021-01-01T13:00 +01:00
        ZonedDateTimePerson person1 = new ZonedDateTimePerson("Alice", ZonedDateTime.of(2021, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC));
        ZonedDateTimePerson person2 = new ZonedDateTimePerson("Bob", ZonedDateTime.of(2021, 1, 1, 13, 0, 0, 0, ZoneId.of("+01:00")));
        ZonedDateTimePerson person3 = new ZonedDateTimePerson("Charlie", ZonedDateTime.of(2021, 1, 1, 14, 0, 0, 0, ZoneOffset.UTC));
        map.addAll(person1, person2, person3);

        // Alice and Bob represent the same instant in UTC, so isHour(12) should match both
        long count = map.query(zonedDateTimePersonIndex.isHour(12)).count();
        assertEquals(2, count);

        // Only Charlie is at 14:00 UTC
        long count2 = map.query(zonedDateTimePersonIndex.isHour(14)).count();
        assertEquals(1, count2);
    }

    private GigaMap<ZonedDateTimePerson> prepageGigaMap()
    {
        GigaMap<ZonedDateTimePerson> map = GigaMap.New();

        BitmapIndices<ZonedDateTimePerson> bitmap = map.index().bitmap();
        bitmap.add(zonedDateTimePersonIndex);

        ZonedDateTimePerson person1 = new ZonedDateTimePerson("Alice", toUTC(2021, 1, 1, 12, 0, 0));
        ZonedDateTimePerson person2 = new ZonedDateTimePerson("Bob", toUTC(2021, 1, 1, 13, 0, 0));
        ZonedDateTimePerson person3 = new ZonedDateTimePerson("Charlie", toUTC(2021, 1, 1, 14, 0, 0));
        ZonedDateTimePerson person4 = new ZonedDateTimePerson("David", toUTC(2021, 1, 1, 15, 0, 0));
        ZonedDateTimePerson person5 = new ZonedDateTimePerson("Eve", toUTC(2021, 1, 1, 16, 0, 0));
        ZonedDateTimePerson person6 = new ZonedDateTimePerson("Frank", toUTC(2021, 1, 1, 17, 0, 0));
        ZonedDateTimePerson person7 = new ZonedDateTimePerson("Grace", toUTC(2021, 1, 1, 18, 0, 0));
        ZonedDateTimePerson person8 = new ZonedDateTimePerson("Hank", toUTC(2021, 1, 1, 19, 0, 0));
        ZonedDateTimePerson person9 = new ZonedDateTimePerson("Ivy", toUTC(2021, 1, 1, 20, 0, 0));
        ZonedDateTimePerson person10 = new ZonedDateTimePerson("Jack", toUTC(2021, 1, 1, 21, 0, 0));
        ZonedDateTimePerson person11 = new ZonedDateTimePerson("Karl", null);

        map.addAll(person1, person2, person3, person4, person5, person6, person7, person8, person9, person10, person11);

        return map;
    }

    private static ZonedDateTime toUTC(int year, int month, int day, int hour, int minute, int second)
    {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.UTC);
    }

    private static class ZonedDateTimePersonIndex extends IndexerZonedDateTime.Abstract<ZonedDateTimePerson>
    {

        @Override
        protected ZonedDateTime getZonedDateTime(ZonedDateTimePerson entity)
        {
            return entity.getTimestamp();
        }
    }

    private static class ZonedDateTimePerson
    {
        private final String name;
        private final ZonedDateTime timestamp;

        public ZonedDateTimePerson(String name, ZonedDateTime timestamp)
        {
            this.name = name;
            this.timestamp = timestamp;
        }

        public String name()
        {
            return this.name;
        }

        public ZonedDateTime getTimestamp()
        {
            return this.timestamp;
        }
    }
}
