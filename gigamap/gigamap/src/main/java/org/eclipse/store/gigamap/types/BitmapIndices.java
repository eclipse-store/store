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

import org.eclipse.store.gigamap.exceptions.BitmapIndicesException;
import org.eclipse.store.gigamap.exceptions.ConstraintViolationException;
import org.eclipse.store.gigamap.exceptions.UniqueConstraintViolationExceptionBitmap;
import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.ConstHashEnum;
import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.collections.types.XGettingEnum;
import org.eclipse.serializer.collections.types.XGettingTable;
import org.eclipse.serializer.collections.types.XImmutableEnum;
import org.eclipse.serializer.collections.types.XIterable;
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.serializer.util.X;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Mutable bitmap index registry and manager.
 *
 * @param <E> the entity type
 */
public interface BitmapIndices<E>
extends
IndexGroup.Internal<E>,
UniqueConstraints<E>,
XIterable<BitmapIndex<E, ?>>,
Iterable<KeyValue<String, ? extends BitmapIndex<E, ?>>>
{
	/**
	 * Adds an {@link Indexer} to this group.
	 * <p>
	 * Shortcut for
	 * <code>
	 * add(indexer.name(), indexer)
	 * </code>
	 *
	 * @param <K> the key type
	 * @param indexer the indexing logic
	 * @return the resuling index
	 * @throws IllegalStateException if an indexer with the same name is already registered
	 */
	public <K> BitmapIndex<E, K> add(final Indexer<? super E, K> indexer);
	
	/**
	 * Adds all indexers to this group.
	 * <p>
	 * For details see {@link #add(Indexer)}.
	 *
	 * @param indexers the new indexing logic
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public default BitmapIndices<E> addAll(final Indexer<? super E, ?>... indexers)
	{
		return this.addAll(X.List(indexers));
	}
	
	/**
	 * Adds all indexers to this group.
	 * <p>
	 * For details see {@link #add(Indexer)}.
	 *
	 * @param indexers the new indexing logic
	 * @return this
	 */
	public BitmapIndices<E> addAll(Iterable<? extends Indexer<? super E, ?>> indexers);
	
	/**
	 * Ensures that an index exists in this group.
	 *
	 * @param <K> the key type
	 * @param indexer the indexing logic
	 * @return the resuling index
	 */
	public <K> BitmapIndex<E, K> ensure(final Indexer<? super E, K> indexer);

	/**
	 * Ensures that indexes exist in this group.
	 * <p>
	 * For details see {@link #ensure(Indexer)}
	 *
	 * @param indexers the indexing logic
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public default BitmapIndices<E> ensureAll(final Indexer<? super E, ?>... indexers)
	{
		for(final Indexer<? super E, ?> indexer : indexers)
		{
			this.ensure(indexer);
		}
		
		return this;
	}
	
	/**
	 * Ensures that indexes exist in this group.
	 * <p>
	 * For details see {@link #ensure(Indexer)}
	 *
	 * @param indexers the indexing logic
	 * @return this
	 */
	public default BitmapIndices<E> ensureAll(final Iterable<? extends Indexer<? super E, ?>> indexers)
	{
		for(final Indexer<? super E, ?> indexer : indexers)
		{
			this.ensure(indexer);
		}
		
		return this;
	}
	
	/**
	 * Ensures that all indices are optimized for small memory usage.
	 *
	 * @see #ensureOptimizedPerformance()
	 */
	public void ensureOptimizedSize();
	
	/**
	 * Ensures that all indices are optimized for best runtime performance.
	 *
	 * @see #ensureOptimizedSize()
	 */
	public void ensureOptimizedPerformance();
	
	/**
	 * Gets the registered indexing logic with the given name and type, or <code>null</code>.
	 *
	 * @param <K> the key type
	 * @param <I> the indexer type
	 * @param indexerType the type of the indexer to search
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	public <K, I extends Indexer<E, K>> I getIndexer(Class<I> indexerType, String name);
	
	/**
	 * Retrieves an indexer associated with the specified key type and name.
	 *
	 * @param <K> The type of the key used by the indexer.
	 * @param keyType The class type of the key.
	 * @param name the name of the indexer to search
	 * @return The indexer corresponding to the specified key type and name.
	 */
	@SuppressWarnings("unchecked")
	public default <K> Indexer<E, K> getIndexerForKey(final Class<K> keyType, final String name)
	{
		return (Indexer<E, K>)this.getIndexer(Indexer.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerBoolean<E> getIndexerBoolean(final String name)
	{
		return this.getIndexer(IndexerBoolean.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerCharacter<E> getIndexerCharacter(final String name)
	{
		return this.getIndexer(IndexerCharacter.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerByte<E> getIndexerByte(final String name)
	{
		return this.getIndexer(IndexerByte.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerShort<E> getIndexerShort(final String name)
	{
		return this.getIndexer(IndexerShort.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerInteger<E> getIndexerInteger(final String name)
	{
		return this.getIndexer(IndexerInteger.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerLong<E> getIndexerLong(final String name)
	{
		return this.getIndexer(IndexerLong.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerFloat<E> getIndexerFloat(final String name)
	{
		return this.getIndexer(IndexerFloat.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerDouble<E> getIndexerDouble(final String name)
	{
		return this.getIndexer(IndexerDouble.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerString<E> getIndexerString(final String name)
	{
		return this.getIndexer(IndexerString.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerLocalDate<E> getIndexerLocalDate(final String name)
	{
		return this.getIndexer(IndexerLocalDate.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerLocalTime<E> getIndexerLocalTime(final String name)
	{
		return this.getIndexer(IndexerLocalTime.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerLocalDateTime<E> getIndexerLocalDateTime(final String name)
	{
		return this.getIndexer(IndexerLocalDateTime.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default IndexerYearMonth<E> getIndexerYearMonth(final String name)
	{
		return this.getIndexer(IndexerYearMonth.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default BinaryIndexerUUID<E> getIndexerUUID(final String name)
	{
		return this.getIndexer(BinaryIndexerUUID.class, name);
	}
	
	/**
	 * Gets the registered indexing logic with the given name, or <code>null</code>.
	 *
	 * @param name the name of the indexer to search
	 * @return the found indexer or <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public default <K> IndexerMultiValue<E, K> getIndexerMultiValue(final String name)
	{
		return this.getIndexer(IndexerMultiValue.class, name);
	}
	
	/**
	 * Gets the registered String index with given name, or <code>null</code>.
	 * <p>
	 * This is a shortcut for <code>get(String.class, name);</code>.
	 *
	 * @param name the name of the index to search
	 * @return the found index or <code>null</code>
	 */
	public default BitmapIndex<E, String> get(final String name)
	{
		return this.get(String.class, name);
	}

	/**
	 * Gets the registered index with given key type and name, or <code>null</code>.
	 *
	 * @param <K> the key type
	 * @param keyType the key type of the index to search
	 * @param name the name of the index to search
	 * @return the found index or <code>null</code>
	 */
	public <K> BitmapIndex<E, K> get(Class<K> keyType, String name);
	
	
	public interface Internal<E> extends BitmapIndices<E>
	{
		public <K> BitmapIndex.Internal<E, K> internalGet(Class<K> keyType, String indexName);
	}
	
	/**
	 * Get the registered identity indices to distinctly identify entities.
	 *
	 * @return all registered indentity indices, might be <code>null</code>
	 */
	public XImmutableEnum<? extends BitmapIndex<E, ?>> identityIndices();
	
	/**
	 * Sets the identity indices to distinctly identify entities.
	 *
	 * @param identityIndices the new, non-empty, identity indices
	 * @return this
	 */
	public BitmapIndices<E> setIdentityIndices(XGettingEnum<? extends IndexIdentifier<? super E, ?>> identityIndices);
	
	/**
	 * Sets the identity indices to distinctly identify entities.
	 *
	 * @param identityIndices the new, non-empty, identity indices
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public default <I extends IndexIdentifier<? super E, ?>> BitmapIndices<E> setIdentityIndices(final I... identityIndices)
	{
		return this.setIdentityIndices(X.Enum(identityIndices));
	}
	
	public XImmutableEnum<? extends BitmapIndex<E, ?>> uniqueConstraints();
	
	/**
	 * Creates statistics of this index group for debugging or analyzing purposes.
	 *
	 * @return the statistics of this index group
	 */
	public Statistics<E> createStatistics();
	
	public void accessIndices(Consumer<? super XGettingTable<String, ? extends BitmapIndex<E, ?>>> logic);
	
	public void accessUniqueIndices(Consumer<? super XImmutableEnum<? extends BitmapIndex<E, ?>>> logic);
	
	
	public final class Default<E> extends AbstractStateChangeFlagged implements Internal<E>
	{
		static BinaryTypeHandler<Default<?>> provideTypeHandler()
		{
			return BinaryHandlerBitmapIndicesDefault.New();
		}
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final GigaMap.Internal<E> parent;
		
		final EqHashTable<String, BitmapIndex.Internal<E, ?>> bitmapIndices;
		
		XImmutableEnum<? extends BitmapIndex<E, ?>> identityIndices = null;
		
		XImmutableEnum<BitmapIndex.Internal<E, ?>> uniqueConstraints = null;
		
		private transient BitmapIndex.Internal<E, ?>[] cachedIndices           ;
		private transient boolean[]                    cachedIsUniqueIndex     ;
		private transient ChangeHandler[]              cachedPrevChangeHandlers;
		private transient ChangeHandler[]              cachedNewChangeHandlers ;
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		protected Default(final GigaMap.Internal<E> parent)
		{
			this(parent, EqHashTable.New(), true);
		}
		
		protected Default(
			final GigaMap.Internal<E>                           parent       ,
			final EqHashTable<String, BitmapIndex.Internal<E, ?>> bitmapIndices,
			final boolean                                        stateChanged
		)
		{
			super(stateChanged);
			this.parent        = parent       ;
			this.bitmapIndices = bitmapIndices;
		}
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		private void createChangeHandlers(
			final ChangeHandler[] changeHandlers,
			final E                                      entity
		)
		{
			for(int i = 0; i < this.cachedIndices.length; i++)
			{
				changeHandlers[i] = this.cachedIndices[i].getChangeHandler(entity);
			}
		}
		
		private void clearCachedChangeHandlers()
		{
			for(int i = 0; i < this.cachedPrevChangeHandlers.length; i++)
			{
				this.cachedPrevChangeHandlers[i] = null;
				this.cachedNewChangeHandlers[i]  = null;
			}
		}
		
		@SuppressWarnings("unchecked")
		void rebuildCache()
		{
			final int indexCount = this.bitmapIndices.intSize();
			
			this.cachedIndices            = new BitmapIndex.Internal[indexCount];
			this.cachedIsUniqueIndex      = new boolean[indexCount];
			this.cachedPrevChangeHandlers = new ChangeHandler[indexCount];
			this.cachedNewChangeHandlers  = new ChangeHandler[indexCount];
			
			int i = 0;
			for(final BitmapIndex.Internal<E, ?> index : this.bitmapIndices.values())
			{
				this.cachedIndices[i]       = index;
				this.cachedIsUniqueIndex[i] = this.isUniqueConstraint(index);
				i++;
				// changeHandler arrays stay empty until needed.
			}
		}
		
		@Override
		public final GigaMap.Internal<E> parentMap()
		{
			return this.parent;
		}
		
		protected final EqHashTable<String, BitmapIndex.Internal<E, ?>> bitmapIndices()
		{
			return this.bitmapIndices;
		}
		
		protected final int indexCount()
		{
			return this.bitmapIndices.intSize();
		}
		
		@Override
		public final synchronized boolean isViolated(final long entityId, final E replacedEntity, final E entity)
		{
			return this.internalCheckViolation(entityId, replacedEntity, entity, false) != null;
		}
		
		@Override
		public final synchronized void check(final long entityId, final E replacedEntity, final E entity)
		{
			final RuntimeException result = this.internalCheckViolation(entityId, replacedEntity, entity, true);
			if(result == null)
			{
				return;
			}
			
			throw result;
		}
		
		/*
		 * While logic-flags are kind of goofy, there really is no other choice, if ...
		 * ... the violated index shall be contained in the exception, if needed.
		 * ... there should not be redundant code for both variants (querying and checking).
		 * ... there should not be unnecessary instantiations (as they are checked potentially for millions of entities)
		 */
		private RuntimeException internalCheckViolation(
			final long    entityId       ,
			final E       replacedEntity ,
			final E       entity         ,
			final boolean createException
		)
		{
			if(this.uniqueConstraints == null)
			{
				return null;
			}
			
			for(final BitmapIndex.Internal<E, ?> index : this.uniqueConstraints)
			{
				if(index.equalKeys(replacedEntity, entity))
				{
					// if old and new entity have equal keys, a replacement cannot be a unique violation.
					continue;
				}
				
				if(index.internalContains(entity))
				{
					if(createException)
					{
						throw new UniqueConstraintViolationExceptionBitmap(entityId, replacedEntity, entity, index);
					}
					return X.BREAK();
				}
			}
			
			return null;
		}
		
		@Override
		public final void internalAdd(final long entityId, final E entity)
		{
			for(final BitmapIndex.Internal<E, ?> index : this.bitmapIndices.values())
			{
				index.internalAdd(entityId, entity);
			}
			this.markStateChangeChildren();
		}
		
		@Override
		public final void internalAddAll(final long firstEntityId, final Iterable<? extends E> entities)
		{
			for(final BitmapIndex.Internal<E, ?> index : this.bitmapIndices.values())
			{
				index.internalAddAll(firstEntityId, entities);
			}
			this.markStateChangeChildren();
		}
		
		@Override
		public final void internalAddAll(final long firstEntityId, final E[] entities)
		{
			for(final BitmapIndex.Internal<E, ?> index : this.bitmapIndices.values())
			{
				index.internalAddAll(firstEntityId, entities);
			}
			this.markStateChangeChildren();
		}
		
		@Override
		public final void internalRemove(final long entityId, final E entity)
		{
			for(final BitmapIndex.Internal<E, ?> index : this.bitmapIndices.values())
			{
				index.internalRemove(entityId, entity);
			}
			this.markStateChangeChildren();
		}
		
		@Override
		public void internalRemoveAll()
		{
			for(final BitmapIndex.Internal<E, ?> index : this.bitmapIndices.values())
			{
				index.internalRemoveAll();
			}
			this.markStateChangeChildren();
		}

		@Override
		public <K> BitmapIndex<E, K> add(final Indexer<? super E, K> indexer)
		{
			synchronized(this.parentMap())
			{
				this.validateIndexToAdd(indexer);
				
				final BitmapIndex.Internal<E, K> index = indexer.createFor(this);
				this.internalAddBitmapIndex(index);
				this.rebuildCache();
				
				return index;
			}
		}
		
		@Override
		public final BitmapIndices<E> addAll(final Iterable<? extends Indexer<? super E, ?>> indexers)
		{
			synchronized(this.parentMap())
			{
				for(final Indexer<? super E, ?> indexer : indexers)
				{
					this.validateIndexToAdd(indexer);
				}
				
				for(final Indexer<? super E, ?> indexer : indexers)
				{
					final BitmapIndex.Internal<E, ?> index = indexer.createFor(this);
					this.internalAddBitmapIndex(index);
				}
				this.rebuildCache();
			}
			
			return this;
		}
		
		@Override
		public <K> BitmapIndex<E, K> ensure(final Indexer<? super E, K> indexer)
		{
			return this.ensureBitmapIndex(indexer);
		}
		
		/**
		 * Ensures that all indices are optimized for small memory usage ("consolidated").
		 */
		@Override
		public final void ensureOptimizedSize()
		{
			synchronized(this.parentMap())
			{
				this.iterate(BitmapIndex::ensureOptimizedSize);
			}
		}
		
		/**
		 * Ensures that all indices are optimized for adding new entries as fast as possible.
		 */
		@Override
		public final void ensureOptimizedPerformance()
		{
			synchronized(this.parentMap())
			{
				this.iterate(BitmapIndex::ensureOptimizedPerformance);
			}
		}
		
		@Override
		public void internalPrepareIndicesUpdate(final E replacedEntity)
		{
			if(this.bitmapIndices.isEmpty())
			{
				// no-op
				return;
			}

			// Derive state handlers for the state of the replaced entity.
			this.createChangeHandlers(this.cachedPrevChangeHandlers, replacedEntity);
		}
		
		@Override
		public void internalFinishIndicesUpdate()
		{
			if(this.bitmapIndices.isEmpty())
			{
				// no-op
				return;
			}

			this.clearCachedChangeHandlers();
		}
		
		@Override
		public final void internalUpdateIndices(
			final long                         entityId         ,
			final E                            replacedEntity   ,
			final E                            entity           ,
			final CustomConstraints<? super E> customConstraints
		)
		{
			if(this.bitmapIndices.isEmpty())
			{
				// no-op
				return;
			}

			try
			{
				// Derive state handlers for the new, potentially changed state
				this.createChangeHandlers(this.cachedNewChangeHandlers, entity);
				
				if(customConstraints != null)
				{
					try
					{
						customConstraints.check(entityId, replacedEntity, entity);
					}
					catch(final ConstraintViolationException e)
					{
						for(final ChangeHandler cachedPrevChangeHandler : this.cachedPrevChangeHandlers)
						{
							cachedPrevChangeHandler.removeFromIndex(entityId);
						}
						throw e;
					}
				}
				
				// Evaluate changes for each index
				for(int i = 0; i < this.cachedPrevChangeHandlers.length; i++)
				{
					if(this.cachedPrevChangeHandlers[i].isEqual(this.cachedNewChangeHandlers[i]))
					{
						// Mark index position to be irrelevant (unchanged)
						this.cachedNewChangeHandlers[i] = null;
						continue;
					}
					try
					{
						if(this.cachedIsUniqueIndex[i])
						{
							if(this.cachedIndices[i].internalContains(entity))
							{
								throw new UniqueConstraintViolationExceptionBitmap(entityId, replacedEntity, entity, this.cachedIndices[i]);
							}
						}
					}
					catch(final ConstraintViolationException e)
					{
						for(final ChangeHandler cachedPrevChangeHandler : this.cachedPrevChangeHandlers)
						{
							cachedPrevChangeHandler.removeFromIndex(entityId);
						}
						throw e;
					}
				}
				
				// Update Indices for all actual changes
				for(int i = 0; i < this.cachedNewChangeHandlers.length; i++)
				{
					// Skip index positions for unchanged values
					if(this.cachedNewChangeHandlers[i] == null)
					{
						continue;
					}
					this.cachedNewChangeHandlers[i].changeInIndex(entityId, this.cachedPrevChangeHandlers[i]);
				}
			}
			finally
			{
				this.markStateChangeChildren();
			}
		}
		
		private boolean isUniqueConstraint(final BitmapIndex.Internal<E, ?> index)
		{
			return index.isSuitableAsUniqueConstraint()
				&& this.uniqueConstraints != null
				&& this.uniqueConstraints.contains(index)
			;
		}

		@Override
		public final BitmapIndex<E, String> get(final String name)
		{
			synchronized(this.parentMap())
			{
				return this.internalGet(String.class, name);
			}
		}

		@Override
		public final <K> BitmapIndex<E, K> get(final Class<K> keyType, final String indexName)
		{
			synchronized(this.parentMap())
			{
				return this.internalGet(keyType, indexName);
			}
		}

		@Override
		public final <K> BitmapIndex.Internal<E, K> internalGet(final Class<K> keyType, final String indexName)
		{
			final BitmapIndex.Internal<E, ?> index = this.bitmapIndices.get(indexName);
			if(index == null)
			{
				// no index at all registered for that name, regardless of keyType.
				return null;
			}
			
			// might throw an exception here instead of "faking" a lookup miss.
			@SuppressWarnings("unchecked") // cast safety guaranteed by adding logic
			final BitmapIndex.Internal<E, K> bitmapIndex = index.keyType() == keyType
				? (BitmapIndex.Internal<E, K>)index
				: null
			;
			
			return bitmapIndex;
		}
		
		@Override
		public <K, I extends Indexer<E, K>> I getIndexer(final Class<I> indexerType, final String name)
		{
			synchronized(this.parentMap())
			{
				final BitmapIndex.Internal<E, ?> index = this.bitmapIndices.get(name);
				if(index != null)
				{
					return indexerType.cast(index.indexer());
				}
				
				return null;
			}
		}
		
		final void internalSetIdentityIndices(final XImmutableEnum<? extends BitmapIndex<E, ?>> identityIndices)
		{
			this.identityIndices = identityIndices;
		}
		
		final void internalSetUniqueConstraints(final XImmutableEnum<BitmapIndex.Internal<E, ?>> uniqueConstraints)
		{
			this.uniqueConstraints = uniqueConstraints;
		}
		
		@Override
		public final XImmutableEnum<? extends BitmapIndex<E, ?>> identityIndices()
		{
			return this.identityIndices;
		}

		@Override
		public final XImmutableEnum<? extends BitmapIndex<E, ?>> uniqueConstraints()
		{
			return this.uniqueConstraints;
		}
		
		@Override
		public final BitmapIndices<E> setIdentityIndices(final XGettingEnum<? extends IndexIdentifier<? super E, ?>> identityIndices)
		{
			if(X.hasNoContent(identityIndices))
			{
				return this;
			}
			
			final BulkList<BitmapIndex<E, ?>> resolved = BulkList.New(identityIndices.size());
			
			synchronized(this.parentMap())
			{
				for(final IndexIdentifier<? super E, ?> i : identityIndices)
				{
					final BitmapIndex<E, ?> index = i.resolveFor(this);
					resolved.add(index);
				}
				this.internalSetIdentityIndices(ConstHashEnum.New(resolved));
				this.markStateChangeInstance();
			}
			
			return this;
		}
		
		private void internalAddBitmapIndex(final BitmapIndex.Internal<E, ?> index)
		{
			this.bitmapIndices.add(index.name(), index);
			
			// just to be safe and the performance cost is irrelevant for a structural element.
			if(index.parent() != this)
			{
				throw new BitmapIndicesException(
					"Inconsistent parent reference for index " + BitmapIndex.class.getSimpleName() + " \"" + index.name() + "\".",
					this
				);
			}
			
			this.markStateChangeInstance();
			
			this.parent.iterateIndexed(index::internalAdd);
			this.parent.internalReportIndexGroupStateChange(this);
		}
		

		
		static <E> EqHashTable<String, BitmapIndex.Internal<E, ?>> createHashTable(final Class<?> keyType)
		{
			return EqHashTable.New();
		}
		
		final <I> BitmapIndex<E, I> ensureBitmapIndex(final Indexer<? super E, I> indexer)
		{
			BitmapIndex<E, I> index = this.get(indexer.keyType(), indexer.name());
			if(index == null)
			{
				index = this.add(indexer);
			}
			return index;
		}
		
		@Override
		protected final void clearChildrenStateChangeMarkers()
		{
			this.bitmapIndices.values().iterate(BitmapIndex.Internal::clearStateChangeMarkers);
		}
		
		@Override
		protected final void storeChildren(final Storer storer)
		{
			// this is just a local, partial lock that does NOT protect the whole giga map storing process. See GigaMap#store.
			synchronized(this.parentMap())
			{
				super.storeChildren(storer);
			}
		}

		@Override
		protected final void storeChangedChildren(final Storer storer)
		{
			for(final BitmapIndex.Internal<E, ?> index : this.bitmapIndices.values())
			{
				// must take a detour over the TypeHandler here as well because of interface abstraction.
				storer.store(index);
			}
		}
		
		private void validateIndexToAdd(final Indexer<? super E, ?> indexer)
		{
			if(indexer == null)
			{
				throw new IllegalArgumentException("Indexer may not be null.");
			}
			
			final String indexName = indexer.name();
			if(indexName == null)
			{
				throw new IllegalArgumentException("Index name may not be null.");
			}
			
			
			// Index name may not be taken, yet.
			final BitmapIndex<E, ?> index = this.bitmapIndices.get(indexName);
			if(index != null)
			{
				throw new RuntimeException(BitmapIndex.class.getSimpleName() + " already registered for name \"" + index.name() + "\".");
			}
		}
		
		private void internalAddUniqueConstraint(final BitmapIndex.Internal<E, ?> index)
		{
			if(this.uniqueConstraints == null)
			{
				this.uniqueConstraints = ConstHashEnum.New(index);
			}
			else if(this.uniqueConstraints.contains(index))
			{
				return;
			}
			else
			{
				// no need for a set as the #contains call above already ensured uniqueness after the #add.
				final BulkList<BitmapIndex.Internal<E, ?>> mutable = BulkList.New(this.uniqueConstraints);
				mutable.add(index);
				this.uniqueConstraints = ConstHashEnum.New(mutable);
			}
			
			// report change in case #1 and #3 (#2 aborts)
			this.parent.internalReportIndexGroupStateChange(this);
		}
		
		@Override
		public final UniqueConstraints<E> addUniqueConstraint(final String indexName, final Indexer<? super E, ?> indexer)
		{
			// validation and registering creates so many instances that this one detour instance does not matter.
			this.addUniqueConstraints(X.Constant(indexer));
			
			return this;
		}
		
		@Override
		public final UniqueConstraints<E> addUniqueConstraints(final Iterable<? extends Indexer<? super E, ?>> indexers)
		{
			synchronized(this.parentMap())
			{
				// Basic validation before changing any state.
				for(final Indexer<? super E, ?> indexer : indexers)
				{
					this.validateIndexToAdd(indexer);
				}

				// Building unique indices, their data and data-related validation.
				final EqHashTable<String, BitmapIndex.Internal<E, ?>> indices = EqHashTable.New();
				this.buildUniqueIndices(indexers, indices);
				this.buildIndexDataAndValidateUniqueness(indices);
				
				// When everything is guaranteed to be valid and consistent, the indices are actually added.
				for(final BitmapIndex.Internal<E, ?> index : indices.values())
				{
					this.internalAddUniqueConstraint(index);
					this.internalAddBitmapIndex(index);
				}
				this.rebuildCache();
			}
			
			return this;
		}
		
		private void buildUniqueIndices(
			final Iterable<? extends Indexer<? super E, ?>> indexers,
			final EqHashTable<String, BitmapIndex.Internal<E, ?>> indices
		)
		{
			for(final Indexer<? super E, ?> indexer : indexers)
			{
				final BitmapIndex.Internal<E, ?> index = indexer.createFor(this);
				if(!index.isSuitableAsUniqueConstraint())
				{
					throw new BitmapIndicesException(
						"Index not suited as a unique constraint: \"" + index.name() + "\" class " + index.getClass(),
						this
					);
				}
				if(!indices.add(index.name(), index))
				{
					throw new BitmapIndicesException(
						"Conflicted index name: \"" + index.name() + "\".",
						this
					);
				}
			}
		}
		
		private void buildIndexDataAndValidateUniqueness(
			final EqHashTable<String, BitmapIndex.Internal<E, ?>> indices
		)
		{
			this.parent.iterateIndexed((final long entityId, final E entity) ->
			{
				for(final BitmapIndex.Internal<E, ?> index : indices.values())
				{
					if(index.internalContains(entity))
					{
						throw new UniqueConstraintViolationExceptionBitmap(entityId, null, entity, index);
					}
					index.internalAdd(entityId, entity);
				}
			});
		}
		
		@Override
		public final void accessUniqueConstraints(final Consumer<? super XImmutableEnum<? extends GigaIndex<E>>> logic)
		{
			if(this.uniqueConstraints == null)
			{
				return;
			}
			logic.accept(this.uniqueConstraints);
		}

		@Override
		public final void accessUniqueIndices(final Consumer<? super XImmutableEnum<? extends BitmapIndex<E, ?>>> logic)
		{
			if(this.uniqueConstraints == null)
			{
				return;
			}
			logic.accept(this.uniqueConstraints);
		}
		
		@Override
		public final void accessIndices(final Consumer<? super XGettingTable<String, ? extends BitmapIndex<E, ?>>> logic)
		{
			synchronized(this.parentMap())
			{
				logic.accept(this.bitmapIndices);
			}
		}
		
		
		
		
		// no idea why "BitmapIndex.Internal" is not directly compatible with "? extends BitmapIndex", but here we are.
		protected static final class EntryIterator<E, I extends BitmapIndex<E, ?>>
		implements Iterator<KeyValue<String, ? extends BitmapIndex<E, ?>>>
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			private final Iterator<KeyValue<String, I>> iterator;
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////

			EntryIterator(final EqHashTable<String, I> bitmapIndices)
			{
				super();
				this.iterator = bitmapIndices.iterator();
			}
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////

			@Override
			public boolean hasNext()
			{
				return this.iterator.hasNext();
			}

			@Override
			public KeyValue<String, I> next()
			{
				return this.iterator.next();
			}
			
		}
		
		@Override
		public <I extends Consumer<? super BitmapIndex<E, ?>>> I iterate(final I iterator)
		{
			synchronized(this.parentMap())
			{
				for(final KeyValue<String, ? extends BitmapIndex<E, ?>> entry : this)
				{
					iterator.accept(entry.value());
				}
			}
			
			return iterator;
		}

		@Override
		public final Iterator<KeyValue<String, ? extends BitmapIndex<E, ?>>> iterator()
		{
			synchronized(this.parentMap())
			{
				return new EntryIterator<>(this.bitmapIndices.copy());
			}
		}

		@Override
		public Statistics<E> createStatistics()
		{
			// concurrency handled inside
			return DefaultStatistics.createStatistics(this);
		}
		
	}
	
	public interface Statistics<E>
	{
		public XGettingTable<String, BitmapIndex.Statistics<E>> entries();
		
		public default int entryCount()
		{
			return this.entries().intSize();
		}
		
		public int totalDataMemorySize();
		
		public default VarString assemble(final VarString vs)
		{
			return this.assemble(vs, Integer.MAX_VALUE);
		}
		
		public VarString assemble(VarString vs, int levels);
	}
	
}

