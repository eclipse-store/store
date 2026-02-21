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
//		static String longArrayToString(final long[] encoded)
//		{
//			// Find the actual byte length by scanning for non-zero bytes
//			int byteLength = encoded.length * 8;
//			for(int i = encoded.length * 8 - 1; i >= 0; i--)
//			{
//				final int arrayIndex = i / 8;
//				final int bitPosition = (i % 8) * 8;
//				if(((encoded[arrayIndex] >> bitPosition) & 0xFF) != 0)
//				{
//					byteLength = i + 1;
//					break;
//				}
//			}
//
//			final byte[] bytes = new byte[byteLength];
//
//			for(int i = 0; i < byteLength; i++)
//			{
//				final int arrayIndex = i / 8;
//				final int bitPosition = (i % 8) * 8;
//				bytes[i] = (byte)((encoded[arrayIndex] >> bitPosition) & 0xFF);
//			}
//
//			return new String(bytes, StandardCharsets.UTF_8);
//		}
		
		
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
		 * For non-zero packed values, the raw encoding is used (backwards compatible with existing storages).
		 * Only 8 consecutive null bytes ({@code 0x00}) at a position boundary produce {@code 0L},
		 * which is mapped to {@code Long.MAX_VALUE}. This is collision-free because
		 * {@code Long.MAX_VALUE} ({@code 0x7FFFFFFFFFFFFFFF}) requires {@code 0xFF} bytes in
		 * positions 0-6, and {@code 0xFF} is invalid in UTF-8 — it can never appear in the
		 * output of {@code String.getBytes(UTF_8)}.
		 */
		@Override
		protected long[] fillCarrier(final String value, final long[] carrier)
		{
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
