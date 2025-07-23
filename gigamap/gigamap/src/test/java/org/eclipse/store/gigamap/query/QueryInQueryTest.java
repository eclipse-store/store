package org.eclipse.store.gigamap.query;

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
import org.eclipse.store.gigamap.types.GigaQuery;
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

public class QueryInQueryTest
{
    @TempDir
    Path workDir;


    @Test
    void queryInQueryTest()
    {
        GigaMap<Address> addressGigaMap = GigaMap.New();
        AddressIndexer addressIndexer = new AddressIndexer();
        addressGigaMap.index().bitmap().add(addressIndexer);
        Address address = new Address("street", "city");
        Address address1 = new Address("street1", "city1");
        Address address2 = new Address("street2", "city2");
        addressGigaMap.addAll(address, address1, address2);

        GigaMap<Person> personGigaMap = GigaMap.New();
        PersonAddressIndexer personStreetIndexer = new PersonAddressIndexer();
        personGigaMap.index().bitmap().add(personStreetIndexer);
        Person person = new Person("Joe", address);
        Person person1 = new Person("Karl", address1);
        Person person2 = new Person("John", address2);
        personGigaMap.addAll(person, person1, person2);

        GigaQuery<Person> street = personGigaMap.query(personStreetIndexer.in(addressGigaMap.query(addressIndexer.is("street"))));
        Assertions.assertEquals(1, street.count());

        Person person3 = street.findFirst().get();
        Assertions.assertEquals("Joe", person3.getName());

        //save it
        Root root = new Root(addressGigaMap, personGigaMap);
        try (EmbeddedStorageManager storage = EmbeddedStorage.start(root, workDir)) {
            GigaMap<Address> addressGigaMap1 = root.getAddressGigaMap();
            GigaMap<Person> personGigaMap1 = root.getPersonGigaMap();
            GigaQuery<Person> street1 = personGigaMap1.query(personStreetIndexer.in(addressGigaMap1.query(addressIndexer.is("street"))));
            Assertions.assertEquals(1, street1.count());
            Person person4 = street1.findFirst().get();
            Assertions.assertEquals("Joe", person4.getName());
        }

        //reload it and test again
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Root root2 = (Root) storageManager.root();
            GigaMap<Address> addressGigaMap1 = root2.getAddressGigaMap();
            GigaMap<Person> personGigaMap1 = root2.getPersonGigaMap();
            GigaQuery<Person> street1 = personGigaMap1.query(personStreetIndexer.in(addressGigaMap1.query(addressIndexer.is("street"))));
            Assertions.assertEquals(1, street1.count());
            Person person4 = street1.findFirst().get();
            Assertions.assertEquals("Joe", person4.getName());
        }

    }

    private static class PersonAddressIndexer extends Indexer.Abstract<Person, Address>
    {
        @Override
        public Class<Address> keyType()
        {
            return Address.class;
        }

        @Override
        public Address index(Person entity)
        {
            return entity.getAddress();
        }

    }

    private static class AddressIndexer extends IndexerString.Abstract<Address>
    {
        @Override
        protected String getString(final Address entity)
        {
            return entity.getStreet();
        }
    }

    private static class Root
    {
        GigaMap<Address> addressGigaMap;
        GigaMap<Person> personGigaMap;

        public Root(GigaMap<Address> addressGigaMap, GigaMap<Person> personGigaMap)
        {
            this.addressGigaMap = addressGigaMap;
            this.personGigaMap = personGigaMap;
        }

        public GigaMap<Address> getAddressGigaMap()
        {
            return addressGigaMap;
        }

        public GigaMap<Person> getPersonGigaMap()
        {
            return personGigaMap;
        }

    }

    private static class Person
    {
        private final String name;
        private final Address address;

        public Person(final String name, final Address city)
        {
            this.name = name;
            this.address = city;
        }

        public String getName()
        {
            return name;
        }

        public Address getAddress()
        {
            return address;
        }

    }


    private static class Address
    {
        private final String street;
        private final String city;

        public Address(final String street, final String city)
        {
            this.street = street;
            this.city = city;
        }

        public String getStreet()
        {
            return street;
        }

        public String getCity()
        {
            return city;
        }

    }

}
