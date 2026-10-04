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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the rebuild scoring view's scope: only the graph builder's lookup may resolve through it.
 * Search and persistence adapters must resolve the current Store even while a rebuild is live, and
 * the view must be released when the build ends, so nothing stale can survive it.
 */
class VectorIndexRebuildScopeTest
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
    void onlyTheBuildLookupReadsTheScoringSnapshotAndItIsReleasedAfterwards(@TempDir final Path directory)
    {
        final GigaMap<Entity> map = GigaMap.New();
        map.index().register(VectorIndices.Category()).add("vec",
            VectorIndexConfiguration.builder().dimension(4)
                .similarityFunction(VectorSimilarityFunction.COSINE).eventualIndexing(false).build(),
            new EntityVectorizer());
        final long target = map.add(new Entity(new float[]{1, 0, 0, 0}));
        map.add(new Entity(new float[]{0, 1, 0, 0}));
        map.add(new Entity(new float[]{0, 0, 1, 0}));
        try(var storage = EmbeddedStorage.start(map, directory))
        {
            map.store();
        }

        try(var storage = EmbeddedStorage.start(directory))
        {
            final GigaMap<Entity> restored = storage.root();
            @SuppressWarnings("unchecked")
            final VectorIndex.Default<Entity> internal =
                (VectorIndex.Default<Entity>)restored.index()
                    .get(VectorIndices.Category()).get("vec");
            final AtomicBoolean hookRan = new AtomicBoolean();
            internal.rebuildScoringTestHook = snapshot ->
            {
                hookRan.set(true);
                /* An ordinal the collected snapshot never held is visible only to the build lookup:
                 * the store-side lookup must keep resolving the current Store even mid-build. */
                final int absent = 10_000;
                final float[] poison = new float[]{0, 0, 0, 9};
                assertFalse(snapshot.containsKey(absent));
                snapshot.put(absent, poison);
                assertSame(poison, internal.lookupBuildVector(absent),
                    "the builder's adapter scores from the live snapshot");
                assertNull(internal.lookupComputedVector(absent),
                    "search and persistence resolve the current Store, never the snapshot");
                /* An ordinal present in both resolves identically: the snapshot shares, not copies. */
                assertArrayEquals(new float[]{1, 0, 0, 0}, internal.lookupBuildVector((int)target));
            };
            try
            {
                /* The first search performs the deferred graph rebuild and fires the hook. */
                final VectorIndex<Entity> index = internal;
                assertEquals(target,
                    index.search(new float[]{1, 0, 0, 0}, 1).toList().get(0).entityId());
                /* Post-build searches derive the same answer from the Store, not a retained snapshot. */
                assertEquals(target,
                    index.search(new float[]{1, 0, 0, 0}, 1).toList().get(0).entityId());
            }
            finally
            {
                assertTrue(hookRan.get(), "the scoring snapshot never went live during the rebuild");
                assertNull(internal.rebuildVectors,
                    "the scoring snapshot is released when the build ends, success or failure");
            }
        }
    }
}
