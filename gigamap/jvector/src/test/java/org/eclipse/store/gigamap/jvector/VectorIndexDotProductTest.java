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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A DOT_PRODUCT index is not bounded to [0, 1]: jVector scores {@code (1 + dot) / 2}, which is negative when the dot
 * product is below -1, as it is for the vectors of a maximum inner product search that point away from the query.
 * Such a result is a legitimate one, and the search must not drop it.
 */
class VectorIndexDotProductTest
{
    private static final int COUNT = 40;
    private static final int TOP   = 5;

    static final class Item
    {
        final float[] vector;

        Item(final float[] vector)
        {
            this.vector = vector;
        }
    }

    static final class ItemVectorizer extends Vectorizer<Item>
    {
        @Override
        public float[] vectorize(final Item item)
        {
            return item.vector;
        }
    }

    private static VectorIndexConfiguration.Builder dotProduct()
    {
        return VectorIndexConfiguration.builder()
            .dimension(2)
            .similarityFunction(VectorSimilarityFunction.DOT_PRODUCT)
            .eventualIndexing(false);
    }

    /**
     * Items of equal length on an arc from 100 to 178 degrees, so that all of them point away from a query along
     * +x: with the query {10, 0} the dot product of item i is 30 * cos(100 + 2i degrees), from about -5.2 down to
     * -30. All are below -1, and item 0 is the best.
     */
    private static void addAwayFromTheQuery(final GigaMap<Item> map)
    {
        for(int i = 0; i < COUNT; i++)
        {
            final double angle = Math.toRadians(100 + 2 * i);
            map.add(new Item(new float[]{(float)(3 * Math.cos(angle)), (float)(3 * Math.sin(angle))}));
        }
    }

    private static List<Long> bestFirst()
    {
        final List<Long> ids = new ArrayList<>();
        for(long i = 0; i < TOP; i++)
        {
            ids.add(i);
        }
        return ids;
    }

    /**
     * The best items in order, and the score of the best one on jVector's scale, {@code (1 + dot) / 2}: negative,
     * as the dot product, 30 * cos(100 degrees), is below -1.
     */
    private static void assertBestFirst(final VectorIndex<Item> index)
    {
        final VectorSearchResult<Item> result = index.search(new float[]{10, 0}, TOP);
        final List<Long> ids = new ArrayList<>();
        float best = Float.NaN;
        for(final ScoredSearchResult.Entry<Item> entry : result)
        {
            if(ids.isEmpty())
            {
                best = entry.score();
            }
            ids.add(entry.entityId());
        }
        assertEquals(bestFirst(), ids);
        assertTrue(best < 0, "a negative score must reach the caller: " + best);
        assertEquals((1 + 30 * Math.cos(Math.toRadians(100))) / 2, best, 1e-3);
    }

    @Test
    void anInMemoryIndexReturnsResultsWhoseDotProductIsBelowMinusOne()
    {
        final GigaMap<Item> map = GigaMap.New();
        try(final VectorIndex<Item> index = map.index().register(VectorIndices.Category())
            .add("vec", dotProduct().build(), new ItemVectorizer()))
        {
            addAwayFromTheQuery(map);

            assertBestFirst(index);
        }
    }

    @Test
    void anOnDiskIndexReturnsResultsWhoseDotProductIsBelowMinusOne(@TempDir final Path tempDir)
    {
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir.resolve("storage")))
        {
            final GigaMap<Item> map = GigaMap.New();
            storage.setRoot(map);
            final VectorIndex<Item> index = map.index().register(VectorIndices.Category()).add(
                "vec",
                dotProduct().onDisk(true).indexDirectory(tempDir.resolve("index")).build(),
                new ItemVectorizer()
            );
            addAwayFromTheQuery(map);

            // Before the persist the items are in the live graph of the index, which searchHeapGraph searches;
            // after it they are in a segment, which searchSegment searches.
            assertBestFirst(index);
            index.persistToDisk();
            assertBestFirst(index);
        }
    }
}
