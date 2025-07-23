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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QueryIteratorCloseTest
{

    @Test
    void addAllAfterQueryTest()
    {
        final GigaMap<NamePerson> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(nameIndexer);

        final GigaMap<NamePerson> gigaMap2 = GigaMap.New();
        gigaMap2.index().bitmap().add(nameIndexer);

        gigaMap.add(new NamePerson("name1", 1));
        gigaMap2.add(new NamePerson("name1", 1));

        gigaMap.query(nameIndexer.is("name1")).forEach(person ->  {});
        gigaMap2.query(nameIndexer.is("name1")).forEach(person ->  {});


        final List<NamePerson> personList = new ArrayList<>();
        personList.add(new NamePerson("name1", 1));

        gigaMap.addAll(personList);

        assertEquals(2, gigaMap.query(nameIndexer.is("name1")).count());
    }


    @Test
    void addAllWithIndexAfterQuery()
    {
        final GigaMap<NamePerson> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(nameIndexer);

        final List<NamePerson> personList = new ArrayList<>();
        personList.add(new NamePerson("name1", 1));

        gigaMap.query(nameIndexer.is("name1")).forEach(person ->  {});

        gigaMap.addAll(personList);
    }

    @Test
    void removeWithIndexAfterQueryWithSession()
    {
        final GigaMap<NamePerson> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(nameIndexer);
        final NamePerson namePerson = new NamePerson("name1", 1);
        gigaMap.add(namePerson);

        try (Stream<NamePerson> name1 = gigaMap.query(nameIndexer.is("name1")).stream() )
        {
            name1.forEach(person ->  {});
        }

        gigaMap.remove(namePerson);

    }


    static IndexerString<NamePerson> nameIndexer = new IndexerString.Abstract<>()
    {
        @Override
        protected String getString(final NamePerson entity)
        {
            return entity.name;
        }
    };

    static class NamePerson {
        private String name;
        private int age;

        public NamePerson(final String name, final int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return this.name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public int getAge() {
            return this.age;
        }

        public void setAge(final int age) {
            this.age = age;
        }
    }
}
