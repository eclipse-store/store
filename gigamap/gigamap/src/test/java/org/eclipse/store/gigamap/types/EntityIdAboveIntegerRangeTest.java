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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Entity ids beyond the int range have to keep working, over the whole documented capacity of 2^50
 * (internal issue #133).
 * <p>
 * Several places derived an entity id from a segment index with {@code int} arithmetic and only widened
 * to {@code long} on assignment or at the call - too late. All of them broke at the same boundary, id
 * 2^31:
 * <ul>
 * <li>{@code GigaMap.Default#iterateIndexed} and its two private helpers reported wrapped, negative
 * ids, corrupting everything built on them: {@code reindex()}, bitmap index registration back-fill,
 * the Lucene back-fill (which persists the id) and the jvector graph rebuild. On top of the overflow,
 * {@code level1Exp + level2Exp} may legally reach 40 while an {@code int} shift uses only the low 5
 * bits of its distance (JLS 15.19), so {@code i<<32} evaluated to {@code i<<0 == i} and high level3
 * segments reported ids <i>colliding</i> with those of segment 0.</li>
 * <li>{@code AbstractBitmapIterating} and {@code ResultIdIterator} computed the level2 base id as
 * {@code level3Index * LEVEL_2_ID_COUNT}, so every query silently returned nothing for entities above
 * 2^31 - independently of {@code iterateIndexed}, and even for an index that was filled correctly by
 * the ordinary add path.</li>
 * </ul>
 * Populating 2^31 entities is not feasible in a test, but the ids only have to be <i>sparse</i>, not
 * numerous: this test lives in the {@code types} package so it can use the package-private
 * {@code GigaMap.Default} constructor that seeds the id counter, and
 * {@code GigaMap.Default#setupAddingState} then allocates just the one segment chain the seeded id
 * points at. A map whose first entity sits at id 2^31 therefore costs a level3 array of a few thousand
 * references plus one level2 and one level1 segment.
 */
public class EntityIdAboveIntegerRangeTest
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

	private static final NameIndexer NAME = new NameIndexer();

	/** 2^31, the first id the former {@code int} arithmetic could not represent. */
	private static final long FIRST_ID_ABOVE_INT_RANGE = 1L << 31;



	///////////////////////////////////////////////////////////////////////////
	// the ids iterateIndexed reports //
	///////////////////////////////////

	/**
	 * With the default dimensions {@code level1Exp + level2Exp == 18}, so id 2^31 is the base id of
	 * level3 segment 8192 and {@code 8192<<18} used to be {@link Integer#MIN_VALUE}.
	 */
	@Test
	public void iterateIndexedReportsIdsAboveIntegerRange()
	{
		final GigaMap.Default<Item> map = mapStartingAt(8, 10, 30, FIRST_ID_ABOVE_INT_RANGE);

		final Item item0 = new Item("item0");
		final Item item1 = new Item("item1");
		final long id0   = map.add(item0);
		final long id1   = map.add(item1);

		assertEquals(FIRST_ID_ABOVE_INT_RANGE    , id0, "the map hands out the seeded id");
		assertEquals(FIRST_ID_ABOVE_INT_RANGE + 1, id1, "and the one after it");

		final Map<Long, Item> iterated = iterateIndexed(map);

		assertEquals(Set.of(id0, id1), iterated.keySet(),
			"the reported ids must be the ids the map handed out, not their int-wrapped counterparts "
			+ (int)id0 + " and " + (int)id1);
		assertSame(item0, iterated.get(id0), "and each id must carry its own entity");
		assertSame(item1, iterated.get(id1), "and each id must carry its own entity");
	}

	/**
	 * {@code level1Exp + level2Exp == 32} makes the level3 shift distance reach the int width, where
	 * masking turned {@code i<<32} into {@code i<<0 == i}. That is worse than an overflow: level3
	 * segment 1 reported base id 1, which is a perfectly valid id belonging to segment 0.
	 * <p>
	 * So segment 0 is populated up to that very id here, which makes the defect an actual <i>collision</i>
	 * rather than merely a wrong magnitude: two different entities reported the same id, and since every
	 * consumer of {@code iterateIndexed} keys by it, the one that came first was silently dropped. Asserting
	 * only the high entity's id would have missed that entirely.
	 */
	@Test
	public void iterateIndexedDoesNotAliasLowerIdsWhenTheShiftDistanceReachesIntWidth()
	{
		final GigaMap.Default<Item> map = mapStartingAt(14, 18, 8, 1L << 32);

		final Item high   = new Item("high");
		final long highId = map.add(high);
		assertEquals(1L << 32, highId, "the map hands out the seeded id");

		// entities in level3 segment 0, at exactly the ids the aliased base id collided with
		final Item low0 = new Item("low0");
		final Item low1 = new Item("low1");
		assertNull(map.set(0L, low0), "the slot in level3 segment 0 was still empty");
		assertNull(map.set(1L, low1), "the slot in level3 segment 0 was still empty");

		final Map<Long, Item> iterated = iterateIndexed(map);

		assertEquals(Set.of(0L, 1L, highId), iterated.keySet(),
			"level3 segment 1 must report base id " + highId + ", not alias id 1 of segment 0");
		assertSame(low0, iterated.get(0L)    , "and each id must carry its own entity");
		assertSame(low1, iterated.get(1L)    , "id 1 must still carry the entity that actually lives there");
		assertSame(high, iterated.get(highId), "and each id must carry its own entity");
	}

	/**
	 * The level2 helper has the same defect on its own: with {@code level1Exp == 20} the level2 index
	 * 2048 makes {@code 2048<<20} overflow while the level3 index is still 0, so the level3 shift is
	 * not involved at all here.
	 */
	@Test
	public void iterateIndexedReportsIdsAboveIntegerRangeWithinALevel3Segment()
	{
		final GigaMap.Default<Item> map = mapStartingAt(20, 12, 8, 2048L << 20);

		final Item item = new Item("item");
		final long id   = map.add(item);

		assertEquals(FIRST_ID_ABOVE_INT_RANGE, id, "the map hands out the seeded id");

		final Map<Long, Item> iterated = iterateIndexed(map);

		assertEquals(Set.of(id), iterated.keySet(),
			"the level2 index shift must not wrap the id to " + (int)id);
		assertSame(item, iterated.get(id), "and the id must carry its entity");
	}



	///////////////////////////////////////////////////////////////////////////
	// the ids queries iterate over //
	/////////////////////////////////

	/**
	 * The index is registered before anything is added, so it is filled purely by the ordinary add
	 * path and {@code iterateIndexed} is not involved at all. This isolates the query-side base id
	 * computation, which used to drop every hit above 2^31.
	 */
	@Test
	public void queryFindsEntitiesAboveIntegerRange()
	{
		final GigaMap.Default<Item> map = mapStartingAt(8, 10, 30, FIRST_ID_ABOVE_INT_RANGE);
		map.index().bitmap().add(NAME);

		final Item item0 = new Item("item0");
		final Item item1 = new Item("item1");
		final long id0   = map.add(item0);
		final long id1   = map.add(item1);

		assertEquals(1L, map.query(NAME, "item0").count(), "the entity must be findable");
		assertEquals(Set.of(id0), queryIds(map, "item0"), "at the id the map handed out");
		assertEquals(List.of(item0), map.query(NAME, "item0").toList(), "and it must be the entity itself");
		assertEquals(Set.of(id1), queryIds(map, "item1"), "and each entity keeps its own id");
	}

	/**
	 * Registering an index on a populated map back-fills it through {@code iterateIndexed}
	 * ({@code BitmapIndices#internalAddBitmapIndex}), so a wrong id made the index corrupt from birth.
	 */
	@Test
	public void bitmapIndexBackfillAboveIntegerRangeKeepsTheEntityQueryable()
	{
		final GigaMap.Default<Item> map = mapStartingAt(8, 10, 30, FIRST_ID_ABOVE_INT_RANGE);
		final Item item = new Item("item0");
		final long id   = map.add(item);

		map.index().bitmap().add(NAME);

		assertEquals(1L, map.query(NAME, "item0").count(), "the back-filled entity must be findable");
		assertEquals(Set.of(id), queryIds(map, "item0"), "and at the id the map handed out");
		assertEquals(List.of(item), map.query(NAME, "item0").toList(), "and it must be the entity itself");
	}

	/**
	 * {@code reindex()} drops and rebuilds every index group from {@code iterateIndexed}
	 * ({@code IndexGroup.Internal#internalReindex}), so a wrong id turned the recovery tool into the
	 * thing that breaks the index.
	 */
	@Test
	public void reindexAboveIntegerRangeRealignsTheIndex()
	{
		final GigaMap.Default<Item> map = mapStartingAt(8, 10, 30, FIRST_ID_ABOVE_INT_RANGE);
		map.index().bitmap().add(NAME);
		final Item item = new Item("item0");
		final long id   = map.add(item);

		map.reindex();

		assertEquals(1L, map.query(NAME, "item0").count(), "reindex must not lose the entity");
		assertEquals(Set.of(id), queryIds(map, "item0"), "and must keep it at its own id");
		assertEquals(List.of(item), map.query(NAME, "item0").toList(), "and it must be the entity itself");
	}



	///////////////////////////////////////////////////////////////////////////
	// helpers //
	////////////

	/**
	 * Creates a map whose id counter starts at the given id, so that the first added entity lands in
	 * the segment chain that id points at without any of the ids below it being populated.
	 */
	private static GigaMap.Default<Item> mapStartingAt(
		final int  lowLevelLengthExponent        ,
		final int  midLevelLengthExponent        ,
		final int  highLevelMaximumLengthExponent,
		final long startId
	)
	{
		return new GigaMap.Default<>(
			new GigaMap.DefaultEqualator<>(),
			lowLevelLengthExponent,
			midLevelLengthExponent,
			0,
			highLevelMaximumLengthExponent,
			0,
			startId
		);
	}

	private static Map<Long, Item> iterateIndexed(final GigaMap<Item> map)
	{
		final Map<Long, Item> collected = new LinkedHashMap<>();
		map.iterateIndexed((id, entity) -> collected.put(id, entity));

		return collected;
	}

	private static Set<Long> queryIds(final GigaMap<Item> map, final String name)
	{
		final Set<Long> ids = new LinkedHashSet<>();
		map.query(NAME, name).iterateIndexed((id, entity) -> ids.add(id));

		return ids;
	}

}
