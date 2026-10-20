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

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link Vectorizer#vectorKey(Object)} and the unchanged-vector short-circuit of an update: an update
 * whose vector did not change costs no graph repair and no change bookkeeping, and with a key not even a
 * vectorizer call.
 */
class VectorKeyTest
{
    private static final int DIMENSION = 4;

    /**
     * An entity with a vector and a version of that vector, and a field that has nothing to do with either.
     */
    static final class Doc
    {
        final float[] vector ;
        final long    version;
        final String  label  ;

        Doc(
            final float[] vector ,
            final long    version,
            final String  label
        )
        {
            this.vector  = vector ;
            this.version = version;
            this.label   = label  ;
        }
    }

    static class CountingVectorizer extends Vectorizer<Doc>
    {
        final AtomicInteger calls = new AtomicInteger();
        final boolean       keyed   ;
        final boolean       embedded;

        CountingVectorizer(
            final boolean keyed   ,
            final boolean embedded
        )
        {
            this.keyed    = keyed   ;
            this.embedded = embedded;
        }

        @Override
        public float[] vectorize(final Doc entity)
        {
            this.calls.incrementAndGet();
            return entity.vector;
        }

        @Override
        public long vectorKey(final Doc entity)
        {
            return this.keyed ? entity.version : NO_KEY;
        }

        @Override
        public boolean isEmbedded()
        {
            return this.embedded;
        }
    }

    private static VectorIndexConfiguration config()
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();
    }

    private static float[] vector(final float first)
    {
        return new float[]{first, 1f, 2f, 3f};
    }

    private static VectorIndex.Default<Doc> register(
        final GigaMap<Doc>       map       ,
        final CountingVectorizer vectorizer
    )
    {
        final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
        return (VectorIndex.Default<Doc>)indices.add("embeddings", config(), vectorizer);
    }

    @Test
    void update_sameKey_skipsVectorize()
    {
        final GigaMap<Doc>             map        = GigaMap.New();
        final CountingVectorizer       vectorizer = new CountingVectorizer(true, false);
        final VectorIndex.Default<Doc> index      = register(map, vectorizer);
        final long id = map.add(new Doc(vector(1f), 7L, "a"));
        final int  callsAfterAdd = vectorizer.calls.get();
        final long modCount      = index.getStructuralModCount();

        map.set(id, new Doc(vector(1f), 7L, "relabelled"));

        assertEquals(callsAfterAdd, vectorizer.calls.get(), "an equal key must spare the vectorizer");
        assertEquals(modCount, index.getStructuralModCount());

        map.set(id, new Doc(vector(5f), 8L, "changed"));

        assertEquals(callsAfterAdd + 1, vectorizer.calls.get());
        assertArrayEquals(vector(5f), index.getVector(id));
        assertEquals(id, index.search(vector(5f), 1).iterator().next().entityId());
    }

    @Test
    void update_noKeySameFloats_skipsGraphRepair()
    {
        final GigaMap<Doc>             map        = GigaMap.New();
        final CountingVectorizer       vectorizer = new CountingVectorizer(false, false);
        final VectorIndex.Default<Doc> index      = register(map, vectorizer);
        final long id = map.add(new Doc(vector(1f), 0L, "a"));
        map.add(new Doc(vector(2f), 0L, "b"));
        final long modCount = index.getStructuralModCount();
        final int  nodes    = index.inMemoryGraphNodeCount();

        map.set(id, new Doc(vector(1f), 0L, "relabelled"));

        assertEquals(modCount, index.getStructuralModCount(), "an equal vector is no content change");
        assertEquals(nodes, index.inMemoryGraphNodeCount());
        assertEquals(id, index.search(vector(1f), 1).iterator().next().entityId());
    }

    @Test
    void update_embeddedSameVector_noGraphRepair()
    {
        final GigaMap<Doc>             map        = GigaMap.New();
        final CountingVectorizer       vectorizer = new CountingVectorizer(false, true);
        final VectorIndex.Default<Doc> index      = register(map, vectorizer);
        final long id = map.add(new Doc(vector(1f), 0L, "a"));
        final long modCount = index.getStructuralModCount();

        map.set(id, new Doc(vector(1f), 0L, "relabelled"));
        assertEquals(modCount, index.getStructuralModCount(), "embedded mode takes the same short-circuit");

        map.set(id, new Doc(vector(9f), 0L, "changed"));
        assertEquals(modCount + 1, index.getStructuralModCount());
        assertArrayEquals(vector(9f), index.getVector(id));
    }
}
