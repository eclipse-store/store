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
 * Byte-decomposed composite indexer for {@link Integer} keys.
 *
 * @param <E> the entity type
 *
 * @see ByteIndexerNumber
 */
public interface ByteIndexerInteger<E> extends ByteIndexerNumber<E, Integer>
{
	/**
	 * Abstract base class for an {@link Integer} key {@link ByteIndexerNumber}.
	 *
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends ByteIndexerNumber.Abstract<E, Integer> implements ByteIndexerInteger<E>
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

		protected abstract Integer getInteger(E entity);

		@Override
		protected final int byteCount()
		{
			return Integer.BYTES;
		}

		@Override
		protected final void toOrderedBytes(final Integer value, final byte[] target)
		{
			final int ordered = value ^ 0x80000000;
			target[0] = (byte)(ordered >>> 24);
			target[1] = (byte)(ordered >>> 16);
			target[2] = (byte)(ordered >>>  8);
			target[3] = (byte) ordered;
		}
	}

}
