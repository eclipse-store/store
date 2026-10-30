package one.microstream.test.housekeeping;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - housekeeping
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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.types.StorageDataFileEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises SKILL Pattern G: after a bulk deletion, force GC and file
 * compaction via {@code issueFullGarbageCollection()} +
 * {@code issueFullFileCheck()}.
 *
 * Verifies the lesson from SKILL Anti-pattern 2 (and the skill's deeper
 * "Expecting immediate disk shrinkage" pitfall): on-disk file size does NOT
 * drop just from clearing the collection; it drops only after manual
 * housekeeping runs.
 */
class ManualHousekeepingTest
{
    private static long onDiskBytes(Path dir) throws IOException
    {
        try (Stream<Path> walk = Files.walk(dir))
        {
            return walk.filter(Files::isRegularFile)
                .mapToLong(p -> p.toFile().length())
                .sum();
        }
    }

    /**
     * Aggressive file evaluator that makes compaction observable in a unit
     * test: tiny file size bounds and a near-100% minimum use ratio so every
     * non-head file becomes eligible for dissolving on the first issueFileCheck
     * after deletion. With Eclipse Store's production defaults (1 MiB / 8 MiB /
     * 0.75 ratio), shrinkage is real but only observable after a long delete
     * stream — not appropriate for a unit assertion.
     */
    private static EmbeddedStorageManager startWithAggressiveEvaluator(
        OrdersRoot root, Path dir)
    {
        StorageDataFileEvaluator eval = StorageDataFileEvaluator.New(
            1024,        // min 1 KiB
            2048,        // max 2 KiB
            0.99,        // dissolve on any gap > 1%
            false        // do not touch the head file (skill default)
        );
        StorageConfiguration config = StorageConfiguration.Builder()
            .setStorageFileProvider(Storage.FileProvider(dir))
            .setDataFileEvaluator(eval)
            .createConfiguration();
        return EmbeddedStorage.Foundation(config).start(root);
    }

    @Test
    void patternG_bulk_delete_then_manual_housekeeping_shrinks_files(@TempDir Path tmp) throws IOException
    {
        Path storageDir = tmp.resolve("storage");

        // session 1: populate. With 1-2 KiB file bounds each Order ends up in
        // its own file (closed once max-size is reached). After bulk delete,
        // every non-head file is 100% gap and dissolves on issueFullFileCheck.
        OrdersRoot root1 = new OrdersRoot();
        EmbeddedStorageManager m1 = startWithAggressiveEvaluator(root1, storageDir);
        for (int i = 0; i < 500; i++)
        {
            root1.orders().add(new Order("o-" + i, "customer-" + i, i));
        }
        m1.store(root1.orders());
        m1.shutdown();

        // reopen — measure on-disk size BEFORE deletion + housekeeping
        OrdersRoot root2 = new OrdersRoot();
        EmbeddedStorageManager m2 = startWithAggressiveEvaluator(root2, storageDir);

        long sizeBefore = onDiskBytes(storageDir);

        // bulk delete: clear the collection, store the COLLECTION (Pattern C)
        root2.orders().clear();
        m2.store(root2.orders());

        // Pattern G: drive GC + file compaction. Measure shrinkage after
        // shutdown — channel-side dissolve work is dispatched and physical
        // file removal completes during the shutdown drain.
        m2.issueFullGarbageCollection();
        m2.issueFullFileCheck();
        m2.shutdown();

        long sizeAfter = onDiskBytes(storageDir);

        assertTrue(sizeAfter < sizeBefore,
            "on-disk size should shrink after issueFullGarbageCollection + "
            + "issueFullFileCheck. before=" + sizeBefore + " after=" + sizeAfter);

        // session 3: reopen and confirm the deletion is durable
        OrdersRoot root3 = new OrdersRoot();
        EmbeddedStorageManager m3 = startWithAggressiveEvaluator(root3, storageDir);
        try
        {
            assertTrue(root3.orders().isEmpty(),
                "orders list must be empty after restart following Pattern G");
        }
        finally
        {
            m3.shutdown();
        }
    }
}
