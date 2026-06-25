package test.eclipse.store.legacy.attribute;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import test.eclipse.store.legacy.legacy.attribute.data.AttPerson;
import test.eclipse.store.legacy.legacy.attribute.data.AttPerson2;

class AttributeBasicTest {

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
    void attributeBasicTest() {
        AttPerson person = new AttPerson("Karel", "May", "Brown","Black");

        storage = EmbeddedStorage.start(person, tempDir);
        storage.shutdown();

        AttPerson2 person2 = new AttPerson2();

        storage = EmbeddedStorage
                .Foundation(tempDir)
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(
                                KeyValue.New("test.eclipse.store.legacy.legacy.attribute.data.AttPerson", "test.eclipse.store.legacy.legacy.attribute.data.AttPerson2"))))
                .setRoot(person2)
                .createEmbeddedStorageManager();


        storage.start();
        assertEquals( person2.getAge(), 0);
        storage.store(person2);
        storage.shutdown();
        person = new AttPerson();

        storage = EmbeddedStorage
                .Foundation(tempDir)
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(
                                KeyValue.New("test.eclipse.store.legacy.legacy.attribute.data.AttPerson2", "test.eclipse.store.legacy.legacy.attribute.data.AttPerson"))))
                .setRoot(person)
                .createEmbeddedStorageManager();

        storage.start();

        assertEquals(person.findEyeColor(), "Brown");
        assertEquals(person.getAge(), 0);
        storage.shutdown();

    }

}
