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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class CrudTest
{
    @TempDir
    Path tempDir;


    @SuppressWarnings("unchecked")
	@Test
    void addTestMap()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        assertEquals(0, gigaMap.size());
        for (int i = 0; i < 1000; i++)
        {
            gigaMap.add(String.valueOf(i));
        }
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, this.tempDir)) {
            assertEquals(1000, gigaMap.size());
            gigaMap.store();
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(this.tempDir)) {
            final GigaMap<String> root = (GigaMap<String>) storageManager.root();
            assertEquals(1000, root.size());
            root.add("1000");
            root.store();
            assertEquals(1001, root.size());
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(this.tempDir)) {
            final GigaMap<String> root = (GigaMap<String>) storageManager.root();
            assertEquals(1001, root.size());
        }

    }

    @Test
    void removeAllTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        for (int i = 0; i < 1000; i++)
        {
            gigaMap.add(String.valueOf(i));
        }
        gigaMap.removeAll();
        assertEquals(0, gigaMap.size());
        assertEquals(0, gigaMap.query().count());
    }

    @Test
    void buildTest()
    {
        class StringIndexer extends IndexerString.Abstract<String>
        {
            @Override
            protected String getString(final String entity)
            {
            	return entity;
            }
        }

        StringIndexer stringIndexer = new StringIndexer();
        GigaMap<String> gigaMap = GigaMap.<String>Builder().
                withBitmapIndex(stringIndexer).
                build();
        gigaMap.add("1000");
        gigaMap.remove("1000");
        assertEquals(0, gigaMap.size());
    }


    //https://github.com/microstream-one/microstream-private/issues/729
    @Test
    void addRemoveTest()
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
        gigaMap.index().bitmap().add(new StringIndexer());
        gigaMap.add("1000");
        gigaMap.remove("1000");
        assertEquals(0, gigaMap.size());
        assertTrue(gigaMap.isEmpty());
    }

    @Test
    void addRemove_withStorage_Test()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(new StringIndexer_addRemove_withStorage_Test());

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, this.tempDir)) {
            gigaMap.add("1000");
            gigaMap.add("1000");
            gigaMap.store();
            assertEquals(0, gigaMap.remove("1000"));
            assertEquals(1, gigaMap.remove("1000"));
        }
        Assertions.assertTrue(gigaMap.isEmpty());
    }

    static class StringIndexer_addRemove_withStorage_Test extends IndexerString.Abstract<String>
    {
		@Override
		protected String getString(final String entity)
		{
			return entity;
		}
    }

    @Test
    void addRemove_sameObjects_withStorage_Test()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(new StringIndexer_addRemove_withStorage_Test());
        gigaMap.add("1000");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, this.tempDir)) {
            gigaMap.add("100");
            gigaMap.replace("1000", "10001");
        }
    }

    @Test
    void updateTest()
    {
        class UpdateCrudTestObjectIndexer extends IndexerString.Abstract<CrudTestObject>
        {
			@Override
			protected String getString(final CrudTestObject entity)
			{
				return entity.s;
			}
        }

        final GigaMap<CrudTestObject> gigaMap = GigaMap.New();

        final UpdateCrudTestObjectIndexer index = new UpdateCrudTestObjectIndexer();
        gigaMap.index().bitmap().add(index);
        gigaMap.add(new CrudTestObject("1000"));
        gigaMap.add(new CrudTestObject("1500"));
        gigaMap.update(gigaMap.get(1), someObject -> someObject.setS("2000"));

        assertEquals("2000", gigaMap.get(1).s);
    }


    @Test
    void read_notExistingItemTest()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        gigaMap.add("1000");
        assertEquals(null, gigaMap.get(5));
    }

    @Test
    void replaceTest()
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
        gigaMap.index().bitmap().add(new StringIndexer());

        gigaMap.add("1000");
        gigaMap.replace("1000",  "10001");

        assertEquals("10001", gigaMap.get(0));
    }


    @Test
    void updateMethod()
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
        gigaMap.index().bitmap().add(new StringIndexer());

        gigaMap.add("1000");
        gigaMap.update("1000",  value -> value = "10001");

        assertEquals("1000", gigaMap.get(0), "original value will be not changed");
    }

    @Test
    void update_with_mutable_objectTest()
    {
        class UpdateCrudTestObjectIndexer extends IndexerString.Abstract<CrudTestObject>
        {
			@Override
			protected String getString(final CrudTestObject entity)
			{
                return entity.s;
			}
        }


        final GigaMap<CrudTestObject> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(new UpdateCrudTestObjectIndexer());

        final CrudTestObject c = new CrudTestObject("1000");

        gigaMap.add(c);
        gigaMap.update(c, crudTestObject -> {
            crudTestObject.setS(crudTestObject.s + "1");
        });
        assertEquals("10001", gigaMap.get(0).s);
    }

    @Test
    void update_viaApplyForObjectTest()
    {
        class UpdateCrudTestObjectIndexer extends IndexerString.Abstract<CrudTestObject>
        {
			@Override
			protected String getString(final CrudTestObject entity)
			{
                return entity.s;
			}
        }


        final GigaMap<CrudTestObject> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(new UpdateCrudTestObjectIndexer());

        final CrudTestObject c = new CrudTestObject("1000");

        gigaMap.add(c);
        gigaMap.apply(c, crudTestObject -> {
            crudTestObject.setS(crudTestObject.s + "1");
            return crudTestObject;
        });

        assertEquals("10001", gigaMap.get(0).s);
    }



    public static class CrudTestObject {
        String s;

        public CrudTestObject(final String s)
        {
            this.s = s;
        }

        public void setS(final String s)
        {
            this.s = s;
        }

        @Override
        public String toString()
        {
            return "CrudTestObject{" +
                    "s='" + this.s + '\'' +
                    '}';
        }
    }


    @Test
    public void testGigaMapOperations() {
        final GigaMap<String> gigaMap = GigaMap.New();
        // Test adding an element
        final long id = gigaMap.add("Hello");
        assertEquals(1, gigaMap.size());

        // Test retrieving the element
        String element = gigaMap.get(id);
        assertEquals("Hello", element);

        // Test removing the element
        final String removedElement = gigaMap.removeById(id);
        assertEquals("Hello", removedElement);
        assertEquals(0, gigaMap.size());

        // Test retrieving the element after removal
        element = gigaMap.peek(id);
        assertNull(element);
    }

    @Test
    void testDuplicateEntries() {
        final GigaMap<String> gigaMap = GigaMap.New();
        gigaMap.add("Hello");
        gigaMap.add("Hello");
        assertEquals(2, gigaMap.size());
    }

    @Test
    void testNullEntries() {
        final GigaMap<String> gigaMap = GigaMap.New();
        assertThrows(IllegalArgumentException.class, () -> gigaMap.add(null));
    }

    @Test
    void testLargeData() {
        final GigaMap<String> gigaMap = GigaMap.New();
        for (int i = 0; i < 10000; i++) {
            gigaMap.add("Hello" + i);
        }
        assertEquals(10000, gigaMap.size());
    }

    @Test
    void testEmptyMap() {
        final GigaMap<String> gigaMap = GigaMap.New();
        assertEquals(0, gigaMap.size());
    }

    @Test
    void testNonExistentEntries() {
        final GigaMap<String> gigaMap = GigaMap.New();
        gigaMap.add("Hello");
        assertEquals(null, gigaMap.get(1));
    }
}
