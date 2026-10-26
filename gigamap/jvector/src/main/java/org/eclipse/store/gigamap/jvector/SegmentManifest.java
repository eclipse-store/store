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


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * The commit point of an on-disk {@link VectorIndex}: which segments make up the index, what each of
 * them encodes, which of their nodes are deleted, and the {@link DiskIndexManager.MetaState} witness
 * that ties all of it to a state of the store.
 * <p>
 * Segment files are immutable once written and are only ever referenced through a manifest, so
 * replacing the manifest is what switches an index from one set of segments to the next. It is
 * written last, to a temp file that is synced and then moved into place, so a crash leaves either the
 * old manifest or the new one, and segment files that neither names are leftovers to delete.
 * <p>
 * Layout, all big-endian:
 * <pre>
 * int  magic, int version, int dimension
 * long expectedVectorCount, long highestEntityId, long structuralModCount
 * int  segmentCount
 * per segment:
 *   long sequence, int nodeCount
 *   int  storageCode, int scoringCode, int pqSubspaces, int nvqSubvectors
 *   byte hasPqVectors
 *   int  deletedCount, int[deletedCount] deletedLocals
 * long crc32 of everything before it
 * </pre>
 * Immutable.
 */
final class SegmentManifest
{
    ///////////////////////////////////////////////////////////////////////////
    // constants //
    //////////////

    private static final int MAGIC = 0x4356_4D46; // "CVMF"



    ///////////////////////////////////////////////////////////////////////////
    // instance fields //
    ////////////////////

    private final int                        dimension;
    private final DiskIndexManager.MetaState witness  ;
    private final List<Segment>              segments ;



    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    /**
     * @param dimension the vector dimension of the index
     * @param witness   the store state the segments together describe
     * @param segments  the segments, in strictly ascending sequence; at most
     *                  {@code SegmentOwners.MAX_SLOT + 1}
     * @throws IllegalArgumentException if the segments are out of order or too many
     */
    SegmentManifest(
        final int                        dimension,
        final DiskIndexManager.MetaState witness  ,
        final List<Segment>              segments
    )
    {
        this.dimension = dimension;
        this.witness   = Objects.requireNonNull(witness, "witness");
        this.segments  = List.copyOf(segments);
        if(this.segments.size() > SegmentOwners.MAX_SLOT + 1)
        {
            throw new IllegalArgumentException("Too many segments: " + this.segments.size()
                + ", at most " + (SegmentOwners.MAX_SLOT + 1));
        }
        long previous = -1;
        for(final Segment segment : this.segments)
        {
            if(segment.sequence() <= previous)
            {
                throw new IllegalArgumentException("Segment sequences must be strictly ascending, got "
                    + segment.sequence() + " after " + previous);
            }
            previous = segment.sequence();
        }
    }



    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

    int dimension()
    {
        return this.dimension;
    }

    DiskIndexManager.MetaState witness()
    {
        return this.witness;
    }

    /**
     * @return the segments, in ascending sequence; unmodifiable
     */
    List<Segment> segments()
    {
        return this.segments;
    }

    /**
     * Returns one more than the highest segment sequence, the lowest sequence a new segment may use.
     *
     * @return the next sequence, {@code 0} without segments
     */
    long nextSequence()
    {
        return this.segments.isEmpty()
            ? 0
            : this.segments.get(this.segments.size() - 1).sequence() + 1
        ;
    }

    /**
     * Returns {@code format} with its counts in effective form (see
     * {@link GraphFormat#effectivePqSubspaces(int)} and {@link GraphFormat#effectiveNvqSubvectors()}).
     * <p>
     * This is the form a manifest records, so that two configurations producing the same graph
     * compare equal: the automatic sentinel and the value it resolves to are the same setting, and a
     * count carries no meaning when nothing is quantized with it.
     *
     * @param format    the format
     * @param dimension the configured vector dimension
     * @return the effective format
     */
    static GraphFormat effectiveFormat(
        final GraphFormat format   ,
        final int         dimension
    )
    {
        return new GraphFormat(
            format.storage(),
            format.scoring(),
            format.effectivePqSubspaces(dimension),
            format.effectiveNvqSubvectors()
        );
    }

