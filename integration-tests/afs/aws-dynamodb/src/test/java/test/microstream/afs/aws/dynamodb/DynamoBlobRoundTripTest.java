package test.microstream.afs.aws.dynamodb;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - aws-dynamodb
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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import org.eclipse.store.afs.aws.dynamodb.types.DynamoDbConnector;
import org.eclipse.store.afs.blobstore.types.BlobStoreConnector;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.eclipse.store.afs.blobstore.types.BlobStorePath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Bypasses Eclipse Store entirely and exercises the DynamoDB AFS connector with a known
 * byte pattern. Pinpoints whether the corruption observed in HashMapDynamoReproTest is
 * in the connector or in higher layers.
 */
@SuppressWarnings("resource")
public class DynamoBlobRoundTripTest {

    private static DynamoDbClient client;

    @BeforeAll
    static void setUp() {
        client = DynamoContainer.buildClient();
    }

    @Test
    void singleBlob_350kb_roundTripsExactly() {
        assertByteIdentity(350_000, 1);
    }

    @Test
    void twoBlobs_500kb_singleSourceBuffer_roundTripsExactly() {
        assertByteIdentity(500_000, 1);
    }

    @Test
    void twoBlobs_500kb_manySmallSourceBuffers_roundTripsExactly() {
        assertByteIdentity(500_000, 100);
    }

    @Test
    void threeBlobs_900kb_roundTripsExactly() {
        assertByteIdentity(900_000, 1);
    }

    /**
     * Simulates how EmbeddedStorage actually writes — many appends, each (probably)
     * smaller than MAX_BLOB_SIZE, growing the file past MAX_BLOB_SIZE in aggregate.
     */
    @Test
    void appendedWrites_aggregate600kb_roundTripsExactly() {
        assertAppendIdentity(new int[]{ 100_000, 100_000, 100_000, 100_000, 100_000, 100_000 });
    }

    @Test
    void appendedWrites_unevenSizes_roundTripsExactly() {
        assertAppendIdentity(new int[]{ 200_000, 199_999, 50_000, 100_000, 80_000 });
    }

    /** Two appends each just under MAX_BLOB_SIZE, total ~780 KB. */
    @Test
    void appendedWrites_twoLargeWrites_roundTripsExactly() {
        assertAppendIdentity(new int[]{ 390_000, 390_000 });
    }

    /**
     * Reads the file back in small chunks at varied offsets, including across the
     * 400 KB blob boundary. This is closer to how StorageFileManager actually reads.
     */
    @Test
    void partialReads_acrossBlobBoundary_returnExactBytes() {
        final String table = "part_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        TypeTest.createTable(client, table);
        final BlobStoreConnector connector = DynamoDbConnector.Caching(client);
        final BlobStorePath path = BlobStoreFileSystem.toPath(table, "subdir", "blob.dat");

        final int total = 600_000;
        final byte[] expected = new byte[total];
        for (int i = 0; i < total; i++) expected[i] = (byte) ((i * 31 + 7) ^ (i >>> 8));

        final ByteBuffer src = ByteBuffer.allocate(total);
        src.put(expected);
        src.flip();
        connector.writeData(path, Arrays.asList(src));

        // The DynamoDB connector's MAX_BLOB_SIZE is 400_000 — so this file lives in 2 blobs.
        // Read 1 KB chunks at offsets 0, 100k, 399_000 (crosses boundary), 400_000, 500_000, 599_000.
        final long[] offsets = { 0, 100_000, 399_000, 400_000, 500_000, 599_000 };
        for (final long off : offsets) {
            final int len = Math.min(1024, total - (int) off);
            final ByteBuffer buf = connector.readData(path, off, len);
            Assertions.assertEquals(len, buf.remaining(),
                    "readData remaining at offset " + off);
            final byte[] actual = new byte[len];
            buf.get(actual);
            for (int i = 0; i < len; i++) {
                if (actual[i] != expected[(int) off + i]) {
                    Assertions.fail(String.format(
                            "Mismatch at file offset %d (byte %d into the chunk read at offset %d, length %d).%n" +
                                    "Expected: %s%nActual:   %s",
                            off + i, i, off, len,
                            hexWindow(expected, (int) off + i, 16),
                            hexWindow(actual, i, 16)));
                }
            }
        }
    }

    /** Reads 1 byte at a time across blob boundary — catches any per-blob position arithmetic. */
    @Test
    void partialReads_singleByteSweep_around400kBoundary() {
        final String table = "swep_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        TypeTest.createTable(client, table);
        final BlobStoreConnector connector = DynamoDbConnector.Caching(client);
        final BlobStorePath path = BlobStoreFileSystem.toPath(table, "subdir", "blob.dat");

        final int total = 410_000;
        final byte[] expected = new byte[total];
        for (int i = 0; i < total; i++) expected[i] = (byte) ((i * 31 + 7) ^ (i >>> 8));

        final ByteBuffer src = ByteBuffer.allocate(total);
        src.put(expected);
        src.flip();
        connector.writeData(path, Arrays.asList(src));

        // Sweep ±20 bytes around the 400_000 boundary, one byte each
        for (int off = 399_980; off < 400_020; off++) {
            final ByteBuffer buf = connector.readData(path, off, 1);
            Assertions.assertEquals(1, buf.remaining(), "remaining at " + off);
            final byte b = buf.get();
            Assertions.assertEquals(
                    expected[off], b,
                    "byte at offset " + off + " expected=0x" + String.format("%02x", expected[off] & 0xFF)
                            + " actual=0x" + String.format("%02x", b & 0xFF));
        }
    }

