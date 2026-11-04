package test.eclipse.store.legacy.csv;

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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.legacy.csv.data.CsvPerson;
import test.eclipse.store.legacy.csv.data.CsvPerson2;
import test.eclipse.store.legacy.csv.data.DeletePerson;
import test.eclipse.store.legacy.csv.data.DeletePerson2;

class CsvLegacyTest
{

    @TempDir
    Path tempDir;

    private EmbeddedStorageManager storage;

    @AfterEach
    void cleanStorage()
    {
        if (null != storage && !storage.isShutdown()) {
            storage.shutdown();
        }
    }

    @Test
    void legacyDirectTest() throws URISyntaxException
    {
        CsvPerson person = new CsvPerson("Karel", "May", "1001", "blue");


        storage = EmbeddedStorage.start(person, tempDir);
        storage.shutdown();

        CsvPerson2 person2 = new CsvPerson2();
        URL url = this.getClass().getClassLoader().getResource("legacy/refactoring.csv");
        assertNotNull(url);
        Path path = new File(url.toURI()).toPath();

        storage = EmbeddedStorage
                .Foundation(tempDir)
                .setRefactoringMappingProvider(Persistence.RefactoringMapping(path))
                .setRoot(person2)
                .start();

        assertEquals(person.getFirstName(), person2.getFirstName());
        assertEquals(person.getOriginal(), person2.getCopy());
        assertNull(person2.getTittle());
        storage.shutdown();
    }

    @Test
    void legacyRemoveFieldCSVTest() throws URISyntaxException
    {
        DeletePerson person = new DeletePerson("Karel", "May", "1001", "blue");


        storage = EmbeddedStorage.start(person, tempDir);
        storage.shutdown();

        DeletePerson2 person2 = new DeletePerson2();
        URL url = this.getClass().getClassLoader().getResource("legacy/delete.csv");
        assertNotNull(url);
        Path path = new File(url.toURI()).toPath();

        storage = EmbeddedStorage
                .Foundation(tempDir)
                .setRefactoringMappingProvider(Persistence.RefactoringMapping(path))
                .setRoot(person2)
                .start();

        assertEquals(person.getFirstName(), person2.getFirstName());
        assertEquals(person.getFullName(), person2.getFullName());
        storage.shutdown();
    }

    @Test
    void legacyAddFieldCSVTest() throws URISyntaxException
    {
        DeletePerson2 person = new DeletePerson2("Karel", "May", "1001");


        storage = EmbeddedStorage.start(person, tempDir);
        storage.shutdown();

        DeletePerson person2 = new DeletePerson();
        URL url = this.getClass().getClassLoader().getResource("legacy/add.csv");
        assertNotNull(url);
        Path path = new File(url.toURI()).toPath();

        storage = EmbeddedStorage
                .Foundation(tempDir)
                .setRefactoringMappingProvider(Persistence.RefactoringMapping(path))
                .setRoot(person2)
                .start();

        assertEquals(person.getFirstName(), person2.getFirstName());
        assertEquals(person.getFullName(), person2.getFullName());
        storage.shutdown();
    }

}
