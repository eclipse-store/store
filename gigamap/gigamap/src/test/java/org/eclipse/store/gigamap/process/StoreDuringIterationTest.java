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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class StoreDuringIterationTest
{
    @TempDir
    Path tempDir;

    @Test
    void storeDuringIterationIsAllowed()
    {
        final GigaMap<String> gigaMap = GigaMap.New();
        gigaMap.add("entity");

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(gigaMap, this.tempDir))
        {
            // iterate()/forEach() hold the map read-only, so structural mutation (add/remove/update)
            // throws. store() is NOT a structural modification — it persists the graph without changing
            // the entity structure — so it is allowed during iteration and must not throw.
            assertDoesNotThrow(() -> gigaMap.forEach(e -> gigaMap.store()));
        }
    }
}
