package test.microstream.afs.aws.s3;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - aws-s3
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

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

public class S3Container {

    static final String BUCKET_NAME = "microstream-test";

    public static S3MockContainer container;

    static {
        container = new S3MockContainer("latest").withInitialBuckets(BUCKET_NAME);
        container.start();
    }

    public static S3Client buildS3Client() {

        final AwsBasicCredentials credentials = AwsBasicCredentials.create("accessKey", "secretKey");

        StaticCredentialsProvider provider = StaticCredentialsProvider.create(credentials);

        URI serviceURl = URI.create(container.getHttpEndpoint());

        S3Client s3Client = S3Client.builder()
                .endpointOverride(serviceURl)
                .credentialsProvider(provider)
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true) // Enable path-style access (sometimes necessary for mocked S3)
                        .build())
                .build();

        // withInitialBuckets uses a legacy env var that may not be translated by newer s3mock
        // Docker images — create the bucket explicitly to be safe
        try {
            s3Client.createBucket(b -> b.bucket(BUCKET_NAME));
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ignored) {
            // bucket already exists, nothing to do
        }

        return s3Client;
    }

}
