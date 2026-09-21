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

import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.ScoredSearchResult;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PQ-compressed scoring in an in-memory index.
 * <p>
 * The on-disk modes hang their compression off the persist, which gives them a natural point to
 * train a codebook and encode against it. An in-memory index has no persist, so the switch from
 * exact to compressed scoring happens at optimization instead - and until it happens the index
 * behaves exactly as it always did. Most of what is worth testing here is that lifecycle.
 */
@Tag("slow")
class VectorIndexInMemoryPqTest
{
    static final int DIM = 64;

    static final class Doc
    {
        final String  content;
        final float[] embedding;

        Doc(final String content, final float[] embedding)
        {
            this.content   = content;
            this.embedding = embedding;
        }
    }

    /** Embedded vectorizer, so that every vector lookup is a countable call. */
    static final class CountingVectorizer extends Vectorizer<Doc>
    {
        final AtomicLong calls = new AtomicLong();

        @Override
        public boolean isEmbedded()
        {
            return true;
        }

        @Override
        public float[] vectorize(final Doc entity)
        {
            this.calls.incrementAndGet();
            return entity.embedding;
        }
    }

    private static float[] randomUnit(final Random random)
    {
        final float[] v = new float[DIM];
        double norm = 0;
        for(int i = 0; i < DIM; i++)
        {
            v[i] = (float)random.nextGaussian();
            norm += v[i] * v[i];
        }
        final float inv = (float)(1.0 / Math.sqrt(norm));
        for(int i = 0; i < DIM; i++)
        {
            v[i] *= inv;
        }
        return v;
    }

    private static float[] nearVector(final Random random, final float[] centre)
    {
        final float[] v = new float[DIM];
        double norm = 0;
        for(int i = 0; i < DIM; i++)
        {
            v[i] = centre[i] + 0.15f * (float)random.nextGaussian();
            norm += v[i] * v[i];
        }
        final float inv = (float)(1.0 / Math.sqrt(norm));
        for(int i = 0; i < DIM; i++)
        {
            v[i] *= inv;
        }
        return v;
    }

    private static List<float[]> clusteredVectors(final Random random, final int count)
    {
        final List<float[]> centroids = new ArrayList<>();
        for(int c = 0; c < 20; c++)
        {
            centroids.add(randomUnit(random));
        }
        final List<float[]> vectors = new ArrayList<>();
        for(int i = 0; i < count; i++)
        {
            vectors.add(nearVector(random, centroids.get(i % centroids.size())));
        }
        return vectors;
    }

