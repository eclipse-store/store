package org.eclipse.store.gigamap;

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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import java.util.UUID;

import org.eclipse.store.gigamap.types.BinaryIndexerByte;
import org.eclipse.store.gigamap.types.BinaryIndexerDouble;
import org.eclipse.store.gigamap.types.BinaryIndexerFloat;
import org.eclipse.store.gigamap.types.BinaryIndexerInteger;
import org.eclipse.store.gigamap.types.BinaryIndexerLong;
import org.eclipse.store.gigamap.types.BinaryIndexerShort;
import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.BinaryIndexerUUID;
import org.eclipse.store.gigamap.types.ByteIndexerByte;
import org.eclipse.store.gigamap.types.ByteIndexerDouble;
import org.eclipse.store.gigamap.types.ByteIndexerFloat;
import org.eclipse.store.gigamap.types.ByteIndexerInstant;
import org.eclipse.store.gigamap.types.ByteIndexerInteger;
import org.eclipse.store.gigamap.types.ByteIndexerLong;
import org.eclipse.store.gigamap.types.ByteIndexerShort;
import org.eclipse.store.gigamap.types.IndexerByte;
import org.eclipse.store.gigamap.types.IndexerDouble;
import org.eclipse.store.gigamap.types.IndexerFloat;
import org.eclipse.store.gigamap.types.IndexerInstant;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.gigamap.types.IndexerLocalDate;
import org.eclipse.store.gigamap.types.IndexerLocalDateTime;
import org.eclipse.store.gigamap.types.IndexerLocalTime;
import org.eclipse.store.gigamap.types.IndexerLong;
import org.eclipse.store.gigamap.types.IndexerShort;
import org.eclipse.store.gigamap.types.IndexerMultiValue;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.gigamap.types.IndexerZonedDateTime;

import java.util.List;

/**
 * Compilation-only test to verify that all indexer type hierarchies resolve
 * without ambiguous method errors. If any default-vs-abstract conflict
 * exists in the type hierarchy, this class will fail to compile.
 */
public class CompilationTest
{
	// --- IndexerNumber hierarchy (default vs abstract via NumberQueryable) ---

	private static final IndexerByte<Object> INDEXER_BYTE = new IndexerByte.Abstract<>()
	{
		@Override
		protected Byte getByte(final Object entity)
		{
			return 0;
		}
	};

	private static final IndexerShort<Object> INDEXER_SHORT = new IndexerShort.Abstract<>()
	{
		@Override
		protected Short getShort(final Object entity)
		{
			return 0;
		}
	};

	private static final IndexerInteger<Object> INDEXER_INTEGER = new IndexerInteger.Abstract<>()
	{
		@Override
		public Integer getInteger(final Object entity)
		{
			return 0;
		}
	};

