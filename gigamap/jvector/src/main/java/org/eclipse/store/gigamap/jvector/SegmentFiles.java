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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

/**
 * Names and low-level file operations of the on-disk segment layout of a {@link VectorIndex}.
 * <p>
 * All files of an index named {@code <name>} live in its index directory:
 * <ul>
 *   <li>{@code <name>.manifest} - the commit point, see {@link SegmentManifest};</li>
 *   <li>{@code <name>.seg-<n>.graph} - the jVector graph of segment {@code n}, with dense local
 *       ordinals;</li>
 *   <li>{@code <name>.seg-<n>.l2g} - the local-to-global ordinal array of segment {@code n};</li>
 *   <li>{@code <name>.seg-<n>.pqv} - the PQ codes of segment {@code n}, by local ordinal, only for
 *       {@link ApproximateScoring#PQ_IN_MEMORY}.</li>
 * </ul>
 * The manifest is written to {@code <file>.tmp} and moved into place once complete. Segment files
 * are written in place, under a sequence no existing file carries, so a crash can only leave files
 * no manifest lists, which the next load deletes. The layout pattern also matches a {@code .tmp}
 * variant of a segment file, which this code does not write, so that such a stray file is deleted too.
 * <p>
 * The file system offers no durability without an explicit sync, and jVector's writers never sync,
 * so {@link #force(Path)} and {@link #syncDirectory(Path)} provide the ordering a crash must not be
 * able to break: segment files on disk before the manifest naming them, the manifest before the
 * files it no longer names are deleted.
 */
final class SegmentFiles
{
    ///////////////////////////////////////////////////////////////////////////
    // constants //
    //////////////

    private static final Logger LOG = LoggerFactory.getLogger(SegmentFiles.class);

    static final String MANIFEST_FILE_EXT = ".manifest";
    static final String GRAPH_FILE_EXT    = ".graph"   ;
    static final String L2G_FILE_EXT      = ".l2g"     ;
    static final String PQV_FILE_EXT      = ".pqv"     ;
    static final String TEMP_FILE_EXT     = ".tmp"     ;

    private static final String SEGMENT_INFIX = ".seg-";

    private static final int L2G_MAGIC   = 0x4C32_4731; // "L2G1"
    private static final int L2G_VERSION = 1;

    private static final int STREAM_BUFFER_SIZE = 1 << 16;



    ///////////////////////////////////////////////////////////////////////////
    // static methods //
    ///////////////////

    static String manifestFileName(final String name)
    {
        return name + MANIFEST_FILE_EXT;
    }

    static String graphFileName(
        final String name    ,
        final long   sequence
    )
    {
        return segmentPrefix(name, sequence) + GRAPH_FILE_EXT;
    }

    static String ordinalMapFileName(
        final String name    ,
        final long   sequence
    )
    {
        return segmentPrefix(name, sequence) + L2G_FILE_EXT;
    }

    static String pqVectorsFileName(
        final String name    ,
        final long   sequence
    )
    {
        return segmentPrefix(name, sequence) + PQV_FILE_EXT;
    }

    static Path tempPathOf(final Path path)
    {
        return path.resolveSibling(path.getFileName() + TEMP_FILE_EXT);
    }

    private static String segmentPrefix(
        final String name    ,
        final long   sequence
    )
    {
        if(sequence < 0)
        {
            throw new IllegalArgumentException("Negative segment sequence: " + sequence);
        }
        return name + SEGMENT_INFIX + sequence;
    }

    /**
     * Returns the pattern matching every file of the segment layout of index {@code name}, except the
     * manifest itself, with the segment sequence in group 1 when the file belongs to a segment.
     * <p>
     * Anchored and with the name quoted, so that it never matches a file of another index sharing
     * the directory: index {@code a.seg-1} has files {@code a.seg-1.manifest} and
     * {@code a.seg-1.seg-0.graph}, and neither matches the pattern of index {@code a}.
     *
     * @param name the index name
     * @return the pattern
     */
    static Pattern layoutFilePattern(final String name)
    {
        return Pattern.compile(
            "^" + Pattern.quote(name) + "(?:"
                + Pattern.quote(SEGMENT_INFIX) + "(\\d{1,18})"
                + "(?:" + Pattern.quote(GRAPH_FILE_EXT) + "|" + Pattern.quote(L2G_FILE_EXT)
                + "|" + Pattern.quote(PQV_FILE_EXT) + ")(?:" + Pattern.quote(TEMP_FILE_EXT) + ")?"
                + "|" + Pattern.quote(MANIFEST_FILE_EXT + TEMP_FILE_EXT)
                + ")$"
        );
    }

