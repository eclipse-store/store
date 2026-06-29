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
import java.text.DecimalFormat;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Disabled("https://github.com/eclipse-store/store/issues/522")
public class DecimalFormatTest
{
    @TempDir
    Path tempDir;

    @Test
    void decimalFormatAsFieldInDataClass()
    {
        DecimalFormat df = new DecimalFormat("0.###");
        double sample = 3.14159;

        DecimalData root = new DecimalData(df);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        DecimalData loaded = new DecimalData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(df.format(sample), loaded.getFormat().format(sample));
        }
    }

    private static class DecimalData
    {
        private DecimalFormat format;

        public DecimalData(DecimalFormat format)
        {
            this.format = format;
        }

        public DecimalData()
        {
        }

        public DecimalFormat getFormat()
        {
            return format;
        }

        public void setFormat(DecimalFormat format)
        {
            this.format = format;
        }
    }
}

