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

import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.Storer;

import java.util.function.Consumer;
import java.util.function.Predicate;


/**
 * The SingleBitmapIndex class represents a specialized bitmap index where the indexing logic
 * is based on a single Boolean value representing the presence or absence of entities.
 * <p>
 * A SingleBitmapIndex is designed for scenarios where there is only one logical key (`Boolean.TRUE`)
 * and the corresponding index-related operations depend solely on the presence of this key.
 *
 * @param <E> The type of entities that the bitmap index handles and indexes.
 */
// Actually "Boolean", but that would create ambiguity with the java lang type.
public final class SingleBitmapIndex<E>
extends BitmapIndex.Abstract<E, E, Boolean>
implements BitmapIndex.TopLevel<E, Boolean>, ChangeHandler
{
	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////
	
	static BinaryTypeHandler<SingleBitmapIndex<?>> provideTypeHandler()
	{
		return BinaryHandlerBitmapIndexSingle.New();
	}
	
	@SuppressWarnings("unchecked") // cast safety guaranteed by calling logic.
	static <E, I> Internal<E, I> internalCreate(
		final BitmapIndices<E>      parent ,
		final String                name   ,
		final Indexer<? super E, I> indexer
	)
	{
		return (Internal<E, I>)new SingleBitmapIndex<>(parent, name, (Indexer<? super E, Boolean>)indexer);
	}
	
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	final Indexer<? super E, Boolean>  indexer;
	BitmapEntry<E, E, Boolean> entry;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	SingleBitmapIndex(
		final BitmapIndices<E>            parent      ,
		final String                      name        ,
		final Indexer<? super E, Boolean> indexer     ,
		final BitmapEntry<E, E, Boolean>  entry       ,
		final boolean                     stateChanged
	)
	{
		super(parent, name, stateChanged);
		this.indexer = indexer;
		this.entry = entry;
	}

	SingleBitmapIndex(
		final BitmapIndices<E>            parent ,
		final String                      name   ,
		final Indexer<? super E, Boolean> indexer
	)
	{
		this(parent, name, indexer, null, true);
		this.entry = new BitmapEntry<>(this, Boolean.TRUE, 0, true);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public final Indexer<? super E, Boolean> indexer()
	{
		return this.indexer;
	}
	
	@Override
	public final BitmapResult internalQuery(final Boolean key)
	{
		if(key == null)
		{
			return EMPTY_RESULT;
		}
		if(key == true)
		{
			return this.createResult();
		}
		
		return this.entryResultInverted();
	}
	
	@Override
	protected Boolean indexEntity(final E entity)
	{
		return this.indexer.index(entity);
	}
	
	final void setEntry(final BitmapEntry<E, E, Boolean> entry)
	{
		this.entry = entry;
		entry.internalSetBackReferences(this, Boolean.TRUE);
	}
	
	@Override
	public final void iterateEntries(final Consumer<? super BitmapEntry<?, ?, ?>> logic)
	{
		logic.accept(this.entry);
	}
	
	@Override
	public final <C extends Consumer<? super Boolean>> C iterateKeys(final C logic)
	{
		logic.accept(Boolean.TRUE);
		
		return logic;
	}
	
	@Override
	public final int entryCount()
	{
		return 1;
	}
	
	final BitmapEntry<E, E, Boolean> entry()
	{
		return this.entry;
	}
	
	@Override
	protected void internalAddToEntry(final long entityId, final E indexable)
	{
		this.internalAdd(entityId, indexable);
	}
	
	@Override
	public final void internalAdd(final long entityId, final E entity)
	{
		final Boolean key = this.indexer.index(entity);
		if(key == null || !key)
		{
			return;
		}
		
		this.entry.add(entityId);
		this.markStateChangeChildren();
	}
	
	@Override
	public final void internalAddAll(final long firstEntityId, final Iterable<? extends E> entities)
	{
		final BitmapEntry<E, E, Boolean>  entry   = this.entry;
		final Indexer<? super E, Boolean> indexer = this.indexer;
		
		long currentEntityId = firstEntityId - 1;
		for(final E entity : entities)
		{
			// entityId must be incremented in any case to ensure consistency to the parent map.
			++currentEntityId;
			final Boolean key = indexer.index(entity);
			if(key == null || !key)
			{
				continue;
			}
			entry.add(currentEntityId);
		}
		this.markStateChangeChildren();
	}
	
	@Override
	public final void internalAddAll(final long firstEntityId, final E[] entities)
	{
		final BitmapEntry<E, E, Boolean>  entry   = this.entry;
		final Indexer<? super E, Boolean> indexer = this.indexer;
		
		long currentEntityId = firstEntityId - 1;
		for(final E entity : entities)
		{
			// entityId must be incremented in any case to ensure consistency to the parent map.
			++currentEntityId;
			final Boolean key = indexer.index(entity);
			if(key == null || !key)
			{
				continue;
			}
			entry.add(currentEntityId);
		}
		this.markStateChangeChildren();
	}
	
	@Override
	public final void internalRemove(final long entityId, final E entity)
	{
		final Boolean key = this.indexer.index(entity);
		if(key == null || !key)
		{
			return;
		}
		
		this.entry.remove(entityId);
		this.markStateChangeChildren();
		// no storing here. This is done efficiently via GigaMap#store and internal state change marking.
	}
	
	@Override
	protected final void removeEntry(final BitmapEntry<E, E, Boolean> entry)
	{
		// not used in this implementation since ... well ... there is only one entry. Lol.
		throw new Error();
	}
	
	@Override
	public final void internalRemoveAll()
	{
		this.entry.removeAll();
		this.markStateChangeChildren();
	}
	
	@Override
	public final boolean internalContains(final E entity)
	{
		return this.indexer.index(entity) == Boolean.TRUE;
	}
	
	private BitmapResult createResult()
	{
		return this.entry.createResult();
	}
	
	private BitmapResult entryResultInverted()
	{
		return new BitmapResult.Not(this.createResult());
	}
	
	@Override
	public final BitmapResult search(final Predicate<? super Boolean> predicate)
	{
		final BitmapResult[] results = new BitmapResult[2];
		int r = 0;
		if(predicate.test(Boolean.TRUE))
		{
			results[r++] = this.createResult();
		}
		if(predicate.test(Boolean.FALSE))
		{
			results[r++] = this.entryResultInverted();
		}
		
		// it is theoretically possible that the predicate just always returns false, so r == 0 must be checked.
		if(r == 0)
		{
			return EMPTY_RESULT;
		}
		
		return new BitmapResult.ChainOr(results);
	}
	
	@Override
	public final ChangeHandler getChangeHandler(final E oldEntity)
	{
		if(oldEntity == null)
		{
			return NullChangeChandler.SINGLETON;
		}
		
		final Boolean key = this.indexer.index(oldEntity);
		
		return key == null || !key
			? this // index itself acts as a (no-op) changeHandler for both FALSE and null.
			: this.entry
		;
	}
	
	@Override
	public final boolean isEqual(final ChangeHandler other)
	{
		return other == this;
	}
	
	@Override
	public final void removeFromIndex(final long entityId)
	{
		// marks stateChangeChildren internally
		this.entry.removeFromIndex(entityId);
	}
	
	@Override
	public final void changeInIndex(final long entityId, final ChangeHandler prevEntityHandler)
	{
		prevEntityHandler.removeFromIndex(entityId);
	}
	
	@Override
	protected final void storeChangedChildren(final Storer storer)
	{
		if(!this.entry.isChangedAndNotNew())
		{
			return;
		}
		this.entry.storeChildren(storer);
	}
	
	@Override
	protected final void clearChildrenStateChangeMarkers()
	{
		this.entry.clearStateChangeMarkers();
	}
	
	@Override
	public final boolean isSuitableAsUniqueConstraint()
	{
		// since this index type has only 2 distinct values, it is absolutely impossible to be used as a unique constraint.
		return false;
	}
	
}
