package org.eclipse.store.gigamap.misc;

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

import org.eclipse.serializer.hashing.XHashing;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EqualatorStoreTest
{

    @TempDir
    Path storagePath;


    @Test
    void testEquals()
    {
        Item item1 = new Item("item");
        Item item2 = new Item("item");

        GigaMap<Item> map = GigaMap.New(XHashing.hashEqualityValue());
        map.index().bitmap().add(new ItemIndexer());

        map.add(item1);
        map.add(item2);
        map.remove(new Item("item"));
        map.remove(new Item("item"));
        assertEquals(0, map.size());

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(map, storagePath))
        {
            map.add(item1);
            map.add(item2);
            map.remove(new Item("item"));
            map.remove(new Item("item"));
            assertEquals(0, map.size());
            map.add(item1);
            map.add(item2);
            assertEquals(2, map.size());
            map.store();
        }

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(storagePath))
        {
            GigaMap<Item> loaded = (GigaMap<Item>) storage.root();
            assertEquals(2, loaded.size());
            loaded.remove(item1);
            loaded.remove(item2);
            assertEquals(0, loaded.size());
        }
    }

    private static class ItemIndexer extends IndexerString.Abstract<Item>
    {
		@Override
		protected String getString(Item entity)
        {
            return entity.getValue();
		}
    }

    private static class Item {
        private final String value;

        public Item(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            return Objects.equals(value, item.value);
        }

        @Override
        public int hashCode()
        {
            return 126454566;
        }
    }
}
