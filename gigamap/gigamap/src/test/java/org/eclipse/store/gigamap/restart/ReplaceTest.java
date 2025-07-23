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
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReplaceTest
{

    @TempDir
    Path newDirectory;

    @Test
    void replaceItemStoreTest()
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

        Customer newCustomer = new Customer("Updated Name", 100);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            final GigaMap<Customer> root = (GigaMap<Customer>) manager.root();
            Optional<Customer> first = root.query().findFirst();
            if (first.isPresent()) {
                Customer customer = first.get();
                root.replace(customer, newCustomer);
                root.store();
            }

            long count = root.query(nameIndexer.startsWith("Updated")).count();
            assertEquals(1, count);
            Customer updated = root.query(nameIndexer.startsWith("Updated")).findFirst().get();
            assertEquals(newCustomer, updated);

            long count1 = root.query(nameIndexer.startsWith("John")).count();
            assertEquals(0, count1, "Count of customers with name starting with 'John' should be 0");

        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            final GigaMap<Customer> root2 = (GigaMap<Customer>) manager.root();

            long count = root2.query(nameIndexer.startsWith("Upda")).count();
            assertEquals(1, count, "Count of customers with name starting with 'Upda' should be 1");
            Customer updated = root2.query(nameIndexer.startsWith("Updated")).findFirst().get();
            assertEquals(newCustomer, updated);

            long count1 = root2.query(nameIndexer.startsWith("John")).count();
            assertEquals(0, count1, "Count of customers with name starting with 'John' should be 0");

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

        @Override
        public String toString()
        {
            return "Customer{" +
                    "name='" + name + '\'' +
                    ", age=" + age +
                    '}';
        }

        @Override
        public boolean equals(Object o)
        {
            if (o == null || getClass() != o.getClass()) return false;
            Customer customer = (Customer) o;
            return age == customer.age && Objects.equals(name, customer.name);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(name, age);
        }
    }


}
