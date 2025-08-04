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

import org.eclipse.serializer.collections.BulkList;
import org.eclipse.store.gigamap.annotations.Index;

/**
 * Indexing logic for multi-value keys, like collections or arrays.
 * 
 * @param <E> the entity type
 * @param <K> the key component type
 */
public interface IndexerMultiValue<E, K> extends Indexer<E, K>
{
	/**	 * Extract the value of the entity which is stored in the {@link Index}.
	 *
	 * @param entity the source entity
	 * @return the indexed value
	 */
	public Iterable<? extends K> indexEntityMultiValue(final E entity);
	
	/**
	 * Creates an equality condition which checks against a collection of keys.
	 * <p>
	 * It provides a concise way to combine multiple conditions using AND logic
	 * without having to explicitly write out each condition separately.
	 * 
	 * @param keys the keys to compare to
	 * @return a new condition
	 */
	@SuppressWarnings("unchecked")
	public default <S extends E> Condition<S> all(final GigaQuery<K> keys)
	{
		return new Condition.All<>(this, (K[])keys.toList().toArray());
	}
	
	/**
	 * Creates an equality condition which checks against a collection of keys.
	 * <p>
	 * It provides a concise way to combine multiple conditions using AND logic
	 * without having to explicitly write out each condition separately.
	 * 
	 * @param keys the keys to compare to
	 * @return a new condition
	 */
	@SuppressWarnings("unchecked")
	public default <S extends E> Condition<S> all(final K... keys)
	{
		return new Condition.All<>(this, keys);
	}
	
	
	/**
	 * Abstract base class for a multi value {@link Indexer}.
	 * 
	 * @param <E> the entity type
	 * @param <K> the key component type
	 */
	public abstract class Abstract<E, K> extends Indexer.Abstract<E, K> implements IndexerMultiValue<E, K>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		public final K index(final E entity)
		{
			throw new UnsupportedOperationException("use indexEntityMultiValue() instead");
		}
		
		@Override
		public <S extends E> Condition<S> like(final E sample)
		{
			final BulkList<Condition<S>> conditions = new BulkList<>();
			for(final K key : this.indexEntityMultiValue(sample))
			{
				conditions.add(this.is(key));
			}
			return new Condition.And<>(conditions);
		}
		
		@Override
		public <S extends E> Condition<S> unlike(final E sample)
		{
			return new Condition.Not<>(this.like(sample));
		}
		
	}
	
}
