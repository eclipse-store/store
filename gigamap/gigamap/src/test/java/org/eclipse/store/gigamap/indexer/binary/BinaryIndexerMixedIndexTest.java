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
import org.eclipse.store.gigamap.types.BinaryIndexerUUID;
import org.eclipse.store.gigamap.types.Condition;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.GigaQuery;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests combining {@link BinaryIndexerLong} (and {@link BinaryIndexerUUID}) with regular
 * bitmap indexes ({@link IndexerString}, {@link IndexerInteger}) in the same {@link GigaMap}.
 * Covers AND/OR/NOT conditions, sequential {@code GigaQuery.and()}, and more complex combos.
 */
public class BinaryIndexerMixedIndexTest
{
	// -----------------------------------------------------------------------
	// Section 1: BinaryIndexerLong + IndexerString + IndexerInteger
	// -----------------------------------------------------------------------

	/**
	 * Event entity:
	 *   typeId  → BinaryIndexerLong  (binary bitmap)
	 *   region  → IndexerString      (regular bitmap)
	 *   priority→ IndexerInteger     (regular bitmap)
	 *
	 * Dataset (6 events, 0-indexed by insertion order):
	 *   typeId=1, region="EU",   priority=1  (Aardvark)
	 *   typeId=1, region="US",   priority=2  (Bear)
	 *   typeId=2, region="EU",   priority=1  (Cheetah)
	 *   typeId=2, region="US",   priority=3  (Dolphin)
	 *   typeId=3, region="EU",   priority=2  (Elephant)
	 *   typeId=3, region="APAC", priority=1  (Fox)
	 */
	@Nested
	class LongStringInteger
	{
		record Event(long typeId, String region, int priority, String name) {}

		static final BinaryIndexerLong<Event> TYPE_INDEX = new BinaryIndexerLong.Abstract<>()
		{
			@Override
			protected Long getLong(final Event e) { return e.typeId(); }
		};

		static final IndexerString<Event> REGION_INDEX = new IndexerString.Abstract<>()
		{
			@Override
			protected String getString(final Event e) { return e.region(); }
		};

		static final IndexerInteger<Event> PRIORITY_INDEX = new IndexerInteger.Abstract<>()
		{
			@Override
			protected Integer getInteger(final Event e) { return e.priority(); }
		};

		private GigaMap<Event> map;
		private Event aardvark, bear, cheetah, dolphin, elephant, fox;

		@BeforeEach
		void setUp()
		{
			map = GigaMap.<Event>Builder()
				.withBitmapIndex(TYPE_INDEX)
				.withBitmapIndex(REGION_INDEX)
				.withBitmapIndex(PRIORITY_INDEX)
				.build();

			aardvark = new Event(1L, "EU",   1, "Aardvark");
			bear     = new Event(1L, "US",   2, "Bear");
			cheetah  = new Event(2L, "EU",   1, "Cheetah");
			dolphin  = new Event(2L, "US",   3, "Dolphin");
			elephant = new Event(3L, "EU",   2, "Elephant");
			fox      = new Event(3L, "APAC", 1, "Fox");

			map.addAll(aardvark, bear, cheetah, dolphin, elephant, fox);
		}

		// -----------------------------------------------------------------------
		// Sequential GigaQuery.and()
		// -----------------------------------------------------------------------

		@Test
		void binaryAndStringViaSequentialAnd()
		{
			final GigaQuery<Event> q = map.query();
			q.and(TYPE_INDEX.is(1L));
			q.and(REGION_INDEX.is("EU"));

			final List<Event> results = q.toList();
			assertEquals(1, results.size());
			assertEquals("Aardvark", results.get(0).name());
		}

		@Test
		void binaryAndIntegerViaSequentialAnd()
		{
			final GigaQuery<Event> q = map.query();
			q.and(TYPE_INDEX.is(3L));
			q.and(PRIORITY_INDEX.is(2));

			final List<Event> results = q.toList();
			assertEquals(1, results.size());
			assertEquals("Elephant", results.get(0).name());
		}

		@Test
		void threeWayAndBinaryStringInteger()
		{
			final GigaQuery<Event> q = map.query();
			q.and(TYPE_INDEX.is(2L));
			q.and(REGION_INDEX.is("EU"));
			q.and(PRIORITY_INDEX.is(1));

			final List<Event> results = q.toList();
			assertEquals(1, results.size());
			assertEquals("Cheetah", results.get(0).name());
		}

		@Test
		void sequentialAndReturnsEmptyWhenNoOverlap()
		{
			final GigaQuery<Event> q = map.query();
			q.and(TYPE_INDEX.is(1L));
			q.and(REGION_INDEX.is("APAC")); // type=1 has no APAC entries

			assertTrue(q.toList().isEmpty());
		}

		// -----------------------------------------------------------------------
		// Condition.and()
		// -----------------------------------------------------------------------

