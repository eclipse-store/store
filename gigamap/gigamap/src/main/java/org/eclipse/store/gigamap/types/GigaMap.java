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

import org.eclipse.store.gigamap.exceptions.ConstraintViolationException;
import org.eclipse.store.gigamap.types.GigaQuery.ConditionBuilder;
import org.eclipse.store.gigamap.types.IterationThreadProvider.IterationLogicProvider;
import org.eclipse.serializer.branching.ThrowBreak;
import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.ConstList;
import org.eclipse.serializer.collections.HashEnum;
import org.eclipse.serializer.collections.interfaces.Sized;
import org.eclipse.serializer.collections.types.XEnum;
import org.eclipse.serializer.collections.types.XGettingEnum;
import org.eclipse.serializer.collections.types.XIterable;
import org.eclipse.serializer.equality.Equalator;
import org.eclipse.serializer.equality.IdentityEqualator;
import org.eclipse.serializer.hashing.XHashing;
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.*;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.util.X;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.eclipse.serializer.util.X.notNull;

/**
 * An indexed collection designed to cope with vast amounts of data.
 * <p>
 * It stores the data in nested, lazy-loaded segments backed by indices.
 * This allows for efficient querying of data without the need to load all of it into memory.
 * Instead, only the segments required to return the resulting entities are loaded on demand.
 * With this approach, GigaMap can handle billions of entities with exceptional performance.
 * <p>
 * Compared to other collections, the main advantage of GigaMap is its ability to query data
 * without the need to load all the data first. This makes it a highly efficient and flexible
 * solution for managing, querying, and storing large quantities of data.
 * <p>
 * The indices and queries are created with a Java API, so learning another query language is unnecessary.
 * <p>
 * GigaMap does <strong>not</strong> allow <code>null</code> entries.
 * <p>
 * Equality depends on the given {@link #equalator()}. By default, identity equality is used.
 * If you want value equality instead, you can use {@link XHashing#hashEqualityValue()} in the constructor methods,
 * or {@link Builder#withValueEquality()}.
 *
 * @param <E> the type of entities in this collection
 */
public interface GigaMap<E> extends XIterable<E>, Sized, Iterable<E>
{
	/**
     * Returns the total number of elements in this collection.
     *
     * @return the total number of elements in this collection
     */
	@Override
	public long size();
	
	/**
     * Returns the highest used id.
     *
     * @return the highest used id
     */
	public long highestUsedId();

	/**
     * Returns {@code true} if this collection contains no elements.
     *
     * @return {@code true} if this collection contains no elements
     */
	@Override
	public boolean isEmpty();

	/**
	 * Returns the element to which the specified id is mapped.
	 * 
	 * @param entityId the id of the requested element
	 * @return the element with the requested id or <code>null</code>
	 */
	public E get(long entityId);
		
	/**
	 * Adds the specified element to this collection.
	 * <p>
	 * Null values are not allowed.
	 * 
	 * @param element the element to add, not <code>null</code>
	 * @return the assigned id
	 * @throws IllegalArgumentException if element is <code>null</code>
	 */
	public long add(E element);
	
	/**
	 * Adds all elements to this collection.
	 * <p>
	 * Null values are not allowed
	 * 
	 * @param elements the elements to add
	 * @return the last assigned id
	 * @throws IllegalArgumentException if an element is <code>null</code>
	 */
	public long addAll(Iterable<? extends E> elements);
	
	/**
	 * Adds all elements to this collection.
	 * <p>
	 * Null values are not allowed
	 * 
	 * @param elements the elements to add
	 * @return the last assigned id
	 * @throws IllegalArgumentException if an element is <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default long addAll(final E... elements)
	{
		return this.addAll(ConstList.New(elements));
	}
	
	/**
	 * Returns the element to which the specified id is mapped, if it is already loaded.
	 * 
	 * @param entityId the id of the requested element
	 * @return the element with the requested id, or null if it isn't loaded
	 */
	public E peek(long entityId);
	
	/**
	 * Removes the entitiy mapped to the specified id.
	 * 
	 * @param entityId the id of the element to be deleted
	 * @return the deleted element, or <code>null</code> if none was deleted
	 */
	public E removeById(long entityId);
	
	/**
	 * Removes the specified entity if present in this collection and returns its previously mapped id.
	 * <p>
	 * In order for this method to work, at least one bitmap index is needed. If no index is present an
	 * {@link IllegalStateException} will be thrown.
	 * <p>
	 * To get the best performance for this operation is the use of an identity index.
	 * See {@link BitmapIndices#setIdentityIndices(IndexIdentifier...)}.
	 * 
	 * 
	 * @param entity the entity to be removed
	 * @return the previously mapped id of the entity, or -1 if none was removed
	 * @throws IllegalStateException if no bitmap index is present
	 */
	public long remove(E entity);

	/**
	 * Removes the specified entity if present in this collection and returns its previously mapped id.
	 * 
	 * @param entity the entity to be removed
	 * @param indexToUse the index to distinctly identify the entity
	 * @return the previously mapped id of the entity, or -1 if none was removed
	 */
	public default long remove(final E entity, final IndexIdentifier<E, ?> indexToUse)
	{
		return this.remove(entity, X.Constant(indexToUse));
	}

	/**
	 * Removes the specified entity if present in this collection and returns its previously mapped id.
	 * 
	 * @param entity the entity to be removed
	 * @param indicesToUse the indices to distinctly identify the entity
	 * @return the previously mapped id of the entity, or -1 if none was removed
	 */
	@SuppressWarnings("unchecked")
	public default long remove(final E entity, final IndexIdentifier<E, ?>... indicesToUse)
	{
		return this.remove(entity, X.ConstList(indicesToUse));
	}
	
	/**
	 * Removes the specified entity if present in this collection and returns its previously mapped id.
	 * 
	 * @param entity the entity to be removed
	 * @param indicesToUse the indices to distinctly identify the entity
	 * @return the previously mapped id of the entity, or -1 if none was removed
	 */
	public long remove(E entity, Iterable<? extends IndexIdentifier<E, ?>> indicesToUse);
	
	/**
	 * Removes all entities, effectively clearing all data from this collection.
	 */
	public void removeAll();
	
	/**
	 * Synonym for {@link #removeAll()}.
	 */
	public default void clear()
	{
		this.removeAll();
	}
	
	/**
	 * Checks if this {@link GigaMap} is in a read-only state.
	 *
	 * @return true if this {@link GigaMap} is read-only, false otherwise.
	 */
	public boolean isReadOnly();
	
	/**
	 * Marks this {@link GigaMap} as read-only, indicating that
	 * it cannot be modified further.
	 */
	public void markReadOnly();
	
	/**
	 * Removes the read-only status from this {@link GigaMap}.
	 */
	public void unmarkReadOnly();
	
	/**
	 * Removes all read-only marks currently set.
	 * This operation is intended to reset or clear any read-only status
	 * that may have been applied to this {@link GigaMap}.
	 *
	 * @return true if the read-only marks were successfully cleared, false otherwise
	 */
	public boolean clearReadOnlyMarks();
	
	/**
	 * Replaces an entity if present in this collection, updates the indices accordingly,
	 * and returns the old entity mapped to the specified id.
	 * 
	 * @param entityId the entity id to replace
	 * @param entity the new entity
	 * @return the old entity
	 * @throws IllegalArgumentException if the entityId is not found
	 */
	public E set(long entityId, E entity);
	
	/**
	 * Replaces the specified entity if present with a different one, updates the indices accordingly,
	 * and returns its mapped id.
	 * <p>
	 * In order for this method to work, at least one bitmap index is needed. If no index is present an
	 * {@link IllegalStateException} will be thrown.
	 * <p>
	 * To get the best performance for this operation is the use of an identity index.
	 * See {@link BitmapIndices#setIdentityIndices(IndexIdentifier...)}.
	 * 
	 * 
	 * @param current the entity to be removed
	 * @param replacement the new entity instance
	 * @return the mapped id of the entity
	 * @throws IllegalStateException if no bitmap index is present
	 * @throws IllegalArgumentException if current and replacement are the same object
	 */
	public long replace(E current, E replacement);
	
	/**
	 * Updates the specified entity and the indices accordingly.
	 * <p>
	 * In order for this method to work, at least one bitmap index is needed. If no index is present an
	 * {@link IllegalStateException} will be thrown.
	 * <p>
	 * To get the best performance for this operation is the use of an identity index.
	 * See {@link BitmapIndices#setIdentityIndices(IndexIdentifier...)}.
	 * 
	 * @param current the entity to be updated
	 * @param logic the update logic to be executed
	 * @return the updated entity
	 * @throws IllegalStateException if no bitmap index is present
	 */
	public default E update(final E current, final Consumer<? super E> logic)
	{
		this.apply(current, e ->
		{
			logic.accept(e);
			return null;
		});

		return current;
	}
	
	/**
	 * Applies the specified logic for the given entity and updates the indices accordingly.
	 * <p>
	 * In order for this method to work, at least one bitmap index is needed. If no index is present an
	 * {@link IllegalStateException} will be thrown.
	 * <p>
	 * To get the best performance for this operation is the use of an identity index.
	 * See {@link BitmapIndices#setIdentityIndices(IndexIdentifier...)}.
	 * 
	 * @param current the entity to be updated
	 * @param logic the logic to be executed
	 * @return the result of the given logic
	 * @throws IllegalStateException if no bitmap index is present
	 */
	public <R> R apply(E current, Function<? super E, R> logic);
	
	/**
	 * Releases all strong references to on-demand loaded data.
	 */
	public void release();
	
	/**
	 * Returns the GigaIndices instance that represents the indices structure for this {@link GigaMap}.
	 *
	 * @return the GigaIndices instance associated with this {@link GigaMap}.
	 */
	public GigaIndices<E> index();
	
	/**
	 * Retrieves the constraints associated with this {@link GigaMap}.
	 *
	 * @return a GigaConstraints object containing the constraints.
	 */
	public GigaConstraints<E> constraints();
	
