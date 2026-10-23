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


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SegmentManifest} and the ordinal map and directory sync helpers of {@link SegmentFiles}.
 */
class SegmentManifestTest
{
    private static final GraphFormat INLINE = new GraphFormat(VectorStorage.INLINE, ApproximateScoring.NONE, 0, 0);
    private static final GraphFormat NVQ_PQ = new GraphFormat(VectorStorage.NVQ, ApproximateScoring.FUSED_PQ, 4, 2);

    @TempDir
    Path directory;

    @Test
    void read_writtenManifest_roundTrips() throws IOException
    {
        final SegmentManifest manifest = sampleManifest();
        final Path            path     = this.directory.resolve("idx.manifest");

        manifest.write(path);
        final SegmentManifest read = SegmentManifest.read(path);

        assertEquals(manifest.dimension(), read.dimension());
        assertTrue(manifest.witness().matches(read.witness()));
        assertEquals(manifest.segments(), read.segments());
        assertArrayEquals(new int[]{1, 4}, read.segments().get(1).deletedLocals());
        assertEquals(13, read.nextSequence());
    }

    @Test
    void read_emptySegmentList_roundTrips() throws IOException
    {
        final SegmentManifest manifest = new SegmentManifest(8, new DiskIndexManager.MetaState(0, -1, 3), List.of());
        final Path            path     = this.directory.resolve("idx.manifest");

        manifest.write(path);

        assertEquals(List.of(), SegmentManifest.read(path).segments());
        assertEquals(0, SegmentManifest.read(path).nextSequence());
    }

    @Test
    void read_truncatedManifest_isRejected() throws IOException
    {
        final Path path = this.directory.resolve("idx.manifest");
        sampleManifest().write(path);
        final byte[] bytes = Files.readAllBytes(path);

        for(final int length : new int[]{0, 7, bytes.length / 2, bytes.length - 1})
        {
            Files.write(path, Arrays.copyOf(bytes, length));
            assertThrows(IOException.class, () -> SegmentManifest.read(path), "length " + length);
        }
    }

    @Test
    void read_flippedByte_isRejected() throws IOException
    {
        final Path path = this.directory.resolve("idx.manifest");
        sampleManifest().write(path);
        final byte[] bytes = Files.readAllBytes(path);
        bytes[bytes.length / 2] ^= 0x10;
        Files.write(path, bytes);

        assertThrows(IOException.class, () -> SegmentManifest.read(path));
    }

    @Test
    void read_previousVersion_isRejected() throws IOException
    {
        final Path path = this.directory.resolve("idx.manifest");
        writeRaw(path, DiskIndexManager.GRAPH_FILE_VERSION - 1, VectorStorage.INLINE.code());

        final IOException failure = assertThrows(IOException.class, () -> SegmentManifest.read(path));
        assertTrue(failure.getMessage().contains("version"), failure.getMessage());
    }

    @Test
    void read_unknownFormatCode_isRejected() throws IOException
    {
        final Path path = this.directory.resolve("idx.manifest");
        writeRaw(path, DiskIndexManager.GRAPH_FILE_VERSION, 999);

        final IOException failure = assertThrows(IOException.class, () -> SegmentManifest.read(path));
        assertTrue(failure.getMessage().contains("unknown format codes"), failure.getMessage());
    }

    @Test
    void constructor_unorderedSegments_throws()
    {
        final SegmentManifest.Segment a = new SegmentManifest.Segment(3, 2, INLINE, false, new int[0]);
        final SegmentManifest.Segment b = new SegmentManifest.Segment(3, 2, INLINE, false, new int[0]);
        final SegmentManifest.Segment c = new SegmentManifest.Segment(5, 2, INLINE, false, new int[0]);

        assertThrows(IllegalArgumentException.class,
            () -> new SegmentManifest(4, new DiskIndexManager.MetaState(1, 1, 1), List.of(a, b)),
            "a duplicate sequence");
        assertThrows(IllegalArgumentException.class,
            () -> new SegmentManifest(4, new DiskIndexManager.MetaState(1, 1, 1), List.of(c, a)),
            "a descending sequence");
    }

