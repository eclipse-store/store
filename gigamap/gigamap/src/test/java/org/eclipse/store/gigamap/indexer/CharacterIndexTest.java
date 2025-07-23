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
import org.eclipse.store.gigamap.types.IndexerCharacter;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class CharacterIndexTest
{
    @TempDir
    Path tempDir;

    private final CharacterPersonIndex characterPersonIndex = new CharacterPersonIndex();

    @Test
    void byExample()
    {
        GigaMap<CharacterPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            CharacterPerson characterPerson = map.get(1);
            long count1 = map.query(characterPersonIndex.byExample(characterPerson)).count();
            assertEquals(1, count1);
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<CharacterPerson> newMap = (GigaMap<CharacterPerson>) manager.root();
            CharacterPerson characterPerson = newMap.get(1);
            long count1 = newMap.query(characterPersonIndex.byExample(characterPerson)).count();
            assertEquals(1, count1);
        }
    }

    @Test
    void inTest()
    {
        GigaMap<CharacterPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            long count = map.query(characterPersonIndex.in('A', 'B')).count();
            assertEquals(2, count);
            map.query(characterPersonIndex.in('A', 'B')).forEach(characterPerson -> assertTrue(characterPerson.getInitial() == 'A' || characterPerson.getInitial() == 'B'));
            map.query(characterPersonIndex.in('B', 'C')).forEach(characterPerson -> assertTrue(characterPerson.getInitial() == 'B' || characterPerson.getInitial() == 'C'));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<CharacterPerson> newMap = (GigaMap<CharacterPerson>) manager.root();
            long count = newMap.query(characterPersonIndex.in('A', 'B')).count();
            assertEquals(2, count);
            newMap.query(characterPersonIndex.in('A', 'B')).forEach(characterPerson -> assertTrue(characterPerson.getInitial() == 'A' || characterPerson.getInitial() == 'B'));
            newMap.query(characterPersonIndex.in('B', 'C')).forEach(characterPerson -> assertTrue(characterPerson.getInitial() == 'B' || characterPerson.getInitial() == 'C'));
        }
    }

    @Test
    void characterPersonTest()
    {
        GigaMap<CharacterPerson> map = prepageGigaMap();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.query(characterPersonIndex.is('A')).forEach(characterPerson -> assertEquals('A', characterPerson.getInitial()));
            map.query(characterPersonIndex.is('B')).forEach(characterPerson -> assertEquals('B', characterPerson.getInitial()));

            map.query(characterPersonIndex.is('A')).and(characterPersonIndex.is('B'))
                    .forEach(characterPerson -> fail("Should not be reached"));

            List<CharacterPerson> personList = map.query(characterPersonIndex.is('A')).or(characterPersonIndex.is('B'))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<CharacterPerson> newMap = (GigaMap<CharacterPerson>) manager.root();
            newMap.query(characterPersonIndex.is('A')).forEach(characterPerson -> assertEquals('A', characterPerson.getInitial()));
            newMap.query(characterPersonIndex.is('B')).forEach(characterPerson -> assertEquals('B', characterPerson.getInitial()));

            newMap.query(characterPersonIndex.is('A')).and(characterPersonIndex.is('B'))
                    .forEach(characterPerson -> fail("Should not be reached"));

            List<CharacterPerson> personList = newMap.query(characterPersonIndex.is('A')).or(characterPersonIndex.is('B'))
                    .stream().collect(Collectors.toList());
            assertEquals(2, personList.size());
        }
    }

    private GigaMap<CharacterPerson> prepageGigaMap()
    {
        GigaMap<CharacterPerson> map = GigaMap.New();

        map.index().bitmap().add(characterPersonIndex);

        CharacterPerson person1 = new CharacterPerson("Alice", 'A');
        CharacterPerson person2 = new CharacterPerson("Bob", 'B');
        CharacterPerson person3 = new CharacterPerson("Charlie", 'C');
        map.addAll(person1, person2, person3);
        return map;
    }

    private static class CharacterPersonIndex extends IndexerCharacter.Abstract<CharacterPerson>
    {
    	@Override
    	protected Character getCharacter(CharacterPerson entity)
        {
            return entity.getInitial();
        }
    }

    private static class CharacterPerson
    {
        private final String name;
        private final char initial;

        public CharacterPerson(String name, char initial)
        {
            this.name = name;
            this.initial = initial;
        }

        public String name()
        {
            return this.name;
        }

        public char getInitial()
        {
            return this.initial;
        }
    }
}