    /**
     * Writes this manifest to {@code path} and forces it to the storage device, replacing any file
     * there.
     *
     * @param path the (temporary) path to write to
     * @throws IOException if writing or syncing fails
     */
    void write(final Path path) throws IOException
    {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try(final DataOutputStream out = new DataOutputStream(bytes))
        {
            out.writeInt(MAGIC);
            out.writeInt(DiskIndexManager.GRAPH_FILE_VERSION);
            out.writeInt(this.dimension);
            out.writeLong(this.witness.expectedVectorCount);
            out.writeLong(this.witness.highestEntityId);
            out.writeLong(this.witness.structuralModCount);
            out.writeInt(this.segments.size());
            for(final Segment segment : this.segments)
            {
                segment.writeTo(out);
            }
            final CRC32 crc = new CRC32();
            crc.update(bytes.toByteArray());
            out.writeLong(crc.getValue());
        }

        try(final FileChannel channel = FileChannel.open(path,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
        {
            final ByteBuffer buffer = ByteBuffer.wrap(bytes.toByteArray());
            while(buffer.hasRemaining())
            {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    /**
     * Reads a manifest written by {@link #write(Path)}.
     *
     * @param path the manifest file
     * @return the manifest
     * @throws InvalidManifestException if the file is truncated, corrupt, of another version, or
     *         carries a value this build cannot interpret, such as a storage or scoring code a newer
     *         version wrote
     * @throws IOException if the file cannot be read at all, which may be transient
     */
    static SegmentManifest read(final Path path) throws IOException
    {
        final byte[] bytes = Files.readAllBytes(path);
        if(bytes.length < Long.BYTES)
        {
            throw new InvalidManifestException("Manifest " + path + " is truncated");
        }
        final int  payloadLength = bytes.length - Long.BYTES;
        final CRC32 crc = new CRC32();
        crc.update(bytes, 0, payloadLength);
        if(ByteBuffer.wrap(bytes, payloadLength, Long.BYTES).getLong() != crc.getValue())
        {
            throw new InvalidManifestException("Manifest " + path + " is corrupt or truncated");
        }

        try(final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, payloadLength)))
        {
            if(in.readInt() != MAGIC)
            {
                throw new InvalidManifestException("Not a segment manifest: " + path);
            }
            final int version = in.readInt();
            if(version != DiskIndexManager.GRAPH_FILE_VERSION)
            {
                throw new InvalidManifestException("Manifest " + path + " has version " + version
                    + ", this build reads " + DiskIndexManager.GRAPH_FILE_VERSION);
            }
            final int  dimension           = in.readInt();
            final long expectedVectorCount = in.readLong();
            final long highestEntityId     = in.readLong();
            final long structuralModCount  = in.readLong();
            final int  segmentCount        = in.readInt();
            if(segmentCount < 0 || segmentCount > SegmentOwners.MAX_SLOT + 1)
            {
                throw new InvalidManifestException("Manifest " + path + " lists " + segmentCount + " segments");
            }
            final List<Segment> segments = new ArrayList<>(segmentCount);
            for(int i = 0; i < segmentCount; i++)
            {
                segments.add(Segment.readFrom(in, path));
            }
            if(in.read() != -1)
            {
                throw new InvalidManifestException("Manifest " + path + " has trailing bytes");
            }
            return new SegmentManifest(
                dimension,
                new DiskIndexManager.MetaState(expectedVectorCount, highestEntityId, structuralModCount),
                segments
            );
        }
        catch(final EOFException e)
        {
            throw new InvalidManifestException("Manifest " + path + " is truncated", e);
        }
        catch(final IllegalArgumentException e)
        {
            throw new InvalidManifestException("Manifest " + path + " is invalid: " + e.getMessage(), e);
        }
    }



    ///////////////////////////////////////////////////////////////////////////
    // member types //
    /////////////////

    /**
     * One segment as the manifest records it.
     *
     * @param sequence      the number in the segment's file names, unique within the index
     * @param nodeCount     the number of nodes in the segment's graph, i.e. its local ordinals are
     *                      {@code 0..nodeCount-1}
     * @param format        the {@linkplain #effectiveFormat effective} format the segment was
     *                      written for. The configured one, not what the write achieved: a
     *                      configured quantizer whose training declined leaves its feature out of
     *                      the graph, and the search path gates on the graph's own feature set.
     * @param hasPqVectors  whether the segment has a {@code .pqv} file
     * @param deletedLocals the local ordinals whose entity this segment no longer holds the live copy
     *                      of, strictly ascending; copied on the way in and out
     */
    record Segment(
        long        sequence     ,
        int         nodeCount    ,
        GraphFormat format       ,
        boolean     hasPqVectors ,
        int[]       deletedLocals
    )
    {
        Segment
        {
            if(sequence < 0)
            {
                throw new IllegalArgumentException("Negative segment sequence: " + sequence);
            }
            if(nodeCount <= 0)
            {
                throw new IllegalArgumentException("A segment holds at least one node, got " + nodeCount);
            }
            Objects.requireNonNull(format, "format");
            deletedLocals = deletedLocals.clone();
            int previous = -1;
            for(final int local : deletedLocals)
            {
                if(local <= previous || local >= nodeCount)
                {
                    throw new IllegalArgumentException("Deleted local ordinals must be strictly ascending"
                        + " within 0.." + (nodeCount - 1) + ", got " + local + " after " + previous);
                }
                previous = local;
            }
        }

        /**
         * @return a copy of the deleted local ordinals
         */
        @Override
        public int[] deletedLocals()
        {
            return this.deletedLocals.clone();
        }

        /**
         * @return the number of deleted local ordinals, without copying them
         */
        int deletedCount()
        {
            return this.deletedLocals.length;
        }

        /**
         * Returns this segment with another deletion set.
         *
         * @param deletedLocals the new deleted local ordinals, strictly ascending
         * @return the segment
         */
        Segment withDeletedLocals(final int[] deletedLocals)
        {
            return new Segment(this.sequence, this.nodeCount, this.format, this.hasPqVectors, deletedLocals);
        }

        @Override
        public boolean equals(final Object other)
        {
            return other instanceof final Segment segment
                && this.sequence     == segment.sequence
                && this.nodeCount    == segment.nodeCount
                && this.hasPqVectors == segment.hasPqVectors
                && this.format.equals(segment.format)
                && Arrays.equals(this.deletedLocals, segment.deletedLocals)
            ;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.sequence, this.nodeCount, this.format, this.hasPqVectors)
                * 31 + Arrays.hashCode(this.deletedLocals);
        }

        @Override
        public String toString()
        {
            return "Segment[sequence=" + this.sequence + ", nodeCount=" + this.nodeCount + ", format="
                + this.format + ", hasPqVectors=" + this.hasPqVectors + ", deleted="
                + this.deletedLocals.length + "]";
        }

        private void writeTo(final DataOutputStream out) throws IOException
        {
            out.writeLong(this.sequence);
            out.writeInt(this.nodeCount);
            out.writeInt(this.format.storage().code());
            out.writeInt(this.format.scoring().code());
            out.writeInt(this.format.pqSubspaces());
            out.writeInt(this.format.nvqSubvectors());
            out.writeByte(this.hasPqVectors ? 1 : 0);
            out.writeInt(this.deletedLocals.length);
            for(final int local : this.deletedLocals)
            {
                out.writeInt(local);
            }
        }

        private static Segment readFrom(
            final DataInputStream in  ,
            final Path            path
        ) throws IOException
        {
            final long sequence  = in.readLong();
            final int  nodeCount = in.readInt();

            // fromCode returns null for a code this build does not know, which is what a file written
            // by a newer version carrying a constant this one lacks looks like. Rejecting it means
            // such a file is rebuilt, never misread as some other constant - the reason these are
            // explicit codes rather than ordinals.
            final int                storageCode = in.readInt();
            final int                scoringCode = in.readInt();
            final VectorStorage      storage     = VectorStorage.fromCode(storageCode);
            final ApproximateScoring scoring     = ApproximateScoring.fromCode(scoringCode);
            if(storage == null || scoring == null)
            {
                throw new InvalidManifestException("Manifest " + path + " records unknown format codes (storage="
                    + storageCode + ", scoring=" + scoringCode + ") for segment " + sequence);
            }
            final int     pqSubspaces   = in.readInt();
            final int     nvqSubvectors = in.readInt();
            final boolean hasPqVectors  = in.readByte() != 0;

            final int deletedCount = in.readInt();
            // Bounded by what the file still holds, too, so a count no file could back allocates nothing.
            if(deletedCount < 0 || deletedCount > nodeCount || deletedCount > in.available() / Integer.BYTES)
            {
                throw new InvalidManifestException("Manifest " + path + " records " + deletedCount
                    + " deletions for segment " + sequence + " of " + nodeCount + " nodes");
            }
            final int[] deletedLocals = new int[deletedCount];
            for(int i = 0; i < deletedCount; i++)
            {
                deletedLocals[i] = in.readInt();
            }

            return new Segment(
                sequence,
                nodeCount,
                new GraphFormat(storage, scoring, pqSubspaces, nvqSubvectors),
                hasPqVectors,
                deletedLocals
            );
        }
    }


    /**
     * Thrown when a manifest could be read but describes nothing this build can use: truncated,
     * corrupt, of another version, or carrying an unknown value. Distinct from a plain
     * {@link IOException}, which may be a transient failure to read the file at all and must not cost
     * the segments it names.
     */
    static final class InvalidManifestException extends IOException
    {
        InvalidManifestException(final String message)
        {
            super(message);
        }

        InvalidManifestException(
            final String    message,
            final Throwable cause
        )
        {
            super(message, cause);
        }
    }

}
