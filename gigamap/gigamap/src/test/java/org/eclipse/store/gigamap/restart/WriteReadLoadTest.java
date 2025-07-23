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

import org.eclipse.store.gigamap.types.BitmapIndex;
import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class WriteReadLoadTest
{

    final static int AMOUNT = 1_000;

    @TempDir
    static Path newDirectory;


    @BeforeAll
    static void writeTest()
    {

        GigaMap<String> gigaMap = GigaMap.New();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            if (manager.root() == null) {
                manager.setRoot(gigaMap);
                manager.storeRoot();
            } else {
                gigaMap = (GigaMap<String>) manager.root();
            }

            for (int i = 0; i < AMOUNT; i++) {
                gigaMap.add("Hello" + i);
            }

            for (int i = 0; i < AMOUNT; i++) {
                gigaMap.add("ahoj" + i);
            }

            for (int i = 0; i < AMOUNT; i++) {
                gigaMap.add("servus" + i);
            }
            gigaMap.store();

        }
    }

    @Test
    @Order(1)
    void readFromRepositoryTest()
    {
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            final GigaMap<String> gigaMap = (GigaMap<String>) manager.root();
            assertAll(
                    () -> assertEquals(AMOUNT * 3, gigaMap.size()),
                    () -> assertEquals("Hello0", gigaMap.get(0)),
                    () -> assertEquals("ahoj0", gigaMap.get(1000)),
                    () -> assertEquals("servus0", gigaMap.get(2000))
            );
        }
    }

    @Test
    @Order(2)
    void addIndex()
    {

        GigaMap<String> gigaMap = GigaMap.New();

        final StringIndexer stringIndexer = new StringIndexer();


        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            if (manager.root() == null) {
                manager.setRoot(gigaMap);
            } else {
                gigaMap = (GigaMap<String>) manager.root();
            }

            final BitmapIndices<String> register = gigaMap.index().bitmap();
            final BitmapIndex<String, String> stringIndexer1 = register.get("StringIndexer");
            if (stringIndexer1 == null) {
                register.add(stringIndexer);
            }
            gigaMap.store();
        }
    }

    static class StringIndexer extends IndexerString.Abstract<String>
    {
        @Override
        protected String getString(final String entity)
        {
            return entity;
        }
    }

}
