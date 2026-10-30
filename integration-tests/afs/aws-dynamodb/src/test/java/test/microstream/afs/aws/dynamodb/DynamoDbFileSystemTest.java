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

import org.eclipse.store.afs.aws.dynamodb.types.DynamoDbConnector;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import test.microstream.afs.blobstore.BlobStoreFileSystemTest;

public class DynamoDbFileSystemTest extends BlobStoreFileSystemTest {
    private static final String TABLE_NAME = "microstream-test";

    private final DynamoDbClient client = DynamoContainer.buildClient();


    public DynamoDbFileSystemTest() {
        super(TABLE_NAME);
    }

    @Override
    protected BlobStoreFileSystem setupFileSystem() {

        TypeTest.createTable(this.client, TABLE_NAME);

        return BlobStoreFileSystem.New(
                DynamoDbConnector.Caching(this.client)
        );
    }

    @Override
    protected void cleanupFileSystem() {
        final DeleteTableRequest deleteTableRequest = DeleteTableRequest.builder().tableName(TABLE_NAME).build();
        this.client.deleteTable(deleteTableRequest);
    }


}
