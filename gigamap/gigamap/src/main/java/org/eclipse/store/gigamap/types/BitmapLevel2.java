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

import org.eclipse.store.gigamap.exceptions.BitmapLevel2Exception;
import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.math.XMath;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.persistence.types.Unpersistable;
import org.eclipse.serializer.typing.XTypes;

import java.util.function.Consumer;


/**
 * The BitmapLevel2 class provides detailed mechanisms and operations for managing
 * and manipulating Level 2 Bitmap segments within a hierarchical bitmap structure.
 * The class supports allocation, compression, decompression, and addressing
 * functionalities, while also facilitating interaction with Level 1 segments.
 * This class includes both static and instance-level methods to comprehensively
 * handle Level 2 bitmap management.
 * <p>
 * Key responsibilities include:
 * <ul>
 * <li>Managing Level 2 segment attributes such as type, total length, and segment counts.</li>
 * <li>Handling the allocation and deallocation of Level 1 and Level 2 bitmap segments.</li>
 * <li>Supporting compression and decompression of Level 1 segments within Level 2 structures.</li>
 * <li>Permitting a hierarchical link to Level 3 contexts.</li>
 * <li>Ensuring validation and structural integrity of Level 2 configurations.</li>
 * </ul>
 */
public class BitmapLevel2 extends AbstractStateChangeFlagged implements Unpersistable
{
	///////////////////////////////////////////////////////////////////////////
	// Constants //
	//////////////
		
	private static final int
		POINTER_LENGTH                  = Long.BYTES, // low-level pointers are always longs! NOT object references!
		SEGMENT_COUNT                   = BitmapLevel3.LEVEL_2_SEGMENT_LENGTH,
		LEVEL1_UNCOMPRESSED_DATA_LENGTH = BitmapLevel3.LEVEL_1_SEGMENT_LENGTH,
		ENTRY_HEADER_BASE_LENGTH        = Byte.BYTES                            ,    // applies in any case, including trivial entries
		ENTRY_HEADER_FULL_LENGTH        = ENTRY_HEADER_BASE_LENGTH + Short.BYTES,    // non-trivial entries have an extension short value
		ENTRY_LENGTH_BIT_SIZE           = 10,                                        // 10 bits are required to hold the worst case entry length of slightly over 512 bytes.
		ENTRY_LENGTH_BIT_MASK           = (1<<ENTRY_LENGTH_BIT_SIZE) - 1,            // Lowest 10 bits of the header extension short are the length.
		ENTRY_BITPOP_BIT_SIZE           = 12,                                        // 12 bits are required to hold the maximum bit population of 4096.
		ENTRY_BITPOP_EXTENSION_BIT_SIZE = Short.SIZE - ENTRY_LENGTH_BIT_SIZE,        // 16 - 10 = 6
		ENTRY_BITPOP_EXTENSION_MASK     = (1<< ENTRY_BITPOP_EXTENSION_BIT_SIZE) - 1,
		ENTRY_BITPOP_EXTENSION_OFFSET   = ENTRY_LENGTH_BIT_SIZE,                     // must shift exactly the length's required space
		ENTRY_BITPOP_BASE_BIT_SIZE      = ENTRY_BITPOP_BIT_SIZE - ENTRY_BITPOP_EXTENSION_BIT_SIZE, // remaining part
		ENTRY_BITPOP_BASE_OFFSET        = ENTRY_BITPOP_EXTENSION_BIT_SIZE,           // must shift away the bits that get writting in the extension
		ENTRY_BITPOP_BASE_READ_MASK     = (1<<ENTRY_BITPOP_BASE_BIT_SIZE) - 1,
		
		OFFSET_TOTAL_LENGTH             = 0,
		OFFSET_STANDALONE_SEGMENT_COUNT = OFFSET_TOTAL_LENGTH             + Integer.BYTES,
		OFFSET_LEVEL2_INDEX_ARRAY_START = OFFSET_STANDALONE_SEGMENT_COUNT + Short.BYTES,
		OFFSET_LEVEL2_INDEX_ARRAY_BOUND = OFFSET_LEVEL2_INDEX_ARRAY_START + SEGMENT_COUNT * POINTER_LENGTH,
		TRANSIENT_BASE_LENGTH           = OFFSET_LEVEL2_INDEX_ARRAY_BOUND,
		
		OFFSET_PERSISTENT_DATA          = TRANSIENT_BASE_LENGTH,
		OFFSET_LEVEL3_INDEX             = OFFSET_PERSISTENT_DATA,
		OFFSET_TYPE                     = OFFSET_LEVEL3_INDEX         + Integer.BYTES,
		OFFSET_HIGHEST_LEVEL2_INDEX     = OFFSET_TYPE                 + Byte.BYTES,
		OFFSET_SEGMENT_COUNT            = OFFSET_HIGHEST_LEVEL2_INDEX + Byte.BYTES,
		OFFSET_SEGMENTs                 = OFFSET_SEGMENT_COUNT        + Short.BYTES,
		BASE_LENGTH                     = OFFSET_SEGMENTs,
		
		ENTRY_OFFSET_HEADER             = 0,
		ENTRY_OFFSET_HEADER_EXTENSION   = ENTRY_OFFSET_HEADER + ENTRY_HEADER_BASE_LENGTH,
		ENTRY_BASE_LENGTH               = ENTRY_OFFSET_HEADER + ENTRY_HEADER_FULL_LENGTH,
		ENTRY_OFFSET_DATA               = ENTRY_BASE_LENGTH,
		ENTRY_TRIVIAL_TOTAL_LENGTH      = ENTRY_HEADER_BASE_LENGTH, // trivial entry consists of just a base header

		/*
		 * Tricky!
		 * Standalone segments must have enough leading bytes to contain:
		 * - a complete entry header for inplace compression
		 * - AND the maximum length of a chunk head for the first chunk
		 * in order to prevent data overwrite errors during inplace compression.
		 * 
		 * So the UNCOMPRESSED data must start a little bit further ahead than the compressed data.
		 * Both must leave space for the entry base length, but the uncompressed data must also leave
		 * additional space for the compression's first chunk's head.
		 */
		LEVEL1_STANDALONE_BASE_LENGTH              = ENTRY_BASE_LENGTH + BytePatternCompression.entryCompressionBaseLength(),
		LEVEL1_STANDALONE_OFFSET_UNCOMPRESSED_DATA = LEVEL1_STANDALONE_BASE_LENGTH + BytePatternCompression.chunkHeadMaximumLength(),
		LEVEL1_STANDALONE_OFFSET_COMPRESSED_DATA   = ENTRY_BASE_LENGTH,
		LEVEL1_STANDALONE_TOTAL_LENGTH             = LEVEL1_STANDALONE_OFFSET_UNCOMPRESSED_DATA + LEVEL1_UNCOMPRESSED_DATA_LENGTH,
	