		@Test
		void conditionAndBinaryAndString()
		{
			final Condition<Event> cond = TYPE_INDEX.is(2L).and(REGION_INDEX.is("US"));
			final List<Event> results = map.query(cond).toList();
			assertEquals(1, results.size());
			assertEquals("Dolphin", results.get(0).name());
		}

		@Test
		void conditionAndBinaryAndInteger()
		{
			final Condition<Event> cond = TYPE_INDEX.is(1L).and(PRIORITY_INDEX.is(2));
			final List<Event> results = map.query(cond).toList();
			assertEquals(1, results.size());
			assertEquals("Bear", results.get(0).name());
		}

		@Test
		void conditionAndStringAndBinary()
		{
			// Same as above but condition order reversed (string AND binary)
			final Condition<Event> cond = REGION_INDEX.is("EU").and(TYPE_INDEX.is(3L));
			final List<Event> results = map.query(cond).toList();
			assertEquals(1, results.size());
			assertEquals("Elephant", results.get(0).name());
		}

		@Test
		void conditionAndIntegerAndBinary()
		{
			final Condition<Event> cond = PRIORITY_INDEX.is(1).and(TYPE_INDEX.is(2L));
			final List<Event> results = map.query(cond).toList();
			assertEquals(1, results.size());
			assertEquals("Cheetah", results.get(0).name());
		}

		@Test
		void conditionAndAllThreeIndexes()
		{
			final Condition<Event> cond = TYPE_INDEX.is(3L)
				.and(REGION_INDEX.is("APAC"))
				.and(PRIORITY_INDEX.is(1));

			final List<Event> results = map.query(cond).toList();
			assertEquals(1, results.size());
			assertEquals("Fox", results.get(0).name());
		}

		// -----------------------------------------------------------------------
		// Condition.or()
		// -----------------------------------------------------------------------

		@Test
		void conditionOrBinaryAndString()
		{
			// type=1 → Aardvark, Bear; region="APAC" → Fox → union = 3
			final Condition<Event> cond = TYPE_INDEX.is(1L).or(REGION_INDEX.is("APAC"));
			assertEquals(3, map.query(cond).count());
		}

		@Test
		void conditionOrBinaryAndInteger()
		{
			// type=3 → Elephant, Fox; priority=3 → Dolphin → union = 3
			final Condition<Event> cond = TYPE_INDEX.is(3L).or(PRIORITY_INDEX.is(3));
			assertEquals(3, map.query(cond).count());
		}

		@Test
		void conditionOrStringAndBinary()
		{
			// region="US" → Bear, Dolphin; type=3 → Elephant, Fox → union = 4
			final Condition<Event> cond = REGION_INDEX.is("US").or(TYPE_INDEX.is(3L));
			assertEquals(4, map.query(cond).count());
		}

		@Test
		void conditionOrWithOverlap()
		{
			// type=1 → Aardvark, Bear; priority=2 → Bear, Elephant → union = 3
			final Condition<Event> cond = TYPE_INDEX.is(1L).or(PRIORITY_INDEX.is(2));
			assertEquals(3, map.query(cond).count());
		}

		// -----------------------------------------------------------------------
		// NOT conditions mixed
		// -----------------------------------------------------------------------

		@Test
		void binaryNotAndString()
		{
			// type != 1 → Cheetah, Dolphin, Elephant, Fox; AND region="EU" → Cheetah, Elephant
			final Condition<Event> cond = TYPE_INDEX.not(1L).and(REGION_INDEX.is("EU"));
			final List<Event> results = map.query(cond).toList();
			assertEquals(2, results.size());
			assertTrue(results.stream().noneMatch(e -> e.typeId() == 1L));
			assertTrue(results.stream().allMatch(e -> "EU".equals(e.region())));
		}

		@Test
		void stringNotAndBinary()
		{
			// region != "EU" → Bear, Dolphin, Fox; AND type=3 → Fox
			final Condition<Event> cond = REGION_INDEX.not("EU").and(TYPE_INDEX.is(3L));
			final List<Event> results = map.query(cond).toList();
			assertEquals(1, results.size());
			assertEquals("Fox", results.get(0).name());
		}

		// -----------------------------------------------------------------------
		// notIn on binary combined with regular index
		// -----------------------------------------------------------------------

		@Test
		void binaryNotInAndString()
		{
			// type not in {1, 2} → Elephant, Fox; AND region="EU" → Elephant
			final Condition<Event> cond = TYPE_INDEX.notIn(1L, 2L).and(REGION_INDEX.is("EU"));
			final List<Event> results = map.query(cond).toList();
			assertEquals(1, results.size());
			assertEquals("Elephant", results.get(0).name());
		}

		@Test
		void binaryInAndInteger()
		{
			// type in {1, 3} → Aardvark, Bear, Elephant, Fox; AND priority=1 → Aardvark, Fox
			final Condition<Event> cond = TYPE_INDEX.in(1L, 3L).and(PRIORITY_INDEX.is(1));
			final List<Event> results = map.query(cond).toList();
			assertEquals(2, results.size());
			assertTrue(results.stream().allMatch(e -> e.priority() == 1));
			assertTrue(results.stream().noneMatch(e -> e.typeId() == 2L));
		}

