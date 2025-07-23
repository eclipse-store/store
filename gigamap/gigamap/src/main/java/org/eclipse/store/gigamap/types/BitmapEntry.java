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

import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.persistence.types.Unpersistable;


/**
 * BitmapEntry is a data structure that represents an individual entry in a bitmap index.
 * It holds details such as the entry's associated key, position, and level-3 bitmap data.
 * This class supports operations on individual entries, including adding, removing,
 * and manipulating their internal state.
 *
 * @param <E> The type representing the entity in the index.
 * @param <I> The type of the identifier for the entities in the index.
 * @param <K> The type of the key associated with the entry in the index.
 */
public final class BitmapEntry<E, I, K>
extends AbstractStateChangeFlagged
implements ChangeHandler, Unpersistable
{
	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////
	
	static BinaryTypeHandler<BitmapEntry<?, ?, ?>> provideTypeHandler()
	{
		return BinaryHandlerBitmapEntry.New();
	}
	
	@SuppressWarnings("unchecked")
	static <E, I, K> BitmapEntry<E, I, K>[] createEntriesArray(final int length)
	{
		return new BitmapEntry[length];
	}
	
		
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
		
	        final BitmapLevel3 level3  ;
	private final int          position;

	private transient BitmapIndex.Abstract<E, I, K> parent; // must be transient for when the entry is held in another cell.
	private transient K                             key   ; // must be transient for when the entry is held in another cell.
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
		
	BitmapEntry(
		final BitmapIndex.Abstract<E, I, K> parent      ,
		final K                                 key         ,
		final int                               position    ,
		final boolean                           stateChanged
	)
	{
		super(stateChanged);
		this.parent   = parent  ;
		this.key      = key     ;
		this.position = position;
		this.level3   = BitmapLevel3.New();
	}
		
		
		
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
		
	public String indexName()
	{
		return this.parent.name();
	}
	
	public K key()
	{
		return this.key;
	}
	
	public int position()
	{
		return this.position;
	}
	
	public boolean isEmpty()
	{
		return this.getLevel3().totalSegmentCount() == 0L;
	}
	
	public BitmapLevel3 getLevel3()
	{
		final BitmapLevel3 level3 = this.level3;
		
		// no-op for default implementation. Parent context back reference is only needed in the debug implementation.
		level3.setParentContext(this);
		
		return level3;
	}
	
	public BitmapResult createResult()
	{
		return new EntryResult(this.getLevel3());
	}
	
	final void add(final long entityId)
	{
		this.getLevel3().add(entityId);
		this.markStateChangeChildren();
	}
	
	final boolean remove(final long entityId)
	{
		final boolean result = this.getLevel3().remove(entityId);
		this.markStateChangeChildren();
		
		return result;
	}
	
	final void removeAll()
	{
		this.getLevel3().removeAll();
		this.markStateChangeChildren();
	}
	
	final void ensureCompressed()
	{
		this.getLevel3().ensureCompressed();
	}
	
	final void ensureDecompressed()
	{
		this.getLevel3().ensureDecompressed();
	}
	
	void internalSetBackReferences(final BitmapIndex.Abstract<E, I, K> parent, final K key)
	{
		this.parent = parent;
		this.key    = key   ;
	}
	
	@Override
	protected void storeChangedChildren(final Storer storer)
	{
		// only one child to store and it must be handled via its type handler, so generic store call.
		storer.store(this.level3);
	}

	@Override
	protected void clearChildrenStateChangeMarkers()
	{
		this.level3.clearStateChangeMarkers();
	}
	
	@Override
	public boolean isEqual(final ChangeHandler other)
	{
		return other == this;
	}

	@Override
	public void removeFromIndex(final long entityId)
	{
		// marks stateChangeChildren internally
		this.parent.internalRemoveFromEntry(entityId, this);
	}

	@Override
	public void changeInIndex(final long entityId, final ChangeHandler prevEntityHandler)
	{
		prevEntityHandler.removeFromIndex(entityId);
		this.add(entityId);
		this.parent.markStateChangeChildren();
	}
	
	final String toInfoString()
	{
		return toInfoString(this);
	}
	
	static String toInfoString(final BitmapEntry<?, ?, ?> instance)
	{
		return "Index \"" + instance.indexName() + "\" key \"" + instance.key() + "\"";
	}
	
	@Override
	public String toString()
	{
		return XChars.systemString(this) + ": " + this.toInfoString();
	}
			
}
