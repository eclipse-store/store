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
 * <p>
 * <b>Restriction:</b> {@code null} is not supported as an index key and is rejected with an
 * {@link IllegalArgumentException} - both when extracted from an entity during indexing and when passed to
 * {@code is}, {@code not}, {@code in}, {@code notIn}, {@code isNull} or {@code notNull}. A binary index encodes
 * the key directly into a single {@code long} bit pattern whose every bit position is an index entry, so unlike
 * the composite binary indexers ({@link BinaryIndexerString}, {@link BinaryIndexerUUID}) it has no spare slot for
 * a null sentinel, and unlike the sub-64-bit numeric types {@link BinaryIndexerLong} has no unused bit pattern
 * left either. To index a nullable numeric field, use the hashing {@link IndexerNumber} variants
 * (for example {@link IndexerLong}), which support {@code isNull()}/{@code notNull()} and keep {@code 0}
 * distinct from {@code null}.
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
	 * @param key the key to compare for equality, must not be null
	 * @return a new condition representing the equality check for the given key
	 * @throws IllegalArgumentException if the key is {@code null}
	 */
	public <S extends E> Condition<S> is(K key);
	
	/**
	 * Creates a negated condition for the given key. This condition checks whether
	 * the key extracted by this index is not equal to the specified key.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param key the key to compare for inequality, must not be null
	 * @return a new condition representing the inequality check for the given key
	 * @throws IllegalArgumentException if the key is {@code null}
	 */
	public <S extends E> Condition<S> not(K key);
	
	/**
	 * Creates a condition that checks if the key extracted by this index is contained
	 * within the specified keys.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param keys the array of keys to compare to, none of which may be null
	 * @return a new condition representing the containment check for the provided keys
	 * @throws IllegalArgumentException if any of the keys is {@code null}
	 */
	@SuppressWarnings("unchecked")
	public <S extends E> Condition<S> in(K... keys);
	
	/**
	 * Creates a condition that checks if the key extracted by this index is not contained
	 * within the specified keys.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param keys the array of keys to compare to, none of which may be null
	 * @return a new condition representing the negated containment check for the provided keys
	 * @throws IllegalArgumentException if any of the keys is {@code null}
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
			// must be validated before #toLong, whose unboxing would turn a null key into a raw NullPointerException.
			final K number = this.getNumber(entity);
			BinaryIndexer.Static.validate(number, this);
			
			return this.toLong(number);
		}
		
		@Override
		public <S extends E> Condition<S> is(final K key)
		{
			BinaryIndexer.Static.validate(key, this);
			
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
				BinaryIndexer.Static.validate(keys[i], this);
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
