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
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.Storer;

import java.util.function.Function;

import static org.eclipse.serializer.util.X.notNull;


/**
 * Represents a collection of indices within a {@link GigaMap}.
 * Provides mechanisms for managing and retrieving bitmap indices,
 * as well as other categorized or grouped indices.
 *
 * @param <E> the type of elements associated with this collection of indices
 */
public interface GigaIndices<E> extends GigaMap.Component<E>
{
	/**
	 * Retrieves a BitmapIndices object associated with this GigaIndices instance.
	 * This method provides access to the bitmap-based index for the current object.
	 *
	 * @return a BitmapIndices instance representing the bitmap index structure.
	 */
	public BitmapIndices<E> bitmap();
	
	/**
	 * Retrieves the bitmap index associated with the specified name.
	 *
	 * @param name the name of the bitmap index to retrieve
	 * @return the BitmapIndex object corresponding to the specified name
	 */
	public default BitmapIndex<E, ?> bitmap(final String name)
	{
		return this.bitmap().get(name);
	}
	
	/**
	 * Retrieves a specific BitmapIndex associated with the provided IndexIdentifier.
	 * This method allows access to a bitmap index based on the key type and name
	 * specified in the IndexIdentifier.
	 *
	 * @param <K>   the type of the key associated with the BitmapIndex
	 * @param index the IndexIdentifier containing key type and name used to locate
	 *              the desired bitmap index
	 * @return a BitmapIndex corresponding to the specified IndexIdentifier
	 */
	public default <K> BitmapIndex<E, K> bitmap(final IndexIdentifier<E, K> index)
	{
		return this.bitmap().get(index.keyType(), index.name());
	}
	
	/**
	 * Retrieves an index group of the specified category type associated with the current instance.
	 *
	 * @param <C>      the type of the index group being retrieved
	 * @param category the class of the index group category to retrieve
	 * @return the index group of the specified category type
	 */
	public <C extends IndexGroup<E>> C get(Class<C> category);
	
	/**
	 * Retrieves an index group associated with this instance and the specified category.
	 * This method allows access to an index group based on the index category provided
	 * as a parameter.
	 *
	 * @param <I>       the type of the index group being retrieved
	 * @param category  the index category defining the type of index group to retrieve
	 * @return the index group of the specified category type
	 */
	public <I extends IndexGroup<E>> I get(IndexCategory<E, I> category);
	
	/**
	 * Adds a new index category to this GigaIndices instance.
	 *
	 * @param category the index category to be added, specified as an IndexCategory object
	 *                 that defines the type of index group to associate with this instance.
	 * @return {@code true} if the index category was successfully added,
	 *         {@code false} if the category could not be added (e.g., if it already exists).
	 */
	public boolean add(IndexCategory<E, ?> category);
	
	/**
	 * Registers an index group associated with the specified index category.
	 * The method associates the given index category with this instance,
	 * creating and initializing a new index group if necessary.
	 *
	 * @param <I>      the type of the index group to be registered
	 * @param category the index category defining the type of index group to register
	 * @return the registered index group corresponding to the specified category
	 */
	public <I extends IndexGroup<E>> I register(IndexCategory<E, I> category);
	
	/**
	 * The Internals interface extends the GigaIndices interface to provide additional functionality
	 * specifically related to managing internal operations and indices. This interface is meant
	 * to define internal mechanics involved with handling indices while still providing access
	 * to specific internal components.
	 *
	 * @param <E> The type of elements managed by the indices.
	 */
	public interface Internals<E> extends GigaIndices<E>
	{
		// this is actually not internal, but a public API method but with an internal return type. So no "internal~" prefix.
		@Override
		public BitmapIndices.Internal<E> bitmap();
	}
	
	public final class Default<E> extends AbstractStateChangeFlagged implements Internals<E>
	{
		static BinaryTypeHandler<Default<?>> provideTypeHandler()
		{
			return BinaryHandlerGigaIndicesDefault.New();
		}
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final GigaMap.Default<E> parent;

		/*
		 * Logic wise, this would be a HashTable, but it is preferable to use a list for the following reasons:
		 * - very low number of entries (~1 to 10) can be iterated faster than hash-lookup-ed.
		 * - having class instances as keys would complicate storing or require a special type handler
		 * - hash lookup wouldn't suffice since quering for a interface type should still yield the suitable entry.
		 */
		final BulkList<IndexGroup.Internal<E>> indexGroups;
					
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(final GigaMap.Default<E> parent)
		{
			this(parent, BulkList.New(), true);
		}
		
