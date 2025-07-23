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

import org.eclipse.store.gigamap.exceptions.BitmapIndexException;
import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.collections.types.XImmutableList;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.persistence.types.Unpersistable;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.serializer.util.X;

import java.util.function.Consumer;
import java.util.function.Predicate;


/**
 * The BitmapIndex interface defines a specialized index structure
 * for entities, allowing efficient querying and management based
 * on specific keys. It supports methods for optimizing memory usage
 * and performance, along with hierarchical management via a parent
 * index structure.
 *
 * @param <E> The type of elements/entities managed by the index.
 * @param <K> The type of keys used to index entities.
 */
public interface BitmapIndex<E, K> extends IndexIdentifier<E, K>, GigaIndex<E>
{
	/**
	 * Returns the parent BitmapIndices associated with this BitmapIndex.
	 *
	 * @return the parent BitmapIndices instance of this BitmapIndex
	 */
	public BitmapIndices<E> parent();
	
	@Override
	public Indexer<? super E, K> indexer();
	
	@Override
	public String name();
	
	@Override
	public default Class<K> keyType()
	{
		return this.indexer().keyType();
	}
	
	/**
	 * Compares the keys of two entities for equality. If both entities are null, it returns true.
	 * If only one entity is null, it returns false. Otherwise, it uses the indexer and a hash-based
	 * {@link org.eclipse.serializer.hashing.HashEqualator} to determine if the keys of the two entities are equal.
	 *
	 * @param entity1 the first entity to compare
	 * @param entity2 the second entity to compare
	 * @return true if the keys of both entities are equal, or if both entities are null;
	 *         false otherwise
	 */
	public default boolean equalKeys(final E entity1, final E entity2)
	{
		if(entity1 == null)
		{
			return entity2 == null;
		}
		else if(entity2 == null)
		{
			return false;
		}
		
		final K key1 = this.indexer().index(entity1);
		final K key2 = this.indexer().index(entity2);
		
		return this.indexer().hashEqualator().equal(key1, key2);
	}
	
	/**
	 * Searches the index using the provided predicate and returns the result as a BitmapResult.
	 * The predicate is applied to the keys in the index to determine which entries should be included in the result.
	 *
	 * @param predicate the condition that determines whether a key in the index matches the search criteria.
	 *                  Must not be null.
	 * @return a BitmapResult containing the matching entries as determined by the given predicate.
	 */
	public BitmapResult search(Predicate<? super K> predicate);
	
	/**
	 * Ensures that the index' internal data is optimized for small memory usage.<br>
	 * E.g. by turning data into a compressed form.
	 */
	public void ensureOptimizedSize();
	
	/**
	 * Ensures that the index' internal data is optimized for performing operations (reading/writing) quickly.<br>
	 * E.g. by holding data in an uncompressed form.
	 */
	public void ensureOptimizedPerformance();
	
	/**
	 * Creates and returns an instance of the {@code BitmapIndex.Statistics<E>}, which provides
	 * detailed statistical information about this BitmapIndex. The statistics can include
	 * data such as entry count, total memory size used by the data, and other key metrics.
	 *
	 * @return an instance of {@code BitmapIndex.Statistics<E>} containing statistical details
	 *         about the current state and structure of the {@code BitmapIndex}.
	 */
	public Statistics<E> createStatistics();
	
	/**
	 * Iterates over all the keys in the index and applies the given logic to each key.
	 * This method accepts a {@link Consumer} that defines the operation to perform on each key.
	 *
	 * @param logic the consumer logic to be applied to each key; must not be null
	 * @return the same {@link Consumer} instance passed as the parameter, after it has been applied to all keys
	 */
	public <C extends Consumer<? super K>> C iterateKeys(C logic);
	
	
	@Override
	public default boolean test(final E entity, final K key)
	{
		final Indexer<? super E, K> indexer = this.indexer();
		final K indexedKey = indexer.index(entity);

		return indexer.hashEqualator().equal(indexedKey, key);
	}
		
	
	@SuppressWarnings("unchecked") // in case of this, S == E, but the compiler cannot understand that.
	@Override
	public default <S extends E> Internal<S, K> resolveFor(final BitmapIndices.Internal<S> parent)
	{
		// bitmap instance itself does not need to be resolved, but instead validated.
		if(parent == this.parent())
		{
			return (Internal<S, K>)this;
		}
		
		throw new BitmapIndexException("Invalid parent.", this);
	}
	
