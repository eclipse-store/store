package org.eclipse.store.gigamap.indexer.byte_;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.types.ByteIndexerByte;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ByteIndexerByteTest
{
    @TempDir
    Path tempDir;

    @Test
    void equalityQueries()
    {
        final ByteValueIndexer indexer = new ByteValueIndexer();

        final GigaMap<BytePojo> map = GigaMap.<BytePojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new BytePojo((byte) -128));
        map.add(new BytePojo((byte) -1));
        map.add(new BytePojo((byte) 0));
        map.add(new BytePojo((byte) 1));
        map.add(new BytePojo((byte) 127));

        assertEquals(1, map.query(indexer.is((byte) 0)).toList().size());
        assertEquals(4, map.query(indexer.not((byte) 0)).toList().size());
        assertEquals(2, map.query(indexer.in((byte) -1, (byte) 1)).toList().size());
        assertEquals(3, map.query(indexer.notIn((byte) -1, (byte) 1)).toList().size());
    }

    @Test
    void rangeQueries()
    {
        final ByteValueIndexer indexer = new ByteValueIndexer();

        final GigaMap<BytePojo> map = GigaMap.<BytePojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new BytePojo((byte) -128));
        map.add(new BytePojo((byte) -1));
        map.add(new BytePojo((byte) 0));
        map.add(new BytePojo((byte) 1));
        map.add(new BytePojo((byte) 127));

        // lessThan
        assertEquals(2, map.query(indexer.lessThan((byte) 0)).toList().size());
        assertEquals(0, map.query(indexer.lessThan(Byte.MIN_VALUE)).toList().size());

        // lessThanEqual
        assertEquals(3, map.query(indexer.lessThanEqual((byte) 0)).toList().size());
        assertEquals(1, map.query(indexer.lessThanEqual(Byte.MIN_VALUE)).toList().size());

        // greaterThan
        assertEquals(2, map.query(indexer.greaterThan((byte) 0)).toList().size());
        assertEquals(0, map.query(indexer.greaterThan(Byte.MAX_VALUE)).toList().size());

        // greaterThanEqual
        assertEquals(3, map.query(indexer.greaterThanEqual((byte) 0)).toList().size());
        assertEquals(1, map.query(indexer.greaterThanEqual(Byte.MAX_VALUE)).toList().size());

        // between
        assertEquals(3, map.query(indexer.between((byte) -1, (byte) 1)).toList().size());
        assertEquals(5, map.query(indexer.between(Byte.MIN_VALUE, Byte.MAX_VALUE)).toList().size());
    }

    @Test
    void persistenceRoundTrip()
    {
        final ByteValueIndexer indexer = new ByteValueIndexer();

        final GigaMap<BytePojo> map = GigaMap.<BytePojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new BytePojo((byte) -128));
        map.add(new BytePojo((byte) 0));
        map.add(new BytePojo((byte) 127));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir))
        {
            assertEquals(3, map.size());
        }

        final GigaMap<BytePojo> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, tempDir))
        {
            assertEquals(3, newMap.size());

            final List<BytePojo> result = newMap.query(indexer.is((byte) 0)).toList();
            assertEquals(1, result.size());
            assertEquals((byte) 0, result.get(0).getValue());

            assertEquals(1, newMap.query(indexer.greaterThan((byte) 0)).toList().size());
            assertEquals(1, newMap.query(indexer.lessThan((byte) 0)).toList().size());
        }
    }


    static class ByteValueIndexer extends ByteIndexerByte.Abstract<BytePojo>
    {
        @Override
        protected Byte getByte(final BytePojo entity)
        {
            return entity.getValue();
        }
    }

    static class BytePojo
    {
        private final Byte value;

        BytePojo(final Byte value)
        {
            this.value = value;
        }

        public Byte getValue()
        {
            return this.value;
        }
    }

}