		// -----------------------------------------------------------------------
		// Size consistency
		// -----------------------------------------------------------------------

		@Test
		void sizeRemainsUnaffectedByQuerying()
		{
			assertEquals(6, map.size());
			map.query(TYPE_INDEX.is(1L).and(REGION_INDEX.is("EU"))).toList();
			assertEquals(6, map.size());
		}

		// -----------------------------------------------------------------------
		// CRUD: add/remove verify both indexes stay consistent
		// -----------------------------------------------------------------------

		@Test
		void afterAddBothIndexesReflectNewEntity()
		{
			final Event newEvent = new Event(4L, "LATAM", 5, "Gorilla");
			map.add(newEvent);

			assertEquals(7, map.size());

			// Both binary and string indexes must find the new entity
			final Condition<Event> cond = TYPE_INDEX.is(4L).and(REGION_INDEX.is("LATAM"));
			final List<Event> results = map.query(cond).toList();
			assertEquals(1, results.size());
			assertEquals("Gorilla", results.get(0).name());
		}

		@Test
		void afterRemoveBothIndexesNoLongerFindEntity()
		{
			map.remove(aardvark);
			assertEquals(5, map.size());

			// Neither binary nor string query should return Aardvark
			assertTrue(map.query(TYPE_INDEX.is(1L).and(REGION_INDEX.is("EU"))).toList().isEmpty());
		}

		@Test
		void noOpUpdateDoesNotCorruptIndexes()
		{
			// Event is a record (immutable) so the update body is a no-op.
			// Verify that a no-op update leaves both indexes in the correct state.
			map.update(aardvark, e -> {});

			// type=1 → Aardvark, Bear (still 2)
			assertEquals(2, map.query(TYPE_INDEX.is(1L)).count());
			// region="EU" AND type=1 → only Aardvark (1)
			assertEquals(1, map.query(REGION_INDEX.is("EU").and(TYPE_INDEX.is(1L))).count());
			// region="EU" → Aardvark, Cheetah, Elephant (still 3)
			assertEquals(3, map.query(REGION_INDEX.is("EU")).count());
		}
	}

	// -----------------------------------------------------------------------
	// Section 2: BinaryIndexerUUID (composite binary) + IndexerString
	// -----------------------------------------------------------------------

	/**
	 * Combines {@link BinaryIndexerUUID} (a composite binary index encoding two longs)
	 * with a regular {@link IndexerString} index on the same entity.
	 */
	@Nested
	class UuidAndString
	{
		static class Asset
		{
			final UUID   id;
			      String category;
			      String name;

			Asset(final UUID id, final String category, final String name)
			{
				this.id       = id;
				this.category = category;
				this.name     = name;
			}
		}

		static final BinaryIndexerUUID<Asset> UUID_INDEX = new BinaryIndexerUUID.Abstract<>()
		{
			@Override
			protected UUID getUUID(final Asset a) { return a.id; }
		};

		static final IndexerString<Asset> CATEGORY_INDEX = new IndexerString.Abstract<>()
		{
			@Override
			protected String getString(final Asset a) { return a.category; }
		};

		private final UUID idA = UUID.randomUUID();
		private final UUID idB = UUID.randomUUID();
		private final UUID idC = UUID.randomUUID();

		private GigaMap<Asset> map;
		private Asset assetA, assetB, assetC, assetD;

		@BeforeEach
		void setUp()
		{
			map = GigaMap.<Asset>Builder()
				.withBitmapIndex(UUID_INDEX)
				.withBitmapIndex(CATEGORY_INDEX)
				.build();

			assetA = new Asset(idA, "hardware", "GPU");
			assetB = new Asset(idB, "software", "IDE");
			assetC = new Asset(idC, "hardware", "CPU");
			assetD = new Asset(idA, "software", "Driver"); // same UUID as assetA, different category

			map.addAll(assetA, assetB, assetC, assetD);
		}

		@Test
		void uuidAndCategoryBothMatching()
		{
			// Only assetA has idA AND "hardware"
			final Condition<Asset> cond = UUID_INDEX.is(idA).and(CATEGORY_INDEX.is("hardware"));
			final List<Asset> results = map.query(cond).toList();
			assertEquals(1, results.size());
			assertEquals("GPU", results.get(0).name);
		}

		@Test
		void uuidAndCategoryNoOverlap()
		{
			// idB is software (Bear), not hardware
			final Condition<Asset> cond = UUID_INDEX.is(idB).and(CATEGORY_INDEX.is("hardware"));
			assertTrue(map.query(cond).toList().isEmpty());
		}

		@Test
		void uuidOrCategory()
		{
			// idA → assetA, assetD (2); "hardware" → assetA, assetC (2); union = assetA, assetC, assetD = 3
			final Condition<Asset> cond = UUID_INDEX.is(idA).or(CATEGORY_INDEX.is("hardware"));
			assertEquals(3, map.query(cond).count());
		}

