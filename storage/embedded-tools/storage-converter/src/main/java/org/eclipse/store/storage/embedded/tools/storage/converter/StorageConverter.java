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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.collections.XSort;
import org.eclipse.serializer.collections.types.XGettingList;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;
import org.eclipse.store.storage.types.StorageChecksumAnomalyReporter;
import org.eclipse.store.storage.types.StorageChunkChecksumCalculator;
import org.eclipse.store.storage.types.StorageChunkChecksumProvider;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.types.StorageDataInventoryFile;
import org.eclipse.store.storage.types.StorageInventory;
import org.eclipse.store.storage.types.StorageLiveFileProvider;
import org.eclipse.store.storage.types.StorageMetaRecord;
import org.slf4j.Logger;

/**
 * Offline copying utility that rewrites the contents of an EmbeddedStorage into a freshly laid-out target
 * storage.
 * <p>
 * Operation: the converter walks the data files of every channel in the source storage in reverse file
 * order, registers the latest version of every encountered entity, and writes those entities (optionally
 * after running them through a chain of {@link BinaryConverter}s) into the target storage's channels chosen
 * by {@code oid % targetChannelCount}. The target storage's data files are filled up to the configured
 * data-file maximum size and then rolled over.
 * <p>
 * Typical use cases are changing the channel count, switching to a new on-disk binary format, or migrating
 * to a different file-name layout. Backups of the source storage are not converted; only the live data
 * files and the persistence type dictionary are processed.
 *
 * @see MainUtilStorageConverter
 * @see BinaryConverter
 */
public class StorageConverter
{
	private final static Logger logger = Logging.getLogger(StorageConverter.class);

	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private final StorageLiveFileProvider   srcFileProvider;
	private final int                       srcChannelCount;
	private final StorageInventory[]        srcInventories;

	private final HashSet<Long>             processedIds;
	private final HashMap<Long, FileEntity> currentFileEntities;

	private       ByteBuffer                bufferIn;
	private final StorageConverterTarget    target;
	
	private final BinaryConverterSelector   binaryConverterSelector;
	private final ConverterTypeDictionary   converterTypeDictionary;

	// Source-side chunk-checksum verification (driven by the source policy): recompute and compare every
	// chunk-checksum record while reading, so corruption is caught before it propagates into the target.
	// null when not verifying.
	private final boolean                                              sourceVerifying;
	private final Map<Long, StorageChunkChecksumCalculator.Algorithm>  verifyAlgorithms;
	private final StorageChecksumAnomalyReporter                       anomalyReporter;

	/**
	 * Helper class describing a single entity in the current processed file
	 */
	private static class FileEntity
	{
		final long offset;
		final long length;

		public FileEntity(final long offset, final long length)
		{
			super();
			this.offset = offset;
			this.length = length;
		}
	}

	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	/**
	 * Converts a EmbeddedStorage into another one.
	 * 
	 * @param sourceStorageConfiguration configuration for storage to be converted.
	 * @param targetStorageConfiguration configuration of the target storage.
	 */
	public StorageConverter	(
		final StorageConfiguration sourceStorageConfiguration,
		final StorageConfiguration targetStorageConfiguration
	)
	{
		this.srcFileProvider         = sourceStorageConfiguration.fileProvider();
		this.srcChannelCount         = sourceStorageConfiguration.channelCountProvider().getChannelCount();

		this.converterTypeDictionary = new ConverterTypeDictionary(this.srcFileProvider.provideTypeDictionaryIoHandler().loadTypeDictionary());
		this.binaryConverterSelector = new BinaryConverterSelector(this.converterTypeDictionary);
		
		this.srcInventories          = this.createChannelInventories();
		this.processedIds            = new HashSet<>();
		this.currentFileEntities     = new HashMap<>();

		this.target                  = StorageConverterTarget.New(targetStorageConfiguration);

		final StorageChunkChecksumProvider srcProvider = sourceStorageConfiguration.chunkChecksumProvider();
		this.sourceVerifying  = srcProvider.policy().verify();
		this.verifyAlgorithms = this.sourceVerifying ? srcProvider.createAlgorithmsByKind()                  : null;
		this.anomalyReporter  = this.sourceVerifying ? StorageChecksumAnomalyReporter.New(srcProvider.policy()) : null;
	}

