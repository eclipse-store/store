package test.eclipse.store.handler;

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

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import test.eclipse.serializer.fixtures.TypeEnum;

public class BinaryHandlerTest
{

    @TempDir
    Path storagePath;

    @ParameterizedTest
    @EnumSource(TypeEnum.class)
    public void saveAndLoadTest(TypeEnum type)
    {

        final var original = type.getOriginal();
        EmbeddedStorageManager storageManager = EmbeddedStorage.start(original, storagePath);
        storageManager.shutdown();

        storageManager = EmbeddedStorage.start(storagePath);
        Object o = storageManager.root();

        original.proveResults(o);

        storageManager.shutdown();
    }

    /**
     * Tests data types without storing data in them. e.g. new ArrayList<>()
     *
     * @param type Injected by JunitFramework
     */
    @ParameterizedTest
    @EnumSource(TypeEnum.class)
    public void saveAndLoadEmptyClassTest(TypeEnum type)
    {

        EmbeddedStorageManager storageManager = EmbeddedStorage.start(type.createEmptyInstance(), storagePath);
        storageManager.shutdown();

        storageManager = EmbeddedStorage.start(type.createEmptyInstance(), storagePath);

        storageManager.shutdown();
    }

}
