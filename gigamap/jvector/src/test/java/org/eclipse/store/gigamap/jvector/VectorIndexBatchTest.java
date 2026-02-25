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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for batch vectorization via {@link Vectorizer#vectorizeAll} and {@link GigaMap#addAll}.
 */
class VectorIndexBatchTest
{
    static final class Document
    {
        private final String  content;
        private final float[] embedding;

        Document(final String content, final float[] embedding)
        {
            this.content   = content;
            this.embedding = embedding;
        }

        public String content()
        {
            return this.content;
        }

        public float[] embedding()
        {
            return this.embedding;
        }

        @Override
        public boolean equals(final Object obj)
        {
            if(obj == this)
                return true;
            if(obj == null || obj.getClass() != this.getClass())
                return false;
            final var that = (Document)obj;
            return Objects.equals(this.content, that.content) &&
                Arrays.equals(this.embedding, that.embedding);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.content, this.embedding);
        }
    }

    static class EmbeddedDocumentVectorizer extends Vectorizer<Document>
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            return entity.embedding();
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

    static class ComputedDocumentVectorizer extends Vectorizer<Document>
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            return entity.embedding();
        }
    }

    /**
     * Vectorizer that tracks whether vectorizeAll was called.
     */
    static class BatchTrackingVectorizer extends Vectorizer<Document>
    {
        int vectorizeCallCount;
        int vectorizeAllCallCount;

        @Override
        public float[] vectorize(final Document entity)
        {
            this.vectorizeCallCount++;
            return entity.embedding();
        }

        @Override
        public List<float[]> vectorizeAll(final List<? extends Document> entities)
        {
            this.vectorizeAllCallCount++;
            return super.vectorizeAll(entities);
        }
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

    @Test
    void testAddAllUsesBatchVectorization()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final BatchTrackingVectorizer vectorizer = new BatchTrackingVectorizer();

        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        vectorIndices.add("embeddings", config, vectorizer);

        final List<Document> docs = List.of(
            new Document("Doc A", new float[]{1.0f, 0.0f, 0.0f}),
            new Document("Doc B", new float[]{0.0f, 1.0f, 0.0f}),
            new Document("Doc C", new float[]{0.0f, 0.0f, 1.0f})
        );

        gigaMap.addAll(docs);

        assertEquals(3, gigaMap.size());
        assertEquals(1, vectorizer.vectorizeAllCallCount, "vectorizeAll should be called once for the batch");
    }

    @Test
    void testAddAllSearchResultsMatchIndividualAdds()
    {
        final int dimension = 3;

        // Setup with individual adds
        final GigaMap<Document> gigaMapIndividual = GigaMap.New();
        final VectorIndices<Document> indicesIndividual = gigaMapIndividual.index().register(VectorIndices.Category());
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();
        final VectorIndex<Document> indexIndividual = indicesIndividual.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        );

        final Document doc1 = new Document("Hello world", new float[]{1.0f, 0.0f, 0.0f});
        final Document doc2 = new Document("Hello there", new float[]{0.9f, 0.1f, 0.0f});
        final Document doc3 = new Document("Goodbye world", new float[]{0.0f, 1.0f, 0.0f});

        gigaMapIndividual.add(doc1);
        gigaMapIndividual.add(doc2);
        gigaMapIndividual.add(doc3);

        // Setup with addAll
        final GigaMap<Document> gigaMapBatch = GigaMap.New();
        final VectorIndices<Document> indicesBatch = gigaMapBatch.index().register(VectorIndices.Category());
        final VectorIndex<Document> indexBatch = indicesBatch.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        );

        gigaMapBatch.addAll(List.of(doc1, doc2, doc3));

        // Search both and compare
        final float[] query = new float[]{1.0f, 0.0f, 0.0f};
        final List<VectorSearchResult.Entry<Document>> resultsIndividual = indexIndividual.search(query, 3).toList();
        final List<VectorSearchResult.Entry<Document>> resultsBatch = indexBatch.search(query, 3).toList();

        assertEquals(resultsIndividual.size(), resultsBatch.size());
        for(int i = 0; i < resultsIndividual.size(); i++)
        {
            assertEquals(
                resultsIndividual.get(i).entity().content(),
                resultsBatch.get(i).entity().content(),
                "Search results should match at index " + i
            );
            assertEquals(
                resultsIndividual.get(i).score(),
                resultsBatch.get(i).score(),
                0.001f,
                "Scores should match at index " + i
            );
        }
    }

    @Test
    void testAddAllLargeBatchWithComputedVectorizer()
    {
        final int dimension = 16;
        final int batchSize = 200;
        final Random random = new Random(42);

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        );

        final List<Document> batch = new ArrayList<>();
        for(int i = 0; i < batchSize; i++)
        {
            batch.add(new Document("doc_" + i, randomVector(random, dimension)));
        }

        gigaMap.addAll(batch);

        assertEquals(batchSize, gigaMap.size());

        final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
        assertEquals(10, result.size());
    }

    @Test
    void testCustomBatchVectorizer()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();

        final Vectorizer<Document> customBatchVectorizer = new Vectorizer<>()
        {
            @Override
            public float[] vectorize(final Document entity)
            {
                return entity.embedding();
            }

            @Override
            public List<float[]> vectorizeAll(final List<? extends Document> entities)
            {
                // Custom batch: return reversed vectors to prove this path is used
                return entities.stream()
                    .map(e ->
                    {
                        final float[] v = e.embedding().clone();
                        for(int i = 0; i < v.length / 2; i++)
                        {
                            final float tmp = v[i];
                            v[i] = v[v.length - 1 - i];
                            v[v.length - 1 - i] = tmp;
                        }
                        return v;
                    })
                    .toList();
            }
        };

        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        final VectorIndex<Document> index = vectorIndices.add("embeddings", config, customBatchVectorizer);

        gigaMap.addAll(List.of(
            new Document("A", new float[]{1.0f, 0.0f, 0.0f}),
            new Document("B", new float[]{0.0f, 0.0f, 1.0f})
        ));

        // Search with reversed query: {0,0,1} should match "A" because its vector was reversed to {0,0,1}
        final VectorSearchResult<Document> result = index.search(new float[]{0.0f, 0.0f, 1.0f}, 1);
        assertEquals(1, result.size());
        assertEquals("A", result.toList().get(0).entity().content(),
            "Custom batch vectorizer should have reversed the vectors");
    }

    @Test
    void testAddAllPersistence(@TempDir final Path tempDir)
    {
        final int dimension = 8;

        // Phase 1: Add via addAll and persist
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                final GigaMap<Document> gigaMap = GigaMap.New();
                storage.setRoot(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
                final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                    .dimension(dimension)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .build();

                vectorIndices.add("embeddings", config, new ComputedDocumentVectorizer());

                final float[] needleVector = new float[dimension];
                needleVector[0] = 1.0f;

                final List<Document> docs = new ArrayList<>();
                final Random random = new Random(42);
                for(int i = 0; i < 20; i++)
                {
                    docs.add(new Document("doc_" + i, randomVector(random, dimension)));
                }
                docs.add(new Document("needle", needleVector));

                gigaMap.addAll(docs);
                assertEquals(21, gigaMap.size());

                storage.storeRoot();
            }
        }

        // Phase 2: Restart and verify
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                assertNotNull(gigaMap);
                assertEquals(21, gigaMap.size());

                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());
                final VectorIndex<Document> index = vectorIndices.get("embeddings");

                final float[] query = new float[dimension];
                query[0] = 1.0f;

                final VectorSearchResult<Document> result = index.search(query, 1);
                assertEquals(1, result.size());
                assertEquals("needle", result.toList().get(0).entity().content(),
                    "Batch-added vectors should survive restart");
            }
        }
    }
}
