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
 * Byte-decomposed composite indexer for {@link Short} keys.
 *
 * @param <E> the entity type
 *
 * @see ByteIndexerNumber
 */
public interface ByteIndexerShort<E> extends ByteIndexerNumber<E, Short>
{
	/**
	 * Abstract base class for a {@link Short} key {@link ByteIndexerNumber}.
	 *
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends ByteIndexerNumber.Abstract<E, Short> implements ByteIndexerShort<E>
	{
		protected Abstract()
		{
			super();
		}

		@Override
		protected final Short getNumber(final E entity)
		{
			return this.getShort(entity);
		}

		protected abstract Short getShort(E entity);

		@Override
		protected final int byteCount()
		{
			return Short.BYTES;
		}

		@Override
		protected final void toOrderedBytes(final Short value, final byte[] target)
		{
			final int ordered = value ^ 0x8000;
			target[0] = (byte)(ordered >>> 8);
			target[1] = (byte) ordered;
		}
	}

}
