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
 * Byte-decomposed composite indexer for {@link Long} keys.
 *
 * @param <E> the entity type
 *
 * @see ByteIndexerNumber
 */
public interface ByteIndexerLong<E> extends ByteIndexerNumber<E, Long>
{
	/**
	 * Abstract base class for a {@link Long} key {@link ByteIndexerNumber}.
	 *
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends ByteIndexerNumber.Abstract<E, Long> implements ByteIndexerLong<E>
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

		protected abstract Long getLong(E entity);

		@Override
		protected final int byteCount()
		{
			return Long.BYTES;
		}

		@Override
		protected final void toOrderedBytes(final Long value, final byte[] target)
		{
			final long ordered = value ^ 0x8000000000000000L;
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
