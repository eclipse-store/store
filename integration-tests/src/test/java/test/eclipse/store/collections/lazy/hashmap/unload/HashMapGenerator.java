package test.eclipse.store.collections.lazy.hashmap.unload;

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

import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.eclipse.serializer.collections.lazy.LazySegmentUnloader;

import net.datafaker.Faker;

public class HashMapGenerator {

    static Faker faker =  new Faker();

    public static LazyHashMap<Integer, MapPerson> generate(int count, LazySegmentUnloader unloader) {

        return generate(count, unloader, 1000);
    }

    public static LazyHashMap<Integer, MapPerson> generate(int count, LazySegmentUnloader unloader, int segmentSize) {
        LazyHashMap<Integer, MapPerson> persons = new LazyHashMap<>(segmentSize, unloader);
        for (int i = 0; i < count; i++) {
            MapPerson person = new MapPerson();
            person.setFirstname(faker.name().firstName());
            person.setLastname(faker.name().lastName());
            person.setMail(faker.internet().emailAddress());
            person.setIp(faker.internet().ipV4Address());
            persons.put(Integer.valueOf(i), person);
        }
        return persons;
    }
}
