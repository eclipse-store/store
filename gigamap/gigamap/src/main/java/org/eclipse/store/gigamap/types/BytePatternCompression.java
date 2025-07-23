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
import org.eclipse.serializer.memory.XMemory;

public class BytePatternCompression
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////
	
	private static final long
		MASK_UPPER_HALF = 0xFFFF_FFFF_0000_0000L,
		MASK_LOWER_HALF = 0x0000_0000_FFFF_FFFFL,
	
		MASK_BYTES1_0 = 0x0000_0000_0000_00FFL,
		MASK_BYTES1_1 = 0x0000_0000_0000_FF00L,
		MASK_BYTES1_2 = 0x0000_0000_00FF_0000L,
		MASK_BYTES1_3 = 0x0000_0000_FF00_0000L,
		MASK_BYTES1_4 = 0x0000_00FF_0000_0000L,
		MASK_BYTES1_5 = 0x0000_FF00_0000_0000L,
		MASK_BYTES1_6 = 0x00FF_0000_0000_0000L,
		MASK_BYTES1_7 = 0xFF00_0000_0000_0000L
	;
	
	private static final int
		MASK_INT_BYTE1_0 = 0x0000_00FF,
		MASK_INT_BYTE1_1 = 0x0000_FF00,
		MASK_INT_BYTE1_2 = 0x00FF_0000,
		MASK_INT_BYTE1_3 = 0xFF00_0000,
				
		CHUNK_HEAD_ONES_BIT_MASK = 0b1000_0000,
		CHUNK_HEAD_ONES_NOT_MASK = ~CHUNK_HEAD_ONES_BIT_MASK
	;
		
	// chunk type has 7 bits available, resulting in 128 different compressible cases (values 0 to 127)
	private static final byte
		CHUNK_TYPE_TRIVIAL_SIZED  =   0, // [    0   ] 01 case  (header extension byte defining 13-64 long values)
		CHUNK_TYPE_TRIVIAL_SIZEDf = (byte)(CHUNK_TYPE_TRIVIAL_SIZED | CHUNK_HEAD_ONES_BIT_MASK),
		CHUNK_TYPE_UNCOMPD_SIZED  =   1  // [    1   ] 01 case  (header extension byte defining 13-64 long values)
	;
	private static final int
		CHUNK_TYPE_TRIVIAL_FIXED  =   2, // [  2;  17] 16 cases (01 to 16 long values)
		CHUNK_TYPE_UNCOMPD_FIXED  =  18, // [ 18;  33] 16 cases (01 to 16 long values)
		CHUNK_TYPE_EXTENSION      =  34, // currently not used, but reserved for signaling a header "extension".
		// start of single value types. Do not change this value without fully understanding the logic!
		CHUNK_TYPE_BIT_ROW        =  35  // sub type to efficiently represent a single long with a "row" of 1-bits.
	;
	private static final byte
		CHUNK_TYPE_BYTES_1_POS_0  =  36, // [ 36;  39] 04 cases (4 cases of 1 byte located in the lower half)
		CHUNK_TYPE_BYTES_1_POS_4  =  40  // [ 40;  43] 04 cases (4 cases of 1 byte located in the upper half)
	;
	private static final int
		CHUNK_TYPE_BYTES_2_SPLIT  =  44, // [ 44;  59] 16 cases (4 * 4 cases of 2 bytes split accross both halves)
		CHUNK_TYPE_BYTES_2_LOWER  =  60, // [ 60;  65] 06 cases (6 cases of 2 bytes distributed in the lower half)
		CHUNK_TYPE_BYTES_2_UPPER  =  66, // [ 66;  71] 06 cases (6 cases of 2 bytes distributed in the upper half)
		
		CHUNK_TYPE_BYTES_3_LOWER  =  72, // [ 72;  74] 04 cases (4 cases of 3 bytes distributed in the lower half)
		CHUNK_TYPE_BYTES_3_UPPER  =  76, // [ 74;  79] 04 cases (4 cases of 3 bytes distributed in the upper half)
		CHUNK_TYPE_BYTES_3_2AND1  =  80, // [ 80; 103] 24 cases (24 cases of 3 bytes split 2&1 accross both halves)
		CHUNK_TYPE_BYTES_3_1AND2  = 104, // [104; 127] 24 cases (24 cases of 3 bytes split 1&2 accross both halves)
		CHUNK_TYPE_BOUND          = 128  // EXCLUSIVE bounding value for valid chunk types (values 0 to 127).
	;
	
	private static final int
		CHUNK_TYPE_BYTES_START_FIXED_SIZE  = CHUNK_TYPE_TRIVIAL_FIXED,
		CHUNK_TYPE_BYTES_START_SINGLE_VAL  = CHUNK_TYPE_BIT_ROW,
		CHUNK_TYPE_BYTES_START_1           = CHUNK_TYPE_BYTES_1_POS_0,
		CHUNK_TYPE_BYTES_START_2           = CHUNK_TYPE_BYTES_2_SPLIT,
		CHUNK_TYPE_BYTES_START_3           = CHUNK_TYPE_BYTES_3_LOWER,
		
		CHUNK_TYPE_BOUND_SIZED             = CHUNK_TYPE_BYTES_START_FIXED_SIZE,
		CHUNK_TYPE_BOUND_FIXED_TRIVIAL     = CHUNK_TYPE_UNCOMPD_FIXED,
		CHUNK_TYPE_BOUND_FIXED             = CHUNK_TYPE_EXTENSION,
		
		CHUNK_TYPE_TRIVIAL_FIXED_SIZE_BASE = CHUNK_TYPE_TRIVIAL_FIXED - 1,
		CHUNK_TYPE_UNCOMPD_FIXED_SIZE_BASE = CHUNK_TYPE_UNCOMPD_FIXED - 1,
		CHUNK_TYPE_TRIVIAL_FIXABLE_SIZE    = CHUNK_TYPE_UNCOMPD_FIXED - CHUNK_TYPE_TRIVIAL_FIXED,
		CHUNK_TYPE_UNCOMPD_FIXABLE_SIZE    = CHUNK_TYPE_EXTENSION     - CHUNK_TYPE_UNCOMPD_FIXED
	;
	
	/*
	 * bit count is stored as [0; 63] representing [1; 64] bits in a row.
	 * Anything above and below is an error (2 bits reserved).
	 */
	private static final int
		BIT_ROW_DATA_VALUE_START =  0,
		BIT_ROW_DATA_VALUE_BOUND = 64
	;
	
	private static final byte
		ZERO =  0, // 0000_0000
		ONE  = -1  // 1111_1111
	;
	
	private static final int
		COMPRESSION_OFFSET_DATA_START   = 0,
		COMPRESSION_OFFSET_CHUNKS_COUNT = COMPRESSION_OFFSET_DATA_START,
		COMPRESSION_OFFSET_CHUNKS_START = COMPRESSION_OFFSET_CHUNKS_COUNT + Byte.BYTES,
		ENTRY_COMPRESSION_BASE_LENGTH   = COMPRESSION_OFFSET_CHUNKS_START,
		
		// chunk of one entry using simple range compression. "Head" is type and length, so data can be optional.

		CHUNK_HEAD_LENGTH_STANDARD = Byte.BYTES                             , // standard header has 1 byte  of length.
		CHUNK_HEAD_LENGTH_W_EXTNSN = CHUNK_HEAD_LENGTH_STANDARD + Byte.BYTES, // extended header has 2 bytes of length.
		
		CHUNK_OFFSET_HEAD = 0                                             ,
		CHUNK_OFFSET_EXTN = CHUNK_OFFSET_HEAD + CHUNK_HEAD_LENGTH_STANDARD,
		
		SINGLE_TRIVIAL_CHUNK_LENGTH = ENTRY_COMPRESSION_BASE_LENGTH + CHUNK_HEAD_LENGTH_W_EXTNSN
	;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// basic getters, setters, etc //
	////////////////////////////////
	
	static int entryCompressionBaseLength()
	{
		// currently only the chunks count (1 byte)
		return ENTRY_COMPRESSION_BASE_LENGTH;
	}
	
	static int chunkHeadMaximumLength()
	{
		// to prevent overwrite errors during inplace compression!
		return CHUNK_HEAD_LENGTH_W_EXTNSN;
	}
	
	static int singleTrivialChunkLength()
	{
		return SINGLE_TRIVIAL_CHUNK_LENGTH;
	}
	
	static boolean isSingleChunkEntry(final long compressedDataAddress)
	{
		return getChunksCount(compressedDataAddress) == 1;
	}
	
	static boolean isFirstChunkTrivial(final long compressedDataAddress)
	{
		return isTrivialChunkType(getChunkHead(toChunksStartAddress(compressedDataAddress)));
	}
	
	private static boolean isTrivialChunkType(final int chunkHead)
	{
		final int chunkType = toChunkType(chunkHead);
		
		return chunkType == CHUNK_TYPE_TRIVIAL_SIZED
			|| chunkType >= CHUNK_TYPE_TRIVIAL_FIXED && chunkType < CHUNK_TYPE_UNCOMPD_FIXED
		;
	}
	
	static boolean isFirstChunkAllZeroes(final long compressedDataAddress)
	{
		if(isFlippedBits(toChunksStartAddress(compressedDataAddress)))
		{
			return false;
		}

		return isTrivialChunkType(getChunkHead(toChunksStartAddress(compressedDataAddress)));
	}
	
	static boolean isFirstChunkAllOnes(final long compressedDataAddress)
	{
		if(!isFlippedBits(toChunksStartAddress(compressedDataAddress)))
		{
			return false;
		}

		return isTrivialChunkType(getChunkHead(toChunksStartAddress(compressedDataAddress)));
	}
	
	private static int getChunksCount(final long compressedDataAddress)
	{
		return Byte.toUnsignedInt(
			XMemory.get_byte(compressedDataAddress + COMPRESSION_OFFSET_CHUNKS_COUNT)
		);
	}
	
	static int getLevel1EntryChunkCount(final long compressedDataAddress)
	{
		return getChunksCount(compressedDataAddress);
	}
	
	private static void setChunksCount(final long compressionTargetAddress, final int chunksCount)
	{
		XMemory.set_byte(compressionTargetAddress + COMPRESSION_OFFSET_CHUNKS_COUNT, (byte)chunksCount);
	}
	
	private static long toChunksStartAddress(final long compressedDataAddress)
	{
		return compressedDataAddress + COMPRESSION_OFFSET_CHUNKS_START;
	}
	
	private static long validateLevel1SegmentLength(final long length)
	{
		if((length & 0b111L) == 0)
		{
			// multiple of 8 (Long.BYTES), so it's valid. Even if it's only a single long value, theoretically
			return length;
		}
		
		throw new IllegalArgumentException("Invalid level 1 segment length: " + length);
	}
				
	
	
	///////////////////////////////////////////////////////////////////////////
	// compression //
	////////////////
	
	public static long compress(
		final long sourceDataAddress ,
		final long targetStartAddress,
		final long length
	)
	{
		final long sourceAddressBound = sourceDataAddress + validateLevel1SegmentLength(length);

		int  chunksCount = 0;
		long lastTrivialValue = Long.MIN_VALUE;
		long currentTargetAddress = toChunksStartAddress(targetStartAddress);
		long pendingTrivialChunkStartAddress = 0L;
		long pendingUncompressedChunkStartAddress = 0L;
		for(long a = sourceDataAddress; a < sourceAddressBound; a += Long.BYTES)
		{
			final long currentValue;
			if((currentValue = XMemory.get_long(a)) == lastTrivialValue)
			{
				/*
				 * To exclude the very rare case of a value being Long.MIN_VALUE and causing a false positive,
				 * it must be checked whether there actually is a pending trivial chunk.
				 */
				if(pendingTrivialChunkStartAddress != 0L)
				{
					continue;
				}
			}
			if(pendingTrivialChunkStartAddress != 0)
			{
				currentTargetAddress = completeTrivialChunk(pendingTrivialChunkStartAddress, a, currentTargetAddress);
				chunksCount++;
				lastTrivialValue = Long.MIN_VALUE;
				pendingTrivialChunkStartAddress = 0;
			}
			if(currentValue == 0L || currentValue == -1L)
			{
				if(pendingUncompressedChunkStartAddress != 0L)
				{
					currentTargetAddress = completeUncompressedChunk(pendingUncompressedChunkStartAddress, a, currentTargetAddress);
					pendingUncompressedChunkStartAddress = 0L;
					chunksCount++;
				}
				lastTrivialValue = currentValue;
				pendingTrivialChunkStartAddress = a;
				continue;
			}

			final long newTargetAddress = compressValue(a, currentTargetAddress, pendingUncompressedChunkStartAddress);
			if(newTargetAddress == 0L)
			{
				if(pendingUncompressedChunkStartAddress == 0L)
				{
					pendingUncompressedChunkStartAddress = a;
				}
				continue;
			}
			currentTargetAddress = newTargetAddress;
			if(pendingUncompressedChunkStartAddress != 0L)
			{
				pendingUncompressedChunkStartAddress = 0L;
				chunksCount++;
			}
			chunksCount++;
		}
		
		if(pendingTrivialChunkStartAddress != 0)
		{
			currentTargetAddress = completeTrivialChunk(pendingTrivialChunkStartAddress, sourceAddressBound, currentTargetAddress);
			chunksCount++;
		}
		if(pendingUncompressedChunkStartAddress != 0L)
		{
			currentTargetAddress = completeUncompressedChunk(pendingUncompressedChunkStartAddress, sourceAddressBound, currentTargetAddress);
			chunksCount++;
		}
		
		setChunksCount(targetStartAddress, chunksCount);
		
		return currentTargetAddress - targetStartAddress;
	}
	
	private static long completeTrivialChunk(
		final long addressStart ,
		final long addressBound ,
		final long targetAddress
	)
	{
		final int size = (int)(addressBound - addressStart) >>> 3;
		if(XMemory.get_long(addressStart) == 0L)
		{
			if(size < CHUNK_TYPE_TRIVIAL_FIXABLE_SIZE)
			{
				return completeChunkHead(targetAddress,	CHUNK_TYPE_TRIVIAL_FIXED_SIZE_BASE + size);
			}
			return completeChunkHead(targetAddress,	CHUNK_TYPE_TRIVIAL_SIZED, size);
		}
		if(XMemory.get_long(addressStart) == -1L)
		{
			if(size < CHUNK_TYPE_TRIVIAL_FIXABLE_SIZE)
			{
				return completeChunkHead(targetAddress,	markFlippedBits(CHUNK_TYPE_TRIVIAL_FIXED_SIZE_BASE + size));
			}
			return completeChunkHead(targetAddress,	CHUNK_TYPE_TRIVIAL_SIZEDf, size);
		}
		
		throw new BitmapLevel2Exception();
	}
	
	private static long completeUncompressedChunk(final long addressStart, final long addressBound, final long targetAddress)
	{
		final int size = (int)(addressBound - addressStart) >>> 3;
		final long targetDataAddress;
		if(size < CHUNK_TYPE_UNCOMPD_FIXABLE_SIZE)
		{
			targetDataAddress = completeChunkHead(targetAddress, CHUNK_TYPE_UNCOMPD_FIXED_SIZE_BASE + size);
		}
		else
		{
			targetDataAddress = completeChunkHead(targetAddress, CHUNK_TYPE_UNCOMPD_SIZED, size);
		}
		XMemory.copyRange(addressStart, targetDataAddress, addressBound - addressStart);
		
		return targetDataAddress + addressBound - addressStart;
	}
				
	static long compressValue(
		final long valueAddress            ,
		final long targetAddress           ,
		final long pendingChunkStartAddress
	)
	{
		int emptyByteCount = 0, fullByteCount = 0;
		
		final long value = XMemory.get_long(valueAddress);
		if((value & MASK_BYTES1_0) == 0L)
			emptyByteCount++;
		else if((value & MASK_BYTES1_0) == MASK_BYTES1_0)
			fullByteCount++;
		if((value & MASK_BYTES1_1) == 0L)
			emptyByteCount++;
		else if((value & MASK_BYTES1_1) == MASK_BYTES1_1)
			fullByteCount++;
		if((value & MASK_BYTES1_2) == 0L)
			emptyByteCount++;
		else if((value & MASK_BYTES1_2) == MASK_BYTES1_2)
			fullByteCount++;
		if((value & MASK_BYTES1_3) == 0L)
			emptyByteCount++;
		else if((value & MASK_BYTES1_3) == MASK_BYTES1_3)
			fullByteCount++;
		if((value & MASK_BYTES1_4) == 0L)
			emptyByteCount++;
		else if((value & MASK_BYTES1_4) == MASK_BYTES1_4)
			fullByteCount++;
		if((value & MASK_BYTES1_5) == 0L)
			emptyByteCount++;
		else if((value & MASK_BYTES1_5) == MASK_BYTES1_5)
			fullByteCount++;
		if((value & MASK_BYTES1_6) == 0L)
			emptyByteCount++;
		else if((value & MASK_BYTES1_6) == MASK_BYTES1_6)
			fullByteCount++;
		if((value & MASK_BYTES1_7) == 0L)
			emptyByteCount++;
		else if((value & MASK_BYTES1_7) == MASK_BYTES1_7)
			fullByteCount++;
		
		
		/*
		 * 7 or 8 "pure" bytes can be a bit row pattern.
		 * Note: bit row pattern has a flip mark strategy different to the rest:
		 * It's not about if there are more full or empty bytes,
		 * but if the row starts at the lowest or the highest bit.
		 */
		if(emptyByteCount + fullByteCount >= 7)
		{
			final long newTargetAddress = tryBitRowCompression(
				valueAddress,
				value,
				emptyByteCount,
				fullByteCount,
				targetAddress,
				pendingChunkStartAddress
			);
			if(newTargetAddress != 0L)
			{
				return newTargetAddress;
			}
			// not a bit row, so fall through to normal logic
		}
		
		if(emptyByteCount < fullByteCount)
		{
			return compressValue(targetAddress, valueAddress, ~value, true, Long.BYTES - fullByteCount, pendingChunkStartAddress);
		}
		return compressValue(targetAddress, valueAddress, value, false, Long.BYTES - emptyByteCount, pendingChunkStartAddress);
	}
	
	private static long tryBitRowCompression(
		final long valueAddress            ,
		final long value                   ,
		final int  emptyByteCount          ,
		final int  fullByteCount           ,
		final long targetAddress           ,
		final long pendingChunkStartAddress
	)
	{
		final int bitCount = determineBitRowBitCount2(value, fullByteCount);
		if(bitCount == 0)
		{
			return 0L;
		}
		
		// must (sadly) handle pending chunks in here redundantly because of the different flip mark logic
		final long actualTargetAddress = ensureCompletedPendingChunk(pendingChunkStartAddress, valueAddress, targetAddress);
		if(bitCount > 0)
		{
			handleBitRow(actualTargetAddress, bitCount);
		}
		else if(bitCount < 0)
		{
			handleBitRow(actualTargetAddress, -bitCount);
			setChunkHead(actualTargetAddress, markFlippedBits(getChunkHead(actualTargetAddress)));
		}
		
		// bit row has 1 chunk head byte and always only 1 byte of data. See logic in #handleBitRow
		return actualTargetAddress + 2;
	}
	
	private static int determineBitRowBitCount2(final long value, final int fullByteCount)
	{
		final int bitCount = determineBitRowBitCount(value, fullByteCount);

		return bitCount != 0
			? bitCount
			: -determineBitRowBitCount(~value, 7 - fullByteCount)
		;
	}
	
	private static int determineBitRowBitCount(final long v, final int fullByteCount)
	{
		/*
		 * Note: while case 0 and 8 can normally never occur because those are trivial cases
		 * that have to be handled by a prior logic, case 0 CAN occur if a value with 7 full bytes
		 * gets flipped and reevaluated with (7 - 7 = 0) full bytes.
		 */
		switch(fullByteCount)
		{
			case  0: return determineBitRowBitCount(v, 0x00_______________00L, 0xFFFF_FFFF_FFFF_FF00L,  0);
			case  1: return determineBitRowBitCount(v, 0x00_____________00FFL, 0xFFFF_FFFF_FFFF_0000L,  8);
			case  2: return determineBitRowBitCount(v, 0x00__________00_FFFFL, 0xFFFF_FFFF_FF00_0000L, 16);
			case  3: return determineBitRowBitCount(v, 0x00________00FF_FFFFL, 0xFFFF_FFFF_0000_0000L, 24);
			case  4: return determineBitRowBitCount(v, 0x00_____00_FFFF_FFFFL, 0xFFFF_FF00_0000_0000L, 32);
			case  5: return determineBitRowBitCount(v, 0x00___00FF_FFFF_FFFFL, 0xFFFF_0000_0000_0000L, 40);
			case  6: return determineBitRowBitCount(v, 0x0000_FFFF_FFFF_FFFFL, 0xFF00_0000_0000_0000L, 48);
			case  7: return determineBitRowBitCount(v, 0x00FF_FFFF_FFFF_FFFFL, 0x0000_0000_0000_0000L, 56);
			default:
				throw new BitmapLevel2Exception("Invalid full byte count: " + fullByteCount);
		}
	}
	
	private static int determineBitRowBitCount(
		final long value         ,
		final long fullBytesMask ,
		final long emptyBytesMask,
		final int  rowBaseLength
	)
	{
		if((value & fullBytesMask) == fullBytesMask)
		{
			if((value & emptyBytesMask) == 0L)
			{
				final int middleByteBitCount = bitRowMiddleByteBitCount((int)(value >>> rowBaseLength));
				if(middleByteBitCount >= 0)
				{
					return rowBaseLength + middleByteBitCount;
				}
			}
		}
		return 0;
	}
	
			
	private static long compressValue(
		final long    targetAddress           ,
		final long    valueAddress            ,
		final long    value                   ,
		final boolean markFlipped             ,
		final int     dirtyByteCount          ,
		final long    pendingChunkStartAddress
	)
	{
		if(dirtyByteCount >= 4)
		{
			return 0L;
		}
		
		final long actualTargetAddress = ensureCompletedPendingChunk(pendingChunkStartAddress, valueAddress, targetAddress);
		switch(dirtyByteCount)
		{
			case  1: handleDirtyBytes1(actualTargetAddress, value); break;
			case  2: handleDirtyBytes2(actualTargetAddress, value); break;
			case  3: handleDirtyBytes3(actualTargetAddress, value); break;
			default:
				throw new BitmapLevel2Exception("Invalid dirty byte count: " + dirtyByteCount);
		}
		
		if(markFlipped)
		{
			setChunkHead(actualTargetAddress, markFlippedBits(getChunkHead(actualTargetAddress)));
		}
		
		// advance actual target address by the chunk head byte (1) and the amount of dirty bytes.
		return actualTargetAddress + 1 + dirtyByteCount;
	}
	
	private static long ensureCompletedPendingChunk(
		final long pendingChunkStartAddress,
		final long valueAddress            ,
		final long targetAddress
	)
	{
		return pendingChunkStartAddress != 0L
			? completeUncompressedChunk(pendingChunkStartAddress, valueAddress, targetAddress)
			: targetAddress
		;
	}
	
				
	private static void handleBitRow(
		final long targetAddress ,
		final int  bitCount
	)
	{
		setChunkHead(targetAddress, CHUNK_TYPE_BIT_ROW);
		writeBitRowBitCount(targetAddress + 1, bitCount);
	}
	
	private static void writeBitRowBitCount(final long targetAddress, final int bitCount)
	{
		// bit row bit count is [1; 64], but 6 bit can only represent [0; 63], so 1 is subtracted.
		setByte(targetAddress, bitCount - 1);
	}
	
				
	private static void handleDirtyBytes1(final long targetAddress, final long value)
	{
		// no bitrow check here since a single byte is handled efficiently, anyway
		if((value & MASK_LOWER_HALF) != 0L)
		{
			handleDirtyBytes1Int(targetAddress, CHUNK_TYPE_BYTES_1_POS_0, (int)(value & MASK_LOWER_HALF));
		}
		else
		{
			handleDirtyBytes1Int(targetAddress, CHUNK_TYPE_BYTES_1_POS_4, (int)(value >>> Integer.SIZE));
		}
	}
	
	private static void handleDirtyBytes1Int(final long targetAddress, final int chunkBaseType, final int value)
	{
		setChunkHead(targetAddress, chunkBaseType + handleDirtyBytes1Int(targetAddress + 1, value));
	}
	
	private static int handleDirtyBytes1Int(final long targetAddress, final int value)
	{
		if((value & MASK_INT_BYTE1_0) != 0L)
		{
			setByte(targetAddress, value);
			return 0;
		}
		else if((value & MASK_INT_BYTE1_1) != 0L)
		{
			setByte(targetAddress, value >>>  8);
			return 1;
		}
		else if((value & MASK_INT_BYTE1_2) != 0L)
		{
			setByte(targetAddress, value >>> 16);
			return 2;
		}
		else if((value & MASK_INT_BYTE1_3) != 0L)
		{
			setByte(targetAddress, value >>> 24);
			return 3;
		}
		
		throw new BitmapLevel2Exception();
	}
		
	private static int handleDirtyBytes2Int(final long targetAddress, final int value)
	{
		if((value & MASK_INT_BYTE1_0) != 0)
		{
			if((value & MASK_INT_BYTE1_1) != 0)
			{
				set2Bytes(targetAddress, value >>> 8, value & 0xFF);
				return 0;
			}
			else if((value & MASK_INT_BYTE1_2) != 0)
			{
				set2Bytes(targetAddress, value >>> 16, value & 0xFF);
				return 1;
			}
			else if((value & MASK_INT_BYTE1_3) != 0)
			{
				set2Bytes(targetAddress, value >>> 24, value & 0xFF);
				return 2;
			}
		}
		else if((value & MASK_INT_BYTE1_1) != 0)
		{
			if((value & MASK_INT_BYTE1_2) != 0)
			{
				set2Bytes(targetAddress, value >>> 16, value >>> 8 & 0xFF);
				return 3;
			}
			else if((value & MASK_INT_BYTE1_3) != 0)
			{
				set2Bytes(targetAddress, value >>> 24, value >>> 8 & 0xFF);
				return 4;
			}
		}
		else if((value & MASK_INT_BYTE1_2) != 0 && (value & MASK_INT_BYTE1_3) != 0)
		{
			set2Bytes(targetAddress, value >>> 24, value >>> 16 & 0xFF);
			return 5;
		}
		
		return -1;
	}
		
	private static int handleDirtyBytes3Int(final long targetAddress, final int value)
	{
		if((value & MASK_INT_BYTE1_0) == 0L)
		{
			set3Bytes(targetAddress, value >>> 24, value >>> 16 & 0xFF, value >>> 8 & 0xFF);
			return 0;
		}
		else if((value & MASK_INT_BYTE1_1) == 0L)
		{
			set3Bytes(targetAddress, value >>> 24, value >>> 16 & 0xFF, value);
			return 1;
		}
		else if((value & MASK_INT_BYTE1_2) == 0L)
		{
			set3Bytes(targetAddress, value >>> 24, value >>> 8 & 0xFF, value);
			return 2;
		}
		else if((value & MASK_INT_BYTE1_3) == 0L)
		{
			set3Bytes(targetAddress, value >>> 16 & 0xFF, value >>> 8 & 0xFF, value);
			return 3;
		}
		
		throw new BitmapLevel2Exception();
	}
	
	private static int bitRowMiddleByteBitCount(final int lastByte)
	{
		/*
		 * The 0 case is a bit tricky to understand:
		 * The prior logic checked for the sum of full and empty bytes to be 7 or 8.
		 * In the case of 7, the "middle" byte will not be 0.
		 * The "middle" byte being 0 happens if the sum of full and empty bytes is 8 (i.e. it's the remaining empty byte)
		 */
		switch(lastByte)
		{
			case 0b0000_0000 : return 0; // valid case: the bit row happens to end with a full byte.
			case 0b0000_0001 : return 1;
			case 0b0000_0011 : return 2;
			case 0b0000_0111 : return 3;
			case 0b0000_1111 : return 4;
			case 0b0001_1111 : return 5;
			case 0b0011_1111 : return 6;
			case 0b0111_1111 : return 7;
			case 0b1111_1111 : return 8;
			default          : return -1; // not a bit row pattern
		}
	}
			
	private static void handleDirtyBytes2(final long targetAddress, final long value)
	{
		final int dirtyBytes2Code;
		if((value & MASK_LOWER_HALF) != 0L)
		{
			if((value & MASK_UPPER_HALF) != 0L)
			{
				// each half has exactly one dirty byte
				final int upperHalfByteCode = handleDirtyBytes1Int(targetAddress + 1, (int)(value >>> Integer.SIZE));
				final int lowerHalfByteCode = handleDirtyBytes1Int(targetAddress + 2, (int)(value & MASK_LOWER_HALF));
				dirtyBytes2Code = CHUNK_TYPE_BYTES_2_SPLIT + upperHalfByteCode * 4 + lowerHalfByteCode;
			}
			else
			{
				// both dirty bytes are in the lower half
				final int lowerHalfByteCode = handleDirtyBytes2Int(targetAddress + 1, (int)(value & MASK_LOWER_HALF));
				dirtyBytes2Code = CHUNK_TYPE_BYTES_2_LOWER + lowerHalfByteCode;
			}
		}
		else
		{
			// both dirty bytes are in the upper half
			final int upperHalfByteCode = handleDirtyBytes2Int(targetAddress + 1, (int)(value >>> Integer.SIZE));
			dirtyBytes2Code = CHUNK_TYPE_BYTES_2_UPPER + upperHalfByteCode;
		}
		setChunkHead(targetAddress, dirtyBytes2Code);
	}
	
	private static void handleDirtyBytes3(final long targetAddress, final long value)
	{
		final int dirtyBytes3Code;
		if((value & MASK_UPPER_HALF) == 0L) // 62 + 4 cases (65)
		{
			dirtyBytes3Code = CHUNK_TYPE_BYTES_3_LOWER + handleDirtyBytes3Int(targetAddress + 1, (int)(value & MASK_LOWER_HALF));
		}
		else if((value & MASK_LOWER_HALF) == 0L) // 66 + 4 cases (69)
		{
			dirtyBytes3Code = CHUNK_TYPE_BYTES_3_UPPER + handleDirtyBytes3Int(targetAddress + 1, (int)(value >>> Integer.SIZE));
		}
		else
		{
			final int upperHalfByteCode = handleDirtyBytes2Int(targetAddress + 1, (int)(value >>> Integer.SIZE));
			if(upperHalfByteCode >= 0) // 70 + 6*4=24 cases (93)
			{
				final int lowerHalfByteCode = handleDirtyBytes1Int(targetAddress + 3, (int)(value & MASK_LOWER_HALF));
				dirtyBytes3Code = CHUNK_TYPE_BYTES_3_2AND1 + upperHalfByteCode * 4 + lowerHalfByteCode;
			}
			else // 94 + 6*4=24 cases (117)
			{
				final int upperHalfByteCod2 = handleDirtyBytes1Int(targetAddress + 1, (int)(value >>> Integer.SIZE));
				final int lowerHalfByteCode = handleDirtyBytes2Int(targetAddress + 2, (int)(value & MASK_LOWER_HALF));
				dirtyBytes3Code = CHUNK_TYPE_BYTES_3_1AND2 + upperHalfByteCod2 + lowerHalfByteCode * 4;
			}
		}
		setChunkHead(targetAddress, dirtyBytes3Code);
	}
	
	private static long completeChunkHead(final long targetAddress, final int chunkType)
	{
		setChunkHead(targetAddress, chunkType);
		
		return targetAddress + CHUNK_HEAD_LENGTH_STANDARD;
	}
	
	private static long completeChunkHead(final long targetAddress, final byte chunkType, final int extension)
	{
		setChunkHead(targetAddress, chunkType);
		setChunkHeadExtension(targetAddress, extension);
		
		return targetAddress + CHUNK_HEAD_LENGTH_W_EXTNSN;
	}
	
	private static void setChunkHead(final long targetAddress, final int chunkType)
	{
		setChunkHead(targetAddress, (byte)chunkType);
	}
	
	private static void setChunkHead(final long targetAddress, final byte chunkType)
	{
		XMemory.set_byte(targetAddress + CHUNK_OFFSET_HEAD, chunkType);
	}
	
	private static int getChunkHead(final long chunkStartAddress)
	{
		return Byte.toUnsignedInt(
			XMemory.get_byte(chunkStartAddress + CHUNK_OFFSET_HEAD)
		);
	}
	
	private static boolean isFlippedBits(final long chunkStartAddress)
	{
		return XMemory.get_byte(chunkStartAddress + CHUNK_OFFSET_HEAD) < 0;
	}
	
	
		
	private static int markFlippedBits(final int chunkHead)
	{
		return chunkHead | CHUNK_HEAD_ONES_BIT_MASK;
	}
	
	private static int toChunkType(final int chunkHead)
	{
		return chunkHead & CHUNK_HEAD_ONES_NOT_MASK;
	}
	
	private static void setChunkHeadExtension(final long targetAddress, final int extension)
	{
		XMemory.set_byte(targetAddress + CHUNK_OFFSET_EXTN, (byte)extension);
	}
	
	private static int getChunkHeadExtension(final long chunkStartAddress)
	{
		return Byte.toUnsignedInt(
			XMemory.get_byte(chunkStartAddress + CHUNK_OFFSET_EXTN)
		);
	}
			
	private static long setByte(final long targetAddress, final int value1)
	{
		XMemory.set_byte(targetAddress, (byte)value1);
		
		return targetAddress + Byte.BYTES;
	}
	
	private static void set2Bytes(final long targetAddress, final int value1, final int value2)
	{
		setByte(targetAddress    , value1);
		setByte(targetAddress + 1, value2);
	}
	
	private static void set3Bytes(final long targetAddress, final int value1, final int value2, final int value3)
	{
		setByte(targetAddress    , value1);
		setByte(targetAddress + 1, value2);
		setByte(targetAddress + 2, value3);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// decompression //
	//////////////////
	
	public static long decompress(final long sourceAddress, final long targetAddress)
	{
		final int chunksCount = getChunksCount(sourceAddress);
//		System.out.println("Decompressing...\nChunksCount = " + chunksCount);
				
		long currentChunkAddress  = toChunksStartAddress(sourceAddress);
		long currentTargetAddress = targetAddress;
		for(int i = 0; i < chunksCount; i++)
		{
			final int     chunkHead = getChunkHead(currentChunkAddress);
			final int     chunkType = toChunkType(chunkHead);
			final boolean flipFlag  = isFlippedBits(currentChunkAddress);
			
			final long dataLength, chunkLength;
			if(chunkType < CHUNK_TYPE_BYTES_START_SINGLE_VAL)
			{
				if(chunkType < CHUNK_TYPE_TRIVIAL_FIXED)
				{
					dataLength = getChunkHeadExtension(currentChunkAddress) * Long.BYTES;
					if(chunkType == CHUNK_TYPE_TRIVIAL_SIZED)
					{
						decompressChunkTrivial(flipFlag, currentTargetAddress, dataLength);
						chunkLength = CHUNK_HEAD_LENGTH_W_EXTNSN;
					}
					else // == CHUNK_TYPE_UNCOMPD_FIXED
					{
						copyUncompressed(currentChunkAddress + CHUNK_HEAD_LENGTH_W_EXTNSN, currentTargetAddress, dataLength);
						chunkLength = CHUNK_HEAD_LENGTH_W_EXTNSN + dataLength;
					}
				}
				else if(chunkType < CHUNK_TYPE_UNCOMPD_FIXED)
				{
					dataLength = (chunkType - CHUNK_TYPE_TRIVIAL_FIXED_SIZE_BASE) * Long.BYTES;
					decompressChunkTrivial(flipFlag, currentTargetAddress, dataLength);
					chunkLength = CHUNK_HEAD_LENGTH_STANDARD;
				}
				else if(chunkType < CHUNK_TYPE_EXTENSION)
				{
					dataLength = (chunkType - CHUNK_TYPE_UNCOMPD_FIXED_SIZE_BASE) * Long.BYTES;
					copyUncompressed(currentChunkAddress + CHUNK_HEAD_LENGTH_STANDARD, currentTargetAddress, dataLength);
					chunkLength = CHUNK_HEAD_LENGTH_STANDARD + dataLength;
				}
				else // == CHUNK_TYPE_EXTENSION
				{
					handleChunkTypeExtension(chunkType);
					chunkLength = 0; // unreachable, only to satisfy the compiler.
					dataLength  = 0; // unreachable, only to satisfy the compiler.
				}
			}
			else // >= CHUNK_TYPE_BYTES_START_SINGLE_VAL (BitRow and BytePattern 1/2/3)
			{
				dataLength = Long.BYTES;
				chunkLength = decompressChunkSingleValue(
					currentChunkAddress + CHUNK_HEAD_LENGTH_STANDARD, currentTargetAddress, chunkType, flipFlag
				);
			}
			currentChunkAddress += chunkLength;
			currentTargetAddress += dataLength;
		}
		
		return currentTargetAddress;
	}
		
	private static long decompressChunkSingleValue(
		final long    chunkDataAddress,
		final long    targetAddress   ,
		final int     chunkType       ,
		final boolean flipFlag
	)
	{
		if(chunkType < CHUNK_TYPE_BYTES_START_2)
		{
			if(chunkType < CHUNK_TYPE_BYTES_START_1)
			{
				// may never get here before BitRow is implemented or something went very wrong.
				return decompressBitRowValue(chunkDataAddress, targetAddress, flipFlag);
			}
			// 1 byte BytePattern
			return decompressChunkBytePattern1(chunkDataAddress, targetAddress, flipFlag, chunkType);
		}
		else if(chunkType < CHUNK_TYPE_BYTES_START_3)
		{
			// 2 bytes BytePattern
			return decompressChunkBytePattern2(chunkDataAddress, targetAddress, flipFlag, chunkType);
		}
		// 3 bytes BytePattern
		return decompressChunkBytePattern3(chunkDataAddress, targetAddress, flipFlag, chunkType);
	}
	
	private static long handleChunkTypeExtension(final int chunkType)
	{
		// (06.11.2023 TM)NOTE: currently not used, only reserved for future extensions if needed.
		throw new org.eclipse.serializer.meta.NotImplementedYetError("BytePattern header extension not supported (yet).");
	}
	
	private static long decompressBitRowValue(
		final long    chunkDataAddress,
		final long    targetAddress   ,
		final boolean flipBits
	)
	{
		// assembles the raw value, which might be inverted. Bits are flipped below, if necessary.
		final long value = assembleValueBitRowPattern(chunkDataAddress);
		
		setBytePatternValue(targetAddress, flipBits, value);
		
		// 1 byte chunk header plus 1 byte data.
		return 2;
	}
	
	private static void decompressChunkTrivial(
		final boolean flipBits     ,
		final long    targetAddress,
		final long    byteCount
	)
	{
		XMemory.fillMemory(targetAddress, byteCount, flipBits ? ONE : ZERO);
	}
	
	private static void copyUncompressed(
		final long sourceAddress,
		final long targetAddress,
		final long byteCount
	)
	{
		XMemory.copyRange(sourceAddress, targetAddress, byteCount);
	}
	
	private static void setBytePatternValue(
		final long    targetAddress,
		final boolean flipBits     ,
		final long    value
	)
	{
		XMemory.set_long(targetAddress, flipBits ? ~value : value);
	}
	
	private static int decompressChunkBytePattern1(
		final long    chunkDataAddress,
		final long    targetAddress   ,
		final boolean flipBits        ,
		final int     chunkType
	)
	{
		setBytePatternValue(targetAddress, flipBits, assembleByte(chunkDataAddress, chunkType - CHUNK_TYPE_BYTES_START_1));

		// 1 byte chunk header plus 1 byte data.
		return 2;
	}
	
	private static long assembleValueBytePattern1(final long chunkDataAddress, final int chunkType, final boolean flipBytes)
	{
		return flipBytes
			? ~assembleByte(chunkDataAddress, chunkType - CHUNK_TYPE_BYTES_START_1)
			: assembleByte(chunkDataAddress, chunkType - CHUNK_TYPE_BYTES_START_1)
		;
	}
		
	private static long assembleByte(final long dataAddress, final int position)
	{
		/* (06.11.2023 TM)TODO: which is faster?
		 * Pure calculation vs. offset switch.
		 * The switch SHOULD be faster.
		 * 
		 * Also keep in mind: this is not just for the 1-Byte BytePattern,
		 * but 2 and 3 use this method internally as well. So it is really
		 * used A LOT.
		 */
		return Byte.toUnsignedLong(XMemory.get_byte(dataAddress)) << position * Byte.SIZE;
//		return switchShift(Byte.toUnsignedLong(XMemory.get_byte(dataAddress)), position);
	}
	
	private static long switchShift(final long byteValue, final int position)
	{
		switch(position)
		{
			case  0: return byteValue      ;
			case  1: return byteValue <<  8;
			case  2: return byteValue << 16;
			case  3: return byteValue << 24;
			case  4: return byteValue << 32;
			case  5: return byteValue << 40;
			case  6: return byteValue << 48;
			case  7: return byteValue << 56;
			default: throw new BitmapLevel2Exception("Invalid position: " + position);
		}
	}
	
	private static long assembleBytes(final long dataAddress, final int p1, final int p2)
	{
		return assembleByte(dataAddress, p1) | assembleByte(dataAddress + 1, p2);
	}
	
	private static long assembleBytes(final long dataAddress, final int p1, final int p2, final int p3)
	{
		return assembleByte(dataAddress, p1) | assembleByte(dataAddress + 1, p2) | assembleByte(dataAddress + 2, p3);
	}
	
	private static int decompressChunkBytePattern2(
		final long    chunkDataAddress,
		final long    targetAddress   ,
		final boolean flipBits        ,
		final int     chunkType
	)
	{
		setBytePatternValue(targetAddress, flipBits, assembleValueBytePattern2(chunkDataAddress, chunkType));

		// 1 byte chunk header plus 2 bytes data.
		return 3;
	}
	
	private static long assembleValueBytePattern2(final long chunkDataAddress, final int chunkType, final boolean flipBytes)
	{
		return flipBytes
			? ~assembleValueBytePattern2(chunkDataAddress, chunkType)
			: assembleValueBytePattern2(chunkDataAddress, chunkType)
		;
	}
	
	private static long assembleValueBytePattern2(final long dataAddress, final int chunkType)
	{
		if(chunkType < CHUNK_TYPE_BYTES_2_LOWER)
		{
			final int code = chunkType - CHUNK_TYPE_BYTES_2_SPLIT;
			// (06.11.2023 TM)TODO: use 16-case switch here as well in case 1-byte-switch is faster?
			return assembleBytes(dataAddress, 4 + (code >>> 2), code & 0b11);
		}
		
		final int code = chunkType - CHUNK_TYPE_BYTES_2_LOWER;
		switch(code)
		{
			case  0: return assembleBytes(dataAddress, 1, 0); // +0
			case  1: return assembleBytes(dataAddress, 2, 0); // +0
			case  2: return assembleBytes(dataAddress, 3, 0); // +0
			case  3: return assembleBytes(dataAddress, 2, 1); // +0
			case  4: return assembleBytes(dataAddress, 3, 1); // +0
			case  5: return assembleBytes(dataAddress, 3, 2); // +0
			case  6: return assembleBytes(dataAddress, 5, 4); // +4
			case  7: return assembleBytes(dataAddress, 6, 4); // +4
			case  8: return assembleBytes(dataAddress, 7, 4); // +4
			case  9: return assembleBytes(dataAddress, 6, 5); // +4
			case 10: return assembleBytes(dataAddress, 7, 5); // +4
			case 11: return assembleBytes(dataAddress, 7, 6); // +4
			default: throw new BitmapLevel2Exception("Invalid code: " + code);
		}
	}
	
	private static long assemble2ByteHalf(final long dataAddress, final int code, final int offset)
	{
		switch(code)
		{
			case  0: return assembleBytes(dataAddress, offset + 1, offset + 0);
			case  1: return assembleBytes(dataAddress, offset + 2, offset + 0);
			case  2: return assembleBytes(dataAddress, offset + 3, offset + 0);
			case  3: return assembleBytes(dataAddress, offset + 2, offset + 1);
			case  4: return assembleBytes(dataAddress, offset + 3, offset + 1);
			case  5: return assembleBytes(dataAddress, offset + 3, offset + 2);
			default: throw new BitmapLevel2Exception("Invalid code: " + code);
		}
	}
	
	private static long assemble3ByteHalf(final long dataAddress, final int code)
	{
		switch(code)
		{
			case  0: return assembleBytes(dataAddress, 3, 2, 1);
			case  1: return assembleBytes(dataAddress, 3, 2, 0);
			case  2: return assembleBytes(dataAddress, 3, 1, 0);
			case  3: return assembleBytes(dataAddress, 2, 1, 0);
			case  4: return assembleBytes(dataAddress, 7, 6, 5);
			case  5: return assembleBytes(dataAddress, 7, 6, 4);
			case  6: return assembleBytes(dataAddress, 7, 5, 4);
			case  7: return assembleBytes(dataAddress, 6, 5, 4);
			default: throw new BitmapLevel2Exception("Invalid code: " + code);
		}
	}
	
	private static int decompressChunkBytePattern3(
		final long    chunkDataAddress,
		final long    targetAddress   ,
		final boolean flipBits        ,
		final int     chunkType
	)
	{
		setBytePatternValue(targetAddress, flipBits, assembleValueBytePattern3(chunkDataAddress, chunkType));

		// 1 byte chunk header plus 3 bytes data.
		return 4;
	}
	
	private static long assembleValueBytePattern3(final long chunkDataAddress, final int chunkType, final boolean flipBytes)
	{
		return flipBytes
			? ~assembleValueBytePattern3(chunkDataAddress, chunkType)
			: assembleValueBytePattern3(chunkDataAddress, chunkType)
		;
	}
	
	private static long assembleValueBytePattern3(final long chunkDataAddress, final int chunkType)
	{
		if(chunkType < CHUNK_TYPE_BYTES_3_2AND1)
		{
			return assemble3ByteHalf(chunkDataAddress, chunkType - CHUNK_TYPE_BYTES_3_LOWER);
		}
		else if(chunkType < CHUNK_TYPE_BYTES_3_1AND2)
		{
			// CHUNK_TYPE_BYTES_3_2AND1
			return assemble2ByteHalf(chunkDataAddress    , chunkType - CHUNK_TYPE_BYTES_3_2AND1 >> 2, 4)
				 | assembleByte     (chunkDataAddress + 2, chunkType - CHUNK_TYPE_BYTES_3_2AND1 & 0b11 )
			;
		}
		// CHUNK_TYPE_BYTES_3_1AND2
		return assembleByte     (chunkDataAddress    , (chunkType - CHUNK_TYPE_BYTES_3_1AND2 & 0b11) + 4)
			 | assemble2ByteHalf(chunkDataAddress + 1,  chunkType - CHUNK_TYPE_BYTES_3_1AND2 >> 2,     0)
		;
	}
	
	private static int getChunkMultiValueSize(final long chunkStartAddress, final int chunkType)
	{
		return chunkType < CHUNK_TYPE_TRIVIAL_FIXED
			? getChunkHeadExtension(chunkStartAddress)
			: chunkType < CHUNK_TYPE_UNCOMPD_FIXED
				? chunkType - CHUNK_TYPE_TRIVIAL_FIXED_SIZE_BASE
				: chunkType - CHUNK_TYPE_UNCOMPD_FIXED_SIZE_BASE
		;
	}
	
	private static boolean isChunkSingleValue(final int chunkType)
	{
		return chunkType >= CHUNK_TYPE_BYTES_START_SINGLE_VAL;
	}
	
	private static int getSingleValueChunkLength(final int chunkType)
	{
		return chunkType < CHUNK_TYPE_BYTES_START_2
			? chunkType < CHUNK_TYPE_BYTES_START_1
				? 2 // BitRow
				: 2 // Byte1
			: chunkType < CHUNK_TYPE_BYTES_START_3
				? 3 // Byte2
				: 4 // Byte3
		;
	}
	
	private static int getMultiValueChunkLength(final long chunkStartAddress, final int chunkType)
	{
		return chunkType < CHUNK_TYPE_BOUND_SIZED
			? chunkType == CHUNK_TYPE_TRIVIAL_SIZED
				? 2
				: 2 + getChunkHeadExtension(chunkStartAddress) * Long.BYTES
			: chunkType < CHUNK_TYPE_BOUND_FIXED
				? chunkType < CHUNK_TYPE_BOUND_FIXED_TRIVIAL
					? 1
					: 1 + (chunkType - CHUNK_TYPE_UNCOMPD_FIXED_SIZE_BASE) * Long.BYTES
				: getExtensionChunkLength(chunkStartAddress)
		;
	}
	
	private static int getExtensionChunkLength(final long chunkStartAddress)
	{
		// (08.11.2023 TM)TODO: implement Extension ChunkType when supported. Obviously.
		throw new org.eclipse.serializer.meta.NotImplementedYetError("Extension ChunkType not implemented, yet.");
	}
	
	static final long getBitmapValueFromCompressedEntry(final long compressedDataAddress, final int valueIndex)
	{
		final int  chunksCount        = getChunksCount(compressedDataAddress);
		final long chunksStartAddress = toChunksStartAddress(compressedDataAddress);
		
		long currentChunkAddress = chunksStartAddress;
		for(int i = 0, skippedValues = 0; i < chunksCount; i++)
		{
			final int chunkType = toChunkType(getChunkHead(currentChunkAddress));
			if(isChunkSingleValue(chunkType))
			{
				if(valueIndex == skippedValues++)
				{
					return getSingleValueChunkBitmapValue(
						currentChunkAddress, chunkType, isFlippedBits(currentChunkAddress)
					);
				}
				currentChunkAddress += getSingleValueChunkLength(chunkType);
				continue;
			}
			
			final int chunkSize = getChunkMultiValueSize(currentChunkAddress, chunkType);
			if(valueIndex < (skippedValues += chunkSize))
			{
				// already advanced skippedValues must be "rolled back" to calculate the chunk's value offset.
				return getMultiValueChunkBitmapValue(
					currentChunkAddress, chunkType, isFlippedBits(currentChunkAddress), valueIndex - (skippedValues - chunkSize)
				);
			}
			currentChunkAddress += getMultiValueChunkLength(currentChunkAddress, chunkType);
		}
		
		// existing, but completely empty segment (0 chunks)
		return 0L;
	}
	
	private static long getSingleValueChunkBitmapValue(
		final long    chunkAddress,
		final int     chunkType   ,
		final boolean flipBits
	)
	{
		return chunkType < CHUNK_TYPE_BYTES_START_2
			? chunkType < CHUNK_TYPE_BYTES_START_1
				? assembleValueBitRowPattern(chunkAddress + 1, flipBits)
				: assembleValueBytePattern1(chunkAddress + 1, chunkType, flipBits)
			: chunkType < CHUNK_TYPE_BYTES_START_3
				? assembleValueBytePattern2(chunkAddress + 1, chunkType, flipBits)
				: assembleValueBytePattern3(chunkAddress + 1, chunkType, flipBits)
		;
	}
	
	private static long assembleValueBitRowPattern(final long chunkAddress)
	{
		// chunkDataAddress already points to the byte after the chunk header, so nothing to add here.
		final int bitRowValue = XMemory.get_byte(chunkAddress);
		if(bitRowValue < BIT_ROW_DATA_VALUE_START || bitRowValue >= BIT_ROW_DATA_VALUE_BOUND)
		{
			throw new BitmapLevel2Exception("Invalid bit row value: " + bitRowValue);
		}
		
		/* this is funny:
		 * 1.) bit count is stored as [0; 63] but represents [1; 64], so add 1.
		 * 2.) shift value 1 to the position 1 left of the last 1 bit to be set.
		 * 3.) subtract 1 to delete the overshooting 1 and turn all 0s below it into 1s.
		 */
		return (1L << bitRowValue + 1) - 1;
	}
	
	private static long assembleValueBitRowPattern(
		final long    chunkAddress,
		final boolean flipBits
	)
	{
		// assembles the raw value, which might be inverted. Bits are flipped below, if necessary.
		final long value = assembleValueBitRowPattern(chunkAddress);
		
		return flipBits ? ~value : value;
	}
	
	private static long getMultiValueChunkBitmapValue(
		final long    chunkAddress,
		final int     chunkType   ,
		final boolean flipBits    ,
		final int     valueOffset
	)
	{
		return chunkType < CHUNK_TYPE_BOUND_SIZED
			? chunkType == CHUNK_TYPE_TRIVIAL_SIZED
				? flipBits ? -1L : 0L
				: XMemory.get_long(chunkAddress + 2 + valueOffset  * Long.BYTES)
			: chunkType < CHUNK_TYPE_BOUND_FIXED
				? chunkType < CHUNK_TYPE_BOUND_FIXED_TRIVIAL
					? flipBits ? -1L : 0L
					: XMemory.get_long(chunkAddress + 1 + valueOffset  * Long.BYTES)
				: getExtensionChunkBitmapValue(chunkAddress, flipBits, valueOffset)
		;
	}
		
	private static long getExtensionChunkBitmapValue(
		final long    chunkAddress,
		final boolean flipBits    ,
		final int     valueOffset
	)
	{
		// (08.11.2023 TM)TODO: implement Extension ChunkType when supported. Obviously.
		throw new org.eclipse.serializer.meta.NotImplementedYetError("Extension ChunkType not implemented, yet.");
	}
		
	@Deprecated
	static void DEBUG_assembleChunks(final VarString vs, final long compressedDataAddress)
	{
		final int  chunksCount        = getChunksCount(compressedDataAddress);
		final long chunksStartAddress = toChunksStartAddress(compressedDataAddress);

		vs.add(", chunksCount = " + chunksCount + ", chunksStartAddress@" + chunksStartAddress);

		long currentChunkAddress = chunksStartAddress;
		int bitmapValueIndex = 0;
		for(int i = 0; i < chunksCount; i++)
		{
			final int chunkType = toChunkType(getChunkHead(currentChunkAddress));
			if(isChunkSingleValue(chunkType))
			{
				final long bitmapValue = getSingleValueChunkBitmapValue(
					currentChunkAddress, chunkType, isFlippedBits(currentChunkAddress)
				);
				vs.lf().add("Chunk #" + i + " (SingleValue), binary length = " + getSingleValueChunkLength(chunkType) + ", chunk type = " + chunkTypeToString(chunkType));
				BitmapLevel2.assembleBitmapValue(vs, bitmapValueIndex++, bitmapValue);
				currentChunkAddress += getSingleValueChunkLength(chunkType);
				continue;
			}

			final int chunkSize = getChunkMultiValueSize(currentChunkAddress, chunkType);
			vs.lf().add("Chunk #" + i + " (MultiValue, Size = " + chunkSize + "), binary length = " + getMultiValueChunkLength(currentChunkAddress, chunkType) + ", chunk type = " + chunkTypeToString(chunkType));
			for(int c = 0; c < chunkSize; c++)
			{
				final long bitmapValue = getMultiValueChunkBitmapValue(
					currentChunkAddress, chunkType, isFlippedBits(currentChunkAddress), c
				);
				BitmapLevel2.assembleBitmapValue(vs, bitmapValueIndex++, bitmapValue);
			}
			currentChunkAddress += getMultiValueChunkLength(currentChunkAddress, chunkType);
		}
	}

	public static String chunkTypeToString(final int chunkType)
	{
		/*
		 * "pseudo-switch" to model ranges instead of values. The highest value must be checked first, the lowest last.
		 * Note that this construct does not have the best performance since the cases are not divided using
		 * a binary strategy, but are simply checked in a linear way, in order to improve readability:
		 * - (upper bound)
		 * - 4x 3-byte-patterns
		 * - 3x 2-byte-patterns
		 * - 2x 1-byte-patterns
		 * - bit row
		 * - extension
		 * - 2x fixed size trivial/uncompressed
		 * - 1x variable size uncompressed
		 * - 1x all-1-bits trivial (trivial but flipped)
		 * - 1x all-0-bits trivial
		 * - (negative values)
		 */
		return chunkType >= CHUNK_TYPE_BOUND
				? "[INVALID : " + chunkType + "]"
			: chunkType >= CHUNK_TYPE_BYTES_3_1AND2
				? chunkType + ": 3 bytes split 1&2 accross both halves"
			: chunkType >= CHUNK_TYPE_BYTES_3_2AND1
				? chunkType + ": 3 bytes split 2&1 accross both halves"
			: chunkType >= CHUNK_TYPE_BYTES_3_UPPER
				? chunkType + ": 3 bytes distributed in the upper half"
			: chunkType >= CHUNK_TYPE_BYTES_3_LOWER
				? chunkType + ": 3 bytes distributed in the lower half"
			: chunkType >= CHUNK_TYPE_BYTES_2_UPPER
				? chunkType + ": 2 bytes distributed in the upper half"
			: chunkType >= CHUNK_TYPE_BYTES_2_LOWER
				? chunkType + ": 2 bytes distributed in the lower half"
			: chunkType >= CHUNK_TYPE_BYTES_2_SPLIT
				? chunkType + ": 2 bytes split accross both halves"
			: chunkType >= CHUNK_TYPE_BYTES_1_POS_4
				? chunkType + ": 1 byte located in upper half"
			: chunkType >= CHUNK_TYPE_BYTES_1_POS_0
				? chunkType + ": 1 byte located in lower half"
			: chunkType == CHUNK_TYPE_BIT_ROW
				? chunkType + ": Bitrow"
			: chunkType == CHUNK_TYPE_EXTENSION
				? chunkType + ": [Extension, currently not used]"
			: chunkType >= CHUNK_TYPE_UNCOMPD_FIXED
				? chunkType + ": Uncompressed with fixed size"
			: chunkType >= CHUNK_TYPE_TRIVIAL_FIXED
				? chunkType + ": All-same-bits with fixed size"
			: chunkType == CHUNK_TYPE_UNCOMPD_SIZED
				? chunkType + ": Uncompressed with explicit size (13-64 long values"
			: chunkType == CHUNK_TYPE_TRIVIAL_SIZEDf
				? chunkType + ": All-1-bits with explicit size (13-64 long values"
			: chunkType == CHUNK_TYPE_TRIVIAL_SIZED
				? chunkType + ": All-0-bits with explicit size (13-64 long values"
			: "[INVALID : " + chunkType + "]"
		;
	}
	
}
