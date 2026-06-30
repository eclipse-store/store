package org.eclipse.store.storage.embedded.tools.storage.converter;

/*-
 * #%L
 * EclipseStore Storage Embedded Tools Storage Converter
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

import java.io.Closeable;
import java.nio.ByteBuffer;

import org.eclipse.serializer.afs.types.AWritableFile;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.exceptions.StorageException;
import org.eclipse.store.storage.types.StorageChunkChecksumCalculator.Algorithm;
import org.eclipse.store.storage.types.StorageChunkChecksumProvider;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.types.StorageDataFileEvaluator;
import org.eclipse.store.storage.types.StorageLiveFileProvider;
import org.eclipse.store.storage.types.StorageMetaRecord;
import org.slf4j.Logger;


/**
 * Handles everything related to the "Target Storage" of the storage converter.
 * <p>
 * Two strategies, selected once by {@link #New(StorageConfiguration)} from the target configuration's
 * {@link StorageChunkChecksumProvider}:
 * <ul>
 *   <li>{@link NotChecksummed} &mdash; raw-streaming: entities are written verbatim and files roll over at
 *       {@link StorageDataFileEvaluator#fileMaximumSize()}. Used when the provider does not emit.</li>
 *   <li>{@link Checksummed} &mdash; mirrors the engine's coalescing-dissolution write: each file opens with a
 *       {@code FileHeaderV1}, live entities are coalesced into chunks of about
 *       {@link StorageDataFileEvaluator#coalesceChunkTargetBytes()} bytes each followed by a covering
 *       chunk-checksum record, and the chained tip threads across a channel's files.</li>
 * </ul>
 */
public interface StorageConverterTarget extends Closeable
{
	/**
	 * Write the supplied TypeDictionary String as the new target's TypeDictionary.
	 *
	 * @param srcTypeDictionary the source type dictionary as String
	 */
	void storeTypeDictionary(String srcTypeDictionary);

	/**
	 * Write the content of the supplied ByteBuffer to the target's storage system.
	 *
	 * @param buffer content to be transferred
	 * @param oid    object id (selects the target channel by {@code oid % channelCount})
	 */
	void transferBytes(ByteBuffer buffer, long oid);

	@Override
	void close();


	/**
	 * Creates the appropriate target for the supplied configuration: a {@link Checksummed} target when the
	 * configured {@link StorageChunkChecksumProvider} emits, otherwise a {@link NotChecksummed} target. The
	 * initial per-channel files are opened after construction (so a {@link Checksummed} target's header write
	 * sees its fully initialized state).
	 *
	 * @param storageConfiguration the target {@link StorageConfiguration}
	 * @return a ready-to-use {@link StorageConverterTarget}
	 */
	static StorageConverterTarget New(final StorageConfiguration storageConfiguration)
	{
		final StorageConverterTarget.Abstract target =
			storageConfiguration.chunkChecksumProvider().policy().emit()
				? new Checksummed(storageConfiguration)
				: new NotChecksummed(storageConfiguration);
		target.openInitialFiles();
		return target;
	}


	///////////////////////////////////////////////////////////////////////////
	// shared base //
	////////////////

	/**
	 * Shared state and file management for both target strategies: the file provider, channel count, data-file
	 * evaluator, the per-channel target-file array and the type-dictionary write. Subclasses contribute the
	 * write strategy ({@link #transferBytes}, {@link #close}) and an optional per-file-creation hook
	 * ({@link #onFileCreated}).
	 */
	abstract class Abstract implements StorageConverterTarget
	{
		final static Logger logger = Logging.getLogger(StorageConverterTarget.class);


		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		final StorageLiveFileProvider      fileProvider;
		final int                          channelCount;
		final StorageDataFileEvaluator     dataFileEvaluator;
		final StorageConverterTargetFile[] files;


		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Abstract(final StorageConfiguration storageConfiguration)
		{
			super();
			this.fileProvider      = storageConfiguration.fileProvider();
			this.channelCount      = storageConfiguration.channelCountProvider().getChannelCount();
			this.dataFileEvaluator = storageConfiguration.dataFileEvaluator();
			this.files             = new StorageConverterTargetFile[this.channelCount];
		}


		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		/**
		 * Opens the initial (number 0) file of every channel. Called by {@link #New(StorageConfiguration)}
		 * <b>after</b> the instance is fully constructed, so a subclass's {@link #onFileCreated} hook sees its
		 * own initialized state (it must not be called from a constructor).
		 */
		final void openInitialFiles()
		{
			for (int channelIndex = 0; channelIndex < this.channelCount; channelIndex++)
			{
				this.files[channelIndex] = this.createNewStorageFile(0, channelIndex);
			}
		}

		final StorageConverterTargetFile createNewStorageFile(final long fileNumber, final int channelIndex)
		{
			logger.debug("Creating new storage file {} for channel {}.", fileNumber, channelIndex);

			final AWritableFile file = this.fileProvider.provideDataFile(channelIndex, fileNumber).useWriting();
			file.ensureExists();

			final StorageConverterTargetFile targetFile = new StorageConverterTargetFile(file, fileNumber);
			this.onFileCreated(targetFile, channelIndex);
			return targetFile;
		}

