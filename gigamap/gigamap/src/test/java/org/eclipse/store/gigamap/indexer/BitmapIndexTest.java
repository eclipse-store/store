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

import org.eclipse.store.gigamap.types.BitmapIndex;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


public class BitmapIndexTest
{


    @Test
    void dummyIndexerTest()
    {
        Indexer.Creator.Dummy<Entity, Integer> creator  = new Indexer.Creator.Dummy<>();
        Indexer<Entity, Integer>               indexer2 = creator.create();
        assertNull(indexer2);
    }

    @Test
    void addNullIndexer()
    {
        final GigaMap<Entity> gigaMap = GigaMap.New();
        assertThrows(RuntimeException.class, () -> {
            gigaMap.index().bitmap().add(null);
        });
    }

    @Test
    void indexEntityTest()
    {
        final EntityValueIndexer indexer = new EntityValueIndexer();
        final GigaMap<Entity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);
        final BitmapIndex<Entity, Integer>     bitmap   = gigaMap.index().bitmap(indexer);
        final Indexer<? super Entity, Integer> indexer1 = bitmap.indexer();

        Entity entity = new Entity("entity1", 1);
        Integer i = indexer1.index(entity);
        assertEquals(1, i);

        Entity entity2 = new Entity("entity2", 2);
        Integer i2 = indexer1.index(entity2);
        assertEquals(2, i2);

    }

    @Test
    void indexer_methodTest()
    {
        final EntityValueIndexer indexer = new EntityValueIndexer();
        final GigaMap<Entity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);
        final BitmapIndex<Entity, Integer> bitmap = gigaMap.index().bitmap(indexer);
        final Indexer<? super Entity, Integer> indexer1 = bitmap.indexer();
        assertSame(indexer1, indexer);
    }

    @Test
    void indexTest_test()
    {
        final EntityValueIndexer indexer = new EntityValueIndexer();
        final GigaMap<Entity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);

        final Entity entity1 = new Entity("entity1", 1);
        final Entity entity2 = new Entity("entity2", 2);

        gigaMap.addAll(entity1, entity2);

        final BitmapIndex<Entity, Integer> bitmap = gigaMap.index().bitmap(indexer);
        bitmap.test(entity1, 1 );

        // just call the method
        final BitmapIndex.Category<Entity> category = BitmapIndex.Indices(indexer);
        assertNotNull(category);
        bitmap.ensureOptimizedPerformance();
        bitmap.ensureOptimizedSize();
    }

    @Test
    void equalKeysTest()
    {
        final EntityValueIndexer indexer = new EntityValueIndexer();
        final GigaMap<Entity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);

        final Entity entity1 = new Entity("entity1", 1);
        final Entity entity2 = new Entity("entity2", 2);

        gigaMap.addAll(entity1, entity2);

        System.out.println(indexer.name());
        final BitmapIndex<Entity, ?> bitmap = gigaMap.index().bitmap(indexer);
        final boolean b = bitmap.equalKeys(entity1, entity2);
        assertFalse(b);

        assertFalse(bitmap.equalKeys(entity1, null));
        assertFalse(bitmap.equalKeys(null, entity2));
        assertTrue(bitmap.equalKeys(null, null));

        assertTrue(bitmap.equalKeys(entity1, entity1));

    }

    private static class EntityValueIndexer extends IndexerInteger.Abstract<Entity> {
    	
        @Override
        protected Integer getInteger(final Entity entity)
        {
            return entity.value;
        }
    }

    private static class Entity {
        private final String name;
        private final int    value;

        public Entity(final String name, final int value)
        {
            super();
            this.name  = name;
            this.value = value;
        }

        public String getName()
        {
            return this.name;
        }

        public int getValue()
        {
            return this.value;
        }

        @Override
        public String toString()
        {
            return "Entity [name=" + this.name + ", value=" + this.value + "]";
        }
    }
}
