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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import io.github.jbellis.jvector.graph.GraphIndexBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.assertConsistent;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.config;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.index;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.map;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.read;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.register;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An index that was loaded but not used since the start still has its vectors to recover, which loads entities
 * lazily through the storage. Its background persists - scheduled or at shutdown - must not
 * do that recovery: a storage shutting down meanwhile keeps its lock while it waits for the index to close, and the
 * recovery would wait for that lock. An index that was used still writes what it rebuilt.
 */
class VectorIndexShutdownRecoveryTest
{
    static VectorIndexConfiguration.Builder onDisk(final Path dir)
    {
        return config().onDisk(true).indexDirectory(dir);
    }

    static void store(final Path storage, final VectorIndexConfiguration configuration)
    {
        try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
        {
            final GigaMap<Doc> map = map(new Random(1));
            manager.setRoot(map);
            manager.storeRoot();
            register(map, configuration);
            map.store();
        }
    }

    static void deleteIndexFiles(final Path index)
    {
        try(Stream<Path> files = Files.list(index))
        {
            files.forEach(file ->
            {
                try
                {
                    Files.delete(file);
                }
                catch(final IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            });
        }
        catch(final IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    /** starts the storage, runs {@code session}, shuts down - on a daemon thread, so a hang fails instead of blocking */
    static void session(final Path storage, final Consumer<GigaMap<Doc>> session) throws InterruptedException
    {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread application = new Thread(() ->
        {
            try
            {
                final EmbeddedStorageManager manager = EmbeddedStorage.start(storage);
                try
                {
                    session.accept(manager.root());
                }
                finally
                {
                    manager.shutdown();
                }
            }
            catch(final Throwable t)
            {
                failure.set(t);
            }
        });
        application.setDaemon(true);
        application.start();
        application.join(60_000);
        assertFalse(application.isAlive(), "the storage did not shut down");
        assertNull(failure.get());
    }

    /** uses the index - a search - which recovers its vectors and rebuilds its graph if they are due */
    static VectorIndex<Doc> use(final VectorIndex<Doc> index)
    {
        index.search(new float[VectorIndexFailedGraphInsertTest.DIMENSION], 1);
        return index;
    }

    static boolean recovered(final VectorIndex<Doc> index)
    {
        return (Boolean)read(index, "vectorsRecovered");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void untouchedIndexWithLostFiles_shutdownCompletes(@TempDir final Path dir) throws Exception
    {
        final Path storage = dir.resolve("storage");
        store(storage, onDisk(dir.resolve("index")).optimizationIntervalMs(3_600_000).build());
        deleteIndexFiles(dir.resolve("index"));

        session(storage, map -> assertFalse(recovered(index(map)))); // the index is not used
        session(storage, map -> assertConsistent(map, use(index(map))));
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void untouchedIndex_scheduledPersistDoesNotRecover(@TempDir final Path dir) throws Exception
    {
        final Path storage = dir.resolve("storage");
        store(storage, onDisk(dir.resolve("index")).persistenceIntervalMs(20).build());
        deleteIndexFiles(dir.resolve("index"));

        session(storage, map ->
        {
            try
            {
                Thread.sleep(300); // several persistence intervals; the load requested a persist
            }
            catch(final InterruptedException e)
            {
                throw new RuntimeException(e);
            }
            assertFalse(recovered(index(map)), "a background persist recovered the vectors of an unused index");
        });
        session(storage, map -> assertConsistent(map, use(index(map))));
    }

    /** control: an index used since the start writes its rebuilt graph, and the next start loads it */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void usedIndexWithLostFiles_shutdownWritesTheRebuiltGraph(@TempDir final Path dir) throws Exception
    {
        final Path storage = dir.resolve("storage");
        store(storage, onDisk(dir.resolve("index")).optimizationIntervalMs(3_600_000).build());
        deleteIndexFiles(dir.resolve("index"));

        session(storage, map -> assertConsistent(map, use(index(map)))); // used: recovers and rebuilds
        session(storage, map ->
        {
            final VectorIndex<Doc> index = use(index(map));
            assertConsistent(map, index);
            // a rejected load rebuilds every entity into the live graph; a loaded one leaves it empty
            assertEquals(0, ((GraphIndexBuilder)read(index, "builder")).getGraph().size(0),
                "the shutdown did not write the rebuilt graph");
        });
    }
}
