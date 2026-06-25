package test.eclipse.store.legacy.cross;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.persistence.types.PersistenceRefactoringMappingProvider;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.serializer.typing.KeyValue;

abstract class AbstractLegacyTest {

    @TempDir
    Path location;

    protected String classPackage = "test.eclipse.store.legacy.legacy.cross.data";

    protected EmbeddedStorageManager startStorage(Object root, String oldClass, String newClass) {
        return EmbeddedStorage
                .Foundation(location)
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(KeyValue.New(oldClass, newClass))))
                .setRoot(root)
                .start();
    }
}
