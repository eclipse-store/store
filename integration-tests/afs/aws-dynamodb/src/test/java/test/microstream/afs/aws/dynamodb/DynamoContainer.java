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

import java.net.URI;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoContainer {

    private static final GenericContainer dynamoDB;

    static {
        dynamoDB = new GenericContainer(DockerImageName.parse("amazon/dynamodb-local:latest"))
                .withExposedPorts(8000);
        dynamoDB.start();
    }
    
    public static DynamoDbClient buildClient() {
        final AwsBasicCredentials credentials = AwsBasicCredentials.create(
                "accessKey",
                "secretKey"
        );

        final StaticCredentialsProvider provider = StaticCredentialsProvider.create(credentials);
        DynamoDbClient client;
        try {
            client = DynamoDbClient.builder()
                    .endpointOverride(new URI("http://" + dynamoDB.getHost() + ":" + dynamoDB.getFirstMappedPort()))
                    .region(Region.EU_CENTRAL_1)
                    .credentialsProvider(provider)
                    .build();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        return client;

    }
}
