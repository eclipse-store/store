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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pattern D / Anti-pattern 3 — the lazy walk and "the modified object must be stored".
 * <p>
 * Two scenarios:
 * <ol>
 *   <li>Replacing the Address reference on a persisted Customer with a NEW Address
 *       and storing the customer. The customer is the explicit argument (always
 *       re-written); the new Address is not yet registered, so the lazy walk
 *       descends and writes it. This succeeds.</li>
 *   <li>Mutating a field on a persisted Customer (the email) and then storing only
 *       the enclosing map. The map is re-written (no entries added/removed, but
 *       the explicit argument is always re-serialized), but the lazy walk hits
 *       the already-registered Customer and stops — the email change is invisible
 *       to the walk. The fix would have been {@code storage.store(customer)}.</li>
 * </ol>
 */
class PatternDEagerStorerInPlaceMutationTest
{
    @Test
    void replacing_address_with_new_record_persists_via_lazy_store(@TempDir Path tmp)
    {
        final Path dir = tmp.resolve("db");

        // seed
        {
            final AppRoot root = new AppRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                final Customer c = new Customer(
                    "dave@acme.com", "Dave", new Address("Old 1", "Linz"));
                root.customersById().put(c.email(), c);
                storage.store(root.customersById());
            }
            finally
            {
                storage.shutdown();
            }
        }

        // mutate via setter to a NEW Address; store the customer (lazy)
        {
            final AppRoot root = new AppRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                final Customer c = root.customersById().get("dave@acme.com");
                c.setAddress(new Address("New 99", "Wien"));
                // Lazy storer: customer is the explicit argument (always re-written)
                // and root.customer.address now points to a NEW Address object
                // which the walk has not seen yet — so it gets serialized.
                storage.store(c);
            }
            finally
            {
                storage.shutdown();
            }
        }

        // verify
        {
            final AppRoot root = new AppRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                final Customer c = root.customersById().get("dave@acme.com");
                assertEquals("New 99", c.address().street());
                assertEquals("Wien",   c.address().city());
            }
            finally
            {
                storage.shutdown();
            }
        }
    }

    @Test
    void mutating_customer_then_storing_only_the_map_loses_the_change(@TempDir Path tmp)
    {
        final Path dir = tmp.resolve("db");

        // seed
        {
            final AppRoot root = new AppRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                final Customer c = new Customer(
                    "eve@acme.com", "Eve", new Address("Park 1", "Linz"));
                root.customersById().put(c.email(), c);
                storage.store(root.customersById());
            }
            finally
            {
                storage.shutdown();
            }
        }

        // session 2: mutate a field on the persisted Customer, then store ONLY the map.
        // The lazy walk into an already-registered customer skips its fields — so the
        // email-rewrite is silently dropped.
        {
            final AppRoot root = new AppRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                final Customer c = root.customersById().get("eve@acme.com");
                // Mutate the customer's name in place (immutable String reassignment
                // on a mutable field of the already-persisted Customer).
                c.setEmail("eve@acme.com"); // unchanged — keep map key stable
                c.incrementOrderCount();    // <-- the in-place mutation we care about
                // WRONG-ish: only store the map. The map walk hits the already-
                // registered Customer and stops; the orderCount change is invisible.
                storage.store(root.customersById());
            }
            finally
            {
                storage.shutdown();
            }
        }

        // verify the change did NOT persist (Anti-pattern 3 / Pitfall 2 demonstration)
        {
            final AppRoot root = new AppRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                final Customer c = root.customersById().get("eve@acme.com");
                // skill claim: lazy walk skips an already-registered customer's fields,
                // so the incremented orderCount was NOT persisted — still 0.
                assertNotEquals(1, c.orderCount(),
                    "lazy walk into the already-registered Customer should have skipped "
                  + "its fields; orderCount mutation should NOT have persisted");
                assertEquals(0, c.orderCount());
            }
            finally
            {
                storage.shutdown();
            }
        }
    }
}
