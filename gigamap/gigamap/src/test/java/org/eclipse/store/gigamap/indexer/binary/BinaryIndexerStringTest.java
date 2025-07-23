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

import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class BinaryIndexerStringTest
{

    @TempDir
    Path tempDir;


    @Test
    void binaryIndexStringTest()
    {
        StringBinaryIndexer indexer = new StringBinaryIndexer();

        GigaMap<StringBinaryIndexerPojo> map = GigaMap.<StringBinaryIndexerPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new StringBinaryIndexerPojo("one"));
        map.add(new StringBinaryIndexerPojo("two"));
        map.add(new StringBinaryIndexerPojo("three"));

        List<StringBinaryIndexerPojo> one = map.query(indexer.is("one")).toList();
        assertEquals(1, one.size());
        one.forEach(pojo -> {
            assertEquals("one",  pojo.getStringValue());
        });

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
            // Storage operations can be performed here
        }

        GigaMap<StringBinaryIndexerPojo> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, tempDir)) {
            assertEquals(3, newMap.size());

            List<StringBinaryIndexerPojo> newList = newMap.query(indexer.is("one")).toList();
            assertEquals(1, newList.size());
            newList.forEach(pojo -> {
                assertEquals("one", pojo.getStringValue());
            });

            newMap.add(new StringBinaryIndexerPojo("four"));
            newMap.store();
        }

        GigaMap<StringBinaryIndexerPojo> newMap2 = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap2, tempDir)) {
            assertEquals(4, newMap2.size());
            List<StringBinaryIndexerPojo> four1 = newMap2.query(indexer.is("four")).toList();
            four1.forEach(pojo -> {
                assertEquals("four", pojo.getStringValue());
            });

            List<StringBinaryIndexerPojo> four = newMap2.query(indexer.not("four")).toList();
            assertEquals(3, four.size());
            four.forEach(pojo -> {
                assertNotEquals("four", pojo.getStringValue());
            });

        }
    }

    static class StringBinaryIndexer extends BinaryIndexerString.Abstract<StringBinaryIndexerPojo>
    {
        @Override
        protected String getString(StringBinaryIndexerPojo entity)
        {
            return entity.getStringValue();
        }
    }

    static class StringBinaryIndexerPojo
    {
        private final String stringValue;

        public StringBinaryIndexerPojo(final String stringValue)
        {
            this.stringValue = stringValue;
        }

        public String getStringValue()
        {
            return this.stringValue;
        }

        @Override
        public String toString()
        {
            return "StringBinaryIndexerPojo{" +
                    "stringValue='" + stringValue + '\'' +
                    '}';
        }
    }
}
