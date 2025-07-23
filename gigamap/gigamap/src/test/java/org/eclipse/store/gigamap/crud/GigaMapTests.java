
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

import com.github.javafaker.Faker;
import org.eclipse.serializer.util.X;
import org.eclipse.store.gigamap.data.Entity;
import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaIterator;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.GigaQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;


public class GigaMapTests
{
	@Test
	void empty()
	{
		final GigaMap<Entity> map = GigaMap.New();
		assertTrue(map.isEmpty());
		assertEquals(0, map.size());
	}
	
	@Test
	void one()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.add(Entity.Random());
		assertEquals(1, map.size());
	}
	
	@Test
	void addSingle()
	{
		final int             initialSize = 100;
		final GigaMap<Entity> map         = fixedSizeMap(initialSize, EntityCreator.FLAT);
		map.add(Entity.Random());
		assertEquals(initialSize + 1, map.size());
	}
	
	@Test
	void addNull()
	{
		final GigaMap<Entity> map = emptyMap();
		assertThrows(IllegalArgumentException.class, () -> map.add(null));
	}
	
	@Test
	void addMultiple()
	{
		final int             initialSize = 100;
		final GigaMap<Entity> map         = fixedSizeMap(initialSize, EntityCreator.FLAT);
		final List<Entity>    add         = Entity.RandomList(initialSize);
		map.addAll(add);
		assertEquals(initialSize + add.size(), map.size());
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
	}
	
	@Test
	void removeAll()
	{
		final GigaMap<Entity> map = fixedSizeMap(100, EntityCreator.FLAT);
		map.removeAll();
		assertTrue(map.isEmpty());
		
		final Entity entity = Entity.Random();
		
		// test if adding and querying still works after removeAll
		map.add(entity);
		assertEquals(1, map.size());		
		assertEquals(1, map.query(Entity.uuidIndex.is(entity.getUuid())).count());
	}
	
	@Test
	void iterationCount()
	{
		final int             entryCount = 100;
		final GigaMap<Entity> map        = fixedSizeMap(entryCount, EntityCreator.FLAT);
		final AtomicInteger   counter    = new AtomicInteger();
		map.iterate(element -> counter.incrementAndGet());
		assertEquals(entryCount, counter.get());
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
		try(final GigaIterator<Entity> iterator = query.iterator())
		{
			iterator.forEachRemaining(e -> iteratorCounter.incrementAndGet());
		}
		query.execute(e -> consumerCounter.incrementAndGet());
		assertEquals(forEachCounter.get(), iteratorCounter.get());
		assertEquals(forEachCounter.get(), consumerCounter.get());
		assertEquals(iteratorCounter.get(), consumerCounter.get());
	}
	
	@Test
	void queryNot()
	{
		final GigaMap<Entity>   map   = fixedSizeMap(100, EntityCreator.FLAT);
		final GigaQuery<Entity> query = map.query(Entity.wordIndex.not("red"));
		query.execute(Assertions::assertNotNull);
	}
	
	@Test
	void set()
	{
		final GigaMap<Entity> map   = emptyMap();
		final Entity          first = Entity.Random();
		map.addAll(first, Entity.Random(), Entity.Random());
		final Entity replaced = map.set(0, Entity.Random());
		assertSame(first, replaced);
		assertThrows(IllegalArgumentException.class, () -> map.set(1000, Entity.Random()));
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
	}
	
	@Test
	void updateError()
	{
		final GigaMap<Entity> map     = emptyMap();
		map.add(Entity.Random().setWord("a"));
		map.add(Entity.Random().setWord("b"));
		map.add(Entity.Random().setWord("c"));
		map.add(Entity.Random().setWord("d"));
		
		final String newWord = "newWord";
		assertThrows(ArithmeticException.class, () ->
			map.update(map.get(0), e -> {
				e.setWord(newWord);
				throw new ArithmeticException();
			})
		);
		
		// changes should be reverted in index
		assertEquals(0, map.query(Entity.wordIndex.is(newWord)).count());
	}
	
	// TODO test peeking in combination with unloading
	@Test
	void peek()
	{
		final GigaMap<Entity> map = fixedSizeMap(10, EntityCreator.FLAT);
		assertNotNull(map.peek(1));
		assertNull(map.peek(100));
	}
	
	@Test
	void getIndexByName()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().add(Entity.intValueIndex);
		map.index().bitmap().add(Entity.wordIndex);
		assertNotNull(map.index().bitmap().get(Integer.class, Entity.intValueIndex.name()));
		assertNotNull(map.index().bitmap().get(Entity.wordIndex.name()));
	}
	
	@Test
	void indexKeyResolving()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().add(Entity.intValueIndex);
		map.add(Entity.Random().setIntValue(0));
		map.add(Entity.Random().setIntValue(1));
		map.add(Entity.Random().setIntValue(2));
		map.add(Entity.Random().setIntValue(2));
		final List<Integer> keys = Entity.intValueIndex.resolveKeys(map);
		assertEquals(List.of(0, 1, 2), keys);
	}
	
	@Test
	void toString_()
	{
		final GigaMap<String> map = GigaMap.New();
		map.addAll("a", "b", "c", "d", "e", "f");
		assertEquals("[]", GigaMap.New().toString(10));
		assertEquals("[a, b, c]", map.toString(3));
		assertEquals("[d, e, f]", map.toString(3, 3));
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
