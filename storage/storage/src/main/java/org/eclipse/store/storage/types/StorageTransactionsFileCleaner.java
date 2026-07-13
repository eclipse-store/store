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

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.zip.CRC32;

import org.eclipse.serializer.afs.types.AFS;
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.util.X;
import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.exceptions.StorageException;
import org.eclipse.store.storage.exceptions.StorageExceptionTransactionsFileCompaction;
import org.eclipse.store.storage.types.StorageTransactionsAnalysis.EntryIterator;
import org.eclipse.store.storage.types.StorageTransactionsAnalysis.Logic;
import org.slf4j.Logger;

public interface StorageTransactionsFileCleaner
{
	/**
	 * Reduces the size of the storage transactions log file if it exceeds the configured limit.
	 * <br>
	 * Each storage file is reduced to a single FileCreation entry carrying its latest length;
	 * only the head file keeps its latest store timestamps. FileDeletion entries are kept if the
	 * storage data file still exists on the file system; otherwise, all entries of the deleted
	 * file are removed.
	 *
	 * @param checkSize if false the file is compacted regardless of its current size.
	 */
	public void compactTransactionsFile(boolean checkSize);

	/**
	 * Whether {@link #compactTransactionsFile(boolean)} would actually compact under the passed
	 * size-check mode, without performing any work. Lets callers avoid requesting durability
	 * barriers for a compaction that would be skipped anyway.
	 *
	 * @param checkSize the size-check mode, see {@link #compactTransactionsFile(boolean)}.
	 * @return whether a compaction is due.
	 */
	public boolean requiresCompaction(boolean checkSize);

	/**
	 * Heals a compaction that was interrupted by a crash. A complete swap file (length and
	 * checksum match its header) is the authoritative state of an interrupted rewrite and is
	 * restored into the transactions file; an incomplete one means the live file was never
	 * touched and the fragment is discarded. Must be called during initialization, before the
	 * transactions file is read.
	 *
	 * @param transactionsFile the channel's transactions file.
	 * @param channelIndex the channel index, for logging.
	 * @param writeController controller gating the repair writes.
	 * @return whether the transactions file content may not be reflected in the backup (an
	 *         interrupted rewrite bypassed or lost the backup queue), so the caller must have
	 *         the backup transactions file rebuilt.
	 */
	public static boolean recoverInterruptedCompaction(
		final AFile                  transactionsFile,
		final int                    channelIndex    ,
		final StorageWriteController writeController
	)
	{
		return Default.recoverInterruptedCompaction(transactionsFile, channelIndex, writeController);
	}

	public final class Default implements StorageTransactionsFileCleaner
	{
		///////////////////////////////////////////////////////////////////////////
		// nested types //
		/////////////////

		/**
		 * Captures the values the {@link StorageTransactionsAnalysis.EntryAggregator} validates
		 * but does not retain: creation and deletion timestamps and lengths, and the transfer
		 * entries logged after the latest store. The latter must survive the compaction
		 * verbatim: the initialization's rollback guard reads their source files from the
		 * reloaded log.
		 */
		private static final class EntrySupplements
		{
			static final class FileSupplement
			{
				long creationTimeStamp ;
				long creationFileLength;
				long deletionTimeStamp ;
				long deletionFileLength;
			}

			static final class TransferSupplement
			{
				long fileLength      ;
				long timeStamp       ;
				long sourceFileNumber;
				long sourceFileOffset;
			}

			final LinkedHashMap<Long, FileSupplement> files              = new LinkedHashMap<>();
			final BulkList<TransferSupplement>        postStoreTransfers = BulkList.New()      ;

