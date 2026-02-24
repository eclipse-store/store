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

import org.eclipse.store.gigamap.types.ByteIndexerInteger;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ByteIndexerIntegerTest
{
    @TempDir
    Path tempDir;

    @Test
    void equalityQueries()
    {
        final IntValueIndexer indexer = new IntValueIndexer();

        final GigaMap<IntPojo> map = GigaMap.<IntPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new IntPojo(Integer.MIN_VALUE));
        map.add(new IntPojo(-1));
        map.add(new IntPojo(0));
        map.add(new IntPojo(1));
        map.add(new IntPojo(Integer.MAX_VALUE));

        assertEquals(1, map.query(indexer.is(0)).toList().size());
        assertEquals(4, map.query(indexer.not(0)).toList().size());
        assertEquals(2, map.query(indexer.in(-1, 1)).toList().size());
        assertEquals(3, map.query(indexer.notIn(-1, 1)).toList().size());
    }

    @Test
    void rangeQueries()
    {
        final IntValueIndexer indexer = new IntValueIndexer();

        final GigaMap<IntPojo> map = GigaMap.<IntPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new IntPojo(Integer.MIN_VALUE));
        map.add(new IntPojo(-1));
        map.add(new IntPojo(0));
        map.add(new IntPojo(1));
        map.add(new IntPojo(Integer.MAX_VALUE));

        // lessThan
        assertEquals(2, map.query(indexer.lessThan(0)).toList().size());
        assertEquals(0, map.query(indexer.lessThan(Integer.MIN_VALUE)).toList().size());

        // lessThanEqual
        assertEquals(3, map.query(indexer.lessThanEqual(0)).toList().size());
        assertEquals(1, map.query(indexer.lessThanEqual(Integer.MIN_VALUE)).toList().size());

        // greaterThan
        assertEquals(2, map.query(indexer.greaterThan(0)).toList().size());
        assertEquals(0, map.query(indexer.greaterThan(Integer.MAX_VALUE)).toList().size());

        // greaterThanEqual
        assertEquals(3, map.query(indexer.greaterThanEqual(0)).toList().size());
        assertEquals(1, map.query(indexer.greaterThanEqual(Integer.MAX_VALUE)).toList().size());

        // between
        assertEquals(3, map.query(indexer.between(-1, 1)).toList().size());
        assertEquals(5, map.query(indexer.between(Integer.MIN_VALUE, Integer.MAX_VALUE)).toList().size());
    }

    @Test
    void rangeQueriesWithNearbyValues()
    {
        final IntValueIndexer indexer = new IntValueIndexer();

        final GigaMap<IntPojo> map = GigaMap.<IntPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new IntPojo(100));
        map.add(new IntPojo(200));
        map.add(new IntPojo(300));
        map.add(new IntPojo(400));
        map.add(new IntPojo(500));

        assertEquals(2, map.query(indexer.lessThan(300)).toList().size());
        assertEquals(3, map.query(indexer.lessThanEqual(300)).toList().size());
        assertEquals(2, map.query(indexer.greaterThan(300)).toList().size());
        assertEquals(3, map.query(indexer.greaterThanEqual(300)).toList().size());
        assertEquals(3, map.query(indexer.between(200, 400)).toList().size());
    }

    @Test
    void persistenceRoundTrip()
    {
        final IntValueIndexer indexer = new IntValueIndexer();

        final GigaMap<IntPojo> map = GigaMap.<IntPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new IntPojo(Integer.MIN_VALUE));
        map.add(new IntPojo(0));
        map.add(new IntPojo(Integer.MAX_VALUE));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir))
        {
            assertEquals(3, map.size());
        }

        final GigaMap<IntPojo> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, tempDir))
        {
            assertEquals(3, newMap.size());

            final List<IntPojo> result = newMap.query(indexer.is(0)).toList();
            assertEquals(1, result.size());
            assertEquals(0, result.get(0).getValue());

            assertEquals(1, newMap.query(indexer.greaterThan(0)).toList().size());
            assertEquals(1, newMap.query(indexer.lessThan(0)).toList().size());

            assertEquals(3, newMap.query(indexer.between(Integer.MIN_VALUE, Integer.MAX_VALUE)).toList().size());
        }
    }


    static class IntValueIndexer extends ByteIndexerInteger.Abstract<IntPojo>
    {
        @Override
        protected Integer getInteger(final IntPojo entity)
        {
            return entity.getValue();
        }
    }

    static class IntPojo
    {
        private final Integer value;

        IntPojo(final Integer value)
        {
            this.value = value;
        }

        public Integer getValue()
        {
            return this.value;
        }
    }

}
