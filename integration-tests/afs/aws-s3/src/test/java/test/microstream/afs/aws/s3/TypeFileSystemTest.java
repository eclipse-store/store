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

import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.afs.aws.s3.types.S3Connector;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.junit.jupiter.api.BeforeAll;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import test.microstream.afs.AbstractTypeFileSystemTest;

public class TypeFileSystemTest extends AbstractTypeFileSystemTest {

    private static  S3Client s3Client;
    private static AFileSystem aFileSystem;


    @BeforeAll
    public static void prepare() {
        s3Client = S3Container.buildS3Client();
        aFileSystem = BlobStoreFileSystem.New(
                S3Connector.Caching(s3Client)
        );

    }

    @Override
    public EmbeddedStorageManager startStorage(final Object root) {

        final String bucketName = root.getClass().getSimpleName().toLowerCase();
        final CreateBucketRequest request = CreateBucketRequest.builder().bucket(bucketName).build();

        final HeadBucketRequest headBucketRequest = HeadBucketRequest.builder().bucket(bucketName).build();
        try {
            s3Client.headBucket(headBucketRequest);
        } catch (final NoSuchBucketException e) {
            s3Client.createBucket(request);
        }

        return EmbeddedStorage.start(root, aFileSystem.ensureDirectoryPath(bucketName));
    }

}
