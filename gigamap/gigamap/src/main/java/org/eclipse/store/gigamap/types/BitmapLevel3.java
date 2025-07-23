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
 * The BitmapLevel3 class provides a hierarchical bitmapped structure used for managing
 * and storing entity IDs efficiently across multiple levels. This class supports an
 * architecture to represent a three-level hierarchy of data organization and offers
 * various methods to manage the state and structure of the hierarchy.
 * <p>
 * This class is built to handle dynamic adjustments to the structure, including
 * compression and decompression, addition, and removal of entities. It maintains
 * metadata about segments and provides mechanisms to track changes to ensure persistence
 * of modified states. Apart from encapsulating data organization logic, it serves as
 * a core object for storage management in related systems.
 * <p>
 * Class Highlights:
 * <ul>
 * <li>Supports tracking and management of individual and hierarchical level IDs.</li>
 * <li>Efficient representation of large datasets using a bitmapped structure.</li>
 * <li>Enables state change detection for persistence operations.</li>
 * </ul>
 * Key Features:
 * <ul>
 * <li>Hierarchical segmentation with Level 3, Level 2, and Level 1 structures, where
 *   Level 3 is the top-most segment-level representation.</li>
 * <li>Automatic resizing to handle the addition of new entities dynamically.</li>
 * <li>Efficient ID-based mapping through calculated indices at each level.</li>
 * <li>Methods to mark, clear, and store state changes for integration into persistence systems.</li>
 * </ul>
 */
