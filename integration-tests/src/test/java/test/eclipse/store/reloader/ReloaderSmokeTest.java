package test.eclipse.store.reloader;

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
import java.util.ArrayList;

import org.eclipse.serializer.persistence.util.Reloader;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import test.eclipse.store.library.TypeEnum;
import test.eclipse.store.library.types.BinaryHandlerTestData;

public class ReloaderSmokeTest
{

    @TempDir
    Path location;

    public static final String constString = "Hello World";

    @Test
    void reloaderBasicTest()
    {
        ArrayList<Integer> emptyArrayList = new ArrayList<>();
        try (EmbeddedStorageManager storage = EmbeddedStorage.start(emptyArrayList, location)) {
            storage.storeRoot();
            Reloader reloader = Reloader.New(storage.persistenceManager());

            emptyArrayList.add(50);
            //storage.store(emptyArrayList);
            reloader.reloadDeep(emptyArrayList);

            Assertions.assertTrue(emptyArrayList.isEmpty());
        }
    }

    @Test
    void reloaderListString()
    {
        ArrayList<String> emptyArrayList = new ArrayList<>();
        try (EmbeddedStorageManager storage = EmbeddedStorage.start(emptyArrayList, location)) {
            Reloader reloader = Reloader.New(storage.persistenceManager());

            emptyArrayList.add(constString);
            //storage.store(emptyArrayList);
            reloader.reloadDeep(emptyArrayList);

            Assertions.assertTrue(emptyArrayList.isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(value = TypeEnum.class, names = {"Lazy"}, mode = EnumSource.Mode.EXCLUDE)
    public void reloadTypeTest(TypeEnum type)
    {
        BinaryHandlerTestData emptyInstance = (BinaryHandlerTestData) type.createEmptyInstance();

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(emptyInstance, location)) {
            emptyInstance.fillSampleData();

            Reloader reloader = Reloader.New(storage.persistenceManager());

            reloader.reloadDeep(emptyInstance);

            emptyInstance.proveResults(type.createEmptyInstance());
        }


    }
}
