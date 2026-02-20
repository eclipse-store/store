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

import org.eclipse.store.gigamap.types.ByteIndexerLong;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ByteIndexerLongTest
{
    @TempDir
    Path tempDir;

    @Test
    void equalityQueries()
    {
        final LongValueIndexer indexer = new LongValueIndexer();

        final GigaMap<LongPojo> map = GigaMap.<LongPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new LongPojo(Long.MIN_VALUE));
        map.add(new LongPojo(-1L));
        map.add(new LongPojo(0L));
        map.add(new LongPojo(1L));
        map.add(new LongPojo(Long.MAX_VALUE));

        assertEquals(1, map.query(indexer.is(0L)).toList().size());
        assertEquals(4, map.query(indexer.not(0L)).toList().size());
        assertEquals(2, map.query(indexer.in(-1L, 1L)).toList().size());
        assertEquals(3, map.query(indexer.notIn(-1L, 1L)).toList().size());
    }

    @Test
    void rangeQueries()
    {
        final LongValueIndexer indexer = new LongValueIndexer();

        final GigaMap<LongPojo> map = GigaMap.<LongPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new LongPojo(Long.MIN_VALUE));
        map.add(new LongPojo(-1L));
        map.add(new LongPojo(0L));
        map.add(new LongPojo(1L));
        map.add(new LongPojo(Long.MAX_VALUE));

        // lessThan
        assertEquals(2, map.query(indexer.lessThan(0L)).toList().size());
        assertEquals(0, map.query(indexer.lessThan(Long.MIN_VALUE)).toList().size());

        // lessThanEqual
        assertEquals(3, map.query(indexer.lessThanEqual(0L)).toList().size());
        assertEquals(1, map.query(indexer.lessThanEqual(Long.MIN_VALUE)).toList().size());

        // greaterThan
        assertEquals(2, map.query(indexer.greaterThan(0L)).toList().size());
        assertEquals(0, map.query(indexer.greaterThan(Long.MAX_VALUE)).toList().size());

        // greaterThanEqual
        assertEquals(3, map.query(indexer.greaterThanEqual(0L)).toList().size());
        assertEquals(1, map.query(indexer.greaterThanEqual(Long.MAX_VALUE)).toList().size());

        // between
        assertEquals(3, map.query(indexer.between(-1L, 1L)).toList().size());
        assertEquals(5, map.query(indexer.between(Long.MIN_VALUE, Long.MAX_VALUE)).toList().size());
    }

    @Test
    void persistenceRoundTrip()
    {
        final LongValueIndexer indexer = new LongValueIndexer();

        final GigaMap<LongPojo> map = GigaMap.<LongPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new LongPojo(Long.MIN_VALUE));
        map.add(new LongPojo(0L));
        map.add(new LongPojo(Long.MAX_VALUE));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir))
        {
            assertEquals(3, map.size());
        }

        final GigaMap<LongPojo> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, tempDir))
        {
            assertEquals(3, newMap.size());

            final List<LongPojo> result = newMap.query(indexer.is(0L)).toList();
            assertEquals(1, result.size());
            assertEquals(0L, result.get(0).getValue());

            assertEquals(1, newMap.query(indexer.greaterThan(0L)).toList().size());
            assertEquals(1, newMap.query(indexer.lessThan(0L)).toList().size());
        }
    }


    static class LongValueIndexer extends ByteIndexerLong.Abstract<LongPojo>
    {
        @Override
        protected Long getLong(final LongPojo entity)
        {
            return entity.getValue();
        }
    }

    static class LongPojo
    {
        private final Long value;

        LongPojo(final Long value)
        {
            this.value = value;
        }

        public Long getValue()
        {
            return this.value;
        }
    }

}
