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

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.microstream.record.data.PersonRecord;

public class RecordTest {

    @TempDir
    Path location;


    @Test
    public void saveRecordTest() {
        PersonRecord personRecord = new PersonRecord("Maria", "Lukasova");

        EmbeddedStorageManager storage = EmbeddedStorage.start(personRecord, location);
        storage.shutdown();

        PersonRecord secondRecord = new PersonRecord("Kamila", "Pazourkova");
        storage = EmbeddedStorage.start(secondRecord, location);

        Assertions.assertEquals("Maria", secondRecord.firstName());

    }


}
