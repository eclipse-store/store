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

import org.eclipse.store.gigamap.types.ByteIndexerLong;
import org.eclipse.store.gigamap.types.Condition;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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


    /**
     * Regression test for issue #653 / #654: composing a {@link ByteIndexerLong} numeric
     * range condition via {@link Condition#and(Condition)} (i) with another range condition
     * and (ii) with a condition from a different indexer type must produce the same result
     * as composing via {@link org.eclipse.store.gigamap.types.GigaQuery#and(Condition)}.
     * <p>
     * Representative coverage for {@link org.eclipse.store.gigamap.types.ByteIndexerNumber};
     * all numeric subclasses share the same abstract greaterThan/lessThan implementation.
     */
    @Test
    void numberRangeCompositionViaConditionAnd()
    {
        final NamedLongValueIndexer valueIdx = new NamedLongValueIndexer();
        final NameIndex             nameIdx  = new NameIndex();

        final GigaMap<NamedLongPojo> map = GigaMap.<NamedLongPojo>Builder()
            .withBitmapIndex(valueIdx)
            .withBitmapIndex(nameIdx)
            .build();
        map.add(new NamedLongPojo("Alice",   10L));
        map.add(new NamedLongPojo("Bob",     20L));
        map.add(new NamedLongPojo("Charlie", 30L));
        map.add(new NamedLongPojo("Dave",    40L));

        // (i) range AND range
        final Condition<NamedLongPojo> greater = valueIdx.greaterThan(15L);
        final Condition<NamedLongPojo> less    = valueIdx.lessThan(35L);

        final List<NamedLongPojo> rangeAnd = map.query(greater.and(less)).toList();
        final List<NamedLongPojo> rangeQ   = map.query().and(greater).and(less).toList();

        assertEquals(2, rangeAnd.size(), "Condition.and() must respect both bounds");
        assertEquals(rangeQ.size(), rangeAnd.size(), "Condition.and() and GigaQuery.and() must agree");
        rangeAnd.forEach(e -> assertNotEquals("Alice", e.name()));
        rangeAnd.forEach(e -> assertNotEquals("Dave",  e.name()));

        // (ii) range AND condition from a different indexer
        final Condition<NamedLongPojo> bobMatch = nameIdx.is("Bob");

        final List<NamedLongPojo> mixedAnd = map.query(greater.and(bobMatch)).toList();
        final List<NamedLongPojo> mixedQ   = map.query().and(greater).and(bobMatch).toList();

        assertEquals(1, mixedAnd.size(), "range AND non-range condition must intersect correctly");
        assertEquals("Bob", mixedAnd.get(0).name());
        assertEquals(mixedQ.size(), mixedAnd.size());
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

    static class NamedLongValueIndexer extends ByteIndexerLong.Abstract<NamedLongPojo>
    {
        @Override
        protected Long getLong(final NamedLongPojo entity)
        {
            return entity.value();
        }
    }

    static class NameIndex extends IndexerString.Abstract<NamedLongPojo>
    {
        @Override
        protected String getString(final NamedLongPojo entity)
        {
            return entity.name();
        }
    }

    static class NamedLongPojo
    {
        private final String name;
        private final Long   value;

        NamedLongPojo(final String name, final Long value)
        {
            this.name  = name;
            this.value = value;
        }

        String name()
        {
            return this.name;
        }

        Long value()
        {
            return this.value;
        }
    }

}
