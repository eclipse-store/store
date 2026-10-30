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

import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.eclipse.store.afs.googlecloud.firestore.types.GoogleCloudFirestoreConnector;
import org.junit.jupiter.api.BeforeAll;

import com.google.cloud.firestore.Firestore;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import test.microstream.afs.AbstractTypeFileSystemTest;

public class GoogleCloudFirestoreTypeTest extends AbstractTypeFileSystemTest {

    private static AFileSystem aFileSystem;

    @BeforeAll
    static void beforeAll(){

        final Firestore firestore = FirestoreContainer.createFirestore();
        aFileSystem = BlobStoreFileSystem.New(
                GoogleCloudFirestoreConnector.New(firestore)
        );
    }

    @Override
    public EmbeddedStorageManager startStorage(Object root) {
        return EmbeddedStorage.start(root, aFileSystem.ensureDirectoryPath(root.getClass().getSimpleName()));
    }
}
