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
import org.eclipse.store.gigamap.types.IndexerDouble;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class DoubleNonPrimitiveIndexTest
{
    @TempDir
    Path tempDir;

    @Test
    void nullTests()
    {
        GigaMap<DoublePerson> map = prepageGigaMap();
        DoublePersonIndex doublePersonIndex = new DoublePersonIndex();

        assertEquals(0, map.query(doublePersonIndex.lessThan(null)).count());
        assertEquals(0, map.query(doublePersonIndex.lessThanEqual(null)).count());
        assertEquals(0, map.query(doublePersonIndex.greaterThan(null)).count());
        assertEquals(0, map.query(doublePersonIndex.greaterThanEqual(null)).count());

        assertEquals(0, map.query(doublePersonIndex.between(null, null)).count());
        assertEquals(0, map.query(doublePersonIndex.between(30.0, null)).count());
        assertEquals(0, map.query(doublePersonIndex.between(null, 30.0)).count());
    }

    @Test
    void between()
    {
        GigaMap<DoublePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(new DoublePersonIndex().between(20.0, 30.0)).count();
            assertEquals(2, count);
            map.query(new DoublePersonIndex().between(20.0, 30.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() >= 20.0 && doublePerson.getScore() <= 30.0));
            map.query(new DoublePersonIndex().between(30.0, 40.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() >= 30.0 && doublePerson.getScore() <= 40.0));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<DoublePerson> newMap = (GigaMap<DoublePerson>) manager.root();
            long count = newMap.query(new DoublePersonIndex().between(20.0, 30.0)).count();
            assertEquals(2, count);
            newMap.query(new DoublePersonIndex().between(20.0, 30.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() >= 20.0 && doublePerson.getScore() <= 30.0));
            newMap.query(new DoublePersonIndex().between(30.0, 40.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() >= 30.0 && doublePerson.getScore() <= 40.0));
        }
    }

    @Test
    void greaterThanEqual()
    {
        GigaMap<DoublePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(new DoublePersonIndex().greaterThanEqual(20.0)).count();
            assertEquals(3, count);
            map.query(new DoublePersonIndex().greaterThanEqual(20.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() >= 20.0));
            map.query(new DoublePersonIndex().greaterThanEqual(40.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() >= 40.0));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<DoublePerson> newMap = (GigaMap<DoublePerson>) manager.root();
            long count = newMap.query(new DoublePersonIndex().greaterThanEqual(20.0)).count();
            assertEquals(3, count);
            newMap.query(new DoublePersonIndex().greaterThanEqual(20.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() >= 20.0));
            newMap.query(new DoublePersonIndex().greaterThanEqual(40.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() >= 40.0));
        }
    }

    @Test
    void greaterThan()
    {
        GigaMap<DoublePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(new DoublePersonIndex().greaterThan(20.0)).count();
            assertEquals(2, count);
            map.query(new DoublePersonIndex().greaterThan(20.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() > 20.0));
            map.query(new DoublePersonIndex().greaterThan(30.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() > 30.0));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<DoublePerson> newMap = (GigaMap<DoublePerson>) manager.root();
            long count = newMap.query(new DoublePersonIndex().greaterThan(20.0)).count();
            assertEquals(2, count);
            newMap.query(new DoublePersonIndex().greaterThan(20.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() > 20.0));
            newMap.query(new DoublePersonIndex().greaterThan(30.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() > 30.0));
        }
    }

    @Test
    void lessThanEqual()
    {
        GigaMap<DoublePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(new DoublePersonIndex().lessThanEqual(20.0)).count();
            assertEquals(3, count);
            map.query(new DoublePersonIndex().lessThanEqual(20.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() <= 20.0));
            map.query(new DoublePersonIndex().lessThanEqual(10.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() <= 10.0));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<DoublePerson> newMap = (GigaMap<DoublePerson>) manager.root();
            long count = newMap.query(new DoublePersonIndex().lessThanEqual(20.0)).count();
            assertEquals(3, count);
            newMap.query(new DoublePersonIndex().lessThanEqual(20.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() <= 20.0));
            newMap.query(new DoublePersonIndex().lessThanEqual(10.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() <= 10.0));
        }
    }

    @Test
    void lessThan()
    {
        GigaMap<DoublePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(new DoublePersonIndex().lessThan(20.0)).count();
            assertEquals(2, count);
            map.query(new DoublePersonIndex().lessThan(20.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() < 20.0));
            map.query(new DoublePersonIndex().lessThan(10.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() < 10.0));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<DoublePerson> newMap = (GigaMap<DoublePerson>) manager.root();
            long count = newMap.query(new DoublePersonIndex().lessThan(20.0)).count();
            assertEquals(2, count);
            newMap.query(new DoublePersonIndex().lessThan(20.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() < 20.0));
            newMap.query(new DoublePersonIndex().lessThan(10.0)).forEach(doublePerson -> assertTrue(doublePerson.getScore() < 10.0));
        }
    }

    @Test
    void doublePersonTest()
    {
        GigaMap<DoublePerson> map = GigaMap.New();
        DoublePersonIndex doublePersonIndex = new DoublePersonIndex();

        BitmapIndices<DoublePerson> bitmap = map.index().bitmap();
        bitmap.add(doublePersonIndex);

        //generate some data
        DoublePerson person1 = new DoublePerson("Alice", 20.0);
        DoublePerson person2 = new DoublePerson("Bob", 30.0);
        DoublePerson person3 = new DoublePerson("Charlie", 40.0);
        map.addAll(person1, person2, person3);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.query(doublePersonIndex.is(20.0)).forEach(doublePerson -> assertEquals(20.0, doublePerson.getScore()));
            map.query(doublePersonIndex.is(30.0)).forEach(doublePerson -> assertEquals(30.0, doublePerson.getScore()));

            map.query(doublePersonIndex.is(20.0)).and(doublePersonIndex.is(30.0))
                    .forEach(doublePerson -> fail("Should not be reached"));

            List<DoublePerson> personList = map.query(doublePersonIndex.is(20.0)).or(doublePersonIndex.is(30.0))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<DoublePerson> newMap = (GigaMap<DoublePerson>) manager.root();
            newMap.query(doublePersonIndex.is(20.0)).forEach(doublePerson -> assertEquals(20.0, doublePerson.getScore()));
            newMap.query(doublePersonIndex.is(30.0)).forEach(doublePerson -> assertEquals(30.0, doublePerson.getScore()));

            newMap.query(doublePersonIndex.is(20.0)).and(doublePersonIndex.is(30.0))
                    .forEach(doublePerson -> fail("Should not be reached"));

            List<DoublePerson> personList = newMap.query(doublePersonIndex.is(20.0)).or(doublePersonIndex.is(30.0))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }
    }

    private GigaMap<DoublePerson> prepageGigaMap()
    {
        GigaMap<DoublePerson> map = GigaMap.New();

        BitmapIndices<DoublePerson> bitmap = map.index().bitmap();
        bitmap.add(new DoublePersonIndex());

        map.addAll(
                new DoublePerson("Eliza", 0.0),
                new DoublePerson("Fiona", 10.0),
                new DoublePerson("George", 20.0),
                new DoublePerson("Hannah", 30.0),
                new DoublePerson("Ivan", 40.0),
                new DoublePerson("Jack", null)
        );

        return map;
    }

    private static class DoublePersonIndex extends IndexerDouble.Abstract<DoublePerson>
    {
		@Override
		protected Double getDouble(DoublePerson entity)
		{
            return entity.getScore();
		}
    }

    private static class DoublePerson
    {
        private final String name;
        private final Double score;

        public DoublePerson(String name, Double score)
        {
            this.name = name;
            this.score = score;
        }

        public String name()
        {
            return this.name;
        }

        public Double getScore()
        {
            return this.score;
        }
    }
}
