package org.eclipse.store.gigamap.process;

/*-
 * #%L
 * EclipseStore GigaMap
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

import org.eclipse.serializer.util.X;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * XIterable contract: iterate() absorbs X.BREAK() as a controlled early exit.
 * GigaMap honors it, and GigaQuery (which extends XIterable) must too instead of
 * letting the raw ThrowBreak escape to the caller.
 */
public class BreakContractTest
{
    private static GigaMap<Long> newMap()
    {
        final GigaMap<Long> map = GigaMap.New();
        for (long i = 0; i < 10; i++)
        {
            map.add(i);
        }
        return map;
    }

    @Test
    void mapForEachAbsorbsBreak()
    {
        assertDoesNotThrow(() -> newMap().forEach(e -> { throw X.BREAK(); }));
    }

    @Test
    void queryIterateAbsorbsBreak()
    {
        assertDoesNotThrow(() -> newMap().query().iterate(e -> { throw X.BREAK(); }));
    }

    @Test
    void queryForEachAbsorbsBreak()
    {
        assertDoesNotThrow(() -> newMap().query().forEach(e -> { throw X.BREAK(); }));
    }

    @Test
    void queryIterateIndexedAbsorbsBreak()
    {
        assertDoesNotThrow(() -> newMap().query().iterateIndexed((id, e) -> { throw X.BREAK(); }));
    }
}