    /**
     * Returns the segment sequence a file name matched by {@link #layoutFilePattern(String)} carries.
     *
     * @param matcher a matcher that has matched
     * @return the sequence, or empty for the manifest's temp file
     */
    static OptionalLong sequenceOf(final Matcher matcher)
    {
        final String sequence = matcher.group(1);
        return sequence == null
            ? OptionalLong.empty()
            : OptionalLong.of(Long.parseLong(sequence))
        ;
    }

    /**
     * Returns the next free segment sequence in {@code directory}: one more than the highest any file
     * of the layout carries, so a new segment never reuses the name of a file a crash left behind.
     *
     * @param directory the index directory
     * @param name      the index name
     * @param floor     the lowest sequence to return, e.g. one more than every segment a manifest
     *                  lists
     * @return the next free sequence
     * @throws IOException if the directory cannot be listed
     */
    static long nextFreeSequence(
        final Path   directory,
        final String name     ,
        final long   floor
    ) throws IOException
    {
        final Pattern pattern = layoutFilePattern(name);
        long next = floor;
        if(!Files.isDirectory(directory))
        {
            return next;
        }
        try(final Stream<Path> files = Files.list(directory))
        {
            for(final Path file : (Iterable<Path>)files::iterator)
            {
                final Matcher matcher = pattern.matcher(file.getFileName().toString());
                if(matcher.matches())
                {
                    final OptionalLong sequence = sequenceOf(matcher);
                    if(sequence.isPresent())
                    {
                        next = Math.max(next, sequence.getAsLong() + 1);
                    }
                }
            }
        }
        return next;
    }

