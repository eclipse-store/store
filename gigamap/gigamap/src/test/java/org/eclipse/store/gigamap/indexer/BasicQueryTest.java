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
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class BasicQueryTest
{

    @Test
    void queryWithKey()
    {
        GigaMap<Person> gigaMap    = GigaMap.New();
        AgeIndexer      ageIndexer = new AgeIndexer();
        gigaMap.index().bitmap().add(ageIndexer);
        gigaMap.add(new Person("Joe", 30, "address", "city", "country", "email", "phone"));
        gigaMap.add(new Person("Karl", 39, "address", "city", "country", "email", "phone"));
        gigaMap.add(new Person(null, 25, "address", "city", "country", "email", "phone"));

        List<Person> jeo = gigaMap.query("org.eclipse.store.gigamap.indexer.BasicQueryTest.AgeIndexer", 30).toList();
        assertEquals(1, jeo.size());
        assertEquals("Joe", jeo.get(0).getName());

    }

    @Test
    void notNullTest()
    {
        GigaMap<Person> gigaMap = GigaMap.New();
        NameIndexer nameIndexer = new NameIndexer();
        gigaMap.index().bitmap().add(nameIndexer);

        gigaMap.add(new Person("Joe", 30, "address", "city", "country", "email", "phone"));
        gigaMap.add(new Person("Karl", 39, "address", "city", "country", "email", "phone"));
        gigaMap.add(new Person(null, 25, "address", "city", "country", "email", "phone"));

        AtomicInteger count = new AtomicInteger();
        gigaMap.query(nameIndexer.notNull()).forEach(person -> count.incrementAndGet());
        assertEquals(2, count.get());
    }

    @Test
    void isNullTest()
    {
        GigaMap<Person> gigaMap = GigaMap.New();
        NameIndexer nameIndexer = new NameIndexer();
        gigaMap.index().bitmap().add(nameIndexer);

        gigaMap.add(new Person("Joe", 30, "address", "city", "country", "email", "phone"));
        gigaMap.add(new Person("Karl", 39, "address", "city", "country", "email", "phone"));
        gigaMap.add(new Person(null, 25, "address", "city", "country", "email", "phone"));

        AtomicInteger count = new AtomicInteger();
        gigaMap.query(nameIndexer.isNull()).forEach(person -> count.incrementAndGet());
        assertEquals(1, count.get());
    }

    @Test
    void notInTest()
    {
        GigaMap<Person> gigaMap = GigaMap.New();
        NameIndexer nameIndexer = new NameIndexer();
        gigaMap.index().bitmap().add(nameIndexer);

        gigaMap.add(new Person("Joe", 30, "address", "city", "country", "email", "phone"));
        gigaMap.add(new Person("Karl", 39, "address", "city", "country", "email", "phone"));
        gigaMap.add(new Person(null, 25, "address", "city", "country", "email", "phone"));

        AtomicInteger count = new AtomicInteger();
        gigaMap.query(nameIndexer.notIn("hello")).forEach(person -> count.incrementAndGet());
        assertEquals(3, count.get());

        count.set(0);
        gigaMap.query(nameIndexer.notIn("Joe")).forEach(person -> count.incrementAndGet());
        assertEquals(2, count.get());
    }

    @Test
    void byExample()
    {
        GigaMap<Person> gigaMap = GigaMap.New();
        NameIndexer nameIndexer = new NameIndexer();
        gigaMap.index().bitmap().add(nameIndexer);

        Person p = new Person("Joe", 30, "address", "city", "country", "email", "phone");

        gigaMap.add(p);
        gigaMap.add(new Person("Karl", 39, "address", "city", "country", "email", "phone"));
        gigaMap.add(new Person(null, 25, "address", "city", "country", "email", "phone"));

        AtomicInteger count = new AtomicInteger();
        gigaMap.query(nameIndexer.byExample(p)).forEach(person -> count.incrementAndGet());
        assertEquals(1, count.get());
    }

    @Test
    void notByExample()
    {
        GigaMap<Person> gigaMap = GigaMap.New();
        NameIndexer nameIndexer = new NameIndexer();
        gigaMap.index().bitmap().add(nameIndexer);

        Person p = new Person("Joe", 30, "address", "city", "country", "email", "phone");

        gigaMap.add(p);
        gigaMap.add(new Person("Karl", 39, "address", "city", "country", "email", "phone"));
        gigaMap.add(new Person(null, 25, "address", "city", "country", "email", "phone"));

        AtomicInteger count = new AtomicInteger();
        gigaMap.query(nameIndexer.notByExample(p)).forEach((Person person) -> count.incrementAndGet());
        assertEquals(2, count.get());
    }

    @Test
    void notByExample_another_instance()
    {
        GigaMap<Person> gigaMap = GigaMap.New();
        NameIndexer nameIndexer = new NameIndexer();
        gigaMap.index().bitmap().add(nameIndexer);

        Person p = new Person("Joe", 30, "address", "city", "country", "email", "phone");
        gigaMap.add(p);
        gigaMap.add(new Person("Karl", 39, "address", "city", "country", "email", "phone"));
        gigaMap.add(new Person(null, 25, "address", "city", "country", "email", "phone"));


        Person p2 = new Person("Joe", 30, "address", "city", "country", "email", "phone");

        AtomicInteger count = new AtomicInteger();
        gigaMap.query(nameIndexer.notByExample(p2)).forEach(person -> count.incrementAndGet());
        assertEquals(2, count.get());
    }


    @Test
    void queryToList()
    {
        GigaMap<Person> gigaMap = GigaMap.New();
        NameIndexer nameIndexer = new NameIndexer();
        gigaMap.index().bitmap().add(nameIndexer);

        Person p = new Person("Joe", 30, "address", "city", "country", "email", "phone");
        gigaMap.add(p);
        gigaMap.add(new Person("Karl", 39, "address", "city", "country", "email", "phone"));
        gigaMap.add(new Person(null, 25, "address", "city", "country", "email", "phone"));


        Person p2 = new Person("Joe", 30, "address", "city", "country", "email", "phone");

        AtomicInteger count = new AtomicInteger();
        List<Person> list = gigaMap.query(nameIndexer.notByExample(p2)).toList();
        assertEquals(2, list.size());
        assertEquals("Karl", list.get(0).getName());
    }

    @Test
    void queryToSet()
    {
        GigaMap<Person> gigaMap = GigaMap.New();
        NameIndexer nameIndexer = new NameIndexer();
        gigaMap.index().bitmap().add(nameIndexer);

        Person p = new Person("Joe", 30, "address", "city", "country", "email", "phone");
        gigaMap.add(p);
        gigaMap.add(new Person("Karl", 39, "address", "city", "country", "email", "phone"));
        gigaMap.add(new Person(null, 25, "address", "city", "country", "email", "phone"));


        Person p2 = new Person("Joe", 30, "address", "city", "country", "email", "phone");

        AtomicInteger count = new AtomicInteger();
        Set<Person> list = gigaMap.query(nameIndexer.notByExample(p2)).toSet();
        assertEquals(2, list.size());
    }

    @Test
    void findFirst()
    {
        GigaMap<Person> gigaMap = GigaMap.New();
        NameIndexer nameIndexer = new NameIndexer();
        gigaMap.index().bitmap().add(nameIndexer);

        Person p = new Person("Joe", 30, "address", "city", "country", "email", "phone");
        gigaMap.add(p);
        gigaMap.add(new Person("Karl", 39, "address", "city", "country", "email", "phone"));
        gigaMap.add(new Person(null, 25, "address", "city", "country", "email", "phone"));


        Person p2 = new Person("Joe", 30, "address", "city", "country", "email", "phone");

        AtomicInteger count = new AtomicInteger();
        Optional<Person> first = gigaMap.query(nameIndexer.notByExample(p2)).findFirst();
        assertTrue(first.isPresent());
        assertEquals("Karl", first.get().getName());
    }

    static class NameIndexer extends IndexerString.Abstract<Person>
    {
    	
        @Override
        protected String getString(Person entity)
        {
            return entity.name;
        }
    }

    static class AgeIndexer extends IndexerInteger.Abstract<Person>
    {

        @Override
        protected Integer getInteger(Person entity)
        {
            return entity.age;
        }
    }

    static class Person
    {
        private final String name;
        private final int age;
        private final String address;
        private final String city;
        private final String country;
        private final String email;
        private final String phone;

        public Person(String name, int age, String address, String city, String country, String email, String phone)
        {
            this.name = name;
            this.age = age;
            this.address = address;
            this.city = city;
            this.country = country;
            this.email = email;
            this.phone = phone;
        }

        public String getName()
        {
            return name;
        }

        public int getAge()
        {
            return age;
        }

        public String getAddress()
        {
            return address;
        }

        public String getCity()
        {
            return city;
        }

        public String getCountry()
        {
            return country;
        }

        public String getEmail()
        {
            return email;
        }

        public String getPhone()
        {
            return phone;
        }
    }

}

