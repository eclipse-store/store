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

import org.eclipse.store.gigamap.types.BinaryIndexer;
import org.eclipse.store.gigamap.types.BinaryIndexerByte;
import org.eclipse.store.gigamap.types.BinaryIndexerDouble;
import org.eclipse.store.gigamap.types.BinaryIndexerFloat;
import org.eclipse.store.gigamap.types.BinaryIndexerInteger;
import org.eclipse.store.gigamap.types.BinaryIndexerLong;
import org.eclipse.store.gigamap.types.BinaryIndexerNumber;
import org.eclipse.store.gigamap.types.BinaryIndexerShort;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.Indexer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numeric binary indexers encode their key directly into a single long bit pattern, and every one of them
 * unboxes that key in its {@code toLong} conversion. A null key therefore used to escape as a raw
 * {@link NullPointerException} - from {@code map.add(...)} for all six types, and additionally from
 * {@code isNull()}/{@code notNull()} for {@link BinaryIndexerLong}, whose {@code is(Long)} override shadows the
 * validating {@code BinaryIndexer.is(Long)} default that the other five already went through.
 * <p>
 * Null cannot be supported here: unlike the composite binary indexers ({@code BinaryIndexerString},
 * {@code BinaryIndexerUUID}) the single-long family has no spare slot for a null sentinel, and
 * {@link BinaryIndexerLong} has already sacrificed its only spare bit pattern ({@code Long.MAX_VALUE}) to the zero
 * sentinel. So null is rejected - but with the same designed, descriptive {@link IllegalArgumentException} that
 * already guards {@code Long.MAX_VALUE}, on every entry point, for every numeric type.
 */
public class BinaryIndexerNumberNullKeyTest
{
	/**
	 * A single entity type for all six indexers: each indexer casts the value to its own key type, and each test
	 * builds a map carrying only its own index, so only matching values are ever put in.
	 */
	record Box(String name, Number value) {}