			void accept(final long address)
			{
				switch(Logic.getEntryType(address))
				{
					case Logic.TYPE_FILE_CREATION:
					{
						final FileSupplement supplement = new FileSupplement();
						supplement.creationTimeStamp  = Logic.getEntryTimestamp(address);
						supplement.creationFileLength = Logic.getFileLength(address);
						this.files.put(Logic.getFileNumber(address), supplement);
						// transfers into the now closed file are absorbed into its length; only
						// transfers above the current head's latest store need reproducing
						this.postStoreTransfers.clear();
						break;
					}
					case Logic.TYPE_FILE_DELETION:
					{
						final FileSupplement supplement = this.files.get(Logic.getFileNumber(address));
						if(supplement != null)
						{
							supplement.deletionTimeStamp  = Logic.getEntryTimestamp(address);
							supplement.deletionFileLength = Logic.getFileLength(address);
						}
						break;
					}
					case Logic.TYPE_STORE:
					{
						this.postStoreTransfers.clear();
						break;
					}
					case Logic.TYPE_TRANSFER:
					{
						final TransferSupplement transfer = new TransferSupplement();
						transfer.fileLength       = Logic.getFileLength(address)   ;
						transfer.timeStamp        = Logic.getEntryTimestamp(address);
						transfer.sourceFileNumber = Logic.getFileNumber(address)   ;
						transfer.sourceFileOffset = Logic.getSpecialOffset(address);
						this.postStoreTransfers.add(transfer);
						break;
					}
					case Logic.TYPE_FILE_TRUNCATION:
					{
						// the truncation already cut any transfers above the new length
						this.postStoreTransfers.clear();
						break;
					}
					default:
					{
						break;
					}
				}
			}

			FileSupplement get(final long fileNumber)
			{
				return this.files.get(fileNumber);
			}

		}

		/**
		 * The assembled compacted log plus its contiguous, type-homogeneous sections, each
		 * written through the matching mirrored writer call.
		 */
		private static final class CompactedContent
		{
			static final class Section
			{
				final byte type ;
				final int  start;
				final int  end  ;

				Section(final byte type, final int start, final int end)
				{
					super();
					this.type  = type ;
					this.start = start;
					this.end   = end  ;
				}
			}

			final ByteBuffer        buffer          ;
			final BulkList<Section> sections        ;
			final long              headLatestLength;

			CompactedContent(
				final ByteBuffer        buffer          ,
				final BulkList<Section> sections        ,
				final long              headLatestLength
			)
			{
				super();
				this.buffer           = buffer          ;
				this.sections         = sections        ;
				this.headLatestLength = headLatestLength;
			}
		}

		///////////////////////////////////////////////////////////////////////////
		// constants //
		//////////////

		/*
		 * Sibling of the transactions file holding the complete compacted content while the
		 * live file is rewritten. Layout: [8B content length][8B CRC-32 of content][content]
		 */
		private static final String SWAP_FILE_NAME_SUFFIX = "_compaction";
		private static final String SWAP_FILE_TYPE        = "swp";
		private static final int    SWAP_HEADER_LENGTH    = 2 * XMemory.byteSize_long();


		///////////////////////////////////////////////////////////////////////////
		// static fields //
		//////////////////

		private final static Logger logger = Logging.getLogger(StorageTransactionsFileCleaner.class);


		///////////////////////////////////////////////////////////////////////////
		// static methods //
		///////////////////

		static AFile compactionSwapFile(final AFile transactionsFile)
		{
			return transactionsFile.parent().ensureFile(
				transactionsFile.name() + SWAP_FILE_NAME_SUFFIX,
				SWAP_FILE_TYPE
			);
		}

		private static long crc32(final ByteBuffer content)
		{
			final CRC32 crc = new CRC32();
			crc.update(content.duplicate());
			return crc.getValue();
		}

		private static void writeSwapFile(final AFile swapFile, final ByteBuffer content)
		{
			final ByteBuffer header = XMemory.allocateDirectNative(SWAP_HEADER_LENGTH);
			try
			{
				final long headerAddress = XMemory.getDirectByteBufferAddress(header);
				XMemory.set_long(headerAddress                           , content.remaining());
				XMemory.set_long(headerAddress + XMemory.byteSize_long() , crc32(content)     );

				swapFile.ensureExists();
				AFS.executeWriting(swapFile, wf ->
				{
					// discard a stale fragment from an older interruption
					wf.truncate(0);
					wf.writeBytes(X.ArrayView(header, content));
					// the swap must be durably complete before the live file is touched, or a
					// power loss could invalidate it after the truncate persisted. Residual: the
					// file's directory entry cannot be synced (no such primitive), so some file
					// systems may still lose the whole swap file on power loss.
					wf.synchronize();
				});
			}
			finally
			{
				XMemory.deallocateDirectByteBuffer(header);
			}
		}

		private static void deleteSwapFile(final AFile swapFile)
		{
			// a leftover complete swap file would be restored by a later initialization,
			// discarding every entry written after this point - fail loudly
			AFS.executeWriting(swapFile, wf ->
			{
				if(wf.exists() && !wf.delete())
				{
					throw new StorageException(
						"Could not delete compaction swap file " + swapFile.toPathString()
					);
				}
			});
		}

