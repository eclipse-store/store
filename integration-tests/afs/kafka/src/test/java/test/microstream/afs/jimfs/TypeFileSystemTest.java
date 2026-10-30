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

import org.eclipse.serializer.afs.types.AFileSystem;
import org.junit.jupiter.api.BeforeAll;

import test.microstream.afs.AbstractTypeFileSystemTest;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

public class TypeFileSystemTest extends AbstractTypeFileSystemTest {

    private static AFileSystem aFileSystem;

    @BeforeAll
    public static void beforeAll() {
        aFileSystem = Container.resolveFilesystem();
    }

    @Override
    public EmbeddedStorageManager startStorage(Object root) {

        return EmbeddedStorage.start(root, aFileSystem.ensureDirectoryPath(root.getClass().getSimpleName()));
    }

}
