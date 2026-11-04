package test.eclipse.store.legacy.primitive;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.persistence.types.PersistenceRefactoringMappingProvider;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.legacy.legacy.primitive.data.PrimitiveLegacy;
import test.eclipse.store.legacy.legacy.primitive.data.PrimitiveLegacy2;

class PrimitiveLegacyTest
{

    @TempDir
    Path location;

    @Test
    void primitiveLegacyTest()
    {

        PrimitiveLegacy primitiveLegacy = PrimitiveLegacy.fillSample();

        EmbeddedStorageManager storage = EmbeddedStorage.start(primitiveLegacy, location);
        storage.shutdown();

        PrimitiveLegacy2 primitiveLegacy2 = new PrimitiveLegacy2();
        storage = EmbeddedStorage
                .Foundation(location)
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(
                                KeyValue.New("test.eclipse.store.legacy.legacy.primitive.data.PrimitiveLegacy", "test.eclipse.store.legacy.legacy.primitive.data.PrimitiveLegacy2"))))
                .setRoot(primitiveLegacy2)
                .start();

        assertEquals(primitiveLegacy.toString(), primitiveLegacy2.toString());
        storage.shutdown();
        primitiveLegacy = new PrimitiveLegacy();

        storage = EmbeddedStorage
                .Foundation(location)
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(
                                KeyValue.New("test.eclipse.store.legacy.legacy.primitive.data.PrimitiveLegacy2", "test.eclipse.store.legacy.legacy.primitive.data.PrimitiveLegacy"))))
                .setRoot(primitiveLegacy)
                .start();

        assertEquals(primitiveLegacy.toString(), primitiveLegacy2.toString());
        storage.shutdown();


    }
}
