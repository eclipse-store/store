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
 * An interface that extends {@link BinaryIndexerNumber} specifically for using {@link Double} as the key type.
 * It provides indexing capabilities, optimized for binary operations and high-cardinality indices,
 * while working with entities of type {@code E}.
 *
 * @param <E> the type of entities being indexed
 */
public interface BinaryIndexerDouble<E> extends BinaryIndexerNumber<E, Double>
{
	public abstract class Abstract<E> extends BinaryIndexerNumber.Abstract<E, Double> implements BinaryIndexerDouble<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		protected final Double getNumber(final E entity)
		{
			return this.getDouble(entity);
		}
		
		/**
		 * Converts a Double value to a non-zero long suitable for the binary bitmap index.
		 * <p>
		 * The bitmap index uses bit positions in a long as array indices. A value of {@code 0L}
		 * has no bits set and would cause the entity to be silently skipped during indexing.
		 * <p>
		 * The IEEE 754 bit representation ({@code doubleToLongBits}) is used as the bitmap key.
		 * For non-zero bit patterns, the value is used as-is (backwards compatible with existing storages).
		 * Only {@code 0.0} produces a zero bit pattern ({@code -0.0} has bit 63 set and is non-zero).
		 * <p>
		 * For zero, the sentinel {@code 0x7FF0000000000001L} is used. This is a non-canonical
		 * IEEE 754 NaN encoding (exponent all-ones, non-zero mantissa) that {@code doubleToLongBits}
		 * can never return for any double value, because it normalizes all NaN representations
		 * to the canonical {@code 0x7FF8000000000000L}. This makes the sentinel completely
		 * collision-free, unlike the Long indexer which must sacrifice an actual value.
		 */
		@Override
		protected long toLong(final Double number)
		{
			final long bits = Double.doubleToLongBits(number);
			if(bits == 0L)
			{
				return 0x7FF0000000000001L;
			}
			return bits;
		}
		
		protected abstract Double getDouble(final E entity);
		
	}
	
}
