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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import one.microstream.test.lazyloading.ledger.BusinessYear;
import one.microstream.test.lazyloading.ledger.LedgerRoot;
import one.microstream.test.lazyloading.ledger.Turnover;

/**
 * SKILL Pattern A — lazy-wrap a large child collection.
 *
 * <p>Mutate the inner list, store the INNER collection (per the skill's
 * annotation under Pattern A and anti-pattern 3 — never store the Lazy wrapper
 * after a child mutation). Shutdown, reopen, verify the turnover persisted.
 */
class LazyWrapTest extends LazyTestBase
{
    @Test
    void mutating_then_storing_inner_collection_persists_across_restart(@TempDir Path tmp)
    {
        final Path dir = tmp.resolve("storage");

        // session 1: seed
        {
            final LedgerRoot root = new LedgerRoot();
            final BusinessYear y2026 = new BusinessYear(2026);
            root.years().put(2026, y2026);

            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                // storeRoot is needed because we mutated the years map (added an entry).
                storage.store(root.years());

                // SKILL Pattern A: add via inner collection, store the INNER collection.
                y2026.turnovers().add(new Turnover("Q1 invoice", 12_345L));
                storage.store(y2026.turnovers());
            }
            finally
            {
                storage.shutdown();
            }
        }

        // session 2: reopen, verify
        {
            final LedgerRoot root = new LedgerRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                final BusinessYear y2026 = root.years().get(2026);
                assertNotNull(y2026, "year 2026 must be loaded from storage");

                // Accessor triggers the lazy load.
                assertEquals(1, y2026.turnovers().size(),
                    "expected one persisted turnover after restart");
                final Turnover t = y2026.turnovers().get(0);
                assertEquals("Q1 invoice", t.label());
                assertEquals(12_345L, t.amountCents());
            }
            finally
            {
                storage.shutdown();
            }
        }
    }
}