	/**
	 * Converts a EmbeddedStorage into another one.
	 * 
	 * @param sourceStorageConfiguration configuration for storage to be converted.
	 * @param targetStorageConfiguration configuration of the target storage.
	 * @param binaryConverters list of BinaryConvert class names that should be applied.
	 */
	public StorageConverter	(
		final StorageConfiguration sourceStorageConfiguration,
		final StorageConfiguration targetStorageConfiguration,
		final String[] binaryConverters
	)
	{
		this.srcFileProvider         = sourceStorageConfiguration.fileProvider();
		this.srcChannelCount         = sourceStorageConfiguration.channelCountProvider().getChannelCount();

		this.converterTypeDictionary = new ConverterTypeDictionary(this.srcFileProvider.provideTypeDictionaryIoHandler().loadTypeDictionary());
		this.binaryConverterSelector = new BinaryConverterSelector(this.converterTypeDictionary);
		
		for(String converter : binaryConverters) {
			this.binaryConverterSelector.initConverter(converter);
		}
		
		
		this.srcInventories          = this.createChannelInventories();
		this.processedIds            = new HashSet<>();
		this.currentFileEntities     = new HashMap<>();

		this.target                  = StorageConverterTarget.New(targetStorageConfiguration);

		final StorageChunkChecksumProvider srcProvider = sourceStorageConfiguration.chunkChecksumProvider();
		this.sourceVerifying  = srcProvider.policy().verify();
		this.verifyAlgorithms = this.sourceVerifying ? srcProvider.createAlgorithmsByKind()                  : null;
		this.anomalyReporter  = this.sourceVerifying ? StorageChecksumAnomalyReporter.New(srcProvider.policy()) : null;
	}

	/**
	 * Creates a <b>verify-only</b> converter: it walks the source storage and verifies its chunk checksums
	 * according to the source configuration's {@link StorageChunkChecksumProvider} policy, but writes nothing
	 * (no target storage is created). Anomalies react per the source policy. A non-verifying source policy
	 * makes this a no-op scan.
	 *
	 * @param sourceStorageConfiguration configuration for the storage to be verified.
	 */
	public StorageConverter(final StorageConfiguration sourceStorageConfiguration)
	{
		this.srcFileProvider         = sourceStorageConfiguration.fileProvider();
		this.srcChannelCount         = sourceStorageConfiguration.channelCountProvider().getChannelCount();

		this.converterTypeDictionary = new ConverterTypeDictionary(this.srcFileProvider.provideTypeDictionaryIoHandler().loadTypeDictionary());
		this.binaryConverterSelector = new BinaryConverterSelector(this.converterTypeDictionary);

		this.srcInventories          = this.createChannelInventories();
		this.processedIds            = new HashSet<>();
		this.currentFileEntities     = new HashMap<>();

		this.target                  = null; // verify-only: no target, nothing is written

		final StorageChunkChecksumProvider srcProvider = sourceStorageConfiguration.chunkChecksumProvider();
		this.sourceVerifying  = srcProvider.policy().verify();
		this.verifyAlgorithms = this.sourceVerifying ? srcProvider.createAlgorithmsByKind()                  : null;
		this.anomalyReporter  = this.sourceVerifying ? StorageChecksumAnomalyReporter.New(srcProvider.policy()) : null;
	}


	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	/**
	 * Execute the conversion, or &mdash; when constructed without a target &mdash; the verify-only scan.
	 */
	public void start()
	{
		if(this.target == null)
		{
			// verify-only: walk and verify the source, write nothing.
			if(!this.sourceVerifying)
			{
				logger.warn("Verify-only run, but the source policy does not verify; nothing will be checked.");
			}
			this.convertStorage();
		}
		else
		{
			try
			{
				this.copyTypeDictionary();
				this.convertStorage();
			}
			finally
			{
				// always release the target's off-heap buffers (per-channel chunk buffers + header/record
				// buffers) even if conversion throws partway; StorageConverterTarget.close() is exception-safe.
				this.close();
			}
		}
	}

	private void copyTypeDictionary()
	{
		this.target.storeTypeDictionary(this.converterTypeDictionary.toString());
	}

	private void close()
	{
		this.target.close();
	}

