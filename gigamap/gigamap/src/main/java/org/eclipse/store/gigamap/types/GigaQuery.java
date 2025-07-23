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

import org.eclipse.serializer.collections.XArrays;
import org.eclipse.serializer.collections.types.XIterable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


/**
 * Facility to construct and execute queries on a {@link GigaMap}.
 * <p>
 * Use one of the various query*() methods of the {@link GigaMap} to create a {@link GigaQuery}.
 * 
 * 
 * @param <E> the type of entities in this query
 */
public interface GigaQuery<E> extends Predicate<E>, XIterable<E>, Iterable<E>, GigaMap.Component<E>
{
	/**
	 * Returns an iterator over the results of this query.
	 * <p>
	 * <strong>
	 * Important: Always close the iterator after using it, in order to release the read-lock of the GigaTable.
	 * Best to use it in a try-with-resource.
	 * </strong>
	 * 
	 * @return an {@link GigaIterator} over the resulting entities
	 */
	@Override
	public GigaIterator<E> iterator();
	
	/**
	 * Iterates over the results of this query using the provided {@code Consumer} procedure.
	 *
	 * @param <P> the type of the {@code Consumer} that will process the elements
	 * @param procedure the {@code Consumer} instance that processes elements
	 * @return the same {@code Consumer} provided as {@code procedure}, after iteration is complete
	 */
	@Override
	public default <P extends Consumer<? super E>> P iterate(final P procedure)
	{
		try(final GigaIterator<E> it = this.iterator())
		{
			while(it.hasNext())
			{
				procedure.accept(it.next());
			}
		}
		return procedure;
	}
	
	/**
	 * Iterates over the results of this query with their corresponding id
	 * and applies the given consumer to each element.
	 *
	 * @param <I> The type of the consumer that will process each element and its id.
	 * @param consumer The consumer that processes each element and its id during iteration.
	 * @return The consumer after all elements of the collection have been processed.
	 */
	public default <I extends EntryConsumer<? super E>> I iterateIndexed(final I consumer)
	{
		try(final GigaIterator<E> it = this.iterator())
		{
			while(it.hasNext())
			{
				it.nextIndexed(consumer);
			}
		}
		return consumer;
	}
	
	/**
	 * Returns a {@link Stream} over the results of this query.
	 * <p>
	 * <strong>
	 * Important: Always close the stream after using it, in order to release the read-lock of the GigaTable.
	 * Best to use it in a try-with-resource.
	 * </strong>
	 * 
	 * @return a {@link Stream} containing the resulting entities
	 */
	public default Stream<E> stream()
	{
		// TODO find out if custom stream implementation is worth it
		
		final GigaIterator<E> iterator = this.iterator();
		return StreamSupport.stream(
			() -> Spliterators.spliteratorUnknownSize(iterator, 0),
			Spliterator.CONCURRENT,
			false
		)
		.onClose(iterator::close)
		;
	}
	
	/**
	 * Fetches this query's result into a list.
	 * 
	 * @return a {@link List} containing the resulting entities
	 */
	public default List<E> toList()
	{
		final List<E> list = new ArrayList<>();
		try(final GigaIterator<E> it = this.iterator())
		{
			while(it.hasNext())
			{
				list.add(it.next());
			}
		}
		return list;
	}

	/**
	 * Fetches a limited amount of this query's result into a list.
	 * 
	 * @param limit maximum number of results (&gt;=0)
	 * @return a {@link List} containing the resulting entities
	 */
	public default List<E> toList(final int limit)
	{
		return this.toList(0, limit);
	}

