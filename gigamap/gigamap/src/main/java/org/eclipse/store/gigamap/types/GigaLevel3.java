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
import org.eclipse.serializer.reference.Lazy;


/**
 * The GigaLevel3 class represents a specific hierarchical structure where it serves as the third level
 * (Level 3) of a lazy-loaded, nested data organization. It extends the {@link AbstractStateChangeFlagged}
 * class to track state changes at both the instance level and within its child elements.
 * <p>
 * This class maintains an array of segments, each being a Lazy reference to Level 2 objects (GigaLevel2).
 * The GigaLevel3 class is designed to handle state manipulation and lazy initialization of child elements.
 * <p>
 * Key features include:
 * <ul>
 * <li>State change tracking inherited from AbstractStateChangeFlagged.</li>
 * <li>Lazy initialization and manipulation of GigaLevel2 elements.</li>
 * <li>Utility methods to manage children state changes and markers.</li>
 * <li>Capacity management for dynamically resizing the internal segment array.</li>
 * <li>Clear encapsulation for marking child-level usage and state changes.</li>
 * </ul>
 *
 * @param <E> the type of elements stored in Level 2 objects referenced by this class
 */
public final class GigaLevel3<E> extends AbstractStateChangeFlagged implements Unpersistable
{
	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////
	
	static BinaryTypeHandler<GigaLevel3<?>> provideTypeHandler()
	{
		return BinaryHandlerGigaLevel3.New();
	}
	
	@SuppressWarnings("unchecked")
	private Lazy<GigaLevel2<E>>[] createSegmentsArray(final int length)
	{
		return new Lazy[length];
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
		
	Lazy<GigaLevel2<E>>[] segments;

	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	GigaLevel3(final int length, final boolean newInstance)
	{
		super(newInstance);
		this.segments = this.createSegmentsArray(length);
	}
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	private Object usageMarker()
	{
		// encapsulated as a method for consistent use.
		return this;
	}
	
	private void markUsedLevel2Entry(final Lazy<GigaLevel2<E>> level2Entry)
	{
		// encapsulated as a method for consistent use.
		level2Entry.markUsedFor(this.usageMarker());
	}
	private void unmarkUsedLevel2Entry(final Lazy<GigaLevel2<E>> level2Entry)
	{
		// encapsulated as a method for consistent use.
		level2Entry.unmarkUsedFor(this.usageMarker());
	}
	
	final void reinitialize(final int length)
	{
		this.initializeState(false);
		this.segments = this.createSegmentsArray(length);
		this.markStateChangeInstance();
	}
	
	final void markChanged(final int level3Index)
	{
		final Lazy<GigaLevel2<E>> level2Entry = this.segments[level3Index];
		final GigaLevel2<E> level2 = level2Entry.get();
		this.markUsedLevel2Entry(level2Entry);
		this.markStateChangeChildren();
		level2.markStateChangeInstance();
	}
	
	final void addLevel2(final GigaLevel2<E> level2, final int level3Index)
	{
		final Lazy<GigaLevel2<E>> level2Entry = Lazy.Reference(level2);
		this.segments[level3Index] = level2Entry;
		this.markUsedLevel2Entry(level2Entry);
		this.markStateChangeInstance();
	}
	
	final void enlargeLevel3(final int minimumCapacity)
	{
		// add 10% capacity to avoid frequent rebuilding, but at the very least 1 more length.
		final int newLength = Math.max(minimumCapacity * 11 / 10, minimumCapacity + 1);
		
		final Lazy<GigaLevel2<E>>[] newArray = this.createSegmentsArray(newLength);
		System.arraycopy(this.segments, 0, newArray, 0, this.segments.length);
		
		this.segments = newArray;
		this.markStateChangeInstance();
	}
		
	@Override
	protected void storeChangedChildren(final Storer storer)
	{
		for(final Lazy<GigaLevel2<E>> level3Entry : this.segments)
		{
			if(level3Entry == null)
			{
				continue;
			}
			
			final GigaLevel2<?> level2 = level3Entry.peek();
			if(level2 == null || !level2.isChangedAndNotNew())
			{
				continue;
			}
			
			storer.store(level2);
		}
	}

	@Override
	protected void clearChildrenStateChangeMarkers()
	{
		for(final Lazy<GigaLevel2<E>> level3Entry : this.segments)
		{
			if(level3Entry == null)
			{
				continue;
			}
			
			final GigaLevel2<?> level2 = level3Entry.peek();
			if(level2 == null || !level2.stateChangedInstance())
			{
				continue;
			}
			
			this.unmarkUsedLevel2Entry(level3Entry);
			level2.clearStateChangeMarkers();
		}
	}

}