		LEVEL2_TYPE_UNDEFINED               = 0, // necessary to express that an uninitialized value is an undefined type
		LEVEL2_TYPE_UNCOMPRESSED            = 1,
		LEVEL2_TYPE_BYTEPATTERN_COMPRESSION = 2,
		// currently unused:     [3;255]. Meaning lots of space for other compression types in the future.
		COMPRESSION_TYPE_HIGHEST_USED = LEVEL2_TYPE_BYTEPATTERN_COMPRESSION,

		REMOVAL_TYPE_NONE   = -1, // entityId (single bit) has not been removed at all
		REMOVAL_TYPE_NORMAL =  0, // entityId (single bit) has been removed, but there are still other bits in the long.
		REMOVAL_TYPE_EMPTY  = +1  // entityId (single bit) has been removed and the whole long value is now empty (0L).
	;
	
	private static final byte
		ENTRY_HEADER_TYPE_ALL_ZEROES = -128, // 1000_0000 // "trivial" flag bit and only zeroes
		ENTRY_HEADER_TYPE_ALL_ONES   =   -1  // 1111_1111 // "trivial" flag bit and only ones
		// "0xxx_xxxx" means non-trivial entry. The 7 "x" bits are used as a part of the 12 bit long bitPopulation value
	;
	
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// Static Methods //
	///////////////////
	
	static BinaryTypeHandler<BitmapLevel2> provideTypeHandler()
	{
		return BinaryHandlerBitmapLevel2.New();
	}
	
	private static void setEntryHeader(final long level1EntryAddress, final byte entryType)
	{
		XMemory.set_byte(level1EntryAddress + ENTRY_OFFSET_HEADER, entryType);
	}
	
	private static void setEntryNonTrivialHeader(
		final long level1EntryAddress,
		final int  entryTotalLength  ,
		final int  bitPopulation
	)
	{
		validateBitPopulation(bitPopulation);
		
		// no entryType needed since it would be 0, anyway.
		final byte headerBase = buildEntryHeaderNonTrivial(bitPopulation);
		setEntryHeader(level1EntryAddress, headerBase);
		
		final short headerExtension = buildEntryHeaderNonTrivialExtension(entryTotalLength, bitPopulation);
		XMemory.set_short(level1EntryAddress + ENTRY_OFFSET_HEADER_EXTENSION, headerExtension);
	}
	
	private static void validateBitPopulation(final int bitPopulation)
	{
		if(bitPopulation > 0 && bitPopulation < BitmapLevel3.LEVEL_1_TOTAL_BITCOUNT_BOUND)
		{
			return;
		}
		
		throw new BitmapLevel2Exception("Invalid bit population: " + bitPopulation);
	}
	
	private static int getEntryNonTrivialBitPopulation(final long level1EntryAddress)
	{
		// messy assembly of the bit population value split across entry base header and header extension
		return
			(Byte.toUnsignedInt(getEntryHeader(level1EntryAddress)) & ENTRY_BITPOP_BASE_READ_MASK) << ENTRY_BITPOP_BASE_OFFSET
			|
			((getEntryHeaderNonTrivialExtension(level1EntryAddress) & ENTRY_BITPOP_EXTENSION_MASK) >>> ENTRY_BITPOP_EXTENSION_OFFSET)
			+ 1 // must add the missing count back in. See header building methods.
		;
	}
		
	private static byte buildEntryHeaderNonTrivial(final int bitPopulation)
	{
		// bit population can be [1;4096], but 12 bits have a value range of [0; 4095], so - 1 is necessary.
		return (byte)(bitPopulation - 1 >>> ENTRY_BITPOP_BASE_OFFSET);
	}
	
	private static short buildEntryHeaderNonTrivialExtension(final int entryTotalLength, final int bitPopulation)
	{
		// bit population can be [1;4096], but 12 bits have a value set of [0; 4095], so - 1 is necessary.
		return (short)
			((bitPopulation - 1 & ENTRY_BITPOP_EXTENSION_MASK) << ENTRY_BITPOP_EXTENSION_OFFSET
			|
			entryTotalLength & ENTRY_LENGTH_BIT_MASK)
		;
	}
	
	private static long getEntryNonTrivialDataAddress(final long level1EntryAddress)
	{
		// data address is the entry address plus the (non-trivial) entry header length.
		return level1EntryAddress + ENTRY_OFFSET_DATA;
	}
	
	private static long allocateNew(final int level3Index)
	{
		final long level2Address = allocateLevel2Block(BASE_LENGTH);

		setLevel3Index(level2Address, level3Index);
		
		return level2Address;
	}
	
	private static long allocateLevel2Block(final int length)
	{
		final long level2Address = XMemory.allocate(length);
		
		// must ensure all base length bytes are set to 0 since logic relies on it (see comments below)
		XMemory.fillMemory(level2Address, BASE_LENGTH, (byte)0);
						
		setTotalLength(level2Address, length);
		setLevel2SegmentType(level2Address, LEVEL2_TYPE_BYTEPATTERN_COMPRESSION); // currently ALWAYS simple range.
		// all other base values remain 0 for now.
		// all level1 pointers remain null
		
		return level2Address;
	}
	
	static void validateLevel2SegmentType(final long level2Address)
	{
		final int segmentType = getLevel2SegmentType(level2Address);
		
		// currently ALWAYS simple range.
		if(segmentType == LEVEL2_TYPE_BYTEPATTERN_COMPRESSION)
		{
			return;
		}
		
		if(segmentType == LEVEL2_TYPE_UNDEFINED)
		{
			throw createInvalidLevel2SegmentTypeException(segmentType, "undefined");
		}
		if(segmentType == LEVEL2_TYPE_UNCOMPRESSED)
		{
			throw createInvalidLevel2SegmentTypeException(segmentType, "uncompressed, not yet supported");
		}
		
		throw createInvalidLevel2SegmentTypeException(segmentType, null);
	}
		
	private static void setTotalLength(final long level2Address, final int totalLength)
	{
		XMemory.set_int(level2Address + OFFSET_TOTAL_LENGTH, totalLength);
	}
	
	private static int getTotalLength(final long level2Address)
	{
		return XMemory.get_int(level2Address + OFFSET_TOTAL_LENGTH);
	}
	
	private static void setSegmentCount(final long level2Address, final int segmentCount)
	{
		XMemory.set_short(level2Address + OFFSET_SEGMENT_COUNT, (short)segmentCount);
	}
	
	private static int getSegmentCount(final long level2Address)
	{
		return Short.toUnsignedInt(XMemory.get_short(level2Address + OFFSET_SEGMENT_COUNT));
	}
	
	private static void setLevel2SegmentType(final long level2Address, final int segmentType)
	{
		XMemory.set_byte(level2Address + OFFSET_TYPE, (byte)segmentType);
	}
	
