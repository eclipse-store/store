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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SegmentSequences}: sequences only grow within a run, and the ones being written or
 * still open are protected from the cleanup of unlisted files.
 */
class SegmentSequencesTest
{
    @Test
    void reserve_isAboveEveryFileAndEverySequenceHandedOut(@TempDir final Path directory) throws IOException
    {
        Files.createFile(directory.resolve(SegmentFiles.graphFileName("idx", 7)));
        final SegmentSequences sequences = new SegmentSequences();

        assertEquals(8, sequences.reserve(directory, "idx"));

        // The file of 8 was never written, as after an abandoned flush whose files are gone: 8 is
        // still not handed out again.
        assertEquals(9, sequences.reserve(directory, "idx"));
    }

    @Test
    void observe_raisesTheNextSequence(@TempDir final Path directory) throws IOException
    {
        final SegmentSequences sequences = new SegmentSequences();
        sequences.observe(41);

        assertEquals(42, sequences.reserve(directory, "idx"));
    }

    @Test
    void isProtected_coversWrittenAndOpenSegmentsUntilReleased(@TempDir final Path directory)
        throws IOException
    {
        final SegmentSequences sequences = new SegmentSequences();
        final long             writing   = sequences.reserve(directory, "idx");
        sequences.opened(20);
        sequences.opened(20);

        assertTrue(sequences.isProtected(writing));
        assertTrue(sequences.isProtected(20));
        assertFalse(sequences.isProtected(21));

        sequences.written(writing);
        assertFalse(sequences.isProtected(writing));
        assertFalse(sequences.closed(20), "still open once");
        assertTrue(sequences.isProtected(20), "opened twice, closed once");

        assertTrue(sequences.closed(20), "the last close leaves nothing protecting it");
        assertFalse(sequences.isProtected(20));
    }

    @Test
    void deleteIfUnprotected_keepsTheFilesOfProtectedSequencesOnly(@TempDir final Path directory)
        throws IOException
    {
        final SegmentSequences sequences = new SegmentSequences();
        final long             writing   = sequences.reserve(directory, "idx");
        sequences.opened(20);
        final Path reserved = Files.createFile(directory.resolve(SegmentFiles.graphFileName("idx", writing)));
        final Path open     = Files.createFile(directory.resolve(SegmentFiles.graphFileName("idx", 20)));
        final Path leftover = Files.createFile(directory.resolve(SegmentFiles.graphFileName("idx", 21)));

        assertFalse(sequences.deleteIfUnprotected(writing, reserved));
        assertFalse(sequences.deleteIfUnprotected(20, open));
        assertTrue(sequences.deleteIfUnprotected(21, leftover));

        assertTrue(Files.exists(reserved));
        assertTrue(Files.exists(open));
        assertFalse(Files.exists(leftover));
    }

}
