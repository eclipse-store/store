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
import org.eclipse.serializer.collections.EqHashTable;

import java.util.function.Consumer;
import java.util.function.Predicate;


/**
 * Abstract implementation of a bitmap index using hash-based operations for indexing.
 * This class extends the {@code BitmapIndex.Abstract} and provides functionality to
 * manage indexed data using a hash table and an optional indexer for key generation.
 *
 * @param <E> Type of the entity stored in the bitmap index.
 * @param <I> Type of the input used for indexing.
 * @param <K> Type of the key generated for indexing.
 */
public abstract class AbstractBitmapIndexHashing<E, I, K> extends BitmapIndex.Abstract<E, I, K>
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	      Indexer<? super I, K>                indexer; // may be null for sub indices, since a sub index is its own sub indexer.
	final EqHashTable<K, BitmapEntry<E, I, K>> entries;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	AbstractBitmapIndexHashing(
		final BitmapIndices<E>                     parent      ,
		final String                               name        ,
		final Indexer<? super I, K>                indexer     ,
		final EqHashTable<K, BitmapEntry<E, I, K>> entries     ,
		final boolean                              stateChanged
	)
	{
		super(parent, name, stateChanged);
		this.indexer = indexer;
		this.entries = entries;
	}
	
	AbstractBitmapIndexHashing(
		final BitmapIndices<E>      parent ,
		final String                name   ,
		final Indexer<? super I, K> indexer
	)
	{
		this(parent, name, indexer, EqHashTable.New(indexer.hashEqualator()), true);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	protected void initializeIndexer(final Indexer<? super I, K> indexer)
	{
		if(this.indexer != null)
		{
			if(this.indexer == indexer)
			{
				return;
			}
			throw new IllegalArgumentException();
		}
		this.indexer = indexer;
	}
	
	@Override
	public Indexer<? super I, K> indexer()
	{
		return this.indexer;
	}
	
	@Override
	protected K indexEntity(final I entity)
	{
		return this.indexer.index(entity);
	}
	
	protected final BitmapEntry<E, I, K> getEntryForKey(final K key)
	{
		return this.entries.get(key);
	}
	
	public BitmapResult internalQuery(final K key)
	{
		final BitmapEntry<E, I, K> entry = this.getEntryForKey(key);
		
		// never return null since it is perfectly valid to query for a key that does not exist.
		return entry != null
			? entry.createResult()
			: EMPTY_RESULT
		;
	}
	
	public boolean containsKey(final K key)
	{
		return this.getEntryForKey(key) != null;
	}
	
	@Override
	public boolean internalContains(final I entity)
	{
		return this.containsKey(this.indexEntity(entity));
	}
	
	@Override
	public final BitmapEntry<E, I, K> internalEnsureEntry(final I entity)
	{
		final K key = this.indexEntity(entity);
		return this.internalEnsureEntryForKey(key);
	}
	
	public BitmapResult search(final Predicate<? super K> predicate)
	{
		final BitmapResult[] results = new BitmapResult[this.entries.intSize()];
		int r = 0;
		synchronized(this.parentMap())
		{
			for(final BitmapEntry<E, I, K> e : this.entries.values())
			{
				if(!predicate.test(e.key()))
				{
					continue;
				}
				results[r++] = e.createResult();
			}
		}
		
		if(r == 0)
		{
			return EMPTY_RESULT;
		}
		
		return new BitmapResult.ChainOr(results);
	}
	
	public ChangeHandler getChangeHandler(final I oldEntity)
	{
		if(oldEntity == null)
		{
			return NullChangeChandler.SINGLETON;
		}
		
		if(this.indexer instanceof IndexerMultiValue)
		{
			final BulkList<ChangeHandler> changeHandlers = BulkList.New();
			@SuppressWarnings("unchecked")
			final Iterable<? extends K> keys = ((IndexerMultiValue<I, K>)this.indexer).indexEntityMultiValue(oldEntity);
			for(final K key : keys)
			{
				if(key != null)
				{
					changeHandlers.add(this.createChangeHandler(key));
				}
			}
			return new ChangeHandler.Chain(changeHandlers.immure());
		}

		final K key = this.indexEntity(oldEntity);
		return this.createChangeHandler(key);
	}
	
	private ChangeHandler createChangeHandler(final K key)
	{
		final BitmapEntry<E, I, K> entry = this.entries.get(key);
		if(entry != null)
		{
			return entry;
		}
		
		// Entry for a new key may not be added right away, but only after constraints validations etc. have been passed.
		return new NewKeyChangeChandler<>(this, key);
	}
	
	static final class NewKeyChangeChandler<I, K> implements ChangeHandler
	{
		private final AbstractBitmapIndexHashing<?, I, K> index ;
		private final K                                   newKey;
		
		NewKeyChangeChandler(final AbstractBitmapIndexHashing<?, I, K> index, final K newKey)
		{
			super();
			this.index = index;
			this.newKey = newKey;
		}
		
		@Override
		public boolean isEqual(final ChangeHandler other)
		{
			// Should never be true since change handlers are always compared prev <-> new
			return other instanceof NewKeyChangeChandler<?, ?>
				&& this.index == ((NewKeyChangeChandler<?, ?>)other).index
				&& this.newKey.equals(((NewKeyChangeChandler<?, ?>)other).newKey)
				;
		}
		
		@Override
		public void removeFromIndex(final long entityId)
		{
			throw new Error("Removing an entityId for a new key may never be required.");
		}
		
		@Override
		public void changeInIndex(final long entityId, final ChangeHandler prevEntityHandler)
		{
			// Create a new entry after all validations etc. have been passed.
			final BitmapEntry<?, I, K> newEntry = this.index.internalEnsureEntryForKey(this.newKey);
			
			// EntityId gets added to the newly created entry just like an existing entry would.
			newEntry.changeInIndex(entityId, prevEntityHandler);
		}
		
	}
	
	@Override
	public void internalAddToEntry(final long entityId, final I indexable)
	{
		if(this.indexer instanceof IndexerMultiValue)
		{
			@SuppressWarnings("unchecked")
			final Iterable<? extends K> keys = ((IndexerMultiValue<I, K>)this.indexer).indexEntityMultiValue(indexable);
			for(final K key : keys)
			{
				if(key == null)
				{
					continue;
				}
				final BitmapEntry<E, I, K> entry = this.internalEnsureEntryForKey(key);
				entry.add(entityId);
			}
		}
		else
		{
			final BitmapEntry<E, I, K> entry = this.internalEnsureEntry(indexable);
			entry.add(entityId);
		}
	}
	
	protected final BitmapEntry<E, I, K> internalEnsureEntryForKey(final K key)
	{
		BitmapEntry<E, I, K> entry = this.entries.get(key);
		if(entry == null)
		{
			this.entries.put(key, entry = new BitmapEntry<>(this, key, this.entries.intSize(), true));
			this.markStateChangeInstance();
		}
		return entry;
	}
	
	@Override
	public final void iterateEntries(final Consumer<? super BitmapEntry<?, ?, ?>> logic)
	{
		this.entries.values().iterate(logic);
	}
	
	@Override
	public final <C extends Consumer<? super K>> C iterateKeys(final C logic)
	{
		this.entries.keys().iterate(logic);
		
		return logic;
	}
	
	@Override
	public final int entryCount()
	{
		return this.entries.intSize();
	}
	
	final EqHashTable<K, BitmapEntry<E, I, K>> entries()
	{
		return this.entries;
	}
	
	@Override
	protected final void removeEntry(final BitmapEntry<E, I, K> entry)
	{
		this.entries.removeFor(entry.key());
	}
	
	public void internalRemove(final long entityId, final I indexable)
	{
		if(this.indexer instanceof IndexerMultiValue)
		{
			@SuppressWarnings("unchecked")
			final Iterable<? extends K> keys = ((IndexerMultiValue<I, K>)this.indexer).indexEntityMultiValue(indexable);
			for(final K key : keys)
			{
				if(key != null)
				{
					final BitmapEntry<E, I, K> entry = this.getEntryForKey(key);
					if(entry !=  null)
					{
						this.internalRemoveFromEntry(entityId, entry);
					}
				}
			}
		}
		else
		{
			final BitmapEntry<E, I, K> entry = this.internalLookupEntry(indexable);
			if(entry == null)
			{
				// entity's key is not indexed at all
				return;
			}
			this.internalRemoveFromEntry(entityId, entry);
		}
	}
	
	public void internalRemoveAll()
	{
		this.entries.clear();
		this.markStateChangeInstance();
	}
	
	public BitmapEntry<E, I, K> internalLookupEntry(final I indexable)
	{
		return this.getEntryForKey(
			this.indexEntity(indexable)
		);
	}
	
	public void internalHandleChanged(final I oldKeys , final long entityId, final I newKeys)
	{
		// (20.12.2024 TM)TODO: handle multi value indexing. See other call site of #internalEnsureEntry
		final BitmapEntry<E, I, K> oldEntityEntry = this.internalLookupEntry(oldKeys);
		final BitmapEntry<E, I, K> newEntityEntry = this.internalEnsureEntry(newKeys);
		if(newEntityEntry == oldEntityEntry)
		{
			return;
		}
		
		this.internalRemoveFromEntry(entityId, oldEntityEntry);
		newEntityEntry.add(entityId);
		this.markStateChangeChildren2();
	}
	
}
