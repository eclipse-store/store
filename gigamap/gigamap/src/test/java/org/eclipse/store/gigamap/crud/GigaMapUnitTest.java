package org.eclipse.store.gigamap.crud;

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

import org.eclipse.store.gigamap.types.*;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class GigaMapUnitTest
{

    @TempDir
    Path tempDir;


    @Test
    void newWithLogMidHighExponent_withLimit()
    {
        final GigaMap<String> gigaMap = GigaMap.New(5, 10, 5, 8);
        gigaMap.add("1000");
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertEquals("1000", gigaMap.get(0));
        }
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertEquals("1000", gigaMap2.get(0));
        }
    }

    @Test
    void newWithLogMidHighExponent()
    {
        final GigaMap<String> gigaMap = GigaMap.New(5, 10, 20);
        gigaMap.add("1000");
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertEquals("1000", gigaMap.get(0));
        }
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertEquals("1000", gigaMap2.get(0));
        }
    }

    @Test
    void addNullTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        assertThrows(IllegalArgumentException.class, () -> gigaMap.add(null));
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertThrows(IllegalArgumentException.class, () -> gigaMap.add(null));
        }
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertThrows(IllegalArgumentException.class, () -> gigaMap2.add(null));
        }
    }

    @Test
    void replaceWithNullTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        String s = "1000";
        gigaMap.add(s);
        assertThrows(IllegalArgumentException.class, () -> gigaMap.replace(s, null));
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertThrows(IllegalArgumentException.class, () -> gigaMap.add(null));
        }
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertThrows(IllegalArgumentException.class, () -> gigaMap2.replace(s, null));
        }
    }

    @Test
    void setWithNull()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        String s = "1000";
        gigaMap.add(s);
        assertThrows(IllegalArgumentException.class, () -> gigaMap.set(0, null));
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertThrows(IllegalArgumentException.class, () -> gigaMap.set(0, null));
        }
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertThrows(IllegalArgumentException.class, () -> gigaMap2.set(0, null));
        }
    }

    @Test
    void newWithLowMidExponent()
    {
        final GigaMap<String> gigaMap = GigaMap.New(10, 20);
        gigaMap.add("1000");
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertEquals("1000", gigaMap.get(0));
        }
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertEquals("1000", gigaMap2.get(0));
        }
    }

    @Test
    void removeWithNull()
    {
        class StringIndexer extends IndexerString.Abstract<String>
        {
            @Override
            protected String getString(final String entity)
            {
                return entity;
            }
        }

        final GigaMap<String> gigaMap = GigaMap.New();
        StringIndexer indexer = new StringIndexer();
        gigaMap.index().bitmap().add(indexer);
        String s = "1000";
        gigaMap.add(s);
        assertThrows(IllegalArgumentException.class, () -> gigaMap.remove(null, indexer));
        assertThrows(IllegalArgumentException.class, () -> gigaMap.remove(null));

    }

    @Test
    void newWithExponent()
    {
        final GigaMap<String> gigaMap = GigaMap.New(10);
        gigaMap.add("1000");
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertEquals("1000", gigaMap.get(0));
        }
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertEquals("1000", gigaMap2.get(0));
        }
    }

    @Test
    void highestUsedIdTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(new StringIndexer());

        assertEquals(-1, gigaMap.highestUsedId());

        gigaMap.add("1000");
        assertEquals(0, gigaMap.highestUsedId());

        for (int i = 0; i < 10; i++) {
            gigaMap.add(String.valueOf(i));
        }

        assertEquals(10, gigaMap.highestUsedId());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertEquals(10, gigaMap.highestUsedId());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertEquals(10, gigaMap2.highestUsedId());
        }
    }

    @Test
    void isEmpty()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(new StringIndexer());

        assertTrue(gigaMap.isEmpty());

        gigaMap.add("1000");
        assertFalse(gigaMap.isEmpty());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertFalse(gigaMap.isEmpty());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertFalse(gigaMap2.isEmpty());
        }
    }

    @Test
    void getTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(new StringIndexer());

        assertTrue(gigaMap.isEmpty());
        assertNull(gigaMap.get(5));
        assertNull(gigaMap.get(-1));
        assertNull(gigaMap.get(0));

        assertNull(gigaMap.get(Long.MIN_VALUE));
        assertNull(gigaMap.get((long) Math.pow(2, 48))); // max entity count


        gigaMap.add("1000");
        assertEquals("1000", gigaMap.get(0));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertEquals("1000", gigaMap.get(0));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertEquals("1000", gigaMap2.get(0));
        }

    }

    @Test
    void getEasyTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        assertNull(gigaMap.get(0));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertNull(gigaMap.get(0));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertNull(gigaMap2.get(0));
        }

    }

    @Test
    void addTest()
    {

        final GigaMap<String> gigaMap = GigaMap.New();
        final StringIndexer indexer = new StringIndexer();
        gigaMap.index().bitmap().add(indexer);

        final String s = "2000";

        for (int i = 0; i < 1000; i++) {
            gigaMap.add(s);
        }

        for (int i = 0; i < 1000; i++) {
            gigaMap.remove(s);
        }
        assertTrue(gigaMap.isEmpty());
        assertEquals(999, gigaMap.highestUsedId());
        assertEquals(0, gigaMap.query(indexer.is("1000")).count());
        assertEquals(1000, gigaMap.add("1000"));
        gigaMap.remove("1000");
        gigaMap.add("1000");
        assertEquals(1002, gigaMap.add("1000"));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertEquals(1003, gigaMap.add("1000"));
            gigaMap.store();
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertEquals(1004, gigaMap2.add("1000"));
        }
    }

    @Test
    void valueOfTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        final StringIndexer indexer = new StringIndexer();
        gigaMap.index().bitmap().add(indexer);

        gigaMap.add(String.valueOf(1000));
        assertEquals(-1, gigaMap.remove(String.valueOf(1000))); // will not be removed. It expects the same instance.

        long count = gigaMap.query(indexer.is("1000")).count();
        assertEquals(1, count);

    }

    @Test
    void addAllTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        final StringIndexer indexer = new StringIndexer();
        gigaMap.index().bitmap().add(indexer);

        //generate ArrayList with 1000 elements
        final List<String> values = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            values.add(String.valueOf(i));
        }
        final long l = gigaMap.addAll(values);
        assertEquals(1000, l);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertEquals(1000, gigaMap.addAll(values));
            gigaMap.store();
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertEquals(1000, gigaMap2.addAll(values));
            assertEquals(3000, gigaMap2.size());
        }
    }

    @Test
    void addAllVarArgsTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        final StringIndexer indexer = new StringIndexer();
        gigaMap.index().bitmap().add(indexer);

        gigaMap.addAll("1000", "1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009");
        assertEquals(10, gigaMap.size());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            gigaMap.addAll("1000", "1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009");
            gigaMap.store();
            assertEquals(20, gigaMap.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            gigaMap2.addAll("1000", "1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009");
            assertEquals(30, gigaMap2.size());
        }

    }

    @Test
    void peekTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        final StringIndexer indexer = new StringIndexer();
        gigaMap.index().bitmap().add(indexer);

        gigaMap.add("1000");
        assertEquals("1000", gigaMap.peek(0));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertEquals("1000", gigaMap.peek(0));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertNull(gigaMap2.peek(0));
        }
    }

    @Test
    void peakEasyTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        assertNull(gigaMap.peek(-1));
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertNull(gigaMap.peek(-1));
        }
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertNull(gigaMap2.peek(-1));
        }
    }

    @Test
    void removeByIdTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        final StringIndexer indexer = new StringIndexer();
        gigaMap.index().bitmap().add(indexer);

        gigaMap.addAll("1000", "1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009");

        assertEquals("1000", gigaMap.removeById(0));
        gigaMap.removeById(1);
        gigaMap.removeById(2);
        gigaMap.removeById(3);
        gigaMap.removeById(4);
        gigaMap.removeById(5);
        gigaMap.removeById(6);
        gigaMap.removeById(7);
        gigaMap.removeById(8);
        gigaMap.removeById(9);

        assertEquals(0, gigaMap.size());
        assertNull(gigaMap.removeById(9));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertNull(gigaMap.removeById(9));
        }
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertNull(gigaMap2.removeById(9));
        }
    }

    @Test
    void removeByIdWithoutIndexerTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();

        gigaMap.addAll("1000", "1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009");

        assertEquals("1000", gigaMap.removeById(0));
        gigaMap.removeById(1);
        gigaMap.removeById(2);
        gigaMap.removeById(3);
        gigaMap.removeById(4);
        gigaMap.removeById(5);
        gigaMap.removeById(6);
        gigaMap.removeById(7);
        gigaMap.removeById(8);
        assertEquals("1009", gigaMap.removeById(9));

        assertEquals(0, gigaMap.size());

        assertNull(gigaMap.removeById(9));
    }

    @Test
    void removeWithIndexTest()
    {
        class StringIndexer extends IndexerString.Abstract<String>
        {
            @Override
            protected String getString(final String entity)
            {
                return entity;
            }
        }

        final GigaMap<String> gigaMap = GigaMap.New();
        final StringIndexer indexer = new StringIndexer();
        gigaMap.index().bitmap().add(indexer);

        gigaMap.addAll("1000", "1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009");

        gigaMap.remove("1000", indexer);
        gigaMap.remove("1001", indexer);
        gigaMap.remove("1002", indexer);
        gigaMap.remove("1003", indexer);
        gigaMap.remove("1004", indexer);
        gigaMap.remove("1005", indexer);
        gigaMap.remove("1006", indexer);
        gigaMap.remove("1007", indexer);
        gigaMap.remove("1008", indexer);
        gigaMap.remove("1009", indexer);

        assertEquals(0, gigaMap.size());
    }

    @Test
    void removeWithNonExistsIndexTest()
    {
        class StringIndexer extends IndexerString.Abstract<String>
        {
            @Override
            protected String getString(final String entity)
            {
                return entity;
            }
        }

        final GigaMap<String> gigaMap = GigaMap.New();
        final StringIndexer indexer = new StringIndexer();

        gigaMap.addAll("1000", "1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009");

        assertThrows(RuntimeException.class, () -> gigaMap.remove("1000", indexer));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertThrows(RuntimeException.class, () -> gigaMap.remove("1000", indexer));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertThrows(RuntimeException.class, () -> gigaMap2.remove("1000", indexer));
        }
    }

    @Test
    void removeWithTwoExistsIndexTest()
    {
        class Entity
        {
            String name;
            Integer year;

            public Entity(final Integer year)
            {
                this.year = year;
                this.name = year + "";
            }

        }

        class StringIndexer extends IndexerString.Abstract<Entity>
        {
			@Override
			protected String getString(final Entity entity)
			{
                return entity.name;
			}
        }

        class IntegerIndexer extends IndexerInteger.Abstract<Entity>
        {
			@Override
			protected Integer getInteger(final Entity entity)
			{
                return entity.year;
			}
        }

        final GigaMap<Entity> gigaMap = GigaMap.New();
        final StringIndexer indexer = new StringIndexer();
        final IntegerIndexer integerIndexer = new IntegerIndexer();
        gigaMap.index().bitmap().add(indexer);
        gigaMap.index().bitmap().add(integerIndexer);

        gigaMap.add(new Entity(1000));
        gigaMap.add(new Entity(1001));
        gigaMap.add(new Entity(1002));

        Optional<Entity> first = gigaMap.query(indexer.is("1000")).findFirst();
        assertTrue(first.isPresent());

        final long remove = gigaMap.remove(first.get(), indexer, integerIndexer);
        assertEquals(0, remove, "Index of the entity should be 0");
        assertEquals(2, gigaMap.size());

    }

    @Test
    void replaceTest()
    {
        class Entity
        {
            String name;
            Integer year;

            public Entity(final Integer year)
            {
                this.year = year;
                this.name = year + "";
            }

        }

        class StringIndexer extends IndexerString.Abstract<Entity>
        {
            @Override
            protected String getString(final Entity entity)
            {
                return entity.name;
            }
        }

        class IntegerIndexer extends IndexerInteger.Abstract<Entity>
        {
			@Override
			protected Integer getInteger(Entity entity)
			{
                return entity.year;
			}
        }

        final GigaMap<Entity>       gigaMap = GigaMap.New();
        final BitmapIndices<Entity> indices = gigaMap.index().bitmap();
        final StringIndexer         indexer = new StringIndexer();
        final IntegerIndexer integerIndexer = new IntegerIndexer();
        indices.add(indexer);
        indices.add(integerIndexer);

        gigaMap.add(new Entity(1000));
        gigaMap.add(new Entity(1001));
        gigaMap.add(new Entity(1002));

        assertEquals(0, gigaMap.replace(gigaMap.get(0), new Entity(1003)));
        assertEquals(1, gigaMap.replace(gigaMap.get(1), new Entity(1004)));
        assertEquals(2, gigaMap.replace(gigaMap.get(2), new Entity(1005)));

        assertEquals(3, gigaMap.size());
        assertEquals(1003, gigaMap.get(0).year);
        
        final Entity first = gigaMap.get(0);
        assertThrows(IllegalArgumentException.class, () -> gigaMap.replace(first, first));
    }

    @Test
    void updateTest()
    {
        class Entity
        {
            String name;
            Integer year;

            public Entity(final Integer year)
            {
                this.year = year;
                this.name = year + "";
            }

        }

        class StringIndexer extends IndexerString.Abstract<Entity>
        {
            @Override
        	protected String getString(final Entity entity)
            {
                return entity.name;
            }
        }

        final GigaMap<Entity> gigaMap = GigaMap.New();
        final StringIndexer indexer = new StringIndexer();
        gigaMap.index().bitmap().add(indexer);

        gigaMap.add(new Entity(1000));
        gigaMap.add(new Entity(1001));
        gigaMap.add(new Entity(1002));

        final Entity update = gigaMap.update(gigaMap.get(0), entity -> entity.year = 1003);
        assertEquals(1003, update.year);
        gigaMap.update(gigaMap.get(1), entity -> entity.year = 1004);
        gigaMap.update(gigaMap.get(2), entity -> entity.year = 1005);

        assertEquals(3, gigaMap.size());
        assertEquals(1003, gigaMap.get(0).year);
    }

    @Test
    void removeAllTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        final StringIndexer indexer = new StringIndexer();
        gigaMap.index().bitmap().add(indexer);

        gigaMap.addAll("1000", "1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009");

        gigaMap.removeAll();

        assertEquals(0, gigaMap.size());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertEquals(0, gigaMap.size());
            gigaMap.addAll("2000", "2001", "2002", "2003", "2004", "2005", "2006", "2007", "2008", "2009");
            gigaMap.store();
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertEquals(10, gigaMap2.size());
            gigaMap2.removeAll();
            assertEquals(0, gigaMap2.size());
            manager.storeRoot();
            assertEquals(0, gigaMap2.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertEquals(0, gigaMap2.size());
        }

    }

    @Test
    void removeAllTest_thousandsOfItems()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        final StringIndexer indexer = new StringIndexer();
        gigaMap.index().bitmap().add(indexer);

        final List<String> values = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            values.add(String.valueOf(i));
        }
        gigaMap.addAll(values);


        gigaMap.removeAll();

        assertEquals(0, gigaMap.size());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            assertEquals(0, gigaMap.size());
            gigaMap.addAll(values);
            gigaMap.store();
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertEquals(gigaMap.size(), gigaMap2.size());

            String s = gigaMap2.query(indexer.is("20")).findFirst().get();
            assertEquals("20", s);

            gigaMap2.removeAll();
            assertEquals(0, gigaMap2.size());
            manager.storeRoot();
            assertEquals(0, gigaMap2.size());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertEquals(0, gigaMap2.size());
        }

    }

    @Test
    void indexTest()
    {
        class Entity
        {
            String name;
            Integer year;

            public Entity(final Integer year)
            {
                this.year = year;
                this.name = year + "";
            }

        }

        class StringIndexer extends IndexerString.Abstract<Entity>
        {
            @Override
        	protected String getString(final Entity entity)
            {
                return entity.name;
            }
        }


        final GigaMap<Entity> gigaMap = GigaMap.New();
        final StringIndexer indexer = new StringIndexer();
        gigaMap.index().bitmap().add(indexer);

        gigaMap.add(new Entity(1000));
        gigaMap.add(new Entity(1001));
        gigaMap.add(new Entity(1002));

        final GigaIndices<Entity> index = gigaMap.index();
        assertNotNull(index);
    }

    static class StringIndexer extends IndexerString.Abstract<String>
    {
    	@Override
    	protected String getString(final String entity)
    	{
            return entity;
        }
    }

    @Test
    void toStringTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        assertEquals("[]", gigaMap.toString(5));

        gigaMap.addAll("1000", "1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009");
        assertEquals("[1000, 1001, 1002, 1003, 1004]", gigaMap.toString(5));

        gigaMap.removeAll();
        assertEquals("[]", gigaMap.toString(5));
    }

    @Test
    void toStringMaxLimitTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();

        gigaMap.addAll("1000", "1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009");
        assertEquals("[1000, 1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009]", gigaMap.toString(Integer.MAX_VALUE));

        gigaMap.removeAll();
        assertEquals("[]", gigaMap.toString(5));
    }

    @Test
    void toStringLimitOffset()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        gigaMap.addAll("1000", "1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009");
        assertEquals("[1005, 1006]", gigaMap.toString(5, 2));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap2 = (GigaMap<String>) manager.root();
            assertEquals("[1005, 1006]", gigaMap2.toString(5, 2));
        }
    }

    @Test
    void toString_withZeroOffset_returnsFirstElements() {
        final GigaMap<String> gigaMap = GigaMap.New();
        gigaMap.addAll("1000", "1001", "1002", "1003", "1004");
        assertEquals("[1000, 1001]", gigaMap.toString(0, 2));
    }
}

