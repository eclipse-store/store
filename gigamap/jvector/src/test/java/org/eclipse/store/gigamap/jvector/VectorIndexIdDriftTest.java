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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression cover for computed-mode entity-id drift: an index whose vectors are not addressed by the parent
 * {@link GigaMap}'s entity ids.
 * <p>
 * Computed-mode vectors once lived in a store of their own, whose id allocator drifted away from the parent's ids.
 * Now the graph ordinals and the vector-file slots are the parent entity ids themselves, so nothing may number
 * the entries on its own. These tests create the conditions under which a second numbering would show: an index
 * registered onto a parent map that already has deletion holes, and entities without a vector.
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

    /** Computed-mode vectorizer: the index keeps the vectors itself. */
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
     * exists. Registering the index afterwards backfills only the survivors, parent ids 0,2,4: an index
     * that numbered its entries itself would give them 0,1,2.
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

        // A numbering of its own would have no entry 4 and would hand back v4's vector for parent id 2
        // (its entry 2). Both must resolve by the parent entity id.
        assertArrayEquals(basis(0), index.getVector(ids[0]), "surviving entity 0");
        assertArrayEquals(basis(2), index.getVector(ids[2]), "surviving entity 2 must not read v4");
        assertArrayEquals(basis(4), index.getVector(ids[4]), "highest parent id exceeds the store's");

        assertNull(index.getVector(ids[1]), "removed before registration, never indexed");
        assertNull(index.getVector(ids[3]), "removed before registration, never indexed");

        // The scoring path reads the vectors by graph ordinal, which only an actual search exercises.
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

        // set() on an id above a hole must overwrite that entity's own vector.
        // basis(1) is free again - entity 1 was removed before the index existed.
        map.set(ids[2], new Doc("v2-changed", basis(1)));
        assertArrayEquals(basis(1), index.getVector(ids[2]), "set must write through to the right entry");
        assertEquals(ids[2], topHit(index, basis(1)));
        assertArrayEquals(basis(4), index.getVector(ids[4]), "neighbouring entries untouched");

        // add() on a map with holes: the new entity's vector goes under the id the parent assigns.
        final long idNew = map.add(new Doc("vNew", basis(3)));
        assertArrayEquals(basis(3), index.getVector(idNew), "newly added entity resolves");
        assertEquals(idNew, topHit(index, basis(3)));
        assertEquals(4, index.search(basis(0), 10).size());

        // removeById() above a hole: only that entry may disappear.
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

    // ==================== C. Entities without a vector widen the holes ====================

    @Test
    void vectorLessEntities_leaveOnlyHoles()
    {
        final GigaMap<Doc> map = GigaMap.New();
        final long[]       ids = driftedMap(map);

        final VectorIndex<Doc> index = registerIndex(map, new NullableComputedVectorizer());
        final long noVector   = map.add(new Doc("noVector", null));
        final long withVector = map.add(new Doc("vLate", basis(1)));
        map.set(ids[0], new Doc("v0-nulled", null));

        assertNull(index.getVector(noVector), "an entity without a vector has none");
        assertNull(index.getVector(ids[0]), "a vector set to null is gone");
        assertArrayEquals(basis(1), index.getVector(withVector), "the entity after the vector-less one");
        assertArrayEquals(basis(2), index.getVector(ids[2]));
        assertArrayEquals(basis(4), index.getVector(ids[4]));

        assertEquals(3, index.search(basis(0), 10).size(), "exactly the entities with a vector are indexed");
        assertEquals(withVector, topHit(index, basis(1)));
        assertEquals(ids[2], topHit(index, basis(2)));
        assertEquals(ids[4], topHit(index, basis(4)));
    }

    // ==================== D. Drift survives a persistence round-trip ====================

    /**
     * After a reload nothing is accumulated by mutations: the index is restored from what was persisted,
     * so the holes must come back as holes rather than be numbered away.
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