		@Test
		void categoryAndUuidViaSequentialAnd()
		{
			final GigaQuery<Asset> q = map.query();
			q.and(CATEGORY_INDEX.is("software"));
			q.and(UUID_INDEX.is(idA));

			// assetD has idA AND software
			final List<Asset> results = q.toList();
			assertEquals(1, results.size());
			assertEquals("Driver", results.get(0).name);
		}

		@Test
		void uuidAndCategoryThreeWayOr()
		{
			// idA → assetA, assetD; idC → assetC; union OR category="software" → assetA, assetB, assetC, assetD = 4
			final Condition<Asset> cond = UUID_INDEX.is(idA).or(UUID_INDEX.is(idC)).or(CATEGORY_INDEX.is("software"));
			assertEquals(4, map.query(cond).count());
		}

		@Test
		void uuidQueryReturnsAllAssetsWithSameId()
		{
			// Both assetA and assetD share idA
			final List<Asset> results = map.query(UUID_INDEX.is(idA)).toList();
			assertEquals(2, results.size());
			assertTrue(results.stream().allMatch(a -> idA.equals(a.id)));
		}

		@Test
		void afterRemoveUuidIndexIsUpdated()
		{
			map.remove(assetA);
			// idA still has assetD
			final List<Asset> results = map.query(UUID_INDEX.is(idA)).toList();
			assertEquals(1, results.size());
			assertEquals("Driver", results.get(0).name);
		}

		@Test
		void afterRemoveLastWithUuidQueryReturnsEmpty()
		{
			map.remove(assetB);
			assertTrue(map.query(UUID_INDEX.is(idB)).toList().isEmpty());
		}
	}

	// -----------------------------------------------------------------------
	// Section 3: Two BinaryIndexerLong + IndexerInteger (3-index map)
	// -----------------------------------------------------------------------

	/**
	 * Tests a GigaMap with two separate {@link BinaryIndexerLong} indexes and one
	 * regular {@link IndexerInteger} index, verifying complex AND/OR chains
	 * across all three indexes simultaneously.
	 */
	@Nested
	class TwoBinaryPlusInteger
	{
		record Ticket(long typeId, long ownerId, int severity, String title) {}

		static final BinaryIndexerLong<Ticket> TYPE_INDEX = new BinaryIndexerLong.Abstract<>()
		{
			@Override
			protected Long getLong(final Ticket t) { return t.typeId(); }
		};

		static final BinaryIndexerLong<Ticket> OWNER_INDEX = new BinaryIndexerLong.Abstract<>()
		{
			@Override
			protected Long getLong(final Ticket t) { return t.ownerId(); }
		};

		static final IndexerInteger<Ticket> SEVERITY_INDEX = new IndexerInteger.Abstract<>()
		{
			@Override
			protected Integer getInteger(final Ticket t) { return t.severity(); }
		};

		private GigaMap<Ticket> map;

		// Dataset:
		// type=1, owner=10, sev=1 → "Alpha"
		// type=1, owner=20, sev=2 → "Beta"
		// type=2, owner=10, sev=1 → "Gamma"
		// type=2, owner=20, sev=3 → "Delta"
		// type=3, owner=30, sev=2 → "Epsilon"
		@BeforeEach
		void setUp()
		{
			map = GigaMap.<Ticket>Builder()
				.withBitmapIndex(TYPE_INDEX)
				.withBitmapIndex(OWNER_INDEX)
				.withBitmapIndex(SEVERITY_INDEX)
				.build();

			map.addAll(
				new Ticket(1L, 10L, 1, "Alpha"),
				new Ticket(1L, 20L, 2, "Beta"),
				new Ticket(2L, 10L, 1, "Gamma"),
				new Ticket(2L, 20L, 3, "Delta"),
				new Ticket(3L, 30L, 2, "Epsilon")
			);
		}

		@Test
		void twoBinaryAndIntegerAllMatch()
		{
			// type=1, owner=10, sev=1 → Alpha only
			final Condition<Ticket> cond = TYPE_INDEX.is(1L)
				.and(OWNER_INDEX.is(10L))
				.and(SEVERITY_INDEX.is(1));

			final List<Ticket> results = map.query(cond).toList();
			assertEquals(1, results.size());
			assertEquals("Alpha", results.get(0).title());
		}

		@Test
		void twoBinaryAndIntegerNoMatch()
		{
			// type=1 AND owner=10 AND sev=2 → no such ticket
			final Condition<Ticket> cond = TYPE_INDEX.is(1L)
				.and(OWNER_INDEX.is(10L))
				.and(SEVERITY_INDEX.is(2));

			assertTrue(map.query(cond).toList().isEmpty());
		}

		@Test
		void twoBinaryOrOneInteger()
		{
			// (type=3 OR owner=20) → Beta, Delta, Epsilon (3 tickets)
			final Condition<Ticket> cond = TYPE_INDEX.is(3L).or(OWNER_INDEX.is(20L));
			assertEquals(3, map.query(cond).count());
		}

