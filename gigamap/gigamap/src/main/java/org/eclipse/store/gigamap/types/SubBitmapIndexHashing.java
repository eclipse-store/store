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

import org.eclipse.store.gigamap.types.AbstractCompositeBitmapIndex.Sub;
import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.hashing.HashEqualator;
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;

import java.util.function.Consumer;


/**
 * A specialized sub-index class extending {@link AbstractBitmapIndexHashing} to handle bitmap
 * indexing specific to subsets of keys. This class provides functionality to index, query,
 * and manage keys in an array structure, where the index position is explicitly assigned
 * and operated upon within a composite array.
 *
 * @param <E> the type of elements in the parent bitmap index
 */
public final class SubBitmapIndexHashing<E>
extends AbstractBitmapIndexHashing<E, Object[], Object>
implements Sub<E, Object[], Object>
{
	static BinaryTypeHandler<SubBitmapIndexHashing<?>> provideTypeHandler()
	{
		return BinaryHandlerSubBitmapIndexHashing.New();
	}
	
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private int position; // actually "index" in the composite array, but this term conflicts with the "Index" concept, hence "position".
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	SubBitmapIndexHashing(
		final BitmapIndices<E>                                      parent      ,
		final String                                                name        ,
		final int                                                   position    ,
		final EqHashTable<Object, BitmapEntry<E, Object[], Object>> entries     ,
		final boolean                                               stateChanged
	)
	{
		super(parent, name, null, entries, stateChanged);
		this.position = position;
		this.initializeIndexer(this);
	}
	
	SubBitmapIndexHashing(
		final BitmapIndices<E>               parent       ,
		final String                         name         ,
		final HashEqualator<Object>          hashEqualator,
		final int                            position
	)
	{
		this(parent, name, position, EqHashTable.New(hashEqualator), true);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public void setPosition(final int position)
	{
		this.position = position;
	}
	
	@Override
	public final Class<Object> keyType()
	{
		return Object.class;
	}
	
	@Override
	public final Object index(final Object[] keys)
	{
		return keys[this.position];
	}
	
	@Override
	public Indexer<? super Object[], Object> indexer()
	{
		// sub index is its own sub indexer!
		return this;
	}
	
	@Override
	public boolean internalContains(final Object[] indexable)
	{
		return this.containsKey(indexable[this.position]);
	}
	
	@Override
	public BitmapResult internalQueryForKeys(final Object[] keys)
	{
		return this.internalQuery(keys[this.position]);
	}
	
	@Override
	public <C extends Consumer<? super Object[]>> C iterateKeys(final Object[] carrier, final int i, final C logic)
	{
		final Object currentElement = carrier[i];
		try
		{
			this.iterateKeys(k ->
			{
				carrier[i] = k;
				logic.accept(carrier);
			});
		}
		finally
		{
			carrier[i] = currentElement;
		}
		
		return logic;
	}
	
	@Override
	public void internalAddToEntry(final long entityId, final Object[] indexable)
	{
		super.internalAddToEntry(entityId, indexable);
		this.markStateChangeChildren();
	}
	
	@Override
	public final BitmapEntry<E, Object[], Object> internalLookupEntry(final Object[] indexable)
	{
		// only to link the method to the interface declaration to make it findable as an implementor.
		return super.internalLookupEntry(indexable);
	}
	
	@Override
	public final void internalHandleChanged(final Object[] oldKeys, final long entityId, final Object[] newKeys)
	{
		// only to link the method to the interface declaration to make it findable as an implementor.
		super.internalHandleChanged(oldKeys, entityId, newKeys);
	}
	
}