		private static void deleteSwapFileOrEscalate(final AFile swapFile, final Throwable cause)
		{
			try
			{
				deleteSwapFile(swapFile);
			}
			catch(final Throwable deleteFailure)
			{
				// a possibly complete swap file could not be removed: same trap as a failed rewrite
				cause.addSuppressed(deleteFailure);
				throw new StorageExceptionTransactionsFileCompaction(
					"Could not remove the compaction swap file after a failed swap write: "
					+ swapFile.toPathString(),
					cause
				);
			}
		}

		/**
		 * Returns the swap file's content if its length and CRC-32 match the header,
		 * {@code null} otherwise.
		 */
		private static ByteBuffer readCompleteSwapContent(final AFile swapFile)
		{
			final long swapSize = swapFile.size();
			if(swapSize < SWAP_HEADER_LENGTH)
			{
				return null;
			}

			return AFS.apply(swapFile, rf ->
			{
				final ByteBuffer header = XMemory.allocateDirectNative(SWAP_HEADER_LENGTH);
				try
				{
					rf.readBytes(header, 0, SWAP_HEADER_LENGTH);
					final long headerAddress = XMemory.getDirectByteBufferAddress(header);
					final long contentLength = XMemory.get_long(headerAddress);
					final long contentCrc    = XMemory.get_long(headerAddress + XMemory.byteSize_long());
					if(contentLength < 0 || contentLength != swapSize - SWAP_HEADER_LENGTH)
					{
						return null;
					}

					final ByteBuffer content = XMemory.allocateDirectNative(X.checkArrayRange(contentLength));
					boolean valid = false;
					try
					{
						rf.readBytes(content, SWAP_HEADER_LENGTH, contentLength);
						content.flip();
						valid = crc32(content) == contentCrc;
						return valid ? content : null;
					}
					finally
					{
						if(!valid)
						{
							XMemory.deallocateDirectByteBuffer(content);
						}
					}
				}
				finally
				{
					XMemory.deallocateDirectByteBuffer(header);
				}
			});
		}

		private static boolean transactionsFileEquals(final AFile transactionsFile, final ByteBuffer content)
		{
			if(!transactionsFile.exists() || transactionsFile.size() != content.remaining())
			{
				return false;
			}
			if(content.remaining() == 0)
			{
				return true;
			}

			return AFS.apply(transactionsFile, rf ->
			{
				final ByteBuffer live = XMemory.allocateDirectNative(content.remaining());
				try
				{
					rf.readBytes(live, 0, live.capacity());
					live.flip();
					return live.equals(content.duplicate());
				}
				finally
				{
					XMemory.deallocateDirectByteBuffer(live);
				}
			});
		}

		private static Iterable<? extends ByteBuffer> sectionView(
			final ByteBuffer buffer,
			final int        start ,
			final int        end
		)
		{
			final ByteBuffer view = buffer.duplicate();
			view.limit(end).position(start);
			return X.ArrayView(view);
		}

		static boolean recoverInterruptedCompaction(
			final AFile                  transactionsFile,
			final int                    channelIndex    ,
			final StorageWriteController writeController
		)
		{
			final AFile swapFile = compactionSwapFile(transactionsFile);
			if(!swapFile.exists())
			{
				return false;
			}

			final ByteBuffer content = readCompleteSwapContent(swapFile);
			if(content == null)
			{
				// crash while the swap file was still being written: the live file was never touched
				logger.warn(
					"Channel {}: discarding incomplete compaction swap file {}",
					channelIndex,
					swapFile.toPathString()
				);
				if(writeController.isWritable())
				{
					try
					{
						deleteSwapFile(swapFile);
					}
					catch(final RuntimeException e)
					{
						// the fragment is inert (fails the header check) and gets replaced by the
						// next compaction, so a failed delete must not block the start
						logger.warn(
							"Channel {}: could not delete incomplete compaction swap file {}",
							channelIndex,
							swapFile.toPathString(),
							e
						);
					}
				}
				return false;
			}

			try
			{
				if(transactionsFileEquals(transactionsFile, content))
				{
					// the interrupted rewrite had completed; only the swap deletion is outstanding,
					// so even a read-only start may proceed (leaving the swap for a writable start)
					if(writeController.isWritable())
					{
						deleteSwapFile(swapFile);
						// the completed rewrite may not be reflected in the backup (its queued
						// mirror items died with the process) - report it for a backup rebuild
						return true;
					}
					logger.warn(
						"Channel {}: transaction file already matches compaction swap file {}; leaving it for the next writable start",
						channelIndex,
						swapFile.toPathString()
					);
					return false;
				}

				// the live file may be empty, partial or stale; the swap content is the
				// authoritative compacted state (nothing was appended after its creation)
				logger.warn(
					"Channel {}: restoring transaction file {} from interrupted compaction swap file {}",
					channelIndex,
					transactionsFile.toPathString(),
					swapFile.toPathString()
				);
				writeController.validateIsWritable();

				transactionsFile.ensureExists();
				AFS.executeWriting(transactionsFile, wf ->
				{
					wf.truncate(0);
					wf.writeBytes(content);
					// force the restored log before discarding its only heal source
					wf.synchronize();
				});

				deleteSwapFile(swapFile);

				return true;
			}
			finally
			{
				XMemory.deallocateDirectByteBuffer(content);
			}
		}


		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final StorageLiveTransactionsFile storageLiveTransactionsFile;
		private final int channelIndex;
		private final long transactionFileSizeLimit;
		private final StorageFileWriter storageFileWriter;
		private final StorageLiveFileProvider fileProvider;

