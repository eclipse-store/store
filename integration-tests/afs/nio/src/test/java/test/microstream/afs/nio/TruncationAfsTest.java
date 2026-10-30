package test.microstream.afs.nio;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - nio
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
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.io.TempDir;
import test.microstream.afs.AbstractTruncationTest;

import java.nio.file.Path;

public class TruncationAfsTest extends AbstractTruncationTest
{
    @TempDir
    Path location;

    NioFileSystem fileSystem;

    @Override
    public void initFileSystem()
    {
        fileSystem = NioFileSystem.New();
    }

    @Override
    public ADirectory defineLocation()
    {
        return fileSystem.ensureDirectory(location);
    }

    @Override
    public EmbeddedStorageManager startStorage(Object root)
    {
        return EmbeddedStorage.start(root, location);
    }

    @Override
    public EmbeddedStorageManager startStorage()
    {
        return EmbeddedStorage.start(location);
    }
}
