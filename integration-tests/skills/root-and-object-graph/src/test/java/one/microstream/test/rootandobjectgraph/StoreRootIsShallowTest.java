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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pitfall #4 — {@code storeRoot()} stores the root reference itself, not the
 * nested collections. Mutating a child collection and calling only
 * {@code storeRoot()} does NOT persist the mutation.
 *
 * <p>The corollary, also asserted here: calling {@code store(root.customers())}
 * on the modified collection DOES round-trip.
 */
class StoreRootIsShallowTest
{
    @Test
    void storeRoot_doesNotPersistCollectionMutations(@TempDir Path tmp)
    {
        Path storageDir = tmp.resolve("storage");

        // session 1: mutate a child collection, then call ONLY storeRoot()
        AppRoot root1 = new AppRoot();
        EmbeddedStorageManager m1 = EmbeddedStorage.start(root1, storageDir);
        try
        {
            root1.customers().put("c1", new Customer("c1", "Alice"));
            // Pitfall #4: only storeRoot() — root reference unchanged, so this is
            // effectively a no-op for the new customer.
            m1.storeRoot();
        }
        finally
        {
            m1.shutdown();
        }

        // session 2: reopen and confirm the customer is NOT there
        AppRoot root2 = new AppRoot();
        EmbeddedStorageManager m2 = EmbeddedStorage.start(root2, storageDir);
        try
        {
            assertTrue(root2.customers().isEmpty(),
                "storeRoot() alone must NOT persist nested collection mutations");
        }
        finally
        {
            m2.shutdown();
        }
    }

    @Test
    void storeCollection_doesPersist(@TempDir Path tmp)
    {
        Path storageDir = tmp.resolve("storage");

        // session 1: mutate, then store(root.customers()) — the modified collection
        AppRoot root1 = new AppRoot();
        EmbeddedStorageManager m1 = EmbeddedStorage.start(root1, storageDir);
        try
        {
            root1.customers().put("c1", new Customer("c1", "Alice"));
            m1.store(root1.customers());
        }
        finally
        {
            m1.shutdown();
        }

        // session 2: customer must round-trip
        AppRoot root2 = new AppRoot();
        EmbeddedStorageManager m2 = EmbeddedStorage.start(root2, storageDir);
        try
        {
            assertEquals(1, root2.customers().size(),
                "store(root.customers()) must persist collection mutations");
            assertEquals("Alice", root2.customers().get("c1").name());
        }
        finally
        {
            m2.shutdown();
        }
    }
}
