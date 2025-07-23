package org.eclipse.store.gigamap.crud;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GigaMapCombiTest
{

    @TempDir
    Path tempDir;

    @Test
    void combinedTest()
    {
        GigaMap<GigaMap<String>> gigaMap = GigaMap.New();

        // Add multiple GigaMaps to the outer GigaMap
        for (int i = 0; i < 5; i++) {
            GigaMap<String> subMap = GigaMap.New();
            for (int j = 0; j < 5; j++) {
                subMap.add("Hello" + i + j);
            }
            gigaMap.add(subMap);
        }

        // Store the GigaMap
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, tempDir)) {
            gigaMap.store();
        }

        // Load the GigaMap from the storage
        GigaMap<GigaMap<String>> loadedGigaMap;
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            loadedGigaMap = (GigaMap<GigaMap<String>>) storageManager.root();
            assertEquals(gigaMap.size(), loadedGigaMap.size());
            // Verify that the loaded GigaMap is identical to the original one
            for (int i = 0; i < gigaMap.size(); i++) {
                assertEquals(gigaMap.get(i).size(), loadedGigaMap.get(i).size());
                for (int j = 0; j < gigaMap.get(i).size(); j++) {
                    assertEquals(gigaMap.get(i).get(j), loadedGigaMap.get(i).get(j));
                }
            }
        }

    }


}
