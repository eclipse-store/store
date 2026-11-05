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
import org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.FailingScoreProvider;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.ID;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.assertConsistent;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.config;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.consolidationRequired;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.drain;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.failNextInserts;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.finds;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.map;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.orphan;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.rebuildRequired;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.register;
import static org.eclipse.store.gigamap.jvector.VectorIndexFailedGraphInsertTest.vector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An Error in the background task manager's work must neither vanish nor stop it: an indexing operation that fails
 * with one is reported like a RuntimeException, and a periodic optimization or persistence that fails with one runs
 * again at its next interval.
 */
class VectorIndexBackgroundErrorTest
{
    static final Duration WITHIN = Duration.ofSeconds(20);

    /** the changes the background task manager counts towards the next optimization */
    static int optimizationChanges(final BackgroundTaskManager manager) throws ReflectiveOperationException
    {
        final Field field = BackgroundTaskManager.class.getDeclaredField("optimizationChangeCount");
        field.setAccessible(true);
        return ((AtomicInteger)field.get(manager)).get();
    }

    @Test
    void eventual_removeFailingWithAnError_isEscalated_andTheWorkerGoesOn()
    {
        final Random       random = new Random(1);
        final GigaMap<Doc> map    = map(random);
        try(final VectorIndex<Doc> index = register(map, config().eventualIndexing(true).build()))
        {
            index.optimize();
            final float[]               removedVector = map.get(ID).vector;
            final BackgroundTaskManager manager       = ((VectorIndex.Default<Doc>)index).backgroundTaskManager;
            final AtomicBoolean         once          = new AtomicBoolean(true);
            manager.applyTestHook = () ->
            {
                if(once.compareAndSet(true, false))
                {
                    throw new OutOfMemoryError("injected");
                }
            };
            try
            {
                map.removeById(ID);
                drain(index);
            }
            finally
            {
                manager.applyTestHook = null;
            }
            assertFalse(once.get(), "injected failure");
            assertTrue(rebuildRequired(index), "the failed removal was not escalated");

            assertFalse(finds(index, ID, removedVector), "the removed entity is still found");
            assertConsistent(map, index);

            final Doc  doc = new Doc(vector(random));
            final long id  = map.add(doc);
            drain(index);
            assertTrue(finds(index, id, doc.vector), "the worker stopped applying operations");
        }
    }

    /** the repair of a background optimization fails with an Error while it inserts an orphan again */
    @Test
    void backgroundOptimization_failingWithAnError_runsAgain()
    {
        final Random       random = new Random(2);
        final GigaMap<Doc> map    = map(random);
        try(final VectorIndex<Doc> index = register(map, config()
            .optimizationIntervalMs(50)
            .minChangesBetweenOptimizations(1)
            .build()))
        {
            final BackgroundTaskManager manager = ((VectorIndex.Default<Doc>)index).backgroundTaskManager;
            // The registration's back-fill counts as changes: let that optimization run and nothing be due any more
            // before the failure is armed, or it would repair the orphan first.
            await().atMost(WITHIN).until(() -> manager.getOptimizationCount() > 0 && optimizationChanges(manager) == 0);
            final long before = manager.getOptimizationCount();
            orphan(index, (int)ID);
            final FailingScoreProvider provider = failNextInserts(index, 1);
            provider.skip.set(1); // the add's own insert; the optimization's repair inserts the orphan next
            provider.error = true;
            map.add(new Doc(vector(random))); // the change that makes an optimization due

            await().atMost(WITHIN).until(() -> provider.fired.get() == 1 && manager.getOptimizationCount() > before);
            assertConsistent(map, index);
        }
    }

    /** the consolidation a failed graph operation requires fails with an Error in a background persist */
    @Test
    void backgroundPersistence_failingWithAnError_runsAgain(@TempDir final Path dir)
    {
        final Random random = new Random(3);
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir.resolve("storage")))
        {
            final GigaMap<Doc> map = map(random);
            storage.setRoot(map);
            storage.storeRoot();
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)register(map, config()
                .onDisk(true)
                .indexDirectory(dir.resolve("index"))
                .persistenceIntervalMs(50)
                .minChangesBetweenPersists(1_000) // only the escalation makes a persist due
                .build());
            map.store();
            index.persistToDisk();
            map.set(ID, new Doc(vector(random))); // live in the live graph, so the restore inserts it again

            final AtomicBoolean once = new AtomicBoolean(true);
            index.rebuildWorkerTestHook = () ->
            {
                if(once.compareAndSet(true, false))
                {
                    throw new OutOfMemoryError("injected");
                }
            };
            try
            {
                // a set whose insert and restore fail: escalated, the next background persist consolidates
                failNextInserts(index, 2);
                assertThrows(RuntimeException.class, () -> map.set(ID, new Doc(vector(random))));
                // escalated, unless a background persist has consolidated already, which only the failed attempt allows
                assertTrue(consolidationRequired(index) || !once.get(), "the failure was not escalated");

                await().atMost(WITHIN).until(() -> !once.get() && !consolidationRequired(index));
            }
            finally
            {
                index.rebuildWorkerTestHook = null;
            }
            assertConsistent(map, index);
        }
    }
}
