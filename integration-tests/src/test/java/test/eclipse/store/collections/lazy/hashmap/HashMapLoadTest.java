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

import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.eclipse.serializer.collections.lazy.LazySegmentUnloader;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import test.eclipse.store.library.TypeRegister;
import test.eclipse.store.library.types.LazyData;
import test.eclipse.store.library.types.PrimitiveTypes;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class HashMapLoadTest
{

    @TempDir
    Path location;

    private final static int COUNT = 100;


    @Test
    //@RepeatedTest(1000)
    @Disabled("https://github.com/microstream-one/microstream-private/issues/715")
    void lazyHashMap() throws InterruptedException
    {
        LazyHashMap<Integer, TypeRegister> bigLazyHashMap = new LazyHashMap<>(5, new LazySegmentUnloader.Timed(500));
        Map<Integer, TypeRegister> integerTypeRegisterMap;
        for (int i = 0; i < COUNT; i++) {
            bigLazyHashMap.put(i, new TypeRegister());
        }
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(bigLazyHashMap, location)) {
            bigLazyHashMap.forEach((integer, typeRegister) -> {
                typeRegister.fillSampleDate();
                Storer eagerStorer = manager.createEagerStorer();
                eagerStorer.store(typeRegister);
                eagerStorer.commit();
            });
            manager.store(bigLazyHashMap);
            integerTypeRegisterMap = Map.copyOf(bigLazyHashMap);
        }

        LazyHashMap<Integer, TypeRegister> loadedMap;
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(location)) {
            loadedMap = (LazyHashMap<Integer, TypeRegister>) manager.root();
            System.out.println(loadedMap.size());

            Assertions.assertIterableEquals(integerTypeRegisterMap.entrySet(), loadedMap.entrySet());
        }

    }

    @Test
    void lazyHashMapPrimitive() throws InterruptedException
    {
        LazyHashMap<Integer, LazyData> bigLazyHashMap = new LazyHashMap<>(5, new LazySegmentUnloader.Timed(500));
        Map<Integer, PrimitiveTypes> integerTypeRegisterMap = new HashMap<>();

        for (int i = 0; i < COUNT; i++) {
            bigLazyHashMap.put(i, new LazyData());
        }
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(bigLazyHashMap, location)) {
            bigLazyHashMap.forEach((integer, primitiveTypes) -> {
                primitiveTypes.fillSampleData();
                Storer eagerStorer = manager.createEagerStorer();
                eagerStorer.store(primitiveTypes);
                eagerStorer.commit();
            });
            manager.store(bigLazyHashMap);

            bigLazyHashMap.entrySet().forEach(entry -> {integerTypeRegisterMap.put(entry.getKey(), entry.getValue().getLazy().get());});

        }

        LazyHashMap<Integer, LazyData> loadedMap;
        Map<Integer, PrimitiveTypes> loadedMapWithoutLazy = new HashMap<>();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(location)) {
            loadedMap = (LazyHashMap<Integer, LazyData>) manager.root();


            for (Map.Entry<Integer, LazyData> entry : loadedMap.entrySet()) {
               loadedMapWithoutLazy.put(entry.getKey(), entry.getValue().getLazy().get());
            }

        }

        Assertions.assertIterableEquals(integerTypeRegisterMap.entrySet(), loadedMapWithoutLazy.entrySet());

    }

}
