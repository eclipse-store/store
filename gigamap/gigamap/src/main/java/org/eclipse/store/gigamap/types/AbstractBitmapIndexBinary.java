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

import org.eclipse.serializer.branching.ThrowBreak;
import org.eclipse.serializer.collections.XSort;
import org.eclipse.serializer.persistence.types.Storer;

import java.util.function.Consumer;
import java.util.function.Predicate;


/**
 * AbstractBitmapIndexBinary provides a base implementation for bitmap index structures
 * that operate using binary indexing. This class offers core functionality for handling
 * entries, indexing, querying, and managing state changes within the bitmap index.
 *
 * @param <E> the type of entities managed by the bitmap index
 * @param <I> the type of indexable binary values
 */
public abstract class AbstractBitmapIndexBinary<E, I> extends BitmapIndex.Abstract<E, I, Long>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
	
	private static final BitmapResult[] EMPTY_RESULT = new BitmapResult[0];
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	BinaryIndexer<? super I>  indexer; // may be null for sub indices, since a sub index is its own sub indexer.
	BitmapEntry<E, I, Long>[] entries;
	
	private transient int  entriesCount;
	private transient long entriesLengthCheckMask;
	
	private transient final ContainsBreaker<I> contains;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	AbstractBitmapIndexBinary(
		final BitmapIndices<E>         parent      ,
		final String                   name        ,
		final BinaryIndexer<? super I> indexer     ,
		final boolean                  stateChanged
	)
	{
		super(parent, name, stateChanged);
		this.indexer  = indexer;
		this.entries  = BitmapEntry.createEntriesArray(0);
		this.contains = new ContainsBreaker<>();
		this.initializeTransientState();
	}
	
	AbstractBitmapIndexBinary(final BitmapIndices<E> parent, final String name, final BinaryIndexer<? super I> indexer)
	{
		this(parent, name, indexer, true);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	protected void initializeIndexer(final BinaryIndexer<? super I> indexer)
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
	protected final void storeChangedChildren(final Storer storer)
	{
		for(final BitmapEntry<E, I, Long> entry : this.entries)
		{
			if(entry == null || !entry.isChangedAndNotNew())
			{
				continue;
			}
			entry.storeChildren(storer);
		}
	}
	
	@Override
	protected final void clearChildrenStateChangeMarkers()
	{
		for(final BitmapEntry<E, I, Long> entry : this.entries)
		{
			if(entry != null)
			{
				entry.clearStateChangeMarkers();
			}
		}
	}
	
	@Override
	public final BinaryIndexer<? super I> indexer()
	{
		return this.indexer;
	}
	
	final void initializeTransientState()
	{
		this.updateCachedEntriesLength();
	}
	
	private void updateCachedEntriesLength()
	{
		this.entriesCount = this.entries.length;
		this.entriesLengthCheckMask = ~((1L << this.entriesCount) - 1);
	}
	
	final BitmapEntry<E, I, Long>[] entries()
	{
		return this.entries;
	}
	
	private BitmapEntry<E, I, Long>[] getEntries(final long keys)
	{
		// no point in checking if the whole range of a long value is already covered.
		if(this.entriesCount < Long.SIZE && (keys & this.entriesLengthCheckMask) != 0L)
		{
			long currentMask   = this.entriesLengthCheckMask;
			int  currentLength = this.entriesCount;
			
			while(currentLength < Long.SIZE && (keys & currentMask) != 0L)
			{
				currentMask <<= 1;
				currentLength++;
			}
			
			this.enlargeArray(currentLength);
		}
		return this.entries;
	}
	
	private void enlargeArray(final int requiredSize)
	{
		final BitmapEntry<E, I, Long>[] newArray = BitmapEntry.createEntriesArray(requiredSize);
		System.arraycopy(this.entries, 0, newArray, 0, this.entries.length);
		// note: new entries are only added on demand because this massively simplifies removing logic.
		
		// note: no need to deallocate anything here since all level instances remain the same.
		this.entries = newArray;
		this.updateCachedEntriesLength();
		
		// instance has changed because the array has changed.
		this.markStateChangeInstance();
	}
	
	@Override
	public final void iterateEntries(final Consumer<? super BitmapEntry<?, ?, ?>> logic)
	{
		synchronized(this.parentMap())
		{
			// note: entry is null if that bit position has no 1 bit accross ALL entities, i.e. is irrelevant for a query.
			for(final BitmapEntry<E, I, Long> entry : this.entries)
			{
				if(entry == null)
				{
					continue;
				}
				logic.accept(entry);
			}
		}
	}
	
	@Override
	public <C extends Consumer<? super Long>> C iterateKeys(final C logic)
	{
		synchronized(this.parentMap())
		{
			for(final BitmapEntry<E, I, Long> entry : this.entries)
			{
				if(entry == null)
				{
					continue;
				}
				logic.accept(entry.key());
			}
			
			return logic;
		}
	}
	
	@Override
	public final int entryCount()
	{
		return this.entriesCount;
	}
	
	protected long indexBinary(final I entity)
	{
		final long keys = this.indexer.indexBinary(entity);
		BinaryIndexer.Static.validate(keys, this.indexer);
		return keys;
	}
	
	@Override
	protected void internalAddToEntry(final long entityId, final I entity)
	{
		// composite binary key: the position of each bit is equal to the corresponding entry's array index.
		final long keys = this.indexBinary(entity);
		if(keys == 0L)
		{
			// if there is no 1 bit at all in the keys, nothing can be added.
			return;
		}
		
		// must get the entry under the lock protection to ensure consistency.
		final BitmapEntry<E, I, Long>[] entries = this.getEntries(keys);
		final int entriesLength = this.entriesCount;
		for(int i = 0; i < entriesLength; i++)
		{
			if((keys >>> i & 1) == 0)
			{
				continue;
			}
			if(entries[i] == null)
			{
				// new entries are only added on demand because this massively simplifies removing logic.
				this.internalAddEntry(i);
			}
			entries[i].add(entityId);
		}
	}
	
	static Long arrayIndexToKey(final int i)
	{
		return 1L << i;
	}
	
	private void internalAddEntry(final int i)
	{
		this.entries[i] = new BitmapEntry<>(this, arrayIndexToKey(i), i, true);
		this.markStateChangeInstance();
	}
	
	public void internalRemove(final long entityId, final I entity)
	{
		final long keys = this.indexBinary(entity);
		this.internalRemove(entityId, keys);
	}
	
	public void internalRemoveAll()
	{
		this.entries = BitmapEntry.createEntriesArray(0);
		this.updateCachedEntriesLength();
		this.markStateChangeChildren();
	}
	
	final void internalRemove(final long entityId, final long keys)
	{
		// composite binary key: the position of each bit is equal to the corresponding entry's array index.
		if(keys == 0L)
		{
			// if there is no 1 bit at all in the keys, nothing can be removed.
			return;
		}
		if(!this.fitsInEntries(keys))
		{
			// if the keys value's 1-bits don't fit in the entry range, the entity cannot be contained.
			return;
		}
		
		// must get the entry under the lock protection to ensure consistency.
		final BitmapEntry<E, I, Long>[] entries = this.entries;
		final int entriesLength = this.entriesCount;
		for(int i = 0; i < entriesLength; i++)
		{
			if((keys >>> i & 1) == 0)
			{
				continue;
			}
			this.internalRemoveFromEntry(entityId, entries[i]);
		}
		
		// keys is guaranteed to contain at least one 1 bit at this point, so mark changed.
		this.markStateChangeChildren();
	}
	
	@Override
	protected final void removeEntry(final BitmapEntry<E, I, Long> entry)
	{
		this.entries[entry.position()] = null;
	}
	
	public BitmapResult internalQuery(final long key)
	{
		return new BitmapResult.ChainAnd(this.internalQueryResults(key));
	}
	
	public final BitmapResult[] internalQueryResults(final long keys)
	{
		if(!this.fitsInEntries(keys))
		{
			// if the keys value's 1-bits don't fit in the entry range, the key cannot be contained.
			return EMPTY_RESULT;
		}
		
		final BitmapResult[] results = new BitmapResult[this.entriesCount];
		int r = 0;
		
		for(int i = 0; i < this.entriesCount; i++)
		{
			if((keys >>> i & 1) != 0)
			{
				// if the key demands a 1 bit at index i, but the index doesn't even have an entry for i, the key CANNOT be contained.
				if(this.entries[i] == null)
				{
					return EMPTY_RESULT;
				}
				
				// add condition for index i to filter for 1-bits.
				results[r++] = this.entries[i].createResult();
			}
			else
			{
				// if the key demands a 0 bit at index i, but the index doesn't even have an entry for i, it is irrelevant.
				if(this.entries[i] == null)
				{
					continue;
				}
				
				// add inverted condition for index i to filter for 0-bits.
				results[r++] = new BitmapResult.Not(this.entries[i].createResult());
			}
		}
		
		// never return null since it is perfectly valid to query for a key that does not exist.
		if(r == 0)
		{
			return EMPTY_RESULT;
		}
		
		XSort.insertionsort(results, BitmapResult::andOptimize, 0, r);
		
		return results;
	}
	
	// Required for Internal interface.
	@Override
	public boolean internalContains(final I entity)
	{
		final long keys = this.indexBinary(entity);
		
		return this.internalContainsKeys(keys);
	}
	
	private boolean fitsInEntries(final long keys)
	{
		return (keys & this.entriesLengthCheckMask) == 0L;
	}
	
	public final boolean internalContainsKeys(final long keys)
	{
		if(!this.fitsInEntries(keys))
		{
			// if the keys value's 1-bits don't fit in the entry range, the key cannot be contained.
			return false;
		}
		
		// quick check without instantiations to see if maybe the entity cannot be contained in the first place.
		for(int i = 0; i < this.entriesCount; i++)
		{
			if((keys >>> i & 1) == 1 && this.entries[i] == null)
			{
				// keys value requires an entry at index i (1 bit), but there is no entry, so the entity cannot be contained.
				return false;
			}
		}
		
		final BitmapResult[] results = this.internalQueryResults(keys);
		if(results == EMPTY_RESULT)
		{
			return false;
		}
		
		final GigaMap<E> parentMap = this.parent().parentMap();
		
		// if the entity might be contained, a full query result evaluation has to be performed. But without loading entities.
		try
		{
			// contains function throws a Break on the first encounter of a match (aka entity is contained)
			GigaMap.Default.execute(this.contains, results, 0, parentMap.size(), null);
		}
		catch(final ThrowBreak b)
		{
			// contains function aborted the execution because of a match, aka the entity is contained, so return true.
			return true;
		}
		
		// execution ran through until the end without finding any match. So the entity is not contained, return false.
		return false;
	}
	
	public final BitmapResult search(final Predicate<? super Long> predicate)
	{
		final BitmapResult[] results = new BitmapResult[this.entries.length];
		int r = 0;
		
		synchronized(this.parentMap())
		{
			for(final BitmapEntry<E, I, Long> entry : this.entries)
			{
				if(predicate.test(entry.key()))
				{
					results[r++] = entry.createResult();
				}
				else
				{
					results[r++] = new BitmapResult.Not(entry.createResult());
				}
			}
		}
		
		// note: r is always results.length since every position is guaranteed to get a result (if/else)
		XSort.insertionsort(results, BitmapResult::andOptimize);
		
		return new BitmapResult.ChainAnd(results);
	}
	
	final void internalHandleChanged(
		final long entityId ,
		final I    newEntity,
		final long oldKeys
	)
	{
		final long newKeys = this.indexBinary(newEntity);
		this.internalHandleChanged(oldKeys, entityId, newKeys);
	}
	
	final void internalHandleChanged(final long oldKeys, final long entityId, final long newKeys)
	{
		final long dispensableEntries = oldKeys & ~newKeys;
		final long additionalEntries  = newKeys & ~oldKeys;

		boolean changed = false;

		final BitmapEntry<E, I, Long>[] entries = this.getEntries(newKeys);
		for(int i = 0; i < entries.length; i++)
		{
			if((dispensableEntries >>> i & 1) == 1)
			{
				this.internalRemoveFromEntry(entityId, entries[i]);
				changed = true;
			}
			else if((additionalEntries >>> i & 1) == 1)
			{
				if(entries[i] == null)
				{
					// new entries are only added on demand because this massively simplifies removing logic.
					this.internalAddEntry(i);
				}
				entries[i].add(entityId);
				changed = true;
			}
			// else: no change, so nothing to do.
		}

		if(changed)
		{
			this.markStateChangeChildren();
		}
	}

	@Override
	protected Long indexEntity(final I entity)
	{
		return this.indexer().index(entity);
	}
	
	public ChangeHandler getChangeHandler(final I oldEntity)
	{
		if(oldEntity == null)
		{
			return NullChangeChandler.SINGLETON;
		}
		
		final long keys = this.indexBinary(oldEntity);
		
		return new BinaryChangeHandler<>(this, keys);
	}
	
	static final class BinaryChangeHandler<I> implements ChangeHandler
	{
		final private AbstractBitmapIndexBinary<?, I> index;
		final private long                            keys ;
		
		BinaryChangeHandler(
			final AbstractBitmapIndexBinary<?, I> index,
			final long      keys
		)
		{
			super();
			this.index = index;
			this.keys  = keys ;
		}
		
		@Override
		public boolean isEqual(final ChangeHandler other)
		{
			return other instanceof BinaryChangeHandler
				&& this.index == ((BinaryChangeHandler<?>)other).index
				&& this.keys == ((BinaryChangeHandler<?>)other).keys
			;
		}
		
		@Override
		public void removeFromIndex(final long entityId)
		{
			// marks stateChangeChildren internally
			this.index.internalRemove(entityId, this.keys);
		}
		
		@Override
		public void changeInIndex(final long entityId, final ChangeHandler prevEntityHandler)
		{
			this.index.internalHandleChanged(((BinaryChangeHandler<?>)prevEntityHandler).keys, entityId, this.keys);
		}
		
	}
	
}
