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
 * An interface that extends {@link BinaryIndexerNumber} specifically for using {@link Float} as the key type.
 * It provides indexing capabilities, optimized for binary operations and high-cardinality indices,
 * while working with entities of type {@code E}.
 *
 * @param <E> the type of entities being indexed
 */
public interface BinaryIndexerFloat<E> extends BinaryIndexerNumber<E, Float>
{
	public abstract class Abstract<E> extends BinaryIndexerNumber.Abstract<E, Float> implements BinaryIndexerFloat<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		protected final Float getNumber(final E entity)
		{
			return this.getFloat(entity);
		}
		
		/**
		 * Converts a Float value to a non-zero long suitable for the binary bitmap index.
		 * <p>
		 * The bitmap index uses bit positions in a long as array indices. A value of {@code 0L}
		 * has no bits set and would cause the entity to be silently skipped during indexing.
		 * <p>
		 * The IEEE 754 bit representation ({@code floatToIntBits}) is used as the bitmap key.
		 * For non-zero bit patterns, the unsigned int-to-long conversion is used. This is identical
		 * to sign extension for positive floats (backwards compatible with existing storages)
		 * and maps negative floats to the upper unsigned 32-bit range using only 32 bits,
		 * avoiding the 64-bit sign extension that plain int-to-long widening would produce.
		 * <p>
		 * Only {@code 0.0f} produces a zero bit pattern ({@code -0.0f} has bit 31 set and is non-zero).
		 * For this case, a sentinel value of {@code 1L << 32} (= 2^32) is used. This is exactly one
		 * above the maximum unsigned 32-bit value, so it can never collide with any non-zero
		 * float's bit representation.
		 */
		@Override
		protected long toLong(final Float number)
		{
			final int bits = Float.floatToIntBits(number);
			if(bits == 0)
			{
				return 1L << Integer.SIZE;
			}
			return Integer.toUnsignedLong(bits);
		}
		
		protected abstract Float getFloat(final E entity);
		
	}
	
}
