package test.microstream.afs.azure.storage;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - azure
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
import org.eclipse.store.afs.azure.storage.types.AzureStorageConnector;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.junit.jupiter.api.BeforeAll;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobContainerItem;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import test.microstream.afs.AbstractTypeFileSystemTest;

public class TypeFileSystemTest extends AbstractTypeFileSystemTest {

    private static BlobServiceClient serviceClient;
    private static AFileSystem aFileSystem;

    @Override
    public Object saveToMSAndReload(Object original, Object loaded) {
        String rootName = original.getClass().getSimpleName().toLowerCase();

        final PagedIterable<BlobContainerItem> blobContainerItems = serviceClient.listBlobContainers();
        if (blobContainerItems.stream().filter(b -> b.getName().equals(rootName)).findFirst().orElse(null) == null) {
            serviceClient.createBlobContainer(rootName);
        }

        return super.saveToMSAndReload(original, loaded);
    }

    @BeforeAll
    public static void beforeAll() {
        serviceClient = AzureContainer.createClient();
        aFileSystem = BlobStoreFileSystem.New(AzureStorageConnector.Caching(serviceClient));
    }

    @Override
    public EmbeddedStorageManager startStorage(Object root) {
        return EmbeddedStorage.start(root, aFileSystem.ensureDirectoryPath(root.getClass().getSimpleName().toLowerCase()));
    }
}
