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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the persist window: the stretch of {@code doPersistToDisk} Phase 2 during
 * which the parentMap monitor is released, sync-mode mutations defer their builder ops, and the
 * in-memory builder is swapped underneath them (internal #142).
 * <p>
 * Covered here:
 * <ul>
 *   <li>a drain on the persist thread racing a drain on an application thread must not double-add
 *       an ordinal ({@code IllegalStateException: Node N already exists}), which used to abort the
 *       drain, lose the op and kill the persist cycle for the rest of the session;</li>
 *   <li>a persist that races ongoing writes must still re-enter incremental mode instead of falling
 *       back to full in-memory mode, since the fallback is what put a swapped builder underneath the
 *       deferred ops in the first place;</li>
 *   <li>an update whose computed vector did not change must not perform any graph work.</li>
 * </ul>
 */
@Tag("slow")
class VectorIndexPersistWindowConcurrencyTest
{
    static final int DIM             = 64;
    static final int ENTITY_COUNT    = 60;
    static final int HOT_ORDINALS    = 4;
    static final int OPS_PER_ORDINAL = 12;
    static final int WRITER_BURSTS   = 2;
    static final int PERSIST_ROUNDS  = 6;

    record Doc(String content, float[] embedding) {}

    /** Computed (non-embedded) vectorizer: the branch the reported stack trace came from. */
    static class ComputedDocVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.embedding();
        }
    }

    /**
     * Deterministic pseudo-random unit vector. Two calls with the same seed produce equal but not
     * identical arrays, so an equality-based fast path is actually exercised.
     */
    static float[] vec(final int seed)
    {
        // Scramble the seed: java.util.Random's first outputs for nearby seeds are strongly
        // correlated, which would make "nearest neighbor" assertions depend on luck.
        final Random random = new Random(seed * 6364136223846793005L + 1442695040888963407L);
        final float[] vector = new float[DIM];
        float norm = 0;
        for(int i = 0; i < DIM; i++)
        {
            vector[i] = random.nextFloat() * 2 - 1;
            norm += vector[i] * vector[i];
        }
        norm = (float)Math.sqrt(norm);
        for(int i = 0; i < DIM; i++)
        {
            vector[i] /= norm;
        }
        return vector;
    }

    private static VectorIndexConfiguration diskConfig(final Path indexDirectory)
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDirectory)
            .build();
    }

    /**
     * Reads a private field of {@link VectorIndex.Default}. The state under test (incremental mode,
     * the deferred op queue) is deliberately internal; asserting on it is what makes these tests
     * fail for the right reason instead of only observing degraded-but-still-correct search.
     */
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

    private static boolean isIncrementalMode(final VectorIndex<?> index)
    {
        return internalState(index, "incrementalMode");
    }

    private static int deferredOpCount(final VectorIndex<?> index)
    {
        final Queue<Runnable> queue = internalState(index, "deferredBuilderOps");
        return queue == null ? 0 : queue.size();
    }

    /**
     * Meeting point for the two drains. Timeouts are benign: they only mean the other side did not
     * reach its drain this round, so the round simply does not collide.
     *
     * @return {@code true} if both sides met
     */
    private static boolean rendezvous(final CyclicBarrier barrier)
    {
        try
        {
            barrier.await(2, TimeUnit.SECONDS);
            return true;
        }
        catch(final TimeoutException e)
        {
            barrier.reset();
        }
        catch(final BrokenBarrierException e)
        {
            // the other side timed out and reset the barrier; nothing left to synchronize on
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        return false;
    }


    // ==================== 1. Concurrent drain must not double-add ====================

    /**
     * {@code drainDeferredBuilderOps} used to run without any lock: the persist thread drains after
     * releasing {@code builderLock}, while an application thread drains the same queue holding only
     * the GigaMap monitor. Two ops for the same ordinal then both observed
     * {@code containsNode(ordinal) == false} and both called {@code addGraphNode}, so one died with
     * {@code IllegalStateException: Node N already exists} - aborting the drain (losing the polled
     * op) and failing every subsequent persistence tick.
     * <p>
     * {@code persistPhase2TestHook} queues several ops per hot ordinal inside the persist window and
     * releases a competing writer; {@code drainEntryTestHook} then parks both threads immediately in
     * front of the drain so they enter it together.
     */
    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    @SuppressWarnings("unchecked")
    void concurrentDrainDuringPersistDoesNotDoubleAdd(@TempDir final Path indexDir)
        throws InterruptedException
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", diskConfig(indexDir), new ComputedDocVectorizer()))
        {
            final long[] ids = new long[ENTITY_COUNT];
            for(int i = 0; i < ENTITY_COUNT; i++)
            {
                ids[i] = map.add(new Doc("d" + i, vec(i)));
            }
            index.persistToDisk(); // enter incremental on-disk mode

            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            final AtomicReference<Throwable> writerFailure = new AtomicReference<>();
            final AtomicBoolean              writerActive  = new AtomicBoolean();
            final CyclicBarrier              drainBarrier  = new CyclicBarrier(2);
            final AtomicBoolean              collided      = new AtomicBoolean();

            // Both the persist thread and the competing writer park here right before draining.
            def.drainEntryTestHook = () ->
            {
                if(rendezvous(drainBarrier))
                {
                    collided.set(true);
                }
            };

            try
            {
                for(int round = 0; round < PERSIST_ROUNDS; round++)
                {
                    final int roundIndex = round;
                    final AtomicReference<Thread> writer = new AtomicReference<>();

                    // Dirty the index, otherwise the persist is skipped as incremental-clean and
                    // never reaches Phase 2.
                    map.set(ids[ENTITY_COUNT - 1], new Doc("last", vec(700_000 + roundIndex)));

                    def.persistPhase2TestHook = () ->
                    {
                        def.persistPhase2TestHook = null;

                        // Queue a RUN of deferred ops per hot ordinal. Consecutive queue entries for
                        // the same ordinal are what makes two concurrent drains collide on it, which
                        // is the shape a bulk load produces (every edge insert re-updates both
                        // endpoint nodes).
                        for(int i = 0; i < HOT_ORDINALS; i++)
                        {
                            for(int pass = 0; pass < OPS_PER_ORDINAL; pass++)
                            {
                                map.set(ids[i], new Doc("d" + i, vec(1_000 + roundIndex * 1_000 + pass * 100 + i)));
                            }
                        }

                        writerActive.set(true);
                        collided.set(false);
                        final Thread writerThread = new Thread(() ->
                        {
                            try
                            {
                                // A bounded burst of real updates on the same hot ordinals, so both
                                // drains find clustered work for the same nodes.
                                for(int pass = 0; pass < WRITER_BURSTS && writerActive.get(); pass++)
                                {
                                    for(int i = 0; i < HOT_ORDINALS; i++)
                                    {
                                        for(int repeat = 0; repeat < OPS_PER_ORDINAL; repeat++)
                                        {
                                            map.set(ids[i], new Doc("d" + i,
                                                vec(500_000 + pass * 1_000 + repeat * 100 + i)));
                                        }
                                    }
                                }

                                // Then wait for the drain window with updates that re-set an
                                // unchanged vector: those still run the drain (and so meet the
                                // barrier) but queue no further graph work, keeping the queue - and
                                // the test runtime - bounded.
                                final float[] parked = vec(600_000);
                                while(writerActive.get() && !collided.get())
                                {
                                    map.set(ids[HOT_ORDINALS], new Doc("parked", parked));
                                    Thread.sleep(1);
                                }
                            }
                            catch(final InterruptedException e)
                            {
                                Thread.currentThread().interrupt();
                            }
                            catch(final Throwable t)
                            {
                                writerFailure.compareAndSet(null, t);
                            }
                        }, "vector-index-competing-writer");
                        writerThread.setDaemon(true);
                        writerThread.start();
                        writer.set(writerThread);
                    };

                    index.persistToDisk();

                    writerActive.set(false);
                    final Thread writerThread = writer.get();
                    if(writerThread != null)
                    {
                        writerThread.join(TimeUnit.SECONDS.toMillis(30));
                        // A writer that outlives its round would keep mutating into the next one and
                        // make every later assertion depend on timing, so fail here instead.
                        assertFalse(writerThread.isAlive(),
                            "the competing writer must terminate before round " + roundIndex + " ends");
                    }

                    assertNull(writerFailure.get(),
                        "a concurrent writer must not observe a builder-op failure in round " + roundIndex);
                    assertTrue(collided.get(),
                        "the writer must actually have met the persist thread at the drain in round " + roundIndex);
                }
            }
            finally
            {
                writerActive.set(false);
                def.drainEntryTestHook   = null;
                def.persistPhase2TestHook = null;
            }

            // The queue must be fully applied, not stranded by an aborted drain.
            assertEquals(0, deferredOpCount(index), "deferred builder ops must be fully drained");

            // Normalize to a known state and verify the graph still holds every entity exactly once.
            for(int i = 0; i < ENTITY_COUNT; i++)
            {
                map.set(ids[i], new Doc("d" + i, vec(9_000 + i)));
            }
            index.optimize();

            final Set<Long> found = new HashSet<>();
            for(int i = 0; i < ENTITY_COUNT; i++)
            {
                final long hit = index.search(vec(9_000 + i), 1).stream()
                    .findFirst()
                    .orElseThrow()
                    .entityId();
                assertEquals(ids[i], hit, "entity " + i + " must still be its own nearest neighbor");
                assertTrue(found.add(hit), "entity " + i + " must not be indexed twice");
            }
        }
    }


    // ==================== 2. A persist racing writes must re-enter incremental mode ====================

    /**
     * {@code reenterIncrementalMode} reloads the file it has just written. It used to validate that
     * file against the LIVE store witnesses, but persist Phase 2 runs with the parentMap monitor
     * released, so any mutation landing in that window advances the live counters past the written
     * graph and the freshly written, perfectly usable file was rejected: "Disk index metadata
     * mismatch ... will rebuild" followed by "staying in full in-memory mode". That mode flip - a
     * builder swap between deferral and drain - is the precondition for the double-add crash.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @SuppressWarnings("unchecked")
    void persistRacingAWriteStaysInIncrementalMode(@TempDir final Path indexDir)
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", diskConfig(indexDir), new ComputedDocVectorizer()))
        {
            final long[]    ids      = new long[10];
            final float[][] expected = new float[ids.length][];
            for(int i = 0; i < ids.length; i++)
            {
                expected[i] = vec(i);
                ids[i]      = map.add(new Doc("d" + i, expected[i]));
            }

            index.persistToDisk();
            assertTrue(isIncrementalMode(index), "the first persist must enter incremental mode");

            // Dirty the index, otherwise the next persist is skipped as incremental-clean.
            expected[1] = vec(101);
            map.set(ids[1], new Doc("d1", expected[1]));

            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            def.persistPhase2TestHook = () ->
            {
                def.persistPhase2TestHook = null;
                // Advances structuralModCount after the .meta witnesses were captured in Phase 1.
                expected[0] = vec(777);
                map.set(ids[0], new Doc("d0", expected[0]));
            };

            index.persistToDisk();

            assertTrue(isIncrementalMode(index),
                "a persist racing a write must reload its own output instead of dropping to full in-memory mode");
            assertEquals(0, deferredOpCount(index), "deferred builder ops must be fully drained");

            // The Phase 2 mutation itself must have survived the re-entry, and so must everything else.
            assertEquals(ids[0], index.search(expected[0], 1).stream().findFirst().orElseThrow().entityId(),
                "the mutation that landed in the persist window must be searchable");
            for(int i = 1; i < ids.length; i++)
            {
                assertEquals(ids[i], index.search(expected[i], 1).stream().findFirst().orElseThrow().entityId(),
                    "entity " + i + " must survive the persist");
            }
        }
    }


    // ==================== 3. Unchanged computed vector must be a no-op ====================

    /**
     * In computed mode the stored vector IS the indexed value, so an update that leaves it untouched
     * has nothing to do. Without the equality check every {@code update()} paid
     * {@code markNodeDeleted + removeDeletedNodes + addGraphNode} - two ForkJoinPool round trips per
     * level - which is what flooded the deferred op queue during a bulk load (internal #142).
     * {@code structuralModCount} is the observable witness: it is bumped by exactly the
     * {@code markContentChanged()} that the fast path must skip.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @SuppressWarnings("unchecked")
    void updateWithUnchangedComputedVectorDoesNoGraphWork(@TempDir final Path indexDir)
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", diskConfig(indexDir), new ComputedDocVectorizer()))
        {
            final long a = map.add(new Doc("a", vec(0)));
            final long b = map.add(new Doc("b", vec(1)));

            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            final long modCountAfterAdds = def.structuralModCount;

            // Same vector content (a fresh array), unrelated field changed.
            map.set(a, new Doc("a-renamed", vec(0)));
            assertEquals(modCountAfterAdds, def.structuralModCount,
                "an update that does not change the vector must not count as a structural change");
            assertEquals(a, index.search(vec(0), 1).stream().findFirst().orElseThrow().entityId(),
                "the entity must stay searchable by its unchanged vector");

            // A real vector change must still be recorded and applied.
            map.set(a, new Doc("a-renamed", vec(2)));
            assertTrue(def.structuralModCount > modCountAfterAdds,
                "an update that changes the vector must count as a structural change");
            assertEquals(a, index.search(vec(2), 1).stream().findFirst().orElseThrow().entityId(),
                "the entity must be searchable by its new vector");
            assertEquals(b, index.search(vec(1), 1).stream().findFirst().orElseThrow().entityId(),
                "the untouched entity must be unaffected");
        }
    }
}
