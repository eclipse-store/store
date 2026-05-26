package org.eclipse.store.storage.util;

/*-
 * #%L
 * EclipseStore Storage
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.afs.types.*;
import org.eclipse.serializer.memory.*;
import org.eclipse.serializer.persistence.binary.types.*;
import org.eclipse.serializer.util.logging.*;
import org.eclipse.store.storage.exceptions.*;
import org.eclipse.store.storage.types.*;
import org.slf4j.*;

import java.nio.*;
import java.util.*;

/**
 * Strategy for restoring a previously persisted object by object id.
 * <p>
 * Implementations locate previously stored binary entities in the deletion
 * directory, read the raw binary blob for the requested object id and re-append that
 * blob into to the storage so the object becomes available again.
 */
public interface StorageObjectRestorer
{
    /**
     * Restore the persisted object with the given persistence object id.
     *
     * @param objectId the persistence object id to restore
     * @return {@code true} when an object blob was found and appended to live storage; {@code false} otherwise
     */
    public boolean restoreObject(final long objectId);

    /**
     * Default implementation of {@link StorageObjectRestorer} that scans deleted data files
     * for occurrences of an object id, reads the raw entity blob and appends it to the
     * storage channel. It also writes a transactions log entry for the
     * appended data.
     * <p>
     * This implementation must not be used with a running storage instance!
     */
    public static class Default implements StorageObjectRestorer
    {
        private static final Logger logger = Logging.getLogger(StorageObjectRestorer.class);


        ///////////////////////////////////////////////////////////////////////////
        // instance fields //
        ////////////////////

        private final StorageLiveFileProvider                                     storageFileProvider;
        private final int                                                         channelCount;
        private final HashMap<Integer, TreeMap<Long, StorageDataInventoryFile>> dataFiles      = new HashMap<>();
        private final int[]                                                      dataFilesSizes;

        // The chunk-checksum provider from the StorageConfiguration, or null when constructed from a bare
        // StorageLiveFileProvider. Supplies the primary write algorithm so a restored file carries a
        // FileHeaderV1 + covering record matching the store's on-disk kind. null + a checksummed store:
        // resolvePrimaryAlgorithm throws rather than silently writing an unprotected, header-less file.
        private final StorageChunkChecksumProvider                               chunkChecksumProvider;


        /**
         * Immutable descriptor of a stored entity occurrence inside a data file.
         *
         * @param file the backing {@link AFile} containing the entity
         * @param entryPosition byte offset inside the file where the entity header begins
         * @param rawLength total raw length of the entity record (header + payload)
         */
        public record ObjectEntry(AFile file, long entryPosition, long rawLength)
        {
        }


        ///////////////////////////////////////////////////////////////////////////
        // constructors //
        /////////////////

        /**
         * Create a new restorer instance.
         *
         * @param storageFileProvider provider used to locate data and transactions files
         * @param channelCount the number of storage channels configured in the system
         */
        public Default(final StorageLiveFileProvider storageFileProvider, final int channelCount)
        {
            super();
            this.storageFileProvider   = storageFileProvider;
            this.channelCount          = channelCount;
            this.dataFilesSizes        = new int[channelCount];
            this.chunkChecksumProvider = null; // no configuration: checksummed targets are refused.
        }

        /**
         * Create a new restorer instance from a {@link StorageConfiguration}.
         * <p>
         * This convenience constructor extracts the {@link StorageLiveFileProvider}
         * and the configured channel count from the provided configuration.
         *
         * @param configuration storage configuration containing file provider and channel count
         */
        public Default(final StorageConfiguration configuration)
        {
            super();
            this.storageFileProvider   = configuration.fileProvider();
            this.channelCount          = configuration.channelCountProvider().getChannelCount();
            this.dataFilesSizes        = new int[channelCount];
            // chunkChecksumProvider() is a default method that never returns null (falls back to the
            // framework default provider); the on-disk kind probe, not this provider, decides whether
            // coverage is actually required.
            this.chunkChecksumProvider = configuration.chunkChecksumProvider();
        }


        ///////////////////////////////////////////////////////////////////////////
        // methods //
        ////////////

