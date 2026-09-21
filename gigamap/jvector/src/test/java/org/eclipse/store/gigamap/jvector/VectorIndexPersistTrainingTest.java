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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that what a persist trains, that same persist writes.
 */
@Tag("slow")
class VectorIndexPersistTrainingTest
{
    static final int DIM   = 64;
    static final int COUNT = 300;

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

    static final class Vz extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.embedding;
        }
    }

    private static float[] unit(final Random random)
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

    private static VectorIndexConfiguration fusedPqOnDisk(final Path indexDir)
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .onDisk(true)
            .indexDirectory(indexDir)
            .approximateScoring(ApproximateScoring.FUSED_PQ)
            .pqSubspaces(DIM / 4)
            .build();
    }

    /**
     * A persist that trains a codebook must also write it.
     * <p>
     * The decision to skip a persist is made on the same "is training pending" predicates that
     * training has just turned false, so a persist that trains and is otherwise idle used to skip
     * the write that was the entire reason it trained. The codebook then existed only in heap, and
     * every later persist skipped for the same reason - the index stayed uncompressed on disk for
     * good, while reporting compression as configured.
     * <p>
     * The route there is the shutdown persist, which deliberately does not train: shutdown must not
     * wait for k-means. So an index closed without an earlier persist leaves an uncompressed graph
     * behind, and comes back incremental, clean, and with training due - which is exactly the state
     * the skip applies to.
     * <p>
     * Measured on the graph file, because that is where fused codes go and it is the artifact the
     * next process actually loads. A heap-only codebook does not make it bigger.
     *
     * @param dir the temporary storage directory
     */
    @Test
    @Timeout(value = 600, unit = TimeUnit.SECONDS)
    void aPersistThatTrainsAlsoWrites(@TempDir final Path dir) throws Exception
    {
        final Path storageDir = dir.resolve("storage");
        final Path indexDir   = dir.resolve("index");
        final Path graphFile  = indexDir.resolve("embeddings.graph");

        final Random random = new Random(4711);

        // Session one: fill and close. The shutdown persist writes the graph without training.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            map.index().register(VectorIndices.Category())
                .add("embeddings", fusedPqOnDisk(indexDir), new Vz());
            for(int i = 0; i < COUNT; i++)
            {
                map.add(new Doc("d" + i, unit(random)));
            }
            storage.storeRoot();
        }

        assertTrue(Files.exists(graphFile), "the shutdown persist must have written a graph");
        final long uncompressedSize = Files.size(graphFile);

        // Session two: load and persist once. Nothing has changed, so this persist exists only to
        // do the training the shutdown one skipped - and to write what it trains.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index()
                .get(VectorIndices.Category())
                .get("embeddings")
            ;
            index.persistToDisk();
        }

        final long afterTrainingSize = Files.size(graphFile);

        // Fused codes are pqSubspaces * maxDegree bytes per node on top of the inline vectors, so
        // the difference is not subtle. Anything close to unchanged means the codebook was trained
        // and then left in heap.
        assertTrue(afterTrainingSize > uncompressedSize,
            "the graph did not grow (" + uncompressedSize + " -> " + afterTrainingSize + " bytes),"
                + " so the persist trained a codebook and then skipped the write that had to"
                + " persist it");
    }
}