	private static int getLevel2SegmentType(final long level2Address)
	{
		return Byte.toUnsignedInt(XMemory.get_byte(level2Address + OFFSET_TYPE));
	}
	
	private static void setStandaloneSegmentCount(final long level2Address, final int standaloneSegmentCount)
	{
		XMemory.set_short(level2Address + OFFSET_STANDALONE_SEGMENT_COUNT, (short)standaloneSegmentCount);
	}
	
	private static int getStandaloneSegmentCount(final long level2Address)
	{
		return Short.toUnsignedInt(XMemory.get_short(level2Address + OFFSET_STANDALONE_SEGMENT_COUNT));
	}
	
	private static void incrementSegmentCount(final BitmapLevel3 level3, final long level2Address)
	{
		setSegmentCount(level2Address, getSegmentCount(level2Address) + 1);
		level3.incrementTotalSegmentCount();
	}
	
	private static void incrementStandaloneSegmentCount(final long level2Address)
	{
		setStandaloneSegmentCount(level2Address, getStandaloneSegmentCount(level2Address) + 1);
	}
	
	private static long getIndexArrayStartAddress(final long level2Address)
	{
		return level2Address + OFFSET_LEVEL2_INDEX_ARRAY_START;
	}
	
	private static long getIndexArrayBoundAddressEffective(final long level2Address)
	{
		final int highestLevel2Index = getHighestLevel2Index(level2Address);
		
		// bound address is that of one more index than the current highest, hence +1.
		return toLevel2SegmentsIndexAddress(level2Address, highestLevel2Index + 1);
	}
				
	private static long entryAddressMark(final long level1EntryUnmarkedAddress)
	{
		// the reverse function of * -1 is, of course, * -1
		return level1EntryUnmarkedAddress * -1;
	}
	
	private static long entryAddressUnmark(final long level1EntryMarkedAddress)
	{
		// the reverse function of * -1 is, of course, * -1
		return level1EntryMarkedAddress * -1;
	}
		
	private static boolean isCompressed(final long level2Address)
	{
		return getStandaloneSegmentCount(level2Address) == 0;
	}
	
	private static boolean isFullyDecompressed(final long level2Address)
	{
		return getStandaloneSegmentCount(level2Address) == getSegmentCount(level2Address);
	}
	
	private static void decrementStandaloneSegmentCount(final long level2Address)
	{
		setStandaloneSegmentCount(level2Address, getStandaloneSegmentCount(level2Address) - 1);
	}
	
	private static void decrementSegmentCount(final long level2Address)
	{
		setSegmentCount(level2Address, getSegmentCount(level2Address) - 1);
	}
	
	private static int getLevel3Index(final long level2Address)
	{
		return XMemory.get_int(level2Address + OFFSET_LEVEL3_INDEX);
	}
	
	private static int getHighestLevel2Index(final long level2Address)
	{
		return Byte.toUnsignedInt(XMemory.get_byte(level2Address + OFFSET_HIGHEST_LEVEL2_INDEX));
	}
		
	private static void setHighestLevel2Index(final long level2Address, final int level2Index)
	{
		XMemory.set_byte(level2Address + OFFSET_HIGHEST_LEVEL2_INDEX, (byte)level2Index);
	}
	
	private static void setLevel3Index(final long level2Address, final int level3Index)
	{
		XMemory.set_int(level2Address + OFFSET_LEVEL3_INDEX, level3Index);
	}
	
	private static long toLevel2SegmentsIndexAddress(final long level2Address, final int level2Index)
	{
		return getIndexArrayStartAddress(level2Address) + level2Index * POINTER_LENGTH;
	}
	
	private static long toLevel2EntriesStartAddress(final long level2Address)
	{
		return level2Address + OFFSET_SEGMENTs;
	}
		
	private static void removeLevel1StandaloneSegment(final long level2Address, final int level2Index)
	{
		XMemory.free(getLevel1SegmentAddress(level2Address, level2Index));
		clearLevel1SegmentAddress(level2Address, level2Index);
		decrementStandaloneSegmentCount(level2Address);
		decrementSegmentCount(level2Address);
		// level3 total segment count decrementation cannot be done here but must be handled via return value
	}
	
	private static void setLevel1SegmentAddress(
		final long level2Address,
		final int  level2Index  ,
		final long level1Address
	)
	{
		XMemory.set_long(toLevel2SegmentsIndexAddress(level2Address, level2Index), level1Address);
	}
	
	private static void registerLevel1SegmentAddress(
		final long level2Address,
		final int  level2Index  ,
		final long level1Address
	)
	{
		if(level2Index > getHighestLevel2Index(level2Address))
		{
			setHighestLevel2Index(level2Address, level2Index);
		}
		setLevel1SegmentAddress(level2Address, level2Index, level1Address);
	}
	
	private static void clearLevel1SegmentAddress(
		final long level2Address,
		final int  level2Index
	)
	{
		// must check to adjust highest level2Index if it happens to be the index to be cleared
		if(level2Index == getHighestLevel2Index(level2Address))
		{
			final int newHighestLevel2Index = scrollToNextLowestLevel2Index(level2Address, level2Index);
			if(newHighestLevel2Index < 0)
			{
				setHighestLevel2Index(level2Address, 0);
			}
			else
			{
				setHighestLevel2Index(level2Address, newHighestLevel2Index);
			}
		}
		
		// actual clearing
		setLevel1SegmentAddress(level2Address, level2Index, 0L);
	}
	
	private static int scrollToNextLowestLevel2Index(
		final long level2Address,
		final int  level2Index
	)
	{
		int i = level2Index;
		while(--i >= 0)
		{
			final long indexArrayAddress = toLevel2SegmentsIndexAddress(level2Address, i);
			if(XMemory.get_long(indexArrayAddress) != 0L)
			{
				return i;
			}
		}
				
		// will be -1 at this point, i.e. "not found".
		return i;
	}
			
	private static byte getEntryHeader(final long level1EntryAddress)
	{
		return XMemory.get_byte(level1EntryAddress + ENTRY_OFFSET_HEADER);
	}
	
	private static int getEntryHeaderNonTrivialExtension(final long level1EntryAddress)
	{
		return Short.toUnsignedInt(
			XMemory.get_short(level1EntryAddress + ENTRY_OFFSET_HEADER_EXTENSION)
		);
	}
	
	private static int getEntryNonTrivialTotalLength(final long level1EntryAddress)
	{
		return getEntryHeaderNonTrivialExtension(level1EntryAddress) & ENTRY_LENGTH_BIT_MASK;
	}
	
