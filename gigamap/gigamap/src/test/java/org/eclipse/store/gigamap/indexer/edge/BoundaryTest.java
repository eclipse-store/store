package org.eclipse.store.gigamap.indexer.edge;

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
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BoundaryTest
{

    @TempDir
    Path tempDir;

    private final IdIndex idIndex = new IdIndex();

    @Test
    void test_1024_elements()
    {
        GigaMap<Item> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(idIndex);

        for (int i = 0; i <= 1024; i++) {
            gigaMap.add(new Item(i, "Item " + i));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            Item item = gigaMap.query(idIndex.is(1024)).findFirst().get();
            assertEquals(1024, item.id());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<Item> loadedGigaMap = (GigaMap<Item>) manager.root();
            Item item = loadedGigaMap.query(idIndex.is(1024)).findFirst().get();
            assertEquals(1024, item.id());
        }
    }

    @Test
    void test_65536_elements()
    {
        GigaMap<Item> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(idIndex);

        for (int i = 0; i <= 65536; i++) {
            gigaMap.add(new Item(i, "Item " + i));
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
            Item item = gigaMap.query(idIndex.is(65536)).findFirst().get();
            assertEquals(65536, item.id());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<Item> loadedGigaMap = (GigaMap<Item>) manager.root();
            Item item = loadedGigaMap.query(idIndex.is(65536)).findFirst().get();
            assertEquals(65536, item.id());
        }
    }

    private static class IdIndex extends IndexerInteger.Abstract<Item>
    {
    	@Override
    	protected Integer getInteger(Item entity)
        {
            return entity.id();
        }
    }


    private static class Item
    {
        private final int id;
        private final String name;

        public Item(int id, String name)
        {
            this.id = id;
            this.name = name;
        }

        public int id()
        {
            return id;
        }

        public String name()
        {
            return name;
        }

        @Override
        public String toString()
        {
            return "Item [id=" + id + ", name=" + name + "]";
        }
    }
}
