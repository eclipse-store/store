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

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.afs.aws.s3.types.S3Connector;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import test.microstream.afs.AbstractTruncationTest;

public class TruncationAfsTest extends AbstractTruncationTest
{

    private static final String blob = "truncation-test";

    private AFileSystem aFileSystem;

    private ADirectory aRootDirectory;

    private S3Client s3Client;

    @Override
    public void initFileSystem()
    {


        try {
            s3Client = S3Container.buildS3Client();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        aFileSystem = BlobStoreFileSystem.New(
                S3Connector.Caching(s3Client));

    }

    @Override
    public ADirectory defineLocation()
    {
        final String bucketName = blob;
        final CreateBucketRequest request = CreateBucketRequest.builder().bucket(bucketName).build();

        final HeadBucketRequest headBucketRequest = HeadBucketRequest.builder().bucket(bucketName).build();
        try {
            s3Client.headBucket(headBucketRequest);
        } catch (final NoSuchBucketException e) {
            s3Client.createBucket(request);
        }
        aRootDirectory = aFileSystem.ensureDirectoryPath(blob);
        return aRootDirectory;
    }

    @Override
    public EmbeddedStorageManager startStorage(Object root)
    {
        return EmbeddedStorage.start(root, aFileSystem.ensureDirectoryPath(blob));
    }

    @Override
    public EmbeddedStorageManager startStorage()
    {

        final HeadBucketRequest headBucketRequest = HeadBucketRequest.builder().bucket(blob).build();
        try {
            s3Client.headBucket(headBucketRequest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return EmbeddedStorage.start(aFileSystem.ensureDirectoryPath(blob));
    }
}
