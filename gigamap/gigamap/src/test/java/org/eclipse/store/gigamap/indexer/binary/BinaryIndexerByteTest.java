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

import org.eclipse.store.gigamap.types.BinaryIndexerByte;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BinaryIndexerByteTest
{
    @TempDir
    Path tempDir;

    static ByteBinaryIndexer indexer = new ByteBinaryIndexer();

    @Test
    void binaryIndexerByteTest()
    {
        GigaMap<BinaryIndexerBytePojo> map = GigaMap.<BinaryIndexerBytePojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new BinaryIndexerBytePojo((Byte.valueOf("1"))));
        map.add(new BinaryIndexerBytePojo((Byte.valueOf("2"))));
        map.add(new BinaryIndexerBytePojo((Byte.valueOf("3"))));


        List<BinaryIndexerBytePojo> list = map.query(indexer.is(Byte.valueOf("1"))).toList();
        assertEquals(1, list.size());
        assertEquals(list.get(0).getByteValue(), Byte.valueOf("1"));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {

        }


        GigaMap<BinaryIndexerBytePojo> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, tempDir)) {
            assertEquals(3, newMap.size());

            List<BinaryIndexerBytePojo> newList = newMap.query(indexer.is(Byte.valueOf("1"))).toList();
            assertEquals(1, newList.size());
            assertEquals(Byte.valueOf("1"), newList.get(0).getByteValue() );

            newMap.add(new BinaryIndexerBytePojo(Byte.MAX_VALUE));
            newMap.store();
        }

        GigaMap<BinaryIndexerBytePojo> newMap2 = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap2, tempDir)) {
            assertEquals(4, newMap2.size());

            List<BinaryIndexerBytePojo> newList = newMap2.query(indexer.is(Byte.MAX_VALUE)).toList();
            assertEquals(1, newList.size());
            assertEquals(Byte.MAX_VALUE, newList.get(0).getByteValue());

            newMap2.removeAll();
            newMap2.store();
        }

        GigaMap<BinaryIndexerBytePojo> newMap3 = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap3, tempDir)) {
            assertEquals(0, newMap3.size());
            List<BinaryIndexerBytePojo> newList = newMap3.query(indexer.is(Byte.MAX_VALUE)).toList();
            assertTrue(newList.isEmpty());
        }


    }

    private static class ByteBinaryIndexer extends BinaryIndexerByte.Abstract<BinaryIndexerBytePojo>
    {

        @Override
        protected Byte getByte(BinaryIndexerBytePojo entity)
        {
            return entity.getByteValue();
        }
    }


    private static class BinaryIndexerBytePojo
    {
        private Byte byteValue;


        public BinaryIndexerBytePojo(Byte byteValue)
        {
            this.byteValue = byteValue;
        }

        public Byte getByteValue()
        {
            return byteValue;
        }

        public void setByteValue(Byte byteValue)
        {
            this.byteValue = byteValue;
        }
    }
}