    private static void assertAppendIdentity(final int[] chunkSizes) {
        final String table = "appd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        TypeTest.createTable(client, table);

        final BlobStoreConnector connector = DynamoDbConnector.Caching(client);
        final BlobStorePath path = BlobStoreFileSystem.toPath(table, "subdir", "blob.dat");

        int total = 0;
        for (final int n : chunkSizes) total += n;
        final byte[] expected = new byte[total];
        for (int i = 0; i < total; i++) expected[i] = (byte) ((i * 31 + 7) ^ (i >>> 8));

        int written = 0;
        for (final int n : chunkSizes) {
            final ByteBuffer bb = ByteBuffer.allocate(n);
            bb.put(expected, written, n);
            bb.flip();
            connector.writeData(path, Arrays.asList(bb));
            written += n;
        }

        final ByteBuffer readBack = connector.readData(path, 0, total);
        Assertions.assertEquals(total, readBack.remaining(),
                "readData returned fewer bytes than written (chunks=" + Arrays.toString(chunkSizes) + ")");
        final byte[] actual = new byte[total];
        readBack.get(actual);

        if (!Arrays.equals(expected, actual)) {
            final int firstDiff = firstDifferingIndex(expected, actual);
            Assertions.fail(String.format(
                    "Append mismatch for chunks=%s.%n" +
                            "First diff at byte %d (0x%X).%n" +
                            "Expected: %s%nActual:   %s",
                    Arrays.toString(chunkSizes), firstDiff, firstDiff,
                    hexWindow(expected, firstDiff, 16),
                    hexWindow(actual,   firstDiff, 16)));
        }
    }

    private static void assertByteIdentity(final int totalSize, final int sourceChunks) {
        final String table = "blob_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        TypeTest.createTable(client, table);

        final BlobStoreConnector connector = DynamoDbConnector.Caching(client);
        final BlobStoreFileSystem fs = BlobStoreFileSystem.New(connector);

        final BlobStorePath path = BlobStoreFileSystem.toPath(table, "subdir", "blob.dat");

        // Deterministic, full-range byte pattern — byte i = (i * 31 + 7) ^ (i >>> 8)
        final byte[] expected = new byte[totalSize];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) ((i * 31 + 7) ^ (i >>> 8));
        }

        // Source split into N equal-ish ByteBuffers to mimic storage-layer chunking
        final ByteBuffer[] sourceBuffers = splitToBuffers(expected, sourceChunks);
        connector.writeData(path, Arrays.asList(sourceBuffers));

        // Read back the whole file (readData returns an already-flipped buffer)
        final ByteBuffer readBack = connector.readData(path, 0, totalSize);
        Assertions.assertEquals(totalSize, readBack.remaining(),
                "readData returned fewer bytes than written");
        final byte[] actual = new byte[totalSize];
        readBack.get(actual);

        // Compare and, on mismatch, report the first divergence
        if (!Arrays.equals(expected, actual)) {
            final int firstDiff = firstDifferingIndex(expected, actual);
            Assertions.fail(String.format(
                    "Round-trip mismatch for totalSize=%d, sourceChunks=%d.%n" +
                            "First diff at byte %d (0x%X).%n" +
                            "Expected: %s%nActual:   %s",
                    totalSize, sourceChunks, firstDiff, firstDiff,
                    hexWindow(expected, firstDiff, 16),
                    hexWindow(actual,   firstDiff, 16)));
        }
    }

    private static ByteBuffer[] splitToBuffers(final byte[] src, final int chunks) {
        final ByteBuffer[] out = new ByteBuffer[chunks];
        final int chunkSize = src.length / chunks;
        for (int i = 0; i < chunks; i++) {
            final int from = i * chunkSize;
            final int to   = (i == chunks - 1) ? src.length : from + chunkSize;
            final ByteBuffer bb = ByteBuffer.allocate(to - from);
            bb.put(src, from, to - from);
            bb.flip();
            out[i] = bb;
        }
        return out;
    }

    private static int firstDifferingIndex(final byte[] a, final byte[] b) {
        final int min = Math.min(a.length, b.length);
        for (int i = 0; i < min; i++) if (a[i] != b[i]) return i;
        return min;
    }

    private static String hexWindow(final byte[] arr, final int at, final int span) {
        final int from = Math.max(0, at);
        final int to   = Math.min(arr.length, at + span);
        final StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) sb.append(' ');
            sb.append(String.format("%02x", arr[i] & 0xFF));
        }
        return sb.toString();
    }
}
