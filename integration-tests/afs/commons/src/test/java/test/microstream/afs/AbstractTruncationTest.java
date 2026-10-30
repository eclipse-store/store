package test.microstream.afs;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - commons
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

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.serializer.afs.types.AWritableFile;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public abstract class AbstractTruncationTest
{


    String VALUE = "Some long string value";

    EmbeddedStorageManager storage;

    public void storageShutdown() {
        this.storage.shutdown();
    }

    public abstract  ADirectory defineLocation();

    public abstract EmbeddedStorageManager startStorage(Object root);

    public abstract EmbeddedStorageManager startStorage();

    public abstract void initFileSystem();

    @Test
    void truncationWithAfs()
    {

        TruncationRoot root = new TruncationRoot();
        root.setValue(VALUE);

        initFileSystem();

        ADirectory aDirectory = defineLocation();


        try (EmbeddedStorageManager storageManager = startStorage(root)) {

            //do nothing
        }

        aDirectory.inventorize();

        ADirectory aChannel = aDirectory.getDirectory("channel_0");
        //System.out.println(aChannel.toPathString());
        aChannel.inventorize();
        AFile file = aChannel.getFile("channel_0_1.dat");

        AWritableFile aWritableFile = file.tryUseWriting();
        byte[] lines = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        ByteBuffer buffer = ByteBuffer.wrap(lines);
        aWritableFile.writeBytes(buffer);
        aWritableFile.release();

        try (EmbeddedStorageManager storageManager = startStorage()) {
            TruncationRoot localRoot = (TruncationRoot) storageManager.root();
            Assertions.assertEquals(VALUE, localRoot.getValue());
        }

    }


    static class TruncationRoot
    {

        String value;

        public String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }
    }

}
