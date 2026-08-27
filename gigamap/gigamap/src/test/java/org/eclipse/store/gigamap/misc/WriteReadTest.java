package org.eclipse.store.gigamap.misc;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class WriteReadTest
{

    @TempDir
    static Path newDirectory;

    @BeforeAll
    static void writeTest()
    {
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            GigaMap<String> gigaMap = manager.ensureRoot(GigaMap::New);
            gigaMap.add("Hello");
            gigaMap.add("ahoj");
            gigaMap.add("servus");
            gigaMap.store();

        }
    }

    @Test
    void readAndCheckValuesTest()
    {
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            final GigaMap<String> gigaMap = manager.root();
            assertAll(
                    () -> assertEquals(3, gigaMap.size()),
                    () -> assertEquals("Hello", gigaMap.get(0)),
                    () -> assertEquals("ahoj", gigaMap.get(1)),
                    () -> assertEquals("servus", gigaMap.get(2))
            );
        }
    }
}
