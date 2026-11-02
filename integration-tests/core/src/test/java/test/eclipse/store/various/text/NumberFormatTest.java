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
import java.text.NumberFormat;
import java.util.Locale;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Disabled("https://github.com/eclipse-serializer/serializer/issues/236")
public class NumberFormatTest
{
    @TempDir
    Path tempDir;

    @Test
    void numberFormatAsFieldInDataClass()
    {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        double sample = 1234.56;

        NumberData root = new NumberData(nf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        NumberData loaded = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(nf.format(sample), loaded.getFormat().format(sample));
        }
    }

    @Test
    void numberFormatCurrencyInstance()
    {
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.US);
        double sample = 99.99;

        NumberData root = new NumberData(nf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        NumberData loaded = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(nf.format(sample), loaded.getFormat().format(sample));
        }
    }

    @Test
    void numberFormatPercentInstance()
    {
        NumberFormat nf = NumberFormat.getPercentInstance(Locale.US);
        double sample = 0.75;

        NumberData root = new NumberData(nf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        NumberData loaded = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(nf.format(sample), loaded.getFormat().format(sample));
        }
    }

    @Test
    void numberFormatIntegerInstance()
    {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        long sample = 123456789L;

        NumberData root = new NumberData(nf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        NumberData loaded = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(nf.format(sample), loaded.getFormat().format(sample));
        }
    }

    @Test
    void numberFormatWithMaximumFractionDigits()
    {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setMaximumFractionDigits(2);
        double sample = 3.14159;

        NumberData root = new NumberData(nf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        NumberData loaded = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(nf.format(sample), loaded.getFormat().format(sample));
            assertEquals(2, loaded.getFormat().getMaximumFractionDigits());
        }
    }

    @Test
    void numberFormatWithMinimumFractionDigits()
    {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setMinimumFractionDigits(4);
        double sample = 10.5;

        NumberData root = new NumberData(nf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        NumberData loaded = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(nf.format(sample), loaded.getFormat().format(sample));
            assertEquals(4, loaded.getFormat().getMinimumFractionDigits());
        }
    }

    @Test
    void numberFormatWithGrouping()
    {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setGroupingUsed(true);
        long sample = 1000000;

        NumberData root = new NumberData(nf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        NumberData loaded = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(nf.format(sample), loaded.getFormat().format(sample));
        }
    }

    @Test
    void numberFormatWithoutGrouping()
    {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setGroupingUsed(false);
        long sample = 1000000;

        NumberData root = new NumberData(nf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        NumberData loaded = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(nf.format(sample), loaded.getFormat().format(sample));
        }
    }

    @Test
    void numberFormatDifferentLocales()
    {
        NumberFormat nfUS = NumberFormat.getInstance(Locale.US);
        NumberFormat nfFR = NumberFormat.getInstance(Locale.FRANCE);
        NumberFormat nfDE = NumberFormat.getInstance(Locale.GERMANY);
        double sample = 1234.56;

        NumberData root1 = new NumberData(nfUS);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root1, tempDir.resolve("us"))) {
            storageManager.storeRoot();
        }

        NumberData root2 = new NumberData(nfFR);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root2, tempDir.resolve("fr"))) {
            storageManager.storeRoot();
        }

        NumberData root3 = new NumberData(nfDE);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root3, tempDir.resolve("de"))) {
            storageManager.storeRoot();
        }

        NumberData loaded1 = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded1, tempDir.resolve("us"))) {
            assertEquals(nfUS.format(sample), loaded1.getFormat().format(sample));
        }

        NumberData loaded2 = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded2, tempDir.resolve("fr"))) {
            assertEquals(nfFR.format(sample), loaded2.getFormat().format(sample));
        }

        NumberData loaded3 = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded3, tempDir.resolve("de"))) {
            assertEquals(nfDE.format(sample), loaded3.getFormat().format(sample));
        }
    }

    @Test
    void numberFormatWithZero()
    {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        double sample = 0.0;

        NumberData root = new NumberData(nf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        NumberData loaded = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(nf.format(sample), loaded.getFormat().format(sample));
        }
    }

    @Test
    void numberFormatWithNegativeNumbers()
    {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        double sample = -9876.54;

        NumberData root = new NumberData(nf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        NumberData loaded = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(nf.format(sample), loaded.getFormat().format(sample));
        }
    }

    @Test
    void numberFormatWithVeryLargeNumbers()
    {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        double sample = 9.9E20;

        NumberData root = new NumberData(nf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        NumberData loaded = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(nf.format(sample), loaded.getFormat().format(sample));
        }
    }

    @Test
    void numberFormatWithVerySmallNumbers()
    {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        double sample = 0.000001;

        NumberData root = new NumberData(nf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        NumberData loaded = new NumberData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(nf.format(sample), loaded.getFormat().format(sample));
        }
    }

    private static class NumberData
    {
        private NumberFormat format;

        public NumberData(NumberFormat format)
        {
            this.format = format;
        }

        public NumberData()
        {
        }

        public NumberFormat getFormat()
        {
            return format;
        }

        public void setFormat(NumberFormat format)
        {
            this.format = format;
        }
    }
}

