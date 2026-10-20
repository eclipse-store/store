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
import java.util.Set;

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
    void protectedSequences_coverWrittenAndOpenSegmentsUntilReleased(@TempDir final Path directory)
        throws IOException
    {
        final SegmentSequences sequences = new SegmentSequences();
        final long             writing   = sequences.reserve(directory, "idx");
        sequences.opened(20);
        sequences.opened(20);

        assertEquals(Set.of(writing, 20L), sequences.protectedSequences());

        sequences.written(writing);
        assertFalse(sequences.closed(20), "still open once");
        assertEquals(Set.of(20L), sequences.protectedSequences(), "opened twice, closed once");

        assertTrue(sequences.closed(20), "the last close leaves nothing protecting it");
        assertTrue(sequences.protectedSequences().isEmpty());
    }

}
