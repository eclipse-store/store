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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Hands out the sequence numbers of an index's segments and keeps track of the ones whose files must
 * not be deleted although no manifest lists them.
 * <p>
 * Owned by the index instance for its whole life, across {@code GigaMap.removeAll()}, so sequences
 * only ever grow within a run. That matters because a flush can still be writing when a
 * {@code removeAll()} starts the index over in the same directory: the flush notices and deletes the
 * files it wrote, and those must not be files the new generation has written under the same names.
 * <p>
 * Two kinds of sequence are protected from the cleanup of unlisted files: the ones a flush is writing
 * and has not committed yet, and the ones of segments still open, which a search may still be
 * traversing after a newer manifest dropped them. A cleanup deletes only through
 * {@link #deleteIfUnprotected(long, Path)}, which checks both under the lock a reservation takes.
 * <p>
 * Thread-safe.
 */
final class SegmentSequences
{
    ///////////////////////////////////////////////////////////////////////////
    // instance fields //
    ////////////////////

    // All guarded by this.
    private       long               next     ;
    private final Set<Long>          writing  = new HashSet<>();
    private final Map<Long, Integer> openings = new HashMap<>();
    // The sequences the manifest on disk lists; null until one was read or written.
    private       Set<Long>          committed;



    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

    /**
     * Reserves the next sequence for a segment about to be written, above every sequence handed out
     * before and above every sequence a file in the directory carries.
     *
     * @param directory the index directory
     * @param name      the index name
     * @return the reserved sequence, protected until {@link #written(long)}
     * @throws IOException if the directory cannot be listed
     */
    synchronized long reserve(
        final Path   directory,
        final String name
    ) throws IOException
    {
        final long sequence = SegmentFiles.nextFreeSequence(directory, name, this.next);
        this.next = sequence + 1;
        this.writing.add(sequence);
        return sequence;
    }

    /**
     * Ends the protection of a reserved sequence, once its segment is committed (then the manifest
     * protects it) or abandoned (then its files are gone or may go).
     *
     * @param sequence the sequence from {@link #reserve(Path, String)}
     */
    synchronized void written(final long sequence)
    {
        this.writing.remove(sequence);
    }

    /**
     * Raises the next sequence above one that exists, e.g. in a loaded manifest.
     *
     * @param sequence an existing sequence
     */
    synchronized void observe(final long sequence)
    {
        this.next = Math.max(this.next, sequence + 1);
    }

    /**
     * Records that a segment with this sequence was opened.
     *
     * @param sequence the segment's sequence
     */
    synchronized void opened(final long sequence)
    {
        this.observe(sequence);
        this.openings.merge(sequence, 1, Integer::sum);
    }

    /**
     * Records that a segment with this sequence was closed.
     *
     * @param sequence the segment's sequence
     * @return whether nothing protects the sequence any more: no segment of it is open and none is
     *         being written
     */
    synchronized boolean closed(final long sequence)
    {
        this.openings.computeIfPresent(sequence, (key, count) -> count > 1 ? count - 1 : null);
        return !this.openings.containsKey(sequence) && !this.writing.contains(sequence);
    }

    /**
     * Records the sequences the manifest on disk lists, after it was read or written. Kept here, for
     * the whole session, rather than by the disk manager, which removeAll replaces while segments of
     * the replaced one may still be open.
     *
     * @param sequences the listed sequences
     */
    synchronized void committed(final Set<Long> sequences)
    {
        this.committed = Set.copyOf(sequences);
    }

    /**
     * Returns whether the manifest on disk, as last recorded, does not list a sequence.
     *
     * @param sequence the sequence
     * @return {@code true} if a manifest was recorded and does not list it; {@code false} if it does
     *         or none was recorded yet
     */
    synchronized boolean isUnlisted(final long sequence)
    {
        return this.committed != null && !this.committed.contains(sequence);
    }

    /**
     * Returns whether the files of a sequence must survive a cleanup of unlisted files: it is being
     * written, or a segment of it is open.
     *
     * @param sequence the sequence
     * @return whether the sequence is protected
     */
    synchronized boolean isProtected(final long sequence)
    {
        return this.writing.contains(sequence) || this.openings.containsKey(sequence);
    }

    /**
     * Deletes a file of a segment unless its sequence is protected, deciding and deleting under the lock
     * {@link #reserve(Path, String)} takes. A cleanup lists the directory before it deletes, and a merge
     * reserves, writes and opens its segment without the flush permit, so a protection read before the
     * listing could miss a segment reserved after it.
     *
     * @param sequence the sequence the file carries
     * @param file     the file
     * @return whether the file was deleted
     */
    synchronized boolean deleteIfUnprotected(
        final long sequence,
        final Path file
    )
    {
        return !this.isProtected(sequence) && SegmentFiles.deleteQuietly(file);
    }

}
