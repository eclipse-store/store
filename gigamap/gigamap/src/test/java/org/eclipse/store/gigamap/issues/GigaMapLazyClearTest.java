package org.eclipse.store.gigamap.issues;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.serializer.memory.MemoryStatistics;
import org.eclipse.serializer.memory.MemoryStatisticsProvider;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.LazyReferenceManager;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * GigaMap segments are lazy references cleared by the {@link LazyReferenceManager}. A segment changed but not yet
 * stored is pinned by a usage mark only; clearing it would drop the change while the store still persists the index
 * and size updates. Every test checks the map in memory and after a restart.
 * <p>
 * Between clearing and the store the map is not accessed: an access may re-resolve a cleared segment via the object
 * registry and hide a lost change.
 */
public class GigaMapLazyClearTest
{
	// 16 entities per segment: many lazy references with few entities
	static final int LOW_LEVEL_EXPONENT = 4;
	// 25 full segments and one with a single entity: the add goes into a stored, partly filled segment
	static final int INITIAL            = 401;
	static final int VALUES             = 20;

	static final ValueIndexer VALUE_INDEXER = new ValueIndexer();

	@TempDir
	Path dir;

	private EmbeddedStorageManager   storage;
	private MemoryStatisticsProvider previousProvider;

	// lazy references registered before the map of a test, e.g. by earlier tests: not counted as cleared segments
	private Set<Lazy<?>> foreignReferences;

	@AfterEach
	void cleanUp()
	{
		try
		{
			if(this.storage != null && this.storage.isRunning())
			{
				this.storage.shutdown();
			}
		}
		finally
		{
			if(this.previousProvider != null)
			{
				MemoryStatisticsProvider.set(this.previousProvider);
			}
		}
	}

	/**
	 * {@link LazyReferenceManager#clear()} is what the JMX operation unloadAll calls.
	 */
	@Test
	void clearBetweenAddAndStoreKeepsAdd()
	{
		final GigaMap<Entity> map   = this.startWithEntities();
		final int[]           model = Arrays.copyOf(initialModel(), INITIAL + 1);

		assertEquals(INITIAL, map.add(new Entity(3)));
		model[INITIAL] = 3;

		LazyReferenceManager.get().clear();
		assertTrue(this.clearedMapReferences() > 0, "precondition: clear() must have cleared segments of the map");
		map.store();

		this.assertPersisted(model, new boolean[model.length]);
	}

	@Test
	void clearBetweenRemoveAndStoreKeepsRemoval()
	{
		final GigaMap<Entity> map     = this.startWithEntities();
		final int[]           model   = initialModel();
		final boolean[]       removed = new boolean[model.length];

		map.removeById(9);
		removed[9] = true;

		LazyReferenceManager.get().clear();
		assertTrue(this.clearedMapReferences() > 0, "precondition: clear() must have cleared segments of the map");
		map.store();

		this.assertPersisted(model, removed);
	}

	@Test
	void clearBetweenUpdateAndStoreKeepsUpdate()
	{
		final GigaMap<Entity> map   = this.startWithEntities();
		final int[]           model = initialModel();

		map.update(5L, e -> e.value = VALUES + 1);
		model[5] = VALUES + 1;

		LazyReferenceManager.get().clear();
		assertTrue(this.clearedMapReferences() > 0, "precondition: clear() must have cleared segments of the map");
		map.store();

		this.assertPersisted(model, new boolean[model.length]);
	}

	/**
	 * Timeout clearing (1 ms timeout, so no grace time) between random mutations, run synchronously.
	 */
	@Test
	void timeoutClearingBetweenMutationsKeepsData()
	{
		final GigaMap<Entity> map = this.startWithEntities();
		// beyond the 1 ms timeout
		this.mutateWithClearing(map, Lazy.CheckerTimeout(1), 5);
	}

	/**
	 * Memory clearing of the default checker under post-GC memory pressure. Memory clearing applies only beyond the
	 * grace time (1 s), and every change touches its segment: the final changes age beyond it before the clearing.
	 */
	@Test
	void memoryPressureClearingBetweenMutationsKeepsData()
	{
		final GigaMap<Entity> map = this.startWithEntities();

		// only for the test thread: the cleanup thread of the running global manager keeps the real statistics
		final Thread testThread = Thread.currentThread();
		this.previousProvider = MemoryStatisticsProvider.set(new MemoryStatisticsProvider()
		{
			@Override
			public MemoryStatistics heapMemoryUsage()
			{
				// retained heap at the committed size: always above the default limit (0.8)
				return Thread.currentThread() == testThread
					? MemoryStatistics.New(1L << 30, 1L << 30, 1L << 30, 1L << 30, 1_000, -1, -1)
					: GigaMapLazyClearTest.this.previousProvider.heapMemoryUsage()
				;
			}

			@Override
			public MemoryStatistics nonHeapMemoryUsage()
			{
				return GigaMapLazyClearTest.this.previousProvider.nonHeapMemoryUsage();
			}
		});
		this.mutateWithClearing(map, Lazy.Checker(), 1_100);
	}

