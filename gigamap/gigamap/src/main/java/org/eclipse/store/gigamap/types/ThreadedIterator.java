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

import org.eclipse.store.gigamap.types.IterationThreadProvider.IterationLogicProvider;
import org.eclipse.serializer.collections.types.XGettingCollection;
import org.eclipse.serializer.concurrency.XThreads;
import org.eclipse.serializer.math.XMath;
import org.eclipse.serializer.memory.XMemory;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Multithreaded implementation of a {@link GigaIterator}, or more precisely a
 * {@link ResultIdIterator} that gets wrapped via {@link GigaIterator.Wrapping}.<p>
 * This was the initial implementation of multithreaded querying and is quite complex
 * due to the complexity added by the {@link Iterator} concept.<br>
 * When performance is required (which is moreless the only reason to use multithreading),
 * the newer multithreaded implementation of the non-iterator iteration is far superior,
 * i.e. faster and simpler.
 * 
 */
public final class ThreadedIterator implements ResultIdIterator, GigaMap.Reading, IterationLogicProvider
{
	/* (05.06.2024 TM)XXX: Maybe delete ThreadedIterator.
	 * At least as soon as considerable maintenance effort arises.
	 * The monolithic GigaMap iteration automatically uses multithreading
	 * internally if a IterationThreadProvider is present in the GigaQuery.
	 * It is a MUCH simpler implementation AND considerably faster.
	 */
	
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
	
	static final int
		LEVEL_1_VALUE_COUNT          = BitmapLevel3.LEVEL_1_SEGMENT_VALUE_COUNT   ,
		LEVEL_1_LENGTH               = BitmapLevel3.LEVEL_1_SEGMENT_LENGTH        ,
		LEVEL_1_ID_COUNT             = BitmapLevel3.LEVEL_1_ID_COUNT              ,
		REGISTRY_SEGMENT_SIZE        = 64                                         ,
		REGISTRY_SEGMENT_ID_COUNT    = REGISTRY_SEGMENT_SIZE * LEVEL_1_ID_COUNT
	;
	
	// this is used for a pointer value, but address 1 is never valid, so it can be used as a meta value.
	static final long SKIP_LEVEL1_SEGMENT_MARKER = 1L;
	
	
	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////
	
	static long registryIterationStartAddress(final long registryAddress)
	{
		// registry memory consists purely of singular long memory pointers per item, no offset.
		return XMath.positive(registryAddress);
	}

	static long registryIterationBoundAddress(final long registryAddress, final int registrySegmentCount)
	{
		// registry memory consists purely of singular long memory pointers per item, no offset.
		return XMath.positive(registryAddress) + XMath.notNegative(registrySegmentCount) * Long.BYTES;
	}

	static int registrySegmentIterationStartIndex()
	{
		// registrySegment memory consists purely of singular long memory pointers per item, no offset.
		return 0;
	}

	static int registrySegmentIterationIndexCount()
	{
		// registrySegment memory consists purely of singular long memory pointers per item, no offset.
		return REGISTRY_SEGMENT_SIZE;
	}

	static long registrySegmentEntryAddress(final long registrySegmentAddress, final int entryIndex)
	{
		return registrySegmentAddress + entryIndex * Long.BYTES;
	}

	static boolean isValidRegistrySegmentAddress(final long registrySegmentAddress)
	{
		return registrySegmentAddress > SKIP_LEVEL1_SEGMENT_MARKER;
	}
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final GigaMap.Default<?>                   parent            ;
	private final BitmapResult[]                       results           ;
	private final IterationThreadProvider              threadProvider    ;
	private final XGettingCollection<? extends Thread> threads           ;
	private final int                                  level1SegmentCount;
	private final int                                  registrySegmentCount;
	private final long                                 registryAddress   ;
	private final int                                  waitTimeNs        ;
		
