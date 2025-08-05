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
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AddTest
{

    @TempDir
    Path newDirectory;


    @Test
    void addItemToStoredGM_updateApi()
    {
        final GigaMap<Customer> gigaMap = GigaMap.New();
        NameIndexer nameIndexer = new NameIndexer();
        gigaMap.index().bitmap().add(nameIndexer);

        gigaMap.add(new Customer("John", 25));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, newDirectory)) {
        }

        GigaMap<Customer> newGigaMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newGigaMap, newDirectory)) {

            newGigaMap.add(new Customer("Jarmila", 30));
            newGigaMap.store();
        }

        GigaMap<Customer> gm2 = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gm2, newDirectory)) {

            assertEquals(2, gm2.size());
            IndexerString<Customer> indexerString = gm2.index().bitmap().getIndexerString(nameIndexer.name());
            List<Customer> j = gm2.query(indexerString.startsWith("J")).toList();
            assertEquals(2, j.size(), "There should be 2 customers with names starting with 'J'");
        }

    }


    @Test
    void addItemStoreTest()
    {
        final GigaMap<Customer> gigaMap = GigaMap.New();
        NameIndexer nameIndexer = new NameIndexer();
        gigaMap.index().bitmap().add(nameIndexer);

        gigaMap.add(new Customer("John", 25));
        gigaMap.add(new Customer("Jane", 30));
        gigaMap.add(new Customer("Doe", 35));
        gigaMap.add(new Customer("Smith", 40));
        gigaMap.add(new Customer("Brown", 45));
        gigaMap.add(new Customer("Doe", 50));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            manager.setRoot(gigaMap);
            manager.storeRoot();
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            final GigaMap<Customer> root = (GigaMap<Customer>) manager.root();
            root.add(new Customer("Updated Name", 100));
            root.store();

            long count = root.query(nameIndexer.startsWith("Updated")).count();
            assertEquals(1, count, "Count of customers with name starting with 'Updated' should be 1");
            assertEquals(7, root.size(), "Size of the map should be 7 after adding a new customer");
        }


        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            final GigaMap<Customer> root2 = (GigaMap<Customer>) manager.root();

            assertEquals(7, root2.size(), "Size of the map should be 7 after adding a new customer");
            assertEquals("Updated Name", root2.query(nameIndexer.is("Updated Name")).findFirst().get().getName());

            long count = root2.query(nameIndexer.startsWith("Updated")).count();
            assertEquals(1, count, "Count of customers with name starting with 'Updated' should be 1");
            root2.add(new Customer("Updated Name second", 110));
            root2.store();
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            final GigaMap<Customer> root2 = (GigaMap<Customer>) manager.root();

            assertEquals(8, root2.size(), "Size of the map should be 7 after adding a new customer");
            assertEquals("Updated Name second", root2.query(nameIndexer.is("Updated Name second")).findFirst().get().getName());

            long count = root2.query(nameIndexer.startsWith("Updated")).count();
            assertEquals(2, count, "Count of customers with name starting with 'Updated' should be 1");
        }

    }

    @Test
    void updateApiReplaceAfter_updateApiTest()
    {
        final GigaMap<Customer> gigaMap = GigaMap.New();
        NameIndexer nameIndexer = new NameIndexer();
        gigaMap.index().bitmap().add(nameIndexer);

        gigaMap.add(new Customer("John", 25));
        gigaMap.add(new Customer("Jane", 30));
        gigaMap.add(new Customer("Doe", 35));
        gigaMap.add(new Customer("Smith", 40));
        gigaMap.add(new Customer("Brown", 45));
        gigaMap.add(new Customer("Doe", 50));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, newDirectory)) {
        }

        GigaMap<Customer> gm2 = GigaMap.New();
        AgeIndexer ageIndexer = new AgeIndexer();
        gm2.index().bitmap().add(ageIndexer);
        gm2.add(new Customer("Updated Name", 100));
        gm2.add(new Customer("Updated Name", 110));
        gm2.add(new Customer("Updated Name", 120));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gm2, newDirectory)) {

            Assertions.assertThrows(RuntimeException.class, () -> gm2.query(ageIndexer.is(100)).toList(),
                    "After reloading, the index should not be available");
            gm2.index().bitmap().add(ageIndexer);
            assertTrue(gm2.query(ageIndexer.is(100)).toList().isEmpty());

            assertEquals(gm2.size() , gigaMap.size(), "Size of the map should be equal to the size of the giga map");
        }

    }

    static class NameIndexer extends IndexerString.Abstract<Customer>
    {
        @Override
        protected String getString(Customer entity)
        {
            return entity.getName();
        }
    }

    static class AgeIndexer extends IndexerInteger.Abstract<Customer>
    {
        @Override
        protected Integer getInteger(Customer entity)
        {
            return entity.getAge();
        }
    }

    public static class Customer
    {
        private final String name;
        private final int age;

        public Customer(String name, int age)
        {
            this.name = name;
            this.age = age;
        }

        public String getName()
        {
            return name;
        }

        public int getAge()
        {
            return age;
        }

        @Override
        public String toString()
        {
            return "Customer{" +
                    "name='" + name + '\'' +
                    ", age=" + age +
                    '}';
        }
    }
}
