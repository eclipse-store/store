package org.eclipse.store.gigamap.indexer.binary;

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

/// Reproduces a bug in EclipseStore GigaMap where [Condition.And] and
/// [Condition.Or] combined with [BinaryBitmapIndex] (created by
/// [GigaMap.Builder#withBitmapIndex]) return entities that match only a
/// **subset** of the conjuncts, instead of requiring **all** conjuncts to match.
///
/// ## Setup
///
/// We create a GigaMap with three [BinaryIndexerLong] bitmap indexes and insert
/// six entities.  Some share individual fields (e.g. `groupId` or `roleId`) but
/// no two share the full `(actorId, groupId, roleId)` triple:
///
/// | id | actorId | groupId | roleId |
/// |----|---------|---------|--------|
/// | 10 | 100     | 1000    | 10000  |
/// | 20 | 200     | 1000    | 10000  |
/// | 30 | 300     | 2000    | 10000  |
/// | 40 | 400     | 2000    | 20000  |
/// | 50 | 500     | 1000    | 20000  |
/// | 60 | 600     | 2000    | 30000  |
///
/// ## The Bug
///
/// ### Manifestation 1 — nested `And`
///
/// When we query for the **non-existent** tuple `(actorId=999, groupId=2000, roleId=20000)`
/// using a nested `Condition.And` (the exact nesting order from production code):
///
/// ```java
/// var condition = GROUP_INDEX.is(2000L)
///         .and(ROLE_INDEX.is(20000L)
///                 .and(ACTOR_INDEX.is(999L)));
/// var results = gigaMap.query(condition).toList();
/// ```
///
/// The expected result is **empty** (no entity has `actorId=999`).
///
/// The actual result is entity `40` `(actorId=400, groupId=2000, roleId=20000)` —
/// an entity that matches `groupId` and `roleId` but **not** `actorId`.  The
/// `And` evaluator returned a partial match instead of a strict intersection.
///
/// ### Manifestation 2 — `Or` wrapping `And`
///
/// When the `And` is wrapped in an `Or`:
///
/// ```java
/// var condition = ID_INDEX.is(999L)
///         .or(ACTOR_INDEX.is(999L)
///                 .and(GROUP_INDEX.is(2000L))
///                 .and(ROLE_INDEX.is(20000L)));
/// ```
///
/// The query returns **all entities in the map** instead of an empty list.
///
/// ## Root Cause Analysis
///
/// [BinaryIndexer] stores values in a [BinaryBitmapIndex] (a bitmap-backed index
/// optimised for high-cardinality `long` values). When a query receives a
/// composite [Condition.And] or [Condition.Or] tree, the evaluator is supposed
/// to intersect (for `And`) or union (for `Or`) the bitmaps from each
/// sub-condition.
///
/// Instead, the evaluator appears to:
/// 1. Mis-resolve bitmap segments during intersection/union (possibly due to
///    improper offset arithmetic when combining multiple `BinaryBitmapIndex`
///    bitsets).
/// 2. Or short-circuit and return the first entity found in **any** sub-index
///    bitmap without completing the full conjunction.
///
/// This means composite conditions are effectively treated as broken
/// partial-matches instead of strict set operations.
///
/// ## Workaround
///
/// Avoid composing [Condition.And] or [Condition.Or] trees manually and passing
/// them to `gigaMap.query(...)`. Instead, apply conditions sequentially via
/// `GigaQuery.and(Condition)`:
///
/// ```java
/// var query = gigaMap.query();
/// query.and(ACTOR_INDEX.is(999L));
/// query.and(GROUP_INDEX.is(2000L));
/// query.and(ROLE_INDEX.is(20000L));
/// var results = query.toList(); // correct — empty
/// ```
///
/// The [GigaQueryBuilder] abstraction in the `peruncs` stack already implements
/// this workaround by maintaining a list of standalone conditions and applying
/// them one-by-one in its `create()` method.
///
/// ## Affected Versions
///
/// Observed on EclipseStore 3.1.0. The same bug likely affects any version that
/// uses the `BinaryBitmapIndex` condition-evaluation path for composite
/// conditions.
///
/// ## Related
///
/// - [GigaQueryBuilder] — documents the same workaround:
///   `WORKAROUND: This builder avoids using [Condition#and(Condition)] internally`
/// - [BinaryIndexBug] — related reproduction for `And` with mixed indexers
public class BinaryIndexerCompositeConditionTest
{

