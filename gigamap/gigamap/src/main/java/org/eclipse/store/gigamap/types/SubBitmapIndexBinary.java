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
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;

import java.util.function.Consumer;


/**
 * The SubBitmapIndexBinary class is a specialized implementation of a binary
 * index within a composite array, particularly tailored for managing indices
 * in bitmap data structures. It defines a sub-indexer that operates on a
 * specific position of a composite array, where each position represents a
 * distinct indexable element in the bitmap system. This functionality allows
 * efficient indexing and querying operations for entities mapped in the
 * parent bitmap index.
 *
 * @param <E> The type of elements stored in the bitmap index.
 */
public final class SubBitmapIndexBinary<E>
extends AbstractBitmapIndexBinary<E, long[]>
implements Sub<E, long[], Long>, BinaryIndexer<long[]>
{
	static BinaryTypeHandler<SubBitmapIndexBinary<?>> provideTypeHandler()
	{
		return BinaryHandlerSubBitmapIndexBinary.New();
	}
	
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private int position; // actually "index" in the composite array, but this term conflicts with the "Index" concept, hence "position".
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	SubBitmapIndexBinary(
		final BitmapIndices<E>              parent      ,
		final String                        name        ,
		final int                           position    ,
		final boolean                       stateChanged
	)
	{
		super(parent, name + ":" + position, null, stateChanged);
		this.position = position;
		this.initializeIndexer(this);
	}
	
	SubBitmapIndexBinary(
		final BitmapIndices<E>              parent      ,
		final String                        name        ,
		final int                           position
	)
	{
		super(parent, name + ":" + position, null);
		this.position = position;
		this.initializeIndexer(this);
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
	public final Class<Long> keyType()
	{
		return Long.class;
	}
	
	@Override
	public final Long index(final long[] keys)
	{
		return keys[this.position];
	}
	
	@Override
	public final long indexBinary(final long[] keys)
	{
		return keys[this.position];
	}
	
	@Override
	public <C extends Consumer<? super long[]>> C iterateKeys(final long[] carrier, final int i, final C logic)
	{
		this.iterateKeys(k ->
		{
			carrier[i] = k;
			logic.accept(carrier);
		});
		
		return logic;
	}
	
	@Override
	public void internalAddToEntry(final long entityId, final long[] indexable)
	{
		super.internalAddToEntry(entityId, indexable);
		this.markStateChangeChildren();
	}
	
	@Override
	public BitmapResult internalQueryForKeys(final long[] keys)
	{
		return this.internalQuery(keys[this.position]);
	}
	
	@Override
	public void internalHandleChanged(final long[] oldKeys, final long entityId, final long[] newKeys)
	{
		this.internalHandleChanged(this.key(oldKeys), entityId, this.key(newKeys));
	}

    private long key(final long[] keys)
    {
        return keys.length > this.position
            ? keys[this.position]
            : 0L
        ;
    }
	
}
