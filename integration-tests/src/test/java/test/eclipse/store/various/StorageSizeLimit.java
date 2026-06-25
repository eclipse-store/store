package test.eclipse.store.various;

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

import org.eclipse.serializer.configuration.types.ByteSize;
import org.eclipse.serializer.configuration.types.ByteUnit;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


public class StorageSizeLimit
{

    @TempDir
    static Path tempDir;


    @Test
    @Disabled("Takes too long")
    void sizeLimitTest()
    {

        byte[] b = new byte[1024 * 1024 * 1024];
        Root root = new Root(b);
        System.out.println(tempDir.toAbsolutePath().toString());
        try (EmbeddedStorageManager storageManager = prepareFoundation().start(root)) {
            byte[] data1 = new byte[1024 * 1024 * 1024];
            byte[] data2 = new byte[1024 * 1024 * 1024];
            System.out.println(data1.length);
            root.setData1(data1);
            root.setData2(data2);
            storageManager.storeRoot();
        } catch (Exception e) {
            e.printStackTrace();
        }


        Root root1 = new Root();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root1, tempDir)) {
            System.out.println(root1.data.length);

        }

        System.out.println("Data stored");

    }

    private static EmbeddedStorageFoundation<?> prepareFoundation()
    {

        return EmbeddedStorageConfigurationBuilder.New()
                .setDataFileMaximumSize(ByteSize.New(2, ByteUnit.GB))
                .setStorageDirectory(tempDir.toAbsolutePath().toString())
                .createEmbeddedStorageFoundation();
    }

    private static class Root
    {
        private byte[] data;
        private byte[] data1;
        private byte[] data2;

        public Root()
        {
        }

        public Root(byte[] data)
        {
            this.data = data;
        }

        public byte[] getData()
        {
            return data;
        }

        public void setData(byte[] data)
        {
            this.data = data;
        }

        public void setData1(byte[] data1)
        {
            this.data1 = data1;
        }

        public void setData2(byte[] data2)
        {
            this.data2 = data2;
        }

        public byte[] getData1()
        {
            return data1;
        }

        public byte[] getData2()
        {
            return data2;
        }
    }
}