	/**
	 * Registers index categories into the index management system of the GigaMap.
	 *
	 * @param indexCategory the index category to be registered, containing definitions of indices
	 *                      and their corresponding groups.
	 * @return the current instance of GigaMap for method chaining.
	 */
	public default GigaMap<E> registerIndices(final IndexCategory<E, ? extends IndexGroup<E>> indexCategory)
	{
		this.index().register(indexCategory);
		
		return this;
	}
	
	/**
	 * Provides an iterator for traversing elements of the collection.
	 *
	 * @return a GigaIterator instance for iterating over the elements
	 */
	@Override
	public GigaIterator<E> iterator();
	
	/**
	 * Iterates over all elements.
	 * <p>
	 * Keep in mind that this can result in a very expensive operation, depending on the overall count of elements.
	 * 
	 * @param <I> type of iterator
	 * @param iterator the consumer of elements
	 * @return the given iterator
	 */
	@Override
	public <I extends Consumer<? super E>> I iterate(I iterator);

	/**
	 * Iterates over all elements, handing over the entity ids as well.
	 * <p>
	 * Keep in mind that this can result in a very expensive operation, depending on the overall count of elements.
	 * 
	 * @param <I> type of consumer
	 * @param consumer the consumer of elements
	 * @return the given consumer
	 */
	public <I extends EntryConsumer<? super E>> I iterateIndexed(I consumer);
	
	/**
	 * Returns a String representation of this GigaMap, displaying up to <code>limit</code> elements.
	 * 
	 * @param limit maximum amount of elements in the resulting String (&gt;=0)
	 * @return a String representation of this GigaMap
	 */
	public default String toString(final int limit)
	{
		return this.toString(0, limit);
	}
	
	/**
	 * Returns a String representation of this GigaMap, displaying up to <code>limit</code> elements, starting at <code>offset</code>.
	 * 
	 * @param offset the first element (&gt;=0)
	 * @param limit maximum amount of elements in the resulting String (&gt;=0)
	 * @return a String representation of this GigaMap
	 */
	public default String toString(final int offset, final int limit)
	{
		if(offset < 0)
		{
			throw new IllegalArgumentException("offset can't be negative");
		}
		if(limit < 0)
		{
			throw new IllegalArgumentException("limit can't be negative");
		}
		if(limit == 0)
		{
			return "[]";
		}
		
		final List<E> list = new ArrayList<>(Math.min(1024, limit));
		try(final GigaIterator<E> it = this.iterator())
		{
			int i = 0;
			while(++i <= offset && it.hasNext())
			{
				it.next();
			}
			i = 0;
			while(++i <= limit && it.hasNext())
			{
				list.add(it.next());
			}
		}
		
		return list.stream()
			.map(String::valueOf)
			.collect(Collectors.joining(", ", "[", "]"))
		;
	}
	
	/**
	 * Creates an empty query, meaning without any given condition.
	 * <p>
	 * Executing this would return a result with all existing elements.
	 * 
	 * @return a new query object
	 */
	public default GigaQuery<E> query()
	{
		return this.query(IterationThreadProvider.None());
	}
	
	/**
	 * Creates an empty query, meaning without any given condition.
	 * <p>
	 * Executing this would return a result with all existing elements.
	 * 
	 * @param threadProvider a custom thread provider
	 * @return a new query object
	 */
	public GigaQuery<E> query(IterationThreadProvider threadProvider);
		
	/**
	 * Creates a condition builder for a specific index, which can be used to start a query.
	 * 
	 * @param <K> the index key type
	 * @param index the index identifier to build this condition for
	 * @return a new condition builder
	 */
	public default <K> ConditionBuilder<E, K> query(final IndexIdentifier<E, K> index)
	{
		return this.query().and(index);
	}
	
	/**
	 * Creates a query for a specific index looking for a certain key.
	 * <p>
	 * This is a shortcut for <code>query(index.is(key))</code>.
	 * 
	 * @param <K> the index key type
	 * @param index the index identifier to build a condition for
	 * @param key the key to compare to
	 * @return a new query object
	 */
	public default <K> GigaQuery<E> query(final IndexIdentifier<E, K> index, final K key)
	{
		return this.query().and(index.is(key));
	}
	
	/**
	 * Creates a new query initialized with a certain condition.
	 * 
	 * @param condition the first condition for the query
	 * @return a new query object
	 */
	public default GigaQuery<E> query(final Condition<E> condition)
	{
		return this.query().and(condition);
	}
	
	/**
	 * Creates a condition builder for a specific String index, which can be used to start a query.
	 * 
	 * @param stringIndexName the String index name
	 * @return a new condition builder
	 */
	public default ConditionBuilder<E, String> query(final String stringIndexName)
	{
		return this.query().and(stringIndexName);
	}
	
	/**
	 * Creates a query for a specific String index looking for a certain key.
	 * 
	 * @param stringIndexName the String index identifier to build a condition for
	 * @param key the key to compare to
	 * @return a new query object
	 */
	public default GigaQuery<E> query(final String stringIndexName, final String key)
	{
		return this.query().and(stringIndexName, key);
	}
	
	/**
	 * Creates a condition builder for a specific index, which can be used to start a query.
	 * 
	 * @param <K> the index key type
	 * @param indexName the name of the index
	 * @param keyType the index key type
	 * @return a new condition builder
	 */
	public default <K> ConditionBuilder<E, K> query(final String indexName, final Class<K> keyType)
	{
		return this.query().and(indexName, keyType);
	}
	
	/**
	 * Creates a query for a specific index looking for a certain key.
	 * 
	 * @param <K> the index key type
	 * @param indexName the name of the index
	 * @param keyType the index key type
	 * @param key the key to compare to
	 * @return a new condition builder
	 */
	public default <K> GigaQuery<E> query(final String indexName, final Class<K> keyType, final K key)
	{
		return this.query().and(indexName, keyType, key);
	}
	
	/**
	 * Creates a query for a specific index looking for a certain key.
	 * <p>
	 * Keep in mind that the type of the index and the key have to match.
	 * 
	 * @param <K> the index key type
	 * @param indexName the name of the index
	 * @param key the key to compare to
	 * @return a new condition builder
	 */
	public default <K> GigaQuery<E> query(final String indexName, final K key)
	{
		return this.query().and(indexName, key);
	}
	
	/**
	 * Stores this {@link GigaMap} instance and implicitely all changes to its component instances like indizes, etc.
	 * <p>
	 * Note that changes to entities do NOT get stored implicitely since it is not possible to automatically
	 * track changes made to objects outside the framework.
	 * <p>
	 * <b>Note on concurrency:</b><br>
	 * Using this method guarantees concurrency safety since it locks the {@link GigaMap} instance internally.<br>
	 * Storing the instance in any other way or storing only one of its component instances (e.g. an index) does
	 * NOT guarantee this safety since it is technically impossible to acquire a lock that spans the complete storing process
	 * from inside a method that is part of that process.<br>
	 * In those cases, the responsibility shifts to the calling context's logic to handle concurrency apropriately, e.g. by
	 * enclosing the storing  with a <code>synchronized(gigaMap) {...}</code> block.
	 * <p>
	 * <b>Additional background on this:</b><br>
	 * Storing is a complex process that includes calling a dozen or more type handler store methods for dozens, potentially
	 * hundreds of component instances. Even if all those calls acquire locks, other threads are still able to
	 * mutate the gigamap or some of its component instances IN BETWEEN the calls.<br>
	 * This is the same principle as the problem why just making all methods synchronized in the old JDK collections
	 * (like Vector etc.) was not sufficient to solve concurrency for all cases: race conditions could still happen
	 * IN BETWEEN the synchronized calls.<br>
	 * The only way to fully and correctly handle concurrency situations that are created on the application level
	 * is by handling them in application logic. I.e. using {@code synchronized} if and how the concurrency situation requires it
	 * <p>
	 * This method only works when a storing context was connected before,
	 * either by calling the store method of an e.g., EmbeddedStorageManager,
	 * or when this instance was restored/loaded out of an existing storage.
	 * *
	 * @return the objectId of this instance
	 * @throws IllegalStateException if this instance wasn't stored once initially by a storing context to be connected to it
	 * @see PersistenceStoring#store(Object)
	 */
	public long store();
	
	/**
	 * Provides this set {@link Equalator} instance of this {@link GigaMap}.
	 *
	 * @return the {@link Equalator} used by this {@link GigaMap}
	 */
	public Equalator<? super E> equalator();
	
	/**
	 * Creates and returns a new instance of the Builder.
	 *
	 * @param <E> the type of elements that this Builder will handle
	 * @return a new instance of the Builder
	 */
	public static <E> Builder<E> Builder()
	{
		return new Builder.Default<>();
	}
	
	/**
	 * Interface representing a builder for constructing instances of {@link GigaMap} with various types
	 * of bitmap indices and constraints.
	 *
	 * @param <E> the type of elements to be stored in the {@link GigaMap}.
	 */
	public static interface Builder<E>
	{
		/**
		 * Configures the builder with a bitmap index using the provided indexer.
		 *
		 * @param index the indexer to be used to create the bitmap index, must not be null
		 * @return the builder instance for method chaining
		 */
		public Builder<E> withBitmapIndex(Indexer<? super E, ?> index);
		
		/**
		 * Configures the builder with multiple bitmap indices using the provided indexers.
		 *
		 * @param indices an iterable collection of indexers to be used for creating bitmap indices, must not be null
		 * @return the builder instance for method chaining
		 */
		public Builder<E> withBitmapIndices(Iterable<? extends Indexer<? super E, ?>> indices);
		
		/**
		 * Configures the builder with multiple bitmap indices using the provided array of indexers.
		 *
		 * @param indices the indexers to be used for creating bitmap indices, must not be null
		 * @return the builder instance for method chaining
		 */
		@SuppressWarnings("unchecked")
		public Builder<E> withBitmapIndices(Indexer<? super E, ?>... indices);
		
		/**
		 * Configures the builder with a bitmap identity index using the provided indexer.
		 *
		 * @param index the indexer to be used to create the bitmap identity index, must not be null
		 * @return the builder instance for method chaining
		 */
		public Builder<E> withBitmapIdentityIndex(Indexer<? super E, ?> index);
		