		/**
		 * Hook invoked right after a target file is created and ensured to exist. Default: no-op
		 * ({@link NotChecksummed}). {@link Checksummed} overrides it to write the {@code FileHeaderV1}.
		 */
		void onFileCreated(final StorageConverterTargetFile file, final int channelIndex)
		{
			// no-op
		}

		@Override
		public final void storeTypeDictionary(final String srcTypeDictionary)
		{
			logger.debug("Transferring type dictionary to target.");

			this.fileProvider.provideTypeDictionaryIoHandler().storeTypeDictionary(srcTypeDictionary);
		}
	}


	///////////////////////////////////////////////////////////////////////////
	// no-checksum strategy //
	/////////////////////////

	/**
	 * Raw-streaming target: writes entity bytes verbatim and rolls over at
	 * {@link StorageDataFileEvaluator#fileMaximumSize()}.
	 */
	final class NotChecksummed extends Abstract
	{
		NotChecksummed(final StorageConfiguration storageConfiguration)
		{
			super(storageConfiguration);
		}

		@Override
		public void transferBytes(final ByteBuffer buffer, final long oid)
		{
			final int channelIndex = (int) (oid % this.channelCount);

			logger.trace("Transferring blob pos: {}, limit: {} to target channel {}.",
					buffer.position(), buffer.limit(), channelIndex);

			StorageConverterTargetFile file = this.files[channelIndex];

			if (buffer.limit() - buffer.position() + file.size() > this.dataFileEvaluator.fileMaximumSize())
			{
				file.release();
				file = this.createNewStorageFile(file.fileNumber() + 1, channelIndex);
				this.files[channelIndex] = file;
			}

			this.files[channelIndex].writeBytes(buffer);
		}

		@Override
		public void 	close()
		{
			for (final StorageConverterTargetFile file : this.files)
			{
				file.release();
			}
		}
	}


	///////////////////////////////////////////////////////////////////////////
	// checksum strategy //
	//////////////////////

	/**
	 * Chunk-checksum-emitting target: each file opens with a {@code FileHeaderV1}; live entities are coalesced
	 * into chunks of about {@link StorageDataFileEvaluator#coalesceChunkTargetBytes()} bytes, each flushed with
	 * a covering chunk-checksum record; files roll over at {@link StorageDataFileEvaluator#fileMaximumSize()}
	 * (reserving the record). Each channel owns a stateful primary {@link Algorithm} whose chain tip threads
	 * across that channel's files.
	 */
	final class Checksummed extends Abstract
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final long         coalesceChunkTargetBytes;
		private final int          headerLength;
		private final int          recordLength;
		private final Algorithm[]  algorithms;   // per channel; chain tip threads across files
		private final ByteBuffer[] chunkBuffers; // per channel; single-chunk accumulators
		private final ByteBuffer   headerBuffer; // reused
		private final ByteBuffer   recordBuffer; // reused


		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Checksummed(final StorageConfiguration storageConfiguration)
		{
			super(storageConfiguration);

			final StorageChunkChecksumProvider provider = storageConfiguration.chunkChecksumProvider();
			this.coalesceChunkTargetBytes = this.dataFileEvaluator.coalesceChunkTargetBytes();
			this.headerLength             = StorageMetaRecord.LENGTH_FILEHEADERV1;
			this.algorithms               = new Algorithm[this.channelCount];
			this.chunkBuffers             = new ByteBuffer[this.channelCount];

			final int initialChunkCapacity = (int) Math.min(
				Math.max(this.coalesceChunkTargetBytes, 4096L),
				16L * 1024 * 1024
			);
			int algorithmRecordLength = 0;
			for (int channelIndex = 0; channelIndex < this.channelCount; channelIndex++)
			{
				final Algorithm algorithm = provider.createPrimaryAlgorithm();
				this.algorithms[channelIndex]   = algorithm;
				this.chunkBuffers[channelIndex] = XMemory.allocateDirectNative(initialChunkCapacity);
				algorithmRecordLength           = (int) algorithm.recordLength();
			}
			this.recordLength = algorithmRecordLength;
			this.headerBuffer = XMemory.allocateDirectNative(this.headerLength);
			this.recordBuffer = XMemory.allocateDirectNative(this.recordLength);
		}


		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		void onFileCreated(final StorageConverterTargetFile file, final int channelIndex)
		{
			// Write the FileHeaderV1 as the new file's first entry. The chain root snapshots the channel
			// algorithm's current tip (the predecessor file's final tip, or the initial seed for the first
			// file) so a chained file verifies self-contained; all-zero for a non-chained kind.
			final Algorithm algorithm = this.algorithms[channelIndex];
			final byte[]    chainRoot = algorithm.isChained()
				? algorithm.chainTip()
				: new byte[StorageMetaRecord.LENGTH_HASH_SHA256];

			this.headerBuffer.clear();
			StorageMetaRecord.writeFileHeaderV1(this.headerBuffer, algorithm.kind(), chainRoot);
			this.headerBuffer.flip();
			file.writeBytes(this.headerBuffer);
		}

