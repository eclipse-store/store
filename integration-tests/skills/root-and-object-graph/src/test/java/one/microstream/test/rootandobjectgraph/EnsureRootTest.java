package one.microstream.test.rootandobjectgraph;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - root-and-object-graph
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Core API — {@link org.eclipse.store.storage.types.StorageManager#ensureRoot}.
 *
 * <p>Session 1: empty DB. {@code ensureRoot(supplier)} must invoke the
 * supplier, set the produced root, and persist it.
 *
 * <p>Session 2: populated DB. {@code ensureRoot(supplier)} must NOT invoke the
 * supplier — the persisted root is returned unchanged.
 */
class EnsureRootTest
{
    @Test
    void ensureRoot_invokesSupplierOnFreshDb_skipsOnPopulatedDb(@TempDir Path tmp)
    {
        Path storageDir = tmp.resolve("storage");

        // session 1: fresh DB, no root passed to start()
        AtomicInteger calls1 = new AtomicInteger();
        EmbeddedStorageManager m1 = EmbeddedStorage.start(storageDir);
        try
        {
            assertNull(m1.root(),
                "no root passed to start() and no setRoot() yet — root() must be null");

            AppRoot ensured = m1.ensureRoot(() ->
            {
                calls1.incrementAndGet();
                return new AppRoot();
            });

            assertEquals(1, calls1.get(),
                "supplier must be invoked exactly once on a fresh DB");
            assertNotNull(ensured);
            assertNotNull(m1.root(),
                "after ensureRoot, root() must return the supplied instance");

            // also write some state so session 2 has something to load
            ensured.customers().put("c1", new Customer("c1", "Alice"));
            m1.store(ensured.customers());
        }
        finally
        {
            m1.shutdown();
        }

        // session 2: reopen the same directory. Root exists on disk.
        AtomicInteger calls2 = new AtomicInteger();
        EmbeddedStorageManager m2 = EmbeddedStorage.start(storageDir);
        try
        {
            // root is loaded automatically by start(dir) when one is persisted.
            assertNotNull(m2.root(),
                "with a persisted root on disk, start(dir) must load it");

            AppRoot ensured2 = m2.ensureRoot(() ->
            {
                calls2.incrementAndGet();
                return new AppRoot();
            });

            assertEquals(0, calls2.get(),
                "supplier must NOT be invoked when a root already exists");
            assertNotNull(ensured2);
            assertEquals(1, ensured2.customers().size(),
                "the existing root (with its data) must be returned");
            assertEquals("Alice", ensured2.customers().get("c1").name());
        }
        finally
        {
            m2.shutdown();
        }
    }
}
