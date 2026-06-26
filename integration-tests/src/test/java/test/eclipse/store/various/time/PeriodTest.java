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
import java.time.Period;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PeriodTest
{
    @TempDir
    Path tempDir;

    @Test
    void periodStoreAndReload()
    {
        Period p = Period.ofYears(2).withMonths(3).withDays(5);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(p, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Period loaded = (Period) storageManager.root();

            assertEquals(p, loaded, "Period should be equal after storing and reloading");
        }
    }

    @Test
    void periodUpdateApiBehavior_insideRoot()
    {
        Period p = Period.ofYears(2).withMonths(3).withDays(5);
        PeriodRoot root = new PeriodRoot(p);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
        }

        Period p2 = Period.ZERO;
        PeriodRoot loadedRoot = new PeriodRoot(p2);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {

            assertEquals(p, loadedRoot.getValue(), "Period should be equal after storing and reloading");
        }
    }

    private class PeriodRoot
    {
        private Period value;

        public PeriodRoot(Period value)
        {
            this.value = value;
        }

        public Period getValue()
        {
            return value;
        }
    }

    @Test
    void savePeriodDataAndReload()
    {
        Period p = Period.ofYears(2).withMonths(3).withDays(5);

        PeriodData root = new PeriodData(p);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        PeriodData loadedRoot = new PeriodData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(p, loadedRoot.getValue(), "PeriodData should be equal after storing and reloading");
        }
    }

    private static class PeriodData
    {
        private Period value;

        public PeriodData(Period value)
        {
            this.value = value;
        }

        public PeriodData()
        {
        }

        public Period getValue()
        {
            return value;
        }

        public void setValue(Period value)
        {
            this.value = value;
        }
    }
}
