package test.microstream.afs.googlecloud.firestore;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - googlecloud-firestore
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

import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.eclipse.store.afs.googlecloud.firestore.types.GoogleCloudFirestoreConnector;

import com.google.cloud.firestore.Firestore;

import test.microstream.afs.blobstore.BlobStoreFileSystemTest;

public class GoogleCloudFirestoreFileSystemTest extends BlobStoreFileSystemTest {
    public GoogleCloudFirestoreFileSystemTest() {
        super(
                "microstream-test" // bucket-name
        );
    }

    @Override
    protected BlobStoreFileSystem setupFileSystem() {

        final Firestore firestore = FirestoreContainer.createFirestore();

        return BlobStoreFileSystem.New(
                GoogleCloudFirestoreConnector.New(firestore)
        );
    }

    @Override
    protected void cleanupFileSystem() {
    }

}