    /**
     * As {@link #inMemoryPqConfig()}, but with eventual indexing on, so a graph update is applied by
     * a background callback holding only the read lock rather than synchronously under the monitor.
     * That is the one path that can run <i>while</i> the switch holds the monitor, so it is the path
     * the switch's concurrency argument has to survive.
     *
     * @return the configuration
     */
    private static VectorIndexConfiguration eventualInMemoryPqConfig()
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .approximateScoring(ApproximateScoring.PQ_IN_MEMORY)
            .pqSubspaces(DIM / 4)
            .eventualIndexing(true)
            .optimizationIntervalMs(3_600_000)
            .build();
    }

    private static VectorIndexConfiguration inMemoryPqConfig()
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .approximateScoring(ApproximateScoring.PQ_IN_MEMORY)
            .pqSubspaces(DIM / 4)
            // Required for this mode: the switch happens at optimization, so there has to be one.
            // A long interval, because these tests call optimize() directly rather than waiting.
            .optimizationIntervalMs(3_600_000)
            .build();
    }

    /**
     * As {@link #inMemoryPqConfig()}, but ticking often and with a change threshold far out of
     * reach, so that a scheduled optimization can only happen if something armed it.
     *
     * @return the configuration
     */
    private static VectorIndexConfiguration tickingInMemoryPqConfig()
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .approximateScoring(ApproximateScoring.PQ_IN_MEMORY)
            .pqSubspaces(DIM / 4)
            // Long enough that the entities are all in before the first tick: a tick that finds
            // too few vectors to train declines and clears the counter, which would mask whether
            // the tick was armed at all.
            .optimizationIntervalMs(1_500)
            .minChangesBetweenOptimizations(50_000)
            .build();
    }

    /**
     * A newly created index must earn its optimization like any other.
     * <p>
     * A restarted index gets its first optimization armed for it, because it cannot earn one: its
     * entities are already in the store, so nothing bumps the change count and every scheduled
     * optimization is skipped. That bootstrap must not reach a <i>new</i> index, which is not in
     * that position at all - its entities arrive afterwards and each one bumps the count on its way
     * in. Arming it too would let a small index switch on the first tick without ever reaching
     * {@code minChangesBetweenOptimizations}, contradicting what that setting documents.
     * <p>
     * The index here holds more than enough vectors to train, so the switch not happening can only
     * be the threshold - which the explicit {@code optimize()} at the end confirms by making it
     * happen at once.
     * <p>
     * Both registration orders, because they differ in exactly the way a vector-count test would
     * get wrong: registering on an already-populated map reads a non-empty count at initialization
     * and is still a new index, whose entities arrive immediately afterwards through the backfill
     * and each count. Only an index restored from storage is armed.
     *
     * @param registerFirst whether the index is registered before the entities are added
     * @throws InterruptedException if the wait for the scheduled ticks is interrupted
     */
    private void assertNewIndexDoesNotSwitchBeforeThreshold(final boolean registerFirst)
        throws InterruptedException
    {
        final Random        random  = new Random(4242);
        final List<float[]> vectors = clusteredVectors(random, 300);

        final GigaMap<Doc> map = GigaMap.New();
        if(!registerFirst)
        {
            for(int i = 0; i < vectors.size(); i++)
            {
                map.add(new Doc("d" + i, vectors.get(i)));
            }
        }

        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", tickingInMemoryPqConfig(), new CountingVectorizer()))
        {
            if(registerFirst)
            {
                for(int i = 0; i < vectors.size(); i++)
                {
                    map.add(new Doc("d" + i, vectors.get(i)));
                }
            }

            // Several tick intervals, so this is not merely a race the switch lost.
            Thread.sleep(5_000L);

            assertEquals(0L, ((VectorIndex.Default<Doc>)index).backgroundTaskManager.getOptimizationCount(),
                "a scheduled optimization ran although the change threshold was never reached, so"
                    + " the first one was armed for an index that had not earned it");

            assertFalse(index.isPqCompressionActive(),
                "a new index switched on a scheduled optimization it never earned: 300 changes"
                    + " against a threshold of 50000");

            index.optimize();

            assertTrue(index.isPqCompressionActive(),
                "and it must be capable of switching, so the absence above is the threshold rather"
                    + " than an index that could not switch anyway");
        }
    }

    /** Registered on an empty map, the entities added afterwards. */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void aNewIndexOnAnEmptyMapDoesNotSwitchBeforeReachingTheChangeThreshold() throws InterruptedException
    {
        this.assertNewIndexDoesNotSwitchBeforeThreshold(true);
    }

    /**
     * Registered on a map that already holds the entities, which the backfill then indexes.
     * <p>
     * The case a vector-count test gets wrong: the count is already non-zero when the index
     * initializes, and the index is new all the same.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void aNewIndexOnAPopulatedMapDoesNotSwitchBeforeReachingTheChangeThreshold() throws InterruptedException
    {
        this.assertNewIndexDoesNotSwitchBeforeThreshold(false);
    }

    /**
     * An explicit {@code optimize()} must not erase changes that have not been optimized.
     * <p>
     * The bootstrap request and the mutation count answer different questions - "this index was
     * armed for an optimization it cannot earn" against "this much has changed" - so consuming the
     * request must not consume the count. An explicit {@code optimize()} clears the request,
     * because it does the work the request was asking for; the mutations that accrued before it are
     * a separate claim on a later optimization and stay standing.
     * <p>
     * Set up so the erasure is the only thing that can decide the outcome: 300 changes before the
     * explicit call and 250 after, against a threshold of 500. Together they clear it, so a
     * scheduled optimization must follow. Each alone does not, so if the first 300 are erased none
     * ever runs.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void anExplicitOptimizeDoesNotEraseUnoptimizedChanges() throws InterruptedException
    {
        final Random        random  = new Random(2718);
        final List<float[]> vectors = clusteredVectors(random, 550);

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", VectorIndexConfiguration.builder()
                .dimension(DIM)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .maxDegree(16)
                .beamWidth(100)
                .approximateScoring(ApproximateScoring.PQ_IN_MEMORY)
                .pqSubspaces(DIM / 4)
                .optimizationIntervalMs(1_000)
                .minChangesBetweenOptimizations(500)
                .build(), new CountingVectorizer()))
        {
            final VectorIndex.Default<Doc> internal = (VectorIndex.Default<Doc>)index;

            for(int i = 0; i < 300; i++)
            {
                map.add(new Doc("d" + i, vectors.get(i)));
            }

            // Below the threshold, so nothing has been scheduled on its own yet.
            assertEquals(0L, internal.backgroundTaskManager.getOptimizationCount(),
                "the threshold was reached during setup, so this run cannot tell the two apart");

            index.optimize();
            assertTrue(index.isPqCompressionActive());

            for(int i = 300; i < 550; i++)
            {
                map.add(new Doc("d" + i, vectors.get(i)));
            }

            // Several tick intervals.
            Thread.sleep(4_000L);

            assertTrue(internal.backgroundTaskManager.getOptimizationCount() > 0L,
                "no scheduled optimization ran, so the 300 changes made before the explicit"
                    + " optimize() were erased along with the bootstrap request");
        }
    }

    /**
     * An index that only ever mutates through eventual indexing must still reach the switch on its
     * own.
     * <p>
     * The bootstrap that arms a restored index is deliberately not given to a new one, which is
     * expected to earn its optimization through the change count. Under eventual indexing nothing
     * bumps that count at mutation time - {@code enqueue} only queues the operation - so the
     * question is whether the count is ever bumped at all. It is: each queued operation marks dirty
     * when it is <i>applied</i>, on the executor thread. This pins that, because the two mechanisms
     * would otherwise cancel out and leave such an index on exact scoring for good.
     * <p>
     * No {@code optimize()} call anywhere - the whole point is that the scheduled one happens by
     * itself.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void anEventuallyIndexedIndexReachesTheSwitchOnItsOwn() throws InterruptedException
    {
        final Random        random  = new Random(1618);
        final List<float[]> vectors = clusteredVectors(random, 300);

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", VectorIndexConfiguration.builder()
                .dimension(DIM)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .maxDegree(16)
                .beamWidth(100)
                .approximateScoring(ApproximateScoring.PQ_IN_MEMORY)
                .pqSubspaces(DIM / 4)
                .eventualIndexing(true)
                .optimizationIntervalMs(1_000)
                .minChangesBetweenOptimizations(200)
                .build(), new CountingVectorizer()))
        {
            for(int i = 0; i < vectors.size(); i++)
            {
                map.add(new Doc("d" + i, vectors.get(i)));
            }

            // 300 changes against a threshold of 200, so a scheduled optimization is due once the
            // queued operations have been applied. Several tick intervals to let that happen.
            Thread.sleep(6_000L);

            assertTrue(index.isPqCompressionActive(),
                "an eventually indexed index never switched: its mutations are queued rather than"
                    + " counted at mutation time, so if applying them does not mark dirty either,"
                    + " the scheduled optimization is skipped forever");
        }
    }

    /**
     * An index restored from storage switches on a scheduled optimization it could never earn.
     * <p>
     * This is the case the whole unearned-optimization path exists for, and the only one that
     * reaches it. A reloaded index has its entities already in the store, so nothing bumps the
     * change count for them and the threshold is never approached - yet it starts on exact scoring,
     * because the codes are transient and do not survive the round trip. Without the carve-out it
     * would stay that way for the rest of the session.
     * <p>
     * Deliberately end to end, with a real storage round trip and no {@code optimize()} call after
     * the reload: everything the carve-out depends on - the restored flag being set on the load
     * path, the scheduled tick asking for it, the switch finding enough vectors - has to hold at
     * once for this to pass. The threshold is far out of reach, so a scheduled optimization can
     * only happen because the index was recognised as restored.
     *
     * @param dir the temporary storage directory
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void aRestoredIndexSwitchesWithoutReachingTheChangeThreshold(@TempDir final Path dir)
        throws InterruptedException
    {
        final Random        random  = new Random(31415);
        final List<float[]> vectors = clusteredVectors(random, 300);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            map.index().register(VectorIndices.Category())
                .add("embeddings", tickingInMemoryPqConfig(), new CountingVectorizer());
            for(int i = 0; i < vectors.size(); i++)
            {
                map.add(new Doc("d" + i, vectors.get(i)));
            }
            storage.storeRoot();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index()
                .get(VectorIndices.Category())
                .get("embeddings")
            ;

            assertEquals(300, map.size(), "the entities came back");
            assertFalse(index.isPqCompressionActive(),
                "the codes are transient, so a restored index starts on exact scoring");

            // A search, to force the deferred initialization the load path leaves for first access.
            // Nothing here mutates, so nothing contributes to the change count.
            topK(index, vectors.get(0), 10);

            // Several tick intervals. No optimize() call - the scheduled one has to happen by
            // itself, 300 entities short of a threshold of 50000.
            Thread.sleep(6_000L);

            assertTrue(index.isPqCompressionActive(),
                "a restored index never switched: it cannot earn a scheduled optimization, so"
                    + " without the carve-out for exactly this case it stays on exact scoring for"
                    + " the whole session");
            // And the graph it switched to is the restored data, not an empty one.
            assertEquals(10, topK(index, vectors.get(7), 10).size(),
                "the index returns no results after switching, so the replacement was not built"
                    + " from the restored entities");
        }
    }

    /**
     * The mode is on-disk-free but not optimization-free: without a scheduled optimization there is
     * no point at which the switch could happen, so the configuration would be accepted and then
     * silently never take effect. That is rejected rather than allowed.
     */
    @Test
    void inMemoryPqRequiresAScheduledOptimization()
    {
        final IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(DIM)
                .approximateScoring(ApproximateScoring.PQ_IN_MEMORY)
                .build()
        );
        assertTrue(e.getMessage().contains("optimization"), e.getMessage());

        // With one scheduled it builds, and without onDisk - which FUSED_PQ would still refuse.
        assertNotNull(inMemoryPqConfig());
        assertThrows(IllegalStateException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(DIM)
                .approximateScoring(ApproximateScoring.FUSED_PQ)
                .optimizationIntervalMs(1_000)
                .build()
        );
    }

    /**
     * Once switched, inserting into the graph must score from the codes rather than from vectors.
     * <p>
     * This is the claim the whole mode rests on - construction performs on the order of a thousand
     * vector lookups per insertion, and scoring from in-heap codes is what removes them - and it is
     * the one no other test here can see. Every other test passes whether the builder was handed the
     * PQ provider or the exact one, because both produce a correct graph and the search path uses
     * the codes either way. A gate that required a non-empty code store silently sent the rebuilt
     * builder down the exact path and the suite stayed green.
     * <p>
     * Counting the vectorizer distinguishes them, but only over a window that holds nothing else:
     * insertions made <i>after</i> the switch. Measuring across {@code optimize()} itself does not
     * work - it runs {@code cleanup()} on the old, exact-provider builder first, and that alone
     * costs a couple of hundred reads per node, swamping what the switch does.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void insertsAfterTheSwitchScoreFromTheCodes()
    {
        final Random        random  = new Random(8181);
        final List<float[]> vectors = clusteredVectors(random, 1000);
        final List<float[]> later   = clusteredVectors(random, 200);

        final CountingVectorizer vectorizer = new CountingVectorizer();
        final GigaMap<Doc>       map        = GigaMap.New();

        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", inMemoryPqConfig(), vectorizer))
        {
            for(int i = 0; i < vectors.size(); i++)
            {
                map.add(new Doc("d" + i, vectors.get(i)));
            }

            index.optimize();
            assertTrue(index.isPqCompressionActive(), "the optimization must have switched");

            final long beforeInserts = vectorizer.calls.get();

            for(int i = 0; i < later.size(); i++)
            {
                map.add(new Doc("late" + i, later.get(i)));
            }

            final long perInsert = (vectorizer.calls.get() - beforeInserts) / later.size();

            // A handful per insertion: the entity's own vector, read to encode its code and to hand
            // it to the builder. The exact provider instead reads one per comparison, which at this
            // beam width is two orders of magnitude more.
            assertTrue(perInsert < 20,
                "inserting after the switch cost " + perInsert + " vectorizer calls per node, which"
                    + " is the exact provider's cost profile - the builder is not scoring from codes");
        }
    }

    /**
     * A mutation cannot interleave with the switch: it waits for it, and then survives it.
     * <p>
     * The switch holds the {@code parentMap} monitor from the moment it reads the GigaMap to the
     * moment it publishes the replacement, and every GigaMap mutator is synchronized on that same
     * monitor. So there is no picture for a mutation to invalidate - it either landed before the
     * snapshot, and the replay carries it into the replacement, or it has not run yet and applies to
     * the replacement afterwards.
     * <p>
     * This pins both halves. The hook runs with the replacement built and unpublished, which is the
     * latest instant at which an interleaving mutation could still do damage; a mutator thread
     * started there must be found blocked rather than proceeding. Once the switch completes the
     * mutation goes through, and the entity it adds must then be findable - which it can only be if
     * the add reached the graph the switch published rather than the one it abandoned.
     * <p>
     * An add rather than an update, deliberately: an embedded vec&rarr;vec update leaves the graph
     * connections alone by contract (see the update path) and is findable at its new vector only
     * after a later rebuild, so it could not tell the two graphs apart here. A new node is linked in
     * on the spot.
     * <p>
     * The test fails if the switch stops holding the monitor across the rebuild: the mutator thread
     * would then run to completion inside the window.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void aConcurrentMutationWaitsForTheSwitchAndThenSurvivesIt() throws InterruptedException
    {
        final Random        random  = new Random(31337);
        final List<float[]> vectors = clusteredVectors(random, 1000);

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", inMemoryPqConfig(), new CountingVectorizer()))
        {
            for(int i = 0; i < vectors.size(); i++)
            {
                map.add(new Doc("d" + i, vectors.get(i)));
            }

            final VectorIndex.Default<Doc> internal = (VectorIndex.Default<Doc>)index;

            // The entity added during the switch, at a vector of its own.
            final float[]    added      = nearVector(new Random(99), vectors.get(900));
            final AtomicLong addedId    = new AtomicLong(-1L);

            final Thread        mutator    = new Thread(
                () -> addedId.set(map.add(new Doc("added", added))), "pq-switch-mutator");
            final AtomicBoolean wasBlocked = new AtomicBoolean();
            final AtomicBoolean hookRan    = new AtomicBoolean();

            internal.pqSwitchPublishTestHook = () ->
            {
                hookRan.set(true);
                mutator.start();

                // A thread waiting on a monitor reports BLOCKED. Polled rather than slept on, so
                // the test neither races a slow thread start nor pauses for a fixed time.
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while(System.nanoTime() < deadline)
                {
                    if(mutator.getState() == Thread.State.BLOCKED)
                    {
                        wasBlocked.set(true);
                        return;
                    }
                    Thread.onSpinWait();
                }
            };

            index.optimize();
            mutator.join(TimeUnit.SECONDS.toMillis(30));

            assertTrue(hookRan.get(), "the switch did not reach the publication point");
            assertTrue(wasBlocked.get(),
                "a mutation started while the replacement was being published was not blocked on"
                    + " the GigaMap monitor, so it could interleave with the switch");
            assertFalse(mutator.isAlive(), "the mutation must complete once the switch releases the monitor");

            assertTrue(index.isPqCompressionActive(),
                "the switch must complete - a concurrent mutation delays it, it does not cancel it");
            assertTrue(topK(index, added, 10).contains(addedId.get()),
                "the entity added during the switch must be findable, which it can only be if the"
                    + " add reached the graph the switch published");
        }
    }

    /**
     * Graph updates still queued when the switch runs must neither be lost nor rejected.
     * <p>
     * Under eventual indexing a mutation applies to the GigaMap synchronously and only its graph
     * update is deferred to a background callback, which takes the read lock and never the monitor.
     * So a callback can be pending, or running, while the switch holds the monitor - the single case
     * the switch cannot lock out, and the reason it does not try to.
     * <p>
     * What this pins is that nothing is lost. The snapshot is taken under the monitor from the
     * GigaMap, which the mutation has already updated, so every pending entity is in the replacement
     * even though its callback has not run - and the callbacks that drain afterwards, into a
     * replacement that already carries their ordinals, must not disturb it either. It drives that by
     * calling {@code doOptimize()} directly, the entry point that does <i>not</i> drain the queue
     * first.
     * <p>
     * How much overlap a given run gets is up to the background thread, and the assertion is
     * deliberately not conditioned on it. Whether the queue still held operations at the moment of
     * the switch does not change what must be true afterwards, because the guarantee does not come
     * from the timing: the snapshot is taken from the GigaMap under the monitor, and a mutation has
     * applied to the GigaMap before its callback is ever enqueued. A run that happens to drain
     * early asserts the same thing and passes for the same reason.
     * <p>
     * A barrier that pinned the overlap exactly would need to hold an indexing callback mid-flight,
     * and there is no seam for that which does not also change what is being tested. Note that a
     * mutation cannot be <i>started</i> during the switch at all - it would need the monitor the
     * switch is holding - so the only callbacks in play are those enqueued before it began.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void queuedGraphUpdatesSurviveTheSwitch()
    {
        final Random        random  = new Random(8191);
        final List<float[]> vectors = clusteredVectors(random, 1000);

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", eventualInMemoryPqConfig(), new CountingVectorizer()))
        {
            final List<Long> ids = new ArrayList<>();
            for(int i = 0; i < vectors.size(); i++)
            {
                ids.add(map.add(new Doc("d" + i, vectors.get(i))));
            }

            final VectorIndex.Default<Doc> internal = (VectorIndex.Default<Doc>)index;

            // Deliberately not drained: optimize() would drain first, which is exactly the overlap
            // this test needs to keep.
            internal.doOptimize();

            assertTrue(index.isPqCompressionActive(),
                "the switch must happen even with graph updates still queued");

            internal.backgroundTaskManager.drainQueue();

            // Graph membership, not search results. "Lost" is a question about the graph, and a
            // top-k search cannot answer it: a node that is present but ranked out of the window
            // looks identical to one that is gone, so the assertion would report a loss for a
            // recall wobble and depend on approximate traversal to prove an exact property.
            // Recall has a test of its own, with a floor, in recallStaysHighAfterTheSwitch.
            //
            // Every ordinal, not a sample: one lost entity among a thousand is the failure here.
            final OnHeapGraphIndex graph = publishedGraph(internal);
            for(int i = 0; i < ids.size(); i++)
            {
                final int ordinal = Math.toIntExact(ids.get(i));
                assertTrue(graph.containsNode(ordinal) && !graph.getDeletedNodes().get(ordinal),
                    "entity " + i + " (ordinal " + ordinal + ") is not a live node of the graph the"
                        + " switch published, so a graph update queued across the switch was lost");
            }
        }
    }

    /**
     * Until the codes are published, the index must not report compressed scoring.
     * <p>
     * Training is only the first step of the transition - the codes still have to be encoded and the
     * graph rebuilt against them - so a codebook exists well before anything can score from it.
     * Reporting from the codebook would make {@code isPqCompressionActive()} true for that whole
     * stretch while {@code searchInMemoryIndex} was still selecting the exact provider, which is the
     * opposite of what "actually in effect, as opposed to merely configured" promises.
     * <p>
     * The published codes are the witness instead, and the hook observes the instant before any of
     * the publication happens: the replacement is complete, the codebook exists, and nothing at all
     * is visible yet. That is the whole window the status must stay false for - the codes are
     * published first of the three, so from the first visible write onwards the index really is
     * scoring from them.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void theIndexDoesNotReportCompressedScoringUntilTheCodesArePublished()
    {
        final Random        random  = new Random(606);
        final List<float[]> vectors = clusteredVectors(random, 1000);

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", inMemoryPqConfig(), new CountingVectorizer()))
        {
            for(int i = 0; i < vectors.size(); i++)
            {
                map.add(new Doc("d" + i, vectors.get(i)));
            }

            final VectorIndex.Default<Doc> internal = (VectorIndex.Default<Doc>)index;

            final AtomicBoolean hookRan           = new AtomicBoolean();
            final AtomicBoolean activeInTheWindow = new AtomicBoolean();
            internal.pqSwitchPublishTestHook = () ->
            {
                hookRan.set(true);
                activeInTheWindow.set(index.isPqCompressionActive());
            };

            index.optimize();

            assertTrue(hookRan.get(), "the switch did not reach the publication point");
            assertFalse(activeInTheWindow.get(),
                "the index reported compressed scoring before the codes were published, so callers"
                    + " would have believed a switch that had not happened yet");
            assertTrue(index.isPqCompressionActive(),
                "and it must report it once the codes are published");
        }
    }

    /**
     * An index that is emptied and refilled must be able to switch again.
     * <p>
     * {@code removeAll} tears the index down through {@code closeInternalResources} and then
     * re-initialises it. The codes describe a graph that no longer exists, so they have to go with
     * it - and not only because they would be wrong. The switch returns early when the code store is
     * already set, so a set of codes surviving the teardown would leave the refilled index reporting
     * compressed scoring while scoring against a codebook trained on data it no longer holds, and no
     * later optimization would ever correct it.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void removeAllLetsTheIndexSwitchAgain()
    {
        final Random random = new Random(4242);

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", inMemoryPqConfig(), new CountingVectorizer()))
        {
            final List<float[]> first = clusteredVectors(random, 1000);
            for(int i = 0; i < first.size(); i++)
            {
                map.add(new Doc("a" + i, first.get(i)));
            }

            index.optimize();
            assertTrue(index.isPqCompressionActive(), "the first fill must have switched");

            map.removeAll();

            assertFalse(index.isPqCompressionActive(),
                "the codes describe a graph that no longer exists, so the emptied index must report"
                    + " exact scoring again");

            final List<float[]> second = clusteredVectors(random, 1000);
            final List<Long>    ids    = new ArrayList<>();
            for(int i = 0; i < second.size(); i++)
            {
                ids.add(map.add(new Doc("b" + i, second.get(i))));
            }

            index.optimize();

            assertTrue(index.isPqCompressionActive(),
                "the refilled index must switch again rather than being stuck on whatever the"
                    + " teardown left behind");

            // And it answers from the new data, not the old.
            final float[] query = nearVector(new Random(7), second.get(23));
            final Set<Long> hits = topK(index, query, 10);
            assertFalse(hits.isEmpty(), "the refilled index must return results");
            assertTrue(ids.containsAll(hits), "every hit must come from the second fill: " + hits);
        }
    }

    /**
     * An index cannot start compressed - the builder takes its score provider at construction and a
     * codebook needs 256 vectors - so it starts exact and switches at the first optimization that
     * has enough data. Both states must answer correctly.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void switchHappensAtOptimizeAndResultsSurviveIt()
    {
        final Random        random  = new Random(21);
        final List<float[]> vectors = clusteredVectors(random, 1000);

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", inMemoryPqConfig(), new CountingVectorizer()))
        {
            final List<Long> ids = new ArrayList<>();
            for(int i = 0; i < vectors.size(); i++)
            {
                ids.add(map.add(new Doc("d" + i, vectors.get(i))));
            }

            assertFalse(index.isPqCompressionActive(),
                "an in-memory index must start on exact scoring");

            final float[] query  = nearVector(new Random(5), vectors.get(17));
            final Set<Long> before = topK(index, query, 10);

            index.optimize();

            assertTrue(index.isPqCompressionActive(),
                "the optimization must have trained a codebook and switched the score provider");

            final Set<Long> after = topK(index, query, 10);

            // Compressed traversal is approximate, so the two need not be identical - but they must
            // still be answering the same question.
            final Set<Long> overlap = new HashSet<>(before);
            overlap.retainAll(after);
            assertTrue(overlap.size() >= 8,
                "the top-10 must be substantially the same either side of the switch, overlap was "
                    + overlap.size() + " (" + before + " vs " + after + ")");
        }
    }

    /**
     * The whole point of the mode: once switched, traversal scores from the in-heap codes instead of
     * reaching for the vectors. In embedded mode every such reach is a {@code Vectorizer} call, so
     * the drop is directly countable.
     * <p>
     * This is the test that fails if the search path never consults the codes.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void compressedScoringCollapsesVectorLookupsPerQuery()
    {
        final Random        random  = new Random(33);
        final List<float[]> vectors = clusteredVectors(random, 1000);

        final CountingVectorizer vectorizer = new CountingVectorizer();
        final GigaMap<Doc>       map        = GigaMap.New();

        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", inMemoryPqConfig(), vectorizer))
        {
            for(int i = 0; i < vectors.size(); i++)
            {
                map.add(new Doc("d" + i, vectors.get(i)));
            }

            final int queries = 50;

            vectorizer.calls.set(0);
            this.runQueries(index, vectors, queries, 7);
            final long exactLookups = vectorizer.calls.get();

            index.optimize();
            assertTrue(index.isPqCompressionActive());

            vectorizer.calls.set(0);
            this.runQueries(index, vectors, queries, 7);
            final long compressedLookups = vectorizer.calls.get();

            assertTrue(compressedLookups * 4 < exactLookups,
                "compressed scoring must collapse the per-query lookups, measured "
                    + compressedLookups + " against " + exactLookups + " for " + queries
                    + " queries. If these are comparable, traversal is not using the codes.");
        }
    }

    /**
     * The codes are maintained incrementally, so mutations after the switch have to keep them in
     * step. A node added afterwards must be findable, and one removed must not come back.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void mutationsAfterTheSwitchStayConsistent()
    {
        final Random        random  = new Random(44);
        final List<float[]> vectors = clusteredVectors(random, 800);

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", inMemoryPqConfig(), new CountingVectorizer()))
        {
            final List<Long> ids = new ArrayList<>();
            for(int i = 0; i < vectors.size(); i++)
            {
                ids.add(map.add(new Doc("d" + i, vectors.get(i))));
            }

            index.optimize();
            assertTrue(index.isPqCompressionActive());

            // Added after the switch: its code has to be encoded on the way in, or the builder would
            // have scored it against a zero code while inserting it.
            final float[] fresh   = randomUnit(new Random(4242));
            final long    freshId = map.add(new Doc("fresh", fresh));

            final Set<Long> found = topK(index, fresh, 5);
            assertTrue(found.contains(freshId),
                "a vector added after the switch must be findable by its own embedding");

            // Removed after the switch: gone from the results.
            map.removeById(freshId);
            assertFalse(topK(index, fresh, 5).contains(freshId),
                "a removed vector must not come back");

            // And an update. An embedded vec->vec update leaves the graph connections alone by
            // contract - the node keeps the neighbours it earned at its old position - so it
            // becomes reachable from its new one only once they are rebuilt. Hence the optimize:
            // without it this asserts reachability the module does not promise, and passes or
            // fails on how the graph happened to be wired.
            //
            // It still pins the code refresh, and more tightly than the bare search did. The
            // rebuild re-links from the codes, so a node whose code was never refreshed would be
            // linked at its old position and stay unreachable from the new one.
            final long updated = ids.get(3);
            final float[] replacement = randomUnit(new Random(9191));
            map.set(updated, new Doc("d3-updated", replacement));
            index.optimize();
            assertTrue(topK(index, replacement, 5).contains(updated),
                "an updated vector must be findable by its new embedding");
        }
    }

    /**
     * Control for {@link #mutationsAfterTheSwitchStayConsistent}: the same sequence on a plain
     * exact-scoring in-memory index, to attribute any difference correctly.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void controlMutationsOnAnExactIndex()
    {
        final Random        random  = new Random(44);
        final List<float[]> vectors = clusteredVectors(random, 800);

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", VectorIndexConfiguration.builder()
                .dimension(DIM)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .maxDegree(16)
                .beamWidth(100)
                .build(), new CountingVectorizer()))
        {
            final List<Long> ids = new ArrayList<>();
            for(int i = 0; i < vectors.size(); i++)
            {
                ids.add(map.add(new Doc("d" + i, vectors.get(i))));
            }
            index.optimize();

            final float[] fresh   = randomUnit(new Random(4242));
            final long    freshId = map.add(new Doc("fresh", fresh));
            assertTrue(topK(index, fresh, 5).contains(freshId), "control: fresh add findable");

            map.removeById(freshId);
            assertFalse(topK(index, fresh, 5).contains(freshId), "control: removed not returned");

            // Same sequence, same rebuild, so the two remain comparable.
            final long updated = ids.get(3);
            final float[] replacement = randomUnit(new Random(9191));
            map.set(updated, new Doc("d3-updated", replacement));
            index.optimize();
            assertTrue(topK(index, replacement, 5).contains(updated),
                "control: an updated vector must be findable by its new embedding on an exact index");
        }
    }

    /**
     * Compressed traversal does no reranking during construction, so graph quality can drop. This
     * pins that the resulting graph is still good enough to retrieve against, measured the same way
     * the on-disk modes are.
     */
    @Test
    @Timeout(value = 600, unit = TimeUnit.SECONDS)
    void recallStaysHighAfterTheSwitch()
    {
        final Random        random  = new Random(55);
        final List<float[]> vectors = clusteredVectors(random, 2000);

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", inMemoryPqConfig(), new CountingVectorizer()))
        {
            final List<Long> ids = new ArrayList<>();
            for(int i = 0; i < vectors.size(); i++)
            {
                ids.add(map.add(new Doc("d" + i, vectors.get(i))));
            }

            index.optimize();
            assertTrue(index.isPqCompressionActive());

            final int    k           = 10;
            final int    queryCount  = 50;
            final Random queryRandom = new Random(55 * 31 + 5);
            double       totalRecall = 0;

            for(int q = 0; q < queryCount; q++)
            {
                final float[] query = nearVector(queryRandom, vectors.get(queryRandom.nextInt(vectors.size())));

                final Set<Long> expected = new HashSet<>(bruteForceTopK(query, vectors, ids, k));
                final Set<Long> actual   = topK(index, query, k);

                actual.retainAll(expected);
                totalRecall += (double)actual.size() / k;
            }

            final double recall = totalRecall / queryCount;
            assertTrue(recall >= 0.90,
                "recall@" + k + " after the switch was " + recall + ". Construction under compressed"
                    + " scoring does no reranking, so a large drop here means beamWidth needs raising"
                    + " for this mode.");
        }
    }

    private void runQueries(
        final VectorIndex<Doc> index  ,
        final List<float[]>    vectors,
        final int              count  ,
        final int              seed
    )
    {
        final Random random = new Random(seed);
        for(int q = 0; q < count; q++)
        {
            index.search(nearVector(random, vectors.get(random.nextInt(vectors.size()))), 10);
        }
    }

    /**
     * The graph the index is currently serving, read directly rather than inferred from searches.
     * <p>
     * Reflection because the field is private, and private is right for it - this is the one test
     * that needs to assert graph membership exactly, and that does not justify widening the
     * production surface. Nothing else here reaches past the public API.
     *
     * @param index the index to read
     * @return its current in-memory graph
     */
    private static OnHeapGraphIndex publishedGraph(final VectorIndex.Default<Doc> index)
    {
        try
        {
            final Field field = VectorIndex.Default.class.getDeclaredField("index");
            field.setAccessible(true);
            return (OnHeapGraphIndex)field.get(index);
        }
        catch(final ReflectiveOperationException e)
        {
            throw new AssertionError("cannot read the index's graph", e);
        }
    }

    private static Set<Long> topK(final VectorIndex<Doc> index, final float[] query, final int k)
    {
        final Set<Long> ids = new HashSet<>();
        for(final ScoredSearchResult.Entry<Doc> entry : index.search(query, k))
        {
            ids.add(entry.entityId());
        }
        return ids;
    }

    private static List<Long> bruteForceTopK(
        final float[]       query  ,
        final List<float[]> vectors,
        final List<Long>    ids    ,
        final int           k
    )
    {
        final Integer[] order = new Integer[vectors.size()];
        final float[]   sims  = new float[vectors.size()];
        for(int i = 0; i < vectors.size(); i++)
        {
            order[i] = i;
            sims[i]  = cosine(query, vectors.get(i));
        }
        java.util.Arrays.sort(order, (a, b) -> Float.compare(sims[b], sims[a]));

        final List<Long> top = new ArrayList<>(k);
        for(int i = 0; i < k; i++)
        {
            top.add(ids.get(order[i]));
        }
        return top;
    }

    private static float cosine(final float[] a, final float[] b)
    {
        float dot = 0, na = 0, nb = 0;
        for(int i = 0; i < a.length; i++)
        {
            dot += a[i] * b[i];
            na  += a[i] * a[i];
            nb  += b[i] * b[i];
        }
        return (float)(dot / (Math.sqrt(na) * Math.sqrt(nb)));
    }
}