		/**
		 * Configures the builder with multiple bitmap identity indices using the provided enumerated collection of indexers.
		 *
		 * @param indices the enumerated collection of indexers to be used for creating bitmap identity indices, must not be null
		 * @return the builder instance for method chaining
		 */
		public Builder<E> withBitmapIdentityIndices(XGettingEnum<? extends Indexer<? super E, ?>> indices);
		
		/**
		 * Configures the builder with multiple bitmap identity indices using the provided array of indexers.
		 *
		 * @param indices an array of indexers to be used for creating bitmap identity indices, must not be null
		 * @return the builder instance for method chaining
		 */
		@SuppressWarnings("unchecked")
		public Builder<E> withBitmapIdentityIndices(Indexer<? super E, ?>... indices);
		
		/**
		 * Configures the builder with a bitmap unique index using the provided indexer.
		 *
		 * @param index the indexer to be used for creating the bitmap unique index, must not be null
		 * @return the builder instance for method chaining
		 */
		public Builder<E> withBitmapUniqueIndex(Indexer<? super E, ?> index);
		
		/**
		 * Configures the builder with multiple bitmap unique indices using the provided collection of indexers.
		 *
		 * @param indices an iterable collection of indexers to be used for creating bitmap unique indices, must not be null
		 * @return the builder instance for method chaining
		 */
		public Builder<E> withBitmapUniqueIndices(Iterable<? extends Indexer<? super E, ?>> indices);
		
		/**
		 * Configures the builder with multiple bitmap unique indices using the provided array of indexers.
		 *
		 * @param indices an array of indexers to be used for creating bitmap unique indices, must not be null
		 * @return the builder instance for method chaining
		 */
		@SuppressWarnings("unchecked")
		public Builder<E> withBitmapUniqueIndices(Indexer<? super E, ?>... indices);
		
		/**
		 * Configures the builder with custom constraint using the provided {@code customConstraint}.
		 *
		 * @param customConstraint the custom constraint to configure the builder with, must not be null
		 * @return the builder instance for method chaining
		 */
		public Builder<E> withCustomConstraint(CustomConstraint<? super E> customConstraint);
		
		/**
		 * Configures the builder to use value-based equality for comparing elements.
		 *
		 * @return the builder instance for method chaining
		 */
		public Builder<E> withValueEquality();
		
		/**
		 * Configures the builder to use identity-based equality for comparing elements.
		 *
		 * @return the builder instance for method chaining
		 */
		public Builder<E> withIdentityEquality();
		
		/**
		 * Builds and returns a GigaMap instance configured with the specified parameters
		 * and indices defined in the Builder.
		 *
		 * @return a new instance of GigaMap containing the configuration and indices
		 *         provided to the Builder
		 */
		public GigaMap<E> build();
		
		
		public static class Default<E> implements Builder<E>
		{
			private boolean useValueEquality = false;
			
			private final XEnum<Indexer<? super E, ?>>       bitmapIndices     = HashEnum.New();
			private final XEnum<Indexer<? super E, ?>>       identityIndices   = HashEnum.New();
			private final XEnum<Indexer<? super E, ?>>       uniqueIndices     = HashEnum.New();
			private final XEnum<CustomConstraint<? super E>> customConstraints = HashEnum.New();
			
			Default()
			{
				super();
			}

			@Override
			public Builder<E> withBitmapIndex(final Indexer<? super E, ?> index)
			{
				this.bitmapIndices.add(index);
				return this;
			}
			
			@Override
			public Builder<E> withBitmapIndices(final Iterable<? extends Indexer<? super E, ?>> indices)
			{
				indices.forEach(this.bitmapIndices::add);
				return this;
			}
			
			@SuppressWarnings("unchecked")
			@Override
			public Builder<E> withBitmapIndices(final Indexer<? super E, ?>... indices)
			{
				this.bitmapIndices.addAll(indices);
				return this;
			}
			
			@Override
			public Builder<E> withBitmapIdentityIndex(final Indexer<? super E, ?> index)
			{
				this.identityIndices.add(index);
				return this;
			}

			@SuppressWarnings("unchecked")
			@Override
			public Builder<E> withBitmapIdentityIndices(final Indexer<? super E, ?>... indices)
			{
				this.identityIndices.addAll(indices);
				return this;
			}
			
			@Override
			public Builder<E> withBitmapIdentityIndices(final XGettingEnum<? extends Indexer<? super E, ?>> indices)
			{
				indices.forEach(this.identityIndices::add);
				return this;
			}
			
			@Override
			public Builder<E> withBitmapUniqueIndex(final Indexer<? super E, ?> index)
			{
				this.uniqueIndices.add(index);
				return this;
			}
			
			@Override
			public Builder<E> withBitmapUniqueIndices(final Iterable<? extends Indexer<? super E, ?>> indices)
			{
				indices.forEach(this.uniqueIndices::add);
				return this;
			}

			@SuppressWarnings("unchecked")
			@Override
			public Builder<E> withBitmapUniqueIndices(final Indexer<? super E, ?>... indices)
			{
				this.uniqueIndices.addAll(indices);
				return this;
			}
			
			@Override
			public Builder<E> withCustomConstraint(final CustomConstraint<? super E> customConstraint)
			{
				this.customConstraints.add(customConstraint);
				return this;
			}
			
			@Override
			public Builder<E> withValueEquality()
			{
				this.useValueEquality = true;
				return this;
			}
			
			@Override
			public Builder<E> withIdentityEquality()
			{
				this.useValueEquality = false;
				return this;
			}
			
			@Override
			public GigaMap<E> build()
			{
				final GigaMap<E> gigaMap = this.useValueEquality
					? GigaMap.New(XHashing.hashEqualityValue())
					: GigaMap.New()
				;
				
				final BitmapIndices<E> indices = gigaMap.index().bitmap();
				if(!this.bitmapIndices.isEmpty())
				{
					indices.ensureAll(this.bitmapIndices);
				}
				if(!this.identityIndices.isEmpty())
				{
					indices.ensureAll(this.identityIndices);
					indices.setIdentityIndices(this.identityIndices);
				}
				if(!this.uniqueIndices.isEmpty())
				{
					indices.addUniqueConstraints(this.uniqueIndices);
				}
				if(!this.customConstraints.isEmpty())
				{
					gigaMap.constraints().custom().addConstraints(this.customConstraints);
				}
				
				return gigaMap;
			}
				
		}
		
	}
	
	
	
	/**
	 * Creates a new empty {@link GigaMap}.
	 * 
	 * @param <E> the entity type
	 * @return a newly created GigaMap
	 */
	public static <E> GigaMap<E> New()
	{
		// creates a default distribution of 8/10/13 length exponents (256 / 1024 / 8192 lengths) for levels 1/2/3.
		return New(new DefaultEqualator<>());
	}
		
	/**
	 * Creates a new empty {@link GigaMap}.
	 * 
	 * @param <E> the entity type
	 * @param lowLevelLengthExponent exponent for the lower segment (0-20)
	 * @return a newly created GigaMap
	 */
	public static <E> GigaMap<E> New(final int lowLevelLengthExponent)
	{
		return New(
			new DefaultEqualator<>(),
			lowLevelLengthExponent
		);
	}
	
	/**
	 * Creates a new empty {@link GigaMap}.
	 * 
	 * @param <E> the entity type
	 * @param lowLevelLengthExponent exponent for the lower segment (0-20)
	 * @param midLevelLengthExponent exponent for the middle segment (8-20)
	 * @return a newly created GigaMap
	 */
	public static <E> GigaMap<E> New(
		final int lowLevelLengthExponent,
		final int midLevelLengthExponent
	)
	{
		return New(
			new DefaultEqualator<>(),
			lowLevelLengthExponent,
			midLevelLengthExponent
		);
	}
	
	/**
	 * Creates a new empty {@link GigaMap}.
	 * <p>
	 * The sum of all exponents cannot be greater than 50.
	 * 
	 * @param <E> the entity type
	 * @param lowLevelLengthExponent exponent for the lower segment (0-20)
	 * @param midLevelLengthExponent exponent for the middle segment (8-20)
	 * @param highLevelMaximumLengthExponent maximum exponent for the higher segment (8-30)
	 * @return a newly created GigaMap
	 */
	public static <E> GigaMap<E> New(
		final int lowLevelLengthExponent        ,
		final int midLevelLengthExponent        ,
		final int highLevelMaximumLengthExponent
	)
	{
		return New(
			new DefaultEqualator<>(),
			lowLevelLengthExponent,
			midLevelLengthExponent,
			highLevelMaximumLengthExponent
		);
	}
	
	/**
	 * Creates a new empty {@link GigaMap}.
	 * <p>
	 * The sum of lowLevelLengthExponent, midLevelLengthExponent and highLevelMaximumLengthExponent cannot be greater than 50.
	 * 
	 * @param <E> the entity type
	 * @param lowLevelLengthExponent exponent for the lower segment (0-20)
	 * @param midLevelLengthExponent exponent for the middle segment (8-20)
	 * @param highLevelMinimumLengthExponent minimum exponent for the higher segment (0-30)
	 * @param highLevelMaximumLengthExponent maximum exponent for the higher segment (8-30)
	 * @return a newly created GigaMap
	 */
	public static <E> GigaMap<E> New(
		final int lowLevelLengthExponent        ,
		final int midLevelLengthExponent        ,
		final int highLevelMinimumLengthExponent,
		final int highLevelMaximumLengthExponent
	)
	{
		return New(
			new DefaultEqualator<>(),
			lowLevelLengthExponent,
			midLevelLengthExponent,
			highLevelMinimumLengthExponent,
			highLevelMaximumLengthExponent
		);
	}
	
	/**
	 * Creates a new empty {@link GigaMap}.
	 * 
	 * @param <E> the entity type
	 * @param equalator custom equalator
	 * @return a newly created GigaMap
	 */
	public static <E> GigaMap<E> New(final Equalator<? super E> equalator)
	{
		// creates a default distribution of 8/10/13 length exponents (256 / 1024 / 8192 lengths) for levels 1/2/3.
		return New(
			equalator,
			Dimensions.defaultLowLevelLengthExponent()
		);
	}
		
