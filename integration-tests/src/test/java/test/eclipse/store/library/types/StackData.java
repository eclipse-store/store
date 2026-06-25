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

import java.util.Stack;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StackData implements BinaryHandlerTestData {
    private Stack<Integer> stack = new Stack<>();

    // ===== proposed edge-cases (review & cherry-pick) =====
    // Stack extends Vector — storage handler is likely the Vector handler. The probes target the
    // LIFO API: after round-trip pop/peek/search must reflect the original push order, including
    // boundary cases (empty, null entries, duplicates, large extent).
    private Stack<Integer> emptyStack;
    private Stack<Integer> largeStack;
    private Stack<Integer> nullsStack;
    private Stack<Integer> duplicatesStack;
    private Stack<Integer> peekAndSearchStack;

    Stack<Integer> getStack() {
        return stack;
    }

    // ===== proposed edge-cases — getters =====

    public Stack<Integer> getEmptyStack() {
        return emptyStack;
    }

    public Stack<Integer> getLargeStack() {
        return largeStack;
    }

    public Stack<Integer> getNullsStack() {
        return nullsStack;
    }

    public Stack<Integer> getDuplicatesStack() {
        return duplicatesStack;
    }

    public Stack<Integer> getPeekAndSearchStack() {
        return peekAndSearchStack;
    }

    @Override
    public StackData fillSampleData() {
        stack.push(1);
        stack.push(2);
        stack.push(3);

        // ===== proposed edge-cases =====
        emptyStack = new Stack<>();
        largeStack = createLargeStack();
        nullsStack = createNullsStack();
        duplicatesStack = createDuplicatesStack();
        peekAndSearchStack = createPeekAndSearchStack();

        return this;
    }

    Stack<Integer> createLargeStack() {
        Stack<Integer> s = new Stack<>();
        for (int i = 0; i < 1000; i++) {
            s.push(i);
        }
        return s;
    }

    Stack<Integer> createNullsStack() {
        // Stack permits null (inherited from Vector). pop order from top: null, 1, null
        Stack<Integer> s = new Stack<>();
        s.push(null);
        s.push(1);
        s.push(null);
        return s;
    }

    Stack<Integer> createDuplicatesStack() {
        Stack<Integer> s = new Stack<>();
        s.push(5);
        s.push(5);
        s.push(5);
        return s;
    }

    Stack<Integer> createPeekAndSearchStack() {
        // After push(10), push(20), push(30): top = 30; search() is 1-based from top:
        // search(30) = 1, search(20) = 2, search(10) = 3, search(99) = -1
        Stack<Integer> s = new Stack<>();
        s.push(10);
        s.push(20);
        s.push(30);
        return s;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        StackData copy = (StackData) o;

        assertAll("Stack tests",
                // Existing assertion — pop order on the primary stack
                () -> {
                    if (this.stack.isEmpty()) {
                        assertTrue(copy.getStack().isEmpty());
                    } else {
                        assertEquals(3, copy.getStack().pop());
                        assertEquals(2, copy.getStack().pop());
                        assertEquals(1, copy.getStack().pop());
                    }
                },

                // ===== proposed edge-case verifications =====
                () -> {
                    if (this.getEmptyStack() != null) {
                        assertTrue(copy.getEmptyStack().isEmpty(), "empty stack remains empty after round-trip");
                    } else {
                        assertNull(copy.getEmptyStack());
                    }
                },
                () -> {
                    if (this.getLargeStack() != null) {
                        // Verify LIFO end-to-end: pop must yield 999, 998, …, 0
                        assertEquals(1000, copy.getLargeStack().size(), "large stack size");
                        for (int i = 999; i >= 0; i--) {
                            assertEquals(Integer.valueOf(i), copy.getLargeStack().pop(), "large stack pop at expected " + i);
                        }
                        assertTrue(copy.getLargeStack().isEmpty(), "large stack drained");
                    } else {
                        assertNull(copy.getLargeStack());
                    }
                },
                () -> {
                    if (this.getNullsStack() != null) {
                        assertEquals(3, copy.getNullsStack().size(), "null-stack size");
                        assertNull(copy.getNullsStack().pop(), "top = null");
                        assertEquals(Integer.valueOf(1), copy.getNullsStack().pop(), "middle = 1");
                        assertNull(copy.getNullsStack().pop(), "bottom = null");
                    } else {
                        assertNull(copy.getNullsStack());
                    }
                },
                () -> {
                    if (this.getDuplicatesStack() != null) {
                        assertEquals(3, copy.getDuplicatesStack().size(), "duplicates size");
                        assertEquals(Integer.valueOf(5), copy.getDuplicatesStack().pop());
                        assertEquals(Integer.valueOf(5), copy.getDuplicatesStack().pop());
                        assertEquals(Integer.valueOf(5), copy.getDuplicatesStack().pop());
                    } else {
                        assertNull(copy.getDuplicatesStack());
                    }
                },
                () -> {
                    if (this.getPeekAndSearchStack() != null) {
                        // peek() and search() are non-mutating — order matters only relative to push order
                        Stack<Integer> s = copy.getPeekAndSearchStack();
                        assertEquals(Integer.valueOf(30), s.peek(), "peek() returns top (last pushed)");
                        assertEquals(1, s.search(30), "search(top) == 1");
                        assertEquals(2, s.search(20), "search(middle) == 2");
                        assertEquals(3, s.search(10), "search(bottom) == 3");
                        assertEquals(-1, s.search(99), "search(absent) == -1");
                        assertEquals(3, s.size(), "peek/search are non-mutating");
                    } else {
                        assertNull(copy.getPeekAndSearchStack());
                    }
                }
        );
    }
}