        /**
         * Restore the object with id {@code objectId}.
         * <p>
         * The implementation searches for the most recent occurrence of the object in
         * deleted data files and, when found, reads the raw entity blob and appends it
         * to the live storage channel. A transactions log entry is attempted after
         * append. All significant actions are logged for diagnostics.
         *
         * @param objectId persistence id of the object to restore
         * @return {@code true} when an object blob was found and successfully appended; {@code false} otherwise
         */
        @Override
        public boolean restoreObject(final long objectId)
        {
            if(!checkFiles())
            {
                return false;
            }

            collectDeletedDataFiles();
            collectStorageFilesSizes();

            final TreeMap<Long, ObjectEntry> findings = searchObject(objectId);
            logger.debug("Found occurrences of {}: \n {}", objectId, findings);

            if(!findings.isEmpty())
            {
                final ObjectEntry entry = findings.firstEntry().getValue();
                logger.info("Restoring object {} with blob from {}@{}", objectId, entry.file().identifier(), entry.entryPosition());
                final byte[] blob = readBlob(entry);

                logger.debug("object {} blob size: {}", objectId, blob.length);

                return appendBlobToStorage(blob, (int) (objectId % channelCount));
            }
            else
            {
                logger.info("No occurrences found for object {}", objectId);
                return false;
            }
        }

        /**
         * Validate that the configured {@link StorageLiveFileProvider} exposes the
         * required directories and that they are accessible.
         * <p>
         * The method logs descriptive error messages for each missing or
         * inaccessible item and returns {@code false} on any failure. It does not
         * throw but allows callers to decide how to proceed when {@code false} is
         * returned.
         *
         * @return {@code true} when the provider and both the deletion and base
         *         directories are configured and exist; {@code false} otherwise
         */
        private boolean checkFiles()
        {
            if (this.storageFileProvider == null)
            {
                logger.error("No StorageLiveFileProvider configured");
                return false;
            }

            final ADirectory deletionDirectory = this.storageFileProvider.deletionDirectory();
            if (deletionDirectory == null)
            {
                logger.error("No deletion directory configured, cannot restore object");
                return false;
            }

            if (!deletionDirectory.exists())
            {
                logger.error("Deletion directory {} does not exist, cannot restore object", deletionDirectory);
                return false;
            }

            final ADirectory baseDirectory = this.storageFileProvider.baseDirectory();
            if (baseDirectory == null)
            {
                logger.error("No base directory configured, cannot restore object");
                return false;
            }

            if (!baseDirectory.exists())
            {
                logger.error("Base directory {} does not exist, cannot restore object", baseDirectory);
                return false;
            }

            if (this.channelCount < 1)
            {
               logger.error("Channel count {} must be greater than zero", channelCount);
            }

            return true;
        }

