package one.microstream.test.lazyloading.ledger;

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

import java.util.ArrayList;

import org.eclipse.serializer.reference.Lazy;

/**
 * Per-skill Pattern A: the per-year turnover list is lazy.
 * Field type is {@code Lazy<ArrayList<Turnover>>} (concrete class, see pitfall #4).
 */
public class BusinessYear
{
    private final int                          year;
    private       Lazy<ArrayList<Turnover>>    turnovers;

    public BusinessYear()
    {
        // for storage
        this.year = 0;
    }

    public BusinessYear(final int year)
    {
        this.year      = year;
        this.turnovers = Lazy.Reference(new ArrayList<>());
    }

    public int year()
    {
        return this.year;
    }

    /**
     * Pattern B (null-safe accessor): {@code Lazy.get(null)} returns null,
     * {@code Lazy.get(lazy)} returns the value.
     */
    public ArrayList<Turnover> turnovers()
    {
        return Lazy.get(this.turnovers);
    }

    /**
     * Expose the Lazy wrapper itself, for {@code clear()} / {@code isLoaded()} assertions.
     * SKILL Pattern C / F.
     */
    public Lazy<ArrayList<Turnover>> turnoversLazy()
    {
        return this.turnovers;
    }

    public void clearTurnovers()
    {
        this.turnovers = null;
    }

    /**
     * Deferred-init path from pitfalls-deep-dive #11 (Pattern 2): assign a
     * fresh {@code Lazy.Reference} into a previously-null field. The reproducer
     * checks that storing the containing year picks up the new wrapper.
     */
    public void initializeTurnoversWith(final ArrayList<Turnover> initial)
    {
        if (this.turnovers != null)
        {
            throw new IllegalStateException("turnovers already initialized");
        }
        this.turnovers = Lazy.Reference(initial);
    }
}
