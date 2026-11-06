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

import org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.Doc;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.quantization.NVQuantization;
import io.github.jbellis.jvector.quantization.ProductQuantization;
import io.github.jbellis.jvector.vector.types.VectorFloat;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.assertConsistent;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.config;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.map;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.read;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.register;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A compressor training that fails with an Error (an OutOfMemoryError, or one of jVector's assertions) takes the
 * documented fallback like a RuntimeException: the persist writes an uncompressed graph or full-precision vectors,
 * the in-memory PQ switch stays on exact scoring, and the training is not tried again until the data changes.
 */
class VectorIndexTrainingErrorTest
{
    static final String INJECTED = "injected training failure";

    /** delegates; its training fails with an Error */
    static final class FailingPq implements PQCompressionManager
    {
        final PQCompressionManager delegate ;
        final AtomicInteger        trainings = new AtomicInteger();

        FailingPq(final PQCompressionManager delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public boolean isTrained()
        {
            return this.delegate.isTrained();
        }

        @Override
        public ProductQuantization getPQ()
        {
            return this.delegate.getPQ();
        }

        @Override
        public void trainIfNeeded()
        {
            this.delegate.trainIfNeeded();
        }

        @Override
        public List<VectorFloat<?>> collectTrainingSampleIfNeeded()
        {
            return this.delegate.collectTrainingSampleIfNeeded();
        }

        @Override
        public void trainFrom(final List<VectorFloat<?>> trainingVectors)
        {
            this.trainings.incrementAndGet();
            throw new AssertionError(INJECTED);
        }

        @Override
        public void adoptTrainedPQ(final ProductQuantization pq)
        {
            this.delegate.adoptTrainedPQ(pq);
        }

        @Override
        public void reset()
        {
            this.delegate.reset();
        }
    }

    /** delegates; its training fails with an Error */
    static final class FailingNvq implements NVQCompressionManager
    {
        final NVQCompressionManager delegate ;
        final AtomicInteger         trainings = new AtomicInteger();

        FailingNvq(final NVQCompressionManager delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public boolean isTrained()
        {
            return this.delegate.isTrained();
        }

        @Override
        public NVQuantization getNVQ()
        {
            return this.delegate.getNVQ();
        }

        @Override
        public boolean trainFrom(final List<VectorFloat<?>> trainingVectors)
        {
            this.trainings.incrementAndGet();
            // An AssertionError, not an OutOfMemoryError: JUnit takes the latter as fatal for the whole test run.
            throw new AssertionError(INJECTED);
        }

        @Override
        public void adoptTrainedNVQ(final NVQuantization nvq)
        {
            this.delegate.adoptTrainedNVQ(nvq);
        }

        @Override
        public void reset()
        {
            this.delegate.reset();
        }
    }

    @Test
    void persist_pqTrainingFailingWithAnError_writesAnUncompressedGraph(@TempDir final Path dir)
    {
        final Random random  = new Random(1);
        final Path   storage = dir.resolve("storage");
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc> map = map(random);
            manager.setRoot(map);
            manager.storeRoot();
            final VectorIndex<Doc> index = register(map, config()
                .onDisk(true)
                .indexDirectory(dir.resolve("index"))
                .approximateScoring(ApproximateScoring.FUSED_PQ)
                .pqSubspaces(2)
                .build());
            map.store();
            final FailingPq pq = new FailingPq((PQCompressionManager)read(index, "pqManager"));
            write(index, "pqManager", pq);

            index.persistToDisk(); // aborted by the Error before
            assertEquals(1, pq.trainings.get(), "trainings");
            index.persistToDisk();
            assertEquals(1, pq.trainings.get(), "trained again although nothing changed");
            assertFalse(pq.isTrained());
            assertConsistent(map, index);
            map.store();
        }
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc> map = manager.root();
            assertConsistent(map, VectorIndexFailedGraphInsertTest.index(map));
        }
    }

    @Test
    void persist_nvqTrainingFailingWithAnError_writesFullPrecisionVectors(@TempDir final Path dir)
    {
        final Random random  = new Random(2);
        final Path   storage = dir.resolve("storage");
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc> map = map(random);
            manager.setRoot(map);
            manager.storeRoot();
            final VectorIndex<Doc> index = register(map, config()
                .onDisk(true)
                .indexDirectory(dir.resolve("index"))
                .vectorStorage(VectorStorage.NVQ)
                .build());
            map.store();
            final FailingNvq nvq = new FailingNvq((NVQCompressionManager)read(index, "nvqManager"));
            write(index, "nvqManager", nvq);

            index.persistToDisk(); // aborted by the Error before
            assertEquals(1, nvq.trainings.get(), "trainings");
            index.persistToDisk();
            assertEquals(1, nvq.trainings.get(), "trained again although nothing changed");
            assertFalse(nvq.isTrained());
            assertConsistent(map, index);
            map.store();
        }
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc> map = manager.root();
            assertConsistent(map, VectorIndexFailedGraphInsertTest.index(map));
        }
    }

    /**
     * In-memory PQ on disk keeps its codes in a sidecar file, whose absence a load takes for a loss: the graph written
     * without codes after a failed training must still be adopted by the next start, not rebuilt on every start.
     */
    @Test
    void persist_inMemoryPqOnDisk_trainingFailingWithAnError_isAdoptedAfterARestart(@TempDir final Path dir)
    {
        final Random random  = new Random(4);
        final Path   storage = dir.resolve("storage");
        final VectorIndexConfiguration configuration = config()
            .onDisk(true)
            .indexDirectory(dir.resolve("index"))
            .approximateScoring(ApproximateScoring.PQ_IN_MEMORY)
            .pqSubspaces(2)
            .optimizationIntervalMs(3_600_000)
            .build();
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc> map = map(random);
            manager.setRoot(map);
            manager.storeRoot();
            final VectorIndex<Doc> index = register(map, configuration);
            map.store();
            final FailingPq pq = new FailingPq((PQCompressionManager)read(index, "pqManager"));
            write(index, "pqManager", pq);

            index.persistToDisk();
            assertEquals(1, pq.trainings.get(), "trainings");
            map.store();
        }
        for(int start = 1; start <= 2; start++)
        {
            try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
            {
                final GigaMap<Doc>     map   = manager.root();
                final VectorIndex<Doc> index = VectorIndexFailedGraphInsertTest.index(map);
                assertConsistent(map, index);
                // a rejected load rebuilds every entity into the live graph; an adopted one leaves it empty
                final GraphIndexBuilder live = (GraphIndexBuilder)read(index, "builder");
                assertEquals(0, live.getGraph().size(0), "start " + start + " rebuilt the index instead of loading it");
            }
        }
    }

    @Test
    void pqSwitch_trainingFailingWithAnError_staysOnExactScoring()
    {
        final Random           random = new Random(3);
        final GigaMap<Doc>     map    = map(random);
        final VectorIndex<Doc> index  = register(map, config()
            .approximateScoring(ApproximateScoring.PQ_IN_MEMORY)
            .pqSubspaces(2)
            .optimizationIntervalMs(3_600_000) // required for in-memory PQ; the test switches explicitly
            .build());
        final FailingPq pq = new FailingPq((PQCompressionManager)read(index, "pqManager"));
        write(index, "pqManager", pq);

        index.optimize(); // aborted by the Error before
        assertEquals(1, pq.trainings.get(), "trainings");
        index.optimize();
        assertEquals(1, pq.trainings.get(), "trained again although nothing changed");
        assertFalse(index.isPqCompressionActive());
        assertConsistent(map, index);
    }
}