        /**
         * Append the given raw entity {@code blob} to the live storage for the
         * provided {@code channelIndex}.
         * <p>
         * This method selects the next data file number for the channel, ensures the
         * file exists, writes the provided blob and attempts to write a matching
         * transactions log entry. Any problems are logged and the method returns
         * {@code false} to indicate failure; on success it returns {@code true}.
         *
         * @param blob raw entity bytes including header
         * @param channelIndex storage channel index to append into
         * @return {@code true} on successful append and transaction log write attempt; {@code false} on error
         */
        private boolean appendBlobToStorage(final byte[] blob, final int channelIndex)
        {
            try
            {
                final TreeMap<Long, StorageDataInventoryFile> channelFiles = new TreeMap<>(Comparator.reverseOrder());
                storageFileProvider.collectDataFiles(StorageDataInventoryFile::New, f -> channelFiles.put(f.number(), f), channelIndex);

                final long lastFileNumber = channelFiles.isEmpty()
                    ? 1L
                    : channelFiles.firstKey() + 1;

                // Decide how to cover the recovery file from the store's ON-DISK checksum kind (the disk is
                // truth; the config is only intent). resolvePrimaryAlgorithm throws when the store is
                // checksummed but no matching algorithm is available — that must stay loud (below).
                final long                                     onDiskKind = probeOnDiskChecksumKind(channelFiles);
                final StorageChunkChecksumCalculator.Algorithm algorithm  = resolvePrimaryAlgorithm(onDiskKind);

                final AFile storageFile = storageFileProvider.provideDataFile(channelIndex, lastFileNumber);
                storageFile.ensureExists();

                // Total content length of the recovery file — logged in the Store entry so the transaction
                // log length matches the bytes on disk, whether or not a checksum was emitted.
                final long contentLength = algorithm == null
                    ? writeRawBlob(storageFile, blob)
                    : writeCheckedBlob(storageFile, blob, algorithm);

                try
                {
                    final long fileTimeStamp = getLastTimeStamp(storageFileProvider) + 1;
                    writeTransactionLogCreate(channelIndex, lastFileNumber, fileTimeStamp);
                    writeTransactionLogStore(channelIndex, fileTimeStamp, Math.toIntExact(contentLength));

                    //write other channels transaction log zero store
                    for(int channel = 0; channel < channelCount; channel++)
                    {
                        if(channel != channelIndex)
                        {
                            writeTransactionLogStore(channel, fileTimeStamp, dataFilesSizes[channel]);
                        }
                    }
                }
                catch (final StorageException e)
                {
                    logger.warn("Failed to get last timestamp from storage file, no transaction log entry written!", e);
                    return false;
                }

                return true;
            }
            catch (final StorageExceptionChunkChecksumUnavailable e)
            {
                // Guard: checksum-protected store but no compliant algorithm. Surface loudly and
                // actionably rather than swallowing into a silent, unprotected header-less append.
                throw e;
            }
            catch (final Throwable t)
            {
                logger.warn("Failed to append blob to storage for channel {}", channelIndex, t);
                return false;
            }
        }

        /**
         * Determines the target store's on-disk chunk-checksum kind by reading the {@code FileHeaderV1} of
         * its existing data files (highest-numbered first, returning the first header found). Returns
         * {@code 0} when no data file carries a {@code FileHeaderV1} — i.e. the store is not checksum-protected.
         *
         * @param channelFiles the channel's current data files (reverse-ordered: highest number first)
         * @return the on-disk chunk-checksum kind, or {@code 0} if the store is not checksummed
         */
        private static long probeOnDiskChecksumKind(final TreeMap<Long, StorageDataInventoryFile> channelFiles)
        {
            for (final StorageDataInventoryFile inventoryFile : channelFiles.values())
            {
                final long kind = readFileHeaderKind(inventoryFile.file());
                if (kind != 0L)
                {
                    return kind;
                }
            }
            return 0L;
        }

        /**
         * Reads the chunk-checksum kind from the {@code FileHeaderV1} at offset 0 of {@code file}, or
         * {@code 0} if the file has no {@code FileHeaderV1} (too short, positive-length first entry, or a
         * non-header meta record) — i.e. a legacy / unchecksummed file.
         */
        private static long readFileHeaderKind(final AFile file)
        {
            if (file.size() < StorageMetaRecord.LENGTH_FILEHEADERV1)
            {
                return 0L;
            }
            final ByteBuffer buffer = XMemory.allocateDirectNative(StorageMetaRecord.LENGTH_FILEHEADERV1);
            final AReadableFile rFile = file.useReading();
            try
            {
                rFile.open();
                rFile.readBytes(buffer, 0, StorageMetaRecord.LENGTH_FILEHEADERV1);
                final long address = XMemory.getDirectByteBufferAddress(buffer);

                // a FileHeaderV1 is the file's first entry: a negative-length meta record of that kind.
                if (Binary.getEntityLengthRawValue(address) >= 0L
                    || !StorageMetaRecord.isMetaRecord(address)
                    || StorageMetaRecord.kindOf(address) != StorageMetaRecord.KIND_FileHeaderV1)
                {
                    return 0L;
                }
                return StorageMetaRecord.readFileHeaderV1(address).chunkChecksumKind();
            }
            finally
            {
                rFile.release();
                XMemory.deallocateDirectByteBuffer(buffer);
            }
        }

