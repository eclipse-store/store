package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
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


import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The background task manager downgrades a task failure to a debug line only when it is the abandoned-rebuild
 * exception a close produces; every other failure, in particular any other {@link IllegalStateException} raised
 * during shutdown, must stay classified as a real error.
 */
class RebuildAbandonedExceptionTest
{
    @Test
    void isCause_rebuildAbandoned_isRecognised()
    {
        final RebuildAbandonedException abandoned = new RebuildAbandonedException("vec");

        assertTrue(RebuildAbandonedException.isCause(abandoned));
        assertInstanceOf(IllegalStateException.class, abandoned, "callers keep catching IllegalStateException");
        assertTrue(abandoned.getMessage().contains("closing"));
    }

    @Test
    void isCause_wrappedRebuildAbandoned_isRecognised()
    {
        // The shape of a ForkJoin rethrow: a re-created exception carrying the original as its cause.
        final IllegalStateException wrapper = new IllegalStateException(
            "re-created", new RuntimeException(new RebuildAbandonedException("vec")));

        assertTrue(RebuildAbandonedException.isCause(wrapper));
    }

    @Test
    void isCause_ordinaryIllegalStateException_isNotRecognised()
    {
        // What a real failure during shutdown looks like, e.g. jVector rejecting a duplicate node.
        assertFalse(RebuildAbandonedException.isCause(new IllegalStateException("Node 7 already exists")));
        assertFalse(RebuildAbandonedException.isCause(
            new IllegalStateException("wrapper", new IllegalStateException("is closing"))));
        assertFalse(RebuildAbandonedException.isCause(new UncheckedIOException(new IOException("disk"))));
        assertFalse(RebuildAbandonedException.isCause(null));
    }

    @Test
    void isCause_deepCauseChain_isRecognised()
    {
        // Deeper than any fixed traversal limit would reach.
        Throwable chain = new RebuildAbandonedException("vec");
        for(int i = 0; i < 1_000; i++)
        {
            chain = new IllegalStateException("level " + i, chain);
        }

        assertTrue(RebuildAbandonedException.isCause(chain));
    }

    @Test
    void isCause_cyclicCauseChainWithMatchBeyondCycleStart_isRecognised()
    {
        final IllegalStateException first  = new IllegalStateException("first");
        final IllegalStateException second = new IllegalStateException("second", new RebuildAbandonedException("vec"));
        first.initCause(second);

        assertTrue(RebuildAbandonedException.isCause(first));
    }

    @Test
    void isCause_cyclicCauseChain_terminates()
    {
        final IllegalStateException first  = new IllegalStateException("first");
        final IllegalStateException second = new IllegalStateException("second", first);
        first.initCause(second);

        assertFalse(RebuildAbandonedException.isCause(first));
    }

    @Test
    void isCause_cycleEnteredBelowTheRoot_terminatesOrMatches()
    {
        // root -> a -> b -> a: the cycle does not include the root.
        final IllegalStateException a    = new IllegalStateException("a");
        final IllegalStateException b    = new IllegalStateException("b", a);
        a.initCause(b);
        final IllegalStateException root = new IllegalStateException("root", a);
        assertFalse(RebuildAbandonedException.isCause(root));

        // root -> c -> abandoned -> c: the match sits inside a cycle entered below the root.
        final IllegalStateException     c         = new IllegalStateException("c");
        final RebuildAbandonedException abandoned = new RebuildAbandonedException("vec");
        c.initCause(abandoned);
        abandoned.initCause(c);
        assertTrue(RebuildAbandonedException.isCause(new IllegalStateException("root", c)));
    }

    @Test
    void isCause_selfCause_terminates()
    {
        // Throwable.initCause rejects itself as cause, but getCause() can be overridden to return it.
        final IllegalStateException selfCaused = new IllegalStateException("self")
        {
            @Override
            public synchronized Throwable getCause()
            {
                return this;
            }
        };

        assertFalse(RebuildAbandonedException.isCause(selfCaused));
    }

    @Test
    void isCause_onlySuppressed_isNotRecognised()
    {
        // Suppressed exceptions are secondary failures (e.g. of a cleanup), not what made the task fail.
        final IllegalStateException primary = new IllegalStateException("Node 7 already exists");
        primary.addSuppressed(new RebuildAbandonedException("vec"));

        assertFalse(RebuildAbandonedException.isCause(primary));
    }
}
