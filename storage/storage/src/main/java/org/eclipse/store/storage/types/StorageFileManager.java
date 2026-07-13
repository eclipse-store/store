package org.eclipse.store.storage.types;

import static org.eclipse.serializer.math.XMath.notNegative;

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


import static org.eclipse.serializer.util.X.mayNull;
import static org.eclipse.serializer.util.X.notNull;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import org.eclipse.serializer.afs.types.AFS;
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.collections.XSort;
import org.eclipse.serializer.collections.types.XGettingSequence;
import org.eclipse.serializer.exceptions.MultiCauseException;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.time.XTime;
import org.eclipse.serializer.typing.Disposable;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.serializer.typing.XTypes;
import org.eclipse.serializer.util.BufferSizeProvider;
import org.eclipse.serializer.util.X;
import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.types.StorageTransactionsAnalysis.Logic;
import org.eclipse.store.storage.exceptions.StorageException;
import org.eclipse.store.storage.exceptions.StorageExceptionCommitSizeExceeded;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;
import org.eclipse.store.storage.exceptions.StorageExceptionIncompleteTransactionsEntry;
import org.eclipse.store.storage.exceptions.StorageExceptionIoReading;
import org.eclipse.store.storage.exceptions.StorageExceptionIoWriting;
import org.eclipse.store.storage.exceptions.StorageExceptionIoWritingChunk;
import org.slf4j.Logger;


// note that the name channel refers to the entity hash channel, not a nio channel
public interface StorageFileManager extends StorageChannelResetablePart, Disposable
{
	/* (17.09.2014 TM)TODO: Much more loose coupling
	 * Make all storage stuff much more loosely coupled with more interface methods and
	 * self-passing between components.
	 * For example:
	 * - Initializer, maybe even sub components like initializationValidator, etc.
	 * - Exporter
	 * - Importer
	 * - Garbage Collector (if possible to decouple efficiently)
	 *
	 * Everything that is not performance-critical (i.e. does not get called repeatedly in "hot" code)
	 * should be as flexible as possible to allow finer customization, logging (=aspect) wrapping, etc.
	 * All hot code should be contained in one very small component to be easily exchangeable and/or
	 * specifically loggable.
	 */

	@Override
	public int channelIndex();

	@Override
	public void reset();
	
	public long[] storeChunks(long timestamp, ByteBuffer[] dataBuffers) throws StorageExceptionIoWritingChunk;

	public void rollbackWrite();

	public void commitWrite();

	public StorageInventory readStorage();

	public StorageIdAnalysis initializeStorage(
		long             taskTimestamp           ,
		long             consistentStoreTimestamp,
		StorageInventory storageInventory        ,
		StorageChannel   parent
	);

	public StorageLiveDataFile currentStorageFile();

	public void iterateStorageFiles(Consumer<? super StorageLiveDataFile> procedure);

	public boolean incrementalFileCleanupCheck(long nanoTimeBudgetBound);

	public boolean issuedFileCleanupCheck(long nanoTimeBudgetBound);

	/**
	 * Re-verifies this channel's data-file chunk checksums for the on-demand integrity check, collecting
	 * anomalies into the returned per-channel result without throwing. {@code freshScan} starts a new scan
	 * (snapshotting the scope: every file's number and length, the head file bounded to its committed length);
	 * otherwise the in-progress snapshot is resumed until the whole scope is covered
	 * ({@link StorageIntegrityCheckResult#isComplete()}), skipping any file dissolved by housekeeping in between.
	 * A channel that already finished its scope returns a complete, empty result on a resume call (it does not
	 * re-scan), so a budgeted scan across uneven channels terminates without duplicating findings.
	 *
	 * @param nanoTimeBudgetBound the {@link System#nanoTime()} bound to stop at (resume granularity is one file).
	 * @param freshScan           whether to start a new scan (vs resume the in-progress one).
	 * @return this channel's findings and completion state.
	 */
	public StorageIntegrityCheckResult verifyChunkChecksums(long nanoTimeBudgetBound, boolean freshScan);

	public void exportData(StorageLiveFileProvider fileProvider);

	public StorageRawFileStatistics.ChannelStatistics createRawFileStatistics();

	// this is not "reset" in terms of "set to initial state", more like a "go back to the start of the chain".
	public void restartFileCleanupCursor();



	public final class Default implements StorageFileManager, StorageFileUser
	{
		private final static Logger logger = Logging.getLogger(StorageFileManager.class);
		
		///////////////////////////////////////////////////////////////////////////
		// constants //
		//////////////

		// the only reason for this limit is to have an int instead of a long for the item's file position.
		static final int MAX_FILE_LENGTH = Integer.MAX_VALUE;

		// Upper bound for the on-demand integrity check's per-channel verify buffer. A data file at or below this
		// size is read whole (the proven resident verifyDataFile walk); a larger one is streamed in windows of this
		// size (verifyDataFileStreaming), so peak direct memory of a full integrity check is channelCount × this
		// value regardless of the configured data-file maximum size (which can be raised toward the ~2 GiB cap).
		// 8 MiB matches StorageDataFileEvaluator.defaultFileMaximumSize(), so default-sized stores never stream.
		static final int INTEGRITY_VERIFY_BUFFER_LIMIT = 8 * 1024 * 1024;

		// (22.05.2015 TM)TODO: Debug Flag to disable file cleanup for testing
		private static final boolean DEBUG_ENABLE_FILE_CLEANUP = true;



		///////////////////////////////////////////////////////////////////////////
		// static methods //
		///////////////////

		private static long[] allChunksStoragePositions(final ByteBuffer[] chunks, final long basePosition)
		{
			final long[] storagePositions = new long[chunks.length];
			long position = basePosition;
			for(int i = 0; i < chunks.length; i++)
			{
				storagePositions[i] = position;
				position += chunks[i].limit();
			}
			
			return storagePositions;
		}



		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		// state 1.0: immutable or stateless (as far as this implementation is concerned)

		private final int                                    channelIndex                 ;
		private final StorageInitialDataFileNumberProvider   initialDataFileNumberProvider;
		private final StorageTimestampProvider               timestampProvider            ;
		private final StorageLiveFileProvider                fileProvider                 ;
		private final StorageDataFileEvaluator               dataFileEvaluator            ;
		private final StorageChunkChecksumCalculator         chunkChecksumCalculator      ;
		private final StorageMetaRecordRegistry              metaRecordRegistry           ;
		private final StorageEntityCache.Default             entityCache                  ;
		private final StorageWriteController                 writeController              ;
		private final StorageFileWriter                      writer                       ;
		private final StorageBackupHandler                   backupHandler                ;
		private final StorageTransactionsFileCleaner.Creator transactionFileCleanerCreator;
		
		// to avoid permanent lambda instantiation
		private final Consumer<? super StorageLiveDataFile.Default> deleter        = this::deleteFile       ;
		private final Consumer<? super StorageLiveDataFile.Default> pendingDeleter = this::deletePendingFile;
		
		
		// state 1.1: entry buffers. Don't need to be resetted. See comment in reset().

		// all ".clear()" calls on these buffers are only for flushing them out. Filling them happens only via address.
		private final ByteBuffer
			entryBufferFileCreation   = XMemory.allocateDirectNative(StorageTransactionsAnalysis.Logic.entryLengthFileCreation())  ,
			entryBufferStore          = XMemory.allocateDirectNative(StorageTransactionsAnalysis.Logic.entryLengthStore())         ,
			entryBufferTransfer       = XMemory.allocateDirectNative(StorageTransactionsAnalysis.Logic.entryLengthTransfer())      ,
			entryBufferFileDeletion   = XMemory.allocateDirectNative(StorageTransactionsAnalysis.Logic.entryLengthFileCreation())  ,
			entryBufferFileTruncation = XMemory.allocateDirectNative(StorageTransactionsAnalysis.Logic.entryLengthFileTruncation())
		;
		
		private final Iterable<? extends ByteBuffer>
			entryBufferWrapFileCreation   = X.ArrayView(this.entryBufferFileCreation  ),
			entryBufferWrapStore          = X.ArrayView(this.entryBufferStore         ),
			entryBufferWrapTransfer       = X.ArrayView(this.entryBufferTransfer      ),
			entryBufferWrapFileDeletion   = X.ArrayView(this.entryBufferFileDeletion  ),
			entryBufferWrapFileTruncation = X.ArrayView(this.entryBufferFileTruncation)
		;

		private final long
			entryBufferFileCreationAddress   = XMemory.getDirectByteBufferAddress(this.entryBufferFileCreation)  ,
			entryBufferStoreAddress          = XMemory.getDirectByteBufferAddress(this.entryBufferStore)         ,
			entryBufferTransferAddress       = XMemory.getDirectByteBufferAddress(this.entryBufferTransfer)      ,
			entryBufferFileDeletionAddress   = XMemory.getDirectByteBufferAddress(this.entryBufferFileDeletion)  ,
			entryBufferFileTruncationAddress = XMemory.getDirectByteBufferAddress(this.entryBufferFileTruncation)
		;

		// Entry Buffers have their "effectively immutable" first parts initialized once and never changed again.
		{
			StorageTransactionsAnalysis.Logic.initializeEntryFileCreation  (this.entryBufferFileCreationAddress  );
			StorageTransactionsAnalysis.Logic.initializeEntryStore         (this.entryBufferStoreAddress         );
			StorageTransactionsAnalysis.Logic.initializeEntryTransfer      (this.entryBufferTransferAddress      );
			StorageTransactionsAnalysis.Logic.initializeEntryFileDeletion  (this.entryBufferFileDeletionAddress  );
			StorageTransactionsAnalysis.Logic.initializeEntryFileTruncation(this.entryBufferFileTruncationAddress);
		}
		
		
		// state 2.0: final references to mutable instances, i.e. content must be cleared on reset

		// cleared by clearStandardByteBuffer() / reset().
		private final ByteBuffer standardByteBuffer;

		// Reusable fixed-size chunk-checksum meta buffers (single-threaded channel use, like the entry
		// buffers above). Sized from the calculator, so allocated in the constructor rather than as field
		// initializers (chunkChecksumCalculator is not yet assigned at field-init time). Cleared before
		// each use and freed in deleteBuffers(). Safe to reuse immediately after a write returns: the
		// bytes are already in the data file, and a backup copy item re-reads them from the file.
		// chunkChecksumBuffer is shared by storeChunks / dissolution-transfer / import (never concurrent).
		private final ByteBuffer chunkChecksumBuffer;
		private final ByteBuffer fileHeaderBuffer   ;
		private final Iterable<? extends ByteBuffer> chunkChecksumBufferWrap;
		private final Iterable<? extends ByteBuffer> fileHeaderBufferWrap   ;
		
		
		// state 3.0: mutable fields. Must be cleared on reset.
		
		// cleared and nulled by clearTransactionsFile() / clearRegisteredFiles() / reset()
		private StorageLiveTransactionsFile fileTransactions;
		
		// cleared and nulled by clearRegisteredFiles() / reset()
		private StorageLiveDataFile.Default fileCleanupCursor;

		// cleared by clearUncommittedDataLength() / reset().
		// uncommittedDataLength = bytes of positive-length entity records pending commit
		//                         (will contribute to BOTH fileTotalLength and fileDataLength).
		// uncommittedMetaLength = bytes of negative-length meta records pending commit
		//                         (will contribute to fileTotalLength only — matches load-side
		//                         gap-length semantics in StorageEntityInitializer).
		private long uncommittedDataLength;
		private long uncommittedMetaLength;

		// cleared in reset() directly, but kind of irrelevant.
		private int pendingFileDeletes;

		// all channel-local, written only by the channel thread; cleared in reset().
		// durability gate state, see StorageRequestTaskStorageFlush:
		// - lastWrittenStoreTimestamp: highest store timestamp written to the transactions log.
		// - allDurableStoreTimestamp: every store below it is fully durable on every channel
		//   (learned from this channel's own participation in a completed storage-flush task).
		// - durabilityDeferredDeletes: file number -> guard timestamp frozen at the first
		//   deletion attempt, so ongoing stores cannot defer a deletion forever.
		private long lastWrittenStoreTimestamp;
		private long allDurableStoreTimestamp ;
		private boolean storageFlushRequested ;
		// an UNCONDITIONAL (checkSize=false) log cleanup deferred by the durability gate: it must be
		// completed unconditionally at barrier completion, not downgraded to the size-gated check.
		private boolean unconditionalCompactionPending;
		// set whenever this channel rolls a file over; polled (and cleared) by the current task so its
		// completion barrier can make the sealed store durable on every channel (eager rollover
		// durability - prevents a baseline collapse onto a not-yet-all-durable store).
		private boolean rolloverOccurred;
		private final EqHashTable<Long, Long> durabilityDeferredDeletes = EqHashTable.New();
		
		
		// state 3.1: variable length content

		// cleared and nulled by clearRegisteredFiles() / reset()
		private StorageLiveDataFile.Default headFile;

		// On-demand integrity-check scan state spanning the budgeted resume calls of one scan: the scope (file
		// numbers + lengths, head bounded to its committed length) is snapshotted when a fresh scan starts.
		// null arrays == no scan in progress; cleared on reset via clearRegisteredFiles().
		private long[]  integrityScopeNumbers   ;
		private long[]  integrityScopeLengths   ;
		private int     integrityScopeCursor    ;
		private long    integrityScopeHeadNumber; // the file that was head at snapshot (exempt from size-change)


		private StorageTransactionsFileCleaner transactionFileCleaner;

