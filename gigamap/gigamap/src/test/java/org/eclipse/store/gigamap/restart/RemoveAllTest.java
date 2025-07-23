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
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RemoveAllTest
{

    @TempDir
    Path newDirectory;

    @Test
    void updateStoreTest()
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

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            final GigaMap<Customer> root = (GigaMap<Customer>) manager.root();
            root.removeAll();
            root.store();
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            final GigaMap<Customer> root = (GigaMap<Customer>) manager.root();
            assertEquals(0, root.size(), "Size of the map should be 0 after removing all elements");

            long count = root.query(nameIndexer.startsWith("John")).count();
            assertEquals(0, count, "Count of customers with name starting with 'John' should be 0");
        }
    }

    @Test
    void updateStoreUpdateApiTest()
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

        final GigaMap<Customer> root = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(root, newDirectory)) {
            root.removeAll();
            manager.store(root);
        }

        final GigaMap<Customer> loaded = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(loaded, newDirectory)) {
            assertEquals(0, loaded.size(), "Size of the map should be 0 after removing all elements");

            long count = loaded.query(nameIndexer.startsWith("John")).count();
            assertEquals(0, count, "Count of customers with name starting with 'John' should be 0");
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

    public static class Customer {
        private String name;
        private int age;

        public Customer(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }
}
