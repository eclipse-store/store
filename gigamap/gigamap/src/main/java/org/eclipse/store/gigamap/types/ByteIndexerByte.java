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
 * Byte-decomposed composite indexer for {@link Byte} keys.
 *
 * @param <E> the entity type
 *
 * @see ByteIndexerNumber
 */
public interface ByteIndexerByte<E> extends ByteIndexerNumber<E, Byte>
{
	/**
	 * Abstract base class for a {@link Byte} key {@link ByteIndexerNumber}.
	 *
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends ByteIndexerNumber.Abstract<E, Byte> implements ByteIndexerByte<E>
	{
		protected Abstract()
		{
			super();
		}

		@Override
		protected final Byte getNumber(final E entity)
		{
			return this.getByte(entity);
		}

		protected abstract Byte getByte(E entity);

		@Override
		protected final int byteCount()
		{
			return 1;
		}

		@Override
		protected final void toOrderedBytes(final Byte value, final byte[] target)
		{
			target[0] = (byte)(value ^ 0x80);
		}
	}

}
