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
import org.eclipse.store.gigamap.types.IndexerString;
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
            GigaMap<BooleanPerson> newMap = storageManager.root();
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
            GigaMap<BooleanPerson> newMap = manager.root();
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
            GigaMap<BooleanPerson> newMap = manager.root();
            long count1 = newMap.query(booleanPersonIndex.isTrue()).count();
            assertEquals(2, count1);

            newMap.query(booleanPersonIndex.isTrue()).forEach(booleanPerson -> assertTrue(booleanPerson.isAdult()));
            newMap.query(booleanPersonIndex.isFalse()).forEach(booleanPerson -> assertFalse(booleanPerson.isAdult()));
        }
    }

    @Test
    void updateLastTrueToFalse_doesNotThrow()
    {
        // Regression for issue 685: flipping the indexed boolean from TRUE to FALSE on the
        // last (and only) TRUE-indexed entity used to throw java.lang.Error from
        // SingleBitmapIndex.removeEntry via the update / set / replace change-handler path.
        GigaMap<BooleanPerson> map = GigaMap.New();
        map.index().bitmap().add(this.booleanPersonIndex);

        BooleanPerson person = new BooleanPerson("Solo", true);
        map.add(person);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            map.update(person, p -> p.setAdult(Boolean.FALSE));
            map.store();

            assertEquals(0, map.query(this.booleanPersonIndex.isTrue()).count());
            assertEquals(1, map.query(this.booleanPersonIndex.isFalse()).count());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<BooleanPerson> newMap = manager.root();
            assertEquals(0, newMap.query(this.booleanPersonIndex.isTrue()).count());
            assertEquals(1, newMap.query(this.booleanPersonIndex.isFalse()).count());
        }
    }

    @Test
    void setLastTrueToFalse_doesNotThrow()
    {
        // Regression for issue 685, set-based path. Same shape as updateLastTrueToFalse_doesNotThrow
        // but mutates via map.set(entityId, replacement) instead of map.update(...).
        GigaMap<BooleanPerson> map = GigaMap.New();
        map.index().bitmap().add(this.booleanPersonIndex);

        BooleanPerson person = new BooleanPerson("Solo", true);
        long entityId = map.add(person);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            BooleanPerson replacement = new BooleanPerson("Solo", false);
            map.set(entityId, replacement);
            map.store();

            assertEquals(0, map.query(this.booleanPersonIndex.isTrue()).count());
            assertEquals(1, map.query(this.booleanPersonIndex.isFalse()).count());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<BooleanPerson> newMap = manager.root();
            assertEquals(0, newMap.query(this.booleanPersonIndex.isTrue()).count());
            assertEquals(1, newMap.query(this.booleanPersonIndex.isFalse()).count());
        }
    }

    @Test
    void nullValuedEntityUpdateDoesNotThrow()
    {
        // Reproducer: update() / apply() on an entity whose indexed Boolean is null used to throw
        // IllegalArgumentException("Entity not found"), even though the entity is present
        // (counted by size(), retrievable by get(id)).
        //
        // Root cause: the entity locator queries the indices with the entity's current value;
        // SingleBitmapIndex.internalQuery(null) returned the empty result, so a null-valued entity
        // yielded no candidate id. A false value is located via the inverted (NOT-TRUE) result, so
        // the defect was null-specific (see falseValuedEntityUpdateDoesNotThrow for the contrast).
        GigaMap<BooleanPerson> map = GigaMap.New();
        map.index().bitmap().add(this.booleanPersonIndex);

        BooleanPerson person = new BooleanPerson("Dave", null);   // optional flag left unset
        map.add(person);
        assertEquals(1, map.size());                              // the entity is present...

        map.update(person, p -> p.setAdult(Boolean.TRUE));        // ...and must not throw "Entity not found"

        assertEquals(1, map.query(this.booleanPersonIndex.isTrue()).count());
    }

    @Test
    void falseValuedEntityUpdateDoesNotThrow()
    {
        // Control: a false-valued entity updates fine - proves the null reproducer is not vacuous.
        GigaMap<BooleanPerson> map = GigaMap.New();
        map.index().bitmap().add(this.booleanPersonIndex);

        BooleanPerson person = new BooleanPerson("Eve", Boolean.FALSE);
        map.add(person);

        map.update(person, p -> p.setAdult(Boolean.TRUE));

        assertEquals(1, map.query(this.booleanPersonIndex.isTrue()).count());
    }

    @Test
    void nullValuedEntityWithSecondNonNullIndexUpdates()
    {
        // Even with a second, non-null index (so the entity is findable via that index), updating a
        // null-valued Boolean entity must succeed: the entity locator intersects across indices, and
        // the null-boolean leg must not contribute an empty set.
        IndexerString<BooleanPerson> nameIndex = new IndexerString.Abstract<>()
        {
            @Override
            protected String getString(BooleanPerson e)
            {
                return e.name();
            }
        };
        GigaMap<BooleanPerson> map = GigaMap.New();
        map.index().bitmap().add(this.booleanPersonIndex);
        map.index().bitmap().add(nameIndex);

        BooleanPerson person = new BooleanPerson("Alice", null);   // adult null, name non-null
        map.add(person);

        map.update(person, p -> p.setAdult(Boolean.TRUE));

        assertEquals(1, map.query(this.booleanPersonIndex.isTrue()).count());
    }

    @Test
    void conditionTestMatchesBitmapQuery()
    {
        // The in-memory predicate path (Condition#test) must agree with the bitmap query for this
        // index. Since SingleBitmapIndex cannot tell FALSE from null (neither is in the TRUE set),
        // is(false) and is(null) both resolve to the NOT-TRUE set in BOTH paths.
        GigaMap<BooleanPerson> map = GigaMap.New();
        map.index().bitmap().add(this.booleanPersonIndex);

        BooleanPerson adult   = new BooleanPerson("Alice", Boolean.TRUE);
        BooleanPerson child   = new BooleanPerson("Bob",   Boolean.FALSE);
        BooleanPerson unknown = new BooleanPerson("Dave",  null);
        map.addAll(adult, child, unknown);

        // isTrue(): only the TRUE-valued entity, consistent across query and predicate.
        assertEquals(1, map.query(this.booleanPersonIndex.isTrue()).count());
        assertTrue(this.booleanPersonIndex.isTrue().test(adult));
        assertFalse(this.booleanPersonIndex.isTrue().test(child));
        assertFalse(this.booleanPersonIndex.isTrue().test(unknown));

        // isFalse() / is(null): the NOT-TRUE set (FALSE and null), consistent across query and predicate.
        assertEquals(2, map.query(this.booleanPersonIndex.isFalse()).count());
        assertFalse(this.booleanPersonIndex.isFalse().test(adult));
        assertTrue(this.booleanPersonIndex.isFalse().test(child));
        assertTrue(this.booleanPersonIndex.isFalse().test(unknown));   // null is NOT-TRUE

        assertEquals(2, map.query(this.booleanPersonIndex.is((Boolean)null)).count());
        assertFalse(this.booleanPersonIndex.is((Boolean)null).test(adult));
        assertTrue(this.booleanPersonIndex.is((Boolean)null).test(child));      // false is NOT-TRUE
        assertTrue(this.booleanPersonIndex.is((Boolean)null).test(unknown));
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
            GigaMap<BooleanPerson> newMap = manager.root();
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

        public BooleanPerson(String name, Boolean isAdult)
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
