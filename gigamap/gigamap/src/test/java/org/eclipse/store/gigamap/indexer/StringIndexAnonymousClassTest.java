package org.eclipse.store.gigamap.indexer;

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
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

public class StringIndexAnonymousClassTest
{

    @TempDir
    Path tempDir;

    @Test
    void addTwoAnonymousIndexes()
    {
        GigaMap<LocalDateIndexData> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(nameIndexer);
        gigaMap.index().bitmap().add(secondNameIndexer);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            Root root = new Root();
            root.setMap(gigaMap);
            addDummyDataGigaMap(gigaMap);
            manager.setRoot(root);
            manager.storeRoot();

            Assertions.assertEquals(1, root.getMap().query(nameIndexer.is("name1")).count());
        }

    }


    public void addDummyDataGigaMap(GigaMap<LocalDateIndexData> gigaMap)
    {
        gigaMap.add(new LocalDateIndexData("name1", "secondName1", LocalDate.of(2019, 1, 1)));
    }


    public final static IndexerString<LocalDateIndexData> nameIndexer = new IndexerString.Abstract<>()
    {

        @Override
        protected String getString(LocalDateIndexData entity)
        {
            return entity.name;
        }
    };

    public final static IndexerString<LocalDateIndexData> secondNameIndexer = new IndexerString.Abstract<>()
    {


        @Override
        protected String getString(LocalDateIndexData entity)
        {
            return entity.secondName;
        }
    };

    static class Root
    {
        GigaMap<LocalDateIndexData> map;

        public GigaMap<LocalDateIndexData> getMap()
        {
            return map;
        }

        public void setMap(GigaMap<LocalDateIndexData> map)
        {
            this.map = map;
        }
    }

    static class LocalDateIndexData
    {
        private final String name;
        private final String secondName;
        private final LocalDate date;

        public LocalDateIndexData(String name, String secondName, LocalDate date)
        {
            this.name = name;
            this.secondName = secondName;
            this.date = date;
        }
    }
}