	/**
	 * Retrieves the {@link ChangeHandler} associated with the specified entity.
	 * The ChangeHandler provides mechanisms to handle changes in the index,
	 * such as removal or updates triggered by modifications to the entity.
	 *
	 * @param entity the entity whose ChangeHandler is to be retrieved; must not be null
	 * @return a {@link ChangeHandler} instance applicable to the specified entity
	 */
	public ChangeHandler getChangeHandler(E entity);
	
	/**
	 * Creates a new BitmapIndex.Category instance using the provided array of Indexer instances.
	 *
	 * @param <E> the type of elements contained in the indices
	 * @param indexers an array of Indexer instances to initialize the category; must not be null
	 * @return a new instance of BitmapIndex.Category containing the specified indexers
	 */
	@SafeVarargs
	public static <E> Category<E> Indices(final Indexer<E, ?>... indexers)
	{
		return new Category.Default<>(X.ConstList(indexers));
	}
	
	public static <E> Category<E> Category()
	{
		return new Category.Default<>();
	}
	
	
	// just to hide the mutating methods a little bit from the publicly used type.
	public interface Internal<E, K> extends BitmapIndex<E, K>
	{
		public void internalAdd(long entityId, E entity);
		
		public void internalAddAll(long firstEntityId, Iterable<? extends E> entities);
		
		public void internalAddAll(long firstEntityId, E[] entities);
		
		public void internalRemove(long entityId, E entity);
		
		public void internalRemoveAll();
		
		public boolean internalContains(E entity);
		
		public BitmapResult internalQuery(K key);
		
		public void clearStateChangeMarkers();
	}
	
	
	/**
	 * An abstract class that provides the foundation for indexing and managing
	 * entities within a parent structure, supporting features such as state change tracking
	 * and indexing mechanism customization. This class is designed to be extended by specific
	 * implementations to define precise behaviors for managing entries, keys, and their
	 * corresponding indexing logic.
	 * <p>
	 * A key characteristic of this class is its reliance on indexers and bitmap entries
	 * to organize and access its data, along with several methods that must be implemented
	 * by subclasses to handle specific indexing tasks.
	 * <p>
	 * Various utility methods are provided for managing indexing operations, ensuring optimal
	 * performance, optimizing state sizes, and maintaining proper synchronization with the
	 * parent structures.
	 * <p>
	 * This abstract class requires concrete implementations to define detailed logic related
	 * to:
	 * <ul>
	 * <li>Key generation for entities.</li>
	 * <li>How entries should be handled, added, removed, and optimized.</li>
	 * <li>Methods for ensuring the structure conforms to its required configuration.</li>
	 * </ul>
	 *
	 * @param <E> The type of the elements/entities being indexed.
	 * @param <I> The type of the indexable entities (e.g., entities that can be indexed).
	 * @param <K> The type of the keys used for indexing.
	 */
	public abstract class Abstract<E, I, K>
	extends AbstractStateChangeFlagged
	implements
	Unpersistable // all index implementations must have a dedicated type handler which then overrides the persistability check.
	{
		///////////////////////////////////////////////////////////////////////////
		// constants //
		//////////////
		
		static final BitmapResult EMPTY_RESULT = new BitmapResult.Empty();
				
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		final BitmapIndices<E> parent;
		final String           name  ;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Abstract(
			final BitmapIndices<E> parent      ,
			final String           name        ,
			final boolean          stateChanged
		)
		{
			super(stateChanged);
			this.parent = parent;
			this.name   = name  ;
		}
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
				
		public final GigaMap<E> parentMap()
		{
			return this.parent.parentMap();
		}
		
		public BitmapIndices<E> parent()
		{
			return this.parent;
		}
						
		public abstract void iterateEntries(Consumer<? super BitmapEntry<?, ?, ?>> logic);
		
		public abstract int entryCount();
		
		protected abstract K indexEntity(I entity);
		
		public abstract <C extends Consumer<? super K>> C iterateKeys(C logic);
		
		public abstract boolean internalContains(I entity);
		
		
		public void ensureOptimizedSize()
		{
			// needs its own locking logic as it can be called from external logic.
			synchronized(this.parentMap())
			{
				this.iterateEntries(BitmapEntry::ensureCompressed);
			}
		}

		public void ensureOptimizedPerformance()
		{
			// needs its own locking logic as it can be called from external logic.
			synchronized(this.parentMap())
			{
				this.iterateEntries(BitmapEntry::ensureDecompressed);
			}
		}

		public final String name()
		{
			return this.name;
		}
				
		public abstract Indexer<? super I, K> indexer();
		
		public void internalAdd(final long entityId, final I entity)
		{
			this.internalAddToEntry(entityId, entity);
			this.markStateChangeChildren();
			
			// no storing here. This is done efficiently via GigaMap's internal state change marking.
		}
		
		public void internalAddAll(final long firstEntityId, final Iterable<? extends I> entities)
		{
			long currentEntityId = firstEntityId;
			for(final I entity : entities)
			{
				this.internalAddToEntry(currentEntityId++, entity);
			}
			this.markStateChangeChildren();
		}
		
		public void internalAddAll(final long firstEntityId, final I[] entities)
		{
			long currentEntityId = firstEntityId;
			for(final I entity : entities)
			{
				this.internalAddToEntry(currentEntityId++, entity);
			}
			this.markStateChangeChildren();
		}
		
		protected abstract void internalAddToEntry(long entityId, I indexable);
		
		public BitmapEntry<E, I, K> internalEnsureEntry(final I indexable)
		{
			// may never be called. Either override and implement or implement a logic that does not call this method.
			throw new Error();
		}
		
		public final void internalRemoveFromEntry(
			final long                 entityId,
			final BitmapEntry<E, I, K> entry
		)
		{
			if(entry != null && entry.remove(entityId))
			{
				if(entry.isEmpty())
				{
					this.removeEntry(entry);
					this.markStateChangeInstance();
				}
				else
				{
					this.markStateChangeChildren();
				}
			}
		}
		
		public final void markStateChangeChildren2()
		{
			this.markStateChangeChildren();
		}
		
		public final void clearStateChangeMarkers2()
		{
			this.clearStateChangeMarkers();
		}
		
		@Override
		protected void storeChangedChildren(final Storer storer)
		{
			// keys can never change, new entries are covered by storing the whole instance. Only entries are relevant here.
			this.iterateEntries(entry ->
			{
				if(!entry.isChangedAndNotNew())
				{
					return;
				}
				entry.storeChildren(storer);
			});
		}
		
		@Override
		protected void clearChildrenStateChangeMarkers()
		{
			this.iterateEntries(BitmapEntry::clearStateChangeMarkers);
		}
		
		protected abstract void removeEntry(BitmapEntry<E, I, K> entry);
						
	}
	
	
	interface TopLevel<E, K> extends Internal<E, K>
	{
		public void iterateEntries(Consumer<? super BitmapEntry<?, ?, ?>> logic);
				