		@Test
		void binaryAndIntegerOrBinary()
		{
			// (sev=1 AND type=1) OR owner=30 → Alpha, Epsilon
			final Condition<Ticket> cond =
				SEVERITY_INDEX.is(1).and(TYPE_INDEX.is(1L))
				.or(OWNER_INDEX.is(30L));

			assertEquals(2, map.query(cond).count());
		}

		@Test
		void integerAndTwoBinaryViaSequentialAnd()
		{
			final GigaQuery<Ticket> q = map.query();
			q.and(SEVERITY_INDEX.is(2));
			q.and(TYPE_INDEX.is(1L));
			q.and(OWNER_INDEX.is(20L));

			final List<Ticket> results = q.toList();
			assertEquals(1, results.size());
			assertEquals("Beta", results.get(0).title());
		}

		@Test
		void binaryNotCombinedWithIntegerAndBinary()
		{
			// type != 1 → Gamma, Delta, Epsilon; AND sev=1 → Gamma; AND owner=10 → Gamma
			final Condition<Ticket> cond = TYPE_INDEX.not(1L)
				.and(SEVERITY_INDEX.is(1))
				.and(OWNER_INDEX.is(10L));

			final List<Ticket> results = map.query(cond).toList();
			assertEquals(1, results.size());
			assertEquals("Gamma", results.get(0).title());
		}

		@Test
		void countIsConsistentAcrossAllThreeIndexes()
		{
			// sev=2 → Beta, Epsilon (2 tickets)
			assertEquals(2, map.query(SEVERITY_INDEX.is(2)).count());

			// After adding more tickets this count should update correctly
			map.add(new Ticket(4L, 40L, 2, "Zeta"));
			assertEquals(3, map.query(SEVERITY_INDEX.is(2)).count());

			// The binary index should also reflect the new ticket
			assertEquals(1, map.query(TYPE_INDEX.is(4L)).count());
		}
	}

	// -----------------------------------------------------------------------
	// Section 4 (slow): 100 000 records — exact count and content verification
	// -----------------------------------------------------------------------

	/**
	 * Large-scale stress test with 100 000 entities indexed by four indexes simultaneously:
	 * <ul>
	 *   <li>{@code typeId}    – {@link BinaryIndexerLong}  (5 values, 20 000 each)</li>
	 *   <li>{@code status}    – {@link IndexerString}       (4 values, 25 000 each)</li>
	 *   <li>{@code priority}  – {@link IndexerInteger}      (3 values: 33 334 / 33 333 / 33 333)</li>
	 *   <li>{@code customerId}– {@link BinaryIndexerUUID}   (10 distinct UUIDs, 10 000 each)</li>
	 * </ul>
	 *
	 * <p>Data layout (orderId 0..99 999):
	 * <pre>
	 *   typeId     = (orderId % 5) + 1          → {1..5}
	 *   status     = STATUSES[orderId % 4]       → {"OPEN","CLOSED","PENDING","SHIPPED"}
	 *   priority   = (orderId % 3) + 1           → {1,2,3}
	 *   customerId = CUSTOMER_UUIDS[orderId % 10]→ 10 distinct UUIDs
	 * </pre>
	 *
	 * <p>Expected combined counts derived via LCM / Chinese Remainder Theorem:
	 * <pre>
	 *   type=1 AND open                → lcm(5,4)=20   → 100 000/20  =  5 000
	 *   type=1 OR open                 → 20 000+25 000−5 000         = 40 000
	 *   type=1 AND priority=1          → lcm(5,3)=15   → 6 667
	 *   open AND priority=1            → lcm(4,3)=12   → 8 334
	 *   type=1 AND open AND priority=1 → lcm(5,4,3)=60 → 1 667
	 *   customer[0] AND open           → lcm(10,4)=20  → 5 000
	 *   all four (type=1, open, prio=1, customer[0]) → lcm(5,4,3,10)=60 → 1 667
	 *   type in {1,3} AND open         → 5 000+5 000                 = 10 000
	 *   type notIn {1,2}               → 3×20 000                    = 60 000
	 * </pre>
	 */
	@Nested
	@Tag("slow")
	class LargeDataset
	{
		static final int COUNT = 100_000;

		static final String[] STATUSES = {"OPEN", "CLOSED", "PENDING", "SHIPPED"};

		// 10 deterministic, non-zero UUIDs (BinaryIndexerUUID treats 0L halves specially).
		static final UUID[] CUSTOMER_UUIDS;
		static
		{
			CUSTOMER_UUIDS = new UUID[10];
			for(int i = 0; i < 10; i++)
			{
				CUSTOMER_UUIDS[i] = new UUID(1_000L + i, 2_000L + i);
			}
		}

		record Order(long typeId, String status, int priority, UUID customerId) {}

		static final BinaryIndexerLong<Order> TYPE_INDEX = new BinaryIndexerLong.Abstract<>()
		{
			@Override
			protected Long getLong(final Order o) { return o.typeId(); }
		};

