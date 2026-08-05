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
import org.eclipse.serializer.persistence.types.Unpersistable;


/**
 * GigaLevel1 represents a generic container that manages an array of entities.
 * It extends {@link AbstractStateChangeFlagged} to incorporate state change tracking
 * capabilities but functions as a leaf node in the state hierarchy, meaning it
 * does not track state changes for children since it does not conceptually
 * contain any.
 * <p>
 * This class provides functionality to initialize and manage a typed array of
 * entities. It ensures safe type handling during runtime via instantiation-specific
 * operations despite type erasure.
 *
 * @param <E> The type of elements stored in this container.
 */
public final class GigaLevel1<E> extends AbstractStateChangeFlagged implements Unpersistable
{
	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////
	
	static BinaryTypeHandler<GigaLevel1<?>> provideTypeHandler()
	{
		return BinaryHandlerGigaLevel1.New();
	}
	
	@SuppressWarnings("unchecked")
	private E[] createEntitiesArray(final int length)
	{
		return (E[])new Object[length];
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	E[] entities;
		
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	GigaLevel1(final int length, final boolean newInstance)
	{
		super(newInstance);
		this.entities = this.createEntitiesArray(length);
	}



	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	/**
	 * Whether this segment currently holds no entity at all, which makes it eligible for being released
	 * from its parent {@link GigaLevel2}.
	 * <p>
	 * {@code scanStartIndex} only steers where the scan begins, it does not narrow what is examined:
	 * every slot is checked, ending with {@code scanStartIndex} itself, so the answer never depends on
	 * the caller having cleared that slot. Starting right after the slot a removal just cleared is what
	 * makes a sequential drain, forward or backward, hit a surviving neighbour on the very first
	 * comparison instead of walking the whole array.
	 * <p>
	 * Bound to the array's own length rather than to the parent map's configured segment size, simply
	 * because this class owns the array and needs no state of the map to walk it. The two always agree:
	 * the length exponents are persisted with the map and a reloaded map is constructed from those, so
	 * a reloaded array's length is the configured segment size.
	 *
	 * @param scanStartIndex the index to start scanning after, typically the slot that was cleared last
	 * @return {@code true} if every slot is {@code null}, {@code false} otherwise
	 */
	final boolean isEmpty(final int scanStartIndex)
	{
		final E[] entities = this.entities;
		final int length   = entities.length;

		for(int i = 1; i <= length; i++)
		{
			int index = scanStartIndex + i;
			if(index >= length)
			{
				index -= length;
			}
			if(entities[index] != null)
			{
				return false;
			}
		}

		return true;
	}

	@Override
	protected void storeChangedChildren(final Storer storer)
	{
		// GigaLevel1 may never be marked as having children changed since is it a leaf instance. Not perfectly clean.
		throw new UnsupportedOperationException();
	}
	
	@Override
	protected void clearChildrenStateChangeMarkers()
	{
		// no-op since there are no state-change-marked children.
	}
	
}
