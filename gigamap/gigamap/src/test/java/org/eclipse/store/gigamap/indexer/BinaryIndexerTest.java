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

import org.eclipse.store.gigamap.types.BinaryIndexer;
import org.eclipse.store.gigamap.types.BitmapIndex;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerLong;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


public class BinaryIndexerTest
{

    @TempDir
    Path storagePath;

    @Test
    void testBinaryIndexerEqualsNormalIndexer()
    {
        BinaryIndexer<LongEntity> binaryIndexer = new LongIndexer();
        IndexerLong<LongEntity>   normalIndexer = new LongNormalIndexer();

        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(binaryIndexer);
        gigaMap.index().bitmap().add(normalIndexer);

        List<LongEntity> list = new ArrayList<>();

        // Add predictable values to test
        for (int i = 1; i <= 1000; i++) {
            LongEntity entity = new LongEntity(i);
            gigaMap.add(entity);
            list.add(entity);
        }

        // Persist
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        // Reload and compare results of both indexes
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(storagePath)) {
            GigaMap<LongEntity> map2 = (GigaMap<LongEntity>) storageManager.root();

            BitmapIndex<LongEntity, Long> binary = map2.index().bitmap().get(Long.class, "org.eclipse.store.gigamap.indexer.BinaryIndexerTest.LongIndexer");
            BitmapIndex<LongEntity, Long> normal = map2.index().bitmap().get(Long.class, "org.eclipse.store.gigamap.indexer.BinaryIndexerTest.LongNormalIndexer");

            for (LongEntity entity : list) {
                Long value = entity.value;

                LongEntity resultBinary = map2.query(binary.is(value)).findFirst().orElse(null);
                LongEntity resultNormal = map2.query(normal.is(value)).findFirst().orElse(null);

                assertNotNull(resultBinary, "Binary index did not find entity for value: " + value);
                assertNotNull(resultNormal, "Normal index did not find entity for value: " + value);

                assertEquals(resultNormal.value, resultBinary.value,
                        "Mismatch between BinaryIndexer and NormalIndexer for value: " + value);
            }
        }
    }

    @Test
    void testBinaryIndexerEqualsNormalIndexerUpdateApi()
    {
        BinaryIndexer<LongEntity> binaryIndexer = new LongIndexer();
        IndexerLong<LongEntity> normalIndexer = new LongNormalIndexer();

        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(binaryIndexer);
        gigaMap.index().bitmap().add(normalIndexer);

        List<LongEntity> list = new ArrayList<>();

        // Add predictable values to test
        for (int i = 1; i <= 1000; i++) {
            LongEntity entity = new LongEntity(i);
            gigaMap.add(entity);
            list.add(entity);
        }

        // Persist
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        // Reload and compare results of both indexes
        GigaMap<LongEntity> map2 = GigaMap.New();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map2, storagePath)) {

            BitmapIndex<LongEntity, Long> binary = map2.index().bitmap().get(Long.class, "org.eclipse.store.gigamap.indexer.BinaryIndexerTest.LongIndexer");
            BitmapIndex<LongEntity, Long> normal = map2.index().bitmap().get(Long.class, "org.eclipse.store.gigamap.indexer.BinaryIndexerTest.LongNormalIndexer");

            for (LongEntity entity : list) {
                Long value = entity.value;

                LongEntity resultBinary = map2.query(binary.is(value)).findFirst().orElse(null);
                LongEntity resultNormal = map2.query(normal.is(value)).findFirst().orElse(null);

                assertNotNull(resultBinary, "Binary index did not find entity for value: " + value);
                assertNotNull(resultNormal, "Normal index did not find entity for value: " + value);

                assertEquals(resultNormal.value, resultBinary.value,
                        "Mismatch between BinaryIndexer and NormalIndexer for value: " + value);
            }
        }
    }


    @Test
    void testBinaryIndexEdgeCases()
    {
        LongIndexer indexer = new LongIndexer();
        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);

        // Add multiple entities with the same value
        for (int i = 0; i < 10; i++) {
            gigaMap.add(new LongEntity(42L));
        }

        // Add some edge case values
        LongEntity max = new LongEntity(Long.MAX_VALUE - 1);

        gigaMap.add(max);

        // Add and then remove an entity
        LongEntity toRemove = new LongEntity(12345L);
        gigaMap.add(toRemove);
        assertEquals(1, gigaMap.query(indexer.is(12345L)).count());

        gigaMap.remove(toRemove);
        assertEquals(0, gigaMap.query(indexer.is(12345L)).count());

        // Verify counts
        assertEquals(10, gigaMap.query(indexer.is(42L)).count());
        assertEquals(1, gigaMap.query(indexer.is(Long.MAX_VALUE - 1)).count());

        // Persist
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        // Reload and re-verify
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(storagePath)) {
            GigaMap<LongEntity> reloadedMap = (GigaMap<LongEntity>) storageManager.root();
            BitmapIndex<LongEntity, Long> index = reloadedMap.index().bitmap().get(Long.class, "org.eclipse.store.gigamap.indexer.BinaryIndexerTest.LongIndexer");

            assertEquals(10, reloadedMap.query(index.is(42L)).count());
            assertEquals(1, reloadedMap.query(index.is(Long.MAX_VALUE - 1)).count());
            assertEquals(0, reloadedMap.query(index.is(12345L)).count());

            // Query for something never inserted
            assertEquals(0, reloadedMap.query(index.is(999999L)).count());
        }
    }

    @Test
    void testUpdate()
    {
        LongIndexer indexer = new LongIndexer();
        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);

        LongEntity entity = new LongEntity(99L);
        gigaMap.add(entity);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(storagePath)) {
            GigaMap<LongEntity> map2 = (GigaMap<LongEntity>) storageManager.root();
            LongEntity entity1 = map2.query(indexer.is(99L)).findFirst().orElse(null);
            assertNotNull(entity1);
            assertEquals(99L, entity1.value);

            map2.update(entity1, longEntity -> longEntity.setValue(100L));

            assertEquals(1, map2.query(indexer.is(100L)).count());

            map2.remove(entity1);

            assertEquals(0, map2.query(indexer.is(100L)).count());

        }

    }


    @Test
    void testRemoveAndRequery()
    {
        LongIndexer indexer = new LongIndexer();
        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);

        LongEntity entity = new LongEntity(99L);
        gigaMap.add(entity);

        assertEquals(1, gigaMap.query(indexer.is(99L)).count());

        gigaMap.remove(entity);

        assertEquals(0, gigaMap.query(indexer.is(99L)).count());
    }

    @Test
    void testDuplicateValues()
    {
        LongIndexer indexer = new LongIndexer();
        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);

        for (int i = 0; i < 100; i++) {
            gigaMap.add(new LongEntity(42L)); // all with same value
        }

        assertEquals(100, gigaMap.query(indexer.is(42L)).count());
    }

    @Test
    void testPowersOfTwoPlusOne()
    {
        LongIndexer indexer = new LongIndexer();
        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);

        List<LongEntity> list = new ArrayList<>(); // cache for verification after reload

        // Insert values of the form 2^i - 1
        for (int i = 0; i < 63; i++) { // 2^63 - 1 = Long.MAX_VALUE
            long value = (1L << i) + 1;
            LongEntity longEntity = new LongEntity(value);
            gigaMap.add(longEntity);
            list.add(longEntity);
        }

        // Persist to storage
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            // No operation, just persisting the data
        }

        // Reload and verify correctness of queries
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(storagePath)) {
            GigaMap<LongEntity> map2 = (GigaMap<LongEntity>) storageManager.root();

            BitmapIndex<LongEntity, Long> index = map2.index().bitmap().get(Long.class, "org.eclipse.store.gigamap.indexer.BinaryIndexerTest.LongIndexer");
            for (LongEntity longEntity : list) {
                LongEntity result = map2.query(index.is(longEntity.value)).findFirst().orElse(null);
                assertNotNull(result, "Entity not found for value: " + longEntity.value);
                assertEquals(longEntity.value, result.value, "Value mismatch");

                // Optional: query for a value that is not in the map
                assertNull(map2.query(index.is(Long.MIN_VALUE)).findFirst().orElse(null));
            }
        }
    }

    @Test
    void testPowersOfTwoMinusOne()
    {
        LongIndexer indexer = new LongIndexer();
        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);

        List<LongEntity> list = new ArrayList<>(); // cache for verification after reload

        // Insert values of the form 2^i - 1
        for (int i = 1; i < 63; i++) { // 2^63 - 1 = Long.MAX_VALUE
            long value = (1L << i) - 1;
            LongEntity longEntity = new LongEntity(value);
            gigaMap.add(longEntity);
            list.add(longEntity);
        }

        // Persist to storage
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            // No operation, just persisting the data
        }

        // Reload and verify correctness of queries
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(storagePath)) {
            GigaMap<LongEntity> map2 = (GigaMap<LongEntity>) storageManager.root();

            BitmapIndex<LongEntity, Long> index = map2.index().bitmap().get(Long.class, "org.eclipse.store.gigamap.indexer.BinaryIndexerTest.LongIndexer");
            for (LongEntity longEntity : list) {
                LongEntity result = map2.query(index.is(longEntity.value)).findFirst().orElse(null);
                assertNotNull(result, "Entity not found for value: " + longEntity.value);
                assertEquals(longEntity.value, result.value, "Value mismatch");

                // Optional: query for a value that is not in the map
                assertNull(map2.query(index.is(Long.MIN_VALUE)).findFirst().orElse(null));
            }
        }
    }


    @Test
    void testPowersOfTwo()
    {
        LongIndexer indexer = new LongIndexer();
        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);

        List<LongEntity> list = new ArrayList<>(); // cache

        for (int i = 0; i < 63; i++) { // až 2^62, because 2^63 == Long.MIN_VALUE (negative)
            long value = 1L << i; // 2^i
            LongEntity longEntity = new LongEntity(value);
            gigaMap.add(longEntity);
            list.add(longEntity);
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(storagePath)) {
            GigaMap<LongEntity> map2 = (GigaMap<LongEntity>) storageManager.root();

            BitmapIndex<LongEntity, Long> index = map2.index().bitmap().get(Long.class, "org.eclipse.store.gigamap.indexer.BinaryIndexerTest.LongIndexer");
            for (LongEntity longEntity : list) {
                LongEntity longEntity1 = map2.query(index.is(longEntity.value)).findFirst().orElse(null);
                assertNotNull(longEntity1, "Entity not found for value: " + longEntity.value);
                assertEquals(longEntity.value, longEntity1.value, "Value mismatch");

                // Optional: query for a value that is not in the map
                // Uncomment the following line after fix: https://github.com/microstream-one/gigamap/issues/230
                //assertNull(map2.query(index.is(Long.MIN_VALUE)).findFirst().orElse(null));
            }
        }
    }

    @Test
    void testRandom()
    {
        LongIndexer indexer = new LongIndexer();
        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);

        List<LongEntity> list = new ArrayList<>(); //cache

        SecureRandom random = new SecureRandom();

        for (int i = 0; i < 1000; i++) {
            LongEntity longEntity = new LongEntity(Math.abs(random.nextLong()));
            gigaMap.add(longEntity);
            list.add(longEntity);
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(storagePath)) {
            GigaMap<LongEntity> map2 = (GigaMap<LongEntity>) storageManager.root();

            BitmapIndex<LongEntity, Long> index = map2.index().bitmap().get(Long.class, "org.eclipse.store.gigamap.indexer.BinaryIndexerTest.LongIndexer");
            for (LongEntity longEntity : list) {
                LongEntity longEntity1 = map2.query(index.is(longEntity.value)).findFirst().orElse(null);
                assertEquals(longEntity.value, longEntity1.value);

            }
        }
    }

    @Test
    void saveValueNotOneFirstTest()
    {
        LongIndexer indexer = new LongIndexer();
        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);
        gigaMap.add(new LongEntity(5L));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(storagePath)) {
            storageManager.setRoot(gigaMap);
            storageManager.storeRoot();
        }
    }


    @Test
    void longMaxValueHardTest()
    {
        LongIndexer indexer = new LongIndexer();
        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);
        gigaMap.add(new LongEntity(1L));


        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(storagePath)) {
            GigaMap<LongEntity> map2 = (GigaMap<LongEntity>) storageManager.root();
            map2.add(new LongEntity(5L));
//            map2.store();

            LongEntity longEntity = map2.query(indexer.is(5L)).findFirst().orElse(null);
            assertNotNull(longEntity);
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(storagePath)) {
            GigaMap<LongEntity> map2 = (GigaMap<LongEntity>) storageManager.root();

            LongEntity longEntity = map2.query(indexer.is(5L)).findFirst().orElse(null);
            assertNull(longEntity);
        }


    }

    @Test
    void longMaxValueNormalHardTest()
    {
        LongNormalIndexer indexer = new LongNormalIndexer();
        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);
        gigaMap.add(new LongEntity(1L));


        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(storagePath)) {
            GigaMap<LongEntity> map2 = (GigaMap<LongEntity>) storageManager.root();
            map2.add(new LongEntity(Long.MAX_VALUE));
//            map2.store();

            LongEntity longEntity = map2.query(indexer.is(Long.MAX_VALUE)).findFirst().orElse(null);
            assertNotNull(longEntity);
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(storagePath)) {
            GigaMap<LongEntity> map2 = (GigaMap<LongEntity>) storageManager.root();

            LongEntity longEntity = map2.query(indexer.is(Long.MAX_VALUE)).findFirst().orElse(null);
            assertNull(longEntity);
        }


    }

    @Test
    void index()
    {
        LongIndexer indexer = new LongIndexer();
        BinaryIndexer<LongEntity> wrapped = BinaryIndexer.Wrap(indexer);
        GigaMap<LongEntity> gigaMap = GigaMap.New();

        LongEntity entity1 = new LongEntity(1);
        gigaMap.add(entity1);

        gigaMap.index().bitmap().add(wrapped);

        LongEntity entity2 = new LongEntity(2);
        LongEntity entity3 = new LongEntity(3);
        gigaMap.addAll(entity2, entity3);

        wrapped.index(entity1); // index after add index into gigamap

        LongEntity longEntity = gigaMap.query(wrapped.is(1L)).findFirst().get();
        assertEquals(entity1.value, longEntity.value);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            GigaMap<LongEntity> map2 = (GigaMap<LongEntity>) storageManager.root();
            longEntity = map2.query(wrapped.is(1L)).findFirst().get();
            assertEquals(entity1.value, longEntity.value);
        }

    }

    @Test
    void basicTest()
    {
        LongIndexer indexer = new LongIndexer();
        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(indexer);

        LongEntity entity1 = new LongEntity(1);
        gigaMap.add(entity1);

        LongEntity entity2 = new LongEntity(2);
        LongEntity entity3 = new LongEntity(3);
        gigaMap.addAll(entity2, entity3);

        //generate 1000 entities
        for (int i = 4; i < 10_000; i++) {
            gigaMap.add(new LongEntity(i));
        }

        LongEntity longEntity = gigaMap.query(indexer.is(1L)).findFirst().get();
        assertEquals(entity1.value, longEntity.value);

        assertEquals(5_000L, gigaMap.query(indexer.is(5_000L)).findFirst().get().value);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            GigaMap<LongEntity> map2 = (GigaMap<LongEntity>) storageManager.root();
            BitmapIndex<LongEntity, Long> index = map2.index().bitmap().get(Long.class, "org.eclipse.store.gigamap.indexer.BinaryIndexerTest.LongIndexer");

            longEntity = map2.query(index.is(1L)).findFirst().get();
            assertEquals(entity1.value, longEntity.value);
            assertEquals(5_000L, gigaMap.query(index.is(5_000L)).findFirst().get().value);
            List<LongEntity> list = gigaMap.query(index.in(20L, 30L, 40L)).toList();
            assertEquals(3, list.size());

            map2.add(new LongEntity(Long.MAX_VALUE));
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            GigaMap<LongEntity> map2 = (GigaMap<LongEntity>) storageManager.root();
            BitmapIndex<LongEntity, Long> index = map2.index().bitmap().get(Long.class, "org.eclipse.store.gigamap.indexer.BinaryIndexerTest.LongIndexer");

            assertEquals(5000L, map2.query(index.is(5_000L)).findFirst().get().value);

            // enable after fix: https://github.com/microstream-one/gigamap/issues/230
            //LongEntity longEntityMax = map2.query(index.is(Long.MAX_VALUE)).findFirst().get();
            //assertEquals(Long.MAX_VALUE, longEntityMax.value);


        }

    }

    @Test
    void binaryIndexerWrap()
    {
        LongIndexer indexer = new LongIndexer();
        BinaryIndexer<LongEntity> wrapped = BinaryIndexer.Wrap(indexer);
        GigaMap<LongEntity> gigaMap = GigaMap.New();
        gigaMap.index().bitmap().add(wrapped);

        LongEntity entity1 = new LongEntity(1);
        LongEntity entity2 = new LongEntity(2);
        LongEntity entity3 = new LongEntity(3);

        gigaMap.addAll(entity1, entity2, entity3);

        LongEntity longEntity = gigaMap.query(wrapped.is(1L)).findFirst().get();
        assertEquals(entity1.value, longEntity.value);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            GigaMap<LongEntity> map2 = (GigaMap<LongEntity>) storageManager.root();
            longEntity = map2.query(wrapped.is(1L)).findFirst().get();
            assertEquals(entity1.value, longEntity.value);

        }

    }

    private static class LongNormalIndexer extends IndexerLong.Abstract<LongEntity>
    {


        @Override
        protected Long getLong(LongEntity entity)
        {
            return entity.value;
        }
    }

    private static class LongIndexer extends BinaryIndexer.Abstract<LongEntity>
    {


        @Override
        public long indexBinary(LongEntity entity)
        {
            return entity.value;
        }
    }


    private static class LongEntity
    {
        private long value;

        public LongEntity(long value)
        {
            this.value = value;
        }

        public void setValue(long value)
        {
            this.value = value;
        }

        @Override
        public String toString()
        {
            return "LongEntity{" +
                    "value=" + value +
                    '}';
        }
    }


}
