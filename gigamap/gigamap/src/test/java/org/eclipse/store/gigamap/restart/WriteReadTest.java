
package org.eclipse.store.gigamap.restart;

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
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class WriteReadTest
{
    @TempDir
    static Path tempDir;

    @SuppressWarnings("unchecked")
    @Test
    @Order(1)
    void writeTest()
    {
        GigaMap<String> gigaMap = GigaMap.New();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            if (manager.root() == null) {
                manager.setRoot(gigaMap);
                manager.storeRoot();
            } else {
                gigaMap = (GigaMap<String>) manager.root();
            }

            gigaMap.add("Hello");
            gigaMap.store();

            gigaMap.add("ahoj");
            gigaMap.store();

            gigaMap.add("servus");
            gigaMap.store();

            gigaMap.addAll("a", "b", "c");
            gigaMap.store();

        }

    }

    @SuppressWarnings("unchecked")
    @Test
    @Order(2)
    void name()
    {
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            final GigaMap<String> gigaMap = (GigaMap<String>) manager.root();
            assertAll(
                    () -> assertEquals("Hello", gigaMap.get(0)),
                    () -> assertEquals("ahoj", gigaMap.get(1)),
                    () -> assertEquals("servus", gigaMap.get(2)),
                    () -> assertEquals("a", gigaMap.get(3)),
                    () -> assertEquals("b", gigaMap.get(4)),
                    () -> assertEquals("c", gigaMap.get(5)),
                    () -> assertEquals(6, gigaMap.size()
            ));
        }
    }

}
