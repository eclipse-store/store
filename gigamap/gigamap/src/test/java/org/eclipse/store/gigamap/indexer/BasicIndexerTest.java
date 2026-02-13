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

import org.eclipse.serializer.hashing.XHashing;
import org.eclipse.store.gigamap.types.BinaryIndexer;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class BasicIndexerTest
{
	@Test
	void empty()
	{
		final LongIndexer indexer = new LongIndexer();
		final GigaMap<Long> map = GigaMap.New();
		map.index().bitmap().add(indexer);
		assertEquals(0, map.query(indexer).is(1L).count());
		assertEquals(0, map.query(indexer).is(4097L).count());
	}

	@Test
	void simple()
	{
		final LongIndexer indexer = new LongIndexer();
		final GigaMap<Long> map = GigaMap.New();
		map.index().bitmap().add(indexer);
		map.add(1L);
		assertEquals(1, map.query(indexer).is(1L).count());
		assertEquals(0, map.query(indexer).is(2L).count());
	}

	@Test
	void gaps()
	{
		final LongIndexer indexer = new LongIndexer();
		final GigaMap<Long> map = GigaMap.New();
		map.index().bitmap().add(indexer);
		map.add(1L);
		map.add(5L);
		map.add(5L);
		map.add(100L);
		map.add(100L);
		map.add(100L);
		assertEquals(1, map.query(indexer).is(1L).count());
		assertEquals(2, map.query(indexer).is(5L).count());
		assertEquals(3, map.query(indexer).is(100L).count());
		assertEquals(0, map.query(indexer).is(2L).count());
		assertEquals(0, map.query(indexer).is(10L).count());
		assertEquals(0, map.query(indexer).is(200L).count());
	}

	@Test
	void pow2()
	{
		final LongIndexer indexer = new LongIndexer();
		final GigaMap<Long> map = GigaMap.New();
		map.index().bitmap().add(indexer);
		for(int p = 2; p < 63; p++)
		{
			final long pow2 = 1L << p;
			map.add(pow2 - 1);
			map.add(pow2);
			map.add(pow2 + 1);
		}

		for(int p = 2; p < 63; p++)
		{
			final long pow2 = 1L << p;
			assertEquals(0, map.query(indexer).is(pow2 - 2).count());
			assertEquals(1, map.query(indexer).is(pow2 - 1).count());
			assertEquals(1, map.query(indexer).is(pow2).count());
			assertEquals(1, map.query(indexer).is(pow2 + 1).count());
			assertEquals(0, map.query(indexer).is(pow2 + 2).count());
		}
	}

	@Test
	void max()
	{
		final LongIndexer indexer = new LongIndexer();
		final GigaMap<Long> map = GigaMap.New();
		map.index().bitmap().add(indexer);
		map.add(Long.MAX_VALUE);
		assertEquals(0, map.query(indexer).is(1L).count());
		assertEquals(0, map.query(indexer).is(1024L).count());
		assertEquals(0, map.query(indexer).is(4096L).count());
		assertEquals(0, map.query(indexer).is(4097L).count());
		assertEquals(1, map.query(indexer).is(Long.MAX_VALUE).count());
	}

	@Test
	void error()
	{
		final LongIndexer indexer = new LongIndexer();
		final GigaMap<Long> map = GigaMap.New();
		map.index().bitmap().add(indexer);
		assertThrows(IllegalArgumentException.class, () -> map.add(-1L));
		assertThrows(IllegalArgumentException.class, () -> map.add(0L));
	}

	@Test
	void in()
	{
		final LongIndexer indexer = new LongIndexer();
		final GigaMap<Long> map = GigaMap.New();
		map.index().bitmap().add(indexer);
		map.add(1L);
		map.add(5L);
		map.add(10L);
		map.add(20L);

		// Test with single match
		assertEquals(1, map.query(indexer).in(1L).count());

		// Test with multiple matches
		assertEquals(2, map.query(indexer).in(1L, 5L).count());
		assertEquals(3, map.query(indexer).in(1L, 5L, 10L).count());
		assertEquals(4, map.query(indexer).in(1L, 5L, 10L, 20L).count());

		// Test with non-existent values
		assertEquals(0, map.query(indexer).in(2L, 3L).count());
		assertEquals(2, map.query(indexer).in(1L, 2L, 5L, 15L).count());

		// Test with empty array
		assertEquals(0, map.query(indexer).in().count());
	}

	@Test
	void remove()
	{
		final LongIndexer indexer = new LongIndexer();
		final GigaMap<Long> map = GigaMap.New(XHashing.hashEqualityValue());
		map.index().bitmap().add(indexer);
		map.add(1L);
		map.add(5L);
		map.add(10L);

		// Verify initial state
		assertEquals(1, map.query(indexer).is(1L).count());
		assertEquals(1, map.query(indexer).is(5L).count());
		assertEquals(1, map.query(indexer).is(10L).count());

		// Remove one element
		map.remove(5L);
		assertEquals(1, map.query(indexer).is(1L).count());
		assertEquals(0, map.query(indexer).is(5L).count());
		assertEquals(1, map.query(indexer).is(10L).count());

		// Remove another element
		map.remove(1L);
		assertEquals(0, map.query(indexer).is(1L).count());
		assertEquals(0, map.query(indexer).is(5L).count());
		assertEquals(1, map.query(indexer).is(10L).count());

		// Remove last element
		map.remove(10L);
		assertEquals(0, map.query(indexer).is(1L).count());
		assertEquals(0, map.query(indexer).is(5L).count());
		assertEquals(0, map.query(indexer).is(10L).count());
	}

	@Test
	void wrapper()
	{
		// Create a regular indexer
		final LongIndexer originalIndexer = new LongIndexer();

		// Create a wrapped indexer
		final BinaryIndexer<Long> wrappedIndexer = BinaryIndexer.Wrap(originalIndexer);

		// Test both indexers
		final GigaMap<Long> map = GigaMap.New();
		map.index().bitmap().add(wrappedIndexer);

		map.add(1L);
		map.add(5L);
		map.add(10L);

		// Verify the wrapped indexer works correctly
		assertEquals(1, map.query(wrappedIndexer).is(1L).count());
		assertEquals(1, map.query(wrappedIndexer).is(5L).count());
		assertEquals(1, map.query(wrappedIndexer).is(10L).count());
		assertEquals(0, map.query(wrappedIndexer).is(2L).count());

		// Test in() method with wrapped indexer
		assertEquals(2, map.query(wrappedIndexer).in(1L, 5L).count());
		assertEquals(3, map.query(wrappedIndexer).in(1L, 5L, 10L).count());

		// Verify error handling still works
		assertThrows(IllegalArgumentException.class, () -> map.add(-1L));
		assertThrows(IllegalArgumentException.class, () -> map.add(0L));
	}

	@Test
	void keyType()
	{
		final LongIndexer indexer = new LongIndexer();
		assertEquals(Long.class, indexer.keyType());

		final BinaryIndexer<Long> wrappedIndexer = BinaryIndexer.Wrap(indexer);
		assertEquals(Long.class, wrappedIndexer.keyType());
	}

	@Test
	void largeValues()
	{
		final LongIndexer indexer = new LongIndexer();
		final GigaMap<Long> map = GigaMap.New();
		map.index().bitmap().add(indexer);

		// Test values close to Long.MAX_VALUE
		final long nearMax1 = Long.MAX_VALUE - 1;
		final long nearMax2 = Long.MAX_VALUE - 100;

		map.add(nearMax1);
		map.add(nearMax2);
		map.add(Long.MAX_VALUE);

		assertEquals(1, map.query(indexer).is(nearMax1).count());
		assertEquals(1, map.query(indexer).is(nearMax2).count());
		assertEquals(1, map.query(indexer).is(Long.MAX_VALUE).count());
		assertEquals(0, map.query(indexer).is(nearMax1 - 1).count());

		// Test in() method with large values
		assertEquals(2, map.query(indexer).in(nearMax1, Long.MAX_VALUE).count());
		assertEquals(3, map.query(indexer).in(nearMax1, nearMax2, Long.MAX_VALUE).count());
	}

	@Test
	void update()
	{
		final LongIndexer indexer = new LongIndexer();
		final GigaMap<Long> map = GigaMap.New();
		map.index().bitmap().add(indexer);

		// Add initial elements
		map.add(1L);
		map.add(5L);
		map.add(10L);

		// Verify initial state
		assertEquals(1, map.query(indexer).is(1L).count());
		assertEquals(1, map.query(indexer).is(5L).count());
		assertEquals(1, map.query(indexer).is(10L).count());

		// Replace an element with a new value
		map.replace(5L, 15L);

		// Verify the old value is gone and the new value is present
		assertEquals(1, map.query(indexer).is(1L).count());
		assertEquals(0, map.query(indexer).is(5L).count());
		assertEquals(1, map.query(indexer).is(10L).count());
		assertEquals(1, map.query(indexer).is(15L).count());

		// Replace another element
		map.replace(1L, 20L);

		// Verify the changes
		assertEquals(0, map.query(indexer).is(1L).count());
		assertEquals(1, map.query(indexer).is(20L).count());
		assertEquals(1, map.query(indexer).is(10L).count());
		assertEquals(1, map.query(indexer).is(15L).count());

		// Test in() method after updates
		assertEquals(2, map.query(indexer).in(10L, 15L).count());
		assertEquals(3, map.query(indexer).in(10L, 15L, 20L).count());
		assertEquals(0, map.query(indexer).in(1L, 5L).count());
	}

	@Test
	void setWithNoIndex()
	{
		final GigaMap<String> map = GigaMap.New();
		final long id = map.add("foo");
		assertEquals("foo", map.set(id, "bar"));
	}

	@Test
	void bulk()
	{
		final LongIndexer indexer = new LongIndexer();
		final GigaMap<Long> map = GigaMap.New();
		map.index().bitmap().add(indexer);

		// Test bulk add with array
		map.addAll(1L, 2L, 3L, 4L, 5L);

		// Verify all elements were added
		assertEquals(5, map.size());
		assertEquals(1, map.query(indexer).is(1L).count());
		assertEquals(1, map.query(indexer).is(5L).count());

		// Test bulk add with list
		final List<Long> moreValues = Arrays.asList(10L, 20L, 30L);
		map.addAll(moreValues);

		// Verify the additional elements were added
		assertEquals(8, map.size());
		assertEquals(1, map.query(indexer).is(10L).count());
		assertEquals(1, map.query(indexer).is(30L).count());

		// Test querying with in() after bulk operations
		assertEquals(3, map.query(indexer).in(1L, 3L, 5L).count());
		assertEquals(3, map.query(indexer).in(10L, 20L, 30L).count());

		// Test removeAll
		map.removeAll();
		assertEquals(0, map.size());
		assertEquals(0, map.query(indexer).is(1L).count());
		assertEquals(0, map.query(indexer).in(10L, 20L, 30L).count());
	}
	
	@Test
	void huge()
	{
		final LongIndexer indexer = new LongIndexer();
		final GigaMap<Long> map = GigaMap.New(XHashing.hashEqualityValue());
		map.index().bitmap().add(indexer);
		
		final long start = 1_000_000_000L, count = 10_000_000L;
		for(long l = start, max = start + count; l < max; l++)
		{
			map.add(l);
		}
		
		assertEquals(count, map.size());
		assertEquals(2, map.query(indexer).in(start, start + count - 1).count());
		
		map.remove(start + count / 2);
		assertEquals(count - 1, map.size());
	}



	private static class LongIndexer extends BinaryIndexer.Abstract<Long>
	{
		@Override
		public long indexBinary(final Long entity)
		{
			return entity;
		}
	}

}
