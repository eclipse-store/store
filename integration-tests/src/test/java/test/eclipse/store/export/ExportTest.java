package test.eclipse.store.export;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.afs.types.AReadableFile;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportTest
{

    @TempDir
    Path location;

    @Test
    void exportToCSVTest() throws IOException
    {
        List<Customer> customers = new ArrayList<>();
        customers.add(CustomerGenerator.generateNewCustomer());
        customers.add(CustomerGenerator.generateNewCustomer());
        customers.add(CustomerGenerator.generateNewCustomer());
        customers.add(CustomerGenerator.generateNewCustomer());
        customers.add(CustomerGenerator.generateNewCustomer());


        Path exportPathBin = location.resolve("export");

        EmbeddedStorageManager storage = EmbeddedStorage.start(customers, location);

        final NioFileSystem fs = NioFileSystem.New();

        final ADirectory exportPathDir = fs.ensureDirectoryPath(exportPathBin.toFile().getAbsolutePath());

        StorageEntityTypeExportStatistics exportResult = storage.exportTypes(
                new StorageEntityTypeExportFileProvider.Default(exportPathDir, "bin"));

        //get all exported binary files
        List<String> binaryFiles = new ArrayList<>();
        exportResult.typeStatistics().values().forEach(v -> binaryFiles.add(v.file().identifier()));

        Path csvPath = location.resolve("csv");

        final ADirectory aCsvDir = fs.ensureDirectoryPath(csvPath.toFile().getAbsolutePath());

        //create the CSV converter
        StorageDataConverterTypeBinaryToCsv converter =
                new StorageDataConverterTypeBinaryToCsv.UTF8(
                        StorageDataConverterCsvConfiguration.defaultConfiguration(),
                        new StorageEntityTypeConversionFileProvider.Default(aCsvDir, "csv"),
                        storage.typeDictionary(),
                        null, // no type name mapping
                        4096, // read buffer size
                        4096  // write buffer size
                );

        //convert all binary files to CSV files

        for (String file : binaryFiles) {
            AReadableFile dataFile = exportPathDir.ensureFile(file).useReading();
            try {
                converter.convertDataFile(dataFile);
            } finally {
                dataFile.close();
            }
        }

        List<File> files = (List<File>) FileUtils.listFiles(csvPath.toFile(), null, true);
        assertTrue(files.size() > 10);

//        //reimport

        Path reimportStoragePath = location.resolve("reimport");
        reimportStoragePath.toFile().mkdir();
        ADirectory reimportStorage = fs.ensureDirectoryPath(reimportStoragePath.toFile().getAbsolutePath());

        StorageDataConverterTypeCsvToBinary<AFile> reimportConverter =
                StorageDataConverterTypeCsvToBinary.New(
                        StorageDataConverterCsvConfiguration.defaultConfiguration(),
                        storage.typeDictionary(),
                        new StorageEntityTypeConversionFileProvider.Default(reimportStorage, "dat")
                );

        for (File file : files) {
            AReadableFile storageFile = aCsvDir.ensureFile(file.getName()).useReading();
            try {
                reimportConverter.convertCsv(storageFile);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                storageFile.close();
            }
        }
        List<File> datFiles = (List<File>) FileUtils.listFiles(reimportStoragePath.toFile(), null, true);
        assertTrue(datFiles.size() > 10, files.toString());

        storage.shutdown();
    }

}
