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
import java.text.ChoiceFormat;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ChoiceFormatTest
{
    @TempDir
    Path tempDir;

    @Test
    void choiceFormatStoreAndReload()
    {
        double[] limits = {0, 1, 5};
        String[] formats = {"zero","one","many"};
        ChoiceFormat cf = new ChoiceFormat(limits, formats);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(cf, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            ChoiceFormat loaded = (ChoiceFormat) storageManager.root();
            assertEquals("many", loaded.format(10));
        }
    }

    @Test
    void choiceFormatAsFieldInDataClass()
    {
        double[] limits = {0, 1, 5};
        String[] formats = {"zero","one","many"};
        ChoiceFormat cf = new ChoiceFormat(limits, formats);

        ChoiceData root = new ChoiceData(cf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        ChoiceData loaded = new ChoiceData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals("one", loaded.getChoice().format(1));
        }
    }

    @Test
    void choiceFormatWithNegativeNumbers()
    {
        double[] limits = {-10, -5, 0, 5, 10};
        String[] formats = {"very negative", "negative", "zero", "positive", "very positive"};
        ChoiceFormat cf = new ChoiceFormat(limits, formats);

        ChoiceData root = new ChoiceData(cf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        ChoiceData loaded = new ChoiceData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals("very negative", loaded.getChoice().format(-10));
            assertEquals("negative", loaded.getChoice().format(-5));
            assertEquals("positive", loaded.getChoice().format(7));
            assertEquals("very positive", loaded.getChoice().format(10));
        }
    }

    @Test
    void choiceFormatWithBoundaryValues()
    {
        double[] limits = {0, 1, 5};
        String[] formats = {"zero","one","many"};
        ChoiceFormat cf = new ChoiceFormat(limits, formats);

        ChoiceData root = new ChoiceData(cf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        ChoiceData loaded = new ChoiceData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals("zero", loaded.getChoice().format(0));
            assertEquals("one", loaded.getChoice().format(1));
            assertEquals("many", loaded.getChoice().format(5));
            assertEquals("many", loaded.getChoice().format(100));
        }
    }

    @Test
    void choiceFormatWithDoubleValues()
    {
        double[] limits = {0.0, 0.5, 1.0};
        String[] formats = {"low", "medium", "high"};
        ChoiceFormat cf = new ChoiceFormat(limits, formats);

        ChoiceData root = new ChoiceData(cf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        ChoiceData loaded = new ChoiceData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals("low", loaded.getChoice().format(0.3));
            assertEquals("medium", loaded.getChoice().format(0.7));
            assertEquals("high", loaded.getChoice().format(1.5));
        }
    }

    @Test
    void choiceFormatWithLargeArray()
    {
        double[] limits = new double[10];
        String[] formats = new String[10];
        for (int i = 0; i < 10; i++) {
            limits[i] = i * 10.0;
            formats[i] = "range" + i;
        }
        ChoiceFormat cf = new ChoiceFormat(limits, formats);

        ChoiceData root = new ChoiceData(cf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        ChoiceData loaded = new ChoiceData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals("range0", loaded.getChoice().format(5));
            assertEquals("range5", loaded.getChoice().format(55));
            assertEquals("range9", loaded.getChoice().format(95));
        }
    }

    @Test
    void choiceFormatWithSingleElement()
    {
        double[] limits = {0};
        String[] formats = {"all"};
        ChoiceFormat cf = new ChoiceFormat(limits, formats);

        ChoiceData root = new ChoiceData(cf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        ChoiceData loaded = new ChoiceData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals("all", loaded.getChoice().format(-100));
            assertEquals("all", loaded.getChoice().format(0));
            assertEquals("all", loaded.getChoice().format(100));
        }
    }

    @Test
    void choiceFormatWithInfinity()
    {
        double[] limits = {Double.NEGATIVE_INFINITY, 0, 1};
        String[] formats = {"negative", "zero", "positive"};
        ChoiceFormat cf = new ChoiceFormat(limits, formats);

        assertEquals("negative", cf.format(-1000));
        assertEquals("zero", cf.format(0));
        assertEquals("positive", cf.format(1000));

        ChoiceData root = new ChoiceData(cf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        ChoiceData loaded = new ChoiceData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals("negative", loaded.getChoice().format(-1000));
            assertEquals("zero", loaded.getChoice().format(0));
            assertEquals("positive", loaded.getChoice().format(1000));
        }
    }

    @Test
    void choiceFormatWithSpecialStrings()
    {
        double[] limits = {0, 1, 2};
        String[] formats = {"", "one item", "items with special chars: €£¥"};
        ChoiceFormat cf = new ChoiceFormat(limits, formats);

        ChoiceData root = new ChoiceData(cf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        ChoiceData loaded = new ChoiceData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals("", loaded.getChoice().format(0.5));
            assertEquals("one item", loaded.getChoice().format(1.5));
            assertEquals("items with special chars: €£¥", loaded.getChoice().format(5));
        }
    }

    @Test
    void choiceFormatWithVeryLargeNumbers()
    {
        double[] limits = {0, 1000000, 1000000000};
        String[] formats = {"small", "million", "billion"};
        ChoiceFormat cf = new ChoiceFormat(limits, formats);

        ChoiceData root = new ChoiceData(cf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        ChoiceData loaded = new ChoiceData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals("small", loaded.getChoice().format(999));
            assertEquals("million", loaded.getChoice().format(5000000));
            assertEquals("billion", loaded.getChoice().format(5000000000L));
        }
    }

    private static class ChoiceData
    {
        private ChoiceFormat choice;

        public ChoiceData(ChoiceFormat choice)
        {
            this.choice = choice;
        }

        public ChoiceData()
        {
        }

        public ChoiceFormat getChoice()
        {
            return choice;
        }

        public void setChoice(ChoiceFormat choice)
        {
            this.choice = choice;
        }
    }
}

