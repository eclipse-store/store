package one.microstream.test.afs;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - storage-targets-afs
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.store.afs.aws.s3.types.S3Connector;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

/**
 * Skill patterns B/C adapted to a MinIO S3-compatible backend running in TestContainers.
 *
 * Uses the production-recommended {@link S3Connector#Caching(S3Client)} path.
 * Static credentials are the documented exception for tests (skill recommends
 * credentials.type=default in production).
 */
@Testcontainers
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
@Disabled
class MinioRoundTripTest
{
    private static final String BUCKET = "test-bucket";

    @Container
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:latest");

    @Test
    void roundTripAgainstMinio()
    {
        final S3Client s3 = buildClient();
        try
        {
            createBucket(s3);

            // Seed
            {
                final BlobStoreFileSystem fs        = BlobStoreFileSystem.New(S3Connector.Caching(s3));
                final ADirectory          storageDir = fs.ensureDirectoryPath(BUCKET, "data");

                final Notebook root = new Notebook();
                final EmbeddedStorageManager storage = EmbeddedStorage.start(root, storageDir);
                root.notes().add(new Note(10L, "alpha"));
                root.notes().add(new Note(20L, "beta"));
                root.notes().add(new Note(30L, "gamma"));
                storage.store(root.notes());   // store the modified COLLECTION, not the root
                storage.shutdown();
            }

            // Reopen — fresh filesystem + connector to prove persistence
            {
                final BlobStoreFileSystem fs        = BlobStoreFileSystem.New(S3Connector.Caching(s3));
                final ADirectory          storageDir = fs.ensureDirectoryPath(BUCKET, "data");

                final Notebook root = new Notebook();
                final EmbeddedStorageManager storage = EmbeddedStorage.start(root, storageDir);
                try
                {
                    final Notebook loaded = (Notebook) storage.root();
                    assertEquals(3, loaded.notes().size());
                    assertEquals("alpha", loaded.notes().get(0).text());
                    assertEquals("beta",  loaded.notes().get(1).text());
                    assertEquals("gamma", loaded.notes().get(2).text());
                }
                finally
                {
                    storage.shutdown();
                }
            }
        }
        finally
        {
            s3.close();
        }
    }

    private static S3Client buildClient()
    {
        final AwsBasicCredentials credentials = AwsBasicCredentials.create(
            MINIO.getUserName(),
            MINIO.getPassword()
        );

        return S3Client.builder()
            .endpointOverride(URI.create(MINIO.getS3URL()))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .build();
    }

    private static void createBucket(final S3Client s3)
    {
        try
        {
            s3.createBucket(b -> b.bucket(BUCKET));
        }
        catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ignored)
        {
            // bucket already there
        }
    }
}
