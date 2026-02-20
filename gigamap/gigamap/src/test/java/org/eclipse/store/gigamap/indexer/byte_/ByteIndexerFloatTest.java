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

import org.eclipse.store.gigamap.types.ByteIndexerFloat;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ByteIndexerFloatTest
{
    @TempDir
    Path tempDir;

    @Test
    void equalityQueries()
    {
        final FloatValueIndexer indexer = new FloatValueIndexer();

        final GigaMap<FloatPojo> map = GigaMap.<FloatPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new FloatPojo(-Float.MAX_VALUE));
        map.add(new FloatPojo(-1.0f));
        map.add(new FloatPojo(0.0f));
        map.add(new FloatPojo(1.0f));
        map.add(new FloatPojo(Float.MAX_VALUE));

        assertEquals(1, map.query(indexer.is(0.0f)).toList().size());
        assertEquals(4, map.query(indexer.not(0.0f)).toList().size());
        assertEquals(2, map.query(indexer.in(-1.0f, 1.0f)).toList().size());
        assertEquals(3, map.query(indexer.notIn(-1.0f, 1.0f)).toList().size());
    }

    @Test
    void rangeQueries()
    {
        final FloatValueIndexer indexer = new FloatValueIndexer();

        final GigaMap<FloatPojo> map = GigaMap.<FloatPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new FloatPojo(-Float.MAX_VALUE));
        map.add(new FloatPojo(-1.0f));
        map.add(new FloatPojo(0.0f));
        map.add(new FloatPojo(1.0f));
        map.add(new FloatPojo(Float.MAX_VALUE));

        // lessThan
        assertEquals(2, map.query(indexer.lessThan(0.0f)).toList().size());
        assertEquals(0, map.query(indexer.lessThan(-Float.MAX_VALUE)).toList().size());

        // lessThanEqual
        assertEquals(3, map.query(indexer.lessThanEqual(0.0f)).toList().size());
        assertEquals(1, map.query(indexer.lessThanEqual(-Float.MAX_VALUE)).toList().size());

        // greaterThan
        assertEquals(2, map.query(indexer.greaterThan(0.0f)).toList().size());
        assertEquals(0, map.query(indexer.greaterThan(Float.MAX_VALUE)).toList().size());

        // greaterThanEqual
        assertEquals(3, map.query(indexer.greaterThanEqual(0.0f)).toList().size());
        assertEquals(1, map.query(indexer.greaterThanEqual(Float.MAX_VALUE)).toList().size());

        // between
        assertEquals(3, map.query(indexer.between(-1.0f, 1.0f)).toList().size());
        assertEquals(5, map.query(indexer.between(-Float.MAX_VALUE, Float.MAX_VALUE)).toList().size());
    }

    @Test
    void rangeQueriesWithNegativeValues()
    {
        final FloatValueIndexer indexer = new FloatValueIndexer();

        final GigaMap<FloatPojo> map = GigaMap.<FloatPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new FloatPojo(-3.0f));
        map.add(new FloatPojo(-2.0f));
        map.add(new FloatPojo(-1.0f));

        assertEquals(1, map.query(indexer.lessThan(-2.0f)).toList().size());
        assertEquals(2, map.query(indexer.lessThanEqual(-2.0f)).toList().size());
        assertEquals(1, map.query(indexer.greaterThan(-2.0f)).toList().size());
        assertEquals(2, map.query(indexer.greaterThanEqual(-2.0f)).toList().size());
    }

    @Test
    void persistenceRoundTrip()
    {
        final FloatValueIndexer indexer = new FloatValueIndexer();

        final GigaMap<FloatPojo> map = GigaMap.<FloatPojo>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new FloatPojo(-1.0f));
        map.add(new FloatPojo(0.0f));
        map.add(new FloatPojo(1.0f));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir))
        {
            assertEquals(3, map.size());
        }

        final GigaMap<FloatPojo> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, tempDir))
        {
            assertEquals(3, newMap.size());

            final List<FloatPojo> result = newMap.query(indexer.is(0.0f)).toList();
            assertEquals(1, result.size());
            assertEquals(0.0f, result.get(0).getValue());

            assertEquals(1, newMap.query(indexer.greaterThan(0.0f)).toList().size());
            assertEquals(1, newMap.query(indexer.lessThan(0.0f)).toList().size());
        }
    }


    static class FloatValueIndexer extends ByteIndexerFloat.Abstract<FloatPojo>
    {
        @Override
        protected Float getFloat(final FloatPojo entity)
        {
            return entity.getValue();
        }
    }

    static class FloatPojo
    {
        private final Float value;

        FloatPojo(final Float value)
        {
            this.value = value;
        }

        public Float getValue()
        {
            return this.value;
        }
    }

}
