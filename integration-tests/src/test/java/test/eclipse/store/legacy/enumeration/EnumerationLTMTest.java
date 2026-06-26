package test.eclipse.store.legacy.enumeration;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.persistence.exceptions.PersistenceExceptionConsistencyObjectId;
import org.eclipse.serializer.persistence.types.PersistenceRefactoringMappingProvider;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.legacy.legacy.enumeration.data.EnumerationCopy;
import test.eclipse.store.legacy.legacy.enumeration.data.EnumerationOrig;

public class EnumerationLTMTest
{


    @TempDir
    Path tempDir;

    private EmbeddedStorageManager storage;


    @AfterEach
    void cleanStorage()
    {
        if (null != storage && !storage.isShutdown()) {
            storage.shutdown();
        }
    }

    @Test
    void removeMemberBasicTest()
    {
        EnumerationOrig eOrig = EnumerationOrig.FIRST;

        storage = EmbeddedStorage.start(eOrig, tempDir);
        storage.shutdown();


        storage = EmbeddedStorage.start(eOrig, tempDir);
        storage.shutdown();


        EnumerationCopy eCopy = EnumerationCopy.SECOND;

        storage = EmbeddedStorage
                .Foundation(tempDir)
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(
                                KeyValue.New("test.eclipse.store.legacy.legacy.enumeration.data.EnumerationOrig", "test.eclipse.store.legacy.legacy.enumeration.data.EnumerationCopy"))))
                .setRoot(eCopy)
                .createEmbeddedStorageManager();


        assertThrows(PersistenceExceptionConsistencyObjectId.class, () -> storage.start());
        // TODO could be changed after this issue will be resolved: https://github.com/microstream-one/internal/issues/45

//        assertEquals( EnumerationOrig.FIRST.getName(), eCopy.getName());
//        assertEquals( EnumerationOrig.FIRST.getValue(), eCopy.getValue());
//        storage.store(eCopy);
//        storage.shutdown();
//        eOrig = EnumerationOrig.SECOND;
//
//        storage = EmbeddedStorage
//                .Foundation(tempDir)
//                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
//                        EqHashTable.New(
//                                KeyValue.New("test.eclipse.store.legacy.legacy.enumeration.data.EnumerationCopy", "test.eclipse.store.legacy.legacy.enumeration.data.EnumerationOrig"))))
//                .setRoot(eOrig)
//                .createEmbeddedStorageManager();
//
//        storage.start();
//
//        assertEquals(EnumerationOrig.FIRST.getName(), eOrig.getName());
//        assertEquals(EnumerationOrig.FIRST.getValue(), eOrig.getValue());
//
//        storage.shutdown();

    }

    // -------------------------------------------------------------------------
    // Same scenario, but enum is wrapped inside a class behind a Lazy reference
    // -------------------------------------------------------------------------

    @Test
    void removeMemberBasicTest_lazyWrapped()
    {
        LazyEnumWrapper<EnumerationOrig> origWrapper = new LazyEnumWrapper<>(EnumerationOrig.FIRST);

        storage = EmbeddedStorage.start(origWrapper, tempDir);
        storage.shutdown();

        storage = EmbeddedStorage.start(origWrapper, tempDir);
        storage.shutdown();

        LazyEnumWrapper<EnumerationCopy> copyWrapper = new LazyEnumWrapper<>(EnumerationCopy.SECOND);

        storage = EmbeddedStorage
                .Foundation(tempDir)
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(
                                KeyValue.New("test.eclipse.store.legacy.legacy.enumeration.data.EnumerationOrig", "test.eclipse.store.legacy.legacy.enumeration.data.EnumerationCopy"))))
                .setRoot(copyWrapper)
                .createEmbeddedStorageManager();

        storage.start();
        EnumerationCopy eCopy = copyWrapper.lazy.get();
        assertEquals(EnumerationOrig.FIRST.getName(), eCopy.getName());
        assertEquals(EnumerationOrig.FIRST.getValue(), eCopy.getValue());
        storage.store(copyWrapper);
        storage.shutdown();

        LazyEnumWrapper<EnumerationOrig> origWrapper2 = new LazyEnumWrapper<>(EnumerationOrig.SECOND);

        storage = EmbeddedStorage
                .Foundation(tempDir)
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(
                                KeyValue.New("test.eclipse.store.legacy.legacy.enumeration.data.EnumerationCopy", "test.eclipse.store.legacy.legacy.enumeration.data.EnumerationOrig"))))
                .setRoot(origWrapper2)
                .createEmbeddedStorageManager();

        storage.start();
        EnumerationOrig eOrig = origWrapper2.lazy.get();
        assertEquals(EnumerationOrig.FIRST.getName(), eOrig.getName());
        assertEquals(EnumerationOrig.FIRST.getValue(), eOrig.getValue());
        storage.shutdown();
    }

    static class LazyEnumWrapper<E>
    {
        Lazy<E> lazy;

        LazyEnumWrapper(E value)
        {
            this.lazy = Lazy.Reference(value);
        }
    }
}
