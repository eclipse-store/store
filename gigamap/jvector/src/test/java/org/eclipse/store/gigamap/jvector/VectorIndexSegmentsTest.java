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
import io.github.jbellis.jvector.disk.ReaderSupplier;
import io.github.jbellis.jvector.disk.ReaderSupplierFactory;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.graph.disk.feature.FeatureId;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An on-disk index made of several segments: each persist writes the in-memory graph as one more.
 * Covers what N segments need of the load, the owners and the search - dense local ordinals translated
 * in the right places, each entity live in exactly one source, deletions surviving a restart - the
 * consolidation that rewrites everything when segments pile up while merges are held back, and the
 * rewrite of segments that no longer match the configuration.
 */
@Tag("slow")
class VectorIndexSegmentsTest
{
    private static final String NAME      = "embeddings";
    private static final int    DIMENSION = 32;

    private static final VectorTypeSupport VECTOR_TYPES = VectorizationProvider.getInstance().getVectorTypeSupport();

    record Document(String content, float[] embedding) {}

    static final class DocumentVectorizer extends Vectorizer<Document>
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            return entity.embedding();
        }

        @Override
        public boolean allowsNullVectors()
        {
            return true;
        }
    }

    /** The format matrix: every way a segment can store and score its vectors. */
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
    void flush_twoSegments_searchFindsEntitiesOfBoth(@TempDir final Path tempDir)
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(1);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE)))
        {
            final long first = map.add(new Document("first", basis(0)));
            addRandom(map, random, 300);
            index.persistToDisk();

            final long second = map.add(new Document("second", basis(1)));
            addRandom(map, random, 200);
            index.persistToDisk();

            assertEquals(2, SegmentLayout.graphFiles(indexDir, NAME).size(),
                "the persist must have flushed a second segment");
            assertTrue(hasSegments(index));
            assertEachEntityLiveInOneSource(index, map.size());
            assertEquals(first, topHit(index, basis(0)), "an entity of the first segment");
            assertEquals(second, topHit(index, basis(1)), "an entity of the second segment");
            assertNoDuplicates(index.search(basis(2), 50));
            assertEquals(50, index.search(basis(2), 50).size());
        }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void flush_updateAndRemoveAcrossSegments_onlyCurrentVersionsAnswer(@TempDir final Path tempDir)
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(2);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE)))
        {
            final long removed = map.add(new Document("removed", basis(0)));
            final long updated = map.add(new Document("updated", basis(1)));
            addRandom(map, random, 300);
            index.persistToDisk();

            final long movedTwice = map.add(new Document("movedTwice", basis(2)));
            addRandom(map, random, 100);
            index.persistToDisk();

            // One entity of each segment changes, one of them twice across a third segment.
            map.removeById(removed);
            map.set(updated, new Document("updated", basis(3)));
            map.set(movedTwice, new Document("movedTwice", basis(4)));
            index.persistToDisk();
            map.set(movedTwice, new Document("movedTwice", basis(5)));

            assertEquals(3, SegmentLayout.graphFiles(indexDir, NAME).size());
            assertEachEntityLiveInOneSource(index, map.size());
            assertFalse(ids(index.search(basis(0), 200)).contains(removed),
                "a removed entity must not answer, not even far down the list");
            assertNotEquals(removed, topHit(index, basis(0)), "a removed entity must not answer");
            assertEquals(updated, topHit(index, basis(3)), "an updated entity answers by its new vector");
            assertNotEquals(updated, topHit(index, basis(1)), "and no longer by its old one");
            assertEquals(movedTwice, topHit(index, basis(5)));
            assertNotEquals(movedTwice, topHit(index, basis(4)), "the copy in the third segment is stale");
            assertNotEquals(movedTwice, topHit(index, basis(2)), "and so is the one in the second");
            assertNoDuplicates(index.search(basis(6), 100));
        }
    }

    /**
     * An eventual-indexing operation still queued at the freeze moves into the frozen graph, whose flush
     * applies it, because the witnesses the flush commits count it. If applying it fails, the segment
     * would miss an entity the witnesses count, so the flush commits nothing and the next persist
     * consolidates from the store, which holds every entity.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void flush_movedOperationFails_commitsNothingAndTheNextPersistConsolidates(@TempDir final Path tempDir)
        throws Exception
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .eventualIndexing(true)
            .build();

        try(final VectorIndex<Document> index = register(map, config))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            addRandom(map, new Random(11), 300);
            index.persistToDisk();
            def.backgroundTaskManager.drainQueue();

            // Hold the worker inside its next operation, so the ones queued behind it are still queued
            // when the persist freezes.
            final CountDownLatch applying = new CountDownLatch(1);
            final CountDownLatch release  = new CountDownLatch(1);
            def.backgroundTaskManager.applyTestHook = () ->
            {
                def.backgroundTaskManager.applyTestHook = null;
                applying.countDown();
                awaitQuietly(release);
            };
            map.add(new Document("held", basis(2)));
            awaitQuietly(applying);
            final long moved = map.add(new Document("moved", basis(0)));

            final AtomicBoolean failed = new AtomicBoolean();
            def.movedOpTestHook = () ->
            {
                if(failed.compareAndSet(false, true))
                {
                    throw new IllegalStateException("simulated moved operation failure");
                }
            };
            final AtomicReference<Throwable> persistFailure = new AtomicReference<>();
            final Thread persist = new Thread(() ->
            {
                try
                {
                    index.persistToDisk();
                }
                catch(final Throwable t)
                {
                    persistFailure.set(t);
                }
            }, "persist");
            persist.start();
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while(persist.getState() != Thread.State.WAITING)
            {
                assertTrue(System.nanoTime() < deadline, "the persist must wait for the indexing lock");
                Thread.onSpinWait();
            }
            release.countDown();
            persist.join(TimeUnit.SECONDS.toMillis(60));
            def.movedOpTestHook = null;

            assertTrue(failed.get(), "the queued operation must have been moved into the frozen graph");
            assertNotNull(persistFailure.get(), "the flush must fail rather than commit a segment missing it");
            assertEquals(1, segments(index).size(), "nothing may be committed");
            assertEquals(0, def.consolidationCount());

            index.persistToDisk();
            def.backgroundTaskManager.drainQueue();
            assertEquals(1, def.consolidationCount(), "the next persist must consolidate from the store");
            assertEquals(1, SegmentLayout.graphFiles(indexDir, NAME).size());
            assertEachEntityLiveInOneSource(index, map.size());
            assertEquals(moved, topHit(index, basis(0)), "the entity whose moved operation failed must answer");
        }
    }

    private static void awaitQuietly(final CountDownLatch latch)
    {
        try
        {
            assertTrue(latch.await(30, TimeUnit.SECONDS), "timed out");
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void restart_threeSegmentsWithDeletions_keepsResultsAndDeletions(@TempDir final Path tempDir)
    {
        final Path   storageDir = tempDir.resolve("storage");
        final Path   indexDir   = tempDir.resolve("index");
        final Random random     = new Random(3);
        final float[] query     = randomVector(new Random(33));

        final long       removed;
        final List<Long> before;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Document>     map   = rootOf(storage);
            final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE));

            addRandom(map, random, 300);
            index.persistToDisk();
            removed = map.add(new Document("removed", query));
            addRandom(map, random, 100);
            index.persistToDisk();
            addRandom(map, random, 100);
            index.persistToDisk();

            // Removed after its segment was written, and committed by a manifest-only persist.
            map.removeById(removed);
            index.persistToDisk();
            assertEquals(3, SegmentLayout.graphFiles(indexDir, NAME).size(), "an empty heap writes no segment");

            before = ids(index.search(query, 20));
            assertFalse(before.contains(removed));
            assertEachEntityLiveInOneSource(index, map.size());
            storage.storeRoot();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Document>     map   = rootOf(storage);
            final VectorIndex<Document> index = map.index().get(VectorIndices.Category()).get(NAME);

            final List<Long> after = ids(index.search(query, 20));
            assertTrue(hasSegments(index), "all three segments must have loaded, no rebuild");
            assertEquals(before, after, "the same top-k from the same segments");
            assertFalse(after.contains(removed), "the deletion set must have survived the restart");
            assertFalse(ids(index.search(query, 200)).contains(removed), "not even far down the list");
            assertEachEntityLiveInOneSource(index, map.size());
            assertEquals(3, segments(index).size());
        }
    }

    /**
     * A scheduled background persist commits the deletions of entities in segments although nothing is
     * live and the flush threshold is far off: it has no segment to write, only the manifest. Declined
     * below the threshold instead, a crash after the removal would find the manifest stale and rebuild.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void backgroundPersist_withOnlyDeletions_commitsTheManifestBelowTheThreshold(@TempDir final Path tempDir)
        throws InterruptedException
    {
        final Path    storageDir = tempDir.resolve("storage");
        final Path    indexDir   = tempDir.resolve("index");
        final Random  random     = new Random(18);
        final float[] query      = randomVector(new Random(38));
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .persistenceIntervalMs(100L)
            .minChangesBetweenPersists(1)
            .persistOnShutdown(false) // the close stands for a crash
            .build();

        final long removed;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Document>             map   = rootOf(storage);
            final VectorIndex<Document>         index = register(map, config);
            final VectorIndex.Default<Document> def   = (VectorIndex.Default<Document>)index;

            addRandom(map, random, 200);
            removed = map.add(new Document("removed", query));
            index.persistToDisk();
            final long flushes = def.flushCount();

            map.removeById(removed);
            storage.storeRoot();

            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while(def.flushCount() == flushes)
            {
                assertTrue(System.nanoTime() < deadline, "the scheduled persist must commit the deletion");
                Thread.sleep(20);
            }
            assertEquals(1, SegmentLayout.graphFiles(indexDir, NAME).size(), "a deletion writes no segment");
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Document>     map   = rootOf(storage);
            final VectorIndex<Document> index = map.index().get(VectorIndices.Category()).get(NAME);

            assertTrue(hasSegments(index), "the manifest the background persist committed must load, no rebuild");
            assertFalse(ids(index.search(query, 200)).contains(removed), "the deletion must have been committed");
            assertEachEntityLiveInOneSource(index, map.size());
        }
    }

    /**
     * Each persist adds a segment, until the next one would exceed the configured maximum: that persist
     * consolidates everything into one segment instead and deletes the files of the ones it replaced.
     * The fallback for merges that fell behind, so they are held back here.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void consolidation_atTheSegmentCap_writesOneSegmentAndDeletesTheOthers(@TempDir final Path tempDir)
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(4);
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .maxSegments(2)
            .build();

        try(final VectorIndex<Document> index = register(map, config))
        {
            ((VectorIndex.Default<Document>)index).mergesSuspended = true;
            addRandom(map, random, 300);
            index.persistToDisk();
            addRandom(map, random, 100);
            index.persistToDisk();
            assertEquals(2, SegmentLayout.graphFiles(indexDir, NAME).size());
            assertEquals(0, ((VectorIndex.Default<Document>)index).consolidationCount());

            addRandom(map, random, 10);
            index.persistToDisk();

            assertEquals(1, ((VectorIndex.Default<Document>)index).consolidationCount());
            assertEquals(List.of(NAME + ".manifest", NAME + ".seg-2.graph", NAME + ".seg-2.l2g"),
                SegmentLayout.fileNames(indexDir), "one segment, and the files of the ones it replaced deleted");
            assertEachEntityLiveInOneSource(index, map.size());
            assertEquals(100, index.search(randomVector(random), 100).size());
        }
    }

    /**
     * With eventual indexing, each persist writes a segment too: the operations still queued at the
     * freeze move into the frozen graph, so the witnesses the flush commits cover exactly what it writes.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void flush_eventualIndexing_writesASegmentPerPersist(@TempDir final Path tempDir)
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(5);

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .eventualIndexing(true)
            .build();
        try(final VectorIndex<Document> index = register(map, config))
        {
            addRandom(map, random, 300);
            index.persistToDisk();
            addRandom(map, random, 100);
            index.persistToDisk();
            ((VectorIndex.Default<Document>)index).backgroundTaskManager.drainQueue();

            assertEquals(2, SegmentLayout.graphFiles(indexDir, NAME).size());
            assertEachEntityLiveInOneSource(index, map.size());
            assertEquals(100, index.search(randomVector(random), 100).size());
        }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void persist_sparseIds_graphFileFollowsTheNodeCount(@TempDir final Path tempDir) throws IOException
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(6);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE)))
        {
            addRandom(map, random, 3000);
            for(long id = 0; id < 2900; id++)
            {
                map.removeById(id);
            }
            index.persistToDisk();
        }

        final Path graph = SegmentLayout.onlyGraphFile(indexDir, NAME);
        try(final ReaderSupplier supplier = ReaderSupplierFactory.open(graph);
            final OnDiskGraphIndex disk = OnDiskGraphIndex.load(supplier))
        {
            assertEquals(100, disk.size(0));
            assertEquals(100, disk.getIdUpperBound(), "dense local ordinals, no record for the 2900 ids below");
        }
        // One node record is the vector, the neighbour count and maxDegree neighbours: far below 3000 of them.
        final long recordBytes = Integer.BYTES + DIMENSION * Float.BYTES + Integer.BYTES + 32L * Integer.BYTES;
        assertTrue(Files.size(graph) < 1000 * recordBytes,
            "the graph holds 100 nodes but takes " + Files.size(graph) + " bytes");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void flush_nullVectors_areInNoSegment(@TempDir final Path tempDir)
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(7);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE)))
        {
            for(int i = 0; i < 300; i++)
            {
                map.add(new Document("a" + i, i % 3 == 0 ? null : randomVector(random)));
            }
            index.persistToDisk();
            for(int i = 0; i < 90; i++)
            {
                map.add(new Document("b" + i, i % 3 == 0 ? null : randomVector(random)));
            }
            index.persistToDisk();

            final List<DiskSegment> segments = segments(index);
            assertEquals(2, segments.size());
            assertEquals(200, segments.get(0).nodeCount());
            assertEquals(60, segments.get(1).nodeCount());
            assertEachEntityLiveInOneSource(index, 260);
            final VectorSearchResult<Document> some = index.search(randomVector(random), 100);
            assertEquals(100, some.size());
            for(final ScoredSearchResult.Entry<Document> entry : some)
            {
                assertNotNull(entry.entity().embedding());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Format.class)
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void flush_eachFormat_recallAndRestartHold(final Format format, @TempDir final Path tempDir)
    {
        final Path          storageDir = tempDir.resolve("storage");
        final Path          indexDir   = tempDir.resolve("index");
        final Random        random     = new Random(8);
        final List<float[]> vectors    = new ArrayList<>();
        final List<Long>    ids        = new ArrayList<>();

        final double recallBefore;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Document>     map   = rootOf(storage);
            final VectorIndex<Document> index = register(map, configuration(indexDir, format));

            // Above the PQ training threshold in the first segment, so both segments carry codes.
            addRecorded(map, random, 600, vectors, ids);
            index.persistToDisk();
            addRecorded(map, random, 300, vectors, ids);
            index.persistToDisk();

            final List<DiskSegment> segments = segments(index);
            assertEquals(2, segments.size());
            assertFalse(segments.get(0).flaggedForMerge() || segments.get(1).flaggedForMerge(),
                "segments written for the configured format with the index's codebook");
            for(final DiskSegment segment : segments)
            {
                assertFormatIsCarried(format, segment);
            }

            recallBefore = recall(index, vectors, ids);
            assertTrue(recallBefore >= 0.9, format + " recall over two segments: " + recallBefore);
            storage.storeRoot();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex<Document> index = rootOf(storage).index().get(VectorIndices.Category()).get(NAME);
            assertTrue(hasSegments(index), "both segments must load after a restart");
            assertEquals(recallBefore, recall(index, vectors, ids), 1e-9, "the same answers after a restart");
        }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void load_leftoverFiles_areDeletedButOtherIndicesKept(@TempDir final Path tempDir) throws IOException
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Document>     map   = rootOf(storage);
            final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE));
            addRandom(map, new Random(9), 300);
            index.persistToDisk();
            storage.storeRoot();
        }
        final List<String> written = SegmentLayout.fileNames(indexDir);

        final List<String> leftovers = List.of(
            NAME + ".seg-7.graph", NAME + ".seg-7.l2g", NAME + ".seg-8.pqv.tmp", NAME + ".manifest.tmp"
        );
        // Files outside the layout stay, whoever they belong to: another index sharing the directory, or
        // the single-file layout of earlier versions, which persisted data need not survive.
        final List<String> foreign = List.of(
            NAME + ".seg-1.manifest", NAME + ".seg-1.seg-0.graph", "other.seg-0.graph", "other.graph",
            NAME + ".graph", NAME + ".meta", NAME + ".pqv"
        );
        for(final String file : leftovers)
        {
            Files.writeString(indexDir.resolve(file), "leftover");
        }
        for(final String file : foreign)
        {
            Files.writeString(indexDir.resolve(file), "another index's");
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex<Document> index = rootOf(storage).index().get(VectorIndices.Category()).get(NAME);
            assertEquals(10, index.search(randomVector(new Random(10)), 10).size());
            assertTrue(hasSegments(index));
        }

        final Set<String> expected = new HashSet<>(written);
        expected.addAll(foreign);
        assertEquals(expected, new HashSet<>(SegmentLayout.fileNames(indexDir)),
            "the leftovers of this index go, every file outside its layout stays");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void flush_segmentWithNothingLive_isDroppedFromTheManifest(@TempDir final Path tempDir)
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(14);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE)))
        {
            final List<Long> first = new ArrayList<>();
            for(int i = 0; i < 40; i++)
            {
                first.add(map.add(new Document("first", randomVector(random))));
            }
            index.persistToDisk();

            addRandom(map, random, 300);
            index.persistToDisk();
            assertEquals(2, SegmentLayout.graphFiles(indexDir, NAME).size());

            // Every entity of the first segment goes, and nothing new arrives: a manifest-only commit.
            first.forEach(map::removeById);
            index.persistToDisk();

            assertEquals(1, SegmentLayout.graphFiles(indexDir, NAME).size(),
                "a segment with nothing live must leave the manifest, and its files with it");
            assertEquals(1, segments(index).size());
            assertEachEntityLiveInOneSource(index, map.size());
            assertEquals(10, index.search(randomVector(random), 10).size());
        }
    }

    /**
     * An explicit persist whose write fails leaves the frozen graph for the next persist, and must make
     * one due: the changes it holds may never have been counted, as eventual-indexing operations the
     * freeze moved out of the queue before the worker applied them, and both the scheduled and the
     * shutdown persist run only for counted changes. The count is cleared here to stand for that.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void flush_explicitWriteFails_makesAPersistDue(@TempDir final Path tempDir) throws ReflectiveOperationException
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .eventualIndexing(true)
            .persistenceIntervalMs(600_000L)
            .build();

        try(final VectorIndex<Document> index = register(map, config))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            addRandom(map, new Random(16), 50);
            def.backgroundTaskManager.drainQueue();
            final Field countField = BackgroundTaskManager.class.getDeclaredField("persistenceChangeCount");
            countField.setAccessible(true);
            final AtomicInteger count = (AtomicInteger)countField.get(def.backgroundTaskManager);
            count.set(0);

            FlushHooks.onceAt(def, VectorIndex.Default.FlushStage.CLEANED, () ->
            {
                throw new IllegalStateException("simulated write failure");
            });
            assertThrows(RuntimeException.class, index::persistToDisk);

            assertEquals(1, def.sourceSet().frozen().size(), "the frozen graph must stay for the next persist");
            assertTrue(count.get() > 0, "the failed write must make a persist due");
        }
    }

    /**
     * A frozen graph left by a failed flush, whose entities are all removed before the next flush
     * writes it, gives a segment with nothing live: written, but listed by no manifest. Its files must
     * go with that flush.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void flush_retainedFrozenGraphWithNothingLive_leavesNoFiles(@TempDir final Path tempDir)
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(17);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE)))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            final List<Long>                    ids = new ArrayList<>();
            for(int i = 0; i < 30; i++)
            {
                ids.add(map.add(new Document("doc", randomVector(random))));
            }
            FlushHooks.onceAt(def, VectorIndex.Default.FlushStage.CLEANED, () ->
            {
                throw new IllegalStateException("simulated write failure");
            });
            assertThrows(RuntimeException.class, index::persistToDisk);
            assertEquals(1, def.sourceSet().frozen().size());

            ids.forEach(map::removeById);
            index.persistToDisk();

            assertTrue(def.sourceSet().frozen().isEmpty());
            assertTrue(SegmentLayout.graphFiles(indexDir, NAME).isEmpty(),
                "the files of a segment no manifest lists must not stay behind");
        }
    }

    /**
     * After removeAll, which replaces the disk manager, a segment of the cleared index that a search
     * still holds keeps its files through the next flush, which drops it, and loses them once the search
     * lets go of it.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void flush_afterRemoveAll_droppedSegmentHeldBySearch_filesGoOnceReleased(@TempDir final Path tempDir)
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(18);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE)))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            addRandom(map, random, 40);
            index.persistToDisk();
            assertEquals(1, SegmentLayout.graphFiles(indexDir, NAME).size());

            final SourceSet held = def.sourceSet();
            assertTrue(held.tryAcquire());
            map.removeAll();
            addRandom(map, random, 40);
            index.persistToDisk();
            assertEquals(2, SegmentLayout.graphFiles(indexDir, NAME).size(),
                "the files of a segment a search still holds must stay");

            held.release();
            assertEquals(1, SegmentLayout.graphFiles(indexDir, NAME).size(),
                "the files of the cleared index's segment must go with its last holder");
        }
    }

    /**
     * A search that holds the sources while a flush drops a segment keeps that segment open, so the
     * cleanup after the commit keeps its files. They go once the search lets go of it.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void flush_droppedSegmentHeldBySearch_filesGoOnceReleased(@TempDir final Path tempDir)
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(15);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.INLINE)))
        {
            final VectorIndex.Default<Document> def   = (VectorIndex.Default<Document>)index;
            final List<Long>                    first = new ArrayList<>();
            for(int i = 0; i < 40; i++)
            {
                first.add(map.add(new Document("first", randomVector(random))));
            }
            index.persistToDisk();
            addRandom(map, random, 300);
            index.persistToDisk();
            assertEquals(2, SegmentLayout.graphFiles(indexDir, NAME).size());

            // As a search does: hold the published sources across the flush that drops the segment.
            final SourceSet held = def.sourceSet();
            assertTrue(held.tryAcquire());
            first.forEach(map::removeById);
            index.persistToDisk();
            assertEquals(1, segments(index).size());
            assertEquals(2, SegmentLayout.graphFiles(indexDir, NAME).size(),
                "the files of a segment a search still holds must stay");

            held.release();
            assertEquals(1, SegmentLayout.graphFiles(indexDir, NAME).size(),
                "the files of a dropped segment must go with its last holder");
            assertEquals(10, index.search(randomVector(random), 10).size());
        }
    }

    /**
     * A segment written for another format still loads, flagged for merge: searched exactly over the
     * live vectors and contributing no quantizer until a background merge rewrites it, which starts once
     * the vectors are recovered on the first access, without a consolidation. The next persist commits
     * the rewrite.
     */
    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void load_segmentOfAnotherFormat_isSearchedExactlyAndRewrittenByAMerge(@TempDir final Path tempDir)
        throws IOException, InterruptedException
    {
        final Path           storageDir = tempDir.resolve("storage");
        final Path           indexDir   = tempDir.resolve("index");
        final Path           inlineDir  = tempDir.resolve("inline");
        final List<Document> documents  = randomDocuments(new Random(15), 400);

        // The same entities, written once as INLINE ...
        final GigaMap<Document> inlineMap = GigaMap.New();
        try(final VectorIndex<Document> index = register(inlineMap, configuration(inlineDir, Format.INLINE)))
        {
            documents.forEach(inlineMap::add);
            index.persistToDisk();
        }
        // ... and once, in a store, as NVQ. Same entities in the same order give the same witnesses, so
        // the INLINE files can stand in for the NVQ ones, as if the configuration had changed since.
        final List<Long> ids = new ArrayList<>(documents.size());
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Document>     map   = rootOf(storage);
            final VectorIndex<Document> index = register(map, configuration(indexDir, Format.NVQ));
            for(final Document document : documents)
            {
                ids.add(map.add(document));
            }
            index.persistToDisk();
            storage.storeRoot();
        }
        for(final String file : SegmentLayout.fileNames(indexDir))
        {
            Files.delete(indexDir.resolve(file));
        }
        for(final String file : SegmentLayout.fileNames(inlineDir))
        {
            Files.copy(inlineDir.resolve(file), indexDir.resolve(file));
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex<Document> index = rootOf(storage).index().get(VectorIndices.Category()).get(NAME);
            // Held back, so the flagged segment can be looked at before it is rewritten.
            ((VectorIndex.Default<Document>)index).mergesSuspended = true;
            assertTrue(hasSegments(index), "a segment of another format must load, not be rejected");
            assertTrue(anyFlagged(index));
            assertFalse(index.isNvqCompressionActive(), "a flagged segment contributes no quantizer");
            assertFalse(index.isPqCompressionActive());
            for(int i = 0; i < documents.size(); i += 40)
            {
                assertEquals(ids.get(i), topHit(index, documents.get(i).embedding()),
                    "the flagged segment must answer exactly, by its live vectors");
            }
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex<Document>         index = rootOf(storage).index().get(VectorIndices.Category()).get(NAME);
            final VectorIndex.Default<Document> def   = (VectorIndex.Default<Document>)index;
            assertTrue(anyFlagged(index), "nothing rewrote the flagged segment while merges were held back");

            assertEquals(ids.get(0), topHit(index, documents.get(0).embedding()), "the first access");
            awaitMerges(def, 1);
            assertEquals(0, def.consolidationCount(), "the merge rewrote it, not a rebuild under the locks");
            assertFalse(anyFlagged(index), "the merge must have rewritten it");
            assertEquals(1, segments(index).size());
            index.persistToDisk();
            assertTrue(index.isNvqCompressionActive(), "the rewritten segment carries the configured format");
            storage.storeRoot();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex<Document> index = rootOf(storage).index().get(VectorIndices.Category()).get(NAME);
            assertTrue(hasSegments(index));
            assertFalse(anyFlagged(index));
            assertTrue(index.isNvqCompressionActive());
            assertEquals(ids.get(0), topHit(index, documents.get(0).embedding()));
        }
    }

    /**
     * Segments encoded with different PQ codebooks cannot be scored against each other, so an explicit
     * persist that finds a segment of another codebook than the index's rewrites it with the current one,
     * by a merge on its own thread, and commits that.
     */
    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void persist_segmentOfAnotherCodebook_isRewrittenWithTheCurrentOne(@TempDir final Path tempDir)
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(16);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.FUSED_PQ)))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            final long first = map.add(new Document("first", basis(0)));
            addRandom(map, random, 400);
            index.persistToDisk();

            // A codebook trained on other vectors, as a retraining would produce.
            final PQCompressionManager pqManager = internalState(def, "pqManager");
            pqManager.reset();
            final List<VectorFloat<?>> trainingVectors = new ArrayList<>(400);
            for(int i = 0; i < 400; i++)
            {
                trainingVectors.add(VECTOR_TYPES.createFloatVector(randomVector(random)));
            }
            pqManager.trainFrom(trainingVectors);

            final long second = map.add(new Document("second", basis(1)));
            addRandom(map, random, 50);
            index.persistToDisk();

            assertEquals(0, def.consolidationCount(), "a merge rewrites it, not a rebuild under the locks");
            assertTrue(def.mergeCount() >= 1, "the segment of the old codebook must be rewritten");
            for(final DiskSegment segment : segments(index))
            {
                assertEquals(pqManager.getPQ(), DiskIndexManager.codebookOf(segment),
                    "every segment carries the current codebook");
            }
            assertEquals(segments(index).size(), SegmentLayout.graphFiles(indexDir, NAME).size(),
                "the rewrite is committed");
            assertEquals(first, topHit(index, basis(0)));
            assertEquals(second, topHit(index, basis(1)));
        }
    }

    /**
     * A segment written before the codebook could be trained carries no codes, so it could never be
     * traversed on them. Once a later persist trains the codebook, it rewrites that segment, and every
     * segment carries the codes from then on.
     */
    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void persist_segmentWrittenBeforeTheCodebook_isRewrittenOnceTrained(@TempDir final Path tempDir)
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(17);

        try(final VectorIndex<Document> index = register(map, configuration(indexDir, Format.FUSED_PQ)))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            final long first = map.add(new Document("first", basis(0)));
            addRandom(map, random, 100);
            index.persistToDisk();
            assertFalse(index.isPqCompressionActive(), "too few vectors to train a codebook");
            assertNull(DiskIndexManager.codebookOf(segments(index).get(0)));

            addRandom(map, random, 400);
            index.persistToDisk();

            assertTrue(index.isPqCompressionActive());
            assertEquals(0, def.consolidationCount());
            assertTrue(def.mergeCount() >= 1, "the segment without codes must be rewritten");
            for(final DiskSegment segment : segments(index))
            {
                assertTrue(segment.graph().getFeatureSet().contains(FeatureId.FUSED_PQ));
            }
            assertEquals(first, topHit(index, basis(0)));
        }
    }

    /**
     * The PQ codes are read by {@code PQVectors.load}, which allocates by the counts in the file. A
     * flipped bit there must be caught by the file's CRC before jVector reads it, rather than end in an
     * allocation that halts the JVM.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void load_corruptPqCodes_isRejectedBeforeJVectorReadsThem(@TempDir final Path tempDir) throws IOException
    {
        final Path              indexDir = tempDir.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();
        final VectorIndexConfiguration config = configuration(indexDir, Format.PQ_IN_MEMORY);

        try(final VectorIndex<Document> index = register(map, config))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            addRandom(map, new Random(13), 400);
            index.persistToDisk();
            final DiskIndexManager.MetaState witness = new DiskIndexManager.MetaState(
                def.getExpectedVectorCount(), def.getHighestEntityId(), def.getStructuralModCount());

            final Path   pqv   = SegmentLayout.onlyPqVectorsFile(indexDir, NAME);
            final byte[] bytes = Files.readAllBytes(pqv);
            // Into the leading counts jVector sizes its codebook allocation by.
            bytes[1] ^= 0x7F;
            Files.write(pqv, bytes);

            try(final SegmentLoad manager = SegmentLoad.of(
                def, NAME, indexDir, DIMENSION, GraphFormat.of(config), false))
            {
                assertFalse(manager.tryLoad(witness), "corrupt PQ codes must reject the load");
            }
        }
    }

    /**
     * Asserts the invariant the owners exist for: every entity is accepted by exactly one source - a
     * segment, a frozen in-memory graph or the live in-memory graph - and nothing else is. Checked on
     * the accept bits directly, because search results cannot show it: the exact reranker scores a stale
     * copy with the live vector, and the merge then drops the duplicate.
     */
    private static void awaitMerges(
        final VectorIndex.Default<Document> def  ,
        final long                          count
    )
        throws InterruptedException
    {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while(def.mergeCount() < count)
        {
            assertTrue(System.nanoTime() < deadline, "expected " + count + " merges, got " + def.mergeCount());
            Thread.sleep(10);
        }
        assertTrue(def.awaitMergesIdle(60_000L), "the merges must go idle");
    }

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
            final Set<Long> exact = new HashSet<>(IntStream.range(0, vectors.size())
                .boxed()
                .sorted(Comparator.comparingDouble(i -> -cosine(query, vectors.get(i))))
                .limit(k)
                .map(ids::get)
                .toList());
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
        final Path   indexDir,
        final Format format
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
            .build();
    }

    private static VectorIndex<Document> register(
        final GigaMap<Document>        map   ,
        final VectorIndexConfiguration config
    )
    {
        return map.index().register(VectorIndices.Category()).add(NAME, config, new DocumentVectorizer());
    }

    /**
     * Whether the index answers from segments it loaded or wrote, rather than from a graph rebuilt from
     * the store.
     */
    private static boolean hasSegments(final VectorIndex<Document> index)
    {
        final SourceSet set = ((VectorIndex.Default<Document>)index).sourceSet();
        return set != null && !set.segments().isEmpty();
    }

    private static List<DiskSegment> segments(final VectorIndex<Document> index)
    {
        return ((VectorIndex.Default<Document>)index).sourceSet().segments();
    }

    private static boolean anyFlagged(final VectorIndex<Document> index)
    {
        return segments(index).stream().anyMatch(DiskSegment::flaggedForMerge);
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

    private static List<Document> randomDocuments(
        final Random random,
        final int    count
    )
    {
        final List<Document> documents = new ArrayList<>(count);
        for(int i = 0; i < count; i++)
        {
            documents.add(new Document("doc", randomVector(random)));
        }
        return documents;
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
