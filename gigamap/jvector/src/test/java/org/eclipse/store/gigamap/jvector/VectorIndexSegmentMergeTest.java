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
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import io.github.jbellis.jvector.graph.disk.feature.FeatureId;
import io.github.jbellis.jvector.util.Bits;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The background merge of an on-disk index's segments: built from the vector file into one new segment,
 * published by moving the owners, and committed by the next flush, while mutations, searches and a close
 * may happen at any of its stages.
 */
@Tag("slow")
class VectorIndexSegmentMergeTest
{
    private static final String NAME      = "embeddings";
    private static final int    DIMENSION = 32;
    private static final long   AWAIT_MS  = 60_000L;

    record Document(String content, float[] embedding) {}

    static final class DocumentVectorizer extends Vectorizer<Document>
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            return entity.embedding();
        }
    }

    /** The format matrix of the merged segment. */
    enum Format
    {
        INLINE      (VectorStorage.INLINE, ApproximateScoring.NONE        ),
        NVQ         (VectorStorage.NVQ   , ApproximateScoring.NONE        ),
        FUSED_PQ    (VectorStorage.INLINE, ApproximateScoring.FUSED_PQ    ),
        NVQ_FUSED_PQ(VectorStorage.NVQ   , ApproximateScoring.FUSED_PQ    ),
        PQ_IN_MEMORY(VectorStorage.INLINE, ApproximateScoring.PQ_IN_MEMORY);

        final VectorStorage      storage;
        final ApproximateScoring scoring;

        Format(
            final VectorStorage      storage,
            final ApproximateScoring scoring
        )
        {
            this.storage = storage;
            this.scoring = scoring;
        }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void merge_twoSegmentsAtFactorTwo_publishesOneSegmentThatTheNextPersistCommits(@TempDir final Path tempDir)
        throws InterruptedException
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(1);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE, 2)))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            final long first = map.add(new Document("first", basis(0)));
            addRandom(map, random, 300);
            index.persistToDisk();
            final long second = map.add(new Document("second", basis(1)));
            addRandom(map, random, 200);
            index.persistToDisk();

            awaitMerges(def, 1);
            assertEquals(1, segments(index).size(), "the merged segment replaces both");
            assertEquals(0, def.consolidationCount(), "nothing was rebuilt under the locks");
            assertEquals(3, SegmentLayout.graphFiles(indexDir, NAME).size(),
                "the manifest still lists the merged segments until the next persist");
            assertEachEntityLiveInOneSource(index, map.size());
            assertEquals(first, topHit(index, basis(0)));
            assertEquals(second, topHit(index, basis(1)));

            index.persistToDisk();
            assertEquals(1, SegmentLayout.graphFiles(indexDir, NAME).size(),
                "the commit lists only the merged segment and deletes the files of the others");
            assertEquals(map.size(), segments(index).get(0).nodeCount());
            assertNoDuplicates(index.search(basis(2), 100));
            assertEquals(100, index.search(basis(2), 100).size());
        }
    }

    /**
     * A flush that publishes while a merge runs finds the run scheduled and schedules none. When that run then
     * fails or is abandoned, it must look again, or the merge the flush made due waits for the next flush.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void merge_flushDuringARunThatFails_isMergedAfterIt(@TempDir final Path tempDir) throws InterruptedException
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(6);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE, 2)))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            addRandom(map, random, 300);
            index.persistToDisk();
            addRandom(map, random, 200);

            final CountDownLatch built   = new CountDownLatch(1);
            final CountDownLatch release = new CountDownLatch(1);
            def.mergeStageTestHook = stage ->
            {
                if(stage == VectorIndex.Default.MergeStage.BUILT)
                {
                    def.mergeStageTestHook = null;
                    built.countDown();
                    awaitQuietly(release);
                    throw new IllegalStateException("the run fails");
                }
            };
            index.persistToDisk();
            assertTrue(built.await(AWAIT_MS, TimeUnit.MILLISECONDS), "the first run must reach BUILT");

            // Publishes a third segment while the run is scheduled: its trigger schedules nothing.
            addRandom(map, random, 200);
            index.persistToDisk();
            release.countDown();

            assertTrue(def.awaitMergesIdle(AWAIT_MS));
            assertTrue(def.mergeCount() >= 1L, "the failed run looked again for the merge the flush asked for");
            assertEachEntityLiveInOneSource(index, map.size());
        }
    }

    /**
     * A merge reserves, writes and opens its segment without the flush permit, so it can do so while a flush cleans
     * up the files the manifest does not list. The cleanup must leave the merged segment's files in place: deleted,
     * the merge fails reading them back, or, once open, the next persist commits a manifest naming files that are
     * gone, and the next load rebuilds the graph.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void merge_writtenWhileAFlushCleansUp_keepsItsFilesThroughTheNextCommit(@TempDir final Path tempDir)
        throws InterruptedException, IOException
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(7);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE, 2)))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            addRandom(map, random, 300);
            index.persistToDisk();
            addRandom(map, random, 200);

            final CountDownLatch built        = new CountDownLatch(1);
            final CountDownLatch cleanupBegan = new CountDownLatch(1);
            final CountDownLatch written      = new CountDownLatch(1);
            def.mergeStageTestHook = stage ->
            {
                if(stage == VectorIndex.Default.MergeStage.BUILT)
                {
                    built.countDown();
                    awaitQuietly(cleanupBegan);
                }
                else if(stage == VectorIndex.Default.MergeStage.WRITTEN)
                {
                    def.mergeStageTestHook = null;
                    // No second merge: it would replace the merged segment and hide what the cleanup did.
                    def.mergesSuspended = true;
                    written.countDown();
                }
            };
            // The second segment makes the merge due; it waits before reserving its sequence.
            index.persistToDisk();
            assertTrue(built.await(AWAIT_MS, TimeUnit.MILLISECONDS), "the merge must reach BUILT");

            addRandom(map, random, 100);
            def.reapListingTestHook = () ->
            {
                def.reapListingTestHook = null;
                cleanupBegan.countDown();
                // The merge reserves, writes and opens its segment before this cleanup lists the directory.
                awaitQuietly(written);
            };
            index.persistToDisk();

            assertTrue(def.awaitMergesIdle(AWAIT_MS));
            assertEquals(1L, def.mergeCount(), "the merge must publish its segment");
            for(final DiskSegment segment : segments(index))
            {
                assertSegmentFilesExist(indexDir, segment.sequence(), "an open segment's files must survive the cleanup");
            }

            // Commits the merged segment.
            index.persistToDisk();
            final SegmentManifest manifest = SegmentManifest.read(SegmentLayout.manifest(indexDir, NAME));
            for(final SegmentManifest.Segment listed : manifest.segments())
            {
                assertSegmentFilesExist(indexDir, listed.sequence(), "the manifest must not list deleted files");
            }
            assertEachEntityLiveInOneSource(index, map.size());
        }
    }

    /** the graph and the ordinal map, which every segment has */
    private static void assertSegmentFilesExist(final Path indexDir, final long sequence, final String message)
    {
        for(final String file : List.of(
            SegmentFiles.graphFileName(NAME, sequence), SegmentFiles.ordinalMapFileName(NAME, sequence)))
        {
            assertTrue(Files.exists(indexDir.resolve(file)), message + ": " + file);
        }
    }

    /**
     * A publication that fails after the merged segment joined the set, before or after the owners moved, is
     * withdrawn: the merged segment leaves the set and its owners go back, since its files are deleted. The
     * segments it was to replace answer as before, and the next persist commits them.
     */
    @ParameterizedTest
    @EnumSource(value = VectorIndex.Default.MergeStage.class, names = {"JOINED", "MOVED"})
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void merge_publicationFailsAfterTheJoin_isWithdrawn(
        final VectorIndex.Default.MergeStage stage  ,
        @TempDir final Path                  tempDir
    )
        throws InterruptedException
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(5);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE, 2)))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            final long first = map.add(new Document("first", basis(0)));
            addRandom(map, random, 300);
            index.persistToDisk();
            final long second = map.add(new Document("second", basis(1)));
            addRandom(map, random, 200);

            final CountDownLatch failed = new CountDownLatch(1);
            def.mergeStageTestHook = reachedStage ->
            {
                if(reachedStage == stage)
                {
                    def.mergeStageTestHook = null;
                    // No second attempt, so the state the failure left is what is checked.
                    def.mergesSuspended    = true;
                    failed.countDown();
                    throw new IllegalStateException("publication failed at " + stage);
                }
            };
            index.persistToDisk();
            assertTrue(failed.await(AWAIT_MS, TimeUnit.MILLISECONDS), "the merge must reach " + stage);
            assertTrue(def.awaitMergesIdle(AWAIT_MS));

            assertEquals(0L, def.mergeCount());
            assertEquals(2, segments(index).size(), "the merged segment left the set again");
            assertEquals(2, SegmentLayout.graphFiles(indexDir, NAME).size(), "and its files are gone");
            assertEachEntityLiveInOneSource(index, map.size());
            assertEquals(first, topHit(index, basis(0)));
            assertEquals(second, topHit(index, basis(1)));

            final long added = map.add(new Document("added", basis(2)));
            index.persistToDisk();
            assertEachEntityLiveInOneSource(index, map.size());
            assertEquals(added, topHit(index, basis(2)));
            assertEquals(first, topHit(index, basis(0)));
        }
    }

    /**
     * An entity removed or updated while the merge runs is no longer owned by the merged segments when the
     * owners move, so it keeps its newer state, whichever stage the merge was at.
     */
    @ParameterizedTest
    @EnumSource(value = VectorIndex.Default.MergeStage.class, names = {"SELECTED", "BUILT", "WRITTEN", "JOINED"})
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void merge_deleteAndUpdateDuringTheMerge_keepTheirNewerState(
        final VectorIndex.Default.MergeStage stage  ,
        @TempDir final Path                  tempDir
    )
        throws InterruptedException
    {
        final Path   storageDir = tempDir.resolve("storage");
        final Path   indexDir   = tempDir.resolve("index");
        final Random random     = new Random(2);

        final long removed;
        final long updated;
        final long kept;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Document>             map   = rootOf(storage);
            final VectorIndex<Document>         index = register(map, configuration(indexDir, Format.INLINE, 2));
            final VectorIndex.Default<Document> def   = (VectorIndex.Default<Document>)index;

            removed = map.add(new Document("removed", basis(0)));
            addRandom(map, random, 300);
            index.persistToDisk();
            updated = map.add(new Document("updated", basis(1)));
            kept    = map.add(new Document("kept", basis(2)));
            addRandom(map, random, 200);

            final CountDownLatch reached = new CountDownLatch(1);
            final CountDownLatch release = new CountDownLatch(1);
            def.mergeStageTestHook = reachedStage ->
            {
                if(reachedStage == stage)
                {
                    reached.countDown();
                    awaitQuietly(release);
                }
            };
            index.persistToDisk();
            assertTrue(reached.await(AWAIT_MS, TimeUnit.MILLISECONDS), "the merge must reach " + stage);

            // The merge holds the flush permit from JOINED on, so this one must not persist.
            map.removeById(removed);
            map.set(updated, new Document("updated", basis(3)));
            assertEquals(updated, topHit(index, basis(3)), "searches go on during the merge");
            release.countDown();
            def.mergeStageTestHook = null;

            awaitMerges(def, 1);
            assertEachEntityLiveInOneSource(index, map.size());
            assertFalse(ids(index.search(basis(0), 200)).contains(removed), "the removed entity must stay removed");
            assertEquals(updated, topHit(index, basis(3)), "the update answers by its new vector");
            assertNotEquals(updated, topHit(index, basis(1)), "and not by the merged copy of its old one");
            assertEquals(kept, topHit(index, basis(2)), "an untouched entity answers from the merged segment");

            index.persistToDisk();
            storage.storeRoot();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Document>     map   = rootOf(storage);
            final VectorIndex<Document> index = map.index().get(VectorIndices.Category()).get(NAME);
            assertTrue(hasSegments(index), "the merged state must load, no rebuild");
            assertEachEntityLiveInOneSource(index, map.size());
            assertFalse(ids(index.search(basis(0), 200)).contains(removed));
            assertEquals(updated, topHit(index, basis(3)));
            assertEquals(kept, topHit(index, basis(2)));
        }
    }

    /**
     * The merged segment is written with the index's format and quantizers like any flushed segment, and
     * answers as well as the segments it replaces, before and after a restart.
     */
    @ParameterizedTest
    @EnumSource(Format.class)
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void merge_eachFormat_carriesTheFormatAndKeepsRecallThroughARestart(
        final Format        format ,
        @TempDir final Path tempDir
    )
        throws InterruptedException
    {
        final Path          storageDir = tempDir.resolve("storage");
        final Path          indexDir   = tempDir.resolve("index");
        final Random        random     = new Random(3);
        final List<float[]> vectors    = new ArrayList<>();
        final List<Long>    ids        = new ArrayList<>();

        final double recallMerged;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Document>             map   = rootOf(storage);
            final VectorIndex<Document>         index = register(map, configuration(indexDir, format, 2));
            final VectorIndex.Default<Document> def   = (VectorIndex.Default<Document>)index;

            // Above the PQ training threshold in the first segment, so both segments carry codes.
            addRecorded(map, random, 600, vectors, ids);
            index.persistToDisk();
            addRecorded(map, random, 300, vectors, ids);
            index.persistToDisk();

            awaitMerges(def, 1);
            final List<DiskSegment> segments = segments(index);
            assertEquals(1, segments.size());
            assertFalse(segments.get(0).flaggedForMerge());
            assertFormatIsCarried(format, segments.get(0));
            recallMerged = recall(index, vectors, ids);
            assertTrue(recallMerged >= 0.9, format + " recall of the merged segment: " + recallMerged);

            index.persistToDisk();
            storage.storeRoot();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex<Document> index = rootOf(storage).index().get(VectorIndices.Category()).get(NAME);
            assertTrue(hasSegments(index), "the merged segment must load after a restart");
            assertEquals(1, segments(index).size());
            assertFormatIsCarried(format, segments(index).get(0));
            assertEquals(recallMerged, recall(index, vectors, ids), 1e-9, "the same answers after a restart");
        }
    }

    /**
     * A merge is committed only by the next persist. A process that ends before loses the merge, not
     * the data: the manifest on disk still lists the merged segments, and the merged one's files are
     * deleted at the next load.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void restart_beforeTheMergeIsCommitted_loadsTheMergedSegmentsAgain(@TempDir final Path tempDir)
        throws InterruptedException
    {
        final Path   storageDir = tempDir.resolve("storage");
        final Path   indexDir   = tempDir.resolve("index");
        final Random random     = new Random(4);

        final long first;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Document> map = rootOf(storage);
            // No persist on shutdown: the close stands for a crash.
            final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                .dimension(DIMENSION)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .onDisk(true)
                .indexDirectory(indexDir)
                .segmentMergeFactor(2)
                .persistOnShutdown(false)
                .build();
            final VectorIndex<Document>         index = register(map, config);
            final VectorIndex.Default<Document> def   = (VectorIndex.Default<Document>)index;

            first = map.add(new Document("first", basis(0)));
            addRandom(map, random, 300);
            index.persistToDisk();
            addRandom(map, random, 200);
            index.persistToDisk();
            storage.storeRoot();
            awaitMerges(def, 1);
            assertEquals(3, SegmentLayout.graphFiles(indexDir, NAME).size());
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Document>             map   = rootOf(storage);
            final VectorIndex<Document>         index = map.index().get(VectorIndices.Category()).get(NAME);
            final VectorIndex.Default<Document> def   = (VectorIndex.Default<Document>)index;
            def.mergesSuspended = true;
            assertTrue(hasSegments(index), "the committed manifest must load, no rebuild");
            assertEquals(2, segments(index).size(), "the two segments the manifest lists");
            assertEquals(2, SegmentLayout.graphFiles(indexDir, NAME).size(), "the uncommitted merge is deleted");
            assertEachEntityLiveInOneSource(index, map.size());
            assertEquals(first, topHit(index, basis(0)));
        }
    }

    /**
     * A close stops a merge at its next stage and waits for it, since the merge reads the vector file the
     * close unmaps, and the merge publishes and leaves nothing.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void close_duringAMerge_stopsItAndLeavesOnlyTheCommittedSegments(@TempDir final Path tempDir)
        throws InterruptedException
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(6);

        final VectorIndex<Document>         index = register(map, configuration(indexDir, Format.INLINE, 2));
        final VectorIndex.Default<Document> def   = (VectorIndex.Default<Document>)index;
        addRandom(map, random, 300);
        index.persistToDisk();
        addRandom(map, random, 300);

        final CountDownLatch reached = new CountDownLatch(1);
        def.mergeStageTestHook = stage ->
        {
            if(stage == VectorIndex.Default.MergeStage.SELECTED)
            {
                reached.countDown();
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                while(!def.isMergeAbortRequested() && System.nanoTime() < deadline)
                {
                    Thread.onSpinWait();
                }
            }
        };
        index.persistToDisk();
        assertTrue(reached.await(AWAIT_MS, TimeUnit.MILLISECONDS));

        index.close();

        assertEquals(0, def.mergeCount(), "the merge must have stopped");
        assertEquals(2, SegmentLayout.graphFiles(indexDir, NAME).size(), "nothing of the merge is left");
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while(mergeThreadAlive())
        {
            assertTrue(System.nanoTime() < deadline, "the merge thread must end after the close");
            Thread.sleep(20);
        }
    }

    /**
     * A removeAll during a merge starts the index over: the merge publishes nothing into the new
     * generation, and that generation persists and merges as usual.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void removeAll_duringAMerge_publishesNothingIntoTheNewGeneration(@TempDir final Path tempDir)
        throws InterruptedException
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(7);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE, 2)))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            addRandom(map, random, 300);
            index.persistToDisk();
            addRandom(map, random, 300);

            final CountDownLatch reached = new CountDownLatch(1);
            final CountDownLatch release = new CountDownLatch(1);
            def.mergeStageTestHook = stage ->
            {
                if(stage == VectorIndex.Default.MergeStage.WRITTEN)
                {
                    reached.countDown();
                    awaitQuietly(release);
                }
            };
            index.persistToDisk();
            assertTrue(reached.await(AWAIT_MS, TimeUnit.MILLISECONDS));

            map.removeAll();
            release.countDown();
            assertTrue(def.awaitMergesIdle(AWAIT_MS));
            def.mergeStageTestHook = null;
            assertEquals(0, def.mergeCount(), "the merge of the cleared generation must publish nothing");
            assertTrue(segments(index).isEmpty());
            assertEquals(0, index.search(basis(0), 10).size());

            final long fresh = map.add(new Document("fresh", basis(0)));
            addRandom(map, random, 100);
            index.persistToDisk();
            assertEquals(1, segments(index).size());
            assertEquals(fresh, topHit(index, basis(0)));
            assertEachEntityLiveInOneSource(index, map.size());
        }
    }

    /**
     * A search that started before the merged segment joined the set does not have it, so the owners
     * must not move to it while that search runs: it would miss every entity moved.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void merge_searchInFlightWhenTheSegmentJoins_findsEveryEntity(@TempDir final Path tempDir)
        throws Exception
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(8);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE, 2)))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            def.mergesSuspended = true;
            final long first = map.add(new Document("first", basis(0)));
            addRandom(map, random, 300);
            index.persistToDisk();
            addRandom(map, random, 200);
            index.persistToDisk();

            // A search that holds the current set, without the merged segment, until the merge has joined.
            final CountDownLatch             searching = new CountDownLatch(1);
            final CountDownLatch             joined    = new CountDownLatch(1);
            final AtomicReference<Long>      hit       = new AtomicReference<>();
            final AtomicReference<Throwable> failure   = new AtomicReference<>();
            def.sourcesAcquiredTestHook = () ->
            {
                if(Thread.currentThread().getName().equals("held-search"))
                {
                    searching.countDown();
                    awaitQuietly(joined);
                    // Long enough for an owner move that did not wait for this search to have run.
                    sleepQuietly(500L);
                }
            };
            def.mergeStageTestHook = stage ->
            {
                if(stage == VectorIndex.Default.MergeStage.JOINED)
                {
                    joined.countDown();
                }
            };
            final Thread search = new Thread(() ->
            {
                try
                {
                    hit.set(topHit(index, basis(0)));
                }
                catch(final Throwable t)
                {
                    failure.set(t);
                }
            }, "held-search");
            search.start();
            assertTrue(searching.await(AWAIT_MS, TimeUnit.MILLISECONDS));

            def.mergesSuspended = false;
            requestMerge(def);
            search.join(AWAIT_MS);
            def.sourcesAcquiredTestHook = null;
            assertNull(failure.get());
            assertEquals(first, hit.get(), "the held search must find the entity the merge moves");
            awaitMerges(def, 1);
            assertEquals(first, topHit(index, basis(0)));
        }
    }

    // helpers

    private static void requestMerge(final VectorIndex.Default<Document> def)
    {
        try
        {
            final var method = VectorIndex.Default.class.getDeclaredMethod("scheduleMergeIfDue");
            method.setAccessible(true);
            method.invoke(def);
        }
        catch(final ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }

    private static void awaitMerges(
        final VectorIndex.Default<Document> def  ,
        final long                          count
    )
        throws InterruptedException
    {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MS);
        while(def.mergeCount() < count)
        {
            assertTrue(System.nanoTime() < deadline, "expected " + count + " merges, got " + def.mergeCount());
            Thread.sleep(10);
        }
        assertTrue(def.awaitMergesIdle(AWAIT_MS), "the merges must go idle");
    }

    private static boolean mergeThreadAlive()
    {
        return Thread.getAllStackTraces().keySet().stream()
            .anyMatch(thread -> thread.getName().equals("VectorIndex-Background-" + NAME + "-merge"));
    }

    private static void awaitQuietly(final CountDownLatch latch)
    {
        try
        {
            latch.await(AWAIT_MS, TimeUnit.MILLISECONDS);
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(final long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Every entity is accepted by exactly one source, see {@code VectorIndexSegmentsTest}.
     */
    private static void assertEachEntityLiveInOneSource(
        final VectorIndex<Document> index        ,
        final long                  expectedCount
    )
    {
        final VectorIndex.Default<Document> def     = (VectorIndex.Default<Document>)index;
        final SourceSet                     set     = def.sourceSet();
        final SegmentOwners                 owners  = set.owners().owners();
        final Map<Integer, Integer>         sources = new HashMap<>();
        for(final DiskSegment segment : set.segments())
        {
            final Bits accepted = segment.acceptBits(owners);
            for(int local = 0; local < segment.nodeCount(); local++)
            {
                if(accepted.get(local))
                {
                    sources.merge(segment.localToGlobal()[local], 1, Integer::sum);
                }
            }
        }
        for(final HeapGeneration frozen : set.frozen())
        {
            countHeapGraph(frozen.graph(), owners, frozen.slot(), sources);
        }
        countHeapGraph(internalState(def, "index"), owners, set.owners().liveSlot(), sources);

        assertEquals(expectedCount, sources.size(), "every entity with a vector must be live somewhere");
        sources.forEach((ordinal, count) ->
            assertEquals(1, count, "entity " + ordinal + " is live in " + count + " sources"));
    }

    private static void countHeapGraph(
        final OnHeapGraphIndex      graph  ,
        final SegmentOwners         owners ,
        final int                   slot   ,
        final Map<Integer, Integer> sources
    )
    {
        final Bits live = graph.getView().liveNodes();
        graph.nodeStream(0)
            .filter(ordinal -> live.get(ordinal) && owners.owner(ordinal) == slot)
            .forEach(ordinal -> sources.merge(ordinal, 1, Integer::sum));
    }

    @SuppressWarnings("unchecked")
    private static <T> T internalState(
        final VectorIndex.Default<?> index    ,
        final String                 fieldName
    )
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

    private static void assertFormatIsCarried(
        final Format      format ,
        final DiskSegment segment
    )
    {
        final Set<FeatureId> features = segment.graph().getFeatureSet();
        assertEquals(format.storage == VectorStorage.NVQ, features.contains(FeatureId.NVQ_VECTORS), format.name());
        assertEquals(format.scoring == ApproximateScoring.FUSED_PQ, features.contains(FeatureId.FUSED_PQ), format.name());
        if(format.scoring == ApproximateScoring.PQ_IN_MEMORY)
        {
            assertNotNull(segment.pqVectors(), "PQ_IN_MEMORY codes for segment " + segment.sequence());
            assertEquals(segment.nodeCount(), segment.pqVectors().count(), "by local ordinal");
        }
        else
        {
            assertNull(segment.pqVectors());
        }
    }

    private static double recall(
        final VectorIndex<Document> index  ,
        final List<float[]>         vectors,
        final List<Long>            ids
    )
    {
        final int    k       = 10;
        final Random queries = new Random(80);
        int          found   = 0;
        int          total   = 0;
        for(int q = 0; q < 20; q++)
        {
            final float[] query = randomVector(queries);
            final Set<Long> exact = new HashSet<>();
            final List<Integer> order = new ArrayList<>();
            for(int i = 0; i < vectors.size(); i++)
            {
                order.add(i);
            }
            order.sort((a, b) -> Double.compare(cosine(query, vectors.get(b)), cosine(query, vectors.get(a))));
            for(int i = 0; i < k; i++)
            {
                exact.add(ids.get(order.get(i)));
            }
            for(final ScoredSearchResult.Entry<Document> entry : index.search(query, k))
            {
                if(exact.contains(entry.entityId()))
                {
                    found++;
                }
            }
            total += k;
        }
        return (double)found / total;
    }

    private static double cosine(
        final float[] a,
        final float[] b
    )
    {
        double dot = 0;
        double na  = 0;
        double nb  = 0;
        for(int i = 0; i < a.length; i++)
        {
            dot += a[i] * b[i];
            na  += a[i] * a[i];
            nb  += b[i] * b[i];
        }
        return dot / Math.sqrt(na * nb);
    }

    private static VectorIndexConfiguration configuration(
        final Path   indexDir   ,
        final Format format     ,
        final int    mergeFactor
    )
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .vectorStorage(format.storage)
            .approximateScoring(format.scoring)
            .pqSubspaces(format.scoring == ApproximateScoring.NONE ? 0 : DIMENSION / 4)
            .segmentMergeFactor(mergeFactor)
            .build();
    }

    private static VectorIndex<Document> register(
        final GigaMap<Document>        map   ,
        final VectorIndexConfiguration config
    )
    {
        return map.index().register(VectorIndices.Category()).add(NAME, config, new DocumentVectorizer());
    }

    private static boolean hasSegments(final VectorIndex<Document> index)
    {
        final SourceSet set = ((VectorIndex.Default<Document>)index).sourceSet();
        return set != null && !set.segments().isEmpty();
    }

    private static List<DiskSegment> segments(final VectorIndex<Document> index)
    {
        return ((VectorIndex.Default<Document>)index).sourceSet().segments();
    }

    private static long topHit(
        final VectorIndex<Document> index,
        final float[]               query
    )
    {
        return index.search(query, 1).toList().get(0).entityId();
    }

    private static List<Long> ids(final VectorSearchResult<Document> result)
    {
        final List<Long> ids = new ArrayList<>();
        for(final ScoredSearchResult.Entry<Document> entry : result)
        {
            ids.add(entry.entityId());
        }
        return ids;
    }

    private static void assertNoDuplicates(final VectorSearchResult<Document> result)
    {
        final List<Long> ids = ids(result);
        assertEquals(ids.size(), new HashSet<>(ids).size(), "an entity answered twice: " + ids);
    }

    private static float[] basis(final int axis)
    {
        final float[] vector = new float[DIMENSION];
        vector[axis] = 1f;
        return vector;
    }

    private static float[] randomVector(final Random random)
    {
        final float[] vector = new float[DIMENSION];
        for(int i = 0; i < DIMENSION; i++)
        {
            // Away from the basis vectors the tests search for, so those answer unambiguously.
            vector[i] = random.nextFloat() * 0.2f - 0.1f;
        }
        return vector;
    }

    private static void addRandom(
        final GigaMap<Document> map   ,
        final Random            random,
        final int               count
    )
    {
        for(int i = 0; i < count; i++)
        {
            map.add(new Document("r" + i, randomVector(random)));
        }
    }

    private static void addRecorded(
        final GigaMap<Document> map    ,
        final Random            random ,
        final int               count  ,
        final List<float[]>     vectors,
        final List<Long>        ids
    )
    {
        for(int i = 0; i < count; i++)
        {
            final float[] vector = new float[DIMENSION];
            for(int d = 0; d < DIMENSION; d++)
            {
                vector[d] = random.nextFloat() * 2 - 1;
            }
            vectors.add(vector);
            ids.add(map.add(new Document("v" + i, vector)));
        }
    }


    /**
     * The root as the typed map: the storage manager is not generic over the root.
     */
    @SuppressWarnings("unchecked")
    private static GigaMap<Document> rootOf(final EmbeddedStorageManager storage)
    {
        return (GigaMap<Document>)storage.root();
    }
}
