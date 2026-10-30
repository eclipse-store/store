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

import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pattern B — manual {@link Storer} for an atomic multi-object transaction.
 * <p>
 * Storer is NOT AutoCloseable — explicit {@code commit()} (no try-with-resources).
 * Three mutations across two collections plus one customer field, staged on one
 * storer and committed atomically.
 */
class PatternBManualStorerAtomicityTest
{
    @Test
    void three_mutations_persist_atomically(@TempDir Path tmp)
    {
        final Path dir = tmp.resolve("db");

        // seed an existing customer so we can mutate a field on it in this run
        {
            final AppRoot root = new AppRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                final Customer bob = new Customer(
                    "bob@acme.com", "Bob", new Address("Old 1", "Linz"));
                root.customersById().put(bob.email(), bob);
                storage.store(root.customersById());
            }
            finally
            {
                storage.shutdown();
            }
        }

        // session 2 — three mutations under one Storer.commit()
        {
            final AppRoot root = new AppRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                // (1) new customer added to the map
                final Customer carol = new Customer(
                    "carol@acme.com", "Carol", new Address("New 2", "Wien"));
                root.customersById().put(carol.email(), carol);

                // (2) new order added to the orders list
                final Order order = new Order("o-1", "carol@acme.com");
                root.orders().add(order);

                // (3) field mutation on existing customer
                final Customer bob = root.customersById().get("bob@acme.com");
                bob.incrementOrderCount();

                // Manual Storer — NOT AutoCloseable, explicit commit() required.
                final Storer storer = storage.createStorer();
                storer.store(root.customersById());
                storer.store(root.orders());
                storer.store(bob);
                storer.commit();
            }
            finally
            {
                storage.shutdown();
            }
        }

        // session 3 — verify all three persisted
        {
            final AppRoot root = new AppRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                assertEquals(2, root.customersById().size());
                final Customer carol = root.customersById().get("carol@acme.com");
                assertNotNull(carol);
                assertEquals("Carol", carol.name());

                assertEquals(1, root.orders().size());
                assertEquals("o-1", root.orders().get(0).id());

                final Customer bob = root.customersById().get("bob@acme.com");
                assertEquals(1, bob.orderCount());
            }
            finally
            {
                storage.shutdown();
            }
        }
    }
}