		// the log's length after the last completed compaction; the log is append-only between
		// compactions, so an unchanged length means unchanged content whose compaction would be
		// a verbatim no-op. -1 = no compaction happened yet this session.
		private long lastCompactedLength = -1;


		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		public Default(
			final StorageLiveTransactionsFile fileTransactions,
			final int channelIndex,
			final long transactionFileSizeLimit,
			final StorageLiveFileProvider fileProvider,
			final StorageFileWriter storageFileWriter)
		{
			super();
			this.channelIndex = channelIndex;
			this.transactionFileSizeLimit = transactionFileSizeLimit;
			this.storageLiveTransactionsFile = fileTransactions;
			this.fileProvider = fileProvider;
			this.storageFileWriter = storageFileWriter;
		}
		
		
		///////////////////////////////////////////////////////////////////////////
		// declared methods //
		/////////////////////

		private void compactTransactionsFileInternal()
		{
			logger.info("Compacting transaction file {}", this.storageLiveTransactionsFile.identifier());

			/*
			 * One pass: the aggregator (the reload validator itself) is the single source of truth
			 * for per-file lengths, deleted flags and the head file's store anchors; the supplements
			 * keep only the creation/deletion values it discards. Read through the file's managed
			 * access: releasing a separate lease retires the channel's shared handle and races a
			 * concurrently copying backup thread.
			 */
			final StorageTransactionsAnalysis.EntryAggregator aggregator =
				new StorageTransactionsAnalysis.EntryAggregator(this.channelIndex);
			final EntrySupplements supplements = new EntrySupplements();
			this.storageLiveTransactionsFile.processBy((EntryIterator)(address, availableEntryLength) ->
			{
				if(!aggregator.accept(address, availableEntryLength))
				{
					return false;
				}
				if(availableEntryLength >= 0)
				{
					supplements.accept(address);
				}
				return true;
			});
			final StorageTransactionsAnalysis analysis = aggregator.yield(this.storageLiveTransactionsFile);

			final CompactedContent compacted = this.assembleCompactedContent(analysis, supplements);
			try
			{
				if(this.liveFileEquals(compacted.buffer))
				{
					// already compacted: rewriting byte-identical content would only churn the
					// swap file, two fsyncs and the backup queue on every housekeeping cycle
					this.lastCompactedLength = this.storageLiveTransactionsFile.size();
					return;
				}

				// the content becomes durable in the swap file BEFORE the live file is touched,
				// so an interrupted rewrite is healed by #recoverInterruptedCompaction
				final AFile swapFile = compactionSwapFile(this.storageLiveTransactionsFile.file());
				try
				{
					writeSwapFile(swapFile, compacted.buffer);
				}
				catch(final Throwable e)
				{
					// live file untouched; the swap may be a fragment or even complete (a failed
					// synchronize after a complete write) - remove it so no stale restore can occur
					deleteSwapFileOrEscalate(swapFile, e);
					throw e;
				}

				try
				{
					this.rewriteTransactionsFile(compacted, swapFile);
				}
				catch(final Throwable e)
				{
					// the live file's state is unknown, but the in-memory content is still
					// authoritative: retry the idempotent rewrite once
					try
					{
						this.rewriteTransactionsFile(compacted, swapFile);
						logger.warn("Transaction file compaction failed transiently and was healed by retry", e);
					}
					catch(final Throwable retryFailure)
					{
						/*
						 * The live file stays broken and the retained swap file is its only heal
						 * source; any further append would be discarded by the swap restore on the
						 * next initialization. The dedicated type makes the channel stop processing
						 * (see StorageChannel#issuedTransactionsLogCleanup).
						 */
						e.addSuppressed(retryFailure);
						throw new StorageExceptionTransactionsFileCompaction(
							"Transaction file rewrite failed and could not be reconciled, swap file retained: "
							+ swapFile.toPathString(),
							e
						);
					}
				}

				this.lastCompactedLength = this.storageLiveTransactionsFile.size();
			}
			finally
			{
				XMemory.deallocateDirectByteBuffer(compacted.buffer);
			}
		}

