package org.eclipse.store.gigamap.indexer.edge;

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
import java.util.HashMap;
import java.util.Map;

public class InteratorIndexIdTest
{

    @TempDir
    Path tempDir;


    @Test
    void indexerIndexIdTest()
    {
        GigaMap<Guitar> guitars = GigaMap.New();
        GuitarBrandIndexer brandIndexer = new GuitarBrandIndexer();
        guitars.index().bitmap().add(brandIndexer);

        guitars.add(new Guitar("Fender", "Stratocaster", 1960));
        guitars.add(new Guitar("Gibson", "Les Paul", 1952));
        guitars.add(new Guitar("Gibson", "SG", 1961));

        Map<Long, Guitar> controlMap = new HashMap<>();
        guitars.query(brandIndexer.is("Gibson")).iterateIndexed(controlMap::put);

        Assertions.assertAll(
                () -> Assertions.assertEquals(2, controlMap.size()),
                () -> Assertions.assertEquals("Gibson", controlMap.get(1L).getBrand()),
                () -> Assertions.assertEquals("Les Paul", controlMap.get(1L).getModel()),
                () -> Assertions.assertEquals(1952, controlMap.get(1L).getYear()),
                () -> Assertions.assertEquals("Gibson", controlMap.get(2L).getBrand()),
                () -> Assertions.assertEquals("SG", controlMap.get(2L).getModel()),
                () -> Assertions.assertEquals(1961, controlMap.get(2L).getYear())
        );
        guitars.query(brandIndexer.is("Fender")).iterator().nextIndexed(controlMap::put);

        Assertions.assertAll(
                () -> Assertions.assertEquals(3, controlMap.size()),
                () -> Assertions.assertEquals("Fender", controlMap.get(0L).getBrand()),
                () -> Assertions.assertEquals("Stratocaster", controlMap.get(0L).getModel()),
                () -> Assertions.assertEquals(1960, controlMap.get(0L).getYear())
        );

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(guitars, tempDir) ) {

        }

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(guitars, tempDir) ) {
            GigaMap<Guitar> loadedGuitars = (GigaMap<Guitar>) storage.root();
            Map<Long, Guitar> loadedControlMap = new HashMap<>();
            loadedGuitars.query(brandIndexer.is("Gibson")).iterateIndexed(loadedControlMap::put);

            Assertions.assertAll(
                    () -> Assertions.assertEquals(2, loadedControlMap.size()),
                    () -> Assertions.assertEquals("Gibson", loadedControlMap.get(1L).getBrand()),
                    () -> Assertions.assertEquals("Les Paul", loadedControlMap.get(1L).getModel()),
                    () -> Assertions.assertEquals(1952, loadedControlMap.get(1L).getYear()),
                    () -> Assertions.assertEquals("Gibson", loadedControlMap.get(2L).getBrand()),
                    () -> Assertions.assertEquals("SG", loadedControlMap.get(2L).getModel()),
                    () -> Assertions.assertEquals(1961, loadedControlMap.get(2L).getYear())
            );
        }

    }

    private static class GuitarBrandIndexer extends IndexerString.Abstract<Guitar>
    {

        @Override
        protected String getString(Guitar entity)
        {
            return entity.getBrand();
        }
    }

    private static class Guitar
    {
        private String brand;
        private String model;
        private int year;

        public Guitar(String brand, String model, int year)
        {
            this.brand = brand;
            this.model = model;
            this.year = year;
        }

        public String getBrand()
        {
            return brand;
        }

        public String getModel()
        {
            return model;
        }

        public int getYear()
        {
            return year;
        }

        @Override
        public String toString()
        {
            return "Guitar{" +
                    "brand='" + brand + '\'' +
                    ", model='" + model + '\'' +
                    ", year=" + year +
                    '}';
        }
    }
}
