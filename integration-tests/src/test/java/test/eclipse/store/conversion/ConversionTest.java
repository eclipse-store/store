package test.eclipse.store.conversion;

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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.tools.storage.converter.MainUtilStorageConverter;
import org.eclipse.store.storage.embedded.tools.storage.converter.StorageConverter;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.datafaker.Faker;

public class ConversionTest
{

    @TempDir
    Path src;

    @TempDir
    Path dest;

    final private Faker faker = new Faker();

    @Test
    public void conversionTest()
    {

        List<String> dataSrc = new ArrayList<>();
        List<String> dataDst = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            dataSrc.add(faker.lorem().sentence());
        }

        final EmbeddedStorageFoundation<?> sourceFoundation = EmbeddedStorageConfiguration.Builder()
                .setStorageDirectory(src.toAbsolutePath().toString())
                .setChannelCount(16)
                .createEmbeddedStorageFoundation();

        final EmbeddedStorageFoundation<?> targetFoundation = EmbeddedStorageConfiguration.Builder()
                .setStorageDirectory(dest.toAbsolutePath().toString())
                .setChannelCount(1)
                .createEmbeddedStorageFoundation();


        EmbeddedStorageManager sourceStorage = sourceFoundation.start(dataSrc);
        sourceStorage.shutdown();


        final StorageConverter storageConverter = new StorageConverter(sourceFoundation.getConfiguration(), targetFoundation.getConfiguration());
        storageConverter.start();

        EmbeddedStorageManager dstStorage = targetFoundation.start(dataDst);
        dstStorage.shutdown();

        Assertions.assertEquals(dataSrc.size(), dataDst.size());

        //System.out.println("Storage conversion finished!");


    }

    @Test
    public void conversionMainTest(@TempDir Path configFolder) throws IOException
    {

        List<String> dataSrc = new ArrayList<>();
        List<String> dataDst = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            dataSrc.add(faker.lorem().sentence());
        }

        Path srcConfig = configFolder.resolve("scr-config.ini");
        String srcConfigContent = "channel-count = 2\n" +
                "storage-directory = " + src.toAbsolutePath();
        FileUtils.writeStringToFile(srcConfig.toFile(), srcConfigContent, "UTF-8");


        Path dstConfig = configFolder.resolve("dst-config.ini");
        String dstConfigContent = "channel-count = 16\n" +
                "storage-directory = " + dest.toAbsolutePath();
        FileUtils.writeStringToFile(dstConfig.toFile(), dstConfigContent, "UTF-8");

        final EmbeddedStorageConfigurationBuilder configuration = EmbeddedStorageConfiguration.load(srcConfig.toString());
        final EmbeddedStorageManager srcStorage = configuration.createEmbeddedStorageFoundation().createEmbeddedStorageManager(dataSrc).start();
        srcStorage.shutdown();


        MainUtilStorageConverter.main(new String[]{String.valueOf(srcConfig.toAbsolutePath()), String.valueOf(dstConfig.toAbsolutePath())});

        final EmbeddedStorageConfigurationBuilder dstConfiguration = EmbeddedStorageConfiguration.load(dstConfig.toString());
        final EmbeddedStorageManager dstStorage = dstConfiguration.createEmbeddedStorageFoundation().createEmbeddedStorageManager(dataDst).start();
        dstStorage.shutdown();

        Assertions.assertEquals(dataSrc.size(), dataDst.size());

        //System.out.println("Storage conversion finished!");


    }
}
