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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinaryHandlerGenericEnumTest
{

    private EmbeddedStorageManager storage;

    private final String TEXT = "SomeText";

    @TempDir
    Path workDir;

    @AfterEach
    void cleanStorage() throws IOException
    {
        if (null != storage && !storage.isShutdown()) {
            storage.shutdown();
        }
        FileUtils.deleteDirectory(workDir.toFile());
    }

    @Test
    void binaryHandlerGenericEnum()
    {

        GenericEnumData original = GenericEnumData.THIRD;
        original.setOtherValue(TEXT);
        GenericEnumData copy = GenericEnumData.THIRD;
        saveAndShutdown(original);

        original.setOtherValue("somethingOthers");
        load(copy);

        assertEquals(TEXT, copy.getOtherValue());
    }

//    @Test
//    void exportImportTest() {
//        GenericEnumData original = GenericEnumData.THIRD;
//        original.setOtherValue(TEXT);
//        GenericEnumData copy = GenericEnumData.THIRD;
//
//        storage = startStorage(original);
//        StorageConnection connection = storage.createConnection();
//        String fileSuffix = "bin";
//        assertNotNull(workDir);
//        StorageEntityTypeExportStatistics exportResult = connection.exportTypes(
//                new StorageEntityTypeExportFileProvider.Default(workDir, fileSuffix),
//                typeHandler -> true // export all, customize if necessary
//        );
//        XSequence<Path> exportFiles = CQL
//                .from(exportResult.typeStatistics().values())
//                .project(s -> (new File(s.file().identifier())).toPath())
//                .execute();
//        storage.shutdown();
//
//        storage = startStorage(copy);
//        StorageConnection  loadConnection = storage.createConnection();
//
//        loadConnection.importFiles(HashEnum.New(exportFiles));
//        assertEquals(TEXT, copy.getOtherValue());
//    }

    private void saveAndShutdown(GenericEnumData original)
    {
        storage = startStorage(original);
        storage.storeRoot();
        storage.shutdown();
    }

    private void load(GenericEnumData loaded)
    {
        storage = startStorage(loaded);
    }

    private EmbeddedStorageManager startStorage(Object root)
    {
        return EmbeddedStorage.start(root, workDir);
    }

    private enum GenericEnumData
    {
        FIRST(1), SECOND(2), THIRD(3), FOURTH(4), FIVE(5);

        int value;
        String otherValue;

        GenericEnumData(int value)
        {
            this.value = value;
        }

        public String getOtherValue()
        {
            return otherValue;
        }

        public void setOtherValue(String otherValue)
        {
            this.otherValue = otherValue;
        }

        public int getValue()
        {
            return value;
        }

        public void setValue(int value)
        {
            this.value = value;
        }
    }
}