    /**
     * Forces the content of {@code file} to the storage device.
     *
     * @param file the file to sync
     * @throws IOException if the file cannot be opened or synced
     */
    static void force(final Path file) throws IOException
    {
        try(final FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE))
        {
            channel.force(true);
        }
    }

    /**
     * Forces the entries of {@code directory} to the storage device, so a move into it survives a
     * crash.
     * <p>
     * Not every platform can open a directory for syncing (Windows cannot), and there the move is as
     * durable as the file system makes it: a directory that cannot be opened is skipped, logged at
     * debug level only, since it would otherwise repeat on every persist. A sync that fails on a
     * directory that could be opened is a genuine I/O failure, though, and is thrown: the caller must
     * not delete the files of the previous generation on the strength of a move that may not survive.
     *
     * @param directory the directory to sync
     * @throws IOException if syncing the opened directory fails
     */
    static void syncDirectory(final Path directory) throws IOException
    {
        final FileChannel channel;
        try
        {
            channel = FileChannel.open(directory, StandardOpenOption.READ);
        }
        catch(final IOException | UnsupportedOperationException e)
        {
            LOG.debug("Cannot sync directory '{}' on this platform: {}", directory, e.toString());
            return;
        }
        try(channel)
        {
            channel.force(true);
        }
    }

    /**
     * Moves {@code source} onto {@code target}, atomically where the file system supports it,
     * replacing any existing target.
     * <p>
     * Falls back to a plain replace-existing move only when the file system cannot move atomically at
     * all ({@link AtomicMoveNotSupportedException}). A replacing move that is not atomic deletes the
     * target first, and a crash in between leaves no manifest, which loses the index on the next load,
     * so any other failure is thrown instead: a transient one fails this persist and leaves the previous
     * manifest in place. Windows needs no fallback either, since its atomic move replaces an existing
     * target, and fails only when the target is held open, which a plain move would fail on as well.
     *
     * @param source the complete file
     * @param target where it goes
     * @throws IOException if the move fails
     */
    static void atomicMove(
        final Path source,
        final Path target
    ) throws IOException
    {
        try
        {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch(final AtomicMoveNotSupportedException atomicFailure)
        {
            LOG.debug("Atomic move not supported for '{}', falling back to non-atomic replace", target);
            try
            {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            catch(final IOException nonAtomicFailure)
            {
                nonAtomicFailure.addSuppressed(atomicFailure);
                throw nonAtomicFailure;
            }
        }
    }

    /**
     * Appends the CRC32 of the whole file as a big-endian {@code long}, so {@link #verifyCrc32} can
     * check every byte before it.
     *
     * @param file the complete file
     * @throws IOException if reading or appending fails
     */
    static void appendCrc32(final Path file) throws IOException
    {
        final long crc = crc32Of(file, Files.size(file));
        try(final DataOutputStream out = new DataOutputStream(
            Files.newOutputStream(file, StandardOpenOption.APPEND)))
        {
            out.writeLong(crc);
        }
    }

    /**
     * Checks the CRC32 {@link #appendCrc32} closed {@code file} with.
     *
     * @param file the file
     * @throws IOException if the file is shorter than its CRC, unreadable, or does not match it
     */
    static void verifyCrc32(final Path file) throws IOException
    {
        final long payload = Files.size(file) - Long.BYTES;
        if(payload < 0)
        {
            throw new EOFException(file + " is too short to carry its CRC");
        }
        final long actual = crc32Of(file, payload);
        final long stored;
        try(final FileChannel channel = FileChannel.open(file, StandardOpenOption.READ))
        {
            final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            while(buffer.hasRemaining())
            {
                if(channel.read(buffer, payload + buffer.position()) < 0)
                {
                    throw new EOFException(file + " ended inside its CRC");
                }
            }
            stored = buffer.flip().getLong();
        }
        if(stored != actual)
        {
            throw new IOException(file + " fails its CRC32 check");
        }
    }

    private static long crc32Of(
        final Path file  ,
        final long length
    ) throws IOException
    {
        final CRC32  crc    = new CRC32();
        final byte[] buffer = new byte[STREAM_BUFFER_SIZE];
        try(final InputStream in = new BufferedInputStream(Files.newInputStream(file), STREAM_BUFFER_SIZE))
        {
            long remaining = length;
            while(remaining > 0)
            {
                final int read = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                if(read < 0)
                {
                    throw new EOFException(file + " ended before its CRC32 was complete");
                }
                crc.update(buffer, 0, read);
                remaining -= read;
            }
        }
        return crc.getValue();
    }

    /**
     * Deletes a file if present, logging (never throwing) on failure. For cleanup paths, where a
     * thrown error would mask the outcome they clean up after.
     *
     * @param path the file to delete
     * @return {@code true} if the file existed and was deleted
     */
    static boolean deleteQuietly(final Path path)
    {
        try
        {
            return Files.deleteIfExists(path);
        }
        catch(final IOException e)
        {
            LOG.warn("Failed to delete file '{}': {}", path, e.getMessage());
            return false;
        }
    }

    /**
     * Writes a segment's local-to-global ordinal array: magic, version, count, the ordinals, and a
     * CRC32 over all of it. Written in place under its final name; the caller forces it.
     *
     * @param path          the path to write to
     * @param localToGlobal the ordinals
     * @throws IOException if writing fails
     */
    static void writeOrdinalMap(
        final Path  path         ,
        final int[] localToGlobal
    ) throws IOException
    {
        final CRC32 crc = new CRC32();
        try(final DataOutputStream out = new DataOutputStream(new CheckedOutputStream(
            new BufferedOutputStream(Files.newOutputStream(path), STREAM_BUFFER_SIZE), crc)))
        {
            out.writeInt(L2G_MAGIC);
            out.writeInt(L2G_VERSION);
            out.writeInt(localToGlobal.length);
            for(final int global : localToGlobal)
            {
                out.writeInt(global);
            }
            // The checksum covers everything before it. The checked stream sits above the buffer, so
            // it has seen every byte written so far.
            out.writeLong(crc.getValue());
        }
    }

    /**
     * Reads a segment's local-to-global ordinal array written by {@link #writeOrdinalMap}.
     *
     * @param path          the file
     * @param expectedCount the node count the manifest records for the segment
     * @return the ordinals, strictly ascending
     * @throws IOException if the file is unreadable, of another format, corrupt, or does not hold
     *         {@code expectedCount} strictly ascending ordinals
     */
    static int[] readOrdinalMap(
        final Path path         ,
        final int  expectedCount
    ) throws IOException
    {
        final CRC32 crc = new CRC32();
        try(final DataInputStream in = new DataInputStream(new CheckedInputStream(
            new BufferedInputStream(Files.newInputStream(path), STREAM_BUFFER_SIZE), crc)))
        {
            if(in.readInt() != L2G_MAGIC || in.readInt() != L2G_VERSION)
            {
                throw new IOException("Not an ordinal map of this version: " + path);
            }
            final int count = in.readInt();
            if(count != expectedCount)
            {
                throw new IOException("Ordinal map " + path + " holds " + count + " ordinals, the manifest "
                    + expectedCount);
            }
            final int[] localToGlobal = new int[count];
            int previous = -1;
            for(int local = 0; local < count; local++)
            {
                final int global = in.readInt();
                if(global <= previous)
                {
                    throw new IOException("Ordinal map " + path + " is not strictly ascending at " + local);
                }
                localToGlobal[local] = global;
                previous = global;
            }
            final long computed = crc.getValue();
            final long stored;
            try
            {
                stored = in.readLong();
            }
            catch(final EOFException e)
            {
                throw new IOException("Ordinal map " + path + " is truncated", e);
            }
            if(stored != computed || in.read() != -1)
            {
                throw new IOException("Ordinal map " + path + " is corrupt");
            }
            return localToGlobal;
        }
    }



    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    private SegmentFiles()
    {
        throw new UnsupportedOperationException();
    }

}