	/**
	 * Creates a new empty {@link GigaMap}.
	 * 
	 * @param <E> the entity type
	 * @param equalator custom equalator
	 * @param lowLevelLengthExponent exponent for the lower segment (0-20)
	 * @return a newly created GigaMap
	 */
	public static <E> GigaMap<E> New(
		final Equalator<? super E> equalator             ,
		final int                  lowLevelLengthExponent
	)
	{
		return New(
			equalator,
			lowLevelLengthExponent,
			Dimensions.defaultMidLevelLengthExponent()
		);
	}
	
	/**
	 * Creates a new empty {@link GigaMap}.
	 * 
	 * @param <E> the entity type
	 * @param equalator custom equalator
	 * @param lowLevelLengthExponent exponent for the lower segment (0-20)
	 * @param midLevelLengthExponent exponent for the middle segment (8-20)
	 * @return a newly created GigaMap
	 */
	public static <E> GigaMap<E> New(
		final Equalator<? super E> equalator             ,
		final int                  lowLevelLengthExponent,
		final int                  midLevelLengthExponent
	)
	{
		return New(
			equalator,
			lowLevelLengthExponent,
			midLevelLengthExponent,
			Dimensions.defaultHighLevelMinimumLengthExponent(),
			Math.min(
				Dimensions.maximumLengthExponentSum() - midLevelLengthExponent - lowLevelLengthExponent,
				Dimensions.maximumHighLevelMaximumLengthExponent()
			)
		);
	}
	
	/**
	 * Creates a new empty {@link GigaMap}.
	 * <p>
	 * The sum of all exponents cannot be greater than 50.
	 * 
	 * @param <E> the entity type
	 * @param equalator custom equalator
	 * @param lowLevelLengthExponent exponent for the lower segment (0-20)
	 * @param midLevelLengthExponent exponent for the middle segment (8-20)
	 * @param highLevelMaximumLengthExponent maximum exponent for the higher segment (8-30)
	 * @return a newly created GigaMap
	 */
	public static <E> GigaMap<E> New(
		final Equalator<? super E> equalator                     ,
		final int                  lowLevelLengthExponent        ,
		final int                  midLevelLengthExponent        ,
		final int                  highLevelMaximumLengthExponent
	)
	{
		return New(
			equalator,
			lowLevelLengthExponent,
			midLevelLengthExponent,
			Dimensions.defaultHighLevelMinimumLengthExponent(),
			highLevelMaximumLengthExponent
		);
	}
	
	/**
	 * Creates a new empty {@link GigaMap}.
	 * <p>
	 * The sum of lowLevelLengthExponent, midLevelLengthExponent and highLevelMaximumLengthExponent cannot be greater than 50.
	 * 
	 * @param <E> the entity type
	 * @param equalator custom equalator
	 * @param lowLevelLengthExponent exponent for the lower segment (0-20)
	 * @param midLevelLengthExponent exponent for the middle segment (8-20)
	 * @param highLevelMinimumLengthExponent minimum exponent for the higher segment (0-30)
	 * @param highLevelMaximumLengthExponent maximum exponent for the higher segment (8-30)
	 * @return a newly created GigaMap
	 */
	public static <E> GigaMap<E> New(
		final Equalator<? super E> equalator                     ,
		final int                  lowLevelLengthExponent        ,
		final int                  midLevelLengthExponent        ,
		final int                  highLevelMinimumLengthExponent,
		final int                  highLevelMaximumLengthExponent
	)
	{
		/*
		 * Initial Id changed to 0 from 1.
		 * No reason found why that would have to be 1.
		 * That just creates a dummy null which complicates reading bitmap index debug output
		 * and causes top-level conditions using not to return null entities.
		 */
		
		// exponent validation is done by constructor
		return new Default<E>(
			notNull(equalator),
			lowLevelLengthExponent,
			midLevelLengthExponent,
			highLevelMinimumLengthExponent,
			highLevelMaximumLengthExponent,
			0,
			0
		);
	}
		
