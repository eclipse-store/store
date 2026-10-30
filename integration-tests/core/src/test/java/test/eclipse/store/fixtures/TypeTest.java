package test.eclipse.store.fixtures;

/*-
 * #%L
 * EclipseStore Integration Tests - Core
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

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import test.microstream.library.TypeEnum;
import test.microstream.library.types.BinaryHandlerTestData;

import java.nio.file.Path;

public class TypeTest {

    @TempDir
    static Path targetDirectory;

    static Path actualDirectory;

    EmbeddedStorageManager storage;

    @ParameterizedTest
    @EnumSource(TypeEnum.class)
    public void saveAndLoadTest(TypeEnum type) {
        System.out.println(type.getOriginal().getClass().getSimpleName());

        actualDirectory = targetDirectory.resolve(String.valueOf(System.currentTimeMillis() ));

        Object o = saveToMSAndReload(type.getOriginal(),actualDirectory);

        type.getOriginal().proveResults(o);

        storageShutdown();
    }

    @ParameterizedTest
    @EnumSource(TypeEnum.class)
    public void saveUpdateLoadTest(TypeEnum type) {
        System.out.println(type.getOriginal().getClass().getSimpleName());

        actualDirectory = targetDirectory.resolve(String.valueOf(System.currentTimeMillis() ));

        saveToMS(type.getOriginal(), actualDirectory);

        Object loaded = saveToMSAndReload(type.getOriginal().updateSampleData(), actualDirectory);

        type.getOriginal().proveResults(loaded);

        storageShutdown();
    }

    @ParameterizedTest
    @EnumSource(value = TypeEnum.class, names = {"Lazy"}, mode = EnumSource.Mode.EXCLUDE)
    public void loadDefaultCompare(TypeEnum type) {
        BinaryHandlerTestData emptyInstance = (BinaryHandlerTestData) type.createEmptyInstance();
        BinaryHandlerTestData controlInstance = (BinaryHandlerTestData) type.createEmptyInstance();
        emptyInstance.proveResults(controlInstance);
    }



    public void storageShutdown() {
        storage.shutdown();
    }

    public EmbeddedStorageManager startStorage(Object root, Path actualDirectory) {
        return EmbeddedStorage.start(root, actualDirectory);
    }

    public Object saveToMSAndReload(Object original, Path actualDirectory) {

        saveToMS(original, actualDirectory);

        storage = EmbeddedStorage.start(actualDirectory);
        return storage.root();
    }

    public void saveToMS(Object original, Path actualDirectory) {

        storage = startStorage(original, actualDirectory);
        storage.storeRoot();
        storage.shutdown();
    }
}
