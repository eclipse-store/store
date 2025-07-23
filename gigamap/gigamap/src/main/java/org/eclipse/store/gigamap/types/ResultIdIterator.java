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

import java.util.NoSuchElementException;


/**
 * An interface to iterate over result IDs with various utilities for ID access,
 * parent mapping, and lifecycle management. Implementations of this interface
 * allow for efficient retrieval and handling of large sets of IDs.
 */
public interface ResultIdIterator
{
	/**
	 * Checks if there is another ID available for iteration.
	 *
	 * @return true if there is another ID available, false otherwise.
	 */
	public boolean hasNextId();
	
	/**
	 * Retrieves the next available unique identifier.
	 *
	 * @return the next unique identifier as a long value
	 */
	public long nextId();
	
	/**
	 * Retrieves the parent GigaMap associated with this iterator.
	 *
	 * @return the parent GigaMap instance
	 */
	public GigaMap<?> parent();
	
	/**
	 * Marks the current iteration as inactive, signaling that it will no longer be used.
	 * This method can be used to cease operation on the current state of the iterator,
	 * allowing for resource management or operational state changes.
	 */
	public void setInactive();
	
	/**
	 * Releases any resources associated with this ResultIdIterator and marks it as closed.
	 * This method should be called when the iterator is no longer needed to ensure proper
	 * resource management. Once closed, the iterator cannot be used for further iteration,
	 * and calling its methods might result in undefined behavior.
	 */
	public void close();
	
	/**
	 * Checks whether the iterator has been marked as closed and is no longer active.
	 *
	 * @return true if the iterator is closed, false otherwise
	 */
	public boolean isClosed();
	
	
	
	public final class Default implements ResultIdIterator, GigaMap.Reading
	{
		///////////////////////////////////////////////////////////////////////////
		// constants //
		//////////////
		
		private static final int
			LEVEL_2_ID_COUNT          = BitmapLevel3.LEVEL_2_ID_COUNT         ,
			LEVEL_1_ID_COUNT          = BitmapLevel3.LEVEL_1_ID_COUNT         ,
			LEVEL_1_TOTAL_SIZE_EXP    = BitmapLevel3.LEVEL_1_TOTAL_SIZE_EXP   ,
			LEVEL_2_TOTAL_SIZE_EXP    = BitmapLevel3.LEVEL_2_TOTAL_SIZE_EXP   ,
			VALUE_BIT_LENGTH_EXPONENT = BitmapLevel3.VALUE_BIT_LENGTH_EXPONENT
		;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final GigaMap.Default<?>  parent;
		private final long highestUsedId;
		
		/*
		 * Tricky little optimization:
		 * The logic using this array assumes AND logic.
		 * This seems wrong at first glance, but here's the trick:
		 * If the overall result is an and-Chain, its elements are sorted in an optimized order
		 * and passed here as an array without the chain wrapper. THEN, AND logic applies to the array's elements.
		 * If the overall result is anything else, it is wrapped in a length-1-array and the AND logic
		 * used to connect the array's elements will never apply.
		 */
		private final BitmapResult[] results;
		
		private final int level3IndexBound;
		private int       level2IndexBound;
		private int       level1IndexBound;
		
		private long level2BaseId = 0;
		private long level1BaseId = 0;
		private long valueBaseId  = Long.MIN_VALUE; // important to provoke NoSuchEl.Ex. for invalid #get calls
		
		private int  currentLevel3Index = -1; // -1 required because of pre-increment logic.
		private int  currentLevel2Index = -1; // -1 required because of pre-increment logic.
		private int  currentLevel1Index = -1; // -1 required because of pre-increment logic.
		private long currentBitmapValue = 0L;
		private int  currentBitPosition = Long.SIZE; // required to force efficient initialization.
		
		private boolean isActive = true;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(final GigaMap.Default<?> parent, final BitmapResult[] results)
		{
			super();
			this.parent        = parent;
			this.highestUsedId = parent.highestUsedId();
			this.results       = results      ;
			
			this.level3IndexBound = XTypes.to_int(this.highestUsedId >>> LEVEL_2_TOTAL_SIZE_EXP) + 1;
			this.level2IndexBound = this.calculateIndexBoundLevel2();
			this.level1IndexBound = this.calculateIndexBoundLevel1();

			this.updateCurrentLevel2Segment(0);
			this.updateCurrentLevel1Segment(0);
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final GigaMap.Default<?> parent()
		{
			return this.parent;
		}
				
		@Override
		public final boolean hasNextId()
		{
			/*
			 * Note:
			 * When currentBitPosition is Long.SIZE, the 1L is shifted out to the left, yielding 0L.
			 * This means this case will ALWAYS call the scrolling method (required for initialization).
			 * 
			 * Respectively:
			 * When currentBitmapValue is -1L (meaning all bits are 1) and currentBitPosition is NOT overflown,
			 * the calculation will NEVER yield 0L and thus NEVER call the scrolling method (required for being closed).
			 * 
			 * All other ("normal") cases mean the current long value is checked bit by bit, while skipping
			 * 0 bits and advancing to the next long value after checking the highest bit.
			 */
			// must ensure a marked bit position
			if((this.currentBitmapValue & 1L<<this.currentBitPosition) != 0L)
			{
				return true;
			}
			this.scrollToNextMarkedBitPosition();
			
			// iterator has been closed/deactivated. No more elements.
			return this.isActive;
		}
		
		@Override
		public final long nextId()
		{
			if((this.currentBitmapValue & 1L<<this.currentBitPosition) == 0L)
			{
				this.scrollToNextMarkedBitPosition();
				if(!this.isActive)
				{
					throw new NoSuchElementException();
				}
			}
			return this.valueBaseId + this.currentBitPosition++;
		}
				
		private void scrollToNextMarkedBitPosition()
		{
			long bitmapValue = this.currentBitmapValue;
			int  bitPosition = this.currentBitPosition;
			
			do
			{
				/* note:
				 * no bitPositionBound since the bitmap long values are always processed until the last bit.
				 * Overhang bits are simply 0 and thus have no effect.
				 */
				if(++bitPosition >= Long.SIZE)
				{
					if(!this.scrollToNextBitmapValue())
					{
						return; // scrolling logic reached end of data and did alredy setup the internal state to abort.
					}
					// start the new bitmap value at the beginning.
					bitmapValue = this.currentBitmapValue;
					bitPosition = 0;
				}
			}
			while((bitmapValue & 1L<<bitPosition) == 0L);
			this.currentBitPosition = bitPosition;
		}
		
		private boolean scrollToNextBitmapValue()
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
			while(this.updateCurrentBitmapValue(currentLevel1Index));
			
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
			while(this.updateCurrentLevel1Segment(currentLevel2Index));
			
			return true;
		}
		
