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
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Searches that overlap {@link VectorIndex#invalidateGraph()} are held back and then answered from the rebuilt
 * graph: none fails and none sees the empty graph that stands between the retirement and the rebuild. The
 * entities never change, so every search must find the same results.
 */
class VectorIndexInvalidateGraphConcurrencyTest
{
    private static final float[] QUERY = {1, 0, 0, 1};

    static class Entity
    {
        final float[] vector;

        Entity(final float[] vector)
        {
            this.vector = vector;
        }
    }

    static class EntityVectorizer extends Vectorizer<Entity>
    {
        @Override
        public float[] vectorize(final Entity entity)
        {
            return entity.vector;
        }
    }

    private static VectorIndex<Entity> newFilledIndex(final GigaMap<Entity> map)
    {
        final VectorIndices<Entity> vectorIndices = map.index().register(VectorIndices.Category());
        vectorIndices.add(
            "vec",
            VectorIndexConfiguration.builder()
                .dimension(4)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .eventualIndexing(false)
                .build(),
            new EntityVectorizer()
        );
        for(int i = 0; i < 100; i++)
        {
            map.add(new Entity(new float[]{1, i % 7, i % 3, 1}));
        }
        return vectorIndices.get("vec");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void searchesOverlappingInvalidationsWithoutTheMonitorAreAnsweredFromTheRebuiltGraph() throws Exception
    {
        this.run(false);
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void searchesOverlappingInvalidationsUnderTheMonitorAreAnsweredFromTheRebuiltGraph() throws Exception
    {
        this.run(true);
    }

    private void run(final boolean underMonitor) throws Exception
    {
        final GigaMap<Entity>     map   = GigaMap.New();
        final VectorIndex<Entity> index = newFilledIndex(map);
        final int                 k     = index.search(QUERY, 3).size();
        assertEquals(3, k);

        final AtomicBoolean   stop          = new AtomicBoolean();
        final List<Throwable> failures      = new CopyOnWriteArrayList<>();
        final AtomicLong      searches      = new AtomicLong();
        final AtomicLong      invalidations = new AtomicLong();
        final ExecutorService executor      = Executors.newFixedThreadPool(5);
        try
        {
            for(int i = 0; i < 4; i++)
            {
                executor.submit(() ->
                {
                    while(!stop.get())
                    {
                        try
                        {
                            final int found = index.search(QUERY, 3).size();
                            if(found != 3)
                            {
                                failures.add(new AssertionError("expected 3 results, got " + found));
                            }
                            searches.incrementAndGet();
                        }
                        catch(final Throwable t)
                        {
                            failures.add(t);
                        }
                    }
                });
            }
            executor.submit(() ->
            {
                while(!stop.get())
                {
                    try
                    {
                        if(underMonitor)
                        {
                            synchronized(map)
                            {
                                index.invalidateGraph();
                            }
                        }
                        else
                        {
                            index.invalidateGraph();
                        }
                        invalidations.incrementAndGet();
                        Thread.sleep(5);
                    }
                    catch(final Throwable t)
                    {
                        failures.add(t);
                    }
                }
            });
            Thread.sleep(4_000);
        }
        finally
        {
            stop.set(true);
            executor.shutdown();
            assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS), "workers did not finish");
        }

        assertTrue(searches.get() > 0 && invalidations.get() > 0, "the threads did not overlap");
        assertTrue(failures.isEmpty(), failures.size() + " failures, first: " + (failures.isEmpty() ? "" : failures.get(0)));
        assertFalse(index.search(QUERY, 3).isEmpty(), "the index must still answer once the churn stops");
    }
}