	// Unpersistable to force a custom type handler due to required initialization call.
	public class Default<E>
	implements   Internal<E>, BitmapResult.Resolver<E>, Unpersistable, PersistenceCommitListener
	{
		static BinaryTypeHandler<GigaMap.Default<?>> provideTypeHandler()
		{
			return BinaryHandlerGigaMapDefault.New();
		}
		
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final GigaLevel3<E>              level3     ;
		private final GigaIndices.Default<E>     indices    ;
		private final GigaConstraints.Default<E> constraints;
		private final Equalator<? super E>       equalator  ;
	
		private final int
			level1LengthExponent       ,
			level2LengthExponent       ,
			level3MinimumLengthExponent,
			level3MaximumLengthExponent
		;
		
		// size is the element count (reduced by gaps), nextId is the allocation progress (NOT reduced by gaps)
		private long baseSize, baseAddingId;
		
				
		/*
		 * CurrentLevel~ indices works like a digital clock with 3 digits:
		 * - All start at 0.
		 * - Once a lower index overflows, it resets to 0 and increments the higher index.
		 * - An overflow of a level triggers the storing of the level's array.
		 * - A level 3 overflow is a capacity exception (which will most likely never happen).
		 * 
		 * Like clockwork, literally :-D.
		 * 
		 * (And yes, this could easily be abstracted to have a configurable amount of levels instead of hardcoded 3.)
		 */
		
		private transient int level2TotalLengthExp;
		private transient int level3MaximumLength, level2Size, level1Size;
		private transient int bitMaskLevel1, bitMaskLevel2;
		private transient int level1IndexBound;
		private transient long maximumEntityId;
		
		private transient GigaLevel2<E> addingLevel2;
		private transient GigaLevel1<E> addingLevel1;

		private transient int addingLevel1Index;
		private transient int addingLevel2Index;
		private transient int addingLevel3Index;
				
		private transient Persister storeContext;
		
		private transient int readOnlyCount;
		private transient int activeIteratorCount;
		private final transient BulkList<Reading> activeIterators;
							
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default(
			final Equalator<? super E> equalator                     ,
			final int                  level1LengthExponent          ,
			final int                  level2LengthExponent          ,
			final int                  highLevelMinimumLengthExponent,
			final int                  highLevelMaximumLengthExponent,
			final long                 size                          ,
			final long                 currentId
		)
		{
			this(
				equalator,
				level1LengthExponent,
				level2LengthExponent,
				highLevelMinimumLengthExponent,
				highLevelMaximumLengthExponent,
				size,
				currentId,
				true
			);
		}
		
		Default(
			final Equalator<? super E> equalator                  ,
			final int                  level1LengthExponent       ,
			final int                  level2LengthExponent       ,
			final int                  level3MinimumLengthExponent,
			final int                  level3MaximumLengthExponent,
			final long                 size                       ,
			final long                 currentId                  ,
			final boolean              createInstances
		)
		{
			super();
			Dimensions.validateSegmentSizeDistribution(
				level1LengthExponent,
				level2LengthExponent,
				level3MinimumLengthExponent,
				level3MaximumLengthExponent
			);
			
			this.equalator                   = equalator                  ;
			this.level1LengthExponent        = level1LengthExponent       ;
			this.level2LengthExponent        = level2LengthExponent       ;
			this.level3MinimumLengthExponent = level3MinimumLengthExponent;
			this.level3MaximumLengthExponent = level3MaximumLengthExponent;
			this.baseSize                    = size                       ;
			this.baseAddingId                = currentId                  ;
			this.level1IndexBound            = 0                          ;
			
			this.initializeConfiguration();
			
			this.readOnlyCount       = 0;
			this.activeIteratorCount = 0;
			this.activeIterators     = BulkList.New();

			if(createInstances)
			{
				this.level3  = this.createLevel3();
				this.indices = new GigaIndices.Default<>(this);
				
				// mandatory since unique constraints use (=are) bitmap indices.
				final BitmapIndices<E> bitmapIndices = this.index().register(BitmapIndex.Category());
				final CustomConstraints.Default<E> cuConstraints = new CustomConstraints.Default<>(this, null);
				this.constraints = new GigaConstraints.Default<>(bitmapIndices, cuConstraints);
			}
			else
			{
				// only useful for initialization by direct memory setting operations like type handler.
				this.level3      = null;
				this.indices     = null;
				this.constraints = null;
			}
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
				
		@Override
		public final synchronized long size()
		{
			return this.baseSize + this.addingLevel1Index;
		}
		
		@Override
		public final long highestUsedId()
		{
			// concurrency handling done by called method
			return this.nextFreeId() - 1;
		}

		public final synchronized long nextFreeId()
		{
			return this.baseAddingId + this.addingLevel1Index;
		}

		public final synchronized boolean linkStoreContext(final Persister storeContext)
		{
			if(this.storeContext != null)
			{
				if(this.storeContext == storeContext)
				{
					return true;
				}
				throw new IllegalStateException(this + " is already linked to another " + Persister.class.getSimpleName() + " instance.");
			}
			
			this.storeContext = storeContext;
			return true;
		}
		
		@Override
		public final GigaIndices<E> index()
		{
			return this.indices;
		}
		
		@Override
		public final GigaConstraints<E> constraints()
		{
			return this.constraints;
		}

		@Override
		public final Equalator<? super E> equalator()
		{
			return this.equalator;
		}
		
		@Override
		public final void internalReportIndexGroupStateChange(final IndexGroup<E> indexGroup)
		{
			this.indices.internalReportIndexGroupStateChange(indexGroup);
		}
		
		@Override
		public final void internalReportConstraintsStateChange()
		{
			this.constraints.reportChildStateChange();
		}
		
		private void initializeConfiguration()
		{
			this.level1Size          = 1<<this.level1LengthExponent;
			this.level2Size          = 1<<this.level2LengthExponent;
			this.level3MaximumLength = 1<<this.level3MaximumLengthExponent;
			
			this.level2TotalLengthExp = this.level1LengthExponent + this.level2LengthExponent;
			this.maximumEntityId      = 1L << this.level2TotalLengthExp + this.level3MaximumLengthExponent;
			
			this.bitMaskLevel1 = (1 << this.level1LengthExponent) - 1;
			this.bitMaskLevel2 = (1 << this.level2LengthExponent) - 1;
		}
				
		protected final GigaLevel3<E> level3()
		{
			return this.level3;
		}
		
		@Override
		public final boolean isEmpty()
		{
			// concurrency handling done by called method
			return this.size() == 0;
		}
		
		private void validateEntityId(final long entityId)
		{
			if(entityId < 0L || entityId >= this.nextFreeId())
			{
				throw new IllegalArgumentException("Invalid EntityId: " + entityId + " not in [0; " + this.highestUsedId() + "].");
			}
		}
			
		@Override
		public final synchronized E get(final long entityId)
		{
			if(entityId < 0L)
			{
				return null;
			}
			
			final int level3Index = this.toLevel3Index(entityId);
			final GigaLevel3<E> level3 = this.level3();
			if(level3Index >= level3.segments.length)
			{
				return null; // lookup miss (e.g. exceeding currentId)
			}
			
			final Lazy<GigaLevel2<E>> level2Lazy = level3.segments[level3Index];
			if(level2Lazy == null)
			{
				return null; // lookup miss (e.g. exceeding currentId)
			}

			final GigaLevel2<E> level2 = level2Lazy.get();
			final Lazy<GigaLevel1<E>> level1Lazy = level2.segments[this.toLevel2Index(entityId)];
			if(level1Lazy == null)
			{
				return null; // lookup miss (e.g. exceeding currentId)
			}

			final GigaLevel1<E> level1 = level1Lazy.get();
			return level1.entities[this.toLevel1Index(entityId)]; // potentially null (lookup miss)
		}
				
		@Override
		public final synchronized E peek(final long entityId)
		{
			if(entityId < 0L)
			{
				return null;
			}
			
			final GigaLevel3<E> level3 = this.level3();
			final int level3Index = this.toLevel3Index(entityId);
			if(level3Index >= level3.segments.length)
			{
				return null; // lookup miss (e.g. exceeding currentId)
			}
			
			final Lazy<GigaLevel2<E>> level2Lazy = level3.segments[level3Index];
			if(level2Lazy == null)
			{
				return null; // lookup miss (e.g. exceeding currentId)
			}

			final GigaLevel2<E> level2 = level2Lazy.peek();
			if(level2 == null)
			{
				return null; // entity cannot be loaded when the parent segment is not loaded
			}
			final Lazy<GigaLevel1<E>> level1Lazy = level2.segments[this.toLevel2Index(entityId)];
			if(level1Lazy == null)
			{
				return null; // lookup miss (e.g. exceeding currentId)
			}

			final GigaLevel1<E> level1 = level1Lazy.peek();
			if(level1 == null)
			{
				return null; // entity cannot be loaded when the parent segment is not loaded
			}
			return level1.entities[this.toLevel1Index(entityId)]; // potentially null (lookup miss)
		}
		
		final boolean isEntityPeeking(final long entityId, final E entity)
		{
			return this.equalator.equal(this.peek(entityId), entity);
		}
		
		final boolean isEntity(final long entityId, final E entity)
		{
			return this.equalator.equal(this.get(entityId), entity);
		}
		
		@Override
		public final synchronized E removeById(final long entityId)
		{
			this.ensureMutability();
			this.ensureClearedAddingState();
			
			return this.internalRemove(entityId);
		}
		
		private E internalRemove(final long entityId)
		{
			if(entityId < 0L)
			{
				return null;
			}
			
			final GigaLevel3<E> level3 = this.level3();
			final int level3Index = this.toLevel3Index(entityId);
			if(level3Index >= level3.segments.length)
			{
				return null; // lookup miss (e.g. exceeding currentId)
			}
			
			final Lazy<GigaLevel2<E>> level2Lazy = level3.segments[level3Index];
			if(level2Lazy == null)
			{
				return null; // lookup miss (e.g. exceeding currentId)
			}

			final GigaLevel2<E> level2 = level2Lazy.get();
			final int level2Index = this.toLevel2Index(entityId);
			final Lazy<GigaLevel1<E>> level1Lazy = level2.segments[level2Index];
			if(level1Lazy == null)
			{
				return null; // lookup miss (e.g. exceeding currentId)
			}

			final GigaLevel1<E> level1 = level1Lazy.get();
			final int level1Index = this.toLevel1Index(entityId);
			final E current = level1.entities[level1Index];
			if(current == null)
			{
				// id is already null, no removal at all
				return null;
			}
			
			// actual removal including change marking
			this.baseSize--;
			level1.entities[level1Index] = null;

			level3.markChanged(level3Index);
			level2.markChanged(level2Index);
						
			this.indices.internalRemove(entityId, current);
			
			return current;
		}
		

		@Override
		public final synchronized long remove(
			final E                                         entity      ,
			final Iterable<? extends IndexIdentifier<E, ?>> indicesToUse
		)
		{
			this.validateForCRUD(entity);
			this.ensureMutability();
			this.ensureClearedAddingState();
			
			final long entityId = this.lookupEntityIdPeeking(entity, indicesToUse);
			if(entityId >= 0)
			{
				this.internalRemove(entityId);
			}
			
			return entityId;
		}
		
		@Override
		public final synchronized long remove(final E entity)
		{
			this.ensureMutability();
			
			final Iterable<? extends IndexIdentifier<E, ?>> identityLookupIndices =
				this.determineIdentityLookupIndices()
			;
			
			return this.remove(entity, identityLookupIndices);
		}

		@Override
		public final synchronized void removeAll()
		{
			this.ensureMutability();
			this.ensureClearedAddingState();
			
			this.baseSize         = 0;
			this.baseAddingId     = 0;
			this.level1IndexBound = 0;
			this.addingLevel2      = null;
			this.addingLevel1      = null;
			this.addingLevel3Index = 0;
			this.addingLevel2Index = 0;
			this.addingLevel1Index = 0;
			
			this.initializeConfiguration();
			
			this.readOnlyCount       = 0;
			this.activeIteratorCount = 0;
			this.activeIterators.clear();
			
			for(final Lazy<?> e : this.level3.segments)
			{
				if(e != null)
				{
					e.forceClear();
				}
			}

			this.level3.reinitialize(1<<this.level3MinimumLengthExponent);
			this.indices.internalRemoveAll();
		}
		
		private Iterable<? extends IndexIdentifier<E, ?>> determineIdentityLookupIndices()
		{
			final XGettingEnum<? extends BitmapIndex<E, ?>> explicitIndices =
				this.indices.bitmap().identityIndices()
			;
			if(explicitIndices != null)
			{
				return explicitIndices;
			}
			
			final BulkList<IndexIdentifier<E, ?>> indicesCollector = BulkList.New();
			
			// try to find a unique Binary index since those are the most efficient
			this.indices.bitmap().accessUniqueIndices(ucs ->
			{
				final BitmapIndex<E, ?> binaryIndex = ucs.search(i -> i instanceof BinaryBitmapIndex<?>);
				if(binaryIndex != null)
				{
					indicesCollector.add(binaryIndex);
				}
			});

			// if there are still no identifying indices, just use the first unique index, if there are any at all.
			if(indicesCollector.isEmpty())
			{
				this.indices.bitmap().accessUniqueIndices(
					ucs -> indicesCollector.add(ucs.get())
				);
			}
			
			// if there are no unique indices, a combination of ALL bitmap Indices are used as a (sub-optimal) fallback.
			if(indicesCollector.isEmpty())
			{
				this.indices.bitmap().iterate(indicesCollector);
			}
			
			if(indicesCollector.isEmpty())
			{
				throw new IllegalStateException("Cannot perform entity-related searching without at least one index.");
			}
			
			return indicesCollector;
		}
					
		private long lookupEntityIdPeeking(
			final E                                         entity      ,
			final Iterable<? extends IndexIdentifier<E, ?>> indicesToUse
		)
		{
			final GigaQuery<E> query = this.query();
			
			for(final IndexIdentifier<E, ?> index : indicesToUse)
			{
				query.and(index.indexer().like(entity));
			}
			
			final EntityIdResolver resolver = this.equalator.isReferentialEquality()
				? new EntityIdResolver(entity)
				: new EntityIdResolverLoading(entity)
			;
			
			try
			{
				query.resolve(resolver);
			}
			catch(final ThrowBreak b)
			{
				// entity found, execution was aborted
			}
			
			return resolver.foundEntityId;
		}
						
		class EntityIdResolver implements BitmapResult.Resolver<E>
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			final E entity;
			long foundEntityId = -1L;
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////

			EntityIdResolver(final E entity)
			{
				super();
				this.entity = entity;
			}
			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////

			@Override
			public final E get(final long entityId)
			{
				if(!this.isMatchingEntity(entityId))
				{
					return null;
				}
				this.foundEntityId = entityId;
				throw X.BREAK();
			}
			
			protected boolean isMatchingEntity(final long entityId)
			{
				// assumed referential equality equalator which does not need to load an entity to compare it to itself
				return GigaMap.Default.this.isEntityPeeking(entityId, this.entity);
			}

			@Override
			public final GigaMap<E> parent()
			{
				return GigaMap.Default.this;
			}
		}
		
		final class EntityIdResolverLoading extends EntityIdResolver
		{
			
			EntityIdResolverLoading(final E entity)
			{
				super(entity);
			}
			
			@Override
			protected final boolean isMatchingEntity(final long entityId)
			{
				// loading comparison required for value-based equality equalator.
				return GigaMap.Default.this.isEntity(entityId, this.entity);
			}
			
		}
		

		@Override
		public final synchronized E set(final long entityId, final E entity)
		{
			this.validateForCRUD(entity);
			this.ensureMutability();
			this.validateEntityId(entityId);
			
			return this.internalSet(entityId, entity);
		}
		
		@Override
		public final synchronized long replace(final E current, final E replacement)
		{
			if(current == replacement)
			{
				throw new IllegalArgumentException("'current' and 'replacement' cannot be the same instance");
			}
			this.validateForCRUD(replacement);
			
			this.ensureMutability();
			
			final long entityId = this.lookupEntityIdPeeking(current, this.determineIdentityLookupIndices());
			if(entityId < 0)
			{
				return entityId;
			}

			this.internalSet(entityId, replacement);
			
			return entityId;
		}
		
		private E internalSet(final long entityId, final E entity)
		{
			final GigaLevel3<E>          level3   = this.level3();
			final Lazy<GigaLevel2<E>> lvl2Lazy = level3.segments[this.toLevel3Index(entityId)];
			final GigaLevel2<E>          level2   = lvl2Lazy.peek();
			final Lazy<GigaLevel1<E>> lvl1Lazy = level2.segments[this.toLevel2Index(entityId)];
			final GigaLevel1<E>          level1   = lvl1Lazy.peek();
			
			final int level1Index    = this.toLevel1Index(entityId);
			final E   replacedEntity = level1.entities[level1Index];
			
			// only change state if there is an actual change in the entity data.
			if(!this.equalator.equal(replacedEntity, entity))
			{
				this.constraints.check(entityId, replacedEntity, entity);
				level1.entities[level1Index] = entity;
				level1.markStateChangeInstance();
				level2.markStateChangeChildren();
				level3.markStateChangeChildren();
				
				this.indices.internalUpdateIndices(entityId, replacedEntity, entity, this.constraints.custom());
			}
			
			return replacedEntity;
		}
				
		@Override
		public final synchronized <R> R apply(final E current, final Function<? super E, R> logic)
		{
			this.ensureMutability();
			
			final long entityId = this.lookupEntityIdPeeking(current, this.determineIdentityLookupIndices());
			if(entityId < 0)
			{
				throw new IllegalArgumentException("Entity not found");
			}
			
			try
			{
				return this.indices.internalUpdateIndices(entityId, current, logic, this.constraints.custom());
			}
			catch(final ConstraintViolationException e)
			{
				this.ensureClearedAddingState();
				
				/*
				 * Since a state change done by a custom logic cannot be rolled back, there is no other choice
				 * but to remove the entity from the map and report the entity and entityId to the calling
				 * context in the exception.
				 */
				this.internalRemove(entityId);
				throw e;
			}
		}
		
		private void ensureMutability()
		{
			while(this.checkingIsReadOnly())
			{
				try
				{
					this.wait();
				}
				catch(final InterruptedException e)
				{
					throw new IllegalStateException(
						XChars.systemString(this) + " is not mutable. (readOnlyCount " + this.readOnlyCount + ")",
						e
					);
				}
			}
		}
		
		@Override
		public final synchronized long add(final E element)
		{
			this.validateForCRUD(element);
			this.ensureMutability();
			this.internalAdd(element);
			
			// all indices in all index groups have too be updated. Indices do NOT store by themselves!
			this.indices.internalAdd(this.nextFreeId(), element);
			
			return this.baseAddingId + this.addingLevel1Index++;
		}
		
		final void checkConstraints(final long entityId, final E replacedEntity, final E entity)
		{
			this.constraints.check(entityId, replacedEntity, entity);
		}
		
		private void internalAdd(final E entity)
		{
			this.constraints.check(-1, null, entity);
			this.ensureAddingStateSetup();
						
			// actual adding
			this.addingLevel1.entities[this.addingLevel1Index] = entity;
		}
		
		private void validateForCRUD(final E entity)
		{
			/*
			 * No sense in adding null entities for the following reasons:
			 * - This map is meant as a data storage, not a transient collection to implement some algorithm
			 * - Forces indexers to check for null entities
			 * - unnecessarily occupies IDs
			 * - might cause problems or complication in some parts of the logic.
			 
			 */
			if(entity == null)
			{
				throw new IllegalArgumentException("null entries are not allowed");
			}
		}
		
		void ensureClearedSetupState()
		{
			this.baseSize         = 0;
			this.baseAddingId     = 0;
			this.level1IndexBound = 0;
			this.addingLevel2      = null;
			this.addingLevel1      = null;
			this.addingLevel3Index = 0;
			this.addingLevel2Index = 0;
			this.addingLevel1Index = 0;
			
			this.initializeConfiguration();
			
			this.readOnlyCount       = 0;
			this.activeIteratorCount = 0;
			this.activeIterators.clear();
		}
		
		private void ensureAddingStateSetup()
		{
			// handling both adding state initialization and level1 overflow
			if(this.addingLevel1Index >= this.level1IndexBound)
			{
				this.setupAddingState();
			}
			else
			{
				this.level3.markChanged(this.addingLevel3Index);
				this.addingLevel2.markChanged(this.addingLevel2Index);
			}
		}
		
		private void ensureClearedAddingState()
		{
			if(this.addingLevel1Index < this.level1IndexBound)
			{
				// adding state already cleared.
				return;
			}
			
			this.clearAddingState();
		}
		
		@Override
		public final synchronized long addAll(final Iterable<? extends E> entities)
		{
			for(final E element : entities)
			{
				this.validateForCRUD(element);
			}
			this.ensureMutability();

			final long currentId = this.nextFreeId();
			try
			{
				for(final E element : entities)
				{
					this.internalAdd(element);
					this.addingLevel1Index++;
				}
				
				this.indices.internalAddAll(currentId, entities);
				
				return this.nextFreeId() - currentId;
			}
			catch(final Exception e)
			{
				this.rollbackToEntityId(currentId);
				throw e;
			}
		}
		
		private void rollbackToEntityId(final long requiredCurrentId)
		{
			this.clearAddingState();
			while(this.highestUsedId() >= requiredCurrentId)
			{
				this.internalRemove(this.highestUsedId());
				this.baseAddingId--;
			}
		}
					
		private void setupAddingState()
		{
			final long size = this.size();
			
			// the next free id is now the current id because it is the one being used now.
			final long currentId = this.nextFreeId();
			
			this.addingLevel3Index = this.toLevel3Index(currentId);
			this.addingLevel2Index = this.toLevel2Index(currentId);
			this.addingLevel1Index = this.toLevel1Index(currentId);
			
			// helper value to recognize both uninitialized state and level1 overflow.
			this.level1IndexBound = this.level1Size;
			
			this.baseSize      = size      - this.addingLevel1Index;
			this.baseAddingId = currentId - this.addingLevel1Index;
			
			/* (23.12.2022 TM)TODO: crash robustness
			 * compare the highest non-null element with currentId to recognize previous incomplete stores.
			 * #storeCurrentLevel1AndClear might have stored the level1 array but not the data instance.
			 * In that case, the data instance's state would be outdated and would have to be derived from
			 * actual data.
			 */
			
			if(this.addingLevel3Index == this.level3MaximumLength)
			{
				throw new IllegalStateException("Reached maximum capacity.");
			}
			
			this.addingLevel2 = this.ensureLevel2(this.level3, this.addingLevel3Index);
			this.addingLevel1 = this.ensureLevel1(this.addingLevel2, this.addingLevel2Index);
		}
		
		private void clearAddingState()
		{
			// baseSize must hold the whole size since level1Index will get set to 0.
			this.baseSize          = this.size()      ;
			this.baseAddingId      = this.nextFreeId();
			this.addingLevel2      = null;
			this.addingLevel1      = null;
			this.addingLevel3Index = 0;
			this.addingLevel2Index = 0;
			this.addingLevel1Index = 0;
			this.level1IndexBound  = 0;
		}
		
		private GigaLevel2<E> ensureLevel2(final GigaLevel3<E> level3, final int level3Index)
		{
			if(level3Index >= level3.segments.length)
			{
				level3.enlargeLevel3(level3Index + 1);
			}
			else if(level3.segments[level3Index] != null)
			{
				level3.markChanged(level3Index);
				return level3.segments[level3Index].get();
			}
			
			final GigaLevel2<E> level2 = this.createLevel2();
			level3.addLevel2(level2, level3Index);
			return level2;
		}
		
		private GigaLevel1<E> ensureLevel1(final GigaLevel2<E> level2, final int level2Index)
		{
			if(level2.segments[level2Index] != null)
			{
				level2.markChanged(level2Index);
				return level2.segments[level2Index].get();
			}
			
			final GigaLevel1<E> level1 = this.createLevel1();
			level2.addLevel1(level1, level2Index);
			return level1;
		}
		
		
		@Override
		public final synchronized void onAfterCommit()
		{
			this.level3.clearStateChangeMarkers();
			this.indices.clearStateChangeMarkers();
			this.constraints.clearStateChangeMarkers();
		}
		
		public final void internalRegisterChangeStores(final Storer storer)
		{
			if(this.indices.isChangedAndNotNew())
			{
				storer.store(this.indices);
			}
			// new or unchanged instances get handled by the default lazy storing logic. Changed and not new must be handled here.
			if(this.level3.isChangedAndNotNew())
			{
				storer.store(this.level3);
			}
			
			// new or unchanged instances get handled by the default lazy storing logic. Changed and not new must be handled here.
			if(this.constraints.isChangedAndNotNew())
			{
				storer.store(this.constraints);
			}
						
			// must clear state change markers as soon as there is at least one either "changed" or "new" marker. So almost always.
			storer.registerCommitListener(this);
		}
						
		@Override
		public final synchronized void release()
		{
			this.ensureMutability();
			for(final Lazy<?> e : this.level3.segments)
			{
				if(e != null && e.isStored())
				{
					e.clear();
				}
			}
			
			this.clearAddingState();
		}
						
		@Override
		public final synchronized <I extends Consumer<? super E>> I iterate(final I iterator)
		{
			final Lazy<GigaLevel2<E>>[] level3 = this.level3.segments;
			for(final Lazy<GigaLevel2<E>> level2Root : level3)
			{
				if(level2Root == null)
				{
					continue;
				}
				final Lazy<GigaLevel1<E>>[] level2 = level2Root.get().segments;
				try
				{
					iterate(level2, iterator);
				}
				catch(final ThrowBreak b)
				{
					break;
				}
			}
			
			return iterator;
		}
				
		static <E> void iterate(final Lazy<GigaLevel1<E>>[] level2, final Consumer<? super E> iterator)
		{
			for(final Lazy<GigaLevel1<E>> level1Root : level2)
			{
				if(level1Root == null)
				{
					continue;
				}
				final GigaLevel1<E> level1 = level1Root.get();
				iterate(level1, iterator);
			}
		}
		
		static <E> void iterate(final GigaLevel1<E> level1, final Consumer<? super E> iterator)
		{
			for(final E e : level1.entities)
			{
				if(e == null)
				{
					continue;
				}
				iterator.accept(e);
			}
		}
				
		@Override
		public final synchronized <I extends EntryConsumer<? super E>> I iterateIndexed(final I consumer)
		{
			final int level1p2Pow2 = this.level2TotalLengthExp;
			final Lazy<GigaLevel2<E>>[] level3 = this.level3.segments;
			for(int i = 0; i < level3.length; i++)
			{
				final Lazy<GigaLevel2<E>> level2Root;
				if((level2Root = level3[i]) == null)
				{
					continue;
				}
				final GigaLevel2<E> level2 = level2Root.get();
				try
				{
					this.iterate(level2, i<<level1p2Pow2, consumer);
				}
				catch(final ThrowBreak b)
				{
					break;
				}
			}
			
			return consumer;
		}
		
		private void iterate(
			final GigaLevel2<E>            level2   ,
			final int                      baseIndex,
			final EntryConsumer<? super E> consumer
		)
		{
			final Lazy<GigaLevel1<E>>[] segments = level2.segments;
			final int level1Pow2 = this.level1LengthExponent;
			for(int i = 0; i < segments.length; i++)
			{
				final Lazy<GigaLevel1<E>> level1Root;
				if((level1Root = segments[i]) == null)
				{
					continue;
				}
				final GigaLevel1<E> level1 = level1Root.get();
				this.iterate(level1.entities, baseIndex + (i << level1Pow2),  consumer);
			}
		}
		
		private void iterate(
			final E[]                      level1   ,
			final int                      baseIndex,
			final EntryConsumer<? super E> consumer
		)
		{
			for(int i = 0; i < level1.length; i++)
			{
				final E e;
				if((e = level1[i]) == null)
				{
					continue;
				}
				consumer.accept(baseIndex + i, e);
			}
		}
		
		private GigaLevel3<E> createLevel3()
		{
			return new GigaLevel3<>(1<<this.level3MinimumLengthExponent, true);
		}
		
		private GigaLevel2<E> createLevel2()
		{
			return new GigaLevel2<>(this.level2Size, true);
		}
		
		private GigaLevel1<E> createLevel1()
		{
			return new GigaLevel1<>(this.level1Size, true);
		}
										
		private int toLevel1Index(final long index)
		{
			// no shift since mask cuts off anything unwanted to the left.
			return (int)index & this.bitMaskLevel1;
		}
		
		private int toLevel2Index(final long index)
		{
			// mask and shift since the level 2 value lies "in the middle" of the bits.
			return (int)(index >>> this.level1LengthExponent) & this.bitMaskLevel2;
		}
		
		private int toLevel3Index(final long entityId)
		{
			if(entityId > this.maximumEntityId)
			{
				throw new NoSuchElementException(
					"Specified entityId " + entityId
					+ " exceeds the configured maximum entityId of " + this.maximumEntityId
				);
			}
			
			// no mask since right shifting already cuts off all unwanted bits to the right.
			return (int)(entityId >>> this.level2TotalLengthExp);
		}
						
		@Override
		public final GigaMap<E> parent()
		{
			return this;
		}
		
		@Override
		public final GigaQuery<E> query(final IterationThreadProvider threadProvider)
		{
			return new GigaQuery.Default<>(this, threadProvider);
		}
						
		final synchronized GigaIterator<E> createIterator(
			final Condition<E>             condition     ,
			final BitmapResult.Resolver<E> resolver      ,
			final IterationThreadProvider  threadProvider
		)
		{
			final BitmapResult   result  = condition.evaluate(this.indices.bitmap());
			final BitmapResult[] results = result.andOptimize();
			
			if(isNoResult(results))
			{
				return GigaIterator.Empty(this);
			}
			
			final GigaIterator<E> iterator;
			final Reading         reading ;

			final int threadCount = threadProvider.provideThreadCount(this, results);
			if(threadCount <= 0)
			{
				@SuppressWarnings("resource") // has to be closed by caller
				final GigaIterator.Default<E> singleThreaded =
					new GigaIterator.Default<>(this, resolver, 0, this.nextFreeId(), results)
				;
				iterator = singleThreaded;
				reading  = singleThreaded;
			}
			else
			{
				threadProvider.prepareIteration();
				final ThreadedIterator multiThreaded =
					new ThreadedIterator(this, results, threadCount, threadProvider)
				;
				@SuppressWarnings("resource") // has to be closed by caller
				final GigaIterator.Wrapping<E> wrapper =
					new GigaIterator.Wrapping<>(this, multiThreaded, resolver)
				;
				
				iterator = wrapper;
				reading  = wrapper;
			}

			this.activeIterators.add(reading);
			this.activeIteratorCount++;
			this.markReadOnly();

			return iterator;
		}
		
		static boolean isNoResult(final BitmapResult[] results)
		{
			if(results == null || results.length == 0)
			{
				return true;
			}
			
			for(final BitmapResult result : results)
			{
				if(result != null)
				{
					return false;
				}
			}
			
			return true;
		}
				
		/* (03.01.2024 TM)TODO: More elaborate iterator management.
		 * - flag to disallow iterator usage to guarantee mutability
		 * - flag (or queued waiters) to disallow the creation of any new iterators to guarantee eventual mutability
		 * - configurable strategy (exception, waiting with and without timeout, etc.)
		 * - optionally thread-specific (using thread-localizing Threaded type)
		 * - Some kind of forced closing of all Iterators.
		 * ...
		 * (this gets really complicated, just to use an iterator instead of an loop-iterating method)
		 * 
		 */
		
		final synchronized void closeIterator(final Reading iterator)
		{
			if(iterator.parent() != this)
			{
				throw new IllegalArgumentException("Passed iterator does not belong to this " + GigaMap.class.getSimpleName());
			}
			if(iterator.isClosed())
			{
				return;
			}
			if(this.activeIteratorCount == 0)
			{
				throw new IllegalStateException("No active iterators.");
			}
			
			final boolean removed = this.activeIterators.removeOne(iterator);
			if(!removed)
			{
				throw new IllegalArgumentException("Iterator was not registered as active.");
			}
			
//			iterator.clearIterationState(); // should be unnecessary since the instances will never be read again.
			iterator.setInactive();
			
			try
			{
				if(--this.activeIteratorCount == 0)
				{
					this.activeIterators.truncate();
				}
			}
			finally
			{
				this.unmarkReadOnly();
			}
		}
		
		private boolean checkingIsReadOnly()
		{
			if(this.readOnlyCount == 0)
			{
				return false;
			}
			
			if(this.readOnlyCount > this.activeIteratorCount)
			{
				throw new IllegalStateException(XChars.systemString(this) + " is in read only mode.");
			}
			
			return true;
		}
				
		@Override
		public final synchronized boolean isReadOnly()
		{
			return this.readOnlyCount != 0;
		}
					
		@Override
		public final synchronized void markReadOnly()
		{
			this.readOnlyCount++;
		}
		
		@Override
		public final synchronized void unmarkReadOnly()
		{
			if(--this.readOnlyCount == 0)
			{
				// notify threads waiting for gigamap instance to become mutable again
				this.notifyAll();
			}
		}
		
		@Override
		public final synchronized boolean clearReadOnlyMarks()
		{
			final boolean result = this.readOnlyCount > this.activeIteratorCount;
			this.readOnlyCount = this.activeIteratorCount;
			
			return result;
		}
		
		final void executeInReadOnlyMode(
			final Condition<E>        condition,
			final long                idStart  ,
			final long                idBound  ,
			final Consumer<? super E> consumer
		)
		{
			try
			{
				this.markReadOnly();
				this.executeReadOnly(condition, idStart, idBound, consumer);
			}
			finally
			{
				this.unmarkReadOnly();
			}
		}
		
		final void executeReadOnly(
			final Condition<E>        condition,
			final long                idStart  ,
			final long                idBound  ,
			final Consumer<? super E> consumer
		)
		{
			if(condition == null)
			{
				// it is perfectly valid to execute a query without a condition
				return;
			}
			
			/*
			 * Note: result is already a copy with its own exclusive iteration state.
			 * So executing a query in a single thread is thread-safe, even if there
			 * are multiple queries being executed concurrently in multiple threads.
			 * 
			 * But if ONE query is executed with multiple threads, the result needs to be
			 * copied AGAIN to give each thread its own exclusive thread-local iteration state.
			 * 
			 * Result instances are very small and shallow instances, only pointing to the actual data.
			 * So copying them is hardly noticable regarding performance.
			 */
			final long         effStart = Math.max(idStart, 0);
			final long         effBound = Math.min(Math.max(idBound, effStart), this.nextFreeId());
			final BitmapResult result   = condition.evaluate(this.indices.bitmap());
			
			execute(this, result.andOptimize(), effStart, effBound, consumer);
		}
		
		static <E> void execute(
			final BitmapResult.Resolver<E> resolver,
			final BitmapResult[]           results ,
			final long                     startId ,
			final long                     boundId ,
			final Consumer<? super E>      consumer
		)
		{
			if(isNoResult(results))
			{
				return;
			}
			
			final GigaIteration<E> iteration = new GigaIteration<>(resolver, startId, boundId, results);
			iteration.execute(consumer);
		}
		
		final void executeReadOnly(
			final Condition<E>            condition     ,
			final long                    idStart       ,
			final long                    idBound       ,
			final Consumer<? super E>[]   consumers     ,
			final IterationThreadProvider threadProvider
		)
		{
			if(condition == null)
			{
				// it is perfectly valid to execute a query without a condition
				return;
			}
			
			if(consumers.length == 1 || threadProvider == null)
			{
				this.executeReadOnly(condition, idStart, idBound, consumers[0]);
				return;
			}
			
			final long         effStart = Math.max(idStart, 0);
			final long         effBound = Math.min(Math.max(idBound, effStart), this.nextFreeId());
			final BitmapResult result   = condition.evaluate(this.indices.bitmap());
			
			final LogicProvider<E> logicProvider =
				new LogicProvider<>(this, effStart, effBound, result.andOptimize(), consumers)
			;
			threadProvider.executeThreaded(this, consumers.length, logicProvider);
		}
				
		@Override
		public final synchronized long store()
		{
			return this.storeContext().store(this);
		}
		
		private Persister storeContext()
		{
			if(this.storeContext == null)
			{
				throw new IllegalStateException(
					GigaMap.class.getSimpleName()
					+ " instance must be stored once initially by a storing context to be connected to it."
				);
			}
			
			return this.storeContext;
		}
		
		
		
		static final class LogicProvider<E> implements IterationLogicProvider
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			private final GigaMap.Default<E>    gigaMap     ;
			private final long                  startId     ;
			private final long                  boundId     ;
			private final BitmapResult[]        results     ;
			private final Consumer<? super E>[] consumers   ;
			private final long                  idsPerThread;
			
			private int currentThread = 0;
						
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////

			LogicProvider(
				final GigaMap.Default<E>    gigaMap  ,
				final long                  startId  ,
				final long                  boundId  ,
				final BitmapResult[]        results  ,
				final Consumer<? super E>[] consumers
			)
			{
				super();
				this.gigaMap   = gigaMap  ;
				this.startId   = startId  ;
				this.boundId   = boundId  ;
				this.results   = results  ;
				this.consumers = consumers;
				
				this.idsPerThread = (boundId - startId) / consumers.length;
			}



			@Override
			public Runnable provideIterationLogic()
			{
				final int index = this.currentThread;
				final long threadStartId = this.startId + this.currentThread * this.idsPerThread;
				final long threadBoundId = ++this.currentThread < this.consumers.length
					? threadStartId + this.idsPerThread
					: this.boundId
				;
				
				final BitmapResult[] resultsThreadIterationCopy = BitmapResult.createIterationCopy(this.results);
				return () ->
					execute(
						this.gigaMap,
						resultsThreadIterationCopy,
						threadStartId,
						threadBoundId,
						this.consumers[index]
					);
			}
			
		}
		