public class BitmapLevel3 extends AbstractStateChangeFlagged implements Unpersistable
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////

	/*
	 * !!! Note:
	 * These values are not configurable, not even as source code constants.
	 * They are merely readability symbols for technical values. Max byte value, Long.SIZE, etc.
	 * The algorithms and persistence formats are tailored to use exactely those technical values.
	 * E.g. A level array having a length of 256 so that a single byte can represent the index in the array.
	 * DO NOT CHANGE THESE VALUES! Things would break.
	 */
	
	/*
	 * It is possible to change those two constants, but within serious limitations:
	 * - Only SMALLER values than the normal ones are okay.
	 * - Only without storing and loading because the type handlers would probably no longer fit.
	 * - The resulting Level1Segment length must be a multiple of 8 to correctly fit whole bitmap long values.
	 * 
	 * This makes a lot of sense for testing.
	 * When changes work for small entity amounts but fail for bigger entity amounts,
	 * it can quickly become cumbersome to test with higher segments.
	 * However, if the segments are rather tiny, a small amount of entities suffices to
	 * reproduce the problematic behavior and hence is MUCH easier to debug.
	 * Valid values:
	 * LEVEL_2_LENGTH_EXPONENT: 1 - 7. MAYBE 0 (only one 2^0=1 Level1Segment per Level2Segment. But not sure!)
	 * LEVEL_1_LENGTH_EXPONENT: 4 - 8. MAYBE 3 (only one 2^3=8 byte BitmapValue per Level1Segment. But not sure!)
	 * 
	 * For proper testing, the values 2 and 4 should be optimal: Having only 1 or 2 of something is always a
	 * corner case because there is no middle element. Having 4 should pretty much cover all cases of "many" elements.
	 */

	static final int
		LEVEL_2_LENGTH_EXPONENT    = Byte.SIZE, // 2^8 == 1<<8 == 256. DO NOT CHANGE! SEE Note ABOVE!
		LEVEL_1_LENGTH_EXPONENT    = 9, // 2^9 == 1<<9 == 512 (bytes per level1 block). DO NOT CHANGE! SEE Note ABOVE!
		VALUE_BIT_LENGTH_EXPONENT  = 6, // 2^6 == 1<<6 == 64 == Long.SIZE. DO NOT CHANGE! SEE Note ABOVE!
		VALUE_BYTE_LENGTH_EXPONENT = 3, // 2^3 == 1<<3 == 8 == Long.BYTES. DO NOT CHANGE! SEE Note ABOVE!
		BYTE_BIT_COUNT_EXPONENT    = 3, // 2^3 == 1<<3 == 8 == number of bits in a byte. DO NOT CHANGE! SEE Note ABOVE!
		VALUE_ID_MASK              = Long.SIZE - 1, // A long has bits to represent 64 entities, 0-63. DO NOT CHANGE! SEE Note ABOVE!
		LEVEL_1_VALUE_COUNT_EXP    = LEVEL_1_LENGTH_EXPONENT - VALUE_BYTE_LENGTH_EXPONENT, // 2^9 bytes, but each value is 2^3 bytes.
		LEVEL_1_TOTAL_SIZE_EXP     = LEVEL_1_LENGTH_EXPONENT + BYTE_BIT_COUNT_EXPONENT, // 2^(9+3) Entities/Bits per Level1 block
		LEVEL_2_TOTAL_SIZE_EXP     = LEVEL_2_LENGTH_EXPONENT + LEVEL_1_TOTAL_SIZE_EXP, // 2^(8+12) == 1<<20 == 1_048_576
		LEVEL_1_INDEX_MASK         = (1 << LEVEL_1_VALUE_COUNT_EXP) - 1,
		LEVEL_2_INDEX_MASK         = (1 << LEVEL_2_LENGTH_EXPONENT) - 1,
		LEVEL_2_SEGMENT_LENGTH     = 1 << LEVEL_2_LENGTH_EXPONENT,
		LEVEL_2_ID_COUNT           = 1 << LEVEL_2_TOTAL_SIZE_EXP, // 1_048_576 Entities per Level2 segment
		LEVEL_1_ID_COUNT           = 1 << LEVEL_1_TOTAL_SIZE_EXP, // 4_096 Entities per Level1 segment
		LEVEL_2_ID_MASK            = (1 << LEVEL_2_TOTAL_SIZE_EXP) - 1,
		LEVEL_1_ID_MASK            = (1 << LEVEL_1_TOTAL_SIZE_EXP) - 1,
		
		// note: the correctness of the simple value range compression algorithm depends on this value being 512!
		LEVEL_1_SEGMENT_LENGTH       = 1 << LEVEL_1_LENGTH_EXPONENT,
		LEVEL_1_TOTAL_BITCOUNT       = LEVEL_1_SEGMENT_LENGTH * Byte.SIZE,
		LEVEL_1_TOTAL_BITCOUNT_BOUND = LEVEL_1_TOTAL_BITCOUNT + 1,
		LEVEL_1_SEGMENT_VALUE_COUNT  = LEVEL_1_SEGMENT_LENGTH / Long.BYTES // amount of long values in segment
	;
		
	static final byte
		MOD_NEW     = 2,
		MOD_CHANGED = 1
	;
	
	
	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////
	
	static BinaryTypeHandler<BitmapLevel3> provideTypeHandler()
	{
		return BinaryHandlerBitmapLevel3.New();
	}
	
	public static BitmapLevel3 New()
	{
		return new BitmapLevel3(1, true);
	}
	
	public static int calculateLevel3Length(final long highestUsedEntityId)
	{
		return toLevel3Index(highestUsedEntityId) + 1;
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
		
	BitmapLevel2[] segments;
	
	private transient int  segmentCount     ; // the non-null segments of THIS level (shallow)
	private transient long totalSegmentCount; // sum of all level1 instances below this level (deep)

	private transient long currentAddLevel1StartEntityId;
	private transient long currentAddLevel1BoundEntityId;
	private transient long currentAddLevel1Segment      ;
		
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
		
	BitmapLevel3(final int length, final boolean isNew)
	{
		super(isNew);
		this.segments = createArray(length);
		
		this.clearCurrentAddLevel1Segment();
	}
	
	@FunctionalInterface
	interface Creator
	{
		public BitmapLevel3 create(BitmapEntry<?, ?, ?> parent);
	}
		
		
		
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	void setParentContext(final BitmapEntry<?, ?, ?> parent)
	{
		// empty for default implementation
	}
	
	BitmapEntry<?, ?, ?> parent()
	{
		return null;
	}
	
	String indexName()
	{
		return null;
	}
	
	Object key()
	{
		return null;
	}
		
	@Override
	public final void clearStateChangeMarkers()
	{
		// no lock necessary since entry instances are used exclusively by logic that already has the lock.
		
		// must clear the current~ state to enforce state change marking on the next #add call!
		this.clearCurrentAddLevel1Segment();
		
		super.clearStateChangeMarkers();
	}

	@Override
	protected void clearChildrenStateChangeMarkers()
	{
		for(final BitmapLevel2 level2 : this.segments)
		{
			if(level2 == null)
			{
				continue;
			}
			
			level2.clearStateChangeMarkers();
		}
	}
	
	static BitmapLevel2[] createArray(final int length)
	{
		return new BitmapLevel2[length];
	}
	
	private void enlargeLevel3(final int minimumCapacity)
	{
		// add 10% capacity to avoid frequent rebuilding, but at the very least 1 more length.
		final int newLength = Math.max(minimumCapacity * 11 / 10, this.segments.length + 1);
		
		final BitmapLevel2[] newArray = createArray(newLength);
		System.arraycopy(this.segments, 0, newArray, 0, this.segments.length);
		this.segments = newArray;
		
		// no instance change marking here since adding the level2 instance will do that, anyway
	}
	
	public final long totalSegmentCount()
	{
		return this.totalSegmentCount;
	}
	
	final void incrementTotalSegmentCount()
	{
		this.totalSegmentCount++;
	}
	
	final void decrementTotalSegmentCount()
	{
		this.totalSegmentCount--;
	}
		
	final void initializeTotalSegmentCount(final long totalSegmentCount)
	{
		this.totalSegmentCount = totalSegmentCount;
	}
	
	final int localSegmentCount()
	{
		return this.segmentCount;
	}
	
	final void initializeSegmentCount(final int segmentCount)
	{
		this.segmentCount = segmentCount;
	}
	
	final BitmapLevel2[] segments()
	{
		return this.segments;
	}
			
	final void add(final long entityId)
	{
		// if the passed entityId does not fit in the current level1's id range, it needs to to be (re)set.
		if(entityId < this.currentAddLevel1StartEntityId || entityId >= this.currentAddLevel1BoundEntityId)
		{
			this.setCurrentAddLevel1Segment(entityId);
		}
		
		// actual adding of the level1-id part corresponding to the currently set level1 segment.
		BitmapLevel2.addToLevel1(this.currentAddLevel1Segment, toLevel1Id(entityId));
	}
	
	final boolean remove(final long entityId)
	{
		// classic bit shoving to deconstruct the index parts
		final int level3Index = toLevel3Index(entityId);
		if(level3Index >= this.segments.length)
		{
			return false;
		}
		
		final BitmapLevel2 level2;
		if((level2 = this.segments[level3Index]) == null)
		{
			return false;
		}
		
		final int level1RemovalType;
		if(BitmapLevel2.isRemovalTypeRemoved(level1RemovalType = level2.remove(entityId)))
		{
			final boolean level2Removed;
			if(BitmapLevel2.isRemovalTypeEmpty(level1RemovalType))
			{
				// empty means one complete level1 segment is empty and hence the segment count must be updated.
				level2Removed = this.handleRemovedLevel1Segment(entityId);
			}
			else
			{
				level2Removed = false;
			}
			
			if(!level2Removed)
			{
				// only mark level2 segment itself as changed AND if it has not been removed, of course.
				this.markLevel2SegmentChanged(level3Index);
			}
						
			return true;
		}
		
		return false;
	}
	
	final void removeAll()
	{
		this.segments = createArray(1);
		
		this.clearCurrentAddLevel1Segment();
		
		this.markStateChangeInstance();
		this.markStateChangeChildren();
	}
	
	private boolean handleRemovedLevel1Segment(final long entityId)
	{
		final int     level3Index = toLevel3Index(entityId);
		final BitmapLevel2 level2 = this.segments[level3Index];
		
		// must update the total segment count (of level1 segments!) here
		this.decrementTotalSegmentCount();
		this.markStateChangeInstance();
		
		// temporary batch processing helper fields must be kept consistent!
		if(entityId >= this.currentAddLevel1StartEntityId && entityId < this.currentAddLevel1BoundEntityId)
		{
			this.clearCurrentAddLevel1Segment();
		}
		
		// should the level1 removal cause the complete level2 segment to be empty, it must be removed as well.
		if(level2.segmentCount() == 0)
		{
			this.clearLevel2Segment(level3Index);
			return true;
		}
		
		return false;
	}
		
	final void ensureCompressed()
	{
		for(final BitmapLevel2 level2 : this.segments)
		{
			if(level2 == null)
			{
				continue;
			}
			level2.ensureCompressed();
		}
		
		// crucial to clear zombie pointers after compression deallocated the current adding memory blocks!
		this.clearCurrentAddLevel1Segment();
	}
	
	final void ensureDecompressed()
	{
		for(final BitmapLevel2 level2 : this.segments)
		{
			if(level2 == null)
			{
				continue;
			}
			level2.ensureDecompressed();
		}
	}
	
	private void clearLevel2Segment(final int level3Index)
	{
		this.segments[level3Index] = null;
		this.segmentCount--;
		this.markStateChangeInstance();
	}
		
	static int toLevel3Index(final long entityId)
	{
		return (int)(entityId >>> LEVEL_2_TOTAL_SIZE_EXP);
	}
	
	static int toLevel2Index(final long entityId)
	{
		return (int)(entityId >>> LEVEL_1_TOTAL_SIZE_EXP) & LEVEL_2_INDEX_MASK;
	}
	
	static int toLevel1Index(final long entityId)
	{
		return (int)(entityId >>> VALUE_BIT_LENGTH_EXPONENT) & LEVEL_1_INDEX_MASK;
	}
		
	static int toLevel1Id(final long entityId)
	{
		return (int)entityId & LEVEL_1_ID_MASK;
	}
		
	private void setCurrentAddLevel1Segment(final long entityId)
	{
		// classic bit shoving to deconstruct the index parts
		final int level3Index = toLevel3Index(entityId);
		
		final BitmapLevel2 level2 = this.ensureLevel2Segment(level3Index);
		this.currentAddLevel1Segment       = level2.ensureAddableLevel1Segment(entityId, this);
		this.currentAddLevel1StartEntityId = entityId & ~LEVEL_1_ID_MASK;
		this.currentAddLevel1BoundEntityId = this.currentAddLevel1StartEntityId + LEVEL_1_ID_COUNT;
	}
	
	private void clearCurrentAddLevel1Segment()
	{
		this.currentAddLevel1Segment       =  0L;
		this.currentAddLevel1StartEntityId = -1L;
		this.currentAddLevel1BoundEntityId = -1L;
	}
		
	private BitmapLevel2 ensureLevel2Segment(final int level3Index)
	{
		if(level3Index >= this.segments.length)
		{
			this.enlargeLevel3(level3Index + 1);
		}
		if(this.segments[level3Index] == null)
		{
			this.segments[level3Index] = BitmapLevel2.New(level3Index);
			
			this.segmentCount++;
			this.markStateChangeInstance();
		}
		else
		{
			// If no new segment was needed, the already existing segment will be changed.
			this.markLevel2SegmentChanged(level3Index);
		}
		return this.segments[level3Index];
	}
	
	private void markLevel2SegmentChanged(final int level3Index)
	{
		this.segments[level3Index].markStateChangeInstance();
		this.markStateChangeChildren();
	}
				
	static int orderByTotalSegmentCount(final BitmapLevel3 e1, final BitmapLevel3 e2)
	{
		return e2.totalSegmentCount >= e1.totalSegmentCount ? e2.totalSegmentCount != e1.totalSegmentCount ? -1 : 0 : 1;
	}
	
	@Override
	protected void storeChangedChildren(final Storer storer)
	{
		for(final BitmapLevel2 level2 : this.segments)
		{
			if(level2 == null)
			{
				continue;
			}
			
			// storing a child makes only sense if it is changed but not new, since new instances will get stored automatically.
			if(!level2.isChangedAndNotNew())
			{
				continue;
			}
			
			storer.store(level2);
		}
	}
	
}

