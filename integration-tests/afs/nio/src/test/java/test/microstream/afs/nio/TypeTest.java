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

import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;

import test.microstream.afs.AbstractTypeFileSystemTest;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

public class TypeTest extends AbstractTypeFileSystemTest {

    @TempDir
    static Path targetDirectory;

    static Path actualDirectory;

    @Override
    public Object saveToMSAndReload(Object original, Object loaded) {
        actualDirectory = targetDirectory.resolve(String.valueOf(System.currentTimeMillis() ));
        return super.saveToMSAndReload(original, loaded);
    }

    @Override
    public EmbeddedStorageManager startStorage(Object root) {
        return EmbeddedStorage.start(root, actualDirectory);
    }

}