		static final IndexerString<Order> STATUS_INDEX = new IndexerString.Abstract<>()
		{
			@Override
			protected String getString(final Order o) { return o.status(); }
		};

		static final IndexerInteger<Order> PRIORITY_INDEX = new IndexerInteger.Abstract<>()
		{
			@Override
			protected Integer getInteger(final Order o) { return o.priority(); }
		};

		static final BinaryIndexerUUID<Order> CUSTOMER_INDEX = new BinaryIndexerUUID.Abstract<>()
		{
			@Override
			protected UUID getUUID(final Order o) { return o.customerId(); }
		};

		private GigaMap<Order> map;

		@BeforeEach
		void setUp()
		{
			map = GigaMap.<Order>Builder()
				.withBitmapIndex(TYPE_INDEX)
				.withBitmapIndex(STATUS_INDEX)
				.withBitmapIndex(PRIORITY_INDEX)
				.withBitmapIndex(CUSTOMER_INDEX)
				.build();

			for(int i = 0; i < COUNT; i++)
			{
				map.add(new Order(
					(i % 5) + 1L,
					STATUSES[i % 4],
					(i % 3) + 1,
					CUSTOMER_UUIDS[i % 10]
				));
			}
		}

		// -----------------------------------------------------------------------
		// Basic single-index counts
		// -----------------------------------------------------------------------

		@Test
		void sizeIsExact()
		{
			assertEquals(COUNT, map.size());
		}

		@Test
		void singleBinaryIndexCounts()
		{
			assertEquals(20_000, map.query(TYPE_INDEX.is(1L)).count());
			assertEquals(20_000, map.query(TYPE_INDEX.is(2L)).count());
			assertEquals(20_000, map.query(TYPE_INDEX.is(3L)).count());
			assertEquals(20_000, map.query(TYPE_INDEX.is(4L)).count());
			assertEquals(20_000, map.query(TYPE_INDEX.is(5L)).count());
		}

		@Test
		void singleStringIndexCounts()
		{
			assertEquals(25_000, map.query(STATUS_INDEX.is("OPEN")).count());
			assertEquals(25_000, map.query(STATUS_INDEX.is("CLOSED")).count());
			assertEquals(25_000, map.query(STATUS_INDEX.is("PENDING")).count());
			assertEquals(25_000, map.query(STATUS_INDEX.is("SHIPPED")).count());
		}

		@Test
		void singleIntegerIndexCounts()
		{
			// orderId%3==0: 0,3,...,99999 → 33 334; orderId%3==1 / ==2 → 33 333 each
			assertEquals(33_334, map.query(PRIORITY_INDEX.is(1)).count());
			assertEquals(33_333, map.query(PRIORITY_INDEX.is(2)).count());
			assertEquals(33_333, map.query(PRIORITY_INDEX.is(3)).count());
		}

		@Test
		void singleUuidIndexCounts()
		{
			for(int i = 0; i < 10; i++)
			{
				assertEquals(10_000, map.query(CUSTOMER_INDEX.is(CUSTOMER_UUIDS[i])).count(),
					"UUID[" + i + "] should match exactly 10 000 orders");
			}
		}

		// -----------------------------------------------------------------------
		// Binary AND String
		// -----------------------------------------------------------------------

		@Test
		void binaryAndStringCount()
		{
			// orderId%5==0 AND orderId%4==0 → orderId%20==0 → 5 000
			assertEquals(5_000, map.query(TYPE_INDEX.is(1L).and(STATUS_INDEX.is("OPEN"))).count());
		}

		@Test
		void binaryAndStringAllResultsHaveCorrectValues()
		{
			final List<Order> results = map.query(TYPE_INDEX.is(1L).and(STATUS_INDEX.is("OPEN"))).toList();
			assertEquals(5_000, results.size());
			assertTrue(results.stream().allMatch(o -> o.typeId() == 1L && "OPEN".equals(o.status())));
		}

		@Test
		void stringAndBinaryCountEqualsReversedOrder()
		{
			// AND is commutative — both orderings must give the same count
			final long ab = map.query(TYPE_INDEX.is(1L).and(STATUS_INDEX.is("OPEN"))).count();
			final long ba = map.query(STATUS_INDEX.is("OPEN").and(TYPE_INDEX.is(1L))).count();
			assertEquals(ab, ba);
			assertEquals(5_000, ab);
		}

		// -----------------------------------------------------------------------
		// Binary OR String
		// -----------------------------------------------------------------------

		@Test
		void binaryOrStringCount()
		{
			// |type=1| + |open| - |type=1 AND open| = 20000 + 25000 - 5000 = 40 000
			assertEquals(40_000, map.query(TYPE_INDEX.is(1L).or(STATUS_INDEX.is("OPEN"))).count());
		}

		@Test
		void stringOrStringCount()
		{
			assertEquals(50_000, map.query(STATUS_INDEX.is("OPEN").or(STATUS_INDEX.is("CLOSED"))).count());
		}

		// -----------------------------------------------------------------------
		// Binary AND Integer
		// -----------------------------------------------------------------------

