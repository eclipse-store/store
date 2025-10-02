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
 * Indexing logic for {@link Byte} keys.
 * <p>
 * It is optimized for low-cardinality indices, for high-cardinality use {@link BinaryIndexerByte}.
 *
 * @param <E> the entity type
 * 
 * @see IndexerNumber
 */
public interface IndexerByte<E> extends IndexerNumber<E, Byte>
{

	/**
	 * Abstract base class for a {@link Byte} key {@link Indexer}.
	 * 
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends IndexerComparing.Abstract<E, Byte> implements IndexerByte<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		public final Class<Byte> keyType()
		{
			return Byte.class;
		}
		
		@Override
		public final Byte index(final E entity)
		{
			return this.getByte(entity);
		}
		
		protected abstract Byte getByte(E entity);

	}

}
