package test.eclipse.store.legacy.delete;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.legacy.legacy.delete.data.DellPerson;
import test.eclipse.store.legacy.legacy.delete.data.DellPerson2;

class DeleteFieldTest
{

    @TempDir
    Path tempDir;

    private EmbeddedStorageManager storage;

    private static final String FIRST_NAME = "Karel";
    private static final String FULL_NAME = "Karel-May";

    @AfterEach
    void cleanStorage()
    {
        if (null != storage && !storage.isShutdown()) {
            storage.shutdown();
        }
    }

    @Test
    void removeMemberBasicTest()
    {
        DellPerson person = new DellPerson(FIRST_NAME, "May", FULL_NAME, "Brown", "Black");

        storage = EmbeddedStorage.start(person, tempDir);
        storage.shutdown();

        DellPerson2 person2 = new DellPerson2();

        storage = EmbeddedStorage
                .Foundation(tempDir)
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(
                                KeyValue.New("test.eclipse.store.legacy.legacy.delete.data.DellPerson", "test.eclipse.store.legacy.legacy.delete.data.DellPerson2"))))
                .setRoot(person2)
                .createEmbeddedStorageManager();


        storage.start();
        assertEquals(person2.getAge(), 0);
        assertEquals(FIRST_NAME, person2.getFirstName());
        assertEquals(FULL_NAME, person2.getFullName());
        storage.store(person2);
        storage.shutdown();
        person = new DellPerson();

        storage = EmbeddedStorage
                .Foundation(tempDir)
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(
                                KeyValue.New("test.eclipse.store.legacy.legacy.delete.data.DellPerson2", "test.eclipse.store.legacy.legacy.delete.data.DellPerson"))))
                .setRoot(person)
                .createEmbeddedStorageManager();

        storage.start();

        assertEquals(person.findEyeColor(), "Brown");
        assertEquals(person.getAge(), 0);
        assertEquals(FIRST_NAME, person2.getFirstName());
        assertEquals(FULL_NAME, person2.getFullName());
        storage.shutdown();

    }

}
