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
 * Indexing logic for {@link Long} keys.
 * <p>
 * It is optimized for low-cardinality indices, for high-cardinality use {@link BinaryIndexerLong}.
 * 
 * @param <E> the entity type
 * 
 * @see IndexerNumber
 */
public interface IndexerLong<E> extends IndexerNumber<E, Long>
{

	/**
	 * Abstract base class for a {@link Long} key {@link Indexer}.
	 * 
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends IndexerComparing.Abstract<E, Long> implements IndexerLong<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		public final Class<Long> keyType()
		{
			return Long.class;
		}
		
		@Override
		public final Long index(final E entity)
		{
			return this.getLong(entity);
		}
		
		protected abstract Long getLong(E entity);

	}
	
}
