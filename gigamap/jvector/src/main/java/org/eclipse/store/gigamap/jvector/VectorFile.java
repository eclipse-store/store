package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
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


import org.eclipse.serializer.exceptions.IORuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.CRC32;

/**
 * The vectors of one {@link VectorIndex}, in chunked files that are memory-mapped and read without any lock.
 * <p>
 * <b>A cache, not the source of truth.</b> The entity is the source of truth, reached through the
 * {@link Vectorizer}, whose contract is that {@code vectorize} reproduces the committed vector of a persisted
 * entity. So this file is never made durable on the store path. Data pages are forced only by a periodic
 * {@link #checkpoint()}, on {@link #close()}, and by recovery for the chunks it repaired ({@link #repaired}),
 * and whatever a crash may have left inconsistent is found at the
 * next {@link #open} and repaired by the index from the entities. Losing the file costs time, never data.
 * <p>
 * <b>Layout.</b> {@code <name>.vec} holds the header, {@code <name>.vec-<n>} chunk {@code n}. A chunk is
 * {@link #slotsPerChunk()} slots of {@code roundUp(32 + 4 * dimension, 64)} bytes; slot {@code i} belongs to
 * ordinal {@code i} and is overwritten in place. A slot starts with a 32-byte header,
 * {@code {long seq; long epochFlags; long vectorKey; long reserved}}, followed by the floats, so a read touches
 * one or two pages rather than one for the header and one for the vector: mapped pages count against the memory
 * of a container. A slot that was never written is all zeros, which reads as absent. There is no checksum:
 * recovery never trusts a slot it cannot prove, so a checksum would cost every write and buy nothing.
 * <p>
 * <b>Concurrency.</b> One writer at a time, which the index guarantees by writing under the parent GigaMap
 * monitor only, and any number of readers on any thread, without a lock. A slot is guarded by a seqlock in its
 * {@code seq} field: the writer makes it odd, writes, and makes it even again; a reader retries while it is odd
 * or changed during its copy. The chunk table is a volatile array, copied by the writer when it grows, so a
 * reader sees either a chunk or none. Chunks are never truncated or unmapped while the file is open.
 * <p>
 * <b>Epochs.</b> Every write stamps its slot with the write epoch {@code W}. The index persists the store epoch
 * {@code E} with its record: {@link #seal()} on the store path makes {@code E = W} and advances {@code W}, so a
 * slot at an epoch above the persisted {@code E} was written after the last committed store and may be ahead of
 * it. {@code C}, the highest epoch known committed, is advanced by {@link #committed(long)}.
 * <p>
 * <b>Hot chunks.</b> The only thing ever forced on the write path. Besides the hot bit, the header keeps per chunk
 * whether it was created and how many of its slots hold a vector, written with every header write, so an open can
 * drop a cold chunk that holds none without reading it. The first write
 * into a cold chunk sets the chunk's bit in the header and forces the header before it writes the slot, so the
 * bit is on disk before any data page of that chunk can be. A {@link #checkpoint()} forces the hot chunks and
 * makes a chunk cold again once its last write is on disk and committed. Cold chunks are trusted at open without
 * being read; only hot ones are looked at, which bounds recovery by the chunks written since the last
 * checkpoint.
 * <p>
 * <b>Open.</b> Same boot (or a clean close): the page cache survived, so every completed write is on disk as
 * written, and a hot chunk only needs its slots above {@code E}, or half written, repaired. Another or an unknown
 * boot: a dirty page of a hot chunk may have reached the disk torn or stale while still looking valid, so every
 * ordinal of a hot chunk is repaired. A missing, foreign or unreadable file, or one older than the store, is
 * replaced by a fresh one, and the index re-vectorizes every entity into it (see {@link Recovery}). Restoring a
 * vector file separately from the store is not supported for that reason; deleting it forces a rebuild.
 * <p>
 * <b>Boot identity</b> comes from {@code /proc/sys/kernel/random/boot_id}, so only Linux tells a process crash
 * from a reboot. Elsewhere every unclean open takes the reboot path, which is correct, only slower.
 */
interface VectorFile extends Closeable
{
    /**
     * The extension of the header file. Chunk {@code n} is in {@code <name>.vec-<n>}. Neither matches
     * {@link SegmentFiles#layoutFilePattern(String)}, so the cleanup of the segment layout leaves them alone.
     */
    public static final String HEADER_FILE_EXT = ".vec";

    /**
     * The default size of a chunk, in bytes.
     */
    public static final long DEFAULT_CHUNK_BYTES = 64L << 20;

    /**
     * The fewest slots a configured chunk must hold. The chunk table is dense up to the highest chunk written, at
     * about 20 bytes per chunk number, so this bounds it at 2^20 chunks, about 20 MB, for any ordinal an index
     * accepts, however sparse.
     */
    public static final int MIN_SLOTS_PER_CHUNK = 2048;

    /**
     * @return the vector dimension
     */
    public int dimension();

    /**
     * @return the number of slots in a chunk
     */
    public int slotsPerChunk();

    /**
     * Returns the highest ordinal ever written, present or not, or {@code -1}. Monotonic while the file is open.
     *
     * @return the highest written ordinal
     */
    public int highestOrdinal();

    /**
     * Returns whether a chunk exists. Every slot of a chunk that does not exist is absent, so a scan over the
     * ordinals can skip it whole: ordinals are entity ids, which can be sparse.
     *
     * @param chunk the chunk index, the ordinal divided by {@link #slotsPerChunk()}
     * @return whether the chunk exists
     */
    public boolean hasChunk(int chunk);

