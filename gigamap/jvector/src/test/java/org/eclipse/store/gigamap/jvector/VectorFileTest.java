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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link VectorFile}: the seqlocked slots, the hot-chunk bits and checkpoints, and what an open finds
 * after a clean close, a process crash, a reboot or a file that does not belong to the store.
 */
class VectorFileTest
{
    // dimension 4: a slot of 64 bytes, four slots per chunk
    private static final int  DIMENSION   = 4;
    private static final long CHUNK_BYTES = 256L;
    private static final UUID BOOT_A      = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOOT_B      = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @TempDir
    Path directory;

    @AfterEach
    void restoreBootId()
    {
        VectorFile.Default.bootIdSource(null);
        VectorFile.Default.chunkCreationTestHook = null;
        VectorFile.Default.headerWriteTestHook   = null;
    }

    private VectorFile.Default open(
        final long nonce     ,
        final long storeEpoch
    )
    {
        return VectorFile.Default.open(this.directory, "idx", DIMENSION, CHUNK_BYTES, nonce, storeEpoch, "test");
    }

    private VectorFile.Default fresh()
    {
        final VectorFile.Default file = this.open(0L, 0L);
        file.recovered();
        return file;
    }

    private static float[] vector(final float value)
    {
        return new float[]{value, value + 1f, value + 2f, value + 3f};
    }

    /**
     * Writes, seals and commits like a store would, returning the sealed epoch.
     */
    private static long store(final VectorFile file)
    {
        final long epoch = file.seal();
        file.committed(epoch);
        return epoch;
    }

    @Test
    void write_thenRead_roundTrips()
    {
        try(final VectorFile file = this.fresh())
        {
            file.write(2, vector(1f), 42L);

            assertArrayEquals(vector(1f), file.read(2));
            assertTrue(file.isPresent(2));
            assertEquals(42L, file.key(2));
            assertTrue(file.holds(2, vector(1f)));
            assertFalse(file.holds(2, vector(2f)));

            file.write(2, null, 43L);
            assertNull(file.read(2));
            assertFalse(file.isPresent(2));
            assertEquals(43L, file.key(2), "a tombstone keeps its key");
        }
    }

    @Test
    void forEachPresent_sparseChunks_visitsPresentOrdinalsOfCreatedChunksOnly()
    {
        try(final VectorFile.Default file = this.fresh())
        {
            file.write(1, vector(1f), Vectorizer.NO_KEY);
            file.write(2, null, 7L);
            file.write(41, vector(41f), Vectorizer.NO_KEY);
            final LongAdder reads = new LongAdder();
            file.readCountTestSeam = reads;
            final List<Integer> slots   = new ArrayList<>();
            final List<Integer> present = new ArrayList<>();

            assertTrue(file.forEachSlot(ordinal -> slots.add(ordinal)));
            assertTrue(file.forEachPresent(ordinal -> present.add(ordinal)));

            // Chunks 0 and 10 only, of 4 slots each, up to the highest ordinal written.
            assertEquals(List.of(0, 1, 2, 3, 40, 41), slots);
            assertEquals(List.of(1, 41), present, "a tombstone holds no vector");
            assertEquals(0L, reads.sum(), "presence is checked without reading a vector");
            assertFalse(file.forEachPresent(ordinal -> false), "a visitor stops the scan");
        }
    }

    @Test
    void write_beyondChunk_growsTableAndOtherOrdinalsReadAbsent()
    {
        try(final VectorFile file = this.fresh())
        {
            file.write(1, vector(1f), Vectorizer.NO_KEY);
            file.write(41, vector(41f), Vectorizer.NO_KEY);

            assertArrayEquals(vector(41f), file.read(41));
            assertNull(file.read(40));
            assertNull(file.read(17), "a chunk in between is never created");
            assertFalse(Files.exists(this.directory.resolve("idx.vec-4")));
            assertTrue(Files.exists(this.directory.resolve("idx.vec-10")));
            assertEquals(Vectorizer.NO_KEY, file.key(17));
            assertTrue(file.highestOrdinal() >= 41);
            assertFalse(file.hasChunk(4), "a chunk never written to does not exist");
            assertTrue(file.hasChunk(10));
            assertFalse(file.hasChunk(11), "beyond the table");
            assertFalse(file.hasChunk(-1));
        }
    }

