package org.eclipse.store.gigamap.restart;

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

import com.github.javafaker.Faker;
import org.eclipse.store.gigamap.types.*;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class WriteReadPersonTest
{

    final static int AMOUNT = 1_000;

    @TempDir
    static Path newDirectory;

    static Faker faker = new Faker();


    @BeforeAll
    static void writeTest()
    {
        GigaMap<Patient> gigaMap = GigaMap.New();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            if (manager.root() == null) {
                manager.setRoot(gigaMap);
            } else {
                gigaMap = (GigaMap<Patient>) manager.root();
            }
            manager.storeRoot();

            for (int i = 0; i < AMOUNT; i++) {
                gigaMap.add(Patient.createRandomPatient());
            }

            //add Patient with name Joe
            gigaMap.add(new Patient("Joe", "Doe", 30, "address", "city", "country", "email", "phone", 'M'));

            //add Patient with name Karl
            gigaMap.add(new Patient("Karl", "Doe", 39, "address", "city", "country", "email", "phone", 'M'));
            gigaMap.store();

        }
    }

    @Test
    void readFromRepositoryTest()
    {
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            final GigaMap<Patient> gigaMap = (GigaMap<Patient>) manager.root();
            assertEquals(AMOUNT + 2, gigaMap.size());
        }
    }

    @Test
    void addIndex()
    {
        GigaMap<Patient> gigaMap = GigaMap.New();

        final PatientIndexer patientIndexer = new PatientIndexer();
        final PatientIndexerFaxIndexer patientIndexerFaxIndexer = new PatientIndexerFaxIndexer();
        final PatientSexIndexer patientSexIndexer = new PatientSexIndexer();
        final PatientAgeIndexer patientAgeIndexer = new PatientAgeIndexer();

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newDirectory)) {
            if (manager.root() == null) {
                manager.setRoot(gigaMap);
            } else {
                gigaMap = (GigaMap<Patient>) manager.root();
            }

            final BitmapIndices<Patient>       register = gigaMap.index().bitmap();
            final BitmapIndex<Patient, String> indexer  = register.get("PatientIndexer");
            if (indexer == null) {
                register.add(patientIndexer);
            }

            final BitmapIndex<Patient, String> indexerFax = register.get("PatientIndexerFaxIndexer");
            if (indexerFax == null) {
                register.add(patientIndexerFaxIndexer);
            }

            final BitmapIndex<Patient, Character> patientSexIndexerExists = register.get(Character.class, "PatientSexIndexer");
            if (patientSexIndexerExists == null) {
                register.add(patientSexIndexer);
            }

            final BitmapIndex<Patient, Integer> patientAgeIndexerExists = register.get(Integer.class, "PatientAgeIndexer");
            if (patientAgeIndexerExists == null) {
                register.add(patientAgeIndexer);
            }


            final AtomicInteger count = new AtomicInteger();
            count.set(0);

            gigaMap.query(patientIndexer.is("Joe")).execute((patient) -> count.incrementAndGet());
            assertNotEquals(0, count.get());

            count.set(0);
            gigaMap.query(patientIndexer.is("Karl")).execute(patient -> count.incrementAndGet());
            assertNotEquals(0, count.get());

            count.set(0);
            gigaMap.query(patientIndexer.is("Karl")).and(patientAgeIndexer.is(39)).execute(patient -> count.incrementAndGet());
            assertNotEquals(0, count.get());

            gigaMap.store();
        }
    }

    static class PatientIndexer extends IndexerString.Abstract<Patient>
    {
        @Override
        protected String getString(final Patient entity)
        {
            return entity.name;
        }
    }

    static class PatientIndexerFaxIndexer extends IndexerString.Abstract<Patient>
    {
        @Override
        protected String getString(final Patient entity)
        {
            return null;
        }
    }

    static class PatientSexIndexer extends IndexerCharacter.Abstract<Patient>
    {
        @Override
        protected Character getCharacter(final Patient entity)
        {
            return entity.c;
        }
    }

    static class PatientAgeIndexer extends IndexerInteger.Abstract<Patient>
    {
        @Override
        protected Integer getInteger(final Patient entity)
        {
            if (entity == null) {
                return null;
            }
            return entity.age;
        }
    }


    static class Patient
    {
        String name;
        String secondName;
        int age;
        String address;
        String city;
        String country;
        String email;
        String phone;
        String fax;
        Character c;

        public Patient(final String name, final String secondName, final int age, final String address, final String city, final String country, final String email, final String phone, final Character c)
        {
            this.name = name;
            this.secondName = secondName;
            this.age = age;
            this.address = address;
            this.city = city;
            this.country = country;
            this.email = email;
            this.phone = phone;
            this.c = c;
        }

        static Patient createRandomPatient()
        {
            return new Patient(
                    faker.name().firstName(),
                    faker.name().lastName(),
                    faker.number().numberBetween(20, 100),
                    faker.address().streetAddress(),
                    faker.address().city(),
                    faker.address().country(),
                    faker.internet().emailAddress(),
                    faker.phoneNumber().phoneNumber(),
                    faker.number().numberBetween(0, 1) == 0 ? 'M' : 'F'
            );
        }

        @Override
        public String toString()
        {
            return "Patient{" +
                    "name='" + this.name + '\'' +
                    ", secondName='" + this.secondName + '\'' +
                    ", age=" + this.age +
                    ", address='" + this.address + '\'' +
                    ", city='" + this.city + '\'' +
                    ", country='" + this.country + '\'' +
                    ", email='" + this.email + '\'' +
                    ", phone='" + this.phone + '\'' +
                    ", fax='" + this.fax + '\'' +
                    ", c=" + this.c +
                    '}';
        }
    }

}
