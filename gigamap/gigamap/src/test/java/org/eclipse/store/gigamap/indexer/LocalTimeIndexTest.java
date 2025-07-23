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
import org.eclipse.store.gigamap.types.IndexerLocalTime;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class LocalTimeIndexTest
{
    @TempDir
    Path tempDir;

    private LocalTimePersonIndex localTimePersonIndex = new LocalTimePersonIndex();


    @Test
    void nullTest()
    {
        GigaMap<LocalTimePerson> map = GigaMap.New();
        BitmapIndices<LocalTimePerson> bitmap = map.index().bitmap();
        bitmap.add(localTimePersonIndex);

        LocalTimePerson person = new LocalTimePerson("Alice", null);
        map.add(person);

        assertAll(
                () -> assertEquals(1, map.query(localTimePersonIndex.is((LocalTime) null)).count()),
                () -> assertEquals(0, map.query(localTimePersonIndex.isTime(0, 0, 0)).count()),

                () -> assertThrows(IllegalArgumentException.class, () -> map.query(localTimePersonIndex.before(null)).count()),
                () -> assertThrows(IllegalArgumentException.class, () -> map.query(localTimePersonIndex.beforeEqual(null)).count()),

                () -> assertThrows(IllegalArgumentException.class, () -> map.query(localTimePersonIndex.after(null)).count()),
                () -> assertThrows(IllegalArgumentException.class, () -> map.query(localTimePersonIndex.afterEqual(null)).count()),

                () -> assertThrows(IllegalArgumentException.class, () -> map.query(localTimePersonIndex.between(null, null)).count()),
                () -> assertThrows(IllegalArgumentException.class, () -> map.query(localTimePersonIndex.between(LocalTime.of(0, 0), null)).count()),
                () -> assertThrows(IllegalArgumentException.class, () -> map.query(localTimePersonIndex.between(null, LocalTime.of(0, 0))).count())

        );


    }

    @Test
    void between()
    {
        GigaMap<LocalTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localTimePersonIndex.between(LocalTime.of(12, 0), LocalTime.of(14, 0))).count();
            assertEquals(3, count);
            long count1 = map.query(localTimePersonIndex.between(LocalTime.of(12, 0), LocalTime.of(14, 0))).count();
            assertEquals(3, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalTimePerson> newMap = (GigaMap<LocalTimePerson>) manager.root();
            long count = newMap.query(localTimePersonIndex.between(LocalTime.of(12, 0), LocalTime.of(14, 0))).count();
            assertEquals(3, count);
            long count1 = newMap.query(localTimePersonIndex.between(LocalTime.of(12, 0), LocalTime.of(14, 0))).count();
            assertEquals(3, count1);
            long count2 = newMap.query(localTimePersonIndex.between(LocalTime.of(13, 0), LocalTime.of(14, 0))).count();
            assertEquals(2, count2);
        }
    }

    @Test
    void after()
    {
        GigaMap<LocalTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localTimePersonIndex.after(LocalTime.of(12, 0))).count();
            assertEquals(2, count);
            long count1 = map.query(localTimePersonIndex.after(LocalTime.of(13, 0))).count();
            assertEquals(1, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalTimePerson> newMap = (GigaMap<LocalTimePerson>) manager.root();
            long count = newMap.query(localTimePersonIndex.after(LocalTime.of(12, 0))).count();
            assertEquals(2, count);
            long count1 = newMap.query(localTimePersonIndex.after(LocalTime.of(13, 0))).count();
            assertEquals(1, count1);
        }
    }

    @Test
    void afterEqual()
    {
        GigaMap<LocalTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localTimePersonIndex.afterEqual(LocalTime.of(12, 0))).count();
            assertEquals(3, count);
            long count1 = map.query(localTimePersonIndex.afterEqual(LocalTime.of(13, 0))).count();
            assertEquals(2, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalTimePerson> newMap = (GigaMap<LocalTimePerson>) manager.root();
            long count = newMap.query(localTimePersonIndex.afterEqual(LocalTime.of(12, 0))).count();
            assertEquals(3, count);
            long count1 = newMap.query(localTimePersonIndex.afterEqual(LocalTime.of(13, 0))).count();
            assertEquals(2, count1);
        }
    }

    @Test
    void before()
    {
        GigaMap<LocalTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localTimePersonIndex.before(LocalTime.of(13, 0))).count();
            assertEquals(1, count);
            long count1 = map.query(localTimePersonIndex.before(LocalTime.of(12, 0))).count();
            assertEquals(0, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalTimePerson> newMap = (GigaMap<LocalTimePerson>) manager.root();
            long count = newMap.query(localTimePersonIndex.before(LocalTime.of(13, 0))).count();
            assertEquals(1, count);
            long count1 = newMap.query(localTimePersonIndex.before(LocalTime.of(12, 0))).count();
            assertEquals(0, count1);
        }
    }

    @Test
    void beforeEqual()
    {
        GigaMap<LocalTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localTimePersonIndex.beforeEqual(LocalTime.of(13, 0))).count();
            assertEquals(2, count);
            long count1 = map.query(localTimePersonIndex.beforeEqual(LocalTime.of(12, 0))).count();
            assertEquals(1, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalTimePerson> newMap = (GigaMap<LocalTimePerson>) manager.root();
            long count = newMap.query(localTimePersonIndex.beforeEqual(LocalTime.of(13, 0))).count();
            assertEquals(2, count);
            long count1 = newMap.query(localTimePersonIndex.beforeEqual(LocalTime.of(12, 0))).count();
            assertEquals(1, count1);
        }
    }

    @Test
    void isSecond()
    {
        GigaMap<LocalTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localTimePersonIndex.isSecond(0)).count();
            assertEquals(3, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalTimePerson> newMap = (GigaMap<LocalTimePerson>) manager.root();
            long count = newMap.query(localTimePersonIndex.isSecond(0)).count();
            assertEquals(3, count);
        }
    }

    @Test
    void isMinute()
    {
        GigaMap<LocalTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localTimePersonIndex.isMinute(0)).count();
            assertEquals(3, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalTimePerson> newMap = (GigaMap<LocalTimePerson>) manager.root();
            long count = newMap.query(localTimePersonIndex.isMinute(0)).count();
            assertEquals(3, count);
        }
    }

    @Test
    void isHour()
    {
        GigaMap<LocalTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localTimePersonIndex.isHour(12)).count();
            assertEquals(1, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalTimePerson> newMap = (GigaMap<LocalTimePerson>) manager.root();
            long count = newMap.query(localTimePersonIndex.isHour(12)).count();
            assertEquals(1, count);
        }
    }

    @Test
    void isTime()
    {
        GigaMap<LocalTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(localTimePersonIndex.isTime(12, 0, 0)).count();
            assertEquals(1, count);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalTimePerson> newMap = (GigaMap<LocalTimePerson>) manager.root();
            long count = newMap.query(localTimePersonIndex.isTime(12, 0, 0)).count();
            assertEquals(1, count);
        }
    }

    @Test
    void localTimePersonTest()
    {
        GigaMap<LocalTimePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.query(localTimePersonIndex.is(LocalTime.of(12, 0))).forEach(localTimePerson -> assertEquals(LocalTime.of(12, 0), localTimePerson.getLunchTime()));
            map.query(localTimePersonIndex.is(LocalTime.of(13, 0))).forEach(localTimePerson -> assertEquals(LocalTime.of(13, 0), localTimePerson.getLunchTime()));

            map.query(localTimePersonIndex.is(LocalTime.of(12, 0))).and(localTimePersonIndex.is(LocalTime.of(13, 0)))
                    .forEach(localTimePerson -> fail("Should not be reached"));

            List<LocalTimePerson> personList = map.query(localTimePersonIndex.is(LocalTime.of(12, 0))).or(localTimePersonIndex.is(LocalTime.of(13, 0)))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<LocalTimePerson> newMap = (GigaMap<LocalTimePerson>) manager.root();
            newMap.query(localTimePersonIndex.is(LocalTime.of(12, 0))).forEach(localTimePerson -> assertEquals(LocalTime.of(12, 0), localTimePerson.getLunchTime()));
            newMap.query(localTimePersonIndex.is(LocalTime.of(13, 0))).forEach(localTimePerson -> assertEquals(LocalTime.of(13, 0), localTimePerson.getLunchTime()));

            newMap.query(localTimePersonIndex.is(LocalTime.of(12, 0))).and(localTimePersonIndex.is(LocalTime.of(13, 0)))
                    .forEach(localTimePerson -> fail("Should not be reached"));

            List<LocalTimePerson> personList = newMap.query(localTimePersonIndex.is(LocalTime.of(12, 0))).or(localTimePersonIndex.is(LocalTime.of(13, 0)))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }
    }

    private GigaMap<LocalTimePerson> prepageGigaMap()
    {
        GigaMap<LocalTimePerson> map = GigaMap.New();

        BitmapIndices<LocalTimePerson> bitmap = map.index().bitmap();
        bitmap.add(localTimePersonIndex);

        //generate some data
        LocalTimePerson person1 = new LocalTimePerson("Alice", LocalTime.of(12, 0));
        LocalTimePerson person2 = new LocalTimePerson("Bob", LocalTime.of(13, 0));
        LocalTimePerson person3 = new LocalTimePerson("Charlie", LocalTime.of(14, 0));
        LocalTimePerson person4 = new LocalTimePerson("Alice", null);
        map.addAll(person1, person2, person3, person4);
        return map;
    }

    private static class LocalTimePersonIndex extends IndexerLocalTime.Abstract<LocalTimePerson>
    {

        @Override
        protected LocalTime getLocalTime(LocalTimePerson entity)
        {
            return entity.getLunchTime();
        }
    }

    private static class LocalTimePerson
    {
        private final String name;
        private final LocalTime lunchTime;

        public LocalTimePerson(String name, LocalTime lunchTime)
        {
            this.name = name;
            this.lunchTime = lunchTime;
        }

        public String name()
        {
            return this.name;
        }

        public LocalTime getLunchTime()
        {
            return this.lunchTime;
        }
    }
}