	/**
	 * Fetches an offset and limited amount of this query's result into a list.
	 * 
	 * @param offset the index of the first element to fetch (&gt;=0)
	 * @param limit maximum number of results (&gt;=0)
	 * @return a {@link List} containing the resulting entities
	 */
	public default List<E> toList(final int offset, final int limit)
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
			return Collections.emptyList();
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
		return list;
	}

	/**
	 * Fetches this query's result into a set.
	 * 
	 * @return a {@link Set} containing the resulting entities
	 */
	public default Set<E> toSet()
	{
		final Set<E> set = new HashSet<>();
		try(final GigaIterator<E> it = this.iterator())
		{
			while(it.hasNext())
			{
				set.add(it.next());
			}
		}
		return set;
	}

	/**
	 * Fetches a limited amount of this query's result into a set.
	 * 
	 * @param limit maximum number of results (&gt;=0)
	 * @return a {@link Set} containing the resulting entities
	 */
	public default Set<E> toSet(final int limit)
	{
		return this.toSet(0, limit);
	}

	/**
	 * Fetches an offset and limited amount of this query's result into a set.
	 * 
	 * @param offset the index of the first element to fetch (&gt;=0)
	 * @param limit maximum number of results (&gt;=0)
	 * @return a {@link Set} containing the resulting entities
	 */
	public default Set<E> toSet(final int offset, final int limit)
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
			return Collections.emptySet();
		}
		
		final Set<E> set = new HashSet<>(Math.min(1024, limit));
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
				set.add(it.next());
			}
		}
		return set;
	}
	
	/**
	 * Returns the first result of this query.
	 * 
	 * @return an {@link Optional} first element of the result
	 */
	public default Optional<E> findFirst()
	{
		try(final GigaIterator<E> it = this.iterator())
		{
			return it.hasNext()
				? Optional.of(it.next())
				: Optional.empty()
			;
		}
	}
	
	/**
	 * Calculates the absolute count of resulting entities of this query.
	 * <p>
	 * Note that the query has to be executed in the background, in order to calculate the exact count.
	 * 
	 * @return the size of the result
	 */
	public default long count()
	{
		long count = 0L;
		try(final GigaIterator<E> it = this.iterator())
		{
			while(it.hasNext())
			{
				count++;
				it.next();
			}
		}
		return count;
	}
	
	/**
	 * Executes this query handing over the results to an {@link EntryConsumer}.
	 * 
	 * @param <A> the acceptor type
	 * @param entryConsumer the result acceptor
	 * @return the given acceptor
	 */
	public <A extends EntryConsumer<? super E>> A executeWithId(A entryConsumer);
	
	public <R extends BitmapResult.Resolver<E>> R resolve(R resolver);
	
	/**
	 * Executes this query handing over the results to an entity consumer.
	 * 
	 * @param <C> the consumer type
	 * @param consumer the result consumer
	 * @return the given consumer
	 */
	public <C extends Consumer<? super E>> C execute(final C consumer);
	
	/**
	 * Executes this query handing over the results to multiple entity consumers.
	 * <p>
	 * If this query was created with a multi-threading {@link IterationThreadProvider} this will be executed in parallel.
	 *
	 * @param consumers an array of {@link Consumer} instances that will process the resulting entities
	 */
	@SuppressWarnings("unchecked")
	public void execute(final Consumer<? super E>... consumers);
	
	/**
	 * Constructs a condition builder for adding a condition to the query
	 * using the specified string index name. The returned builder allows
	 * further operation on the condition for refining the query.
	 *
	 * @param stringIndexName the name of the string index to add a condition to
	 * @return a condition builder for the specified string index
	 */
	public default ConditionBuilder<E, String> and(final String stringIndexName)
	{
		return this.and(stringIndexName, String.class);
	}
	
	/**
	 * Creates a condition builder for adding a condition to the query based on the specified index name and key type.
	 * The returned builder can be used to further define and refine the query conditions.
	 *
	 * @param <K> the type of the key associated with the index
	 * @param indexName the name of the index to add a condition to
	 * @param keyType the class type of the key associated with the index
	 * @return a condition builder configured with the specified index and key type
	 */
	public default <K> ConditionBuilder<E, K> and(final String indexName, final Class<K> keyType)
	{
		return this.and(IndexIdentifier.New(indexName, keyType));
	}
	
	/**
	 * Constructs a condition builder for adding a condition to the query using the specified index identifier.
	 * The returned builder can be used to further define and refine the query conditions.
	 *
	 * @param <K> the type of the key associated with the index
	 * @param indexIdentifier the index identifier specifying the index and its associated key type
	 * @return a condition builder configured with the specified index and key type
	 */
	public <K> ConditionBuilder<E, K> and(IndexIdentifier<E, K> indexIdentifier);
	
	/**
	 * Sets the starting ID for the query. This method is used to specify a lower boundary
	 * for filtering the results based on the ID of the entities.
	 *
	 * @param idStart the starting ID to filter the entities (inclusive)
	 * @return the current {@link GigaQuery} instance with the updated starting ID
	 */
	public GigaQuery<E> idStart(long idStart);
	
	/**
	 * Sets the upper boundary for the query based on the ID of the entities.
	 * This method is used to define a limiting value for filtering the results, inclusive of the specified ID.
	 *
	 * @param idBound the maximum ID to filter the entities (inclusive)
	 * @return the current {@link GigaQuery} instance with the updated ID boundary
	 */
	public GigaQuery<E> idBound(long idBound);
	
	/**
	 * Sets a range for the query by specifying a starting ID and an upper boundary ID.
	 * This method is used to filter the results based on the IDs of the entities.
	 *
	 * @param idStart the starting ID to filter the entities (inclusive)
	 * @param idBound the maximum ID to filter the entities (inclusive)
	 * @return the current {@link GigaQuery} instance with the updated range
	 */
	public default GigaQuery<E> idRange(final long idStart, final long idBound)
	{
		this.idStart(idStart);
		this.idBound(idBound);
		
		return this;
	}
	
	/**
	 * Adds a condition to the query based on a string index name and key.
	 * Combines the conditions using a logical "AND" operation.
	 *
	 * @param stringIndexName the name of the string index to add a condition to
	 * @param key the key to use for the condition
	 * @return the updated {@link GigaQuery} instance with the added condition
	 */
	public default GigaQuery<E> and(final String stringIndexName, final String key)
	{
		return this.and(stringIndexName).is(key);
	}
	
	/**
	 * Combines the current query with an additional condition on the specified indexed field
	 * and matches it against the provided value.
	 *
	 * @param <K>       The type of the key associated with the index.
	 * @param indexName The name of the index field to apply the condition to.
	 * @param keyType   The class type of the key.
	 * @param key       The value to match the indexed field against.
	 * @return A new query object that represents the combination of the current query and
	 *         the additional condition.
	 */
	public default <K> GigaQuery<E> and(final String indexName, final Class<K> keyType, final K key)
	{
		return this.and(indexName, keyType).is(key);
	}
	
	/**
	 * Adds a condition to the query using the specified index name and key.
	 * This method helps in chaining query conditions with the logical AND operation.
	 *
	 * @param <K> the type of the key
	 * @param indexName the name of the index to be used in the query
	 * @param key the key value to be used for the condition
	 * @return the updated GigaQuery instance with the added condition
	 */
	@SuppressWarnings("unchecked")
	public default <K> GigaQuery<E> and(final String indexName, final K key)
	{
		return this.and(indexName, (Class<K>)key.getClass(), key);
	}
	
	/**
	 * Combines the current query conditions with the specified condition using a logical AND operator.
	 *
	 * @param condition the condition to be combined with the current query conditions
	 * @return a new GigaQuery object containing the combined conditions
	 */
	public GigaQuery<E> and(Condition<E> condition);
	
	/**
	 * Combines the current condition with another condition using an OR logical operator.
	 *
	 * @param stringIndexName the name of the string index to be used in the OR condition
	 * @return a ConditionBuilder instance representing the combined condition
	 */
	public default ConditionBuilder<E, String> or(final String stringIndexName)
	{
		return this.or(stringIndexName, String.class);
	}
	
	/**
	 * Constructs a condition using the logical OR operator with the specified index name and key type.
	 *
	 * @param indexName the name of the index to be used in the condition
	 * @param keyType the class type of the key associated with the specified index
	 * @return a ConditionBuilder object representing the OR condition
	 */
	public default <K> ConditionBuilder<E, K> or(final String indexName, final Class<K> keyType)
	{
		return this.or(IndexIdentifier.New(indexName, keyType));
	}
	
	/**
	 * Adds a condition to the query with an "OR" logical operator. This allows multiple conditions
	 * to be logically combined where at least one condition must evaluate to true.
	 *
	 * @param index the IndexIdentifier representing the indexed field to be used in the condition
	 * @return a ConditionBuilder instance to allow further refinement of the query conditions
	 */
	public <K> ConditionBuilder<E, K> or(IndexIdentifier<E, K> index);
	
	/**
	 * Adds an OR condition to the query for the specified index name
	 * and key.
	 *
	 * @param stringIndexName the name of the index to which the OR condition is to be applied
	 * @param key the key value that the OR condition should match
	 * @return the modified query object with the added OR condition
	 */
	public default GigaQuery<E> or(final String stringIndexName, final String key)
	{
		return this.or(stringIndexName).is(key);
	}
	
	/**
	 * Adds an "OR" condition to the query using the specified index name, key type, and key value.
	 *
	 * @param <K> the type of the key
	 * @param indexName the name of the index to use for the query
	 * @param keyType the class type of the key
	 * @param key the value of the key to match in the query
	 * @return the updated GigaQuery object with the "OR" condition applied
	 */
	public default <K> GigaQuery<E> or(final String indexName, final Class<K> keyType, final K key)
	{
		return this.or(indexName, keyType).is(key);
	}
	
	/**
	 * Adds an OR condition to the query based on the provided index name and key.
	 *
	 * @param <K> the type of the key
	 * @param indexName the name of the index to apply the OR condition
	 * @param key the key value to use for the OR condition
	 * @return an updated GigaQuery instance with the OR condition applied
	 */
	@SuppressWarnings("unchecked")
	public default <K> GigaQuery<E> or(final String indexName, final K key)
	{
		return this.or(indexName, (Class<K>)key.getClass(), key);
	}
	
	/**
	 * Combines the current query with the specified condition using a logical OR operator.
	 *
	 * @param condition the condition to be combined with the current query using OR
	 * @return a new GigaQuery instance representing the combined query
	 */
	public GigaQuery<E> or(Condition<E> condition);
	
	
	
	/**
	 * The {@code ConditionBuilder} interface provides methods for building query conditions
	 * for a particular data type. It allows the creation of queries based on key values,
	 * predicates, and sample data.
	 *
	 * @param <E> the entity type handled by the query
	 * @param <K> the key type used in building conditions
	 */
	public interface ConditionBuilder<E, K>
	{
		/**
		 * Constructs a query condition that matches entities using the specified key.
		 *
		 * @param key the key to be used for matching entities
		 * @return a {@code GigaQuery} object representing the resulting query condition
		 */
		public GigaQuery<E> is(K key);
		
		/**
		 * Constructs a query condition that excludes entities matching the specified key.
		 *
		 * @param key the key to be excluded from the resulting query condition
		 * @return a {@code GigaQuery} object representing the resulting query condition with the exclusion applied
		 */
		public GigaQuery<E> not(K key);
		
		/**
		 * Constructs a query condition that matches entities which have a similar key as the provided sample entity.
		 *
		 * @param sample the sample entity used to define the similarity condition
		 * @return a {@code GigaQuery} object representing the resulting query condition
		 */
		public GigaQuery<E> like(E sample);
		
		/**
		 * Constructs a query condition that excludes entities which have a similar key as the provided sample entity.
		 *
		 * @param sample the sample entity used to define the condition for exclusion
		 * @return a {@code GigaQuery} object representing the resulting query condition
		 */
		public GigaQuery<E> unlike(E sample);
		
		/**
		 * Constructs a query condition that matches entities using the specified keys.
		 *
		 * @param keys the keys to be used for matching entities
		 * @return a {@code GigaQuery} object representing the resulting query condition
		 */
		@SuppressWarnings("unchecked")
		public GigaQuery<E> in(K... keys);
		
		/**
		 * Constructs a query condition that matches entities based on a predicate applied to their keys.
		 *
		 * @param keyPredicate the predicate to apply to keys for matching entities
		 * @return a {@code GigaQuery} object representing the resulting query condition
		 */
		public GigaQuery<E> is(Predicate<? super K> keyPredicate);
		
		
		public final class Default<E, K> implements ConditionBuilder<E, K>
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////

			private final GigaQuery.Default<E>  parent;
			private final IndexIdentifier<E, K> index ;
			private final Condition.Linker      linker;
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			Default(
				final GigaQuery.Default<E>  parent,
				final IndexIdentifier<E, K> index ,
				final Condition.Linker      linker
			)
			{
				super();
				this.parent = parent;
				this.index  = index ;
				this.linker = linker;
			}
			 
			
			 
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////
			 
			@Override
			public GigaQuery<E> is(final K key)
			{
				return this.link(this.index.is(key));
			}

			@Override
			public GigaQuery<E> not(final K key)
			{
				return this.link(this.index.not(key));
			}

			@Override
			public GigaQuery<E> like(final E sample)
			{
				return this.link(this.index.like(sample));
			}

			@Override
			public GigaQuery<E> unlike(final E sample)
			{
				return this.link(this.index.unlike(sample));
			}

			@Override
			@SuppressWarnings("unchecked")
			public GigaQuery<E> in(final K... keys)
			{
				return this.link(this.index.in(keys));
			}

			@Override
			public GigaQuery<E> is(final Predicate<? super K> keyPredicate)
			{
				return this.link(this.index.is(keyPredicate));
			}
			
			private GigaQuery<E> link(final Condition<E> condition)
			{
				// parent either returns itself if it is a query or the newly created condition wrapping the parent
				this.parent.linkCondition(condition, this.linker);
				
				return this.parent;
			}
			 
		}
	}
	
	
	
	public class Default<E> implements GigaQuery<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final GigaMap.Default<E>      parent        ;
		private final IterationThreadProvider threadProvider;
		
		private Condition<E> condition;
		private long         idStart  ;
		private long         idBound  ;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final GigaMap.Default<E>      parent        ,
			final IterationThreadProvider threadProvider
		)
		{
			super();
			this.parent         = parent        ;
			this.threadProvider = threadProvider;
			this.idStart        = 0L            ;
			this.idBound        = Long.MAX_VALUE;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
				
		@Override
		public final GigaMap<E> parentMap()
		{
			return this.parent;
		}
		
		@Override
		public final GigaQuery<E> and(final Condition<E> condition)
		{
			// already created condition instance can be added right away with AND logic
			return this.linkCondition(condition.complete(), Condition.CREATOR_AND);
		}

		@Override
		public GigaQuery<E> or(final Condition<E> condition)
		{
			// already created condition instance can be added right away with OR logic
			return this.linkCondition(condition.complete(), Condition.CREATOR_OR);
		}

		@Override
		public GigaQuery<E> idStart(final long idStart)
		{
			this.idStart = idStart;
			
			return this;
		}
		
		@Override
		public GigaQuery<E> idBound(final long idBound)
		{
			this.idBound = idBound;
			
			return this;
		}
		
		protected GigaQuery<E> linkCondition(final Condition<E> condition, final Condition.Linker linker)
		{
			// The first ever passed condition becomes the current condition as a standalone, no linking needed.
			this.condition = this.condition != null
				? this.condition.linkCondition(condition, linker)
				: condition
			;
			
			// query always returns itself after linking, NOT the created linking condition.
			return this;
		}
		
		@Override
		public <C extends EntryConsumer<? super E>> C executeWithId(final C entryConsumer)
		{
			this.internalExecute(BitmapResult.Resolver(this.parent, entryConsumer));
			
			return entryConsumer;
		}
		
		@Override
		public GigaIterator<E> iterator()
		{
			// convenience variant for a query without a condition.
			if(this.condition == null)
			{
				return this.parent.iterator();
			}
			
			// by default, the parent GigaMap does resolving on its own.
			return this.iterator(this.parent);
		}
		
		public GigaIterator<E> iterator(final BitmapResult.Resolver<E> resolver)
		{
			return this.parent.createIterator(this.condition, resolver, this.threadProvider);
		}
				
		@Override
		public <R extends BitmapResult.Resolver<E>> R resolve(final R resolver)
		{
			if(resolver.parent() != this.parent)
			{
				throw new IllegalArgumentException();
			}
			this.internalExecute(resolver);
			
			return resolver;
		}
		
		@Override
		public <C extends Consumer<? super E>> C execute(final C consumer)
		{
			this.parent.executeInReadOnlyMode(this.condition, this.idStart, this.idBound, consumer);
			
			return consumer;
		}
		

		@Override
		@SuppressWarnings("unchecked")
		public final void execute(final Consumer<? super E>... consumers)
		{
			if(XArrays.hasNoContent(consumers))
			{
				throw new IllegalArgumentException();
			}
			this.parent.executeReadOnly(this.condition, this.idStart, this.idBound, consumers, this.threadProvider);
		}
		
		private void internalExecute(final BitmapResult.Resolver<E> resolver)
		{
			synchronized(this.parentMap())
			{
				try(final GigaIterator<E> iterator = this.iterator(resolver))
				{
					while(iterator.hasNext())
					{
						// iterator calls the linked resolver internally
						iterator.next();
					}
				}
			}
		}

		protected <K> ConditionBuilder<E, K> createConditionBuilder(
			final IndexIdentifier<E, K> indexIdentifier,
			final Condition.Linker      linker
		)
		{
			return new ConditionBuilder.Default<>(this, indexIdentifier, linker);
		}

		
		// not necessary for querying the parent map, but a nice usage on the side.
		@Override
		public boolean test(final E entity)
		{
			return this.condition.test(entity);
		}
		
		@Override
		public <K> ConditionBuilder<E, K> and(final IndexIdentifier<E, K> indexIdentifier)
		{
			return this.createConditionBuilder(indexIdentifier, Condition.CREATOR_AND);
		}
		
		@Override
		public <K> ConditionBuilder<E, K> or(final IndexIdentifier<E, K> indexIdentifier)
		{
			return this.createConditionBuilder(indexIdentifier, Condition.CREATOR_OR);
		}
		
	}
	
	public static int threadsNone(final GigaMap<?> parent, final BitmapResult[] results)
	{
		return 0;
	}
	
	public static int threads1(final GigaMap<?> parent, final BitmapResult[] results)
	{
		return 1;
	}
	
	public static int threads2(final GigaMap<?> parent, final BitmapResult[] results)
	{
		return 2;
	}
	
	public static int threads4(final GigaMap<?> parent, final BitmapResult[] results)
	{
		return 4;
	}
	
	public static int threads6(final GigaMap<?> parent, final BitmapResult[] results)
	{
		return 6;
	}
	
	public static int threads8(final GigaMap<?> parent, final BitmapResult[] results)
	{
		return 8;
	}
	
	public static int threads12(final GigaMap<?> parent, final BitmapResult[] results)
	{
		return 12;
	}
	
	public static int threads16(final GigaMap<?> parent, final BitmapResult[] results)
	{
		return 16;
	}
	
}