    @Test
    void constructor_segmentCountAtTheSlotLimit_roundTripsAndOneMoreThrows() throws IOException
    {
        final int                        limit   = SegmentOwners.MAX_SLOT + 1;
        final DiskIndexManager.MetaState witness = new DiskIndexManager.MetaState(1, 1, 1);
        final SegmentManifest            atLimit = new SegmentManifest(4, witness, segments(limit));
        final Path                       path    = this.directory.resolve("idx.manifest");

        atLimit.write(path);
        assertEquals(limit, SegmentManifest.read(path).segments().size());

        assertThrows(IllegalArgumentException.class, () -> new SegmentManifest(4, witness, segments(limit + 1)));
    }

    @Test
    void read_segmentCountAboveTheSlotLimit_isRejected() throws IOException
    {
        final int  limit = SegmentOwners.MAX_SLOT + 1;
        final Path path  = this.directory.resolve("idx.manifest");
        new SegmentManifest(4, new DiskIndexManager.MetaState(1, 1, 1), segments(limit)).write(path);

        // The segment count follows magic, version, dimension and the three witness longs.
        final byte[]     bytes  = Files.readAllBytes(path);
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.putInt(3 * Integer.BYTES + 3 * Long.BYTES, limit + 1);
        final CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length - Long.BYTES);
        buffer.putLong(bytes.length - Long.BYTES, crc.getValue());
        Files.write(path, bytes);

