package test.eclipse.store.collections.lazy.arraylist;

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


import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.serializer.collections.types.XSequence;
import org.eclipse.serializer.util.cql.CQL;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.*;

public class Export
{

    public static XSequence<Path> export(final EmbeddedStorageManager storage, final String target)
    {

        final NioFileSystem fileSystem = NioFileSystem.New();

        final String fileSuffix = "bin";
        final StorageConnection connection = storage.createConnection();
        final StorageEntityTypeExportStatistics exportResult = connection.exportTypes(
                new StorageEntityTypeExportFileProvider.Default(
                        fileSystem.ensureDirectoryPath(target),
                        fileSuffix
                ),
                typeHandler -> true // export all, customize if necessary
        );
        final XSequence<Path> exportFiles = CQL
                .from(exportResult.typeStatistics().values())
                .project(s -> Paths.get(s.file().toPathString()))
                .execute();

        return exportFiles;
    }

    public static void convertToCSV(final EmbeddedStorageManager storage, final XSequence<Path> files, final String target)
    {

        final NioFileSystem fileSystem = NioFileSystem.New();

        final StorageDataConverterTypeBinaryToCsv converter =
                new StorageDataConverterTypeBinaryToCsv.UTF8(
                        StorageDataConverterCsvConfiguration.defaultConfiguration(),
                        new StorageEntityTypeConversionFileProvider.Default(
                                fileSystem.ensureDirectoryPath(target),
                                "csv"
                        ),
                        storage.typeDictionary(),
                        null, // no type name mapping
                        4096, // read buffer size
                        4096  // write buffer size
                );

        files.forEach(file -> converter.convertDataFile(
                fileSystem.ensureFile(file).tryUseReading()
        ));
    }

    public static void exportAsCSV(final EmbeddedStorageManager storage, final Path targetDir)
    {

        System.out.println("deleting target dir before export " + targetDir.toString());

        try {
            Utils.deleteAll(targetDir);
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }

        final Path binDir = targetDir.resolve("bin");
        final XSequence<Path> exports = export(storage, binDir.toString());

        final Path csvDir = targetDir.resolve("csv");
        convertToCSV(storage, exports, csvDir.toString());

    }

}
