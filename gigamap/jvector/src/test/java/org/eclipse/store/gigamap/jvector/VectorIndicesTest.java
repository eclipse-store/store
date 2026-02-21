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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VectorIndices}.
 * <p>
 * Tests the core functionality of vector index management:
 * - Index registration and retrieval
 * - Index name validation
 * - Lifecycle management
 */
class VectorIndicesTest
{
    record Document(String content, float[] embedding) {}

    static class DocumentVectorizer extends Vectorizer<Document>
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            return entity.embedding();
        }
    }

    @Test
    void testAddIndex()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        final VectorIndex<Document> index = vectorIndices.add("test-index", config, new DocumentVectorizer());

        assertNotNull(index);
        assertEquals("test-index", index.name());
    }

    @Test
    void testAddDuplicateIndexThrows()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        vectorIndices.add("duplicate", config, new DocumentVectorizer());

        assertThrows(RuntimeException.class, () ->
            vectorIndices.add("duplicate", config, new DocumentVectorizer())
        );
    }

    @Test
    void testGetExistingIndex()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        final VectorIndex<Document> created = vectorIndices.add("my-index", config, new DocumentVectorizer());
        final VectorIndex<Document> retrieved = vectorIndices.get("my-index");

        assertSame(created, retrieved);
    }

    @Test
    void testGetNonExistentIndexReturnsNull()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        assertNull(vectorIndices.get("non-existent"));
    }

    @Test
    void testEnsureCreatesNewIndex()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        final VectorIndex<Document> index = vectorIndices.ensure("new-index", config, new DocumentVectorizer());

        assertNotNull(index);
        assertEquals("new-index", index.name());
    }

    @Test
    void testEnsureReturnsExistingIndex()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        final VectorIndex<Document> first = vectorIndices.ensure("existing", config, new DocumentVectorizer());
        final VectorIndex<Document> second = vectorIndices.ensure("existing", config, new DocumentVectorizer());

        assertSame(first, second);
    }

    @Test
    void testValidateIndexNameNull()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        assertThrows(IllegalArgumentException.class, () ->
            vectorIndices.add(null, config, new DocumentVectorizer())
        );
    }

    @Test
    void testValidateIndexNameEmpty()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        assertThrows(IllegalArgumentException.class, () ->
            vectorIndices.add("", config, new DocumentVectorizer())
        );
    }

    @Test
    void testValidateIndexNameWithSlash()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        assertThrows(IllegalArgumentException.class, () ->
            vectorIndices.add("invalid/name", config, new DocumentVectorizer())
        );
    }

    @Test
    void testValidateIndexNameWithBackslash()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        assertThrows(IllegalArgumentException.class, () ->
            vectorIndices.add("invalid\\name", config, new DocumentVectorizer())
        );
    }

    @Test
    void testValidateIndexNameTooLong()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        final String tooLong = "a".repeat(201);

        assertThrows(IllegalArgumentException.class, () ->
            vectorIndices.add(tooLong, config, new DocumentVectorizer())
        );
    }

    @Test
    void testValidateIndexNameWithValidCharacters()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        assertDoesNotThrow(() ->
            vectorIndices.add("valid-index_name.123", config, new DocumentVectorizer())
        );
    }

    @Test
    void testInternalAddPropagates()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        vectorIndices.add("index1", config, new DocumentVectorizer());
        vectorIndices.add("index2", config, new DocumentVectorizer());

        final Document doc = new Document("test", new float[]{1.0f, 0.0f, 0.0f});
        gigaMap.add(doc);

        final VectorIndex<Document> index1 = vectorIndices.get("index1");
        final VectorIndex<Document> index2 = vectorIndices.get("index2");

        final VectorSearchResult<Document> result1 = index1.search(new float[]{1.0f, 0.0f, 0.0f}, 1);
        final VectorSearchResult<Document> result2 = index2.search(new float[]{1.0f, 0.0f, 0.0f}, 1);

        assertEquals(1, result1.size());
        assertEquals(1, result2.size());
    }

    @Test
    void testInternalRemovePropagates()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        vectorIndices.add("index1", config, new DocumentVectorizer());
        vectorIndices.add("index2", config, new DocumentVectorizer());

        final Document doc = new Document("test", new float[]{1.0f, 0.0f, 0.0f});
        gigaMap.add(doc);
        gigaMap.removeById(0);

        final VectorIndex<Document> index1 = vectorIndices.get("index1");
        final VectorIndex<Document> index2 = vectorIndices.get("index2");

        final VectorSearchResult<Document> result1 = index1.search(new float[]{1.0f, 0.0f, 0.0f}, 1);
        final VectorSearchResult<Document> result2 = index2.search(new float[]{1.0f, 0.0f, 0.0f}, 1);

        assertEquals(0, result1.size());
        assertEquals(0, result2.size());
    }

    @Test
    void testInternalRemoveAllPropagates()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        vectorIndices.add("index1", config, new DocumentVectorizer());

        gigaMap.add(new Document("test1", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("test2", new float[]{0.0f, 1.0f, 0.0f}));

        gigaMap.removeAll();

        final VectorIndex<Document> index1 = vectorIndices.get("index1");
        final VectorSearchResult<Document> result = index1.search(new float[]{1.0f, 0.0f, 0.0f}, 10);

        assertEquals(0, result.size());
    }

    @Test
    void testIterateIndices()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        vectorIndices.add("index1", config, new DocumentVectorizer());
        vectorIndices.add("index2", config, new DocumentVectorizer());
        vectorIndices.add("index3", config, new DocumentVectorizer());

        final int[] count = {0};
        vectorIndices.iterate(index -> count[0]++);

        assertEquals(3, count[0]);
    }

    @Test
    void testAccessIndices()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        vectorIndices.add("index1", config, new DocumentVectorizer());
        vectorIndices.add("index2", config, new DocumentVectorizer());

        vectorIndices.accessIndices(table -> {
            assertNotNull(table.get("index1"));
            assertNotNull(table.get("index2"));
            assertNull(table.get("non-existent"));
        });
    }

    @Test
    void testIndexAutoPopulatesExistingEntities()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();

        gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("doc2", new float[]{0.0f, 1.0f, 0.0f}));

        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        final VectorIndex<Document> index = vectorIndices.add("new-index", config, new DocumentVectorizer());

        final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 10);

        assertEquals(2, result.size(), "Index should auto-populate with existing entities");
    }
}