	/// Simple entity with three independently-indexed `long` fields.
	/// Mirrors the `(actor, group, role)` tuple pattern used in RBAC stores.
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

	/// Demonstrates that a single-index `is()` query works correctly.
	@Test
	void singleIndexQueryWorks()
	{
		var results = gigaMap.query(ACTOR_INDEX.is(200L)).toList();
		assertEquals(1, results.size());
		assertEquals(20L, results.get(0).id());
	}

	/// Demonstrates that a two-index `And` query works correctly.
	@Test
	void twoIndexAndQueryWorks()
	{
		var condition = ACTOR_INDEX.is(200L).and(GROUP_INDEX.is(1000L));
		var results = gigaMap.query(condition).toList();
		assertEquals(1, results.size());
		assertEquals(20L, results.get(0).id());
	}

	/// A three-index `And` query for a non-existent tuple should return empty.
	///
	/// We query for `(actorId=999, groupId=2000, roleId=20000)`.
	/// No entity in the store has `actorId=999`, so the result should be empty.
	///
	/// **Note:** With `BinaryIndexerLong` and small values this test currently
	/// passes.  The bug was originally observed with `BinaryIndexerTsid` using
	/// `Tsid.fast()` values (large pseudo-random longs) where the standalone
	/// `And` also returned wrong entities.  The `Or` wrapping `And` variant
	/// below fails reliably with both indexer types.
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

	/// Same three-index `And` but with the nesting order used in the original
	/// `ActorGroupRole.Store` code: `group.and(role.and(actor))`.
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

	/// Another angle: querying for a tuple that matches `groupId` and `roleId`
	/// but **not** `actorId` should also be empty.
	@Test
	void andQueryWithWrongActorShouldBeEmpty()
	{
		var condition = ACTOR_INDEX.is(300L) // entity 30 has actor=300
			.and(GROUP_INDEX.is(2000L))   // entity 30 has group=2000 ✓
			.and(ROLE_INDEX.is(20000L));  // entity 30 has role=10000 ✗

		var results = gigaMap.query(condition).toList();

		assertTrue(
			results.isEmpty(),
			() -> "Expected empty result because no entity has (actor=300, group=2000, role=20000), "
				+ "but got: " + results.stream()
				.map(r -> "(id=%d, actor=%d, group=%d, role=%d)".formatted(
					r.id(), r.actorId(), r.groupId(), r.roleId()))
				.toList()
		);
	}

	/// Shows that the **workaround** (sequential `GigaQuery.and()`) produces
	/// the correct empty result.
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

	/// **THE BUG:** An `Or` wrapping an `And` returns **all entities** instead
	/// of an empty list.
	///
	/// We query for `id=999 OR (actor=999 AND group=2000 AND role=20000)`.
	/// Neither branch matches any entity, so the result should be empty.
	///
	/// The bug causes the query to return every entity in the store.
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

	/// Variant using [UUID]-derived long values — uses only standard JDK types.
	///
	/// The fixed UUIDs encode their distinguishing bits in the least-significant
	/// 64 bits, so [UUID#getLeastSignificantBits] is used to extract distinct
	/// long values per entity.  This exercises the same composite `And`/`Or`
	/// code path with a second, independent set of keys.
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

		/// Three-index `And` with UUID-derived long values.
		///
		/// Queries for a non-existent actor while matching group and role that
		/// exist together in entity 4.
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

		/// `Or` wrapping a three-index `And` with UUID-derived long values.
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
