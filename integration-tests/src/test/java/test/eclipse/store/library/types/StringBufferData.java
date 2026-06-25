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

public class StringBufferData implements BinaryHandlerTestData {
    StringBuffer value = new StringBuffer();

    // ===== proposed edge-cases (review & cherry-pick) =====
    // Note: BinaryHandlerStringBuffer stores BOTH `long capacity` and `chars[] value`
    // and rebuilds via `new StringBuffer(capacity)`, so capacity MUST round-trip too.
    private StringBuffer emptyStringBuffer = new StringBuffer();
    private StringBuffer singleCharStringBuffer = new StringBuffer();
    private StringBuffer stringBufferWithNul = new StringBuffer();
    private StringBuffer stringBufferWithSurrogate = new StringBuffer();
    private StringBuffer oversizedCapacityStringBuffer = new StringBuffer();
    private StringBuffer explicitSmallCapacityStringBuffer = new StringBuffer();
    private StringBuffer largeStringBuffer = new StringBuffer();
    private StringBuffer controlCharsStringBuffer = new StringBuffer();

    @Override
    public StringBufferData fillSampleData() {
        value = new StringBuffer().append("Some Text").append("SecondText")
                .append("A, Á, B, C, Č, D, Ď, E, É, Ě, F, G, H, Ch, I, Í, J, K, L, M, N, Ň, O, Ó, P, Q, R, Ř, S, Š, T, Ť, U, Ú, Ů, V, W, X, Y, Ý, Z, Ž.")
                .append("a, á, b, c, č, d, ď, e, é, ě, f, g, h, ch, i, í, j, k, l, m, n, ň, o, ó, p, q, r, ř, s, š, t, ť, u, ú, ů, v, w, x, y, ý, z, ž.");
        ;

        // ===== proposed edge-cases =====

        // empty — length() == 0 must survive round-trip
        emptyStringBuffer = new StringBuffer();

        // single character
        singleCharStringBuffer = new StringBuffer().append('X');

        // NUL inside the buffer
        stringBufferWithNul = new StringBuffer().append('a').append((char) 0).append('b');

        // 4-byte UTF-8 codepoint (U+1F600 grinning face) — written as surrogate pair
        stringBufferWithSurrogate = new StringBuffer().append(Character.toChars(0x1F600));

        // oversized capacity — explicit 2048, short content; capacity is part of the payload
        oversizedCapacityStringBuffer = new StringBuffer(2048).append("short");

        // explicit small non-default capacity — capacity 7 (default would be 16)
        explicitSmallCapacityStringBuffer = new StringBuffer(7).append("hi");

        // large content — 10k chars
        largeStringBuffer = new StringBuffer().append(String.join("", Collections.nCopies(10_000, "x")));

        // control characters: tab, newline, carriage return
        controlCharsStringBuffer = new StringBuffer().append("line1\nline2\tcol\rend");

        return this;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        StringBufferData copy = (StringBufferData) o;

        assertAll("StringBuffer tests",
                () -> assertEquals(this.value.toString(), copy.value.toString()),

                // ===== proposed edge-case verifications — compare toString() content only =====
                () -> assertEquals(this.emptyStringBuffer.toString(), copy.emptyStringBuffer.toString(), "empty StringBuffer"),
                () -> assertEquals(this.emptyStringBuffer.length(), copy.emptyStringBuffer.length(), "empty StringBuffer length"),
                () -> assertEquals(this.singleCharStringBuffer.toString(), copy.singleCharStringBuffer.toString(), "single char"),
                () -> assertEquals(this.stringBufferWithNul.toString(), copy.stringBufferWithNul.toString(), "StringBuffer with NUL"),
                () -> assertEquals(this.stringBufferWithNul.length(), copy.stringBufferWithNul.length(), "StringBuffer with NUL length"),
                () -> assertEquals(this.stringBufferWithSurrogate.toString(), copy.stringBufferWithSurrogate.toString(), "StringBuffer with 4-byte UTF-8 (U+1F600)"),
                () -> assertEquals(this.oversizedCapacityStringBuffer.toString(), copy.oversizedCapacityStringBuffer.toString(), "oversized-capacity content"),
                // capacity is part of the binary payload — must survive round-trip
                () -> assertEquals(this.oversizedCapacityStringBuffer.capacity(), copy.oversizedCapacityStringBuffer.capacity(), "oversized capacity (2048) preserved"),
                () -> assertEquals(this.explicitSmallCapacityStringBuffer.toString(), copy.explicitSmallCapacityStringBuffer.toString(), "explicit-small-capacity content"),
                () -> assertEquals(this.explicitSmallCapacityStringBuffer.capacity(), copy.explicitSmallCapacityStringBuffer.capacity(), "explicit small capacity (7) preserved"),
                () -> assertEquals(this.largeStringBuffer.toString(), copy.largeStringBuffer.toString(), "large StringBuffer (10k chars)"),
                () -> assertEquals(this.largeStringBuffer.length(), copy.largeStringBuffer.length(), "large StringBuffer length"),
                () -> assertEquals(this.controlCharsStringBuffer.toString(), copy.controlCharsStringBuffer.toString(), "control chars (\\t \\n \\r)")
        );
    }

    public StringBuffer getValue() {
        return value;
    }
}