    @Test
    void read_concurrentOverwrite_neverReturnsATornVector() throws InterruptedException
    {
        try(final VectorFile file = this.fresh())
        {
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            final AtomicBoolean            stop = new AtomicBoolean();
            final AtomicReference<float[]> torn = new AtomicReference<>();
            final Thread reader = new Thread(() ->
            {
                final float[] target = new float[DIMENSION];
                while(!stop.get())
                {
                    file.read(0, target, 0);
                    for(int i = 1; i < DIMENSION; i++)
                    {
                        if(target[i] != target[0] + i)
                        {
                            torn.set(target.clone());
                            return;
                        }
                    }
                }
            });
            reader.start();
            for(int round = 0; round < 200_000; round++)
            {
                file.write(0, vector(round), Vectorizer.NO_KEY);
            }
            stop.set(true);
            reader.join();

            assertNull(torn.get(), () -> "torn read " + Arrays.toString(torn.get()));
        }
    }

    @Test
    void write_firstIntoColdChunk_setsHotBitBeforeData()
    {
        try(final VectorFile.Default file = this.fresh())
        {
            final long before = file.ioCount();
            file.write(5, vector(5f), Vectorizer.NO_KEY);

            assertTrue(file.hotChunksOnDisk().get(1), "the header on disk names the chunk hot");
            final long afterFirst = file.ioCount();
            assertTrue(afterFirst > before);

            file.write(6, vector(6f), Vectorizer.NO_KEY);
            assertEquals(afterFirst, file.ioCount(), "a write into a hot chunk does no I/O");
        }
    }

    @Test
    void checkpoint_clearsOnlyChunksForcedAndCommitted()
    {
        try(final VectorFile.Default file = this.fresh())
        {
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            store(file);
            file.write(4, vector(4f), Vectorizer.NO_KEY);
            file.seal(); // stored, but not committed

            file.checkpoint();

            final BitSet hot = file.hotChunksOnDisk();
            assertFalse(hot.get(0), "forced and committed");
            assertTrue(hot.get(1), "not committed yet");
        }
    }

    @Test
    void checkpoint_chunkWrittenDuringMsync_staysHot()
    {
        try(final VectorFile.Default file = this.fresh())
        {
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            store(file);
            file.afterForceTestHook = () ->
            {
                file.write(1, vector(1f), Vectorizer.NO_KEY);
                file.committed(file.seal());
            };

            file.checkpoint();
            file.afterForceTestHook = null;

            assertTrue(file.hotChunksOnDisk().get(0), "written after the force");
            file.checkpoint();
            assertFalse(file.hotChunksOnDisk().get(0));
        }
    }