	private void transferEntity(final long oid, final FileEntity entity)
	{
		logger.trace("Processing entity {}.", oid);

		this.bufferIn.limit((int) (entity.offset + entity.length));
		this.bufferIn.position((int) entity.offset);

		long tid = XMemory.get_long(XMemory.getDirectByteBufferAddress(this.bufferIn) + entity.offset + 8);
					
		BinaryConverter converter = this.binaryConverterSelector.get(tid);
		if(converter!=null)
		{
			final ByteBuffer converted = converter.convert(this.bufferIn);
			try
			{
				this.target.transferBytes(converted, oid);
			}
			finally
			{
				// the converter hands ownership of a fresh direct buffer and transferBytes only copies it,
				// so free it now instead of leaving each per-entity buffer to the Cleaner. Guard against a
				// converter that returns bufferIn unchanged (that one is owned/freed by processFile).
				if(converted != this.bufferIn)
				{
					XMemory.deallocateDirectByteBuffer(converted);
				}
			}
		}
		else
		{
			this.target.transferBytes(this.bufferIn, oid);
		}
		this.processedIds.add(oid);
	}

	private void processFile(final StorageDataInventoryFile storageDataInventoryFile)
	{
		logger.debug("Processing storageFile: {}", storageDataInventoryFile.identifier());

		this.bufferIn = XMemory.allocateDirectNative(storageDataInventoryFile.size());
		
		try
		{
			try
			{
				storageDataInventoryFile.readBytes(this.bufferIn);
			}
			finally
			{
				storageDataInventoryFile.close();
			}

			final long bufferStartAddress = XMemory.getDirectByteBufferAddress(this.bufferIn);
			final long bufferBoundAddress = bufferStartAddress + this.bufferIn.limit();

			if (this.sourceVerifying)
			{
				// verify the source's own chunk checksums before any entity is transferred, so a FAIL reaction
				// aborts the conversion instead of copying corruption into the target.
				this.verifyFile(storageDataInventoryFile, bufferStartAddress, bufferBoundAddress);
			}

			if (this.target == null)
			{
				// verify-only: the file has been verified above; nothing is registered or transferred.
				return;
			}

			long currentItemLength;
			long offset = 0;

			for (long address = bufferStartAddress; address < bufferBoundAddress;)
			{
				currentItemLength = Binary.getEntityLengthRawValue(address);

				if (currentItemLength > 0)
				{
					this.registerFileEntity(address, offset, currentItemLength);

					address += currentItemLength;
					offset += currentItemLength;
				}
				else if (currentItemLength < 0)
				{
					// comments (indicated by negative length) just get skipped.
					// note that gap length gets registered for the file at the end arithmetically
					address -= currentItemLength;
					offset -= currentItemLength;
				}
				else
				{
					// entity length may never be 0 or the iteration will hang forever
					throw new StorageExceptionConsistency("Zero length data item.");
				}
			}

			this.transferRegisteredEntities();

			logger.trace("Clearing current file entities.");
			this.currentFileEntities.clear();
		}
		finally
		{
			// Release the per-file off-heap buffer in every exit path (the verify-only return included)
			// instead of leaving it for the Cleaner, so a long run over many files does not accumulate
			// direct buffers.
			XMemory.deallocateDirectByteBuffer(this.bufferIn);
			this.bufferIn = null;
		}
	}

