package one.microstream.test.lazyloading;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - lazy-loading
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import one.microstream.test.lazyloading.ledger.BusinessYear;
import one.microstream.test.lazyloading.ledger.LedgerRoot;
import one.microstream.test.lazyloading.ledger.Turnover;

/**
 * SKILL Pattern C + pitfall #5 — manual clear and reload semantics.
 *
 * <p>After populating + storing: {@code lazy.clear()} drops the hard ref,
 * {@code isLoaded()} reports false, {@code .get()} reloads from storage and
 * {@code isLoaded()} flips back to true with the original contents.
 */
class LazyClearReloadTest extends LazyTestBase
{
    @Test
    void clear_then_get_reloads_from_storage(@TempDir Path tmp)
    {
        final Path dir = tmp.resolve("storage");

        final LedgerRoot root = new LedgerRoot();
        final BusinessYear y2026 = new BusinessYear(2026);
        root.years().put(2026, y2026);

        final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
        try
        {
            // populate + store
            y2026.turnovers().add(new Turnover("A", 100L));
            y2026.turnovers().add(new Turnover("B", 200L));
            y2026.turnovers().add(new Turnover("C", 300L));
            storage.store(root.years());
            storage.store(y2026.turnovers());

            // freshly stored — should be loaded (in-memory hard ref is the list we wrote)
            assertTrue(y2026.turnoversLazy().isLoaded(),
                "after store, the lazy should still hold its hard reference");

            // SKILL Pattern C: drop the hard ref
            y2026.turnoversLazy().clear();
            assertFalse(y2026.turnoversLazy().isLoaded(),
                "after clear(), isLoaded() must be false");

            // .get() reloads
            final var reloaded = y2026.turnovers();
            assertTrue(y2026.turnoversLazy().isLoaded(),
                "after .get(), isLoaded() must flip back to true");
            assertEquals(3, reloaded.size());
            assertEquals("A", reloaded.get(0).label());
            assertEquals("B", reloaded.get(1).label());
            assertEquals("C", reloaded.get(2).label());
        }
        finally
        {
            storage.shutdown();
        }
    }
}