        /**
         * Resolves the primary write algorithm for the probed on-disk kind, applying the guard:
         * <ul>
         *   <li>{@code onDiskKind == 0} (store not checksummed) → {@code null}: append the raw blob as before;</li>
         *   <li>checksummed store but no provider (bare constructor) → throw;</li>
         *   <li>checksummed store but the configured provider writes a different kind → throw;</li>
         *   <li>checksummed store and the configured provider's kind matches → a fresh primary algorithm.</li>
         * </ul>
         */
        private StorageChunkChecksumCalculator.Algorithm resolvePrimaryAlgorithm(final long onDiskKind)
        {
            if (onDiskKind == 0L)
            {
                return null; // store not checksum-protected → no regression, append raw blob
            }
            if (this.chunkChecksumProvider == null)
            {
                throw new StorageExceptionChunkChecksumUnavailable(onDiskKind, -1L);
            }
            final long configuredKind = this.chunkChecksumProvider.chunkChecksumKind();
            if (configuredKind != onDiskKind)
            {
                // the provider can verify any kind but only WRITES its primary; a mismatched write would
                // manufacture a mixed store. Refuse rather than guess.
                throw new StorageExceptionChunkChecksumUnavailable(onDiskKind, configuredKind);
            }
            return this.chunkChecksumProvider.createPrimaryAlgorithm();
        }

        /**
         * Appends the raw blob to a fresh recovery file (pre-feature behavior, for unchecksummed stores).
         *
         * @return the recovery file's content length (== blob length)
         */
        private static long writeRawBlob(final AFile storageFile, final byte[] blob)
        {
            final ByteBuffer writeBuf = XMemory.toDirectByteBuffer(blob);
            final AWritableFile wFile = storageFile.useWriting();
            boolean complete = false;
            try
            {
                wFile.writeBytes(writeBuf);
                complete = true;
            }
            finally
            {
                rollbackOnFailure(wFile, complete);
                wFile.release();
                XMemory.deallocateDirectByteBuffer(writeBuf);
            }
            return blob.length;
        }

        /**
         * Writes a covered recovery file: {@code [FileHeaderV1][blob][covering record]} for the store's
         * on-disk kind, using the algorithm's file-free primitives ({@code writeRecord} + the chain-tip
         * accessors) and {@link StorageMetaRecord#writeFileHeaderV1}, so the restored object is
         * checksum-protected and the file (now the head) does not pause coverage for later writes. For
         * chained kinds the new file starts a fresh sub-chain seeded by the algorithm's initial seed; it
         * still verifies self-contained from its own header.
         *
         * @return the recovery file's content length (header + blob + covering record)
         */
        private static long writeCheckedBlob(
            final AFile                                    storageFile,
            final byte[]                                   blob       ,
            final StorageChunkChecksumCalculator.Algorithm algorithm
        )
        {
            final byte[] chainRoot = algorithm.isChained()
                ? algorithm.initialSeed()
                : new byte[StorageMetaRecord.LENGTH_HASH_SHA256];
            if (algorithm.isChained())
            {
                algorithm.setChainTip(chainRoot);
            }

            final int        headerLength = StorageMetaRecord.LENGTH_FILEHEADERV1;
            final int        recordLength = (int)algorithm.recordLength();
            final ByteBuffer headerBuffer = XMemory.allocateDirectNative(headerLength);
            final ByteBuffer blobBuffer   = XMemory.toDirectByteBuffer(blob); // position 0, limit == blob.length
            final ByteBuffer recordBuffer = XMemory.allocateDirectNative(recordLength);

            final AWritableFile wFile = storageFile.useWriting();
            boolean complete = false;
            try
            {
                StorageMetaRecord.writeFileHeaderV1(headerBuffer, algorithm.kind(), chainRoot);
                headerBuffer.flip();

                // the covering record hashes exactly the blob bytes (read non-destructively); for chained kinds
                // this folds + advances the tip seeded above. The blob follows the FileHeaderV1, so headerLength
                // is the chunk start the record stores.
                algorithm.writeRecord(recordBuffer, headerLength, blobBuffer);
                recordBuffer.flip();

                // header first, then the entity blob, then its covering record — the layout the load walk
                // expects ([chunkStart, recordAddress) == the blob bytes).
                wFile.writeBytes(headerBuffer);
                wFile.writeBytes(blobBuffer);
                wFile.writeBytes(recordBuffer);
                complete = true;
            }
            finally
            {
                rollbackOnFailure(wFile, complete);
                wFile.release();
                XMemory.deallocateDirectByteBuffer(headerBuffer);
                XMemory.deallocateDirectByteBuffer(blobBuffer);
                XMemory.deallocateDirectByteBuffer(recordBuffer);
            }
            return (long)headerLength + blob.length + recordLength;
        }

