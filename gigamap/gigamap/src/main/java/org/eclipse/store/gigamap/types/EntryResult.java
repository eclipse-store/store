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

import org.eclipse.serializer.persistence.types.Unpersistable;


/**
 * EntryResult is a class that represents the result of processing
 * bitmap data and provides mechanisms for iterating through hierarchical
 * bitmap levels.
 * <p>
 * This class manages level-3 bitmap data and its underlying segments, along
 * with transient state for iteration purposes. Instances of this class are
 * immutable in structure but maintain a transient state for iteration
 * operations.
 * <p>
 * The main functionalities include:
 * <ul>
 * <li>Checking if AND logic is optimizable.</li>
 * <li>Retrieving the total number of segments in the bitmap data.</li>
 * <li>Accessing specific bitmap values in Level 1 segments.</li>
 * <li>Managing the state of iteration over Level 2 and Level 1 segments.</li>
 * <li>Creating an independent copy of itself for exclusive iteration use.</li>
 * <li>Resetting the iteration state.</li>
 * </ul>
 */
final class EntryResult implements BitmapResult, Unpersistable
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private final BitmapLevel3   data    ;
	private final BitmapLevel2[] segments;
	
	private transient long currentIterationLevel2Segment;
	private transient long currentIterationLevel1Address;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	EntryResult(final BitmapLevel3 data)
	{
		super();
		this.data     = data;
		this.segments = data.segments;
		this.clearIterationState();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	public boolean isAndLogicOptimizable()
	{
		return true;
	}

	@Override
	public long segmentCount()
	{
		return this.data.totalSegmentCount();
	}

	@Override
	public long getCurrentLevel1BitmapValue(final int level1Index)
	{
		// The called method will return 0L if the pointer is 0L. This is kind of a hack, but also correct. And fast.
		return BitmapLevel2.getLevel1BitmapValue(this.currentIterationLevel1Address, level1Index);
	}

	@Override
	public boolean setCurrentIterationLevel1Segment(final int level2Index)
	{
		// if level2segment is null, no level1segment can be set, of course.
		if(this.currentIterationLevel2Segment == 0L)
		{
			if(this.currentIterationLevel1Address != 0L)
			{
				this.currentIterationLevel1Address = 0L;
			}
			return false;
		}
		
		final long level1SegmentAddress =
			BitmapLevel2.getLevel1SegmentAddress(this.currentIterationLevel2Segment, level2Index)
		;
		
		// a level1 segment containing only 0-bits is represented by the pointer itself being 0 with no segment at all.
		if(level1SegmentAddress == 0L)
		{
			if(this.currentIterationLevel1Address != 0L)
			{
				this.currentIterationLevel1Address = 0L;
			}
			return false;
		}
		
		this.currentIterationLevel1Address = level1SegmentAddress;
		
		return true;
	}

	@Override
	public boolean setCurrentIterationLevel2Segment(final int level3Index)
	{
		if(level3Index >= this.segments.length || this.segments[level3Index] == null)
		{
			if(this.currentIterationLevel2Segment != 0L)
			{
				this.clearIterationState();
			}
			return false;
		}
		
		this.currentIterationLevel1Address = 0L;
		this.currentIterationLevel2Segment = this.segments[level3Index].level2Address;
		
		return true;
	}

	@Override
	public void clearIterationState()
	{
		this.currentIterationLevel2Segment = 0L;
		this.currentIterationLevel1Address = 0L;
	}
	
	@Override
	public BitmapResult createIterationCopy()
	{
		// SAME data instance, but with an exclusive entry result instance to have an exclusive iteration state copy
		return new EntryResult(this.data);
	}
	
}
