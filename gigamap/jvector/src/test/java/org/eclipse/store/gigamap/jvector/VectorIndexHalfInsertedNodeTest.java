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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

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

    static class Doc
    {
        final int     no;
        float[] vector;

        Doc(final int no, final float[] vector)
        {
            this.no     = no;
            this.vector = vector;
        }

        Doc(final int no)
        {
            this.no     = no;
            this.vector = position(no);
        }
    }

    static float[] position(final int no)
    {
        return new float[]{1.0f + no, 1.0f + no % 7, no * 0.5f, 1.0f + no % 3};
    }

    static class NeighbourFlakyVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
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

    private static Set<Long> foundIds(final VectorIndex<Doc> index)
    {
        final Set<Long> ids = new TreeSet<>();
        index.search(position(0), COUNT * 4).toList().forEach(e -> ids.add(e.entityId()));
        return ids;
    }

    private static boolean foundExactly(final VectorIndex<Doc> index, final Doc doc, final long id)
    {
        return index.search(doc.vector, 3).toList().stream().anyMatch(e -> e.entityId() == id && e.score() > 0.99f);
    }

    @SuppressWarnings("unchecked")
    private static <T> T internalState(final VectorIndex<?> index, final String fieldName)
    {
        try
        {
            final Field field = VectorIndex.Default.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T)field.get(index);
        }
        catch(final ReflectiveOperationException e)
        {
            throw new AssertionError("cannot read VectorIndex.Default." + fieldName, e);
        }
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

        final Doc  next   = new Doc(COUNT + 1);
        final long nextId = map.add(next);
        assertNotEquals(COUNT, nextId, "the rolled-back add's id was handed out again");
        assertTrue(foundExactly(index, next, nextId), "the entity added after the failed add is not found");
        assertEquals(COUNT + 1, foundIds(index).size(), "entities missing: found " + foundIds(index));
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
        assertEquals(COUNT + 1, foundIds(index).size(), "entities missing: found " + foundIds(index));
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
            assertThrows(RuntimeException.class, () -> foundIds(index), "the failed rebuild was not loud");
            failPoison = false;

            assertEquals(COUNT, foundIds(index).size(), "the retried rebuild lost an entity: " + foundIds(index));
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

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> foundIds(index),
            "a search answered from a graph known to be incomplete");
        assertTrue(e.getMessage().contains("incomplete"), e.getMessage());

        map.reindex();
        final long withVectors = map.size() - (map.get(noVectorId).vector == null ? 1 : 0);
        assertEquals(withVectors, foundIds(index).size(), "entities missing after reindex(): " + foundIds(index));
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

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> foundIds(index),
            "a search answered from the truncated graph of a failed reindex");
        assertTrue(e.getMessage().contains("incomplete"), e.getMessage());

        map.reindex();
        assertEquals(COUNT, foundIds(index).size(), "entities missing after the second reindex(): " + foundIds(index));
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
        assertThrows(IllegalStateException.class, () -> foundIds(index), "a search answered from the incomplete graph");

        assertDoesNotThrow(() -> map.removeById(COUNT), "removing the entity threw the retirement meant for a rollback");
        map.reindex();
        assertEquals(COUNT, foundIds(index).size(), "entities missing after reindex(): " + foundIds(index));
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
            return d.vector == null ? null : d.vector.clone();
        }

        @Override
        public boolean allowsNullVectors()
        {
            return true;
        }
    }

    private static void failOneInsertion(final VectorIndex<?> index, final int ordinal)
    {
        final java.util.concurrent.atomic.AtomicBoolean armed = new java.util.concurrent.atomic.AtomicBoolean(true);
        final java.util.function.IntConsumer hook = o ->
        {
            if(o == ordinal && armed.getAndSet(false))
            {
                throw new IllegalStateException("simulated engine failure inserting ordinal " + o);
            }
        };
        try
        {
            final Field field = VectorIndex.Default.class.getDeclaredField("graphInsertTestHook");
            field.setAccessible(true);
            field.set(index, hook);
        }
        catch(final ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }

    private static void awaitWorker(final VectorIndex<?> index)
    {
        final BackgroundTaskManager manager = internalState(index, "backgroundTaskManager");
        assertNotNull(manager, "precondition: a background task manager exists");
        manager.drainQueue();
        manager.drainQueue();
    }

    @Test
    void failedComputedReindexKeepsEveryVectorForTheRepair()
    {
        final GigaMap<Doc>     map   = populatedMap();
        // a background optimization interval creates the manager that runs the repair
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", VectorIndexConfiguration.builder().dimension(4).similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
                .optimizationIntervalMs(60_000).build(), new ComputedVectorizer());
        assertEquals(COUNT, foundIds(index).size(), "precondition: complete before the reindex");

        failOneInsertion(index, COUNT / 2); // the re-add fails at this ordinal, once
        assertThrows(RuntimeException.class, map::reindex, "precondition: the reindex failed during an insertion");
        awaitWorker(index); // the repair rebuilds from the vector store

        assertEquals(COUNT, map.size(), "precondition: a failed reindex keeps the entities");
        assertEquals(COUNT, foundIds(index).size(),
            "the repair rebuilt a truncated index: the failed reindex had not stored every vector first; found " + foundIds(index));
    }
}
