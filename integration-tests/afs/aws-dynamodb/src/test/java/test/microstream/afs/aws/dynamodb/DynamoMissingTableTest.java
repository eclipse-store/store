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

import java.util.UUID;

import org.eclipse.store.afs.aws.dynamodb.types.DynamoDbConnector;
import org.eclipse.store.afs.blobstore.types.BlobStoreConnector;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.eclipse.store.afs.blobstore.types.BlobStorePath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

/**
 * Covers the {@link DynamoDbConnector}'s behavior against missing tables — the
 * scenario behind Copilot's review on PR #680. The earlier version of
 * {@code childKeys} returned a stream produced by
 * {@code scanPaginator(request).items().stream()...} from inside its
 * {@code try/catch(ResourceNotFoundException)}. Whether the AWS SDK actually
 * triggers the underlying scan during {@code .stream()} (eager) or only on
 * later consumption (lazy) is an SDK implementation detail. The defensive fix
 * drains the paginator inside the try block so the {@code catch} clause is
 * always reachable, regardless of paginator semantics.
 */
@SuppressWarnings("resource")
public class DynamoMissingTableTest {

    private static DynamoDbClient client;

    @BeforeAll
    static void setUp() {
        client = DynamoContainer.buildClient();
    }

    /**
     * Sanity: terminal consumption of a scanPaginator over a missing table raises
     * {@link ResourceNotFoundException}. This is what the connector's
     * {@code childKeys} must absorb.
     */
    @Test
    void scanPaginatorOnMissingTable_terminalConsumption_throwsResourceNotFound() {
        final String missingTable = "missing_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Assertions.assertThrows(
                ResourceNotFoundException.class,
                () -> client.scanPaginator(ScanRequest.builder().tableName(missingTable).build())
                        .items().stream().count());
    }

    /**
     * Public-API regression: {@link BlobStoreConnector#isEmpty(BlobStorePath)} goes
     * through the connector's {@code childKeys}. With the fix, a missing table
     * reports "empty" rather than raising {@link ResourceNotFoundException}.
     */
    @Test
    void isEmpty_onMissingTable_returnsTrueCleanly() {
        final String missingTable = "missing_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        final BlobStoreConnector connector = DynamoDbConnector.New(client);
        final BlobStorePath dir = BlobStoreFileSystem.toPath(missingTable, "subdir");

        Assertions.assertTrue(
                connector.isEmpty(dir),
                "missing-table directory must report empty, not raise ResourceNotFoundException");
    }
}
