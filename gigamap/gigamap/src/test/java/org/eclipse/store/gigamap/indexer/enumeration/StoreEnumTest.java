package org.eclipse.store.gigamap.indexer.enumeration;

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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StoreEnumTest
{

    @TempDir
    Path workDir;

    @Test
    @Disabled("https://github.com/microstream-one/gigamap/issues/71")
    void enumTest()
    {
        GigaMap<StoreEnum> gigaMap = GigaMap.New();
        gigaMap.add(StoreEnum.VALUE);
        gigaMap.add(StoreEnum.SECOND);
        gigaMap.add(StoreEnum.THIRD);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, workDir)) {

        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(workDir)) {
            GigaMap<StoreEnum> loadedGigaMap = (GigaMap<StoreEnum>) manager.root();

            loadedGigaMap.forEach(System.out::println);
        }
    }

    @Test
    void enumArrayListTest() {
        List<StoreEnum> enumList = new ArrayList<>();
        enumList.add(StoreEnum.VALUE);
        enumList.add(StoreEnum.SECOND);
        enumList.add(StoreEnum.THIRD);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(enumList, workDir)) {

        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(workDir)) {
            List<StoreEnum> loadedEnumList = (List<StoreEnum>) manager.root();

            loadedEnumList.forEach(System.out::println);
        }
    }


}