	/**
	 * Verifies the source file's own chunk-checksum records, mirroring the engine's load-time walk
	 * ({@code StorageChunkChecksumCalculator.ChunkChecksumHandler.onLoad}) but off-line: it tracks the chunk
	 * boundary, parses a {@code FileHeaderV1} to seed the chain tip, and on each chunk-checksum record
	 * recomputes the covered bytes {@code [chunkStart, recordAddress)} with the {@link
	 * StorageChunkChecksumCalculator.Algorithm} dispatched by the record's on-disk KIND, comparing against the
	 * stored hash. Anomalies are routed to {@link #anomalyReporter}. The whole file is already resident in
	 * {@link #bufferIn}, so the chunk slices are taken in place.
	 */
	private void verifyFile(
		final StorageDataInventoryFile storageDataInventoryFile,
		final long                     bufferStartAddress      ,
		final long                     bufferBoundAddress
	)
	{
		final long fileNumber = storageDataInventoryFile.number();

		long    chunkStart      = bufferStartAddress; // start of the bytes the next record will cover
		byte[]  chainTip        = null;               // seeded from FileHeaderV1.chainRoot for chained kinds
		boolean headerSeen      = false;
		boolean dataSinceRecord = false;              // live data not yet covered by a record

		for (long address = bufferStartAddress; address < bufferBoundAddress;)
		{
			// the 8-byte length field must be fully resident before it is read; a trailing remnant shorter than
			// a length field cannot be a valid entry, so stop the walk. Only fires on a truncated/corrupt file.
			if (address + Long.BYTES > bufferBoundAddress)
			{
				break;
			}

			final long rawLength = Binary.getEntityLengthRawValue(address);

			if (rawLength > 0)
			{
				// live entity: part of the current chunk's byte range. chunkStart is unchanged.
				dataSinceRecord = true;
				address += rawLength;
			}
			else if (rawLength < 0)
			{
				final long entryLength = -rawLength;

				// the 12-byte envelope (LENGTH + META_MARKER + KIND) must be resident before the marker/KIND are
				// read; otherwise this negative-length entry cannot be a structured meta record — treat it as an
				// opaque gap (chunkStart unchanged), mirroring StorageMetaRecordRegistry.dispatch.
				if (address + StorageMetaRecord.OFFSET_PAYLOAD <= bufferBoundAddress
					&& StorageMetaRecord.isMetaRecord(address))
				{
					final long kind = StorageMetaRecord.kindOf(address);
					if (kind == StorageMetaRecord.KIND_FileHeaderV1)
					{
						if (address + StorageMetaRecord.LENGTH_FILEHEADERV1 > bufferBoundAddress)
						{
							// truncated header: its chain root runs past the resident bytes. Report and skip,
							// mirroring StorageChunkChecksumCalculator.FileHeaderV1Handler.onLoad.
							this.anomalyReporter.onUnknownKind(fileNumber, StorageMetaRecord.KIND_FileHeaderV1);
						}
						else
						{
							final StorageMetaRecord.FileHeaderV1Payload header = StorageMetaRecord.readFileHeaderV1(address);
							chainTip        = header.chainRoot();
							headerSeen      = true;
							chunkStart      = address + entryLength;
							dataSinceRecord = false;
						}
					}
					else
					{
						final StorageChunkChecksumCalculator.Algorithm verifier = this.verifyAlgorithms.get(kind);
						if (verifier == null)
						{
							// a meta record whose KIND we cannot verify (removed/future/custom not in the
							// source provider's verify set): report and skip; chunkStart is unchanged.
							this.anomalyReporter.onUnknownKind(fileNumber, kind);
						}
						else if (address + verifier.recordLength() > bufferBoundAddress)
						{
							// truncated covering record: its stored hash runs past the resident bytes. Report and
							// skip, mirroring StorageChunkChecksumCalculator.ChunkChecksumHandler.onLoad.
							this.anomalyReporter.onUnknownKind(fileNumber, kind);
						}
						else
						{
							chainTip        = this.verifyChunk(verifier, fileNumber, chunkStart, address, chainTip);
							chunkStart      = address + entryLength;
							dataSinceRecord = false;
						}
					}
				}
				// else: a plain gap/tombstone (or a sub-envelope remnant) is part of the current chunk's byte
				// range; chunkStart is unchanged, matching the load walk's [chunkStart, recordAddress) slice.

				address += entryLength;
			}
			else
			{
				throw new StorageExceptionConsistency("Zero length data item.");
			}
		}

		// coverage anomalies: live data left uncovered at end of file. The reporter no-ops these unless the
		// source policy requires coverage. No header at all => unprotected legacy file (MISSING_HEADER);
		// data after the last covering record => UNCOVERED_DATA.
		if (dataSinceRecord)
		{
			if (headerSeen)
			{
				this.anomalyReporter.onUncoveredData(fileNumber, chunkStart - bufferStartAddress);
			}
			else
			{
				this.anomalyReporter.onMissingHeader(fileNumber);
			}
		}

		this.anomalyReporter.onFileWalkComplete(fileNumber);
	}

