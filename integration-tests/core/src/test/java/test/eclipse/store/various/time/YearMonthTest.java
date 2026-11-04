package test.eclipse.store.various.time;

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
import java.time.YearMonth;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class YearMonthTest
{
    @TempDir
    Path tempDir;

    @Test
    void yearMonthStoreAndReload()
    {
        YearMonth ym = YearMonth.of(2020, 1);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(ym, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            YearMonth loaded = (YearMonth) storageManager.root();

            assertEquals(ym, loaded, "YearMonth should be equal after storing and reloading");
        }
    }

    @Test
    void yearMonthUpdateApiBehavior_insideRoot()
    {
        YearMonth ym = YearMonth.of(2020, 1);
        YearMonthRoot root = new YearMonthRoot(ym);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
        }

        YearMonth ym2 = YearMonth.now();
        YearMonthRoot loadedRoot = new YearMonthRoot(ym2);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {

            assertEquals(ym, loadedRoot.getValue(), "YearMonth should be equal after storing and reloading");
        }
    }

    private class YearMonthRoot
    {
        private YearMonth value;

        public YearMonthRoot(YearMonth value)
        {
            this.value = value;
        }

        public YearMonth getValue()
        {
            return value;
        }

        public void setValue(YearMonth value)
        {
            this.value = value;
        }
    }


    @Test
    void saveYearMonthDataAndReload()
    {
        YearMonth ym = YearMonth.of(2020, 1);

        YearMonthData root = new YearMonthData(ym);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        YearMonthData loadedRoot = new YearMonthData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(ym, loadedRoot.getValue(), "YearMonthData should be equal after storing and reloading");
        }
    }

    private static class YearMonthData
    {
        private YearMonth value;

        public YearMonthData(YearMonth value)
        {
            this.value = value;
        }

        public YearMonthData()
        {
        }

        public YearMonth getValue()
        {
            return value;
        }

        public void setValue(YearMonth value)
        {
            this.value = value;
        }
    }
}
