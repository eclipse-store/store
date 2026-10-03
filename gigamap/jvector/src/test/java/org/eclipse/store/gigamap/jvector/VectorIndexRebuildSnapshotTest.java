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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies rebuild scoring with sparse ordinals and later mutations across Store restarts. */
class VectorIndexRebuildSnapshotTest
{
    static class Entity
    {
        float[] vector;

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

    @Test
    void aRebuildSnapshotDoesNotKeepStaleVectorsAfterLaterMutations(@TempDir final Path directory)
    {
        final GigaMap<Entity> map = GigaMap.New();
        map.index().register(VectorIndices.Category()).add("vec",
            VectorIndexConfiguration.builder().dimension(4)
                .similarityFunction(VectorSimilarityFunction.COSINE).eventualIndexing(false).build(),
            new EntityVectorizer());
        for(int i = 0; i < 128; i++)
        {
            map.add(new Entity(new float[]{0, i + 1, 1, 0}));
        }
        for(long id = 0; id < 128; id += 2)
        {
            map.removeById(id);
        }
        final long target = map.add(new Entity(new float[]{1, 0, 0, 0}));
        try(var storage = EmbeddedStorage.start(map, directory))
        {
            map.store();
        }
        for(int round = 0; round < 4; round++)
        {
            try(var storage = EmbeddedStorage.start(directory))
            {
                final GigaMap<Entity> restored = storage.root();
                final VectorIndex<Entity> index = restored.index().get(VectorIndices.Category()).get("vec");
                assertEquals(target, index.search(new float[]{1, 0, 0, 0}, 1).toList().get(0).entityId());
                // The builder must stop using the rebuild snapshot once construction finishes.
                restored.set(target, new Entity(new float[]{0, 0, 0, 1}));
                assertEquals(target, index.search(new float[]{0, 0, 0, 1}, 1).toList().get(0).entityId());
                restored.set(target, new Entity(new float[]{1, 0, 0, 0}));
                restored.store();
            }
        }
        try(var storage = EmbeddedStorage.start(directory))
        {
            final GigaMap<Entity> restored = storage.root();
            restored.removeById(target);
            restored.store();
        }
        try(var storage = EmbeddedStorage.start(directory))
        {
            final GigaMap<Entity> restored = storage.root();
            final VectorIndex<Entity> index = restored.index().get(VectorIndices.Category()).get("vec");
            assertTrue(index.search(new float[]{1, 0, 0, 0}, 8).stream()
                .noneMatch(hit -> hit.entityId() == target), "rebuild must not resurrect a deleted ordinal");
        }
    }
}
