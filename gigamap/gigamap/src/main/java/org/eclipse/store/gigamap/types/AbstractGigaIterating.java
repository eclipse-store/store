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

import org.eclipse.serializer.typing.XTypes;


/**
 * AbstractGigaIterating serves as an abstract base class designed for iteration over data represented
 * in a hierarchical bitmap structure. The class provides a framework for iterating through multiple levels
 * of bitmap results and encapsulates the logic for handling transitions between different levels and indices.
 * <p>
 * The implementation splits the data into hierarchical levels for efficient traversal and checks.
 * Levels include Level 3 (highest), Level 2 (mid), and Level 1 (lowest granularity).
 * <p>
 * This abstract class can be extended by concrete implementations to define specific behaviors for the
 * handling of bitmap results or for resolving the data structure being iterated.
 * <p>
 * Key Features
 * <ul>
 * <li>Optimized handling of AND logic across bitmap result arrays.</li>
 * <li>Supports skipping over trailing null elements in results for better performance.</li>
 * <li>Efficient bit-level and segment-level calculations for hierarchical data navigation.</li>
 * <li>Abstract methods are to be defined by subclasses to implement specific logic.</li>
 * </ul>
 * Note:
 * The class assumes that the bitmaps represent IDs, with each bit in a bitmap corresponding to a possible ID.
 * This implementation emphasizes performance and minimizes unnecessary state changes during iteration.
 * <p>
 * This class does not support direct instantiation and is intended to be subclassed.
 *
 * @param <E> The type of elements being resolved or iterated through within the bitmap structure.
 */
