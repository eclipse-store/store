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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises SKILL Pattern A (remove from a collection) and Pattern C (clear
 * an entire collection). After each mutation the COLLECTION is stored — not
 * the root — because {@code storeRoot()} is shallow (see SKILL.md pitfall #1
 * in storing-data, restated in the housekeeping skill's Pattern A example).
 */
class DeletionTest
{
    @Test
    void patternA_remove_from_collection_persists_across_restart(@TempDir Path tmp)
    {
        Path storageDir = tmp.resolve("storage");

        // session 1: seed three orders
        OrdersRoot root1 = new OrdersRoot();
        EmbeddedStorageManager m1 = OrdersStorage.start(root1, storageDir);
        root1.orders().add(new Order("o-1", "alice", 100));
        root1.orders().add(new Order("o-2", "bob",   200));
        root1.orders().add(new Order("o-3", "carol", 300));
        m1.store(root1.orders());
        m1.shutdown();

        // session 2: remove the middle one; store the COLLECTION
        OrdersRoot root2 = new OrdersRoot();
        EmbeddedStorageManager m2 = OrdersStorage.start(root2, storageDir);
        assertEquals(3, root2.orders().size(), "expected three persisted orders");

        Order toRemove = root2.orders().stream()
            .filter(o -> "o-2".equals(o.id()))
            .findFirst()
            .orElseThrow();
        root2.orders().remove(toRemove);
        m2.store(root2.orders());     // Pattern A: store the parent collection
        m2.shutdown();

        // session 3: reopen and verify the removal is durable
        OrdersRoot root3 = new OrdersRoot();
        EmbeddedStorageManager m3 = OrdersStorage.start(root3, storageDir);
        try
        {
            assertEquals(2, root3.orders().size(),
                "expected two orders after Pattern A removal");
            assertTrue(root3.orders().stream().noneMatch(o -> "o-2".equals(o.id())),
                "removed order must not reappear after restart");
        }
        finally
        {
            m3.shutdown();
        }
    }

    @Test
    void patternC_clear_collection_persists_across_restart(@TempDir Path tmp)
    {
        Path storageDir = tmp.resolve("storage");

        // session 1: seed
        OrdersRoot root1 = new OrdersRoot();
        EmbeddedStorageManager m1 = OrdersStorage.start(root1, storageDir);
        for (int i = 0; i < 5; i++)
        {
            root1.orders().add(new Order("o-" + i, "cust-" + i, 10 * i));
        }
        m1.store(root1.orders());
        m1.shutdown();

        // session 2: clear; store the COLLECTION
        OrdersRoot root2 = new OrdersRoot();
        EmbeddedStorageManager m2 = OrdersStorage.start(root2, storageDir);
        assertEquals(5, root2.orders().size(), "expected five persisted orders");

        root2.orders().clear();
        m2.store(root2.orders());     // Pattern C: store the parent collection
        m2.shutdown();

        // session 3: reopen and verify the list is empty
        OrdersRoot root3 = new OrdersRoot();
        EmbeddedStorageManager m3 = OrdersStorage.start(root3, storageDir);
        try
        {
            assertTrue(root3.orders().isEmpty(),
                "orders list must be empty after Pattern C clear + restart");
        }
        finally
        {
            m3.shutdown();
        }
    }
}
