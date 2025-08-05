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

import java.nio.file.Path;
import java.util.List;

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class StoreEnumInsideTest
{

    @TempDir
    Path tempDir;

    @Test
    @Disabled("https://github.com/eclipse-store/store/issues/448")
    void storeEnumInsideTest()
    {
        Person person = new Person("John", 30, StoreEnum.VALUE);
        Person person2 = new Person("John", 30, StoreEnum.SECOND);
        Person person3 = new Person("John", 30, StoreEnum.THIRD);

        GigaMap<Person> gigaMap = GigaMap.New();
        gigaMap.add(person);
        gigaMap.add(person2);
        gigaMap.add(person3);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(gigaMap, tempDir)) {
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            GigaMap<Person> loadedGigaMap = (GigaMap<Person>) manager.root();

            //TODO check after fix
            List<Person> list = loadedGigaMap.query().toList();
            Assertions.assertEquals(3, list.size(), "There should be 3 persons in the GigaMap");
        }

    }

    private static class Person
    {
        private String name;
        private int age;
        private Address address;

        public Person(String name, int age, StoreEnum address)
        {
            this.name = name;
            this.age = age;
            this.address = new Address(address);
        }

        public String getName()
        {
            return name;
        }

        public int getAge()
        {
            return age;
        }

        public Address getAddress()
        {
            return address;
        }

    }

    private static class Address
    {
        StoreEnum address;

        public Address(StoreEnum address)
        {
            this.address = address;
        }
    }
}