		@Override
		public default Statistics<E> createStatistics()
		{
			// lock required to enclose multiple calls
			synchronized(this.parentMap())
			{
				this.ensureOptimizedSize();
				
				return DefaultStatistics.createStatistics(this);
			}
		}
		
		@Override
		public default boolean isSuitableAsUniqueConstraint()
		{
			// all top level indices should be suitable as unique constraints unless explicitely defined otherwise (e.g. default implementation)
			return true;
		}
	}
	
	
	/**
	 * Represents a category of bitmap indices for managing and organizing entities.
	 * Extends the {@link IndexCategory} interface with a specific implementation of
	 * {@link BitmapIndices}. Provides additional functionality specific to managing
	 * bitmap indices.
	 *
	 * @param <E> the type of entities being managed
	 */
	public interface Category<E> extends IndexCategory<E, BitmapIndices<E>>
	{
		@Override
		public Class<BitmapIndices<E>> indexType();
				
		
		
		public class Default<E> implements Category<E>
		{
			private final Iterable<? extends Indexer<E, ?>> initialIndicesIndexers;
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			Default()
			{
				this(X.empty());
			}
			
			Default(final Iterable<? extends Indexer<E, ?>> initialIndicesIndexers)
			{
				super();
				this.initialIndicesIndexers = initialIndicesIndexers;
			}
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////
			
			@SuppressWarnings({"unchecked", "rawtypes"})
			@Override
			public final Class<BitmapIndices<E>> indexType()
			{
				return (Class)BitmapIndices.class;
			}

