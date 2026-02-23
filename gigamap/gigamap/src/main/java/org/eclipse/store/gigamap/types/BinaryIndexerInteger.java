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
 * An interface that extends {@link BinaryIndexerNumber} specifically for using {@link Integer} as the key type.
 * It provides indexing capabilities, optimized for binary operations and high-cardinality indices,
 * while working with entities of type {@code E}.
 *
 * @param <E> the type of entities being indexed
 */
public interface BinaryIndexerInteger<E> extends BinaryIndexerNumber<E, Integer>
{
	public abstract class Abstract<E> extends BinaryIndexerNumber.Abstract<E, Integer> implements BinaryIndexerInteger<E>
	{
		protected Abstract()
		{
			super();
		}

		@Override
		protected final Integer getNumber(final E entity)
		{
			return this.getInteger(entity);
		}

		/**
		 * Converts an Integer value to a non-zero long suitable for the binary bitmap index.
		 * <p>
		 * The bitmap index uses bit positions in a long as array indices. A value of {@code 0L}
		 * has no bits set and would cause the entity to be silently skipped during indexing.
		 * <p>
		 * For non-zero values, the unsigned representation is used. This is identical to
		 * {@code longValue()} for positive values (backwards compatible with existing storages)
		 * and maps negative values to the upper unsigned range [2^31, 2^32-1] using only 32 bits,
		 * avoiding the 64-bit sign extension that {@code longValue()} would produce.
		 * <p>
		 * For zero, a sentinel value of {@code 1L << 32} (= 2^32) is used. This is exactly one
		 * above the maximum unsigned int value (2^32-1), so it can never collide with any
		 * non-zero int's unsigned representation.
		 */
		@Override
		protected long toLong(final Integer number)
		{
			if(number == 0)
			{
				return 1L << Integer.SIZE;
			}
			return Integer.toUnsignedLong(number);
		}

		protected abstract Integer getInteger(final E entity);

	}
	
}