	private void mutateWithClearing(final GigaMap<Entity> map, final Lazy.Checker checker, final long finalAgeMs)
	{
		int[]     model   = initialModel();
		boolean[] removed = new boolean[model.length];
		int       size    = model.length;

		final Random random = new Random(196);
		for(int op = 1; op <= 1_500; op++)
		{
			final int kind = random.nextInt(10);
			final int id   = random.nextInt(size);
			if(kind < 5)
			{
				if(!removed[id])
				{
					final int value = random.nextInt(VALUES);
					map.update(id, e -> e.value = value);
					model[id] = value;
				}
			}
			else if(kind < 7)
			{
				final int value = random.nextInt(VALUES);
				assertEquals(size, map.add(new Entity(value)));
				if(size == model.length)
				{
					model   = Arrays.copyOf(model, size * 2);
					removed = Arrays.copyOf(removed, size * 2);
				}
				model[size++] = value;
			}
			else if(kind < 8)
			{
				if(!removed[id])
				{
					map.removeById(id);
					removed[id] = true;
				}
			}
			else
			{
				final Entity entity = map.get(id);
				if(removed[id])
				{
					assertNull(entity, "removed entity " + id);
				}
				else
				{
					assertNotNull(entity, "entity " + id);
					assertEquals(model[id], entity.value, "value of entity " + id);
				}
			}

			if(op % 10 == 0)
			{
				LazyReferenceManager.get().cleanUp(checker);
			}
			if(op % 100 == 0)
			{
				// directly after clearing, without an access that could re-resolve a cleared changed segment
				map.store();
			}
		}
		map.store();
		assertModel(map, Arrays.copyOf(model, size), Arrays.copyOf(removed, size));

		/*
		 * A wrongly cleared changed segment is re-resolved by later accesses and stored with a later store. A loss only
		 * shows if the store directly follows the clearing and nothing accesses the map until the restart.
		 */
		if(model.length < size + 3)
		{
			model   = Arrays.copyOf(model, size + 3);
			removed = Arrays.copyOf(removed, size + 3);
		}
		for(int i = 0; i < 3; i++)
		{
			final int id = random.nextInt(size);
			if(!removed[id])
			{
				map.removeById(id);
				removed[id] = true;
			}
			assertEquals(size, map.add(new Entity(i)));
			model[size++] = i;
		}
		// the changed segments must be old enough for the checker, else a missing usage-mark check goes unnoticed
		sleep(finalAgeMs);
		LazyReferenceManager.get().cleanUp(checker);
		assertTrue(this.clearedMapReferences() > 0, "precondition: segments of the map must have been cleared");
		map.store();

		this.assertPersisted(Arrays.copyOf(model, size), Arrays.copyOf(removed, size));
	}

	private static void sleep(final long ms)
	{
		try
		{
			Thread.sleep(ms);
		}
		catch(final InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	private long clearedMapReferences()
	{
		final long[] count = new long[1];
		LazyReferenceManager.get().iterate(lazy ->
		{
			if(!this.foreignReferences.contains(lazy) && lazy.isStored() && !lazy.isLoaded())
			{
				count[0]++;
			}
		});
		return count[0];
	}

	private GigaMap<Entity> startWithEntities()
	{
		this.foreignReferences = Collections.newSetFromMap(new IdentityHashMap<>());
		LazyReferenceManager.get().iterate(this.foreignReferences::add);

		final GigaMap<Entity> map = GigaMap.New(LOW_LEVEL_EXPONENT);
		map.index().bitmap().add(VALUE_INDEXER);
		for(final int value : initialModel())
		{
			map.add(new Entity(value));
		}
		this.storage = EmbeddedStorage.start(map, this.dir);
		return map;
	}

	private static int[] initialModel()
	{
		final int[] model = new int[INITIAL];
		for(int i = 0; i < model.length; i++)
		{
			model[i] = i % VALUES;
		}
		return model;
	}

	@SuppressWarnings("unchecked")
	private void assertPersisted(final int[] model, final boolean[] removed)
	{
		this.storage.shutdown();
		this.storage = EmbeddedStorage.start(this.dir);
		assertModel((GigaMap<Entity>)this.storage.root(), model, removed);
	}

	private static void assertModel(final GigaMap<Entity> map, final int[] model, final boolean[] removed)
	{
		final int[] expectedCounts = new int[VALUES + 2];
		long        expectedSize   = 0;
		for(int id = 0; id < model.length; id++)
		{
			final Entity entity = map.get(id);
			if(removed[id])
			{
				assertNull(entity, "removed entity " + id);
				continue;
			}
			assertNotNull(entity, "entity " + id);
			assertEquals(model[id], entity.value, "value of entity " + id);
			expectedCounts[model[id]]++;
			expectedSize++;
		}
		assertEquals(expectedSize, map.size(), "size");
		for(int value = 0; value < expectedCounts.length; value++)
		{
			assertEquals(expectedCounts[value], map.query(VALUE_INDEXER.is(value)).count(), "index count of value " + value);
		}
	}

	public static class Entity
	{
		int value;

		public Entity(final int value)
		{
			this.value = value;
		}
	}

	static class ValueIndexer extends IndexerInteger.Abstract<Entity>
	{
		@Override
		protected Integer getInteger(final Entity entity)
		{
			return entity.value;
		}
	}
}
