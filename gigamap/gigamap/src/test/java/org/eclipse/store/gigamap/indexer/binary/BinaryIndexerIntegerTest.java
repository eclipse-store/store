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

import org.eclipse.store.gigamap.types.BinaryIndexerInteger;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BinaryIndexerIntegerTest
{

    @TempDir
    Path tempDir;

    @Test
    void binaryIndexIntegerTest()
    {
        IntegerBinaryIndexer indexer = new IntegerBinaryIndexer();

        GigaMap<IntegerBinaryIndexerPojo> map = GigaMap.<IntegerBinaryIndexerPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new IntegerBinaryIndexerPojo(1));
        map.add(new IntegerBinaryIndexerPojo(2));
        map.add(new IntegerBinaryIndexerPojo(3));

        List<IntegerBinaryIndexerPojo> list = map.query(indexer.is(1)).toList();
        assertEquals(1, list.size());
        assertEquals(1, list.get(0).getIntValue());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {

        }

        GigaMap<IntegerBinaryIndexerPojo> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, tempDir)) {
            assertEquals(3, newMap.size());

            List<IntegerBinaryIndexerPojo> newList = newMap.query(indexer.is(1)).toList();
            assertEquals(1, newList.size());
            assertEquals(1, newList.get(0).getIntValue());

            newMap.add(new IntegerBinaryIndexerPojo(Integer.MAX_VALUE));
            newMap.store();
        }

        GigaMap<IntegerBinaryIndexerPojo> newMap2 = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap2, tempDir)) {
            assertEquals(4, newMap2.size());

            List<IntegerBinaryIndexerPojo> newList = newMap2.query(indexer.is(Integer.MAX_VALUE)).toList();
            assertEquals(1, newList.size());
            assertEquals(Integer.MAX_VALUE, newList.get(0).getIntValue());

            List<IntegerBinaryIndexerPojo> list1 = newMap2.query(indexer.not(Integer.MAX_VALUE)).toList();
            assertEquals(3, list1.size());
        }
    }

    static class IntegerBinaryIndexer extends  BinaryIndexerInteger.Abstract<IntegerBinaryIndexerPojo>
    {

        @Override
        protected Integer getInteger(IntegerBinaryIndexerPojo entity)
        {
            return entity.getIntValue();
        }
    }

    static class IntegerBinaryIndexerPojo
    {
        private Integer intValue;

        public IntegerBinaryIndexerPojo(Integer intValue)
        {
            this.intValue = intValue;
        }

        public Integer getIntValue()
        {
            return intValue;
        }

        public void setIntValue(Integer intValue)
        {
            this.intValue = intValue;
        }

        @Override
        public String toString()
        {
            return "IntegerBinaryIndexerPojo{" +
                    "intValue=" + intValue +
                    '}';
        }
    }

}
