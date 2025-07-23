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

import java.util.UUID;

/**
 * Indexing logic for {@link UUID} keys.
 *
 * @param <E> the entity type
 *
 * @see Indexer
 */
public interface BinaryIndexerUUID<E> extends BinaryCompositeIndexer<E>
{
	/**
	 * Creates a condition that matches entities where the UUID is equal to the specified UUID.
	 *
	 * @param <S> a subtype of the entity type E
	 * @param other the UUID to compare against
	 * @return a condition representing the equality check for the specified UUID
	 */
	public <S extends E> Condition<S> is(UUID other);
	

	/**
	 * Abstract base class for a {@link UUID} key {@link Indexer}.
	 * 
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends AbstractSingleValueFixedSize<E, UUID> implements BinaryIndexerUUID<E>
	{
		protected Abstract()
		{
			super();
		}
		
		protected abstract UUID getUUID(E entity);
		
		protected UUID getValue(final E entity)
		{
			return this.getUUID(entity);
		}
		
		@Override
		protected int compositeSize()
		{
			return 2;
		}
		
		@Override
		protected void fillCarrier(final UUID uuid, final long[] carrier)
		{
			carrier[0] = uuid.getMostSignificantBits();
			carrier[1] = uuid.getLeastSignificantBits();
		}
		
		@Override
		public <S extends E> Condition<S> is(final UUID other)
		{
			return this.isValue(other);
		}
		
	}
	
}
