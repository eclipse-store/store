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
import java.time.LocalDate;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LocalDateTest
{
    @TempDir
    Path tempDir;

    @Test
    void localDateTimeTest()
    {
        LocalDate ldt = LocalDate.of(2020, 1, 1);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(ldt, tempDir)) {
        }

        LocalDate ldt2 = null;
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            LocalDate localDate = (LocalDate) storageManager.root();

            assertEquals(ldt, localDate, "LocalDateTime should be equal after storing and reloading");
        }
    }

    @Test
    void localDateTimeContainerUpdateApiTest()
    {
        LocalDate ldt = LocalDate.of(2020, 1, 1);
        LocalDateRoot originalRoot = new LocalDateRoot(ldt);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(originalRoot, tempDir)) {
        }

        LocalDate ldt2 = LocalDate.MAX;
        LocalDateRoot loadedRoot = new LocalDateRoot(ldt2);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {

            assertEquals(originalRoot.getDate(), loadedRoot.getDate(), "LocalDateTime should be equal after storing and reloading");
        }
    }

    private class LocalDateRoot
    {
        private LocalDate date;

        public LocalDateRoot(LocalDate date)
        {
            this.date = date;
        }

        public LocalDate getDate()
        {
            return date;
        }

        public void setDate(LocalDate date)
        {
            this.date = date;
        }
    }

    @Test
    void saveLocalDateTimeDataTest()
    {
        LocalDate ldt = LocalDate.of(2020, 1, 1);

        LocalDateData root = new LocalDateData(ldt);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        LocalDateData loadedRoot = new LocalDateData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(ldt, loadedRoot.getValue(), "LocalDateTimeData should be equal after storing and reloading");
        }
    }

    private class LocalDateData
    {
        private LocalDate value;

        public LocalDateData(LocalDate value)
        {
            this.value = value;
        }

        public LocalDateData()
        {
        }

        public LocalDate getValue()
        {
            return value;
        }

        public void setValue(LocalDate value)
        {
            this.value = value;
        }
    }
}
