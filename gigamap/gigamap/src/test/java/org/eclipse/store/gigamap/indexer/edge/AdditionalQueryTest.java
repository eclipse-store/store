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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AdditionalQueryTest
{

    @TempDir
    Path tempDir;

    private final NameStringIndexer nameIndexer = new NameStringIndexer();


    @Test
    void queryNameTypeAndValue()
    {
        GigaMap<Entity> map = prepageGigaMap();

        Entity alice = map.query(nameIndexer.name(), String.class,"Alice").findFirst().get();
        assertEquals("Alice", alice.getName());
    }

    @Test
    void queryIndexNameAndType()
    {
        GigaMap<Entity> map = prepageGigaMap();

        Entity alice = map.query(nameIndexer.name(), String.class).is("Alice").findFirst().get();
        assertEquals("Alice", alice.getName());
    }

    @Test
    void queryNameAndValue()
    {
        GigaMap<Entity> map = prepageGigaMap();

        Entity alice = map.query(nameIndexer.name(), "Alice").findFirst().get();
        assertEquals("Alice", alice.getName());
    }

    @Test
    void queryIndexAndValue()
    {
        GigaMap<Entity> map = prepageGigaMap();

        Entity alice = map.query(nameIndexer, "Alice").findFirst().get();
        assertEquals("Alice", alice.getName());
    }

    @Test
    void conditionalBuilder_query_test()
    {
        GigaMap<Entity> map = prepageGigaMap();

        Entity alice = map.query(nameIndexer.name()).is("Alice").findFirst().get();
        assertEquals("Alice", alice.getName());
    }

    private GigaMap<Entity> prepageGigaMap() {
        GigaMap<Entity> map = GigaMap.New();
        map.index().bitmap().add(nameIndexer);

        map.add(new Entity("Alice", 20, true));
        map.add(new Entity("Bob", 30, false));
        map.add(new Entity("Charlie", 40, true));
        return map;
    }

    private static class NameStringIndexer extends IndexerString.Abstract<Entity> {
		@Override
		protected String getString(Entity entity)
		{
			return entity.name;
		}
    }

    private static class Entity {
        private String name;
        private int age;
        private boolean active;

        public Entity(String name, int age, boolean active) {
            this.name = name;
            this.age = age;
            this.active = active;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public boolean isActive() {
            return active;
        }
    }
}
