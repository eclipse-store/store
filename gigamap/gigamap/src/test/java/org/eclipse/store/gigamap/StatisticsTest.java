package org.eclipse.store.gigamap;

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

import org.eclipse.serializer.chars.VarString;
import org.eclipse.store.gigamap.types.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StatisticsTest
{

    @Test
    void statisticsTest()
    {
        GigaMap<Car>    gigaMap      = GigaMap.New();
        CarBrandIndexer brandIndexer = new CarBrandIndexer();
        CarModelIndexer modelIndexer = new CarModelIndexer();
        CarYearIndexer yearIndexer = new CarYearIndexer();
        gigaMap.index().bitmap().addAll(brandIndexer, modelIndexer, yearIndexer);
        gigaMap.add(new Car("BMW", "X5", 2020));
        gigaMap.add(new Car("BMW", "X3", 2020));
        gigaMap.add(new Car("Audi", "A4", 2020));
        gigaMap.add(new Car("Audi", "A3", 2020));
        gigaMap.add(new Car("Audi", "A3", 2021));
        gigaMap.add(new Car("Audi", "A3", 2022));
        gigaMap.add(new Car("Audi", "A3", 2023));

        BitmapIndices<Car>                  bitmap     = gigaMap.index().bitmap();
        DefaultStatistics.IndicesStats<Car> statistics = DefaultStatistics.createStatistics(bitmap);
        assertNotNull(statistics);

        VarString string = VarString.New();
        statistics.assemble(string);
        assertTrue(string.length() > 100);

    }


    private static class CarBrandIndexer extends IndexerString.Abstract<Car>
    {

        @Override
        protected String getString(Car entity)
        {
            return entity.getBrand();
        }
    }

    private static class CarModelIndexer extends IndexerString.Abstract<Car>
    {

        @Override
        protected String getString(Car entity)
        {
            return entity.getModel();
        }
    }

    private static class CarYearIndexer extends IndexerInteger.Abstract<Car>
    {


        @Override
        protected Integer getInteger(Car entity)
        {
            return entity.getYear();
        }
    }


    private static class Car {
        private String brand;
        private String model;
        private int year;

        public Car(String brand, String model, int year) {
            this.brand = brand;
            this.model = model;
            this.year = year;
        }

        public String getBrand() {
            return brand;
        }

        public String getModel() {
            return model;
        }

        public int getYear() {
            return year;
        }
    }
}
