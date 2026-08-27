package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.eclipse.serializer.util.X.notNull;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.function.Function;

import org.eclipse.serializer.collections.types.XGettingSequence;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.typing.XTypes;
import org.eclipse.serializer.util.X;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;
import org.eclipse.store.storage.exceptions.StorageExceptionIoReading;

public interface StorageEntityInitializer<D extends StorageLiveDataFile>
{
	public D registerEntities(XGettingSequence<? extends StorageDataInventoryFile> files, long lastFileLength);
	
	
	
	static StorageEntityInitializer<StorageLiveDataFile.Default> New(
		final StorageEntityCache.Default                                      entityCache       ,
		final Function<StorageDataInventoryFile, StorageLiveDataFile.Default> dataFileCreator   ,
		final StorageMetaRecordRegistry                                       metaRecordRegistry
	)
	{
		return new StorageEntityInitializer.Default(
			notNull(dataFileCreator)   ,
			notNull(entityCache)       ,
			notNull(metaRecordRegistry)
		);
	}

	final class Default implements StorageEntityInitializer<StorageLiveDataFile.Default>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final Function<StorageDataInventoryFile, StorageLiveDataFile.Default> dataFileCreator        ;
		private final StorageEntityCache.Default                                      entityCache            ;
		private final StorageMetaRecordRegistry                                       metaRecordRegistry     ;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final Function<StorageDataInventoryFile, StorageLiveDataFile.Default> dataFileCreator        ,
			final StorageEntityCache.Default                                      entityCache            ,
			final StorageMetaRecordRegistry                                       metaRecordRegistry
		)
		{
			super();
			this.dataFileCreator         = dataFileCreator        ;
			this.entityCache             = entityCache            ;
			this.metaRecordRegistry      = metaRecordRegistry     ;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final StorageLiveDataFile.Default registerEntities(
			final XGettingSequence<? extends StorageDataInventoryFile> files ,
			final long                                             lastFileLength
		)
		{
			return registerEntities(this.dataFileCreator, this.entityCache, files.toReversed(), lastFileLength, this.metaRecordRegistry);
		}

		private static StorageLiveDataFile.Default registerEntities(
			final Function<StorageDataInventoryFile, StorageLiveDataFile.Default> fileCreator            ,
			final StorageEntityCache.Default                                      entityCache            ,
			final XGettingSequence<? extends StorageDataInventoryFile>            reversedFiles          ,
			final long                                                            lastFileLength         ,
			final StorageMetaRecordRegistry                                       metaRecordRegistry
		)
		{
			final ByteBuffer                               buffer   = allocateInitializationBuffer(reversedFiles);
			final Iterator<? extends StorageDataInventoryFile> iterator = reversedFiles.iterator();
			final int[] entityOffsets = createAllFilesOffsetsArray(buffer.capacity());

			final long initTime = System.currentTimeMillis();

			// special case handling for last/head file
			final StorageLiveDataFile.Default headFile = setupHeadFile(fileCreator.apply(iterator.next()));
			registerFileEntities(entityCache, initTime, headFile, lastFileLength, buffer, entityOffsets, metaRecordRegistry);

			// simple tail file adding iteration for all remaining (previous!) storage files
			for(StorageLiveDataFile.Default dataFile = headFile; iterator.hasNext();)
			{
				dataFile = linkTailFile(dataFile, fileCreator.apply(iterator.next()));
				registerFileEntities(entityCache, initTime, dataFile, dataFile.size(), buffer, entityOffsets, metaRecordRegistry);
			}

			XMemory.deallocateDirectByteBuffer(buffer);

			return headFile;
		}

		final static void registerFileEntities(
			final StorageEntityCache.Default     entityCache            ,
			final long                           initializationTime     ,
			final StorageLiveDataFile.Default    file                   ,
			final long                           fileActualLength       ,
			final ByteBuffer                     buffer                 ,
			final int[]                          entityOffsets          ,
			final StorageMetaRecordRegistry      metaRecordRegistry
		)
		{
			// entities must be indexed first to allow reverse iteration.
			final int                         entityCount = indexEntities(file, fileActualLength, buffer, entityOffsets, metaRecordRegistry);
			final StorageEntityCacheEvaluator entityCacheEvaluator = entityCache.entityCacheEvaluator;
			final long                        bufferStartAddress   = XMemory.getDirectByteBufferAddress(buffer);
			
			long totalFileContentLength = 0;
			
			// reverse entity iteration to register the most current version first and discard all prior versions.
			for(int i = entityCount; i --> 0;)
			{
				/*
				 * Initialization only registers the first occurrence in the reversed initialization,
				 * meaning only the most current version of every entity (identified by its ObjectId).
				 * All earlier versions are simply ignored, hence the "return false".
				 */
				if(entityCache.getEntry(Binary.getEntityObjectIdRawValue(bufferStartAddress + entityOffsets[i])) != null)
				{
					continue;
				}
				
				final long                  entityAddress = bufferStartAddress + entityOffsets[i];
				final long                  entityLength  = Binary.getEntityLengthRawValue(entityAddress);
				final StorageEntity.Default entity        = entityCache.initialCreateEntity(entityAddress);
				
				entity.updateStorageInformation(XTypes.to_int(entityLength), entityOffsets[i]);
				file.prependEntry(entity);
				totalFileContentLength += entityLength;
				
				if(entityCacheEvaluator.initiallyCacheEntity(entityCache.cacheSize(), initializationTime, entity))
				{
					entity.putCacheData(entityAddress, entityLength);
					entityCache.modifyUsedCacheSize(entityLength);
				}
			}

			// the total length of all actually registered entities is the file's content length. The rest is gaps.
			file.increaseContentLength(totalFileContentLength);

			// indexEntities already registered meta-record bytes via registerMetaLength as the walker
			// recognized them. The remainder is the legacy gap from removed entities.
			file.registerGapLength(buffer.limit() - totalFileContentLength - file.metaLength());
		}
				
		/**
		 * 
		 * @return the entity count.
		 */
		private static int indexEntities(
			final StorageLiveDataFile.Default    file                   ,
			final long                           fileActualLength       ,
			final ByteBuffer                     buffer                 ,
			final int[]                          entityOffsets          ,
			final StorageMetaRecordRegistry      metaRecordRegistry
		)
		{
			int lastEntityIndex = -1;

			fillBuffer(buffer, file, fileActualLength);

			final long bufferStartAddress = XMemory.getDirectByteBufferAddress(buffer);
			final long bufferBoundAddress = bufferStartAddress + buffer.limit();

			long currentItemLength;

			// Start address of the current chunk's hashed bytes. Advances past the FileHeaderV1 (which
			// is not itself part of any chunk's hash) and past each successfully-verified ChunkChecksumV1.
			long chunkStart = bufferStartAddress;

			for(long address = bufferStartAddress; address < bufferBoundAddress;)
			{
				// Validate the item length against the buffer bound before using it: a corrupted length is
				// caught here as a consistency error instead of an out-of-bounds read or a later crash.
				final long remaining = bufferBoundAddress - address;
				if(remaining < Long.BYTES)
				{
					// too few bytes left to read a length: file does not end on a record boundary.
					throw new StorageExceptionConsistency(
						"Truncated data item: " + remaining + " byte(s) remaining at offset "
						+ (address - bufferStartAddress) + " in " + file + ", cannot read an entity length."
					);
				}

				currentItemLength = Binary.getEntityLengthRawValue(address);

				if(currentItemLength > 0)
				{
					// must cover at least the entity header and not overrun the buffered data.
					if(!Binary.isValidEntityLength(currentItemLength) || currentItemLength > remaining)
					{
						throw new StorageExceptionConsistency(
							"Invalid entity length " + currentItemLength + " at offset "
							+ (address - bufferStartAddress) + " in " + file + " (remaining " + remaining + ")."
						);
					}
					entityOffsets[++lastEntityIndex] = (int)(address - bufferStartAddress);
					address += currentItemLength;
				}
				else if(currentItemLength < 0)
				{
					// negative-length entry: either a legacy opaque gap (skip) or a structured
					// meta record (recognize, verify, dispatch). May throw on checksum mismatch.
					// Returns the updated chunkStart for the next chunk.
					// (< -remaining is the overflow-safe form of |length| > remaining.)
					if(currentItemLength < -remaining)
					{
						throw new StorageExceptionConsistency(
							"Invalid item length " + currentItemLength + " at offset "
							+ (address - bufferStartAddress) + " in " + file + " (remaining " + remaining + ")."
						);
					}
					chunkStart = metaRecordRegistry.dispatch(file, buffer, address, chunkStart);
					address -= currentItemLength;
				}
				else
				{
					// entity length may never be 0 or the iteration will hang forever
					throw new StorageExceptionConsistency("Zero length data item.");
				}
			}

			// after the walk, let the registry raise MISSING_HEADER / UNCOVERED_DATA via the reporter
			// (inert unless verifying with coverage required) and flush per-file log dedupe. chunkStart
			// is the start of the last chunk not followed by a covering checksum record.
			metaRecordRegistry.onFileComplete(
				file                            ,
				chunkStart - bufferStartAddress ,
				buffer.limit()                  ,
				lastEntityIndex >= 0
			);

			return lastEntityIndex + 1;
		}



		///////////////////////////////////////////////////////////////////////////
		// utility methods //
		////////////////////

		private static StorageLiveDataFile.Default setupHeadFile(
			final StorageLiveDataFile.Default storageFile
		)
		{
			storageFile.next = storageFile.prev = storageFile;
			
			return storageFile;
		}

		private static StorageLiveDataFile.Default linkTailFile(
			final StorageLiveDataFile.Default currentTailFile,
			final StorageLiveDataFile.Default nextTailFile
		)
		{
			// joined in chain after current head file and before the current first
			nextTailFile.prev = (nextTailFile.next = currentTailFile).prev; // setup new element's links
			currentTailFile.prev = currentTailFile.prev.next = nextTailFile; // insert new element
			
			return nextTailFile;
		}
		
		private static int[] createAllFilesOffsetsArray(final int largestFileLength)
		{
			/*
			 * Assuming the largest file solely consists of stateless entities (only headers)
			 * guarantees to have a large enough array and a fast algorithm using it for all files.
			 * The largest file just shouldn't be allowed too large (for other reasons, too).
			 */
			return new int[largestFileLength / Binary.entityHeaderLength()];
		}
		
		private static ByteBuffer allocateInitializationBuffer(final Iterable<? extends StorageDataInventoryFile> files)
		{
			final int largestFileSize = determineLargestFileSize(files);
			
			// anything below the system's "default" buffer size (a "page", usually 4096) doesn't pay off.
			final ByteBuffer buffer = XMemory.allocateDirectNative(
				Math.max(largestFileSize, XMemory.defaultBufferSize())
			);
			
			return buffer;
		}
		
		private static void fillBuffer(
			final ByteBuffer                  buffer          ,
			final StorageLiveDataFile.Default file            ,
			final long                        fileActualLength
		)
		{
			try
			{
				buffer.clear();
				// the reason for the stupid limit is actually a single toArray() somewhere in NIO.
				buffer.limit(X.checkArrayRange(fileActualLength));
				
				file.readBytes(buffer, 0, fileActualLength);
			}
			catch(final Exception e)
			{
				throw new StorageExceptionIoReading(e);
			}
		}
		
		private static int determineLargestFileSize(final Iterable<? extends StorageDataInventoryFile> files)
		{
			int largestFileSize = -1;
			
			for(final StorageDataInventoryFile file : files)
			{
				final long fileLength = file.size();
				if(fileLength > Integer.MAX_VALUE)
				{
					/* (29.05.2018 TM)NOTE:
					 * In case someone gets here after suffering this exception:
					 * Yes, the file could be read incrementally in chunks, but while this would prevent some
					 * problems with the JDK IO buffer limitation to int, it cannot prevent all of them.
					 * Consider a single entity, probably a giant collection, whose binary form (8 bytes per
					 * element) exceeds the int limit by itself.
					 * The most reasonable strategy is:
					 * For files that should be in the range of 1-100 MB, anyway, assume int to be "infinite".
					 * Blame problems on users who chose too high a file size and on JDK who implemented
					 * the int limit.
					 */
					throw new StorageExceptionIoReading(
						"Storage file size exceeds Java technical IO limitations: " + file.identifier()
					);
				}
				
				if(fileLength > largestFileSize)
				{
					// cast safety is guaranteed by the validation logic above.
					largestFileSize = (int)fileLength;
				}
			}
			
			return largestFileSize;
		}
		
	}

}
