package org.eclipse.store.gigamap.indexer.byte_;

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

import org.eclipse.store.gigamap.types.ByteIndexerShort;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ByteIndexerShortTest
{
    @TempDir
    Path tempDir;

    @Test
    void equalityQueries()
    {
        final ShortValueIndexer indexer = new ShortValueIndexer();

        final GigaMap<ShortPojo> map = GigaMap.<ShortPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new ShortPojo(Short.MIN_VALUE));
        map.add(new ShortPojo((short) -1));
        map.add(new ShortPojo((short) 0));
        map.add(new ShortPojo((short) 1));
        map.add(new ShortPojo(Short.MAX_VALUE));

        assertEquals(1, map.query(indexer.is((short) 0)).toList().size());
        assertEquals(4, map.query(indexer.not((short) 0)).toList().size());
        assertEquals(2, map.query(indexer.in((short) -1, (short) 1)).toList().size());
        assertEquals(3, map.query(indexer.notIn((short) -1, (short) 1)).toList().size());
    }

    @Test
    void rangeQueries()
    {
        final ShortValueIndexer indexer = new ShortValueIndexer();

        final GigaMap<ShortPojo> map = GigaMap.<ShortPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new ShortPojo(Short.MIN_VALUE));
        map.add(new ShortPojo((short) -1));
        map.add(new ShortPojo((short) 0));
        map.add(new ShortPojo((short) 1));
        map.add(new ShortPojo(Short.MAX_VALUE));

        // lessThan
        assertEquals(2, map.query(indexer.lessThan((short) 0)).toList().size());
        assertEquals(0, map.query(indexer.lessThan(Short.MIN_VALUE)).toList().size());

        // lessThanEqual
        assertEquals(3, map.query(indexer.lessThanEqual((short) 0)).toList().size());
        assertEquals(1, map.query(indexer.lessThanEqual(Short.MIN_VALUE)).toList().size());

        // greaterThan
        assertEquals(2, map.query(indexer.greaterThan((short) 0)).toList().size());
        assertEquals(0, map.query(indexer.greaterThan(Short.MAX_VALUE)).toList().size());

        // greaterThanEqual
        assertEquals(3, map.query(indexer.greaterThanEqual((short) 0)).toList().size());
        assertEquals(1, map.query(indexer.greaterThanEqual(Short.MAX_VALUE)).toList().size());

        // between
        assertEquals(3, map.query(indexer.between((short) -1, (short) 1)).toList().size());
        assertEquals(5, map.query(indexer.between(Short.MIN_VALUE, Short.MAX_VALUE)).toList().size());
    }

    @Test
    void persistenceRoundTrip()
    {
        final ShortValueIndexer indexer = new ShortValueIndexer();

        final GigaMap<ShortPojo> map = GigaMap.<ShortPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new ShortPojo(Short.MIN_VALUE));
        map.add(new ShortPojo((short) 0));
        map.add(new ShortPojo(Short.MAX_VALUE));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir))
        {
            assertEquals(3, map.size());
        }

        final GigaMap<ShortPojo> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, tempDir))
        {
            assertEquals(3, newMap.size());

            final List<ShortPojo> result = newMap.query(indexer.is((short) 0)).toList();
            assertEquals(1, result.size());
            assertEquals((short) 0, result.get(0).getValue());

            assertEquals(1, newMap.query(indexer.greaterThan((short) 0)).toList().size());
            assertEquals(1, newMap.query(indexer.lessThan((short) 0)).toList().size());
        }
    }


    static class ShortValueIndexer extends ByteIndexerShort.Abstract<ShortPojo>
    {
        @Override
        protected Short getShort(final ShortPojo entity)
        {
            return entity.getValue();
        }
    }

    static class ShortPojo
    {
        private final Short value;

        ShortPojo(final Short value)
        {
            this.value = value;
        }

        public Short getValue()
        {
            return this.value;
        }
    }

}