		@Override
		public void transferBytes(final ByteBuffer buffer, final long oid)
		{
			final int channelIndex = (int) (oid % this.channelCount);

			logger.trace("Transferring blob pos: {}, limit: {} to target channel {}.",
					buffer.position(), buffer.limit(), channelIndex);

			this.accumulate(buffer, channelIndex);
		}

		/**
		 * Appends an entity's bytes to the channel's chunk accumulator and flushes the chunk (data + covering
		 * record) once it reaches {@code coalesceChunkTargetBytes} (a single entity larger than the target forms
		 * its own chunk).
		 */
		private void accumulate(final ByteBuffer buffer, final int channelIndex)
		{
			ByteBuffer chunk = this.chunkBuffers[channelIndex];

			final int incoming = buffer.limit() - buffer.position();
			if (chunk.remaining() < incoming)
			{
				chunk = this.grow(chunk, incoming);
				this.chunkBuffers[channelIndex] = chunk;
			}
			chunk.put(buffer);

			if (chunk.position() >= this.coalesceChunkTargetBytes)
			{
				this.flushChunk(channelIndex);
			}
		}

		/**
		 * Writes the channel's pending chunk as {@code [data][covering record]}, rolling the file over first if
		 * the chunk plus its record would exceed {@code fileMaximumSize} (unless the file holds only its header,
		 * in which case an oversized single chunk is written into its own file). No-op when the accumulator is
		 * empty.
		 */
		private void flushChunk(final int channelIndex)
		{
			final ByteBuffer chunk = this.chunkBuffers[channelIndex];
			final int        used  = chunk.position();
			if (used == 0)
			{
				return;
			}
			chunk.flip(); // [0, used]

			StorageConverterTargetFile file = this.files[channelIndex];

			final long projectedLength = file.size() + used + this.recordLength;
			if (projectedLength > this.dataFileEvaluator.fileMaximumSize() && file.size() > this.headerLength)
			{
				file.release();
				file = this.createNewStorageFile(file.fileNumber() + 1, channelIndex);
				this.files[channelIndex] = file;
			}

			// a data file must stay loadable into a single direct buffer (< 2 GiB)
			if ((long) this.headerLength + used + this.recordLength > Integer.MAX_VALUE)
			{
				throw new StorageException("Coalesced chunk too large for a single data file: " + used + " bytes.");
			}

			final Algorithm algorithm = this.algorithms[channelIndex];
			this.recordBuffer.clear();
			// the chunk is appended at the file's current size, so that is the chunk start the record stores. Fits
			// an int: the file is bounded by fileMaximumSize (< 2 GiB), re-asserted by the guard above.
			final int chunkStart = (int)file.size();
			// computes the covering checksum over the chunk (read non-destructively); for a chained kind this
			// folds and advances the channel's running tip, so the next chunk chains from it.
			algorithm.writeRecord(this.recordBuffer, chunkStart, chunk);
			this.recordBuffer.flip();

			file.writeBytes(chunk);             // the entity bytes
			file.writeBytes(this.recordBuffer); // its covering chunk-checksum record

			chunk.clear();
		}

		/** Grows a chunk accumulator to hold at least {@code needed} more bytes, preserving its contents. */
		private ByteBuffer grow(final ByteBuffer buffer, final int needed)
		{
			final int        used        = buffer.position();
			final int        newCapacity = Math.max(used + needed, buffer.capacity() * 2);
			final ByteBuffer bigger      = XMemory.allocateDirectNative(newCapacity);

			buffer.flip();      // [0, used]
			bigger.put(buffer); // copies used bytes; bigger.position == used
			XMemory.deallocateDirectByteBuffer(buffer);
			return bigger;
		}

		@Override
		public void close()
		{
			// a flushChunk failure must not leak the remaining channels' files and off-heap buffers: release+free
			// every channel regardless, and rethrow the first failure at the end.
			RuntimeException flushFailure = null;
			for (int channelIndex = 0; channelIndex < this.channelCount; channelIndex++)
			{
				try
				{
					this.flushChunk(channelIndex); // emit the final, partial chunk's covering record
				}
				catch (final RuntimeException e)
				{
					if (flushFailure == null)
					{
						flushFailure = e;
					}
					else
					{
						flushFailure.addSuppressed(e);
					}
				}
				this.files[channelIndex].release();
				XMemory.deallocateDirectByteBuffer(this.chunkBuffers[channelIndex]);
			}
			XMemory.deallocateDirectByteBuffer(this.headerBuffer);
			XMemory.deallocateDirectByteBuffer(this.recordBuffer);

			if (flushFailure != null)
			{
				throw flushFailure;
			}
		}
	}
}