        /**
         * Rolls a partially-written recovery file back by deleting it when a write sequence did not complete.
         * The three {@code writeBytes} calls of {@link #writeCheckedBlob} (and the single one of
         * {@link #writeRawBlob}) are not atomic: an I/O failure mid-sequence would otherwise leave a torn file
         * on disk &mdash; for a checksummed store a {@code [FileHeaderV1][blob]} body with no covering record,
         * which the load walk reports as {@code UNCOVERED_DATA} and a strict policy refuses to open. The file
         * was freshly created by the caller ({@code storageFile.ensureExists()}) and has no transaction-log
         * entry yet, so deleting it restores the pre-write state. Best-effort: a rollback failure is logged but
         * never masks the original write failure propagating out of the {@code finally}.
         */
        private static void rollbackOnFailure(final AWritableFile wFile, final boolean complete)
        {
            if(complete)
            {
                return;
            }
            final String path = wFile.toPathString();
            try
            {
                final boolean deleted = wFile.delete();
                logger.warn(
                    "Recovery-file write did not complete; rolled back by deleting the partial recovery file {} (deleted={}).",
                    path, deleted
                );
            }
            catch(final Throwable suppressed)
            {
                logger.warn("Failed to delete the partially-written recovery file {} during rollback", path, suppressed);
            }
        }

        /**
         * Inspect all channels' transaction files to determine the highest
         * transaction timestamp known to the system.
         *
         * @param storageFileProvider provider used to obtain transaction files
         * @return highest observed transaction timestamp or 0 if none found
         * @throws StorageException when a transactions file is missing or cannot be parsed
         */
        private long getLastTimeStamp(final StorageLiveFileProvider storageFileProvider)
        {
            long lastTimeStamp = 0;

            for (int channelIndex = 0; channelIndex < channelCount; channelIndex++)
            {
                final AFile trAFile = storageFileProvider.provideTransactionsFile(channelIndex);
                if (trAFile == null || !trAFile.exists())
                {
                    throw new StorageException("Transactions file for channel " + channelIndex + " does not exist");
                }

                final StorageLiveTransactionsFile transactionFile = StorageLiveTransactionsFile.New(trAFile, channelIndex);
                try
                {
                    final long[] maxRef = new long[]{0L};
                    final int ch = channelIndex;
                    final StorageTransactionsAnalysis.EntryIterator collector = (address, availableEntryLength) ->
                    {
                        try
                        {
                            final long ts = StorageTransactionsAnalysis.Logic.getEntryTimestamp(address);
                            maxRef[0] = Math.max(maxRef[0], ts);
                            return true;
                        }
                        catch (final Throwable t)
                        {
                            throw new StorageException("Failed to parse transactions entry in channel " + ch, t);
                        }
                    };

                    transactionFile.processBy(collector);
                    lastTimeStamp = Math.max(lastTimeStamp, maxRef[0]);
                }
                finally
                {
                    try
                    {
                        transactionFile.close();
                    }
                    catch (final Exception e)
                    {
                        logger.debug("Failed to close transactions file for channel {}: {}", channelIndex, e.getMessage());
                    }
                }
            }

            logger.debug("Last storage timestamp is {}", lastTimeStamp);

            return lastTimeStamp;
        }

