package org.eclipse.store.gigamap.indexer;

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

import org.eclipse.store.gigamap.types.BinaryIndexerFloat;
import org.eclipse.store.gigamap.types.BinaryIndexerInteger;
import org.eclipse.store.gigamap.types.BinaryIndexerLong;
import org.eclipse.store.gigamap.types.BitmapIndex;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link BitmapIndex#iterateKeyEntityPairs} for the binary (bit-sliced) index family: it must
 * reconstruct the exact {@code (key, entityId)} mapping from the index bitmaps alone — including the
 * {@code 0}/{@code Long.MAX_VALUE} sentinel, negative keys (bit 63), duplicate keys, and deletion
 * holes — without loading the indexed entities.
 */
class BitmapIndexKeyEntityPairsTest
{
    static final class LongEntity
    {
        final long value;
        LongEntity(final long value) { this.value = value; }
    }

    static final class ValueIndexer extends BinaryIndexerLong.Abstract<LongEntity>
    {
        @Override public String name() { return "value"; }
        @Override protected Long getLong(final LongEntity entity) { return entity.value; }
    }

    private static BitmapIndex<LongEntity, Long> valueIndex(final GigaMap<LongEntity> map)
    {
        return map.index().bitmap().get(Long.class, "value");
    }

    private static Map<Long, Long> pairsViaIterateKeyEntityPairs(final GigaMap<LongEntity> map)
    {
        final Map<Long, Long> actual = new HashMap<>(); // entityId -> key
        valueIndex(map).iterateKeyEntityPairs((key, entityId) ->
        {
            final Long prev = actual.put(entityId, key);
            assertTrue(prev == null, "entityId reported more than once: " + entityId);
        });
        return actual;
    }

    private static Map<Long, Long> pairsViaIterateIndexed(final GigaMap<LongEntity> map)
    {
        final Map<Long, Long> expected = new HashMap<>(); // entityId -> key
        map.iterateIndexed((id, entity) -> expected.put(id, entity.value));
        return expected;
    }

    @Test
    void reconstructsAllPairs_withSentinelNegativesDuplicatesAndHoles()
    {
        final GigaMap<LongEntity> map = GigaMap.<LongEntity>Builder()
            .withBitmapIdentityIndex(new ValueIndexer())
            .build();

        // Keys spanning the tricky cases: 0 (stored as the Long.MAX_VALUE sentinel), small positives,
        // duplicates (identity indices do not enforce uniqueness), negatives and Long.MIN_VALUE
        // (sign bit / bit 63), and a large positive.
        map.add(new LongEntity(0L));
        map.add(new LongEntity(1L));
        map.add(new LongEntity(2L));
        map.add(new LongEntity(2L));            // duplicate key
        map.add(new LongEntity(-5L));
        map.add(new LongEntity(Long.MIN_VALUE));
        map.add(new LongEntity(1_234_567_890L));
        final long holeId = map.add(new LongEntity(42L));
        map.add(new LongEntity(0L));            // second zero-key entity

        // Create a deletion hole: its id must NOT appear in the reconstructed pairs.
        map.removeById(holeId);

        final Map<Long, Long> expected = pairsViaIterateIndexed(map);
        final Map<Long, Long> actual   = pairsViaIterateKeyEntityPairs(map);

        assertEquals(expected, actual, "reconstructed (entityId -> key) must match iterateIndexed");
        assertTrue(actual.values().stream().anyMatch(v -> v == 0L), "zero key reconstructed");
        assertTrue(actual.containsValue(Long.MIN_VALUE), "Long.MIN_VALUE key reconstructed");
        assertTrue(actual.containsValue(-5L), "negative key reconstructed");
        assertTrue(actual.keySet().stream().noneMatch(id -> id == holeId), "deleted id excluded");
    }

    @Test
    void emptyIndex_yieldsNoPairs()
    {
        final GigaMap<LongEntity> map = GigaMap.<LongEntity>Builder()
            .withBitmapIdentityIndex(new ValueIndexer())
            .build();

        final Map<Long, Long> actual = pairsViaIterateKeyEntityPairs(map);
        assertEquals(0, actual.size(), "empty index must yield no pairs");
    }

    // ---- non-Long binary indexers: integer keys reconstruct, float fails fast ----

    static final class IntEntity
    {
        final int value;
        IntEntity(final int value) { this.value = value; }
    }

    static final class IntValueIndexer extends BinaryIndexerInteger.Abstract<IntEntity>
    {
        @Override public String name() { return "value"; }
        @Override protected Integer getInteger(final IntEntity entity) { return entity.value; }
    }

    static final class FloatEntity
    {
        final float value;
        FloatEntity(final float value) { this.value = value; }
    }

    static final class FloatValueIndexer extends BinaryIndexerFloat.Abstract<FloatEntity>
    {
        @Override public String name() { return "value"; }
        @Override protected Float getFloat(final FloatEntity entity) { return entity.value; }
    }

    @Test
    @SuppressWarnings("unchecked")
    void reconstructsIntegerKeys_withSentinelAndNegatives()
    {
        final GigaMap<IntEntity> map = GigaMap.<IntEntity>Builder()
            .withBitmapIdentityIndex(new IntValueIndexer())
            .build();

        // Zero (sentinel 1L<<32), negatives (unsigned upper half), Integer.MIN_VALUE (bit 31), positives.
        map.addAll(java.util.List.of(
            new IntEntity(0), new IntEntity(1), new IntEntity(-1),
            new IntEntity(Integer.MIN_VALUE), new IntEntity(1_234_567_890)));

        final Map<Long, Long> expected = new HashMap<>(); // entityId -> key (as long)
        map.iterateIndexed((id, entity) -> expected.put(id, (long)entity.value));

        final Map<Long, Long> actual = new HashMap<>();
        final BitmapIndex<IntEntity, Long> index = map.index().bitmap().get(Long.class, "value");
        index.iterateKeyEntityPairs((key, entityId) -> actual.put(entityId, key));

        assertEquals(expected, actual, "integer keys must be reconstructed to their original values");
    }

    @Test
    @SuppressWarnings("unchecked")
    void floatIndex_failsFastAsUnsupported()
    {
        final GigaMap<FloatEntity> map = GigaMap.<FloatEntity>Builder()
            .withBitmapIdentityIndex(new FloatValueIndexer())
            .build();
        map.add(new FloatEntity(1.5f));

        final BitmapIndex<FloatEntity, Long> index = map.index().bitmap().get(Long.class, "value");
        // Float's sortable-bit encoding has no binaryToKey inverse → fail fast, not emit encoded bits.
        assertThrows(UnsupportedOperationException.class,
            () -> index.iterateKeyEntityPairs((key, entityId) -> { }));
    }
}
