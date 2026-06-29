package test.eclipse.store.various.text;

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

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Disabled("https://github.com/eclipse-store/store/issues/523")
public class SimpleDateFormatTest
{
    @TempDir
    Path tempDir;


    @Test
    void simpleDateFormatAsFieldInDataClass()
    {
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);
        Date sample = new Date(1610000000000L);

        SdfData root = new SdfData(fmt);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        SdfData loadedRoot = new SdfData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(fmt.format(sample), loadedRoot.getFormat().format(sample));
        }
    }

    private static class SdfData
    {
        private SimpleDateFormat format;

        public SdfData(SimpleDateFormat format)
        {
            this.format = format;
        }

        public SdfData()
        {
        }

        public SimpleDateFormat getFormat()
        {
            return format;
        }

        public void setFormat(SimpleDateFormat format)
        {
            this.format = format;
        }
    }
}
