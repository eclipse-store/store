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

import org.eclipse.store.afs.aws.s3.types.S3Connector;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;

import software.amazon.awssdk.services.s3.S3Client;
import test.microstream.afs.blobstore.BlobStoreFileSystemTest;

public class S3FileSystemAdobeEmulatorTest extends BlobStoreFileSystemTest {
    public S3FileSystemAdobeEmulatorTest() {
        super(
                "microstream-test" // bucket-name
        );
    }

    @Override
    protected BlobStoreFileSystem setupFileSystem() {

        S3Client s3Client;

        try {
            s3Client = S3Container.buildS3Client();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        return BlobStoreFileSystem.New(
                S3Connector.Caching(s3Client));
    }

    @Override
    protected void cleanupFileSystem() {
    }

}