		private boolean liveFileEquals(final ByteBuffer content)
		{
			// managed access only: a separate lease+release would retire the channel's shared handle
			final long length = content.remaining();
			if(this.storageLiveTransactionsFile.size() != length)
			{
				return false;
			}
			if(length == 0)
			{
				return true;
			}

			final ByteBuffer live = XMemory.allocateDirectNative((int)length);
			try
			{
				this.storageLiveTransactionsFile.readBytes(live, 0, length);
				live.flip();
				return live.equals(content.duplicate());
			}
			finally
			{
				XMemory.deallocateDirectByteBuffer(live);
			}
		}

		private void rewriteTransactionsFile(final CompactedContent compacted, final AFile swapFile)
		{
			this.storageFileWriter.truncate(this.storageLiveTransactionsFile, 0, this.fileProvider);
			this.writeCompactedContent(compacted);

			// force the rewritten log before discarding its only heal source
			this.storageLiveTransactionsFile.synchronize();
			deleteSwapFile(swapFile);
		}

		/**
		 * Assembles the compacted log into one buffer (content length = buffer limit): one
		 * FileCreation entry per still existing file, carrying the file's latest length - the pattern
		 * the initialization's derive fallback boots from. The head file keeps its store anchors and
		 * post-store transfers, so the reloaded reconciliation state (both anchors, transfer sources)
		 * matches the uncompacted log's. A store-less head collapses both anchors at its creation; to
		 * reproduce that, the latest-store anchor is emitted in the preceding file's group - stamping
		 * it under the head would let a reconciliation that cannot agree on it silently keep or
		 * truncate content the uncompacted log guards loudly.
		 * <p>
		 * Deletion entries come after all per-file groups: on reload a file is registered only when
		 * the next file's creation entry is reached, so an inline deletion would not find its file.
		 */
		private CompactedContent assembleCompactedContent(
			final StorageTransactionsAnalysis analysis   ,
			final EntrySupplements            supplements
		)
		{
			// the head file (highest number, registered last) is exempt from the existence probe:
			// lazily materializing backends report a still-empty head file as non-existing, and
			// dropping it would re-attribute its anchors to the previous file
			final StorageTransactionEntry headFile = analysis.transactionsFileEntries().values().peek();

			final BulkList<StorageTransactionEntry> files = BulkList.New();
			for(final StorageTransactionEntry file : analysis.transactionsFileEntries().values())
			{
				if(file == headFile || this.fileProvider.provideDataFile(this.channelIndex, file.fileNumber()).exists())
				{
					files.add(file);
				}
				else
				{
					logger.debug("channel {} file {} no more existing, removing all entries from transactions log ", this.channelIndex, file.fileNumber());
				}
			}

			final long latestTs = analysis.headFileLatestTimestamp();
			final long lcTs     = analysis.headFileLastConsistentStoreTimestamp();
			final long lcLen    = analysis.headFileLastConsistentStoreLength();

			// store timestamps are strictly increasing, so a head with an own store separates the
			// anchors while a store-less head carries both collapsed onto the latest timestamp
			final boolean headHasOwnStore = latestTs > 0 && lcTs < latestTs;

			// upper bound; the actual content length is set as the buffer's limit after assembly
			final long maximumLength = files.size() * (long)(
				Logic.entryLengthFileCreation() + Logic.entryLengthFileDeletion()
			)
				+ 3L * Logic.entryLengthStore()
				+ supplements.postStoreTransfers.size() * (long)Logic.entryLengthTransfer()
			;

			final ByteBuffer content = XMemory.allocateDirectNative(X.checkArrayRange(maximumLength));
			final long       address = XMemory.getDirectByteBufferAddress(content);

			final BulkList<CompactedContent.Section> sections = BulkList.New();

			int offset       = 0;
			int sectionStart = 0;
			for(final StorageTransactionEntry file : files)
			{
				if(file != headFile)
				{
					final EntrySupplements.FileSupplement supplement = supplements.get(file.fileNumber());
					Logic.initializeEntryFileCreation(address + offset);
					Logic.setEntryFileCreation(address + offset, file.length(), supplement.creationTimeStamp, file.fileNumber());
					offset += Logic.entryLengthFileCreation();
				}
			}
			offset = addSection(sections, Logic.TYPE_FILE_CREATION, sectionStart, offset);

			final boolean headFollowsClosedFile = files.size() > 1;
			if(!headHasOwnStore && latestTs > 0 && headFollowsClosedFile)
			{
				/*
				 * The anchor collapse must be reproduced: the latest store happened before the
				 * head's creation, so its entry belongs to the preceding file's group (with that
				 * file's final length as its coordinate). On reload, the head's creation entry
				 * then re-collapses both anchors exactly like the uncompacted log did.
				 */
				final StorageTransactionEntry lastClosedFile = files.at(files.size() - 2);
				sectionStart = offset;
				Logic.initializeEntryStore(address + offset);
				Logic.setEntryStore(address + offset, lastClosedFile.length(), latestTs);
				offset = addSection(sections, Logic.TYPE_STORE, sectionStart, offset + Logic.entryLengthStore());
			}

			// the head's creation entry carries the pre-latest-store length: it seeds the
			// reloaded rollback anchor until (and unless) a store entry overwrites it
			sectionStart = offset;
			Logic.initializeEntryFileCreation(address + offset);
			Logic.setEntryFileCreation(
				address + offset,
				lcLen,
				supplements.get(headFile.fileNumber()).creationTimeStamp,
				headFile.fileNumber()
			);
			offset = addSection(sections, Logic.TYPE_FILE_CREATION, sectionStart, offset + Logic.entryLengthFileCreation());

			sectionStart = offset;
			if(headHasOwnStore)
			{
				if(lcTs > 0)
				{
					Logic.initializeEntryStore(address + offset);
					Logic.setEntryStore(address + offset, lcLen, lcTs);
					offset += Logic.entryLengthStore();
				}
				// the store's own length; transfers logged above it are reproduced behind it
				Logic.initializeEntryStore(address + offset);
				Logic.setEntryStore(address + offset, analysis.headFileLatestStoreOwnLength(), latestTs);
				offset += Logic.entryLengthStore();
			}
			else if(latestTs > 0 && !headFollowsClosedFile)
			{
				// no preceding group can host the anchor (its file was removed): keep it under
				// the head with the head's base coordinate
				Logic.initializeEntryStore(address + offset);
				Logic.setEntryStore(address + offset, lcLen, latestTs);
				offset += Logic.entryLengthStore();
			}
			offset = addSection(sections, Logic.TYPE_STORE, sectionStart, offset);

			sectionStart = offset;
			for(final EntrySupplements.TransferSupplement transfer : supplements.postStoreTransfers)
			{
				Logic.initializeEntryTransfer(address + offset);
				Logic.setEntryTransfer(
					address + offset,
					transfer.fileLength      ,
					transfer.timeStamp       ,
					transfer.sourceFileNumber,
					transfer.sourceFileOffset
				);
				offset += Logic.entryLengthTransfer();
			}
			offset = addSection(sections, Logic.TYPE_TRANSFER, sectionStart, offset);

			sectionStart = offset;
			for(final StorageTransactionEntry file : files)
			{
				if(file.isDeleted())
				{
					final EntrySupplements.FileSupplement supplement = supplements.get(file.fileNumber());
					Logic.initializeEntryFileDeletion(address + offset);
					Logic.setEntryFileDeletion(address + offset, supplement.deletionFileLength, supplement.deletionTimeStamp, file.fileNumber());
					offset += Logic.entryLengthFileDeletion();
				}
			}
			offset = addSection(sections, Logic.TYPE_FILE_DELETION, sectionStart, offset);

			content.limit(offset);

			return new CompactedContent(content, sections, analysis.headFileLatestLength());
		}

