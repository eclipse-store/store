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

import org.eclipse.store.gigamap.types.ByteIndexerDouble;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ByteIndexerDoubleTest
{
    @TempDir
    Path tempDir;

    @Test
    void equalityQueries()
    {
        final DoubleValueIndexer indexer = new DoubleValueIndexer();

        final GigaMap<DoublePojo> map = GigaMap.<DoublePojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new DoublePojo(-Double.MAX_VALUE));
        map.add(new DoublePojo(-1.0));
        map.add(new DoublePojo(0.0));
        map.add(new DoublePojo(1.0));
        map.add(new DoublePojo(Double.MAX_VALUE));

        assertEquals(1, map.query(indexer.is(0.0)).toList().size());
        assertEquals(4, map.query(indexer.not(0.0)).toList().size());
        assertEquals(2, map.query(indexer.in(-1.0, 1.0)).toList().size());
        assertEquals(3, map.query(indexer.notIn(-1.0, 1.0)).toList().size());
    }

    @Test
    void rangeQueries()
    {
        final DoubleValueIndexer indexer = new DoubleValueIndexer();

        final GigaMap<DoublePojo> map = GigaMap.<DoublePojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new DoublePojo(-Double.MAX_VALUE));
        map.add(new DoublePojo(-1.0));
        map.add(new DoublePojo(0.0));
        map.add(new DoublePojo(1.0));
        map.add(new DoublePojo(Double.MAX_VALUE));

        // lessThan
        assertEquals(2, map.query(indexer.lessThan(0.0)).toList().size());
        assertEquals(0, map.query(indexer.lessThan(-Double.MAX_VALUE)).toList().size());

        // lessThanEqual
        assertEquals(3, map.query(indexer.lessThanEqual(0.0)).toList().size());
        assertEquals(1, map.query(indexer.lessThanEqual(-Double.MAX_VALUE)).toList().size());

        // greaterThan
        assertEquals(2, map.query(indexer.greaterThan(0.0)).toList().size());
        assertEquals(0, map.query(indexer.greaterThan(Double.MAX_VALUE)).toList().size());

        // greaterThanEqual
        assertEquals(3, map.query(indexer.greaterThanEqual(0.0)).toList().size());
        assertEquals(1, map.query(indexer.greaterThanEqual(Double.MAX_VALUE)).toList().size());

        // between
        assertEquals(3, map.query(indexer.between(-1.0, 1.0)).toList().size());
        assertEquals(5, map.query(indexer.between(-Double.MAX_VALUE, Double.MAX_VALUE)).toList().size());
    }

    @Test
    void rangeQueriesWithNegativeValues()
    {
        final DoubleValueIndexer indexer = new DoubleValueIndexer();

        final GigaMap<DoublePojo> map = GigaMap.<DoublePojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new DoublePojo(-3.0));
        map.add(new DoublePojo(-2.0));
        map.add(new DoublePojo(-1.0));

        assertEquals(1, map.query(indexer.lessThan(-2.0)).toList().size());
        assertEquals(2, map.query(indexer.lessThanEqual(-2.0)).toList().size());
        assertEquals(1, map.query(indexer.greaterThan(-2.0)).toList().size());
        assertEquals(2, map.query(indexer.greaterThanEqual(-2.0)).toList().size());
    }

    @Test
    void persistenceRoundTrip()
    {
        final DoubleValueIndexer indexer = new DoubleValueIndexer();

        final GigaMap<DoublePojo> map = GigaMap.<DoublePojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new DoublePojo(-1.0));
        map.add(new DoublePojo(0.0));
        map.add(new DoublePojo(1.0));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir))
        {
            assertEquals(3, map.size());
        }

        final GigaMap<DoublePojo> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, tempDir))
        {
            assertEquals(3, newMap.size());

            final List<DoublePojo> result = newMap.query(indexer.is(0.0)).toList();
            assertEquals(1, result.size());
            assertEquals(0.0, result.get(0).getValue());

            assertEquals(1, newMap.query(indexer.greaterThan(0.0)).toList().size());
            assertEquals(1, newMap.query(indexer.lessThan(0.0)).toList().size());
        }
    }


    static class DoubleValueIndexer extends ByteIndexerDouble.Abstract<DoublePojo>
    {
        @Override
        protected Double getDouble(final DoublePojo entity)
        {
            return entity.getValue();
        }
    }

    static class DoublePojo
    {
        private final Double value;

        DoublePojo(final Double value)
        {
            this.value = value;
        }

        public Double getValue()
        {
            return this.value;
        }
    }

}
