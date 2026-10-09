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
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.eclipse.store.gigamap.jvector.VectorIndexTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A graph insertion that throws partway (jvector registers the node before it links it) leaves a half-inserted node
 * that the idempotent add treats as present. Two paths turned that into an entity that is silently never found: the
 * retry of a failed lazy rebuild after a load, and the reuse of a rolled-back add's id by the next entity.
 * <p>
 * Failure model: an embedded vectorizer whose vector for one entity ({@link #POISON}) is valid on the entity's own
 * insertion but throws when a later insertion scores that entity as a neighbour.
 */
class VectorIndexHalfInsertedNodeTest
{
    private static final int COUNT  = 10;
    private static final int POISON = 3;

    /** 0: never fail; positive: fail every call for POISON after the first {@code skip} calls. */
    private static volatile boolean      failPoison;
    private static final    AtomicInteger poisonCalls = new AtomicInteger();
    private static volatile int          skipCalls;
    /** when set, the poison failure is an {@link Error} instead of an exception (GigaMap rolls back on Exception only) */
    private static volatile boolean      failWithError;
    /** the entity whose next vectorization throws an {@link Error}, once, on whichever thread; -1: none */
    private static volatile int          errorOnceFor = -1;

    static class NeighbourFlakyVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            if(entity.no == errorOnceFor)
            {
                errorOnceFor = -1;
                throw new AssertionError("simulated engine failure vectorizing entity " + entity.no);
            }
            if(failPoison && entity.no == POISON && poisonCalls.getAndIncrement() >= skipCalls)
            {
                if(failWithError)
                {
                    throw new AssertionError("simulated engine failure while scoring entity " + entity.no);
                }
                throw new IllegalStateException("simulated flaky embedding lookup for entity " + entity.no);
            }
            return entity.vector;
        }

        @Override
        public boolean allowsNullVectors()
        {
            return true;
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

    @AfterEach
    void reset()
    {
        failPoison = false;
        skipCalls  = 0;
        failWithError = false;
        errorOnceFor  = -1;
        poisonCalls.set(0);
    }

    private static VectorIndexConfiguration inMemory()
    {
        return VectorIndexConfiguration.builder()
            .dimension(4)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .build();
    }

    private static GigaMap<Doc> populatedMap()
    {
        final GigaMap<Doc> map = GigaMap.New();
        for(int i = 0; i < COUNT; i++)
        {
            map.add(new Doc(i));
        }
        return map;
    }

    private static boolean retiredTheId(final Throwable failure)
    {
        return Arrays.stream(failure.getSuppressed()).anyMatch(s -> s.getMessage() != null && s.getMessage().contains("retired"));
    }

    /**
     * A synchronous add whose insertion fails is rolled back. Its id must be retired, not reclaimed: the next entity
     * would get the same id, the idempotent add would find the half-inserted node and keep it, and the entity would
     * never be found. The retirement is reported as a suppressed exception of the insertion failure.
     */
    @Test
    void failedSynchronousAddRetiresItsId()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", inMemory(), new NeighbourFlakyVectorizer());

        failPoison = true; // the new entity's own vector is fine; scoring POISON as a neighbour fails
        final RuntimeException failure = assertThrows(RuntimeException.class, () -> map.add(new Doc(COUNT)));
        failPoison = false;
        assertEquals(COUNT, map.size(), "precondition: the failed add was rolled back");
        assertTrue(retiredTheId(failure), "the rollback did not retire the id: " + Arrays.toString(failure.getSuppressed()));
        assertFalse((boolean)internalState(index, "halfInsertedNodePending"), "the retirement was not consumed");
        assertFalse((boolean)internalState(index, "graphIncomplete"),
            "a rolled-back add flagged the graph incomplete although its deleted node is purged by the persist's cleanup");

        final Doc  next   = new Doc(COUNT + 1);
        final long nextId = map.add(next);
        assertNotEquals(COUNT, nextId, "the rolled-back add's id was handed out again");
        assertTrue(foundExactly(index, next, nextId), "the entity added after the failed add is not found");
        assertEquals(COUNT + 1, foundIds(index, COUNT).size(), "entities missing: found " + foundIds(index, COUNT));
    }

    /**
     * The same for {@code addAll()}: GigaMap rolls the whole batch back, and the ids from the half-inserted one
     * upwards are retired.
     */
    @Test
    void failedAddAllRetiresTheIds()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", inMemory(), new NeighbourFlakyVectorizer());

        failPoison = true;
        final RuntimeException failure = assertThrows(RuntimeException.class,
            () -> map.addAll(List.of(new Doc(COUNT), new Doc(COUNT + 1))));
        failPoison = false;
        assertEquals(COUNT, map.size(), "precondition: the failed addAll was rolled back");
        assertTrue(retiredTheId(failure), "the rollback did not retire the ids: " + Arrays.toString(failure.getSuppressed()));

        final Doc  next   = new Doc(COUNT + 2);
        final long nextId = map.add(next);
        assertTrue(nextId > COUNT, "an id of the rolled-back batch was handed out again: " + nextId);
        assertTrue(foundExactly(index, next, nextId), "the entity added after the failed addAll is not found");
        assertEquals(COUNT + 1, foundIds(index, COUNT).size(), "entities missing: found " + foundIds(index, COUNT));
    }

    /**
     * A reindex re-adds the entities through the same insertions, but GigaMap does not roll a failed reindex back:
     * the entity whose insertion failed stays. The record of that failure must not retire its id when the entity is
     * removed later on purpose.
     */
    @Test
    void failedReindexDoesNotRetireALaterRemoval()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", inMemory(), new NeighbourFlakyVectorizer());

        // the validation pass and POISON's own insertion succeed, the first neighbour scoring of POISON fails
        skipCalls  = 2;
        failPoison = true;
        assertThrows(RuntimeException.class, map::reindex, "precondition: the reindex failed during an insertion");
        failPoison = false;
        assertEquals(COUNT, map.size(), "precondition: a failed reindex keeps the entities");
        assertFalse((boolean)internalState(index, "halfInsertedNodePending"), "the failed reindex left a retirement pending");

        for(long id = 0; id < COUNT; id++)
        {
            final long removing = id;
            assertDoesNotThrow(() -> map.removeById(removing), "removing entity " + removing + " after a failed reindex threw");
        }
        assertEquals(0, map.size());
    }

    /**
     * An in-memory index is rebuilt from the entities on first access after a load. The rebuild collects the vectors
     * first (POISON's own call succeeds), then inserts them, and scoring POISON as a neighbour fails. The failure is
     * loud. The retry, once the vectorizer works again, must start from an empty builder: inserting into the same
     * builder skips the half-inserted ordinal as present, and that entity is silently lost.
     */
    @Test
    void failedLazyRebuildIsRetriedFromAnEmptyBuilder(@TempDir final Path tempDir)
    {
        try(EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
        {
            final GigaMap<Doc> map = populatedMap();
            storage.setRoot(map);
            storage.storeRoot();
            map.index().register(VectorIndices.Category()).add("emb", inMemory(), new NeighbourFlakyVectorizer());
            map.store();
        }

        try(EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.class).get("emb");

            skipCalls  = 1; // the collection pass succeeds, the first neighbour scoring of POISON fails
            failPoison = true;
            assertThrows(RuntimeException.class, () -> foundIds(index, COUNT), "the failed rebuild was not loud");
            failPoison = false;

            assertEquals(COUNT, foundIds(index, COUNT).size(), "the retried rebuild lost an entity: " + foundIds(index, COUNT));
        }
    }

    /**
     * A synchronous update whose graph insertion fails partway (here: an entity gains an embedding, and scoring a
     * neighbour throws) leaves a half-inserted node for an entity that stays in the map; GigaMap does not roll an
     * update back, so nothing retires the id. The index must record the graph as incomplete: without a background
     * thread, searches throw until {@code reindex()} rebuilds the graph, which then finds the entity.
     */
    @Test
    void failedSynchronousUpdateFlagsTheGraphIncomplete()
    {
        final GigaMap<Doc> map = populatedMap();
        final long noVectorId = map.add(new Doc(COUNT, null)); // no embedding yet
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", inMemory(), new NeighbourFlakyVectorizer());

        final long modCountBefore = internalState(index, "structuralModCount");
        failPoison = true; // the entity's own vector is fine; scoring POISON as a neighbour fails
        assertThrows(RuntimeException.class, () -> map.set(noVectorId, new Doc(COUNT)),
            "precondition: the insertion of the new embedding failed");
        failPoison = false;
        assertTrue((boolean)internalState(index, "graphIncomplete"), "the failed insertion was not recorded");
        assertTrue((long)internalState(index, "structuralModCount") > modCountBefore,
            "a failed update did not move the crash-restart witness although the entity carries the new vector");

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> foundIds(index, COUNT),
            "a search answered from a graph known to be incomplete");
        assertTrue(e.getMessage().contains("incomplete"), e.getMessage());

        map.reindex();
        final long withVectors = map.size() - (map.get(noVectorId).vector == null ? 1 : 0);
        assertEquals(withVectors, foundIds(index, COUNT).size(), "entities missing after reindex(): " + foundIds(index, COUNT));
    }

    /**
     * A reindex is not rolled back: a failed re-add leaves the entities in place and the graph truncated at the
     * failing one. Without a background thread nothing can repair that, so the index must say so from
     * {@code search()} until the next successful reindex.
     */
    @Test
    void failedReindexWithoutABackgroundThreadMakesSearchesThrow()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", inMemory(), new NeighbourFlakyVectorizer());

        skipCalls  = 2; // validation pass and POISON's own insertion succeed, the first neighbour scoring fails
        failPoison = true;
        assertThrows(RuntimeException.class, map::reindex, "precondition: the reindex failed during an insertion");
        failPoison = false;
        assertNotNull(internalState(index, "graphRepairFailure"), "the failed reindex was not latched");

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> foundIds(index, COUNT),
            "a search answered from the truncated graph of a failed reindex");
        assertTrue(e.getMessage().contains("incomplete"), e.getMessage());

        map.reindex();
        assertEquals(COUNT, foundIds(index, COUNT).size(), "entities missing after the second reindex(): " + foundIds(index, COUNT));
    }

    /**
     * GigaMap rolls an add back on {@code Exception} only. An {@link Error} out of the graph insertion leaves the
     * entity in the map with a half-inserted node: no retirement may stay armed (a later legitimate removal would
     * throw), and the graph must be recorded as incomplete instead.
     */
    @Test
    void errorDuringAnAddIsRecordedWithoutRetiringTheId()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", inMemory(), new NeighbourFlakyVectorizer());

        failPoison    = true;
        failWithError = true;
        assertThrows(AssertionError.class, () -> map.add(new Doc(COUNT)));
        failPoison    = false;
        failWithError = false;
        assertEquals(COUNT + 1, map.size(), "precondition: GigaMap does not roll an Error back");
        assertFalse((boolean)internalState(index, "halfInsertedNodePending"), "a retirement stayed armed for an entity that stays");
        assertNotNull(internalState(index, "graphRepairFailure"), "the half-inserted node of a live entity was not recorded");
        assertThrows(IllegalStateException.class, () -> foundIds(index, COUNT), "a search answered from the incomplete graph");

        assertDoesNotThrow(() -> map.removeById(COUNT), "removing the entity threw the retirement meant for a rollback");
        map.reindex();
        assertEquals(COUNT, foundIds(index, COUNT).size(), "entities missing after reindex(): " + foundIds(index, COUNT));
    }

    /**
     * Computed mode: the index stores a copy of each vector, and that store is what a repair rebuilds from. A
     * {@code reindex()} whose graph insertion fails partway must already have stored every vector, or the repair
     * rebuilds a truncated index and calls it complete.
     */
    static class ComputedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc d)
        {
            if(failPoison && d.no == POISON)
            {
                if(failWithError)
                {
                    throw new AssertionError("simulated engine failure vectorizing entity " + d.no);
                }
                throw new IllegalStateException("simulated vectorizer failure for entity " + d.no);
            }
            return d.vector == null ? null : d.vector.clone();
        }

        @Override
        public boolean allowsNullVectors()
        {
            return true;
        }
    }

    @Test
    void failedComputedReindexKeepsEveryVectorForTheRepair()
    {
        final GigaMap<Doc>     map   = populatedMap();
        // a background optimization interval creates the manager that runs the repair
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", VectorIndexConfiguration.builder().dimension(4).similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
                .optimizationIntervalMs(60_000).build(), new ComputedVectorizer());
        assertEquals(COUNT, foundIds(index, COUNT).size(), "precondition: complete before the reindex");

        failOneInsertion(index, COUNT / 2); // the re-add fails at this ordinal, once
        assertThrows(RuntimeException.class, map::reindex, "precondition: the reindex failed during an insertion");
        awaitWorker(index); // the repair rebuilds from the vector store

        assertEquals(COUNT, map.size(), "precondition: a failed reindex keeps the entities");
        assertEquals(COUNT, foundIds(index, COUNT).size(),
            "the repair rebuilt a truncated index: the failed reindex had not stored every vector first; found " + foundIds(index, COUNT));
    }

    /** embedded, never fails: the sibling index in the fan-out tests */
    static class PlainVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc d)
        {
            return d.vector;
        }

        @Override
        public boolean allowsNullVectors()
        {
            return true;
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

    private static VectorIndexConfiguration withManager()
    {
        // a background optimization interval creates the manager that runs the repair
        return VectorIndexConfiguration.builder()
            .dimension(4)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .optimizationIntervalMs(60_000)
            .build();
    }

    /**
     * A computed set() writes the new vector to the store before the graph insertion. When the inline insertion
     * throws, GigaMap rejects the replacement and keeps the old entity, so the store must hold the old vector again:
     * the repair rebuilds the graph from the store, and would otherwise find the old entity by a vector it never
     * had, with the index reporting healthy.
     */
    @Test
    void failedComputedSetRestoresTheStoredVector()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", withManager(), new ComputedVectorizer());
        final long id       = COUNT / 2;
        final Doc  old      = map.get(id);
        final Doc  replaced = new Doc((int)id, position(50));

        failOneInsertion(index, (int)id);
        assertThrows(RuntimeException.class, () -> map.set(id, replaced), "precondition: the set failed during the insertion");
        assertSame(old, map.get(id), "precondition: GigaMap keeps the old entity");
        awaitWorker(index); // the repair rebuilds from the vector store

        assertArrayEquals(old.vector, index.getVector(id), "the store kept the vector of the rejected replacement");
        assertTrue(foundExactly(index, old, id), "the old entity is not found by its own vector");
        assertFalse(foundExactly(index, replaced, id), "the old entity is found by the rejected replacement's vector");
    }

    /**
     * An in-place update is the opposite case: GigaMap cannot reject the mutation, it retains the mutated entity when
     * the index fails, so the store must keep the new vector. Restoring the old one would make the repair index a
     * vector the entity no longer has.
     */
    @Test
    void failedComputedApplyKeepsTheNewVectorInTheStore()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", withManager(), new ComputedVectorizer());
        final long    id        = COUNT / 2;
        final float[] newVector = position(70);

        failOneInsertion(index, (int)id);
        assertThrows(RuntimeException.class, () -> map.apply(id, doc ->
        {
            doc.vector = newVector;
            return null;
        }), "precondition: the apply failed during the insertion");
        final Doc mutated = map.get(id);
        assertArrayEquals(newVector, mutated.vector, "precondition: GigaMap retains the mutated entity");
        awaitWorker(index); // the repair rebuilds from the vector store

        assertArrayEquals(newVector, index.getVector(id), "the store lost the vector of the retained mutation");
        assertTrue(foundExactly(index, mutated, id), "the mutated entity is not found by its new vector");
    }

    /**
     * A computed set() to no embedding removes the stored vector before the graph op. When that op throws, GigaMap
     * rejects the replacement and keeps the old entity, so the store must hold its vector again, like the other two
     * transitions restore theirs.
     */
    @Test
    void failedComputedSetToNullKeepsTheStoredVector()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", withManager(), new ComputedVectorizer());
        final long id  = COUNT / 2;
        final Doc  old = map.get(id);

        failOneDeletion(index, (int)id);
        assertThrows(RuntimeException.class, () -> map.set(id, new Doc((int)id, null)), "precondition: the set failed during the deletion");
        assertSame(old, map.get(id), "precondition: GigaMap keeps the old entity");
        awaitWorker(index); // the repair rebuilds from the vector store

        assertArrayEquals(old.vector, index.getVector(id), "the store lost the vector of the retained entity");
        assertTrue(foundExactly(index, old, id), "the retained entity is not found by its vector");
    }

    /**
     * The in-place counterpart: the mutation to no embedding is retained, so the store deletion stands; the restore
     * must not run here. (A node without a store entry cannot score, so the search assertion holds with or without the
     * graph record; what this guards is the store.)
     */
    @Test
    void failedComputedApplyToNullDropsTheStoredVector()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", withManager(), new ComputedVectorizer());
        final long id       = COUNT / 2;
        final Doc  previous = new Doc((int)id, map.get(id).vector.clone()); // a probe with the vector before the mutation

        failOneDeletion(index, (int)id);
        assertThrows(RuntimeException.class, () -> map.apply(id, doc ->
        {
            doc.vector = null;
            return null;
        }), "precondition: the apply failed during the deletion");
        assertNull(map.get(id).vector, "precondition: GigaMap retains the mutated entity");
        awaitWorker(index);

        assertNull(index.getVector(id), "the store kept a vector the entity no longer has");
        assertFalse(foundExactly(index, previous, id), "an entity without an embedding is found by its previous vector");
    }

    /**
     * The computed re-add of a reindex stores every vector before any graph work. A store write that throws halfway
     * leaves a truncated store; the repair the failure requests rebuilds the graph from it, so the index must stay
     * loud until a reindex() has stored every vector.
     */
    @Test
    void failedReindexStoreWriteIsLoudUntilTheNextReindex()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", withManager(), new ComputedVectorizer());
        // The store is a GigaMap that outlives the generations, so an index on it fails the write of one entry.
        final GigaMap<VectorEntry>                      store = internalState(index, "vectorStore");
        final java.util.concurrent.atomic.AtomicBoolean armed = new java.util.concurrent.atomic.AtomicBoolean();
        store.index().bitmap().add(new IndexerString.Abstract<VectorEntry>()
        {
            @Override
            protected String getString(final VectorEntry entry)
            {
                if(armed.get() && entry.sourceEntityId == COUNT / 2)
                {
                    throw new IllegalStateException("simulated store failure for entity " + entry.sourceEntityId);
                }
                return String.valueOf(entry.sourceEntityId);
            }
        });

        armed.set(true);
        assertThrows(RuntimeException.class, map::reindex, "precondition: the reindex failed during a store write");
        awaitWorker(index); // the repair rebuilds from the truncated store
        assertEquals(COUNT, map.size(), "precondition: a failed reindex keeps the entities");

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> foundIds(index, COUNT),
            "a search answered from a graph rebuilt from a truncated store");
        assertTrue(e.getMessage().contains("reindex"), e.getMessage());

        armed.set(false);
        map.reindex();
        assertEquals(COUNT, foundIds(index, COUNT).size(), "entities missing after reindex()");
    }

    /**
     * The same for an entity without an embedding: the store must not keep the entry the rejected replacement added.
     */
    @Test
    void failedComputedSetFromNullLeavesNoStoredVector()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", withManager(), new ComputedVectorizer());
        final long id       = map.add(new Doc(COUNT, null)); // opted-in: no embedding
        final Doc  replaced = new Doc(COUNT, position(60));
        assertNull(index.getVector(id), "precondition: nothing stored for an entity without an embedding");

        failOneInsertion(index, (int)id);
        assertThrows(RuntimeException.class, () -> map.set(id, replaced), "precondition: the set failed during the insertion");
        awaitWorker(index);

        assertNull(index.getVector(id), "the store kept the vector of the rejected replacement");
        assertFalse(foundExactly(index, replaced, id), "an entity without an embedding is found by the rejected replacement's vector");
        assertEquals(COUNT, foundIds(index, COUNT).size(), "entities missing after the repair: found " + foundIds(index, COUNT));
    }

    /**
     * An Error from the vectorizer on add() escapes before the graph work. GigaMap rolls back on Exception only, so
     * the entity stays in the map, and the index must record that it holds nothing for it. Embedded, the repair
     * re-vectorizes the entities and recovers it.
     */
    @Test
    void errorVectorizingAnAddedEntityIsRepairedFromTheEntities()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", VectorIndexConfiguration.builder().dimension(4).similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
                .eventualIndexing(true).build(), new NeighbourFlakyVectorizer());
        awaitWorker(index);

        errorOnceFor = COUNT; // the new entity's own vectorization, on the caller's thread
        assertThrows(AssertionError.class, () -> map.add(new Doc(COUNT)));
        assertEquals(COUNT + 1, map.size(), "precondition: GigaMap keeps the entity after an Error");
        awaitWorker(index);

        assertEquals(COUNT + 1, foundIds(index, COUNT).size(),
            "the entity added with an Error is silently missing: found " + foundIds(index, COUNT));
    }

    /**
     * Computed, the store never received the entity's vector, so no rebuild from the store can recover it: the index
     * is loud until reindex(), which re-vectorizes the entities.
     */
    @Test
    void errorVectorizingAnAddedEntityIsLoudUntilReindexComputed()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", inMemory(), new ComputedVectorizer());

        failPoison    = true;
        failWithError = true;
        assertThrows(AssertionError.class, () -> map.add(new Doc(POISON)));
        failPoison = false;
        assertEquals(COUNT + 1, map.size(), "precondition: GigaMap keeps the entity after an Error");

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> foundIds(index, COUNT),
            "a search answered although an entity has no vector in the store");
        assertTrue(e.getMessage().contains("reindex"), e.getMessage());

        map.reindex();
        assertEquals(COUNT + 1, foundIds(index, COUNT).size(), "entities missing after reindex()");
    }

    /**
     * GigaMap clears the slot before it asks the indices to remove the entity. A synchronous graph deletion that
     * throws therefore leaves a live node for an entity the map no longer has; the failure must be recorded so the
     * repair drops it, instead of the index reporting itself healthy with a ghost in its graph. Embedded: the ghost
     * node is scored through the parent map, where the entity is gone. (Computed, the same path is taken, but a node
     * without a store entry cannot score, so the search cannot observe the difference.)
     */
    @Test
    void failedDeletionOfARemovedEntityIsRepaired()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", withManager(), new NeighbourFlakyVectorizer());
        final long id = COUNT / 2;

        failOneDeletion(index, (int)id);
        assertThrows(RuntimeException.class, () -> map.removeById(id), "precondition: the removal failed during the deletion");
        assertNull(map.get(id), "precondition: GigaMap has removed the entity");
        awaitWorker(index); // the repair rebuilds from the entities

        final java.util.Set<Long> found = foundIds(index, COUNT);
        assertFalse(found.contains(id), "the removed entity is still found: " + found);
        assertEquals(COUNT - 1, found.size(), "entities missing after the repair: " + found);
    }

    /**
     * An in-place update whose vectorization fails: GigaMap retains the mutated entity (an indexer failure does not
     * reject a mutation), so the computed store keeps a vector the entity no longer has. The index must be loud about
     * it instead of answering with the stale vector, until a reindex() vectorizes the entity again.
     */
    @Test
    void failedVectorizationOfARetainedMutationIsLoudUntilReindex()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", withManager(), new ComputedVectorizer());
        final long    id        = POISON;
        final float[] newVector = position(70);

        failPoison = true; // the vectorizer rejects POISON from now on
        assertThrows(RuntimeException.class, () -> map.apply(id, doc ->
        {
            doc.vector = newVector;
            return null;
        }), "precondition: the apply failed during the vectorization");
        assertArrayEquals(newVector, map.get(id).vector, "precondition: GigaMap retains the mutated entity");

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> foundIds(index, COUNT),
            "a search answered with a stored vector the entity no longer has");
        assertTrue(e.getMessage().contains("reindex"), e.getMessage());

        failPoison = false;
        map.reindex();
        awaitWorker(index);
        assertTrue(foundExactly(index, map.get(id), id), "the entity is not found by its new vector after reindex()");
    }

    /**
     * The same failure on a persisted embedded index, across a restart. An update moves neither the entity count
     * nor the highest id, so unless the structural witness moves, the restart accepts the persisted graph, in which
     * the mutated entity has no node (it had no embedding before), and the latch of the session is gone with it.
     */
    @Test
    void failedVectorizationOfARetainedMutationMovesTheRestartWitness(@TempDir final Path dir)
    {
        final Path storageDir = dir.resolve("storage");
        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            storage.storeRoot();
            for(int i = 0; i < COUNT; i++)
            {
                map.add(new Doc(i, i == POISON ? null : position(i))); // POISON without an embedding
            }
            final VectorIndex<Doc> index = map.index().register(VectorIndices.Category()).add("emb",
                VectorIndexConfiguration.builder().dimension(4).similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
                    .onDisk(true).indexDirectory(dir.resolve("index")).build(),
                new NeighbourFlakyVectorizer());
            index.persistToDisk();
            map.store();

            failPoison = true; // the vectorizer rejects POISON for the rest of the session
            assertThrows(RuntimeException.class, () -> map.apply((long)POISON, doc ->
            {
                doc.vector = position(POISON);
                return null;
            }), "precondition: the apply failed during the vectorization");
            assertArrayEquals(position(POISON), map.get(POISON).vector, "precondition: GigaMap retains the mutated entity");
            map.store();
        }

        failPoison = false; // the cause is fixed before the restart
        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.class).get("emb");
            assertArrayEquals(position(POISON), map.get(POISON).vector, "precondition: the mutation is persisted");
            assertTrue(foundExactly(index, map.get(POISON), POISON),
                "the restarted index accepted the persisted graph, in which the mutated entity has no node");
        }
    }

    /**
     * The second vectorization of an embedded in-place update, the one that classifies a null new vector against the
     * old one, can fail like the first. GigaMap retains the mutation, so the failure must be recorded the same way,
     * or the old node stays served for an entity that has no embedding any more.
     */
    @Test
    void failedClassificationOfARetainedNullMutationIsLoudUntilReindex()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", withManager(), new NeighbourFlakyVectorizer());
        final Doc before = new Doc(POISON, position(POISON)); // a probe with the vector the entity had

        failPoison = true;
        skipCalls  = 1; // the first call (the new, null vector) passes; the second, classifying one throws
        assertThrows(RuntimeException.class, () -> map.apply((long)POISON, doc ->
        {
            doc.vector = null;
            return null;
        }), "precondition: the apply failed during the classification");
        assertNull(map.get(POISON).vector, "precondition: GigaMap retains the mutated entity");
        awaitWorker(index); // the repair fails too: the vectorizer still rejects POISON

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> foundIds(index, COUNT),
            "a search answered although the mutated entity's old node was never recorded");
        assertTrue(e.getMessage().contains("incomplete"), e.getMessage());

        failPoison = false;
        map.reindex();
        awaitWorker(index);
        assertFalse(foundExactly(index, before, POISON), "an entity without an embedding is found by its previous vector");
        assertEquals(COUNT - 1, foundIds(index, COUNT).size(), "entities missing after reindex()");
    }

    /**
     * GigaMap rolls an add back on Exception only. An Error from one vector index must not keep its siblings from
     * seeing the entity that stays in the map; the throwing index is loud on its own.
     */
    @Test
    void errorInOneIndexStillAddsTheEntityToItsSiblings()
    {
        final GigaMap<Doc>       map     = populatedMap();
        final VectorIndices<Doc> group   = map.index().register(VectorIndices.Category());
        final VectorIndex<Doc>   failing = group.add("failing", inMemory(), new ComputedVectorizer()); // first in the fan-out
        final VectorIndex<Doc>   sibling = group.add("sibling", inMemory(), new PlainVectorizer());

        failPoison    = true;
        failWithError = true;
        assertThrows(AssertionError.class, () -> map.add(new Doc(POISON)), "precondition: the add threw the Error");
        failPoison = false;
        assertEquals(COUNT + 1, map.size(), "precondition: GigaMap keeps the entity after an Error");

        assertTrue(foundIds(sibling, COUNT + 1).contains((long)COUNT), "the sibling index never saw the retained entity");
        assertThrows(IllegalStateException.class, () -> foundIds(failing, COUNT + 1), "the throwing index is not loud");
    }

    /** The same for a batch: the entities of the batch stay in the map and must reach every sibling. */
    @Test
    void errorInOneIndexStillAddsTheBatchToItsSiblings()
    {
        final GigaMap<Doc>       map     = populatedMap();
        final VectorIndices<Doc> group   = map.index().register(VectorIndices.Category());
        final VectorIndex<Doc>   failing = group.add("failing", inMemory(), new ComputedVectorizer());
        final VectorIndex<Doc>   sibling = group.add("sibling", inMemory(), new PlainVectorizer());

        failPoison    = true;
        failWithError = true;
        assertThrows(AssertionError.class, () -> map.addAll(List.of(new Doc(POISON), new Doc(COUNT + 1))),
            "precondition: the addAll threw the Error");
        failPoison = false;
        assertEquals(COUNT + 2, map.size(), "precondition: GigaMap keeps the batch after an Error");

        final java.util.Set<Long> found = foundIds(sibling, COUNT + 2);
        assertTrue(found.contains((long)COUNT) && found.contains((long)COUNT + 1),
            "the sibling index never saw the retained batch: " + found);
        assertThrows(IllegalStateException.class, () -> foundIds(failing, COUNT + 2), "the throwing index is not loud");
    }

    /**
     * GigaMap removes the entity before it asks the indices. An Error from one index's deletion must not keep its
     * siblings from dropping their nodes for the removed entity.
     */
    @Test
    void errorInOneIndexStillRemovesTheEntityFromItsSiblings()
    {
        final GigaMap<Doc>       map     = populatedMap();
        final VectorIndices<Doc> group   = map.index().register(VectorIndices.Category());
        final VectorIndex<Doc>   failing = group.add("failing", withManager(), new PlainVectorizer());
        final VectorIndex<Doc>   sibling = group.add("sibling", inMemory(), new PlainVectorizer());
        final long               id      = COUNT / 2;

        final java.util.concurrent.atomic.AtomicBoolean armed = new java.util.concurrent.atomic.AtomicBoolean(true);
        ((VectorIndex.Default<?>)failing).graphDeleteTestHook = o ->
        {
            if(o == id && armed.getAndSet(false))
            {
                throw new AssertionError("simulated engine error deleting ordinal " + o);
            }
        };
        assertThrows(AssertionError.class, () -> map.removeById(id), "precondition: the removal threw the Error");
        assertNull(map.get(id), "precondition: GigaMap has removed the entity");

        assertFalse(foundIds(sibling, COUNT).contains(id), "the sibling index still serves the removed entity");
        awaitWorker(failing); // the throwing index repairs itself
        assertFalse(foundIds(failing, COUNT).contains(id), "the throwing index still serves the removed entity");
    }

    /**
     * The vector store clears its slot before it runs its own indices, so a store removal that throws has removed
     * the entry. For a rejected replacement the old entity keeps its vector in the map but not in the store: the
     * store must be latched, or the entity disappears from the next rebuild silently.
     */
    @Test
    void failedStoreRemovalOfARejectedReplacementIsLoudUntilReindex()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", withManager(), new ComputedVectorizer());
        final long id = COUNT / 2;

        final GigaMap<VectorEntry>                      store = internalState(index, "vectorStore");
        final java.util.concurrent.atomic.AtomicBoolean armed = new java.util.concurrent.atomic.AtomicBoolean();
        store.index().bitmap().add(new IndexerString.Abstract<VectorEntry>()
        {
            @Override
            protected String getString(final VectorEntry entry)
            {
                if(armed.get() && entry.sourceEntityId == id)
                {
                    throw new IllegalStateException("simulated store failure removing the entry of " + id);
                }
                return "";
            }
        });

        armed.set(true);
        assertThrows(RuntimeException.class, () -> map.set(id, new Doc((int)id, null)), "precondition: the set failed in the store");
        armed.set(false);
        assertNotNull(map.get(id).vector, "precondition: GigaMap keeps the old entity");
        awaitWorker(index);

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> foundIds(index, COUNT),
            "a search answered although the store lost the retained entity's vector");
        assertTrue(e.getMessage().contains("reindex"), e.getMessage());

        map.reindex();
        awaitWorker(index);
        assertEquals(COUNT, foundIds(index, COUNT).size(), "entities missing after reindex()");
    }
}
