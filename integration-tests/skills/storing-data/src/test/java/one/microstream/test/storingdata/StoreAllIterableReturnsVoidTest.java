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

import java.nio.file.Path;
import java.util.List;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the Core API row: {@code storeAll(Iterable<?>)} returns {@code void}.
 * Compile-time check is sufficient — calling the method in a statement context
 * compiles only if its return type is void.
 */
class StoreAllIterableReturnsVoidTest
{
    @Test
    void storeAll_iterable_returns_void(@TempDir Path tmp)
    {
        final AppRoot root = new AppRoot();
        final EmbeddedStorageManager storage = EmbeddedStorage.start(root, tmp.resolve("db"));
        try
        {
            final Customer a = new Customer("a@acme.com", "A", new Address("S", "C"));
            final Customer b = new Customer("b@acme.com", "B", new Address("S", "C"));
            root.customersById().put(a.email(), a);
            root.customersById().put(b.email(), b);

            final Iterable<Customer> iterable = List.of(a, b);
            // Compile-only assertion: this statement compiles only because the
            // overload taking Iterable<?> returns void. The Object... overload
            // would return long[]; both are valid signatures, so to disambiguate
            // we widen the receiver type to Iterable<?>.
            storage.storeAll(iterable);
        }
        finally
        {
            storage.shutdown();
        }
    }
}
