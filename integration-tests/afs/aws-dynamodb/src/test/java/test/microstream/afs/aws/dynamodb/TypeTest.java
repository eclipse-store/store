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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.afs.aws.dynamodb.types.DynamoDbConnector;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.junit.jupiter.api.BeforeAll;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import test.microstream.afs.AbstractTypeFileSystemTest;

public class TypeTest extends AbstractTypeFileSystemTest {


    private static DynamoDbClient client;


    @BeforeAll
    public static void beforeAll() {
        client = DynamoContainer.buildClient();
    }

    @Override
    public Object saveToMSAndReload(final Object original, final Object loaded) {
        createTable(client, original.getClass().getSimpleName());
        return super.saveToMSAndReload(original, loaded);
    }

    @Override
    public EmbeddedStorageManager startStorage(final Object root) {
        final AFileSystem aFileSystem = BlobStoreFileSystem.New(DynamoDbConnector.Caching(client));
        return EmbeddedStorage.start(root, aFileSystem.ensureDirectoryPath(root.getClass().getSimpleName()));
    }

    public static void createTable(final DynamoDbClient client, final String tableName) {
        final List<AttributeDefinition> attributes = new ArrayList<>();
        attributes.add(AttributeDefinition.builder().attributeName("key").attributeType(ScalarAttributeType.S).build());
        attributes.add(AttributeDefinition.builder().attributeName("seq").attributeType(ScalarAttributeType.N).build());

        final List<KeySchemaElement> keys = new ArrayList<>();

        keys.add(KeySchemaElement.builder().attributeName("key").keyType(KeyType.HASH).build());
        keys.add(KeySchemaElement.builder().attributeName("seq").keyType(KeyType.RANGE).build());

        client.createTable(CreateTableRequest.builder()
                .tableName(tableName)
                .attributeDefinitions(attributes)
                .keySchema(keys)
                .provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(40L)
                        .writeCapacityUnits(40L)
                        .build())
                .build());
    }


}
