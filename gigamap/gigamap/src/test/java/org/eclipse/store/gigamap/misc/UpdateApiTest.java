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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

public class UpdateApiTest
{
    @TempDir
    Path tempDir;


    @Test
    void updateApi_727_Test()
    {
        final int size = 1000;
        final GigaMap<String> gigaMap = GigaMap.New();

        for (int i = 0; i < size; i++)
        {
            gigaMap.add(String.valueOf(i));
        }
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, this.tempDir)) {
            Assertions.assertEquals(size, gigaMap.size());
        }

        final GigaMap<String> gigaMap2 = GigaMap.New();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap2, this.tempDir)) {
            Assertions.assertEquals(size, gigaMap2.size());
            
            gigaMap2.add("another");
            Assertions.assertEquals(size + 1, gigaMap2.size());
        }
    }
}
