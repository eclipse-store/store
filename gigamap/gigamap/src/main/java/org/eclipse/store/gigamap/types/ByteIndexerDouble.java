package org.eclipse.store.gigamap.types;

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


/**
 * Byte-decomposed composite indexer for {@link Double} keys.
 * <p>
 * Uses IEEE 754 double-to-long bit conversion with sign-magnitude to
 * offset-binary transformation for order-preserving byte encoding.
 * <p>
 * {@link Double#NaN} is rejected at both index time and query time because NaN
 * is not ordered and would produce undefined query results. Positive and negative
 * infinity are fully supported and maintain their natural ordering.
 * <p>
 * Positive zero ({@code 0.0}) and negative zero ({@code -0.0}) are treated as distinct
 * values, with {@code -0.0} ordered before {@code 0.0}. This is consistent with
 * {@link Double#compare(double, double)} and {@link Double#doubleToLongBits(double)} semantics.
 *
 * @param <E> the entity type
 *
 * @see ByteIndexerNumber
 */
public interface ByteIndexerDouble<E> extends ByteIndexerNumber<E, Double>
{
	/**
	 * Abstract base class for a {@link Double} key {@link ByteIndexerNumber}.
	 *
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends ByteIndexerNumber.Abstract<E, Double> implements ByteIndexerDouble<E>
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

		protected abstract Double getDouble(E entity);

		@Override
		protected final int byteCount()
		{
			return Double.BYTES;
		}

		@Override
		protected final void toOrderedBytes(final Double value, final byte[] target)
		{
			if(Double.isNaN(value))
			{
				throw new IllegalArgumentException("NaN is not supported because it is not ordered");
			}
			final long bits = Double.doubleToLongBits(value);
			final long ordered = bits >= 0
				? bits ^ 0x8000000000000000L
				: ~bits
			;
			target[0] = (byte)(ordered >>> 56);
			target[1] = (byte)(ordered >>> 48);
			target[2] = (byte)(ordered >>> 40);
			target[3] = (byte)(ordered >>> 32);
			target[4] = (byte)(ordered >>> 24);
			target[5] = (byte)(ordered >>> 16);
			target[6] = (byte)(ordered >>>  8);
			target[7] = (byte) ordered;
		}
	}

}
