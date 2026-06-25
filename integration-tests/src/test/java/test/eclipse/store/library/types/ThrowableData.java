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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThrowableData implements BinaryHandlerTestData
{
    Throwable value = new Throwable("Test Throwable");

    // ===== proposed edge-cases (review & cherry-pick) =====
    // Throwable has several non-trivial fields: detailMessage, cause (self-ref sentinel when unset),
    // stackTrace[], suppressedExceptions. The existing fixture compares toString() only, which leaves
    // subclass type, cause, suppressed, and stack frames untested. The probes below cover each.
    //
    // Stack-trace note: Throwable.stackTrace[] is lazily built from a transient JVM-internal backtrace.
    // If getStackTrace() is not called before serialize, the field is null and the trace is
    // unrecoverable after load (backtrace doesn't serialize). stackTraceThrowable materializes the
    // array explicitly so the round-trip is actually testable.
    //
    // Cause-self-ref note: Throwable uses cause==this as the "no cause" sentinel. We verify via the
    // public getCause() API (returns null in both cases) — no identity assertion needed.
    private Throwable nullMessageThrowable;
    private Throwable subclassThrowable;
    private Throwable chainedThrowable;
    private Throwable suppressedThrowable;
    private Throwable stackTraceThrowable;

    @Override
    public ThrowableData fillSampleData()
    {
        try {
            value.toString();
        } catch (Throwable throwable) {
            value = throwable;
        }

        // ===== proposed edge-cases =====
        nullMessageThrowable = new Throwable();
        subclassThrowable = new RuntimeException("runtime");
        chainedThrowable = new RuntimeException("outer", new IllegalStateException("inner"));

        Throwable withSuppressed = new Throwable("with suppressed");
        withSuppressed.addSuppressed(new RuntimeException("s1"));
        withSuppressed.addSuppressed(new IllegalStateException("s2"));
        suppressedThrowable = withSuppressed;

        stackTraceThrowable = new RuntimeException("with stack");
        stackTraceThrowable.getStackTrace(); // force lazy materialization of stackTrace[]

        return this;
    }

    // ===== proposed edge-cases — getters =====

    public Throwable getNullMessageThrowable() {
        return nullMessageThrowable;
    }

    public Throwable getSubclassThrowable() {
        return subclassThrowable;
    }

    public Throwable getChainedThrowable() {
        return chainedThrowable;
    }

    public Throwable getSuppressedThrowable() {
        return suppressedThrowable;
    }

    public Throwable getStackTraceThrowable() {
        return stackTraceThrowable;
    }

    @Override
    public void proveResults(Object o)
    {
        Assertions.assertNotNull(o);
        ThrowableData copy = (ThrowableData) o;
        assertAll("Throwable tests",
                () -> assertEquals(this.value.toString(), copy.value.toString()),

                // ===== proposed edge-case verifications =====
                () -> {
                    if (this.getNullMessageThrowable() != null) {
                        assertNull(copy.getNullMessageThrowable().getMessage(), "null detail message");
                        // getCause() must return null whether cause is the self-ref sentinel or genuinely null
                        assertNull(copy.getNullMessageThrowable().getCause(), "no cause → getCause() = null");
                        assertEquals(Throwable.class, copy.getNullMessageThrowable().getClass(), "exact Throwable subclass preserved");
                    } else {
                        assertNull(copy.getNullMessageThrowable());
                    }
                },
                () -> {
                    if (this.getSubclassThrowable() != null) {
                        assertEquals(RuntimeException.class, copy.getSubclassThrowable().getClass(), "concrete subclass preserved");
                        assertEquals("runtime", copy.getSubclassThrowable().getMessage());
                    } else {
                        assertNull(copy.getSubclassThrowable());
                    }
                },
                () -> {
                    if (this.getChainedThrowable() != null) {
                        assertEquals("outer", copy.getChainedThrowable().getMessage());
                        Throwable cause = copy.getChainedThrowable().getCause();
                        assertNotNull(cause, "cause must survive round-trip");
                        assertEquals(IllegalStateException.class, cause.getClass(), "cause subclass preserved");
                        assertEquals("inner", cause.getMessage(), "cause message preserved");
                    } else {
                        assertNull(copy.getChainedThrowable());
                    }
                },
                () -> {
                    if (this.getSuppressedThrowable() != null) {
                        Throwable[] suppressed = copy.getSuppressedThrowable().getSuppressed();
                        assertEquals(2, suppressed.length, "suppressed[] length preserved");
                        assertEquals("s1", suppressed[0].getMessage(), "suppressed[0] message");
                        assertEquals(RuntimeException.class, suppressed[0].getClass(), "suppressed[0] subclass");
                        assertEquals("s2", suppressed[1].getMessage(), "suppressed[1] message");
                        assertEquals(IllegalStateException.class, suppressed[1].getClass(), "suppressed[1] subclass");
                    } else {
                        assertNull(copy.getSuppressedThrowable());
                    }
                },
                () -> {
                    if (this.getStackTraceThrowable() != null) {
                        StackTraceElement[] stack = copy.getStackTraceThrowable().getStackTrace();
                        assertTrue(stack.length > 0, "stack trace materialized before serialize — must survive round-trip");
                        // Construction frame must be present — the throwable was built inside fillSampleData
                        boolean hasFillSampleFrame = false;
                        for (StackTraceElement e : stack) {
                            if ("fillSampleData".equals(e.getMethodName())) {
                                hasFillSampleFrame = true;
                                break;
                            }
                        }
                        assertTrue(hasFillSampleFrame, "expected 'fillSampleData' frame in preserved stack trace");
                    } else {
                        assertNull(copy.getStackTraceThrowable());
                    }
                }
        );
    }
}