		private static int addSection(
			final BulkList<CompactedContent.Section> sections,
			final byte                               type    ,
			final int                                start   ,
			final int                                end
		)
		{
			if(end > start)
			{
				sections.add(new CompactedContent.Section(type, start, end));
			}
			return end;
		}

		/**
		 * Writes the assembled content into the truncated live file: contiguous,
		 * type-homogeneous sections, one mirrored writer call each, preserving writer semantics
		 * such as backup mirroring.
		 */
		private void writeCompactedContent(final CompactedContent compacted)
		{
			for(final CompactedContent.Section section : compacted.sections)
			{
				final Iterable<? extends ByteBuffer> view =
					sectionView(compacted.buffer, section.start, section.end);
				switch(section.type)
				{
					case Logic.TYPE_FILE_CREATION:
					{
						this.storageFileWriter.writeTransactionEntryCreate(
							this.storageLiveTransactionsFile, view, null
						);
						break;
					}
					case Logic.TYPE_STORE:
					{
						this.storageFileWriter.writeTransactionEntryStore(
							this.storageLiveTransactionsFile, view, null, 0, compacted.headLatestLength
						);
						break;
					}
					case Logic.TYPE_TRANSFER:
					{
						this.storageFileWriter.writeTransactionEntryTransfer(
							this.storageLiveTransactionsFile, view, null, 0, compacted.headLatestLength
						);
						break;
					}
					case Logic.TYPE_FILE_DELETION:
					{
						this.storageFileWriter.writeTransactionEntryDelete(
							this.storageLiveTransactionsFile, view, null
						);
						break;
					}
					default:
					{
						throw new StorageException("Unhandled compacted section type: " + section.type);
					}
				}
			}
		}

