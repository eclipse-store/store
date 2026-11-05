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

import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.graph.similarity.ScoreFunction;
import io.github.jbellis.jvector.graph.similarity.SearchScoreProvider;
import io.github.jbellis.jvector.util.PhysicalCoreExecutor;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A graph insertion that fails inside jVector leaves the node registered but without links: jVector's addGraphNode
 * adds the node to the graph before it scores its neighbours. The vector index must stay consistent with the entity
 * the map ends up holding, for every mutation path:
 * <ul>
 * <li>set/replace: the map keeps the replaced entity, so the index keeps its vector;</li>
 * <li>in-place update: the map keeps the mutated entity, so the index keeps the new vector;</li>
 * <li>add: the map rolls back, and the next add under the reclaimed id is found;</li>
 * <li>eventual indexing: the worker applies a failed operation again; if that fails too, an in-memory index rebuilds
 * its graph from the vector file at the next access, an on-disk one at the next persist.</li>
 * </ul>
 * The failure is injected through jVector's public API, without a hook in the index: a builder over the index's own
 * graph whose score provider fails after the node was registered, the way an engine failure or an OutOfMemoryError
 * does. The rebuild after a load is failed through the existing {@code rebuildWorkerTestHook}.
 * <p>
 * Not covered, as it needs a hook in the index: a worker callback racing a graph drop, and a repair that loses other
 * nodes during the recovery.
 */
class VectorIndexFailedGraphInsertTest
{
    static final int    DIMENSION = 8;
    static final int    COUNT     = 300;
    static final long   ID        = 17;
    static final String INJECTED  = "injected graph engine failure";

    static final VectorTypeSupport VTS = VectorizationProvider.getInstance().getVectorTypeSupport();

    static final class Doc
    {
        float[] vector;

        Doc(final float[] vector)
        {
            this.vector = vector;
        }
    }

