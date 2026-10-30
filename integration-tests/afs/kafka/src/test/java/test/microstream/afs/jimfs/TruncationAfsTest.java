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

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import test.microstream.afs.AbstractTruncationTest;

public class TruncationAfsTest extends AbstractTruncationTest
{


    private AFileSystem aFileSystem;

    private ADirectory aRootDirectory;

    @Override
    public void initFileSystem()
    {

        aFileSystem = Container.resolveFilesystem();
    }

    @Override
    public ADirectory defineLocation()
    {
        aRootDirectory = aFileSystem.ensureDirectoryPath(this.getClass().getSimpleName());
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
