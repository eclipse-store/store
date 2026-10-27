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
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A failed add rolls back and gives its id back, while the vector index only marks the node deleted. An add that
 * gets the id again must insert its node anew: reviving the deleted node would keep the links chosen for the
 * rolled-back vector, and the new entity would not be found by its own vector.
 * <p>
 * The rollback is caused by a second vector index whose vectorizer throws after the first index has inserted the
 * node. The rolled-back entities lie in cluster A, the entities that get their ids in cluster B. Every test checks
 * that the entry node is not among the rolled-back nodes: inserting below a deleted entry purges every deleted node
 * first, which would hide the defect.
 */
class VectorIndexReclaimedIdTest
{
    static final int DIMENSION = 8;
    static final int CLUSTER   = 400;
    static final int AXIS_A    = 0;
    static final int AXIS_B    = 1;

    static final String FAILURE = "vectorizer failure";

    static final class Doc
    {
        final float[] vector;
        final boolean failing;

        Doc(final float[] vector, final boolean failing)
        {
            this.vector  = vector;
            this.failing = failing;
        }
    }

    static final class PlainComputedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.vector.clone();
        }
    }

    /** throws for a failing entity, after running {@link #beforeThrow} if one is set */
    static final class FailingVectorizer extends Vectorizer<Doc>
    {
        transient Runnable beforeThrow;

        @Override
        public float[] vectorize(final Doc entity)
        {
            if(entity.failing)
            {
                if(this.beforeThrow != null)
                {
                    this.beforeThrow.run();
                }
                throw new IllegalStateException(FAILURE);
            }
            return entity.vector.clone();
        }
    }

    static final class Fixture implements AutoCloseable
    {
        final GigaMap<Doc>      map;
        final VectorIndex<Doc>  index;
        final VectorIndex<Doc>  failingIndex;
        final FailingVectorizer failing = new FailingVectorizer();

        Fixture(final GigaMap<Doc> map, final VectorIndexConfiguration configuration)
        {
            this.map          = map;
            this.index        = map.index().register(VectorIndices.Category()).add("a", configuration, new PlainComputedVectorizer());
            this.failingIndex = map.index().get(VectorIndices.class).add("b", configuration, this.failing);
        }

        long nextId()
        {
            return this.map.highestUsedId() + 1;
        }

        @Override
        public void close()
        {
            this.index.close();
            this.failingIndex.close();
        }
    }

    static VectorIndexConfiguration.Builder config()
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN);
    }

    static float[] around(final int axis, final Random random, final float noise)
    {
        final float[] vector = new float[DIMENSION];
        vector[axis] = 10f;
        for(int i = 0; i < DIMENSION; i++)
        {
            vector[i] += (float)(random.nextGaussian() * noise);
        }
        return vector;
    }

    static float[] centre(final int axis)
    {
        final float[] vector = new float[DIMENSION];
        vector[axis] = 10f;
        return vector;
    }

    static void fill(final GigaMap<Doc> map, final Random random)
    {
        for(int i = 0; i < CLUSTER; i++)
        {
            map.add(new Doc(around(AXIS_A, random, 0.5f), false));
            map.add(new Doc(around(AXIS_B, random, 0.5f), false));
        }
    }

    /** the entities of a batch in cluster A, the last one failing */
    static List<Doc> failingBatch(final Random random, final int size)
    {
        final List<Doc> docs = new ArrayList<>();
        for(int i = 0; i < size; i++)
        {
            docs.add(new Doc(around(AXIS_A, random, 0.5f), i == size - 1));
        }
        return docs;
    }

    static List<Doc> batchInClusterB(final Random random, final int size)
    {
        final List<Doc> docs = new ArrayList<>();
        for(int i = 0; i < size; i++)
        {
            docs.add(new Doc(around(AXIS_B, random, 0.5f), false));
        }
        return docs;
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

    static OnHeapGraphIndex graph(final VectorIndex<?> index)
    {
        final GraphIndexBuilder builder = internalState(index, "builder");
        assertNotNull(builder, "the index holds an in-memory builder");
        return (OnHeapGraphIndex)builder.getGraph();
    }

    static void awaitWorker(final VectorIndex<?> index)
    {
        final BackgroundTaskManager manager = internalState(index, "backgroundTaskManager");
        assertNotNull(manager, "precondition: a background task manager exists");
        manager.drainQueue();
        manager.drainQueue();
    }


    /**
     * A hook for the failing vectorizer: waits until the worker of the first index has inserted the ids. Throws an
     * {@link IllegalStateException} on timeout, so the mutation is still rolled back and the test reports why.
     */
    static Runnable awaitNodes(final VectorIndex<?> index, final long firstId, final int count)
    {
        return () ->
        {
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime() - deadline < 0)
            {
                boolean all = true;
                for(long id = firstId; id < firstId + count; id++)
                {
                    all &= graph(index).containsNode((int)id);
                }
                if(all)
                {
                    return;
                }
                Thread.onSpinWait();
            }
            throw new IllegalStateException("precondition: the worker inserts the nodes before the rollback");
        };
    }

    /** runs a mutation that the failing vectorizer makes throw, which rolls it back */
    static void assertRolledBackByVectorizer(final Executable mutation)
    {
        assertEquals(FAILURE, assertThrows(IllegalStateException.class, mutation).getMessage(),
            "the mutation fails in the second index's vectorizer");
    }

    /** checks what the rollback leaves: the ids given back, their nodes marked deleted, none of them the entry */
    static void assertRolledBack(final Fixture fixture, final long firstId, final int count)
    {
        assertEquals(firstId, fixture.nextId(), "precondition: the ids are given back");
        final OnHeapGraphIndex graph = graph(fixture.index);
        for(long id = firstId; id < firstId + count; id++)
        {
            assertTrue(graph.containsNode((int)id) && graph.getDeletedNodes().get((int)id),
                "precondition: the rolled-back node " + id + " is a deleted node");
        }
        final int entry = graph.entryNode().node;
        assertFalse(entry >= firstId && entry < firstId + count,
            "precondition: the entry node " + entry + " is not a rolled-back one");
    }

    static boolean foundByOwnVector(final GigaMap<Doc> map, final VectorIndex<Doc> index, final long id)
    {
        return index.search(map.get(id).vector, 1).toList().stream().anyMatch(e -> e.entityId() == id);
    }

    static void assertFoundByOwnVector(final GigaMap<Doc> map, final VectorIndex<Doc> index, final long firstId, final int count, final String phase)
    {
        for(long id = firstId; id < firstId + count; id++)
        {
            assertTrue(foundByOwnVector(map, index, id), phase + ": entity " + id + " is found by its own vector");
        }
    }

    private void addReusingTheIdOfARolledBackAdd(final VectorIndexConfiguration configuration, final boolean eventual)
    {
        final Random random = new Random(3);
        try(final Fixture fixture = new Fixture(GigaMap.New(), configuration))
        {
            fill(fixture.map, random);
            if(eventual)
            {
                awaitWorker(fixture.index);
            }

            final long id = fixture.nextId();
            if(eventual)
            {
                fixture.failing.beforeThrow = awaitNodes(fixture.index, id, 1);
            }
            assertRolledBackByVectorizer(() -> fixture.map.add(new Doc(around(AXIS_A, random, 0.01f), true)));
            if(eventual)
            {
                awaitWorker(fixture.index);
            }
            assertRolledBack(fixture, id, 1);

            assertEquals(id, fixture.map.add(new Doc(centre(AXIS_B), false)));
            if(eventual)
            {
                awaitWorker(fixture.index);
            }
            assertFoundByOwnVector(fixture.map, fixture.index, id, 1, eventual ? "eventual" : "sync");
        }
    }

    private void addAllReusingTheIdsOfARolledBackAddAll(final VectorIndexConfiguration configuration, final boolean eventual)
    {
        // Two entities: with this data a larger rolled-back batch holds the entry node.
        final int    size   = 2;
        final Random random = new Random(5);
        try(final Fixture fixture = new Fixture(GigaMap.New(), configuration))
        {
            fill(fixture.map, random);
            if(eventual)
            {
                awaitWorker(fixture.index);
            }

            final long first = fixture.nextId();
            if(eventual)
            {
                fixture.failing.beforeThrow = awaitNodes(fixture.index, first, size);
            }
            final List<Doc> failing = failingBatch(random, size);
            assertRolledBackByVectorizer(() -> fixture.map.addAll(failing));
            if(eventual)
            {
                awaitWorker(fixture.index);
            }
            assertRolledBack(fixture, first, size);

            fixture.map.addAll(batchInClusterB(random, size));
            if(eventual)
            {
                awaitWorker(fixture.index);
            }
            assertFoundByOwnVector(fixture.map, fixture.index, first, size, eventual ? "eventual" : "sync");
        }
    }

    @Test
    void addReusingTheIdOfARolledBackAddIsSearchable_sync()
    {
        this.addReusingTheIdOfARolledBackAdd(config().build(), false);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void addReusingTheIdOfARolledBackAddIsSearchable_eventual()
    {
        this.addReusingTheIdOfARolledBackAdd(config().eventualIndexing(true).build(), true);
    }

    @Test
    void addAllReusingTheIdsOfARolledBackAddAllIsSearchable_sync()
    {
        this.addAllReusingTheIdsOfARolledBackAddAll(config().build(), false);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void addAllReusingTheIdsOfARolledBackAddAllIsSearchable_eventual()
    {
        this.addAllReusingTheIdsOfARolledBackAddAll(config().eventualIndexing(true).build(), true);
    }

    /** the node written to a segment by a persist is the re-inserted one, which a restart loads */
    @Test
    void addReusingTheIdOfARolledBackAddIsSearchableAfterRestart_onDisk(@TempDir final Path directory)
    {
        final Path                     storageDirectory = directory.resolve("storage");
        final VectorIndexConfiguration configuration    = config().onDisk(true).indexDirectory(directory.resolve("index")).build();
        final Random                   random           = new Random(3);

        final long id;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.<Doc>New(), storageDirectory))
        {
            final GigaMap<Doc> map     = storage.root();
            final Fixture      fixture = new Fixture(map, configuration);
            fill(map, random);

            id = fixture.nextId();
            assertRolledBackByVectorizer(() -> map.add(new Doc(around(AXIS_A, random, 0.01f), true)));
            assertRolledBack(fixture, id, 1);
            assertEquals(id, map.add(new Doc(centre(AXIS_B), false)));
            assertFoundByOwnVector(map, fixture.index, id, 1, "in session");

            fixture.index.persistToDisk();
            map.store();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDirectory))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.class).get("a");
            assertFoundByOwnVector(map, index, id, 1, "after restart");
        }
    }
}
