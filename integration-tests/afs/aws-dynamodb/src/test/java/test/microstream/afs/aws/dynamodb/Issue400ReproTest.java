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

import java.util.Arrays;
import java.util.UUID;

import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.afs.aws.dynamodb.types.DynamoDbConnector;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageChannelCountProvider;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Regression test for <a href="https://github.com/eclipse-store/store/issues/400">
 * eclipse-store/store#400</a>: storing a 256 KB string via {@link DynamoDbConnector}
 * (which serialises to ~524 KB binary, i.e. exceeds {@code MAX_BLOB_SIZE = 400 KB}
 * and forces the multi-blob write/read path).
 *
 * Pre-fix the third or fourth repeat would throw {@code Impossible store length}
 * (issue #400) or {@code Non-deleted non-empty data file not found} (downstream
 * symptom). Post-fix all four runs complete cleanly.
 */
@SuppressWarnings("resource")
public class Issue400ReproTest {

    private static DynamoDbClient client;

    @BeforeAll
    static void setUp() {
        client = DynamoContainer.buildClient();
    }

    public static class DataRoot {
        String content;
        public String getContent()         { return content; }
        public void   setContent(String c) { content = c;    }
    }

    @Test
    void store256kbString_repeated_doesNotThrow() {
        final String table = "i400_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        TypeTest.createTable(client, table);

        final byte[] array = new byte[256 * 1024];
        Arrays.fill(array, (byte) 1);
        final String content = new String(array);

        // Four iterations exercise the multi-blob write path through housekeeping
        // file rotation; pre-fix this is where the connector's pagination/IO bugs
        // manifest as a startup or storeRoot StorageException.
        for (int i = 1; i <= 4; i++) {
            runOne(table, content);
        }
    }

    private static void runOne(final String tableName, final String newContent) {
        final AFileSystem fs = BlobStoreFileSystem.New(DynamoDbConnector.New(client));

        final DataRoot root = new DataRoot();
        final EmbeddedStorageManager storage = EmbeddedStorage.Foundation(
                StorageConfiguration.Builder()
                        .setStorageFileProvider(
                                Storage.FileProviderBuilder(fs)
                                        .setDirectory(fs.ensureDirectoryPath(tableName))
                                        .createFileProvider())
                        .setChannelCountProvider(StorageChannelCountProvider.New(1))
                        .createConfiguration()
        ).start(root);
        try {
            root.setContent(newContent);
            storage.storeRoot();
        } finally {
            storage.shutdown();
        }
    }
}