	private long currentRegistrySegmentAddress;
	private long currentValueGroupAddress;
	private int  currentRegistryIndex;   // points to a level1segment (a valueGroupsSegment) in the result registry.
	private int  currentRegistrySegmentIndex; // points to the value group in the current value groups segment.
	private int  currentLevel1Index;      // points to a value in the current value group.
	private int  currentBitPosition;     // the position (0 to 63) of the bit to be tested in the current bitmap value.
	private long valueBaseId;            // base entityId for the current bitmap value to be added to get the entityId.
	private long currentBitmapValue;     // the current bitmap value to be examined bit by bit.
	
	private boolean isActive;
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	ThreadedIterator(
		final GigaMap.Default<?>      parent        ,
		final BitmapResult[]          results       ,
		final int                     threadCount   ,
		final IterationThreadProvider threadProvider
	)
	{
		super();
		this.parent         = parent        ;
		this.results        = results       ;
		this.threadProvider = threadProvider;
		
		/* (18.01.2024 TM)TOD0: make entityId iterating thread wait time configurable
		 * (19.06.2024 TM)NOTE: however ... since the whole iterator might/could/should be deleted,
		 * it's not really worth the hassle, at the moment.
		 */
		this.waitTimeNs = 1000;
		
		this.level1SegmentCount   = this.calculateLevel1SegmentCount();
		final long registryLength = this.calculateIdListsRegistryLength();
		this.registryAddress      = allocateEmptyMemory(registryLength);
		this.registrySegmentCount = this.calculateRegistrySegmentCount();
		
		this.currentRegistryIndex   = -1; // must be -1 because of pre-increment logic
		this.currentRegistrySegmentIndex = REGISTRY_SEGMENT_SIZE;
		this.currentLevel1Index      = LEVEL_1_VALUE_COUNT;
		this.currentBitmapValue     = 0L;
		this.currentBitPosition     = Long.SIZE; // required to force efficient initialization.
		
		this.isActive = true;
		
		// create and start threads NOT before everything is setup, hence down here at the end.
		this.threads = threadProvider.startIterationThreads(parent, threadCount, this);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public final GigaMap.Default<?> parent()
	{
		return this.parent;
	}
	
	private int calculateLevel1SegmentCount()
	{
		/*
		 * total amount of level1Segments is:
		 * how many level1 segments are needed to contain the highestUsedId?
		 * E.g.:
		 * - One level1 segment can contain 4096 IDs (0 to 4095).
		 * - If the highestUsedId is 4096, TWO level1 segments are required.
		 * - If the highestUsedId is 4095 or lower, ONE level1 segment is required.
		 * So the formula is:
		 * (highestUsedId / level1IdCount) + 1.
		 */
		return (int)(this.parent.highestUsedId() >>> BitmapLevel3.LEVEL_1_TOTAL_SIZE_EXP) + 1;
	}
	
	private long calculateIdListsRegistryLength()
	{
		// level1Segment count times long byte length to get the total byte length
		return (long)this.level1SegmentCount * Long.BYTES;
	}
	
	private int calculateRegistrySegmentCount()
	{
		return this.level1SegmentCount / REGISTRY_SEGMENT_SIZE
			+ (this.level1SegmentCount % REGISTRY_SEGMENT_SIZE == 0 ? 0 : 1)
		;
	}

	@Override
	public boolean hasNextId()
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
		
		return this.hasMoreData();
	}

	@Override
	public long nextId()
	{
		if((this.currentBitmapValue & 1L<<this.currentBitPosition) == 0L)
		{
			this.scrollToNextMarkedBitPosition();
			if(!this.hasMoreData())
			{
				throw new NoSuchElementException();
			}
		}
		return this.valueBaseId + this.currentBitPosition++;
	}
	