    /**
     * Visits the ordinal of every slot of the existing chunks up to {@link #highestOrdinal()}, in ascending order,
     * present or not, skipping the chunks that do not exist. The one whole-file scan of the index: ordinals are
     * entity ids, which can be sparse, and the highest can be {@code Integer.MAX_VALUE - 1}.
     *
     * @param visitor called with each ordinal, returns {@code false} to stop the scan
     * @return whether the scan visited every slot, {@code false} if the visitor stopped it
     */
    public default boolean forEachSlot(final IntPredicate visitor)
    {
        final int highest  = this.highestOrdinal();
        final int perChunk = this.slotsPerChunk();
        for(int chunk = 0; (long)chunk * perChunk <= highest; chunk++)
        {
            if(!this.hasChunk(chunk))
            {
                continue;
            }
            // A long position, so that the last ordinal of the int range does not wrap the loop.
            final long last = Math.min((long)chunk * perChunk + perChunk - 1L, highest);
            for(long position = (long)chunk * perChunk; position <= last; position++)
            {
                if(!visitor.test((int)position))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Visits the ordinal of every slot that holds a vector, in ascending order, see {@link #forEachSlot}.
     *
     * @param visitor called with each ordinal, returns {@code false} to stop the scan
     * @return whether the scan visited every slot, {@code false} if the visitor stopped it
     */
    public default boolean forEachPresent(final IntPredicate visitor)
    {
        return this.forEachSlot(ordinal -> !this.isPresent(ordinal) || visitor.test(ordinal));
    }

    /**
     * @return the identity of this file, persisted with the index so another file is recognized as foreign
     */
    public long nonce();

    /**
     * @return what {@link #open} found and the index has to repair from the entities
     */
    public Recovery recovery();

    /**
     * Copies the vector of {@code ordinal} into {@code target}, lock-free.
     *
     * @param ordinal the ordinal
     * @param target  the array to copy into
     * @param offset  the index in {@code target} to copy to
     * @return whether the ordinal has a vector; {@code target} is unchanged if not
     * @throws IllegalStateException if the file is closed
     */
    public boolean read(int ordinal, float[] target, int offset);

    /**
     * Returns a copy of the vector of {@code ordinal}, lock-free.
     *
     * @param ordinal the ordinal
     * @return the vector, or {@code null} if the ordinal has none
     * @throws IllegalStateException if the file is closed
     */
    public default float[] read(final int ordinal)
    {
        final float[] vector = new float[this.dimension()];
        return this.read(ordinal, vector, 0) ? vector : null;
    }

    /**
     * Whether {@code ordinal} has a vector. For the writer.
     *
     * @param ordinal the ordinal
     * @return whether it has one
     */
    public boolean isPresent(int ordinal);

    /**
     * Returns the vector key the slot of {@code ordinal} was last written with, present or not. For the writer.
     *
     * @param ordinal the ordinal
     * @return the key, or {@link Vectorizer#NO_KEY}
     */
    public long key(int ordinal);

    /**
     * Whether {@code ordinal} has exactly {@code vector}, compared like {@link Arrays#equals(float[], float[])}.
     * For the writer.
     *
     * @param ordinal the ordinal
     * @param vector  the vector to compare with
     * @return whether the slot holds it
     */
    public boolean holds(int ordinal, float[] vector);

    /**
     * Writes the vector of {@code ordinal}, or makes it absent, at the write epoch. For the writer only.
     *
     * @param ordinal the ordinal
     * @param vector  the vector, of {@link #dimension()} floats, or {@code null} to make the ordinal absent
     * @param key     the vector key, or {@link Vectorizer#NO_KEY}
     * @throws IORuntimeException if a chunk cannot be created or the header cannot be written
     */
    public void write(int ordinal, float[] vector, long key);

    /**
     * Whether {@code ordinal}'s slot has to be repaired by an epoch scan: written after the last committed
     * store, or half written by a process that died. For the recovery of the writer.
     *
     * @param ordinal the ordinal
     * @return whether to repair it
     */
    public boolean needsRepair(int ordinal);

    /**
     * Writes a repaired slot at the store epoch, so it does not count as written after the store. For the
     * recovery of the writer.
     *
     * @param ordinal the ordinal
     * @param vector  the vector, or {@code null}
     * @param key     the vector key, or {@link Vectorizer#NO_KEY}
     */
    public void repair(int ordinal, float[] vector, long key);

    /**
     * Forces a chunk whose slots recovery has repaired. Recovery streams chunk by chunk, so this keeps what it
     * repaired from depending on a later checkpoint.
     *
     * @param chunk the chunk
     */
    public void repaired(int chunk);

    /**
     * Ends recovery: forces what it wrote and makes the repaired chunks cold. Checkpoints do nothing before.
     */
    public void recovered();

    /**
     * Seals the writes since the last seal into the store epoch: {@code E = W}, then {@code W + 1}. Called on the
     * store path by the writer, does no I/O.
     *
     * @return the store epoch to persist, or {@code -1} if nothing was written since the last seal
     */
    public long seal();

    /**
     * Records that a store carrying {@code epoch} has committed.
     *
     * @param epoch the committed store epoch
     */
    public void committed(long epoch);

    /**
     * @return whether a checkpoint is due by its interval and there is a hot chunk
     */
    public boolean isCheckpointDue();

    /**
     * Forces the hot chunks and makes cold those whose last write is forced and committed. Forces without a lock
     * the writer takes, then takes the header lock to update and write the header; the writer takes that lock
     * only briefly (to create a chunk, make one hot, or seal), never for a slot write into a hot chunk. Never
     * runs concurrently
     * with itself or {@link #close()}.
     * <p>
     * Does nothing while the file is unchanged since its open - no slot written and no recovery with work to do
     * completed: the header on disk already describes the files, so hot chunks stay hot and the files stay byte
     * for byte as the open found them.
     *
     * @throws IORuntimeException if forcing or writing the header fails
     */
    public void checkpoint();

    /**
     * Checkpoints, marks the file clean and unmaps it. Readers fail with an {@link IllegalStateException}
     * afterwards. A file closed while its recovery has not completed is neither checkpointed nor marked clean,
     * so the next open recovers it again. A temporary file is deleted, see {@link #closeAndDelete()}.
     * <p>
     * A file unchanged since its open is only unmapped: no checkpoint and no header write, so it keeps the hot
     * chunks and the clean or unclean state it was opened with, and the next open recovers exactly what this one
     * would have. Closing guarantees nothing about the files beyond what the session changed in them.
     */
    @Override
    public void close();

    /**
     * Unmaps the file and deletes it, header and chunks.
     */
    public void closeAndDelete();

    /**
     * @return whether the file is closed
     */
    public boolean isClosed();

    /**
     * @return the number of I/O operations so far (header writes, forces, chunk creations); a test seam
     */
    public long ioCount();

    /**
     * Sets how often a checkpoint is due, and has the shared checkpoint thread run it when it is. The thread
     * holds the file only weakly, so an abandoned file is not kept alive by it.
     *
     * @param millis the checkpoint interval in milliseconds, positive
     */
    public void checkpointInterval(long millis);


    /**
     * Opens the vector file of index {@code name} in {@code directory}, or creates a fresh one, and works out what
     * the index has to repair.
     *
     * @param directory     the directory, created only once the file first writes something into it
     * @param name          the index name
     * @param dimension     the vector dimension
     * @param chunkBytes    the chunk size in bytes, at least one slot
     * @param expectedNonce the nonce persisted with the index, {@code 0} if none
     * @param storeEpoch    the store epoch persisted with the index
     * @param description   the index, for logging
     * @return the open file
     * @throws IORuntimeException if the file can neither be opened nor created
     */
    public static VectorFile open(
        final Path   directory    ,
        final String name         ,
        final int    dimension    ,
        final long   chunkBytes   ,
        final long   expectedNonce,
        final long   storeEpoch   ,
        final String description
    )
    {
        return Default.open(directory, name, dimension, chunkBytes, expectedNonce, storeEpoch, description);
    }

    /**
     * Opens a fresh vector file in a new private directory under {@code <java.io.tmpdir>/eclipsestore-vectors}
     * (or {@code eclipsestore-vectors} under the directory the system property
     * {@link Default#TEMPORARY_ROOT_PROPERTY} names), for an in-memory index. The directory has a unique name and
     * stays locked while the file is open; it is deleted when the file is closed or dropped, and directories of
     * this kind that no process holds locked any more are deleted on first use, also those of other processes
     * sharing the temporary directory. Nothing else under that subdirectory is touched.
     *
     * @param name        the index name
     * @param dimension   the vector dimension
     * @param chunkBytes  the chunk size in bytes
     * @param description the index, for logging
     * @return the open, empty file
     * @throws IORuntimeException if the directory or the file cannot be created
     */
    public static VectorFile openTemporary(
        final String name       ,
        final int    dimension  ,
        final long   chunkBytes ,
        final String description
    )
    {
        return Default.openTemporary(name, dimension, chunkBytes, description);
    }

    /**
     * Returns the size in bytes of one slot.
     *
     * @param dimension the vector dimension
     * @return the stride
     */
    public static int strideOf(final int dimension)
    {
        return Default.strideOf(dimension);
    }

    /**
     * Returns whether {@code fileName} is a file of the vector file of index {@code name}.
     *
     * @param name     the index name
     * @param fileName the file name
     * @return whether it is the header or a chunk
     */
    public static boolean isVectorFile(
        final String name    ,
        final String fileName
    )
    {
        return Default.chunkPattern(name).matcher(fileName).matches()
            || fileName.equals(name + HEADER_FILE_EXT);
    }


    /**
     * What {@link #open} found, for the index to repair from the entities.
     *
     * @param fullRebuildReason why the file was replaced by an empty one, so every entity has to be vectorized
     *                          into it, or {@code null} if it was kept
     * @param scanChunks        hot chunks whose slots above the store epoch, or half written, are repaired
     * @param repairChunks      hot chunks of which every ordinal is repaired
     */
    public record Recovery(
        String fullRebuildReason,
        int[]  scanChunks       ,
        int[]  repairChunks
    )
    {
        /**
         * @return whether the file was replaced by an empty one
         */
        public boolean fullRebuild()
        {
            return this.fullRebuildReason != null;
        }

        /**
         * @return whether there is nothing to repair
         */
        public boolean isEmpty()
        {
            return !this.fullRebuild() && this.scanChunks.length == 0 && this.repairChunks.length == 0;
        }
    }


    /**
     * Default implementation.
     */
    public static final class Default implements VectorFile
    {
        ///////////////////////////////////////////////////////////////////////////
        // constants //
        //////////////

        private static final Logger LOG = LoggerFactory.getLogger(VectorFile.class);

        private static final int MAGIC   = 0x4356_4543; // "CVEC"
        // 2: the header records the number of present slots of every chunk.
        private static final int VERSION = 2;

        // slot layout
        private static final long SLOT_SEQ        =  0L;
        private static final long SLOT_EPOCH      =  8L;
        private static final long SLOT_KEY        = 16L;
        private static final long SLOT_FLOATS     = 32L;
        private static final int  SLOT_HEADER     = 32;
        private static final int  SLOT_ALIGNMENT  = 64;
        private static final long FLAG_PRESENT    =  1L;
        private static final long FLAG_TOMBSTONE  =  2L;
        private static final int  EPOCH_SHIFT     =  2;

        // header record layout, little endian
        private static final int  RECORD_FIXED    = 104;
        private static final int  MIN_REGION      = 4096;

        private static final int  ZERO_BUFFER     = 1 << 20;

        private static final VarHandle LONG = ValueLayout.JAVA_LONG.varHandle();

        private static final SecureRandom NONCES = new SecureRandom();

        private static final Cleaner CLEANER = Cleaner.create();

        private static final Path BOOT_ID_FILE = Path.of("/proc/sys/kernel/random/boot_id");

        // Test seam: where the boot identity comes from. Null means unknown.
        private static volatile Supplier<UUID> bootIdSource = Default::readBootId;

        // Test seam: run by an open between the takeover and the registration, holding the open's path lock.
        static volatile Runnable openTestHook;

        // Test seam: run before a chunk file is filled; a hook that throws an IOException simulates a full disk.
        static volatile IoHook chunkCreationTestHook;

        // Test seam: run before a header record is written; a hook that throws an IOException fails the write.
        static volatile IoHook headerWriteTestHook;

        // The default checkpoint interval, overridable per file.
        static final long DEFAULT_CHECKPOINT_INTERVAL_NANOS = 30_000_000_000L;


        ///////////////////////////////////////////////////////////////////////////
        // static methods //
        ///////////////////

        static int strideOf(final int dimension)
        {
            final long raw = SLOT_HEADER + 4L * dimension;
            return Math.toIntExact((raw + SLOT_ALIGNMENT - 1) / SLOT_ALIGNMENT * SLOT_ALIGNMENT);
        }

        static Pattern chunkPattern(final String name)
        {
            return Pattern.compile("^" + Pattern.quote(name + HEADER_FILE_EXT) + "-(\\d{1,9})$");
        }

        /**
         * Test seam: replaces the source of the boot identity, {@code null} restores the default.
         *
         * @param source the source, whose {@code null} result means unknown
         */
        static void bootIdSource(final Supplier<UUID> source)
        {
            bootIdSource = source != null ? source : Default::readBootId;
        }

        private static UUID readBootId()
        {
            try
            {
                return UUID.fromString(Files.readString(BOOT_ID_FILE).trim());
            }
            catch(final IOException | RuntimeException e)
            {
                return null;
            }
        }

        private static UUID currentBootId()
        {
            try
            {
                return bootIdSource.get();
            }
            catch(final RuntimeException e)
            {
                LOG.debug("No boot identity: {}", e.toString());
                return null;
            }
        }

        private static long newNonce()
        {
            long nonce;
            do
            {
                nonce = NONCES.nextLong();
            }
            while(nonce == 0L);
            return nonce;
        }

        /**
         * System property naming the directory under which the temporary vector files of in-memory indices live,
         * in its subdirectory {@code eclipsestore-vectors}; {@code <java.io.tmpdir>} unless set. A deployment with a
         * shared or read-only temporary directory points it elsewhere. Only that subdirectory is ever written or
         * cleaned up, never the named directory itself.
         */
        public static final String        TEMPORARY_ROOT_PROPERTY = "org.eclipse.store.gigamap.jvector.temporaryRoot";

        // The subdirectory of the temporary base that holds the directories of temporary vector files.
        private static final String       TEMPORARY_DIRECTORY = "eclipsestore-vectors";
        private static final Path         TEMPORARY_ROOT    = temporaryRoot();
        private static final AtomicLong   TEMPORARY_REAPED  = new AtomicLong();
        // A directory openTemporary creates (pid, a dash, a unique suffix) and the files it can hold. The reaper
        // deletes nothing else: whatever else sits there belongs to someone else.
        private static final Pattern      TEMPORARY_NAME    = Pattern.compile("\\d+-.+");
        private static final Pattern      TEMPORARY_FILE    = Pattern.compile("\\.lock|.+\\.vec(-\\d+)?");

        private static Path temporaryRoot()
        {
            final String configured = System.getProperty(TEMPORARY_ROOT_PROPERTY);
            final Path   base       = configured != null && !configured.isBlank()
                ? Path.of(configured)
                : Path.of(System.getProperty("java.io.tmpdir"))
            ;
            return base.resolve(TEMPORARY_DIRECTORY);
        }
        // Held locked by the file using a temporary directory, so no other process deletes it as abandoned.
        private static final String       TEMPORARY_LOCK    = ".lock";
        private static final Duration     TEMPORARY_GRACE   = Duration.ofMinutes(1L);

        static Default openTemporary(
            final String name       ,
            final int    dimension  ,
            final long   chunkBytes ,
            final String description
        )
        {
            final Path        directory;
            final FileChannel lock;
            try
            {
                reapTemporaryDirectories();
                Files.createDirectories(TEMPORARY_ROOT);
                // Unique across processes and class loaders, which a pid and a counter are not: pids repeat
                // across containers sharing a temporary directory, and every class loader has its own counter.
                directory = Files.createTempDirectory(TEMPORARY_ROOT, ProcessHandle.current().pid() + "-");
                lock      = lockTemporaryDirectory(directory);
            }
            catch(final IOException e)
            {
                throw new IORuntimeException(e);
            }
            final Default file;
            try
            {
                file = open(directory, name, dimension, chunkBytes, 0L, 0L, description);
            }
            catch(final RuntimeException e)
            {
                closeQuietly(lock);
                deleteDirectory(directory);
                throw e;
            }
            file.temporary = true;
            file.resources.temporaryLock      = lock;
            file.resources.temporaryDirectory = directory;
            return file;
        }

        /**
         * Locks a temporary directory for as long as the returned channel is open: the lock, which the operating
         * system releases when the process ends, is what tells the reaper of any process that the directory is
         * in use.
         */
        private static FileChannel lockTemporaryDirectory(final Path directory) throws IOException
        {
            final FileChannel channel = FileChannel.open(
                directory.resolve(TEMPORARY_LOCK), StandardOpenOption.CREATE, StandardOpenOption.WRITE
            );
            try
            {
                if(channel.tryLock() == null)
                {
                    throw new IOException("The new temporary directory " + directory + " is locked already");
                }
                return channel;
            }
            catch(final IOException | RuntimeException e)
            {
                closeQuietly(channel);
                throw e;
            }
        }

        private static void closeQuietly(final FileChannel channel)
        {
            try
            {
                channel.close();
            }
            catch(final IOException e)
            {
                LOG.debug("Could not close {}: {}", channel, e.toString());
            }
        }

        /**
         * Returns whether a temporary directory is abandoned: its lock file is not locked by any process. One
         * without a lock file is abandoned once it is older than {@link #TEMPORARY_GRACE}, which leaves its
         * creator the moment between creating it and locking it.
         *
         * @param directory a directory under the temporary root
         * @return whether it may be deleted
         */
        static boolean isAbandoned(final Path directory)
        {
            final Path lockFile = directory.resolve(TEMPORARY_LOCK);
            try
            {
                if(!Files.exists(lockFile))
                {
                    return Files.getLastModifiedTime(directory).toInstant()
                        .isBefore(Instant.now().minus(TEMPORARY_GRACE));
                }
                try(final FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE))
                {
                    final FileLock lock = channel.tryLock();
                    if(lock == null)
                    {
                        return false;
                    }
                    lock.release();
                    return true;
                }
            }
            catch(final OverlappingFileLockException e)
            {
                // Held by this process, through another file of another class loader.
                return false;
            }
            catch(final IOException e)
            {
                LOG.debug("Could not check whether {} is in use: {}", directory, e.toString());
                return false;
            }
        }

        /**
         * Deletes, once per class loader that loads this class (a static flag), the temporary directories of
         * processes that no longer run: a process that is killed leaves them behind.
         */
        private static void reapTemporaryDirectories()
        {
            if(!TEMPORARY_REAPED.compareAndSet(0L, 1L) || !Files.isDirectory(TEMPORARY_ROOT))
            {
                return;
            }
            try(final Stream<Path> directories = Files.list(TEMPORARY_ROOT))
            {
                for(final Path directory : (Iterable<Path>)directories::iterator)
                {
                    if(Files.isDirectory(directory) && isOwnTemporaryDirectory(directory) && isAbandoned(directory))
                    {
                        LOG.info("Deleting the abandoned temporary vector directory {}", directory);
                        deleteDirectory(directory);
                    }
                }
            }
            catch(final IOException e)
            {
                LOG.debug("Could not reap the vector directories in {}: {}", TEMPORARY_ROOT, e.toString());
            }
        }

        /**
         * Returns whether {@code directory} is one {@link #openTemporary} creates: named by the pattern it uses and
         * holding nothing but its lock file and vector files. Anything else is left alone, with a warning: a
         * directory the reaper did not create is never its to delete.
         *
         * @param directory a directory under the temporary root
         * @return whether it is a temporary vector directory
         */
        static boolean isOwnTemporaryDirectory(final Path directory)
        {
            if(!TEMPORARY_NAME.matcher(directory.getFileName().toString()).matches())
            {
                LOG.warn("Leaving {} in the temporary vector root alone: not a temporary vector directory", directory);
                return false;
            }
            try(final Stream<Path> entries = Files.list(directory))
            {
                final Path foreign = entries
                    .filter(entry -> !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
                        || !TEMPORARY_FILE.matcher(entry.getFileName().toString()).matches())
                    .findFirst()
                    .orElse(null);
                if(foreign != null)
                {
                    LOG.warn("Leaving {} in the temporary vector root alone: it holds {}, which is not a vector file",
                        directory, foreign.getFileName());
                    return false;
                }
                return true;
            }
            catch(final IOException e)
            {
                LOG.debug("Could not check whether {} is a temporary vector directory: {}", directory, e.toString());
                return false;
            }
        }

        private static void deleteDirectory(final Path directory)
        {
            try(final Stream<Path> files = Files.list(directory))
            {
                for(final Path file : (Iterable<Path>)files::iterator)
                {
                    Files.deleteIfExists(file);
                }
                Files.deleteIfExists(directory);
            }
            catch(final IOException e)
            {
                LOG.debug("Could not delete the vector directory {}: {}", directory, e.toString());
            }
        }

        // The open files of this process by header path, so a second open of the same file takes it over.
        private static final ConcurrentHashMap<Path, WeakReference<Default>> OPEN = new ConcurrentHashMap<>();

        // Serialize the opens of one path, so takeover, open and registration are one step and two concurrent
        // opens cannot both map the file. Striped by path, so opens of other paths rarely wait and no lock is
        // kept per path.
        private static final Object[] OPEN_LOCKS = newLocks(64);

        private static Object[] newLocks(final int count)
        {
            final Object[] locks = new Object[count];
            for(int i = 0; i < count; i++)
            {
                locks[i] = new Object();
            }
            return locks;
        }

        /**
         * Test seam: whether an open file of this process maps the header in {@code directory}.
         *
         * @param directory the directory
         * @param name      the index name
         * @return whether the header is registered as open
         */
        static boolean isRegisteredOpen(
            final Path   directory,
            final String name
        )
        {
            return OPEN.containsKey(directory.resolve(name + HEADER_FILE_EXT).toAbsolutePath().normalize());
        }

        /**
         * Releases a file of this process that maps the same header as a new open, without writing anything:
         * the index it belongs to was dropped without a close (a storage restarted in the same process), and
         * its scheduled checkpoint must not write a header over the one the new open reads. What it wrote is
         * in the page cache, where the new open finds it like after a killed process.
         */
        private static void takeOver(final Path header)
        {
            final WeakReference<Default> previous = OPEN.get(header);
            final Default file = previous != null ? previous.get() : null;
            if(file != null && !file.isClosed())
            {
                LOG.info("Taking over {} from an instance that was not closed", file);
                file.crashForTest();
            }
        }

        static Default open(
            final Path   directory    ,
            final String name         ,
            final int    dimension    ,
            final long   chunkBytes   ,
            final long   expectedNonce,
            final long   storeEpoch   ,
            final String description
        )
        {
            final Path header = directory.resolve(name + HEADER_FILE_EXT).toAbsolutePath().normalize();
            synchronized(OPEN_LOCKS[Math.floorMod(header.hashCode(), OPEN_LOCKS.length)])
            {
                takeOver(header);
                final Runnable hook = openTestHook;
                if(hook != null)
                {
                    hook.run();
                }
                final Default file = openUnregistered(
                    directory, name, dimension, chunkBytes, expectedNonce, storeEpoch, description
                );
                final WeakReference<Default> registration = new WeakReference<>(file);
                OPEN.put(header, registration);
                // Unregistered again when the file is released, closed or dropped, so the map holds no path for
                // good.
                file.resources.register(header, registration);
                return file;
            }
        }

        private static Default openUnregistered(
            final Path   directory    ,
            final String name         ,
            final int    dimension    ,
            final long   chunkBytes   ,
            final long   expectedNonce,
            final long   storeEpoch   ,
            final String description
        )
        {
            final int stride = strideOf(dimension);
            if(chunkBytes < stride)
            {
                throw new IllegalArgumentException("A chunk of " + chunkBytes + " bytes holds no slot of " + stride);
            }
            try
            {
                final Header header = Header.read(directory.resolve(name + HEADER_FILE_EXT));
                final String reason = rejectionReason(
                    header, dimension, stride, chunkBytes, expectedNonce, storeEpoch
                );
                if(reason != null)
                {
                    // Quiet for a new index, and for one that never wrote a vector, whose file was never created.
                    if(header != null || storeEpoch > 0L)
                    {
                        LOG.warn("The vector file of {} is {}: rebuilding it from the entities", description, reason);
                    }
                    return createFresh(directory, name, dimension, chunkBytes, storeEpoch, reason, description);
                }
                return openExisting(directory, name, dimension, chunkBytes, storeEpoch, header, description);
            }
            catch(final IOException e)
            {
                throw new IORuntimeException(e);
            }
        }

        private static String rejectionReason(
            final Header header       ,
            final int    dimension    ,
            final int    stride       ,
            final long   chunkBytes   ,
            final long   expectedNonce,
            final long   storeEpoch
        )
        {
            if(header == null)
            {
                return "missing or unreadable";
            }
            if(header.dimension != dimension
                || header.stride != stride
                || header.chunkBytes != chunkBytes
                || header.bigEndian != (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN))
            {
                return "of another layout";
            }
            if(header.nonce != expectedNonce)
            {
                return "foreign to the stored index";
            }
            if(storeEpoch > header.highestEpoch && header.hot.isEmpty())
            {
                return "older than the stored index";
            }
            if(header.coldEpoch > storeEpoch)
            {
                return "ahead of the stored index in forced chunks";
            }
            return null;
        }

        private static Default createFresh(
            final Path   directory  ,
            final String name       ,
            final int    dimension  ,
            final long   chunkBytes ,
            final long   storeEpoch ,
            final String reason     ,
            final String description
        ) throws IOException
        {
            deleteFiles(directory, name);
            final Header header = new Header();
            header.dimension    = dimension;
            header.stride       = strideOf(dimension);
            header.chunkBytes   = chunkBytes;
            header.bigEndian    = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
            header.nonce        = newNonce();
            header.forcedEpoch  = storeEpoch;
            header.highestEpoch = storeEpoch;
            header.coldEpoch    = 0L;
            header.clean        = true;
            // Nothing is created yet, not even the directory: the header is written with the first chunk, so an
            // index that never holds a vector leaves no trace on disk.
            final Default file = new Default(directory, name, header, storeEpoch, description);
            file.recovery = new Recovery(reason, new int[0], new int[0]);
            return file;
        }

        private static Default openExisting(
            final Path   directory  ,
            final String name       ,
            final int    dimension  ,
            final long   chunkBytes ,
            final long   storeEpoch ,
            final Header header     ,
            final String description
        ) throws IOException
        {
            final Default file = new Default(directory, name, header, storeEpoch, description);
            file.materialized = true;
            boolean opened = false;
            try
            {
                final Recovery recovery = file.mapExisting();
                file.recovery = recovery;
                opened = true;
                if(!recovery.isEmpty())
                {
                    LOG.info("Opened the vector file of {}: {} hot chunks to scan, {} to repair",
                        description, recovery.scanChunks().length, recovery.repairChunks().length);
                }
                return file;
            }
            finally
            {
                if(!opened)
                {
                    file.release();
                }
            }
        }

        private static void deleteFiles(
            final Path   directory,
            final String name
        ) throws IOException
        {
            if(!Files.isDirectory(directory))
            {
                return;
            }
            try(final Stream<Path> files = Files.list(directory))
            {
                for(final Path file : (Iterable<Path>)files::iterator)
                {
                    if(isVectorFile(name, file.getFileName().toString()))
                    {
                        Files.deleteIfExists(file);
                    }
                }
            }
        }



        ///////////////////////////////////////////////////////////////////////////
        // instance fields //
        ////////////////////

        private final Path          directory      ;
        private final String        name           ;
        private final String        description    ;
        private final int           dimension      ;
        private final int           stride         ;
        private final long          chunkBytes     ;
        private final int           slotsPerChunk  ;
        private final long          chunkSize      ;
        private final Arena         arena          ;
        // Opened with the first header write, under headerLock or before the file is published.
        private       FileChannel   headerChannel  ;
        // Whether the header exists on disk: from the open of an existing file, from the first chunk of a new one.
        private       boolean       materialized   ;
        // Whether a slot was written, or a recovery with work to do completed, since the open. Until then the files
        // on disk are exactly what the header on disk describes, so a checkpoint or a close has nothing to record,
        // and writes nothing: an index is opened at load, before anyone can tell whether it will be used, and one
        // that is only opened and closed - say by a copied store re-registering an index whose persisted
        // directory still names the original deployment's directory - must leave that file untouched. Set by the
        // single writer, read under forceLock.
        private volatile boolean    writtenSinceOpen;
        private final Header        header         ;
        private final Object        headerLock     = new Object();
        private final Object        forceLock      = new Object();
        private final AtomicLong    ioCount        = new AtomicLong();
        private final Resources     resources      ;
        private final Cleaner.Cleanable cleanable  ;
        private          boolean    temporary      ;
        private volatile ScheduledFuture<?> checkpointTask;

        // Copied on growth by the writer under headerLock; readers read it once.
        private volatile Chunks     chunks         = Chunks.EMPTY;
        private volatile int        highestOrdinal = -1;
        private volatile boolean    closed         ;

        private volatile long       writeEpoch     ;
        private volatile long       storeEpoch     ;
        private volatile long       committedEpoch ;
        // Epochs sealed for a store that has not committed yet, and epochs committed while an earlier one was
        // still pending, both under headerLock; see committed(long).
        private final TreeSet<Long> uncommittedSeals = new TreeSet<>();
        private final TreeSet<Long> committedSeals   = new TreeSet<>();
        private          boolean    writtenSinceSeal;
        private volatile boolean    recovering     = true;
        // Set once a repair found the header clean and wrote it unclean, so later repairs skip the check.
        private volatile boolean    repairMarkedUnclean;
        private          boolean    createdSinceSync;
        private volatile long       checkpointIntervalNanos = DEFAULT_CHECKPOINT_INTERVAL_NANOS;
        private volatile long       lastCheckpointNanos     = System.nanoTime();

        private Recovery            recovery       ;

        // Test seam: run by a checkpoint once it has forced the hot chunks, before it decides which turn cold.
        volatile Runnable           afterForceTestHook;

        // Test seam: counts the vector reads, if set. Null (and no cost beyond the read of it) in production.
        volatile LongAdder          readCountTestSeam;

        // Test seam: counts the directory syncs, if set.
        volatile LongAdder          directorySyncCountTestSeam;

        // Test seam: counts the presence probes, if set, so a test sees how many slots a scan visits.
        volatile LongAdder          probeCountTestSeam;



        ///////////////////////////////////////////////////////////////////////////
        // constructors //
        /////////////////

        private Default(
            final Path   directory  ,
            final String name       ,
            final Header header     ,
            final long   storeEpoch ,
            final String description
        ) throws IOException
        {
            this.directory     = directory;
            this.name          = name;
            this.description   = description;
            this.header        = header;
            this.dimension     = header.dimension;
            this.stride        = header.stride;
            this.chunkBytes    = header.chunkBytes;
            this.slotsPerChunk = Math.toIntExact(Math.min(Integer.MAX_VALUE, header.chunkBytes / header.stride));
            this.chunkSize     = (long)this.slotsPerChunk * this.stride;
            this.arena         = Arena.ofShared();
            this.storeEpoch     = storeEpoch;
            this.committedEpoch = storeEpoch;
            this.writeEpoch     = Math.max(storeEpoch, header.highestEpoch) + 1;
            // Unmaps a file whose index was dropped without a close, so an abandoned index leaks no mapping.
            this.resources      = new Resources(this.arena);
            this.cleanable      = CLEANER.register(this, this.resources);
        }



        ///////////////////////////////////////////////////////////////////////////
        // open //
        /////////

        /**
         * Maps the chunks the header knows and works out which to scan and which to repair. A chunk the header
         * lists as created but whose file is missing or of the wrong size is recreated and repaired; a chunk file
         * the header does not know was created by a crash before its first write and is deleted.
         */
        private Recovery mapExisting() throws IOException
        {
            final BitSet onDisk = new BitSet();
            try(final Stream<Path> files = Files.list(this.directory))
            {
                final Pattern pattern = chunkPattern(this.name);
                for(final Path file : (Iterable<Path>)files::iterator)
                {
                    final Matcher matcher = pattern.matcher(file.getFileName().toString());
                    if(!matcher.matches())
                    {
                        continue;
                    }
                    final int chunk = Integer.parseInt(matcher.group(1));
                    if(this.header.created.get(chunk) && Files.size(file) == this.chunkSize)
                    {
                        onDisk.set(chunk);
                    }
                    else if(!this.header.created.get(chunk))
                    {
                        Files.deleteIfExists(file);
                    }
                }
            }

            final UUID    boot      = currentBootId();
            final boolean sameBoot  = this.header.clean
                || boot != null && boot.equals(this.header.bootId)
            ;
            final List<Integer> scan      = new ArrayList<>();
            final List<Integer> repair    = new ArrayList<>();
            final List<Integer> dropped   = new ArrayList<>();
            boolean             recreated = false;
            Chunks              table     = Chunks.EMPTY.grownTo(this.header.created.length());
            final BitSet created = this.header.created;
            for(int chunk = created.nextSetBit(0); chunk >= 0; chunk = created.nextSetBit(chunk + 1))
            {
                final boolean intact = onDisk.get(chunk);
                final int     count  = chunk < this.header.present.length ? this.header.present[chunk] : 0;
                if(intact && !this.header.hot.get(chunk) && count == 0)
                {
                    // Cold, so forced and committed, and without a vector: entities removed for good. Neither
                    // mapped nor kept, so the file shrinks with the ids it no longer holds a vector for.
                    dropped.add(chunk);
                    continue;
                }
                table = table.with(chunk, this.mapChunk(chunk, !intact));
                table.present.set(chunk, intact ? count : 0);
                if(!intact)
                {
                    // Created once, but missing or cut short now: zeros again, and every ordinal repaired.
                    this.header.hot.set(chunk);
                    repair.add(chunk);
                    recreated = true;
                }
                else if(this.header.hot.get(chunk))
                {
                    (sameBoot ? scan : repair).add(chunk);
                }
                if(this.header.hot.get(chunk))
                {
                    table.hot.set(chunk, 1);
                    table.lastWriteEpoch.set(chunk, this.storeEpoch);
                }
            }
            this.chunks = table;
            this.highestOrdinal = this.highestWrittenOrdinal(table);
            for(final int chunk : dropped)
            {
                this.header.created.clear(chunk);
            }
            if(recreated || !dropped.isEmpty())
            {
                if(recreated)
                {
                    // Not the current boot: the header would then promise that a scan of the hot chunks is enough,
                    // and an open that follows a recovery cut short (killed, a failing vectorizer, a close) would
                    // only scan the recreated chunk, whose zero slots read as absent, and never repair it. Unknown,
                    // every open repairs the hot chunks in full until a recovery completes.
                    this.header.bootId = null;
                    this.header.clean  = false;
                }
                // Before the dropped chunks are deleted: a crash in between leaves files the header does not
                // list, which the next open deletes.
                this.writeHeader(true);
            }
            for(final int chunk : dropped)
            {
                Files.deleteIfExists(this.chunkPath(chunk));
            }
            if(!dropped.isEmpty())
            {
                LOG.info("Dropped {} chunks without a vector from the vector file of {}", dropped.size(),
                    this.description);
            }
            return new Recovery(
                null,
                scan.stream().mapToInt(Integer::intValue).toArray(),
                repair.stream().mapToInt(Integer::intValue).toArray()
            );
        }

        private MemorySegment mapChunk(
            final int     chunk ,
            final boolean create
        ) throws IOException
        {
            final Path path = this.chunkPath(chunk);
            if(create)
            {
                Files.createDirectories(this.directory);
            }
            try(final FileChannel channel = FileChannel.open(
                path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE
            ))
            {
                if(create)
                {
                    final IoHook hook = chunkCreationTestHook;
                    if(hook != null)
                    {
                        hook.run(path);
                    }
                    // Real zeros, written: a full disk then fails here as an IOException, never later as a
                    // SIGBUS on a page of a sparse file that the file system cannot back.
                    channel.truncate(0L);
                    final ByteBuffer zeros = ByteBuffer.allocateDirect((int)Math.min(ZERO_BUFFER, this.chunkSize));
                    long position = 0L;
                    while(position < this.chunkSize)
                    {
                        zeros.clear();
                        zeros.limit((int)Math.min(zeros.capacity(), this.chunkSize - position));
                        position += channel.write(zeros, position);
                    }
                    this.ioCount.incrementAndGet();
                    this.createdSinceSync = true;
                }
                return channel.map(FileChannel.MapMode.READ_WRITE, 0L, this.chunkSize, this.arena);
            }
        }

        private Path chunkPath(final int chunk)
        {
            return this.directory.resolve(this.name + HEADER_FILE_EXT + "-" + chunk);
        }



        ///////////////////////////////////////////////////////////////////////////
        // reading //
        ////////////

        /**
         * Finds the highest ordinal whose slot was ever written, a write in progress included, by reading the
         * created chunks from the last one down until one holds a written slot: in practice the last chunk
         * only. Not the end of the last chunk, which near the top of the ordinal space would claim an ordinal,
         * and so a dense bound, that no entity has. A recreated chunk reads as zeros and holds none; the
         * repair that rewrites it raises the value again.
         */
        private int highestWrittenOrdinal(final Chunks table)
        {
            for(int chunk = table.segments.length - 1; chunk >= 0; chunk--)
            {
                final MemorySegment segment = table.segments[chunk];
                if(segment == null)
                {
                    continue;
                }
                for(int slot = this.slotsPerChunk - 1; slot >= 0; slot--)
                {
                    final long offset = (long)slot * this.stride;
                    if((long)LONG.get(segment, offset + SLOT_SEQ) != 0L
                        || (long)LONG.get(segment, offset + SLOT_EPOCH) != 0L)
                    {
                        return (int)((long)chunk * this.slotsPerChunk + slot);
                    }
                }
            }
            return -1;
        }

        @Override
        public int dimension()
        {
            return this.dimension;
        }

        @Override
        public int slotsPerChunk()
        {
            return this.slotsPerChunk;
        }

        @Override
        public int highestOrdinal()
        {
            return this.highestOrdinal;
        }

        /**
         * Test seam: the directory of the file.
         *
         * @return the directory
         */
        Path directoryForTest()
        {
            return this.directory;
        }

        @Override
        public boolean hasChunk(final int chunk)
        {
            this.ensureOpen();
            final MemorySegment[] segments = this.chunks.segments;
            return chunk >= 0 && chunk < segments.length && segments[chunk] != null;
        }

        @Override
        public long nonce()
        {
            return this.header.nonce;
        }

        @Override
        public Recovery recovery()
        {
            return this.recovery;
        }

        private MemorySegment segmentOf(final int ordinal)
        {
            if(ordinal < 0)
            {
                return null;
            }
            final MemorySegment[] segments = this.chunks.segments;
            final int             chunk    = ordinal / this.slotsPerChunk;
            return chunk < segments.length ? segments[chunk] : null;
        }

        private long offsetOf(final int ordinal)
        {
            return (long)(ordinal % this.slotsPerChunk) * this.stride;
        }

        @Override
        public boolean read(
            final int     ordinal,
            final float[] target ,
            final int     offset
        )
        {
            this.ensureOpen();
            final LongAdder reads = this.readCountTestSeam;
            if(reads != null)
            {
                reads.increment();
            }
            final MemorySegment segment = this.segmentOf(ordinal);
            if(segment == null)
            {
                return false;
            }
            final long slot = this.offsetOf(ordinal);
            try
            {
                while(true)
                {
                    final long before = (long)LONG.getAcquire(segment, slot + SLOT_SEQ);
                    if((before & 1L) != 0L)
                    {
                        Thread.onSpinWait();
                        continue;
                    }
                    final long flags = (long)LONG.get(segment, slot + SLOT_EPOCH);
                    final boolean present = (flags & FLAG_PRESENT) != 0L;
                    if(present)
                    {
                        MemorySegment.copy(
                            segment, ValueLayout.JAVA_FLOAT, slot + SLOT_FLOATS, target, offset, this.dimension
                        );
                    }
                    VarHandle.loadLoadFence();
                    if((long)LONG.getOpaque(segment, slot + SLOT_SEQ) == before)
                    {
                        return present;
                    }
                }
            }
            catch(final IllegalStateException e)
            {
                throw this.closedException(e);
            }
        }

        private void ensureOpen()
        {
            if(this.closed)
            {
                throw this.closedException(null);
            }
        }

        private IllegalStateException closedException(final IllegalStateException cause)
        {
            if(!this.closed && cause != null)
            {
                return cause;
            }
            return new IllegalStateException(
                "The vectors of " + this.description + " are closed: the index is closed", cause
            );
        }

        private long flagsOf(final int ordinal)
        {
            final MemorySegment segment = this.segmentOf(ordinal);
            return segment == null ? 0L : (long)LONG.get(segment, this.offsetOf(ordinal) + SLOT_EPOCH);
        }

        @Override
        public boolean isPresent(final int ordinal)
        {
            this.ensureOpen();
            final LongAdder probes = this.probeCountTestSeam;
            if(probes != null)
            {
                probes.increment();
            }
            return (this.flagsOf(ordinal) & FLAG_PRESENT) != 0L;
        }

        @Override
        public long key(final int ordinal)
        {
            this.ensureOpen();
            final MemorySegment segment = this.segmentOf(ordinal);
            if(segment == null)
            {
                return Vectorizer.NO_KEY;
            }
            final long offset = this.offsetOf(ordinal);
            if((long)LONG.get(segment, offset + SLOT_EPOCH) == 0L)
            {
                return Vectorizer.NO_KEY;
            }
            return (long)LONG.get(segment, offset + SLOT_KEY);
        }

        @Override
        public boolean holds(
            final int     ordinal,
            final float[] vector
        )
        {
            this.ensureOpen();
            final MemorySegment segment = this.segmentOf(ordinal);
            if(segment == null || vector == null || vector.length != this.dimension)
            {
                return false;
            }
            final long offset = this.offsetOf(ordinal);
            if(((long)LONG.get(segment, offset + SLOT_EPOCH) & FLAG_PRESENT) == 0L)
            {
                return false;
            }
            for(int i = 0; i < this.dimension; i++)
            {
                final float stored = segment.get(ValueLayout.JAVA_FLOAT, offset + SLOT_FLOATS + 4L * i);
                if(Float.floatToIntBits(stored) != Float.floatToIntBits(vector[i]))
                {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean needsRepair(final int ordinal)
        {
            this.ensureOpen();
            final MemorySegment segment = this.segmentOf(ordinal);
            if(segment == null)
            {
                return false;
            }
            final long offset = this.offsetOf(ordinal);
            final long seq    = (long)LONG.get(segment, offset + SLOT_SEQ);
            final long epoch  = (long)LONG.get(segment, offset + SLOT_EPOCH) >>> EPOCH_SHIFT;
            return (seq & 1L) != 0L || epoch > this.storeEpoch;
        }



        ///////////////////////////////////////////////////////////////////////////
        // writing //
        ////////////

        @Override
        public void write(
            final int     ordinal,
            final float[] vector ,
            final long    key
        )
        {
            this.writeAt(ordinal, vector, key, this.writeEpoch);
            this.writtenSinceSeal = true;
        }

        @Override
        public void repair(
            final int     ordinal,
            final float[] vector ,
            final long    key
        )
        {
            this.markUncleanBeforeRepair();
            this.writeAt(ordinal, vector, key, this.storeEpoch);
        }

        /**
         * A clean header makes the open only scan the hot chunks, which is right while nothing has written since
         * the clean close. A repair writes into such a chunk through the fast path, unforced until the next
         * checkpoint; a power loss before it could leave a slot whose seq and epoch reached the disk but not its
         * floats, and a header still promising that a scan finds every damaged slot. So the first repair writes
         * the header unclean, forced, before it writes anything.
         */
        private void markUncleanBeforeRepair()
        {
            if(this.repairMarkedUnclean || this.temporary)
            {
                return;
            }
            synchronized(this.headerLock)
            {
                // A file not materialized yet has no header on disk; its first chunk writes one, unclean.
                if(this.materialized && this.header.clean)
                {
                    this.header.clean = false;
                    this.writeHeader(false);
                }
                this.repairMarkedUnclean = true;
            }
        }

        private void writeAt(
            final int     ordinal,
            final float[] vector ,
            final long    key    ,
            final long    epoch
        )
        {
            this.ensureOpen();
            if(ordinal < 0)
            {
                throw new IllegalArgumentException("Negative ordinal " + ordinal);
            }
            if(vector != null && vector.length != this.dimension)
            {
                throw new IllegalArgumentException(
                    "Vector of dimension " + vector.length + ", expected " + this.dimension
                );
            }
            // Before the write: a checkpoint racing it must not take the file for unwritten.
            this.writtenSinceOpen = true;
            final int           chunk   = ordinal / this.slotsPerChunk;
            final MemorySegment segment = this.hotChunk(chunk, epoch);
            final long          offset  = this.offsetOf(ordinal);
            final long          flags   = epoch << EPOCH_SHIFT | (vector != null ? FLAG_PRESENT : FLAG_TOMBSTONE);
            final long          seq     = (long)LONG.get(segment, offset + SLOT_SEQ) & ~1L;
            final boolean       before  = ((long)LONG.get(segment, offset + SLOT_EPOCH) & FLAG_PRESENT) != 0L;
            if(before != (vector != null))
            {
                // Counted before the write, by the only writer: a count one too high for the moment of the write
                // keeps a chunk only longer, never drops one that holds a vector.
                this.chunks.present.addAndGet(chunk, vector != null ? 1 : -1);
            }
            try
            {
                LONG.setOpaque(segment, offset + SLOT_SEQ, seq + 1L);
                VarHandle.storeStoreFence();
                if(vector != null)
                {
                    MemorySegment.copy(
                        vector, 0, segment, ValueLayout.JAVA_FLOAT, offset + SLOT_FLOATS, this.dimension
                    );
                }
                LONG.set(segment, offset + SLOT_EPOCH, flags);
                LONG.set(segment, offset + SLOT_KEY  , key  );
            }
            catch(final IllegalStateException e)
            {
                throw this.closedException(e);
            }
            finally
            {
                // Even again whatever happened, or every reader of the slot would spin forever.
                if(!this.closed)
                {
                    LONG.setRelease(segment, offset + SLOT_SEQ, seq + 2L);
                }
            }
            if(ordinal > this.highestOrdinal)
            {
                this.highestOrdinal = ordinal;
            }
        }

        /**
         * Returns the mapped chunk, making it hot first if it is cold: its bit is set and the header forced before
         * the caller writes into it. The last write epoch is published before the hot flag is read, and a checkpoint
         * clears the flag before it reads the epoch, so of the two one always sees the other.
         */
        private MemorySegment hotChunk(
            final int  chunk,
            final long epoch
        )
        {
            final Chunks current = this.chunks;
            if(chunk < current.segments.length && current.segments[chunk] != null)
            {
                current.lastWriteEpoch.set(chunk, Math.max(epoch, current.lastWriteEpoch.get(chunk)));
                if(current.hot.get(chunk) != 0)
                {
                    return current.segments[chunk];
                }
            }
            synchronized(this.headerLock)
            {
                Chunks table = this.chunks;
                if(chunk >= table.segments.length)
                {
                    table = table.grownTo(Math.toIntExact((long)chunk + 1L));
                }
                if(table.segments[chunk] == null)
                {
                    try
                    {
                        table = table.with(chunk, this.mapChunk(chunk, true));
                    }
                    catch(final IOException e)
                    {
                        throw new IORuntimeException(e);
                    }
                    this.header.created.set(chunk);
                }
                if(table != this.chunks)
                {
                    // Publishes a grown table or a new chunk to the readers in one volatile write.
                    this.chunks = table;
                }
                table.lastWriteEpoch.set(chunk, Math.max(epoch, table.lastWriteEpoch.get(chunk)));
                if(table.hot.get(chunk) == 0 && this.temporary)
                {
                    // Nothing to recover from: a temporary file is deleted on close and rebuilt on every load, so
                    // the chunk only needs to take the fast path from now on, without a forced header.
                    table.hot.set(chunk, 1);
                }
                if(table.hot.get(chunk) == 0)
                {
                    this.header.hot.set(chunk);
                    this.header.clean        = false;
                    if(!this.recovering)
                    {
                        // Only once recovery has completed: before, the header's boot id still decides how much
                        // the next open repairs (an older boot or none means every hot chunk in full), and a
                        // recovery cut short must leave that decision as it found it.
                        this.header.bootId = currentBootId();
                    }
                    // The epoch of the write that makes the chunk hot, which a repair at the store epoch keeps
                    // below the write epoch: the header must not claim an epoch no slot holds.
                    this.header.highestEpoch = Math.max(this.header.highestEpoch, epoch);
                    this.writeHeader(false);
                    // Only once the header is durable: if its write fails, the chunk stays cold in the table, so
                    // a retried write takes this path again instead of writing into a chunk the header on disk
                    // does not list as hot. The bit set in the header above is harmless meanwhile: it only makes
                    // the next header written more conservative.
                    table.hot.set(chunk, 1);
                }
                return table.segments[chunk];
            }
        }

        @Override
        public void repaired(final int chunk)
        {
            // A full rebuild reports the chunk of every entity, also of one without a vector, whose chunk may
            // never have been created: beyond the table, or a gap in it.
            final MemorySegment[] segments = this.chunks.segments;
            final MemorySegment   segment  = chunk >= 0 && chunk < segments.length ? segments[chunk] : null;
            if(segment != null && !this.temporary)
            {
                segment.force();
                this.ioCount.incrementAndGet();
            }
        }

        @Override
        public void recovered()
        {
            // The counts the header has for the chunks recovery repaired predate what it rewrote in them.
            final Recovery done = this.recovery;
            if(done != null && !done.isEmpty())
            {
                // A recovery that had chunks to scan or repair settles them even if it wrote no slot, and the
                // header has to say so, or every later open scans them again. Only a session that uses the
                // index recovers, so an index that is merely loaded and closed still leaves its files alone.
                this.writtenSinceOpen = true;
            }
            if(done != null)
            {
                for(final int[] chunks : new int[][]{done.scanChunks(), done.repairChunks()})
                {
                    for(final int chunk : chunks)
                    {
                        this.recount(chunk);
                    }
                }
            }
            this.recovering = false;
            this.checkpoint();
        }

        private void recount(final int chunk)
        {
            final Chunks table = this.chunks;
            if(chunk >= table.segments.length || table.segments[chunk] == null)
            {
                return;
            }
            int        count = 0;
            final long first = (long)chunk * this.slotsPerChunk;
            for(long position = first; position < first + this.slotsPerChunk; position++)
            {
                if(position <= Integer.MAX_VALUE && this.isPresent((int)position))
                {
                    count++;
                }
            }
            table.present.set(chunk, count);
        }

        @Override
        public long seal()
        {
            if(!this.writtenSinceSeal || this.closed)
            {
                return -1L;
            }
            final long sealed = this.writeEpoch;
            this.storeEpoch       = sealed;
            this.writeEpoch       = sealed + 1L;
            this.writtenSinceSeal = false;
            synchronized(this.headerLock)
            {
                this.uncommittedSeals.add(sealed);
            }
            return sealed;
        }

        @Override
        public void committed(final long epoch)
        {
            synchronized(this.headerLock)
            {
                this.uncommittedSeals.remove(epoch);
                this.committedSeals.add(epoch);
                // The committed epoch advances only over epochs whose stores all committed. Stores commit
                // independently here, not in the order their seals were made: a store that failed, or that
                // commits after a later one, leaves the slots written at its epoch unproven, and trusting them
                // would serve a vector whose entity store never committed. Such an epoch keeps the committed
                // epoch below it until its retry commits it, and the later commits count once it does;
                // meanwhile its chunks stay hot and a restart repairs them, which costs recovery time, never
                // correctness.
                final long ceiling   = this.uncommittedSeals.isEmpty() ? Long.MAX_VALUE : this.uncommittedSeals.first() - 1L;
                final Long reachable = this.committedSeals.floor(ceiling);
                if(reachable != null && reachable > this.committedEpoch)
                {
                    this.committedEpoch = reachable;
                }
                this.committedSeals.headSet(this.committedEpoch, true).clear();
            }
        }

        /**
         * Test seam: the checkpoint interval.
         *
         * @param nanos the interval in nanoseconds
         */
        void checkpointIntervalNanos(final long nanos)
        {
            this.checkpointIntervalNanos = nanos;
        }

        @Override
        public boolean isCheckpointDue()
        {
            return !this.closed
                && !this.recovering
                && !this.temporary
                && System.nanoTime() - this.lastCheckpointNanos >= this.checkpointIntervalNanos
                && this.hasHotChunk()
            ;
        }

        private boolean hasHotChunk()
        {
            final AtomicIntegerArray hot = this.chunks.hot;
            for(int chunk = 0; chunk < hot.length(); chunk++)
            {
                if(hot.get(chunk) != 0)
                {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void checkpoint()
        {
            synchronized(this.forceLock)
            {
                // A temporary file has nothing to make durable: it is deleted on close and rebuilt on every load.
                if(this.closed || this.recovering || this.temporary)
                {
                    return;
                }
                this.checkpointUnderForceLock();
            }
        }

        private void checkpointUnderForceLock()
        {
            this.lastCheckpointNanos = System.nanoTime();
            if(!this.writtenSinceOpen)
            {
                // Nothing written since the open: every chunk is as forced as the header on disk says, and
                // clearing a hot bit only to rewrite that header would change the file of a session that only
                // read it. Keeping the bits costs at most a scan on the next open.
                return;
            }
            // Every write at an epoch below the sampled one has completed: writes and seals both run on the
            // writer, so W only moves past an epoch once its writes are done.
            final long   sampled = this.writeEpoch;
            final Chunks table   = this.chunks;
            final List<Integer> forced = new ArrayList<>();
            for(int chunk = 0; chunk < table.hot.length(); chunk++)
            {
                if(table.hot.get(chunk) != 0 && table.segments[chunk] != null)
                {
                    try
                    {
                        table.segments[chunk].force();
                    }
                    catch(final IllegalStateException e)
                    {
                        throw this.closedException(e);
                    }
                    this.ioCount.incrementAndGet();
                    forced.add(chunk);
                }
            }
            final Runnable hook = this.afterForceTestHook;
            if(hook != null)
            {
                hook.run();
            }
            synchronized(this.headerLock)
            {
                final Chunks current = this.chunks;
                final long   limit   = Math.min(this.committedEpoch, sampled - 1L);
                boolean      changed = this.header.forcedEpoch != sampled - 1L;
                for(final int chunk : forced)
                {
                    current.hot.set(chunk, 0);
                    final long last = current.lastWriteEpoch.get(chunk);
                    if(last > limit)
                    {
                        // Written during the force, or not committed yet: stays hot.
                        current.hot.set(chunk, 1);
                        continue;
                    }
                    this.header.hot.clear(chunk);
                    this.header.coldEpoch = Math.max(this.header.coldEpoch, last);
                    changed = true;
                }
                if(changed && this.materialized)
                {
                    this.header.forcedEpoch  = sampled - 1L;
                    // The highest epoch sealed, not the next one to write: with no chunk hot, no slot holds a
                    // newer one, and the next store seals at least sampled, so a copy of this file restored
                    // behind that store is recognized as behind.
                    this.header.highestEpoch = Math.max(this.header.highestEpoch, sampled - 1L);
                    this.writeHeader(false);
                }
            }
        }

        @Override
        public long ioCount()
        {
            return this.ioCount.get();
        }

        @Override
        public boolean isClosed()
        {
            return this.closed;
        }

        @Override
        public void close()
        {
            if(this.temporary)
            {
                // Nothing to keep: an in-memory index rebuilds its vectors on every load.
                this.closeAndDelete();
                return;
            }
            synchronized(this.forceLock)
            {
                if(this.closed)
                {
                    return;
                }
                try
                {
                    if(!this.recovering)
                    {
                        this.checkpointUnderForceLock();
                    }
                    synchronized(this.headerLock)
                    {
                        // A file that never wrote a vector was never created, and stays that way; one that wrote
                        // nothing since the open is left exactly as the open found it.
                        if(this.materialized && this.writtenSinceOpen)
                        {
                            this.header.clean        = !this.recovering;
                            // As in a checkpoint: writes at the current write epoch were never sealed, and
                            // keep their chunks hot.
                            this.header.highestEpoch = Math.max(this.header.highestEpoch, this.writeEpoch - 1L);
                            this.writeHeader(false);
                        }
                    }
                }
                finally
                {
                    this.closed = true;
                    this.release();
                }
            }
        }

        @Override
        public void closeAndDelete()
        {
            synchronized(this.forceLock)
            {
                this.closed = true;
                this.release();
                try
                {
                    deleteFiles(this.directory, this.name);
                }
                catch(final IOException e)
                {
                    LOG.warn("Could not delete the vector file of {}: {}", this.description, e.toString());
                }
            }
        }

        /**
         * Drops the file like a process that dies, without a checkpoint or a header write: a test seam, and how
         * an open takes over a file that was never closed. What was written stays in the page cache of the
         * files, as it would.
         */
        void crashForTest()
        {
            synchronized(this.forceLock)
            {
                this.release();
            }
        }

        /**
         * Test seam: the epochs, as {@code {E, W, C}}.
         *
         * @return the store, write and committed epochs
         */
        long[] epochsForTest()
        {
            return new long[]{this.storeEpoch, this.writeEpoch, this.committedEpoch};
        }

        /**
         * Test seam: the hot chunks as the header on disk records them.
         *
         * @return the hot bits of the newest valid header record, or {@code null} if there is none
         */
        BitSet hotChunksOnDisk()
        {
            try
            {
                final Header onDisk = Header.read(this.directory.resolve(this.name + HEADER_FILE_EXT));
                return onDisk == null ? null : onDisk.hot;
            }
            catch(final IOException e)
            {
                throw new IORuntimeException(e);
            }
        }

        private void release()
        {
            this.closed = true;
            final ScheduledFuture<?> task = this.checkpointTask;
            if(task != null)
            {
                task.cancel(false);
            }
            this.cleanable.clean();
        }

        @Override
        public void checkpointInterval(final long millis)
        {
            if(millis <= 0L)
            {
                throw new IllegalArgumentException("Checkpoint interval must be positive: " + millis);
            }
            this.checkpointIntervalNanos = TimeUnit.MILLISECONDS.toNanos(millis);
            final ScheduledFuture<?> previous = this.checkpointTask;
            if(previous != null)
            {
                previous.cancel(false);
            }
            // No checkpoints for a temporary file, see checkpoint().
            this.checkpointTask = this.temporary ? null : Checkpoints.schedule(this, millis);
        }

        /**
         * Writes the header into the record slot the previous one does not occupy and forces it, and the
         * directory if a chunk was created since. Under {@code headerLock}, or before the file is published.
         */
        private void writeHeader(final boolean syncDirectory)
        {
            this.header.present = this.chunks.presentCounts();
            final long previousSequence = this.header.recordSeq;
            final int  previousRegion   = this.header.region;
            long       sequence         = previousSequence + 1L;
            final int  region           = Header.regionFor(this.header.encodedLength());
            if(region > this.header.region)
            {
                // A larger record must not overwrite the current one: it goes beyond both old records, into the
                // second slot of the larger layout, which an odd sequence names.
                if((sequence & 1L) == 0L)
                {
                    sequence++;
                }
                this.header.region = region;
            }
            this.header.recordSeq = sequence;
            final ByteBuffer record   = this.header.encode();
            final long       position = (sequence & 1L) * this.header.region;
            try
            {
                final IoHook hook = headerWriteTestHook;
                if(hook != null)
                {
                    hook.run(this.directory.resolve(this.name + HEADER_FILE_EXT));
                }
                if(this.headerChannel == null)
                {
                    Files.createDirectories(this.directory);
                    this.headerChannel = FileChannel.open(
                        this.directory.resolve(this.name + HEADER_FILE_EXT),
                        StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE
                    );
                    this.resources.channel = this.headerChannel;
                    // Or-ed: a chunk created just before this first header write of a reopened file has set it
                    // already, and its directory entry must be synced as much as a new header's.
                    this.createdSinceSync |= !this.materialized;
                    this.materialized      = true;
                }
                while(record.hasRemaining())
                {
                    this.headerChannel.write(record, position + record.position());
                }
                this.headerChannel.force(true);
                this.ioCount.incrementAndGet();
                if(syncDirectory || this.createdSinceSync)
                {
                    SegmentFiles.syncDirectory(this.directory);
                    this.createdSinceSync = false;
                    final LongAdder syncs = this.directorySyncCountTestSeam;
                    if(syncs != null)
                    {
                        syncs.increment();
                    }
                }
            }
            catch(final IOException e)
            {
                // The record slot written to may be torn, the other one still holds the last good record. Back to
                // the sequence before, so a retry writes the same slot again rather than overwrite that one.
                this.header.recordSeq = previousSequence;
                this.header.region    = previousRegion;
                throw new IORuntimeException(e);
            }
        }

        @Override
        public String toString()
        {
            return "VectorFile[" + this.description + ", " + this.directory.resolve(this.name + HEADER_FILE_EXT) + "]";
        }



        ///////////////////////////////////////////////////////////////////////////
        // nested types //
        /////////////////

        /**
         * Test seam for an I/O step.
         */
        @FunctionalInterface
        interface IoHook
        {
            public void run(Path path) throws IOException;
        }

        /**
         * What the cleaner releases, for a closed file and for one dropped without a close: never the file
         * itself, which would keep it reachable.
         */
        private static final class Resources implements Runnable
        {
            private final    Arena                  arena             ;
            private volatile FileChannel            channel           ;
            private volatile Path                   temporaryDirectory;
            private volatile FileChannel            temporaryLock     ;
            private volatile Path                   openKey           ;
            private volatile WeakReference<Default> openRegistration  ;

            Resources(final Arena arena)
            {
                this.arena = arena;
            }

            void register(
                final Path                   key         ,
                final WeakReference<Default> registration
            )
            {
                this.openKey          = key         ;
                this.openRegistration = registration;
            }

            @Override
            public void run()
            {
                // Only this file's own entry: a later open of the same path has replaced it with its own.
                final Path key = this.openKey;
                if(key != null)
                {
                    OPEN.remove(key, this.openRegistration);
                }
                try
                {
                    final FileChannel header = this.channel;
                    if(header != null)
                    {
                        header.close();
                    }
                }
                catch(final IOException e)
                {
                    LOG.warn("Could not close a vector file header: {}", e.toString());
                }
                finally
                {
                    this.arena.close();
                    final Path directory = this.temporaryDirectory;
                    if(directory != null)
                    {
                        deleteDirectory(directory);
                    }
                    // Released last, so no reaper takes the directory while it is still being deleted.
                    final FileChannel lock = this.temporaryLock;
                    if(lock != null)
                    {
                        closeQuietly(lock);
                    }
                }
            }
        }

        /**
         * The thread that runs due checkpoints for every open file, shared so an index needs no thread of
         * its own for it. It holds each file only weakly: a file dropped without a close is cleaned up by the
         * cleaner, and its task ends with it.
         */
        private static final class Checkpoints
        {
            private static volatile ScheduledExecutorService executor;

            static ScheduledFuture<?> schedule(
                final Default file  ,
                final long    millis
            )
            {
                final WeakReference<Default>              reference = new WeakReference<>(file);
                final AtomicReference<ScheduledFuture<?>> self      = new AtomicReference<>();
                final ScheduledFuture<?> future = executor().scheduleWithFixedDelay(
                    () -> run(reference, self),
                    millis,
                    millis,
                    TimeUnit.MILLISECONDS
                );
                self.set(future);
                return future;
            }

            private static void run(
                final WeakReference<Default>              reference,
                final AtomicReference<ScheduledFuture<?>> self
            )
            {
                final Default file = reference.get();
                if(file == null || file.isClosed())
                {
                    final ScheduledFuture<?> future = self.get();
                    if(future != null)
                    {
                        future.cancel(false);
                    }
                    return;
                }
                try
                {
                    if(file.isCheckpointDue())
                    {
                        file.checkpoint();
                    }
                }
                catch(final RuntimeException e)
                {
                    // Retried at the next tick. A failed checkpoint only leaves more chunks to look at after
                    // a crash.
                    LOG.warn("Checkpoint of {} failed: {}", file, e.toString(), e);
                }
            }

            private static ScheduledExecutorService executor()
            {
                ScheduledExecutorService current = executor;
                if(current == null)
                {
                    synchronized(Checkpoints.class)
                    {
                        current = executor;
                        if(current == null)
                        {
                            final ScheduledThreadPoolExecutor created = new ScheduledThreadPoolExecutor(1, runnable ->
                            {
                                final Thread thread = new Thread(runnable, "eclipsestore-vector-checkpoint");
                                thread.setDaemon(true);
                                return thread;
                            });
                            created.setRemoveOnCancelPolicy(true);
                            executor = current = created;
                        }
                    }
                }
                return current;
            }
        }

        /**
         * The chunk table: the mapped chunks, by chunk number, with the last epoch each was written at, whether it
         * is hot, and how many of its slots hold a vector. Replaced by a copy when it grows, under
         * {@code headerLock}. The counts are kept by the writer and persisted with every header write, so that an
         * open can drop a cold chunk that holds no vector without reading it.
         */
        private record Chunks(
            MemorySegment[]    segments      ,
            AtomicLongArray    lastWriteEpoch,
            AtomicIntegerArray hot           ,
            AtomicIntegerArray present
        )
        {
            static final Chunks EMPTY = new Chunks(
                new MemorySegment[0], new AtomicLongArray(0), new AtomicIntegerArray(0), new AtomicIntegerArray(0)
            );

            Chunks grownTo(final int length)
            {
                final long grown = Math.max(4L, this.segments.length + (long)(this.segments.length >> 1));
                final int  size  = (int)Math.max(length, Math.min(grown, Integer.MAX_VALUE - 8L));
                final AtomicLongArray    epochs  = new AtomicLongArray(size);
                final AtomicIntegerArray hot     = new AtomicIntegerArray(size);
                final AtomicIntegerArray present = new AtomicIntegerArray(size);
                for(int chunk = 0; chunk < this.segments.length; chunk++)
                {
                    epochs.set(chunk, this.lastWriteEpoch.get(chunk));
                    hot.set(chunk, this.hot.get(chunk));
                    present.set(chunk, this.present.get(chunk));
                }
                return new Chunks(Arrays.copyOf(this.segments, size), epochs, hot, present);
            }

            Chunks with(
                final int           chunk  ,
                final MemorySegment segment
            )
            {
                // The per-chunk arrays are shared: only the segment array is published anew.
                final MemorySegment[] copy = this.segments.clone();
                copy[chunk] = segment;
                return new Chunks(copy, this.lastWriteEpoch, this.hot, this.present);
            }

            int[] presentCounts()
            {
                final int[] counts = new int[this.present.length()];
                for(int chunk = 0; chunk < counts.length; chunk++)
                {
                    counts[chunk] = this.present.get(chunk);
                }
                return counts;
            }
        }

        /**
         * The header, of which two records alternate in {@code <name>.vec} so a torn write leaves the other one.
         * Little endian, with a CRC32 over the record.
         */
        static final class Header
        {
            int     dimension   ;
            int     stride      ;
            long    chunkBytes  ;
            boolean bigEndian   ;
            long    nonce       ;
            long    forcedEpoch ;
            long    highestEpoch;
            long    coldEpoch   ;
            boolean clean       ;
            UUID    bootId      ;
            long    recordSeq   ;
            int     region      = MIN_REGION;
            BitSet  created     = new BitSet();
            BitSet  hot         = new BitSet();
            // The present slots of each chunk, by chunk number; beyond its length, zero.
            int[]   present     = new int[0];

            static int regionFor(final int length)
            {
                int region = MIN_REGION;
                while(region < length)
                {
                    region <<= 1;
                }
                return region;
            }

            int encodedLength()
            {
                return lengthFor(this.words());
            }

            // Per word of 64 chunks: a word of each bit set and 64 counts.
            private static int lengthFor(final int words)
            {
                return RECORD_FIXED + (16 + 4 * Long.SIZE) * words + 4;
            }

            private int words()
            {
                return Math.max(this.created.toLongArray().length, this.hot.toLongArray().length);
            }

            ByteBuffer encode()
            {
                final long[] createdWords = this.created.toLongArray();
                final long[] hotWords     = this.hot.toLongArray();
                final int    words        = Math.max(createdWords.length, hotWords.length);
                final int    length       = lengthFor(words);
                final ByteBuffer buffer   = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
                buffer.putInt (MAGIC);
                buffer.putInt (VERSION);
                buffer.putInt (this.dimension);
                buffer.putInt (this.stride);
                buffer.putLong(this.chunkBytes);
                buffer.putLong(this.nonce);
                buffer.putLong(this.forcedEpoch);
                buffer.putLong(this.highestEpoch);
                buffer.putLong(this.coldEpoch);
                buffer.putLong(this.bootId != null ? this.bootId.getMostSignificantBits()  : 0L);
                buffer.putLong(this.bootId != null ? this.bootId.getLeastSignificantBits() : 0L);
                buffer.putInt (this.clean ? 1 : 0);
                buffer.putInt (this.bigEndian ? 1 : 0);
                buffer.putLong(this.recordSeq);
                buffer.putInt (this.region);
                buffer.putInt (words);
                buffer.putLong(0L); // reserved
                for(int i = 0; i < words; i++)
                {
                    buffer.putLong(i < createdWords.length ? createdWords[i] : 0L);
                }
                for(int i = 0; i < words; i++)
                {
                    buffer.putLong(i < hotWords.length ? hotWords[i] : 0L);
                }
                for(int chunk = 0; chunk < words * Long.SIZE; chunk++)
                {
                    buffer.putInt(chunk < this.present.length ? this.present[chunk] : 0);
                }
                final CRC32 crc = new CRC32();
                crc.update(buffer.array(), 0, length - 4);
                buffer.putInt((int)crc.getValue());
                buffer.flip();
                return buffer;
            }

            static Header decode(final ByteBuffer buffer)
            {
                if(buffer.remaining() < RECORD_FIXED + 4)
                {
                    return null;
                }
                final int start = buffer.position();
                if(buffer.getInt(start) != MAGIC || buffer.getInt(start + 4) != VERSION)
                {
                    return null;
                }
                final int words = buffer.getInt(start + 92);
                if(words < 0 || words > (buffer.remaining() - RECORD_FIXED - 4) / (16 + 4 * Long.SIZE))
                {
                    return null;
                }
                final int   length = lengthFor(words);
                final CRC32 crc    = new CRC32();
                crc.update(buffer.array(), buffer.arrayOffset() + start, length - 4);
                if(buffer.getInt(start + length - 4) != (int)crc.getValue())
                {
                    return null;
                }
                final Header header = new Header();
                final ByteBuffer in = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
                in.position(start + 8);
                header.dimension    = in.getInt();
                header.stride       = in.getInt();
                header.chunkBytes   = in.getLong();
                header.nonce        = in.getLong();
                header.forcedEpoch  = in.getLong();
                header.highestEpoch = in.getLong();
                header.coldEpoch    = in.getLong();
                final long most     = in.getLong();
                final long least    = in.getLong();
                header.bootId       = most == 0L && least == 0L ? null : new UUID(most, least);
                header.clean        = in.getInt() != 0;
                header.bigEndian    = in.getInt() != 0;
                header.recordSeq    = in.getLong();
                header.region       = in.getInt();
                in.getInt(); // words
                in.getLong(); // reserved
                final long[] createdWords = new long[words];
                final long[] hotWords     = new long[words];
                for(int i = 0; i < words; i++)
                {
                    createdWords[i] = in.getLong();
                }
                for(int i = 0; i < words; i++)
                {
                    hotWords[i] = in.getLong();
                }
                header.created = BitSet.valueOf(createdWords);
                header.hot     = BitSet.valueOf(hotWords);
                header.present = new int[words * Long.SIZE];
                for(int chunk = 0; chunk < header.present.length; chunk++)
                {
                    header.present[chunk] = in.getInt();
                }
                return header;
            }

            /**
             * Reads the newest valid record: the one at offset 0 and those at every power of two from
             * {@code MIN_REGION} below the file size, where the second record of each layout lives.
             */
            static Header read(final Path path) throws IOException
            {
                final byte[] bytes;
                try
                {
                    bytes = Files.readAllBytes(path);
                }
                catch(final NoSuchFileException e)
                {
                    return null;
                }
                final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                Header best = decodeAt(buffer, 0);
                for(long offset = MIN_REGION; offset < bytes.length; offset <<= 1)
                {
                    final Header candidate = decodeAt(buffer, (int)offset);
                    if(candidate != null && (best == null || candidate.recordSeq > best.recordSeq))
                    {
                        best = candidate;
                    }
                }
                return best;
            }

            private static Header decodeAt(
                final ByteBuffer buffer,
                final int        offset
            )
            {
                if(offset >= buffer.capacity())
                {
                    return null;
                }
                final ByteBuffer view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
                view.position(offset);
                return decode(view);
            }
        }

    }

}