public abstract class AbstractGigaIterating<E>
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	// parent must be referenced separately because resolver might not use/reference it at all.
	final BitmapResult.Resolver<E> resolver;
	
	/*
	 * Tricky little optimization:
	 * The logic using this array assumes AND logic.
	 * This seems wrong at first glance, but here's the trick:
	 * If the overall result is an and-Chain, its elements are sorted in an optimized order
	 * and passed here as an array. THEN, AND logic applies to the array's elements.
	 * If the overall result is anything else, it is wrapped in a length-1-array and the AND logic
	 * used to connect the array's elements will never apply.
	 * 
	 * Also note:
	 * Results arrays can have trailing null elements. The first null element means all elements have been iterated.
	 */
	private final BitmapResult[] results;

	private final long idBound;
	private final long level2TrailingBaseId, level1TrailingBaseId, bitValTrailingBaseId;
	
	private final int level3IndexBound;
	private int       level2IndexBound;
	private int       level1IndexBound;
	
	private long level2BaseId, level1BaseId;
	
	private int currentLevel3Index, currentLevel2Index, currentLevel1Index;
	long currentBitmapValue, bitValBaseId;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	AbstractGigaIterating(
		final BitmapResult.Resolver<E> resolver,
		final long                     idStart ,
		final long                     idBound ,
		final BitmapResult[]           results
	)
	{
		super();
		this.resolver = resolver;
		this.idBound  = idBound ;
		this.results  = results ;
		
		// Bounds for L1 and L2 get adjusted for the trailing segment. Bit position bounds get baked into the value.
		this.level3IndexBound = XTypes.to_int(this.idBound >>> BitmapLevel3.LEVEL_2_TOTAL_SIZE_EXP) + 1;
		this.level2IndexBound = BitmapLevel3.LEVEL_2_SEGMENT_LENGTH;
		this.level1IndexBound = BitmapLevel3.LEVEL_1_SEGMENT_VALUE_COUNT;

		// Cached values for quick&easy trailing segment checks.
		this.level2TrailingBaseId = this.idBound & ~BitmapLevel3.LEVEL_2_ID_MASK;
		this.level1TrailingBaseId = this.idBound & ~BitmapLevel3.LEVEL_1_ID_MASK;
		this.bitValTrailingBaseId = this.idBound & ~BitmapLevel3.VALUE_ID_MASK;
				
		this.initializeLevelIndices(idStart);
		this.initializeCurrentBitmapValue(idStart);
	}
		
	private void initializeLevelIndices(final long idStart)
	{
		final int level3Index = BitmapLevel3.toLevel3Index(idStart);
		final int level2Index = BitmapLevel3.toLevel2Index(idStart);
		final int level1Index = BitmapLevel3.toLevel1Index(idStart);
		
		/*
		 * This is tricky:
		 * During the iteration, if one of the update~ methods returns false, updating the iteration
		 * state can be skipped because the calling context's loop will advance to the next index
		 * and call the method again, eventually updating the iteration state to the currently relevant state.
		 * 
		 * But for initialization, the iteration state must be set in any case to set up the correct starting id.
		 * 
		 * Simply setting the iteration state always would be a waste of time during the iteration.
		 * Instead the special case is covered once for initialization by checking for it, here.
		 */
		if(!this.updateCurrentLevel3Index(level3Index))
		{
			this.updateLevel3IterationState(level3Index);
		}
		if(!this.updateCurrentLevel2Index(level2Index))
		{
			this.updateLevel2IterationState(level2Index);
		}
		if(!this.updateCurrentLevel1Index(level1Index))
		{
			this.updateLevel1IterationState(level1Index);
		}
	}
	
	private void initializeCurrentBitmapValue(final long idStart)
	{
		// Bit starting position gets baked into the bitmap value. The bit scrolling loop will quickly skip over the 0s
		this.currentBitmapValue = startAtId(this.currentBitmapValue, idStart);
	}
	
	static long startAtId(final long bitmapValue, final long idStart)
	{
		/*
		 * The bits of a bitmapValue each represent an id corresponding to their position.
		 * So the bit at position 0 represents the id 0, position 63 represents id 63, etc.
		 * To "bake in" a startId into a bitmapValue, the following algorithm is used:
		 * 1.) only consider the lowest 6 bits (values 0 to 63) of the id to be compatible with a single long bitmap.
		 * 2.) Shift a single bit to the position corresponding to that reduced id as a kind of "marker".
		 * 3.) subtract 1 from the marker value to make all bits BELOW the marker 1s and all bits above 0s.
		 * 4.) bitwise negate the value to create a bit mask that allows only all bits above (0s, now 1s).
		 * 5.) bitwise and that mask with the actual bitmap value, effectively making it start at the desired id.
		 */
		final int bitPositionStart = (int)(idStart & BitmapLevel3.VALUE_ID_MASK);
		
		return bitmapValue & -(1L << bitPositionStart);
	}
	
	static long boundToId(final long bitmapValue, final long idBound)
	{
		// works exactly like #startAtId, except that the bitwise negation is not performed, so all ids at bound upwards are removed.
		final int bitPositionStart = (int)(idBound & BitmapLevel3.VALUE_ID_MASK);
		
		return bitmapValue & (1L<<bitPositionStart) - 1;
	}
		
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
						
	protected boolean scrollToNextBitmapValue()
	{
		final int level1IndexBound = this.level1IndexBound;
		
		int currentLevel1Index = this.currentLevel1Index;
		do
		{
			if(++currentLevel1Index >= level1IndexBound)
			{
				if(!this.scrollToNextLevel1Segment())
				{
					// no more level1 segment to scroll to, because iteration has reached the end of the data.
					return false;
				}
				// start the newly scrolled to level1segment at the beginning.
				currentLevel1Index = 0;
			}
		}
		while(!this.updateCurrentLevel1Index(currentLevel1Index));
		
		return true;
	}
	
	private boolean scrollToNextLevel1Segment()
	{
		final int level2IndexBound = this.level2IndexBound;
		
		int currentLevel2Index = this.currentLevel2Index;
		do
		{
			if(++currentLevel2Index >= level2IndexBound)
			{
				if(!this.scrollToNextLevel2Segment())
				{
					// no more level2 segment to scroll to, because iteration has reached the end of the data.
					return false;
				}
				// start the newly scrolled to level2segment at the beginning.
				currentLevel2Index = 0;
			}
		}
		while(!this.updateCurrentLevel2Index(currentLevel2Index));
		
		return true;
	}
	
	private boolean scrollToNextLevel2Segment()
	{
		final int level3IndexBound = this.level3IndexBound;
		
		int currentLevel3Index = this.currentLevel3Index;
		do
		{
			if(++currentLevel3Index >= level3IndexBound)
			{
				return false;
			}
		}
		while(!this.updateCurrentLevel3Index(currentLevel3Index));
		
		return true;
	}
	
	private boolean updateCurrentLevel3Index(final int level3Index)
	{
		for(final BitmapResult r : this.results)
		{
			if(r == null)
			{
				break;
			}
			if(!r.setCurrentIterationLevel2Segment(level3Index))
			{
				// early exit: an all-0s value will never be changed by AND logic. Skip to the next index.
				return false;
			}
		}
		this.updateLevel3IterationState(level3Index);
		
		return true;
	}
	
	private boolean updateCurrentLevel2Index(final int level2Index)
	{
		for(final BitmapResult r : this.results)
		{
			if(r == null)
			{
				break;
			}
			if(!r.setCurrentIterationLevel1Segment(level2Index))
			{
				// early exit: an all-0s value will never be changed by AND logic. Skip to the next index.
				return false;
			}
		}
		this.updateLevel2IterationState(level2Index);
		
		return true;
	}
	
	private boolean updateCurrentLevel1Index(final int level1Index)
	{
		// -1L means all bits are 1s. The condition result values will filter it down.
		long result = -1L;
		for(final BitmapResult r : this.results)
		{
			if(r == null)
			{
				break;
			}
			if((result &= r.getCurrentLevel1BitmapValue(level1Index)) == 0L)
			{
				// early exit: an all-0s value will never be changed by AND logic. Skip to the next index.
				return false;
			}
		}
		this.currentBitmapValue = result;
		this.updateLevel1IterationState(level1Index);
		
		return true;
	}
	
	private void updateLevel3IterationState(final int level3Index)
	{
		this.currentLevel3Index = level3Index;
		this.level2BaseId       = level3Index * BitmapLevel3.LEVEL_2_ID_COUNT;
		if(this.level2BaseId >= this.level2TrailingBaseId)
		{
			// when the trailing segment is reached, the level2 index bound is 1 beyond the upperId's level2 index.
			this.level2IndexBound = BitmapLevel3.toLevel2Index(this.idBound) + 1;
		}
	}
	
	private void updateLevel2IterationState(final int level2Index)
	{
		this.currentLevel2Index = level2Index;
		this.level1BaseId       = this.level2BaseId + level2Index * BitmapLevel3.LEVEL_1_ID_COUNT;
		if(this.level1BaseId >= this.level1TrailingBaseId)
		{
			// when the trailing segment is reached, the level1 index bound is 1 beyond the upperId's level1 index.
			this.level1IndexBound = BitmapLevel3.toLevel1Index(this.idBound) + 1;
		}
	}
	
	private void updateLevel1IterationState(final int level1Index)
	{
		this.currentLevel1Index = level1Index;
		this.bitValBaseId       = this.level1BaseId + (level1Index << BitmapLevel3.VALUE_BIT_LENGTH_EXPONENT);
		if(this.bitValBaseId >= this.bitValTrailingBaseId)
		{
			// nulling out the bits above the bitPositionBound is effectively baking the bound into the value.
			final int bitPositionBound = (int)(this.idBound & BitmapLevel3.VALUE_ID_MASK);
			this.currentBitmapValue &= (1L<<bitPositionBound) - 1;
		}
	}
	
	protected void clearResultsIterationState()
	{
		for(final BitmapResult r : this.results)
		{
			if(r == null)
			{
				break;
			}
			r.clearIterationState();
		}
	}
				
}
