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
import org.eclipse.store.gigamap.types.IndexerByte;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ByteIndexTest
{
    @TempDir
    Path tempDir;

    private final BytePersonIndex bytePersonIndex = new BytePersonIndex();

    @Test
    void between()
    {
        GigaMap<BytePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(bytePersonIndex.between((byte) 20, (byte) 30)).count();
            assertEquals(2, count);
            map.query(bytePersonIndex.between((byte) 20, (byte) 30)).forEach(bytePerson -> assertTrue(bytePerson.getAge() >= 20 && bytePerson.getAge() <= 30));
            map.query(bytePersonIndex.between((byte) 30, (byte) 40)).forEach(bytePerson -> assertTrue(bytePerson.getAge() >= 30 && bytePerson.getAge() <= 40));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<BytePerson> newMap = (GigaMap<BytePerson>) manager.root();
            long count = newMap.query(bytePersonIndex.between((byte) 20, (byte) 30)).count();
            assertEquals(2, count);
            newMap.query(bytePersonIndex.between((byte) 20, (byte) 30)).forEach(bytePerson -> assertTrue(bytePerson.getAge() >= 20 && bytePerson.getAge() <= 30));
            newMap.query(bytePersonIndex.between((byte) 30, (byte) 40)).forEach(bytePerson -> assertTrue(bytePerson.getAge() >= 30 && bytePerson.getAge() <= 40));
        }
    }

    @Test
    void greaterThanEqual()
    {
        GigaMap<BytePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(bytePersonIndex.greaterThanEqual((byte) 20)).count();
            assertEquals(3, count);
            map.query(bytePersonIndex.greaterThanEqual((byte) 20)).forEach(bytePerson -> assertTrue(bytePerson.getAge() >= 20));
            map.query(bytePersonIndex.greaterThanEqual((byte) 40)).forEach(bytePerson -> assertTrue(bytePerson.getAge() >= 40));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<BytePerson> newMap = (GigaMap<BytePerson>) manager.root();
            long count = newMap.query(bytePersonIndex.greaterThanEqual((byte) 20)).count();
            assertEquals(3, count);
            newMap.query(bytePersonIndex.greaterThanEqual((byte) 20)).forEach(bytePerson -> assertTrue(bytePerson.getAge() >= 20));
            newMap.query(bytePersonIndex.greaterThanEqual((byte) 40)).forEach(bytePerson -> assertTrue(bytePerson.getAge() >= 40));
        }
    }

    @Test
    void greaterThan()
    {
        GigaMap<BytePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(bytePersonIndex.greaterThan((byte) 20)).count();
            assertEquals(2, count);
            map.query(bytePersonIndex.greaterThan((byte) 20)).forEach(bytePerson -> assertTrue(bytePerson.getAge() > 20));
            map.query(bytePersonIndex.greaterThan((byte) 30)).forEach(bytePerson -> assertTrue(bytePerson.getAge() > 30));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<BytePerson> newMap = (GigaMap<BytePerson>) manager.root();
            long count = newMap.query(bytePersonIndex.greaterThan((byte) 20)).count();
            assertEquals(2, count);
            newMap.query(bytePersonIndex.greaterThan((byte) 20)).forEach(bytePerson -> assertTrue(bytePerson.getAge() > 20));
            newMap.query(bytePersonIndex.greaterThan((byte) 30)).forEach(bytePerson -> assertTrue(bytePerson.getAge() > 30));
        }
    }

    @Test
    void lessThanEqual()
    {
        GigaMap<BytePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(bytePersonIndex.lessThanEqual((byte) 30)).count();
            assertEquals(2, count);
            map.query(bytePersonIndex.lessThanEqual((byte) 30)).forEach(bytePerson -> assertTrue(bytePerson.getAge() <= 30));
            map.query(bytePersonIndex.lessThanEqual((byte) 20)).forEach(bytePerson -> assertTrue(bytePerson.getAge() <= 20));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<BytePerson> newMap = (GigaMap<BytePerson>) manager.root();
            long count = newMap.query(bytePersonIndex.lessThanEqual((byte) 30)).count();
            assertEquals(2, count);
            newMap.query(bytePersonIndex.lessThanEqual((byte) 30)).forEach(bytePerson -> assertTrue(bytePerson.getAge() <= 30));
            newMap.query(bytePersonIndex.lessThanEqual((byte) 20)).forEach(bytePerson -> assertTrue(bytePerson.getAge() <= 20));
        }
    }

    @Test
    void lessThan()
    {
        GigaMap<BytePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(bytePersonIndex.lessThan((byte) 30)).count();
            assertEquals(1, count);
            map.query(bytePersonIndex.lessThan((byte) 30)).forEach(bytePerson -> assertTrue(bytePerson.getAge() < 30));
            map.query(bytePersonIndex.lessThan((byte) 20)).forEach(bytePerson -> fail("Should not be reached"));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<BytePerson> newMap = (GigaMap<BytePerson>) manager.root();
            long count = newMap.query(bytePersonIndex.lessThan((byte) 30)).count();
            assertEquals(1, count);
            newMap.query(bytePersonIndex.lessThan((byte) 30)).forEach(bytePerson -> assertTrue(bytePerson.getAge() < 30));
            newMap.query(bytePersonIndex.lessThan((byte) 20)).forEach(bytePerson -> fail("Should not be reached"));
        }
    }

    @Test
    void bytePersonTest()
    {
        GigaMap<BytePerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.query(bytePersonIndex.is((byte) 20)).forEach(bytePerson -> assertEquals(20, bytePerson.getAge()));
            map.query(bytePersonIndex.is((byte) 30)).forEach(bytePerson -> assertEquals(30, bytePerson.getAge()));

            map.query(bytePersonIndex.is((byte) 20)).and(bytePersonIndex.is((byte) 30))
                    .forEach(bytePerson -> fail("Should not be reached"));

            List<BytePerson> personList = map.query(bytePersonIndex.is((byte) 20)).or(bytePersonIndex.is((byte) 30))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<BytePerson> newMap = (GigaMap<BytePerson>) manager.root();
            newMap.query(bytePersonIndex.is((byte) 20)).forEach(bytePerson -> assertEquals(20, bytePerson.getAge()));
            newMap.query(bytePersonIndex.is((byte) 30)).forEach(bytePerson -> assertEquals(30, bytePerson.getAge()));

            newMap.query(bytePersonIndex.is((byte) 20)).and(bytePersonIndex.is((byte) 30))
                    .forEach(bytePerson -> fail("Should not be reached"));

            List<BytePerson> personList = newMap.query(bytePersonIndex.is((byte) 20)).or(bytePersonIndex.is((byte) 30))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }
    }

    private GigaMap<BytePerson> prepageGigaMap()
    {
        GigaMap<BytePerson> map = GigaMap.New();

        map.index().bitmap().add(bytePersonIndex);
        //generate some data
        BytePerson person1 = new BytePerson("Alice", (byte) 20);
        BytePerson person2 = new BytePerson("Bob", (byte) 30);
        BytePerson person3 = new BytePerson("Charlie", (byte) 40);
        map.addAll(person1, person2, person3);

        return map;
    }

    private static class BytePersonIndex extends IndexerByte.Abstract<BytePerson>
    {
        @Override
        protected Byte getByte(BytePerson entity)
        {
            return entity.getAge();
        }
    }

    private static class BytePerson
    {
        private final String name;
        private final byte age;

        public BytePerson(String name, byte age)
        {
            this.name = name;
            this.age = age;
        }

        public String name()
        {
            return this.name;
        }

        public byte getAge()
        {
            return this.age;
        }
    }
}
