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


import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test helpers that locate the files of an on-disk index in the segment layout (see
 * {@link SegmentFiles}).
 */
final class SegmentLayout
{
    private static final Pattern VECTOR_FILE = Pattern.compile(
        Pattern.quote(VectorFile.HEADER_FILE_EXT) + "(-\\d+)?$"
    );

    static Path manifest(
        final Path   indexDirectory,
        final String name
    )
    {
        return indexDirectory.resolve(SegmentFiles.manifestFileName(name));
    }

    /**
     * Returns whether the index has a manifest, i.e. whether a persist committed it.
     */
    static boolean indexExists(
        final Path   indexDirectory,
        final String name
    )
    {
        return Files.exists(manifest(indexDirectory, name));
    }

    /**
     * Returns the graph files of all segments in the directory, in ascending sequence.
     */
    static List<Path> graphFiles(
        final Path   indexDirectory,
        final String name
    )
    {
        return segmentFiles(indexDirectory, name, SegmentFiles.GRAPH_FILE_EXT);
    }

    /**
     * Returns the {@code .pqv} files of all segments in the directory, in ascending sequence.
     */
    static List<Path> pqVectorsFiles(
        final Path   indexDirectory,
        final String name
    )
    {
        return segmentFiles(indexDirectory, name, SegmentFiles.PQV_FILE_EXT);
    }

    /**
     * Returns the graph file of the only segment in the directory, failing if there is not exactly one.
     */
    static Path onlyGraphFile(
        final Path   indexDirectory,
        final String name
    )
    {
        final List<Path> graphs = graphFiles(indexDirectory, name);
        assertEquals(1, graphs.size(), "expected exactly one segment graph, found " + graphs);
        return graphs.get(0);
    }

    /**
     * Returns the {@code .pqv} file of the only segment in the directory, failing if there is not exactly
     * one.
     */
    static Path onlyPqVectorsFile(
        final Path   indexDirectory,
        final String name
    )
    {
        final List<Path> files = pqVectorsFiles(indexDirectory, name);
        assertEquals(1, files.size(), "expected exactly one segment .pqv, found " + files);
        return files.get(0);
    }

    /**
     * Returns every file in the directory, sorted by name, except those of the vector file: it lives beside the
     * segment layout, and none of the layout's cleanups touch it.
     */
    static List<String> fileNames(final Path indexDirectory)
    {
        try(final Stream<Path> files = Files.list(indexDirectory))
        {
            return files
                .map(path -> path.getFileName().toString())
                .filter(fileName -> !VECTOR_FILE.matcher(fileName).find())
                .sorted()
                .toList();
        }
        catch(final IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> segmentFiles(
        final Path   indexDirectory,
        final String name          ,
        final String extension
    )
    {
        if(!Files.isDirectory(indexDirectory))
        {
            return List.of();
        }
        final Pattern pattern = Pattern.compile(
            Pattern.quote(name) + "\\.seg-(\\d+)" + Pattern.quote(extension)
        );
        try(final Stream<Path> files = Files.list(indexDirectory))
        {
            return files
                .filter(path -> pattern.matcher(path.getFileName().toString()).matches())
                .sorted(Comparator.comparingLong(path -> sequenceOf(pattern, path)))
                .toList();
        }
        catch(final IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static long sequenceOf(
        final Pattern pattern,
        final Path    path
    )
    {
        final Matcher matcher = pattern.matcher(path.getFileName().toString());
        if(!matcher.matches())
        {
            throw new IllegalArgumentException(path.toString());
        }
        return Long.parseLong(matcher.group(1));
    }

    private SegmentLayout()
    {
        throw new UnsupportedOperationException();
    }

}
