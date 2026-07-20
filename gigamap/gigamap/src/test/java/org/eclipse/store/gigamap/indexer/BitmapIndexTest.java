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

    /**
     * Regression test for a use-after-free + double-free in the bitmap index off-heap memory
     * (microstream-one/internal#102). Decompressing a partially-decompressed level2 segment used to
     * transfer standalone level1 pointers into the new block while the old block still freed them,
     * producing silent, nondeterministic wrong query counts and, on a subsequent recompression, a
     * hard JVM crash (native heap corruption / double free). The lifecycle below reproduces the mixed
     * compressed+mutated state that the previous coverage never hit. Several rounds are run because
     * the corruption is nondeterministic; a stable query count across the optimize calls proves the
     * transferred pointers are no longer freed prematurely.
     */
    @Test
    void ensureOptimizedPerformance_afterCompressAndMutate_keepsQueryCountsStable()
    {
        for(int round = 0; round < 10; round++)
        {
            final EntityValueIndexer indexer = new EntityValueIndexer();
            final GigaMap<Entity>    map     = GigaMap.New();
            // release the off-heap memory each round so a failure mid-loop cannot pile up native pressure
            try
            {
                map.index().bitmap().add(indexer);

                for(int i = 0; i < 20_000; i++)
                {
                    map.add(new Entity("e" + i, i % 7));
                }
                final BitmapIndex<Entity, Integer> bitmap = map.index().bitmap(indexer);

                bitmap.ensureOptimizedSize();                 // compress everything (standaloneCount == 0)

                for(int i = 0; i < 5_000; i++)                // mutate -> partially-decompressed mixed state
                {
                    map.add(new Entity("m" + i, i % 7));
                }

                final long expected = map.query(indexer.is(3)).count();
                bitmap.ensureOptimizedPerformance();          // decompress (the previously buggy transfer path)
                assertEquals(expected, map.query(indexer.is(3)).count());
                bitmap.ensureOptimizedSize();                 // recompress (previously double-freed -> crash)
                assertEquals(expected, map.query(indexer.is(3)).count());
            }
            finally
            {
                map.release();
            }
        }
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
