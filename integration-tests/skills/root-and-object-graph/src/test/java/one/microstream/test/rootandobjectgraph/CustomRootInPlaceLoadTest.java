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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pattern A — custom root class passed to {@code start(root, dir)}.
 *
 * <p>Verifies that the instance passed to {@code start} <em>is</em> the loaded
 * root (pitfall #6): Eclipse Store populates the existing instance in place
 * rather than constructing a new one.
 */
class CustomRootInPlaceLoadTest
{
    @Test
    void customRoot_isPopulatedInPlace_acrossRestart(@TempDir Path tmp)
    {
        Path storageDir = tmp.resolve("storage");

        // session 1: seed
        AppRoot root1 = new AppRoot();
        EmbeddedStorageManager m1 = EmbeddedStorage.start(root1, storageDir);
        try
        {
            // start() returns: root1's fields are now populated (empty, fresh DB)
            assertSame(root1, m1.root(),
                "the instance passed to start() must be the same as storage.root()");

            root1.customers().put("c1", new Customer("c1", "Alice"));
            root1.customers().put("c2", new Customer("c2", "Bob"));
            root1.orders().add(new Order("o1", "c1", 1500L));
            root1.setSettings(new AppSettings("de-DE", 50));

            // Pitfall #4: store the modified collections, not the root.
            m1.store(root1.customers());
            m1.store(root1.orders());
            // settings was reassigned (non-final field) — root reference changed.
            m1.storeRoot();
        }
        finally
        {
            m1.shutdown();
        }

        // session 2: FRESH AppRoot instance — verify it gets loaded in place
        AppRoot root2 = new AppRoot();
        EmbeddedStorageManager m2 = EmbeddedStorage.start(root2, storageDir);
        try
        {
            // Pitfall #6: the passed root IS the loaded root.
            assertSame(root2, m2.root(),
                "the freshly constructed root2 must be the loaded root");

            assertEquals(2, root2.customers().size(),
                "customers must be loaded onto root2's existing map");
            assertNotNull(root2.customers().get("c1"));
            assertEquals("Alice", root2.customers().get("c1").name());
            assertEquals("Bob",   root2.customers().get("c2").name());

            assertEquals(1, root2.orders().size(),
                "orders must be loaded onto root2's existing list");
            Order loadedOrder = root2.orders().get(0);
            assertEquals("o1", loadedOrder.id());
            assertEquals("c1", loadedOrder.customerId());
            assertEquals(1500L, loadedOrder.amountCents());

            assertNotNull(root2.settings());
            assertEquals("de-DE", root2.settings().locale());
            assertEquals(50, root2.settings().pageSize());

            assertTrue(true);
        }
        finally
        {
            m2.shutdown();
        }
    }
}
