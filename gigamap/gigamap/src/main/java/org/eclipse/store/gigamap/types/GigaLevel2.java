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
 * The GigaLevel2 class represents the second level of a hierarchical structure
 * which organizes and tracks changes to its segments. This class extends
 * {@link AbstractStateChangeFlagged} to utilize state change tracking mechanisms for its
 * own state and the state of its child objects.
 * <p>
 * GigaLevel2 encapsulates an array of Lazy references to GigaLevel1 instances and
 * provides methods to manage the lifecycle, usage, and state changes of its segments.
 *
 * @param <E> the type of elements managed within the hierarchy.
 */
public final class GigaLevel2<E> extends AbstractStateChangeFlagged implements Unpersistable
{
	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////
	
	static BinaryTypeHandler<GigaLevel2<?>> provideTypeHandler()
	{
		return BinaryHandlerGigaLevel2.New();
	}
	
	@SuppressWarnings("unchecked") // because type erasure for the loss.
	private Lazy<GigaLevel1<E>>[] createSegmentsArray(final int length)
	{
		return new Lazy[length];
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	final Lazy<GigaLevel1<E>>[] segments;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	GigaLevel2(final int length, final boolean newInstance)
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
	
	private void markUsedLevel1Entry(final Lazy<GigaLevel1<E>> level1Entry)
	{
		// encapsulated as a method for consistent use.
		level1Entry.markUsedFor(this.usageMarker());
	}
	private void unmarkUsedLevel1Entry(final Lazy<GigaLevel1<E>> level1Entry)
	{
		// encapsulated as a method for consistent use.
		level1Entry.unmarkUsedFor(this.usageMarker());
	}
	
	final void markChanged(final int level2Index)
	{
		final Lazy<GigaLevel1<E>> level1Entry = this.segments[level2Index];
		final GigaLevel1<E> level1 = level1Entry.get();
		this.markUsedLevel1Entry(level1Entry);
		level1.markStateChangeInstance();
		this.markStateChangeChildren();
	}
	
	final void addLevel1(final GigaLevel1<E> level1, final int level2Index)
	{
		final Lazy<GigaLevel1<E>> level1Entry = Lazy.Reference(level1);
		this.segments[level2Index] = level1Entry;
		this.markUsedLevel1Entry(level1Entry);
		this.markStateChangeInstance();
	}
	
	@Override
	protected void storeChangedChildren(final Storer storer)
	{
		for(final Lazy<GigaLevel1<E>> level1Entry : this.segments)
		{
			if(level1Entry == null)
			{
				continue;
			}
			final GigaLevel1<?> level1 = level1Entry.peek();
			
			// storing a child makes only sense if it is changed but not new, since new instances will get stored automatically.
			if(level1 == null || !level1.isChangedAndNotNew())
			{
				continue;
			}
			
			storer.store(level1);
		}
	}
	
	@Override
	protected void clearChildrenStateChangeMarkers()
	{
		for(final Lazy<GigaLevel1<E>> level1Entry : this.segments)
		{
			if(level1Entry == null)
			{
				continue;
			}
			
			final GigaLevel1<?> level1 = level1Entry.peek();
			if(level1 == null || !level1.stateChangedInstance())
			{
				continue;
			}
			this.unmarkUsedLevel1Entry(level1Entry);
			level1.clearStateChangeMarkers();
		}
	}

}
