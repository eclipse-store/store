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

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobContainerItem;
import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.afs.azure.storage.types.AzureStorageConnector;
import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import test.microstream.afs.AbstractTruncationTest;

public class TruncationAfsTest extends AbstractTruncationTest
{

    private static final String blob = "truncation-test";

    private AFileSystem aFileSystem;

    private ADirectory aRootDirectory;

    @Override
    public void initFileSystem()
    {
        final BlobServiceClient serviceClient = AzureContainer.createClient();

        final PagedIterable<BlobContainerItem> blobContainerItems = serviceClient.listBlobContainers();
        if (blobContainerItems.stream().filter(b -> b.getName().equals(blob)).findFirst().orElse(null) == null) {
            serviceClient.createBlobContainer(blob);
        }

        aFileSystem = BlobStoreFileSystem.New(
                AzureStorageConnector.Caching(serviceClient)
        );

    }

    @Override
    public ADirectory defineLocation()
    {
        aRootDirectory = aFileSystem.ensureDirectoryPath(blob);
        return aRootDirectory;
    }

    @Override
    public EmbeddedStorageManager startStorage(Object root)
    {
        return EmbeddedStorage.start(root, aRootDirectory);
    }

    @Override
    public EmbeddedStorageManager startStorage()
    {
        return EmbeddedStorage.start(aRootDirectory);
    }
}
