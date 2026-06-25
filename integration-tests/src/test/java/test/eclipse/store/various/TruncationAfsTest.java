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

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.afs.types.AWritableFile;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public class TruncationAfsTest
{
    @TempDir
    Path location;

    String VALUE = "Some long string value";


    @Test
    void truncationWithAfs()
    {

        TruncationRoot root = new TruncationRoot();
        root.setValue(VALUE);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, location)) {
            //do nothing
        }

        final NioFileSystem fileSystem = NioFileSystem.New();

        ADirectory aDirectory = fileSystem.ensureDirectory(location);
        aDirectory.inventorize();

        ADirectory aChannel = aDirectory.getDirectory("channel_0");
        //System.out.println(aChannel.toPathString());
        aChannel.inventorize();
        AFile file = aChannel.getFile("channel_0_1.dat");

        AWritableFile aWritableFile = file.tryUseWriting();
        byte[] lines = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        ByteBuffer buffer = ByteBuffer.wrap(lines);
        aWritableFile.writeBytes(buffer);
        aWritableFile.close();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(location)) {
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
