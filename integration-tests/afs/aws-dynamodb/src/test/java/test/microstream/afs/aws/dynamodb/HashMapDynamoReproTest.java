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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.UUID;

import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.afs.aws.dynamodb.types.DynamoDbConnector;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Save/reload regression for the DynamoDB AFS backend ({@link BlobStoreFileSystem} +
 * {@link DynamoDbConnector}).
 *
 * Pre-fix, the second {@code EmbeddedStorage.start(...)} threw
 * {@code StorageException: Problem in channel #0} with a varying cause
 * ({@code TypeId not resolvable via type dictionary: ...} or {@code Invalid OID: ...}) —
 * a downstream symptom of the connector silently corrupting bytes at blob boundaries
 * when a channel data file is split across multiple DynamoDB rows
 * ({@code MAX_BLOB_SIZE = 400 000 B}).
 *
 * Trigger threshold (bisected on this fixture):
 *
 *   HashMap&lt;Integer,Integer&gt; entries   | result (pre-fix)
 *   ---------------------------------- | ----------------
 *   1 000 - 5 500                      | PASS
 *   5 600 - 10 000                     | FAIL
 *
 * The same payload round-trips fine on local NIO ({@link #sanityCheck_sameDataRoundTripsOnLocalNio}),
 * which confirms the defect was in the connector, not in the storage / serializer core.
 */
@SuppressWarnings("resource")
public class HashMapDynamoReproTest {

    /** Smallest entry count observed to trigger the failure on this repro. */
    private static final int LARGE_MAP_ENTRIES = 6_000;

    private static DynamoDbClient client;

    @BeforeAll
    static void setUp() {
        client = DynamoContainer.buildClient();
    }

    @Test
    void reloadAfterLargeHashMapIsSplitAcrossDynamoRows() {
        final String tableName = "repro_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        TypeTest.createTable(client, tableName);

        // ----- write -----
        final HashMap<Integer, Integer> original = new HashMap<>();
        for (int i = 0; i < LARGE_MAP_ENTRIES; i++) {
            original.put(i, i * 2);
        }

        EmbeddedStorageManager mgr = startDynamoStorage(original, tableName);
        mgr.storeRoot();
        mgr.shutdown();

        // ----- reload (pre-fix: throws) -----
        final HashMap<Integer, Integer> loaded = new HashMap<>();
        EmbeddedStorageManager mgr2 = startDynamoStorage(loaded, tableName);
        try {
            Assertions.assertEquals(LARGE_MAP_ENTRIES, loaded.size(),
                    "loaded map should contain all entries from the stored map");
            Assertions.assertEquals(Integer.valueOf(2468), loaded.get(1234),
                    "sample entry must round-trip");
        } finally {
            mgr2.shutdown();
        }
    }

    /** Same payload on local NIO — proves the bug was in the connector. */
    @Test
    void sanityCheck_sameDataRoundTripsOnLocalNio() throws Exception {
        final Path dir = Files.createTempDirectory("eclipse-store-nio-repro-");
        try {
            final HashMap<Integer, Integer> original = new HashMap<>();
            for (int i = 0; i < LARGE_MAP_ENTRIES; i++) {
                original.put(i, i * 2);
            }

            EmbeddedStorageManager mgr = EmbeddedStorage.start(original, dir);
            mgr.storeRoot();
            mgr.shutdown();

            final HashMap<Integer, Integer> loaded = new HashMap<>();
            EmbeddedStorageManager mgr2 = EmbeddedStorage.start(loaded, dir);
            try {
                Assertions.assertEquals(LARGE_MAP_ENTRIES, loaded.size());
                Assertions.assertEquals(Integer.valueOf(2468), loaded.get(1234));
            } finally {
                mgr2.shutdown();
            }
        } finally {
            deleteRecursively(dir);
        }
    }

    private static EmbeddedStorageManager startDynamoStorage(final Object root, final String tableName) {
        final AFileSystem fs = BlobStoreFileSystem.New(DynamoDbConnector.Caching(client));
        return EmbeddedStorage.start(root, fs.ensureDirectoryPath(tableName));
    }

    private static void deleteRecursively(final Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (final Exception ignored) {} });
    }
}
