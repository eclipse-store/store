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

import org.eclipse.store.gigamap.types.BinaryIndexerByte;
import org.eclipse.store.gigamap.types.BinaryIndexerDouble;
import org.eclipse.store.gigamap.types.BinaryIndexerFloat;
import org.eclipse.store.gigamap.types.BinaryIndexerInteger;
import org.eclipse.store.gigamap.types.BinaryIndexerLong;
import org.eclipse.store.gigamap.types.BinaryIndexerShort;
import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.BinaryIndexerUUID;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exact-match correctness tests for the binary (bit-sliced) bitmap index family.
 * <p>
 * A binary composite index ({@link BinaryIndexerString}, {@link BinaryIndexerUUID}) stores one
 * bitmap entry per <b>bit</b> of the indexed value and reconstructs an exact match as an AND chain
 * ("require this bit set / that bit unset"). The dangerous edge case is when the searched value's
 * bits are a <b>superset</b> of a stored value's bits: a naive implementation that only looks at
 * the bits that actually have an entry will incorrectly match the stored (subset) value. This was
 * GitHub issue #687, where stored {@code "test2"} (bytes {@code 0x32}) matched a search for
 * {@code "test3"} (bytes {@code 0x33}) because {@code 0x32}'s bits are a subset of {@code 0x33}'s
 * and the distinguishing bit had no index entry.
 * <p>
 * These tests lock in the corrected behavior across:
 * <ul>
 *     <li>{@link BinaryIndexerString} &mdash; variable-size composite (the #687 trigger), including
 *         multi-byte / UTF-8, empty strings, and values longer than 8 bytes (multi-long).</li>
 *     <li>{@link BinaryIndexerUUID} &mdash; fixed-size (2-long) composite.</li>
 *     <li>the single-long numeric families ({@code Long/Integer/Short/Byte/Double/Float}) which use
 *         the non-composite {@code BinaryBitmapIndex} path &mdash; regression confirmation.</li>
 *     <li>the {@code in(...)} path, which routes composite indexes through
 *         {@code AbstractCompositeBitmapIndex.internalQuery} (a different code path than
 *         {@code is(...)}).</li>
 * </ul>
 */
public class BinaryIndexerExactMatchTest
{
	// ---------------------------------------------------------------------------------------------
	// String (variable-size composite)
	// ---------------------------------------------------------------------------------------------

	static final class StrBox
	{
		String value;

		StrBox(final String value)
		{
			this.value = value;
		}
	}

	static final BinaryIndexerString<StrBox> STRING_INDEX = new BinaryIndexerString.Abstract<>()
	{
		@Override
		protected String getString(final StrBox entity)
		{
			return entity.value;
		}
	};

	private static GigaMap<StrBox> stringMap(final String... values)
	{
		final GigaMap<StrBox> map = GigaMap.<StrBox>Builder()
			.withBitmapIndex(STRING_INDEX)
			.build();
		for(final String v : values)
		{
			map.add(new StrBox(v));
		}
		return map;
	}

	@Nested
	class StringExactMatch
	{
		@Test
		void supersetSearchDoesNotMatchStoredSubset()
		{
			// "test"/"test2" are bit-subsets of "test3"; searching "test3" must not return them.
			final GigaMap<StrBox> map = stringMap("test", "test2");
			assertTrue(map.query(STRING_INDEX.is("test3")).toList().isEmpty());
		}

		@Test
		void exactMatchAmongCollidingValues()
		{
			final GigaMap<StrBox> map = stringMap("test", "test2", "test3");
			final List<StrBox> r = map.query(STRING_INDEX.is("test3")).toList();
			assertEquals(1, r.size());
			assertEquals("test3", r.get(0).value);
		}

		@Test
		void nonExistentValueReturnsEmpty()
		{
			final GigaMap<StrBox> map = stringMap("alpha", "beta", "gamma");
			assertTrue(map.query(STRING_INDEX.is("delta")).toList().isEmpty());
		}

		@Test
		void exactMatchAfterUpdateIntoSuperset()
		{
			// reproduction of issue #687: update the first entity into a bit-superset value.
			final GigaMap<StrBox> map = GigaMap.<StrBox>Builder().withBitmapIndex(STRING_INDEX).build();
			final StrBox first = new StrBox("test");
			map.add(first);
			map.add(new StrBox("test2"));

			assertTrue(map.query(STRING_INDEX.is("test3")).toList().isEmpty());

			map.update(first, e -> e.value = "test3");

			final List<StrBox> r = map.query(STRING_INDEX.is("test3")).toList();
			assertEquals(1, r.size());
			assertEquals("test3", r.get(0).value);
			assertTrue(map.query(STRING_INDEX.is("test")).toList().isEmpty());
		}

		@Test
		void differingByteLengthsDoNotCollide()
		{
			final GigaMap<StrBox> map = stringMap("a", "ab", "abc", "abcd");
			assertEquals(1, map.query(STRING_INDEX.is("a")).toList().size());
			assertEquals(1, map.query(STRING_INDEX.is("ab")).toList().size());
			assertEquals(1, map.query(STRING_INDEX.is("abc")).toList().size());
			assertEquals(1, map.query(STRING_INDEX.is("abcd")).toList().size());
			assertTrue(map.query(STRING_INDEX.is("abcde")).toList().isEmpty());
		}

		@Test
		void multiLongSharedPrefixDoesNotCollide()
		{
			// "abcdefgh" packs into exactly one long; "abcdefghi" needs a second long with the same
			// first long. A query for the shorter value must not return the longer one and vice versa.
			final GigaMap<StrBox> map = stringMap("abcdefgh", "abcdefghi");

			final List<StrBox> shortHit = map.query(STRING_INDEX.is("abcdefgh")).toList();
			assertEquals(1, shortHit.size());
			assertEquals("abcdefgh", shortHit.get(0).value);

			final List<StrBox> longHit = map.query(STRING_INDEX.is("abcdefghi")).toList();
			assertEquals(1, longHit.size());
			assertEquals("abcdefghi", longHit.get(0).value);
		}

		@Test
		void searchValueLongerThanAnyStoredReturnsEmpty()
		{
			// the index only ever saw 8-byte values (one long); a 9-byte search value cannot match.
			final GigaMap<StrBox> map = stringMap("abcdefgh", "12345678");
			assertTrue(map.query(STRING_INDEX.is("abcdefghi")).toList().isEmpty());
		}

		@Test
		void unicodeAndMultiByteValues()
		{
			final GigaMap<StrBox> map = stringMap("café", "cafe", "Ñoño", "日本語", "😀");

			assertEquals(1, map.query(STRING_INDEX.is("café")).toList().size());
			assertEquals(1, map.query(STRING_INDEX.is("cafe")).toList().size());
			assertEquals(1, map.query(STRING_INDEX.is("Ñoño")).toList().size());
			assertEquals(1, map.query(STRING_INDEX.is("日本語")).toList().size());
			assertEquals(1, map.query(STRING_INDEX.is("😀")).toList().size());
			// "cafe" and "café" differ only in the trailing multi-byte char; they must not collide.
			assertEquals("café", map.query(STRING_INDEX.is("café")).toList().get(0).value);
		}

		@Test
		void emptyStringIsIndexedAndMatched()
		{
			final GigaMap<StrBox> map = stringMap("", "x");
			final List<StrBox> r = map.query(STRING_INDEX.is("")).toList();
			assertEquals(1, r.size());
			assertEquals("", r.get(0).value);
			assertEquals(1, map.query(STRING_INDEX.is("x")).toList().size());
		}

		@Test
		void notExcludesOnlyTheExactMatch()
		{
			final GigaMap<StrBox> map = stringMap("test", "test2", "test3");
			final List<StrBox> r = map.query(STRING_INDEX.not("test3")).toList();
			assertEquals(2, r.size());
			assertTrue(r.stream().noneMatch(e -> e.value.equals("test3")));
		}
	}

	@Nested
	class CompositeInConditions
	{
		// in(...) on a composite index takes the long[] keys produced by the indexer, and routes
		// through AbstractCompositeBitmapIndex.internalQuery (distinct from the is(...)/search path).
		private long[] key(final String value)
		{
			return STRING_INDEX.index(new StrBox(value));
		}

		@Test
		void inMatchesEachExactValue()
		{
			final GigaMap<StrBox> map = stringMap("alpha", "beta", "gamma");
			final List<StrBox> r = map.query(STRING_INDEX.in(key("alpha"), key("gamma"))).toList();
			assertEquals(2, r.size());
			assertTrue(r.stream().allMatch(e -> e.value.equals("alpha") || e.value.equals("gamma")));
		}

		@Test
		void inSingleKeyBehavesLikeIs()
		{
			final GigaMap<StrBox> map = stringMap("alpha", "beta", "gamma");
			assertEquals(1, map.query(STRING_INDEX.in(key("beta"))).toList().size());
		}

		@Test
		void inForNonExistentReturnsEmpty()
		{
			final GigaMap<StrBox> map = stringMap("alpha", "beta", "gamma");
			assertTrue(map.query(STRING_INDEX.in(key("delta"), key("epsilon"))).toList().isEmpty());
		}

		@Test
		void inWithSupersetKeyDoesNotMatchSubset()
		{
			final GigaMap<StrBox> map = stringMap("test", "test2");
			assertTrue(map.query(STRING_INDEX.in(key("test3"))).toList().isEmpty());
		}

		@Test
		void inWithShorterKeyDoesNotMatchLongerStoredValue()
		{
			// the internalQuery (In/All/Equals) path must enforce trailing-position emptiness just like
			// is(...): a 1-long key for "abcdefgh" must not match the 2-long stored "abcdefghi".
			final GigaMap<StrBox> map = stringMap("abcdefgh", "abcdefghi");
			final List<StrBox> r = map.query(STRING_INDEX.in(key("abcdefgh"))).toList();
			assertEquals(1, r.size());
			assertEquals("abcdefgh", r.get(0).value);
		}

		@Test
		void inWithLongerKeyThanAnyStoredReturnsEmpty()
		{
			final GigaMap<StrBox> map = stringMap("abcdefgh", "12345678");
			assertTrue(map.query(STRING_INDEX.in(key("abcdefghi"))).toList().isEmpty());
		}
	}

	@Nested
	class CompositeConditionTrees
	{
		@Test
		void andOfTwoStringValuesNeverMatchesSingleEntity()
		{
			// an entity can hold only one value for the index, so AND of two distinct values is empty.
			final GigaMap<StrBox> map = stringMap("test", "test3");
			assertTrue(map.query(STRING_INDEX.is("test").and(STRING_INDEX.is("test3"))).toList().isEmpty());
		}

		@Test
		void orOfStringValuesMatchesEachExactly()
		{
			final GigaMap<StrBox> map = stringMap("test", "test2", "test3");
			final List<StrBox> r = map.query(STRING_INDEX.is("test").or(STRING_INDEX.is("test3"))).toList();
			assertEquals(2, r.size());
			assertTrue(r.stream().noneMatch(e -> e.value.equals("test2")));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// UUID (fixed-size 2-long composite)
	// ---------------------------------------------------------------------------------------------

	static final class UuidBox
	{
		UUID value;

		UuidBox(final UUID value)
		{
			this.value = value;
		}
	}

	static final BinaryIndexerUUID<UuidBox> UUID_INDEX = new BinaryIndexerUUID.Abstract<>()
	{
		@Override
		protected UUID getUUID(final UuidBox entity)
		{
			return entity.value;
		}
	};

	// both 64-bit halves are bit-subsets of the corresponding SUPER halves (0x32 ⊂ 0x33).
	private static final UUID SUB_UUID   = new UUID(0x32L, 0x32L);
	private static final UUID SUPER_UUID = new UUID(0x33L, 0x33L);

	private static GigaMap<UuidBox> uuidMap(final UUID... values)
	{
		final GigaMap<UuidBox> map = GigaMap.<UuidBox>Builder()
			.withBitmapIndex(UUID_INDEX)
			.build();
		for(final UUID v : values)
		{
			map.add(new UuidBox(v));
		}
		return map;
	}

	@Nested
	class UuidExactMatch
	{
		@Test
		void supersetSearchDoesNotMatchStoredSubset()
		{
			final GigaMap<UuidBox> map = uuidMap(SUB_UUID);
			assertTrue(map.query(UUID_INDEX.is(SUPER_UUID)).toList().isEmpty());
		}

		@Test
		void exactMatchAmongCollidingUuids()
		{
			final GigaMap<UuidBox> map = uuidMap(SUB_UUID, SUPER_UUID);
			final List<UuidBox> r = map.query(UUID_INDEX.is(SUPER_UUID)).toList();
			assertEquals(1, r.size());
			assertEquals(SUPER_UUID, r.get(0).value);
		}

		@Test
		void randomUuidsRoundTripExactly()
		{
			final UUID a = UUID.fromString("11111111-1111-1111-1111-111111111111");
			final UUID b = UUID.fromString("22222222-2222-2222-2222-222222222222");
			final GigaMap<UuidBox> map = uuidMap(a, b);
			assertEquals(a, map.query(UUID_INDEX.is(a)).toList().get(0).value);
			assertEquals(b, map.query(UUID_INDEX.is(b)).toList().get(0).value);
			assertTrue(map.query(UUID_INDEX.is(UUID.randomUUID())).toList().isEmpty());
		}

		@Test
		void exactMatchAfterUpdateIntoSuperset()
		{
			final GigaMap<UuidBox> map = GigaMap.<UuidBox>Builder().withBitmapIndex(UUID_INDEX).build();
			final UuidBox box = new UuidBox(SUB_UUID);
			map.add(box);

			assertTrue(map.query(UUID_INDEX.is(SUPER_UUID)).toList().isEmpty());

			map.update(box, e -> e.value = SUPER_UUID);

			assertEquals(1, map.query(UUID_INDEX.is(SUPER_UUID)).toList().size());
			assertTrue(map.query(UUID_INDEX.is(SUB_UUID)).toList().isEmpty());
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Single-long numeric families (non-composite BinaryBitmapIndex) - regression confirmation.
	// 0x32's bits are a subset of 0x33's bits, the numeric analogue of the #687 string case.
	// ---------------------------------------------------------------------------------------------

	@Nested
	class NumericRegression
	{
		record LongBox(long v) {}
		record IntBox(int v) {}
		record ShortBox(short v) {}
		record ByteBox(byte v) {}
		record DoubleBox(double v) {}
		record FloatBox(float v) {}

		static final BinaryIndexerLong<LongBox> LONG_INDEX = new BinaryIndexerLong.Abstract<>()
		{
			@Override
			protected Long getLong(final LongBox e)
			{
				return e.v();
			}
		};

		static final BinaryIndexerInteger<IntBox> INT_INDEX = new BinaryIndexerInteger.Abstract<>()
		{
			@Override
			protected Integer getInteger(final IntBox e)
			{
				return e.v();
			}
		};

		static final BinaryIndexerShort<ShortBox> SHORT_INDEX = new BinaryIndexerShort.Abstract<>()
		{
			@Override
			protected Short getShort(final ShortBox e)
			{
				return e.v();
			}
		};

		static final BinaryIndexerByte<ByteBox> BYTE_INDEX = new BinaryIndexerByte.Abstract<>()
		{
			@Override
			protected Byte getByte(final ByteBox e)
			{
				return e.v();
			}
		};

		static final BinaryIndexerDouble<DoubleBox> DOUBLE_INDEX = new BinaryIndexerDouble.Abstract<>()
		{
			@Override
			protected Double getDouble(final DoubleBox e)
			{
				return e.v();
			}
		};

		static final BinaryIndexerFloat<FloatBox> FLOAT_INDEX = new BinaryIndexerFloat.Abstract<>()
		{
			@Override
			protected Float getFloat(final FloatBox e)
			{
				return e.v();
			}
		};

		@Test
		void longSubsetDoesNotCollide()
		{
			final GigaMap<LongBox> map = GigaMap.<LongBox>Builder().withBitmapIndex(LONG_INDEX).build();
			map.add(new LongBox(0x32L));
			map.add(new LongBox(0x33L));
			assertEquals(0x33L, map.query(LONG_INDEX.is(0x33L)).toList().get(0).v());
			assertEquals(1, map.query(LONG_INDEX.is(0x33L)).toList().size());
			assertTrue(map.query(LONG_INDEX.is(0x34L)).toList().isEmpty());
		}

		@Test
		void integerSubsetDoesNotCollide()
		{
			final GigaMap<IntBox> map = GigaMap.<IntBox>Builder().withBitmapIndex(INT_INDEX).build();
			map.add(new IntBox(0x32));
			map.add(new IntBox(0x33));
			assertEquals(1, map.query(INT_INDEX.is(0x33)).toList().size());
			assertEquals(0x33, map.query(INT_INDEX.is(0x33)).toList().get(0).v());
			assertTrue(map.query(INT_INDEX.is(0x34)).toList().isEmpty());
		}

		@Test
		void shortSubsetDoesNotCollide()
		{
			final GigaMap<ShortBox> map = GigaMap.<ShortBox>Builder().withBitmapIndex(SHORT_INDEX).build();
			map.add(new ShortBox((short)0x32));
			map.add(new ShortBox((short)0x33));
			assertEquals(1, map.query(SHORT_INDEX.is((short)0x33)).toList().size());
			assertTrue(map.query(SHORT_INDEX.is((short)0x34)).toList().isEmpty());
		}

		@Test
		void byteSubsetDoesNotCollide()
		{
			final GigaMap<ByteBox> map = GigaMap.<ByteBox>Builder().withBitmapIndex(BYTE_INDEX).build();
			map.add(new ByteBox((byte)0x32));
			map.add(new ByteBox((byte)0x33));
			assertEquals(1, map.query(BYTE_INDEX.is((byte)0x33)).toList().size());
			assertTrue(map.query(BYTE_INDEX.is((byte)0x34)).toList().isEmpty());
		}

		@Test
		void doubleExactMatch()
		{
			final GigaMap<DoubleBox> map = GigaMap.<DoubleBox>Builder().withBitmapIndex(DOUBLE_INDEX).build();
			map.add(new DoubleBox(1.5d));
			map.add(new DoubleBox(3.25d));
			assertEquals(1, map.query(DOUBLE_INDEX.is(3.25d)).toList().size());
			assertEquals(3.25d, map.query(DOUBLE_INDEX.is(3.25d)).toList().get(0).v());
			assertTrue(map.query(DOUBLE_INDEX.is(2.0d)).toList().isEmpty());
		}

		@Test
		void floatExactMatch()
		{
			final GigaMap<FloatBox> map = GigaMap.<FloatBox>Builder().withBitmapIndex(FLOAT_INDEX).build();
			map.add(new FloatBox(1.5f));
			map.add(new FloatBox(3.25f));
			assertEquals(1, map.query(FLOAT_INDEX.is(3.25f)).toList().size());
			assertTrue(map.query(FLOAT_INDEX.is(2.0f)).toList().isEmpty());
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Persistence round-trip for the composite string index.
	// ---------------------------------------------------------------------------------------------

	@Nested
	class Persistence
	{
		@TempDir
		Path tempDir;

		@Test
		void exactMatchSurvivesStoreAndReload()
		{
			final GigaMap<StrBox> map = stringMap("test", "test2", "test3", "abcdefghi", "日本語");
			try(final EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir))
			{
				// stored
			}

			final GigaMap<StrBox> reloaded = GigaMap.New();
			try(final EmbeddedStorageManager manager = EmbeddedStorage.start(reloaded, tempDir))
			{
				assertEquals(5, reloaded.size());
				assertEquals(1, reloaded.query(STRING_INDEX.is("test3")).toList().size());
				// the #687 collision must still be correct after reload
				assertEquals(1, reloaded.query(STRING_INDEX.is("test")).toList().size());
				assertEquals(1, reloaded.query(STRING_INDEX.is("abcdefghi")).toList().size());
				assertEquals(1, reloaded.query(STRING_INDEX.is("日本語")).toList().size());
				assertTrue(reloaded.query(STRING_INDEX.is("nope")).toList().isEmpty());
			}
		}
	}
}