		@Test
		void binaryAndIntegerCount()
		{
			// orderId%5==0 AND orderId%3==0 → orderId%15==0 → 6 667
			assertEquals(6_667, map.query(TYPE_INDEX.is(1L).and(PRIORITY_INDEX.is(1))).count());
		}

		@Test
		void integerAndBinaryAllResultsHaveCorrectValues()
		{
			final List<Order> results = map.query(PRIORITY_INDEX.is(1).and(TYPE_INDEX.is(2L))).toList();
			// type=2: orderId%5==1, priority=1: orderId%3==0 → lcm(5,3)=15, orderId≡1(mod5)∧orderId≡0(mod3)
			// CRT: orderId≡6(mod 15) → 100000/15=6666.7 → floor(99999/15−6/15)+1 counted from 6:
			// Actually: orderId=6,21,36,...,99996 → (99996-6)/15+1=6661 ... hmm, let me recalculate.
			// orderId%5==1 AND orderId%3==0: orderId=6,21,36,...
			// First: 6. Last ≤ 99999: 6+15*k ≤ 99999 → k ≤ 6666.2 → k=6666 → last=6+15*6666=99996
			// Count: 6667
			assertEquals(6_667, results.size());
			assertTrue(results.stream().allMatch(o -> o.typeId() == 2L && o.priority() == 1));
		}

		// -----------------------------------------------------------------------
		// String AND Integer
		// -----------------------------------------------------------------------

		@Test
		void stringAndIntegerCount()
		{
			// orderId%4==0 AND orderId%3==0 → orderId%12==0 → 8 334
			assertEquals(8_334, map.query(STATUS_INDEX.is("OPEN").and(PRIORITY_INDEX.is(1))).count());
		}

		// -----------------------------------------------------------------------
		// Three-way AND: binary + string + integer
		// -----------------------------------------------------------------------

		@Test
		void threeWayAndCount()
		{
			// orderId%5==0 AND orderId%4==0 AND orderId%3==0 → orderId%60==0 → 1 667
			assertEquals(1_667, map.query(
				TYPE_INDEX.is(1L).and(STATUS_INDEX.is("OPEN")).and(PRIORITY_INDEX.is(1))
			).count());
		}

		@Test
		void threeWayAndViaSequentialQuery()
		{
			final GigaQuery<Order> q = map.query();
			q.and(STATUS_INDEX.is("OPEN"));
			q.and(TYPE_INDEX.is(1L));
			q.and(PRIORITY_INDEX.is(1));
			assertEquals(1_667, q.count());
		}

		@Test
		void threeWayAndAllResultsHaveCorrectValues()
		{
			final List<Order> results = map.query(
				TYPE_INDEX.is(1L).and(STATUS_INDEX.is("OPEN")).and(PRIORITY_INDEX.is(1))
			).toList();
			assertEquals(1_667, results.size());
			assertTrue(results.stream().allMatch(o ->
				o.typeId() == 1L && "OPEN".equals(o.status()) && o.priority() == 1
			));
		}

		// -----------------------------------------------------------------------
		// UUID combined with string and binary
		// -----------------------------------------------------------------------

		@Test
		void uuidAndStringCount()
		{
			// orderId%10==0 AND orderId%4==0 → orderId%20==0 → 5 000
			assertEquals(5_000, map.query(
				CUSTOMER_INDEX.is(CUSTOMER_UUIDS[0]).and(STATUS_INDEX.is("OPEN"))
			).count());
		}

		@Test
		void uuidAndStringAllResultsHaveCorrectValues()
		{
			final List<Order> results = map.query(
				CUSTOMER_INDEX.is(CUSTOMER_UUIDS[0]).and(STATUS_INDEX.is("OPEN"))
			).toList();
			assertEquals(5_000, results.size());
			assertTrue(results.stream().allMatch(o ->
				CUSTOMER_UUIDS[0].equals(o.customerId()) && "OPEN".equals(o.status())
			));
		}

		@Test
		void uuidAndBinaryCount()
		{
			// orderId%10==0 ⊆ orderId%5==0 (every multiple of 10 is a multiple of 5)
			// → type=1 AND customer[0] = customer[0] = 10 000
			assertEquals(10_000, map.query(
				TYPE_INDEX.is(1L).and(CUSTOMER_INDEX.is(CUSTOMER_UUIDS[0]))
			).count());
		}

		// -----------------------------------------------------------------------
		// Four-way AND: all four indexes together
		// -----------------------------------------------------------------------

		@Test
		void fourWayAndCount()
		{
			// lcm(5,4,3,10)=60 → 1 667 (orderId%60==0 implies orderId%10==0 since 60=6×10)
			assertEquals(1_667, map.query(
				TYPE_INDEX.is(1L)
					.and(STATUS_INDEX.is("OPEN"))
					.and(PRIORITY_INDEX.is(1))
					.and(CUSTOMER_INDEX.is(CUSTOMER_UUIDS[0]))
			).count());
		}

