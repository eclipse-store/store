package test.microstream.afs.jimfs;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - kafka
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

import test.microstream.afs.blobstore.BlobStoreFileSystemTest;

public class KafkaFileSystemTest extends BlobStoreFileSystemTest {


    @Override
    protected BlobStoreFileSystem setupFileSystem() {

        Container.resolveFilesystem();
        return BlobStoreFileSystem.New(
                Container.getKafkaConnector()
        );
    }


    @Override
    protected void cleanupFileSystem() {

    }

}
