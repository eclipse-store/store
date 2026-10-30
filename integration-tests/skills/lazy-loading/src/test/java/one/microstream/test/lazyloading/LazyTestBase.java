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

import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.LazyReferenceManager;
import org.junit.jupiter.api.BeforeAll;

/**
 * SKILL examples-expanded.md Example 7 — install a long-timeout
 * {@link LazyReferenceManager} so the background daemon does not race
 * test assertions about loaded/cleared state.
 */
abstract class LazyTestBase
{
    @BeforeAll
    static void installNoAutoClearManager()
    {
        LazyReferenceManager.set(LazyReferenceManager.New(
            Lazy.Checker(Long.MAX_VALUE)
        ));
    }
}