		@Test
		void fourWayAndAllResultsHaveCorrectValues()
		{
			final List<Order> results = map.query(
				TYPE_INDEX.is(1L)
					.and(STATUS_INDEX.is("OPEN"))
					.and(PRIORITY_INDEX.is(1))
					.and(CUSTOMER_INDEX.is(CUSTOMER_UUIDS[0]))
			).toList();
			assertEquals(1_667, results.size());
			assertTrue(results.stream().allMatch(o ->
				o.typeId() == 1L
				&& "OPEN".equals(o.status())
				&& o.priority() == 1
				&& CUSTOMER_UUIDS[0].equals(o.customerId())
			));
		}

		// -----------------------------------------------------------------------
		// NOT and notIn
		// -----------------------------------------------------------------------

		@Test
		void binaryNotCount()
		{
			assertEquals(80_000, map.query(TYPE_INDEX.not(1L)).count());
		}

		@Test
		void binaryNotAndStringCount()
		{
			// type != 1 AND open: (open=25000) − (type=1 AND open=5000) = 20 000
			assertEquals(20_000, map.query(TYPE_INDEX.not(1L).and(STATUS_INDEX.is("OPEN"))).count());
		}

		@Test
		void binaryNotInCount()
		{
			// type not in {1,2} → types {3,4,5} → 3 × 20 000 = 60 000
			assertEquals(60_000, map.query(TYPE_INDEX.notIn(1L, 2L)).count());
		}

		@Test
		void binaryNotInAndStringAllResultsHaveCorrectValues()
		{
			final List<Order> results = map.query(
				TYPE_INDEX.notIn(1L, 2L).and(STATUS_INDEX.is("OPEN"))
			).toList();
			// type in {3,4,5}: 60 000 entities; AND open: 60000*(25000/100000)=15000
			// type=3 AND open: orderId%5==2 AND orderId%4==0 → orderId≡12(mod 20) → 5000
			// type=4 AND open: orderId%5==3 AND orderId%4==0 → CRT orderId≡8(mod 20) → 5000
			// type=5 AND open: orderId%5==4 AND orderId%4==0 → CRT orderId≡4(mod 20)? 4%5=4✓,4%4=0✓ → 5000
			assertEquals(15_000, results.size());
			assertTrue(results.stream().allMatch(o -> o.typeId() >= 3 && "OPEN".equals(o.status())));
		}

		// -----------------------------------------------------------------------
		// in()
		// -----------------------------------------------------------------------

		@Test
		void binaryInCount()
		{
			// type in {1,3} → 2 × 20 000 = 40 000
			assertEquals(40_000, map.query(TYPE_INDEX.in(1L, 3L)).count());
		}

		@Test
		void binaryInAndStringCount()
		{
			// type=1 AND open: 5 000; type=3 AND open (orderId%5==2 AND %4==0 → orderId≡12 mod 20): 5 000
			assertEquals(10_000, map.query(TYPE_INDEX.in(1L, 3L).and(STATUS_INDEX.is("OPEN"))).count());
		}

		@Test
		void binaryInAndStringAllResultsHaveCorrectValues()
		{
			final List<Order> results = map.query(
				TYPE_INDEX.in(1L, 3L).and(STATUS_INDEX.is("OPEN"))
			).toList();
			assertEquals(10_000, results.size());
			assertTrue(results.stream().allMatch(o ->
				(o.typeId() == 1L || o.typeId() == 3L) && "OPEN".equals(o.status())
			));
		}

		// -----------------------------------------------------------------------
		// CRUD on large dataset
		// -----------------------------------------------------------------------

		@Test
		void addOneAndBothIndexesReflectIt()
		{
			final UUID freshId = new UUID(9_999L, 9_999L);
			map.add(new Order(9L, "OPEN", 1, freshId));

			// Size increases
			assertEquals(COUNT + 1, map.size());
			// Binary index finds the new entity under its unique typeId
			assertEquals(1, map.query(TYPE_INDEX.is(9L)).count());
			// String index still finds it when combined
			assertEquals(1, map.query(TYPE_INDEX.is(9L).and(STATUS_INDEX.is("OPEN"))).count());
			// UUID index finds it
			assertEquals(1, map.query(CUSTOMER_INDEX.is(freshId)).count());
		}

		@Test
		void removeOneAndAllIndexesDecreaseCounts()
		{
			// Grab an entity we know matches type=1 AND open
			final Order victim = map.query(TYPE_INDEX.is(1L).and(STATUS_INDEX.is("OPEN")))
				.findFirst()
				.orElseThrow();

			map.remove(victim);

			assertEquals(COUNT - 1, map.size());
			assertEquals(4_999, map.query(TYPE_INDEX.is(1L).and(STATUS_INDEX.is("OPEN"))).count());
			// type=1 alone lost one entry as well
			assertEquals(19_999, map.query(TYPE_INDEX.is(1L)).count());
			// open alone lost one entry as well
			assertEquals(24_999, map.query(STATUS_INDEX.is("OPEN")).count());
		}
	}
}
