package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression cover for computed-mode entity-id drift: the vector store's own id allocator diverging
 * from the parent {@link GigaMap}'s id space.
 * <p>
 * Computed-mode lookups used to be positional ({@code vectorStore.get(entityId)}), which silently
 * assumed the two id spaces stayed in lockstep. They resolve by {@link VectorEntry#sourceEntityId}
 * through the store's identity index instead, so drift is expected rather than merely tolerated.
 * These tests force drift deliberately - by registering an index onto a parent map that already has
 * deletion holes - where the existing null-embedding tests only produce it incidentally.
 */
class VectorIndexIdDriftTest
{
    static final int DIM = 5;

    /** Orthogonal unit basis vectors, so nearest-neighbour maps deterministically to one entity. */
    static float[] basis(final int i)
    {
        final float[] v = new float[DIM];
        v[i] = 1.0f;
        return v;
    }

    static final class Doc
    {
        final String  content;
        final float[] embedding; // may be null in the nullable-vectorizer tests

        Doc(final String content, final float[] embedding)
        {
            this.content   = content;
            this.embedding = embedding;
        }
    }

    /** Computed-mode vectorizer: vectors live in the index's own vector store. */
    static class ComputedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.embedding;
        }
    }

    /** Computed-mode vectorizer that also permits vector-less entities. */
    static class NullableComputedVectorizer extends ComputedVectorizer
    {
        @Override
        public boolean allowsNullVectors()
        {
            return true;
        }
    }

    private static VectorIndexConfiguration config()
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();
    }

    private static VectorIndex<Doc> registerIndex(final GigaMap<Doc> map, final Vectorizer<Doc> vectorizer)
    {
        final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
        return indices.add("embeddings", config(), vectorizer);
    }

    private static long topHit(final VectorIndex<Doc> index, final float[] query)
    {
        return index.search(query, 1).stream().findFirst().orElseThrow().entityId();
    }

    /**
     * Adds five docs with basis vectors 0..4, then removes two of them - all before any vector index
     * exists. Registering the index afterwards backfills via
     * {@code parent.iterateIndexed(index::internalAdd)}, so the store's allocator hands out ids
     * 0,1,2 to the surviving parent ids 0,2,4: the two id spaces are now permanently offset.
     *
     * @return the parent entity ids 0..4, the removed ones included
     */
    private static long[] driftedMap(final GigaMap<Doc> map)
    {
        final long[] ids = new long[DIM];
        for(int i = 0; i < DIM; i++)
        {
            ids[i] = map.add(new Doc("v" + i, basis(i)));
        }
        map.removeById(ids[1]);
        map.removeById(ids[3]);
        return ids;
    }

    // ==================== A. Drift by registration onto a map with deletion holes ====================

    @Test
    void driftedStoreIds_resolveBySourceEntityId()
    {
        final GigaMap<Doc> map = GigaMap.New();
        final long[]       ids = driftedMap(map);

        final VectorIndex<Doc> index = registerIndex(map, new ComputedVectorizer());

        // Positional access would return null here (the store has no id 4) and, worse, would hand
        // back v4's vector for parent id 2 (store id 2). Both must resolve by source entity id.
        assertArrayEquals(basis(0), index.getVector(ids[0]), "surviving entity 0");
        assertArrayEquals(basis(2), index.getVector(ids[2]), "surviving entity 2 must not read v4");
        assertArrayEquals(basis(4), index.getVector(ids[4]), "highest parent id exceeds the store's");

        assertNull(index.getVector(ids[1]), "removed before registration, never indexed");
        assertNull(index.getVector(ids[3]), "removed before registration, never indexed");

        // The hot scoring path (lookupComputedVector) is only exercised by an actual search.
        assertEquals(3, index.search(basis(0), 10).size(), "exactly the three survivors are indexed");
        assertEquals(ids[0], topHit(index, basis(0)));
        assertEquals(ids[2], topHit(index, basis(2)));
        assertEquals(ids[4], topHit(index, basis(4)));
    }

    // ==================== B. Mutation on top of a drifted store ====================

    @Test
    void driftedStoreIds_surviveSetAddAndRemove()
    {
        final GigaMap<Doc> map = GigaMap.New();
        final long[]       ids = driftedMap(map);

        final VectorIndex<Doc> index = registerIndex(map, new ComputedVectorizer());

        // set() on a drifted id: internalUpdate must resolve the store id, not reuse the parent's.
        // basis(1) is free again - entity 1 was removed before the index existed.
        map.set(ids[2], new Doc("v2-changed", basis(1)));
        assertArrayEquals(basis(1), index.getVector(ids[2]), "set must write through to the right entry");
        assertEquals(ids[2], topHit(index, basis(1)));
        assertArrayEquals(basis(4), index.getVector(ids[4]), "neighbouring entries untouched");

        // add() on a drifted map: the parent may reuse a freed id while the store allocator marches on.
        final long idNew = map.add(new Doc("vNew", basis(3)));
        assertArrayEquals(basis(3), index.getVector(idNew), "newly added entity resolves");
        assertEquals(idNew, topHit(index, basis(3)));
        assertEquals(4, index.search(basis(0), 10).size());

        // removeById() on a drifted id: only that entry may disappear.
        map.removeById(ids[4]);
        assertNull(index.getVector(ids[4]), "removed entity has no vector");
        assertEquals(3, index.search(basis(0), 10).size());
        assertTrue(index.search(basis(4), 10).stream().noneMatch(e -> e.entityId() == ids[4]),
            "removed entity must not appear in search results");

        // Everything else still resolves to its own vector.
        assertArrayEquals(basis(0), index.getVector(ids[0]));
        assertArrayEquals(basis(1), index.getVector(ids[2]));
        assertArrayEquals(basis(3), index.getVector(idNew));
    }

    // ==================== C. Both computedIdIndex build paths agree ====================

    /**
     * The {@code sourceEntityId -> storeId} map is normally reconstructed from the identity index
     * bitmaps, and only during the {@code complete()} deserialization window by a positional store
     * scan. Nothing else asserts the two paths produce the same mapping, so a divergence would only
     * ever surface as wrong vectors after a reload.
     */
    @Test
    @SuppressWarnings("unchecked")
    void bothComputedIdIndexBuildPaths_produceTheSameMapping()
    {
        final GigaMap<Doc> map = GigaMap.New();
        final long[]       ids = driftedMap(map);

        // Drift plus vector-less entities: those get no store entry at all, widening the offset.
        final VectorIndex<Doc> index = registerIndex(map, new NullableComputedVectorizer());
        map.add(new Doc("noVector", null));
        final long withVector = map.add(new Doc("vLate", basis(1)));
        map.set(ids[0], new Doc("v0-nulled", null));

        final VectorIndex.Default<Doc> def       = (VectorIndex.Default<Doc>)index;
        final Map<Long, Long>         fromIndex = def.buildComputedIdIndexFromIndex();
        final Map<Long, Long>         byScan    = def.buildComputedIdIndexByScan();

        assertNotNull(fromIndex, "the identity index must be queryable on a live map");
        assertFalse(fromIndex.isEmpty(), "guard against both paths agreeing on an empty mapping");
        assertEquals(byScan, fromIndex, "identity-index reconstruction must match the positional scan");

        // Sanity: the mapping covers exactly the entities that have a stored vector.
        assertTrue(fromIndex.containsKey(ids[2]));
        assertTrue(fromIndex.containsKey(ids[4]));
        assertTrue(fromIndex.containsKey(withVector));
        assertFalse(fromIndex.containsKey(ids[0]), "entity whose vector became null has no entry");
        assertFalse(fromIndex.containsKey(ids[1]), "entity removed before registration has no entry");
    }

    // ==================== D. Drift survives a persistence round-trip ====================

    /**
     * On reload the mapping is rebuilt from the persisted identity-index bitmaps rather than
     * accumulated by mutations, which is the path a positional implementation got wrong silently.
     */
    @Test
    void driftedStoreIds_surviveReload(@TempDir final Path dir)
    {
        final long[] ids;

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            ids = driftedMap(map);
            registerIndex(map, new ComputedVectorizer());
            storage.storeRoot();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index()
                .get(VectorIndices.Category())
                .get("embeddings")
            ;

            assertEquals(3, map.size(), "the two removed entities stay removed");

            assertArrayEquals(basis(0), index.getVector(ids[0]), "after reload: entity 0");
            assertArrayEquals(basis(2), index.getVector(ids[2]), "after reload: entity 2");
            assertArrayEquals(basis(4), index.getVector(ids[4]), "after reload: entity 4");
            assertNull(index.getVector(ids[1]));
            assertNull(index.getVector(ids[3]));

            assertEquals(3, index.search(basis(0), 10).size());
            assertEquals(ids[4], topHit(index, basis(4)), "highest parent id still resolves after reload");
        }
    }
}
