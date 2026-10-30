package one.microstream.test.storingdata;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - storing-data
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

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pattern A — "store the modified collection".
 * <p>
 * The map is what changed when a new customer is added — storing
 * {@code root.customersById()} is sufficient; the lazy walk picks up
 * the new customer and its address as not-yet-registered children.
 */
class PatternACollectionStoreTest
{
    @Test
    void addedCustomer_persists_when_collection_is_stored(@TempDir Path tmp)
    {
        final Path dir = tmp.resolve("db");

        // session 1 — seed
        {
            final AppRoot root = new AppRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                final Customer alice = new Customer(
                    "alice@acme.com", "Alice", new Address("Main 1", "Linz"));
                root.customersById().put(alice.email(), alice);
                // Pattern A: store the COLLECTION when an entry was added.
                storage.store(root.customersById());
            }
            finally
            {
                storage.shutdown();
            }
        }

        // session 2 — reopen and verify
        {
            final AppRoot root = new AppRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                assertEquals(1, root.customersById().size());
                final Customer alice = root.customersById().get("alice@acme.com");
                assertNotNull(alice);
                assertEquals("Alice", alice.name());
                assertEquals("Main 1", alice.address().street());
                assertEquals("Linz",   alice.address().city());
            }
            finally
            {
                storage.shutdown();
            }
        }
    }
}