    @Test
    void open_sameBoot_epochScansOnlyHotChunks()
    {
        VectorFile.Default.bootIdSource(() -> BOOT_A);
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            epoch = store(file);
            file.checkpoint();
            file.write(4, vector(4f), Vectorizer.NO_KEY);
            file.crashForTest();
        }

        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            final VectorFile.Recovery recovery = reopened.recovery();
            assertFalse(recovery.fullRebuild());
            assertArrayEquals(new int[]{1}, recovery.scanChunks());
            assertArrayEquals(new int[0], recovery.repairChunks());
            assertTrue(reopened.needsRepair(4), "written after the store");
            assertFalse(reopened.needsRepair(5));
            assertArrayEquals(vector(0f), reopened.read(0), "a cold chunk is trusted");
        }
    }

    @Test
    void open_otherBoot_fullyRepairsHotChunks()
    {
        VectorFile.Default.bootIdSource(() -> BOOT_A);
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            epoch = store(file);
            file.checkpoint();
            file.write(4, vector(4f), Vectorizer.NO_KEY);
            file.crashForTest();
        }

        VectorFile.Default.bootIdSource(() -> BOOT_B);
        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            final VectorFile.Recovery recovery = reopened.recovery();
            assertArrayEquals(new int[0], recovery.scanChunks());
            assertArrayEquals(new int[]{1}, recovery.repairChunks());
        }
    }

    @Test
    void open_unknownBoot_takesRebootPath()
    {
        VectorFile.Default.bootIdSource(() -> null);
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(4, vector(4f), Vectorizer.NO_KEY);
            epoch = store(file);
            file.crashForTest();
        }

        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            assertArrayEquals(new int[]{1}, reopened.recovery().repairChunks());
        }
    }

    @Test
    void open_slotNewerThanStoreEpoch_isRepaired()
    {
        VectorFile.Default.bootIdSource(() -> BOOT_A);
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            epoch = store(file);
            file.write(1, vector(1f), Vectorizer.NO_KEY);
            file.crashForTest();
        }

        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            assertArrayEquals(new int[]{0}, reopened.recovery().scanChunks());
            assertFalse(reopened.needsRepair(0));
            assertTrue(reopened.needsRepair(1));

            reopened.repair(1, null, Vectorizer.NO_KEY);
            reopened.repaired(0);
            reopened.recovered();

            assertFalse(reopened.needsRepair(1));
            assertFalse(reopened.hotChunksOnDisk().get(0), "repaired and forced chunks turn cold");
        }
    }

    @Test
    void open_nonceMismatch_requestsFullRebuild()
    {
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            epoch = store(file);
        }

        try(final VectorFile.Default reopened = this.open(12345L, epoch))
        {
            assertTrue(reopened.recovery().fullRebuild());
            assertNull(reopened.read(0), "a fresh file");
        }
    }

    @Test
    void open_headerBehindStoreNoHotChunk_requestsFullRebuild()
    {
        final long nonce;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            store(file);
        }

        try(final VectorFile.Default reopened = this.open(nonce, 1_000L))
        {
            assertTrue(reopened.recovery().fullRebuild());
        }
    }

    /**
     * A cold file copied after store N, restored after store N+1 committed: no hot chunk explains the lag, so the
     * header must say it holds nothing newer than N.
     */
    @Test
    void open_fileExactlyOneStoreBehind_requestsFullRebuild() throws IOException
    {
        final long nonce;
        final long epoch;
        final Map<Path, byte[]> backup = new HashMap<>();
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            store(file);
            file.checkpoint();
            try(final Stream<Path> files = Files.list(this.directory))
            {
                for(final Path path : (Iterable<Path>)files::iterator)
                {
                    backup.put(path, Files.readAllBytes(path));
                }
            }

            file.write(0, vector(9f), Vectorizer.NO_KEY);
            epoch = store(file);
            file.checkpoint();
        }
        for(final Map.Entry<Path, byte[]> entry : backup.entrySet())
        {
            Files.write(entry.getKey(), entry.getValue());
        }

        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            assertTrue(reopened.recovery().fullRebuild(), "a file one store behind must not be trusted");
        }
    }

    /**
     * A full rebuild repairs at the store epoch, so the rebuilt file holds nothing newer than it: a copy taken
     * then and restored after the next store committed is one store behind.
     */
    @Test
    void open_rebuiltFileOneStoreBehind_requestsFullRebuild() throws IOException
    {
        final long nonce;
        final long epoch;
        final Map<Path, byte[]> backup = new HashMap<>();
        try(final VectorFile.Default rebuilt = this.open(0L, 5L))
        {
            assertTrue(rebuilt.recovery().fullRebuild());
            nonce = rebuilt.nonce();
            rebuilt.repair(0, vector(0f), Vectorizer.NO_KEY);
            rebuilt.repaired(0);
            rebuilt.recovered();
            try(final Stream<Path> files = Files.list(this.directory))
            {
                for(final Path path : (Iterable<Path>)files::iterator)
                {
                    backup.put(path, Files.readAllBytes(path));
                }
            }

            rebuilt.write(0, vector(9f), Vectorizer.NO_KEY);
            epoch = store(rebuilt);
            rebuilt.checkpoint();
        }
        for(final Map.Entry<Path, byte[]> entry : backup.entrySet())
        {
            Files.write(entry.getKey(), entry.getValue());
        }

        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            assertTrue(reopened.recovery().fullRebuild(), "a rebuilt file one store behind must not be trusted");
        }
    }

    @Test
    void open_headerBehindStoreWithHotChunk_isLegitimate()
    {
        VectorFile.Default.bootIdSource(() -> BOOT_A);
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            store(file);
            // Writes into the hot chunk do not rewrite the header, so it lags the seals.
            file.write(1, vector(1f), Vectorizer.NO_KEY);
            store(file);
            file.write(2, vector(2f), Vectorizer.NO_KEY);
            epoch = store(file);
            file.crashForTest();
        }

        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            assertFalse(reopened.recovery().fullRebuild());
            assertArrayEquals(new int[]{0}, reopened.recovery().scanChunks());
            assertArrayEquals(vector(2f), reopened.read(2));
        }
    }

    @Test
    void close_uncommittedWrite_chunkStaysHotInCleanFile()
    {
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            epoch = store(file);
            file.write(4, vector(4f), Vectorizer.NO_KEY);
        }

        // Another boot, but the file is clean, so its pages were forced: a scan is enough.
        VectorFile.Default.bootIdSource(() -> BOOT_B);
        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            assertArrayEquals(new int[]{1}, reopened.recovery().scanChunks());
            assertArrayEquals(new int[0], reopened.recovery().repairChunks());
        }
    }

    @Test
    void close_seals_reopenScansNothing()
    {
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            file.write(9, vector(9f), Vectorizer.NO_KEY);
            epoch = store(file);
        }

        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            assertTrue(reopened.recovery().isEmpty());
            assertArrayEquals(vector(9f), reopened.read(9));
            assertEquals(9, reopened.highestOrdinal(), "the highest written slot, not the end of its chunk");
        }
    }

    /**
     * A session that only reads leaves the file as it found it, byte for byte: checkpoint and close write no
     * header when nothing was written since the open. An index is opened at load, before anyone can tell
     * whether it will be used, so a store that merely opens and closes an index - a copied store re-registering
     * an index whose persisted directory still names the original deployment's directory - must not rewrite that file.
     */
    @Test
    void close_withoutWritesSinceOpen_leavesEveryFileByteForByte() throws IOException
    {
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            file.write(9, vector(9f), Vectorizer.NO_KEY);
            epoch = store(file);
        }
        final Map<String, String> before = this.digests();

        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            reopened.recovered();
            assertArrayEquals(vector(9f), reopened.read(9));
            reopened.checkpoint();
        }

        assertEquals(before, this.digests(), "a session that only read must not rewrite the vector file");
    }

    /**
     * Skipping the header write on a close without writes must not hide anything the next open still has to
     * recover: a chunk left hot by an uncommitted write stays hot, because the header on disk already says so.
     */
    @Test
    void close_withoutWritesSinceOpen_nextOpenStillScansTheHotChunk()
    {
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            epoch = store(file);
            file.write(4, vector(4f), Vectorizer.NO_KEY);
        }
        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            assertArrayEquals(new int[]{1}, reopened.recovery().scanChunks());
        }

        try(final VectorFile.Default again = this.open(nonce, epoch))
        {
            final boolean recovered = Arrays.stream(again.recovery().scanChunks()).anyMatch(chunk -> chunk == 1)
                || Arrays.stream(again.recovery().repairChunks()).anyMatch(chunk -> chunk == 1);
            assertTrue(recovered, "the hot chunk must still be recovered on the next open");
        }
    }

    /**
     * A recovery that scanned a hot chunk and found nothing to repair still settles it: the next open has
     * nothing left to scan. A session that recovers uses the file, so its checkpoint and close write as usual,
     * although it wrote no slot - otherwise every later open would scan the same chunks again.
     */
    @Test
    void recovered_afterAScanThatRepairedNothing_nextOpenHasNothingToScan()
    {
        VectorFile.Default.bootIdSource(() -> BOOT_A);
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            epoch = store(file);
            file.crashForTest();
        }
        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            assertArrayEquals(new int[]{0}, reopened.recovery().scanChunks());
            assertFalse(reopened.needsRepair(0), "committed before the crash");
            reopened.recovered();
        }

        try(final VectorFile.Default again = this.open(nonce, epoch))
        {
            assertTrue(again.recovery().isEmpty(), "the scan settled the chunk");
        }
    }

    /**
     * After a write the close still seals the header as before.
     */
    @Test
    void close_afterAWriteSinceOpen_stillRewritesTheHeader() throws IOException
    {
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            epoch = store(file);
        }
        final Map<String, String> before = this.digests();

        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            reopened.recovered();
            reopened.write(1, vector(1f), Vectorizer.NO_KEY);
            store(reopened);
        }

        assertNotEquals(before.get("idx.vec"), this.digests().get("idx.vec"));
    }

    /**
     * Every file in the test directory with a digest of its contents, by file name.
     */
    private Map<String, String> digests() throws IOException
    {
        final Map<String, String> digests = new TreeMap<>();
        try(final Stream<Path> files = Files.list(this.directory))
        {
            for(final Path file : (Iterable<Path>)files::iterator)
            {
                digests.put(file.getFileName().toString(), Arrays.toString(sha256(Files.readAllBytes(file))));
            }
        }
        return digests;
    }

    private static byte[] sha256(final byte[] bytes)
    {
        try
        {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        }
        catch(final NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    /**
     * A chunk created by the first write after a reopen is listed by the header that write forces, so its
     * directory entry must be synced with it, or an OS crash can lose the file the header names.
     */
    @Test
    void write_newChunkAfterReopen_syncsTheDirectory()
    {
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            epoch = store(file);
        }

        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            reopened.recovered();
            final LongAdder syncs = new LongAdder();
            reopened.directorySyncCountTestSeam = syncs;

            reopened.write(9, vector(9f), Vectorizer.NO_KEY);

            assertEquals(1L, syncs.sum(), "the new chunk's directory entry is synced");
        }
    }

    /**
     * Temporary directories are unique even where a pid and a counter are not, and one in use is never taken for
     * abandoned, also not by another class loader of the same process.
     */
    @Test
    void openTemporary_twice_uniqueLockedDirectoriesDeletedOnClose()
    {
        final Path first;
        final Path second;
        try(
            final VectorFile.Default a = VectorFile.Default.openTemporary("idx", DIMENSION, CHUNK_BYTES, "a");
            final VectorFile.Default b = VectorFile.Default.openTemporary("idx", DIMENSION, CHUNK_BYTES, "b")
        )
        {
            first  = a.directoryForTest();
            second = b.directoryForTest();
            a.write(0, vector(0f), Vectorizer.NO_KEY);
            b.write(0, vector(1f), Vectorizer.NO_KEY);

            assertNotEquals(first, second);
            assertArrayEquals(vector(0f), a.read(0));
            assertFalse(VectorFile.Default.isAbandoned(first), "in use");
            assertFalse(VectorFile.Default.isAbandoned(second), "in use");
        }

        assertFalse(Files.exists(first));
        assertFalse(Files.exists(second));
    }

    /**
     * A temporary file is deleted on close and rebuilt on every load, so it pays for no durability: creating its
     * chunks is all the I/O its writes cost, with no header forced and no checkpoint.
     */
    @Test
    void openTemporary_writesAndCheckpoint_doNoDurabilityIo()
    {
        try(final VectorFile.Default file = VectorFile.Default.openTemporary("idx", DIMENSION, CHUNK_BYTES, "t"))
        {
            file.recovered();
            final long before = file.ioCount();

            file.write(0, vector(0f), Vectorizer.NO_KEY);
            file.write(9, vector(9f), Vectorizer.NO_KEY);
            store(file);
            file.checkpoint();
            file.write(1, vector(1f), Vectorizer.NO_KEY);

            assertEquals(2L, file.ioCount() - before, "the two chunk creations only");
            assertFalse(file.isCheckpointDue());
            assertArrayEquals(vector(9f), file.read(9));
        }
    }

    @Test
    void isAbandoned_unlockedOrOldUnlockedDirectory_isAbandoned() throws IOException
    {
        final Path unlocked = Files.createDirectories(this.directory.resolve("1-unlocked"));
        Files.createFile(unlocked.resolve(".lock"));
        final Path fresh = Files.createDirectories(this.directory.resolve("1-fresh"));
        final Path old   = Files.createDirectories(this.directory.resolve("1-old"));
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofMinutes(5L))));

        assertTrue(VectorFile.Default.isAbandoned(unlocked), "a lock file nobody holds");
        assertFalse(VectorFile.Default.isAbandoned(fresh), "may be between its creation and its lock");
        assertTrue(VectorFile.Default.isAbandoned(old), "too old to be still being created");
    }

    /**
     * A chunk whose vectors were all removed, with the removals committed and forced, is dropped at the next
     * open: neither mapped nor kept, so the file shrinks with the ids it no longer holds a vector for.
     */
    @Test
    void open_coldChunkWithoutVectors_isDropped()
    {
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            file.write(9, vector(9f), Vectorizer.NO_KEY);
            file.write(10, vector(10f), Vectorizer.NO_KEY);
            store(file);
            file.write(9, null, Vectorizer.NO_KEY);
            file.write(10, null, Vectorizer.NO_KEY);
            epoch = store(file);
        }

        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            assertFalse(reopened.hasChunk(2), "dropped");
            assertFalse(Files.exists(this.directory.resolve("idx.vec-2")));
            assertTrue(reopened.hasChunk(0));
            assertArrayEquals(vector(0f), reopened.read(0));
            assertNull(reopened.read(9));
            assertTrue(reopened.recovery().isEmpty());

            reopened.recovered();
            reopened.write(9, vector(1f), Vectorizer.NO_KEY);
            assertArrayEquals(vector(1f), reopened.read(9), "recreated on the next write");
        }
    }

    /**
     * A chunk emptied by removals that were never committed is hot: the committed state may still have vectors in
     * it, so it is kept for recovery to repair.
     */
    @Test
    void open_hotChunkWithoutVectors_isKept()
    {
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            file.write(9, vector(9f), Vectorizer.NO_KEY);
            epoch = store(file);
            file.checkpoint();
            file.write(9, null, Vectorizer.NO_KEY);
            file.crashForTest();
        }

        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            assertTrue(reopened.hasChunk(2), "kept for recovery");
            assertTrue(Files.exists(this.directory.resolve("idx.vec-2")));
        }
    }

    @Test
    void read_afterClose_throwsClosedNotInternalError()
    {
        final VectorFile file = this.fresh();
        file.write(0, vector(0f), Vectorizer.NO_KEY);
        file.close();

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> file.read(0));
        assertTrue(e.getMessage().contains("closed"), e.getMessage());
    }

    @Test
    void createChunk_diskFull_failsWithIOException()
    {
        try(final VectorFile file = this.fresh())
        {
            VectorFile.Default.chunkCreationTestHook = path ->
            {
                throw new IOException("No space left on device");
            };

            final IORuntimeException e = assertThrows(
                IORuntimeException.class,
                () -> file.write(0, vector(0f), Vectorizer.NO_KEY)
            );
            assertInstanceOf(IOException.class, e.getCause());

            VectorFile.Default.chunkCreationTestHook = null;
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            assertNotNull(file.read(0));
        }
    }

    /**
     * A write into a cold chunk must not land before the header on disk lists the chunk as hot. When forcing that
     * header fails, a retried write must force it again, not find the chunk hot already and skip the header.
     */
    @Test
    void write_headerWriteFailsMakingChunkHot_retryWritesTheHeader()
    {
        try(final VectorFile.Default file = this.fresh())
        {
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            store(file);
            file.checkpoint();
            assertFalse(file.hotChunksOnDisk().get(0), "cold before the test");
            VectorFile.Default.headerWriteTestHook = path ->
            {
                throw new IOException("No space left on device");
            };

            assertThrows(IORuntimeException.class, () -> file.write(1, vector(1f), Vectorizer.NO_KEY));
            VectorFile.Default.headerWriteTestHook = null;
            file.write(1, vector(1f), Vectorizer.NO_KEY);

            assertTrue(file.hotChunksOnDisk().get(0), "the retry made the chunk hot on disk");
            assertArrayEquals(vector(1f), file.read(1));
        }
    }

    @Test
    void close_andTakeOver_leaveNoOpenRegistrationBehind()
    {
        final VectorFile.Default first = this.fresh();
        assertTrue(VectorFile.Default.isRegisteredOpen(this.directory, "idx"));

        // A second open takes the first over; closing the second must not leave the path registered.
        final VectorFile.Default second = this.fresh();
        assertTrue(first.isClosed(), "taken over");
        assertTrue(VectorFile.Default.isRegisteredOpen(this.directory, "idx"));
        second.close();

        assertFalse(VectorFile.Default.isRegisteredOpen(this.directory, "idx"));
    }

    /**
     * An open of a path waits for another open of the same path to register, so the two cannot both map the file:
     * the second takes the first over.
     */
    @Test
    void open_concurrentlyOnOnePath_secondWaitsAndTakesTheFirstOver() throws Exception
    {
        final CountDownLatch                       inFirst = new CountDownLatch(1);
        final CountDownLatch                       release = new CountDownLatch(1);
        final AtomicReference<VectorFile.Default>  first   = new AtomicReference<>();
        final AtomicReference<VectorFile.Default>  second  = new AtomicReference<>();
        VectorFile.Default.openTestHook = () ->
        {
            if(inFirst.getCount() > 0)
            {
                inFirst.countDown();
                try
                {
                    release.await(30, TimeUnit.SECONDS);
                }
                catch(final InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
        };
        final Thread firstOpen  = new Thread(() -> first.set(this.open(0L, 0L)), "first-open");
        final Thread secondOpen = new Thread(() -> second.set(this.open(0L, 0L)), "second-open");
        try
        {
            firstOpen.start();
            assertTrue(inFirst.await(30, TimeUnit.SECONDS));
            secondOpen.start();
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while(secondOpen.getState() != Thread.State.BLOCKED && secondOpen.isAlive())
            {
                assertTrue(System.nanoTime() < deadline, "the second open neither waited nor finished");
                Thread.onSpinWait();
            }
            assertNull(second.get(), "the second open must wait for the first to register");
        }
        finally
        {
            release.countDown();
            firstOpen.join(30_000L);
            secondOpen.join(30_000L);
            VectorFile.Default.openTestHook = null;
        }

        assertTrue(first.get().isClosed(), "taken over by the second open");
        assertFalse(second.get().isClosed());
        second.get().close();
    }

    @Test
    void seal_withoutWrites_returnsNoEpoch()
    {
        try(final VectorFile file = this.fresh())
        {
            assertEquals(-1L, file.seal());
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            final long epoch = file.seal();
            assertTrue(epoch > 0L);
            assertEquals(-1L, file.seal());
        }
    }

    /**
     * Eclipse Store's storers commit independently, not in the order the store hooks sealed their epochs. A
     * commit of a later epoch must not let the slots of an earlier, uncommitted one be trusted: the committed
     * epoch advances only over epochs whose stores all committed, and catches up once the earlier one does.
     */
    @Test
    void committed_outOfOrder_advancesOnlyOverCommittedSeals()
    {
        try(final VectorFile.Default file = this.fresh())
        {
            file.write(0, vector(0f), Vectorizer.NO_KEY);
            final long first = file.seal();
            file.write(1, vector(1f), Vectorizer.NO_KEY);
            final long second = file.seal();
            assertTrue(second > first);
            final long before = file.epochsForTest()[2];

            file.committed(second);
            assertEquals(before, file.epochsForTest()[2], "the later commit must not pass the uncommitted epoch");

            file.committed(first);
            assertEquals(second, file.epochsForTest()[2], "both committed: the later epoch is reached");

            // A store that commits an epoch sealed before this open, or one committed already, still counts.
            file.committed(first);
            assertEquals(second, file.epochsForTest()[2]);
        }
    }

    @Test
    @Tag("slow")
    void open_growingHeader_keepsTheNewestRecord()
    {
        final long nonce;
        final long epoch;
        try(final VectorFile.Default file = this.fresh())
        {
            nonce = file.nonce();
            // Enough chunks that the bit sets outgrow the first header region.
            for(int chunk = 0; chunk < 40_000; chunk += 97)
            {
                file.write(chunk * 4, vector(chunk), Vectorizer.NO_KEY);
            }
            epoch = store(file);
        }

        try(final VectorFile.Default reopened = this.open(nonce, epoch))
        {
            assertTrue(reopened.recovery().isEmpty());
            assertArrayEquals(vector(39_964f), reopened.read(39_964 * 4));
        }
    }
}
