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

import org.eclipse.store.gigamap.types.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * https://github.com/eclipse-store/store/issues/641
 */

/**
 * Reproduces a bug in EclipseStore GigaMap where {@link Condition.And} and
 * {@link Condition.Or} combined with {@link BinaryBitmapIndex} (created by
 * {@link GigaMap.Builder#withBitmapIndex}) return entities that match only a
 * <b>subset</b> of the conjuncts, instead of requiring <b>all</b> conjuncts to match.
 *
 * <h2>Setup</h2>
 *
 * We create a GigaMap with three {@link BinaryIndexerLong} bitmap indexes and
 * insert six entities. Some share individual fields (e.g. {@code groupId} or
 * {@code roleId}) but no two share the full {@code (actorId, groupId, roleId)}
 * triple:
 * <pre>
 * | id | actorId | groupId | roleId |
 * |----|---------|---------|--------|
 * | 10 | 100     | 1000    | 10000  |
 * | 20 | 200     | 1000    | 10000  |
 * | 30 | 300     | 2000    | 10000  |
 * | 40 | 400     | 2000    | 20000  |
 * | 50 | 500     | 1000    | 20000  |
 * | 60 | 600     | 2000    | 30000  |
 * </pre>
 *
 * <h2>The Bug</h2>
 *
 * <h3>Manifestation 1 &mdash; nested {@code And}</h3>
 *
 * When we query for the <b>non-existent</b> tuple
 * {@code (actorId=999, groupId=2000, roleId=20000)} using a nested
 * {@link Condition.And} (the exact nesting order from production code):
 * <pre>{@code
 * var condition = GROUP_INDEX.is(2000L)
 *         .and(ROLE_INDEX.is(20000L)
 *                 .and(ACTOR_INDEX.is(999L)));
 * var results = gigaMap.query(condition).toList();
 * }</pre>
 *
 * The expected result is <b>empty</b> (no entity has {@code actorId=999}).
 *
 * <p>The actual result is entity {@code 40} ({@code actorId=400, groupId=2000,
 * roleId=20000}) &mdash; an entity that matches {@code groupId} and
 * {@code roleId} but <b>not</b> {@code actorId}. The {@code And} evaluator
 * returned a partial match instead of a strict intersection.
 *
 * <h3>Manifestation 2 &mdash; {@code Or} wrapping {@code And}</h3>
 *
 * When the {@code And} is wrapped in an {@code Or}:
 * <pre>{@code
 * var condition = ID_INDEX.is(999L)
 *         .or(ACTOR_INDEX.is(999L)
 *                 .and(GROUP_INDEX.is(2000L))
 *                 .and(ROLE_INDEX.is(20000L)));
 * }</pre>
 *
 * The query returns <b>all entities in the map</b> instead of an empty list.
 *
 * <h2>Root Cause</h2>
 *
 * When a {@link BinaryBitmapIndex} query cannot possibly match (a required bit
 * position has no index entry), {@code internalQuery} returned
 * {@code new BitmapResult.ChainAnd(EMPTY_RESULT)} &mdash; an <b>empty</b>
 * {@code ChainAnd}.
 *
 * <p>An empty {@code ChainAnd} reports {@code -1L} (all-1s) from
 * {@code getCurrentLevel1BitmapValue} because its AND-reduction starts at
 * {@code -1L} and has no elements to reduce with. When that empty
 * {@code ChainAnd} was nested inside another {@code ChainAnd} alongside a
 * non-empty sibling, the level-1 AND at the driver couldn't filter it out:
 * {@code non_empty & -1L = non_empty}. The non-matching sub-condition
 * effectively vanished from the intersection, and any entity matching the
 * other conjuncts was returned.
 *
 * <p>For {@code Or} wrapping {@code And}, the same empty {@code ChainAnd}
 * fell through the {@code Or}-branch's {@code And} and made its bitmap match
 * every id, blowing the overall query up to the full entity set.
 *
 * <p>The fix is to return the {@code BitmapResult.Empty} singleton (which
 * correctly reports {@code 0L}/{@code false}) instead of wrapping the empty
 * array in a {@code ChainAnd}, mirroring how
 * {@code AbstractBitmapIndexHashing} already handles the no-match case.
 *
 * <h2>Workaround</h2>
 *
 * Avoid composing {@link Condition.And} or {@link Condition.Or} trees
 * manually and passing them to {@code gigaMap.query(...)}. Instead, apply
 * conditions sequentially via {@code GigaQuery.and(Condition)}:
 * <pre>{@code
 * var query = gigaMap.query();
 * query.and(ACTOR_INDEX.is(999L));
 * query.and(GROUP_INDEX.is(2000L));
 * query.and(ROLE_INDEX.is(20000L));
 * var results = query.toList(); // correct - empty
 * }</pre>
 *
 * The {@code GigaQueryBuilder} abstraction in the {@code peruncs} stack
 * already implements this workaround by maintaining a list of standalone
 * conditions and applying them one-by-one in its {@code create()} method.
 *
 * <h2>Affected Versions</h2>
 *
 * Observed on EclipseStore 3.1.0. The same bug likely affects any version
 * that uses the {@code BinaryBitmapIndex} condition-evaluation path for
 * composite conditions.
 *
 * <h2>Related</h2>
 * <ul>
 *     <li>{@code GigaQueryBuilder} &mdash; documents the same workaround:
 *         {@code WORKAROUND: This builder avoids using
 *         [Condition#and(Condition)] internally}</li>
 *     <li>{@code BinaryIndexBug} &mdash; related reproduction for {@code And}
 *         with mixed indexers</li>
 * </ul>
 */
