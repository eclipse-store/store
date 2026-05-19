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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for basic query operations on {@link BinaryIndexerLong}:
 * {@code is}, {@code not}, {@code in}, {@code notIn}, {@code count},
 * {@code toList}, {@code forEach}, {@code iterateIndexed}, and
 * duplicate-value handling.
 */
public class BinaryIndexerQueryTest
{
	record Product(long categoryId, String name) {}

	static final BinaryIndexerLong<Product> CATEGORY_INDEX = new BinaryIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(final Product p)
		{
			return p.categoryId();
		}
	};

	private GigaMap<Product> map;

	// categoryId distribution:
	//   1L → "Alpha", "Beta"           (2 products)
	//   2L → "Gamma"                    (1 product)
	//   3L → "Delta", "Epsilon", "Zeta" (3 products)
	@BeforeEach
	void setUp()
	{
		map = GigaMap.<Product>Builder()
			.withBitmapIndex(CATEGORY_INDEX)
			.build();

		map.add(new Product(1L, "Alpha"));
		map.add(new Product(1L, "Beta"));
		map.add(new Product(2L, "Gamma"));
		map.add(new Product(3L, "Delta"));
		map.add(new Product(3L, "Epsilon"));
		map.add(new Product(3L, "Zeta"));
	}

	@Test
	void isReturnsExactMatch()
	{
		final List<Product> results = map.query(CATEGORY_INDEX.is(2L)).toList();
		assertEquals(1, results.size());
		assertEquals("Gamma", results.get(0).name());
	}

	@Test
	void isReturnsMultipleMatchesForSharedKey()
	{
		final List<Product> results = map.query(CATEGORY_INDEX.is(1L)).toList();
		assertEquals(2, results.size());
		assertTrue(results.stream().allMatch(p -> p.categoryId() == 1L));
	}

	@Test
	void isReturnsEmptyForNonExistentKey()
	{
		final List<Product> results = map.query(CATEGORY_INDEX.is(99L)).toList();
		assertTrue(results.isEmpty());
	}

	@Test
	void notExcludesSingleValue()
	{
		final List<Product> results = map.query(CATEGORY_INDEX.not(2L)).toList();
		assertEquals(5, results.size());
		assertTrue(results.stream().noneMatch(p -> p.categoryId() == 2L));
	}

	@Test
	void notExcludesAllWhenEveryEntityMatchesKey()
	{
		// All 6 products have categoryId != 99, so not(99) returns all
		assertEquals(6, map.query(CATEGORY_INDEX.not(99L)).count());
	}

	@Test
	void inMatchesAnyOfSeveralKeys()
	{
		final List<Product> results = map.query(CATEGORY_INDEX.in(1L, 2L)).toList();
		assertEquals(3, results.size());
		assertTrue(results.stream().allMatch(p -> p.categoryId() == 1L || p.categoryId() == 2L));
	}

	@Test
	void inWithSingleKeyBehavesLikeIs()
	{
		final List<Product> via_in = map.query(CATEGORY_INDEX.in(3L)).toList();
		final List<Product> via_is = map.query(CATEGORY_INDEX.is(3L)).toList();
		assertEquals(via_is.size(), via_in.size());
	}

	@Test
	void inWithNoMatchReturnsEmpty()
	{
		assertTrue(map.query(CATEGORY_INDEX.in(88L, 99L)).toList().isEmpty());
	}

	@Test
	void notInExcludesAll()
	{
		final List<Product> results = map.query(CATEGORY_INDEX.notIn(1L, 3L)).toList();
		assertEquals(1, results.size());
		assertEquals(2L, results.get(0).categoryId());
	}

	@Test
	void notInWithNonExistentKeysReturnsAll()
	{
		assertEquals(6, map.query(CATEGORY_INDEX.notIn(88L, 99L)).count());
	}

	@Test
	void countMatchesListSize()
	{
		assertEquals(
			map.query(CATEGORY_INDEX.is(3L)).toList().size(),
			map.query(CATEGORY_INDEX.is(3L)).count()
		);
	}

	@Test
	void forEachVisitsAllMatchingEntities()
	{
		final List<Product> visited = new ArrayList<>();
		map.query(CATEGORY_INDEX.is(3L)).forEach(visited::add);
		assertEquals(3, visited.size());
		assertTrue(visited.stream().allMatch(p -> p.categoryId() == 3L));
	}

	@Test
	void iterateIndexedProvidesEntityIdAndEntity()
	{
		final List<Long> ids = new ArrayList<>();
		final List<Product> entities = new ArrayList<>();

		map.query(CATEGORY_INDEX.is(1L)).iterateIndexed((id, entity) ->
		{
			ids.add(id);
			entities.add(entity);
		});

		assertEquals(2, ids.size());
		assertEquals(2, entities.size());
		// IDs must be non-negative and distinct
		assertTrue(ids.stream().allMatch(id -> id >= 0));
		assertEquals(ids.size(), ids.stream().distinct().count());
		assertTrue(entities.stream().allMatch(p -> p.categoryId() == 1L));
	}

	@Test
	void iterateIndexedOnEmptyResultCallsConsumerZeroTimes()
	{
		final List<Long> ids = new ArrayList<>();
		map.query(CATEGORY_INDEX.is(99L)).iterateIndexed((id, entity) -> ids.add(id));
		assertTrue(ids.isEmpty());
	}
}
