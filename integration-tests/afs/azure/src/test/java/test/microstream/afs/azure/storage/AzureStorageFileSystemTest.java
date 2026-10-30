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

import org.eclipse.store.afs.azure.storage.types.AzureStorageConnector;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobContainerItem;

import test.microstream.afs.blobstore.BlobStoreFileSystemTest;

public class AzureStorageFileSystemTest extends BlobStoreFileSystemTest {
    public AzureStorageFileSystemTest() {
        super("microstream-test"); //bucket name
    }

    @Override
    protected BlobStoreFileSystem setupFileSystem() {

        final BlobServiceClient serviceClient = AzureContainer.createClient();

        final PagedIterable<BlobContainerItem> blobContainerItems = serviceClient.listBlobContainers();
        if (blobContainerItems.stream().filter(b -> b.getName().equals("microstream-test")).findFirst().orElse(null) == null) {
            serviceClient.createBlobContainer("microstream-test");
        }

        return BlobStoreFileSystem.New(
                AzureStorageConnector.Caching(serviceClient)
        );
    }

    @Override
    protected void cleanupFileSystem() {
    }

}
