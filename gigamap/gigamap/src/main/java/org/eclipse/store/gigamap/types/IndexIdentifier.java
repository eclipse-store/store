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

import org.eclipse.serializer.util.X;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Base type for index implementations.
 * 
 * @param <E> the entity type
 * @param <K> the key type
 */
public interface IndexIdentifier<E, K>
{
	/**
	 * Get the name of this index. Must be unique.
	 *
	 * @return the unique name of this index
	 */
	public String name();
	
	/**
	 * Get the key type of this index.
	 * <p>
	 * This is the type of the value the {@link Indexer} actually extracts from the entity.
	 * 
	 * @return the key type
	 */
	public Class<K> keyType();
	
	/**
	 * Retrieves the {@link Indexer} associated with this index.
	 * The {@link Indexer} is responsible for extracting keys of type {@code K}
	 * from entities of type {@code E}, utilizing the indexing logic defined.
	 *
	 * @return the {@link Indexer} associated with this index
	 * @throws UnsupportedOperationException if no indexing logic is defined
	 */
	public default Indexer<? super E, K> indexer()
	{
		throw new UnsupportedOperationException("No indexing logic given.");
	}
	
	/**
	 * Returns <code>true</code> if the given key is equal to the resulting key of this indexing logic.
	 * 
	 * @param entity the entity to extract the key of
	 * @param key the key to test against
	 * @return <code>true</code> if the key is equals to the extracted one
	 */
	public default boolean test(final E entity, final K key)
	{
		final Indexer<? super E, K> indexer = this.indexer();
		final K                  indexedKey = indexer.index(entity);
		return indexer.hashEqualator().equal(indexedKey, key);
	}
	
	/**
	 * Creates an equality condition for the given key. This condition checks whether
	 * the key extracted by this index is equal to the specified key.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param key the key to compare for equality
	 * @return a new condition representing the equality check for the given key
	 */
	public default <S extends E> Condition<S> is(final K key)
	{
		return new Condition.Equals<>(this, key);
	}
	
	/**
	 * Creates a condition that checks if the key extracted by this index is null.
	 *
	 * @return a new condition that represents a null check.
	 */
	public default <S extends E> Condition<S> isNull()
	{
		return this.is((K)null);
	}
	
	/**
	 * Creates a negated condition for the given key. This condition checks whether
	 * the key extracted by this index is not equal to the specified key.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param key the key to compare for inequality
	 * @return a new condition representing the inequality check for the given key
	 */
	public default <S extends E> Condition<S> not(final K key)
	{
		return new Condition.Not<>(this.is(key));
	}
	
	/**
	 * Creates a condition that checks if the key extracted by this index is not null.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @return a new condition representing the non-nullity check.
	 */
	public default <S extends E> Condition<S> notNull()
	{
		return this.not((K)null);
	}
	
	/**
	 * Creates a condition based on the key extracted from the provided sample entity.
	 * The condition checks for similarity using the indexing logic of the current index.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param sample the entity to extract the key from for similarity comparison
	 * @return a new condition that checks for similarity based on the extracted key
	 */
	public default <S extends E> Condition<S> like(final E sample)
	{
		final K key = this.indexer().index(sample);
		
		return this.is(key);
	}
	
	/**
	 * Creates a condition based on the key extracted from the provided sample entity.
	 * The condition checks for dissimilarity using the indexing logic of the current index.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param sample the entity to extract the key from for dissimilarity comparison
	 * @return a new condition that checks for dissimilarity based on the extracted key
	 */
	public default <S extends E> Condition<S> unlike(final E sample)
	{
		final K key = this.indexer().index(sample);
		
		return this.not(key);
	}
	
	/**
	 * Synonym for {@link #like(Object)}.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param sample the entity to extract the key from for similarity comparison
	 * @return a new condition that checks for similarity based on the extracted key
	 */
	public default <S extends E> Condition<S> byExample(final E sample)
	{
		return this.like(sample);
	}
	
	/**
	 * Synonym for {@link #unlike(Object)}.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param sample the entity to extract the key from for dissimilarity comparison
	 * @return a new condition that checks for dissimilarity based on the extracted key
	 */
	public default <S extends E> Condition<S> notByExample(final E sample)
	{
		return this.unlike(sample);
	}
	
