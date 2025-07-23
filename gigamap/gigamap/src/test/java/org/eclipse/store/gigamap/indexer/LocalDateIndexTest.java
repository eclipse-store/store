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
import org.eclipse.store.gigamap.types.IndexerLocalDate;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class LocalDateIndexTest
{
    @TempDir
    Path tempDir;

    private final LocalDatePersonIndex localDatePersonIndex = new LocalDatePersonIndex();

    @Test
    void nullTests()
    {
        GigaMap<LocalDatePerson> map = prepageGigaMap();
        assertEquals(1, map.query(localDatePersonIndex.is((LocalDate) null)).count());

        assertThrows(IllegalArgumentException.class, () -> map.query(localDatePersonIndex.before(null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(localDatePersonIndex.beforeEqual(null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(localDatePersonIndex.after(null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(localDatePersonIndex.afterEqual(null)).count());

        assertThrows(IllegalArgumentException.class, () -> map.query(localDatePersonIndex.between(null, null)).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(localDatePersonIndex.between(null, LocalDate.of(2000, 1, 1))).count());
        assertThrows(IllegalArgumentException.class, () -> map.query(localDatePersonIndex.between(LocalDate.of(2000, 1, 1), null)).count());
    }

    @Test
    void between()
    {
        GigaMap<LocalDatePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDatePersonIndex.between(LocalDate.of(1990, 1, 1), LocalDate.of(2000, 1, 1))).count();
            assertEquals(2, count);
            long count1 = map.query(localDatePersonIndex.between(LocalDate.of(1990, 1, 1), LocalDate.of(1990, 1, 1))).count();
            assertEquals(1, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDatePerson> newMap = (GigaMap<LocalDatePerson>) manager.root();
            long count = newMap.query(localDatePersonIndex.between(LocalDate.of(1990, 1, 1), LocalDate.of(2000, 1, 1))).count();
            assertEquals(2, count);
            long count1 = newMap.query(localDatePersonIndex.between(LocalDate.of(1990, 1, 1), LocalDate.of(1990, 1, 1))).count();
            assertEquals(1, count1);
        }
    }

    @Test
    void after()
    {
        GigaMap<LocalDatePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDatePersonIndex.after(LocalDate.of(1990, 1, 1))).count();
            assertEquals(1, count);
//            long count1 = map.query(localDatePersonIndex.after(LocalDate.of(2000, 1, 1))).count();
//            assertEquals(0, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDatePerson> newMap = (GigaMap<LocalDatePerson>) manager.root();
            long count = newMap.query(localDatePersonIndex.after(LocalDate.of(1990, 1, 1))).count();
            assertEquals(1, count);
//            long count1 = newMap.query(localDatePersonIndex.after(LocalDate.of(2000, 1, 1))).count();
//            assertEquals(0, count1);
        }
    }

    @Test
    void before()
    {
        GigaMap<LocalDatePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDatePersonIndex.before(LocalDate.of(2000, 1, 1))).count();
            assertEquals(2, count);
            long count1 = map.query(localDatePersonIndex.before(LocalDate.of(1990, 1, 1))).count();
            assertEquals(1, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDatePerson> newMap = (GigaMap<LocalDatePerson>) manager.root();
            long count = newMap.query(localDatePersonIndex.before(LocalDate.of(2000, 1, 1))).count();
            assertEquals(2, count);
            long count1 = newMap.query(localDatePersonIndex.before(LocalDate.of(1990, 1, 1))).count();
            assertEquals(1, count1);
        }
    }

    @Test
    void isDay()
    {
        GigaMap<LocalDatePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count1 = map.query(localDatePersonIndex.isDay(1)).count();
            assertEquals(3, count1);
//            long count2 = map.query(localDatePersonIndex.isDay(2)).count();
//            assertEquals(0, count2);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDatePerson> newMap = (GigaMap<LocalDatePerson>) manager.root();
            long count1 = newMap.query(localDatePersonIndex.isDay(1)).count();
            assertEquals(3, count1);
//            long count2 = newMap.query(localDatePersonIndex.isDay(2)).count();
//            assertEquals(0, count2);
        }
    }

    @Test
    void isMonth()
    {
        GigaMap<LocalDatePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count1 = map.query(localDatePersonIndex.isMonth(1)).count();
            assertEquals(3, count1);
//            long count2 = map.query(localDatePersonIndex.isMonth(2)).count();
//            assertEquals(0, count2);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDatePerson> newMap = (GigaMap<LocalDatePerson>) manager.root();
            long count1 = newMap.query(localDatePersonIndex.isMonth(1)).count();
            assertEquals(3, count1);
//            long count2 = newMap.query(localDatePersonIndex.isMonth(2)).count();
//            assertEquals(0, count2);
        }
    }

    @Test
    void isYear()
    {
        GigaMap<LocalDatePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count1 = map.query(localDatePersonIndex.isYear(2000)).count();
            assertEquals(1, count1);
            long count2 = map.query(localDatePersonIndex.isYear(1990)).count();
            assertEquals(1, count2);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDatePerson> newMap = (GigaMap<LocalDatePerson>) manager.root();
            long count1 = newMap.query(localDatePersonIndex.isYear(2000)).count();
            assertEquals(1, count1);
            long count2 = newMap.query(localDatePersonIndex.isYear(1990)).count();
            assertEquals(1, count2);
        }
    }

    @Test
    void isDate()
    {
        GigaMap<LocalDatePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count1 = map.query(localDatePersonIndex.isDate(2000, 1, 1)).count();
            assertEquals(1, count1);
            long count2 = map.query(localDatePersonIndex.isDate(1990, 1, 1)).count();
            assertEquals(1, count2);

        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDatePerson> newMap = (GigaMap<LocalDatePerson>) manager.root();
            long count1 = newMap.query(localDatePersonIndex.isDate(2000, 1, 1)).count();
            assertEquals(1, count1);
            long count2 = newMap.query(localDatePersonIndex.isDate(1990, 1, 1)).count();
            assertEquals(1, count2);
        }
    }

    @Test
    void localDatePersonTest()
    {
        GigaMap<LocalDatePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.query(localDatePersonIndex.is(LocalDate.of(2000, 1, 1))).forEach(localDatePerson -> assertEquals(LocalDate.of(2000, 1, 1), localDatePerson.getBirthday()));
            map.query(localDatePersonIndex.is(LocalDate.of(1990, 1, 1))).forEach(localDatePerson -> assertEquals(LocalDate.of(1990, 1, 1), localDatePerson.getBirthday()));

            map.query(localDatePersonIndex.is(LocalDate.of(2000, 1, 1))).and(localDatePersonIndex.is(LocalDate.of(1990, 1, 1)))
                    .forEach(localDatePerson -> fail("Should not be reached"));

            List<LocalDatePerson> personList = map.query(localDatePersonIndex.is(LocalDate.of(2000, 1, 1))).or(localDatePersonIndex.is(LocalDate.of(1990, 1, 1)))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDatePerson> newMap = (GigaMap<LocalDatePerson>) manager.root();
            newMap.query(localDatePersonIndex.is(LocalDate.of(2000, 1, 1))).forEach(localDatePerson -> assertEquals(LocalDate.of(2000, 1, 1), localDatePerson.getBirthday()));
            newMap.query(localDatePersonIndex.is(LocalDate.of(1990, 1, 1))).forEach(localDatePerson -> assertEquals(LocalDate.of(1990, 1, 1), localDatePerson.getBirthday()));

            newMap.query(localDatePersonIndex.is(LocalDate.of(2000, 1, 1))).and(localDatePersonIndex.is(LocalDate.of(1990, 1, 1)))
                    .forEach(localDatePerson -> fail("Should not be reached"));

            List<LocalDatePerson> personList = newMap.query(localDatePersonIndex.is(LocalDate.of(2000, 1, 1))).or(localDatePersonIndex.is(LocalDate.of(1990, 1, 1)))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }
    }

    @Test
    void isDate_invalidDate_IllegalArgumentException()
    {
        GigaMap<LocalDatePerson> map = prepageGigaMap();

        assertThrows(IllegalArgumentException.class, () -> map.query(localDatePersonIndex.isDay(50)).findFirst());
        assertThrows(IllegalArgumentException.class, () -> map.query(localDatePersonIndex.isMonth(-5)).findFirst());

    }

    @Test
    void beforeEqual()
    {
        GigaMap<LocalDatePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDatePersonIndex.beforeEqual(LocalDate.of(1990, 1, 1))).count();
            assertEquals(2, count);
            long count1 = map.query(localDatePersonIndex.beforeEqual(LocalDate.of(1980, 1, 1))).count();
            assertEquals(1, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDatePerson> newMap = (GigaMap<LocalDatePerson>) manager.root();
            long count = newMap.query(localDatePersonIndex.beforeEqual(LocalDate.of(1990, 1, 1))).count();
            assertEquals(2, count);
            long count1 = newMap.query(localDatePersonIndex.beforeEqual(LocalDate.of(1980, 1, 1))).count();
            assertEquals(1, count1);
        }
    }

    @Test
    void afterEqual()
    {
        GigaMap<LocalDatePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localDatePersonIndex.afterEqual(LocalDate.of(1990, 1, 1))).count();
            assertEquals(2, count);
            long count1 = map.query(localDatePersonIndex.afterEqual(LocalDate.of(2000, 1, 1))).count();
            assertEquals(1, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalDatePerson> newMap = (GigaMap<LocalDatePerson>) manager.root();
            long count = newMap.query(localDatePersonIndex.afterEqual(LocalDate.of(1990, 1, 1))).count();
            assertEquals(2, count);
            long count1 = newMap.query(localDatePersonIndex.afterEqual(LocalDate.of(2000, 1, 1))).count();
            assertEquals(1, count1);
        }
    }

    private GigaMap<LocalDatePerson> prepageGigaMap()
    {
        GigaMap<LocalDatePerson> map = GigaMap.New();

        BitmapIndices<LocalDatePerson> bitmap = map.index().bitmap();
        bitmap.add(localDatePersonIndex);

        //generate some data
        LocalDatePerson person1 = new LocalDatePerson("Alice", LocalDate.of(2000, 1, 1));
        LocalDatePerson person2 = new LocalDatePerson("Bob", LocalDate.of(1990, 1, 1));
        LocalDatePerson person3 = new LocalDatePerson("Charlie", LocalDate.of(1980, 1, 1));
        LocalDatePerson person4 = new LocalDatePerson("David", null);
        map.addAll(person1, person2, person3, person4);

        return map;
    }

    private static class LocalDatePersonIndex extends IndexerLocalDate.Abstract<LocalDatePerson>
    {

        @Override
        protected LocalDate getLocalDate(LocalDatePerson entity)
        {
            return entity.getBirthday();
        }
    }

    private static class LocalDatePerson
    {
        private final String name;
        private final LocalDate birthday;

        public LocalDatePerson(String name, LocalDate birthday)
        {
            this.name = name;
            this.birthday = birthday;
        }

        public String name()
        {
            return this.name;
        }

        public LocalDate getBirthday()
        {
            return this.birthday;
        }
    }
}
