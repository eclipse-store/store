package test.eclipse.store.collections.lazy.arraylist.unload;

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

import java.util.List;

import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.eclipse.serializer.collections.lazy.LazySegmentUnloader;

import net.datafaker.Faker;

public class Generator {

    static final int PERSON_COUNT = 200;

	static Faker faker = new Faker();

    public static List<Person> generatePersons() {
        return generatePersons(PERSON_COUNT, new LazySegmentUnloader.Default());
    }

    public static List<Person> generatePersons(int count, LazySegmentUnloader unloader) {
        return generatePersons(count, unloader, 1000);
    }

    public static List<Person> generatePersons(int count, LazySegmentUnloader unloader, int MaxSegmentSize) {

        List<Person> persons = new LazyArrayList<>(1000, unloader);
        for (int i = 0; i < count; i++) {
            Person person = new Person();
            person.setFirstname(faker.name().firstName());
            person.setLastname(faker.name().lastName());
            person.setMail(faker.internet().emailAddress());
            person.setIp(faker.internet().ipV4Address());
            persons.add(person);
        }
        return persons;
    }
}