	private boolean hasMoreData()
	{
		return this.currentRegistryIndex < this.level1SegmentCount;
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
		int currentLevel1Index = this.currentLevel1Index;
		long currentBitmapValue;
		do
		{
			if(++currentLevel1Index >= LEVEL_1_VALUE_COUNT)
			{
				if(!this.scrollToNextLevel1Segment())
				{
					// no more value group to scroll to, because iteration has reached the end of the data.
					return false;
				}
				// start the newly scrolled to level1segment at the beginning.
				currentLevel1Index = 0;
			}
			currentBitmapValue = XMemory.get_long(this.currentValueGroupAddress + currentLevel1Index * Long.BYTES);
		}
		while(currentBitmapValue == 0L);
		this.currentLevel1Index = currentLevel1Index;
		this.currentBitmapValue = currentBitmapValue;
		this.valueBaseId =
			this.currentRegistryIndex * REGISTRY_SEGMENT_ID_COUNT
			+ this.currentRegistrySegmentIndex * LEVEL_1_ID_COUNT
			+ this.currentLevel1Index * Long.SIZE
		;
		
		return true;
	}
	
	private boolean scrollToNextLevel1Segment()
	{
		int currentRegistrySegmentIndex = this.currentRegistrySegmentIndex;
		long currentValueGroupAddress;
		do
		{
			if(++currentRegistrySegmentIndex >= REGISTRY_SEGMENT_SIZE)
			{
				if(!this.scrollToNextRegistrySegment())
				{
					// no more level1 segment to scroll to, because iteration has reached the end of the data.
					return false;
				}
				// start the newly scrolled to level1segment at the beginning.
				currentRegistrySegmentIndex = 0;
			}
			currentValueGroupAddress = XMemory.get_long(this.currentRegistrySegmentAddress + currentRegistrySegmentIndex * Long.BYTES);
		}
		while(currentValueGroupAddress == 0L);
		this.currentRegistrySegmentIndex = currentRegistrySegmentIndex;
		this.currentValueGroupAddress = currentValueGroupAddress;
		
		return true;
	}
	
	private boolean scrollToNextRegistrySegment()
	{
		final long registryAddress      = this.registryAddress;
		final int  waitTimeNs           = this.waitTimeNs;
		final int  registrySegmentCount = this.registrySegmentCount;

		int currentRegistryIndex = this.currentRegistryIndex;
		while(++currentRegistryIndex < registrySegmentCount)
		{
			final long currentRegistryAddress = registryAddress + currentRegistryIndex * Long.BYTES;
			final long registrySegmentAddress = getRegistrySegmentAddress(currentRegistryAddress, waitTimeNs);
			if(registrySegmentAddress == SKIP_LEVEL1_SEGMENT_MARKER)
			{
				// skip this level1 segment since the worker thread had determined that it is completely empty.
				continue;
			}
			
			// set non-empty level1 segment as current iteration state
			this.currentRegistryIndex          = currentRegistryIndex  ;
			this.currentRegistrySegmentAddress = registrySegmentAddress;
			return true;
		}

		this.currentRegistryIndex          = this.level1SegmentCount;
		this.currentRegistrySegmentIndex   = Integer.MAX_VALUE;
		this.currentRegistrySegmentAddress = 0L;
		this.currentLevel1Index            = Integer.MAX_VALUE;
		this.currentValueGroupAddress      = 0L;
		this.currentBitmapValue            = 0L;
		this.currentBitPosition            = Long.SIZE;

		return false;
	}
	
	

	private static long getRegistrySegmentAddress(final long currentRegistryAddress, final int waitTimeNs)
	{
		while(true)
		{
			final long valueGroupsSegmentAddress = XMemory.volatileGet_long(null, currentRegistryAddress);
			if(valueGroupsSegmentAddress > 0L)
			{
				return valueGroupsSegmentAddress;
			}
			XThreads.sleep(0, waitTimeNs);
		}
	}

	@Override
	public void close()
	{
		this.threadProvider.disposeIterationThreads(this.threads);
		this.threadProvider.completeIteration();
	}

	@Override
	public boolean isClosed()
	{
		return !this.isActive;
	}
	