public class BinaryIndexerCompositeConditionTest
{

	/**
	 * Simple entity with three independently-indexed {@code long} fields.
	 * Mirrors the {@code (actor, group, role)} tuple pattern used in RBAC stores.
	 */
	record Assignment(long id, long actorId, long groupId, long roleId)
	{
	}

	static final BinaryIndexerLong<Assignment> ID_INDEX = new BinaryIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(Assignment a)
		{
			return a.id();
		}
	};

	static final BinaryIndexerLong<Assignment> ACTOR_INDEX = new BinaryIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(Assignment a)
		{
			return a.actorId();
		}
	};

	static final BinaryIndexerLong<Assignment> GROUP_INDEX = new BinaryIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(Assignment a)
		{
			return a.groupId();
		}
	};

	static final BinaryIndexerLong<Assignment> ROLE_INDEX = new BinaryIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(Assignment a)
		{
			return a.roleId();
		}
	};

	private GigaMap<Assignment> gigaMap;

	@BeforeEach
	void setUp()
	{
		gigaMap = GigaMap.<Assignment>Builder()
			.withBitmapIdentityIndex(ID_INDEX)
			.withBitmapIndex(ACTOR_INDEX)
			.withBitmapIndex(GROUP_INDEX)
			.withBitmapIndex(ROLE_INDEX)
			.build();

		// Six entities with overlapping but distinct (actorId, groupId, roleId) tuples.
		// Some share individual fields (e.g. groupId or roleId) but no two share the
		// full triple.  This mirrors the AGR pattern in the production store.
		gigaMap.add(new Assignment(10L, 100L, 1000L, 10000L));
		gigaMap.add(new Assignment(20L, 200L, 1000L, 10000L));
		gigaMap.add(new Assignment(30L, 300L, 2000L, 10000L));
		gigaMap.add(new Assignment(40L, 400L, 2000L, 20000L));
		gigaMap.add(new Assignment(50L, 500L, 1000L, 20000L));
		gigaMap.add(new Assignment(60L, 600L, 2000L, 30000L));

		assertEquals(6, gigaMap.size(), "Setup: expected 6 entities in map");
	}

	/**
	 * Demonstrates that a single-index {@code is()} query works correctly.
	 */
	@Test
	void singleIndexQueryWorks()
	{
		var results = gigaMap.query(ACTOR_INDEX.is(200L)).toList();
		assertEquals(1, results.size());
		assertEquals(20L, results.get(0).id());
	}

	/**
	 * Demonstrates that a two-index {@code And} query works correctly.
	 */
	@Test
	void twoIndexAndQueryWorks()
	{
		var condition = ACTOR_INDEX.is(200L).and(GROUP_INDEX.is(1000L));
		var results = gigaMap.query(condition).toList();
		assertEquals(1, results.size());
		assertEquals(20L, results.get(0).id());
	}

	/**
	 * A three-index {@code And} query for a non-existent tuple should return empty.
	 *
	 * <p>We query for {@code (actorId=999, groupId=2000, roleId=20000)}.
	 * No entity in the store has {@code actorId=999}, so the result should be empty.
	 *
	 * <p><b>Note:</b> With {@code BinaryIndexerLong} and small values this test
	 * currently passes. The bug was originally observed with
	 * {@code BinaryIndexerTsid} using {@code Tsid.fast()} values (large
	 * pseudo-random longs) where the standalone {@code And} also returned
	 * wrong entities. The {@code Or} wrapping {@code And} variant below fails
	 * reliably with both indexer types.
	 */
	@Test
	void threeIndexAndQueryForNonExistentTupleShouldBeEmpty()
	{
		var condition = ACTOR_INDEX.is(999L)
			.and(GROUP_INDEX.is(2000L))
			.and(ROLE_INDEX.is(20000L));

		var results = gigaMap.query(condition).toList();

		assertTrue(
			results.isEmpty(),
			() -> "Expected empty result for non-existent tuple (actor=999, group=2000, role=20000), "
				+ "but got: " + results.stream()
				.map(r -> "(id=%d, actor=%d, group=%d, role=%d)".formatted(
					r.id(), r.actorId(), r.groupId(), r.roleId()))
				.toList()
		);
	}

	/**
	 * Same three-index {@code And} but with the nesting order used in the original
	 * {@code ActorGroupRole.Store} code: {@code group.and(role.and(actor))}.
	 */
	@Test
	void threeIndexAndWithNestedGroupingShouldBeEmpty()
	{
		var condition = GROUP_INDEX.is(2000L)
			.and(ROLE_INDEX.is(20000L)
				.and(ACTOR_INDEX.is(999L)));

		var results = gigaMap.query(condition).toList();

		assertTrue(
			results.isEmpty(),
			() -> "Expected empty result for nested And (group=2000, role=20000, actor=999), "
				+ "but got: " + results.stream()
				.map(r -> "(id=%d, actor=%d, group=%d, role=%d)".formatted(
					r.id(), r.actorId(), r.groupId(), r.roleId()))
				.toList()
		);
	}

	/**
	 * Another angle: querying a tuple whose {@code actorId} and {@code groupId}
	 * match entity 30 but whose {@code roleId} does <b>not</b> (entity 30's
	 * roleId is 10000, the query asks for 20000). The result must be empty.
	 */
	@Test
	void andQueryWithWrongRoleShouldBeEmpty()
	{
		var condition = ACTOR_INDEX.is(300L)  // entity 30 has actor=300 ✓
			.and(GROUP_INDEX.is(2000L))   // entity 30 has group=2000 ✓
			.and(ROLE_INDEX.is(20000L));  // entity 30 has role=10000 ✗

		var results = gigaMap.query(condition).toList();

		assertTrue(
			results.isEmpty(),
			() -> "Expected empty result because entity 30 (actor=300, group=2000) has role=10000, not 20000, "
				+ "but got: " + results.stream()
				.map(r -> "(id=%d, actor=%d, group=%d, role=%d)".formatted(
					r.id(), r.actorId(), r.groupId(), r.roleId()))
				.toList()
		);
	}

	/**
	 * Shows that the <b>workaround</b> (sequential {@code GigaQuery.and()})
	 * produces the correct empty result.
	 */
	@Test
	void sequentialAndQueryProducesCorrectResult()
	{
		var query = gigaMap.query();
		query.and(ACTOR_INDEX.is(999L));
		query.and(GROUP_INDEX.is(2000L));
		query.and(ROLE_INDEX.is(20000L));

		var results = query.toList();

		assertTrue(results.isEmpty(),
			"Sequential GigaQuery.and() should correctly intersect bitmaps");
	}

	/**
	 * <b>THE BUG:</b> An {@code Or} wrapping an {@code And} returns
	 * <b>all entities</b> instead of an empty list.
	 *
	 * <p>We query for {@code id=999 OR (actor=999 AND group=2000 AND role=20000)}.
	 * Neither branch matches any entity, so the result should be empty.
	 *
	 * <p>The bug causes the query to return every entity in the store.
	 */
	@Test
	void orWrappingAndForNonExistentIdShouldBeEmpty()
	{
		Condition<Assignment> idMatch = ID_INDEX.is(999L);
		Condition<Assignment> tupleMatch = ACTOR_INDEX.is(999L)
			.and(GROUP_INDEX.is(2000L))
			.and(ROLE_INDEX.is(20000L));

		var results = gigaMap.query(idMatch.or(tupleMatch)).toList();

		assertTrue(
			results.isEmpty(),
			() -> "Expected empty result for id=999 OR non-existent tuple, but got: "
				+ results.stream()
				.map(r -> "(id=%d, actor=%d, group=%d, role=%d)".formatted(
					r.id(), r.actorId(), r.groupId(), r.roleId()))
				.toList()
		);
	}

	/**
	 * Variant using {@link UUID}-derived long values &mdash; uses only standard
	 * JDK types.
	 *
	 * <p>The fixed UUIDs encode their distinguishing bits in the
	 * least-significant 64 bits, so {@link UUID#getLeastSignificantBits} is
	 * used to extract distinct long values per entity. This exercises the same
	 * composite {@code And}/{@code Or} code path with a second, independent set
	 * of keys.
	 */
	@Nested
	class UuidVariantTests
	{

		record UuidAssignment(UUID id, UUID actorId, UUID groupId, UUID roleId)
		{
		}

		static final BinaryIndexerLong<UuidAssignment> UUID_ID_INDEX = new BinaryIndexerLong.Abstract<>()
		{
			@Override
			protected Long getLong(UuidAssignment a)
			{
				return a.id().getLeastSignificantBits();
			}
		};

		static final BinaryIndexerLong<UuidAssignment> UUID_ACTOR_INDEX = new BinaryIndexerLong.Abstract<>()
		{
			@Override
			protected Long getLong(UuidAssignment a)
			{
				return a.actorId().getLeastSignificantBits();
			}
		};

		static final BinaryIndexerLong<UuidAssignment> UUID_GROUP_INDEX = new BinaryIndexerLong.Abstract<>()
		{
			@Override
			protected Long getLong(UuidAssignment a)
			{
				return a.groupId().getLeastSignificantBits();
			}
		};

		static final BinaryIndexerLong<UuidAssignment> UUID_ROLE_INDEX = new BinaryIndexerLong.Abstract<>()
		{
			@Override
			protected Long getLong(UuidAssignment a)
			{
				return a.roleId().getLeastSignificantBits();
			}
		};

		private GigaMap<UuidAssignment> uuidMap;

		@BeforeEach
		void setUpUuid()
		{
			uuidMap = GigaMap.<UuidAssignment>Builder()
				.withBitmapIdentityIndex(UUID_ID_INDEX)
				.withBitmapIndex(UUID_ACTOR_INDEX)
				.withBitmapIndex(UUID_GROUP_INDEX)
				.withBitmapIndex(UUID_ROLE_INDEX)
				.build();

			// Distinct tuples using fixed UUIDs for reproducibility
			uuidMap.add(new UuidAssignment(
				UUID.fromString("00000000-0000-0000-0000-00000000000a"),
				UUID.fromString("00000000-0000-0000-0000-000000000064"),
				UUID.fromString("00000000-0000-0000-0000-0000000003e8"),
				UUID.fromString("00000000-0000-0000-0000-000000002710")));
			uuidMap.add(new UuidAssignment(
				UUID.fromString("00000000-0000-0000-0000-000000000014"),
				UUID.fromString("00000000-0000-0000-0000-0000000000c8"),
				UUID.fromString("00000000-0000-0000-0000-0000000003e8"),
				UUID.fromString("00000000-0000-0000-0000-000000002710")));
			uuidMap.add(new UuidAssignment(
				UUID.fromString("00000000-0000-0000-0000-00000000001e"),
				UUID.fromString("00000000-0000-0000-0000-00000000012c"),
				UUID.fromString("00000000-0000-0000-0000-0000000007d0"),
				UUID.fromString("00000000-0000-0000-0000-000000002710")));
			uuidMap.add(new UuidAssignment(
				UUID.fromString("00000000-0000-0000-0000-000000000028"),
				UUID.fromString("00000000-0000-0000-0000-000000000190"),
				UUID.fromString("00000000-0000-0000-0000-0000000007d0"),
				UUID.fromString("00000000-0000-0000-0000-000000004e20")));

			assertEquals(4, uuidMap.size(), "Setup: expected 4 entities in UUID map");
		}

		/**
		 * Three-index {@code And} with UUID-derived long values.
		 *
		 * <p>Queries for a non-existent actor while matching group and role
		 * that exist together in entity 4.
		 */
		@Test
		void threeIndexAndQueryForNonExistentTupleShouldBeEmpty()
		{
			var condition = UUID_ACTOR_INDEX.is(
					UUID.fromString("00000000-0000-0000-0000-0000000003e7")
						.getLeastSignificantBits())
				.and(UUID_GROUP_INDEX.is(
					UUID.fromString("00000000-0000-0000-0000-0000000007d0")
						.getLeastSignificantBits()))
				.and(UUID_ROLE_INDEX.is(
					UUID.fromString("00000000-0000-0000-0000-000000004e20")
						.getLeastSignificantBits()));

			var results = uuidMap.query(condition).toList();

			assertTrue(
				results.isEmpty(),
				() -> "Expected empty result for non-existent UUID tuple, but got: "
					+ results.stream()
					.map(r -> "(id=%s, actor=%s, group=%s, role=%s)".formatted(
						r.id(), r.actorId(), r.groupId(), r.roleId()))
					.toList()
			);
		}

		/**
		 * {@code Or} wrapping a three-index {@code And} with UUID-derived long
		 * values.
		 */
		@Test
		void orWrappingAndForNonExistentIdShouldBeEmpty()
		{
			Condition<UuidAssignment> idMatch = UUID_ID_INDEX.is(
				UUID.fromString("00000000-0000-0000-0000-0000000003e7")
					.getLeastSignificantBits());
			Condition<UuidAssignment> tupleMatch = UUID_ACTOR_INDEX.is(
					UUID.fromString("00000000-0000-0000-0000-0000000003e7")
						.getLeastSignificantBits())
				.and(UUID_GROUP_INDEX.is(
					UUID.fromString("00000000-0000-0000-0000-0000000007d0")
						.getLeastSignificantBits()))
				.and(UUID_ROLE_INDEX.is(
					UUID.fromString("00000000-0000-0000-0000-000000004e20")
						.getLeastSignificantBits()));

			var results = uuidMap.query(idMatch.or(tupleMatch)).toList();

			assertTrue(
				results.isEmpty(),
				() -> "Expected empty result for id=999 OR non-existent UUID tuple, but got: "
					+ results.stream()
					.map(r -> "(id=%s, actor=%s, group=%s, role=%s)".formatted(
						r.id(), r.actorId(), r.groupId(), r.roleId()))
					.toList()
			);
		}
	}
}
