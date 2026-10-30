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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.afs.aws.dynamodb.types.DynamoDbConnector;
import org.eclipse.store.afs.blobstore.types.BlobStoreConnector;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.eclipse.store.afs.blobstore.types.BlobStorePath;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

/**
 * Diagnostic dump: writes the failing 6 000-entry HashMap once to DynamoDB and once
 * to a local NIO directory, then byte-by-byte compares the channel data files between
 * the two backends. Pinpoints exactly where the DynamoDB read diverges from truth.
 */
@SuppressWarnings("resource")
public class DynamoStorageBytesDumpTest {

    private static DynamoDbClient client;

    @BeforeAll
    static void setUp() {
        client = DynamoContainer.buildClient();
    }

    @Test
    void dumpDynamoBytes_andCompareAgainstNio() throws Exception {
        final int entries = 6_000;

        // ----- 1) write to DynamoDB -----
        final String table = "dump_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        TypeTest.createTable(client, table);

        final HashMap<Integer, Integer> dynRoot = new HashMap<>();
        for (int i = 0; i < entries; i++) dynRoot.put(i, i * 2);

        EmbeddedStorageManager dynMgr = startDynamoStorage(dynRoot, table);
        dynMgr.storeRoot();
        dynMgr.shutdown();

        // ----- 2) write same data to local NIO -----
        final Path nioDir = Files.createTempDirectory("eclipse-store-dump-");
        try {
            final HashMap<Integer, Integer> nioRoot = new HashMap<>();
            for (int i = 0; i < entries; i++) nioRoot.put(i, i * 2);

            EmbeddedStorageManager nioMgr = EmbeddedStorage.start(nioRoot, nioDir);
            nioMgr.storeRoot();
            nioMgr.shutdown();

            // ----- 3) list DynamoDB rows -----
            System.out.println("\n=== DynamoDB rows in table " + table + " ===");
            final ScanResponse scan = client.scan(ScanRequest.builder().tableName(table).build());
            final List<Row> rows = new ArrayList<>();
            for (final Map<String, AttributeValue> item : scan.items()) {
                rows.add(new Row(
                        item.get("key").s(),
                        Long.parseLong(item.get("seq").n()),
                        item.containsKey("size") ? Long.parseLong(item.get("size").n()) : -1L));
            }
            rows.sort(Comparator.<Row, String>comparing(r -> r.key).thenComparingLong(r -> r.seq));
            for (final Row r : rows) {
                System.out.printf("  key=%-90s seq=%d size=%d%n", r.key, r.seq, r.size);
            }

            // ----- 4) list NIO channel files -----
            final List<Path> nioChannelFiles = new ArrayList<>();
            Files.walk(nioDir).filter(p -> p.getFileName().toString().endsWith(".dat")).forEach(nioChannelFiles::add);
            System.out.println("\n=== NIO channel files ===");
            for (final Path p : nioChannelFiles) {
                System.out.printf("  %s (%d bytes)%n", p, Files.size(p));
            }

            // ----- 5) byte-by-byte compare -----
            final BlobStoreConnector connector = DynamoDbConnector.Caching(client);
            System.out.println("\n=== Byte-by-byte comparison ===");
            for (final Path nioFile : nioChannelFiles) {
                final byte[] nioBytes = Files.readAllBytes(nioFile);
                final String fname = nioFile.getFileName().toString();

                final String matchingKey = rows.stream()
                        .map(r -> r.key)
                        .filter(k -> k.endsWith("/" + fname))
                        .findFirst().orElse(null);
                if (matchingKey == null) {
                    System.out.printf("  %s: no matching DynamoDB key%n", fname);
                    continue;
                }

                final String[] parts = matchingKey.split("/");
                final String[] pathParts = new String[parts.length];
                pathParts[0] = table;
                System.arraycopy(parts, 1, pathParts, 1, parts.length - 1);
                final BlobStorePath bsPath = BlobStoreFileSystem.toPath(pathParts);

                final ByteBuffer buf = connector.readData(bsPath, 0, nioBytes.length);
                if (buf.remaining() != nioBytes.length) {
                    System.out.printf("  %s: SIZE MISMATCH — nio=%d dynamo=%d%n",
                            fname, nioBytes.length, buf.remaining());
                    continue;
                }
                final byte[] dynBytes = new byte[nioBytes.length];
                buf.get(dynBytes);
                int firstDiff = -1, diffCount = 0;
                for (int i = 0; i < nioBytes.length; i++) {
                    if (nioBytes[i] != dynBytes[i]) {
                        if (firstDiff < 0) firstDiff = i;
                        diffCount++;
                    }
                }
                if (firstDiff < 0) {
                    System.out.printf("  %s: OK %d bytes identical%n", fname, nioBytes.length);
                } else {
                    System.out.printf("  %s: MISMATCH first diff @offset %d (0x%X), %d differing bytes (size=%d)%n",
                            fname, firstDiff, firstDiff, diffCount, nioBytes.length);
                    System.out.printf("      NIO   : %s%n", hexWindow(nioBytes, firstDiff - 4, 32));
                    System.out.printf("      DYNAMO: %s%n", hexWindow(dynBytes, firstDiff - 4, 32));

                    // Per-blob dump: ask DynamoDB directly for each seq row of THIS file
                    // and report the first 32 bytes plus length, compared to NIO at the
                    // expected offset.
                    System.out.println("\n  --- per-blob comparison ---");
                    final List<Row> fileRows = new ArrayList<>();
                    for (final Row r : rows) if (r.key.equals(matchingKey)) fileRows.add(r);
                    long fileOffset = 0;
                    for (final Row br : fileRows) {
                        final GetItemResponse resp = client.getItem(GetItemRequest.builder()
                                .tableName(table)
                                .key(java.util.Map.of(
                                        "key", AttributeValue.builder().s(br.key).build(),
                                        "seq", AttributeValue.builder().n(Long.toString(br.seq)).build()))
                                .attributesToGet("data")
                                .build());
                        final byte[] blobBytes = resp.item().get("data").b().asByteArray();
                        final int firstN = Math.min(32, blobBytes.length);
                        final byte[] nioSlice = new byte[firstN];
                        System.arraycopy(nioBytes, (int) fileOffset, nioSlice, 0, firstN);
                        final boolean firstEqual = java.util.Arrays.equals(
                                java.util.Arrays.copyOf(blobBytes, firstN), nioSlice);
                        System.out.printf("    seq=%d stored-size=%d blob-bytes=%d → start (first 32) %s%n",
                                br.seq, br.size, blobBytes.length, firstEqual ? "MATCHES" : "DIFFERS");
                        if (!firstEqual) {
                            System.out.printf("      NIO[%d..%d]: %s%n", fileOffset, fileOffset + firstN,
                                    hexWindow(nioBytes, (int) fileOffset, firstN));
                            System.out.printf("      blob seq=%d : %s%n", br.seq,
                                    hexWindow(blobBytes, 0, firstN));
                        }
                        // Also dump tail of the blob (last 32 bytes)
                        if (blobBytes.length >= 32) {
                            final int tailFrom = blobBytes.length - 32;
                            final byte[] nioTail = new byte[32];
                            System.arraycopy(nioBytes, (int) fileOffset + tailFrom, nioTail, 0, 32);
                            final boolean tailEqual = java.util.Arrays.equals(
                                    java.util.Arrays.copyOfRange(blobBytes, tailFrom, blobBytes.length), nioTail);
                            System.out.printf("    seq=%d tail (last 32) %s%n",
                                    br.seq, tailEqual ? "MATCHES" : "DIFFERS");
                            if (!tailEqual) {
                                System.out.printf("      NIO  tail: %s%n", hexWindow(nioBytes, (int) fileOffset + tailFrom, 32));
                                System.out.printf("      blob tail: %s%n", hexWindow(blobBytes, tailFrom, 32));
                            }
                        }
                        fileOffset += blobBytes.length;
                    }
                }
            }
        } finally {
            try {
                Files.walk(nioDir).sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (final Exception ignored) {} });
            } catch (final Exception ignored) {}
        }
    }

    private static EmbeddedStorageManager startDynamoStorage(final Object root, final String tableName) {
        final AFileSystem fs = BlobStoreFileSystem.New(DynamoDbConnector.Caching(client));
        return EmbeddedStorage.start(root, fs.ensureDirectoryPath(tableName));
    }

    private static int secondDifferingIndex(final byte[] a, final byte[] b, final int firstDiff) {
        // skip the contiguous diff region and find the next divergence
        boolean inDiff = true;
        for (int i = firstDiff + 1; i < a.length; i++) {
            if (inDiff) {
                if (a[i] == b[i]) inDiff = false;
            } else if (a[i] != b[i]) {
                return i;
            }
        }
        return -1;
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

    private static final class Row {
        final String key;
        final long seq;
        final long size;
        Row(final String key, final long seq, final long size) { this.key = key; this.seq = seq; this.size = size; }
    }
}
