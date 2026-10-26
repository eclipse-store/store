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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * The vector projection of internal issue #119: {@code update()} of a committed entity whose new
 * embedding the index rejects - a mundane mistake such as an embedding produced by a differently-sized
 * model - used to trigger GigaMap's destructive {@code apply()} fallback and delete the committed
 * entity. The entity's own data is valid; only the derived index could not represent it, so the
 * exception must escape while the entity stays.
 * <p>
 * Together with the bitmap and Lucene cases this confirms the rule is index-family independent.
 */
class VectorIndexUpdateRetentionTest
{
    static final int DIM = 3;

    static final class Doc
    {
        float[] embedding; // mutable so update(id, logic) can change it in place

        Doc(final float[] embedding)
        {
            this.embedding = embedding;
        }
    }

    static final class EmbeddingVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.embedding;
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

    private static VectorIndexConfiguration config()
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();
    }

    @Test
    @Timeout(60)
    void updateToWrongDimensionRetainsTheCommittedEntity()
    {
        final GigaMap<Doc>       map     = GigaMap.New();
        final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
        indices.add("embeddings", config(), new EmbeddingVectorizer());

        final Doc  doc = new Doc(new float[]{1.0f, 0.0f, 0.0f});
        final long id  = map.add(doc);
        assertEquals(1, map.size());

        final float[] wrongDimension = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        assertThrows(RuntimeException.class, () -> map.update(id, e -> e.embedding = wrongDimension));

        assertSame(doc, map.get(id), "the committed entity must survive the index's rejection");
        assertEquals(1, map.size());
        assertSame(wrongDimension, doc.embedding, "the in-place mutation is kept");
    }

    /**
     * The same rejection for an entity whose graph node lives in a segment of an on-disk index: the
     * rejection leaves its owner as it was, so the segment keeps answering for it.
     */
    @Test
    @Timeout(60)
    void updateToWrongDimensionOfAnEntityInASegment_keepsItSearchable(@TempDir final Path dir)
    {
        final GigaMap<Doc>       map     = GigaMap.New();
        final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
        final VectorIndexConfiguration configuration = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(dir.resolve("index"))
            .build();

        try(final VectorIndex<Doc> index = indices.add("embeddings", configuration, new EmbeddingVectorizer()))
        {
            final float[] vector = new float[]{1.0f, 0.0f, 0.0f};
            final Doc     doc    = new Doc(vector);
            final long    id     = map.add(doc);
            map.add(new Doc(new float[]{0.0f, 1.0f, 0.0f}));
            index.persistToDisk();
            assertEquals(1, ((VectorIndex.Default<Doc>)index).sourceSet().segments().size());

            // A replacement rather than an in-place update, which would leave the committed entity with
            // the rejected embedding, read by every search that scores from the entities.
            final Doc wrongDimension = new Doc(new float[]{0.0f, 0.0f, 0.0f, 0.0f});
            assertThrows(RuntimeException.class, () -> map.set(id, wrongDimension));

            assertSame(doc, map.get(id), "the committed entity must survive the index's rejection");
            assertEquals(id, index.search(new float[]{1.0f, 0.1f, 0.0f}, 1).iterator().next().entityId(),
                "the segment must still answer for the entity");
        }
    }
}