	static final BinaryIndexerLong<Box> LONG_INDEX = new BinaryIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(final Box entity)
		{
			return (Long)entity.value();
		}
	};

	static final BinaryIndexerInteger<Box> INTEGER_INDEX = new BinaryIndexerInteger.Abstract<>()
	{
		@Override
		protected Integer getInteger(final Box entity)
		{
			return (Integer)entity.value();
		}
	};

	static final BinaryIndexerShort<Box> SHORT_INDEX = new BinaryIndexerShort.Abstract<>()
	{
		@Override
		protected Short getShort(final Box entity)
		{
			return (Short)entity.value();
		}
	};

	static final BinaryIndexerByte<Box> BYTE_INDEX = new BinaryIndexerByte.Abstract<>()
	{
		@Override
		protected Byte getByte(final Box entity)
		{
			return (Byte)entity.value();
		}
	};

	static final BinaryIndexerFloat<Box> FLOAT_INDEX = new BinaryIndexerFloat.Abstract<>()
	{
		@Override
		protected Float getFloat(final Box entity)
		{
			return (Float)entity.value();
		}
	};

	static final BinaryIndexerDouble<Box> DOUBLE_INDEX = new BinaryIndexerDouble.Abstract<>()
	{
		@Override
		protected Double getDouble(final Box entity)
		{
			return (Double)entity.value();
		}
	};

	/** A plain {@link Indexer} yielding a {@link Long} key, to be wrapped by {@link BinaryIndexer#Wrap(Indexer)}. */
	static final Indexer<Box, Long> WRAPPED_SUBJECT = new Indexer.Abstract<Box, Long>()
	{
		@Override
		public Long index(final Box entity)
		{
			return (Long)entity.value();
		}

		@Override
		public Class<Long> keyType()
		{
			return Long.class;
		}
	};

	private static GigaMap<Box> newMap(final Indexer<? super Box, ?> index)
	{
		return GigaMap.<Box>Builder()
			.withBitmapIndex(index)
			.build();
	}

	/**
	 * A null key extracted from an entity must be rejected with a descriptive
	 * {@link IllegalArgumentException} instead of a raw {@link NullPointerException}, and the rejected add must
	 * roll back cleanly, leaving the map empty and fully usable.
	 */
	private static <K extends Number> void assertNullKeyRejectedOnAdd(
		final BinaryIndexerNumber<Box, K> indexer  ,
		final K                           sampleKey
	)
	{
		final GigaMap<Box> map = newMap(indexer);

		final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
			() -> map.add(new Box("nullKey", null)),
			() -> indexer.name() + " must reject a null key on add instead of throwing a raw NullPointerException");
		assertTrue(e.getMessage().contains(indexer.name()),
			"the exception message must name the index: " + e.getMessage());

		assertEquals(0, map.size(), "a rejected add must not leave the entity in the map");

		map.add(new Box("valid", sampleKey));
		assertEquals(1, map.query(indexer.is(sampleKey)).count(),
			"the map must stay usable after a rejected add");
	}

	/**
	 * Every condition factory taking a key must reject null before the {@code toLong} conversion unboxes it,
	 * including the inherited {@code isNull()}/{@code notNull()} that route through {@code is(null)}.
	 */
	private static <K extends Number> void assertNullKeyRejectedOnQuery(
		final BinaryIndexerNumber<Box, K> indexer  ,
		final K                           sampleKey
	)
	{
		// a K-typed local rather than a cast literal: it keeps the calls unambiguous against the inherited
		// is(Long) / is(Predicate) overloads without an unchecked cast to the type variable.
		final K nullKey = null;

		assertThrows(IllegalArgumentException.class,
			() -> indexer.is(nullKey),
			() -> indexer.name() + ": is(null) must be rejected");
		assertThrows(IllegalArgumentException.class,
			() -> indexer.not(nullKey),
			() -> indexer.name() + ": not(null) must be rejected");
		assertThrows(IllegalArgumentException.class,
			() -> indexer.in(sampleKey, nullKey),
			() -> indexer.name() + ": in(...) must reject a null element");
		assertThrows(IllegalArgumentException.class,
			() -> indexer.notIn(sampleKey, nullKey),
			() -> indexer.name() + ": notIn(...) must reject a null element");
		assertThrows(IllegalArgumentException.class,
			() -> indexer.isNull(),
			() -> indexer.name() + ": isNull() must be rejected, since null cannot be indexed at all");
		assertThrows(IllegalArgumentException.class,
			() -> indexer.notNull(),
			() -> indexer.name() + ": notNull() must be rejected, since null cannot be indexed at all");
	}

	@Test
	void longNullKeyRejected()
	{
		assertNullKeyRejectedOnAdd(LONG_INDEX, 42L);
		assertNullKeyRejectedOnQuery(LONG_INDEX, 42L);
	}

	@Test
	void integerNullKeyRejected()
	{
		assertNullKeyRejectedOnAdd(INTEGER_INDEX, 42);
		assertNullKeyRejectedOnQuery(INTEGER_INDEX, 42);
	}

	@Test
	void shortNullKeyRejected()
	{
		assertNullKeyRejectedOnAdd(SHORT_INDEX, (short)42);
		assertNullKeyRejectedOnQuery(SHORT_INDEX, (short)42);
	}

	@Test
	void byteNullKeyRejected()
	{
		assertNullKeyRejectedOnAdd(BYTE_INDEX, (byte)42);
		assertNullKeyRejectedOnQuery(BYTE_INDEX, (byte)42);
	}

	@Test
	void floatNullKeyRejected()
	{
		assertNullKeyRejectedOnAdd(FLOAT_INDEX, 42.0f);
		assertNullKeyRejectedOnQuery(FLOAT_INDEX, 42.0f);
	}

	@Test
	void doubleNullKeyRejected()
	{
		assertNullKeyRejectedOnAdd(DOUBLE_INDEX, 42.0d);
		assertNullKeyRejectedOnQuery(DOUBLE_INDEX, 42.0d);
	}

	@Test
	void singleNullElementInVarargsRejected()
	{
		// in((Long)null) passes an array holding one null, as opposed to a null array; the cast is only needed
		// to disambiguate the varargs call and is spelled out here where the key type is concrete.
		assertThrows(IllegalArgumentException.class,
			() -> LONG_INDEX.in((Long)null),
			"in(...) must reject a lone null element");
		assertThrows(IllegalArgumentException.class,
			() -> LONG_INDEX.notIn((Long)null),
			"notIn(...) must reject a lone null element");
	}

	@Test
	void wrappedIndexerRejectsNullKeyOnAdd()
	{
		// BinaryIndexer.Wrap unboxes the wrapped indexer's Long key, so it had the same raw-NPE defect.
		final BinaryIndexer<Box> wrapped = BinaryIndexer.Wrap(WRAPPED_SUBJECT);
		final GigaMap<Box>       map     = newMap(wrapped);

		assertThrows(IllegalArgumentException.class,
			() -> map.add(new Box("nullKey", null)),
			"a wrapped indexer yielding a null key must be rejected instead of throwing a raw NullPointerException");

		assertEquals(0, map.size(), "a rejected add must not leave the entity in the map");

		map.add(new Box("valid", 42L));
		assertEquals(1, map.query(wrapped.is(42L)).count(), "the map must stay usable after a rejected add");
	}

	@Test
	void reservedZeroSentinelStillRejected()
	{
		// Regression guard: the pre-existing Long.MAX_VALUE restriction must survive the null validation.
		final GigaMap<Box> map = newMap(LONG_INDEX);

		assertThrows(IllegalArgumentException.class,
			() -> map.add(new Box("sentinel", Long.MAX_VALUE)),
			"Long.MAX_VALUE must still be rejected on add");
		assertThrows(IllegalArgumentException.class,
			() -> LONG_INDEX.is(Long.MAX_VALUE),
			"Long.MAX_VALUE must still be rejected on query");
	}

	@Test
	void nonNullKeysStillWork()
	{
		final GigaMap<Box> map = newMap(LONG_INDEX);
		map.add(new Box("zero", 0L));
		map.add(new Box("negative", -1L));
		map.add(new Box("positive", 42L));

		assertEquals(3, map.size(), "all non-null keys must be indexable");
		assertEquals(1, map.query(LONG_INDEX.is(0L)).count(), "zero must remain queryable via its sentinel");
		assertEquals(1, map.query(LONG_INDEX.is(-1L)).count(), "negative keys must remain queryable");
		assertEquals(1, map.query(LONG_INDEX.is(42L)).count(), "positive keys must remain queryable");
	}
}
