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

import org.eclipse.store.gigamap.types.BinaryIndexerFloat;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BinaryIndexerFloatTest
{

    @TempDir
    Path tempDir;

    @Test
    void binaryIndexFloatTest()
    {
        FloatBinaryIndexer indexer = new FloatBinaryIndexer();

        GigaMap<FloatBinaryIndexerPojo> map = GigaMap.<FloatBinaryIndexerPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new FloatBinaryIndexerPojo(1.0f));
        map.add(new FloatBinaryIndexerPojo(-1.5f));
        map.add(new FloatBinaryIndexerPojo(3.0f));
        map.add(new FloatBinaryIndexerPojo(0.0f));

        for(float value : new float[]{1.0f, -1.5f, 3.0f, 0.0f})
        {
            List<FloatBinaryIndexerPojo> list = map.query(indexer.is(value)).toList();
            assertEquals(1, list.size());
            assertEquals(value, list.get(0).getFloatValue());
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {

        }

        GigaMap<FloatBinaryIndexerPojo> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, tempDir)) {
            assertEquals(4, newMap.size());

            for(float value : new float[]{1.0f, -1.5f, 3.0f, 0.0f})
            {
                List<FloatBinaryIndexerPojo> newList = newMap.query(indexer.is(value)).toList();
                assertEquals(1, newList.size());
                assertEquals(value, newList.get(0).getFloatValue());
            }

            newMap.add(new FloatBinaryIndexerPojo(Float.MAX_VALUE));
            newMap.store();
        }

        GigaMap<FloatBinaryIndexerPojo> newMap2 = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap2, tempDir)) {
            assertEquals(5, newMap2.size());

            List<FloatBinaryIndexerPojo> newList = newMap2.query(indexer.is(Float.MAX_VALUE)).toList();
            assertEquals(1, newList.size());
            assertEquals(Float.MAX_VALUE, newList.get(0).getFloatValue());

            List<FloatBinaryIndexerPojo> list1 = newMap2.query(indexer.not(Float.MAX_VALUE)).toList();
            assertEquals(4, list1.size());
        }
    }

    static class FloatBinaryIndexer extends  BinaryIndexerFloat.Abstract<FloatBinaryIndexerPojo>
    {
        @Override
        protected Float getFloat(FloatBinaryIndexerPojo entity)
        {
            return entity.getFloatValue();
        }
    }

    static class FloatBinaryIndexerPojo
    {
        private final float floatValue;

        public FloatBinaryIndexerPojo(final float floatValue)
        {
            this.floatValue = floatValue;
        }

        public float getFloatValue()
        {
            return this.floatValue;
        }

        @Override
        public String toString()
        {
            return "FloatBinaryIndexerPojo{" +
                    "floatValue=" + floatValue +
                    '}';
        }
    }

}