		@Override
		public synchronized GigaIterator<E> iterator()
		{
			final Itr<E> iterator = new Itr<>(this);
			this.activeIterators.add(iterator);
			this.activeIteratorCount++;
			
			this.markReadOnly();
			
			return iterator;
		}
		
	}
	
	
	/*
	 * The sole purpose of this type is to keep methods that are effectively public API,
	 * but have rather "internal" uses, out of the immediately accessible API.
	 * For two reasons:
	 * - To prevent cluttering up IntelliSense, JavaDoc, etc.
	 * - To prevent easily calling them outside the context of the GigaMap framework and thereby potentially creating errors.
	 * It's not strictly needed from an architectural perspective, more to keep things "nice and tidy".
	 */
	public interface Internal<E> extends GigaMap<E>
	{
		public void internalReportIndexGroupStateChange(IndexGroup<E> indexGroup);
		
		public void internalReportConstraintsStateChange();
	}

	
	// the only purpose of this class is to be persitable instead of using an unpersistable lambda or method reference.
	public final class DefaultEqualator<E> implements IdentityEqualator<E>
	{
		@Override
		public boolean equal(final E e1, final E e2)
		{
			return e1 == e2;
		}
		
	}
	
	public final class Dimensions
	{
		public static int defaultLowLevelLengthExponent()
		{
			// 1<<8 == 2^8 == 256 entities per low-level segment (single cell).
			return 8;
		}
		
		public static int defaultMidLevelLengthExponent()
		{
			// 1<<10 == 2^10 == 1024 low-level segments per mid-level segment (single cell).
			return 10;
		}
		
		public static int defaultHighLevelMinimumLengthExponent()
		{
			// 1<<0 == 2^0 == 1 mid-level segments in the high-level segment (parent gigaMap's cell).
			return 0;
		}
		
		public static int defaultHighLevelMaximumLengthExponent()
		{
			// 1<<30 == 2^30 == ~1 Bil mid-level segments in the high-level segment (parent gigaMap's cell).
			return 30;
		}
		
		public static int minimumLowLevelLengthExponent()
		{
			// 1<<0 == 2^0 == 1 entity per low-level segment. Only reasonable for large subgraphs as entities.
			return 0;
		}
		
		public static int minimumMidLevelLengthExponent()
		{
			// 1<<8 == 2^8 == 256 low-level segments per mid-level segment (single cell). Anything below is bonkers.
			return 8;
		}
		
		public static int minimumHighLevelMinimumLengthExponent()
		{
			// 1<<0 == 2^0 == 1 mid-level segmentin the high-level segment (parent gigaMap's cell).
			return 0;
		}
		
		public static int minimumHighLevelMaximumLengthExponent()
		{
			// 1<<8 == 2^8 == 256 mid-level segments in the high-level segment. Anything below is bonkers.
			return 8;
		}
		
		public static int maximumLowLevelLengthExponent()
		{
			// 1<<20 == 2^20 == ~1 Mio entity per low-level segment. Only reasonable for tiny entities.
			return 20;
		}
		
		public static int maximumMidLevelLengthExponent()
		{
			// 1<<20 == 2^20 == ~1 Mio low-level segments per mid-level segment. For gigantic amounts of tiny entities.
			return 20;
		}
		
		public static int maximumHighLevelMinimumLengthExponent()
		{
			// 1<<30 == 2^30 == ~1 Bil mid-level segments in the high-level segment (parent gigaMap's cell).
			return 30;
		}
		
		public static int maximumHighLevelMaximumLengthExponent()
		{
			// 1<<30 == 2^30 == ~1 Bil mid-level segments in the high-level segment (parent gigaMap's cell).
			return 30;
		}
		
		public static int maximumLengthExponentSum()
		{
			/*
			 * While the giga map itself could theoretically address the full range of 2^63 - 1 entities,
			 * the bitmap index can only cover 2^50 (9+3 on level1 + 8 on level2 + 30 on level3).
			 * So in order to not blow up the bitmap index, the giga map may only contain 2^50 entities.
			 * That is roughly 1 WTFillion (10^15) of entities and should easily suffice even for insane
			 * theoretical academic use cases.
			 */
			// 1<<50 == 2^50 == ~1 WTFillion (10^15) total entity count.
			return 50;
		}
		
		public static void validateSegmentRange(
			final int    definedExponent,
			final int    minimum        ,
			final int    maximum        ,
			final String name
		)
		{
			if(definedExponent >= minimum && definedExponent <= maximum)
			{
				return;
			}
			
			throw new IllegalArgumentException(
				"Invalid segment size: " +name + " level length of 2^" + definedExponent
				+ " not in [2^"	+ minimum + ", 2^" + maximum + "]."
			);
		}
			
		public static void validateSegmentSizeDistribution(
			final int level1       ,
			final int level2       ,
			final int level3Minimum,
			final int level3Maximum
		)
		{
			validateSegmentRange(
				level1,
				minimumLowLevelLengthExponent(),
				maximumLowLevelLengthExponent(),
				"low"
			);
			validateSegmentRange(level2,
				minimumMidLevelLengthExponent(),
				maximumMidLevelLengthExponent() ,
				"mid"
			);
			validateSegmentRange(
				level3Minimum,
				minimumHighLevelMinimumLengthExponent(),
				maximumHighLevelMinimumLengthExponent(),
				"high (maximum)"
			);
			validateSegmentRange(
				level3Maximum,
				minimumHighLevelMaximumLengthExponent(),
				maximumHighLevelMaximumLengthExponent(),
				"high (minimum)"
			);
			
			if(level1 + level2 + level3Maximum > maximumLengthExponentSum())
			{
				throw new IllegalArgumentException(
					"Specified total entity capacity of 2^" + (level1 + level2 + level3Maximum)
					+ " exceeds the technical limit of 2^" + maximumLengthExponentSum() + "."
				);
			}
		}
	}
	
		
	

	static final class Itr<E> implements GigaIterator<E>, Reading
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final GigaMap.Default<E> parent;
		long currentEntityId = -1;
		E currentEntity = null;
		
		boolean isActive = true;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Itr(final GigaMap.Default<E> parent)
		{
			super();
			this.parent = parent;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final boolean hasNext()
		{
			// must close in any and all cases where next is null. Including no elements and any throwable.
			try
			{
				final long idBound = this.parent.nextFreeId();
				while(this.currentEntity == null)
				{
					if(++this.currentEntityId >= idBound)
					{
						this.close();
						return false;
					}
					this.currentEntity = this.parent.get(this.currentEntityId);
				}
				return true;
			}
			catch(final Throwable t)
			{
				this.close();
				throw t;
			}
		}

		@Override
		public final E next()
		{
			if(this.currentEntity == null)
			{
				throw new NoSuchElementException();
			}
			
			final E e = this.currentEntity;
			this.currentEntity = null;
			
			return e;
		}
		
		@Override
		public <I extends EntryConsumer<? super E>> void nextIndexed(final I consumer)
		{
			final E entity = this.next();
			consumer.accept(this.currentEntityId, entity);
		}
		
		@Override
		public final GigaMap<?> parent()
		{
			return this.parent;
		}
		
		@Override
		public final boolean isClosed()
		{
			return !this.isActive;
		}
		
		@Override
		public final void setInactive()
		{
			this.isActive = false;
		}
		
		@Override
		public final void close()
		{
			this.parent.closeIterator(this);
		}
		
		@SuppressWarnings("deprecation")
		@Override
		protected void finalize() throws Throwable
		{
			this.close();
		}
		
	}
	
	
	/**
	 * The Reading interface provides methods for handling read operations within a GigaMap structure.
	 * It represents a stateful reader that can iterate through or interact with the data in a controlled manner.
	 * Implementations of this interface may manage resources that need explicit closing to ensure proper resource
	 * management and cleanup.
	 */
	public interface Reading
	{
		public void close();
		
		public boolean isClosed();
		
		public GigaMap<?> parent();
		
		public void setInactive();
				
	}
	
	
	/**
	 * Represents a component that is part of a GigaMap.
	 * Provides functionality to retrieve the parent map associated with this component.
	 *
	 * @param <E> the type of elements this component handles
	 */
	public interface Component<E>
	{
		/**
		 * Retrieves the parent GigaMap associated with this component.
		 *
		 * @return the parent GigaMap to which this component belongs
		 */
		public GigaMap<E> parentMap();
	}

}
