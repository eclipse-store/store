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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for opted-in null-embedding support: {@link Vectorizer#allowsNullVectors()}.
 * A {@code null} vector means "this entity has no embedding" — it stays in the GigaMap but is
 * excluded from the HNSW graph and never appears in vector search results.
 */
class VectorIndexNullVectorTest
{
    static final int DIM = 4;

    // Orthogonal unit basis vectors so nearest-neighbor maps deterministically to one entity.
    static float[] basis(final int i)
    {
        final float[] v = new float[DIM];
        v[i] = 1.0f;
        return v;
    }

    /**
     * An entity that may or may not carry an embedding.
     */
    static final class Doc
    {
        final String  content;
        final float[] embedding; // may be null

        Doc(final String content, final float[] embedding)
        {
            this.content   = content;
            this.embedding = embedding;
        }
    }

    /** Embedded vectorizer that permits null embeddings. */
    static class NullableEmbeddedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.embedding;
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }

        @Override
        public boolean allowsNullVectors()
        {
            return true;
        }
    }

    /** Computed vectorizer (separate store) that permits null embeddings. */
    static class NullableComputedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.embedding;
        }

        @Override
        public boolean allowsNullVectors()
        {
            return true;
        }
    }

    /** Strict computed vectorizer (default: null forbidden). */
    static class StrictComputedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.embedding;
        }
    }

    /** Strict embedded vectorizer (default: null forbidden). */
    static class StrictEmbeddedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.embedding;
        }

        @Override
        public boolean isEmbedded()
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

    private static VectorIndexConfiguration eventualConfig()
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(true)
            .build();
    }

    @SuppressWarnings("unchecked")
    private static void drain(final VectorIndex<Doc> index)
    {
        final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
        if(def.backgroundTaskManager != null)
        {
            def.backgroundTaskManager.drainQueue();
        }
    }

    private static VectorIndex<Doc> newIndex(final GigaMap<Doc> map, final Vectorizer<Doc> vectorizer)
    {
        final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
        return indices.add("embeddings", config(), vectorizer);
    }

    // ==================== 1. Opt-out regression ====================

    @Test
    void nullVectorRejectedByDefault_computedAdd()
    {
        final GigaMap<Doc> map = GigaMap.New();
        newIndex(map, new StrictComputedVectorizer());
        assertThrows(IllegalStateException.class, () -> map.add(new Doc("no-vec", null)));
    }

    @Test
    void nullVectorRejectedByDefault_embeddedAdd()
    {
        final GigaMap<Doc> map = GigaMap.New();
        newIndex(map, new StrictEmbeddedVectorizer());
        assertThrows(IllegalStateException.class, () -> map.add(new Doc("no-vec", null)));
    }

    @Test
    void nullVectorRejectedByDefault_update()
    {
        final GigaMap<Doc> map = GigaMap.New();
        newIndex(map, new StrictComputedVectorizer());
        final long id = map.add(new Doc("has-vec", basis(0)));
        assertThrows(IllegalStateException.class, () -> map.set(id, new Doc("now-null", null)));
    }

    @Test
    void nullVectorRejectedByDefault_addAll()
    {
        final GigaMap<Doc> map = GigaMap.New();
        newIndex(map, new StrictComputedVectorizer());
        final List<Doc> docs = List.of(
            new Doc("a", basis(0)),
            new Doc("b", null),
            new Doc("c", basis(1))
        );
        assertThrows(IllegalStateException.class, () -> map.addAll(docs));
    }

    // ==================== 2/3. Mixed add + search (both modes) ====================

    @Test
    void mixedAdd_computed()
    {
        assertMixedAddExcludesNulls(new NullableComputedVectorizer());
    }

    @Test
    void mixedAdd_embedded()
    {
        assertMixedAddExcludesNulls(new NullableEmbeddedVectorizer());
    }

    private void assertMixedAddExcludesNulls(final Vectorizer<Doc> vectorizer)
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndex<Doc> index = newIndex(map, vectorizer);

        final long id0 = map.add(new Doc("e0", basis(0)));
        final long idN = map.add(new Doc("none", null));
        final long id1 = map.add(new Doc("e1", basis(1)));
        final long id2 = map.add(new Doc("e2", basis(2)));

        // Only the three vectored entities are searchable.
        final VectorSearchResult<Doc> all = index.search(basis(0), 10);
        assertEquals(3, all.size(), "null-embedding entity must be excluded from search");

        // Nearest-neighbor identity is correct per query.
        assertEquals(id0, index.search(basis(0), 1).stream().findFirst().orElseThrow().entityId());
        assertEquals(id1, index.search(basis(1), 1).stream().findFirst().orElseThrow().entityId());
        assertEquals(id2, index.search(basis(2), 1).stream().findFirst().orElseThrow().entityId());

        // getVector reflects presence/absence.
        assertNotNull(index.getVector(id0));
        assertNull(index.getVector(idN), "entity without embedding has no vector");

        // A later remove/update still resolves the right entries.
        map.removeById(id2);
        assertEquals(2, index.search(basis(0), 10).size());
        assertNull(index.getVector(id2));
    }

    // ==================== 4. addAll alignment with interspersed nulls ====================

    @Test
    void addAll_withNulls_computed()
    {
        assertAddAllAlignment(new NullableComputedVectorizer());
    }

    @Test
    void addAll_withNulls_embedded()
    {
        assertAddAllAlignment(new NullableEmbeddedVectorizer());
    }

    private void assertAddAllAlignment(final Vectorizer<Doc> vectorizer)
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndex<Doc> index = newIndex(map, vectorizer);

        // nulls at head, middle, tail
        final List<Doc> docs = new ArrayList<>();
        docs.add(new Doc("headNull", null));
        docs.add(new Doc("v0", basis(0)));
        docs.add(new Doc("midNull", null));
        docs.add(new Doc("v1", basis(1)));
        docs.add(new Doc("v2", basis(2)));
        docs.add(new Doc("tailNull", null));

        map.addAll(docs);

        assertEquals(3, index.search(basis(0), 10).size());

        // Each vectored entity resolves to the correct content via its own id.
        for(int dim = 0; dim < 3; dim++)
        {
            final long id = index.search(basis(dim), 1).stream().findFirst().orElseThrow().entityId();
            assertEquals("v" + dim, map.get(id).content, "id must map to the aligned entity");
            assertNotNull(index.getVector(id));
        }
    }

    // ==================== 5/6. Update transitions (sync, both modes) ====================

    @Test
    void updateTransitions_computedSync()
    {
        assertUpdateTransitions(new NullableComputedVectorizer());
    }

    @Test
    void updateTransitions_embeddedSync()
    {
        assertUpdateTransitions(new NullableEmbeddedVectorizer());
    }

    private void assertUpdateTransitions(final Vectorizer<Doc> vectorizer)
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndex<Doc> index = newIndex(map, vectorizer);

        final long a = map.add(new Doc("a", basis(0)));
        final long b = map.add(new Doc("b", basis(1)));
        final long c = map.add(new Doc("c", null)); // starts without embedding

        assertEquals(2, index.search(basis(0), 10).size());
        assertNull(index.getVector(c));

        // null -> vec: c gains an embedding and must appear WITHOUT reload.
        map.set(c, new Doc("c", basis(2)));
        assertEquals(3, index.search(basis(0), 10).size());
        assertEquals(c, index.search(basis(2), 1).stream().findFirst().orElseThrow().entityId());
        assertNotNull(index.getVector(c));

        // vec -> null: a loses its embedding and disappears from search.
        map.set(a, new Doc("a", null));
        assertNull(index.getVector(a));
        final VectorSearchResult<Doc> afterRemoveA = index.search(basis(0), 10);
        assertEquals(2, afterRemoveA.size());
        assertTrue(afterRemoveA.stream().noneMatch(e -> e.entityId() == a),
            "entity whose vector became null must not appear");

        // null -> null: no-op, no exception, still absent.
        map.set(a, new Doc("a", null));
        assertNull(index.getVector(a));
        assertEquals(2, index.search(basis(0), 10).size());

        // vec -> vec: b changes embedding; still present and now nearest to its new vector.
        map.set(b, new Doc("b", basis(3)));
        assertEquals(b, index.search(basis(3), 1).stream().findFirst().orElseThrow().entityId());
        assertNotNull(index.getVector(b));

        // vec -> null -> vec on the SAME entity that WAS in the graph. Regression guard: jvector's
        // markNodeDeleted only sets a deleted bit and leaves the node in layer 0, so containsNode
        // stays true; a naive re-add guard would skip resurrection and b would vanish permanently.
        // Searchable before the cycle: {c=basis(2), b=basis(3)} (a is null).
        map.set(b, new Doc("b", null)); // vec -> null: b disappears
        assertNull(index.getVector(b));
        final VectorSearchResult<Doc> afterNullB = index.search(basis(0), 10);
        assertEquals(1, afterNullB.size());
        assertTrue(afterNullB.stream().noneMatch(e -> e.entityId() == b),
            "entity whose vector became null must not appear");

        map.set(b, new Doc("b", basis(3))); // null -> vec: b must REAPPEAR without reload
        assertNotNull(index.getVector(b));
        final VectorSearchResult<Doc> afterReaddB = index.search(basis(0), 10);
        assertEquals(2, afterReaddB.size(), "entity must reappear after a vec->null->vec cycle");
        assertTrue(afterReaddB.stream().anyMatch(e -> e.entityId() == b),
            "resurrected entity must be searchable again");
        assertEquals(b, index.search(basis(3), 1).stream().findFirst().orElseThrow().entityId());
    }

    // ==================== 7. Update transitions (eventual indexing) ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void updateTransitions_eventual_computed()
    {
        assertUpdateTransitionsEventual(new NullableComputedVectorizer());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void updateTransitions_eventual_embedded()
    {
        assertUpdateTransitionsEventual(new NullableEmbeddedVectorizer());
    }

    private void assertUpdateTransitionsEventual(final Vectorizer<Doc> vectorizer)
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
        try(final VectorIndex<Doc> index = indices.add("embeddings", eventualConfig(), vectorizer))
        {
            final long a = map.add(new Doc("a", basis(0)));
            final long b = map.add(new Doc("b", basis(1)));
            final long c = map.add(new Doc("c", null));
            drain(index);
            assertEquals(2, index.search(basis(0), 10).size());

            // null -> vec
            map.set(c, new Doc("c", basis(2)));
            // vec -> null
            map.set(a, new Doc("a", null));
            // remove of a never-indexed entity must not blow up the worker
            drain(index);

            final VectorSearchResult<Doc> result = index.search(basis(0), 10);
            assertEquals(2, result.size());
            assertTrue(result.stream().noneMatch(e -> e.entityId() == a));
            assertEquals(c, index.search(basis(2), 1).stream().findFirst().orElseThrow().entityId());

            // vec -> null -> vec on the SAME entity that WAS in the graph. The eventual path
            // already re-adds correctly (markNodeDeleted + removeDeletedNodes + addGraphNode);
            // lock that in alongside the sync-mode regression guard. Searchable: {b, c}.
            map.set(b, new Doc("b", null));    // vec -> null
            map.set(b, new Doc("b", basis(1))); // null -> vec: b must reappear
            drain(index);
            final VectorSearchResult<Doc> afterCycle = index.search(basis(0), 10);
            assertEquals(2, afterCycle.size(), "entity must reappear after a vec->null->vec cycle");
            assertTrue(afterCycle.stream().anyMatch(e -> e.entityId() == b),
                "resurrected entity must be searchable again");
            assertEquals(b, index.search(basis(1), 1).stream().findFirst().orElseThrow().entityId());
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void nullToNullUpdate_eventual_computed_enqueuesNothing()
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
        try(final VectorIndex<Doc> index = indices.add(
            "embeddings", eventualConfig(), new NullableComputedVectorizer()))
        {
            map.add(new Doc("v", basis(0)));
            final long c = map.add(new Doc("c", null));
            drain(index);

            // null→null is a documented no-op: it must not generate background work.
            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            map.set(c, new Doc("c", null));
            assertEquals(0, def.backgroundTaskManager.getPendingIndexingCount(),
                "computed-mode null→null update must not enqueue a graph operation");

            assertEquals(1, index.search(basis(0), 10).size());
            assertNull(index.getVector(c));
        }
    }

    // ==================== 8. Remove of a never-indexed entity ====================

    @Test
    void removeNeverIndexedEntity_sync()
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndex<Doc> index = newIndex(map, new NullableComputedVectorizer());
        map.add(new Doc("v", basis(0)));
        final long idNone = map.add(new Doc("none", null));

        // Must be a safe no-op — the entity was never added to the graph or the store.
        map.removeById(idNone);
        assertEquals(1, index.search(basis(0), 10).size());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void removeNeverIndexedEntity_eventual()
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
        try(final VectorIndex<Doc> index = indices.add(
            "embeddings", eventualConfig(), new NullableComputedVectorizer()))
        {
            map.add(new Doc("v", basis(0)));
            final long idNone = map.add(new Doc("none", null));
            map.removeById(idNone);
            drain(index);
            assertEquals(1, index.search(basis(0), 10).size());
        }
    }

    // ==================== 9. Persistence round-trip ====================

    @Test
    void persistenceRoundTrip_computed(@TempDir final Path dir)
    {
        assertPersistenceRoundTrip(dir, false);
    }

    @Test
    void persistenceRoundTrip_embedded(@TempDir final Path dir)
    {
        assertPersistenceRoundTrip(dir, true);
    }

    private void assertPersistenceRoundTrip(final Path dir, final boolean embedded)
    {
        // Phase 1: store entities, some without embeddings.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
            indices.add("embeddings", config(),
                embedded ? new NullableEmbeddedVectorizer() : new NullableComputedVectorizer());

            map.add(new Doc("v0", basis(0)));
            map.add(new Doc("none", null));
            map.add(new Doc("v1", basis(1)));
            storage.storeRoot();
        }

        // Phase 2: reload — rebuild must skip null entities and stay consistent.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
        {
            final GigaMap<Doc> map = storage.root();
            final VectorIndices<Doc> indices = map.index().get(VectorIndices.Category());
            final VectorIndex<Doc> index = indices.get("embeddings");

            assertEquals(3, map.size(), "all entities survive, including the vector-less one");
            assertEquals(2, index.search(basis(0), 10).size(), "only vectored entities are searchable");
            assertEquals("v0", map.get(index.search(basis(0), 1).stream().findFirst().orElseThrow().entityId()).content);
            assertEquals("v1", map.get(index.search(basis(1), 1).stream().findFirst().orElseThrow().entityId()).content);
        }
    }

    // ==================== 10. On-disk index with nulls ====================

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void onDiskWithNulls(@TempDir final Path dir) throws Exception
    {
        final Path storageDir = dir.resolve("storage");
        final Path indexDir   = dir.resolve("index");

        final VectorIndexConfiguration diskConfig = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .build();

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
            final VectorIndex<Doc> index = indices.add(
                "embeddings", diskConfig, new NullableComputedVectorizer());

            map.add(new Doc("v0", basis(0)));
            map.add(new Doc("none", null));
            map.add(new Doc("v1", basis(1)));
            map.add(new Doc("v2", basis(2)));

            index.persistToDisk();
            storage.storeRoot();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.Category()).get("embeddings");

            assertEquals(4, map.size());
            assertEquals(3, index.search(basis(0), 10).size());
            assertEquals("v1", map.get(index.search(basis(1), 1).stream().findFirst().orElseThrow().entityId()).content);
        }
    }

    // ==================== 10b. Crash restart: vec→null must not survive a stale disk graph ====================

    /**
     * Regression guard for the disk-metadata blindness: a {@code vec→null} transition changes
     * neither {@code parentMap().size()} nor {@code highestUsedId()}, so before the persisted
     * structural-change counter the on-disk graph passed metadata validation after a crash between
     * the transition (committed via {@code storeRoot()}) and the next {@code persistToDisk()} — the
     * nulled entity kept appearing in search. {@code persistOnShutdown(false)} simulates the crash:
     * {@code close()} must not persist the graph, leaving the {@code .meta} stale.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void crashRestartAfterVecToNull_rebuildsAndExcludesNulled(@TempDir final Path dir)
    {
        final Path storageDir = dir.resolve("storage");
        final Path indexDir   = dir.resolve("index");

        final VectorIndexConfiguration diskConfig = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .persistOnShutdown(false) // simulate a crash: close() must not flush the graph to disk
            .build();

        final long v0;
        final long v1;

        // Phase A: two embedded vectors; persist graph + metadata, then commit the store.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
                .add("embeddings", diskConfig, new NullableEmbeddedVectorizer());

            v0 = map.add(new Doc("v0", basis(0)));
            v1 = map.add(new Doc("v1", basis(1)));

            index.persistToDisk(); // writes .graph + .meta (structuralModCount = 2)
            storage.storeRoot();   // store also carries structuralModCount = 2
            index.close();
        }

        // Phase B: null out v0 and commit the store WITHOUT persisting the graph, then "crash".
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.Category()).get("embeddings");

            map.set(v0, new Doc("v0", null)); // vec→null: bumps structuralModCount to 3
            map.store();                      // persist the mutation (+ counter 3); .meta still carries 2
            index.close();                    // persistOnShutdown(false) → .meta stays stale
        }

        // Phase C: restart. The structuralModCount mismatch (store 3 vs .meta 2) must reject the
        // stale disk graph and rebuild from the store, so v0 is gone from search.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.Category()).get("embeddings");

            assertNull(index.getVector(v0), "v0 lost its embedding");
            final VectorSearchResult<Doc> result = index.search(basis(0), 10);
            assertEquals(1, result.size(), "nulled entity must not survive a crash restart");
            assertTrue(result.stream().noneMatch(e -> e.entityId() == v0),
                "stale on-disk node must not reappear after a vec→null crash restart");
            assertEquals(v1, index.search(basis(1), 1).stream().findFirst().orElseThrow().entityId());
            index.close();
        }
    }

    // ==================== 10c. Deletion during persist Phase 2 (Finding #4) ====================

    /**
     * A sync-mode {@code vec→null} that lands in persist Phase 2 defers its graph-deletion op, which is
     * drained only after {@code doPersistToDisk} swaps the in-memory builder (exit/reenter incremental).
     * Before the fix the deferred {@code markNodeDeleted} hit the new empty builder and was lost, leaving
     * the entity live on the reloaded disk graph until the next persist. The {@code persistPhase2TestHook}
     * injects the delete deterministically into that exact window, on the persist thread — the same
     * monitor / {@code cleanupInProgress} state a concurrent sync mutation would observe.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @SuppressWarnings("unchecked")
    void deletionDuringPersistPhase2NotLost(@TempDir final Path indexDir)
    {
        final VectorIndexConfiguration diskConfig = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .build();

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", diskConfig, new NullableComputedVectorizer()))
        {
            final long a = map.add(new Doc("a", basis(0)));
            map.add(new Doc("b", basis(1)));
            map.add(new Doc("c", basis(2)));
            assertEquals(3, index.search(basis(0), 10).size());

            // One-shot: null out 'a' during persist Phase 2 — after the parentMap monitor is released,
            // before the builder is swapped (reenterIncrementalMode) and the deferred op is drained.
            // Runs on the persist thread: the same cleanupInProgress / monitor state a concurrent sync
            // mutation would observe, so it defers exactly as the bug requires.
            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            def.persistPhase2TestHook = () ->
            {
                def.persistPhase2TestHook = null;
                map.set(a, new Doc("a", null));
            };

            // This persist writes a,b,c to disk (captured before the hook), the hook defers a's delete,
            // then the builder is swapped and the deferred op drained.
            index.persistToDisk();

            final VectorSearchResult<Doc> result = index.search(basis(0), 10);
            assertEquals(2, result.size(), "nulled entity must not survive a persist-window deletion");
            assertTrue(result.stream().noneMatch(e -> e.entityId() == a),
                "deferred deletion drained after the builder swap must still exclude the entity");
            assertNull(index.getVector(a));
        }
    }

    // ==================== 10d. Finding #5 repro: on-disk delete + persist + reload ====================

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void onDiskComputedDeleteThenReloadKeepsSurvivors(@TempDir final Path dir)
    {
        final Path storageDir = dir.resolve("storage");
        final Path indexDir   = dir.resolve("index");
        final VectorIndexConfiguration diskConfig = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .build();

        long v0;
        long v2;
        long v3;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
                .add("embeddings", diskConfig, new NullableComputedVectorizer());

            v0 = map.add(new Doc("v0", basis(0)));
            final long v1 = map.add(new Doc("v1", basis(1)));
            v2 = map.add(new Doc("v2", basis(2)));
            v3 = map.add(new Doc("v3", basis(3)));
            map.removeById(v1);
            index.persistToDisk();
            storage.storeRoot();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.Category()).get("embeddings");

            assertEquals(3, index.search(basis(0), 10).size(),
                "all three survivors must remain searchable after delete+persist+reload");
            assertEquals(v0, index.search(basis(0), 1).stream().findFirst().orElseThrow().entityId());
            assertEquals(v2, index.search(basis(2), 1).stream().findFirst().orElseThrow().entityId());
            assertEquals(v3, index.search(basis(3), 1).stream().findFirst().orElseThrow().entityId());
        }
    }

    // ==================== 11. Query by null-vector entity / null query ====================

    @Test
    void searchByEntityWithoutVector_throws()
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndex<Doc> index = newIndex(map, new NullableComputedVectorizer());
        map.add(new Doc("v", basis(0)));
        final Doc none = new Doc("none", null);
        map.add(none);

        assertThrows(IllegalArgumentException.class, () -> index.search(none, 5));
        assertThrows(IllegalArgumentException.class, () -> index.search(none, 5, 50));
    }

    @Test
    void searchByNullEntity_throws()
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndex<Doc> index = newIndex(map, new NullableComputedVectorizer());
        map.add(new Doc("v", basis(0)));

        // A null query entity must surface as IllegalArgumentException per the API contract,
        // not a NullPointerException from the vectorizer.
        assertThrows(IllegalArgumentException.class, () -> index.search((Doc)null, 5));
        assertThrows(IllegalArgumentException.class, () -> index.search((Doc)null, 5, 50));
    }

    @Test
    void nullQueryVector_throws()
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndex<Doc> index = newIndex(map, new NullableComputedVectorizer());
        map.add(new Doc("v", basis(0)));

        assertThrows(IllegalArgumentException.class, () -> index.search((float[])null, 5));
    }

    // ==================== 12. Custom vectorizeAll with positional nulls ====================

    static class BatchNullableVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.embedding;
        }

        @Override
        public List<float[]> vectorizeAll(final List<? extends Doc> entities)
        {
            // positional, may include nulls
            final List<float[]> out = new ArrayList<>(entities.size());
            for(final Doc d : entities)
            {
                out.add(d.embedding);
            }
            return out;
        }

        @Override
        public boolean allowsNullVectors()
        {
            return true;
        }
    }

    static class BatchStrictVectorizer extends BatchNullableVectorizer
    {
        @Override
        public boolean allowsNullVectors()
        {
            return false;
        }
    }

    @Test
    void vectorizeAll_positionalNulls_allowed()
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
        final VectorIndex<Doc> index = indices.add("embeddings", config(), new BatchNullableVectorizer());

        map.addAll(List.of(
            new Doc("v0", basis(0)),
            new Doc("none", null),
            new Doc("v1", basis(1))
        ));

        assertEquals(2, index.search(basis(0), 10).size());
    }

    @Test
    void vectorizeAll_positionalNulls_rejectedWhenOptedOut()
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
        indices.add("embeddings", config(), new BatchStrictVectorizer());

        assertThrows(IllegalStateException.class, () -> map.addAll(List.of(
            new Doc("v0", basis(0)),
            new Doc("none", null)
        )));
    }

    // ==================== 13. PQ compression with nulls ====================

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void pqCompressionExcludesNulls(@TempDir final Path dir)
    {
        final int dim = 16;
        final Path storageDir = dir.resolve("storage");
        final Path indexDir   = dir.resolve("index");
        final Random random   = new Random(7);

        final VectorIndexConfiguration pqConfig = VectorIndexConfiguration.builder()
            .dimension(dim)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .pqSubspaces(dim / 4)
            .build();

        long knownId;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
            final VectorIndex<Doc> index = indices.add(
                "embeddings", pqConfig, new NullableComputedVectorizer());

            // > 256 vectored entities so PQ training triggers, plus some null ones.
            for(int i = 0; i < 300; i++)
            {
                map.add(new Doc("v" + i, randomUnit(random, dim)));
                if(i % 20 == 0)
                {
                    map.add(new Doc("none" + i, null));
                }
            }
            final float[] known = randomUnit(random, dim);
            knownId = map.add(new Doc("known", known));

            index.persistToDisk();
            storage.storeRoot();

            final VectorSearchResult<Doc> result = index.search(known, 5);
            assertFalse(result.isEmpty());
            assertTrue(result.stream().allMatch(e -> index.getVector(e.entityId()) != null),
                "no null-embedding entity may appear in PQ search results");
        }

        // Reload: PQ-compressed on-disk index round-trips with nulls present.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.Category()).get("embeddings");
            assertNotNull(index.getVector(knownId));
            assertFalse(index.search(map.get(knownId), 5).isEmpty());
        }
    }

    /**
     * Regression for the RAVV {@code size()} contract: in computed mode the graph ordinal is the
     * source entity id, so with null embeddings (sparse ordinals) the highest ordinal exceeds the
     * non-null vector count. When PQ compression is actually trained, {@code ProductQuantization
     * .encodeAll(ravv)} builds a dense {@code PQVectors} of length {@code ravv.size()}; if
     * {@code size()} is the count rather than the ordinal upper bound, the FusedPQ write / PQ search
     * index that dense array by graph ordinal and blow past its end. Unlike
     * {@link #pqCompressionExcludesNulls}, this test explicitly triggers training so the FusedPQ path
     * is exercised.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @SuppressWarnings("unchecked")
    void pqCompressionWithSparseOrdinals_trainedAndSearched(@TempDir final Path dir)
    {
        final int dim = 16;
        final Path indexDir = dir.resolve("index");
        final Random random = new Random(11);

        final VectorIndexConfiguration pqConfig = VectorIndexConfiguration.builder()
            .dimension(dim)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .pqSubspaces(dim / 4)
            .build();

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", pqConfig, new NullableComputedVectorizer()))
        {
            // >256 vectored entities so PQ training triggers, with null embeddings interspersed so the
            // highest graph ordinal (a real node) exceeds the non-null vector count.
            for(int i = 0; i < 300; i++)
            {
                map.add(new Doc("v" + i, randomUnit(random, dim)));
                if(i % 20 == 0)
                {
                    map.add(new Doc("none" + i, null)); // sparse ordinal gaps
                }
            }
            final float[] known = randomUnit(random, dim);
            final long knownId = map.add(new Doc("known", known)); // highest ordinal, a real node

            // Force PQ training so persist writes FusedPQ (the path that indexes the dense PQ array by
            // ordinal). Before the size() fix this threw IndexOutOfBoundsException while encoding /
            // writing the highest-ordinal node (ordinal > non-null count); it must now complete,
            // proving every graph ordinal up to highestUsedId is PQ-encoded and written.
            ((VectorIndex.Internal<Doc>)index).trainCompressionIfNeeded();
            index.persistToDisk();

            // Approximate PQ search: assert it runs and yields only real (non-null) entities. (Exact
            // top-1 identity isn't guaranteed under lossy PQ, so we don't assert the query is #1.)
            final VectorSearchResult<Doc> result = index.search(known, 5);
            assertFalse(result.isEmpty(), "PQ search over sparse ordinals must return results");
            assertTrue(result.stream().allMatch(e -> index.getVector(e.entityId()) != null),
                "no null-embedding entity may appear in PQ search results");
            assertNotNull(index.getVector(knownId), "the highest-ordinal node remains stored and resolvable");
        }
    }

    private static float[] randomUnit(final Random random, final int dim)
    {
        final float[] v = new float[dim];
        float norm = 0;
        for(int i = 0; i < dim; i++)
        {
            v[i] = random.nextFloat() * 2 - 1;
            norm += v[i] * v[i];
        }
        norm = (float)Math.sqrt(norm);
        for(int i = 0; i < dim; i++)
        {
            v[i] /= norm;
        }
        return v;
    }

    // sanity: equals/hashCode of stored entries tolerate null vectors (defensive)
    @Test
    void vectorEntryToleratesNull()
    {
        final VectorEntry a = new VectorEntry(1L, null);
        final VectorEntry b = new VectorEntry(1L, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertTrue(a.toString().contains("sourceEntityId=1"));
        assertNull(a.vector);
        assertFalse(Arrays.equals(new float[]{1f}, a.vector));
    }
}
