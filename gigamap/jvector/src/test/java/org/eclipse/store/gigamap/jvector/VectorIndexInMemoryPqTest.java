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
import org.eclipse.store.gigamap.types.ScoredSearchResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
     * As {@link #inMemoryPqConfig()}, but with eventual indexing on, so a mutation is applied by a
     * background callback holding only the read lock rather than synchronously under the monitor.
     * That is the path the switch window has to survive, and the synchronous one does not exercise
     * it: there a mutation defers its builder op on {@code cleanupInProgress} instead.
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
     * A mutation landing between the plan and the swap must discard the switch, not be overwritten
     * by it.
     * <p>
     * The gap is real and cannot be locked away. Phase 1 reads the GigaMap under the monitor, phase
     * 2 builds the replacement with no lock held, and only phase 3 takes the write lock - because
     * holding the write lock and then reaching for the monitor is the order that deadlocks against
     * {@code internalRemoveAll}. So the index can move on while the replacement is being built, and
     * a replacement built from the older picture would undo whatever moved.
     * <p>
     * The resolution is to discard rather than reconcile: {@code structuralModCount} is checked
     * again under the write lock, and a replacement that no longer describes the index is dropped.
     * The switch is not lost, only deferred - the next optimization plans again from the current
     * state. That is the trade this test pins: <b>the data always wins, the switch waits.</b>
     * <p>
     * Runs with eventual indexing so the mutation reaches the graph through a background callback
     * holding only the read lock, which is the path that can overtake the plan. The queue is drained
     * before asserting, because {@code map.set} under eventual indexing only enqueues - asserting
     * without the drain races the background thread, and an earlier version of this test did.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void aMutationWhilePreparingDiscardsTheSwitchRatherThanTheMutation()
    {
        final Random        random  = new Random(31337);
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

            // The entity updated inside the window, and the vector it moves to - far from where it
            // started, so a search near the new one cannot find it by accident.
            final int     target   = 42;
            final long    targetId = ids.get(target);
            final float[] moved    = nearVector(new Random(99), vectors.get(900));

            internal.pqSwitchWindowTestHook = () ->
                map.set(targetId, new Doc("d" + target, moved));

            index.optimize();

            assertFalse(index.isPqCompressionActive(),
                "a mutation while the replacement was being prepared must discard it: adopting a"
                    + " graph built from the older picture would undo that mutation");

            // The mutation itself is untouched - it went through the ordinary path.
            internal.backgroundTaskManager.drainQueue();
            assertTrue(topK(index, moved, 10).contains(targetId),
                "the mutation must survive: it is the switch that waits, not the data");

            // And the switch is deferred, not abandoned. Nothing mutates this time, so it lands.
            internal.pqSwitchWindowTestHook = null;
            index.optimize();

            assertTrue(index.isPqCompressionActive(),
                "the next optimization must switch, planning from the state the mutation left");
            assertTrue(topK(index, moved, 10).contains(targetId),
                "the mutation must still be found after the switch finally happens");
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

            // And an update.
            final long updated = ids.get(3);
            final float[] replacement = randomUnit(new Random(9191));
            map.set(updated, new Doc("d3-updated", replacement));
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

            final long updated = ids.get(3);
            final float[] replacement = randomUnit(new Random(9191));
            map.set(updated, new Doc("d3-updated", replacement));
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
