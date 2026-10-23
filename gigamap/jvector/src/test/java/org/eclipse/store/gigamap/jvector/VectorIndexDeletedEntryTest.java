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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Inserts into a live graph whose entry node is marked deleted, but not yet purged.
 * <p>
 * The entry node is the only node of its top layer, and jVector's insert search accepts live nodes only,
 * so from a deleted entry it finds no candidate on that layer and hands none down. Every node inserted
 * then links to nothing but nodes inserted after it, an island no search from the entry reaches. Removing
 * every entity is the deterministic way to get there: the entry is among them.
 */
class VectorIndexDeletedEntryTest
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

    private static final int DIMENSION = 16 ;
    private static final int COUNT     = 300;

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

    private static List<Document> documents(final long seed, final String prefix)
    {
        final Random         random    = new Random(seed);
        final List<Document> documents = new ArrayList<>(COUNT);
        for(int i = 0; i < COUNT; i++)
        {
            documents.add(new Document(prefix + i, randomVector(random, DIMENSION)));
        }
        return documents;
    }

    private static VectorIndex<Document> register(
        final GigaMap<Document> gigaMap         ,
        final boolean           eventualIndexing
    )
    {
        final VectorIndices<Document> indices = gigaMap.index().register(VectorIndices.Category());
        return indices.add(
            "embeddings",
            VectorIndexConfiguration.builder()
                .dimension(DIMENSION)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .eventualIndexing(eventualIndexing)
                .build(),
            new ComputedDocumentVectorizer()
        );
    }

    private static void removeAll(final GigaMap<Document> gigaMap, final long count)
    {
        for(long id = 0; id < count; id++)
        {
            gigaMap.removeById(id);
        }
    }

    private static void assertEachFindsItself(final VectorIndex<Document> index, final List<Document> documents)
    {
        int missed = 0;
        for(final Document document : documents)
        {
            final List<ScoredSearchResult.Entry<Document>> hits = index.search(document.embedding(), 1).toList();
            if(hits.isEmpty() || !hits.get(0).entity().content().equals(document.content()))
            {
                missed++;
            }
        }
        assertEquals(0, missed, "entities inserted after the entry node was deleted must be reachable");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void addAll_afterEntryNodeDeleted_newEntitiesAreReachable()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        try(final VectorIndex<Document> index = register(gigaMap, false))
        {
            gigaMap.addAll(documents(1L, "old_"));
            removeAll(gigaMap, COUNT);

            final List<Document> added = documents(2L, "new_");
            gigaMap.addAll(added);

            assertEachFindsItself(index, added);
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void add_afterEntryNodeDeleted_newEntitiesAreReachable()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        try(final VectorIndex<Document> index = register(gigaMap, false))
        {
            gigaMap.addAll(documents(1L, "old_"));
            removeAll(gigaMap, COUNT);

            final List<Document> added = documents(2L, "new_");
            for(final Document document : added)
            {
                gigaMap.add(document);
            }

            assertEachFindsItself(index, added);
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void addAll_eventualModeAfterEntryNodeDeleted_newEntitiesAreReachable()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        try(final VectorIndex<Document> index = register(gigaMap, true))
        {
            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;
            gigaMap.addAll(documents(1L, "old_"));
            defaultIndex.backgroundTaskManager.drainQueue();
            removeAll(gigaMap, COUNT);
            defaultIndex.backgroundTaskManager.drainQueue();

            final List<Document> added = documents(2L, "new_");
            gigaMap.addAll(added);
            defaultIndex.backgroundTaskManager.drainQueue();

            assertEachFindsItself(index, added);
        }
    }
}
