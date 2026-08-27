package org.eclipse.store.gigamap.issues;

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
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link GigaMap#addAll(Iterable)} used to traverse the passed {@link Iterable} once to validate, once to add
 * and then once more per index during the index fan-out. That had three consequences:
 * <ul>
 * <li>an iterable that cannot be traversed repeatedly was exhausted by the validation pass, so the method
 * added nothing, indexed nothing and returned normally - the caller had no indication at all;</li>
 * <li>a stream-backed iterable failed loudly on the second pass;</li>
 * <li>an iterable whose content changed between the passes made the adding pass and the indexing pass see
 * different element sequences: index entries at ids holding no entity (aliasing the next added entity once
 * that id got assigned), or added entities that were never indexed.</li>
 * </ul>
 * These tests pin that the passed iterable is traversed exactly once and that the indexed sequence is the
 * added sequence.
 */
public class AddAllSingleTraversalTest
{
	///////////////////////////////////////////////////////////////////////////
	// shared domain //
	//////////////////

	static class Item
	{
		final String label;

		Item(final String label)
		{
			super();
			this.label = label;
		}
	}

	static final IndexerString<Item> LABEL = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "label";
		}

		@Override
		protected String getString(final Item item)
		{
			return item.label;
		}
	};

	/**
	 * An indexer that throws for one specific label, to trigger the rollback of an otherwise valid batch.
	 */
	static final class FailingLabelIndexer extends IndexerString.Abstract<Item>
	{
		private final String failingLabel;

		FailingLabelIndexer(final String failingLabel)
		{
			super();
			this.failingLabel = failingLabel;
		}

		@Override
		public String name()
		{
			return "failingLabel";
		}

		@Override
		protected String getString(final Item item)
		{
			if(this.failingLabel.equals(item.label))
			{
				throw new RuntimeException("indexer failure for " + item.label);
			}

			return item.label;
		}
	}



	///////////////////////////////////////////////////////////////////////////
	// helpers //
	////////////

	/**
	 * An {@link Iterable} that hands out the <b>same</b> iterator every time, i.e. it is exhausted after the
	 * first traversal. This is the silent variant of the defect: no exception, simply nothing left to add.
	 */
	static <T> Iterable<T> sameIteratorEachTime(final List<T> elements)
	{
		final Iterator<T> shared = elements.iterator();

		return () -> shared;
	}

	/**
	 * An {@link Iterable} counting how often a traversal was started. Each traversal is valid on its own, like
	 * the weakly consistent view of a concurrent collection, but the content changes between traversals.
	 */
	static final class ChangingIterable<T> implements Iterable<T>
	{
		private final List<List<T>> traversals;
		private       int           iteratorRequests;

		@SafeVarargs
		ChangingIterable(final List<T>... traversals)
		{
			super();
			this.traversals = List.of(traversals);
		}

		@Override
		public Iterator<T> iterator()
		{
			// the last defined traversal is repeated for any further request
			final int index = Math.min(this.iteratorRequests++, this.traversals.size() - 1);

			return this.traversals.get(index).iterator();
		}

		int iteratorRequests()
		{
			return this.iteratorRequests;
		}
	}

	private static GigaMap<Item> indexedMap()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(LABEL);

		return map;
	}

	private static List<Item> items(final String... labels)
	{
		return Arrays.stream(labels).map(Item::new).toList();
	}

	private static void assertIndexedOnce(final GigaMap<Item> map, final String... labels)
	{
		for(final String label : labels)
		{
			assertEquals(1, map.query(LABEL.is(label)).count(), "entity \"" + label + "\" must be indexed");
		}
	}



	///////////////////////////////////////////////////////////////////////////
	// iterables that cannot be traversed repeatedly //
	//////////////////////////////////////////////////

	@Test
	@Timeout(60)
	void addAll_sameIteratorEachTime_addsAndIndexesEverything()
	{
		final GigaMap<Item> map = indexedMap();

		final long lastId = map.addAll(sameIteratorEachTime(items("a", "b", "c")));

		assertEquals(3, map.size(), "all entities of the batch must be added");
		assertEquals(2, lastId, "the returned id must be the id of the last added entity");
		assertIndexedOnce(map, "a", "b", "c");
	}

	@Test
	@Timeout(60)
	void addAll_streamBackedIterable_addsAndIndexesEverything()
	{
		final GigaMap<Item> map = indexedMap();

		final Stream<Item> stream = items("a", "b", "c").stream();
		map.addAll(stream::iterator);

		assertEquals(3, map.size());
		assertIndexedOnce(map, "a", "b", "c");
	}

	@Test
	@Timeout(60)
	void addAll_traversesThePassedIterableExactlyOnce()
	{
		final GigaMap<Item> map = indexedMap();

		// two indices: the fan-out used to traverse the passed iterable once per index
		map.index().bitmap().add(new FailingLabelIndexer("nothing fails here"));

		final ChangingIterable<Item> entities = new ChangingIterable<>(items("a", "b", "c"));
		map.addAll(entities);

		assertEquals(1, entities.iteratorRequests(), "the passed iterable must be traversed exactly once");
		assertEquals(3, map.size());
	}



	///////////////////////////////////////////////////////////////////////////
	// iterables whose content changes between traversals //
	///////////////////////////////////////////////////////

	@Test
	@Timeout(60)
	void addAll_iterableGrowingBetweenTraversals_leavesNoPhantomIndexEntry()
	{
		final GigaMap<Item> map = indexedMap();

		// a later traversal would yield one more element than the adding pass saw
		map.addAll(new ChangingIterable<>(items("a", "b", "c"), items("a", "b", "c", "d")));

		assertEquals(3, map.size(), "only the added entities may count");
		assertIndexedOnce(map, "a", "b", "c");
		assertEquals(0, map.query(LABEL.is("d")).count(), "an entity that was never added must not be indexed");

		// the phantom entry used to become visible only here: "x" gets the id the phantom bit was set for
		final Item x = new Item("x");
		map.add(x);

		assertEquals(0, map.query(LABEL.is("d")).count(), "a phantom index entry must not alias a later entity");
		assertEquals(1, map.query(LABEL.is("x")).count());
		assertSame(x, map.query(LABEL.is("x")).toList().get(0));
	}

	@Test
	@Timeout(60)
	void addAll_iterableShrinkingBetweenTraversals_indexesEveryAddedEntity()
	{
		final GigaMap<Item> map = indexedMap();

		// a later traversal would yield fewer elements than the adding pass saw
		map.addAll(new ChangingIterable<>(items("a", "b", "c"), items("a", "b")));

		assertEquals(3, map.size());
		assertIndexedOnce(map, "a", "b", "c");
	}



	///////////////////////////////////////////////////////////////////////////
	// failure handling //
	/////////////////////

	@Test
	@Timeout(60)
	void addAll_nullElementInTheMiddle_rollsBackTheWholeBatch()
	{
		final GigaMap<Item> map = indexedMap();
		map.add(new Item("existing"));

		final List<Item> entities = new ArrayList<>(items("a", "b"));
		entities.add(1, null);

		assertThrows(IllegalArgumentException.class, () -> map.addAll(entities));

		assertEquals(1, map.size(), "the map must be unchanged");
		assertEquals(0, map.query(LABEL.is("a")).count(), "no entity of the batch may be indexed");
		assertIndexedOnce(map, "existing");

		// the map must still be usable afterwards
		final Item next = new Item("next");
		final long nextId = map.add(next);

		assertSame(next, map.get(nextId));
		assertIndexedOnce(map, "next");
	}

	@Test
	@Timeout(60)
	void addAll_throwingIndexer_rollsBackTheWholeBatch()
	{
		final GigaMap<Item> map = indexedMap();
		map.index().bitmap().add(new FailingLabelIndexer("boom"));

		assertThrows(RuntimeException.class, () -> map.addAll(items("a", "boom", "c")));

		assertEquals(0, map.size(), "no entity of the batch may remain");
		assertEquals(0, map.query(LABEL.is("a")).count(), "no index may refer to a rolled back entity");
		assertEquals(0, map.query(LABEL.is("c")).count(), "no index may refer to a rolled back entity");

		// the map must still be usable afterwards
		final Item next = new Item("next");
		assertNotNull(map.get(map.add(next)));
		assertIndexedOnce(map, "next");
	}



	///////////////////////////////////////////////////////////////////////////
	// segment boundaries //
	///////////////////////

	@Test
	@Timeout(60)
	void addAll_batchSpanningMultipleSegments_indexesEveryEntityAtItsOwnId()
	{
		// tiny low level segments (4 entities each), so the batch spans several of them
		final GigaMap<Item> map = GigaMap.New(2);
		map.index().bitmap().add(LABEL);

		final int        count  = 37;
		final List<Item> batch  = new ArrayList<>(count);
		for(int i = 0; i < count; i++)
		{
			batch.add(new Item("label" + i));
		}

		final long lastId = map.addAll(sameIteratorEachTime(batch));

		assertEquals(count, map.size());
		assertEquals(count - 1, lastId);

		// every entity must be indexed at exactly its own id
		for(int i = 0; i < count; i++)
		{
			final List<Item> found = map.query(LABEL.is("label" + i)).toList();
			assertEquals(1, found.size(), "entity \"label" + i + "\" must be indexed exactly once");
			assertSame(batch.get(i), found.get(0), "entity \"label" + i + "\" must be indexed at its own id");
			assertSame(batch.get(i), map.get(i));
		}
	}

	@Test
	@Timeout(60)
	void addAll_multipleBatches_keepIdAccountingIntact()
	{
		final GigaMap<Item> map = GigaMap.New(2);
		map.index().bitmap().add(LABEL);

		final List<Item> first  = items("a", "b", "c", "d", "e");
		final List<Item> second = items("f", "g", "h");

		assertEquals(4, map.addAll(sameIteratorEachTime(first)));
		assertEquals(7, map.addAll(sameIteratorEachTime(second)));

		assertEquals(8, map.size());
		assertIndexedOnce(map, "a", "b", "c", "d", "e", "f", "g", "h");
		assertSame(second.get(0), map.query(LABEL.is("f")).toList().get(0));
	}
}
