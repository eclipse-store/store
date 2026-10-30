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

import java.util.HashMap;

/**
 * Top-level root with a small map of business years.
 * The years map loads eagerly at startup; each year's turnover list is lazy.
 */
public class LedgerRoot
{
    private final HashMap<Integer, BusinessYear> years = new HashMap<>();

    public HashMap<Integer, BusinessYear> years()
    {
        return this.years;
    }
}
