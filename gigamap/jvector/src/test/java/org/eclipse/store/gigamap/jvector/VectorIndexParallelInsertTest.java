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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for the parallel graph inserts of a batch: {@link GigaMap#addAll} in synchronous and eventual
 * mode inserts the batch on the index's private insert pool, with the same search quality as
 * inserting the vectors one after another.
 */
class VectorIndexParallelInsertTest
{
    record Document(String content, float[] embedding) {}

    static class ComputedDocumentVectorizer extends Vectorizer<Document>
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            return entity.embedding();
        }
    }

    private static final int DIMENSION  = 32 ;
    private static final int BATCH_SIZE = 2000;
    private static final int TOP_K      = 10 ;

    @AfterEach
    void resetParallelism()
    {
        ParallelGraphInsertion.parallelismLimitForTest = 0;
    }

    private static float[] randomVector(final Random random, final int dimension)
    {
        final float[] vector = new float[dimension];
        float norm = 0;
        for(int i = 0; i < dimension; i++)
        {
            vector[i] = random.nextFloat() * 2 - 1;
            norm += vector[i] * vector[i];
        }
        norm = (float)Math.sqrt(norm);
        for(int i = 0; i < dimension; i++)
        {
            vector[i] /= norm;
        }
        return vector;
    }

    private static List<Document> documents(final long seed, final int count, final String prefix)
    {
        final Random         random    = new Random(seed);
        final List<Document> documents = new ArrayList<>(count);
        for(int i = 0; i < count; i++)
        {
            documents.add(new Document(prefix + i, randomVector(random, DIMENSION)));
        }
        return documents;
    }

    private static VectorIndexConfiguration.Builder configuration()
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.COSINE);
    }

    private static float dot(final float[] left, final float[] right)
    {
        float sum = 0;
        for(int i = 0; i < left.length; i++)
        {
            sum += left[i] * right[i];
        }
        return sum;
    }

    /**
     * Average recall@{@link #TOP_K} of {@code index} against a brute force search over
     * {@code documents}.
     */
    private static double recall(
        final VectorIndex<Document> index    ,
        final List<Document>        documents,
        final List<float[]>         queries
    )
    {
        int found = 0;
        for(final float[] query : queries)
        {
            final Set<String> expected = documents.stream()
                .sorted(Comparator.comparingDouble((Document document) -> -dot(document.embedding(), query)))
                .limit(TOP_K)
                .map(Document::content)
                .collect(Collectors.toSet());
            final Set<String> actual = new HashSet<>();
            index.search(query, TOP_K).forEach(entry -> actual.add(entry.entity().content()));
            actual.retainAll(expected);
            found += actual.size();
        }
        return (double)found / (queries.size() * TOP_K);
    }

    private static double buildAndMeasureRecall(final List<Document> documents, final List<float[]> queries)
    {
        final GigaMap<Document>       gigaMap = GigaMap.New();
        final VectorIndices<Document> indices = gigaMap.index().register(VectorIndices.Category());
        try(final VectorIndex<Document> index = indices.add(
            "embeddings", configuration().build(), new ComputedDocumentVectorizer()
        ))
        {
            gigaMap.addAll(documents);
            assertEquals(documents.size(), gigaMap.size());
            return recall(index, documents, queries);
        }
    }

    private static Set<Thread> insertWorkers(final String indexName)
    {
        final String prefix = "vector-insert-" + indexName + "-";
        return Thread.getAllStackTraces().keySet().stream()
            .filter(thread -> thread.getName().startsWith(prefix))
            .filter(Thread::isAlive)
            .collect(Collectors.toSet());
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void addAll_parallelBatch_recallMatchesSequentialInserts()
    {
        final List<Document> documents = documents(42L, BATCH_SIZE, "doc_");
        final Random         random    = new Random(7L);
        final List<float[]>  queries   = IntStream.range(0, 50)
            .mapToObj(i -> randomVector(random, DIMENSION))
            .toList();

        // The cap is read when the index creates its pool, so it has to be set before the index.
        ParallelGraphInsertion.parallelismLimitForTest = 1;
        final double sequential = buildAndMeasureRecall(documents, queries);
        ParallelGraphInsertion.parallelismLimitForTest = 0;
        final double parallel = buildAndMeasureRecall(documents, queries);

        assertTrue(parallel >= 0.9, "parallel recall too low: " + parallel);
        assertTrue(parallel >= sequential - 0.03,
            "parallel recall " + parallel + " must match sequential recall " + sequential);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void addAll_largeBatch_insertsOnPrivatePoolAndReusesItsThreads()
    {
        assumeTrue(ParallelGraphInsertion.parallelism() >= 2, "needs at least two cores");

        final String                  name    = "pooled";
        final GigaMap<Document>       gigaMap = GigaMap.New();
        final VectorIndices<Document> indices = gigaMap.index().register(VectorIndices.Category());
        try(final VectorIndex<Document> index = indices.add(
            name, configuration().build(), new ComputedDocumentVectorizer()
        ))
        {
            gigaMap.addAll(documents(1L, 1000, "a_"));
            final Set<Thread> first = insertWorkers(name);
            assertFalse(first.isEmpty(), "a batch above the parallel threshold must use the insert pool");
            assertTrue(first.size() <= ParallelGraphInsertion.parallelism());

            gigaMap.addAll(documents(2L, 1000, "b_"));
            final Set<Thread> second = insertWorkers(name);
            assertTrue(second.size() <= ParallelGraphInsertion.parallelism(),
                "the pool must not grow past its parallelism: " + second.size());
            assertTrue(second.containsAll(first), "the second batch must reuse the first batch's workers");

            assertEquals(2000, gigaMap.size());
            assertEquals(TOP_K, index.search(documents(1L, 1, "a_").get(0).embedding(), TOP_K).size());
        }
        assertTrue(awaitNoWorkers(name), "close() must shut the insert pool down");
    }

    private static boolean awaitNoWorkers(final String indexName)
    {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime() < deadline)
        {
            if(insertWorkers(indexName).isEmpty())
            {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        return false;
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void addAll_eventualMode_allEntitiesSearchableAfterDrain()
    {
        final List<Document>          documents = documents(3L, BATCH_SIZE, "doc_");
        final GigaMap<Document>       gigaMap   = GigaMap.New();
        final VectorIndices<Document> indices   = gigaMap.index().register(VectorIndices.Category());
        try(final VectorIndex<Document> index = indices.add(
            "embeddings", configuration().eventualIndexing(true).build(), new ComputedDocumentVectorizer()
        ))
        {
            gigaMap.addAll(documents);
            ((VectorIndex.Default<Document>)index).backgroundTaskManager.drainQueue();

            for(int i = 0; i < documents.size(); i += 97)
            {
                final Document document = documents.get(i);
                assertEquals(document.content(),
                    index.search(document.embedding(), 1).toList().get(0).entity().content(),
                    "every entity must be indexed once the queue is drained");
            }
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void addAll_repeatedBatchesWithInterleavedRemovals_keepIndexConsistent()
    {
        final GigaMap<Document>       gigaMap = GigaMap.New();
        final VectorIndices<Document> indices = gigaMap.index().register(VectorIndices.Category());
        try(final VectorIndex<Document> index = indices.add(
            "embeddings", configuration().build(), new ComputedDocumentVectorizer()
        ))
        {
            for(int round = 0; round < 4; round++)
            {
                gigaMap.addAll(documents(100L + round, 500, "r" + round + "_"));
                // Removals purge deleted nodes under the exclusive structure lock between batches.
                for(long id = round * 500L; id < round * 500L + 250; id++)
                {
                    gigaMap.removeById(id);
                }
            }
            assertEquals(1000, gigaMap.size());

            final List<Document> remaining = new ArrayList<>();
            gigaMap.iterator().forEachRemaining(remaining::add);
            for(int i = 0; i < remaining.size(); i += 53)
            {
                final Document document = remaining.get(i);
                assertEquals(document.content(),
                    index.search(document.embedding(), 1).toList().get(0).entity().content());
            }
        }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void addAll_withActivePqCodes_encodesEveryInsertedNode(@TempDir final Path indexDirectory)
    {
        final GigaMap<Document>       gigaMap = GigaMap.New();
        final VectorIndices<Document> indices = gigaMap.index().register(VectorIndices.Category());
        final VectorIndexConfiguration config = configuration()
            .onDisk(true)
            .indexDirectory(indexDirectory)
            .enablePqCompression(true)
            .pqSubspaces(8)
            .build();
        try(final VectorIndex<Document> index = indices.add("embeddings", config, new ComputedDocumentVectorizer()))
        {
            gigaMap.addAll(documents(5L, 1000, "base_"));
            index.persistToDisk();
            assumeTrue(index.isPqCompressionActive(), "the persist must have trained a codebook");

            // Inserted in parallel against the PQ code store, which must be grown before the workers run.
            final List<Document> added = documents(6L, 1000, "added_");
            gigaMap.addAll(added);
            assertEquals(2000, gigaMap.size());

            int hits = 0;
            for(final Document document : added)
            {
                final boolean found = index.search(document.embedding(), TOP_K).toList().stream()
                    .anyMatch(entry -> entry.entity().content().equals(document.content()));
                hits += found ? 1 : 0;
            }
            assertTrue(hits >= added.size() * 0.95, "added nodes must be reachable, found " + hits);
        }
    }
}