	private static final IndexerLong<Object> INDEXER_LONG = new IndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(final Object entity)
		{
			return 0L;
		}
	};

	private static final IndexerFloat<Object> INDEXER_FLOAT = new IndexerFloat.Abstract<>()
	{
		@Override
		protected Float getFloat(final Object entity)
		{
			return 0f;
		}
	};

	private static final IndexerDouble<Object> INDEXER_DOUBLE = new IndexerDouble.Abstract<>()
	{
		@Override
		protected Double getDouble(final Object entity)
		{
			return 0d;
		}
	};

	// --- ByteIndexerNumber hierarchy (HashingCompositeIndexer + NumberQueryable) ---

	private static final ByteIndexerByte<Object> BYTE_INDEXER_BYTE = new ByteIndexerByte.Abstract<>()
	{
		@Override
		protected Byte getByte(final Object entity)
		{
			return 0;
		}
	};

	private static final ByteIndexerShort<Object> BYTE_INDEXER_SHORT = new ByteIndexerShort.Abstract<>()
	{
		@Override
		protected Short getShort(final Object entity)
		{
			return 0;
		}
	};

	private static final ByteIndexerInteger<Object> BYTE_INDEXER_INTEGER = new ByteIndexerInteger.Abstract<>()
	{
		@Override
		protected Integer getInteger(final Object entity)
		{
			return 0;
		}
	};

	private static final ByteIndexerLong<Object> BYTE_INDEXER_LONG = new ByteIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(final Object entity)
		{
			return 0L;
		}
	};

	private static final ByteIndexerFloat<Object> BYTE_INDEXER_FLOAT = new ByteIndexerFloat.Abstract<>()
	{
		@Override
		protected Float getFloat(final Object entity)
		{
			return 0f;
		}
	};

	private static final ByteIndexerDouble<Object> BYTE_INDEXER_DOUBLE = new ByteIndexerDouble.Abstract<>()
	{
		@Override
		protected Double getDouble(final Object entity)
		{
			return 0d;
		}
	};

	// --- Temporal hierarchy (IndexerTemporal + IndexerDate/Time/DateTime) ---

	private static final IndexerLocalDate<Object> INDEXER_LOCAL_DATE = new IndexerLocalDate.Abstract<>()
	{
		@Override
		protected LocalDate getLocalDate(final Object entity)
		{
			return LocalDate.EPOCH;
		}
	};

	private static final IndexerLocalTime<Object> INDEXER_LOCAL_TIME = new IndexerLocalTime.Abstract<>()
	{
		@Override
		protected LocalTime getLocalTime(final Object entity)
		{
			return LocalTime.MIDNIGHT;
		}
	};

	private static final IndexerLocalDateTime<Object> INDEXER_LOCAL_DATE_TIME = new IndexerLocalDateTime.Abstract<>()
	{
		@Override
		protected LocalDateTime getLocalDateTime(final Object entity)
		{
			return LocalDateTime.MIN;
		}
	};

	private static final IndexerInstant<Object> INDEXER_INSTANT = new IndexerInstant.Abstract<>()
	{
		@Override
		protected Instant getInstant(final Object entity)
		{
			return Instant.EPOCH;
		}
	};

	private static final IndexerZonedDateTime<Object> INDEXER_ZONED_DATE_TIME = new IndexerZonedDateTime.Abstract<>()
	{
		@Override
		protected ZonedDateTime getZonedDateTime(final Object entity)
		{
			return ZonedDateTime.now();
		}
	};

	// --- ByteIndexerInstant (HashingCompositeIndexer + IndexerTemporal) ---

	private static final ByteIndexerInstant<Object> BYTE_INDEXER_INSTANT = new ByteIndexerInstant.Abstract<>()
	{
		@Override
		protected Instant getInstant(final Object entity)
		{
			return Instant.EPOCH;
		}
	};

	// --- BinaryIndexerNumber hierarchy (BinaryIndexer with long-based keys) ---

	private static final BinaryIndexerByte<Object> BINARY_INDEXER_BYTE = new BinaryIndexerByte.Abstract<>()
	{
		@Override
		protected Byte getByte(final Object entity)
		{
			return 0;
		}
	};

	private static final BinaryIndexerShort<Object> BINARY_INDEXER_SHORT = new BinaryIndexerShort.Abstract<>()
	{
		@Override
		protected Short getShort(final Object entity)
		{
			return 0;
		}
	};

	private static final BinaryIndexerInteger<Object> BINARY_INDEXER_INTEGER = new BinaryIndexerInteger.Abstract<>()
	{
		@Override
		protected Integer getInteger(final Object entity)
		{
			return 0;
		}
	};

	private static final BinaryIndexerLong<Object> BINARY_INDEXER_LONG = new BinaryIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(final Object entity)
		{
			return 0L;
		}
	};

	private static final BinaryIndexerFloat<Object> BINARY_INDEXER_FLOAT = new BinaryIndexerFloat.Abstract<>()
	{
		@Override
		protected Float getFloat(final Object entity)
		{
			return 0f;
		}
	};

	private static final BinaryIndexerDouble<Object> BINARY_INDEXER_DOUBLE = new BinaryIndexerDouble.Abstract<>()
	{
		@Override
		protected Double getDouble(final Object entity)
		{
			return 0d;
		}
	};

	// --- BinaryCompositeIndexer hierarchy (BinaryIndexerString, BinaryIndexerUUID) ---

	private static final BinaryIndexerString<Object> BINARY_INDEXER_STRING = new BinaryIndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Object entity)
		{
			return "";
		}
	};

	private static final BinaryIndexerUUID<Object> BINARY_INDEXER_UUID = new BinaryIndexerUUID.Abstract<>()
	{
		@Override
		protected UUID getUUID(final Object entity)
		{
			return UUID.randomUUID();
		}
	};

	// --- IndexerMultiValue (Indexer with multi-value keys) ---

	private static final IndexerMultiValue<Object, String> INDEXER_MULTI_VALUE = new IndexerMultiValue.Abstract<>()
	{
		@Override
		public Class<String> keyType()
		{
			return String.class;
		}

		@Override
		public Iterable<? extends String> indexEntityMultiValue(final Object entity)
		{
			return List.of();
		}
	};

	// --- IndexerString (Indexer with additional string methods) ---

	private static final IndexerString<Object> INDEXER_STRING = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Object entity)
		{
			return "";
		}
	};

	/**
	 * Exercise all query methods on each indexer to ensure method resolution
	 * is unambiguous at every call site.
	 */
	void verifyMethodResolution()
	{
		final Object entity = new Object();

		// IndexerNumber types: is, not, in, notIn, lessThan, lessThanEqual, greaterThan, greaterThanEqual, between
		INDEXER_BYTE.index(entity);
		INDEXER_BYTE.is((byte) 1);
		INDEXER_BYTE.not((byte) 1);
		INDEXER_BYTE.in((byte) 1, (byte) 2);
		INDEXER_BYTE.notIn((byte) 1, (byte) 2);
		INDEXER_BYTE.lessThan((byte) 1);
		INDEXER_BYTE.lessThanEqual((byte) 1);
		INDEXER_BYTE.greaterThan((byte) 1);
		INDEXER_BYTE.greaterThanEqual((byte) 1);
		INDEXER_BYTE.between((byte) 1, (byte) 2);

		INDEXER_SHORT.index(entity);
		INDEXER_SHORT.is((short) 1);
		INDEXER_SHORT.not((short) 1);
		INDEXER_SHORT.in((short) 1, (short) 2);
		INDEXER_SHORT.notIn((short) 1, (short) 2);
		INDEXER_SHORT.lessThan((short) 1);
		INDEXER_SHORT.between((short) 1, (short) 2);

		INDEXER_INTEGER.index(entity);
		INDEXER_INTEGER.is(1);
		INDEXER_INTEGER.not(1);
		INDEXER_INTEGER.in(1, 2);
		INDEXER_INTEGER.notIn(1, 2);
		INDEXER_INTEGER.lessThan(1);
		INDEXER_INTEGER.lessThanEqual(1);
		INDEXER_INTEGER.greaterThan(1);
		INDEXER_INTEGER.greaterThanEqual(1);
		INDEXER_INTEGER.between(1, 2);

		INDEXER_LONG.index(entity);
		INDEXER_LONG.is(1L);
		INDEXER_LONG.not(1L);
		INDEXER_LONG.in(1L, 2L);
		INDEXER_LONG.notIn(1L, 2L);
		INDEXER_LONG.lessThan(1L);
		INDEXER_LONG.between(1L, 2L);

		INDEXER_FLOAT.index(entity);
		INDEXER_FLOAT.is(1f);
		INDEXER_FLOAT.not(1f);
		INDEXER_FLOAT.in(1f, 2f);
		INDEXER_FLOAT.notIn(1f, 2f);
		INDEXER_FLOAT.lessThan(1f);
		INDEXER_FLOAT.between(1f, 2f);

		INDEXER_DOUBLE.index(entity);
		INDEXER_DOUBLE.is(1d);
		INDEXER_DOUBLE.not(1d);
		INDEXER_DOUBLE.in(1d, 2d);
		INDEXER_DOUBLE.notIn(1d, 2d);
		INDEXER_DOUBLE.lessThan(1d);
		INDEXER_DOUBLE.between(1d, 2d);

		// ByteIndexerNumber types: is, not, in, notIn, lessThan, lessThanEqual, greaterThan, greaterThanEqual, between
		BYTE_INDEXER_BYTE.is((byte) 1);
		BYTE_INDEXER_BYTE.not((byte) 1);
		BYTE_INDEXER_BYTE.in((byte) 1, (byte) 2);
		BYTE_INDEXER_BYTE.notIn((byte) 1, (byte) 2);
		BYTE_INDEXER_BYTE.lessThan((byte) 1);
		BYTE_INDEXER_BYTE.between((byte) 1, (byte) 2);

		BYTE_INDEXER_SHORT.is((short) 1);
		BYTE_INDEXER_SHORT.not((short) 1);
		BYTE_INDEXER_SHORT.in((short) 1, (short) 2);
		BYTE_INDEXER_SHORT.notIn((short) 1, (short) 2);
		BYTE_INDEXER_SHORT.lessThan((short) 1);
		BYTE_INDEXER_SHORT.between((short) 1, (short) 2);

		BYTE_INDEXER_INTEGER.is(1);
		BYTE_INDEXER_INTEGER.not(1);
		BYTE_INDEXER_INTEGER.in(1, 2);
		BYTE_INDEXER_INTEGER.notIn(1, 2);
		BYTE_INDEXER_INTEGER.lessThan(1);
		BYTE_INDEXER_INTEGER.between(1, 2);

		BYTE_INDEXER_LONG.is(1L);
		BYTE_INDEXER_LONG.not(1L);
		BYTE_INDEXER_LONG.in(1L, 2L);
		BYTE_INDEXER_LONG.notIn(1L, 2L);
		BYTE_INDEXER_LONG.lessThan(1L);
		BYTE_INDEXER_LONG.between(1L, 2L);

		BYTE_INDEXER_FLOAT.is(1f);
		BYTE_INDEXER_FLOAT.not(1f);
		BYTE_INDEXER_FLOAT.in(1f, 2f);
		BYTE_INDEXER_FLOAT.notIn(1f, 2f);
		BYTE_INDEXER_FLOAT.lessThan(1f);
		BYTE_INDEXER_FLOAT.between(1f, 2f);

		BYTE_INDEXER_DOUBLE.is(1d);
		BYTE_INDEXER_DOUBLE.not(1d);
		BYTE_INDEXER_DOUBLE.in(1d, 2d);
		BYTE_INDEXER_DOUBLE.notIn(1d, 2d);
		BYTE_INDEXER_DOUBLE.lessThan(1d);
		BYTE_INDEXER_DOUBLE.between(1d, 2d);

		// Temporal types: is, before, beforeEqual, after, afterEqual, between
		final Instant instant = Instant.EPOCH;
		INDEXER_INSTANT.is(instant);
		INDEXER_INSTANT.before(instant);
		INDEXER_INSTANT.beforeEqual(instant);
		INDEXER_INSTANT.after(instant);
		INDEXER_INSTANT.afterEqual(instant);
		INDEXER_INSTANT.between(instant, instant);

		final LocalDate date = LocalDate.EPOCH;
		INDEXER_LOCAL_DATE.is(date);
		INDEXER_LOCAL_DATE.before(date);
		INDEXER_LOCAL_DATE.after(date);
		INDEXER_LOCAL_DATE.between(date, date);

		final LocalTime time = LocalTime.MIDNIGHT;
		INDEXER_LOCAL_TIME.is(time);
		INDEXER_LOCAL_TIME.before(time);
		INDEXER_LOCAL_TIME.after(time);
		INDEXER_LOCAL_TIME.between(time, time);

		final LocalDateTime dateTime = LocalDateTime.MIN;
		INDEXER_LOCAL_DATE_TIME.is(dateTime);
		INDEXER_LOCAL_DATE_TIME.before(dateTime);
		INDEXER_LOCAL_DATE_TIME.after(dateTime);
		INDEXER_LOCAL_DATE_TIME.between(dateTime, dateTime);

		final ZonedDateTime zonedDateTime = ZonedDateTime.now();
		INDEXER_ZONED_DATE_TIME.is(zonedDateTime);
		INDEXER_ZONED_DATE_TIME.before(zonedDateTime);
		INDEXER_ZONED_DATE_TIME.after(zonedDateTime);
		INDEXER_ZONED_DATE_TIME.between(zonedDateTime, zonedDateTime);

		// ByteIndexerInstant: is, before, beforeEqual, after, afterEqual, between, inSecond
		BYTE_INDEXER_INSTANT.is(instant);
		BYTE_INDEXER_INSTANT.before(instant);
		BYTE_INDEXER_INSTANT.beforeEqual(instant);
		BYTE_INDEXER_INSTANT.after(instant);
		BYTE_INDEXER_INSTANT.afterEqual(instant);
		BYTE_INDEXER_INSTANT.between(instant, instant);
		BYTE_INDEXER_INSTANT.inSecond(instant);

		// BinaryIndexerNumber types: is, not, in, notIn (key type is Long)
		BINARY_INDEXER_BYTE.index(entity);
		BINARY_INDEXER_BYTE.is(1L);
		BINARY_INDEXER_BYTE.not(1L);
		BINARY_INDEXER_BYTE.in(1L, 2L);
		BINARY_INDEXER_BYTE.notIn(1L, 2L);

		BINARY_INDEXER_SHORT.index(entity);
		BINARY_INDEXER_SHORT.is(1L);
		BINARY_INDEXER_SHORT.not(1L);
		BINARY_INDEXER_SHORT.in(1L, 2L);
		BINARY_INDEXER_SHORT.notIn(1L, 2L);

		BINARY_INDEXER_INTEGER.index(entity);
		BINARY_INDEXER_INTEGER.is(1L);
		BINARY_INDEXER_INTEGER.not(1L);
		BINARY_INDEXER_INTEGER.in(1L, 2L);
		BINARY_INDEXER_INTEGER.notIn(1L, 2L);

		BINARY_INDEXER_LONG.index(entity);
		BINARY_INDEXER_LONG.is(1L);
		BINARY_INDEXER_LONG.not(1L);
		BINARY_INDEXER_LONG.in(1L, 2L);
		BINARY_INDEXER_LONG.notIn(1L, 2L);

		BINARY_INDEXER_FLOAT.index(entity);
		BINARY_INDEXER_FLOAT.is(1L);
		BINARY_INDEXER_FLOAT.not(1L);
		BINARY_INDEXER_FLOAT.in(1L, 2L);
		BINARY_INDEXER_FLOAT.notIn(1L, 2L);

		BINARY_INDEXER_DOUBLE.index(entity);
		BINARY_INDEXER_DOUBLE.is(1L);
		BINARY_INDEXER_DOUBLE.not(1L);
		BINARY_INDEXER_DOUBLE.in(1L, 2L);
		BINARY_INDEXER_DOUBLE.notIn(1L, 2L);

		// BinaryCompositeIndexer types: is, not, in, notIn (key type is long[])
		BINARY_INDEXER_STRING.index(entity);
		BINARY_INDEXER_STRING.is(new long[]{1L});
		BINARY_INDEXER_STRING.not(new long[]{1L});

		BINARY_INDEXER_UUID.index(entity);
		BINARY_INDEXER_UUID.is(new long[]{1L, 2L});
		BINARY_INDEXER_UUID.not(new long[]{1L, 2L});

		// IndexerMultiValue: is, not, in, notIn, all
		INDEXER_MULTI_VALUE.is("a");
		INDEXER_MULTI_VALUE.not("a");
		INDEXER_MULTI_VALUE.in("a", "b");
		INDEXER_MULTI_VALUE.notIn("a", "b");
		INDEXER_MULTI_VALUE.all("a", "b");

		// IndexerString: is, not, in, notIn, contains, startsWith, endsWith
		INDEXER_STRING.is("a");
		INDEXER_STRING.not("a");
		INDEXER_STRING.in("a", "b");
		INDEXER_STRING.notIn("a", "b");
		INDEXER_STRING.contains("a");
		INDEXER_STRING.containsIgnoreCase("a");
		INDEXER_STRING.startsWith("a");
		INDEXER_STRING.startsWithIgnoreCase("a");
		INDEXER_STRING.endsWith("a");
		INDEXER_STRING.endsWithIgnoreCase("a");
		INDEXER_STRING.isEmpty();
		INDEXER_STRING.isBlank();
	}
}