	/**
	 * Creates a condition that checks if the key extracted by this index is contained
	 * within the list of keys provided by the given GigaQuery object.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param keys the GigaQuery object containing the list of keys to compare to
	 * @return a new condition representing the containment check for the provided keys
	 */
	@SuppressWarnings("unchecked")
	public default <S extends E> Condition<S> in(final GigaQuery<K> keys)
	{
		return new Condition.In<>(this, (K[])keys.toList().toArray());
	}
	
	/**
	 * Creates a condition that checks if the key extracted by this index is contained
	 * within the specified keys.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param keys the array of keys to compare to
	 * @return a new condition representing the containment check for the provided keys
	 */
	@SuppressWarnings("unchecked")
	public default <S extends E> Condition<S> in(final K... keys)
	{
		return new Condition.In<>(this, keys);
	}
	
	/**
	 * Creates a condition that checks if the key extracted by this index is not contained
	 * within the specified keys.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param keys the array of keys to compare to
	 * @return a new condition representing the negated containment check for the provided keys
	 */
	@SuppressWarnings("unchecked")
	public default <S extends E> Condition<S> notIn(final K... keys)
	{
		return new Condition.Not<>(this.in(keys));
	}
	
	/**
	 * Creates a condition that checks if the key extracted by this index is not contained
	 * within the list of keys provided by the given GigaQuery object.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param keys the GigaQuery object containing the list of keys to compare to
	 * @return a new condition representing the negated containment check for the provided keys
	 */
	public default <S extends E> Condition<S> notIn(final GigaQuery<K> keys)
	{
		return new Condition.Not<>(this.in(keys));
	}
	
	/**
	 * Creates a condition that evaluates whether the key extracted by this index satisfies
	 * the specified predicate.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param keyPredicate the predicate to evaluate the extracted key against
	 * @return a new condition representing the predicate check for the extracted key
	 */
	public default <S extends E> Condition<S> is(final Predicate<? super K> keyPredicate)
	{
		return new Condition.Searched<>(this, keyPredicate);
	}
	
	/**
	 * Resolves and retrieves the internal bitmap index associated with this identifier
	 * for the given set of bitmap indices. If the matching index is not found, an exception is thrown.
	 *
	 * @param <S> the specific subtype of the entity this operation applies to, extending the base entity type
	 * @param indices the set of bitmap indices to resolve from
	 * @return the resolved internal bitmap index for this identifier
	 * @throws RuntimeException if the corresponding index cannot be found in the provided indices
	 */
	public default <S extends E> BitmapIndex.Internal<S, K> resolveFor(final BitmapIndices.Internal<S> indices)
	{
		final BitmapIndex.Internal<S, K> index = indices.internalGet(this.keyType(), this.name());
		if(index != null)
		{
			return index;
		}
		
		throw new IllegalArgumentException(
			GigaMap.class.getSimpleName() + " " + indices.parentMap()
			+ " does not have an index defined with name \"" + this.name() + "\"."
		);
	}
	
	/**
	 * Resolves and retrieves a list of keys from the provided GigaMap based on
	 * the internal bitmap index associated with this index identifier.
	 *
	 * @param <S> the specific subtype of the entity this operation applies to, extending the base entity type
	 * @param map the GigaMap containing the entities and the corresponding bitmap index
	 * @return a list of keys resolved from the provided GigaMap
	 */
	public default <S extends E> List<K> resolveKeys(final GigaMap<S> map)
	{
		final List<K> keys = new ArrayList<>();
		this.resolveFor((BitmapIndices.Internal<S>)map.index().bitmap()).iterateKeys(keys::add);
		return keys;
	}
	
	/**
	 * Creates a new {@code IndexIdentifier} instance with the specified index name and key type.
	 *
	 * @param <E> the type of entity this index will operate on
	 * @param <K> the type of key extracted by this index
	 * @param indexName the unique name for the index
	 * @param keyType the class representing the type of the key
	 * @return a new {@code IndexIdentifier} instance
	 */
	public static <E, K> IndexIdentifier<E, K> New(final String indexName, final Class<K> keyType)
	{
		return new Default<>(
			X.notNull(indexName),
			X.notNull(keyType)
		);
	}
	
	public final class Default<E, K> implements IndexIdentifier<E, K>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final String indexName;
		private final Class<K> keyType;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default(final String indexName, final Class<K> keyType)
		{
			super();
			this.indexName = indexName;
			this.keyType = keyType;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public String name()
		{
			return this.indexName;
		}
		
		@Override
		public Class<K> keyType()
		{
			return this.keyType;
		}
						
	}
	
}