		private boolean scrollToNextLevel2Segment()
		{
			// "last" instead of "bound" since there is one more level2 segment, but most probably incomplete.
			final int level3IndexBound = this.level3IndexBound;
			
			int currentLevel3Index = this.currentLevel3Index;
			do
			{
				if(++currentLevel3Index >= level3IndexBound)
				{
					this.close();
					return false;
				}
			}
			while(this.updateCurrentLevel2Segment(currentLevel3Index));
			
			return true;
		}
		
		private boolean updateCurrentLevel2Segment(final int level3Index)
		{
			for(final BitmapResult r : this.results)
			{
				if(!r.setCurrentIterationLevel2Segment(level3Index))
				{
					// early exit: an all-0s value will never be changed by AND logic.
					return true;
				}
			}
			this.updateLevel2IterationState(level3Index);
			
			return false;
		}
		
		private boolean updateCurrentLevel1Segment(final int level2Index)
		{
			for(final BitmapResult r : this.results)
			{
				if(!r.setCurrentIterationLevel1Segment(level2Index))
				{
					// early exit: an all-0s value will never be changed by AND logic.
					return true;
				}
			}
			this.updateLevel1IterationState(level2Index);
			
			return false;
		}
		
		private boolean updateCurrentBitmapValue(final int level1Index)
		{
			// -1L means all bits are 1s. The condition result values will filter it down.
			long result = -1L;
			for(final BitmapResult r : this.results)
			{
				if((result &= r.getCurrentLevel1BitmapValue(level1Index)) == 0L)
				{
					// early exit: an all-0s value will never be changed by AND logic.
					return true;
				}
			}
			this.currentBitmapValue = result;
			this.updateBitmapValueIterationState(level1Index);
			
			return false;
		}
		
		private void updateLevel2IterationState(final int level3Index)
		{
			this.currentLevel3Index = level3Index;
			this.level2BaseId       = level3Index * LEVEL_2_ID_COUNT;
			this.level2IndexBound   = this.calculateIndexBoundLevel2();
		}
		
		private void updateLevel1IterationState(final int level2Index)
		{
			this.currentLevel2Index = level2Index;
			this.level1BaseId       = this.level2BaseId + level2Index * LEVEL_1_ID_COUNT;
			this.level1IndexBound   = this.calculateIndexBoundLevel1();
		}
		
		private void updateBitmapValueIterationState(final int level1Index)
		{
			this.currentLevel1Index = level1Index;
			this.valueBaseId        = this.level1BaseId + (level1Index << BitmapLevel3.VALUE_BIT_LENGTH_EXPONENT);
			// note: no "bitPositionBound" or "valueIndexBound" since values are always handled until the last bit.
		}
		
		private int calculateIndexBoundLevel1()
		{
			return this.calculateIndexBound(this.level1BaseId, LEVEL_1_ID_COUNT, VALUE_BIT_LENGTH_EXPONENT);
		}
		
		private int calculateIndexBoundLevel2()
		{
			return this.calculateIndexBound(this.level2BaseId, LEVEL_2_ID_COUNT, LEVEL_1_TOTAL_SIZE_EXP);
		}
		
		private int calculateIndexBound(final long baseId, final int idCountPerSegment, final int totalSizeExp)
		{
			final int idRemainder = XTypes.to_int(this.highestUsedId - baseId);
			
			/*
			 * Nasty special case:
			 * If the remainder is smaller than the id count per segment, the bound must be one more,
			 * to include the incomplete segment.
			 * Overhang bits are not a problem since they are always 0 and just get skipped by the iteration.
			 */
			return idRemainder >= idCountPerSegment
				? idCountPerSegment >>> totalSizeExp
				: (idRemainder >>> totalSizeExp) + 1
			;
		}
		
		

		@Override
		public void close()
		{
			this.parent.closeIterator(this);
		}
		
		@Override
		public final boolean isClosed()
		{
			return !this.isActive;
		}
		
		@Override
		public final void setInactive()
		{
			// these values make #hasNext and #next dysfunctional forever.
			this.currentBitmapValue = -1L; // these two will cause #hasNext to skip state progressing logic
			this.currentBitPosition =   0; // these two will cause #hasNext to skip state progressing logic
			this.valueBaseId        = Long.MIN_VALUE; // will always cause an exception when calling #next
			this.isActive           = false; // makes #hasNext return false
		}
		
		public void clearIterationState()
		{
			if(!this.isActive)
			{
				// iteration state has already been cleared and will not be set up again.
				return;
			}
			
			for(final BitmapResult r : this.results)
			{
				r.clearIterationState();
			}
		}
		
	}

}
