package org.eclipse.store.gigamap.types;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.Test;

/**
 * {@code ThreadedIterator#calculateLevel1SegmentCount} derives its off-heap registry size from
 * {@link GigaMap#highestUsedId()}, which has two edge cases that its former {@code int} narrowing
 * papered over (internal issue #133).
 * <p>
 * An empty map reports {@code highestUsedId() == -1} ({@code nextFreeId() - 1}), and
 * {@code -1L >>> 12} is {@code 2^52 - 1}. The old {@code (int)} cast truncated that to {@code -1} and
 * {@code +1} landed on exactly 0, which is the value the iterator needs to report "no data" - so the
 * empty case worked purely by accident of the overflow. It now has an explicit branch, and this test
 * pins it, because the whole gigamap suite has no other coverage for a threaded query on an empty map.
 * <p>
 * The other edge is the opposite end: the registry index is an {@code int}, so a
 * {@code highestUsedId} beyond roughly 2^43 cannot be represented. It is rejected explicitly instead
 * of allocating from a negative count.
 * <p>
 * {@code IterationThreadProvider.Pooling} with a fixed thread count is what forces
 * {@code GigaMap.Default#createIterator} onto the {@link ThreadedIterator} path.
 */
public class ThreadedIteratorSegmentCountTest
{
	static class Item
	{
		final String name;

		Item(final String name)
		{
			super();
			this.name = name;
		}
	}

	static class NameIndexer extends IndexerString.Abstract<Item>
	{
		@Override
		protected String getString(final Item entity)
		{
			return entity.name;
		}
	}

	private static final String SEARCHED_KEY = "item3";

	/** Grace period for a worker thread to reach its uncaught exception handler, if it has one to reach. */
	private static final long WORKER_SETTLE_MS = 200;

	@Test
	public void threadedIterationOverAnEmptyMapYieldsNothing()
	{
		final GigaMap<Item>   map  = GigaMap.New();
		final NameIndexer     name = new NameIndexer();
		map.index().bitmap().add(name);

		assertEquals(-1L, map.highestUsedId(), "an empty map reports nextFreeId() - 1");
		assertEquals(0, countThreaded(map, name, "anything"), "and a threaded query over it finds nothing");
	}

	/**
	 * The empty map allocates no registry, and a zero-length allocation has the address 0, on which
	 * {@code ThreadLogic}'s bounds arithmetic fails. The failure was invisible: it happened on a worker
	 * thread, and the reader never consults the registry in that case, so the iteration still produced
	 * the correct (empty) result. Hence this case asserts against the worker threads rather than the
	 * result - no iteration may leave a worker dead behind, whether there is data or not.
	 */
	@Test
	public void threadedIterationLeavesNoWorkerThreadFailing() throws InterruptedException
	{
		final Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
		final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> failures.add(throwable));
		try
		{
			for(final int entityCount : new int[]{0, 1, 1000})
			{
				final GigaMap<Item> map  = GigaMap.New();
				final NameIndexer   name = new NameIndexer();
				map.index().bitmap().add(name);
				for(int i = 0; i < entityCount; i++)
				{
					map.add(new Item("item" + i % 10));
				}

				countThreaded(map, name, SEARCHED_KEY);

				// the worker threads run concurrently with the reader, so give a failing one time to die
				Thread.sleep(WORKER_SETTLE_MS);
				assertTrue(failures.isEmpty(),
					"a threaded query over " + entityCount + " entities must not fail a worker thread, got "
					+ failures);
			}
		}
		finally
		{
			Thread.setDefaultUncaughtExceptionHandler(previous);
		}
	}

	/**
	 * The counterpart, so the empty-map branch cannot be satisfied by a count that is always 0.
	 */
	@Test
	public void threadedIterationOverAPopulatedMapYieldsTheMatches()
	{
		final GigaMap<Item>   map  = GigaMap.New();
		final NameIndexer     name = new NameIndexer();
		map.index().bitmap().add(name);
		for(int i = 0; i < 1000; i++)
		{
			map.add(new Item("item" + i % 10));
		}

		assertEquals(100, countThreaded(map, name, "item3"), "every match must be iterated");
		assertEquals(0  , countThreaded(map, name, "nope") , "and a miss must yield nothing");
	}

	/**
	 * The registry is allocated with one slot per registry segment, each covering
	 * {@code REGISTRY_SEGMENT_SIZE} level1 segments. It used to be sized by the level1 segment count
	 * instead, i.e. up to 64 times too large. Under-allocating it would be far worse than the waste it
	 * replaced - the worker threads and the registry scroll would run off the end of the block - so this
	 * case deliberately spans more than one registry segment and checks that every match still arrives.
	 */
	@Test
	public void threadedIterationAcrossMultipleRegistrySegmentsYieldsEveryMatch()
	{
		final long idsPerRegistrySegment =
			(long)ThreadedIterator.REGISTRY_SEGMENT_SIZE * BitmapLevel3.LEVEL_1_ID_COUNT;

		final GigaMap<Item> map  = GigaMap.New();
		final NameIndexer   name = new NameIndexer();
		map.index().bitmap().add(name);

		final int entityCount = (int)idsPerRegistrySegment + 40_000;
		int       expected    = 0;
		for(int i = 0; i < entityCount; i++)
		{
			final String key = "item" + i % 10;
			map.add(new Item(key));
			if(SEARCHED_KEY.equals(key))
			{
				expected++;
			}
		}

		assertTrue(map.highestUsedId() > idsPerRegistrySegment,
			"the map must span more than one registry segment for this case to mean anything");
		assertEquals(expected, countThreaded(map, name, SEARCHED_KEY),
			"every match across all registry segments must be iterated");
	}

	/**
	 * The upper edge. The registry index is an {@code int}, so beyond roughly 2^43 ids the count is not
	 * representable - and the registry it sizes would need 16 GB of off-heap memory, so the limit is a
	 * reporting problem, not one worth widening the type for.
	 * <p>
	 * Reachable cheaply because the seeded id counter can start at 2^43 while exponents 20/20 keep that
	 * id at level3 index 8, so the whole map costs about 26 MB.
	 */
	@Test
	public void threadedIterationBeyondTheRepresentableSegmentCountIsRejected()
	{
		final GigaMap.Default<Item> map = new GigaMap.Default<>(
			new GigaMap.DefaultEqualator<>(), 20, 20, 0, 8, 0, 1L << 43
		);
		final NameIndexer name = new NameIndexer();
		map.index().bitmap().add(name);
		map.add(new Item("item0"));

		assertEquals(1L << 43, map.highestUsedId(), "the map hands out the seeded id");

		final IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> countThreaded(map, name, "item0"),
			"a segment count beyond int must be reported, not silently truncated");
		assertTrue(e.getMessage().contains("8796093022208"),
			"the message must name the offending highestUsedId, was: " + e.getMessage());
	}

	private static int countThreaded(final GigaMap<Item> map, final NameIndexer name, final String key)
	{
		final IterationThreadProvider provider = IterationThreadProvider.Pooling(
			4, ThreadCountProvider.Fixed(2)
		);

		int count = 0;
		try(final GigaIterator<Item> iterator = map.query(provider).and(name.is(key)).iterator())
		{
			while(iterator.hasNext())
			{
				iterator.next();
				count++;
			}
		}

		return count;
	}

}
