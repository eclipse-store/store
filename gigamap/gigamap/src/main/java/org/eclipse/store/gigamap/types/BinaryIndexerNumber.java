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
 * An interface providing indexing capabilities for entities using numerical keys.
 * This interface extends {@link BinaryIndexer} which is optimized for high-cardinality indices.
 *
 * @param <E> the type of entities being indexed
 * @param <K> the numerical type of the key, which must extend {@link Number}
 */
public interface BinaryIndexerNumber<E, K extends Number> extends BinaryIndexer<E>
{
	/**
	 * Creates an equality condition for the given key. This condition checks whether
	 * the key extracted by this index is equal to the specified key.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param key the key to compare for equality
	 * @return a new condition representing the equality check for the given key
	 */
	public <S extends E> Condition<S> is(K key);
	
	/**
	 * Creates a negated condition for the given key. This condition checks whether
	 * the key extracted by this index is not equal to the specified key.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param key the key to compare for inequality
	 * @return a new condition representing the inequality check for the given key
	 */
	public <S extends E> Condition<S> not(K key);
	
	/**
	 * Creates a condition that checks if the key extracted by this index is contained
	 * within the specified keys.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param keys the array of keys to compare to
	 * @return a new condition representing the containment check for the provided keys
	 */
	@SuppressWarnings("unchecked")
	public <S extends E> Condition<S> in(K... keys);
	
	/**
	 * Creates a condition that checks if the key extracted by this index is not contained
	 * within the specified keys.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param keys the array of keys to compare to
	 * @return a new condition representing the negated containment check for the provided keys
	 */
	@SuppressWarnings("unchecked")
	public <S extends E> Condition<S> notIn(final K... keys);
	
	
	public abstract class Abstract<E, K extends Number> extends BinaryIndexer.Abstract<E> implements BinaryIndexerNumber<E, K>
	{
		protected Abstract()
		{
			super();
		}
		
		protected abstract K getNumber(E entity);
		
		protected long toLong(final K number)
		{
			return number.longValue();
		}
		
		@Override
		public long indexBinary(final E entity)
		{
			return this.toLong(this.getNumber(entity));
		}
		
		@Override
		public <S extends E> Condition<S> is(final K key)
		{
			return super.is(this.toLong(key));
		}
		
		@Override
		public <S extends E> Condition<S> not(final K key)
		{
			return new Condition.Not<>(this.is(key));
		}
		
		@SafeVarargs
		@Override
		public final <S extends E> Condition<S> in(final K... keys)
		{
			final Long[] longKeys = new Long[keys.length];
			for(int i = 0; i < keys.length; i++)
			{
				longKeys[i] = this.toLong(keys[i]);
			}
			return super.in(longKeys);
		}
		
		@SafeVarargs
		@Override
		public final <S extends E> Condition<S> notIn(final K... keys)
		{
			return new Condition.Not<>(this.in(keys));
		}
		
	}
	
}
