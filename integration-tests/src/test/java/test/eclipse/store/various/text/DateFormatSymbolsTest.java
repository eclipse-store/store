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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.text.DateFormatSymbols;
import java.util.Locale;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DateFormatSymbolsTest
{
    @TempDir
    Path tempDir;

    @Test
    void dateFormatSymbolsAsFieldInDataClass()
    {
        DateFormatSymbols dfs = new DateFormatSymbols(Locale.US);

        SymbolsData root = new SymbolsData(dfs);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        SymbolsData loaded = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertArrayEquals(dfs.getMonths(), loaded.getSymbols().getMonths());
            assertArrayEquals(dfs.getWeekdays(), loaded.getSymbols().getWeekdays());
        }
    }

    @Test
    void dateFormatSymbolsWithCustomMonths()
    {
        DateFormatSymbols dfs = new DateFormatSymbols(Locale.US);
        String[] customMonths = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec", ""};
        dfs.setShortMonths(customMonths);

        SymbolsData root = new SymbolsData(dfs);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        SymbolsData loaded = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertArrayEquals(customMonths, loaded.getSymbols().getShortMonths());
        }
    }

    @Test
    void dateFormatSymbolsWithCustomWeekdays()
    {
        DateFormatSymbols dfs = new DateFormatSymbols(Locale.US);
        String[] customWeekdays = {"", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        dfs.setShortWeekdays(customWeekdays);

        SymbolsData root = new SymbolsData(dfs);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        SymbolsData loaded = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertArrayEquals(customWeekdays, loaded.getSymbols().getShortWeekdays());
        }
    }

    @Test
    void dateFormatSymbolsWithAmPmStrings()
    {
        DateFormatSymbols dfs = new DateFormatSymbols(Locale.US);
        String[] amPmStrings = dfs.getAmPmStrings();

        SymbolsData root = new SymbolsData(dfs);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        SymbolsData loaded = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertArrayEquals(amPmStrings, loaded.getSymbols().getAmPmStrings());
        }
    }

    @Test
    void dateFormatSymbolsWithCustomAmPmStrings()
    {
        DateFormatSymbols dfs = new DateFormatSymbols(Locale.US);
        String[] customAmPm = {"Morning", "Evening"};
        dfs.setAmPmStrings(customAmPm);

        SymbolsData root = new SymbolsData(dfs);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        SymbolsData loaded = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertArrayEquals(customAmPm, loaded.getSymbols().getAmPmStrings());
        }
    }

    @Test
    void dateFormatSymbolsDifferentLocales()
    {
        DateFormatSymbols dfsUS = new DateFormatSymbols(Locale.US);
        DateFormatSymbols dfsFR = new DateFormatSymbols(Locale.FRANCE);
        DateFormatSymbols dfsDE = new DateFormatSymbols(Locale.GERMANY);

        SymbolsData root1 = new SymbolsData(dfsUS);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root1, tempDir.resolve("us"))) {
            storageManager.storeRoot();
        }

        SymbolsData root2 = new SymbolsData(dfsFR);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root2, tempDir.resolve("fr"))) {
            storageManager.storeRoot();
        }

        SymbolsData root3 = new SymbolsData(dfsDE);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root3, tempDir.resolve("de"))) {
            storageManager.storeRoot();
        }

        SymbolsData loaded1 = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded1, tempDir.resolve("us"))) {
            assertArrayEquals(dfsUS.getMonths(), loaded1.getSymbols().getMonths());
        }

        SymbolsData loaded2 = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded2, tempDir.resolve("fr"))) {
            assertArrayEquals(dfsFR.getMonths(), loaded2.getSymbols().getMonths());
        }

        SymbolsData loaded3 = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded3, tempDir.resolve("de"))) {
            assertArrayEquals(dfsDE.getMonths(), loaded3.getSymbols().getMonths());
        }
    }

    @Test
    void dateFormatSymbolsWithEras()
    {
        DateFormatSymbols dfs = new DateFormatSymbols(Locale.US);
        String[] eras = dfs.getEras();

        SymbolsData root = new SymbolsData(dfs);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        SymbolsData loaded = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertArrayEquals(eras, loaded.getSymbols().getEras());
        }
    }

    @Test
    void dateFormatSymbolsWithLocalPatternChars()
    {
        DateFormatSymbols dfs = new DateFormatSymbols(Locale.US);
        String patternChars = dfs.getLocalPatternChars();

        SymbolsData root = new SymbolsData(dfs);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        SymbolsData loaded = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(patternChars, loaded.getSymbols().getLocalPatternChars());
        }
    }

    @Test
    void dateFormatSymbolsWithCustomLocalPatternChars()
    {
        DateFormatSymbols dfs = new DateFormatSymbols(Locale.US);
        String customPatternChars = "GyMdkHmsSEDFwWahKzZ";
        dfs.setLocalPatternChars(customPatternChars);

        SymbolsData root = new SymbolsData(dfs);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        SymbolsData loaded = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(customPatternChars, loaded.getSymbols().getLocalPatternChars());
        }
    }

    @Test
    void dateFormatSymbolsJapaneseLocale()
    {
        DateFormatSymbols dfs = new DateFormatSymbols(Locale.JAPAN);

        SymbolsData root = new SymbolsData(dfs);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        SymbolsData loaded = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertArrayEquals(dfs.getMonths(), loaded.getSymbols().getMonths());
            assertArrayEquals(dfs.getWeekdays(), loaded.getSymbols().getWeekdays());
        }
    }

    @Test
    void dateFormatSymbolsChineseLocale()
    {
        DateFormatSymbols dfs = new DateFormatSymbols(Locale.CHINA);

        SymbolsData root = new SymbolsData(dfs);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        SymbolsData loaded = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertArrayEquals(dfs.getMonths(), loaded.getSymbols().getMonths());
        }
    }

    @Test
    void dateFormatSymbolsWithEmptyCustomMonths()
    {
        DateFormatSymbols dfs = new DateFormatSymbols(Locale.US);
        String[] emptyMonths = {"", "", "", "", "", "", "", "", "", "", "", "", ""};
        dfs.setShortMonths(emptyMonths);

        SymbolsData root = new SymbolsData(dfs);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        SymbolsData loaded = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertArrayEquals(emptyMonths, loaded.getSymbols().getShortMonths());
        }
    }

    @Test
    void dateFormatSymbolsWithUnicodeCharacters()
    {
        DateFormatSymbols dfs = new DateFormatSymbols(Locale.US);
        String[] unicodeMonths = {"一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月", ""};
        dfs.setShortMonths(unicodeMonths);

        SymbolsData root = new SymbolsData(dfs);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        SymbolsData loaded = new SymbolsData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertArrayEquals(unicodeMonths, loaded.getSymbols().getShortMonths());
        }
    }

    private static class SymbolsData
    {
        private DateFormatSymbols symbols;

        public SymbolsData(DateFormatSymbols symbols)
        {
            this.symbols = symbols;
        }

        public SymbolsData()
        {
        }

        public DateFormatSymbols getSymbols()
        {
            return symbols;
        }

        public void setSymbols(DateFormatSymbols symbols)
        {
            this.symbols = symbols;
        }
    }
}

