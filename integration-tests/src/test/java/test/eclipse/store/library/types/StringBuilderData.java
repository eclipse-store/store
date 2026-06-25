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

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class StringBuilderData implements BinaryHandlerTestData {
    StringBuilder value = new StringBuilder();

    // ===== proposed edge-cases (review & cherry-pick) =====
    // StringBuilder is value-compared via toString(). The probes target Unicode/UTF-16 boundaries and
    // capacity-vs-content: if ES internally encodes through UTF-8, lone surrogates would be replaced
    // by U+FFFD — the loneSurrogateBuilder probe catches that. Capacity is an implementation detail
    // and not expected to round-trip (handler likely rebuilds via new StringBuilder(content)).
    private StringBuilder emptyBuilder;
    private StringBuilder nulCharBuilder;
    private StringBuilder emojiBuilder;
    private StringBuilder loneSurrogateBuilder;
    private StringBuilder oversizedCapacityBuilder;
    private StringBuilder largeBuilder;

    @Override
    public StringBuilderData fillSampleData() {
        value = new StringBuilder().append("Some Text").append("SecondText")
                .append("A, Á, B, C, Č, D, Ď, E, É, Ě, F, G, H, Ch, I, Í, J, K, L, M, N, Ň, O, Ó, P, Q, R, Ř, S, Š, T, Ť, U, Ú, Ů, V, W, X, Y, Ý, Z, Ž.")
                .append("a, á, b, c, č, d, ď, e, é, ě, f, g, h, ch, i, í, j, k, l, m, n, ň, o, ó, p, q, r, ř, s, š, t, ť, u, ú, ů, v, w, x, y, ý, z, ž.");

        // ===== proposed edge-cases =====
        emptyBuilder = new StringBuilder();
        nulCharBuilder = new StringBuilder().append("a").append((char) 0).append("b");
        emojiBuilder = new StringBuilder().append(Character.toChars(0x1F600)); // U+1F600 grinning face — 2 chars
        loneSurrogateBuilder = new StringBuilder().append((char) 0xD83D);      // unpaired high surrogate
        oversizedCapacityBuilder = new StringBuilder(10_000).append("short");
        largeBuilder = new StringBuilder(String.join("", Collections.nCopies(10_000, "x")));

        return this;
    }

    // ===== proposed edge-cases — getters =====

    public StringBuilder getEmptyBuilder() {
        return emptyBuilder;
    }

    public StringBuilder getNulCharBuilder() {
        return nulCharBuilder;
    }

    public StringBuilder getEmojiBuilder() {
        return emojiBuilder;
    }

    public StringBuilder getLoneSurrogateBuilder() {
        return loneSurrogateBuilder;
    }

    public StringBuilder getOversizedCapacityBuilder() {
        return oversizedCapacityBuilder;
    }

    public StringBuilder getLargeBuilder() {
        return largeBuilder;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        StringBuilderData copy = (StringBuilderData) o;
        assertAll("StringBuilder tests",
                () -> assertEquals(this.value.toString(), copy.value.toString()),

                // ===== proposed edge-case verifications =====
                () -> {
                    if (this.getEmptyBuilder() != null) {
                        assertEquals(0, copy.getEmptyBuilder().length(), "empty builder length");
                        assertEquals("", copy.getEmptyBuilder().toString(), "empty builder toString");
                    } else {
                        assertNull(copy.getEmptyBuilder());
                    }
                },
                () -> {
                    if (this.getNulCharBuilder() != null) {
                        assertEquals(3, copy.getNulCharBuilder().length(), "length with NUL inside");
                        assertEquals('a', copy.getNulCharBuilder().charAt(0));
                        assertEquals((char) 0, copy.getNulCharBuilder().charAt(1), "NUL preserved");
                        assertEquals('b', copy.getNulCharBuilder().charAt(2));
                    } else {
                        assertNull(copy.getNulCharBuilder());
                    }
                },
                () -> {
                    if (this.getEmojiBuilder() != null) {
                        // U+1F600 takes 2 UTF-16 code units → length 2, codePointCount 1
                        assertEquals(2, copy.getEmojiBuilder().length(), "surrogate pair char count");
                        assertEquals(1, copy.getEmojiBuilder().codePointCount(0, 2), "surrogate pair codepoint count");
                        assertEquals(0x1F600, copy.getEmojiBuilder().codePointAt(0), "U+1F600 preserved");
                    } else {
                        assertNull(copy.getEmojiBuilder());
                    }
                },
                () -> {
                    if (this.getLoneSurrogateBuilder() != null) {
                        // If handler routes through UTF-8 encoding, lone surrogate becomes U+FFFD;
                        // a char-faithful handler preserves it.
                        assertEquals(1, copy.getLoneSurrogateBuilder().length(), "lone surrogate length");
                        assertEquals((char) 0xD83D, copy.getLoneSurrogateBuilder().charAt(0), "exact lone surrogate char preserved (no U+FFFD substitution)");
                    } else {
                        assertNull(copy.getLoneSurrogateBuilder());
                    }
                },
                () -> {
                    if (this.getOversizedCapacityBuilder() != null) {
                        // Content survives; capacity is implementation-detail and not asserted on
                        assertEquals("short", copy.getOversizedCapacityBuilder().toString(), "oversized-capacity content");
                        assertEquals(5, copy.getOversizedCapacityBuilder().length());
                    } else {
                        assertNull(copy.getOversizedCapacityBuilder());
                    }
                },
                () -> {
                    if (this.getLargeBuilder() != null) {
                        assertEquals(10_000, copy.getLargeBuilder().length(), "large builder length");
                        assertEquals(this.getLargeBuilder().toString(), copy.getLargeBuilder().toString(), "large builder content");
                    } else {
                        assertNull(copy.getLargeBuilder());
                    }
                }
        );
    }
}