		///////////////////////////////////////////////////////////////////////////
		// override methods //
		/////////////////////

		@Override
		public boolean requiresCompaction(final boolean checkSize)
		{
			// unchanged length since the last compaction = unchanged content = verbatim no-op:
			// skips the acceptor's retry after the flush barrier's completion already compacted,
			// and the every-cycle recompaction of a compacted log still above the size limit.
			if(this.storageLiveTransactionsFile.size() == this.lastCompactedLength)
			{
				return false;
			}
			return !checkSize
				|| this.storageLiveTransactionsFile.size() > this.transactionFileSizeLimit
			;
		}

		@Override
		public void compactTransactionsFile(final boolean checkSize)
		{
			if(!this.requiresCompaction(checkSize))
			{
				return;
			}
			if(checkSize)
			{
				logger.info("Transaction file {} size exceeds limit of {} bytes", this.storageLiveTransactionsFile.identifier(), this.transactionFileSizeLimit);
			}
			this.compactTransactionsFileInternal();
		}

	}

	/**
	 * Defines a creator for StorageTransactionsFileCleaner instances.
	 */
	public interface Creator
	{
		/**
		 * Create a StorageTransactionsFileCleaner instance.
		 * 
		 * @param fileTransactions the transaction File.
		 * @param channelIndex	the channel index.
		 * @param transactionFileSizeLimit transaction file size limit.
		 * @param fileProvider the storage file provider.
		 * @param storageFileWriter the storage file writer.
		 * @return a StorageTransactionsFileCleaner instance.
		 */
		public StorageTransactionsFileCleaner createStorageTransactionsFileCleaner(
			StorageLiveTransactionsFile
			fileTransactions,
			int channelIndex,
			long transactionFileSizeLimit,
			StorageLiveFileProvider fileProvider,
			StorageFileWriter storageFileWriter);
	
		/**
		 * Creates the default StorageTransactionsFileCleaner.Creator instance.
		 * 
		 * @return the default StorageTransactionsFileCleaner.Creator.
		 */
		public static Creator Default()
		{
			return new Default();
		}
		
		/**
		 * Default StorageTransactionsFileCleaner.Creator instance
		 */
		public class Default implements Creator
		{
			@Override
			public StorageTransactionsFileCleaner createStorageTransactionsFileCleaner(
				final StorageLiveTransactionsFile fileTransactions,
				final int channelIndex,
				final long transactionFileSizeLimit,
				final StorageLiveFileProvider fileProvider,
				final StorageFileWriter storageFileWriter)
			{
				return new StorageTransactionsFileCleaner.Default(
					fileTransactions,
					channelIndex,
					transactionFileSizeLimit,
					fileProvider,
					storageFileWriter);
			}
		}
	}
	
}
