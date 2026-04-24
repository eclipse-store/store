package org.eclipse.store.gigamap.indexer.binary;

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

import org.eclipse.store.gigamap.types.BinaryIndexerLong;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CRUD operations (add, remove, update) with {@link BinaryIndexerLong}:
 * verifies that the index is correctly maintained after each mutation.
 */
public class BinaryIndexerCrudTest
{
	static class Item
	{
		long typeId;
		long ownerId;
		String name;

		Item(final long typeId, final long ownerId, final String name)
		{
			this.typeId  = typeId;
			this.ownerId = ownerId;
			this.name    = name;
		}

		@Override
		public String toString()
		{
			return "Item{typeId=" + typeId + ", ownerId=" + ownerId + ", name=" + name + "}";
		}
	}

	static final BinaryIndexerLong<Item> TYPE_INDEX = new BinaryIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(final Item item) { return item.typeId; }
	};

	static final BinaryIndexerLong<Item> OWNER_INDEX = new BinaryIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(final Item item) { return item.ownerId; }
	};

	private GigaMap<Item> map;
	private Item itemA; // typeId=1, ownerId=10
	private Item itemB; // typeId=1, ownerId=20
	private Item itemC; // typeId=2, ownerId=10

	@BeforeEach
	void setUp()
	{
		map = GigaMap.<Item>Builder()
			.withBitmapIndex(TYPE_INDEX)
			.withBitmapIndex(OWNER_INDEX)
			.build();

		itemA = new Item(1L, 10L, "Alpha");
		itemB = new Item(1L, 20L, "Beta");
		itemC = new Item(2L, 10L, "Gamma");

		map.add(itemA);
		map.add(itemB);
		map.add(itemC);
	}

	// -----------------------------------------------------------------------
	// Add
	// -----------------------------------------------------------------------

	@Test
	void addedEntityIsImmediatelyQueryable()
	{
		final Item newItem = new Item(3L, 30L, "Delta");
		map.add(newItem);

		final List<Item> results = map.query(TYPE_INDEX.is(3L)).toList();
		assertEquals(1, results.size());
		assertEquals("Delta", results.get(0).name);
	}

	@Test
	void addIncreasesMapSize()
	{
		assertEquals(3, map.size());
		map.add(new Item(4L, 40L, "Epsilon"));
		assertEquals(4, map.size());
	}

	@Test
	void addDuplicateKeyValueIncreasesResultCount()
	{
		assertEquals(2, map.query(TYPE_INDEX.is(1L)).count());
		map.add(new Item(1L, 30L, "Zeta"));
		assertEquals(3, map.query(TYPE_INDEX.is(1L)).count());
	}

	// -----------------------------------------------------------------------
	// Remove
	// -----------------------------------------------------------------------

	@Test
	void removedEntityIsNoLongerReturnedByQuery()
	{
		map.remove(itemA);

		final List<Item> results = map.query(TYPE_INDEX.is(1L)).toList();
		assertEquals(1, results.size());
		assertEquals("Beta", results.get(0).name);
	}

	@Test
	void removeDecreasesMapSize()
	{
		assertEquals(3, map.size());
		map.remove(itemC);
		assertEquals(2, map.size());
	}

	@Test
	void removeOneOfSeveralWithSameKeyLeavesOthersIntact()
	{
		// typeId=1 has itemA and itemB — remove itemA, itemB must still be found
		map.remove(itemA);

		final List<Item> remaining = map.query(TYPE_INDEX.is(1L)).toList();
		assertEquals(1, remaining.size());
		assertEquals("Beta", remaining.get(0).name);
	}

	@Test
	void removeByIndexHintWorksCorrectly()
	{
		// remove itemC using TYPE_INDEX as hint
		final long removedId = map.remove(itemC, TYPE_INDEX);
		assertNotEquals(-1L, removedId, "remove() should return the entity's id, not -1");

		assertTrue(map.query(TYPE_INDEX.is(2L)).toList().isEmpty());
		assertEquals(2, map.size());
	}

	@Test
	void removeAllOfOneKeyLeavesOtherKeysIntact()
	{
		map.remove(itemA);
		map.remove(itemB);

		assertTrue(map.query(TYPE_INDEX.is(1L)).toList().isEmpty());
		assertEquals(1, map.query(TYPE_INDEX.is(2L)).count());
	}

	// -----------------------------------------------------------------------
	// Update
	// -----------------------------------------------------------------------

	@Test
	void updateIndexedFieldIsReflectedInQueryResults()
	{
		// Change itemA's typeId from 1 → 5
		map.update(itemA, item -> item.typeId = 5L);

		// Old value no longer returns itemA
		final List<Item> oldResults = map.query(TYPE_INDEX.is(1L)).toList();
		assertEquals(1, oldResults.size());
		assertEquals("Beta", oldResults.get(0).name);

		// New value returns itemA
		final List<Item> newResults = map.query(TYPE_INDEX.is(5L)).toList();
		assertEquals(1, newResults.size());
		assertEquals("Alpha", newResults.get(0).name);
	}

	@Test
	void updateNonIndexedFieldDoesNotAffectQueryResults()
	{
		// Change only the name (not indexed)
		map.update(itemA, item -> item.name = "AlphaRenamed");

		// Index query still finds the entity under typeId=1
		final List<Item> results = map.query(TYPE_INDEX.is(1L)).toList();
		assertEquals(2, results.size());
		assertTrue(results.stream().anyMatch(i -> "AlphaRenamed".equals(i.name)));
	}

	@Test
	void updateBothIndexedFieldsKeepsConsistency()
	{
		// Move itemC from (type=2, owner=10) to (type=3, owner=30)
		map.update(itemC, item ->
		{
			item.typeId  = 3L;
			item.ownerId = 30L;
		});

		assertTrue(map.query(TYPE_INDEX.is(2L)).toList().isEmpty());
		assertTrue(map.query(OWNER_INDEX.is(10L)).toList().stream()
			.noneMatch(i -> "Gamma".equals(i.name)));

		assertEquals(1, map.query(TYPE_INDEX.is(3L)).count());
		assertEquals(1, map.query(OWNER_INDEX.is(30L)).count());
	}

	@Test
	void sizeRemainsConstantAfterUpdate()
	{
		assertEquals(3, map.size());
		map.update(itemB, item -> item.typeId = 99L);
		assertEquals(3, map.size());
	}

	// -----------------------------------------------------------------------
	// Combined CRUD
	// -----------------------------------------------------------------------

	@Test
	void addThenRemoveLeavesMapInOriginalState()
	{
		final Item temp = new Item(9L, 90L, "Temp");
		map.add(temp);
		assertEquals(4, map.size());

		map.remove(temp);
		assertEquals(3, map.size());
		assertTrue(map.query(TYPE_INDEX.is(9L)).toList().isEmpty());
	}

	@Test
	void updateThenRemoveIsClean()
	{
		map.update(itemA, item -> item.typeId = 7L);
		assertEquals(1, map.query(TYPE_INDEX.is(7L)).count());

		map.remove(itemA);
		assertTrue(map.query(TYPE_INDEX.is(7L)).toList().isEmpty());
		assertEquals(2, map.size());
	}
}
