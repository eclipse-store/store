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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.ArrayList;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import one.microstream.test.lazyloading.ledger.BusinessYear;
import one.microstream.test.lazyloading.ledger.LedgerRoot;
import one.microstream.test.lazyloading.ledger.Turnover;

/**
 * Validates pitfalls-deep-dive #11 Pattern 2: leave the Lazy field null until
 * first use, then assign once via {@code Lazy.Reference(value)} and store the
 * containing object. The containing-object store must pick up the new wrapper
 * and its inner collection so both survive a restart.
 */
class Pitfall11DeferredInitTest extends LazyTestBase
{
    @Test
    void deferredInit_assignsLazyReferenceOnce_andPersistsViaParentStore(@TempDir Path tmp)
    {
        Path dir = tmp.resolve("storage");

        // session 1: create a year whose turnovers field is null, defer
        // initialization, then store the containing BusinessYear (Pattern 2
        // from pitfalls-deep-dive #11).
        LedgerRoot root1 = new LedgerRoot();
        EmbeddedStorageManager m1 = EmbeddedStorage.start(root1, dir);
        BusinessYear y1 = new BusinessYear(2026);
        y1.clearTurnovers();                           // field is null now
        assertNull(y1.turnoversLazy(), "precondition: turnovers field starts null");

        root1.years().put(2026, y1);
        m1.store(root1.years());                       // register new map entry

        ArrayList<Turnover> initial = new ArrayList<>();
        initial.add(new Turnover("label-1", 100));
        initial.add(new Turnover("label-2", 200));
        y1.initializeTurnoversWith(initial);           // first and only assignment

        // Store the CONTAINING object so the now-non-null Lazy field is
        // captured. The fresh Lazy wrapper and its inner ArrayList have no OIDs
        // yet; the cascade through `y1` should persist them as well.
        m1.store(y1);
        m1.shutdown();

        // session 2: reopen, assert the turnovers survived
        LedgerRoot root2 = new LedgerRoot();
        EmbeddedStorageManager m2 = EmbeddedStorage.start(root2, dir);
        try
        {
            BusinessYear y2 = root2.years().get(2026);
            assertNotNull(y2, "year 2026 must round-trip");
            assertNotNull(y2.turnoversLazy(),
                "turnovers Lazy wrapper must round-trip (Pattern 2 must persist via parent store)");

            ArrayList<Turnover> loaded = y2.turnovers();
            assertNotNull(loaded, "inner collection must round-trip");
            assertEquals(2, loaded.size(), "both seed turnovers must persist");
            assertEquals("label-1", loaded.get(0).label());
            assertEquals(100L,      loaded.get(0).amountCents());
            assertEquals("label-2", loaded.get(1).label());
            assertEquals(200L,      loaded.get(1).amountCents());
        }
        finally
        {
            m2.shutdown();
        }
    }
}
