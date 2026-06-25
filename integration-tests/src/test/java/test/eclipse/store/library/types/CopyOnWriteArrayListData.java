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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CopyOnWriteArrayListData implements BinaryHandlerTestData {

        CopyOnWriteArrayList<PrimitiveTypes> value = new CopyOnWriteArrayList<>();

        // ===== proposed edge-cases (review & cherry-pick) =====
        private CopyOnWriteArrayList<Integer> nullElementList = new CopyOnWriteArrayList<>();
        private CopyOnWriteArrayList<String> emptyList = new CopyOnWriteArrayList<>();
        private CopyOnWriteArrayList<Integer> singleElementList = new CopyOnWriteArrayList<>();
        private CopyOnWriteArrayList<Integer> duplicatesList = new CopyOnWriteArrayList<>();
        private CopyOnWriteArrayList<Integer> extremeValuesList = new CopyOnWriteArrayList<>();
        private CopyOnWriteArrayList<Integer> largeList = new CopyOnWriteArrayList<>();
        private CopyOnWriteArrayList<String> stringEdgeCasesList = new CopyOnWriteArrayList<>();

        @Override
        public CopyOnWriteArrayListData fillSampleData() {
            PrimitiveTypes p = new PrimitiveTypes();
            p.fillSampleData();

            value.add(new PrimitiveTypes());
            value.add(p);
            value.add(new PrimitiveTypes());

            // ===== proposed edge-cases =====

            // COWAL allows null elements (unlike e.g. Collections.synchronizedList wrapping ImmutableCollection)
            nullElementList = new CopyOnWriteArrayList<>();
            nullElementList.add(1);
            nullElementList.add(null);
            nullElementList.add(2);
            nullElementList.add(null);

            // empty — remains empty after round-trip
            emptyList = new CopyOnWriteArrayList<>();

            // singleton
            singleElementList = new CopyOnWriteArrayList<>();
            singleElementList.add(42);

            // duplicates — same value multiple times
            duplicatesList = new CopyOnWriteArrayList<>();
            duplicatesList.add(7);
            duplicatesList.add(7);
            duplicatesList.add(7);

            // extreme Integer values
            extremeValuesList = new CopyOnWriteArrayList<>();
            extremeValuesList.add(Integer.MIN_VALUE);
            extremeValuesList.add(0);
            extremeValuesList.add(Integer.MAX_VALUE);

            // large (10k) — COWAL grows linearly on every add (copy-on-write); round-trip must preserve all
            largeList = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 10_000; i++) {
                largeList.add(i);
            }

            // string edge cases: empty, NUL inside, 4-byte UTF-8 (U+1F600), long
            stringEdgeCasesList = new CopyOnWriteArrayList<>();
            stringEdgeCasesList.add("");
            stringEdgeCasesList.add("a" + ((char) 0) + "b");
            stringEdgeCasesList.add(new String(Character.toChars(0x1F600)));
            stringEdgeCasesList.add(String.join("", Collections.nCopies(1000, "x")));

            return this;
        }

        CopyOnWriteArrayList<PrimitiveTypes> getValue() {
            return value;
        }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        CopyOnWriteArrayListData copy = (CopyOnWriteArrayListData) o;

        assertAll("CopyOnWriteArrayList tests",
                () -> assertIterableEquals(this.getValue(), copy.getValue()),

                // ===== proposed edge-case verifications =====
                () -> assertIterableEquals(this.nullElementList, copy.nullElementList, "null elements + insertion order"),
                () -> {
                    assertNotNull(copy.emptyList);
                    assertTrue(copy.emptyList.isEmpty(), "empty list remains empty");
                },
                () -> assertIterableEquals(this.singleElementList, copy.singleElementList, "single element"),
                () -> assertIterableEquals(this.duplicatesList, copy.duplicatesList, "duplicates preserved"),
                () -> assertIterableEquals(this.extremeValuesList, copy.extremeValuesList, "Integer MIN/MAX + 0"),
                () -> assertEquals(this.largeList.size(), copy.largeList.size(), "large list size"),
                () -> assertIterableEquals(this.largeList, copy.largeList, "large list (10k) — order preserved"),
                () -> assertIterableEquals(this.stringEdgeCasesList, copy.stringEdgeCasesList, "string edge cases (empty, NUL, surrogate pair, long)")
        );
    }
}