		// initialization rewrote the transactions file past the backup queue: the backup copy
		// can then be stale at equal length, so its synchronization must rebuild unconditionally
		private boolean transactionsFileRestored;


		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		public Default(
			final int                                    channelIndex                 ,
			final StorageInitialDataFileNumberProvider   initialDataFileNumberProvider,
			final StorageTimestampProvider               timestampProvider            ,
			final StorageLiveFileProvider                fileProvider                 ,
			final StorageDataFileEvaluator               dataFileEvaluator            ,
			final StorageChunkChecksumCalculator         chunkChecksumCalculator      ,
			final StorageMetaRecordRegistry              metaRecordRegistry           ,
			final StorageEntityCache.Default             entityCache                  ,
			final StorageWriteController                 writeController              ,
			final StorageFileWriter                      writer                       ,
			final BufferSizeProvider                     standardBufferSizeProvider   ,
			final StorageBackupHandler                   backupHandler                ,
			final StorageTransactionsFileCleaner.Creator transactionFileCleanerCreator
		)
		{
			super();
			this.channelIndex                  = notNegative(channelIndex)                 ;
			this.initialDataFileNumberProvider =     notNull(initialDataFileNumberProvider);
			this.timestampProvider             =     notNull(timestampProvider)            ;
			this.dataFileEvaluator             =     notNull(dataFileEvaluator)            ;
			this.chunkChecksumCalculator       =     notNull(chunkChecksumCalculator)      ;
			this.metaRecordRegistry            =     notNull(metaRecordRegistry)           ;
			this.fileProvider                  =     notNull(fileProvider)                 ;
			this.entityCache                   =     notNull(entityCache)                  ;
			this.writeController               =     notNull(writeController)              ;
			this.writer                        =     notNull(writer)                       ;
			this.backupHandler                 =     mayNull(backupHandler)                ;
			this.transactionFileCleanerCreator =     notNull(transactionFileCleanerCreator);
			
			this.standardByteBuffer = XMemory.allocateDirectNative(
				standardBufferSizeProvider.provideBufferSize()
			);

			this.chunkChecksumBuffer     = XMemory.allocateDirectNative(X.checkArrayRange(chunkChecksumCalculator.chunkChecksumRecordLength()));
			this.fileHeaderBuffer        = XMemory.allocateDirectNative(X.checkArrayRange(chunkChecksumCalculator.fileHeaderRecordLength())   );
			this.chunkChecksumBufferWrap = X.ArrayView(this.chunkChecksumBuffer);
			this.fileHeaderBufferWrap    = X.ArrayView(this.fileHeaderBuffer)   ;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final void dispose()
		{
			this.clearRegisteredFiles();
			this.deleteBuffers();
		}

		final boolean isFileCleanupEnabled()
		{
			return this.writeController.isFileCleanupEnabled();
		}

		final <L extends Consumer<StorageEntity.Default>> L iterateEntities(final L logic)
		{
			// (01.04.2016)XXX: not tested yet

			final StorageLiveDataFile.Default head = this.headFile;
			StorageLiveDataFile.Default file = head; // initial reference, but gets handled at the end
			do
			{
				file = file.next;
				final StorageEntity.Default tail = file.tail;
				for(StorageEntity.Default entity = file.head; (entity = entity.fileNext) != tail;)
				{
					logic.accept(entity);
				}
			}
			while(file != head);

			return logic;
		}
		
		final boolean isHeadFile(final StorageLiveDataFile.Default dataFile)
		{
			return this.headFile == dataFile;
		}

		private void addFirstFile()
		{
			// no need for a resetting catch since this method is called in a resetting context already
			this.createNewStorageFile(
				this.initialDataFileNumberProvider.provideInitialDataFileNumber(this.channelIndex())
			);
		}
		
		final void clearTransactionsFile()
		{
			if(this.fileTransactions != null)
			{
				this.fileTransactions.unregisterUsageClosing(this, null);
				this.fileTransactions = null;
			}
		}

		final void clearRegisteredFiles()
		{
			/* (07.07.2016 TM)TODO: StorageFileCloser
			 * to abstract the delicate task of closing files.
			 * Or better enhance StorageFileProvider to a StorageFileHandler
			 * that handles both creation and closing.
			 */
			this.clearTransactionsFile();

			// drop any in-flight integrity-check scan state; it refers to the file generation being cleared.
			this.clearIntegrityScope();

			if(this.headFile == null)
			{
				return; // already cleared or no files in the first place
			}

			final StorageLiveDataFile.Default headFile = this.headFile;

			StorageLiveDataFile.Default file = headFile;
			do
			{
				file.unregisterUsageClosing(this, null);
			}
			while((file = file.next) != headFile);

			this.fileCleanupCursor = this.headFile = null;
		}

		private ByteBuffer buffer(final int length)
		{
			if(length > this.standardByteBuffer.capacity())
			{
				return XMemory.allocateDirectNative(length);
			}
			this.standardByteBuffer.clear().limit(length);

			return this.standardByteBuffer;
		}

		private void clearBuffer(final ByteBuffer buffer)
		{
			buffer.clear();
			if(buffer != this.standardByteBuffer)
			{
				XMemory.deallocateDirectByteBuffer(buffer); // hope this works, not tested yet
			}
		}

		
		final void transferOneChainToHeadFile(final StorageLiveDataFile.Default sourceFile)
		{
			final StorageLiveDataFile.Default headFile = this.headFile           ;
			final StorageEntity.Default   first    = sourceFile.head.fileNext;
			      StorageEntity.Default   last     = null                    ;
			      StorageEntity.Default   current  = first                   ;

			// Reserve room for the trailing ChunkChecksumV1 that appendBytesToHeadFile will emit when the
			// destination head file carries a FileHeaderV1. Without it the dissolution write overshoots
			// fileMaximumSize by the checksum-record length and dissolution loops forever producing
			// identically oversized successors.
			final long checksumReserve         = this.chunkChecksumCalculator.reservedLengthFor(headFile);
			final long firstSourceOffset        = first.storagePosition                                  ;
			final long targetFileOldTotalLength = headFile.totalLength()                                 ;
			final long maximumFileSize          = this.dataFileEvaluator.fileMaximumSize()               ;
			// Clamp to 0: if the target file is already at/above max (legacy/imported head, or future
			// bug) freeSpace would otherwise go negative; the loop is correct with 0 too (any positive
			// entity triggers the "won't fit" branch) but the explicit clamp documents intent.
			final long freeSpace = Math.max(0L, maximumFileSize - targetFileOldTotalLength - checksumReserve);

			// A covering ChunkChecksumV1 hashes one contiguous block, so when a checksum is emitted the
			// transfer may COALESCE several live sub-runs into one compacted chunk, skipping the gaps
			// between them. When no checksum is emitted the relocation uses the zero-copy file-to-file
			// transfer, which can only move one contiguous range, so the chain breaks at the first gap.
			final boolean coalesce = checksumReserve != 0L;

			// Soft cap on a coalesced chunk's size: once the collected live bytes reach this target the
			// run is closed and the chunk emitted, keeping the transient transfer buffer (sized to
			// totalLive) bounded. Only the coalescing path buffers and is capped.
			final long coalesceChunkTargetBytes = this.dataFileEvaluator.coalesceChunkTargetBytes();

			// Live sub-runs to relocate, as physical {sourceStart, length} ranges in the source file.
			// One entry unless coalescing across gaps; their lengths sum to totalLive.
			final BulkList<long[]> subRanges = BulkList.New();
			      long runStart  = firstSourceOffset;
			      long runLen    = 0L               ;
			      long totalLive = 0L               ;

			/*
			 * Collecting the transfer chain has 2 abort conditions:
			 * 1.) the current entity's length would not fit in the target file's remaining space
			 * 2.) the source file's tail is reached (the whole live chain has been collected)
			 * A gap between entities (next entity's position is not the expected position) no longer
			 * aborts the collection when coalescing; it merely closes the current sub-run and opens a
			 * new one after the gap. As the method is guaranteed to be called on a non-empty file, the
			 * first entity is never the tail.
			 */
			while(current != sourceFile.tail)
			{
				// check for enough free space
				if(totalLive + current.length > freeSpace)
				{
					// if there is already something to transfer, break and copy it
					if(totalLive != 0L)
					{
						break;
					}

					// Nothing to transfer yet. Roll over to the next head file ONLY if the current head
					// already holds relocatable content (live entity bytes OR reclaimable gap bytes); rolling
					// over a head that has only protocol overhead (FileHeaderV1 / ChunkChecksumV1) produces an
					// identically-shaped successor and loops forever. Subtract metaLength() rather than testing
					// dataLength(): totalLength() is non-zero once a FileHeaderV1 is emitted, but an all-gap head
					// (every entity GC'd) still has reclaimable gap bytes and must roll over, as it did before
					// the checksum feature (old guard: totalLength() != 0).
					if(headFile.totalLength() - headFile.metaLength() != 0L)
					{
						this.createNextStorageFile();
						return;
					}

					// nothing to transfer yet and head has no entity content — singleton oversized
					// entity falls through and is written into this head file as the only option.
				}

				// gap (intervening checksum record or dead-entity gap): the next live entity is not
				// physically adjacent to the current sub-run.
				if(current.storagePosition != runStart + runLen)
				{
					if(!coalesce)
					{
						// no coalescing: stop at the first gap so the relocation stays one contiguous range.
						break;
					}
					// close the current sub-run and start a new one after the gap.
					subRanges.add(new long[]{runStart, runLen});
					runStart = current.storagePosition;
					runLen   = 0L                     ;
				}

				// set new file. Enqueuing in the file's item chain is done for the whole sub chain
				current.typeInFile      = headFile.typeInFile(current.typeInFile.type);

				// update position to the COMPACTED layout in the target file (old length plus live bytes so far)
				current.storagePosition = XTypes.to_int(targetFileOldTotalLength + totalLive);

				// advance to next entity and add current entity's length to the running totals
				runLen    += current.length;
				totalLive += current.length;
				current = (last = current).fileNext;

				// soft size cap: emit the coalesced chunk once it reaches the target. The current entity
				// was already added, so a single entity larger than the target still forms its own chunk.
				if(coalesce && totalLive >= coalesceChunkTargetBytes)
				{
					break;
				}
			}
			// close the trailing sub-run.
			if(runLen != 0L)
			{
				subRanges.add(new long[]{runStart, runLen});
			}

			// can only reach here if there is at least one entity to transfer

			// update source file to keep consistency as it might not be cleared completely
			sourceFile.removeHeadBoundChain(current, totalLive);

			// update target file's content length. Must be done here as next transfer depends on updated length
			headFile.addChainToTail(first, last);

			this.appendBytesToHeadFile(sourceFile, subRanges, firstSourceOffset, totalLive, checksumReserve);

			// derive fullness state of target file. Can happen on exact fit or oversized single entity.
			if(totalLive >= freeSpace)
			{
				this.createNextStorageFile();
			}
		}

		private void appendBytesToHeadFile(
			final StorageLiveDataFile.Default sourceFile     ,
			final BulkList<long[]>            subRanges      ,
			final long                        firstSourceOffset,
			final long                        totalLive      ,
			final long                        checksumReserve
		)
		{

			final StorageLiveDataFile.Default headFile = this.headFile;

			final long    headFileLength = headFile.totalLength()                       ;
			final long    timestamp      = this.timestampProvider.currentNanoTimestamp();
			final boolean emitChecksum   = checksumReserve != 0L                        ;

			if(emitChecksum)
			{
				/*
				 * The relocated chain must carry its own covering ChunkChecksumV1, otherwise the next
				 * normal commit's ChunkChecksumV1 would implicitly cover these un-hashed bytes and fail
				 * verification at the next load. The bytes are not otherwise in memory, so read the live
				 * sub-runs out of the source file (skipping the gaps) into one compacted block, hash it,
				 * and write the block plus a single covering record as one gathering write. writeStore
				 * (not the bare write) so the backup mirrors the relocated bytes and their checksum.
				 */
				final int        totalLiveInt = X.checkArrayRange(totalLive)              ;
				final ByteBuffer dataBuffer    = XMemory.allocateDirectNative(totalLiveInt);
				final ByteBuffer metaBuffer    = this.chunkChecksumBuffer; // reused; cleared before each use
				metaBuffer.clear();
				try
				{
					// concatenate every live sub-run into the compacted block (gaps are skipped).
					// readBytes fills at the buffer's current position and advances it, so successive
					// sub-runs append; the lengths sum to totalLive, exactly filling the buffer. Each
					// readBytes pins the buffer's limit to position+length, so the full limit must be
					// restored before the next read or its remaining-space guard would see zero.
					for(final long[] subRange : subRanges)
					{
						dataBuffer.limit(totalLiveInt);
						sourceFile.readBytes(dataBuffer, subRange[0], subRange[1]);
					}
					dataBuffer.flip();
					// the compacted block is appended at headFileLength, so that is the chunk start the record stores.
					this.chunkChecksumCalculator.writeChunkChecksumRecord(headFile, headFileLength, metaBuffer, dataBuffer);
					metaBuffer.flip();

					final ByteBuffer[] gather   = {dataBuffer, metaBuffer}                                            ;
					final long         expected = totalLive + this.chunkChecksumCalculator.chunkChecksumRecordLength();

					this.writeStoreExactOrTruncate(headFile, X.ArrayView(gather), expected, headFileLength, "Data transfer");
				}
				finally
				{
					// metaBuffer is the reused chunkChecksumBuffer field — not freed here.
					XMemory.deallocateDirectByteBuffer(dataBuffer);
				}

				// the gather write succeeded (otherwise an exception propagated above): commit the chained-tip
				// advance for this coalesced chunk so the next coalesced chunk chains from it.
				this.chunkChecksumCalculator.commitChunkWrite(headFile);

				// chain entity bytes contribute to both fileTotalLength and fileDataLength;
				// the covering ChunkChecksumV1 entry is a negative-length record and contributes only
				// to fileTotalLength (gap-length semantics — matches load-side accounting).
				headFile.increaseContentLength(totalLive);
				headFile.registerMetaLength(this.chunkChecksumCalculator.chunkChecksumRecordLength());
			}
			else
			{
				// No checksum to emit: the chain was collected as a single contiguous run starting at
				// firstSourceOffset, so relocate it with the zero-copy, backup-aware file-to-file
				// transfer (no in-memory buffering, relocated bytes stay mirrored to the backup).
				long bytes = this.writer.writeTransfer(sourceFile, firstSourceOffset, totalLive, headFile);
				if(totalLive != bytes)
				{
					logger.error("Data transfer error! Expected {} bytes transferred to head file but only {} bytes had been transferred! Trying again.", totalLive, bytes);
					headFile.truncate(headFileLength);

					bytes = this.writer.writeTransfer(sourceFile, firstSourceOffset, totalLive, headFile);
					if(totalLive != bytes)
					{
						logger.error("Data transfer retry error! Expected {} bytes transferred to head file but only {} bytes had been transferred! Aborting!", totalLive, bytes);
						throw new StorageExceptionIoWriting("Transfer to head file failed, only " + bytes + " of " + totalLive + " bytes transferred.");
					}
				}

				// (15.02.2019 TM)NOTE: changed from arithmetic inside #addChainToTail to directly using copyLength in here.
				headFile.increaseContentLength(totalLive);
			}

			final long newHeadFileLength = headFile.totalLength();
			// One transfer entry suffices for a coalesced (or single-run) write: init validates only
			// the monotonic head-file length; the source number/offset are informational. The offset
			// records the first sub-run's start.
			this.writeTransactionsEntryTransfer(sourceFile, firstSourceOffset, totalLive, timestamp, newHeadFileLength);

			/*
			 * Note:
			 * It can happen that a transfer is written completely but the process terminates right before the
			 * transactions entry for it is written.
			 * This causes the next initialization to truncate a perfectly fine and complete transfer chunk
			 * because it cannot find the transactions entry validating that chunk.
			 * However, this is not a problem but happens by design. Data can never be lost by this behavior:
			 * If the process terminates before the entry write can be executed, there can also be no subsequent
			 * file cleanup that deletes the old data file. Hence, the transferred data still exists within it
			 * and gets registered as live data on the next initialization (and probably gets transferred then
			 * once again).
			 */
		}
	
		final StorageLiveDataFile.Default createLiveDataFile(
			final AFile file        ,
			final int   channelIndex,
			final long  number
		)
		{
			return new StorageLiveDataFile.Default(
				            this         ,
				    notNull(file)        ,
				notNegative(channelIndex),
				notNegative(number)
			);
		}

		private void createNewStorageFile(final long fileNumber)
		{

			final AFile file = this.fileProvider.provideDataFile(
				this.channelIndex(),
				fileNumber
			);
			file.ensureExists();

			/*
			 * File#length is incredibly slow compared to FileChannel#size (although irrelevant here),
			 * but still the file length has to be checked before the channel is created, etc.
			 */
			if(!file.isEmpty())
			{
				throw new StorageExceptionIoWriting("New storage file is not empty: " + file);
			}

			// capture the current head as the chain predecessor BEFORE registerStorageHeadFile reassigns
			// this.headFile; null for the first file (chain seeds from the configured initial seed).
			final StorageLiveDataFile.Default predecessor = this.headFile;

			// create and register StorageFile instance with an attached channel
			final StorageLiveDataFile.Default dataFile = this.createLiveDataFile(file, this.channelIndex(), fileNumber);
			this.registerStorageHeadFile(dataFile);

			// Emit FileHeaderV1 as the new file's first content (only when the policy emits). The
			// transaction-log entry below records the initial length so load-time consistency checks
			// see physical-size == expected-size. When the policy does not emit, no FileHeaderV1 is
			// written, the file's chunkChecksumKind stays 0 (indistinguishable from a legacy file), the
			// chunk-checksum gates in storeChunks/dissolution/import emit nothing, and the initial
			// length is 0.
			final long initialFileLength = this.chunkChecksumCalculator.isWriting()
				? this.chunkChecksumCalculator.fileHeaderRecordLength()
				: 0L
			;
			if(initialFileLength != 0L)
			{
				this.writeFileHeaderRecord(dataFile, predecessor);
			}

			this.writeTransactionsEntryFileCreation(
				initialFileLength                                    ,
				this.timestampProvider.currentNanoTimestamp()        ,
				fileNumber
			);
		}

		/**
		 * Writes the file-header meta record (as produced by the {@link StorageChunkChecksumCalculator})
		 * as the new file's first negative-length entry, registers it as gap length and caches the
		 * header state in memory. Only called when the calculator is writing (see
		 * {@link #createNewStorageFile(long)}).
		 */
		private void writeFileHeaderRecord(
			final StorageLiveDataFile.Default file       ,
			final StorageLiveDataFile.Default predecessor
		)
		{
			// chain root stamped into the header: predecessor's tip (chained) / all-zero (non-chained).
			final byte[]     chainRoot    = this.chunkChecksumCalculator.nextChainRoot(predecessor);
			final long       headerLength = this.chunkChecksumCalculator.fileHeaderRecordLength();
			final ByteBuffer headerBuffer = this.fileHeaderBuffer; // reused; cleared before each use
			headerBuffer.clear();
			this.chunkChecksumCalculator.writeFileHeaderRecord(headerBuffer, chainRoot);
			headerBuffer.flip();

			// writeStore (not the bare write) so the backup mirrors the FileHeaderV1 record.
			final long bytesWritten = this.writer.writeStore(file, this.fileHeaderBufferWrap);
			if(bytesWritten != headerLength)
			{
				throw new StorageExceptionIoWriting(
					"Expected " + headerLength
					+ " bytes for file-header meta record but wrote " + bytesWritten
				);
			}

			// Update file accounting and cache the header state in memory so the rest of the session
			// sees the same state a re-load would produce. The file-header meta record is a negative-
			// length entry: it counts toward fileTotalLength but NOT fileDataLength (matches load-side
			// semantics in StorageEntityInitializer).
			file.registerMetaLength(headerLength);
			this.chunkChecksumCalculator.cacheFileHeaderOn(file, chainRoot);
		}
		
		private void registerStorageHeadFile(final StorageLiveDataFile.Default storageFile)
		{
			if(this.headFile == null)
			{
				// initialization special case
				storageFile.next = storageFile.prev = storageFile;
			}
			else
			{
				// join in chain
				storageFile.next = this.headFile.next;
				storageFile.prev = this.headFile;
				this.headFile.next.prev = storageFile;
				this.headFile.next = storageFile;
			}

			// in the end the file is set as current head in any case
			this.headFile = storageFile;
		}

		@Override
		public final int channelIndex()
		{
			return this.channelIndex;
		}

		@Override
		public final StorageLiveDataFile.Default currentStorageFile()
		{
			return this.headFile;
		}

		@Override
		public void iterateStorageFiles(final Consumer<? super StorageLiveDataFile> procedure)
		{
			// keep current als end marker, but start with first file, use current als last and then quit the loop
			final StorageLiveDataFile.Default current = this.headFile;
			StorageLiveDataFile.Default file = current;
			do
			{
				procedure.accept(file = file.next);
			}
			while(file != current);
		}

		private void checkForNewFile()
		{
			if(this.headFile == null || !this.headFile.needsRetirement(this.dataFileEvaluator))
			{
				return;
			}

			// Prompt, ungated rollover: files stay ~fileMaximumSize (no over-fill, no single-channel
			// starvation). Eager durability comes from the store-task barrier fsync and the
			// housekeeping/flush-completion pass; a residual below-baseline is a loud fail-stop in recovery.
			this.createNextStorageFile();
		}

		/**
		 * Synchronizes this channel's storage files: head data file first, then the transactions
		 * log - a durable store entry whose chunk bytes are still volatile would fail the next
		 * start's length validation, so data must reach the medium before its metadata (the same
		 * order the deletion barrier uses). Sealed data files were already synchronized at their
		 * rollover. Processing part of {@link StorageRequestTaskStorageFlush}.
		 */
		final boolean flushStorage()
		{
			if(!this.writeController.isWritable())
			{
				// read-only: nothing was synchronized, so the all-durable watermark must not advance
				// and the durability gates keep deferring. Deliberately NO re-arm of the flush request
				// here: this method cannot tell a gate-requested barrier from a PURE public one, and a
				// pure barrier must not manufacture a request (it would produce a maintenance pass no
				// gate ever asked for). A skipped gate barrier loses nothing - its adjacent maintenance
				// still runs, and every gate with deferred work re-arms the request itself.
				return false;
			}
			this.synchronizeStorageFiles();
			return true;
		}

		/**
		 * Synchronizes the head data file, then the transactions log - data before its metadata, so
		 * a durable store entry never precedes its own chunk bytes on the medium. Unguarded: the
		 * deletion barrier must synchronize regardless of {@link StorageWriteController#isWritable()}
		 * (the source file is about to be unlinked), so it calls this directly; {@link #flushStorage()}
		 * wraps it in the writability guard.
		 */
		private void synchronizeStorageFiles()
		{
			if(this.headFile != null)
			{
				this.headFile.synchronize();
			}
			if(this.fileTransactions != null)
			{
				this.fileTransactions.synchronize();
			}
		}

		/**
		 * Called after an all-channel storage flush completed successfully: every store below
		 * the passed task timestamp is fully durable on every channel, so deferred file
		 * lifecycle events below it may proceed with the next housekeeping check.
		 * <p>
		 * Pure watermark raise. Deliberately does NOT clear {@link #storageFlushRequested}: both
		 * barrier flavors run this, but only the gate-requested one provides the maintenance slot
		 * the request demands - a PURE public barrier clearing the request would strand the
		 * deferred work it promises (e.g. an unconditional log compaction that periodic size-gated
		 * passes can never pick up). The clear happens in
		 * {@link #executeDurabilityCoveredMaintenance(long)}, the request's actual fulfillment.
		 */
		final void commitStorageFlush(final long allDurableTimestamp)
		{
			if(allDurableTimestamp > this.allDurableStoreTimestamp)
			{
				this.allDurableStoreTimestamp = allDurableTimestamp;
			}
		}

		/**
		 * Whether the passed store timestamp is durable on every channel - the single durability
		 * predicate the rollover, deletion and compaction gates share.
		 */
		private boolean isStoreDurable(final long storeTimestamp)
		{
			return storeTimestamp <= this.allDurableStoreTimestamp;
		}

		/**
		 * Executes the durability-gated maintenance this channel may have deferred, at the one
		 * moment the gates pass by construction: within the storage-flush task's processing
		 * (post-completion, after the watermark raise) this channel cannot yet have processed any
		 * store task queued behind the flush, so its latest written store is provably durable on
		 * every channel - and stays so for the whole
		 * pass. Waiting for the next housekeeping cycle instead would race the next store under
		 * sustained traffic: a store per cycle re-defers the periodic dissolution forever, so this
		 * completion pass is the ONLY reclamation slot under sustained traffic and must run at the
		 * full housekeeping file-check budget - a token slice here caps the reclamation rate far
		 * below the write rate and lets garbage accumulate without bound.
		 *
		 * @param nanoTimeBudget the time budget for the file-cleanup pass - the caller passes the
		 *        housekeeping file-check time budget.
		 */
		final void executeDurabilityCoveredMaintenance(final long nanoTimeBudget)
		{
			// This pass IS the fulfillment of a pending flush request, so the request is cleared
			// here and only here - never at the bare watermark raise (commitStorageFlush), which a
			// PURE public barrier also performs without providing this slot. Cleared only when the
			// watermark caught up (a skipped barrier raised nothing); the gates below re-arm it for
			// any work they must defer again.
			if(this.isStoreDurable(this.lastWrittenStoreTimestamp))
			{
				this.storageFlushRequested = false;
			}

			// both entry points are self-guarded no-ops when nothing is pending
			this.checkForNewFile();
			// Honor a deferred UNCONDITIONAL (checkSize=false) log cleanup unconditionally: an explicit
			// issueTransactionsLogCleanup() promises compaction regardless of the log's current size, so
			// completing it size-gated would silently skip a below-limit garbage-heavy log. Consumed
			// here (the store is durable by construction at barrier completion, so it no longer defers).
			final boolean checkSize = !this.unconditionalCompactionPending;
			this.unconditionalCompactionPending = false;
			this.internalTransactionFileCheck(checkSize);

			// the deferred dissolution/deletion work at the configured file-check budget, see above.
			// The deadline is taken fresh so a slow compaction above cannot starve the cleanup pass.
			// Skipped if cleanup is off.
			if(this.isFileCleanupEnabled())
			{
				this.internalCheckForCleanup(
					XTime.calculateNanoTimeBudgetBound(nanoTimeBudget),
					this.dataFileEvaluator
				);
			}
		}

		final boolean pollStorageFlushRequest()
		{
			if(!this.storageFlushRequested)
			{
				return false;
			}
			// read-only: leave the request armed and do not signal an enqueue. A barrier issued now
			// would only skip on every channel, churning one futile all-channel rendezvous per
			// housekeeping cycle for the whole read-only window; it fires on the first cycle after
			// writability returns instead.
			if(!this.writeController.isWritable())
			{
				return false;
			}
			// cleared on poll: one enqueued task per request; a still-failing gate re-requests
			this.storageFlushRequested = false;
			return true;
		}

		final void requestStorageFlush()
		{
			this.storageFlushRequested = true;
		}

		/**
		 * Gates the physical deletion of a dissolved file (transactions entry and unlink alike)
		 * on all-channel durability: rolling back a not-everywhere-durable store truncates the
		 * transfers logged above it, and their bytes are then re-acquired from the source files
		 * - which must therefore still exist. Deferred deletions are revisited by the file
		 * cleanup cursor once the requested storage flush raised the watermark.
		 */
		private boolean deletionDurabilityGatePassed(final StorageLiveDataFile.Default file)
		{
			Long guardTimestamp = this.durabilityDeferredDeletes.get(file.number());
			if(guardTimestamp == null)
			{
				guardTimestamp = this.lastWrittenStoreTimestamp;
			}
			if(this.isStoreDurable(guardTimestamp))
			{
				this.durabilityDeferredDeletes.removeFor(file.number());
				return true;
			}
			this.durabilityDeferredDeletes.put(file.number(), guardTimestamp);
			this.storageFlushRequested = true;
			return false;
		}

		private boolean isDurabilityDeferred(final StorageLiveDataFile.Default file)
		{
			return this.durabilityDeferredDeletes.get(file.number()) != null;
		}

		/**
		 * Retires the head file and opens its successor. The new file-creation entry collapses the
		 * reload's rollback baseline onto the latest store, so recovery can never cut below it: a
		 * consensus below a channel's head-file baseline is a loud fail-stop
		 * ({@link #reconcileForHeadOnlyRecovery}), recovery being head-file only.
		 * <p>
		 * No caller gates this on durability; instead every rollover is made EAGERLY durable, so the
		 * store the baseline collapses onto is all-channel durable before a crash:
		 * <ul>
		 *   <li>a rollover during a store task is fsync'd on every channel at that task's completion
		 *       barrier (see {@code StorageRequestTaskStoreEntities});</li>
		 *   <li>a rollover with no following store (housekeeping dissolution, import) requests a flush
		 *       and is fsync'd at the flush-barrier completion ({@link #executeDurabilityCoveredMaintenance(long)}).</li>
		 * </ul>
		 * A NEW caller MUST preserve this: sealing a not-yet-all-durable store with no fsync catching up
		 * before a crash lets a power loss leave a consensus below the new baseline that BRICKS the
		 * storage (permanent startup refusal), not merely degraded recovery.
		 */
		final void createNextStorageFile()
		{
			// Durability floor: the outgoing head is now complete and will not be appended again.
			// Forcing it here means a power loss can strand at most the current head's tail, never a
			// sealed (long-committed) file. Also covers a head that received transferred bytes and
			// then rolled over mid-dissolution.
			this.headFile.synchronize();
			this.createNewStorageFile(this.headFile.number() + 1);

			// Eager rollover durability: the new file's creation entry collapses the recovery baseline
			// onto the latest store, which must be all-channel durable before a crash (else a power loss
			// leaves an unrecoverable consensus below the baseline).
			// (1) flag the rollover so the current task's completion barrier makes it durable everywhere;
			//     also covers a housekeeping rollover picked up by the next store task.
			// (2) if the sealed store is not yet all-durable, request a flush so a rollover with no
			//     following store on this channel (housekeeping / import) still becomes durable soon.
			this.rolloverOccurred = true;
			if(!this.isStoreDurable(this.lastWrittenStoreTimestamp))
			{
				this.storageFlushRequested = true;
			}
		}

		/**
		 * Reads and clears the rollover flag: whether this channel rolled a file over since the last
		 * poll. Called once per task on the channel thread, so the plain read-and-clear is race-free.
		 */
		final boolean pollRolloverOccurred()
		{
			final boolean occurred = this.rolloverOccurred;
			this.rolloverOccurred = false;
			return occurred;
		}
		
		private long ensureHeadFileTotalLength()
		{
			final long physicalLength = this.headFile.size();
			final long expectedLength = this.headFile.totalLength();
			
			if(physicalLength != expectedLength)
			{
				throw new StorageExceptionIoWriting(
					"Physical length " + physicalLength
					+ " of current head file " + this.headFile.number()
					+ " is not equal its expected length of " + expectedLength
				);
			}
			
			return physicalLength;
		}
		
		/**
		 * Two-stage pre-write rollover for the upcoming gather chunk:
		 * <ol>
		 *   <li>The total commit size must fit in an int (single-buffer load constraint, 2 GiB cap);
		 *       if the cumulative would push the head past 2^31, rotate the head before writing.</li>
		 *   <li>The chunk plus the trailing {@code ChunkChecksumV1} (when applicable) must not push
		 *       the head past {@code fileMaximumSize}. {@link #checkForNewFile()} only retires a head
		 *       that is ALREADY at/above max; without this second guard the last store before
		 *       retirement overshoots by exactly {@code entityBytes + checksumReserve}.
		 *       If the head has no entity bytes yet (only protocol envelope, e.g. just a
		 *       {@code FileHeaderV1}), rolling over cannot help — keep the overshoot rather than
		 *       create an empty successor file (mirrors the singleton fall-through in
		 *       {@link #transferOneChainToHeadFile}).</li>
		 * </ol>
		 */
		private void ensureHeadFitsUpcomingChunk(final ByteBuffer[] dataBuffers)
		{
			long chunkSize = 0;
			for(int i = 0; i < dataBuffers.length; i++)
			{
				chunkSize += dataBuffers[i].limit();
			}
			if(chunkSize > Integer.MAX_VALUE)
			{
				// Should never be reached — handled by StorageDataChunkValidator.MaxFileSize.
				// Kept as a second guard in case the validator is replaced.
				throw new StorageExceptionCommitSizeExceeded(this.channelIndex(), chunkSize);
			}
			if(chunkSize + this.headFile.totalLength() > Integer.MAX_VALUE)
			{
				logger.debug("creating new storage file because appending would exceed the file size limit of 2^31 bytes");
				this.createNextStorageFile();
			}

			final long checksumReserve = this.chunkChecksumCalculator.reservedLengthFor(this.headFile);
			final long maximumFileSize = this.dataFileEvaluator.fileMaximumSize();
			final long projectedTotal  = this.headFile.totalLength() + chunkSize + checksumReserve;
			// Defence in depth: even on a fresh head (dataLength == 0) we refuse to produce a file
			// that the load path can no longer read back into a single 2 GiB direct buffer. This
			// guards against a user wiring a no-op validator that bypasses MaxFileSize. Strict '>' to
			// match the load limit (StorageEntityInitializer rejects only > Integer.MAX_VALUE, and
			// X.checkArrayRange accepts exactly Integer.MAX_VALUE) and the chunkSize guards above.
			if(projectedTotal > Integer.MAX_VALUE)
			{
				throw new StorageExceptionCommitSizeExceeded(this.channelIndex(), projectedTotal);
			}
			if(projectedTotal > maximumFileSize
				&& this.headFile.dataLength() != 0L)
			{
				this.createNextStorageFile();
			}
		}

		@Override
		public final long[] storeChunks(final long timestamp, final ByteBuffer[] dataBuffers)
			throws StorageExceptionIoWritingChunk
		{
			if(dataBuffers.length == 0)
			{
				// nothing to write (empty chunk, only header for consistency). Clear any pending lengths so a
				// prior rolled-back store's stale values cannot be applied by this store's commitWrite.
				this.uncommittedDataLength = 0L;
				this.uncommittedMetaLength = 0L;
				return new long[0];
			}

			this.checkForNewFile();
			this.ensureHeadFitsUpcomingChunk(dataBuffers); // 2 GiB / fileMaximumSize check + possible head rollover

			final long   oldTotalLength   = this.ensureHeadFileTotalLength();
			final long[] storagePositions = allChunksStoragePositions(dataBuffers, oldTotalLength);

			// Single-write contract: compute the checksum over dataBuffers, build the ChunkChecksumV1
			// meta-record buffer, and submit the entity buffers plus the meta buffer as one gathering
			// write (no separate write, no intermediate fsync).
			//
			// Emit the ChunkChecksumV1 only when the policy emits AND the head file carries a
			// FileHeaderV1 (chunkChecksumKind() != 0; no automatic upgrade of legacy files). A legacy
			// head file has none, so emitting a checksum there would make the load walker hash from
			// file offset 0 across un-covered bytes and throw; such files accumulate unprotected writes
			// until they roll over to a fresh FileHeaderV1 file. ensureHeadFitsUpcomingChunk above may
			// have rolled the head over, so the gate re-checks coversChunkWrites against the new head.
			final long writeCount;
			if(this.chunkChecksumCalculator.coversChunkWrites(this.headFile))
			{
				final long       metaLength = this.chunkChecksumCalculator.chunkChecksumRecordLength();
				final ByteBuffer metaBuffer = this.chunkChecksumBuffer; // reused; cleared before each use
				metaBuffer.clear();
				// the checksum write computes the chained tip but does NOT advance the file tip; that happens
				// in commitWrite() via commitChunkWrite(). A rolled-back/failed store thus never advances it.
				// the chunk's data is appended at oldTotalLength (the head file's length before this write), so
				// that is the in-file chunk start the covering record records for the load-walk cross-check.
				this.chunkChecksumCalculator.writeChunkChecksumRecord(this.headFile, oldTotalLength, metaBuffer, dataBuffers);
				metaBuffer.flip();

				final ByteBuffer[] gather = new ByteBuffer[dataBuffers.length + 1];
				System.arraycopy(dataBuffers, 0, gather, 0, dataBuffers.length);
				gather[dataBuffers.length] = metaBuffer;

				writeCount = this.writer.writeStore(this.headFile, X.ArrayView(gather));

				// Split the writeCount: entity bytes go to fileDataLength via increaseContentLength
				// at commit time; meta-record bytes go to fileTotalLength via registerGapLength
				// (matches load-side semantics). The two fields are accumulated here and consumed
				// by commitWrite()/rollbackWrite().
				this.uncommittedDataLength = writeCount - metaLength;
				this.uncommittedMetaLength = metaLength;
			}
			else
			{
				// Legacy head file: write entity buffers only, exactly as the pre-feature engine did.
				writeCount = this.writer.writeStore(this.headFile, X.ArrayView(dataBuffers));
				this.uncommittedDataLength = writeCount;
				this.uncommittedMetaLength = 0L;
			}

			final long newTotalLength = oldTotalLength + writeCount;

			if(newTotalLength != this.headFile.size())
			{
				throwImpossibleStoreLengthException(timestamp, oldTotalLength, writeCount, dataBuffers);
			}

			this.writeTransactionsEntryStore(this.headFile, oldTotalLength, writeCount, timestamp, newTotalLength);

			this.restartFileCleanupCursor();

			return storagePositions;
		}

		@Override
		public final void rollbackWrite()
		{
				// The chained tip is advanced only by commitWrite, so the file tip is already correct here
				// and left as-is. Only roll back the on-disk bytes if an uncommitted write actually happened
				// (size grew past the committed length). Channels that wrote nothing (e.g. empty chunk) must
				// not accrue a spurious truncation entry.
				if(this.headFile.totalLength() != this.headFile.size())
				{
						final long timestamp = this.timestampProvider.currentNanoTimestamp();

						// write truncation entry (BEFORE the actual truncate), mirroring handleLastFile, so the
						// transaction log and the data file stay consistent after a rolled-back partial store.
						this.writeTransactionsEntryFileTruncation(this.headFile, timestamp, this.headFile.totalLength());

						this.writer.truncate(this.headFile, this.headFile.totalLength(), this.fileProvider);
				}

				// The store is abandoned: discard its pending length contributions so a later commitWrite
				// (e.g. a subsequent empty-chunk store that returns early without re-setting these) cannot
				// apply the stale values and spuriously advance the chained tip for a chunk never written.
				this.clearUncommittedDataLength();
		}


		@Override
		public final void commitWrite()
		{
			// commit length: entity bytes contribute to both fileTotalLength and fileDataLength,
			// meta-record bytes contribute to fileTotalLength and fileMetaLength (so dataFillRatio
			// treats them as useful overhead rather than reclaimable gap — matches load-side
			// accounting in StorageEntityInitializer).
			this.headFile.increaseContentLength(this.uncommittedDataLength);
			if(this.uncommittedMetaLength != 0L)
			{
				this.headFile.registerMetaLength(this.uncommittedMetaLength);
				// a covering chunk checksum was written this store: now that it is committed, advance the
				// chained tip on the head file (no-op for non-chained kinds).
				this.chunkChecksumCalculator.commitChunkWrite(this.headFile);
			}

			// reset the length change helper fields
			this.clearUncommittedDataLength();
		}

		final void clearUncommittedDataLength()
		{
			this.uncommittedDataLength     = 0;
			this.uncommittedMetaLength     = 0;
		}
		
		final void loadData(
			final StorageLiveDataFile.Default dataFile   ,
			final StorageEntity.Default       entity     ,
			final long                        length     ,
			final long                        cacheChange
		)
		{
			final ByteBuffer dataBuffer = this.buffer(X.checkArrayRange(length));
			try
			{
				dataFile.readBytes(dataBuffer, entity.storagePosition);
				this.putLiveEntityData(entity, XMemory.getDirectByteBufferAddress(dataBuffer), length, cacheChange);
			}
			catch(final StorageExceptionIoReading e)
			{
				throw e;
			}
			catch(final Exception e)
			{
				// (10.12.2014 TM)EXCP: report relevant values
				throw new StorageExceptionIoReading(e);
			}
			finally
			{
				this.clearBuffer(dataBuffer);
			}
		}

		private void putLiveEntityData(
			final StorageEntity.Default entity     ,
			final long                         address    ,
			final long                         length     ,
			final long                         cacheChange
		)
		{
			entity.putCacheData(address, length);
			this.entityCache.modifyUsedCacheSize(cacheChange);
		}

		@Override
		public final StorageInventory readStorage()
		{
			if(this.headFile != null)
			{
				throw new StorageExceptionIoReading(this.channelIndex() + " already initialized");
			}

			final StorageTransactionsAnalysis      transactionsAnalysis = this.readTransactionsFile();
			final EqHashTable<Long, StorageDataInventoryFile> dataFiles = EqHashTable.New();
			this.fileProvider.collectDataFiles(
				StorageDataInventoryFile::New,
				f ->
					dataFiles.add(f.number(), f),
				this.channelIndex()
			);
			dataFiles.keys().sort(XSort::compare);

			return StorageInventory.New(this.channelIndex(), dataFiles, transactionsAnalysis);
		}

		final StorageTransactionsAnalysis readTransactionsFile()
		{
			final StorageLiveTransactionsFile file = this.createTransactionsFile();

			// heal an interrupted transaction-log compaction
			this.transactionsFileRestored = StorageTransactionsFileCleaner.recoverInterruptedCompaction(
				file.file()         ,
				this.channelIndex() ,
				this.writeController
			);

			if(!file.exists())
			{
				/* (11.09.2014 TM)TODO: missing transactions file handler function
				 * default implementation just returns null.
				 * Also see TO-DO for derive function.
				 */
				return null;
			}

			try
			{
				try
				{
					final StorageTransactionsAnalysis.EntryAggregator
						aggregator = file.processBy(new StorageTransactionsAnalysis.EntryAggregator(this.channelIndex()));
					return aggregator.yield(file);
				}
				catch(final StorageExceptionIncompleteTransactionsEntry e)
				{
					this.truncateTornTransactionsFileTail(file, e);
					final StorageTransactionsAnalysis.EntryAggregator
						aggregator = file.processBy(new StorageTransactionsAnalysis.EntryAggregator(this.channelIndex()));
					return aggregator.yield(file);
				}
			}
			catch(final Exception e)
			{
				StorageClosableFile.close(file, e);
				throw new StorageException(e);
			}
		}

		/**
		 * A torn trailing entry is the expected artifact of a crash during an append: the content
		 * before it is intact and the torn entry's transaction never completed. Truncate it away,
		 * like a torn data file tail.
		 */
		private void truncateTornTransactionsFileTail(
			final StorageLiveTransactionsFile                 file ,
			final StorageExceptionIncompleteTransactionsEntry cause
		)
		{
			if(!this.writeController.isWritable())
			{
				throw cause;
			}
			logger.warn(
				"Channel {}: truncating torn transactions file tail at position {}",
				this.channelIndex(),
				cause.position(),
				cause
			);
			this.writer.truncate(file, cause.position(), this.fileProvider);
		}

		private long validateStorageDataFilesLength(
			final StorageInventory                            storageInventory             ,
			final EqHashTable<Long, StorageDataInventoryFile> supplementedMissingEmptyFiles
		)
		{
			final StorageTransactionsAnalysis tFileAnalysis = storageInventory.transactionsFileAnalysis();
			long unregisteredEmptyLastFileNumber = -1; // -1 for "none"

			if(tFileAnalysis == null || tFileAnalysis.transactionsFileEntries().isEmpty())
			{
				// no transaction file (content) present. Abort and derive later.
				// (06.09.2014 TM)TODO: configurable MissingTransactionsFileHandler callback
				return unregisteredEmptyLastFileNumber;
			}

			final XGettingSequence<StorageDataInventoryFile> dataFiles   = storageInventory.dataFiles().values();
			final EqHashTable<Long, StorageTransactionEntry> fileEntries = EqHashTable.New(tFileAnalysis.transactionsFileEntries());
			final StorageDataInventoryFile                   lastFile    = dataFiles.peek();

			for(final StorageDataInventoryFile file : dataFiles)
			{
				final long actualFileLength = file.size();

				// retrieve and remove (= mark as already handled) the corresponding file entry
				final StorageTransactionEntry entryFile = fileEntries.removeFor(file.number());
				if(entryFile == null)
				{
					// special case: empty file was created but not registered, can be safely ignored
					if(file == lastFile && actualFileLength == 0)
					{
						unregisteredEmptyLastFileNumber = file.number();
						continue;
					}

					// if the transactions file is present, it must be consistent (i.e. account for all files)
					throw new StorageException(
						this.channelIndex() + " could not find transactions entry for file " + file.number()
					);
				}

				/* (18.06.2015 TM)TODO: handle files registered as deleted but not deleted yet
				 * files that were registered as deleted but still linger around can/must be
				 * safely deleted and removed from the dataFiles collection.
				 */

				// compare file lengths (head file special case: can be valid if longer, i.e. uncommitted write)
				if(entryFile.length() == actualFileLength || file == lastFile && entryFile.length() < actualFileLength)
				{
					// actual file length is valid
					continue;
				}

				// inconsistent file length compared to transactions file, throw exception
				throw new StorageExceptionConsistency(
					this.channelIndex() + " Length " + actualFileLength + " of file "
					+ file.number() + " is inconsistent with the transactions entry's length of " + entryFile.length()
				);
			}
			
			// check that all remaining file entries are deleted files. No non-deleted file may be missing!
			for(final StorageTransactionEntry remainingFileEntry : fileEntries.values())
			{
				if(remainingFileEntry.isDeleted())
				{
					continue;
				}
				
				if(remainingFileEntry.isEmpty())
				{
					this.supplementedMissingEmptyFile(
						supplementedMissingEmptyFiles,
						remainingFileEntry.fileNumber()
					);
					continue;
				}

				throw new StorageException(
					"Non-deleted non-empty data file not found: channel " + this.channelIndex()
					+ ", file " + remainingFileEntry.fileNumber()
				);
			}

			/*
			 * At this point it is guaranteed that all transactions entries and existing files are viable in
			 * terms of file lengths. Viable means exact same length for any non-last file and actual file
			 * length equal to or greater than logged length for last file (i.e. there can be one uncommitted
			 * write at the end which can be safely truncated later on).
			 */

			// return required information about uber special case
			return unregisteredEmptyLastFileNumber;
		}
		
		protected void supplementedMissingEmptyFile(
			final EqHashTable<Long, StorageDataInventoryFile> supplementedMissingEmptyFiles,
			final long                                        fileNumber
		)
		{
			final AFile missingEmptyFile = this.fileProvider.provideDataFile(
				this.channelIndex,
				fileNumber
			);
			missingEmptyFile.ensureExists();
			final StorageDataInventoryFile supplementedDataFile = StorageDataInventoryFile.New(
				missingEmptyFile, this.channelIndex, fileNumber
			);
			supplementedMissingEmptyFiles.add(fileNumber, supplementedDataFile);
		}

		@Override
		public StorageIdAnalysis initializeStorage(
			final long             taskTimestamp           ,
			final long             consistentStoreTimestamp,
			final StorageInventory storageInventory        ,
			final StorageChannel   parent
		)
		{
			// a consensus store below this channel's head baseline (a sealed-file store), or a
			// data-bearing file orphaned above the log's head, is a multi-file divergence; reduce it
			// to the head-only path (remove the files above the cut, truncate the log to the store),
			// so everything below runs unchanged on the head-only case.
			final StorageInventory inventory =
				this.reconcileForHeadOnlyRecovery(consistentStoreTimestamp, storageInventory);

			final EqHashTable<Long, StorageDataInventoryFile> supplementedMissingEmptyFiles = EqHashTable.New();

			// validate file lengths, even in case of no files, to validate transactions entries to that state
			final long unregisteredEmptyLastFileNumber = this.validateStorageDataFilesLength(
				inventory,
				supplementedMissingEmptyFiles
			);

			final StorageInventory effectiveStorageInventory = this.determineEffectiveStorageInventory(
				inventory,
				supplementedMissingEmptyFiles
			);

			boolean isEmpty = true;
			try
			{
				isEmpty = effectiveStorageInventory.dataFiles().isEmpty();

				final StorageIdAnalysis idAnalysis;
				if(isEmpty)
				{
					// initialize if there are no files at all (create first file, ensure transactions file)
					this.initializeForNoFiles(taskTimestamp, effectiveStorageInventory);
					idAnalysis = StorageIdAnalysis.New(0L, 0L, 0L);
					
					// blank initialization to avoid redundantly copying the initial transactions entry (nasty bug).
					this.initializeBackupHandler();
				}
				else
				{
					// register a pending store update to keep state (e.g. GC) in a consistent state.
					this.entityCache.registerPendingStoreUpdate();

					// handle files (read, parse, register items) and ensure transactions file
					idAnalysis = this.initializeForExistingFiles(
						taskTimestamp                  ,
						effectiveStorageInventory      ,
						consistentStoreTimestamp       ,
						unregisteredEmptyLastFileNumber
					);
					
					// initialization plus synchronization with existing files.
					this.initializeBackupHandler(effectiveStorageInventory);
				}

				this.transactionFileCleaner = this.transactionFileCleanerCreator.createStorageTransactionsFileCleaner(
					this.fileTransactions,
					this.channelIndex,
					this.dataFileEvaluator.transactionFileMaximumSize(),
					this.fileProvider,
					this.writer
				);
			
				this.restartFileCleanupCursor();

				return idAnalysis;
			}
			catch(final RuntimeException e)
			{
				//as this instance won't be restarted any more, destroy allocated buffers
				parent.dispose();
				throw e;
			}
			finally
			{
				if(!isEmpty)
				{
					// clear the previously registered pending store update
					this.entityCache.clearPendingStoreUpdate();
				}
			}
		}

		/**
		 * Head-file-only recovery guard, run before validation. Recovery only ever cuts within the head
		 * file, so a consensus store below this channel's head-file baseline (in a sealed, earlier file)
		 * is not repairable and is refused loudly - a rollover sealed a not-yet-all-durable store (made
		 * rare by eager rollover durability) or a channel directory was restored from an older
		 * generation. A data file orphaned above the log head by a crash mid-rollover is removed (a
		 * recoverable artifact). No-op when the consensus lies in the head file and no orphan sits above.
		 */
		private StorageInventory reconcileForHeadOnlyRecovery(
			final long             consistentStoreTimestamp,
			final StorageInventory storageInventory
		)
		{
			final StorageTransactionsAnalysis tf = storageInventory.transactionsFileAnalysis();
			if(tf == null || tf.isEmpty() || consistentStoreTimestamp <= 0)
			{
				return storageInventory;
			}

			// cheap pre-checks so anomaly handling runs only for a genuine divergence:
			// (a) the consensus lies below the head baseline (a sealed-file store -> fail-stop), or
			// (b) ANY file sits above the highest file the log knows (an orphan from an interrupted
			//     rollover -> remove it). Include 0-byte orphans: a crash between creating the successor
			//     and writing its FILE_CREATION entry leaves a file the log never recorded, else picked
			//     up as the head on re-read.
			// File creations use strictly increasing numbers, so the log's highest file is its last entry.
			final long maxLoggedFileNumber = tf.transactionsFileEntries().values().peek().fileNumber();
			boolean orphanFileAboveLog = false;
			for(final StorageDataInventoryFile file : storageInventory.dataFiles().values())
			{
				if(file.number() > maxLoggedFileNumber)
				{
					orphanFileAboveLog = true;
					break;
				}
			}
			if(consistentStoreTimestamp >= tf.headFileBaselineTimestamp() && !orphanFileAboveLog)
			{
				return storageInventory;
			}

			// A consensus below the head-file baseline lies in a sealed, earlier file and is NOT
			// repairable by the head-only rollback. Refuse loudly rather than span: eager rollover
			// durability makes a power loss practically never produce this, and the remaining cause -
			// a directory restored from an older generation - must not silently discard the intact
			// channels' committed data.
			if(consistentStoreTimestamp < tf.headFileBaselineTimestamp())
			{
				throw new StorageExceptionConsistency(
					this.channelIndex() + " consensus store timestamp " + consistentStoreTimestamp
					+ " lies below this channel's head-file baseline " + tf.headFileBaselineTimestamp()
					+ ": below-baseline divergence. Recovery is head-file only - a rollover sealed a"
					+ " not-yet-all-durable store, or a channel directory was restored from an older"
					+ " generation. Refusing to start rather than discard committed data."
				);
			}

			// consensus IS head-recoverable, but a data file is orphaned above the log head: an
			// interrupted rollover created the physical successor before its FILE_CREATION entry reached
			// the log. Remove it (and its backup copy); the head-only recovery below then proceeds.
			if(!this.writeController.isWritable())
			{
				throw new StorageExceptionConsistency(
					this.channelIndex() + " an orphaned data file above the log head cannot be removed in read-only mode"
				);
			}
			for(final StorageDataInventoryFile file : storageInventory.dataFiles().values())
			{
				if(file.number() > maxLoggedFileNumber)
				{
					AFS.executeWriting(file.file(), wf ->
					{
						if(wf.exists())
						{
							wf.delete();
						}
					});
					this.deleteBackupDataFile(file.number());
				}
			}
			return this.readStorage();
		}

		protected StorageInventory determineEffectiveStorageInventory(
			final StorageInventory                            storageInventory             ,
			final EqHashTable<Long, StorageDataInventoryFile> supplementedMissingEmptyFiles
		)
		{
			if(supplementedMissingEmptyFiles.isEmpty())
			{
				return storageInventory;
			}
			
			final EqHashTable<Long, StorageDataInventoryFile> completeDataFiles = EqHashTable.New(
				storageInventory.dataFiles()
			)
			.addAll(supplementedMissingEmptyFiles)
			;

			completeDataFiles.keys().sort(XSort::compare);
			
			return StorageInventory.New(
				storageInventory.channelIndex(),
				completeDataFiles.immure(),
				storageInventory.transactionsFileAnalysis()
			);
		}
		
		private boolean initializeBackupHandler()
		{
			if(this.backupHandler == null)
			{
				return false;
			}
			
			this.backupHandler.initialize(this.channelIndex());
			
			return true;
		}
		
		private void initializeBackupHandler(final StorageInventory inventory)
		{
			if(this.backupHandler == null)
			{
				return;
			}

			if(this.transactionsFileRestored)
			{
				// invalidate the backup transactions file at the source, BEFORE the handler
				// registers its file inventory: the synchronization below then rebuilds it
				// regardless of the handler implementation
				this.deleteBackupTransactionsFile();
			}

			this.initializeBackupHandler();
			this.backupHandler.synchronize(inventory);
		}

		/**
		 * Mirrors a recovery-time data-file removal to the backup - used when
		 * {@link #reconcileForHeadOnlyRecovery} removes an orphan file left above the log head by an
		 * interrupted rollover - with the same rescue-move / raw-delete semantics as
		 * {@link #deleteBackupTransactionsFile()}. Runs during recovery, before the backup handler
		 * registers its inventory, so the subsequent synchronization no longer sees the backup as ahead
		 * of the primary and a restore no longer resurrects the removed file.
		 */
		private void deleteBackupDataFile(final long fileNumber)
		{
			if(this.backupHandler == null)
			{
				return;
			}
			this.deleteBackupFile(
				this.backupHandler.setup().backupFileProvider().provideBackupDataFile(this.channelIndex(), fileNumber),
				"backup data file"
			);
		}

		private void deleteBackupTransactionsFile()
		{
			this.deleteBackupFile(
				this.backupHandler.setup().backupFileProvider().provideBackupTransactionsFile(this.channelIndex()),
				"backup transactions file"
			);
		}

		/**
		 * Removes a backup file during recovery with the backup handler's own deletion semantics:
		 * rescue-move when a deletion target is configured, otherwise a raw delete. Raw, released AFS
		 * accesses - a retained lease would collide with the handler's own file wrapper during the
		 * subsequent synchronization. A silently retained file would defeat the removal (at equal length
		 * the synchronization keeps the stale backup content), so a failed raw delete throws.
		 */
		private void deleteBackupFile(final StorageBackupChannelFile backupFile, final String description)
		{
			final AFile file = backupFile.file();
			if(!file.exists())
			{
				return;
			}

			final AFile deletionTargetFile =
				this.backupHandler.setup().backupFileProvider().provideDeletionTargetFile(backupFile);
			AFS.executeWriting(file, wf ->
			{
				if(deletionTargetFile == null)
				{
					if(wf.exists() && !wf.delete())
					{
						throw new StorageException("Could not delete " + description + " " + file.toPathString());
					}
				}
				else
				{
					AFS.executeWriting(deletionTargetFile, targetWf -> wf.moveTo(targetWf));
				}
			});
		}

		private StorageIdAnalysis initializeForExistingFiles(
			final long             taskTimestamp                  ,
			final StorageInventory storageInventory               ,
			final long             consistentStoreTimestamp       ,
			final long             unregisteredEmptyLastFileNumber
		)
		{
			/*
			 * The data files and all entities in them get initialized in reverse order.
			 * The reason is that for every entity, only the latest, most current version counts.
			 * Reversing the order makes this trivial to implement: for every OID (i.e. entity), only the first
			 * occurrence counts and defines type, length and position in the storage. All further occurrences
			 * (meaning EARLIER versions) of an already encountered Entity/OID are simply ignored.
			 */
			
			// local variables for readability, debugging and (paranoid) consistency guarantee
			final XGettingSequence<StorageDataInventoryFile> files = storageInventory.dataFiles().values();

			// validate and determine length of last file before any file is processed to recognize errors early
			final long lastFileLength = unregisteredEmptyLastFileNumber >= 0
				? 0
				: this.determineLastFileLength(consistentStoreTimestamp, storageInventory)
			;

			// register items (gaps and entities, with latest version of each entity replacing all previous)
			final StorageEntityInitializer<StorageLiveDataFile.Default> initializer =
				StorageEntityInitializer.New(this.entityCache, f ->
					StorageLiveDataFile.New(this, f),
					this.metaRecordRegistry
				)
			;
			this.headFile = initializer.registerEntities(files, lastFileLength);

			// validate entities (only the latest versions) before potential transaction file derivation
			final StorageIdAnalysis idAnalysis = this.entityCache.validateEntities();

			// ensure transactions file before handling last file as truncation needs to write in it
			this.ensureTransactionsFile(taskTimestamp, storageInventory, unregisteredEmptyLastFileNumber);

			// special-case handle the last file
			this.handleLastFile(this.headFile, lastFileLength, consistentStoreTimestamp);

			// check if last file is over-sized and should be retired right away.
			this.checkForNewFile();

			// continuousCoverage: if emit is on but the head file is not covered by the primary kind (a
			// legacy file, or one written by a different algorithm), roll over now so new writes land in a
			// covered file instead of extending an unprotected tail. A no-op once the head is already covered.
			// Skipped in read-only mode: createNextStorageFile() writes a FileHeaderV1 + transaction-log
			// entry that a disabled WriteController would reject, and no chunks are ever appended in
			// read-only mode anyway, so ensuring future writes land covered is moot.
			if(this.writeController.isWritable()
				&& this.chunkChecksumCalculator.policy().continuousCoverage()
				&& !this.chunkChecksumCalculator.coversChunkWrites(this.headFile))
			{
				this.createNextStorageFile();
			}

			return idAnalysis;
		}

		private long determineLastFileLength(
			final long             consistentStoreTimestamp,
			final StorageInventory storageInventory
		)
		{
			final StorageTransactionsAnalysis tFileAnalysis = storageInventory.transactionsFileAnalysis();

			if(tFileAnalysis == null || tFileAnalysis.isEmpty())
			{
				/*
				 * if no transactions file was present, it must be assumed that the last file is consistent
				 * (e.g. user manually deleted the transactions file in a consistent database)
				 */
				return storageInventory.dataFiles().values().last().size();
			}
			else if(tFileAnalysis.headFileLatestTimestamp() == consistentStoreTimestamp)
			{
				return tFileAnalysis.headFileLatestLength();
			}
			else if(tFileAnalysis.headFileLastConsistentStoreTimestamp() == consistentStoreTimestamp)
			{
				// common case of a one-store divergence: the cut length is already derived
				return this.validatedCutLength(tFileAnalysis, tFileAnalysis.headFileLastConsistentStoreLength());
			}
			else
			{
				/*
				 * Consensus below both retained anchors: divergence by more than one store, or
				 * every head store rolled back onto the head's creation baseline. The cut length
				 * is the consensus store's recorded head length advanced over the channel-local
				 * transfers that followed it (incl. across a baseline collapse onto it). It is
				 * re-read from the log only in this rare case instead of being retained in
				 * memory - a head file can accumulate millions of store entries. The baseline
				 * case must use the same folding: cutting at the raw creation length would cut
				 * below transfers logged between the head's creation and its first store.
				 */
				return this.validatedCutLength(
					tFileAnalysis,
					this.readStoreAnchorLength(tFileAnalysis, consistentStoreTimestamp)
				);
			}
		}

		/**
		 * Determines, in one pass over this channel's transactions log, the head length to roll back
		 * to for the passed consistent store timestamp: the store's recorded length advanced over any
		 * channel-local transfers and truncations logged before the next store (the same folding
		 * {@code handleEntryStore} / {@code handleEntryFileTruncation} apply). Missing a transfer would
		 * cut below relocated bytes {@link #validatedCutLength} must keep; missing a truncation would
		 * cut above truncated-away bytes and load stale data.
		 * <p>
		 * The consensus store's window survives the log events that collapse onto it: a recovery
		 * truncation stamped with its timestamp re-opens the window at the cut length, and a file
		 * creation while the window is open re-anchors at the new head's creation length (the
		 * baseline collapse) - so transfers logged after either event still fold into the anchor.
		 */
		private long readStoreAnchorLength(
			final StorageTransactionsAnalysis tFileAnalysis           ,
			final long                        consistentStoreTimestamp
		)
		{
			final long[]    anchorLength = {-1L}  ;
			final boolean[] capturing    = {false};
			tFileAnalysis.transactionsFile().processBy((address, availableEntryLength) ->
			{
				if(availableEntryLength < 0)
				{
					return true; // gap / comment, signaled by the read loop
				}
				if(availableEntryLength < Logic.getEntryLength(address))
				{
					// entry cut by the read-window boundary: re-read at this position
					return false;
				}
				switch(Logic.getEntryType(address))
				{
					case Logic.TYPE_FILE_CREATION:
					{
						/*
						 * A creation collapses the rollback baseline onto the current store. If that
						 * is the consensus store (window open), the anchor moves into the new head's
						 * coordinates - its creation length - and the window stays open for the
						 * transfers that follow (exactly the collapse handleEntryFileCreation applies).
						 * Otherwise prior hits belong to a closed file and are invalid as head cut
						 * coordinates.
						 */
						anchorLength[0] = capturing[0] ? Logic.getFileLength(address) : -1L;
						return true;
					}
					case Logic.TYPE_STORE:
					{
						if(Logic.getEntryTimestamp(address) == consistentStoreTimestamp)
						{
							// anchor at this store's length, then extend it over the transfers that
							// follow until the next store - they are channel-local and kept, exactly
							// as handleEntryStore folds them into lastConsistentStoreLength.
							anchorLength[0] = Logic.getFileLength(address);
							capturing[0]    = true;
						}
						else
						{
							// a different store ends the matched store's transfer window
							capturing[0] = false;
						}
						return true;
					}
					case Logic.TYPE_TRANSFER:
					{
						// channel-local growth inside the window; last one wins.
						if(capturing[0])
						{
							anchorLength[0] = Logic.getFileLength(address);
						}
						return true;
					}
					case Logic.TYPE_FILE_TRUNCATION:
					{
						/*
						 * A recovery truncation stamped with the consensus store's timestamp collapses
						 * the head back onto that store (see handleLastFile), so the anchor re-opens at
						 * the cut length and transfers logged after it belong to the survivor's window
						 * again - exactly as handleEntryFileTruncation stamps the survivor on a deep cut.
						 * Missing this re-open would cut below such a transfer's bytes: a spurious
						 * fail-stop when its source file is already deleted (validatedCutLength).
						 */
						if(Logic.getEntryTimestamp(address) == consistentStoreTimestamp)
						{
							anchorLength[0] = Logic.getFileLength(address);
							capturing[0]    = true;
						}
						else if(capturing[0])
						{
							// a cut inside the open window; last length wins.
							anchorLength[0] = Logic.getFileLength(address);
						}
						return true;
					}
					case Logic.TYPE_FILE_DELETION:
					{
						// length-neutral for the head file (source-file deletion); never moves the anchor.
						// Enumerated so the default below can fail-loud on any unclassified type.
						return true;
					}
					default:
					{
						// all length-affecting entry types are handled above; an unclassified one here
						// could hide a truncated head, so refuse rather than risk a stale cut.
						throw new StorageExceptionConsistency(
							"Unexpected transactions entry type " + Logic.getEntryType(address)
							+ " while re-reading the rollback anchor of channel " + this.channelIndex()
						);
					}
				}
			});

			if(anchorLength[0] < 0)
			{
				// should never happen because of all the validations before
				throw new StorageExceptionConsistency(
					"Inconsistent last timestamps in last file of channel " + this.channelIndex()
				);
			}
			return anchorLength[0];
		}

		/**
		 * Guards the rollback of unconfirmed stores: transfers whose bytes lie above the cut are
		 * truncated with the stores' chunks and recovered from their source files
		 * ({@link StorageTransactionsAnalysis#headFileTransfers()}). If such a source file is
		 * already deleted, truncating would destroy the transferred entities' last copy -
		 * refuse to initialize instead of losing committed data silently.
		 */
		private long validatedCutLength(final StorageTransactionsAnalysis tFileAnalysis, final long cutLength)
		{
			for(final KeyValue<Long, Long> transfer : tFileAnalysis.headFileTransfers())
			{
				if(transfer.value() <= cutLength)
				{
					continue; // below the cut: survives the truncation
				}
				final StorageTransactionEntry sourceFile = tFileAnalysis.transactionsFileEntries().get(transfer.key());
				if(sourceFile == null || sourceFile.isDeleted())
				{
					throw new StorageExceptionConsistency(
						"Channel " + this.channelIndex() + " cannot roll back its unconfirmed latest stores:"
						+ " a transfer logged above the rollback point has the already deleted file "
						+ transfer.key() + " as its source, so truncating the transferred bytes"
						+ " would destroy their last copy."
					);
				}
			}
			return cutLength;
		}
								
		private void initializeForNoFiles(final long taskTimestamp, final StorageInventory storageInventory)
		{
			// ensure translations file BEFORE adding the first file as it writes a transactions entry
			this.ensureTransactionsFile(taskTimestamp, storageInventory, -1);
			this.addFirstFile();
		}

		private void ensureTransactionsFile(
			final long             taskTimestamp                  ,
			final StorageInventory storageInventory               ,
			final long             unregisteredEmptyLastFileNumber
		)
		{
			final StorageTransactionsAnalysis trFileAn = storageInventory.transactionsFileAnalysis();
			final StorageLiveTransactionsFile transactionsFile;

			if(trFileAn == null || trFileAn.isEmpty())
			{
				// get or create new
				transactionsFile = trFileAn == null
					? this.createTransactionsFile()
					: trFileAn.transactionsFile()
				;

				// validate length of both cases anyway. 0-length is essential before deriving content
				if(transactionsFile.size() != 0)
				{
					throw new StorageException("Invalid transactions file in channel " + this.channelIndex);
				}

				// guaranteed empty transaction file gets its content derived from the inventory
				this.deriveTransactionsFile(taskTimestamp, storageInventory, transactionsFile);
			}
			else
			{
				// already existing non-empty transactions file: just use it.
				transactionsFile = trFileAn.transactionsFile();
			}

			this.setTransactionsFile(transactionsFile);

			if(unregisteredEmptyLastFileNumber >= 0)
			{
				this.writeTransactionsEntryFileCreation(0, taskTimestamp, unregisteredEmptyLastFileNumber);
			}

		}

		private StorageLiveTransactionsFile createTransactionsFile()
		{
			final AFile file = this.fileProvider.provideTransactionsFile(this.channelIndex());
			file.ensureExists();
			
			return StorageLiveTransactionsFile.New(file, this.channelIndex());
		}

		private void deriveTransactionsFile(
			final long                        taskTimestamp   ,
			final StorageInventory            storageInventory,
			final StorageLiveTransactionsFile tfile
		)
		{
			final XGettingSequence<StorageDataInventoryFile> files   = storageInventory.dataFiles().values();
			final ByteBuffer                                 buffer  = this.entryBufferFileCreation         ;
			final long                                       address = this.entryBufferFileCreationAddress  ;
			final StorageFileWriter                          writer  = this.writer                          ;

			long timestamp = taskTimestamp - storageInventory.dataFiles().size() - 1;

			try
			{
				for(final StorageDataInventoryFile file : files)
				{
					buffer.clear();
					StorageTransactionsAnalysis.Logic.setEntryFileCreation(
						address      ,
						file.size()  ,
						++timestamp  ,
						file.number()
					);
					writer.write(tfile, this.entryBufferWrapFileCreation);
				}
			}
			catch(final Exception e)
			{
				StorageClosableFile.close(tfile, e);
				throw e;
			}
		}

		private void writeTransactionsEntryFileCreation(
			final long length   ,
			final long timestamp,
			final long number
		)
		{
			this.entryBufferFileCreation.clear();
			StorageTransactionsAnalysis.Logic.setEntryFileCreation(
				this.entryBufferFileCreationAddress,
				length                             ,
				timestamp                          ,
				number
			);
			this.writer.writeTransactionEntryCreate(this.fileTransactions, this.entryBufferWrapFileCreation, this.headFile);
		}

		private void writeTransactionsEntryStore(
			final StorageLiveDataFile dataFile              ,
			final long                dataFileOffset        ,
			final long                storeLength           ,
			final long                timestamp             ,
			final long                headFileNewTotalLength
		)
		{
			this.entryBufferStore.clear();
			StorageTransactionsAnalysis.Logic.setEntryStore(
				this.entryBufferStoreAddress,
				headFileNewTotalLength      ,
				timestamp
			);
			this.lastWrittenStoreTimestamp = timestamp;
			this.writer.writeTransactionEntryStore(
				this.fileTransactions    ,
				this.entryBufferWrapStore,
				dataFile                 ,
				dataFileOffset           ,
				storeLength
			);
		}

		private void writeTransactionsEntryTransfer(
			final StorageLiveDataFile sourceFile            ,
			final long                sourcefileOffset      ,
			final long                copyLength            ,
			final long                timestamp             ,
			final long                headNewFileTotalLength
		)
		{
			this.entryBufferTransfer.clear();
			StorageTransactionsAnalysis.Logic.setEntryTransfer(
				this.entryBufferTransferAddress,
				headNewFileTotalLength         ,
				timestamp                      ,
				sourceFile.number()            ,
				sourcefileOffset
			);
			
			this.writer.writeTransactionEntryTransfer(
				this.fileTransactions,
				this.entryBufferWrapTransfer,
				sourceFile,
				sourcefileOffset,
				copyLength
			);
		}

		private void writeTransactionsEntryFileDeletion(
			final StorageLiveDataFile.Default dataFile ,
			final long                        timestamp
		)
		{
			this.entryBufferFileDeletion.clear();
			StorageTransactionsAnalysis.Logic.setEntryFileDeletion(
				this.entryBufferFileDeletionAddress,
				dataFile.totalLength()             ,
				timestamp                          ,
				dataFile.number()
			);
			this.writer.writeTransactionEntryDelete(this.fileTransactions, this.entryBufferWrapFileDeletion, dataFile);
		}

		private void writeTransactionsEntryFileTruncation(
			final StorageLiveDataFile.Default lastFile ,
			final long                        timestamp,
			final long                        newLength
		)
		{
			this.entryBufferFileTruncation.clear();
			StorageTransactionsAnalysis.Logic.setEntryFileTruncation(
				this.entryBufferFileTruncationAddress,
				newLength                            ,
				timestamp                            ,
				lastFile.number()                    ,
				lastFile.size()
			);
			this.writer.writeTransactionEntryTruncate(this.fileTransactions, this.entryBufferWrapFileTruncation, lastFile, newLength);
		}

		private void setTransactionsFile(final StorageLiveTransactionsFile transactionsFile)
		{
			this.fileTransactions = transactionsFile;
			
			transactionsFile.registerUsage(this);
		}
		
		final void clearStandardByteBuffer()
		{
			this.standardByteBuffer.clear();
		}

		@Override
		public final void reset()
		{
			/* Note:
			 * (see field declarations)
			 * 1.0) all final fields don't have to (can't) be resetted. Obviously.
			 * 1.1) entryBuffers don't have to be resetted since they get filled anew for every write.
			 */
			
			// 2.0) final references to mutable instances
			this.clearStandardByteBuffer();
			
			// 3.X) mutable fields and variable length content
			this.clearUncommittedDataLength();
			this.clearRegisteredFiles();
			
			// at this point, it is either 0 already or it won't matter since everything has been cleared.
			this.pendingFileDeletes = 0;

			// durability gate state: cleared timestamps make the gates pass trivially, which is
			// correct after a reset - everything readable at initialization is the reconciled
			// baseline, and only stores of the new session can be subject to a rollback.
			this.lastWrittenStoreTimestamp      = 0;
			this.allDurableStoreTimestamp       = 0;
			this.storageFlushRequested          = false;
			this.unconditionalCompactionPending = false;
			this.rolloverOccurred               = false;
			this.durabilityDeferredDeletes.clear();
		}
		
		/**
		 * The deleteBuffers method is used to allow an early deallocation
		 * of the used DirectByteBuffers in order to reduce the off-heap
		 * memory footprint without the need to relay on the GC.
		 * after calling this method the StorageManager is left in an inoperable state.
		 */
		public final void deleteBuffers()
		{
			logger.debug("Destroying all buffers explicitly!");

			XMemory.deallocateDirectByteBuffer(this.entryBufferFileCreation);
			XMemory.deallocateDirectByteBuffer(this.entryBufferStore);
			XMemory.deallocateDirectByteBuffer(this.entryBufferTransfer);
			XMemory.deallocateDirectByteBuffer(this.entryBufferFileDeletion);
			XMemory.deallocateDirectByteBuffer(this.entryBufferFileTruncation);
			XMemory.deallocateDirectByteBuffer(this.standardByteBuffer);
			XMemory.deallocateDirectByteBuffer(this.chunkChecksumBuffer);
			XMemory.deallocateDirectByteBuffer(this.fileHeaderBuffer);
		}

		final void handleLastFile(
			final StorageLiveDataFile.Default lastFile              ,
			final long                        lastFileLength        ,
			final long                        survivorStoreTimestamp
		)
		{
			if(lastFileLength != lastFile.size())
			{
				// reaching here means in any case that the file has to be truncated and its header must be updated

				// Stamp the truncation entry with the surviving (consensus) store's timestamp, not a fresh
				// one: a deep rollback cuts below the anchors the log replay retains, and
				// handleEntryFileTruncation recovers the survivor's timestamp from this entry so
				// headFileLatestTimestamp (the consensus key) no longer names a rolled-back store. For a
				// shallow cut the length-matched anchor drives the reset, so this timestamp is safe there too.
				this.writeTransactionsEntryFileTruncation(lastFile, survivorStoreTimestamp, lastFileLength);

				// (20.06.2014 TM)TODO: truncator function to give a chance to evaluate / rescue the doomed data
				this.writer.truncate(lastFile, lastFileLength, this.fileProvider);
			}
		}
		
		@Override
		public void exportData(final StorageLiveFileProvider fileProvider)
		{
			final AFile transactionsFile = fileProvider.provideTransactionsFile(this.channelIndex());
			AFS.executeWriting(transactionsFile, wf ->
				this.fileTransactions.copyTo(wf)
			);

			this.iterateStorageFiles(file ->
			{
				final AFile exportFile = fileProvider.provideDataFile(file.channelIndex(), file.number());
				AFS.executeWriting(exportFile, wf ->
					file.copyTo(wf)
				);
			});
		}
		
		private static StorageRawFileStatistics.FileStatistics createFileStatistics(final StorageLiveDataFile.Default file)
		{
			return StorageRawFileStatistics.FileStatistics.New(
				file.number()    ,
				file.identifier(),
				file.dataLength(),
				file.totalLength()
			);
		}

		@Override
		public final StorageRawFileStatistics.ChannelStatistics createRawFileStatistics()
		{
			StorageLiveDataFile.Default file;
			final StorageLiveDataFile.Default currentFile = file = this.headFile;

			long liveDataLength  = 0;
			long totalDataLength = 0;
			final BulkList<StorageRawFileStatistics.FileStatistics> fileStatistics = BulkList.New();

			do
			{
				file = file.next;
				liveDataLength  += file.dataLength();
				totalDataLength += file.totalLength();
				
				final StorageRawFileStatistics.FileStatistics fileStats = createFileStatistics(file);
				fileStatistics.add(fileStats);
			}
			while(file != currentFile);

			return StorageRawFileStatistics.ChannelStatistics.New(
				this.channelIndex(),
				fileStatistics.size(),
				liveDataLength,
				totalDataLength,
				fileStatistics
			);
		}

		@Override
		public final boolean incrementalFileCleanupCheck(final long nanoTimeBudgetBound)
		{
			return this.internalCheckForCleanup(nanoTimeBudgetBound, this.dataFileEvaluator);
		}

		@Override
		public final void restartFileCleanupCursor()
		{
			this.fileCleanupCursor = this.headFile.next;
		}

		@Override
		public final boolean issuedFileCleanupCheck(final long nanoTimeBudgetBound)
		{
			return this.internalCheckForCleanup(nanoTimeBudgetBound, this.dataFileEvaluator);
		}

		@Override
		public final StorageIntegrityCheckResult verifyChunkChecksums(final long nanoTimeBudgetBound, final boolean freshScan)
		{
			final StorageIntegrityCheckResult.Default result = StorageIntegrityCheckResult.New();

			// Honor the configured policy: a non-verifying policy (None / verify-off) checks nothing.
			if(!this.chunkChecksumCalculator.isVerifying() || this.headFile == null)
			{
				return result; // complete + clean
			}

			if(freshScan)
			{
				this.snapshotIntegrityScope();
			}
			else if(this.integrityScopeNumbers == null)
			{
				// already finished this scan: return complete+empty rather than re-snapshotting, so a budgeted
				// scan across uneven channels terminates without re-hashing or duplicating findings.
				return result;
			}

			// O(files) number->file lookup for this pass; a snapshotted file no longer present was dissolved.
			final EqHashTable<Long, StorageLiveDataFile.Default> filesByNumber = this.currentFilesByNumber();

			final StorageChecksumAnomalyReporter reporter = StorageChecksumAnomalyReporter.NewCollecting(
				this.chunkChecksumCalculator.policy(),
				this.channelIndex()                  ,
				result
			);

			// One bounded buffer per channel serves the whole scan: files within the cap are read whole (the proven
			// resident walk), larger files are streamed in windows of this same buffer. Sized to the largest in-scope
			// file but never above INTEGRITY_VERIFY_BUFFER_LIMIT, so peak direct memory stays channelCount × cap.
			long maxScopeLength = 0L;
			for(final long scopeLength : this.integrityScopeLengths)
			{
				if(scopeLength > maxScopeLength)
				{
					maxScopeLength = scopeLength;
				}
			}
			final int bufferSize = X.checkArrayRange(Math.min((long)INTEGRITY_VERIFY_BUFFER_LIMIT, maxScopeLength));

			ByteBuffer buffer = null;
			try
			{
				while(this.integrityScopeCursor < this.integrityScopeNumbers.length)
				{
					final long                        number = this.integrityScopeNumbers[this.integrityScopeCursor];
					final long                        length = this.integrityScopeLengths[this.integrityScopeCursor];
					final StorageLiveDataFile.Default file   = filesByNumber.get(number);

					// file == null => dissolved by housekeeping between resume calls => skip (no false positive).
					if(file != null && length > 0L)
					{
						// A sealed (non-head) file is immutable: a size change since the snapshot is corruption /
						// tampering — always an error. The snapshot head is exempt (it legitimately grows) and is
						// verified only up to its snapshot committed length.
						final long currentSize = file.size();
						final long verifyLength;
						if(number == this.integrityScopeHeadNumber)
						{
							verifyLength = length;
						}
						else
						{
							if(currentSize != length)
							{
								result.add(new StorageIntegrityCheckResult.Finding(
									this.channelIndex(), number, currentSize,
									StorageIntegrityCheckResult.Anomaly.FILE_SIZE_CHANGED, 0L, null, null
								));
							}
							// verify the surviving region; clamp so a shrunk file is not read past its end.
							verifyLength = Math.min(length, currentSize);
						}

						if(verifyLength > 0L)
						{
							if(buffer == null)
							{
								buffer = XMemory.allocateDirectNative(bufferSize);
							}
							if(verifyLength <= INTEGRITY_VERIFY_BUFFER_LIMIT)
							{
								// fits the buffer: read the whole file and use the proven resident walk.
								buffer.clear();
								buffer.limit(X.checkArrayRange(verifyLength));
								file.readBytes(buffer, 0, verifyLength);
								this.chunkChecksumCalculator.verifyDataFile(file, buffer, reporter);
							}
							else
							{
								// larger than the cap: stream the verify in windows of this buffer (bounded memory).
								this.chunkChecksumCalculator.verifyDataFileStreaming(file, verifyLength, buffer, reporter);
							}
						}
					}

					this.integrityScopeCursor++;

					// resume granularity is one whole file (chained files must be walked from their header), and
					// the budget is checked AFTER a file so each call makes progress even with a tiny budget.
					if(this.integrityScopeCursor < this.integrityScopeNumbers.length
						&& System.nanoTime() >= nanoTimeBudgetBound)
					{
						result.setComplete(false);
						return result;
					}
				}

				// whole scope covered: this channel is done. Clear the scope so resume calls (freshScan == false)
				// from siblings still working return complete+empty instead of re-scanning.
				result.setComplete(true);
				this.clearIntegrityScope();
				return result;
			}
			finally
			{
				if(buffer != null)
				{
					XMemory.deallocateDirectByteBuffer(buffer);
				}
			}
		}

		private void snapshotIntegrityScope()
		{
			// snapshot every file's (number, length); the head file is bounded to its current committed length.
			final StorageLiveDataFile.Default headFile = this.headFile;

			int count = 0;
			StorageLiveDataFile.Default file = headFile;
			do
			{
				file = file.next;
				count++;
			}
			while(file != headFile);

			final long[] numbers = new long[count];
			final long[] lengths = new long[count];
			int i = 0;
			file = headFile;
			do
			{
				file = file.next;
				numbers[i] = file.number()     ;
				lengths[i] = file.totalLength(); // head file: current committed length L (immutable in [0, L)).
				i++;
			}
			while(file != headFile);

			this.integrityScopeNumbers    = numbers          ;
			this.integrityScopeLengths    = lengths          ;
			this.integrityScopeCursor     = 0                ;
			this.integrityScopeHeadNumber = headFile.number();
		}

		private void clearIntegrityScope()
		{
			this.integrityScopeNumbers = null;
			this.integrityScopeLengths = null;
			this.integrityScopeCursor  = 0   ;
		}

		private EqHashTable<Long, StorageLiveDataFile.Default> currentFilesByNumber()
		{
			final EqHashTable<Long, StorageLiveDataFile.Default> table = EqHashTable.New();
			final StorageLiveDataFile.Default headFile = this.headFile;
			StorageLiveDataFile.Default file = headFile;
			do
			{
				file = file.next;
				table.put(file.number(), file);
			}
			while(file != headFile);

			return table;
		}

		public boolean issuedTransactionFileCheck(final boolean checkSize)
		{
			return this.internalTransactionFileCheck(checkSize);
		}

		private void deletePendingFile(final StorageLiveDataFile.Default file)
		{
			if(this.pendingFileDeletes < 1)
			{
				/* (31.10.2014 TM)TODO: Proper storage inconsistency handling
				 *  May never just throw an exception and potentially kill the channel thread
				 *  Instead must signal the storage manager (one way or another) to shutdown so that no other
				 *  thread continues working and ruins something.
				 */
				throw new StorageExceptionConsistency(
					this.channelIndex() + " has inconsistent pending deletes: count = "
					+ this.pendingFileDeletes + ", wants to delete " + file
				);
			}
			this.pendingFileDeletes--;
			
			this.deleteFile(file);
		}

		private boolean internalCheckForCleanup(
			final long                               nanoTimeBudgetBound,
			final StorageDataFileDissolvingEvaluator fileDissolver
		)
		{
			if(!DEBUG_ENABLE_FILE_CLEANUP)
			{
				return true;
			}
			
			this.writeController.validateIsFileCleanupEnabled();

			if(this.fileCleanupCursor == null)
			{
				return true;
			}


			StorageLiveDataFile.Default cycleAnchorFile = this.fileCleanupCursor;

			// intentionally no minimum first loop execution as cleanup is not important if the system has heavy load
			while(this.fileCleanupCursor != null && System.nanoTime() < nanoTimeBudgetBound)
			{
				// never check current head file for dissolving

				// delete pending file and do special case checking. This never applies to head files automatically
				if(!this.fileCleanupCursor.hasUsers())
				{
					// durability gate: a deferred file is skipped and revisited after the flush
					if(this.deletionDurabilityGatePassed(this.fileCleanupCursor))
					{
						// an iterable (non-detached) file with no users can only mean a pending delete.
						if(!this.fileCleanupCursor.executeIfUnsuedData(this.pendingDeleter))
						{
							// should a new usage have been registered right after checking, then break and try again later
							break;
						}

						// account for special case of removed file being the anchor file (sadly redundant to below)
						if(this.fileCleanupCursor == cycleAnchorFile)
						{
							this.fileCleanupCursor = cycleAnchorFile = cycleAnchorFile.next;
							continue;
						}
					}
				}
				else if(fileDissolver.needsDissolving(this.fileCleanupCursor))
				{
					// Eager rollover durability: dissolving transfers live chains into the head may roll it
					// over, collapsing the recovery baseline onto the head's latest store. Defer the whole
					// dissolution while that store is not yet all-channel durable, so no rollover seals a
					// non-durable store; request a flush, and the deferred dissolution then runs at the
					// flush-barrier completion (executeDurabilityCoveredMaintenance), where it is durable.
					if(!this.isStoreDurable(this.lastWrittenStoreTimestamp))
					{
						this.storageFlushRequested = true;
						break;
					}

					if(this.fileCleanupCursor == this.headFile)
					{
						this.createNextStorageFile();
					}

					if(!this.incrementalDissolveStorageFile(this.fileCleanupCursor, nanoTimeBudgetBound))
					{
						if(!this.isDurabilityDeferred(this.fileCleanupCursor))
						{
							continue;
						}
						// deletion deferred by the durability gate: advance past the file and
						// revisit it once the requested flush raised the watermark
					}
					// file has been dissolved completely and deleted, do special case checking here as well.

					// account for special case of removed file being the anchor file (sadly redundant to above)
					else if(this.fileCleanupCursor == cycleAnchorFile)
					{
						this.fileCleanupCursor = cycleAnchorFile = cycleAnchorFile.next;
						continue;
					}

					/* Reaching here means normal case of advancing the house-keeping file.
					 * Either a healthy file or a removed file that is not the anchor special case.
					 */
				}

				// Advance to next file, abort if full cycle is completed.
				if((this.fileCleanupCursor = this.fileCleanupCursor.next) == cycleAnchorFile)
				{
					// if there are still pending or durability-deferred deletes, file house keeping cannot be turned off
					if(this.pendingFileDeletes > 0 || !this.durabilityDeferredDeletes.isEmpty())
					{

						// at least one more file is pending deletion
						break;
					}


					/* House-keeping can be completely disabled for now as everything has been checked.
					 * Will be resetted by the next write, see #resetHousekeeping.
					 */
					this.fileCleanupCursor = null;
				}
			}

			return this.fileCleanupCursor == null;
		}

		private boolean incrementalDissolveStorageFile(
			final StorageLiveDataFile.Default file               ,
			final long                        nanoTimeBudgetBound
		)
		{

			if(this.incrementalTransferEntities(file, nanoTimeBudgetBound))
			{
				// durability gate; the caller recognizes the deferral via isDurabilityDeferred
				// and advances past the file instead of retrying within this check.
				if(!this.deletionDurabilityGatePassed(file))
				{
					return false;
				}

				if(file.unregisterUsageClosingData(this, this.deleter))
				{
					return true;
				}


				// file has no more content but can't be deleted yet. Schedule for later deletion.
				this.pendingFileDeletes++;
				return false;
			}

			return false;
		}

		private void deleteFile(final StorageLiveDataFile.Default file)
		{

			file.detach();
			file.close(); // idempotent. No harm in calling on an already closed file.

			/* must write transaction file entry BEFORE actually deleting the file (inverted logic)
			 * Otherwise, consider the following scenario:
			 * File gets physically deleted, but process was terminated before the transactions file entry could have
			 * been written. On the next start, the initialization validation would expect the file to still exist
			 * (because it found no deletion entry), but the file is no longer there. From the validation's
			 * perspective, this is a missing file and therefore an error (happened once during testing!).
			 *
			 * The registration logic must be inverted in this case:
			 * First register the file to be deleted (no longer needed), then, after that entry is ensured,
			 * the file can be physically deleted (or left alive because of a killed process).
			 * This way, the next startup validation know that the file is no longer needed and can react accordingly
			 * (keep it alive to re-evaluate it or delete it, etc.)
			 */
			this.writeTransactionsEntryFileDeletion(file, this.timestampProvider.currentNanoTimestamp());

			// (12.08.2020 TM)FIXME: priv#351: where and how to check whether files may be deleted? Here? Weird!

			// Durability barrier (power-loss safety): before the source file is physically unlinked,
			// (a) the current head file must hold the relocated bytes durably - sealed heads were
			// already forced at their rollover, this covers the not-yet-rolled current head - and
			// (b) the transactions file must hold the transfer + deletion entries durably, so a crash
			// can never leave the source unlinked while the records that relocated its data, or the
			// record explaining its absence, are still in page cache. Unguarded on purpose: this must
			// run even in a non-writable state, since the file is being unlinked regardless.
			this.synchronizeStorageFiles();

			// physically delete file after the transactions entry is ensured
			this.writer.delete(file, this.writeController, this.fileProvider);
		}

		private boolean incrementalTransferEntities(
			final StorageLiveDataFile.Default file               ,
			final long                        nanoTimeBudgetBound
		)
		{
			// check for new head file in any case
			this.checkForNewFile();

			// dissolve file to as much head files as needed.
			while(file.hasContent() && System.nanoTime() < nanoTimeBudgetBound)
			{
				this.transferOneChainToHeadFile(file);
			}


			// if entity migration was completed before time ran out, the file has no more content.
			return !file.hasContent();
		}
		
		final StorageEntity.Default getFirstEntity()
		{
			final StorageLiveDataFile.Default currentFile = this.currentStorageFile();
			if(currentFile == null)
			{
				// can occur when an exception causes a reset call during initialization
				return null;
			}
			
			final StorageLiveDataFile.Default startingFile = currentFile.next;
			StorageLiveDataFile.Default file = startingFile;
			do
			{
				if(file.head.fileNext != startingFile.tail)
				{
					if(file.hasContent())
					{
						return file.head.fileNext;
					}
				}
			}
			while((file = file.next) != startingFile);
			
			// no file contains any (proper) entity. So return null.
			return null;
		}
		
		

		ImportHelper importHelper;

		final void prepareImport()
		{
			this.importHelper = new ImportHelper(this.headFile);
			try
			{
				this.createNextStorageFile();
			}
			catch(final Exception e)
			{
				this.importHelper = null;
				throw new StorageException(e);
			}
			// base of the 2 GiB guard: the fresh import file's FileHeaderV1 (0 when the policy does
			// not emit). totalLength() stays frozen at this value during import (it is only raised in
			// commitImport), so importBatch tracks the projected run length itself.
			this.importHelper.importHeadFileLength = this.headFile.totalLength();
		}

		public void copyData(final StorageImportSource importSource)
		{
			importSource.iterateBatches(this.importHelper.setSource(importSource));
		}

		public void commitImport(final long taskTimestamp)
		{

			// caching variables
			final StorageEntityCache.Default  entityCache = this.entityCache;
			final StorageLiveDataFile.Default headFile    = this.headFile   ;

			final long oldTotalLength = this.headFile.totalLength();
			      long loopFileLength = oldTotalLength;

			/*
			 * GC coordination, mirroring the store path (see StorageEntityCache.Default#postStorePutEntities):
			 * without it, an import committed during an active GC cycle registers entities whose
			 * references are never traversed (no gray enqueuing) and does not block the imminent
			 * sweep - a pre-existing entity referenced only by imported data can then be swept,
			 * leaving a dangling reference on disk ("No entity found for objectId" on load).
			 * The task-scoped signal bracket (held from prepareImportData until the task's cleanUp,
			 * see StorageEntityCache.Default#registerPendingImportUpdate) guarantees that no sweep is
			 * flagged or can be initiated while the entities register here.
			 */
			entityCache.registerImportCommit();

			// kept alive across cleanupImportHelper for the post-durability marking pass below
			final BulkList<StorageChannelImportBatch> importBatches = this.importHelper.importBatches;

			// (05.01.2015 TM)TODO: batch copying must ensure that entity position limit of 2 GB is not exceeded
			for(final StorageChannelImportBatch batch : importBatches)
			{
				// register each entity in the batch (possibly just one)
				for(StorageChannelImportEntity entity = batch.first(); entity != null; entity = entity.next())
				{
					final StorageEntity.Default actual = entityCache.putEntity(entity.objectId(), entity.type());
					actual.updateStorageInformation(entity.length(), X.checkArrayRange(loopFileLength));
					headFile.appendEntry(actual);
					/*
					 * Account the entity's bytes immediately instead of bulk-adding copyLength after
					 * the loop: a later record of this same import may dispose this just-appended
					 * entry again (same objectId under a changed typeId, see putEntity), and its
					 * removal decrements fileDataLength for these very bytes - deferred accounting
					 * would let fileDataLength transiently under-count.
					 */
					headFile.increaseContentLength(entity.length());
					loopFileLength += entity.length();
				}
			}

			// content length was already accounted per entity in the loop above
			final long copyLength = loopFileLength - oldTotalLength;

			// The imported entity bytes were appended contiguously (zero-copy transfer) with no covering
			// ChunkChecksumV1. Emit one now over the whole imported run [oldTotalLength, loopFileLength)
			// so the load-time walker has an authoritative hash and a later store's ChunkChecksumV1 does
			// not fold this un-hashed run into its own chunk hash. Mirrors appendBytesToHeadFile.
			final long metaLength = this.appendImportChunkChecksum(headFile, oldTotalLength, copyLength);

			this.cleanupImportHelper();

			this.writeTransactionsEntryStore(
				this.headFile          ,
				oldTotalLength         ,
				copyLength + metaLength,
				taskTimestamp          ,
				loopFileLength + metaLength
			);

			/*
			 * GC marking pass, deliberately AFTER the import became fully durable (transactions
			 * entry written): gc-protect each imported entity and enqueue it for reference
			 * traversal, exactly like stored/changed data (file positions were set in the
			 * registration loop above, so the mark phase can load the reference data). If any
			 * step above throws, no marks exist yet and the partially registered entities remain
			 * white, exactly as before this coordination existed - the GC never actively
			 * traverses a commit that did not complete. No sweep can interleave between
			 * registration and this pass: the import's gc signal is held until the task's
			 * cleanUp. The entities are guaranteed to still be registered here for the same
			 * reason.
			 */
			for(final StorageChannelImportBatch batch : importBatches)
			{
				for(StorageChannelImportEntity entity = batch.first(); entity != null; entity = entity.next())
				{
					entityCache.markEntityForChangedData(entityCache.getEntry(entity.objectId()));
				}
			}
		}

		/**
		 * Gathering-writes {@code buffers} to {@code headFile}, asserting exactly {@code expected} bytes land. On a
		 * short write it logs, truncates back to {@code truncateBackTo}, rewinds the buffers and retries once; a
		 * second short write throws {@link StorageExceptionIoWriting}. Shared by the dissolution-transfer and
		 * import covering-record writes.
		 *
		 * @param headFile       the head file being appended to.
		 * @param buffers        the buffers to write (and rewind before the retry).
		 * @param expected       the exact byte count the write must produce.
		 * @param truncateBackTo the file length to truncate back to before retrying.
		 * @param what           a short label for the operation, used in the log/exception messages.
		 */
		private void writeStoreExactOrTruncate(
			final StorageLiveDataFile.Default    headFile      ,
			final Iterable<? extends ByteBuffer> buffers       ,
			final long                           expected      ,
			final long                           truncateBackTo,
			final String                         what
		)
		{
			long bytes = this.writer.writeStore(headFile, buffers);
			if(bytes == expected)
			{
				return;
			}

			logger.error("{} error! Expected {} bytes written to head file but only {} bytes had been written! Trying again.", what, expected, bytes);
			headFile.truncate(truncateBackTo);
			for(final ByteBuffer buffer : buffers)
			{
				buffer.rewind();
			}

			bytes = this.writer.writeStore(headFile, buffers);
			if(bytes != expected)
			{
				logger.error("{} retry error! Expected {} bytes written to head file but only {} bytes had been written! Aborting!", what, expected, bytes);
				throw new StorageExceptionIoWriting(what + " to head file failed, only " + bytes + " of " + expected + " bytes written.");
			}
		}

		/**
		 * Writes a single covering {@code ChunkChecksumV1} meta record over the contiguous
		 * imported entity run {@code [chunkStart, chunkStart + chunkLength)} that {@link #importBatch}
		 * appended to the head file. The bytes were transferred zero-copy and are not otherwise in
		 * memory, so they are read back from the head file to compute the checksum. The meta
		 * record is then appended and registered as gap length (negative-length record &mdash; counts
		 * toward {@code fileTotalLength} only, matching load-side accounting). Returns the number of
		 * meta bytes written, or {@code 0} for an empty import (no checksum emitted).
		 *
		 * @param headFile    the import head file the entity run was written to
		 * @param chunkStart  file offset of the first imported entity byte (== old head total length)
		 * @param chunkLength total length of the contiguous imported entity run
		 * @return the meta-record byte count appended, or {@code 0} if {@code chunkLength == 0}
		 */
		private long appendImportChunkChecksum(
			final StorageLiveDataFile.Default headFile   ,
			final long                        chunkStart ,
			final long                        chunkLength
		)
		{
			// chunkLength == 0: empty import, nothing to cover. Otherwise emit only when the policy emits
			// AND the head file carries a FileHeaderV1, as in storeChunks. In practice the import
			// destination is always a fresh FileHeaderV1 file, so the second guard is defensive and keeps
			// the three emission sites (storeChunks, dissolution, import) consistent.
			if(chunkLength == 0L || !this.chunkChecksumCalculator.coversChunkWrites(headFile))
			{
				return 0L;
			}

			final long       headFileLength = headFile.totalLength()                                       ;
			final int        chunkLengthInt = X.checkArrayRange(chunkLength)                                ;
			final long       metaLength     = this.chunkChecksumCalculator.chunkChecksumRecordLength()        ;
			final ByteBuffer dataBuffer     = XMemory.allocateDirectNative(chunkLengthInt)                  ;
			final ByteBuffer metaBuffer     = this.chunkChecksumBuffer; // reused; cleared before each use
			metaBuffer.clear();
			try
			{
				headFile.readBytes(dataBuffer, chunkStart, chunkLength);
				dataBuffer.flip();

				// chunkStart is the imported run's in-file offset — the chunk start the record stores.
				this.chunkChecksumCalculator.writeChunkChecksumRecord(headFile, chunkStart, metaBuffer, dataBuffer);
				metaBuffer.flip();

				// writeStore (not the bare write) so the backup mirrors the import-covering checksum.
				this.writeStoreExactOrTruncate(
					headFile, this.chunkChecksumBufferWrap, metaLength, headFileLength, "Import checksum write");
			}
			finally
			{
				// metaBuffer is the reused chunkChecksumBuffer field — not freed here.
				XMemory.deallocateDirectByteBuffer(dataBuffer);
			}

			// the import-covering write succeeded (otherwise an exception propagated above): commit the
			// chained-tip advance for this imported chunk.
			this.chunkChecksumCalculator.commitChunkWrite(headFile);

			// negative-length meta record: contributes to fileTotalLength and fileMetaLength (so
			// dataFillRatio treats it as useful overhead rather than reclaimable gap).
			headFile.registerMetaLength(metaLength);

			return metaLength;
		}

		final void cleanupImportHelper()
		{
			this.importHelper = null;
		}

		final void importBatch(final StorageImportSource source, final long position, final long length)
		{
			// ignore dummy batches (e.g. transfer file continuation head dummy) and no-op batches in general
			if(length == 0)
			{
				return;
			}

			// 2 GiB loadability guard: the whole per-channel import run is written contiguously into a
			// single head file (no mid-run rollover), and commitImport appends one covering
			// ChunkChecksumV1 at the end. The resulting file must stay loadable into a single direct
			// ByteBuffer, i.e. within Integer.MAX_VALUE bytes. The trailing-checksum reserve is the
			// configured chunk-checksum record length (0 when the policy does not emit). Failing fast
			// here turns a silently-unloadable oversized file into a clean, rolled-back import error.
			final long checksumReserve = this.chunkChecksumCalculator.reservedLengthFor(this.headFile);
			final long projectedLength = this.importHelper.importHeadFileLength + length + checksumReserve;
			if(projectedLength > Integer.MAX_VALUE)
			{
				throw new StorageExceptionCommitSizeExceeded(this.channelIndex(), projectedLength);
			}

			this.checkForNewFile();
			this.writer.writeImport(source, position, length, this.headFile);
			this.importHelper.importHeadFileLength += length;
		}

		final void rollbackImport()
		{
			if(this.importHelper == null)
			{
				// already rolled back, abort before deleting valid files
				return;
			}

			final StorageLiveDataFile.Default first  = this.headFile.next;
			StorageLiveDataFile.Default       doomed = this.importHelper.preImportHeadFile.next;
			this.headFile.next = null;
			(first.prev = this.headFile = this.importHelper.preImportHeadFile).next = first;

			final BulkList<RuntimeException> exceptions = BulkList.New();
			while(doomed != null)
			{
				try
				{
					this.terminateFile(doomed);
				}
				catch(final RuntimeException e)
				{
					exceptions.add(e);
				}
				doomed = doomed.next;
			}
			this.cleanupImportHelper();

			if(!exceptions.isEmpty())
			{
				throw new StorageException(new MultiCauseException(exceptions.toArray(RuntimeException.class)));
			}
		}
		
		private void terminateFile(final StorageLiveDataFile.Default file)
		{
			// (12.08.2020 TM)FIXME: priv#351: where and how to check whether files may be deleted? Here? Weird!
			file.close();
			this.writer.delete(file, this.writeController, this.fileProvider);
		}

		final class ImportHelper implements Consumer<StorageChannelImportBatch>
		{
			final StorageLiveDataFile.Default         preImportHeadFile;
			final BulkList<StorageChannelImportBatch> importBatches     = BulkList.New(1000);
			StorageImportSource                       source           ;

			// Running projected length of the single import head file. Initialized to the fresh import
			// file's totalLength (its FileHeaderV1) and grown per batch so importBatch can guard the
			// 2 GiB load limit.
			long importHeadFileLength;


			ImportHelper(final StorageLiveDataFile.Default preImportHeadFile)
			{
				super();
				this.preImportHeadFile = preImportHeadFile;
			}

			@Override
			public void accept(final StorageChannelImportBatch batch)
			{
				this.importBatches.add(batch);
				StorageFileManager.Default.this.importBatch(this.source, batch.batchOffset(), batch.batchLength());
			}

			final ImportHelper setSource(final StorageImportSource source)
			{
				this.source = source;
				return this;
			}


		}
		
		static void throwImpossibleStoreLengthException(
			final long         timestamp            ,
			final long         currentTotalLength   ,
			final long         uncommittedDataLength,
			final ByteBuffer[] dataBuffers
		)
		{
			final VarString vs = VarString.New();
			vs
			.add("Impossible store length:").lf()
			.add("timestamp = ").add(timestamp).lf()
			.add("currentTotalLength = ").add(currentTotalLength).lf()
			.add("uncommittedDataLength = ").add(uncommittedDataLength).lf()
			.add("resulting length = ").add(currentTotalLength + uncommittedDataLength).lf()
			.add("dataBuffers: ")
			;
			if(dataBuffers.length == 0)
			{
				vs.add("[none]");
			}
			else
			{
				for(int i = 0; i < dataBuffers.length; i++)
				{
					vs.lf()
					.add('#').add(i).add(": ")
					.add("limit = ").add(dataBuffers[i].limit()).add(", ")
					.add("position = ").add(dataBuffers[i].position()).add(", ")
					.add("capacity = ").add(dataBuffers[i].capacity()).add(";")
					;
				}
			}
			
			throw new StorageException(vs.toString());
		}
		
		private boolean internalTransactionFileCheck(final boolean checkSize)
		{
			if(this.fileTransactions == null)
			{
				return true;
			}

			// nothing to compact: never request a durability barrier for a no-op - a steadily
			// storing application would otherwise enqueue one futile all-channel fsync barrier
			// per housekeeping cycle
			if(!this.transactionFileCleaner.requiresCompaction(checkSize))
			{
				return true;
			}

			/*
			 * Compaction durability gate: compacting collapses the log's store history, which
			 * the rollback of a not-everywhere-durable store might still need as its cut
			 * coordinate. Deferred until a storage flush confirmed every own store durable on
			 * all channels - collapsed history is then never rollback-relevant again.
			 */
			if(!this.isStoreDurable(this.lastWrittenStoreTimestamp))
			{
				this.storageFlushRequested = true;
				// remember an UNCONDITIONAL request so its intent survives the deferral: the barrier
				// completion must then compact regardless of size, not size-gated (see the contract of
				// executeDurabilityCoveredMaintenance and issueTransactionsLogCleanup).
				if(!checkSize)
				{
					this.unconditionalCompactionPending = true;
				}
				return false;
			}

			this.transactionFileCleaner.compactTransactionsFile(checkSize);

			return true;
		}

	}
		
}
