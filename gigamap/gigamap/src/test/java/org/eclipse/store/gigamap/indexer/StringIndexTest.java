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
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class StringIndexTest
{
    @TempDir
    Path tempDir;

    private final StringPersonProfessionIndex profIndex = new StringPersonProfessionIndex();

    @Test
    void nullValuesTest()
    {
        GigaMap<StringPerson> map = prepareGigaMap();
        fillData(map);
        map.add(new StringPerson(null, null));
        assertEquals(0 ,map.query(profIndex.contains("flds")).count());
        assertEquals(0 ,map.query(profIndex.containsIgnoreCase("flds")).count());
        assertEquals(0 ,map.query(profIndex.startsWith("ffdsafldjsa jfksdj fklsadj flkdsjfklds")).count());
        assertEquals(0 ,map.query(profIndex.startsWithIgnoreCase("ffdsafldjsa jfksdj fklsadj flkdsjfklds")).count());
        assertEquals(0 ,map.query(profIndex.endsWith("ffdsafldjsa jfksdj fklsadj flkdsjfklds")).count());
        assertEquals(1 ,map.query(profIndex.endsWith(null)).count());
        assertEquals(1 ,map.query(profIndex.isBlank()).count());

        map.add(new StringPerson("David", ""));
        assertEquals(2 ,map.query(profIndex.isBlank()).count());
        assertEquals(2 ,map.query(profIndex.isEmpty()).count());
    }

    @Test
    void isBlank()
    {
        GigaMap<StringPerson> map = prepareGigaMap();
        fillData(map);
        map.add(new StringPerson("David", " "));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            assertEquals(1, map.query(profIndex.isBlank()).count());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<StringPerson> newMap = (GigaMap<StringPerson>) manager.root();
            assertEquals(1, newMap.query(profIndex.isBlank()).count());
        }
    }

    @Test
    void isEmpty()
    {
        GigaMap<StringPerson> map = prepareGigaMap();
        fillData(map);
        map.add(new StringPerson("David", ""));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            assertEquals(1, map.query(profIndex.isEmpty()).count());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<StringPerson> newMap = (GigaMap<StringPerson>) manager.root();
            assertEquals(1, newMap.query(profIndex.isEmpty()).count());
        }
    }

    @Test
    void endsWithIgnoreCase()
    {
        GigaMap<StringPerson> map = prepareGigaMap();
        fillData(map);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            assertEquals(2, map.query(profIndex.endsWithIgnoreCase("eer")).count());
            map.query(profIndex.endsWithIgnoreCase("eer")).forEach(stringPerson -> assertEquals("Engineer", stringPerson.getProfession()));
            assertEquals(1, map.query(profIndex.endsWithIgnoreCase("tor")).count());
            map.query(profIndex.endsWithIgnoreCase("tor")).forEach(stringPerson -> assertEquals("Doctor", stringPerson.getProfession()));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<StringPerson> newMap = (GigaMap<StringPerson>) manager.root();
            assertEquals(2, newMap.query(profIndex.endsWithIgnoreCase("eer")).count());
            newMap.query(profIndex.endsWithIgnoreCase("eer")).forEach(stringPerson -> assertEquals("Engineer", stringPerson.getProfession()));
            assertEquals(1, newMap.query(profIndex.endsWithIgnoreCase("tor")).count());
            newMap.query(profIndex.endsWithIgnoreCase("tor")).forEach(stringPerson -> assertEquals("Doctor", stringPerson.getProfession()));
        }
    }

    @Test
    void endsWith()
    {
        GigaMap<StringPerson> map = prepareGigaMap();
        fillData(map);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            assertEquals(2, map.query(profIndex.endsWith("eer")).count());
            map.query(profIndex.endsWith("eer")).forEach(stringPerson -> assertEquals("Engineer", stringPerson.getProfession()));
            assertEquals(1, map.query(profIndex.endsWith("tor")).count());
            map.query(profIndex.endsWith("tor")).forEach(stringPerson -> assertEquals("Doctor", stringPerson.getProfession()));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<StringPerson> newMap = (GigaMap<StringPerson>) manager.root();
            assertEquals(2, newMap.query(profIndex.endsWith("eer")).count());
            newMap.query(profIndex.endsWith("eer")).forEach(stringPerson -> assertEquals("Engineer", stringPerson.getProfession()));
            assertEquals(1, newMap.query(profIndex.endsWith("tor")).count());
            newMap.query(profIndex.endsWith("tor")).forEach(stringPerson -> assertEquals("Doctor", stringPerson.getProfession()));
        }
    }

    @Test
    void containsIgnoreCase()
    {
        GigaMap<StringPerson> map = prepareGigaMap();
        fillData(map);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            assertEquals(2, map.query(profIndex.containsIgnoreCase("eng")).count());
            map.query(profIndex.containsIgnoreCase("eng")).forEach(stringPerson -> assertEquals("Engineer", stringPerson.getProfession()));
            assertEquals(1, map.query(profIndex.containsIgnoreCase("doc")).count());
            map.query(profIndex.containsIgnoreCase("doc")).forEach(stringPerson -> assertEquals("Doctor", stringPerson.getProfession()));

            assertEquals(0, map.query(profIndex.containsIgnoreCase(null)).count());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<StringPerson> newMap = (GigaMap<StringPerson>) manager.root();
            assertEquals(2, newMap.query(profIndex.containsIgnoreCase("eng")).count());
            newMap.query(profIndex.containsIgnoreCase("eng")).forEach(stringPerson -> assertEquals("Engineer", stringPerson.getProfession()));
            assertEquals(1, newMap.query(profIndex.containsIgnoreCase("doc")).count());
            newMap.query(profIndex.containsIgnoreCase("doc")).forEach(stringPerson -> assertEquals("Doctor", stringPerson.getProfession()));

            assertEquals(0, map.query(profIndex.containsIgnoreCase(null)).count());
        }
    }

    @Test
    void contains()
    {
        GigaMap<StringPerson> map = prepareGigaMap();
        fillData(map);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            assertEquals(2, map.query(profIndex.contains("Eng")).count());
            map.query(profIndex.contains("Eng")).forEach(stringPerson -> assertEquals("Engineer", stringPerson.getProfession()));
            assertEquals(1, map.query(profIndex.contains("Doc")).count());
            map.query(profIndex.contains("Doc")).forEach(stringPerson -> assertEquals("Doctor", stringPerson.getProfession()));
            assertEquals(0, map.query(profIndex.contains(null)).count());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<StringPerson> newMap = (GigaMap<StringPerson>) manager.root();
            assertEquals(2, newMap.query(profIndex.contains("Eng")).count());
            newMap.query(profIndex.contains("Eng")).forEach(stringPerson -> assertEquals("Engineer", stringPerson.getProfession()));
            assertEquals(1, newMap.query(profIndex.contains("Doc")).count());
            newMap.query(profIndex.contains("Doc")).forEach(stringPerson -> assertEquals("Doctor", stringPerson.getProfession()));
            assertEquals(0, map.query(profIndex.contains(null)).count());
        }
    }

    @Test
    void startWith()
    {
        GigaMap<StringPerson> map = prepareGigaMap();
        fillData(map);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            assertEquals(2, map.query(profIndex.startsWith("Eng")).count());
            map.query(profIndex.startsWith("Eng")).forEach(stringPerson -> assertEquals("Engineer", stringPerson.getProfession()));
            assertEquals(1, map.query(profIndex.startsWith("Doc")).count());
            map.query(profIndex.startsWith("Doc")).forEach(stringPerson -> assertEquals("Doctor", stringPerson.getProfession()));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<StringPerson> newMap = (GigaMap<StringPerson>) manager.root();
            assertEquals(2, newMap.query(profIndex.startsWith("Eng")).count());
            newMap.query(profIndex.startsWith("Eng")).forEach(stringPerson -> assertEquals("Engineer", stringPerson.getProfession()));
            assertEquals(1, newMap.query(profIndex.startsWith("Doc")).count());
            newMap.query(profIndex.startsWith("Doc")).forEach(stringPerson -> assertEquals("Doctor", stringPerson.getProfession()));
        }
    }

    @Test
    void startWithIgnoreCase()
    {
        GigaMap<StringPerson> map = prepareGigaMap();
        fillData(map);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            assertEquals(2, map.query(profIndex.startsWithIgnoreCase("eng")).count());
            map.query(profIndex.startsWithIgnoreCase("eng")).forEach(stringPerson -> assertEquals("Engineer", stringPerson.getProfession()));
            assertEquals(1, map.query(profIndex.startsWithIgnoreCase("doc")).count());
            map.query(profIndex.startsWithIgnoreCase("doc")).forEach(stringPerson -> assertEquals("Doctor", stringPerson.getProfession()));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<StringPerson> newMap = (GigaMap<StringPerson>) manager.root();
            assertEquals(2, newMap.query(profIndex.startsWithIgnoreCase("eng")).count());
            newMap.query(profIndex.startsWithIgnoreCase("eng")).forEach(stringPerson -> assertEquals("Engineer", stringPerson.getProfession()));
            assertEquals(1, newMap.query(profIndex.startsWithIgnoreCase("doc")).count());
            newMap.query(profIndex.startsWithIgnoreCase("doc")).forEach(stringPerson -> assertEquals("Doctor", stringPerson.getProfession()));

            newMap.add(new StringPerson("David", null));
            assertEquals(1, newMap.query(profIndex.startsWithIgnoreCase(null)).count());
        }

    }


    @Test
    void stringPersonTest()
    {
        GigaMap<StringPerson> map = prepareGigaMap();
        fillData(map);


        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.query(profIndex.is("Engineer")).forEach(stringPerson -> assertEquals("Engineer", stringPerson.getProfession()));
            map.query(profIndex.is("Doctor")).forEach(stringPerson -> assertEquals("Doctor", stringPerson.getProfession()));

            map.query(profIndex.is("Engineer")).and(profIndex.is("Doctor"))
                    .forEach(stringPerson -> fail("Should not be reached"));

            long count = map.query(profIndex.contains("oct")).count();
            assertEquals(1, count);

            long count1 = map.query(profIndex.containsIgnoreCase("OCT")).count();
            assertEquals(1, count1);

            List<StringPerson> personList = map.query(profIndex.is("Engineer")).or(profIndex.is("Doctor"))
                    .stream().collect(Collectors.toList());
            assertEquals(3, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<StringPerson> newMap = (GigaMap<StringPerson>) manager.root();
            newMap.query(profIndex.is("Engineer")).forEach(stringPerson -> assertEquals("Engineer", stringPerson.getProfession()));
            newMap.query(profIndex.is("Doctor")).forEach(stringPerson -> assertEquals("Doctor", stringPerson.getProfession()));

            newMap.query(profIndex.is("Engineer")).and(profIndex.is("Doctor"))
                    .forEach(stringPerson -> fail("Should not be reached"));

            long count = map.query(profIndex.contains("oct")).count();
            assertEquals(1, count);

            long count1 = map.query(profIndex.containsIgnoreCase("OCT")).count();
            assertEquals(1, count1);

            List<StringPerson> personList = newMap.query(profIndex.is("Engineer")).or(profIndex.is("Doctor"))
                    .stream().collect(Collectors.toList());
            assertEquals(3, personList.size());
        }
    }

    private void fillData(GigaMap<StringPerson> map)
    {
        StringPerson person1 = new StringPerson("Alice", "Engineer");
        StringPerson person2 = new StringPerson("Bob", "Doctor");
        StringPerson person3 = new StringPerson("Charlie", "Engineer");
        map.addAll(person1, person2, person3);
    }

    private GigaMap<StringPerson> prepareGigaMap()
    {
        GigaMap<StringPerson> map = GigaMap.New();

        BitmapIndices<StringPerson> bitmap = map.index().bitmap();
        bitmap.add(profIndex);

        return map;
    }


    private static class StringPersonProfessionIndex extends IndexerString.Abstract<StringPerson>
    {
        @Override
        protected String getString(StringPerson entity)
        {
            return entity.getProfession();
        }
    }

    private static class StringPerson
    {
        private final String name;
        private final String profession;

        public StringPerson(String name, String profession)
        {
            this.name = name;
            this.profession = profession;
        }

        public String name()
        {
            return this.name;
        }

        public String getProfession()
        {
            return this.profession;
        }
    }
}
