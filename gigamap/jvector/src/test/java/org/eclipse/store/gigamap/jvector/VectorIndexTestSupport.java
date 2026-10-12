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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Fixtures shared by the failure-path tests of the vector index: a small entity with a deterministic position, the
 * searches that read it back, the reflection reader for the index's internal state, the worker drain, and the armed
 * test hooks that fail one graph operation.
 */
final class VectorIndexTestSupport
{
    static class Doc
    {
        final int     no;
        /** mutable: the in-place update tests change the embedding of an existing entity */
        float[] vector;

        Doc(final int no)
        {
            this(no, position(no));
        }

        Doc(final int no, final float[] vector)
        {
            this.no     = no;
            this.vector = vector;
        }
    }

    /**
     * Computed, never fails: the index stores a copy of each vector and scores from it, so a stale graph node keeps
     * its position and can be told apart from the entity that reuses its id.
     */
    static class PlainComputedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.vector.clone();
        }
    }

    /** a {@link BackgroundTaskManager.Callback} that does nothing: the test doubles override what they record */
    abstract static class NoOpCallback implements BackgroundTaskManager.Callback
    {
        @Override
        public void applyGraphAdd(final VectorEntry entry, final long epoch)
        {
            // no-op
        }

        @Override
        public void applyGraphBatchAdd(final List<VectorEntry> entries, final long epoch)
        {
            // no-op
        }

        @Override
        public void applyGraphUpdate(final VectorEntry entry, final long epoch)
        {
            // no-op
        }

        @Override
        public void applyGraphRemove(final int ordinal, final long epoch)
        {
            // no-op
        }

        @Override
        public void markDirtyForBackgroundManagers(final int count)
        {
            // no-op
        }

        @Override
        public void markGraphIncomplete(final Throwable cause)
        {
            // no-op
        }

        @Override
        public void markGraphIncomplete(final Throwable cause, final long epoch)
        {
            // no-op
        }

        @Override
        public void repairGraph()
        {
            // no-op
        }

        @Override
        public void doOptimize()
        {
            // no-op
        }

        @Override
        public void doPersistToDisk(final boolean onShutdown)
        {
            // no-op
        }
    }

    static float[] position(final int no)
    {
        return new float[]{1.0f + no, 1.0f + no % 7, no * 0.5f, 1.0f + no % 3};
    }

    /** a 4-dimensional Euclidean index, the shape every fixture here builds on */
    static VectorIndexConfiguration.Builder config()
    {
        return VectorIndexConfiguration.builder()
            .dimension(4)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN);
    }

    static VectorIndexConfiguration.Builder eventual()
    {
        return config().eventualIndexing(true);
    }

    /** adds the entities {@code 0 .. count-1} at their positions */
    static void addDocs(final GigaMap<Doc> map, final int count)
    {
        for(int i = 0; i < count; i++)
        {
            map.add(new Doc(i));
        }
    }

    static GigaMap<Doc> populatedMap(final int count)
    {
        final GigaMap<Doc> map = GigaMap.New();
        addDocs(map, count);
        return map;
    }

    /** ids found by a search wide enough to return every one of {@code count} entities */
    static Set<Long> foundIds(final VectorIndex<?> index, final int count)
    {
        final Set<Long> ids = new TreeSet<>();
        index.search(position(0), count * 2).toList().forEach(e -> ids.add(e.entityId()));
        return ids;
    }

    /** whether a search by the entity's own vector returns its id as a near-exact match */
    static boolean foundExactly(final VectorIndex<Doc> index, final Doc doc, final long id)
    {
        return index.search(doc.vector, 3).toList().stream().anyMatch(e -> e.entityId() == id && e.score() > 0.99f);
    }

    @SuppressWarnings("unchecked")
    static <T> T internalState(final VectorIndex<?> index, final String fieldName)
    {
        try
        {
            final Field field = VectorIndex.Default.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T)field.get(index);
        }
        catch(final ReflectiveOperationException e)
        {
            throw new AssertionError("cannot read VectorIndex.Default." + fieldName, e);
        }
    }

    static BackgroundTaskManager manager(final VectorIndex<?> index)
    {
        final BackgroundTaskManager manager = internalState(index, "backgroundTaskManager");
        assertNotNull(manager, "precondition: a background task manager exists");
        return manager;
    }

    /**
     * Waits until the worker has applied every queued operation and run any repair those operations requested: a
     * repair requested by the first drain is queued behind it, and the second drain is queued behind the repair.
     */
    static void awaitWorker(final VectorIndex<?> index)
    {
        final BackgroundTaskManager manager = manager(index);
        manager.drainQueue();
        manager.drainQueue();
    }

    /** the next graph insertion of {@code ordinal} throws, once */
    static void failOneInsertion(final VectorIndex<?> index, final int ordinal)
    {
        ((VectorIndex.Default<?>)index).graphInsertTestHook = failingOnce(ordinal, "inserting");
    }

    /** the next graph deletion of {@code ordinal} throws, once */
    static void failOneDeletion(final VectorIndex<?> index, final int ordinal)
    {
        ((VectorIndex.Default<?>)index).graphDeleteTestHook = failingOnce(ordinal, "deleting");
    }

    private static IntConsumer failingOnce(final int ordinal, final String action)
    {
        final AtomicBoolean armed = new AtomicBoolean(true);
        return o ->
        {
            if(o == ordinal && armed.getAndSet(false))
            {
                throw new IllegalStateException("simulated engine failure " + action + " ordinal " + o);
            }
        };
    }

    private VectorIndexTestSupport()
    {
        // static only
    }
}
