package org.eclipse.store.gigamap.indexer;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.store.gigamap.types.*;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MultipleValueIndexTest
{

    @TempDir
    Path tempDir;

    @Test
    void indexEntity_UnsupportedOperationException()
    {
        PatientIdentifierIndexer patientIdentifierIndexer = new PatientIdentifierIndexer();

        assertThrows(UnsupportedOperationException.class, () -> patientIdentifierIndexer.index(null));
    }

    @Test
    void allTest()
    {

        class StringIndexer extends IndexerString.Abstract<String>
        {
            @Override
            protected String getString(String entity)
            {
                return entity;
            }
        }

        GigaMap<Patient>         gigaMap                  = GigaMap.New();
        PatientIdentifierIndexer patientIdentifierIndexer = new PatientIdentifierIndexer();
        gigaMap.index().bitmap().add(patientIdentifierIndexer);
        Patient patient1 = new Patient("John", 25, List.of("123", "456"));
        Patient patient2 = new Patient("Jane", 30, List.of("123", "789"));
        Patient patient3 = new Patient("Jack", 35, List.of("123", "456", "789"));
        gigaMap.addAll(patient1, patient2, patient3);

        GigaMap<String> keysMap = GigaMap.New();
        StringIndexer stringIndexer = new StringIndexer();
        keysMap.index().bitmap().add(stringIndexer);
        keysMap.addAll("123", "456");

        GigaQuery<String> query = keysMap.query(stringIndexer.in("123", "456"));

        long count = gigaMap.query(patientIdentifierIndexer.all(query)).count();
        assertEquals(2, count);
    }

    @Test
    void multivalueUpdateTest()
    {
        GigaMap<Patient>         gigaMap                  = GigaMap.New();
        PatientIdentifierIndexer patientIdentifierIndexer = new PatientIdentifierIndexer();
        gigaMap.index().bitmap().add(patientIdentifierIndexer);
        Patient patient1 = new Patient("John", 25, List.of("123", "456"));
        Patient patient2 = new Patient("Jane", 30, List.of("123", "789"));
        gigaMap.addAll(patient1, patient2);

        gigaMap.update(patient1, p -> {
             p.setIdentifiers(List.of("123", "abc"));
        });

        assertEquals(1L, gigaMap.query(patientIdentifierIndexer.is("abc")).count());

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(gigaMap, tempDir))
        {
        }

        GigaMap<Patient> loadedGigaMap = GigaMap.New();
        try (EmbeddedStorageManager storage = EmbeddedStorage.start(loadedGigaMap, tempDir)) {
            Patient patient = loadedGigaMap.get(loadedGigaMap.highestUsedId());
            BitmapIndex<Patient, String> loadedIndex = loadedGigaMap.index().bitmap().get("org.eclipse.store.gigamap.indexer.MultipleValueIndexTest.PatientIdentifierIndexer");
            assertNotNull(loadedIndex);
            loadedGigaMap.update(patient, p -> {
                p.setIdentifiers(List.of("123", "xxx"));
            });
            loadedGigaMap.store();
            assertEquals(1L, loadedGigaMap.query(loadedIndex.is("xxx")).count());
        }
    }

    @Test
    void multivalueRemoveTest()
    {
        GigaMap<Patient>         gigaMap                  = GigaMap.New();
        PatientIdentifierIndexer patientIdentifierIndexer = new PatientIdentifierIndexer();
        gigaMap.index().bitmap().add(patientIdentifierIndexer);
        Patient patient1 = new Patient("John", 25, List.of("123", "456"));
        Patient patient2 = new Patient("Jane", 30, List.of("123", "789"));
        gigaMap.addAll(patient1, patient2);

        gigaMap.remove(patient1);

        assertEquals(1L, gigaMap.size());

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(gigaMap, tempDir))
        {
        }

        GigaMap<Patient> loadedGigaMap = GigaMap.New();
        try (EmbeddedStorageManager storage = EmbeddedStorage.start(loadedGigaMap, tempDir)) {
            Patient patient = loadedGigaMap.get(loadedGigaMap.highestUsedId());
            loadedGigaMap.remove(patient);
            loadedGigaMap.store();
            assertTrue(loadedGigaMap.isEmpty());

            Patient patient3 = new Patient("Jack", 35, List.of("123", "456"));
            loadedGigaMap.add(patient3);
            loadedGigaMap.store();
        }
    }

    private static class PatientIdentifierIndexer extends IndexerMultiValue.Abstract<Patient, String> {

        @Override
        public Iterable<? extends String> indexEntityMultiValue(Patient entity)
        {
            return entity.getIdentifiers();
        }

        @Override
        public Class<String> keyType()
        {
            return String.class;
        }
    }

    private static class Patient{
        private String name;
        private final int age;
        private List<String> identifiers;

        public Patient(String name, int age, List<String> identifiers)
        {
            this.name = name;
            this.age = age;
            this.identifiers = identifiers;
        }

        public String getName()
        {
            return name;
        }

        public int getAge()
        {
            return age;
        }

        public List<String> getIdentifiers()
        {
            return identifiers;
        }

        public void setIdentifiers(final List<String> identifiers)
        {
            this.identifiers = identifiers;
        }

        public void setName(String name)
        {
            this.name = name;
        }
    }
}
