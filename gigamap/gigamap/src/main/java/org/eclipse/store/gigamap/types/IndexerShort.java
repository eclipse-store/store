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
 * Indexing logic for {@link Short} keys.
 * <p>
 * It is optimized for low-cardinality indices, for high-cardinality use {@link BinaryIndexerShort}.
 * 
 * @param <E> the entity type
 * 
 * @see IndexerNumber
 */
public interface IndexerShort<E> extends IndexerNumber<E, Short>
{

	/**
	 * Abstract base class for a {@link Short} key {@link Indexer}.
	 * 
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends IndexerComparing.Abstract<E, Short> implements IndexerShort<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		public final Class<Short> keyType()
		{
			return Short.class;
		}
		
		@Override
		public final Short index(final E entity)
		{
			return this.getShort(entity);
		}
		
		protected abstract Short getShort(E entity);

	}
	
}
