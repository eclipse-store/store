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

import java.util.ArrayDeque;

public class ArrayDequeData implements BinaryHandlerTestData {

    ArrayDeque<String> arrayDeque = new ArrayDeque<>();

    // corner-case deques
    private final ArrayDeque<String> arrayDequeWithDuplicates = new ArrayDeque<>();
    private final ArrayDeque<String> emptyArrayDeque = new ArrayDeque<>();
    private final ArrayDeque<String> arrayDequeWithLongStrings = new ArrayDeque<>();
    private final ArrayDeque<String> arrayDequeWithWhitespace = new ArrayDeque<>();
    private final ArrayDeque<String> arrayDequeWithUnicode = new ArrayDeque<>();
    private final ArrayDeque<String> arrayDequeWithEmptyString = new ArrayDeque<>();
    private final ArrayDeque<String> arrayDequeManyElements = new ArrayDeque<>();

    @Override
    public ArrayDequeData fillSampleData() {
        arrayDeque.add("first");
        arrayDeque.add("second");
        arrayDeque.add("third");

        // duplicates (different String instances with same content + interned)
        arrayDequeWithDuplicates.add("dup");
        arrayDequeWithDuplicates.add("dup");
        arrayDequeWithDuplicates.add("dup");

        // empty deque left intentionally empty
        // emptyArrayDeque is already empty

        // long string
        StringBuilder sb = new StringBuilder(12000);
        for (int i = 0; i < 12000; ++i) {
            sb.append('x');
        }
        arrayDequeWithLongStrings.add(sb.toString());

        // whitespace-only entries
        arrayDequeWithWhitespace.add(" ");
        arrayDequeWithWhitespace.add("\t");
        arrayDequeWithWhitespace.add("\n");

        // unicode and emoji
        arrayDequeWithUnicode.add("čřžťýáíé");
        arrayDequeWithUnicode.add("🙂");
        arrayDequeWithUnicode.add("中文漢字");

        // empty string element
        arrayDequeWithEmptyString.add("");

        // many elements to exercise growth
        for (int i = 0; i < 1024; ++i) {
            arrayDequeManyElements.add("n" + i);
        }

        return this;
    }

    ArrayDeque<String> getArrayDeque() {
        return arrayDeque;
    }

    // getters for corner-case fields
    public ArrayDeque<String> getArrayDequeWithDuplicates() {
        return arrayDequeWithDuplicates;
    }

    public ArrayDeque<String> getEmptyArrayDeque() {
        return emptyArrayDeque;
    }

    public ArrayDeque<String> getArrayDequeWithLongStrings() {
        return arrayDequeWithLongStrings;
    }

    public ArrayDeque<String> getArrayDequeWithWhitespace() {
        return arrayDequeWithWhitespace;
    }

    public ArrayDeque<String> getArrayDequeWithUnicode() {
        return arrayDequeWithUnicode;
    }

    public ArrayDeque<String> getArrayDequeWithEmptyString() {
        return arrayDequeWithEmptyString;
    }

    public ArrayDeque<String> getArrayDequeManyElements() {
        return arrayDequeManyElements;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        ArrayDequeData copy = (ArrayDequeData) o;
        Assertions.assertIterableEquals(this.getArrayDeque(), copy.getArrayDeque());

        // corner-case verifications
        Assertions.assertIterableEquals(this.getArrayDequeWithDuplicates(), copy.getArrayDequeWithDuplicates(), "duplicates");
        Assertions.assertEquals(this.getEmptyArrayDeque().size(), copy.getEmptyArrayDeque().size(), "empty deque size");
        Assertions.assertIterableEquals(this.getArrayDequeWithLongStrings(), copy.getArrayDequeWithLongStrings(), "long strings");
        Assertions.assertIterableEquals(this.getArrayDequeWithWhitespace(), copy.getArrayDequeWithWhitespace(), "whitespace-only entries");
        Assertions.assertIterableEquals(this.getArrayDequeWithUnicode(), copy.getArrayDequeWithUnicode(), "unicode entries");
        Assertions.assertIterableEquals(this.getArrayDequeWithEmptyString(), copy.getArrayDequeWithEmptyString(), "empty string element");
        Assertions.assertIterableEquals(this.getArrayDequeManyElements(), copy.getArrayDequeManyElements(), "many elements");
    }

    @Override
    public ArrayDequeData updateSampleData( ) {
        arrayDeque.add("update");

        // modify corner-case deques a bit
        arrayDequeWithDuplicates.add("dup");
        emptyArrayDeque.add("now-non-empty");
        arrayDequeWithLongStrings.add(arrayDequeWithLongStrings.peekFirst() + "-more");
        arrayDequeWithWhitespace.add("  trimmed  ");
        arrayDequeWithUnicode.add("追加");
        arrayDequeWithEmptyString.add("");
        arrayDequeManyElements.add("n1024");

        return this;
    }

}