		Default(
			final GigaMap.Default<E>               parent      ,
			final BulkList<IndexGroup.Internal<E>> indexGroups ,
			final boolean                          stateChanged
		)
		{
			super(stateChanged);
			this.parent      = parent     ;
			this.indexGroups = indexGroups;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final GigaMap<E> parentMap()
		{
			return this.parent;
		}
		
		protected final void internalAdd(final long entityId, final E entity)
		{
			notNull(entity);
			for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
			{
				indexGroup.internalAdd(entityId, entity);
			}
			this.markStateChangeChildren();
		}
		
		protected final void internalAddAll(final long firstEntityId, final Iterable<? extends E> entities)
		{
			for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
			{
				indexGroup.internalAddAll(firstEntityId, entities);
			}
			this.markStateChangeChildren();
		}
		
		protected final void internalAddAll(final long firstEntityId, final E[] entities)
		{
			for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
			{
				indexGroup.internalAddAll(firstEntityId, entities);
			}
			this.markStateChangeChildren();
		}
		
		protected final void internalRemove(final long entityId, final E entity)
		{
			for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
			{
				indexGroup.internalRemove(entityId, entity);
			}
			this.markStateChangeChildren();
		}
		
		protected void internalRemoveAll()
		{
			for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
			{
				indexGroup.internalRemoveAll();
			}
			this.markStateChangeChildren();
		}
		
		void internalUpdateIndices(
			final long                         entityId          ,
			final E                            replacedEntity    ,
			final E                            entity            ,
			final CustomConstraints<? super E> customConstraints
		)
		{
			try
			{
				for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
				{
					indexGroup.internalPrepareIndicesUpdate(replacedEntity);
					try
					{
						indexGroup.internalUpdateIndices(entityId, replacedEntity, entity, customConstraints);
					}
					finally
					{
						indexGroup.internalFinishIndicesUpdate();
					}
				}
			}
			finally
			{
				this.markStateChangeChildren();
			}
		}
		
		<R> R internalUpdateIndices(
			final long                         entityId         ,
			final E                            entity           ,
			final Function<? super E, R>       logic            ,
			final CustomConstraints<? super E> customConstraints
		)
		{
			for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
			{
				indexGroup.internalPrepareIndicesUpdate(entity);
			}
			
			try
			{
				final R result = logic.apply(entity);
				
				for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
				{
					indexGroup.internalUpdateIndices(entityId, entity, entity, customConstraints);
				}
				
				return result;
			}
			finally
			{
				for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
				{
					indexGroup.internalFinishIndicesUpdate();
				}
				
				this.markStateChangeChildren();
			}
		}
					
		final void internalReportIndexGroupStateChange(final IndexGroup<E> indexGroup)
		{
			// tiny consistency-helper to rule out method calls with null argument.
			notNull(indexGroup);
			
			// simple default implementation ignores the precise instance reported, only the change itself.
			this.markStateChangeChildren();
		}
														
		@Override
		public final BitmapIndices.Internal<E> bitmap()
		{
			@SuppressWarnings("unchecked")
			final BitmapIndices.Internal<E> registered = this.get(BitmapIndices.Default.class);
			
			return registered;
		}

		@Override
		public final <C extends IndexGroup<E>> C get(final Class<C> categoryType)
		{
			synchronized(this.parentMap())
			{
				// exact class match is checked first
				for(final IndexGroup<E> indexGroup : this.indexGroups)
				{
					if(indexGroup.getClass() == categoryType)
					{
						return categoryType.cast(indexGroup);
					}
				}
				
				// if no exact class match was found, the general type is checked (e.g. passed interface type)
				for(final IndexGroup<E> indexGroup : this.indexGroups)
				{
					if(categoryType.isAssignableFrom(indexGroup.getClass()))
					{
						return categoryType.cast(indexGroup);
					}
				}
			}
			
			return null;
		}
		
		@Override
		public final <I extends IndexGroup<E>> I get(final IndexCategory<E, I> category)
		{
			synchronized(this.parentMap())
			{
				// exact class match is checked first
				for(final IndexGroup<E> indexGroup : this.indexGroups)
				{
					if(indexGroup.getClass() == category.indexType())
					{
						return category.indexType().cast(indexGroup);
					}
				}
				
				// if no exact class match was found, the general type is checked (e.g. passed interface type)
				for(final IndexGroup<E> indexGroup : this.indexGroups)
				{
					if(category.indexType().isAssignableFrom(indexGroup.getClass()))
					{
						return category.indexType().cast(indexGroup);
					}
				}
			}
			
			return null;
		}

		@Override
		public final boolean add(final IndexCategory<E, ?> category)
		{
			// concurrency handling done by called method
			return this.register(category) != null;
		}
		
		@Override
		public final <I extends IndexGroup<E>> I register(final IndexCategory<E, I> category)
		{
			synchronized(this.parentMap())
			{
				final IndexGroup.Internal<E> indexGroup = category.createIndexGroup(this.parent);
				
				@SuppressWarnings("unchecked")
				final IndexGroup<E> alreadyRegistered = this.get(indexGroup.getClass());
				if(alreadyRegistered != null)
				{
					return null;
				}
				
				this.indexGroups.add(indexGroup);
				this.markStateChangeInstance();
				
				return category.indexType().cast(indexGroup);
			}
		}

		@Override
		protected final void clearChildrenStateChangeMarkers()
		{
			this.indexGroups.iterate(IndexGroup.Internal::clearStateChangeMarkers);
		}
		
		@Override
		protected void storeChildren(final Storer storer)
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
			for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
			{
				// must take a detour over the TypeHandler because of interface typing
				storer.store(indexGroup);
			}
		}
		
	}
			
}
