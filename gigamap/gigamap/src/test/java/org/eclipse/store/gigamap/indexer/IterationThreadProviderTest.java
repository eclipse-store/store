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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.gigamap.types.IterationThreadProvider;
import org.junit.jupiter.api.Test;

public class IterationThreadProviderTest
{
    @Test
    void iterationThreadProviderTest()
    {
        GigaMap<Person> map = GigaMap.New();
        PersonIndexer personIndexer = new PersonIndexer();
        map.index().bitmap().add(personIndexer);
        prepageGigaMap(map);

        IterationThreadProvider threadProvider = IterationThreadProvider.Creating(
                (parent, results) -> 4);

        long count = map.query(threadProvider).and(personIndexer.not("Person1")).count();
        assertEquals(999, count);
    }

    private GigaMap<Person> prepageGigaMap(GigaMap<Person> map)
    {
        for (int i = 0; i < 1000; i++) {
            map.add(new Person("Person" + i, 20 + i));
        }
        return map;
    }


    private static class PersonIndexer extends IndexerString.Abstract<Person>
    {

        @Override
        protected String getString(Person entity)
        {
            return entity.name;
        }
    }

    private static class Person
    {
        private String name;
        private int age;

        public Person(String name, int age)
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
            return "Person{" +
                    "name='" + name + '\'' +
                    ", age=" + age +
                    '}';
        }
    }

}