	private static int getEntryNonTrivialDataLength(final long level1EntryAddress)
	{
		// data length is the total length minus the (short) header length.
		return getEntryNonTrivialTotalLength(level1EntryAddress) - ENTRY_HEADER_FULL_LENGTH;
	}
	

		
	private static long toLevel1UncompressedDataAddress(final long level1UncompressedAddress)
	{
		return level1UncompressedAddress + LEVEL1_STANDALONE_OFFSET_UNCOMPRESSED_DATA;
	}
	
	private static long toLevel1InPlaceCompressedDataAddress(final long level1UncompressedAddress)
	{
		return level1UncompressedAddress + LEVEL1_STANDALONE_OFFSET_COMPRESSED_DATA;
	}
	
	
	
	static long getLevel1SegmentAddress(final long level2Address, final int level2Index)
	{
		return XMemory.get_long(toLevel2SegmentsIndexAddress(level2Address, level2Index));
	}
	
	static boolean isRemovalTypeNone(final int removalCode)
	{
		return removalCode == REMOVAL_TYPE_NONE;
	}
		
	static boolean isRemovalTypeRemoved(final int removalCode)
	{
		return removalCode >= REMOVAL_TYPE_NORMAL;
	}
	
	static boolean isRemovalTypeNormal(final int removalCode)
	{
		return removalCode == REMOVAL_TYPE_NORMAL;
	}
	
	static boolean isRemovalTypeEmpty(final int removalCode)
	{
		return removalCode == REMOVAL_TYPE_EMPTY;
	}
	
		
	
	///////////////////////////////////////////////////////////////////////////
	// Static Constructors //
	////////////////////////
	
	public static BitmapLevel2 New(final int level3Index)
	{
		return new BitmapLevel2(level3Index);
	}
		
	
	
	///////////////////////////////////////////////////////////////////////////
	// Instance Fields //
	////////////////////

	long level2Address;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// Constructors //
	/////////////////
	
	// for normal instantiation and subclasses
	protected BitmapLevel2(final int level3Index)
	{
		this(true, allocateNew(level3Index));
	}
	
