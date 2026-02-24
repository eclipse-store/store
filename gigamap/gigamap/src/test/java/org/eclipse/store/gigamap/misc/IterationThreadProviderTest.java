package org.eclipse.store.gigamap.misc;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.serializer.exceptions.NumberRangeException;
import org.eclipse.store.gigamap.types.BitmapResult;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.GigaQuery;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.gigamap.types.IterationThreadProvider;
import org.eclipse.store.gigamap.types.ThreadCountProvider;
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

    @Test
    void testFixedThreadCountProvider_validation()
    {
        assertThrows(NumberRangeException.class, () -> ThreadCountProvider.Fixed(-1));
        assertThrows(NumberRangeException.class, () -> ThreadCountProvider.Fixed(0));
    }

    @Test
    void testAdaptiveThreadCountProvider_validation()
    {
        assertThrows(NumberRangeException.class, () -> ThreadCountProvider.Adaptive(-5));
        assertThrows(NumberRangeException.class, () -> ThreadCountProvider.Adaptive(0));
    }

    @Test
    void testAdaptiveThreadCountProvider()
    {
        final ThreadCountProvider provider = ThreadCountProvider.Adaptive();
        final GigaMap<Person> map = GigaMap.New();
        final int availableProcessors = Runtime.getRuntime().availableProcessors();

        final int threadCount = provider.provideThreadCount(map, new BitmapResult[availableProcessors + 10]);
        assertTrue(threadCount <= availableProcessors);
    }


    @Test
    void testIterationThreadProviderWithFixedThreadCount()
    {
        GigaMap<Person> map = GigaMap.New();
        PersonIndexer personIndexer = new PersonIndexer();
        map.index().bitmap().add(personIndexer);
        prepageGigaMap(map);

        IterationThreadProvider threadProvider = IterationThreadProvider.Creating(
                ThreadCountProvider.Fixed(2));

        long count = map.query(threadProvider).and(personIndexer.not("Person1")).count();
        assertEquals(999, count);
    }

    @Test
    void testIterationThreadProviderWithAdaptiveThreadCount()
    {
        GigaMap<Person> map = GigaMap.New();
        PersonIndexer personIndexer = new PersonIndexer();
        map.index().bitmap().add(personIndexer);
        prepageGigaMap(map);

        IterationThreadProvider threadProvider = IterationThreadProvider.Creating(
                ThreadCountProvider.Adaptive(4));

        long count = map.query(threadProvider).and(personIndexer.not("Person1")).count();
        assertEquals(999, count);
    }

    @Test
    void testMultipleConsumersWithFixedThreadProvider()
    {
        final GigaMap<PersonWithCity> gigaMap = GigaMap.New();
        final CityIndexer cityIndexer = new CityIndexer();
        gigaMap.index().bitmap().add(cityIndexer);

        // Prepare test data
        for (int i = 0; i < 400; i++) {
            gigaMap.add(new PersonWithCity("Person" + i, "Berlin", i));
        }

        final IterationThreadProvider provider = IterationThreadProvider.Creating(
                ThreadCountProvider.Fixed(4)
        );

        final GigaQuery<PersonWithCity> query = gigaMap.query(provider)
                .and(cityIndexer.is("Berlin"));

        final AtomicInteger countA = new AtomicInteger(0);
        final AtomicInteger countB = new AtomicInteger(0);
        final AtomicInteger countC = new AtomicInteger(0);
        final AtomicInteger countD = new AtomicInteger(0);

        // Execute with 4 consumers
        query.execute(
                person -> countA.incrementAndGet(),
                person -> countB.incrementAndGet(),
                person -> countC.incrementAndGet(),
                person -> countD.incrementAndGet()
        );

        final long expectedCount = 100;
        assertEquals(expectedCount, countA.get());
        assertEquals(expectedCount, countB.get());
        assertEquals(expectedCount, countC.get());
        assertEquals(expectedCount, countD.get());
    }

    @Test
    void testMultipleConsumersWithPooling()
    {
        final GigaMap<PersonWithCity> gigaMap = GigaMap.New();
        final CityIndexer cityIndexer = new CityIndexer();
        gigaMap.index().bitmap().add(cityIndexer);

        // Prepare test data
        for (int i = 0; i < 300; i++) {
            gigaMap.add(new PersonWithCity("Person" + i, "Munich", i));
        }

        final IterationThreadProvider provider = IterationThreadProvider.Pooling(
                4,
                ThreadCountProvider.Fixed(2)
        );

        final GigaQuery<PersonWithCity> query = gigaMap.query(provider)
                .and(cityIndexer.is("Munich"));

        final AtomicInteger countA = new AtomicInteger(0);
        final AtomicInteger countB = new AtomicInteger(0);

        // Execute with 2 consumers
        query.execute(
                person -> countA.incrementAndGet(),
                person -> countB.incrementAndGet()
        );

        final long expectedCount = 150;
        assertEquals(expectedCount, countA.get());
        assertEquals(expectedCount, countB.get());

        // Execute again to verify pooling reuses threads
        countA.set(0);
        countB.set(0);

        query.execute(
                person -> countA.incrementAndGet(),
                person -> countB.incrementAndGet()
        );

        assertEquals(expectedCount, countA.get());
        assertEquals(expectedCount, countB.get());
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

    private static class PersonWithCity
    {
        private final String name;
        private final String city;
        private final int age;

        public PersonWithCity(final String name, final String city, final int age)
        {
            this.name = name;
            this.city = city;
            this.age = age;
        }
    }

    private static class CityIndexer extends IndexerString.Abstract<PersonWithCity>
    {
        @Override
        protected String getString(final PersonWithCity entity)
        {
            return entity.city;
        }
    }

}
