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

import java.nio.file.Path;
import java.util.Currency;
import java.util.Locale;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LocaleCurrencyTest
{
    @TempDir
    Path tempDir;

    @Test
    void localeStoreAndReload()
    {
        Locale l = new Locale("cs", "CZ");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(l, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Locale loaded = (Locale) storageManager.root();

            assertEquals(l, loaded, "Locale should be equal after storing and reloading");
        }
    }

    @Test
    void currencyStoreAndReload()
    {
        Currency c = Currency.getInstance("CZK");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(c, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Currency loaded = (Currency) storageManager.root();

            assertEquals(c, loaded, "Currency should be equal after storing and reloading");
        }
    }

    @Test
    void saveLocaleCurrencyDataAndReload()
    {
        Locale l = Locale.ENGLISH;
        Currency c = Currency.getInstance("USD");

        LocaleCurrencyData root = new LocaleCurrencyData(l, c);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        LocaleCurrencyData loadedRoot = new LocaleCurrencyData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(l, loadedRoot.getLocale());
            assertEquals(c, loadedRoot.getCurrency());
        }
    }

    private static class LocaleCurrencyData
    {
        private Locale locale;
        private Currency currency;

        public LocaleCurrencyData(Locale locale, Currency currency)
        {
            this.locale = locale;
            this.currency = currency;
        }

        public LocaleCurrencyData()
        {
        }

        public Locale getLocale()
        {
            return locale;
        }

        public Currency getCurrency()
        {
            return currency;
        }

        public void setLocale(Locale locale)
        {
            this.locale = locale;
        }

        public void setCurrency(Currency currency)
        {
            this.currency = currency;
        }
    }
}

