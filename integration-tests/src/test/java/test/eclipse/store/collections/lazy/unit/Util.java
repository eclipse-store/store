package test.eclipse.store.collections.lazy.unit;

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

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.eclipse.serializer.collections.lazy.LazyHashSet;
import org.junit.jupiter.api.Assertions;

import net.datafaker.Faker;

public class Util {

    static Faker faker = new Faker();

    public static LazyHashMap<Integer, String> generateMap(final Integer count) {
        return generateMap(count, 0);
    }

    public static LazyHashMap<Integer, String> generateMap(final Integer count, final int keyStart) {

        final LazyHashMap<Integer, String> map = new LazyHashMap<>(10);

        return fillHashMap(map, count, keyStart);
    }

    public static LazyHashMap<Integer, String> fillHashMap(final LazyHashMap<Integer, String> map, final int count, final int keyStart) {
        for (int i = 0; i < count; i++) {
            map.put(i + keyStart, faker.lorem()
                    .sentence());
        }
        return map;
    }

    public static HashMap<Integer, String> generateHashMap(final Integer count) {
        return generateHashMap(count, 0);
    }

    public static HashMap<Integer, String> generateHashMap(final Integer count, final int keyStart) {

        final HashMap<Integer, String> map = new HashMap<>();


        for (int i = 0; i < count; i++) {
            map.put(i + keyStart, faker.lorem()
                    .sentence());
        }

        return map;
    }

    public static LazyArrayList<String> generateLazyArrayList(final int segmentSize, final int count) {
        final LazyArrayList<String> list = new LazyArrayList<>(segmentSize);
        for (int i = 0; i < count; i++) {
            list.add(faker.lorem().sentence());
        }
        return list;
    }

    public static ArrayList<String> generateArrayList(final int count) {
        final ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(faker.lorem().sentence());
        }
        return list;
    }

    public static LazyHashSet<String> generateLazyHashSet(final int segmentSize, final int count) {
        final LazyHashSet<String> lazyHashSet = new LazyHashSet<>(segmentSize);
        for (int i = 0; i <count; i++) {
            lazyHashSet.add(i +  faker.lorem().sentence());
        }
        Assertions.assertEquals(count, lazyHashSet.size(), "Size of LazyHashSet is not as expected");
        return lazyHashSet;
    }

}
