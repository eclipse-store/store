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

import org.eclipse.store.gigamap.types.BitmapIndex.Abstract;
import org.eclipse.serializer.branching.ThrowBreak;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.XSort;
import org.eclipse.serializer.collections.types.XGettingList;
import org.eclipse.serializer.persistence.types.Storer;

import java.util.function.Consumer;
import java.util.function.Predicate;


/**
 * The AbstractCompositeBitmapIndex is an abstract base class designed to handle composite bitmap index structures.
 * It extends the functionality of the {@link Abstract} and {@link TopLevel} classes.
 * <p>
 * A composite bitmap index organizes data across multiple sub-indices using a composite hierarchical structure.
 * Each sub-index may represent a segment of the composite value. Logical operations are applied across these sub-indices
 * to facilitate efficient querying and data management.
 * <p>
 * This class provides core functionality such as managing sub-indices, indexing entities, and supporting complex
 * search predicates, while delegating specific behaviors to concrete implementations.
 */
public abstract class AbstractCompositeBitmapIndex<E, KS /*extends Array*/, K>
extends Abstract<E, E, KS>
implements BitmapIndex.TopLevel<E, KS>
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	final CompositeIndexer<? super E, KS> indexer;
	
	Sub<E, KS, K>[] subIndices; // kind of the "entries", actually an intermediate layer with wrapped entries.
	
	private transient final ContainsBreaker<E> contains;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	protected AbstractCompositeBitmapIndex(
		final BitmapIndices<E>                parent      ,
		final String                          name        ,
		final CompositeIndexer<? super E, KS> indexer     ,
		final boolean                         stateChanged
	)
	{
		super(parent, name, stateChanged);
		this.indexer    = indexer                 ;
		this.subIndices = createSubIndicesArray(0);
		this.contains   = new ContainsBreaker<>() ;
		
		this.initializeTransientState();
	}
	
	protected AbstractCompositeBitmapIndex(
		final BitmapIndices<E>                parent ,
		final String                          name   ,
		final CompositeIndexer<? super E, KS> indexer
	)
	{
		this(parent, name, indexer, true);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	protected abstract void initializeTransientState();
	
	protected abstract boolean isOversized(KS keys);
	
	protected abstract void ensureSubIndices(KS keys);
	
	protected abstract void clearCarrier();
	
	protected abstract KS carrier();
	
	protected abstract boolean isEmpty(KS keys, int i);
	
	protected abstract void clear(KS keys, int i);
	
	@Override
	protected final void storeChangedChildren(final Storer storer)
	{
		for(final Sub<E, KS, K> subIndex : this.subIndices)
		{
			// calls #registerChangeStores in the index type handler, necessary detour due to interface abstraction.
			storer.store(subIndex);
		}
	}
	
	@Override
	protected final void clearChildrenStateChangeMarkers()
	{
		for(final Sub<E, KS, K> subIndex : this.subIndices)
		{
			subIndex.clearStateChangeMarkers2();
		}
	}
	
	final Sub<E, KS, K>[] subIndices()
	{
		return this.subIndices;
	}
	
	@Override
	public CompositeIndexer<? super E, KS> indexer()
	{
		return this.indexer;
	}
	
	@Override
	protected KS indexEntity(final E entity)
	{
		return this.indexer().index(entity, this.carrier());
	}
	
	@Override
	public final void iterateEntries(final Consumer<? super BitmapEntry<?, ?, ?>> logic)
	{
		synchronized(this.parentMap())
		{
			for(final Sub<E, KS, K> subIndex : this.subIndices)
			{
				subIndex.iterateEntries(logic);
			}
		}
	}
	
	@Override
	public final int entryCount()
	{
		synchronized(this.parentMap())
		{
			int entryCount = 0;
			for(final Sub<E, KS, K> subIndex : this.subIndices)
			{
				entryCount += subIndex.entryCount();
			}
			
			return entryCount;
		}
	}
	
	@Override
	public final void internalAdd(final long entityId, final E entity)
	{
		try
		{
			super.internalAdd(entityId, entity);
		}
		finally
		{
			this.clearCarrier();
		}
	}
	
	@Override
	public final void internalAddAll(final long firstEntityId, final Iterable<? extends E> entities)
	{
		try
		{
			super.internalAddAll(firstEntityId, entities);
		}
		finally
		{
			this.clearCarrier();
		}
	}
	
	@Override
	public final void internalAddAll(final long firstEntityId, final E[] entities)
	{
		try
		{
			super.internalAddAll(firstEntityId, entities);
		}
		finally
		{
			this.clearCarrier();
		}
	}
	
	@Override
	protected final void removeEntry(final BitmapEntry<E, E, KS> entry)
	{
		// not used in this implementation since the actual removing is done in a sub index
		throw new Error();
	}
	
	@Override
	public final void internalRemove(final long entityId, final E entity)
	{
		final KS keys = this.indexer.index(entity, this.carrier());
		this.internalRemoveForKeys(entityId, keys);
	}
	
	@Override
	public final void internalRemoveAll()
	{
		try
		{
			for(final Sub<E, KS, K> subIndex : this.subIndices)
			{
				subIndex.internalRemoveAll();
			}
			this.markStateChangeChildren();
		}
		finally
		{
			this.clearCarrier();
		}
	}
	
	final void internalRemoveForKeys(final long entityId, final KS keys)
	{
		try
		{
			for(final Sub<E, KS, K> subIndex : this.subIndices)
			{
				subIndex.internalRemove(entityId, keys);
			}
			// note: sub indices are never removed, even if they become empty.
		}
		finally
		{
			this.clearCarrier();
		}
	}
	
	@Override
	protected final void internalAddToEntry(final long entityId, final E entity)
	{
		final KS keys = this.indexer.index(entity, this.carrier());
		this.ensureSubIndices(keys);
		
		for(int i = 0; i < this.subIndices.length; i++)
		{
			if(this.isEmpty(keys, i))
			{
				// no need to add a 0 bit to the index
				continue;
			}
			this.subIndices[i].internalAddToEntry(entityId, keys);
			this.markStateChangeChildren();
		}
	}
	
	@Override
	public final <C extends Consumer<? super KS>> C iterateKeys(final C logic)
	{
		synchronized(this.parentMap())
		{
			this.clearCarrier();
			final KS carrier = this.carrier();
			try
			{
				for(int i = 0; i < this.subIndices.length; i++)
				{
					this.subIndices[i].iterateKeys(carrier, i, logic);
					this.clear(carrier, i); // clear current position in carrier
				}
			}
			finally
			{
				this.clearCarrier();
			}
			
			return logic;
		}
	}
	
	@Override
	public final BitmapResult internalQuery(final KS keys)
	{
		final BitmapResult[] results = new BitmapResult[this.subIndices.length];
		int r = 0;
		
		for(int i = 0; i < this.subIndices.length; i++)
		{
			// filter for selected positions to allow querying for specific positions instead of always all positions.
			if(!this.isEmpty(keys, i))
			{
				continue;
			}
			
			// query sub index at position i for the non-null key value
			final BitmapResult result = this.subIndices[i].internalQueryForKeys(keys);
			
			// if one sub index has no entry at all for a non-null key value, the whole key cannot be contained.
			if(result == EMPTY_RESULT)
			{
				return EMPTY_RESULT;
			}
			
			// collect proper sub-result
			results[r++] = result;
		}
		
		// if not even one proper sub-result has been found, the key cannot be contained.
		if(r == 0)
		{
			// never return null since it is perfectly valid to query for a key that is not contained.
			return EMPTY_RESULT;
		}
		
		XSort.insertionsort(results, BitmapResult::andOptimize, 0, r);
		
		// "normal" case of the key being potentially contained, so all sub-results have to be evaluated as an AND chain.
		return new BitmapResult.ChainAnd(results);
	}
	
	@Override
	public final boolean internalContains(final E entity)
	{
		final KS keys = this.indexer.index(entity, this.carrier());
		if(this.isOversized(keys))
		{
			// if the keys contains more key values than there currently are sub indices, the entity cannot possibly be contained.
			return false;
		}
		
		for(int i = 0; i < this.subIndices.length; i++)
		{
			// a null key value means it is irrelevant for the query
			if(this.isEmpty(keys, i))
			{
				continue;
			}
			
			if(!this.subIndices[i].internalContains(keys))
			{
				// if at least one sub index does not contain a non-null key, the whole entity cannot be contained.
				return false;
			}
		}
		
		final BitmapResult[] results = new BitmapResult[this.subIndices.length];
		int r = 0;
		
		for(int i = 0; i < this.subIndices.length; i++)
		{
			if(this.isEmpty(keys, i))
			{
				continue;
			}
			
			// #query is guaranteed to no return a NO_RESULT, since that would have caused a return in the loop above.
			results[r++] = this.subIndices[i].internalQueryForKeys(keys);
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
	
	@SuppressWarnings("unchecked")
	private CompositePredicate<KS> ensureCompositePredicate(final Predicate<? super KS> predicate)
	{
		return predicate instanceof CompositePredicate
			? (CompositePredicate<KS>) predicate
			: this.wrap(predicate, this.carrier())
		;
	}
	
	protected abstract CompositePredicate<KS> wrap(Predicate<? super KS> subject, KS carrier);
	
	protected abstract CompositeChangeToken<E, KS, K> createChangeToken(E oldEntity);
	
	@Override
	public final ChangeHandler getChangeHandler(final E oldEntity)
	{
		if(oldEntity == null)
		{
			return NullChangeChandler.SINGLETON;
		}
		
		return this.createChangeToken(oldEntity);
	}
	
	static XGettingList<BitmapResult>[] createResultsArray(final int length)
	{
		@SuppressWarnings("unchecked")
		final XGettingList<BitmapResult>[] results = new XGettingList[length];
		
		return results;
	}
	
	/*
	 * Searching in a composite index is a little bit more complex:
	 * All entries selected for a sub index are combined via OR logic.
	 * Then all sub index selections are combined via AND logic.
	 * Example:
	 * A Date timestamp is sliced and indexed into year, month, day.
	 * Each one is represented by a sub index.
	 * Each sub index' entries are the values (e.g. for year 2020, 2021, 2022, ...)
	 * Selecting values of a sub index uses OR logic (e.g. year is 2020 OR 2021 OR 2022).
	 * It wouldn't make sense to apply AND logic to an index' entries (e.g. year must be 2020 AND 2021...??)
	 *
	 * Then all the resulting OR chain conditions are combined via AND logic:
	 * year is 2020 OR 2021 OR 2022
	 * AND
	 * month is 8 OR 9
	 * AND
	 * day is 28 OR 29 OR 30
	 *
	 * HOWEVER ... for a BinaryCompositeIndex, the inner logic must be AND, as well.
	 * Because there, entries are not whole values, but bits of a value.
	 * So for example searching for value 5 is bit#0 AND bit#2.
	 * AND ... all bits (BinaryComposite SubIndex Entries) that are no selected, must be EXCLUDED.
	 * (e.g. bit#1 MUST be 0, not any value)
	 *
	 */
	@Override
	public final BitmapResult search(final Predicate<? super KS> passedPredicate)
	{
		final XGettingList<BitmapResult>[] results = createResultsArray(this.subIndices.length);
		
		synchronized(this.parentMap())
		{
			try
			{
				final CompositePredicate<KS> predicate = this.ensureCompositePredicate(passedPredicate);
				
				for(int i = 0; i < this.subIndices.length; i++)
				{
					if(!predicate.setSubKeyPosition(i))
					{
						// null means "skip this subIndex position's result (SubResult) all together.
						results[i] = null;
						continue;
					}
					
					final BulkList<BitmapResult> subResults = BulkList.New(16);
					this.addEntryResults(i, predicate, subResults);
					results[i] = subResults;
					predicate.clearSubKeyPosition(i);
				}
				
			}
			finally
			{
				// normally not needed, but just in case.
				this.clearCarrier();
			}
		}
		
		return this.createSearchResult(results);
	}
	
	protected BitmapResult createSearchResult(final XGettingList<BitmapResult>[] results)
	{
		final BulkList<BitmapResult> subResultAnds = BulkList.New(results.length);
		for(int i = 0; i < results.length; i++)
		{
			if(results[i] == null)
			{
				continue;
			}
			this.addSubResult(results, i, subResultAnds);
		}
		
		final BitmapResult[] selected = subResultAnds.toArray(BitmapResult.class);
		XSort.insertionsort(selected, BitmapResult::andOptimize);
		
		return new BitmapResult.ChainAnd(selected);
	}
	
	protected void addEntryResults(
		final int                    subPosition,
		final CompositePredicate<KS> predicate  ,
		final BulkList<BitmapResult> subResults
	)
	{
		this.subIndices[subPosition].iterateEntries(e ->
		{
			if(predicate.test(subPosition, e.key()))
			{
				subResults.add(e.createResult());
			}
		});
	}
	
	protected void addSubResult(
		final XGettingList<BitmapResult>[] results,
		final int                          i      ,
		final BulkList<BitmapResult> subResultAnds
	)
	{
		final XGettingList<BitmapResult> subResults = results[i];
		subResultAnds.add(new BitmapResult.ChainOr(subResults.toArray(BitmapResult.class)));
	}
	
	final void internalHandleChanged(final KS oldKeys, final long entityId, final KS newKeys)
	{
		this.ensureSubIndices(newKeys);

		for(final Sub<E, KS, K> subIndex : this.subIndices)
		{
			subIndex.internalHandleChanged(oldKeys, entityId, newKeys);
		}
		this.markStateChangeChildren();
	}
	
	public static <E, KS, K> Sub<E,KS, K>[] createSubIndicesArray(final int arrayLength)
	{
		@SuppressWarnings("unchecked")
		final Sub<E, KS, K>[] array = new Sub[arrayLength];
		
		return array;
	}
	
	
	
	// A sub indexer must be its own sub indexer, which converts/extracts a key K from a number of keys KS (usually an array) for its position.
	public interface Sub<E, KS, K> extends GigaIndex<E>, Indexer<KS, K>
	{
		@Override
		public default boolean isSuitableAsUniqueConstraint()
		{
			// a sub index is never suitable as unique constraint
			return false;
		}
		
		// used by type handler to update position after loading, position is not part of the persistent state
		public void setPosition(int position);
		
		public BitmapResult internalQueryForKeys(KS keys);
		
		public <C extends Consumer<? super KS>> C iterateKeys(KS keys, int index, C logic);
		
		public BitmapEntry<E, KS, K> internalEnsureEntry(KS indexable);
		
		public boolean internalContains(KS indexable);
		
		public void internalRemove(long entityId, KS indexable);
		
		public void internalRemoveFromEntry(long entityId, BitmapEntry<E, KS, K> entry);
		
		public void internalRemoveAll();
		
		public void iterateEntries(Consumer<? super BitmapEntry<?, ?, ?>> logic);
		
		public void internalAddToEntry(long entityId, KS indexable);
		
		public void internalHandleChanged(KS oldKeys, long entityId, KS newKeys);
		
		public int entryCount();
		
		public void markStateChangeChildren2();
		
		public void clearStateChangeMarkers2();
		
		@Override
		public String name();
	}
	
	
}
