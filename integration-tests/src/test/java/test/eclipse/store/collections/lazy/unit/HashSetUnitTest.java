package test.eclipse.store.collections.lazy.unit;

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

import org.eclipse.serializer.collections.lazy.LazyHashSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HashSetUnitTest {

    @Test
    void lazyHashSetConstructor() {
        LazyHashSet<String> lazyHashSet = new LazyHashSet<>();
        assertNotNull(lazyHashSet);
    }

    @Test
    void lazyHashSetConstructor_maxSegmentSize() {
        LazyHashSet<String> lazyHashSet = new LazyHashSet<>(10);
        assertNotNull(lazyHashSet);
    }

    @Test
    void iteratorTest() {
        LazyHashSet<String> lazyHashSet = Util.generateLazyHashSet(10, 100);
        for (String s : lazyHashSet) {
            assertNotNull(s);
        }
    }

    @Test
    void sizeTest() {
        LazyHashSet<String> lazyHashSet = Util.generateLazyHashSet(10, 100);
        assertEquals(100, lazyHashSet.size());
    }

    @Test
    void isEmpty() {
        LazyHashSet<String> lazyHashSet = new LazyHashSet<>();
        assertTrue(lazyHashSet.isEmpty());
        lazyHashSet = Util.generateLazyHashSet(10, 100);
        assertFalse(lazyHashSet.isEmpty());
    }

    @Test
    void contains() {
        String value = "Hi Microstream";

        LazyHashSet<String> lazyHashSet = Util.generateLazyHashSet(10, 100);
        lazyHashSet.add(value);
        assertTrue(lazyHashSet.contains(value));
    }

    @Test
    void remove() {
        String value = "Hi Microstream";
        LazyHashSet<String> lazyHashSet = Util.generateLazyHashSet(10, 100);
        lazyHashSet.add(value);
        assertTrue(lazyHashSet.remove(value));
        assertEquals(100, lazyHashSet.size());
    }

    @Test
    void clear() {
        LazyHashSet<String> lazyHashSet = Util.generateLazyHashSet(10, 100);
        lazyHashSet.clear();
        assertTrue(lazyHashSet.isEmpty());
    }
}