			@Override
			public final BitmapIndices<E> createIndexGroup(final GigaMap<E> gigaMap)
			{
				if(!(gigaMap instanceof GigaMap.Internal))
				{
					throw new IllegalArgumentException("gigaMap must be a GigaMap.Internal instance");
				}
				
				final BitmapIndices.Default<E> indices = new BitmapIndices.Default<>((GigaMap.Internal<E>)gigaMap);
				
				if(this.initialIndicesIndexers != null)
				{
					for(final Indexer<E, ?> indexer : this.initialIndicesIndexers)
					{
						indices.add(indexer);
					}
				}
				
				return indices;
			}
			
		}
		
	}
	
	
	/**
	 * Represents statistical information about a {@code BitmapIndex}.
	 * <p>
	 * This interface provides methods to access and compute statistical data,
	 * such as entry counts, memory usage, and other key metrics related to the
	 * structure and content of the associated {@code BitmapIndex}.
	 *
	 * @param <E> the type of elements in the {@code BitmapIndex} associated
	 *            with this statistics instance
	 */
	public interface Statistics<E>
	{
		/**
		 * Retrieves the parent {@code BitmapIndex} associated with this instance.
		 *
		 * @return the parent {@code BitmapIndex} instance, which provides access
		 *         to the underlying data structure and indexing mechanisms.
		 */
		public BitmapIndex<E, ?> parent();
		
		/**
		 * Retrieves the {@code Indexer} associated with the parent {@code BitmapIndex}.
		 * The {@code Indexer} is responsible for providing key extraction logic that
		 * defines how entities are indexed within the parent {@code BitmapIndex}.
		 *
		 * @return the {@code Indexer} instance from the parent {@code BitmapIndex},
		 *         which is responsible for handling key-based operations and indexing.
		 */
		public default Indexer<? super E, ?> indexer()
		{
			return this.parent().indexer();
		}
		
		/**
		 * Retrieves the {@link Class} type of the key used in the parent {@code BitmapIndex}.
		 * This method delegates to the keyType() method of the associated {@code Indexer}.
		 *
		 * @return the {@code Class} object representing the type of keys used within
		 *         the parent {@code BitmapIndex}.
		 */
		public default Class<?> keyType()
		{
			return this.indexer().keyType();
		}
		
		/**
		 * Retrieves a list of key-value pairs, where each key is of type {@code Object}
		 * and the value corresponds to an instance of {@link KeyStatistics}.
		 *
		 * @return an immutable list containing key-value pairs, where the keys represent
		 *         specific identifiers and the values provide associated statistical
		 *         data such as memory size or other metrics.
		 */
		public XImmutableList<? extends KeyValue<Object, ? extends KeyStatistics>> entries();
		
		/**
		 * Returns the number of key-value entry pairs within the {@code entries()} list.
		 * <p>
		 * This method calculates the size of the list of entries, where each
		 * entry represents a specific key-value pair with associated statistical data.
		 *
		 * @return the total number of entries present in the {@code entries()} list
		 */
		public default int entryCount()
		{
			return this.entries().intSize();
		}
		
		/**
		 * Computes and returns the total memory size, in bytes, used by the data
		 * associated with all entries in the {@code BitmapIndex}.
		 * <p>
		 * This method aggregates the memory usage of data stored or indexed within
		 * the structure, providing a comprehensive view of the total memory footprint.
		 *
		 * @return the total memory size, in bytes, used by the data in the associated {@code BitmapIndex}
		 */
		public int totalDataMemorySize();
		
		/**
		 * Assembles a hierarchical representation of data within the provided {@code VarString}.
		 * This method is shorthand for the overload, using a default maximum hierarchy level.
		 *
		 * @param vs the {@code VarString} instance that will be modified or enriched to represent the data structure.
		 * @return the modified or constructed {@code VarString} containing the structured representation of the data.
		 */
		public default VarString assemble(final VarString vs)
		{
			return this.assemble(vs, Integer.MAX_VALUE);
		}
		
		/**
		 * Assembles a hierarchical representation of data within the provided {@code VarString}.
		 * The method organizes and processes content into a structured format based on the specified levels.
		 *
		 * @param vs the {@code VarString} instance that will be modified or enriched to represent the data structure.
		 * @param levels the number of hierarchical levels to include or process within the assembled data.
		 * @return the modified or constructed {@code VarString} containing the structured representation of the data.
		 */
		public VarString assemble(VarString vs, int levels);
		
	}
	
	
	/**
	 * The KeyStatistics interface provides a method for retrieving statistical data
	 * pertaining to memory usage within a structure or entity.
	 * It is designed to represent key statistics related to memory size
	 */
	public interface KeyStatistics
	{
		public int totalDataMemorySize();
	}
	
}
