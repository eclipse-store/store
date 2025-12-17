
package org.eclipse.store.gigamap.crud;

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

import org.eclipse.serializer.util.X;
import org.eclipse.store.gigamap.data.Entity;
import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.GigaQuery;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

import net.datafaker.Faker;

public class GigaMapStoreTests
{
	@TempDir
	Path tempDir;


	@Test
	void empty()
	{
		final GigaMap<Entity> map = GigaMap.New();
		assertTrue(map.isEmpty());
		assertEquals(0, map.size());

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertTrue(map.isEmpty());
			assertEquals(0, map.size());
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
            assertTrue(loaded.isEmpty());
			assertEquals(0, loaded.size());
		}
	}

	@Test
	void emptyUpdateApiTest()
	{
		final GigaMap<Entity> map = GigaMap.New();
		assertTrue(map.isEmpty());
		assertEquals(0, map.size());

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertTrue(map.isEmpty());
			assertEquals(0, map.size());
		}

		GigaMap<Entity> loaded = GigaMap.New();
		try (EmbeddedStorageManager manager = EmbeddedStorage.start(loaded, this.tempDir)) {
			assertTrue(loaded.isEmpty());
			assertEquals(0, loaded.size());
		}
	}
	
	@Test
	void one()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.add(Entity.Random());
		assertEquals(1, map.size());

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertEquals(1, map.size());
			map.add(Entity.Random());
			map.store();
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			assertEquals(2, loaded.size());
			loaded.add(Entity.Random());
			loaded.store();
		}
	}

	@Test
	void oneUpdateApi()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.add(Entity.Random());
		assertEquals(1, map.size());

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertEquals(1, map.size());
		}

		final GigaMap<Entity> loaded = GigaMap.New();
		try (EmbeddedStorageManager manager = EmbeddedStorage.start(loaded, this.tempDir)) {
			assertEquals(1, loaded.size());
			loaded.add(Entity.Random());
			loaded.store();
		}
	}
	
	@Test
	void addSingle()
	{
		final int             initialSize = 100;
		final GigaMap<Entity> map         = fixedSizeMap(initialSize, EntityCreator.FLAT);
		map.add(Entity.Random());
		assertEquals(initialSize + 1, map.size());

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertEquals(initialSize + 1, map.size());
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			assertEquals(initialSize + 1, loaded.size());
		}
	}
	
	@Test
	void addNull()
	{
		final GigaMap<Entity> map = emptyMap();
		assertThrows(IllegalArgumentException.class, () -> map.add(null));

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertThrows(IllegalArgumentException.class, () -> map.add(null));
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			assertThrows(IllegalArgumentException.class, () -> loaded.add(null));
		}
	}

	@Test
	void addNullUpdateApi()
	{
		final GigaMap<Entity> map = emptyMap();
		assertThrows(IllegalArgumentException.class, () -> map.add(null));

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertThrows(IllegalArgumentException.class, () -> map.add(null));
		}

		final GigaMap<Entity> loaded = GigaMap.New();
		try (EmbeddedStorageManager manager = EmbeddedStorage.start(loaded, this.tempDir)) {
			assertThrows(IllegalArgumentException.class, () -> loaded.add(null));
		}
	}

	@Test
	void addMultiple(@TempDir final Path tempDirLocal)
	{
		final int             initialSize = 100;
		final GigaMap<Entity> map         = fixedSizeMap(initialSize, EntityCreator.FLAT);
		final List<Entity>    add         = Entity.RandomList(initialSize);
		map.addAll(add);
		assertEquals(initialSize + add.size(), map.size());

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDirLocal)) {
			assertEquals(initialSize + add.size(), map.size());
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDirLocal)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			assertEquals(initialSize + add.size(), loaded.size());
		}
	}

	@Test
	void addMultipleUpdateApi()
	{
		final int             initialSize = 100;
		final GigaMap<Entity> map         = fixedSizeMap(initialSize, EntityCreator.FLAT);
		final List<Entity>    add         = Entity.RandomList(initialSize);
		map.addAll(add);
		assertEquals(initialSize + add.size(), map.size());

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertEquals(initialSize + add.size(), map.size());
		}

		final GigaMap<Entity> loaded = GigaMap.New();
		try (EmbeddedStorageManager manager = EmbeddedStorage.start(loaded, this.tempDir)) {
			assertEquals(initialSize + add.size(), loaded.size());
		}
	}

	@Test
	void release()
	{
		final GigaMap<Entity> map  = randomSizeMap(100, EntityCreator.FLAT);
		final long            size = map.size();
		map.release();
				
		assertEquals(size, map.size());
		
		map.add(Entity.Random());
		
		assertEquals(size + 1, map.size());
		
		assertNotNull(map.get(0L));

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertEquals(size + 1, map.size());
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			assertEquals(size + 1, loaded.size());
		}
	}
	
	@Test
	void removeSingle()
	{
		final int             initialSize = 100;
		final List<Entity>    entities    = Entity.FixedList(initialSize);
		final GigaMap<Entity> map         = emptyMap();
		map.addAll(entities);
		
		map.remove(entities.get(0));
		assertEquals(initialSize - 1, map.size());

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertEquals(initialSize - 1, map.size());
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			assertEquals(initialSize - 1, loaded.size());
		}
	}
	
	@Test
	void removeSingleWithIndex()
	{
		final int             initialSize = 100;
		final List<Entity>    entities    = Entity.FixedList(initialSize);
		final GigaMap<Entity> map         = emptyMap();
		map.addAll(entities);
		
		map.remove(entities.get(0), Entity.uuidIndex);
		assertEquals(initialSize - 1, map.size());

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertEquals(initialSize - 1, map.size());
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			assertEquals(initialSize - 1, loaded.size());
		}
	}
	
	@Test
	void iterationCount()
	{
		final int             entryCount = 100;
		final GigaMap<Entity> map        = fixedSizeMap(entryCount, EntityCreator.FLAT);
		final AtomicInteger   counter    = new AtomicInteger();
		map.iterate(element -> counter.incrementAndGet());
		assertEquals(entryCount, counter.get());

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertEquals(entryCount, counter.get());
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			final AtomicInteger     loadedCounter = new AtomicInteger();
			loaded.iterate(element -> loadedCounter.incrementAndGet());
			assertEquals(entryCount, loadedCounter.get());
		}
	}
	
	@Test
	void indexedIterationCount()
	{
		final int                  entryCount = 100;
		final GigaMap<Entity>      map        = fixedSizeMap(entryCount, EntityCreator.FLAT);
		final LongSummaryStatistics stats      = new LongSummaryStatistics();
		map.iterateIndexed((id, element) -> stats.accept(id));
		assertEquals(entryCount, stats.getCount());
		assertEquals(entryCount, stats.getMax() + 1);

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertEquals(entryCount, stats.getCount());
			assertEquals(entryCount, stats.getMax() + 1);
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			final LongSummaryStatistics loadedStats = new LongSummaryStatistics();
			loaded.iterateIndexed((id, element) -> loadedStats.accept(id));
			assertEquals(entryCount, loadedStats.getCount());
			assertEquals(entryCount, loadedStats.getMax() + 1);
		}
	}
	
	@Test
	void indexTest()
	{
		final String word   = "red";
		final Entity entity = Entity.Random().setWord(word);
		assertTrue(Entity.wordIndex.test(entity, word));
	}
	
	@Test
	void queryCount()
	{
		final GigaMap<Entity>   map   = fixedSizeMap(
			10_000,
			(index, faker) -> Entity.RandomFlat(faker).setIntValue(index % 100)
		);
		final GigaQuery<Entity> query = map.query(Entity.intValueIndex.is(0));
		assertEquals(100, query.count());

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertEquals(100, query.count());
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			final GigaQuery<Entity> loadedQuery = loaded.query(Entity.intValueIndex.is(0));
			assertEquals(100, loadedQuery.count());
		}
	}
	
	@Test
	void queryCount2()
	{
		final GigaMap<Entity> map       = fixedSizeMap(100_000, EntityCreator.FLAT);
		final int             searchFor = 1;
		final AtomicInteger   expected  = new AtomicInteger();
		map.iterate(i -> {
			if(i.getIntValue() == searchFor)
			{
				expected.incrementAndGet();
			}
		});
		final GigaQuery<Entity> query = map.query(Entity.intValueIndex.is(searchFor));
		assertEquals(expected.get(), query.count());

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertEquals(expected.get(), query.count());
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			final GigaQuery<Entity> loadedQuery = loaded.query(Entity.intValueIndex.is(searchFor));
			assertEquals(expected.get(), loadedQuery.count());
		}
	}
	
	@Test
	void queryIterationComparison()
	{
		final GigaMap<Entity>   map   = fixedSizeMap(
			10_000,
			(index, faker) -> Entity.RandomFlat(faker).setIntValue(index % 100)
		);
		final GigaQuery<Entity> query           = map.query(Entity.intValueIndex.is(0));
		final AtomicInteger     forEachCounter  = new AtomicInteger();
		final AtomicInteger     iteratorCounter = new AtomicInteger();
		final AtomicInteger     consumerCounter = new AtomicInteger();
		query.forEach(e -> forEachCounter.incrementAndGet());
		query.iterator().forEachRemaining(e -> iteratorCounter.incrementAndGet());
		query.execute(e -> consumerCounter.incrementAndGet());
		assertEquals(forEachCounter.get(), iteratorCounter.get());
		assertEquals(forEachCounter.get(), consumerCounter.get());
		assertEquals(iteratorCounter.get(), consumerCounter.get());

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertEquals(forEachCounter.get(), iteratorCounter.get());
			assertEquals(forEachCounter.get(), consumerCounter.get());
			assertEquals(iteratorCounter.get(), consumerCounter.get());
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			final GigaQuery<Entity> loadedQuery = loaded.query(Entity.intValueIndex.is(0));
			final AtomicInteger     loadedForEachCounter  = new AtomicInteger();
			final AtomicInteger     loadedIteratorCounter = new AtomicInteger();
			final AtomicInteger     loadedConsumerCounter = new AtomicInteger();
			loadedQuery.forEach(e -> loadedForEachCounter.incrementAndGet());
			loadedQuery.iterator().forEachRemaining(e -> loadedIteratorCounter.incrementAndGet());
			loadedQuery.execute(e -> loadedConsumerCounter.incrementAndGet());
			assertEquals(loadedForEachCounter.get(), loadedIteratorCounter.get());
			assertEquals(loadedForEachCounter.get(), loadedConsumerCounter.get());
			assertEquals(loadedIteratorCounter.get(), loadedConsumerCounter.get());
		}
	}
	
	@Test
	void queryNot()
	{
		final GigaMap<Entity>   map   = fixedSizeMap(100, EntityCreator.FLAT);
		final GigaQuery<Entity> query = map.query(Entity.wordIndex.not("red"));
		query.execute(entity -> assertNotNull(entity));

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			query.execute(entity -> assertNotNull(entity));
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			final GigaQuery<Entity> loadedQuery = loaded.query(Entity.wordIndex.not("red"));
			loadedQuery.execute(entity -> assertNotNull(entity));
		}
	}
	
	@Test
	void update()
	{
		final GigaMap<Entity> map     = fixedSizeMap(10, EntityCreator.FLAT);
		final Entity          entity  = map.get(5);
		final String          newWord = "updated";
		
		map.update(entity, e -> e.setWord(newWord));
		assertEquals(newWord, entity.getWord());
		
		final Entity found = map.query(Entity.wordIndex.is(newWord)).stream().findFirst().orElse(null);
		assertSame(entity, found);

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			final Entity loadedEntity = map.get(5);
			assertEquals(newWord, loadedEntity.getWord());
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			final Entity loadedEntity = loaded.get(5);
			assertEquals(newWord, loadedEntity.getWord());
		}
	}
	
	// TODO test peeking in combination with unloading
	@Test
	void peek()
	{
		final GigaMap<Entity> map = fixedSizeMap(10, EntityCreator.FLAT);
		assertNotNull(map.peek(1));
		assertNull(map.peek(100));

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertNotNull(map.peek(1));
			assertNull(map.peek(100));
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			assertNull(loaded.peek(1)); // not loaded after start of storage manager
			loaded.get(1);
			assertNotNull(loaded.peek(1)); // loaded after get
			assertNull(loaded.peek(100));
		}
	}
	
	@Test
	void getIndexByName()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().add(Entity.intValueIndex);
		map.index().bitmap().add(Entity.wordIndex);
		assertNotNull(map.index().bitmap().get(Integer.class, Entity.intValueIndex.name()));
		assertNotNull(map.index().bitmap().get(Entity.wordIndex.name()));

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir)) {
			assertNotNull(map.index().bitmap().get(Integer.class, Entity.intValueIndex.name()));
			assertNotNull(map.index().bitmap().get(Entity.wordIndex.name()));
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir)) {
			final GigaMap<Entity> loaded = (GigaMap<Entity>) manager.root();
			assertNotNull(loaded.index().bitmap().get(Integer.class, Entity.intValueIndex.name()));
			assertNotNull(loaded.index().bitmap().get(Entity.wordIndex.name()));
		}
	}
	
	private static GigaMap<Entity> fixedSizeMap(final int entryCount, final EntityCreator entityCreator)
	{
		final GigaMap<Entity> map = emptyMap();
		
		if(entryCount > 0)
		{
			final Faker faker = new Faker();
			for(int i = 0; i < entryCount; i++)
			{
				map.add(entityCreator.create(i, faker));
			}
		}
		
		return map;
	}
	
	private static GigaMap<Entity> randomSizeMap(final int maxEntries, final EntityCreator entityCreator)
	{
		final GigaMap<Entity> map = emptyMap();
		
		if(maxEntries > 0)
		{
			final Faker faker = new Faker();
			final int   max   = faker.random().nextInt(1, maxEntries);
			for(int i = 0; i < max; i++)
			{
				map.add(entityCreator.create(i, faker));
			}
		}
		
		return map;
	}
	
	private static GigaMap<Entity> emptyMap()
	{
		final GigaMap<Entity>       map     = GigaMap.New();
		
		final BitmapIndices<Entity> indices = map.index().bitmap();
		indices.add(Entity.firstCharIndex);
		indices.add(Entity.wordIndex);
		indices.add(Entity.intValueIndex);
		indices.add(Entity.doubleValueIndex);
		indices.add(Entity.localDateTimeIndex);
		indices.add(Entity.uuidIndex);
		indices.add(Entity.subEntityFloatValueIndex);
		indices.add(Entity.subEntityCharValueIndex);
		indices.add(Entity.subEntityInstantMilliIndex);
		indices.add(Entity.numberRangeIndex);
		indices.setIdentityIndices(X.Enum(Entity.uuidIndex));
		
		return map;
	}
	
	
	static interface EntityCreator
	{
		public static EntityCreator DEEP = (index, faker) -> Entity.Random(faker);
		public static EntityCreator FLAT = (index, faker) -> Entity.RandomFlat(faker);
		
		Entity create(int index, Faker faker);
	}
	
}