	@Override
	public void setInactive()
	{
		this.currentRegistryIndex = this.level1SegmentCount;
		this.isActive = false;
	}
	
	
	@Override
	public Runnable provideIterationLogic()
	{
		final BitmapResult[] resultsThreadIterationCopy = BitmapResult.createIterationCopy(this.results);
				
		return new ThreadLogic(
			resultsThreadIterationCopy,
			this.level1SegmentCount   ,
			this.registryAddress      ,
			this.registrySegmentCount
		);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void finalize() throws Throwable
	{
		this.parent.closeIterator(this);
		this.deallocateSegments();
	}
	
	private void deallocateSegments()
	{
		/*
		 * - the registry consists purely of a list of pointer long values pointing to a registrySegment.
		 * - each pointer value can be a dummy.
		 * - registrySegments consist purely of a list of pointer long values pointing to a valueGroup.
		 * - valueGroups contain no pointer, only bitmap values.
		 * 
		 * So deallocating requires:
		 * - Iterate the registry
		 * - For each entry, resolve the registrySegmentAddress
		 * - Iterate the registrySegment
		 * - Deallocate each valueGroup (pointer in the registrySegment)
		 * - AFTERWARDS, deallocate the registrySegment (pointer in the registry)
		 * - At the very end, deallocate the registry itself (this.registryAddress)
		 * 
		 * (quite a work without a garbage collector ... ^^)
		 */

		final long startAddress = registryIterationStartAddress(this.registryAddress);
		final long boundAddress = registryIterationBoundAddress(this.registryAddress, this.registrySegmentCount);

		for(long registryEntryAddress = startAddress; registryEntryAddress < boundAddress; registryEntryAddress += Long.BYTES)
		{
			final long registrySegmentAddress = XMemory.get_long(registryEntryAddress);
			if(!isValidRegistrySegmentAddress(registrySegmentAddress))
			{
				continue;
			}

			final int indexStart = registrySegmentIterationStartIndex();
			final int indexBound = registrySegmentIterationIndexCount();

			for(int segmentEntryIndex = indexStart; segmentEntryIndex < indexBound; segmentEntryIndex++)
			{
				final long segmentEntryAddress = registrySegmentEntryAddress(registrySegmentAddress, segmentEntryIndex);
				final long valueGroupAddress = XMemory.get_long(segmentEntryAddress);
				XMemory.free(valueGroupAddress);
			}
			XMemory.free(registrySegmentAddress);
		}
		XMemory.free(this.registryAddress);
	}
	
	
	static long allocateEmptyMemory(final long length)
	{
		return XMemory.allocateCleared(length);
	}
	
	
	static final class ThreadLogic implements Runnable
	{
		/*
		 * Segmentation rationale:
		 * 
		 * There's a lot of segmentation going on in here, but each segment is there for a specific reason:
		 * 
		 * The registry segments' purpose is to minimize the amount of costly volatile operations.
		 * ("volatile" is a stupidly bad name for that. It actually means more like "avoid thread caches but operate
		 * directly on main memory". Which, of course, is MUCH, MUCH more expensive than just operating on the
		 * caches. The rationale behind the weird name was probably something like "this value is highly volatile,
		 * so it must be treated accordingly" without having to understand any concept of thread local or CPU caches)
		 * 
		 * The multiple Level1Segments are those of the BitmapEntry raw data.
		 */
		
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final BitmapResult[] results             ;
		private final long           registryAddress     ;
		private final int            registrySegmentCount;
		
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		ThreadLogic(
			final BitmapResult[] results             ,
			final int            level1SegmentCount  ,
			final long           registryAddress     ,
			final int            registrySegmentCount
		)
		{
			super();
			this.results              = results             ;
			this.registryAddress      = registryAddress     ;
			this.registrySegmentCount = registrySegmentCount;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public void run()
		{
			final BitmapResult[] results = this.results;

			final long startAddress    = registryIterationStartAddress(this.registryAddress);
			final long boundAddress    = registryIterationBoundAddress(this.registryAddress, this.registrySegmentCount);
			final long lastItemAddress = registryIterationBoundAddress(this.registryAddress, this.registrySegmentCount - 1);

			int currentLevel1SegmentIndex = 0;
			long newSegmentAddress = createRegistrySegment();

			// rea = registryEntryAddress
			for(long rea = startAddress; rea < boundAddress; rea += Long.BYTES, currentLevel1SegmentIndex += REGISTRY_SEGMENT_SIZE)
			{
				// check for a yet unprocessed registry segment
				if(XMemory.volatileGet_long(null, rea) != 0L)
				{
					continue;
				}

				// try to reserve unprocessed coordinate (competing with other threads)
				if(!XMemory.compareAndSwap_long(null, rea, 0L, -1L))
				{
					continue;
				}

				// process reserved coordinate and register the address to the value groups segment.
				if(process(results, currentLevel1SegmentIndex, newSegmentAddress))
				{
					XMemory.volatileSet_long(null, rea, newSegmentAddress);
					newSegmentAddress = rea < lastItemAddress
						? createRegistrySegment()
						: 0L
					;
				}
				else
				{
					XMemory.volatileSet_long(null, rea, SKIP_LEVEL1_SEGMENT_MARKER);
				}
			}

			if(newSegmentAddress != 0L)
			{
				XMemory.free(newSegmentAddress);
			}
		}
				
		private static boolean process(
			final BitmapResult[] results               ,
			final int            level1SegmentIndex    ,
			final long           registrySegmentAddress
		)
		{
			updateIterationStateLevel3(results, level1SegmentIndex >>> BitmapLevel3.LEVEL_2_LENGTH_EXPONENT);

			final int indexStart = registrySegmentIterationStartIndex();
			final int indexBound = registrySegmentIterationIndexCount();

			long    currentValueGroupAddress  = 0L;
			boolean valueGroupsSegmentHasData = false;

			for(int segmentEntryIndex = indexStart; segmentEntryIndex < indexBound; segmentEntryIndex++)
			{
				updateIterationStateLevel2(results, level1SegmentIndex + segmentEntryIndex & BitmapLevel3.LEVEL_2_INDEX_MASK);
				if(currentValueGroupAddress == 0L)
				{
					currentValueGroupAddress = createValueGroup();
				}
				if(processLevel1Segment(results, currentValueGroupAddress))
				{
					XMemory.set_long(registrySegmentEntryAddress(registrySegmentAddress, segmentEntryIndex), currentValueGroupAddress);
					currentValueGroupAddress = 0L;
					valueGroupsSegmentHasData = true;
				}
			}
			if(currentValueGroupAddress != 0L)
			{
				XMemory.free(currentValueGroupAddress);
			}
			
			return valueGroupsSegmentHasData;
		}
		

		private static boolean processLevel1Segment(
			final BitmapResult[] results                 ,
			final long           currentValueGroupAddress
		)
		{
			boolean vgiHasData = false;
			for(int level1Index = 0; level1Index < LEVEL_1_VALUE_COUNT; level1Index++)
			{
				// iterate over each result and calculate the current bitmap value result.
				final long valueResult = calculateBitmapValue(results, level1Index);
				if(valueResult != 0L)
				{
					XMemory.set_long(currentValueGroupAddress + level1Index * Long.BYTES, valueResult);
					vgiHasData = true;
				}
			}
			
			return vgiHasData;
		}
		
		private static void updateIterationStateLevel3(final BitmapResult[] results, final int level3Index)
		{
			for(final BitmapResult r : results)
			{
				r.setCurrentIterationLevel2Segment(level3Index);
			}
		}
		
		private static void updateIterationStateLevel2(final BitmapResult[] results, final int level2Index)
		{
			for(final BitmapResult r : results)
			{
				r.setCurrentIterationLevel1Segment(level2Index);
			}
		}
		
		private static long calculateBitmapValue(final BitmapResult[] results, final int level1Index)
		{
			long valueResult = -1L;
			for(final BitmapResult r : results)
			{
				if((valueResult &= r.getCurrentLevel1BitmapValue(level1Index)) == 0L)
				{
					// early exit: an all-0s value will never be changed by AND logic.
					break;
				}
			}
			
			return valueResult;
		}
				
		private static long createRegistrySegment()
		{
			return allocateEmptyMemory(REGISTRY_SEGMENT_SIZE * Long.BYTES);
		}
		
		private static long createValueGroup()
		{
			return allocateEmptyMemory(LEVEL_1_LENGTH);
		}
				
	}
	
}