        /**
         * Write a store transactions log entries that describes a
         * store operation of the provided length at the calculated
         * timestamp.
         *
         * @param channelIndex channel to which the transaction belongs
         * @param timeStamp    highest known previous timestamp (used to derive the new one)
         * @param length       length of the appended data blob
         */
        private void writeTransactionLogStore(final int channelIndex, final long timeStamp, final int length)
        {
            final ByteBuffer entryBufferStore = XMemory.allocateDirectNative(StorageTransactionsAnalysis.Logic.entryLengthStore());
            final long entryBufferStoreAddress = XMemory.getDirectByteBufferAddress(entryBufferStore);
            StorageTransactionsAnalysis.Logic.initializeEntryStore(entryBufferStoreAddress);
            StorageTransactionsAnalysis.Logic.setEntryStore(XMemory.getDirectByteBufferAddress(entryBufferStore), length, timeStamp);

            final AWritableFile transactionFile = storageFileProvider.provideTransactionsFile(channelIndex).useWriting();

            logger.debug("Writing store transaction log entry for channel {} length {} timestamp {}",
                channelIndex, length, timeStamp);

            try
            {
                transactionFile.writeBytes(entryBufferStore);
            }
            catch (final Exception e)
            {
                throw new RuntimeException("Failed to write store transaction log entry", e);
            }
            finally
            {
                transactionFile.release();
                XMemory.deallocateDirectByteBuffer(entryBufferStore);
            }
        }

        /**
         * Write a Create transactions log entries that describes a
         * file creation operation of the provided length at the calculated
         * timestamp.
         *
         * @param channelIndex channel to which the transaction belongs
         * @param fileNumber   file number of the newly created file
         * @param timeStamp    file creation timestamp
         */
        private void writeTransactionLogCreate(final int channelIndex, final long fileNumber, final long timeStamp)
        {
            final ByteBuffer entryBufferFileCreation = XMemory.allocateDirectNative(StorageTransactionsAnalysis.Logic.entryLengthFileCreation());
            final long entryBufferFileCreationAddress = XMemory.getDirectByteBufferAddress(entryBufferFileCreation);
            StorageTransactionsAnalysis.Logic.initializeEntryFileCreation(entryBufferFileCreationAddress);
            StorageTransactionsAnalysis.Logic.setEntryFileCreation(entryBufferFileCreationAddress, 0, timeStamp, fileNumber);

            final AWritableFile transactionFile = storageFileProvider.provideTransactionsFile(channelIndex).useWriting();

            logger.debug("Writing file creation transaction log entry for channel {} file number {} timestamp {}",
                channelIndex, fileNumber, timeStamp);

            try
            {
                transactionFile.writeBytes(entryBufferFileCreation);
            }
            catch (final Exception e)
            {
                throw new RuntimeException("Failed to write file creation transaction log entry", e);
            }
            finally
            {
                transactionFile.release();
                XMemory.deallocateDirectByteBuffer(entryBufferFileCreation);
            }
        }



        /**
         * Collect inventory information for deleted data files across all channels.
         * <p>
         * The collected map is stored in {@link #dataFiles} and used to search for
         * object occurrences in deleted data files.
         */
        private void collectDeletedDataFiles()
        {
            for (int i = 0; i < this.channelCount; i++)
            {
                final TreeMap<Long, StorageDataInventoryFile> channelFiles = new TreeMap<>(Comparator.reverseOrder());
                this.storageFileProvider.collectDeletedDataFiles(
                    StorageDataInventoryFile::New,
                    f -> channelFiles.put(f.number(), f),
                    i);

                dataFiles.put(i, channelFiles);
                logger.debug("Collected Files {}", channelFiles);
            }
        }

        /**
         * Collect the size (in bytes) of the most-recent data file for each
         * configured storage channel and store the result in the
         * {@link #dataFilesSizes} array.
         */
        private void collectStorageFilesSizes()
        {
            for (int i = 0; i < this.channelCount; i++)
            {
                final TreeMap<Long, Long> channelFiles = new TreeMap<>(Comparator.reverseOrder());
                this.storageFileProvider.collectDataFiles(
                        StorageDataInventoryFile::New,
                        f -> channelFiles.put(f.number(), f.size()),
                        i);

                dataFilesSizes[i] = Math.toIntExact(channelFiles.firstEntry().getValue());
            }
        }

