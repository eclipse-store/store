package test.eclipse.store.collections.lazy.hashmap;

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

import java.nio.file.Path;
import java.util.HashMap;

import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import net.datafaker.Faker;

public class Util
{

    static Faker faker = new Faker();

    public static LazyHashMap<Integer, String> generateMap(Integer count)
    {
        return generateMap(count, 0);
    }

    public static LazyHashMap<Integer, String> generateMap(Integer count, int keyStart)
    {

        LazyHashMap<Integer, String> map = new LazyHashMap<>();


        for (int i = 0; i < count; i++) {
            map.put(i + keyStart, faker.lorem()
                    .sentence());
        }

        return map;
    }

    public static HashMap<Integer, String> generateHashMap(Integer count)
    {
        return generateHashMap(count, 0);
    }

    public static HashMap<Integer, String> generateHashMap(Integer count, int keyStart)
    {

        HashMap<Integer, String> map = new HashMap<>();


        for (int i = 0; i < count; i++) {
            map.put(i + keyStart, faker.lorem()
                    .sentence());
        }

        return map;
    }

    public static <K, V> EmbeddedStorageManager startStorage(LazyHashMap<K, V> root, final Path path)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage
                .Foundation(path)
                .start(root);
        return storage;
    }

    public static <K, V> EmbeddedStorageManager startStorage(final Path path)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage
                .Foundation(path)
                .start();
        return storage;
    }

}
