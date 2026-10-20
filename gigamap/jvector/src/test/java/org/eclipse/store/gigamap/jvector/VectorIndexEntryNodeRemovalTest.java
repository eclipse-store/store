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

import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Removing the entity whose graph node is the HNSW entry node must not disconnect the entities added afterwards.
 * jvector's {@code markNodeDeleted} leaves the entry node in place; an insert that descends from a deleted entry
 * with no live neighbour on its layer gets no neighbours at all, and no search ever reaches it.
 * Every insert path purges the deleted nodes first when the entry is one of them, which picks a live entry.
 */
class VectorIndexEntryNodeRemovalTest
{
    static final int INITIAL = 1000;
    static final int ADDED   = 100;

    static class Doc
    {
        final int     no;
        final float[] vector;

        Doc(final int no)
        {
            this.no     = no;
            this.vector = position(no);
        }
    }

    static float[] position(final int no)
    {
        return new float[]{1.0f + no, 1.0f + no % 7, no * 0.5f, 1.0f + no % 3};
    }

    /** computed: the index keeps a copy of each vector in its vector file */
    static class PlainComputedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.vector.clone();
        }
    }

    /** the issue's shape: the vectors live in the entity */
    static final class EmbeddedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.vector;
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

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

    /** waits until the worker has applied every queued operation */
    static void awaitWorker(final VectorIndex<?> index)
    {
        final BackgroundTaskManager manager = internalState(index, "backgroundTaskManager");
        assertNotNull(manager, "precondition: a background task manager exists");
        manager.drainQueue();
        manager.drainQueue();
    }

    static VectorIndex<Doc> register(final GigaMap<Doc> map, final VectorIndexConfiguration configuration, final Vectorizer<Doc> vectorizer)
    {
        return map.index().register(VectorIndices.Category()).add("v", configuration, vectorizer);
    }

    /** the entity id behind the in-memory graph's current entry node (ordinal == entity id in a fresh map) */
    static long entryNodeEntityId(final VectorIndex<Doc> index)
    {
        final GraphIndexBuilder builder = internalState(index, "builder");
        assertNotNull(builder, "the index holds an in-memory builder");
        return ((OnHeapGraphIndex)builder.getGraph()).entryNode().node;
    }

    /** adds the entities {@code 1000 .. 1099}: their positions differ from every entity added before, so a search by
     *  an entity's own vector can only be answered by that entity */
    static void addLaterDocs(final GigaMap<Doc> map)
    {
        for(int no = INITIAL; no < INITIAL + ADDED; no++)
        {
            map.add(new Doc(no));
        }
    }

    static int foundCount(final GigaMap<Doc> map, final VectorIndex<Doc> index, final long fromId, final long toId)
    {
        int found = 0;
        for(long id = fromId; id < toId; id++)
        {
            final Doc doc = map.get(id);
            if(doc != null && foundExactly(index, doc, id))
            {
                found++;
            }
        }
        return found;
    }

    static void assertLaterAddsSearchable(final GigaMap<Doc> map, final VectorIndex<Doc> index, final String phase)
    {
        assertEquals(ADDED, foundCount(map, index, INITIAL, INITIAL + ADDED), phase + ": every entity added after the removal is searchable");
    }

    @Test
    void removingTheEntryNodeEntityKeepsLaterAddsSearchable_computed()
    {
        final GigaMap<Doc>     map   = populatedMap(INITIAL);
        final VectorIndex<Doc> index = register(map, config().build(), new PlainComputedVectorizer());
        this.removeEntryNodeThenAdd(map, index);

        assertLaterAddsSearchable(map, index, "in memory");
        assertEquals(INITIAL - 1, foundCount(map, index, 0, INITIAL), "the surviving entities are still searchable");

        // the maintenance purges the deleted node; the new nodes must stay connected
        index.optimize();
        assertLaterAddsSearchable(map, index, "after optimize()");
    }

    @Test
    void removingTheEntryNodeEntityKeepsLaterAddsSearchable_embedded()
    {
        final GigaMap<Doc>     map   = populatedMap(INITIAL);
        final VectorIndex<Doc> index = register(map, config().build(), new EmbeddedVectorizer());
        this.removeEntryNodeThenAdd(map, index);

        assertLaterAddsSearchable(map, index, "in memory");
        index.optimize();
        assertLaterAddsSearchable(map, index, "after optimize()");
    }

    @Test
    void removingTheEntryNodeEntityKeepsLaterAddsSearchable_eventual()
    {
        final GigaMap<Doc>     map   = GigaMap.New();
        final VectorIndex<Doc> index = register(map, eventual().build(), new PlainComputedVectorizer());
        addDocs(map, INITIAL);
        awaitWorker(index);

        final long entry = entryNodeEntityId(index);
        map.removeById(entry);
        awaitWorker(index);
        addLaterDocs(map);
        awaitWorker(index);

        assertLaterAddsSearchable(map, index, "worker-applied");
    }

    @Test
    void removingEveryEntityKeepsLaterAddsSearchable()
    {
        final GigaMap<Doc>     map   = populatedMap(INITIAL);
        final VectorIndex<Doc> index = register(map, config().build(), new PlainComputedVectorizer());
        for(long id = 0; id < INITIAL; id++)
        {
            map.removeById(id);
        }
        addLaterDocs(map);

        assertLaterAddsSearchable(map, index, "in memory");
        index.optimize();
        assertLaterAddsSearchable(map, index, "after optimize()");
    }

    private void removeEntryNodeThenAdd(final GigaMap<Doc> map, final VectorIndex<Doc> index)
    {
        final long entry = entryNodeEntityId(index);
        assertNotNull(map.get(entry), "the entry node belongs to a live entity");
        map.removeById(entry);
        addLaterDocs(map);
    }
}
