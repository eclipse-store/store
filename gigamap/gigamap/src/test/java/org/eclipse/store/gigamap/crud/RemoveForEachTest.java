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
import org.eclipse.store.gigamap.types.IndexerLong;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RemoveForEachTest
{

    @TempDir
    Path location;

    @Test
    @Disabled("https://github.com/microstream-one/gigamap/issues/99")
    void remove_stored_test()
    {
        LongIndexer indexer = new LongIndexer();
        GigaMap<Long> map = GigaMap.<Long>Builder().withBitmapIndex(indexer).build();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, location)) {
            List<Long> longList = generateLongList(100);
            map.addAll(longList);
            map.store();
            map.forEach(map::remove); //<--
            longList = generateLongList(100);
            map.addAll(longList);
            map.store();


        }
    }

    private List<Long> generateLongList(int size)
    {
        List<Long> longList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            longList.add((long) i);
        }
        return longList;
    }

    private static class LongIndexer extends IndexerLong.Abstract<Long> {

        @Override
        protected Long getLong(Long entity)
        {
            return entity;
        }
    }
}
