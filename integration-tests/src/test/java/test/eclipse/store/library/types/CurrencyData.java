package test.eclipse.store.library.types;

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

import org.junit.jupiter.api.Assertions;

import java.util.Currency;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CurrencyData implements BinaryHandlerTestData {

    Currency currency;

    // ===== proposed edge-cases (review & cherry-pick) =====
    // Currency is a singleton-per-ISO-4217-code; Currency.getInstance(code) returns the cached
    // instance, so identity is preserved automatically as long as the handler round-trips the
    // currency code string. Probes target the unusual fraction-digit counts (0 for JPY, 3 for
    // BHD vs. the common 2) and the ISO "for testing purposes" XTS code.
    private Currency eurCurrency;
    private Currency jpyCurrency;
    private Currency bhdCurrency;
    private Currency xtsTestCurrency;

    @Override
    public CurrencyData fillSampleData() {
        currency = Currency.getInstance(Locale.CANADA);

        // ===== proposed edge-cases =====
        eurCurrency = Currency.getInstance("EUR");
        jpyCurrency = Currency.getInstance("JPY");
        bhdCurrency = Currency.getInstance("BHD");
        xtsTestCurrency = Currency.getInstance("XTS");

        return this;
    }

    public Currency getCurrency() {
        return currency;
    }

    // ===== proposed edge-cases — getters =====

    public Currency getEurCurrency() {
        return eurCurrency;
    }

    public Currency getJpyCurrency() {
        return jpyCurrency;
    }

    public Currency getBhdCurrency() {
        return bhdCurrency;
    }

    public Currency getXtsTestCurrency() {
        return xtsTestCurrency;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        CurrencyData copy = (CurrencyData) o;
        assertAll("Currency tests",
                () -> assertEquals(this.getCurrency(), copy.getCurrency(), "Currency"),

                // ===== proposed edge-case verifications =====
                () -> {
                    if (this.getEurCurrency() != null) {
                        assertEquals(Currency.getInstance("EUR"), copy.getEurCurrency());
                        assertEquals(2, copy.getEurCurrency().getDefaultFractionDigits(), "EUR fraction digits");
                    } else {
                        assertNull(copy.getEurCurrency());
                    }
                },
                () -> {
                    if (this.getJpyCurrency() != null) {
                        assertEquals(Currency.getInstance("JPY"), copy.getJpyCurrency());
                        assertEquals(0, copy.getJpyCurrency().getDefaultFractionDigits(), "JPY has 0 fraction digits");
                    } else {
                        assertNull(copy.getJpyCurrency());
                    }
                },
                () -> {
                    if (this.getBhdCurrency() != null) {
                        assertEquals(Currency.getInstance("BHD"), copy.getBhdCurrency());
                        assertEquals(3, copy.getBhdCurrency().getDefaultFractionDigits(), "BHD has 3 fraction digits");
                    } else {
                        assertNull(copy.getBhdCurrency());
                    }
                },
                () -> {
                    if (this.getXtsTestCurrency() != null) {
                        assertEquals(Currency.getInstance("XTS"), copy.getXtsTestCurrency());
                        assertEquals("XTS", copy.getXtsTestCurrency().getCurrencyCode());
                    } else {
                        assertNull(copy.getXtsTestCurrency());
                    }
                }
        );
    }
}
