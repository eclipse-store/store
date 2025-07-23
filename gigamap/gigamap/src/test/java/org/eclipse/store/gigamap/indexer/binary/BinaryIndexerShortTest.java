package org.eclipse.store.gigamap.indexer.binary;

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

import org.eclipse.store.gigamap.types.BinaryIndexerShort;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BinaryIndexerShortTest
{

    @TempDir
    Path tempDir;

    @Test
    void binaryIndexFloatTest()
    {
        ShortBinaryIndexer indexer = new ShortBinaryIndexer();

        GigaMap<ShortBinaryIndexerPojo> map = GigaMap.<ShortBinaryIndexerPojo>Builder()
                .withBitmapIdentityIndex(indexer)
                .build();

        map.add(new ShortBinaryIndexerPojo((short) 1));
        map.add(new ShortBinaryIndexerPojo((short) 2));
        map.add(new ShortBinaryIndexerPojo((short) 3));

        List<ShortBinaryIndexerPojo> list = map.query(indexer.is(1L)).toList();
        assertEquals(1, list.size());
        assertEquals((short) 1, list.get(0).getShortValue());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {

        }

        GigaMap<ShortBinaryIndexerPojo> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, tempDir)) {
            assertEquals(3, newMap.size());
            List<ShortBinaryIndexerPojo> newList = newMap.query(indexer.is(1L)).toList();
            assertEquals(1, newList.size());
            assertEquals((short) 1, newList.get(0).getShortValue());
            newMap.add(new ShortBinaryIndexerPojo(Short.MAX_VALUE));
            newMap.store();
        }

        GigaMap<ShortBinaryIndexerPojo> newMap2 = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap2, tempDir)) {
            assertEquals(4, newMap2.size());
            List<ShortBinaryIndexerPojo> newList = newMap2.query(indexer.is(Short.MAX_VALUE)).toList();
            assertEquals(1, newList.size());
            assertEquals(Short.MAX_VALUE, newList.get(0).getShortValue());

            List<ShortBinaryIndexerPojo> list1 = newMap2.query(indexer.not(Short.MAX_VALUE)).toList();
            assertEquals(3, list1.size());
        }
    }

    static class ShortBinaryIndexer extends BinaryIndexerShort.Abstract<ShortBinaryIndexerPojo>
    {

        @Override
        protected Short getShort(ShortBinaryIndexerPojo entity)
        {
            return entity.getShortValue();
        }
    }


    static class ShortBinaryIndexerPojo
    {
        private Short shortValue;

        public ShortBinaryIndexerPojo(Short shortValue)
        {
            this.shortValue = shortValue;
        }

        public Short getShortValue()
        {
            return shortValue;
        }

        public void setShortValue(Short shortValue)
        {
            this.shortValue = shortValue;
        }

        @Override
        public String toString()
        {
            return "ShortBinaryIndexerPojo{" +
                    "shortValue=" + shortValue +
                    '}';
        }
    }

}
