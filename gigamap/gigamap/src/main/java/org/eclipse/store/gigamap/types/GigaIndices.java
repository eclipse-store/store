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

import java.io.Closeable;
import java.io.IOException;
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
	 * Removes the index group of the given category from this {@link GigaMap}, dropping the whole
	 * group and its data.
	 * <p>
	 * If the group implements {@link java.io.Closeable} (e.g. the Lucene full-text index), it is
	 * closed first to release native resources such as file locks, readers and writers. Index data
	 * held inside the object graph is reclaimed by the storage's garbage collection on the next
	 * housekeeping cycle after the surrounding {@link GigaMap} is stored; index artifacts kept in an
	 * external, application-managed directory on the file system are <b>not</b> deleted.
	 * <p>
	 * The core bitmap index group cannot be removed (it hosts the unique and custom constraints);
	 * use {@link BitmapIndices#removeIndex(String)} to remove individual bitmap indices instead.
	 *
	 * @param category the category identifying the index group to remove
	 * @return {@code true} if a matching group existed and was removed, {@code false} otherwise
	 * @throws IllegalArgumentException if the targeted group is the core bitmap index group
	 * @throws RuntimeException if the parent {@link GigaMap} is read-only
	 */
	public boolean remove(IndexCategory<E, ?> category);

	/**
	 * Removes the index group of the given type from this {@link GigaMap}.
	 * <p>
	 * For details see {@link #remove(IndexCategory)}.
	 *
	 * @param categoryType the type of the index group to remove
	 * @return {@code true} if a matching group existed and was removed, {@code false} otherwise
	 */
	public boolean remove(Class<? extends IndexGroup<E>> categoryType);

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
			// mark in any case: a mid-loop throw from an indexer leaves already updated groups behind.
			try
			{
				for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
				{
					indexGroup.internalAdd(entityId, entity);
				}
			}
			finally
			{
				this.markStateChangeChildren();
			}
		}

		protected final void internalAddAll(final long firstEntityId, final Iterable<? extends E> entities)
		{
			try
			{
				for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
				{
					indexGroup.internalAddAll(firstEntityId, entities);
				}
			}
			finally
			{
				this.markStateChangeChildren();
			}
		}

		protected final void internalAddAll(final long firstEntityId, final E[] entities)
		{
			try
			{
				for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
				{
					indexGroup.internalAddAll(firstEntityId, entities);
				}
			}
			finally
			{
				this.markStateChangeChildren();
			}
		}

		protected final void internalRemove(final long entityId, final E entity)
		{
			/*
			 * Best-effort removal across all index groups, mirroring BitmapIndices#internalRemove:
			 * a throwing indexer in one group must not prevent the other groups from being cleaned
			 * up. The first exception is rethrown at the end, subsequent failures are attached as
			 * suppressed exceptions.
			 */
			RuntimeException first = null;
			try
			{
				for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
				{
					try
					{
						indexGroup.internalRemove(entityId, entity);
					}
					catch(final RuntimeException e)
					{
						if(first == null)
						{
							first = e;
						}
						else
						{
							first.addSuppressed(e);
						}
					}
				}
			}
			finally
			{
				this.markStateChangeChildren();
			}
			if(first != null)
			{
				throw first;
			}
		}
		
		protected void internalRemoveAll()
		{
			for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
			{
				indexGroup.internalRemoveAll();
			}
			this.markStateChangeChildren();
		}

		void internalReindex()
		{
			// Rebuild every index group from the current entity state. Each group clears its data and
			// re-indexes all entities of the parent map (see IndexGroup.Internal#internalReindex), so an
			// index that drifted out of sync - e.g. because an indexed entity was mutated directly instead
			// of via update()/apply() - is brought back in line.
			for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
			{
				indexGroup.internalReindex(this.parent);
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
						/*
						 * removeOnFailure == false: the calling context (set/replace) keeps the replaced
						 * entity in place when the update fails, so the previous index entries must
						 * remain untouched.
						 */
						indexGroup.internalUpdateIndices(entityId, replacedEntity, entity, customConstraints, false);
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

		/**
		 * First phase of an in-place entity update: derives the change handlers for the entity's
		 * current (pre-mutation) state in all index groups. This runs all indexers, i.e. user code,
		 * but does not mutate any index state: a throw here leaves the map completely unchanged,
		 * already prepared groups are released again and nothing gets state-change marked.
		 */
		void internalPrepareUpdate(final E entity)
		{
			int prepared = 0;
			try
			{
				for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
				{
					indexGroup.internalPrepareIndicesUpdate(entity);
					prepared++;
				}
			}
			catch(final RuntimeException e)
			{
				int i = 0;
				for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
				{
					if(i++ >= prepared)
					{
						break;
					}
					try
					{
						indexGroup.internalFinishIndicesUpdate();
					}
					catch(final RuntimeException suppressed)
					{
						e.addSuppressed(suppressed);
					}
				}
				throw e;
			}
		}

		/**
		 * Second phase of an in-place entity update: executes the mutating logic and updates all
		 * index groups from the entity's resulting state. Must be preceded by a successful
		 * {@link #internalPrepareUpdate(Object)}.
		 */
		<R> R internalApplyUpdate(
			final long                         entityId         ,
			final E                            entity           ,
			final Function<? super E, R>       logic            ,
			final CustomConstraints<? super E> customConstraints
		)
		{
			try
			{
				R result;
				try
				{
					result = logic.apply(entity);
				}
				catch(final RuntimeException e)
				{
					/*
					 * The logic threw: the entity's state is unreliable and the calling context will
					 * remove the entity. The indices are still updated from the entity's current state
					 * so that the subsequent removal, which re-derives the keys from that same state,
					 * can locate all entries. A group whose update fails de-indexes its previous
					 * entries via the prepared change handlers instead (removeOnFailure). Custom
					 * constraints are not checked; the entity is doomed either way.
					 */
					try
					{
						for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
						{
							indexGroup.internalUpdateIndices(entityId, entity, entity, null, true);
						}
					}
					catch(final RuntimeException suppressed)
					{
						e.addSuppressed(suppressed);
					}
					throw e;
				}

				for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
				{
					/*
					 * removeOnFailure == true: the calling context removes the in-place mutated entity
					 * from the map when the update fails, so on failure a group must de-index the
					 * entity's previous state via its prepared change handlers.
					 */
					indexGroup.internalUpdateIndices(entityId, entity, entity, customConstraints, true);
				}

				this.internalFinishUpdate(null);

				return result;
			}
			catch(final RuntimeException e)
			{
				this.internalFinishUpdate(e);
				throw e;
			}
			finally
			{
				this.markStateChangeChildren();
			}
		}

		private void internalFinishUpdate(final RuntimeException primary)
		{
			RuntimeException first = primary;
			for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
			{
				try
				{
					indexGroup.internalFinishIndicesUpdate();
				}
				catch(final RuntimeException e)
				{
					if(first == null)
					{
						first = e;
					}
					else
					{
						first.addSuppressed(e);
					}
				}
			}
			if(primary == null && first != null)
			{
				throw first;
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
				if(this.parent.isReadOnly())
				{
					throw new IllegalStateException(
						"Cannot register an index group: the GigaMap is read-only."
					);
				}
				final IndexGroup.Internal<E> indexGroup = category.createIndexGroup(this.parent);
				
				@SuppressWarnings("unchecked")
				final IndexGroup<E> alreadyRegistered = this.get(indexGroup.getClass());
				if(alreadyRegistered != null)
				{
					return null;
				}
				
				this.indexGroups.add(indexGroup);

				// let the freshly added group back-fill itself from entities that already exist in the
				// map (no-op for most groups; only reached for a newly-added group, see the early return
				// above). Not invoked on deserialization, which reconstructs groups via their handlers.
				try
				{
					indexGroup.internalOnRegistered();
				}
				catch(final Throwable e)
				{
					// keep registration atomic: a failed back-fill must not leave a half-initialized group
					// registered. Roll back the addition and release native resources the group may hold.
					this.indexGroups.removeOne(indexGroup);
					if(indexGroup instanceof Closeable)
					{
						try
						{
							((Closeable)indexGroup).close();
						}
						catch(final IOException suppressed)
						{
							e.addSuppressed(suppressed);
						}
					}
					throw e;
				}

				// mark the change only after the group is fully and successfully registered.
				this.markStateChangeInstance();

				return category.indexType().cast(indexGroup);
			}
		}

		@Override
		public final boolean remove(final IndexCategory<E, ?> category)
		{
			return this.internalRemoveGroup(category.indexType());
		}

		@Override
		public final boolean remove(final Class<? extends IndexGroup<E>> categoryType)
		{
			return this.internalRemoveGroup(categoryType);
		}

		private boolean internalRemoveGroup(final Class<?> categoryType)
		{
			synchronized(this.parentMap())
			{
				if(this.parent.isReadOnly())
				{
					throw new IllegalStateException(
						"Cannot remove index group \"" + categoryType.getName() + "\": the GigaMap is read-only."
					);
				}

				IndexGroup.Internal<E> found = null;
				// exact class match is checked first, then the general (e.g. interface) type, mirroring #get.
				for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
				{
					if(indexGroup.getClass() == categoryType)
					{
						found = indexGroup;
						break;
					}
				}
				if(found == null)
				{
					for(final IndexGroup.Internal<E> indexGroup : this.indexGroups)
					{
						if(categoryType.isAssignableFrom(indexGroup.getClass()))
						{
							found = indexGroup;
							break;
						}
					}
				}
				if(found == null)
				{
					return false;
				}
				if(found instanceof BitmapIndices)
				{
					throw new IllegalArgumentException(
						"The bitmap index group cannot be removed (it hosts the unique and custom constraints); "
						+ "use BitmapIndices#removeIndex(String) to remove individual bitmap indices."
					);
				}

				// release resources
				if(found instanceof Closeable)
				{
					try
					{
						((Closeable)found).close();
					}
					catch(final IOException e)
					{
						throw new RuntimeException(
							"Failed to close index group \"" + found.getClass().getName()
							+ "\" while removing it.",
							e
						);
					}
				}

				this.indexGroups.removeOne(found);
				this.markStateChangeInstance();
				return true;
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
