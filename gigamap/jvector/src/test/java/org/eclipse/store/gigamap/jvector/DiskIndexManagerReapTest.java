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

import org.eclipse.serializer.exceptions.IORuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cleanup of unlisted files decides a segment file's protection when it deletes the file. A merge reserves,
 * writes and opens its segment without the flush permit, so a reservation or an opening can come after the cleanup
 * began; a protection read up front would let the cleanup delete the files of a segment in use.
 */
class DiskIndexManagerReapTest
{
    private static final String NAME      = "idx";
    private static final int    DIMENSION = 8;

    @Test
    void reapUnlistedFiles_segmentReservedAfterTheCleanupBegan_keepsItsFiles(@TempDir final Path directory)
        throws IOException
    {
        final SegmentSequences sequences = new SegmentSequences();
        final List<Path>       leftover  = segmentFiles(directory, 1);
        final long[]           reserved  = new long[1];
        final DiskIndexManager manager   = manager(directory, sequences, () ->
        {
            // As a merge does: reserve, then write the files.
            try
            {
                reserved[0] = sequences.reserve(directory, NAME);
                segmentFiles(directory, reserved[0]);
            }
            catch(final IOException e)
            {
                throw new IORuntimeException(e);
            }
        });

        manager.reapUnlistedFiles(Set.of());

        segmentFilesIn(directory, reserved[0]).forEach(file -> assertTrue(Files.exists(file),
            "the reserved segment's file must survive: " + file.getFileName()));
        leftover.forEach(file -> assertFalse(Files.exists(file), "a leftover goes: " + file.getFileName()));
    }

    @Test
    void reapUnlistedFiles_segmentOpenedAfterTheCleanupBegan_keepsItsFiles(@TempDir final Path directory)
        throws IOException
    {
        final SegmentSequences sequences = new SegmentSequences();
        final List<Path>       opened    = segmentFiles(directory, 4);
        final DiskIndexManager manager   = manager(directory, sequences, () -> sequences.opened(4));

        manager.reapUnlistedFiles(Set.of());

        opened.forEach(file -> assertTrue(Files.exists(file),
            "the open segment's file must survive: " + file.getFileName()));
    }

    @Test
    void reapUnlistedFiles_keepsListedSegmentsAndDeletesTheManifestTempFile(@TempDir final Path directory)
        throws IOException
    {
        final List<Path> listed   = segmentFiles(directory, 2);
        final List<Path> leftover = segmentFiles(directory, 3);
        final Path       temp     = Files.createFile(
            directory.resolve(SegmentFiles.manifestFileName(NAME) + SegmentFiles.TEMP_FILE_EXT)
        );

        manager(directory, new SegmentSequences(), null).reapUnlistedFiles(Set.of(2L));

        listed.forEach(file -> assertTrue(Files.exists(file), "listed: " + file.getFileName()));
        leftover.forEach(file -> assertFalse(Files.exists(file), "leftover: " + file.getFileName()));
        assertFalse(Files.exists(temp), "the manifest's temp file is a leftover too");
    }

    private static DiskIndexManager manager(
        final Path             directory,
        final SegmentSequences sequences,
        final Runnable         listingHook
    )
    {
        final DiskIndexManager.IndexStateProvider provider = new DiskIndexManager.IndexStateProvider()
        {
            @Override
            public long getExpectedVectorCount()
            {
                return 0L;
            }

            @Override
            public long getHighestEntityId()
            {
                return -1L;
            }

            @Override
            public long getStructuralModCount()
            {
                return 0L;
            }

            @Override
            public Runnable reapListingTestHook()
            {
                return listingHook;
            }
        };
        final VectorIndexConfiguration configuration = VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .onDisk(true)
            .indexDirectory(directory)
            .build();
        return new DiskIndexManager.Default(
            provider, NAME, directory, DIMENSION, GraphFormat.of(configuration), false, sequences
        );
    }

    private static List<Path> segmentFiles(final Path directory, final long sequence) throws IOException
    {
        final List<Path> files = segmentFilesIn(directory, sequence);
        for(final Path file : files)
        {
            Files.createFile(file);
        }
        return files;
    }

    private static List<Path> segmentFilesIn(final Path directory, final long sequence)
    {
        return List.of(
            directory.resolve(SegmentFiles.graphFileName(NAME, sequence)),
            directory.resolve(SegmentFiles.ordinalMapFileName(NAME, sequence)),
            directory.resolve(SegmentFiles.pqVectorsFileName(NAME, sequence))
        );
    }
}
