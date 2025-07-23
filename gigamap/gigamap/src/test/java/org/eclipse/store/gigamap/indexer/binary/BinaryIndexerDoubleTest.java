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

import org.eclipse.store.gigamap.types.BinaryIndexerDouble;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BinaryIndexerDoubleTest
{
    @TempDir
    Path tempDir;

    static DoubleBinaryIndexer indexer = new DoubleBinaryIndexer();

    @Test
    void binaryIndexDoubleTest()
    {
        GigaMap<DoubleBinaryIndexerPojo> map = GigaMap.<DoubleBinaryIndexerPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new DoubleBinaryIndexerPojo(1.0));
        map.add(new DoubleBinaryIndexerPojo(2.0));
        map.add(new DoubleBinaryIndexerPojo(3.0));

        List<DoubleBinaryIndexerPojo> list = map.query(indexer.is(1.0)).toList();
        assertEquals(1, list.size());
        assertEquals(1.0, list.get(0).getDoubleValue());

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {

        }

        GigaMap<DoubleBinaryIndexerPojo> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, tempDir)) {
            assertEquals(3, newMap.size());

            List<DoubleBinaryIndexerPojo> newList = newMap.query(indexer.is(1.0)).toList();
            assertEquals(1, newList.size());
            assertEquals(1.0, newList.get(0).getDoubleValue());

            newMap.add(new DoubleBinaryIndexerPojo(Double.MAX_VALUE));
            newMap.store();
        }

        GigaMap<DoubleBinaryIndexerPojo> newMap2 = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap2, tempDir)) {
            assertEquals(4, newMap2.size());

            List<DoubleBinaryIndexerPojo> newList = newMap2.query(indexer.is(Double.MAX_VALUE)).toList();
            assertEquals(1, newList.size());
            assertEquals(Double.MAX_VALUE, newList.get(0).getDoubleValue());

            List<DoubleBinaryIndexerPojo> list1 = newMap2.query(indexer.not(Double.MAX_VALUE)).toList();
            assertEquals(3, list1.size());

            List<DoubleBinaryIndexerPojo> list2 = newMap2.query(indexer.in(Double.MAX_VALUE, 1.0)).toList();
            assertEquals(2, list2.size());
            assertEquals(Double.MAX_VALUE, list2.get(1).getDoubleValue());



        }

    }


    static class DoubleBinaryIndexer  extends BinaryIndexerDouble.Abstract<DoubleBinaryIndexerPojo>
    {
        @Override
        protected Double getDouble(DoubleBinaryIndexerPojo entity)
        {
            return entity.getDoubleValue();
        }
    }

    static class DoubleBinaryIndexerPojo
    {
        private Double doubleValue;

        public DoubleBinaryIndexerPojo(Double doubleValue)
        {
            this.doubleValue = doubleValue;
        }

        public Double getDoubleValue()
        {
            return doubleValue;
        }

        @Override
        public String toString()
        {
            return "DoubleBinaryIndexerPojo{" +
                "doubleValue=" + doubleValue +
                '}';
        }
    }
}
