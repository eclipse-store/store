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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;

import org.eclipse.serializer.reference.Lazy;
import org.junit.jupiter.api.Test;

import one.microstream.test.lazyloading.ledger.Turnover;

/**
 * SKILL Pattern B — static null-safe accessor {@code Lazy.get(Lazy<T>)}.
 *
 * <p>{@code Lazy.get(null)} returns null without NPE; {@code Lazy.get(lazy)}
 * returns the underlying value (same instance for an in-memory wrapper).
 */
class NullSafeAccessorTest extends LazyTestBase
{
    @Test
    void lazy_get_on_null_returns_null()
    {
        final Lazy<ArrayList<Turnover>> nullWrapper = null;
        assertNull(Lazy.get(nullWrapper),
            "Lazy.get(null) must return null without throwing NPE");
    }

    @Test
    void lazy_get_on_wrapped_value_returns_that_value()
    {
        final ArrayList<Turnover> list = new ArrayList<>();
        list.add(new Turnover("one", 1L));

        final Lazy<ArrayList<Turnover>> wrapped = Lazy.Reference(list);

        final ArrayList<Turnover> via = Lazy.get(wrapped);
        assertSame(list, via,
            "Lazy.get(lazy) must return the wrapped reference");
        assertEquals(1, via.size());
        assertEquals("one", via.get(0).label());
    }

    @Test
    void lazy_reference_can_wrap_null()
    {
        // SKILL: "Initialize with Lazy.Reference(...) (even Lazy.Reference(null) is fine —
        // the outer Lazy is still stored)."
        final Lazy<ArrayList<Turnover>> wrappingNull = Lazy.Reference(null);
        assertNull(Lazy.get(wrappingNull),
            "Lazy.get(Lazy.Reference(null)) must return null");
    }
}
