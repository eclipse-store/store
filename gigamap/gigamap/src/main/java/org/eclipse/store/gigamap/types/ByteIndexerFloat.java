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
 * Byte-decomposed composite indexer for {@link Float} keys.
 * <p>
 * Uses IEEE 754 float-to-int bit conversion with sign-magnitude to
 * offset-binary transformation for order-preserving byte encoding.
 * <p>
 * {@link Float#NaN} is rejected at both index time and query time because NaN
 * is not ordered and would produce undefined query results. Positive and negative
 * infinity are fully supported and maintain their natural ordering.
 *
 * @param <E> the entity type
 *
 * @see ByteIndexerNumber
 */
public interface ByteIndexerFloat<E> extends ByteIndexerNumber<E, Float>
{
	/**
	 * Abstract base class for a {@link Float} key {@link ByteIndexerNumber}.
	 *
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends ByteIndexerNumber.Abstract<E, Float> implements ByteIndexerFloat<E>
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

		protected abstract Float getFloat(E entity);

		@Override
		protected final int byteCount()
		{
			return Float.BYTES;
		}

		@Override
		protected final void toOrderedBytes(final Float value, final byte[] target)
		{
			if(Float.isNaN(value))
			{
				throw new IllegalArgumentException("NaN is not supported because it is not ordered");
			}
			final int bits = Float.floatToIntBits(value);
			final int ordered = bits >= 0
				? bits ^ 0x80000000
				: ~bits
			;
			target[0] = (byte)(ordered >>> 24);
			target[1] = (byte)(ordered >>> 16);
			target[2] = (byte)(ordered >>>  8);
			target[3] = (byte) ordered;
		}
	}

}
