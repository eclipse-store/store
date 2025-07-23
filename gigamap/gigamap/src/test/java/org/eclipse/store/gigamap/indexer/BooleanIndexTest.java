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
import org.eclipse.store.gigamap.types.IndexerBoolean;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class BooleanIndexTest
{

    @TempDir
    Path tempDir;

    private final BooleanPersonIndex booleanPersonIndex = new BooleanPersonIndex();


    @Test
    void updateIndex()
    {
        GigaMap<BooleanPerson> map = prepageGigaMap();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, tempDir)) {}

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            GigaMap<BooleanPerson> newMap = (GigaMap<BooleanPerson>) storageManager.root();
            assertEquals(2, newMap.query(booleanPersonIndex.isTrue()).count());
            assertEquals(1, newMap.query(booleanPersonIndex.isFalse()).count());
            BooleanPerson booleanPerson = newMap.get(0);
            newMap.update(booleanPerson, booleanPerson1 -> booleanPerson1.setAdult(Boolean.TRUE));
        }


    }

    @Test
    void isFalse()
    {
        GigaMap<BooleanPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count1 = map.query(booleanPersonIndex.isFalse()).count();
            assertEquals(1, count1);
            map.query(booleanPersonIndex.isFalse()).forEach(booleanPerson -> assertFalse(booleanPerson.isAdult()));
            map.query(booleanPersonIndex.isTrue()).forEach(booleanPerson -> assertTrue(booleanPerson.isAdult()));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<BooleanPerson> newMap = (GigaMap<BooleanPerson>) manager.root();
            long count1 = newMap.query(booleanPersonIndex.isFalse()).count();
            assertEquals(1, count1);
            newMap.query(booleanPersonIndex.isFalse()).forEach(booleanPerson -> assertFalse(booleanPerson.isAdult()));
            newMap.query(booleanPersonIndex.isTrue()).forEach(booleanPerson -> assertTrue(booleanPerson.isAdult()));
        }
    }

    @Test
    void isTrue()
    {
        GigaMap<BooleanPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {

            long count1 = map.query(booleanPersonIndex.isTrue()).count();
            assertEquals(2, count1);
            map.query(booleanPersonIndex.isTrue()).forEach(booleanPerson -> assertTrue(booleanPerson.isAdult()));
            map.query(booleanPersonIndex.isFalse()).forEach(booleanPerson -> assertFalse(booleanPerson.isAdult()));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<BooleanPerson> newMap = (GigaMap<BooleanPerson>) manager.root();
            long count1 = newMap.query(booleanPersonIndex.isTrue()).count();
            assertEquals(2, count1);

            newMap.query(booleanPersonIndex.isTrue()).forEach(booleanPerson -> assertTrue(booleanPerson.isAdult()));
            newMap.query(booleanPersonIndex.isFalse()).forEach(booleanPerson -> assertFalse(booleanPerson.isAdult()));
        }
    }

    @Test
    void booleanPersonTest()
    {
        GigaMap<BooleanPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.query(booleanPersonIndex.is(true)).forEach(booleanPerson -> assertTrue(booleanPerson.isAdult()));
            map.query(booleanPersonIndex.is(false)).forEach(booleanPerson -> assertFalse(booleanPerson.isAdult()));

            map.query(booleanPersonIndex.is(true)).and(booleanPersonIndex.is(false))
                    .forEach(booleanPerson -> fail("Should not be reached"));

            List<BooleanPerson> personList = map.query(booleanPersonIndex.is(true)).or(booleanPersonIndex.is(false))
                    .stream().collect(Collectors.toList());
            assertEquals(3, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<BooleanPerson> newMap = (GigaMap<BooleanPerson>) manager.root();
            newMap.query(booleanPersonIndex.is(true)).forEach(booleanPerson -> assertTrue(booleanPerson.isAdult()));
            newMap.query(booleanPersonIndex.is(false)).forEach(booleanPerson -> assertFalse(booleanPerson.isAdult()));

            newMap.query(booleanPersonIndex.is(true)).and(booleanPersonIndex.is(false))
                    .forEach(booleanPerson -> fail("Should not be reached"));

            List<BooleanPerson> personList = newMap.query(booleanPersonIndex.is(true)).or(booleanPersonIndex.is(false))
                    .stream().collect(Collectors.toList());
            assertEquals(3, personList.size());
        }

    }

    private GigaMap<BooleanPerson> prepageGigaMap()
    {
        GigaMap<BooleanPerson> map = GigaMap.New();
        map.index().bitmap().add(booleanPersonIndex);

        BooleanPerson person1 = new BooleanPerson("Alice", true);
        BooleanPerson person2 = new BooleanPerson("Bob", false);
        BooleanPerson person3 = new BooleanPerson("Charlie", true);
        map.addAll(person1, person2, person3);
        return map;
    }

    private static class BooleanPersonIndex extends IndexerBoolean.Abstract<BooleanPerson>
    {
		@Override
		protected Boolean getBoolean(BooleanPerson entity)
		{
            return entity.isAdult();
		}
    }


    private static class BooleanPerson
    {
        private final String name;
        private Boolean isAdult;

        public BooleanPerson(String name, boolean isAdult)
        {
            this.name = name;
            this.isAdult = isAdult;
        }

        public String name()
        {
            return this.name;
        }

        public Boolean isAdult()
        {
            return this.isAdult;
        }

        public void setAdult(Boolean adult)
        {
            isAdult = adult;
        }
    }
}
