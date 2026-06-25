package test.eclipse.store.legacy.data;

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
import test.eclipse.store.legacy.legacy.basic.data.Person;
import test.eclipse.store.legacy.legacy.basic.data.Person2;

class LegacyBasicTest {

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
    void legacyDirectTest() {
        Person person = new Person("Karel", "May", "1001");


        storage = EmbeddedStorage.start(person, tempDir);
        storage.shutdown();

        Person2 person2 = new Person2();
        storage = EmbeddedStorage
                .Foundation(tempDir)
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(
                                KeyValue.New("test.eclipse.store.legacy.legacy.basic.data.Person", "test.eclipse.store.legacy.legacy.basic.data.Person2"))))
                .setRoot(person2)
                .start();

        assertEquals(person.getUserCode(), person2.getUserName());
        storage.shutdown();
    }

}
