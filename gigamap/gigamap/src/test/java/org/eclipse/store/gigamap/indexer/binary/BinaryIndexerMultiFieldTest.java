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
import org.eclipse.store.gigamap.types.Condition;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.GigaQuery;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for multi-field queries with {@link BinaryIndexerLong}:
 * sequential {@code GigaQuery.and()}, {@code Condition.and/or},
 * nested AND grouping, {@code idStart}/{@code idBound}, and
 * mixing binary indexes with a regular bitmap index.
 */
public class BinaryIndexerMultiFieldTest
{
	// Task(id, categoryId, statusId, title)
	// categoryId: 10, 20, 30
	// statusId:    1 (open), 2 (closed)
	record Task(long id, long categoryId, long statusId, String title) {}

	static final BinaryIndexerLong<Task> CATEGORY_INDEX = new BinaryIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(final Task t) { return t.categoryId(); }
	};

	static final BinaryIndexerLong<Task> STATUS_INDEX = new BinaryIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(final Task t) { return t.statusId(); }
	};

	static final IndexerString<Task> TITLE_INDEX = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Task t) { return t.title(); }
	};

	private GigaMap<Task> map;

	// Dataset:
	// id=0  cat=10 status=1  "Aardvark"
	// id=1  cat=10 status=2  "Bear"
	// id=2  cat=20 status=1  "Cheetah"
	// id=3  cat=20 status=2  "Dolphin"
	// id=4  cat=30 status=1  "Elephant"
	// id=5  cat=30 status=2  "Fox"
	@BeforeEach
	void setUp()
	{
		map = GigaMap.<Task>Builder()
			.withBitmapIndex(CATEGORY_INDEX)
			.withBitmapIndex(STATUS_INDEX)
			.withBitmapIndex(TITLE_INDEX)
			.build();

		map.add(new Task(0L, 10L, 1L, "Aardvark"));
		map.add(new Task(0L, 10L, 2L, "Bear"));
		map.add(new Task(0L, 20L, 1L, "Cheetah"));
		map.add(new Task(0L, 20L, 2L, "Dolphin"));
		map.add(new Task(0L, 30L, 1L, "Elephant"));
		map.add(new Task(0L, 30L, 2L, "Fox"));
	}

	// -----------------------------------------------------------------------
	// Sequential GigaQuery.and()
	// -----------------------------------------------------------------------

	@Test
	void sequentialAndIntersectsTwoBinaryIndexes()
	{
		final GigaQuery<Task> q = map.query();
		q.and(CATEGORY_INDEX.is(10L));
		q.and(STATUS_INDEX.is(1L));

		final List<Task> results = q.toList();
		assertEquals(1, results.size());
		assertEquals("Aardvark", results.get(0).title());
	}

	@Test
	void sequentialAndReturnsEmptyWhenNoOverlap()
	{
		// cat=10 has ids 0,1 — none with status=99
		final GigaQuery<Task> q = map.query();
		q.and(CATEGORY_INDEX.is(10L));
		q.and(STATUS_INDEX.is(99L));

		assertTrue(q.toList().isEmpty());
	}

	@Test
	void sequentialAndWithThreeConditions()
	{
		final GigaQuery<Task> q = map.query();
		q.and(CATEGORY_INDEX.is(20L));
		q.and(STATUS_INDEX.is(1L));
		q.and(TITLE_INDEX.is("Cheetah"));

		final List<Task> results = q.toList();
		assertEquals(1, results.size());
		assertEquals("Cheetah", results.get(0).title());
	}

	// -----------------------------------------------------------------------
	// Condition.and / Condition.or
	// -----------------------------------------------------------------------

	@Test
	void conditionAndIntersectsTwoBinaryIndexes()
	{
		final Condition<Task> cond = CATEGORY_INDEX.is(20L).and(STATUS_INDEX.is(2L));
		final List<Task> results = map.query(cond).toList();
		assertEquals(1, results.size());
		assertEquals("Dolphin", results.get(0).title());
	}

	@Test
	void conditionAndWithNonExistentKeyReturnsEmpty()
	{
		final Condition<Task> cond = CATEGORY_INDEX.is(10L).and(STATUS_INDEX.is(99L));
		assertTrue(map.query(cond).toList().isEmpty());
	}

	@Test
	void conditionOrUnitesTwoBinaryIndexes()
	{
		// cat=10 → Aardvark, Bear; status=2 → Bear, Dolphin, Fox
		// union = Aardvark, Bear, Dolphin, Fox (4 distinct)
		final Condition<Task> cond = CATEGORY_INDEX.is(10L).or(STATUS_INDEX.is(2L));
		final long count = map.query(cond).count();
		assertEquals(4, count);
	}

	@Test
	void conditionOrWithBothMatchingReturnsUnion()
	{
		// cat=10 OR cat=30 → 4 tasks
		final Condition<Task> cond = CATEGORY_INDEX.is(10L).or(CATEGORY_INDEX.is(30L));
		assertEquals(4, map.query(cond).count());
	}

	@Test
	void conditionOrWithNeitherMatchingReturnsEmpty()
	{
		final Condition<Task> cond = CATEGORY_INDEX.is(99L).or(STATUS_INDEX.is(99L));
		assertTrue(map.query(cond).toList().isEmpty());
	}

	@Test
	void nestedAndGrouping()
	{
		// cat=30 AND (status=1 AND title="Elephant") — nested form
		final Condition<Task> cond = CATEGORY_INDEX.is(30L)
			.and(STATUS_INDEX.is(1L)
				.and(TITLE_INDEX.is("Elephant")));

		final List<Task> results = map.query(cond).toList();
		assertEquals(1, results.size());
		assertEquals("Elephant", results.get(0).title());
	}

	@Test
	void nestedAndGroupingWithNonExistentValueReturnsEmpty()
	{
		// cat=30 AND (status=1 AND title="Fox") — Fox has status=2, not 1
		final Condition<Task> cond = CATEGORY_INDEX.is(30L)
			.and(STATUS_INDEX.is(1L)
				.and(TITLE_INDEX.is("Fox")));

		assertTrue(map.query(cond).toList().isEmpty());
	}

	// -----------------------------------------------------------------------
	// idStart / idBound
	// -----------------------------------------------------------------------

	@Test
	void idBoundLimitsResults()
	{
		// status=1 has 3 results (ids 0, 2, 4); bound to first 2 ids
		final List<Long> allIds = new ArrayList<>();
		map.query(STATUS_INDEX.is(1L)).iterateIndexed((id, e) -> allIds.add(id));
		assertEquals(3, allIds.size());

		// Restrict to the first two by using idBound = allIds.get(2)
		final List<Long> boundIds = new ArrayList<>();
		map.query(STATUS_INDEX.is(1L))
			.idBound(allIds.get(2))
			.iterateIndexed((id, e) -> boundIds.add(id));

		assertEquals(2, boundIds.size());
		assertFalse(boundIds.contains(allIds.get(2)));
	}

	@Test
	void idStartSkipsEarlierResults()
	{
		final List<Long> allIds = new ArrayList<>();
		map.query(STATUS_INDEX.is(1L)).iterateIndexed((id, e) -> allIds.add(id));
		assertEquals(3, allIds.size());

		// Start from the second id
		final List<Long> startIds = new ArrayList<>();
		map.query(STATUS_INDEX.is(1L))
			.idStart(allIds.get(1))
			.iterateIndexed((id, e) -> startIds.add(id));

		assertEquals(2, startIds.size());
		assertFalse(startIds.contains(allIds.get(0)));
	}

	@Test
	void idStartAndIdBoundTogetherReturnSubrange()
	{
		final List<Long> allIds = new ArrayList<>();
		map.query(STATUS_INDEX.is(1L)).iterateIndexed((id, e) -> allIds.add(id));
		assertEquals(3, allIds.size());

		final List<Long> subIds = new ArrayList<>();
		map.query(STATUS_INDEX.is(1L))
			.idStart(allIds.get(1))
			.idBound(allIds.get(2))
			.iterateIndexed((id, e) -> subIds.add(id));

		assertEquals(1, subIds.size());
		assertEquals(allIds.get(1), subIds.get(0));
	}

	// -----------------------------------------------------------------------
	// Mixed binary + regular bitmap index
	// -----------------------------------------------------------------------

	@Test
	void binaryAndRegularIndexCombinedViaSequentialAnd()
	{
		// cat=20 AND title="Cheetah"
		final GigaQuery<Task> q = map.query();
		q.and(CATEGORY_INDEX.is(20L));
		q.and(TITLE_INDEX.is("Cheetah"));

		final List<Task> results = q.toList();
		assertEquals(1, results.size());
		assertEquals(20L, results.get(0).categoryId());
	}

	@Test
	void binaryAndRegularIndexCombinedViaConditionAnd()
	{
		// cat=30 AND title="Fox"
		final Condition<Task> cond = CATEGORY_INDEX.is(30L).and(TITLE_INDEX.is("Fox"));
		final List<Task> results = map.query(cond).toList();
		assertEquals(1, results.size());
		assertEquals("Fox", results.get(0).title());
	}

	@Test
	void binaryAndRegularIndexOrCombined()
	{
		// title="Aardvark" OR cat=30 → Aardvark + Elephant + Fox = 3 entities
		final Condition<Task> cond = TITLE_INDEX.is("Aardvark").or(CATEGORY_INDEX.is(30L));
		assertEquals(3, map.query(cond).count());
	}
}
