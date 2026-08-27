package org.eclipse.store.gigamap.indexer;

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

import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.BinaryIndexerUUID;
import org.eclipse.store.gigamap.types.ByteIndexerLong;
import org.eclipse.store.gigamap.types.Condition;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexIdentifier;
import org.eclipse.store.gigamap.types.IndexerLocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for broken generic condition methods on GigaMap composite indexers.
 * <p>
 * Composite indexers encode a {@code null} logical value as a dedicated sentinel array, not literal
 * {@code null}. Before the fix, {@code notNull()} (and the generic {@code is((K)null)} /
 * {@code not((K)null)} paths) lowered to a predicate backed by a {@code null} sample array, which
 * threw {@link NullPointerException} at query time - and only in some query shapes (e.g. inside
 * {@code And}/{@code Or}). This test pins down that {@code isNull()}/{@code notNull()} work
 * standalone <b>and</b> combined, that the generic {@code Object[]}/{@code long[]}-keyed
 * {@code is}/{@code not} with {@code null} behave as the null query, and that the temporal typed
 * {@code not}/{@code in}/{@code notIn} additions work.
 * <p>
 * Coverage spans one representative per composite family: hashing fixed-size numeric
 * ({@link ByteIndexerLong}), hashing fixed-size temporal ({@link IndexerLocalDate}), binary
 * variable-size ({@link BinaryIndexerString}), and binary fixed-size ({@link BinaryIndexerUUID}).
 */
public class CompositeIndexerNullConditionTest
{
	record Rec(long id, Long num, String str, LocalDate date, UUID uuid)
	{
	}

	static final LocalDate D1 = LocalDate.of(2025, 3, 1);
	static final LocalDate D2 = LocalDate.of(2025, 3, 2);
	static final UUID      U1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
	static final UUID      U2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

	// hashing composite (byte-decomposed numeric)
	static final ByteIndexerLong<Rec> NUM = new ByteIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(final Rec r)
		{
			return r.num();
		}
	};

	// binary composite, variable size
	static final BinaryIndexerString<Rec> STR = new BinaryIndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Rec r)
		{
			return r.str();
		}
	};

	// hashing composite, temporal
	static final IndexerLocalDate<Rec> DATE = new IndexerLocalDate.Abstract<>()
	{
		@Override
		protected LocalDate getLocalDate(final Rec r)
		{
			return r.date();
		}
	};

	// binary composite, fixed size
	static final BinaryIndexerUUID<Rec> UID = new BinaryIndexerUUID.Abstract<>()
	{
		@Override
		protected UUID getUUID(final Rec r)
		{
			return r.uuid();
		}
	};

	private GigaMap<Rec> map;

	@BeforeEach
	void setUp()
	{
		map = GigaMap.<Rec>Builder()
			.withBitmapIndex(NUM)
			.withBitmapIndex(STR)
			.withBitmapIndex(DATE)
			.withBitmapIndex(UID)
			.build();

		//              id  num    str    date  uuid
		map.add(new Rec(0L, null,  null,  null, null));
		map.add(new Rec(1L, null,  "x",   null, U1));
		map.add(new Rec(2L, 10L,   "x",   D1,   U1));
		map.add(new Rec(3L, 20L,   "y",   D2,   U2));
		map.add(new Rec(4L, 20L,   null,  null, null));

		assertEquals(5, map.size());
	}

	private int count(final Condition<Rec> condition)
	{
		return map.query(condition).toList().size();
	}

	// ---- hashing composite numeric (ByteIndexerLong) --------------------------------------------

	@Test
	void byteLong_nullChecks()
	{
		assertEquals(2, count(NUM.isNull()));   // e0, e1
		assertEquals(3, count(NUM.notNull()));  // e2, e3, e4  (previously NPE'd)
		assertEquals(2, count(NUM.is(20L)));    // e3, e4
		assertEquals(3, count(NUM.not(20L)));   // e0, e1, e2  (null included)
	}

	@Test
	void byteLong_nullChecksCombined()
	{
		// notNull()/isNull() nested inside And/Or previously NPE'd depending on shape
		assertEquals(1, count(NUM.notNull().and(STR.is("x"))));   // e2
		assertEquals(2, count(NUM.isNull().and(DATE.isNull())));  // e0, e1
		assertEquals(4, count(NUM.notNull().or(STR.isNull())));   // e2,e3,e4 + e0,e4
	}

	@Test
	void byteLong_genericNullPath()
	{
		// K == Object[] for hashing composites; generic is((K)null)/not((K)null) must behave as the null query
		final IndexIdentifier<Rec, Object[]> generic = NUM;
		assertEquals(2, count(generic.is((Object[])null)));   // == isNull()
		assertEquals(3, count(generic.not((Object[])null)));  // == notNull()
	}

	// ---- binary composite variable-size (BinaryIndexerString) -----------------------------------

	@Test
	void binaryString_nullChecks()
	{
		assertEquals(2, count(STR.isNull()));   // e0, e4
		assertEquals(3, count(STR.notNull()));  // e1, e2, e3
		assertEquals(2, count(STR.is("x")));    // e1, e2
		assertEquals(1, count(STR.notNull().and(NUM.isNull()))); // e1
	}

	// ---- binary composite fixed-size (BinaryIndexerUUID) ----------------------------------------

	@Test
	void binaryUuid_nullChecks()
	{
		assertEquals(2, count(UID.isNull()));   // e0, e4
		assertEquals(3, count(UID.notNull()));  // e1, e2, e3  (previously NPE'd)
		assertEquals(2, count(UID.is(U1)));     // e1, e2
		assertEquals(1, count(UID.notNull().and(NUM.is(10L)))); // e2
	}

	@Test
	void binaryUuid_genericNullPath()
	{
		final IndexIdentifier<Rec, long[]> generic = UID;
		assertEquals(2, count(generic.is((long[])null)));
		assertEquals(3, count(generic.not((long[])null)));
	}

	// ---- hashing composite temporal (IndexerLocalDate) ------------------------------------------

	@Test
	void localDate_nullChecks()
	{
		assertEquals(3, count(DATE.isNull()));   // e0, e1, e4
		assertEquals(2, count(DATE.notNull()));  // e2, e3  (previously NPE'd)
		assertEquals(2, count(DATE.notNull().and(NUM.notNull()))); // e2, e3
	}

	@Test
	void localDate_typedNegationAndIn()
	{
		assertEquals(1, count(DATE.is(D1)));               // e2
		assertEquals(4, count(DATE.not(D1)));              // Not(is) -> e0,e1,e3,e4 (nulls included)
		assertEquals(2, count(DATE.in(D1, D2)));           // e2, e3
		assertEquals(3, count(DATE.notIn(D1, D2)));        // e0, e1, e4
		assertEquals(2, count(DATE.not((LocalDate)null))); // == notNull() -> e2, e3
	}
}