        /**
         * Search the collected deleted-data-file inventory for occurrences of the
         * given {@code objectId}.
         *
         * @param objectId persistence id to lookup
         * @return a sorted map of file positions -> ObjectEntry (newest first) or an empty map
         */
        private TreeMap<Long, ObjectEntry> searchObject(final long objectId)
        {
            final int expectedChannel = Math.toIntExact(objectId % channelCount);
            final TreeMap<Long, StorageDataInventoryFile> channelFiles = dataFiles.get(expectedChannel);

            for (final Map.Entry<Long, StorageDataInventoryFile> longStorageDataInventoryFileEntry : channelFiles.entrySet())
            {
                final Long key = longStorageDataInventoryFileEntry.getKey();
                final StorageDataInventoryFile value = longStorageDataInventoryFileEntry.getValue();

                logger.debug("Searching in channel {} file number: {} file name: {}", expectedChannel, key, value);

                final TreeMap<Long, ObjectEntry> findings = parseFile(value.file(), objectId);
                if(!findings.isEmpty())
                {
                    return findings;
                }
            }

            return new TreeMap<>();
        }

        /**
         * Search the provided data file and index all entities matching {@code objectId}.
         *
         * @param file the data file to parse
         * @param objectId object id to match
         * @return map of entry position -> ObjectEntry (newest first)
         */
        private TreeMap<Long, ObjectEntry> parseFile(final AFile file, final long objectId)
        {
            final TreeMap<Long, ObjectEntry> inventory = new TreeMap<>(Comparator.reverseOrder());

            final AReadableFile rFile = file.useReading();
            try
            {
                rFile.open();

                // Use StorageDataFileItemIterator to walk the binary items.
                // The ItemProcessor receives the memory address of each item and
                // the amount of buffered data available from that address.
                // We need at least the entity header (24 bytes) to read TID/OID.
                final int headerLen = Binary.entityHeaderLength(); // 24

                final StorageDataFileItemIterator.BufferProvider bufferProvider = StorageDataFileItemIterator.BufferProvider.New();

                // We track the running file position ourselves.
                final long[] currentPos = {0L};

                final StorageDataFileItemIterator.ItemProcessor processor = (address, remainingBuffered) ->
                {
                    // Read the raw length value stored at this address.
                    final long rawLength = Binary.getEntityLengthRawValue(address);

                    if (rawLength < 0)
                    {
                        // Negative length == gap comment record; skip it.
                        currentPos[0] += Math.abs(rawLength);
                        return true;
                    }

                    // Positive length == real entity. Need the full header to extract OID.
                    if (remainingBuffered < headerLen)
                    {
                        // Signal the iterator to reload with the full entity length.
                        return false;
                    }

                    final long oid = Binary.getEntityObjectIdRawValue(address);
                    final long entryPosition = currentPos[0];

                    if(oid == objectId)
                    {
                        inventory.put(entryPosition, new ObjectEntry(file, entryPosition, rawLength));
                    }

                    currentPos[0] += rawLength;
                    return true;
                };

                final StorageDataFileItemIterator iterator = StorageDataFileItemIterator.New(bufferProvider, processor);

                iterator.iterateStoredItems(rFile);
            }
            finally
            {
                rFile.release();
            }

            return inventory;
        }

        /**
         * Read the raw entity blob described by {@code entry} from its backing file.
         *
         * @param entry object entry describing file and position
         * @return a heap byte[] containing the complete raw entity
         */
        private byte[] readBlob(final ObjectEntry entry)
        {
            final AReadableFile rFile = entry.file().useReading();
            try
            {
                rFile.open();

                final int length = Math.toIntExact(entry.rawLength());
                final ByteBuffer bb = XMemory.allocateDirectNative(length);
                try
                {
                    rFile.readBytes(bb, entry.entryPosition, length);
                    bb.flip();

                    return XMemory.toArray(bb);
                }
                finally
                {
                    XMemory.deallocateDirectByteBuffer(bb);
                }
            }
            finally
            {
                rFile.release();
            }
        }
    }

}

