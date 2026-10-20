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
import io.github.jbellis.jvector.util.Bits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Crash points of the manifest commit in {@link DiskIndexManager#commit}: a flush that dies at any
 * {@link DiskIndexManager.CommitStage} must leave a directory that loads as the old index before the
 * manifest is moved and as the new one from then on, and the load must clean up whatever the dead
 * flush left behind. Also the load's handling of damaged and stale manifests, and sequence numbers.
 */
class DiskIndexManagerCommitCrashTest
{
    private static final String NAME      = "embeddings";
    private static final int    DIMENSION = 16;
    private static final int    FIRST     = 300;
    private static final int    SECOND    = 50;

    record Document(String content, float[] embedding) {}

    static final class DocumentVectorizer extends Vectorizer<Document>
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            return entity.embedding();
        }
    }

    /** Thrown by the commit hook to stop a persist at a chosen stage, as a crash would. */
    static final class SimulatedCrash extends RuntimeException
    {
        SimulatedCrash(final DiskIndexManager.CommitStage stage)
        {
            super("simulated crash at " + stage);
        }
    }

    @TempDir
    Path directory;

    /**
     * The first flush of an index, killed at each stage. The hook copies the directory at the stage
     * before it throws, so the copy is what a real crash leaves: the exception afterwards still runs the
     * flush's cleanup on the original, which a crash would not. Before the move nothing is committed, so
     * the copy loads as no index at all and the load deletes what the dead flush wrote; from the move on
     * the new generation loads.
     */
    @ParameterizedTest
    @EnumSource(DiskIndexManager.CommitStage.class)
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void firstFlush_crashAtStage_leavesTheNewGenerationOrNone(final DiskIndexManager.CommitStage stage)
        throws IOException
    {
        final Path              indexDir = this.directory.resolve("index");
        final Path              crashed  = this.directory.resolve("crashed");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(stage.ordinal());

        try(final VectorIndex<Document> index = map.index().register(VectorIndices.Category())
            .add(NAME, configuration(indexDir), new DocumentVectorizer()))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            addDocuments(map, random, FIRST);
            final DiskIndexManager.MetaState generation = witnesses(def);
            def.commitStageTestHook = crashAt(stage, indexDir, crashed);
            assertCrashed(index::persistToDisk, stage);
            def.commitStageTestHook = null;

            final boolean committed = stage.compareTo(DiskIndexManager.CommitStage.MANIFEST_MOVED) >= 0;
            try(final SegmentLoad manager = SegmentLoad.of(
                def, NAME, crashed, DIMENSION, GraphFormat.of(configuration(crashed)), false))
            {
                assertEquals(committed, manager.tryLoad(generation),
                    "after a crash at " + stage + " the generation must " + (committed ? "" : "not ") + "load");
                if(committed)
                {
                    assertEquals(1, manager.segments().size());
                    assertEquals(FIRST, manager.segments().get(0).nodeCount());
                }
            }
            assertEquals(
                committed ? List.of(NAME + ".manifest", NAME + ".seg-0.graph", NAME + ".seg-0.l2g") : List.of(),
                SegmentLayout.fileNames(crashed),
                "the load must keep exactly what the manifest lists"
            );
        }
    }

    /**
     * A flush onto an index that has a segment, killed at each stage: it leaves either generation A, or
     * A's segment with the deletion of the removed entity plus the new segment.
     */
    @ParameterizedTest
    @EnumSource(DiskIndexManager.CommitStage.class)
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void flush_crashAtStage_leavesALoadableGeneration(final DiskIndexManager.CommitStage stage)
        throws IOException
    {
        final Path              indexDir = this.directory.resolve("index");
        final Path              crashed  = this.directory.resolve("crashed");
        final GigaMap<Document> map      = GigaMap.New();
        final Random            random   = new Random(7 + stage.ordinal());

        try(final VectorIndex<Document> index = map.index().register(VectorIndices.Category())
            .add(NAME, configuration(indexDir), new DocumentVectorizer()))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            final List<Long> ids = addDocuments(map, random, FIRST);
            index.persistToDisk();
            final DiskIndexManager.MetaState generationA = witnesses(def);
            final List<String> filesOfA = SegmentLayout.fileNames(indexDir);

            addDocuments(map, random, SECOND);
            // A removal from the first segment, so a committed flush also changes its deletion set.
            map.removeById(ids.get(0));
            final DiskIndexManager.MetaState generationB = witnesses(def);
            def.commitStageTestHook = crashAt(stage, indexDir, crashed);
            assertCrashed(index::persistToDisk, stage);
            def.commitStageTestHook = null;

            final boolean committed = stage.compareTo(DiskIndexManager.CommitStage.MANIFEST_MOVED) >= 0;
            try(final SegmentLoad manager = SegmentLoad.of(
                def, NAME, crashed, DIMENSION, GraphFormat.of(configuration(crashed)), false))
            {
                if(committed)
                {
                    assertTrue(manager.tryLoad(generationB), "a committed flush must load after a crash at " + stage);
                    assertEquals(2, manager.segments().size());
                    final DiskSegment first    = manager.segments().get(0);
                    final Bits        accepted = first.acceptBits(manager.owners());
                    assertEquals(FIRST - 1, IntStream.range(0, first.nodeCount()).filter(accepted::get).count(),
                        "the removal must be in the first segment's deletion set");
                    assertEquals(SECOND, manager.segments().get(1).nodeCount());
                    assertFalse(manager.tryLoad(generationA), "the old generation is not what the manifest describes");
                }
                else
                {
                    assertTrue(manager.tryLoad(generationA), "the generation before the flush must load");
                    assertEquals(1, manager.segments().size());
                    assertEquals(FIRST, manager.segments().get(0).nodeCount());
                    assertFalse(manager.tryLoad(generationB), "the new generation was never committed");
                }
            }
            if(!committed)
            {
                assertEquals(filesOfA, SegmentLayout.fileNames(crashed),
                    "the load must delete the segment the dead flush wrote");
            }
        }
    }

    /**
     * Asserts that {@code persist} failed because of the simulated crash at {@code stage}: thrown as is
     * before the manifest moved, and wrapped from then on, where the commit took effect and is
     * reported as incomplete.
     */
    private static void assertCrashed(
        final Runnable                     persist,
        final DiskIndexManager.CommitStage stage
    )
    {
        final RuntimeException failure = assertThrows(RuntimeException.class, persist::run);
        Throwable cause = failure;
        while(cause != null && !(cause instanceof SimulatedCrash))
        {
            cause = cause.getCause();
        }
        assertTrue(cause != null, () -> "the persist must fail with the crash at " + stage + ": " + failure);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void flush_leftoverSegmentFiles_newSegmentTakesTheNextSequence() throws IOException
    {
        final Path              indexDir = this.directory.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();

        try(final VectorIndex<Document> index = map.index().register(VectorIndices.Category())
            .add(NAME, configuration(indexDir), new DocumentVectorizer()))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            Files.createDirectories(indexDir);
            Files.writeString(indexDir.resolve(NAME + ".seg-5.graph"), "left by a crash");
            Files.writeString(indexDir.resolve(NAME + ".seg-5.l2g"), "left by a crash");

            addDocuments(map, new Random(10), FIRST);
            index.persistToDisk();

            assertEquals(List.of(NAME + ".manifest", NAME + ".seg-6.graph", NAME + ".seg-6.l2g"),
                SegmentLayout.fileNames(indexDir),
                "the new segment must not reuse a leftover's sequence, and the commit drops the leftovers");
            try(final SegmentLoad manager = SegmentLoad.of(
                def, NAME, indexDir, DIMENSION, GraphFormat.of(configuration(indexDir)), false))
            {
                assertTrue(manager.tryLoad(witnesses(def)));
                assertEquals(6, manager.segments().get(0).sequence());
            }
        }
    }

    /**
     * Sequences only grow within a run, across {@code removeAll()} as well: a flush still writing for
     * the generation {@code removeAll()} discarded deletes its own files, which must never be the files
     * of the new generation under the same name.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void flush_afterRemoveAll_takesASequenceAboveEveryEarlierOne()
    {
        final Path              indexDir = this.directory.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();

        try(final VectorIndex<Document> index = map.index().register(VectorIndices.Category())
            .add(NAME, configuration(indexDir), new DocumentVectorizer()))
        {
            addDocuments(map, new Random(11), FIRST);
            index.persistToDisk();
            map.removeAll();
            index.persistToDisk();
            assertEquals(List.of(), SegmentLayout.fileNames(indexDir), "an emptied index keeps no files");

            addDocuments(map, new Random(12), SECOND);
            index.persistToDisk();
            assertEquals(List.of(NAME + ".manifest", NAME + ".seg-1.graph", NAME + ".seg-1.l2g"),
                SegmentLayout.fileNames(indexDir), "the sequence of the discarded generation must not come back");
        }
    }

    /**
     * Returns a commit hook that, at {@code stage}, copies {@code indexDir} to {@code snapshot} and then
     * throws, so the copy holds exactly what a crash at that point leaves.
     */
    private static Consumer<DiskIndexManager.CommitStage> crashAt(
        final DiskIndexManager.CommitStage stage   ,
        final Path                         indexDir,
        final Path                         snapshot
    )
    {
        return reached ->
        {
            if(reached == stage)
            {
                try
                {
                    Files.createDirectories(snapshot);
                    for(final String name : SegmentLayout.fileNames(indexDir))
                    {
                        Files.copy(indexDir.resolve(name), snapshot.resolve(name));
                    }
                }
                catch(final IOException e)
                {
                    throw new UncheckedIOException(e);
                }
                throw new SimulatedCrash(stage);
            }
        };
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void tryLoad_staleWitness_keepsTheListedFilesAndDeletesTheRest() throws IOException
    {
        final Path              indexDir = this.directory.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();

        try(final VectorIndex<Document> index = map.index().register(VectorIndices.Category())
            .add(NAME, configuration(indexDir), new DocumentVectorizer()))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            addDocuments(map, new Random(5), FIRST);
            index.persistToDisk();
            final List<String> written = SegmentLayout.fileNames(indexDir);

            Files.writeString(indexDir.resolve(NAME + ".seg-41.graph"), "left by a crash");
            Files.writeString(indexDir.resolve(NAME + ".manifest.tmp"), "left by a crash");

            try(final SegmentLoad manager = SegmentLoad.of(
                def, NAME, indexDir, DIMENSION, GraphFormat.of(configuration(indexDir)), false))
            {
                assertFalse(manager.tryLoad(new DiskIndexManager.MetaState(1, 2, 3)),
                    "a manifest of another store state must be rejected");
            }

            assertEquals(written, SegmentLayout.fileNames(indexDir),
                "a stale index keeps its files until a persist replaces them; only leftovers go");
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void tryLoad_corruptManifest_deletesTheSegmentsButKeepsTheManifest() throws IOException
    {
        final Path                          indexDir = this.directory.resolve("index");
        final GigaMap<Document>             map      = GigaMap.New();
        final VectorIndex.Default<Document> def;
        final DiskIndexManager.MetaState    generation;

        try(final VectorIndex<Document> index = map.index().register(VectorIndices.Category())
            .add(NAME, configuration(indexDir), new DocumentVectorizer()))
        {
            def = (VectorIndex.Default<Document>)index;
            addDocuments(map, new Random(6), FIRST);
            index.persistToDisk();
            generation = witnesses(def);

            final Path   manifest = SegmentLayout.manifest(indexDir, NAME);
            final byte[] bytes    = Files.readAllBytes(manifest);
            bytes[bytes.length / 2] ^= 0x40;
            Files.write(manifest, bytes);
        }

        // The index is closed first, as after a restart: while open it keeps its segment mapped, and
        // Windows cannot delete a mapped file, so the cleanup under test would be blocked by the test.
        try(final SegmentLoad manager = SegmentLoad.of(
            def, NAME, indexDir, DIMENSION, GraphFormat.of(configuration(indexDir)), false))
        {
            assertTrue(manager.indexFilesExist(), "a damaged index still counts as present, so it gets rewritten");
            assertFalse(manager.tryLoad(generation), "a corrupt manifest must be rejected");
        }

        assertEquals(List.of(NAME + ".manifest"), SegmentLayout.fileNames(indexDir),
            "nothing a corrupt manifest names can be trusted");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void tryLoad_manifestUnreadable_keepsTheSegments() throws IOException
    {
        final Path              indexDir = this.directory.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();

        try(final VectorIndex<Document> index = map.index().register(VectorIndices.Category())
            .add(NAME, configuration(indexDir), new DocumentVectorizer()))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            addDocuments(map, new Random(8), FIRST);
            index.persistToDisk();
            final List<String> written = SegmentLayout.fileNames(indexDir);

            // A manifest that exists but cannot be read, as a sharing violation would make it: a
            // directory in its place fails the read with a plain I/O error, on every platform.
            final Path manifest = SegmentLayout.manifest(indexDir, NAME);
            Files.delete(manifest);
            Files.createDirectory(manifest);

            try(final SegmentLoad manager = SegmentLoad.of(
                def, NAME, indexDir, DIMENSION, GraphFormat.of(configuration(indexDir)), false))
            {
                assertTrue(manager.indexFilesExist());
                assertFalse(manager.tryLoad(witnesses(def)), "an unreadable manifest cannot be loaded");
            }
            assertEquals(written, SegmentLayout.fileNames(indexDir),
                "a manifest that may be valid keeps the segments it names");
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void deleteIndexFiles_manifestCannotBeRemoved_keepsTheSegments() throws IOException
    {
        final Path              indexDir = this.directory.resolve("index");
        final GigaMap<Document> map      = GigaMap.New();

        try(final VectorIndex<Document> index = map.index().register(VectorIndices.Category())
            .add(NAME, configuration(indexDir), new DocumentVectorizer()))
        {
            final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
            addDocuments(map, new Random(9), FIRST);
            index.persistToDisk();
            final List<String> written = SegmentLayout.fileNames(indexDir);

            // A non-empty directory in the manifest's place cannot be deleted.
            final Path manifest = SegmentLayout.manifest(indexDir, NAME);
            Files.delete(manifest);
            Files.createDirectory(manifest);
            Files.writeString(manifest.resolve("pinned"), "keeps the directory non-empty");

            try(final SegmentLoad manager = SegmentLoad.of(
                def, NAME, indexDir, DIMENSION, GraphFormat.of(configuration(indexDir)), false))
            {
                manager.deleteIndexFiles();
            }
            assertEquals(written, SegmentLayout.fileNames(indexDir),
                "the segments must outlive a manifest that could not be removed");
        }
    }

    private static VectorIndexConfiguration configuration(final Path indexDir)
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            // Off, so closing the index after a simulated crash does not repair the directory first.
            .persistOnShutdown(false)
            .build();
    }

    private static DiskIndexManager.MetaState witnesses(final VectorIndex.Default<Document> index)
    {
        return new DiskIndexManager.MetaState(
            index.getExpectedVectorCount(),
            index.getHighestEntityId(),
            index.getStructuralModCount()
        );
    }

    private static List<Long> addDocuments(
        final GigaMap<Document> map   ,
        final Random            random,
        final int               count
    )
    {
        final List<Long> ids = new ArrayList<>(count);
        for(int i = 0; i < count; i++)
        {
            final float[] vector = new float[DIMENSION];
            for(int d = 0; d < DIMENSION; d++)
            {
                vector[d] = random.nextFloat() * 2 - 1;
            }
            ids.add(map.add(new Document("doc", vector)));
        }
        return ids;
    }

}