        final IOException failure = assertThrows(SegmentManifest.InvalidManifestException.class,
            () -> SegmentManifest.read(path));
        assertTrue(failure.getMessage().contains("lists " + (limit + 1) + " segments"), failure.getMessage());
    }

    @Test
    void segment_deletedLocalsOutOfRangeOrUnsorted_throws()
    {
        assertThrows(IllegalArgumentException.class, () -> new SegmentManifest.Segment(0, 3, INLINE, false, new int[]{3}));
        assertThrows(IllegalArgumentException.class, () -> new SegmentManifest.Segment(0, 3, INLINE, false, new int[]{2, 1}));
        assertThrows(IllegalArgumentException.class, () -> new SegmentManifest.Segment(0, 0, INLINE, false, new int[0]));
    }

    @Test
    void segment_deletedLocals_areDefensivelyCopied()
    {
        final int[] deleted = {0, 2};
        final SegmentManifest.Segment segment = new SegmentManifest.Segment(0, 3, INLINE, false, deleted);

        deleted[0] = 1;
        segment.deletedLocals()[1] = 1;

        assertArrayEquals(new int[]{0, 2}, segment.deletedLocals());
    }

    @Test
    void readOrdinalMap_writtenMap_roundTrips() throws IOException
    {
        final Path  path = this.directory.resolve("idx.seg-0.l2g");
        final int[] l2g  = {0, 5, 9_000_000, Integer.MAX_VALUE};

        SegmentFiles.writeOrdinalMap(path, l2g);

        assertArrayEquals(l2g, SegmentFiles.readOrdinalMap(path, l2g.length));
    }

    @Test
    void readOrdinalMap_wrongCountOrCorrupt_isRejected() throws IOException
    {
        final Path path = this.directory.resolve("idx.seg-0.l2g");
        SegmentFiles.writeOrdinalMap(path, new int[]{1, 2, 3});

        assertThrows(IOException.class, () -> SegmentFiles.readOrdinalMap(path, 4), "count");

        final byte[] bytes = Files.readAllBytes(path);
        bytes[bytes.length - 12] ^= 0x01;
        Files.write(path, bytes);
        assertThrows(IOException.class, () -> SegmentFiles.readOrdinalMap(path, 3), "checksum");

        Files.write(path, Arrays.copyOf(bytes, bytes.length - 3));
        assertThrows(IOException.class, () -> SegmentFiles.readOrdinalMap(path, 3), "truncated");
    }

    @Test
    void verifyCrc32_appendedCrc_passesAndAFlippedByteFails() throws IOException
    {
        final Path file = this.directory.resolve("idx.seg-0.pqv");
        Files.write(file, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9});
        SegmentFiles.appendCrc32(file);

        SegmentFiles.verifyCrc32(file);

        final byte[] bytes = Files.readAllBytes(file);
        bytes[4] ^= 0x10;
        Files.write(file, bytes);
        assertThrows(IOException.class, () -> SegmentFiles.verifyCrc32(file));
    }

    @Test
    void verifyCrc32_fileShorterThanItsCrc_isRejected() throws IOException
    {
        final Path file = this.directory.resolve("idx.seg-0.pqv");
        Files.write(file, new byte[]{1, 2, 3});

        assertThrows(IOException.class, () -> SegmentFiles.verifyCrc32(file));
    }

    @Test
    void syncDirectory_existingDirectory_succeeds() throws IOException
    {
        Files.writeString(this.directory.resolve("idx.manifest"), "moved here");

        SegmentFiles.syncDirectory(this.directory);
    }

    @Test
    void syncDirectory_directoryThatCannotBeOpened_isSkipped()
    {
        // What a platform without directory sync looks like to the open: the sync is skipped, not failed.
        assertDoesNotThrow(() -> SegmentFiles.syncDirectory(this.directory.resolve("missing")));
    }

    @Test
    void layoutFilePattern_otherIndexNames_doNotMatch()
    {
        final var pattern = SegmentFiles.layoutFilePattern("a");

        assertTrue(pattern.matcher("a.seg-0.graph").matches());
        assertTrue(pattern.matcher("a.seg-12.l2g").matches());
        assertTrue(pattern.matcher("a.seg-3.pqv.tmp").matches());
        assertTrue(pattern.matcher("a.manifest.tmp").matches());
        assertFalse(pattern.matcher("a.manifest").matches(), "the manifest itself is never a leftover");
        assertFalse(pattern.matcher("a.seg-1.seg-0.graph").matches(), "a segment of index a.seg-1");
        assertFalse(pattern.matcher("a.seg-1.manifest").matches(), "the manifest of index a.seg-1");
        assertFalse(pattern.matcher("ab.seg-0.graph").matches());
        assertFalse(pattern.matcher("a.graph").matches());
    }

    private static List<SegmentManifest.Segment> segments(final int count)
    {
        final List<SegmentManifest.Segment> segments = new ArrayList<>(count);
        for(int sequence = 0; sequence < count; sequence++)
        {
            segments.add(new SegmentManifest.Segment(sequence, 1, INLINE, false, new int[0]));
        }
        return segments;
    }

    private static SegmentManifest sampleManifest()
    {
        return new SegmentManifest(
            16,
            new DiskIndexManager.MetaState(40, 99, 7),
            List.of(
                new SegmentManifest.Segment(4, 30, INLINE, false, new int[0]),
                new SegmentManifest.Segment(12, 10, NVQ_PQ, true, new int[]{1, 4})
            )
        );
    }

    /**
     * Writes a one-segment manifest by hand with a valid checksum, so a field can be given a value
     * {@link SegmentManifest#write} would never produce.
     */
    private static void writeRaw(
        final Path path       ,
        final int  version    ,
        final int  storageCode
    ) throws IOException
    {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try(final DataOutputStream out = new DataOutputStream(bytes))
        {
            out.writeInt(0x4356_4D46);
            out.writeInt(version);
            out.writeInt(16);
            out.writeLong(1);
            out.writeLong(1);
            out.writeLong(1);
            out.writeInt(1);
            out.writeLong(0);
            out.writeInt(1);
            out.writeInt(storageCode);
            out.writeInt(ApproximateScoring.NONE.code());
            out.writeInt(0);
            out.writeInt(0);
            out.writeByte(0);
            out.writeInt(0);
        }
        final CRC32 crc = new CRC32();
        crc.update(bytes.toByteArray());
        final ByteBuffer buffer = ByteBuffer.allocate(bytes.size() + Long.BYTES);
        buffer.put(bytes.toByteArray()).putLong(crc.getValue());
        Files.write(path, buffer.array());
    }

}
