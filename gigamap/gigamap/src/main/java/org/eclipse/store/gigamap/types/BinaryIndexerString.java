package org.eclipse.store.gigamap.types;

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

import java.nio.charset.StandardCharsets;


/**
 * Binary indexing logic for high-cardinality String keys.
 *
 * @param <E> the entity type
 *
 * @see Indexer
 */
public interface BinaryIndexerString<E> extends BinaryCompositeIndexer<E>
{
	/**
	 * Creates an equality condition for the given key. This condition checks whether
	 * the key extracted by this index is equal to the specified key.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param key the key to compare for equality
	 * @return a new condition representing the equality check for the given key
	 */
	public <S extends E> Condition<S> is(String key);

	/**
	 * Creates a negated condition for the given key. This condition checks whether
	 * the key extracted by this index is not equal to the specified key.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param key the key to compare for inequality
	 * @return a new condition representing the inequality check for the given key
	 */
	public <S extends E> Condition<S> not(String key);


	public static abstract class Abstract<E> extends AbstractSingleValueVariableSize<E, String> implements BinaryIndexerString<E>
	{
		// reserved sentinel for the empty string: byte 7 = 0xFF, which UTF-8 can never produce, so it
		// cannot collide with any non-empty string's packed long nor with the all-null Long.MAX_VALUE.
		private static final long EMPTY_VALUE_SENTINEL = 0xFF00000000000000L;

		protected Abstract()
		{
			super();
		}

		protected abstract String getString(E entity);

		@Override
		protected final String getValue(final E entity)
		{
			return this.getString(entity);
		}

		/**
		 * Packs the string's UTF-8 bytes into long values (8 bytes per long), ensuring no position
		 * is {@code 0L} since the composite bitmap index treats {@code 0L} as "empty position".
		 * <p>
		 * Strings containing the NUL character (code point {@code 0}) are rejected with an
		 * {@link IllegalArgumentException}: their UTF-8 representation contains {@code 0x00} bytes,
		 * and because the byte length is not encoded, trailing NUL bytes vanish during packing.
		 * That would make e.g. {@code "alpha"} and {@code "alpha"} followed by a NUL - or NUL-only
		 * strings of different length - collide on the same index key, so an exact-match query would return
		 * entities that an in-memory {@link Condition#test(Object)} scan rejects. Failing loudly is
		 * preferred over silently returning wrong results; normalize NUL out at the input instead.
		 * <p>
		 * For the remaining (NUL-free) values the raw encoding is used (backwards compatible with
		 * existing storages). The only way to obtain {@code 0L} for a non-empty, NUL-free string is
		 * a packed long whose bytes are all zero, which cannot occur once NUL is rejected; the
		 * {@code Long.MAX_VALUE} remapping below is therefore a defensive guard. It is collision-free
		 * because {@code Long.MAX_VALUE} ({@code 0x7FFFFFFFFFFFFFFF}) requires {@code 0xFF} bytes in
		 * positions 0-6, and {@code 0xFF} is invalid in UTF-8 - it can never appear in the
		 * output of {@code String.getBytes(UTF_8)}.
		 */
		@Override
		protected long[] fillCarrier(final String value, final long[] carrier)
		{
			// indexOf(int) searches by code point; 0 is the NUL char. Using the int overload avoids
			// embedding a literal NUL in the source. A 0x00 UTF-8 byte can only originate from U+0000,
			// so this is an exact and cheap check for the only offending character.
			if(value.indexOf(0) >= 0)
			{
				throw new IllegalArgumentException(
					"NUL (\\u0000) is not supported as part of a BinaryIndexerString key, "
					+ "because the binary index cannot distinguish trailing NUL bytes. "
					+ "Normalize the value at the input (e.g. cut at the terminator) before indexing."
				);
			}

			if(value.isEmpty())
			{
				// An empty string packs into zero bytes, which would leave it without any bit position
				// and therefore unindexable (never added to a sub index, never matched by a query).
				// Use a reserved single-position sentinel so the empty string is indexable and queryable.
				// 0xFF is never a valid UTF-8 byte, so no non-empty string can ever produce this value,
				// and it differs from the all-null-bytes sentinel (Long.MAX_VALUE) used below.
				// Reuse the (already zero-filled) carrier when possible to avoid an allocation.
				final long[] result = carrier != null && carrier.length >= 1 ? carrier : new long[1];
				result[0] = EMPTY_VALUE_SENTINEL;
				return result;
			}

			final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
			final int size = (bytes.length + 7) / 8; // Round up division to handle any string length
			long[] result = carrier;
			if(carrier == null || carrier.length < size)
			{
				result = new long[size];
			}

			for(int i = 0; i < bytes.length; i++)
			{
				final int arrayIndex  = i / 8;
				final int bitPosition = (i % 8) * 8;
				result[arrayIndex] |= ((long)(bytes[i] & 0xFF)) << bitPosition;
			}

			for(int i = 0; i < size; i++)
			{
				if(result[i] == 0L)
				{
					result[i] = Long.MAX_VALUE;
				}
			}

			return result;
		}

		@Override
		public <S extends E> Condition<S> is(final String key)
		{
			return this.isValue(key);
		}

		@Override
		public <S extends E> Condition<S> not(final String key)
		{
			return new Condition.Not<>(this.is(key));
		}
	}







}
