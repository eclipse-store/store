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

import org.eclipse.serializer.hashing.HashEqualator;


/**
 * Represents an indexer that supports composite keys comprising multiple elements.
 * This interface extends the base {@link Indexer} functionality to work with composite keys.
 * <p>
 * This indexer is also well suited for high cardinality, like the {@link BinaryIndexer}.
 *
 * @param <E> the type of the entity being indexed
 * @param <KS> the type of the composite key
 */
public interface CompositeIndexer<E, KS> extends Indexer<E, KS>
{
	@Override
	public Class<KS> keyType();
	
	/**
	 * Indexes an entity and associates it with a carrier. This method uses an overloaded
	 * implementation to perform the indexing operation.
	 *
	 * @param entity the source entity to be indexed
	 * @param carrier the carrier associated with the indexed value
	 * @return the indexed value derived from the entity
	 */
	public default KS index(final E entity, final KS carrier)
	{
		return this.index(entity);
	}
	
	@Override
	public <T extends E> BitmapIndex.Internal<T, KS> createFor(final BitmapIndices<T> parent);
	
	public default <K> HashEqualator<? super K> provideSubIndexHashEqualator(final int subIndexPosition)
	{
		return Indexer.defaultHashEqualator();
	}
	
	
	public abstract class Abstract<E, KS> extends Indexer.Abstract<E, KS> implements CompositeIndexer<E, KS>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		public abstract KS index(E entity);
		
		@Override
		public abstract KS index(E entity, KS carrier);
		
	}
	
}
