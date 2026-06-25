package test.eclipse.store.legacy.reference;

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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.persistence.types.PersistenceRefactoringMappingProvider;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.serializer.typing.KeyValue;
import test.eclipse.store.legacy.legacy.reference.data.ReferenceAddress;
import test.eclipse.store.legacy.legacy.reference.data.ReferencePerson;
import test.eclipse.store.legacy.legacy.reference.data.ReferencePerson2;

class LegacyReferenceTest {

    @TempDir
    Path tempDir;

    private EmbeddedStorageManager storage;

    @AfterEach
    void cleanStorage() {
        if (null != storage && !storage.isShutdown()) {
            storage.shutdown();
        }
    }

    @Test
    void legacyReferenceTest() {
        ReferenceAddress address = new ReferenceAddress("Fleischgasse 18");
        ReferencePerson person = new ReferencePerson("Karel", "May", "1001", address);


        storage = EmbeddedStorage.start(person, tempDir);
        storage.shutdown();

        ReferencePerson2 person2 = new ReferencePerson2();
        storage = EmbeddedStorage
                .Foundation(tempDir)
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(
                                KeyValue.New("test.eclipse.store.legacy.legacy.reference.data.ReferencePerson", "test.eclipse.store.legacy.legacy.reference.data.ReferencePerson2"))))
                .setRoot(person2)
                .start();

        assertEquals(person.getUserCode(), person2.getUserName());
        assertEquals(person.getAdrress().getStreet(), person2.getAddress2().getStreet());
        storage.shutdown();
    }

}