    static final class DocVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.vector == null ? null : entity.vector.clone();
        }

        @Override
        public boolean allowsNullVectors()
        {
            return true;
        }
    }

    /** rejects the entity in {@link #rejected} */
    static final class RejectingVectorizer extends Vectorizer<Doc>
    {
        static volatile Doc rejected;

        @Override
        public float[] vectorize(final Doc entity)
        {
            if(entity == rejected)
            {
                throw new IllegalStateException("vectorizer rejects the replacement");
            }
            return entity.vector.clone();
        }
    }

    /**
     * Delegates; the next {@link #remaining} insertions fail when they score their first neighbour, which jVector does
     * after it registered the node.
     */
    static final class FailingScoreProvider implements BuildScoreProvider
    {
        final BuildScoreProvider delegate ;
        final AtomicInteger      remaining = new AtomicInteger();
        final AtomicInteger      fired     = new AtomicInteger();
        volatile boolean         error     ;

        FailingScoreProvider(final BuildScoreProvider delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public boolean isExact()
        {
            return this.delegate.isExact();
        }

        @Override
        public VectorFloat<?> approximateCentroid()
        {
            return this.delegate.approximateCentroid();
        }

        @Override
        public SearchScoreProvider searchProviderFor(final VectorFloat<?> vector)
        {
            final SearchScoreProvider provider = this.delegate.searchProviderFor(vector);
            if(this.remaining.getAndUpdate(n -> Math.max(0, n - 1)) == 0)
            {
                return provider;
            }
            return new SearchScoreProvider()
            {
                @Override
                public ScoreFunction scoreFunction()
                {
                    FailingScoreProvider.this.fired.incrementAndGet();
                    if(FailingScoreProvider.this.error)
                    {
                        throw new OutOfMemoryError(INJECTED);
                    }
                    throw new IllegalStateException(INJECTED);
                }

                @Override
                public ScoreFunction.ExactScoreFunction reranker()
                {
                    return provider.reranker();
                }

                @Override
                public ScoreFunction.ExactScoreFunction exactScoreFunction()
                {
                    return provider.exactScoreFunction();
                }
            };
        }

        @Override
        public SearchScoreProvider searchProviderFor(final int node)
        {
            return this.delegate.searchProviderFor(node);
        }

        @Override
        public SearchScoreProvider diversityProviderFor(final int node)
        {
            return this.delegate.diversityProviderFor(node);
        }
    }

    // fixture

    static Object read(final Object target, final String name)
    {
        try
        {
            final Field field = VectorIndex.Default.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        }
        catch(final ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }

    static void write(final Object target, final String name, final Object value)
    {
        try
        {
            final Field field = VectorIndex.Default.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        }
        catch(final ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }

    /**
     * Replaces the live builder of the index by one over the same graph that fails the next {@code failures} inserts.
     * Its score provider is built like the index's own, from the live vector values. Call while no insert runs.
     */
    static FailingScoreProvider failNextInserts(final VectorIndex<Doc> index, final int failures)
    {
        final VectorIndexConfiguration configuration = index.configuration();
        final GraphIndexBuilder        live          = (GraphIndexBuilder)read(index, "builder");
        final FailingScoreProvider     provider      = new FailingScoreProvider(BuildScoreProvider.randomAccessScoreProvider(
            new NullSafeVectorValues((GenerationVectorValues)read(index, "liveVectorValues"), DIMENSION, VTS),
            io.github.jbellis.jvector.vector.VectorSimilarityFunction.EUCLIDEAN
        ));
        provider.remaining.set(failures);
        write(index, "builder", new GraphIndexBuilder(
            provider,
            DIMENSION,
            (OnHeapGraphIndex)live.getGraph(),
            configuration.beamWidth(),
            configuration.neighborOverflow(),
            configuration.alpha(),
            true,
            PhysicalCoreExecutor.pool(),
            ForkJoinPool.commonPool()
        ));
        return provider;
    }

    /** {@code thrown} is the injected failure, or caused by it, and the provider fired {@code times} */
    static void assertInjected(final Throwable thrown, final FailingScoreProvider provider, final int times)
    {
        assertEquals(times, provider.fired.get(), "injected failures");
        for(Throwable cause = thrown; cause != null; cause = cause.getCause())
        {
            if(INJECTED.equals(cause.getMessage()))
            {
                return;
            }
        }
        throw new AssertionError("not the injected failure", thrown);
    }

    static VectorIndexConfiguration.Builder config()
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN);
    }

    static VectorIndexConfiguration onDisk(final Path dir)
    {
        return config().onDisk(true).indexDirectory(dir).build();
    }

    static float[] vector(final Random random)
    {
        final float[] vector = new float[DIMENSION];
        for(int i = 0; i < DIMENSION; i++)
        {
            vector[i] = (float)random.nextGaussian();
        }
        return vector;
    }

    static GigaMap<Doc> map(final Random random)
    {
        final GigaMap<Doc> map = GigaMap.New();
        for(int i = 0; i < COUNT; i++)
        {
            map.add(new Doc(vector(random)));
        }
        return map;
    }

    static List<Doc> docs(final Random random, final int count)
    {
        final List<Doc> docs = new ArrayList<>();
        for(int i = 0; i < count; i++)
        {
            docs.add(new Doc(vector(random)));
        }
        return docs;
    }

    static VectorIndex<Doc> register(final GigaMap<Doc> map, final VectorIndexConfiguration configuration)
    {
        return map.index().register(VectorIndices.Category()).add("v", configuration, new DocVectorizer());
    }

    static VectorIndex<Doc> index(final GigaMap<Doc> map)
    {
        return map.index().get(VectorIndices.class).get("v");
    }

    /**
     * Whether a search for {@code vector} returns {@code id} as an exact match. An exact match, not any hit: a near
     * neighbour of a query is no evidence that the index holds that vector for the entity.
     */
    static boolean finds(final VectorIndex<Doc> index, final long id, final float[] vector)
    {
        return index.search(vector, 5).toList().stream().anyMatch(entry -> entry.entityId() == id && entry.score() > 0.999f);
    }

    /** every entity with a vector is found by it, and the vector file holds exactly the entities' vectors */
    static void assertConsistent(final GigaMap<Doc> map, final VectorIndex<Doc> index)
    {
        final VectorFile file = (VectorFile)read(index, "vectorFile");
        for(long id = 0; id <= map.highestUsedId(); id++)
        {
            final Doc doc = map.get(id);
            if(doc == null || doc.vector == null)
            {
                continue;
            }
            assertArrayEquals(doc.vector, file.read((int)id), "vector file of entity " + id);
            assertTrue(finds(index, id, doc.vector), "entity " + id + " not found by its vector");
        }
    }

    static void drain(final VectorIndex<Doc> index)
    {
        ((VectorIndex.Default<Doc>)index).backgroundTaskManager.drainQueue();
    }

    static boolean rebuildRequired(final VectorIndex<Doc> index)
    {
        return (Boolean)read(index, "graphRebuildRequired");
    }

    static boolean consolidationRequired(final VectorIndex<Doc> index)
    {
        return (Boolean)read(index, "consolidationRequired");
    }

    // synchronous set / replace

    @Test
    void set_failedInsert_keepsTheReplacedEntitysVector_andARetrySucceeds()
    {
        final Random           random = new Random(1);
        final GigaMap<Doc>     map    = map(random);
        final VectorIndex<Doc> index  = register(map, config().build());
        final Doc              old    = map.get(ID);
        final Doc              neu    = new Doc(vector(random));

        final FailingScoreProvider provider = failNextInserts(index, 1);
        assertInjected(assertThrows(RuntimeException.class, () -> map.set(ID, neu)), provider, 1);

        assertSame(old, map.get(ID));
        assertTrue(finds(index, ID, old.vector), "replaced entity not found by its own vector");
        assertFalse(finds(index, ID, neu.vector), "replaced entity found by the rejected vector");
        assertFalse(rebuildRequired(index), "restored without an escalation");
        assertConsistent(map, index);

        map.set(ID, neu); // must not be skipped as unchanged
        assertTrue(finds(index, ID, neu.vector));
        assertConsistent(map, index);
    }

    @Test
    void set_failedInsertAndFailedRestore_rebuildsTheGraphFromTheVectorFile()
    {
        final Random           random = new Random(2);
        final GigaMap<Doc>     map    = map(random);
        final VectorIndex<Doc> index  = register(map, config().build());
        final Doc              neu    = new Doc(vector(random));

        final FailingScoreProvider provider = failNextInserts(index, 2);
        final RuntimeException     failure  = assertThrows(RuntimeException.class, () -> map.set(ID, neu));
        assertInjected(failure, provider, 2);
        assertEquals(1, failure.getSuppressed().length, "the failed restore is attached");
        assertTrue(rebuildRequired(index));

        assertConsistent(map, index); // the first search rebuilds
        assertFalse(rebuildRequired(index));
        assertFalse(finds(index, ID, neu.vector));
    }

    /** the live graph is empty after a persist, so the half-inserted node is its only node and it has no entry */
    @Test
    void set_failedInsert_onDisk_inAnEmptyLiveGraph_persistsAndSurvivesARestart(@TempDir final Path dir)
    {
        final Random  random  = new Random(3);
        final Path    storage = dir.resolve("storage");
        final float[] oldVector;
        final float[] newVector;
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc> map = map(random);
            manager.setRoot(map);
            manager.storeRoot();
            final VectorIndex<Doc> index = register(map, onDisk(dir.resolve("index")));
            map.store();
            index.persistToDisk();
            oldVector = map.get(ID).vector;
            final Doc neu = new Doc(vector(random));
            newVector = neu.vector;

            final FailingScoreProvider provider = failNextInserts(index, 1);
            final RuntimeException     failure  = assertThrows(RuntimeException.class, () -> map.set(ID, neu));
            assertInjected(failure, provider, 1);
            assertEquals(0, failure.getSuppressed().length, "the restore failed");
            assertFalse(consolidationRequired(index), "restored only by an escalation");
            assertTrue(finds(index, ID, oldVector));
            assertFalse(finds(index, ID, newVector));
            index.persistToDisk(); // a half-inserted node left in the live graph would fail the write
            map.store();
        }
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc>     map   = manager.root();
            final VectorIndex<Doc> index = index(map);
            assertTrue(finds(index, ID, oldVector));
            assertFalse(finds(index, ID, newVector));
            assertConsistent(map, index);
        }
    }

    // synchronous in-place update

    @Test
    void update_failedInsert_keepsTheMutatedEntitysVector()
    {
        final Random           random    = new Random(4);
        final GigaMap<Doc>     map       = map(random);
        final VectorIndex<Doc> index     = register(map, config().build());
        final float[]          oldVector = map.get(ID).vector;
        final float[]          newVector = vector(random);

        final FailingScoreProvider provider = failNextInserts(index, 1);
        assertInjected(assertThrows(RuntimeException.class, () -> map.update(ID, doc -> doc.vector = newVector)), provider, 1);

        assertSame(newVector, map.get(ID).vector, "the map keeps the mutated entity");
        assertTrue(finds(index, ID, newVector));
        assertFalse(finds(index, ID, oldVector));
        assertFalse(rebuildRequired(index), "reinserted without an escalation");
        assertConsistent(map, index);
    }

    @Test
    void update_failedInsertAndFailedReinsert_isEscalated_andTheRebuildFindsTheNewVector()
    {
        final Random           random    = new Random(5);
        final GigaMap<Doc>     map       = map(random);
        final VectorIndex<Doc> index     = register(map, config().build());
        final float[]          newVector = vector(random);

        final FailingScoreProvider provider = failNextInserts(index, 2);
        final RuntimeException     failure  = assertThrows(RuntimeException.class, () -> map.update(ID, doc -> doc.vector = newVector));
        assertInjected(failure, provider, 2);
        assertEquals(1, failure.getSuppressed().length, "the failed reinsert is attached");
        assertTrue(rebuildRequired(index));

        assertTrue(finds(index, ID, newVector), "the rebuild lost the kept vector");
        assertConsistent(map, index);
    }

    /** null to vec changes the stored vector count, which a restart reads */
    @Test
    void update_failedInsert_onDisk_storesTheKeptVector(@TempDir final Path dir)
    {
        final Random  random    = new Random(6);
        final Path    storage   = dir.resolve("storage");
        final float[] newVector = vector(random);
        final long    id;
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc> map = map(random);
            id = map.add(new Doc(null));
            manager.setRoot(map);
            manager.storeRoot();
            final VectorIndex<Doc> index = register(map, onDisk(dir.resolve("index")));
            map.store();
            index.persistToDisk();

            final FailingScoreProvider provider = failNextInserts(index, 1);
            final RuntimeException     failure  = assertThrows(RuntimeException.class, () -> map.update(id, doc -> doc.vector = newVector));
            assertInjected(failure, provider, 1);
            assertEquals(0, failure.getSuppressed().length, "the reinsert failed");
            assertFalse(consolidationRequired(index), "reinserted only by an escalation");
            map.store();
        }
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc>     map   = manager.root();
            final VectorIndex<Doc> index = index(map);
            assertArrayEquals(newVector, map.get(id).vector);
            assertEquals(COUNT + 1, (long)read(index, "vectorCount"), "stored vector count");
            assertTrue(finds(index, id, newVector));
            assertConsistent(map, index);
        }
    }

    /** GigaMap keeps an entity mutated in place in memory after an Error, but does not store it */
    @Test
    void update_failedInsertWithAnError_onDisk_restartMatchesTheStoredEntity(@TempDir final Path dir)
    {
        final Random random  = new Random(7);
        final Path   storage = dir.resolve("storage");
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc> map = map(random);
            manager.setRoot(map);
            manager.storeRoot();
            final VectorIndex<Doc> index = register(map, onDisk(dir.resolve("index")));
            map.store();
            index.persistToDisk();

            final float[]              newVector = vector(random);
            final long                 modCount  = (long)read(index, "structuralModCount");
            final FailingScoreProvider provider  = failNextInserts(index, 1);
            provider.error = true;
            assertInjected(assertThrows(OutOfMemoryError.class, () -> map.update(ID, doc -> doc.vector = newVector)), provider, 1);
            assertFalse(consolidationRequired(index), "reinserted only by an escalation");
            assertEquals(modCount, (long)read(index, "structuralModCount"), "a change the map does not store was recorded");
            assertTrue(finds(index, ID, newVector), "the index follows the entity in memory");
            map.store();
        }
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc> map = manager.root();
            assertConsistent(map, index(map));
        }
    }

    // synchronous add

    @Test
    void add_failedInsert_rollsBack_andTheNextAddUnderTheReclaimedIdIsFound()
    {
        final Random           random = new Random(8);
        final GigaMap<Doc>     map    = map(random);
        final VectorIndex<Doc> index  = register(map, config().build());
        final long             next   = map.highestUsedId() + 1;

        final FailingScoreProvider provider = failNextInserts(index, 1);
        assertInjected(assertThrows(RuntimeException.class, () -> map.add(new Doc(vector(random)))), provider, 1);
        assertEquals(COUNT, map.size());

        final Doc  doc = new Doc(vector(random));
        final long id  = map.add(doc);
        assertEquals(next, id, "precondition: the id was reclaimed");
        assertTrue(finds(index, id, doc.vector));
        assertConsistent(map, index);
    }

    // eventual indexing

    @Test
    void eventual_failedAdd_isAppliedAgainByTheWorker()
    {
        final Random       random = new Random(9);
        final GigaMap<Doc> map    = map(random);
        try(final VectorIndex<Doc> index = register(map, config().eventualIndexing(true).build()))
        {
            index.optimize();
            final FailingScoreProvider provider = failNextInserts(index, 1);
            final Doc  doc = new Doc(vector(random));
            final long id  = map.add(doc); // returns normally, the worker applies it
            drain(index);

            assertEquals(1, provider.fired.get(), "injected failures");
            assertFalse(rebuildRequired(index), "the retry did not suffice");
            assertTrue(finds(index, id, doc.vector));
            assertConsistent(map, index);
        }
    }

    @Test
    void eventual_failedAddWithAnError_isAppliedAgainByTheWorker()
    {
        final Random       random = new Random(10);
        final GigaMap<Doc> map    = map(random);
        try(final VectorIndex<Doc> index = register(map, config().eventualIndexing(true).build()))
        {
            index.optimize();
            final FailingScoreProvider provider = failNextInserts(index, 1);
            provider.error = true;
            final Doc  doc = new Doc(vector(random));
            final long id  = map.add(doc);
            drain(index);

            assertEquals(1, provider.fired.get(), "injected failures");
            assertFalse(rebuildRequired(index), "the retry did not suffice");
            assertTrue(finds(index, id, doc.vector));
            assertConsistent(map, index);
        }
    }

    @Test
    void eventual_failedUpdate_isAppliedAgainByTheWorker()
    {
        final Random       random = new Random(11);
        final GigaMap<Doc> map    = map(random);
        try(final VectorIndex<Doc> index = register(map, config().eventualIndexing(true).build()))
        {
            index.optimize();
            final Doc neu = new Doc(vector(random));
            final FailingScoreProvider provider = failNextInserts(index, 1);
            map.set(ID, neu);
            drain(index);

            assertEquals(1, provider.fired.get(), "injected failures");
            assertFalse(rebuildRequired(index), "the retry did not suffice");
            assertTrue(finds(index, ID, neu.vector));
            assertConsistent(map, index);
        }
    }

    @Test
    void eventual_failedBatchAdd_isAppliedAgainByTheWorker()
    {
        final Random       random = new Random(12);
        final GigaMap<Doc> map    = map(random);
        try(final VectorIndex<Doc> index = register(map, config().eventualIndexing(true).build()))
        {
            index.optimize();
            final FailingScoreProvider provider = failNextInserts(index, 1);
            map.addAll(docs(random, 50));
            drain(index);

            assertEquals(1, provider.fired.get(), "injected failures");
            assertFalse(rebuildRequired(index), "the retry did not suffice");
            assertEquals(COUNT + 50, map.size());
            assertConsistent(map, index);
        }
    }

    /** the live graph is empty after a persist, so the batch's half-inserted node has no entry to purge with */
    @Test
    void eventual_failedBatchAdd_onDisk_inAnEmptyLiveGraph_isAppliedAgainByTheWorker(@TempDir final Path dir)
    {
        final Random random = new Random(13);
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(dir.resolve("storage")))
        {
            final GigaMap<Doc> map = map(random);
            manager.setRoot(map);
            manager.storeRoot();
            final VectorIndex<Doc> index = register(map, config().eventualIndexing(true).onDisk(true)
                .indexDirectory(dir.resolve("index")).build());
            map.store();
            index.persistToDisk();

            final FailingScoreProvider provider = failNextInserts(index, 1);
            map.addAll(docs(random, 50));
            drain(index);

            assertEquals(1, provider.fired.get(), "injected failures");
            assertFalse(consolidationRequired(index), "the retry did not suffice");
            assertConsistent(map, index);
        }
    }

    @Test
    void eventual_failedAddAndFailedRetry_inMemory_rebuildsTheGraphFromTheVectorFile()
    {
        final Random       random = new Random(14);
        final GigaMap<Doc> map    = map(random);
        try(final VectorIndex<Doc> index = register(map, config().eventualIndexing(true).build()))
        {
            index.optimize();
            final FailingScoreProvider provider = failNextInserts(index, 2);
            final Doc  doc = new Doc(vector(random));
            final long id  = map.add(doc);
            drain(index);
            assertEquals(2, provider.fired.get(), "injected failures");
            assertTrue(rebuildRequired(index), "the failure was not escalated");

            assertTrue(finds(index, id, doc.vector), "entity missing after the rebuild");
            assertFalse(rebuildRequired(index));
            assertConsistent(map, index);

            // the index keeps working
            final Doc  next   = new Doc(vector(random));
            final long nextId = map.add(next);
            drain(index);
            assertTrue(finds(index, nextId, next.vector));
        }
    }

    @Test
    void eventual_failedAddWithAnErrorAndFailedRetry_isEscalated()
    {
        final Random       random = new Random(15);
        final GigaMap<Doc> map    = map(random);
        try(final VectorIndex<Doc> index = register(map, config().eventualIndexing(true).build()))
        {
            index.optimize();
            final FailingScoreProvider provider = failNextInserts(index, 2);
            provider.error = true;
            final Doc  doc = new Doc(vector(random));
            final long id  = map.add(doc);
            drain(index);
            assertEquals(2, provider.fired.get(), "injected failures");
            assertTrue(rebuildRequired(index), "the Error was not escalated");

            assertTrue(finds(index, id, doc.vector));
            assertConsistent(map, index);
        }
    }

    @Test
    void eventual_failedAddAndFailedRetry_onDisk_theNextPersistRebuilds(@TempDir final Path dir)
    {
        final Random random  = new Random(16);
        final Path   storage = dir.resolve("storage");
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc> map = map(random);
            manager.setRoot(map);
            manager.storeRoot();
            final VectorIndex<Doc> index = register(map, config().eventualIndexing(true).onDisk(true)
                .indexDirectory(dir.resolve("index")).build());
            map.store();
            index.persistToDisk();

            final FailingScoreProvider provider = failNextInserts(index, 2);
            map.add(new Doc(vector(random)));
            drain(index);
            assertEquals(2, provider.fired.get(), "injected failures");
            assertTrue(consolidationRequired(index), "the failure was not escalated");

            index.persistToDisk();
            assertFalse(consolidationRequired(index));
            assertConsistent(map, index);
            map.store();
        }
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc> map = manager.root();
            assertConsistent(map, index(map));
        }
    }

    // a required rebuild and the operations that replace the graph anyway

    @Test
    void removeAll_whileARebuildIsRequired_clearsIt_andTheIndexKeepsWorking()
    {
        final Random           random = new Random(17);
        final GigaMap<Doc>     map    = map(random);
        final VectorIndex<Doc> index  = register(map, config().build());

        final FailingScoreProvider provider = failNextInserts(index, 2);
        assertInjected(assertThrows(RuntimeException.class, () -> map.set(ID, new Doc(vector(random)))), provider, 2);
        assertTrue(rebuildRequired(index));

        map.removeAll();
        assertFalse(rebuildRequired(index));
        map.addAll(docs(random, 50));
        assertEquals(50, map.size());
        assertConsistent(map, index);
    }

    @Test
    void reindex_whileARebuildIsRequired_clearsIt()
    {
        final Random           random = new Random(18);
        final GigaMap<Doc>     map    = map(random);
        final VectorIndex<Doc> index  = register(map, config().build());

        final FailingScoreProvider provider = failNextInserts(index, 2);
        assertInjected(assertThrows(RuntimeException.class, () -> map.set(ID, new Doc(vector(random)))), provider, 2);
        assertTrue(rebuildRequired(index));

        map.reindex();
        assertFalse(rebuildRequired(index));
        assertConsistent(map, index);
    }

    // rebuild after a load

    @Test
    void rebuildAfterLoad_failed_isRetriedFromScratch(@TempDir final Path dir)
    {
        final Random random = new Random(19);
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(dir))
        {
            final GigaMap<Doc> map = map(random);
            register(map, config().build());
            manager.setRoot(map);
            manager.storeRoot();
        }
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(dir))
        {
            final GigaMap<Doc>             map     = manager.root();
            final VectorIndex.Default<Doc> index   = (VectorIndex.Default<Doc>)index(map);
            final AtomicInteger            inserts = new AtomicInteger();
            index.rebuildWorkerTestHook = () ->
            {
                if(inserts.incrementAndGet() == COUNT / 2)
                {
                    throw new IllegalStateException(INJECTED);
                }
            };
            try
            {
                final RuntimeException failure = assertThrows(RuntimeException.class, () -> index.search(vector(random), 1));
                for(Throwable cause = failure; ; cause = cause.getCause())
                {
                    assertTrue(cause != null, "not the injected failure: " + failure);
                    if(INJECTED.equals(cause.getMessage()))
                    {
                        break;
                    }
                }
            }
            finally
            {
                index.rebuildWorkerTestHook = null;
            }
            assertConsistent(map, index);
        }
    }

    // related: a replacement rejected by a later vector index

    /**
     * Only an in-place update, which the map retains, flags the vector group after a failure. A set rejected by a later
     * index must not get the earlier index stored with the replacement by the store right after it.
     */
    @Test
    void set_rejectedByASecondVectorIndex_isNotCommittedForTheFirstByTheNextStore(@TempDir final Path dir)
    {
        final Random  random  = new Random(20);
        final Path    storage = dir.resolve("storage");
        final float[] oldVector;
        final float[] newVector;
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc> map = map(random);
            manager.setRoot(map);
            manager.storeRoot();
            final VectorIndices<Doc> group = map.index().register(VectorIndices.Category());
            final VectorIndex<Doc>   first = group.add("v", onDisk(dir.resolve("v")), new DocVectorizer());
            group.add("w", onDisk(dir.resolve("w")), new RejectingVectorizer());
            map.store();
            first.persistToDisk();
            oldVector = map.get(ID).vector;
            final Doc neu = new Doc(vector(random));
            newVector = neu.vector;

            RejectingVectorizer.rejected = neu;
            try
            {
                assertThrows(IllegalStateException.class, () -> map.set(ID, neu));
            }
            finally
            {
                RejectingVectorizer.rejected = null;
            }
            map.store();
        }
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc>     map   = manager.root();
            final VectorIndex<Doc> first = index(map);
            assertArrayEquals(oldVector, map.get(ID).vector);
            assertTrue(finds(first, ID, oldVector));
            assertFalse(finds(first, ID, newVector));
        }
    }

    // locking

    /**
     * A nested search (its thread holds the read lock) while a rebuild is required, and a mutation under the monitor
     * that waits for the write lock to drop the graph: the nested search must not wait for the monitor.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void nestedSearch_whileARebuildIsRequired_doesNotDeadlock() throws Exception
    {
        final Random                     random     = new Random(21);
        final GigaMap<Doc>               map        = map(random);
        final VectorIndex<Doc>           index      = register(map, config().build());
        final ReentrantReadWriteLock     lock       = (ReentrantReadWriteLock)read(index, "builderLock");
        final CountDownLatch             readLocked = new CountDownLatch(1);
        final CountDownLatch             proceed    = new CountDownLatch(1);
        final AtomicReference<Throwable> failure    = new AtomicReference<>();
        final float[]                    query      = vector(random);
        final Doc                        neu        = new Doc(vector(random));

        final Thread searcher = new Thread(() ->
        {
            lock.readLock().lock(); // the outer search
            try
            {
                readLocked.countDown();
                proceed.await();
                index.search(query, 5).toList(); // the nested search
            }
            catch(final Throwable t)
            {
                failure.compareAndSet(null, t);
            }
            finally
            {
                lock.readLock().unlock();
            }
        });
        searcher.setDaemon(true);
        searcher.start();
        readLocked.await();

        write(index, "graphRebuildRequired", true);
        final Thread mutator = new Thread(() ->
        {
            try
            {
                map.set(ID, neu);
            }
            catch(final Throwable t)
            {
                failure.compareAndSet(null, t);
            }
        });
        mutator.setDaemon(true);
        mutator.start();

        // the mutation holds the monitor and waits for the write lock to drop the graph
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while((Integer)read(index, "exclusiveWaiters") == 0)
        {
            assertTrue(System.nanoTime() < deadline, "the mutation never waited for the write lock");
            Thread.sleep(5);
        }
        proceed.countDown();

        searcher.join(20_000);
        mutator.join(20_000);
        assertFalse(searcher.isAlive() || mutator.isAlive(), "the nested search and the mutation deadlocked");
        assertNull(failure.get());
        assertFalse(rebuildRequired(index));
        assertConsistent(map, index);
    }
}
