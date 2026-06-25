package test.eclipse.store.various.jdk;

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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BigDecimalTest
{
    @TempDir
    Path tempDir;

    @Test
    void bigDecimalStoreAndReload()
    {
        BigDecimal bd = new BigDecimal("123456789.987654321").setScale(9, RoundingMode.HALF_UP);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(bd, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            BigDecimal loaded = (BigDecimal) storageManager.root();

            assertEquals(bd, loaded, "BigDecimal should be equal after storing and reloading");
        }
    }

    @Test
	@Disabled("https://github.com/eclipse-store/store/issues/521")
    void bigDecimalDifferentScalesBehavior()
    {
        BigDecimal bd = new BigDecimal("1.2300").setScale(4, RoundingMode.UNNECESSARY);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(bd, tempDir)) {
        }

        BigDecimal bd2 = new BigDecimal("1.23");
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(bd2, tempDir)) {
            // numeric equality ignoring scale
            assertEquals(bd.stripTrailingZeros(), bd2.stripTrailingZeros(), "BigDecimal numeric value should match when stripped");
        }
    }

    @Test
    void saveBigDecimalDataAndReload()
    {
        BigDecimal bd = new BigDecimal("0");

        BigDecimalData root = new BigDecimalData(bd);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        BigDecimalData loadedRoot = new BigDecimalData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(bd, loadedRoot.getValue(), "BigDecimalData should be equal after storing and reloading");
        }
    }

    private static class BigDecimalData
    {
        private BigDecimal value;

        public BigDecimalData(BigDecimal value)
        {
            this.value = value;
        }

        public BigDecimalData()
        {
        }

        public BigDecimal getValue()
        {
            return value;
        }

        public void setValue(BigDecimal value)
        {
            this.value = value;
        }
    }
}
