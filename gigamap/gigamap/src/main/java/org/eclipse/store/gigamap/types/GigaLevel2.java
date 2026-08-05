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

	/**
	 * Releases the level1 segment at the passed index, the counterpart of
	 * {@link #addLevel1(GigaLevel1, int)}, and reports whether this instance holds no segment at all
	 * afterwards.
	 * <p>
	 * The slot is part of THIS instance's binary form, so the instance itself must be flagged for
	 * re-storing: {@link #storeChangedChildren(Storer)} alone would never persist the removal, and the
	 * dropped segment would come back on the next load.
	 * <p>
	 * The usage mark is removed explicitly although the dropped {@link Lazy} becomes garbage either way
	 * (the lazy reference manager holds only weak references to it), so that no clearing or storing
	 * logic can ever observe a pin for an unreachable child.
	 *
	 * @param level2Index the index of the level1 segment to release
	 * @return {@code true} if this instance holds no level1 segment anymore, {@code false} otherwise
	 */
	final boolean removeLevel1(final int level2Index)
	{
		final Lazy<GigaLevel1<E>> level1Entry = this.segments[level2Index];
		if(level1Entry != null)
		{
			this.unmarkUsedLevel1Entry(level1Entry);
		}

		this.segments[level2Index] = null;
		this.markStateChangeInstance();

		return this.isEmpty(level2Index);
	}

	/**
	 * Whether every slot is {@code null}. Starts scanning after the slot that was cleared last and wraps
	 * around to it, for the same reason as {@link GigaLevel1#isEmpty(int)}: a sequential drain exits on
	 * the first surviving neighbour. A non-null slot counts as occupied even when its segment is not
	 * currently loaded, so no segment has to be loaded to answer this.
	 *
	 * @param scanStartIndex the index to start scanning after, typically the slot that was cleared last
	 * @return {@code true} if every slot is {@code null}, {@code false} otherwise
	 */
	private boolean isEmpty(final int scanStartIndex)
	{
		final Lazy<GigaLevel1<E>>[] segments = this.segments;
		final int                   length   = segments.length;

		for(int i = 1; i <= length; i++)
		{
			int index = scanStartIndex + i;
			if(index >= length)
			{
				index -= length;
			}
			if(segments[index] != null)
			{
				return false;
			}
		}

		return true;
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