	// for internal and type handler use
	BitmapLevel2(final boolean isNew, final long level2Address)
	{
		super(isNew);
		this.level2Address = level2Address;
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// Instance Methods //
	/////////////////////
	
	BitmapLevel3 parent()
	{
		// empty for default implementation
		return null;
	}
	
	void setParentContext(final BitmapLevel3 parent)
	{
		// empty for default implementation
	}
	
	final int level3Index()
	{
		return getLevel3Index(this.level2Address);
	}
	
	final int totalLength()
	{
		return getTotalLength(this.level2Address);
	}
	
	final int segmentCount()
	{
		return getSegmentCount(this.level2Address);
	}
	
	final int remove(final long entityId)
	{
		return remove(this.level2Address, entityId);
	}
	
	final boolean isCompressed()
	{
		return isCompressed(this.level2Address);
	}
	
	final void ensureCompressed()
	{
		if(isCompressed(this.level2Address))
		{
			return;
		}
		
		// consolidate all segments into a newly allocated memory block
		final long newAddress = compress(this.level2Address);
		
		// current level2 structure gets cleaned up
		deallocate(this.level2Address);
			
		// address to the new memory block replaces the old (and now invalid) one.
		this.level2Address = newAddress;
	}
	
	final void ensureDecompressed()
	{
		if(isFullyDecompressed(this.level2Address))
		{
			return;
		}
		
		// decompress ALL level1 segments into standalone segments with uncompressed data
		final long newAddress = decompress(this.level2Address);
		
		// current level2 structure gets cleaned up
		deallocate(this.level2Address);
			
		// address to the new memory block replaces the old (and now invalid) one.
		this.level2Address = newAddress;
	}
	

	
	final long ensureAddableLevel1Segment(final long entityId, final BitmapLevel3 level3)
	{
		return ensureAddableLevel1SegmentForEntityId(this.level2Address, entityId, level3);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void finalize() throws Throwable
	{
		deallocate(this.level2Address);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// Allocation //
	///////////////
		
	private static long allocateLevel1StandaloneSegmentBlank(final long level2Address, final int level2Index)
	{
		final long level1Address = XMemory.allocate(LEVEL1_STANDALONE_TOTAL_LENGTH);
		registerStandaloneLevel1Segment(level2Address, level2Index, level1Address);
		return level1Address;
	}
		
	private static long allocateLevel1StandaloneSegmentEmpty(final long level2Address, final int level2Index)
	{
		final long level1Address = XMemory.allocateCleared(LEVEL1_STANDALONE_TOTAL_LENGTH);
		registerStandaloneLevel1Segment(level2Address, level2Index, level1Address);
		
		return level1Address;
	}
	
	private static long allocateLevel1StandaloneSegmentFull(final long level2Address, final int level2Index)
	{
		final long level1Address = XMemory.allocateCleared(LEVEL1_STANDALONE_TOTAL_LENGTH);
		XMemory.fillMemory(toLevel1UncompressedDataAddress(level1Address), LEVEL1_UNCOMPRESSED_DATA_LENGTH, (byte)-1);
		registerStandaloneLevel1Segment(level2Address, level2Index, level1Address);
		
		return level1Address;
	}
		
	private static void deallocate(final long level2Address)
	{
		final long indexArrayStart = getIndexArrayStartAddress(level2Address);
		final long indexArrayBound = getIndexArrayBoundAddressEffective(level2Address);
		for(long a = indexArrayStart; a < indexArrayBound; a += POINTER_LENGTH)
		{
			// null segments (unused or all-0-bits), full segments (-1L) or cached compressed segments are no pointers
			if(XMemory.get_long(a) <= 0L)
			{
				continue;
			}
			
			// standalone segments (having actual pointers) need to have their allocated memory deallocated
			XMemory.free(XMemory.get_long(a));
		}

		XMemory.free(level2Address);
	}
		
	private static void registerStandaloneLevel1Segment(
		final long level2Address,
		final int  level2Index  ,
		final long level1Address
	)
	{
		registerLevel1SegmentAddress(level2Address, level2Index, level1Address);
		incrementStandaloneSegmentCount(level2Address);
	}
	
	private static long ensureAddableLevel1SegmentForEntityId(
		final long         level2Address,
		final long         entityId     ,
		final BitmapLevel3 level3
	)
	{
		return ensureAddableLevel1SegmentForLevel2Index(level2Address, BitmapLevel3.toLevel2Index(entityId), level3);
	}
	
	private static long ensureAddableLevel1SegmentForLevel2Index(
		final long         level2Address,
		final int          level2Index  ,
		final BitmapLevel3 level3
	)
	{
		// address values < 0 are modified or abused pointer values to indicate special cases.
		final long level1Address;
		if((level1Address = getLevel1SegmentAddress(level2Address, level2Index)) == 0L)
		{
			// only 0L counts as not being a segment. Special segments (< 0) are handled below.
			incrementSegmentCount(level3, level2Address);
			
			return allocateLevel1StandaloneSegmentEmpty(level2Address, level2Index);
		}
		else if(level1Address < 0L)
		{
			return normalizeNegativeLevel1Address(level2Address, level2Index, level1Address);
		}
		
		// uncompressed level1 segment with a proper reference to a standalone allocated memory block.
		return level1Address;
	}
	
	private static long normalizeNegativeLevel1Address(
		final long level2Address,
		final int  level2Index  ,
		final long level1Address
	)
	{
		/*
		 * -1L means a whole level 1 segment that consists purely of 1-bits, so it can allocated generically.
		 * Any other negative value indicates a compressed level1 segment, so it needs to be decompressed.
		 */
		return level1Address == -1L
			? allocateLevel1StandaloneSegmentFull(level2Address, level2Index)
			: decompressLevel1Segment(level2Address, entryAddressUnmark(level1Address), level2Index)
		;
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// Removing //
	/////////////
	
	private static int remove(final long level2Address, final long entityId)
	{
		final int  level2Index   = BitmapLevel3.toLevel2Index(entityId);
		final long level1Address = getLevel1SegmentAddress(level2Address, level2Index);
		
		final long level1UncompressedAddress;
		if(level1Address <= 0L)
		{
			if(level1Address == 0L)
			{
				// null pointer (0L) means the entityId has not been marked in the non-existing segment, anyway.
				return REMOVAL_TYPE_NONE;
			}
			level1UncompressedAddress = normalizeNegativeLevel1Address(level2Address, level2Index, level1Address);
		}
		else
		{
			level1UncompressedAddress = level1Address;
		}
				
		final int removalType;
		if(isRemovalTypeRemoved(removalType = removeFromLevel1(level1UncompressedAddress, BitmapLevel3.toLevel1Id(entityId))))
		{
			// "empty" means the current long value is 0L (it is "empty" now)
			if(isRemovalTypeEmpty(removalType))
			{
				// "empty" here means the current SEGMENT now contains nothing but 0L long values.
				return checkForLevel1UncompressedSegmentClear(level2Address, level1UncompressedAddress, level2Index)
					? REMOVAL_TYPE_EMPTY
					: REMOVAL_TYPE_NORMAL
				;
			}
			return REMOVAL_TYPE_NORMAL;
		}
		
		return REMOVAL_TYPE_NONE;
	}
			
	private static boolean checkForLevel1UncompressedSegmentClear(
		final long level2Address,
		final long level1Address,
		final int  level2Index
	)
	{
		XMath.positive(level1Address);
		final long addressStart = toLevel1UncompressedDataAddress(level1Address);
		final long addressBound = addressStart + LEVEL1_UNCOMPRESSED_DATA_LENGTH;
		for(long a = addressStart; a < addressBound; a += POINTER_LENGTH)
		{
			if(XMemory.get_long(a) != 0L)
			{
				return false;
			}
		}
		
		removeLevel1StandaloneSegment(level2Address, level2Index);
		
		return true;
	}
		
	
	
	///////////////////////////////////////////////////////////////////////////
	// Compression //
	////////////////
	
	private static int ensureCompressedEntries(final long level2Address)
	{
		int totalEntriesLength = 0;
		
		final long indexArrayStart = getIndexArrayStartAddress(level2Address);
		final long indexArrayBound = getIndexArrayBoundAddressEffective(level2Address);
		for(long a = indexArrayStart; a < indexArrayBound; a += POINTER_LENGTH)
		{
			final long level1EntryAddress = XMemory.get_long(a);
			
			// everything with an (abused) pointer value <= 0L either doesn't exist (0L) or is already compressed
			if(level1EntryAddress <= 0L)
			{
				if(level1EntryAddress == 0L)
				{
					totalEntriesLength += ENTRY_TRIVIAL_TOTAL_LENGTH;
					continue;
				}
				if(level1EntryAddress == -1L)
				{
					totalEntriesLength += ENTRY_TRIVIAL_TOTAL_LENGTH;
					continue;
				}
				if(level1EntryAddress < 0L)
				{
					// count already compressed entry data length only. Header will be accounted on its own.
					totalEntriesLength += getEntryNonTrivialTotalLength(entryAddressUnmark(level1EntryAddress));
				}
				continue;
			}
			
			// standalone segments gets compressed in place and has its total length added.
			totalEntriesLength += compressLevel1SegmentInPlace(level1EntryAddress);
		}
		
		return totalEntriesLength;
	}
	
	private static long decompress(final long level2Address)
	{
		final int level3Index        = getLevel3Index(level2Address);
		final int highestLevel2Index = getHighestLevel2Index(level2Address);
		final int level2IndexBound   = highestLevel2Index + 1;
		
		// already sets totalLength and level3Index
		final long newLevel2Address = allocateNew(level3Index);
		setHighestLevel2Index    (newLevel2Address, highestLevel2Index            );           // unchanged, just copied.
		setSegmentCount          (newLevel2Address, getSegmentCount(level2Address));           // unchanged, just copied.
		setStandaloneSegmentCount(newLevel2Address, getStandaloneSegmentCount(level2Address)); // unchanged, just copied.
				
		for(int i = 0; i < level2IndexBound; i++)
		{
			final long oldLevel1Address;
			if((oldLevel1Address = getLevel1SegmentAddress(level2Address, i)) < 0L)
			{
				// method do standalone segment count updating and pointer registering internally themselves.
				if(oldLevel1Address == -1L)
				{
					allocateLevel1StandaloneSegmentFull(newLevel2Address, i);
				}
				else
				{
					decompressLevel1Segment(newLevel2Address, entryAddressUnmark(oldLevel1Address), i);
				}
			}
			else
			{
				// either null pointer or already decompressed/standalone level1 segment, keep as is.
				setLevel1SegmentAddress(newLevel2Address, i, oldLevel1Address);
			}
		}
		
		// totalLength and level3Index have already been set by allocation method above
		
		return newLevel2Address;
	}
	
			
	private static long compress(final long level2Address)
	{
		// note: every index from 0 to (including) highestLevel2Index gets an entry, even if it's just a header dummy.
		final int highestLevel2Index = getHighestLevel2Index(level2Address);
		final int entriesTotalLength = ensureCompressedEntries(level2Address);
		
		// must allocate enough memory to contain the base values plus all entries (headers) plus all the entry data.
		final int requiredLevel2TotalLength = BASE_LENGTH + entriesTotalLength;

		final long newLevel2Address = allocateLevel2Block(requiredLevel2TotalLength);
		
		// level1 segments are iterated and copied or compressed to the target
		final long newEntriesStartAddress = toLevel2EntriesStartAddress(newLevel2Address);
		final long newIndexArrayStart     = getIndexArrayStartAddress(newLevel2Address);
		final long indexArrayStart        = getIndexArrayStartAddress(level2Address);
		final int  indexArrayOffsetBound  = (highestLevel2Index + 1) * POINTER_LENGTH;
		long targetEntryAddress = newEntriesStartAddress;
		for(int indexArrayOffset = 0; indexArrayOffset < indexArrayOffsetBound; indexArrayOffset += POINTER_LENGTH)
		{
			final long newIndexAddress    = newIndexArrayStart + indexArrayOffset;
			final long sourceEntryAddress = XMemory.get_long(indexArrayStart + indexArrayOffset);
			targetEntryAddress += compressEntry(sourceEntryAddress, targetEntryAddress, newIndexAddress);
		}
		
		setTotalLength(newLevel2Address, requiredLevel2TotalLength);       // new total length, of course.
		setLevel3Index(newLevel2Address, getLevel3Index(level2Address));   // unchanged, just copied.
		setHighestLevel2Index(newLevel2Address, highestLevel2Index);       // unchanged, just copied.
		setSegmentCount(newLevel2Address, getSegmentCount(level2Address)); // unchanged, just copied.
		setStandaloneSegmentCount(newLevel2Address, 0);                    // standalone segments are now 0, of course.
		
		return newLevel2Address;
	}
	
	private static int compressEntry(
		final long sourceEntryAddress,
		final long targetEntryAddress,
		final long newIndexAddress
	)
	{
		/* (08.11.2024 TM)XXX: redundancy in already compressed checks?
		 * There seems to be redundant logic regarding checking whether a level1 segment is already compressed:
		 * In #compress, the method #ensureCompressedEntries is called, which ensures that all level1 segments are compressed.
		 * Afterwards, this method (#compressEntry) is called. But it checks again for the segment already being compressed.
		 * Is this a leftover from an improvment/bugfix or is there a detail I am missing?
		 * 
		 * Also, all that "Entry" terminology is confusing.
		 * Is this really one complete Level1 segment or is it something else?
		 */
		if(sourceEntryAddress <= 0L)
		{
			if(sourceEntryAddress == 0L)
			{
				// just one dummy entry indicating empty segment ("0")
				return setAll0BitsDummy(targetEntryAddress, newIndexAddress);
			}
			if(sourceEntryAddress == -1L)
			{
				// just one dummy entry indicating all-1-bits segment ("-1")
				return setAll1BitsDummy(targetEntryAddress, newIndexAddress);
			}
			
			// already compressed entry simply needs to be copied right away.
			return copyCompressedData(entryAddressUnmark(sourceEntryAddress), targetEntryAddress, newIndexAddress);
		}

		// note: the standalone level1 segment now contains the COMPRESSED data, with the header at offset 0!
		return handleInplaceCompressedStandaloneSegment(sourceEntryAddress, targetEntryAddress, newIndexAddress);
	}
	
	private static int handleInplaceCompressedStandaloneSegment(
		final long sourceEntryAddress,
		final long targetEntryAddress,
		final long newIndexAddress
	)
	{
		final long compressedDataAddress = getEntryNonTrivialDataAddress(sourceEntryAddress);
		if(BytePatternCompression.isSingleChunkEntry(compressedDataAddress))
		{
			// for a trivial (single chunk) segment, the first chunk is also the only chunk.
			if(BytePatternCompression.isFirstChunkAllZeroes(compressedDataAddress))
			{
				return setAll0BitsDummy(targetEntryAddress, newIndexAddress);
			}
			else if(BytePatternCompression.isFirstChunkAllOnes(compressedDataAddress))
			{
				return setAll1BitsDummy(targetEntryAddress, newIndexAddress);
			}
			// must be a single-chunk completely uncompressed level1 segment. Fall through to normal case.
		}

		// normal case: compressed data is not trivial and thus cannot be substituted by a trivial dummy entry.
		return copyCompressedData(sourceEntryAddress, targetEntryAddress, newIndexAddress);
	}
	
	private static int setAll0BitsDummy(final long targetEntryAddress, final long newIndexAddress)
	{
		XMemory.set_long(newIndexAddress, 0L);
		setEntryHeader(targetEntryAddress, ENTRY_HEADER_TYPE_ALL_ZEROES);
		
		return ENTRY_TRIVIAL_TOTAL_LENGTH;
	}
	
	private static int setAll1BitsDummy(final long targetEntryAddress, final long newIndexAddress)
	{
		XMemory.set_long(newIndexAddress, -1L);
		setEntryHeader(targetEntryAddress, ENTRY_HEADER_TYPE_ALL_ONES);
		
		return ENTRY_TRIVIAL_TOTAL_LENGTH;
	}
	
	private static int copyCompressedData(
		final long entrySourceAddress,
		final long entryTargetAddress,
		final long indexAddress
	)
	{
		final int entryTotalLength = getEntryNonTrivialTotalLength(entrySourceAddress);
		XMemory.copyRange(entrySourceAddress, entryTargetAddress, entryTotalLength);
		XMemory.set_long(indexAddress, entryAddressMark(entryTargetAddress));
		
		return entryTotalLength;
	}
	
	private static long decompressLevel1Segment(
		final long level2Address,
		final long level1EntryAddress,
		final int  level2Index
	)
	{
		XMath.positive(level1EntryAddress);
		
		final long level1UncompressedAddress     = allocateLevel1StandaloneSegmentBlank(level2Address, level2Index);
		final long level1UncompressedDataAddress = toLevel1UncompressedDataAddress(level1UncompressedAddress);
		final long entryDataAddress              = getEntryNonTrivialDataAddress(level1EntryAddress);
		
		BytePatternCompression.decompress(entryDataAddress, level1UncompressedDataAddress);
		
		return level1UncompressedAddress;
	}
		
	private static RuntimeException createInvalidLevel2SegmentTypeException(
		final int    level2SegmentType,
		final String details
	)
	{
		return new BitmapLevel2Exception(
			"Invalid level2 segment type: " + level2SegmentType + (details == null ? "" : " (" + details + ")")
			+ ". Valid compression type values are in range [1; " + COMPRESSION_TYPE_HIGHEST_USED + "]."
		);
	}
		
	private static int compressLevel1SegmentInPlace(final long level1UncompressedAddress)
	{
		final long targetAddress          = level1UncompressedAddress;
		final long targetDataStartAddress = toLevel1InPlaceCompressedDataAddress(targetAddress);
		final long sourceDataStartAddress = toLevel1UncompressedDataAddress(level1UncompressedAddress);

		final long entryDataLength = BytePatternCompression.compress(
			sourceDataStartAddress,
			targetDataStartAddress,
			BitmapLevel3.LEVEL_1_SEGMENT_LENGTH
		);
		
		final int entryTotalLength = calculateEntryTotalLength(
			targetDataStartAddress,
			targetDataStartAddress - targetAddress,
			entryDataLength
		);
		
		// (07.11.2023 TM)NOTE: old approach that relied on trivializedEntry forecast inside the compression logic
//		final int entryTotalLength = entryDataLength == 0L
//			? ENTRY_HEADER_BASE_LENGTH
//			: (int)(targetDataStartAddress - targetAddress + entryDataLength)
//		;
		
		// (26.10.2023 TM)TODO: calculate actual bit population (including special case of a trivialized entry!)
		setEntryNonTrivialHeader(targetAddress, entryTotalLength, 1);
		
		return entryTotalLength;
	}
	
	private static int calculateEntryTotalLength(
		final long targetDataStartAddress,
		final long entryBaseLength       ,
		final long entryDataLength
	)
	{
		/*
		 * Very nasty special case:
		 * An entry (compressed level1 segment) with only a single chunk that is trivial
		 * (meaning all-0-bits or all-1-bits) will later get "trivialized", meaning the entry
		 * will be dropped alltogether and replaced by a null pointer (0L) or a marker pseudo pointer (-1L).
		 * THAT means, that the dropped entry's data length may NOT be counted at all.
		 * Instead, only the entry header base length may be counted towards the total data length, since
		 * every entry gets at least a base header entry as an "anchor" hint/information about its trivial type
		 * and as a iteration entry.
		 * 
		 * The way to retroactively detect these cases with reasonable efficiency is:
		 * - Check for the entry's data length. If it is more than a single trivial chunk's length,
		 *   it CANNOT be a singular trivial chunk and hence must be a "normal" entry.
		 * - If the length is small enough, check for chunk count 1 and for trivial chunk type.
		 * - If not both are true despite the data length being so small, something must be wrong.
		 */
		if(entryDataLength > BytePatternCompression.singleTrivialChunkLength())
		{
			return (int)(entryBaseLength + entryDataLength);
		}
		
		if(BytePatternCompression.isSingleChunkEntry(targetDataStartAddress)
		&& BytePatternCompression.isFirstChunkTrivial(targetDataStartAddress)
		)
		{
			// only the base header for trivialized entries.
			return ENTRY_HEADER_BASE_LENGTH;
		}
		
		throw new BitmapLevel2Exception();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// Level 1 //
	////////////
	
	static void addToLevel1(final long level1UncompressedAddress, final int level1Id)
	{
		// note: state change marking happens in Level2 modification log array.
		
		// calculate the required bitmap value's address based on level1 base address and level1Id
		final long valueAddress = toBitmapValueAddress(level1UncompressedAddress, level1Id);
		
		// get the current long value, ensure the corresponding bit to be 1 and set the result back to memory.
		XMemory.set_long(valueAddress, setBit(valueAddress, level1Id));
	}
	
	private static long toBitmapValueAddress(final long level1UncompressedAddress, final int level1Id)
	{
		// must normalize to valueIndex first, then multiply by long byte length to get the long value's starting address.
		return toLevel1UncompressedDataAddress(level1UncompressedAddress)
			+ (level1Id >>> BitmapLevel3.VALUE_BIT_LENGTH_EXPONENT << BitmapLevel3.VALUE_BYTE_LENGTH_EXPONENT)
		;
	}
	
	private static long setBit(final long bitmapValueAddress, final int level1Id)
	{
		/*
		 * the long value at the calculated address is read.
		 * the bit index portion of the id are those bits "below" whole 64-bit packages, aka modulo or bitwise and.
		 * setting the bit corresponding to the id is simply by "or'ing in" a 1 shifted to the calculated bit index.
		 */
		return XMemory.get_long(bitmapValueAddress) | 1L<<(level1Id & BitmapLevel3.VALUE_ID_MASK);
	}
			
	static long getLevel1BitmapValue(final long level1Address, final int valueIndex)
	{
		if(level1Address <= 0L)
		{
			return level1Address < -1L
				? BytePatternCompression.getBitmapValueFromCompressedEntry(
					getEntryNonTrivialDataAddress(entryAddressUnmark(level1Address)),
					valueIndex
				)
				: level1Address // 0L and -1L can be returned directly
			;
		}
		
		// uncompressed (standalone) level1 segment. Value can be read directly from the calculated address.
		return XMemory.get_long(
			toLevel1UncompressedDataAddress(level1Address)
			+ (valueIndex<<BitmapLevel3.VALUE_BYTE_LENGTH_EXPONENT)
		);
	}
			
	// same as #add except for setting 0 bit instead of 1 bit.
	private static int removeFromLevel1(final long level1Address, final int level1Id)
	{
		// note: state change marking happens in Level2 modification log array.
		
		final long valueAddress = toBitmapValueAddress(level1Address, level1Id);
	
		// the long value at the calculated address
		final long value = XMemory.get_long(valueAddress);
	
		// the bit index portion of the id are those bits "below" whole 64-bit packages, aka modulo or bitwise and.
		final int bitIdxShift = level1Id & BitmapLevel3.VALUE_ID_MASK;
	
		if((value & 1L<<bitIdxShift) == 0L)
		{
			// bit is already 0, so no removal
			return REMOVAL_TYPE_NONE;
		}
		
		// clearing the bit corresponding to the id is "and'ing" the bitwise negative of 1 shifted to the bit index.
		final long modifiedBits = value & ~(1L<<bitIdxShift);
	
		// the modified long value is stored at its address in memory.
		XMemory.set_long(valueAddress, modifiedBits);
		
		return modifiedBits == 0L
			? REMOVAL_TYPE_EMPTY
			: REMOVAL_TYPE_NORMAL
		;
	}
	

	
	///////////////////////////////////////////////////////////////////////////
	// Persistence //
	////////////////
	
	
	static int getTotalLengthFromPersistentLength(final int persistentLength)
	{
		return TRANSIENT_BASE_LENGTH + persistentLength;
	}
	
	static int getPersistentLengthFromTotalLength(final int totalLength)
	{
		return totalLength - TRANSIENT_BASE_LENGTH;
	}
	
	static long toPersistentDataAddress(final long level2Address)
	{
		return level2Address + OFFSET_PERSISTENT_DATA;
	}
	
	static void initializeFromData(final long level2Address, final long totalLength)
	{
		final long entriesStartAddress    = toLevel2EntriesStartAddress(level2Address);
		final int  highestLevel2Index     = getHighestLevel2Index(level2Address);
		final int  boundIndex             = highestLevel2Index + 1;
		final long indexArrayAddressStart = getIndexArrayStartAddress(level2Address);
		final long indexArrayAddressBound = indexArrayAddressStart + boundIndex * POINTER_LENGTH;
		
		// must ensure all TRANSIENT(!) base length bytes are set to 0 since logic relies on it (see #processEntry)
		XMemory.fillMemory(level2Address, TRANSIENT_BASE_LENGTH, (byte)0);
		
		long currentEntryAddress = entriesStartAddress;
		for(long i = indexArrayAddressStart; i < indexArrayAddressBound; i += POINTER_LENGTH)
		{
			currentEntryAddress += processEntry(i, currentEntryAddress);
		}
		
		final long entriesTotalLength   = currentEntryAddress - entriesStartAddress;
		final int calculatedTotalLength = validateTotalLength(totalLength, BASE_LENGTH + entriesTotalLength);
		setTotalLength(level2Address, calculatedTotalLength);
		setStandaloneSegmentCount(level2Address, 0); // per definition for newly inialized level2 segments
	}
	
	private static int processEntry(
		final long indexArrayAddress   ,
		final long currentEntryAdddress
	)
	{
		final byte entryHeader;
		if((entryHeader = getEntryHeader(currentEntryAdddress)) < 0)
		{
			if(entryHeader == ENTRY_HEADER_TYPE_ALL_ZEROES)
			{
				// index array position stays 0L
				return ENTRY_TRIVIAL_TOTAL_LENGTH;
			}
			else if(entryHeader == ENTRY_HEADER_TYPE_ALL_ONES)
			{
				XMemory.set_long(indexArrayAddress, -1L);
				return ENTRY_TRIVIAL_TOTAL_LENGTH;
			}
			else
			{
				throw new BitmapLevel2Exception("Unknown trivial entry header: " + entryHeader);
			}
		}
		
		XMemory.set_long(indexArrayAddress, entryAddressMark(currentEntryAdddress));
		return getEntryNonTrivialTotalLength(currentEntryAdddress);
	}

	private static int validateTotalLength(final long passedTotalLength, final long calculatedTotalLength)
	{
		if(calculatedTotalLength == passedTotalLength)
		{
			return XTypes.to_int(passedTotalLength);
		}

		throw new BitmapLevel2Exception("TotalLength mismatch: " + calculatedTotalLength + " != " + passedTotalLength);
	}
	
	@Override
	protected void storeChangedChildren(final Storer storer)
	{
		// BitmapLevel2 may never be marked as having children changed since is it a leaf instance. Not perfectly clean design.
		throw new UnsupportedOperationException();
	}
	
	@Override
	protected void clearChildrenStateChangeMarkers()
	{
		// no-op since there are no state-change-marked children.
	}
	
	static void assembleBitmapValue(final VarString vs, final int index, final long value)
	{
		vs.lf().add("BitmapValue #" + index).tab();
		assembleBitmapValuePadded(vs, value);
		vs.tab().add(value);
	}
	
	static void assembleBitmapValuePadded(
		final VarString vs   ,
		final long      value
	)
	{
		// 10 instead of 8 or 4 because debug strings are meant for human readability, not memory structure.
		assembleBitmapValuePadded(vs, value, 10, false);
	}
	
	static void assembleBitmapValuePadded(
		final VarString vs         ,
		final long      value      ,
		final int       groupLength,
		final boolean   leftToRight
	)
	{
		int currentGroupLength = groupLength == 0 ? Long.SIZE : Long.SIZE % groupLength;
		if(currentGroupLength == 0)
		{
			// damn special cases
			currentGroupLength = groupLength;
		}
		final long highestBit = 1L << Long.SIZE - 1;
		int bitCount = 0;
		
		if(leftToRight)
		{
			for(long bit = 1; bitCount++ < Long.SIZE; bit <<= 1)
			{
				vs.add((value & bit) == 0L ? '0' : '1');
				if(--currentGroupLength == 0)
				{
					vs.blank();
					currentGroupLength = groupLength;
				}
			}
			vs.deleteLast();
		}
		else
		{
			for(long bit = highestBit; bitCount++ < Long.SIZE; bit >>>= 1)
			{
				vs.add((value & bit) == 0L ? '0' : '1');
				if(--currentGroupLength == 0)
				{
					vs.blank();
					currentGroupLength = groupLength;
				}
			}
			vs.deleteLast();
		}
	}
		
	public final BaseValueView queryBaseValues()
	{
		return new BaseValueView(
			getLevel2SegmentType(this.level2Address),
			getSegmentCount(this.level2Address),
			getTotalLength(this.level2Address)
		);
	}
		
	public final <C extends Consumer<? super Level1EntryView>> C queryLevel1Entries(final C acceptor)
	{
		this.ensureCompressed();
		final long level2Address = this.level2Address;
		
		final int highestLevel2Index = getHighestLevel2Index(level2Address);
		final int level2IndexBound   = highestLevel2Index + 1;
		
		for(int i = 0; i < level2IndexBound; i++)
		{
			final long            level1Address = getLevel1SegmentAddress(level2Address, i);
			final Level1EntryView level1Entry;
			if(level1Address > 0L)
			{
				throw new BitmapLevel2Exception(); // may never happen in compressed state ensured by call above.
			}
			else if(level1Address < -1L)
			{
				final long actualLevel1Address = entryAddressUnmark(level1Address);
				final int  entryByteLength     = getEntryNonTrivialTotalLength(actualLevel1Address);
				final long entryDataAddress    = getEntryNonTrivialDataAddress(actualLevel1Address);
				final int  chunkCount          = BytePatternCompression.getLevel1EntryChunkCount(entryDataAddress);
				level1Entry = new Level1EntryView(i, entryByteLength, chunkCount);
			}
			else
			{
				level1Entry = new Level1EntryView(i, 0, 0);
			}
			acceptor.accept(level1Entry);
		}
		
		return acceptor;
	}
			
	/**
	 * This is just a "view" class, used for querying data in an compression-independant format.
	 * It is not used anywhere in logic executing the actual functionality of the index.
	 *
	 */
	public static final class BaseValueView
	{
		private final int type        ;
		private final int segmentCount;
		private final int totalLength ;
		
		BaseValueView(final int type, final int segmentCount, final int totalLength)
		{
			super();
			this.type         = type        ;
			this.segmentCount = segmentCount;
			this.totalLength  = totalLength ;
		}
		
		public int type()
		{
			return this.type;
		}
		
		public int totalLength()
		{
			return this.totalLength;
		}
		
		public int segmentCount()
		{
			return this.segmentCount;
		}
		
	}
	
	/**
	 * This is just a "view" class, used for querying data in an compression-independent format.
	 * It is not used anywhere in logic executing the actual functionality of the index.
	 *
	 */
	public static final class Level1EntryView
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final int index     ;
		private final int byteLength;
		private final int chunkCount;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Level1EntryView(final int index, final int byteLength, final int chunkCount)
		{
			super();
			this.index      = index     ;
			this.byteLength = byteLength;
			this.chunkCount = chunkCount;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		public int index()
		{
			return this.index;
		}
		
		public int getByteLength()
		{
			return this.byteLength;
		}
		
		public int getChunkCount()
		{
			return this.chunkCount;
		}
		
	}
	
}
