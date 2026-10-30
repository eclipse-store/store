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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.MultipleFailuresError;
import test.microstream.library.TypeRegister;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class TypeRegisterTest {

    @TempDir
    Path storagePath;

    @Test
    public void typeRegisterCreateTest() {
        TypeRegister typeRegister = new TypeRegister();
        typeRegister.fillSampleDate();

        EmbeddedStorageManager storageManager = EmbeddedStorage.start(typeRegister, storagePath);
        storageManager.shutdown();

        TypeRegister copy = new TypeRegister();
        storageManager = EmbeddedStorage.start(copy, storagePath);

        typeRegister.proveData(copy);

        storageManager.shutdown();
    }

    @Test
    public void test_if_assert_work_for_not_equals_object_Test() {
        TypeRegister typeRegister = new TypeRegister();
        typeRegister.fillSampleDate();

        TypeRegister copy = new TypeRegister();

        assertThrows(MultipleFailuresError.class, () -> typeRegister.proveData(copy));
    }

    @Test
    public void get_field_by_name() {
        TypeRegister typeRegister = new TypeRegister();

        Object o = typeRegister.getFieldsByName("arrayListData");

        assertNotNull(o);
    }
}