	/**
	 * Recomputes and compares one chunk-checksum record. Returns the chain tip to carry forward (the stored
	 * hash for chained kinds &mdash; equal to the recomputed hash on a match, and on a tolerated mismatch the
	 * basis the writer chained from; {@code null}/unchanged for non-chained kinds).
	 */
	private byte[] verifyChunk(
		final StorageChunkChecksumCalculator.Algorithm verifier     ,
		final long                                      fileNumber   ,
		final long                                      chunkStart   ,
		final long                                      recordAddress,
		final byte[]                                    chainTip
	)
	{
		if (verifier.isChained() && chainTip == null)
		{
			// a chained record with no FileHeaderV1 seed cannot be verified; report the missing header and
			// skip rather than fold a null tip into the digest.
			this.anomalyReporter.onMissingHeader(fileNumber);
			return null;
		}

		final long bufferStartAddress = XMemory.getDirectByteBufferAddress(this.bufferIn);

		// framing cross-check (mirrors ChunkChecksumHandler.onLoad): if the record's stored chunk start disagrees
		// with the boundary this walk reconstructed, a corrupted LENGTH rerouted the walk past a record. Report
		// and skip the hash (the range is wrong); the caller re-anchors past this record.
		final int  storedChunkStart = StorageMetaRecord.readChunkChecksumChunkStart(recordAddress);
		final long walkerChunkStart = chunkStart - bufferStartAddress;
		if(storedChunkStart != walkerChunkStart)
		{
			this.anomalyReporter.onChunkBoundaryMismatch(
				fileNumber, recordAddress - bufferStartAddress, storedChunkStart, walkerChunkStart
			);
			// re-anchor the chain to the on-disk record (chained): continue from its stored tip.
			return verifier.isChained() ? verifier.readStoredChecksumBytes(recordAddress) : chainTip;
		}

		final long       chunkLength        = recordAddress - chunkStart;
		final ByteBuffer chunk              = XMemory.slice(this.bufferIn, chunkStart - bufferStartAddress, chunkLength);

		final byte[] expected = verifier.readStoredChecksumBytes(recordAddress);
		if (verifier.isChained())
		{
			verifier.setChainTip(chainTip);
		}
		final byte[] actual = verifier.computeChecksumBytes(chunk);

		if (!Arrays.equals(actual, expected))
		{
			this.anomalyReporter.onChecksumMismatch(fileNumber, chunkStart - bufferStartAddress, verifier.kind(), expected, actual);
		}

		// continue a chain from the stored tip (== actual on a match); for non-chained kinds the tip is unused.
		return verifier.isChained() ? expected : chainTip;
	}

	private void transferRegisteredEntities()
	{
		this.currentFileEntities.forEach(this::transferRegisteredEntity);
	}

	private void transferRegisteredEntity(final long oid, final FileEntity fileEntry)
	{
		this.transferEntity(oid, fileEntry);
	}

	private void registerFileEntity(final long address, final long offset, final long itemLength)
	{
		final long oid = Binary.getEntityObjectIdRawValue(address);

		if (this.processedIds.contains(oid))
		{
			logger.trace("Oid {} skipped, already processed.", oid);
			return;
		}

		if (this.currentFileEntities.put(oid, new FileEntity(offset, itemLength)) == null)
		{
			logger.trace("Adding new FileEntry oid {}, address {}, offset {}, length {} for processing.",
					oid, address, offset, itemLength);
		}
		else
		{
			logger.trace("Replaced existing FileEntry for oid {}, address {}, offset {}, length {} for processing.",
					oid, address, offset, itemLength);
		}
	}

	private void convertStorage()
	{
		for (final StorageInventory channelInventory : this.srcInventories)
		{
			this.processChannel(channelInventory);
		}
	}

	private void processChannel(final StorageInventory channelInventory)
	{
		logger.trace("Processing channel {}.", channelInventory.channelIndex());

		final XGettingList<StorageDataInventoryFile> reversedFiles = channelInventory.dataFiles().values().toReversed();
		final Iterator<? extends StorageDataInventoryFile> iterator = reversedFiles.iterator();

		while (iterator.hasNext())
		{
			this.processFile(iterator.next());
		}
	}

	private StorageInventory[] createChannelInventories()
	{
		final StorageInventory[] inventories = new StorageInventory[this.srcChannelCount];
		for (int i = 0; i < this.srcChannelCount; i++)
		{
			inventories[i] = this.createChannelInventory(i);
		}
		return inventories;
	}

	private StorageInventory createChannelInventory(final int channelIndex)
	{
		final EqHashTable<Long, StorageDataInventoryFile> dataFiles = EqHashTable.New();
		this.srcFileProvider.collectDataFiles(StorageDataInventoryFile::New, f -> dataFiles.add(f.number(), f),
				channelIndex);
		dataFiles.keys().sort(XSort::compare);

		return StorageInventory.New(channelIndex, dataFiles, null);
	}

}
