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

import org.eclipse.store.gigamap.types.*;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class QueryVariableTests
{

    @TempDir
    Path workDir;


    NameStringIndexer nameIndexer = new NameStringIndexer();
    AgeIndexer ageIndexer = new AgeIndexer();
    ActiveIndexer activeIndexer = new ActiveIndexer();

    Condition<Person> firstSubQuery = nameIndexer.is("Alice").and(ageIndexer.is(30));

    @Test
    void queryTest()
    {
        GigaMap<Person> map = GigaMap.New();
        map.index().bitmap().addAll(nameIndexer, ageIndexer, activeIndexer);
        map.add(new Person("Alice", 30, true));

        map.query
                        (
                                firstSubQuery.and(activeIndexer.isTrue())
                        )
                .and
                        (
                                firstSubQuery.and(activeIndexer.isFalse())
                        )
                .stream().forEach(System.out::println);

//       map.query(nameIndexer.is("Alice").and(ageIndexer.is(30)).and(nameIndexer.is("Alice").and(ageIndexer.is(30)))).forEach(System.out::println);

    }

    @Test
    void queryOrTest()
    {
        GigaMap<Person> map = GigaMap.New();
        map.index().bitmap().addAll(nameIndexer, ageIndexer, activeIndexer);
        map.add(new Person("Alice", 30, true));
        map.add(new Person("Bob", 40, false));
        map.add(new Person("Charlie", 50, true));

        List<Person> collect = map.query
                        (
                                nameIndexer.is("Alice").and(ageIndexer.is(30))
                        )
                .and
                        (
                                nameIndexer.is("Bob").and(ageIndexer.is(40))
                        )
                .or
                        (
                                activeIndexer.isTrue()
                        )
                .toList();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            GigaMap<Person> map2 = (GigaMap<Person>) storageManager.root();

            List<Person> collect2 = map2.query
                            (
                                    nameIndexer.is("Alice").and(ageIndexer.is(30))
                            )
                    .and
                            (
                                    nameIndexer.is("Bob").and(ageIndexer.is(40))
                            )
                    .or
                            (
                                    activeIndexer.isTrue()
                            )
                    .stream().collect(Collectors.toList());

            assertEquals(2, collect2.size());
            assertIterableEquals(collect, collect2);

        }

    }

    @Test
    void queryOrAndTest()
    {
        GigaMap<Person> map = GigaMap.New();
        map.index().bitmap().addAll(nameIndexer, ageIndexer, activeIndexer);
        map.add(new Person("Alice", 30, true));
        map.add(new Person("Bob", 40, false));
        map.add(new Person("Charlie", 50, true));

        long count = map.query
                        (
                                nameIndexer.is("Alice").and(ageIndexer.is(30))
                        )
                .or
                        (
                                nameIndexer.is("Bob").and(ageIndexer.is(40))
                        )
                .and
                        (
                                activeIndexer.isTrue()
                        )
                .or
                        (
                                activeIndexer.isFalse()
                        )
                .stream().count();

        assertEquals(2, count);
    }

    private static class NameStringIndexer extends IndexerString.Abstract<Person>
    {

        @Override
        protected String getString(Person entity)
        {
            return entity.getName();
        }
    }

    private static class AgeIndexer extends IndexerInteger.Abstract<Person>
    {

        @Override
        protected Integer getInteger(Person entity)
        {
            return entity.getAge();
        }
    }

    private static class ActiveIndexer extends IndexerBoolean.Abstract<Person>
    {

        @Override
        protected Boolean getBoolean(Person entity)
        {
            return entity.isActive();
        }
    }

    private static class Person
    {

        private String name;
        private int age;
        private boolean active;

        public Person(String name, int age, boolean active)
        {
            this.name = name;
            this.age = age;
            this.active = active;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public int getAge()
        {
            return age;
        }

        public void setAge(int age)
        {
            this.age = age;
        }

        public boolean isActive()
        {
            return active;
        }

        public void setActive(boolean active)
        {
            this.active = active;
        }

        @Override
        public String toString()
        {
            return "Person{" +
                    "hash" + hashCode() +
                    ", name='" + name + '\'' +
                    ", age=" + age +
                    ", active=" + active +
                    '}';
        }


        @Override
        public boolean equals(Object o)
        {
            if (o == null || getClass() != o.getClass()) return false;
            Person person = (Person) o;
            return age == person.age && active == person.active && Objects.equals(name, person.name);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(name, age, active);
        }
    }
}
