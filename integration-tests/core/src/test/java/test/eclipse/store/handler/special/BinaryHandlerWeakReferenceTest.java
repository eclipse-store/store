package test.eclipse.store.handler.special;

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

import java.lang.ref.WeakReference;
import java.nio.file.Path;

import org.eclipse.serializer.persistence.exceptions.PersistenceExceptionTypeNotPersistable;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BinaryHandlerWeakReferenceTest
{

    @TempDir
    Path workDir;
    private EmbeddedStorageManager storage;

    @Test
    void binaryHandlerWeakReferenceTest()
    {
        Integer i = 30;

        WeakReference original = new WeakReference<>(i);
        WeakReference copy = new WeakReference(null);
        Assertions.assertThrows(PersistenceExceptionTypeNotPersistable.class, () -> saveAndReload(original, copy));

    }

    <O> O saveAndReload(O original, O loaded)
    {
        storage = startStorage(original);
        storage.storeRoot();
        storage.shutdown();

        storage = startStorage(loaded);
        return loaded;
    }


    private EmbeddedStorageManager startStorage(Object root)
    {
        return EmbeddedStorage.start(root, workDir);
    }
}
