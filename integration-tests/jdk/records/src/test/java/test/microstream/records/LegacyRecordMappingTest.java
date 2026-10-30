package test.microstream.records;

/*-
 * #%L
 * EclipseStore Integration Tests - JDK - Records
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

import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.persistence.types.PersistenceRefactoringMappingProvider;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.microstream.record.data.legacy.RemoveCopy;
import test.microstream.record.data.legacy.RemoveOrig;

public class LegacyRecordMappingTest {

    @TempDir
    Path location;

    private static final String NAME = "Karel";
    private static final String LAST_NAME = "Loprais";
    private static final String FULL_NAME = "Karel Loprais";

    @Test
    public void removeFieldTest() {
        RemoveOrig removeOrig = new RemoveOrig(NAME, LAST_NAME, FULL_NAME);

        EmbeddedStorageManager storage = EmbeddedStorage.start(removeOrig, location);
        storage.shutdown();

        RemoveCopy copy = new RemoveCopy("Some", "Name");


        storage = EmbeddedStorage
                .Foundation(location)
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(
                                KeyValue.New("test.microstream.record.data.legacy.RemoveOrig", "test.microstream.record.data.legacy.RemoveCopy"))))
                .setRoot(copy)
                .start();

        Assertions.assertEquals(NAME, copy.firstName() );
        Assertions.assertEquals(LAST_NAME, copy.lastName());

    }

    @Test
    public void addFieldTest() {
        RemoveCopy removeCopy = new RemoveCopy(NAME, LAST_NAME);

        EmbeddedStorageManager storage = EmbeddedStorage.start(removeCopy, location);
        storage.shutdown();

        RemoveOrig orig = new RemoveOrig("some", "super", "original Data");


        storage = EmbeddedStorage
                .Foundation(location)
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(
                                KeyValue.New("test.microstream.record.data.legacy.RemoveCopy", "test.microstream.record.data.legacy.RemoveOrig"))))
                .setRoot(orig)
                .start();

        Assertions.assertEquals(NAME, orig.firstName() );
        Assertions.assertEquals(LAST_NAME, orig.lastName());
    }


}
