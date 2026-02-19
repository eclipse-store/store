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

import org.eclipse.store.gigamap.types.BinaryIndexerLong;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BinaryIndexerLongTest
{

    @TempDir
    Path tempDir;

    @Test
    void binaryIndexFloatTest()
    {
        LongBinaryIndexer indexer = new LongBinaryIndexer();

        GigaMap<LongBinaryIndexerPojo> map = GigaMap.<LongBinaryIndexerPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new LongBinaryIndexerPojo(1L));
        map.add(new LongBinaryIndexerPojo(-3L));
        map.add(new LongBinaryIndexerPojo(3L));
        map.add(new LongBinaryIndexerPojo(0L));

        for(long value : new long[]{1L, -3L, 3L, 0L})
        {
            List<LongBinaryIndexerPojo> list = map.query(indexer.is(value)).toList();
            assertEquals(1, list.size());
            assertEquals(value, list.get(0).getLongValue());
        }

        assertThrows(IllegalArgumentException.class, () ->
            map.add(new LongBinaryIndexerPojo(Long.MAX_VALUE)));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {

        }

        GigaMap<LongBinaryIndexerPojo> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, tempDir)) {
            assertEquals(4, newMap.size());

            for(long value : new long[]{1L, -3L, 3L, 0L})
            {
                List<LongBinaryIndexerPojo> newList = newMap.query(indexer.is(value)).toList();
                assertEquals(1, newList.size());
                assertEquals(value, newList.get(0).getLongValue());
            }

            newMap.add(new LongBinaryIndexerPojo(100L));
            newMap.store();
        }

        GigaMap<LongBinaryIndexerPojo> newMap2 = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap2, tempDir)) {
            assertEquals(5, newMap2.size());
            List<LongBinaryIndexerPojo> newList = newMap2.query(indexer.is(100L)).toList();
            assertEquals(1, newList.size());
            assertEquals(100L, newList.get(0).getLongValue());

            List<LongBinaryIndexerPojo> list1 = newMap2.query(indexer.not(100L)).toList();
            assertEquals(4, list1.size());
        }
    }

    static class LongBinaryIndexer extends  BinaryIndexerLong.Abstract<LongBinaryIndexerPojo>
    {

        @Override
        protected Long getLong(LongBinaryIndexerPojo entity)
        {
            return entity.getLongValue();
        }
    }

    static class LongBinaryIndexerPojo
    {
        private Long longValue;

        public LongBinaryIndexerPojo(Long longValue)
        {
            this.longValue = longValue;
        }

        public Long getLongValue()
        {
            return longValue;
        }

        public void setLongValue(Long longValue)
        {
            this.longValue = longValue;
        }

        @Override
        public String toString()
        {
            return "LongBinaryIndexerPojo{" +
                    "longValue=" + longValue +
                    '}';
        }
    }

}
