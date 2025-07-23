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
import org.eclipse.store.gigamap.types.IndexerFloat;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class FloatNonPrimitiveIndexTest
{
    @TempDir
    Path tempDir;

    private FloatPersonIndex floatPersonIndex = new FloatPersonIndex();

    @Test
    void nullTests()
    {
        GigaMap<FloatPerson> map = prepageGigaMap();
        assertEquals(0, map.query(floatPersonIndex.lessThan(null)).count());
        assertEquals(0, map.query(floatPersonIndex.lessThanEqual(null)).count());
        assertEquals(0, map.query(floatPersonIndex.greaterThan(null)).count());
        assertEquals(0, map.query(floatPersonIndex.greaterThanEqual(null)).count());

        assertEquals(0, map.query(floatPersonIndex.between(null, null)).count());
        assertEquals(0, map.query(floatPersonIndex.between(30.0f, null)).count());
        assertEquals(0, map.query(floatPersonIndex.between(null, 30.0f)).count());
    }

    @Test
    void between()
    {
        GigaMap<FloatPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(floatPersonIndex.between(20.0f, 30.0f)).count();
            assertEquals(2, count);
            map.query(floatPersonIndex.between(20.0f, 30.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() >= 20.0f && floatPerson.getScore() <= 30.0f));
            map.query(floatPersonIndex.between(30.0f, 40.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() >= 30.0f && floatPerson.getScore() <= 40.0f));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<FloatPerson> newMap = (GigaMap<FloatPerson>) manager.root();
            long count = newMap.query(floatPersonIndex.between(20.0f, 30.0f)).count();
            assertEquals(2, count);
            newMap.query(floatPersonIndex.between(20.0f, 30.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() >= 20.0f && floatPerson.getScore() <= 30.0f));
            newMap.query(floatPersonIndex.between(30.0f, 40.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() >= 30.0f && floatPerson.getScore() <= 40.0f));
        }
    }

    @Test
    void greaterThanEqual()
    {
        GigaMap<FloatPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(floatPersonIndex.greaterThanEqual(20.0f)).count();
            assertEquals(3, count);
            map.query(floatPersonIndex.greaterThanEqual(20.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() >= 20.0f));
            map.query(floatPersonIndex.greaterThanEqual(40.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() >= 40.0f));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<FloatPerson> newMap = (GigaMap<FloatPerson>) manager.root();
            long count = newMap.query(floatPersonIndex.greaterThanEqual(20.0f)).count();
            assertEquals(3, count);
            newMap.query(floatPersonIndex.greaterThanEqual(20.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() >= 20.0f));
            newMap.query(floatPersonIndex.greaterThanEqual(40.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() >= 40.0f));
        }
    }

    @Test
    void greaterThan()
    {
        GigaMap<FloatPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(floatPersonIndex.greaterThan(20.0f)).count();
            assertEquals(2, count);
            map.query(floatPersonIndex.greaterThan(20.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() > 20.0f));
            map.query(floatPersonIndex.greaterThan(30.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() > 30.0f));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<FloatPerson> newMap = (GigaMap<FloatPerson>) manager.root();
            long count = newMap.query(floatPersonIndex.greaterThan(20.0f)).count();
            assertEquals(2, count);
            newMap.query(floatPersonIndex.greaterThan(20.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() > 20.0f));
            newMap.query(floatPersonIndex.greaterThan(30.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() > 30.0f));
        }
    }

    @Test
    void lessThanEqual()
    {
        GigaMap<FloatPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(floatPersonIndex.lessThanEqual(30.0f)).count();
            assertEquals(2, count);
            map.query(floatPersonIndex.lessThanEqual(30.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() <= 30.0f));
            map.query(floatPersonIndex.lessThanEqual(20.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() <= 20.0f));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<FloatPerson> newMap = (GigaMap<FloatPerson>) manager.root();
            long count = newMap.query(floatPersonIndex.lessThanEqual(30.0f)).count();
            assertEquals(2, count);
            newMap.query(floatPersonIndex.lessThanEqual(30.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() <= 30.0f));
            newMap.query(floatPersonIndex.lessThanEqual(20.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() <= 20.0f));
        }
    }

    @Test
    void lessThan()
    {
        GigaMap<FloatPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(floatPersonIndex.lessThan(30.0f)).count();
            assertEquals(1, count);
            map.query(floatPersonIndex.lessThan(30.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() < 30.0f));
            map.query(floatPersonIndex.lessThan(20.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() < 20.0f));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<FloatPerson> newMap = (GigaMap<FloatPerson>) manager.root();
            long count = newMap.query(floatPersonIndex.lessThan(30.0f)).count();
            assertEquals(1, count);
            newMap.query(floatPersonIndex.lessThan(30.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() < 30.0f));
            newMap.query(floatPersonIndex.lessThan(20.0f)).forEach(floatPerson -> assertTrue(floatPerson.getScore() < 20.0f));
        }
    }

    @Test
    void floatPersonTest()
    {
        GigaMap<FloatPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.query(floatPersonIndex.is(20.0f)).forEach(floatPerson -> assertEquals(20.0f, floatPerson.getScore()));
            map.query(floatPersonIndex.is(30.0f)).forEach(floatPerson -> assertEquals(30.0f, floatPerson.getScore()));

            map.query(floatPersonIndex.is(20.0f)).and(floatPersonIndex.is(30.0f))
                    .forEach(floatPerson -> fail("Should not be reached"));

            List<FloatPerson> personList = map.query(floatPersonIndex.is(20.0f)).or(floatPersonIndex.is(30.0f))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<FloatPerson> newMap = (GigaMap<FloatPerson>) manager.root();
            newMap.query(floatPersonIndex.is(20.0f)).forEach(floatPerson -> assertEquals(20.0f, floatPerson.getScore()));
            newMap.query(floatPersonIndex.is(30.0f)).forEach(floatPerson -> assertEquals(30.0f, floatPerson.getScore()));

            newMap.query(floatPersonIndex.is(20.0f)).and(floatPersonIndex.is(30.0f))
                    .forEach(floatPerson -> fail("Should not be reached"));

            List<FloatPerson> personList = newMap.query(floatPersonIndex.is(20.0f)).or(floatPersonIndex.is(30.0f))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }
    }

    private GigaMap<FloatPerson> prepageGigaMap()
    {
        GigaMap<FloatPerson> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(floatPersonIndex);

        FloatPerson person1 = new FloatPerson("Alice", 20.0f);
        FloatPerson person2 = new FloatPerson("Bob", 30.0f);
        FloatPerson person3 = new FloatPerson("Charlie", 40.0f);
        FloatPerson person4 = new FloatPerson("David", null);
        gigaMap.addAll(person1, person2, person3, person4);
        return gigaMap;
    }

    private static class FloatPersonIndex extends IndexerFloat.Abstract<FloatPerson>
    {
		@Override
		protected Float getFloat(FloatPerson entity)
		{
            return entity.getScore();
		}
    }

    private static class FloatPerson
    {
        private final String name;
        private final Float score;

        public FloatPerson(String name, Float score)
        {
            this.name = name;
            this.score = score;
        }

        public String name()
        {
            return this.name;
        }

        public Float getScore()
        {
            return this.score;
        }
    }
}
