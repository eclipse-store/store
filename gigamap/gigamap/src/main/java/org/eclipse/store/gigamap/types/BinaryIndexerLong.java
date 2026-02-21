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

/**
 * An interface that extends {@link BinaryIndexerNumber} specifically for using {@link Long} as the key type.
 * It provides indexing capabilities, optimized for binary operations and high-cardinality indices,
 * while working with entities of type {@code E}.
 * <p>
 * <b>Restriction:</b> {@code Long.MAX_VALUE} is not supported as an index key and will throw an
 * {@link IllegalArgumentException}. It is reserved internally as a sentinel for zero in the binary
 * bitmap index. All other long values, including zero and negative values, are fully supported.
 *
 * @param <E> the type of entities being indexed
 */
public interface BinaryIndexerLong<E> extends BinaryIndexerNumber<E, Long>
{
	@Override
	public <S extends E> Condition<S> is(Long key);
	
	@Override
	public <S extends E> Condition<S> in(Long... keys);
		
	
	
	public abstract class Abstract<E> extends BinaryIndexerNumber.Abstract<E, Long> implements BinaryIndexerLong<E>
	{
		protected Abstract()
		{
			super();
		}

		@Override
		protected final Long getNumber(final E entity)
		{
			return this.getLong(entity);
		}

		/**
		 * Converts a Long value to a non-zero long suitable for the binary bitmap index.
		 * <p>
		 * The bitmap index uses bit positions in a long as array indices. A value of {@code 0L}
		 * has no bits set and would cause the entity to be silently skipped during indexing.
		 * <p>
		 * For non-zero values, the value is used as-is (backwards compatible with existing storages).
		 * Negative long values have the sign bit set and use up to 64 bitmap entries, which is
		 * inherent to the 64-bit representation.
		 * <p>
		 * For zero, {@code Long.MAX_VALUE} is used as a sentinel. Unlike sub-64-bit types,
		 * the full 64-bit long range has no unused bit position above it for a collision-free sentinel.
		 * {@code Long.MAX_VALUE} is sacrificed because zero is a far more common index value.
		 * Using {@code Long.MAX_VALUE} as an index key is therefore not supported and throws
		 * an {@link IllegalArgumentException}.
		 *
		 * @throws IllegalArgumentException if the value is {@code Long.MAX_VALUE}
		 */
		@Override
		protected long toLong(final Long number)
		{
			if(number == Long.MAX_VALUE)
			{
				throw new IllegalArgumentException(
					"Long.MAX_VALUE is not supported as an index key because it is reserved as the zero sentinel in the binary bitmap index."
				);
			}
			if(number == 0L)
			{
				return Long.MAX_VALUE;
			}
			return number;
		}

		protected abstract Long getLong(final E entity);

	}
	
}
