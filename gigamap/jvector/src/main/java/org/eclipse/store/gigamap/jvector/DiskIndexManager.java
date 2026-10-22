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

import io.github.jbellis.jvector.disk.RandomAccessReader;
import io.github.jbellis.jvector.disk.ReaderSupplier;
import io.github.jbellis.jvector.disk.ReaderSupplierFactory;
import io.github.jbellis.jvector.disk.SimpleWriter;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndexWriter;
import io.github.jbellis.jvector.graph.disk.OnDiskParallelGraphIndexWriter;
import io.github.jbellis.jvector.graph.disk.OrdinalMapper;
import io.github.jbellis.jvector.graph.disk.feature.Feature;
import io.github.jbellis.jvector.graph.disk.feature.FeatureId;
import io.github.jbellis.jvector.graph.disk.feature.FusedPQ;
import io.github.jbellis.jvector.graph.disk.feature.InlineVectors;
import io.github.jbellis.jvector.graph.disk.feature.NVQ;
import io.github.jbellis.jvector.quantization.NVQuantization;
import io.github.jbellis.jvector.quantization.PQVectors;
import io.github.jbellis.jvector.quantization.ProductQuantization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Manages the on-disk storage of a VectorIndex: a set of immutable segments committed through a
 * {@link SegmentManifest}.
 * <p>
 * This manager handles the files, and holds no segment itself:
 * <ul>
 *   <li>loading the segments a manifest lists, and deriving the owners from their deletion
 *       sets;</li>
 *   <li>writing a graph as a new segment with dense local ordinals, opening it, and committing a
 *       manifest that lists it;</li>
 *   <li>deleting files no manifest lists, left behind by a crash, a superseded commit or an abandoned
 *       flush, except those of segments still being written or still open.</li>
 * </ul>
 * The index owns the opened segments and closes them by reference count (see {@link IndexSource}).
 * See {@link SegmentFiles} for the file names.
 * <p>
 * Precondition: one process writes an index directory. There is no lock file, and a load deletes
 * every file of its layout that its manifest does not list, so a second process loading the same
 * directory would delete segment files the first has written but not committed yet.
 */
interface DiskIndexManager
{
    /**
     * On-disk format version, recorded in the manifest.
     * <p>
     * History:
     * <ul>
     *   <li>{@code 1} - version, dimension, vectorCount.</li>
     *   <li>{@code 2} - adds highestEntityId to detect count-collision corruption
     *       (equal numbers of additions and removals between persists).</li>
     *   <li>{@code 3} - adds structuralModCount, a persisted counter bumped on every
     *       graph-affecting mutation (including vec-null transitions, which leave count
     *       and highestEntityId unchanged). Catches the crash-restart window where the
     *       store advanced past the on-disk graph but the two proxies stayed equal.</li>
     *   <li>{@code 4} - adds a {@code boolean} recording whether
     *       {@link VectorIndexConfiguration#enablePqCompression()} was on when the graph was
     *       written.</li>
     *   <li>{@code 5} - replaces that {@code boolean} with the codes of
     *       {@link VectorIndexConfiguration#vectorStorage()} and
     *       {@link VectorIndexConfiguration#approximateScoring()} plus the effective
     *       {@link VectorIndexConfiguration#pqSubspaces()} and
     *       {@link VectorIndexConfiguration#nvqSubvectors()}.</li>
     *   <li>{@code 6} - replaces the single {@code <name>.graph} / {@code <name>.meta} /
     *       {@code <name>.pqv} file set by segments with dense local ordinals and a manifest that
     *       records the witnesses once and the format and deletions per segment.</li>
     *   <li>{@code 7} - the same layout. Bumped so that segments written before graph nodes left
     *       unreachable by parallel inserts or purges were reconnected are rebuilt
     *       once, with the repair, instead of keeping their unreachable nodes until a merge that
     *       may never come for the largest segment.</li>
     * </ul>
     * The codes are {@link VectorStorage#code()} and {@link ApproximateScoring#code()}, which are
     * deliberately not ordinals: an existing constant's code never changes and a new one is
     * appended, so inserting a constant cannot silently reinterpret an already written file. A code
     * this build does not know rejects the manifest, so a file written by a newer version is rebuilt
     * rather than misread.
     * <p>
     * Bumping this constant invalidates existing on-disk indices: {@code tryLoad} rejects a manifest
     * of another version, deleting the segments it lists, and the graph is rebuilt from the
     * vector file, so no data is lost. The step to 6 changed the file names as
     * well, so the files of version 5 and earlier are found by no manifest at all and are ignored:
     * the graph is built from the vector file, and the next persist writes the new layout.
     */
    static final int GRAPH_FILE_VERSION = 7;

    /**
     * Returns whether a manifest is present on disk, regardless of whether it is usable.
     * <p>
     * Distinguishes "nothing persisted yet" from "a persisted index was rejected", which a
     * {@code null} from {@link #load(MetaState, OwnerTable)} alone does not. Must be asked before the
     * load, which deletes what it cannot use.
     *
     * @return {@code true} if a persisted index is present
     */
    public boolean indexFilesExist();

    /**
     * Loads the index the manifest describes, validating the manifest's witnesses against
     * {@code expected}: an index the store has advanced past is rejected so it gets rebuilt from the
     * source vectors.
     * <p>
     * The segments take the slots {@code 0..n-1} of {@code owners}, in manifest order, and the owners
     * are derived from their deletion sets. The caller owns the returned segments, none of which is
     * retained yet; a rejected load leaves nothing open.
     * <p>
     * Deletes every file of the layout the manifest does not list, except the protected sequences
     * (see {@link SegmentSequences}). A manifest that is read but rejected for its witnesses, its
     * dimension or a segment that fails to open keeps the files it lists until a persist replaces
     * them; one rejected for its witnesses or its dimension is retired besides, so that no later load
     * accepts it (see {@link Default#retireRejectedManifest}). A manifest that cannot be read at all keeps
     * every file, since the failure may be transient. Only an invalid manifest (truncated, corrupt, of
     * another version) costs its segments, since nothing it names can be trusted any more.
     *
     * @param expected the witnesses the manifest is required to carry
     * @param owners   an empty owner table to take the slots from and derive the owners into
     * @return the loaded index, or {@code null} if there is none or it was rejected
     */
    public default LoadedIndex load(
        final MetaState  expected,
        final OwnerTable owners
    )
    {
        return this.load(expected, owners, true);
    }

    /**
     * {@link #load(MetaState, OwnerTable)}, retiring a manifest it rejects only if {@code retireRejected}. A
     * load within a session that compares against counters the session has advanced (the reload after a
     * {@code removeAll}) passes {@code false}: the files it rejects still match the stored record until the
     * next store commits, and the next session's load compares against that record and retires them if they
     * do not.
     *
     * @param expected       the witnesses the manifest is required to carry
     * @param owners         an empty owner table to take the slots from and derive the owners into
     * @param retireRejected whether a manifest rejected for its witnesses or its dimension is retired
     * @return the loaded index, or {@code null} if there is none or it was rejected
     */
    public LoadedIndex load(
        MetaState  expected,
        OwnerTable owners,
        boolean    retireRejected
    );

    /**
     * Returns whether the index the manifest describes loads against {@code expected}, closing what
     * it opened again. For tests and diagnostics.
     *
     * @param expected the witnesses the manifest is required to carry
     * @return whether it loads
     */
    public default boolean tryLoad(final MetaState expected)
    {
        final LoadedIndex loaded = this.load(expected, OwnerTable.New());
        if(loaded == null)
        {
            return false;
        }
        loaded.segments().forEach(DiskSegment::close);
        return true;
    }

    /**
     * Writes an in-memory graph as the files of a new segment and syncs them, without committing
     * it: no manifest lists the segment yet. A failure deletes what was written.
     *
     * @param graph    the graph to write, cleaned up and not empty
     * @param ravv     the vectors by global ordinal
     * @param pq       the PQ codebook to write the segment's codes with, or {@code null}
     * @param nvq      the NVQ quantizer to store the vectors with, or {@code null}
     * @param sequence the segment's sequence, reserved for it
     * @return the manifest entry of the segment, with no deletions
     * @throws IOException if writing fails
     */
    public SegmentManifest.Segment writeSegment(
        OnHeapGraphIndex         graph   ,
        RandomAccessVectorValues ravv    ,
        ProductQuantization      pq      ,
        NVQuantization           nvq     ,
        long                     sequence
    ) throws IOException;

    /**
     * Opens the files of a written segment.
     *
     * @param entry     the segment's manifest entry; its deletions become the segment's committed
     *                  ones
     * @param lease     the slot lease the segment holds, retained by this call
     * @param flagged   whether the segment no longer matches the index
     * @param logLength how many entries of the slot's leave log the entry's deletions cover
     * @return the segment, not retained yet
     * @throws IOException if a file cannot be read or does not match the entry
     */
    public DiskSegment openSegment(
        SegmentManifest.Segment entry    ,
        OwnerTable.Lease        lease    ,
        boolean                 flagged  ,
        int                     logLength
    ) throws IOException;

    /**
     * Commits a manifest listing {@code listed} with {@code witness}: temp file, sync, atomic move,
     * directory sync. The files of the listed segments must be written and synced. Calls the commit
     * stage hook at every {@link CommitStage}.
     *
     * @param witness the witnesses sampled together with the content the manifest describes
     * @param listed  the segments the index consists of, ascending by sequence
     * @throws IOException if the commit fails; then the previous manifest is still in place, unless
     *                     the failure came after the move
     */
    public void commit(
        MetaState                     witness,
        List<SegmentManifest.Segment> listed
    ) throws IOException;

    /**
     * Deletes the files of every segment in the directory that is not in {@code keep} and not
     * protected (see {@link SegmentSequences}), and the manifest's temp file. Never throws; a failed
     * deletion is logged and retried by the next cleanup.
     *
     * @param keep the sequences the committed manifest lists
     */
    public void reapUnlistedFiles(Set<Long> keep);

    /**
     * Deletes the files of one segment, written but never committed. Never throws.
     *
     * @param sequence the segment's sequence
     */
    public void deleteSegmentFiles(long sequence);

    /**
     * Removes the persisted manifest and every segment file that is not protected, if present.
     * <p>
     * Used when the index holds no vectors any more and therefore has nothing to persist: the files
     * left over from an earlier, non-empty state describe content that no longer exists.
     * Correctness does not depend on this removal - {@link #load(MetaState, OwnerTable)} rejects a
     * stale manifest through its witness comparison - it only keeps dead files from occupying disk
     * space. Opens and closes nothing: open segments keep their files until they are closed.
     * <p>
     * Never throws: a failed deletion is logged and leaves a harmless stale file behind, which the
     * next load deletes. This is called from the persist path, including on shutdown, where a cleanup
     * failure must not mask or replace the outcome of the persist itself.
     */
    public void deleteIndexFiles();

    /**
     * Returns the PQ codebook a segment's codes were encoded with, from its {@link FeatureId#FUSED_PQ}
     * feature or its {@code .pqv}, or {@code null} if it carries none.
     *
     * @param segment the segment
     * @return the codebook, or {@code null}
     */
    public static ProductQuantization codebookOf(final DiskSegment segment)
    {
        return Default.productQuantizationOf(segment.graph(), segment.pqVectors());
    }


    /**
     * Thrown by {@link #commit} for a failure after the new manifest has replaced the old one: the
     * commit took effect, only its durability is not proven, so the caller must treat the segments it
     * lists as committed.
     */
    public static final class CommitIncompleteException extends IOException
    {
        private static final long serialVersionUID = 1L;

        CommitIncompleteException(final Throwable cause)
        {
            super("The manifest was replaced, but the commit did not complete: " + cause, cause);
        }
    }


    /**
     * What a successful {@link #load(MetaState, OwnerTable)} hands over.
     *
     * @param segments the segments in slots {@code 0..n-1}, ascending by sequence, none retained
     * @param codebook the PQ codebook of the newest segment written for the configured format that
     *                 carries one, or {@code null}; segments encoded with another one, or with none
     *                 while this exists, are flagged
     * @param nvq      the NVQ quantizer of the newest unflagged segment that stores quantized
     *                 vectors, or {@code null}
     */
    public record LoadedIndex(
        List<DiskSegment>   segments,
        ProductQuantization codebook,
        NVQuantization      nvq
    )
    {
        /**
         * @return whether a segment is flagged for a rewrite
         */
        public boolean hasFlaggedSegments()
        {
            return this.segments.stream().anyMatch(DiskSegment::flaggedForMerge);
        }
    }


    /**
     * The points of the commit sequence in {@link #commit}, in order. A crash at any of them must
     * leave a loadable index: the old one before {@link #MANIFEST_MOVED}, the new one from it on.
     */
    public enum CommitStage
    {
        /** The new segments' files are written and synced, the manifest is untouched. */
        SEGMENTS_WRITTEN,

        /** The new manifest is written and synced to its temp file. */
        MANIFEST_TEMP_WRITTEN,

        /** The new manifest has replaced the old one, the directory is not synced yet. */
        MANIFEST_MOVED,

        /** The directory is synced; the files of segments no longer listed are still there. */
        DIRECTORY_SYNCED
    }


    /**
     * Provider interface for accessing runtime index state.
     */
    public interface IndexStateProvider
    {
        /**
         * Returns the expected vector count for metadata verification.
         *
         * @return the expected vector count
         */
        public long getExpectedVectorCount();

        /**
         * Returns the highest entity id currently allocated by the underlying
         * {@code GigaMap}. Combined with {@link #getExpectedVectorCount()},
         * this catches the count-collision corruption window where equal
         * numbers of additions and removals would otherwise leave the count
         * unchanged between persists.
         * <p>
         * GigaMap allocates entity ids monotonically, so any addition strictly
         * increases this value; removals do not decrement it. Together with
         * the count, this forms a cheap O(1) integrity check.
         *
         * @return the highest allocated entity id, or {@code -1} if no entities exist
         */
        public long getHighestEntityId();

        /**
         * Returns a monotonically increasing count of graph-affecting mutations
         * (add / remove / vec-null transition). Unlike {@link #getExpectedVectorCount()}
         * and {@link #getHighestEntityId()}, this value changes on a vec-null transition,
         * so comparing the store-recovered value against the one stamped into the manifest
         * detects the crash-restart window where a stale on-disk index would otherwise be
         * accepted (a nulled entity still returned, a new embedding missing).
         *
         * @return the persisted structural-change counter
         */
        public long getStructuralModCount();

        /**
         * Test-only: called by {@link Default#commit} at each {@link CommitStage}, read at every
         * stage so that a hook set after the manager was created still applies. A hook that throws
         * simulates a crash at that point.
         *
         * @return the hook, {@code null} in production
         */
        public default Consumer<CommitStage> commitStageTestHook()
        {
            return null;
        }

        /**
         * Test-only: run by a cleanup of unlisted files right before it lists the index directory.
         *
         * @return the hook, {@code null} in production
         */
        public default Runnable reapListingTestHook()
        {
            return null;
        }
    }


    /**
     * Immutable snapshot of the three witness values a manifest carries.
     * <p>
     * These describe the <em>written index</em>, so they must be sampled at the same instant the
     * in-memory graph is captured - inside {@code synchronized(parentMap)} in persist Phase 1 - and
     * then stamped verbatim into the manifest. Reading them live during Phase 2 (after the monitor is
     * released) would let a {@code vec-null} mutation landing mid-write advance
     * {@link IndexStateProvider#getStructuralModCount() structuralModCount} before the manifest is
     * written, stamping a post-mutation counter over a pre-mutation graph. A crash before the next
     * persist would then leave the store counter equal to the manifest counter, so the stale graph
     * would be wrongly accepted on restart (the nulled entity reappearing in search). Capturing all
     * three keeps the manifest internally consistent with the captured graph.
     */
    public static final class MetaState
    {
        final long expectedVectorCount;
        final long highestEntityId    ;
        final long structuralModCount ;

        public MetaState(
            final long expectedVectorCount,
            final long highestEntityId    ,
            final long structuralModCount
        )
        {
            this.expectedVectorCount = expectedVectorCount;
            this.highestEntityId     = highestEntityId    ;
            this.structuralModCount  = structuralModCount ;
        }

        /**
         * Returns whether all three witnesses equal those of {@code other}, i.e. whether
         * {@link DiskIndexManager#tryLoad(MetaState)} would accept a manifest stamped with
         * {@code other} when this state is expected.
         *
         * @param other the witnesses to compare with
         * @return {@code true} if all three witnesses are equal
         */
        public boolean matches(final MetaState other)
        {
            return this.expectedVectorCount == other.expectedVectorCount
                && this.highestEntityId     == other.highestEntityId
                && this.structuralModCount  == other.structuralModCount
            ;
        }

        /**
         * The witness of a manifest a load rejected and retired. It matches no index state: an empty map has a
         * {@code highestEntityId} of {@code -1}, but the vector count and the mod count are never negative, and
         * a match needs all three.
         */
        static final MetaState RETIRED = new MetaState(-1L, -1L, -1L);

        boolean isRetired()
        {
            return this.matches(RETIRED);
        }

        @Override
        public String toString()
        {
            return "MetaState[expectedVectorCount=" + this.expectedVectorCount + ", highestEntityId="
                + this.highestEntityId + ", structuralModCount=" + this.structuralModCount + "]";
        }
    }


    /**
     * Default implementation of DiskIndexManager.
     * <p>
     * Holds no mutable state of its own besides the shared {@link SegmentSequences}. Writing and
     * committing happen one flush at a time, under the index's flush permit.
     */
    public static class Default implements DiskIndexManager
    {
        private static final Logger LOG = LoggerFactory.getLogger(DiskIndexManager.class);

        /**
         * Marks the witness appended to a {@code .pqv} by {@code writePqVectors}, so a file without
         * one - truncated, or of another format - is told apart from one whose witness disagrees.
         */
        static final int PQV_WITNESS_MAGIC = 0x5051_5657;

        /** Size of that witness: the magic plus the segment sequence it carries. */
        static final int PQV_WITNESS_BYTES = Integer.BYTES + Long.BYTES;

        /**
         * Size of the CRC32 that closes a {@code .pqv}, over everything before it. Checked before
         * {@code PQVectors.load} sees the file, since that sizes its allocations from the counts in it.
         */
        static final int PQV_CRC_BYTES = Long.BYTES;

        private final IndexStateProvider provider           ;
        private final String             name               ;
        private final Path               indexDirectory     ;
        private final int                dimension          ;
        private final GraphFormat        format             ;
        private final boolean            parallelOnDiskWrite;
        private final SegmentSequences   sequences          ;

        Default(
            final IndexStateProvider provider           ,
            final String             name               ,
            final Path               indexDirectory     ,
            final int                dimension          ,
            final GraphFormat        format             ,
            final boolean            parallelOnDiskWrite
        )
        {
            this(provider, name, indexDirectory, dimension, format, parallelOnDiskWrite, new SegmentSequences());
        }

        Default(
            final IndexStateProvider provider           ,
            final String             name               ,
            final Path               indexDirectory     ,
            final int                dimension          ,
            final GraphFormat        format             ,
            final boolean            parallelOnDiskWrite,
            final SegmentSequences   sequences
        )
        {
            this.provider            = provider           ;
            this.name                = name               ;
            this.indexDirectory      = indexDirectory     ;
            this.dimension           = dimension          ;
            this.format              = format             ;
            this.parallelOnDiskWrite = parallelOnDiskWrite;
            this.sequences           = sequences          ;
        }

        @Override
        public boolean indexFilesExist()
        {
            return this.indexDirectory != null
                && Files.exists(this.manifestPath())
            ;
        }

        static ProductQuantization productQuantizationOf(
            final OnDiskGraphIndex graph    ,
            final PQVectors        pqVectors
        )
        {
            if(pqVectors != null)
            {
                // PQ_IN_MEMORY writes no fused feature, so the graph header carries no codebook. The
                // .pqv embeds its own, which PQVectors.load has already restored.
                return pqVectors.getCompressor();
            }
            return graph.getFeatures().get(FeatureId.FUSED_PQ) instanceof final FusedPQ fusedPQ
                ? fusedPQ.getPQ()
                : null
            ;
        }

        @Override
        public LoadedIndex load(
            final MetaState  expected,
            final OwnerTable owners,
            final boolean    retireRejected
        )
        {
            if(this.indexDirectory == null)
            {
                return null;
            }

            final Path manifestPath = this.manifestPath();
            if(!Files.exists(manifestPath))
            {
                LOG.debug("No disk index manifest found for '{}'", this.name);
                this.sequences.committed(Set.of());
                this.reapUnlistedFiles(Set.of());
                return null;
            }

            final SegmentManifest manifest;
            try
            {
                manifest = SegmentManifest.read(manifestPath);
            }
            catch(final SegmentManifest.InvalidManifestException e)
            {
                // Nothing the manifest names can be trusted without it, so its segments go too. The
                // manifest itself stays until a persist replaces it, so the index still counts as
                // present and that persist gets scheduled.
                LOG.info("Disk index manifest of '{}' is unusable, will rebuild: {}", this.name, e.getMessage());
                this.sequences.committed(Set.of());
                this.reapUnlistedFiles(Set.of());
                return null;
            }
            catch(final IOException e)
            {
                // The file could not be read at all, which may be transient (a sharing violation, a
                // scanner holding it): reject the load but keep every file, since the manifest may be
                // perfectly valid and is what decides which of them still matter.
                LOG.warn("Could not read the disk index manifest of '{}', will rebuild: {}", this.name, e.toString());
                return null;
            }

            final Set<Long> listed = new HashSet<>();
            manifest.segments().forEach(segment -> listed.add(segment.sequence()));
            this.sequences.committed(listed);
            this.reapUnlistedFiles(listed);

            if(manifest.witness().isRetired())
            {
                LOG.info("Disk index of '{}' was rejected by an earlier load and not replaced since, will rebuild",
                    this.name);
                return null;
            }
            if(manifest.dimension() != this.dimension)
            {
                LOG.info("Disk index of '{}' has dimension {}, configured {}, will rebuild",
                    this.name, manifest.dimension(), this.dimension);
                if(retireRejected)
                {
                    this.retireRejectedManifest(manifest);
                }
                return null;
            }
            if(!manifest.witness().matches(expected))
            {
                // The store advanced past the on-disk index since it was written (e.g. a vec-null
                // transition committed via storeRoot() but not yet persistToDisk()). Reject it so
                // it is rebuilt from the current source vectors.
                LOG.info("Disk index of '{}' is stale ({}, store {}), will rebuild",
                    this.name, manifest.witness(), expected);
                if(retireRejected)
                {
                    this.retireRejectedManifest(manifest);
                }
                return null;
            }

            final List<LoadedFiles> files    = new ArrayList<>(manifest.segments().size());
            final List<DiskSegment> opened   = new ArrayList<>(manifest.segments().size());
            boolean                 complete = false;
            try
            {
                final GraphFormat configured = SegmentManifest.effectiveFormat(this.format, this.dimension);
                for(final SegmentManifest.Segment segment : manifest.segments())
                {
                    files.add(this.openSegmentFiles(segment));
                }

                // The codebook every segment's codes have to be encoded with: the newest one among
                // the segments written for the configured format. A segment encoded with another
                // can still be searched, exactly, but its codes cannot be scored against the others.
                ProductQuantization codebook = null;
                for(int i = files.size() - 1; i >= 0 && codebook == null; i--)
                {
                    if(files.get(i).segment().format().equals(configured))
                    {
                        codebook = productQuantizationOf(files.get(i).graph(), files.get(i).pqVectors());
                    }
                }

                for(int slot = 0; slot < files.size(); slot++)
                {
                    final LoadedFiles         loaded          = files.get(slot);
                    final ProductQuantization pq              = productQuantizationOf(
                        loaded.graph(),
                        loaded.pqVectors()
                    );
                    final boolean             otherCodebook   = pq != null && !pq.equals(codebook);
                    // Written before the codebook was trained: it carries no codes the index could
                    // traverse, so it would be searched exactly for good unless it is rewritten.
                    final boolean             missingCodebook = pq == null && codebook != null
                        && configured.usesPq();
                    final boolean             mismatch        = otherCodebook || missingCodebook
                        || !loaded.segment().format().equals(configured);
                    if(mismatch)
                    {
                        LOG.info("Segment {} of '{}' was written for {} (configured {}){}, flagged for merge",
                            loaded.segment().sequence(), this.name, loaded.segment().format(), configured,
                            otherCodebook ? " with another codebook" : missingCodebook ? " without the codebook" : "");
                    }
                    opened.add(this.newSegment(loaded, owners.allocate(slot), mismatch, 0));
                }
                deriveOwners(manifest.segments(), opened, owners);

                NVQuantization nvq = null;
                for(int i = opened.size() - 1; i >= 0 && nvq == null; i--)
                {
                    if(!opened.get(i).flaggedForMerge()
                        && opened.get(i).graph().getFeatures().get(FeatureId.NVQ_VECTORS) instanceof final NVQ found)
                    {
                        nvq = found.getNVQuantization();
                    }
                }
                final ProductQuantization usable = opened.stream()
                    .filter(segment -> !segment.flaggedForMerge())
                    .anyMatch(segment -> productQuantizationOf(segment.graph(), segment.pqVectors()) != null)
                    ? codebook
                    : null
                ;

                complete = true;
                LOG.info("Loaded disk index '{}' with {} segments and {} live nodes",
                    this.name, opened.size(), countLive(manifest.segments()));
                return new LoadedIndex(List.copyOf(opened), usable, nvq);
            }
            catch(final IOException | RuntimeException e)
            {
                LOG.warn("Failed to load disk index for '{}'", this.name, e);
                return null;
            }
            finally
            {
                if(!complete)
                {
                    // Also on an Error (an AssertionError from jVector under -ea), which the catch
                    // does not take. The segments own the files they were created from; the rest are
                    // still loose.
                    opened.forEach(DiskSegment::close);
                    files.subList(opened.size(), files.size()).forEach(LoadedFiles::close);
                }
            }
        }

        private DiskSegment newSegment(
            final LoadedFiles      loaded   ,
            final OwnerTable.Lease lease    ,
            final boolean          flagged  ,
            final int              logLength
        )
        {
            final long sequence = loaded.segment().sequence();
            this.sequences.opened(sequence);
            try
            {
                return DiskSegment.New(
                    sequence,
                    lease,
                    loaded.segment().format(),
                    flagged,
                    loaded.localToGlobal(),
                    loaded.graph(),
                    loaded.readerSupplier(),
                    loaded.pqVectors(),
                    loaded.segment().deletedLocals(),
                    logLength,
                    () -> this.segmentClosed(sequence)
                );
            }
            catch(final RuntimeException e)
            {
                this.sequences.closed(sequence);
                lease.release();
                throw e;
            }
        }

        private static long countLive(final List<SegmentManifest.Segment> segments)
        {
            return segments.stream()
                .mapToLong(segment -> segment.nodeCount() - segment.deletedCount())
                .sum()
            ;
        }

        /**
         * Derives the owners from the deletion sets: each segment owns every entity of a local
         * ordinal it has not deleted. Segments are applied in ascending sequence, so if two claim the
         * same entity - which the commit protocol never produces - the newer one wins.
         */
        private void deriveOwners(
            final List<SegmentManifest.Segment> entries ,
            final List<DiskSegment>             segments,
            final OwnerTable                    owners
        )
        {
            long conflicts = 0;
            for(int i = 0; i < segments.size(); i++)
            {
                final int   slot          = segments.get(i).slot();
                final int[] localToGlobal = segments.get(i).localToGlobal();
                final int[] deleted       = entries.get(i).deletedLocals();
                int         nextDeleted   = 0;
                for(int local = 0; local < localToGlobal.length; local++)
                {
                    if(nextDeleted < deleted.length && deleted[nextDeleted] == local)
                    {
                        nextDeleted++;
                        continue;
                    }
                    // Straight into the owners, not through the table: nothing has left anything yet.
                    if(owners.owners().set(localToGlobal[local], slot) != SegmentOwners.NONE)
                    {
                        conflicts++;
                    }
                }
            }
            if(conflicts > 0)
            {
                LOG.warn("{} entities of '{}' are live in more than one segment, the newest copy is used",
                    conflicts, this.name);
            }
        }

        /**
         * The open files of one segment, before it is decided whether the segment is flagged.
         */
        /** A file of the layout a cleanup found unlisted, with the segment sequence it carries, if any. */
        private record UnlistedFile(Path path, OptionalLong sequence) {}

        private record LoadedFiles(
            SegmentManifest.Segment segment       ,
            OnDiskGraphIndex        graph         ,
            ReaderSupplier          readerSupplier,
            int[]                   localToGlobal ,
            PQVectors               pqVectors
        )
        {
            void close()
            {
                try
                {
                    this.graph.close();
                }
                catch(final IOException e)
                {
                    LOG.warn("Error closing graph of segment {}: {}", this.segment.sequence(), e.getMessage());
                }
                try
                {
                    this.readerSupplier.close();
                }
                catch(final IOException e)
                {
                    LOG.warn("Error closing reader of segment {}: {}", this.segment.sequence(), e.getMessage());
                }
            }
        }

        private LoadedFiles openSegmentFiles(final SegmentManifest.Segment segment) throws IOException
        {
            final long sequence = segment.sequence();
            final int[] localToGlobal = SegmentFiles.readOrdinalMap(
                this.indexDirectory.resolve(SegmentFiles.ordinalMapFileName(this.name, sequence)),
                segment.nodeCount()
            );

            PQVectors pqVectors = null;
            if(segment.hasPqVectors())
            {
                pqVectors = this.readPqVectors(
                    this.indexDirectory.resolve(SegmentFiles.pqVectorsFileName(this.name, sequence)),
                    segment
                );
            }

            final ReaderSupplier readerSupplier = ReaderSupplierFactory.open(
                this.indexDirectory.resolve(SegmentFiles.graphFileName(this.name, sequence))
            );
            try
            {
                final OnDiskGraphIndex graph;
                try
                {
                    graph = OnDiskGraphIndex.load(readerSupplier);
                }
                catch(final OutOfMemoryError | AssertionError e)
                {
                    // jVector reads the upper layers at open and sizes their arrays from the file, checked only by
                    // an assert: a damaged count is one impossible allocation, nothing is held. As an Error it would
                    // fail the storage's start, on every restart; rejected, the segment costs a graph rebuild.
                    throw new IOException("Segment " + sequence + " graph file is damaged", e);
                }
                if(graph.size(0) != segment.nodeCount())
                {
                    graph.close();
                    throw new IOException("Segment " + sequence + " graph holds " + graph.size(0)
                        + " nodes, the manifest records " + segment.nodeCount());
                }
                return new LoadedFiles(segment, graph, readerSupplier, localToGlobal, pqVectors);
            }
            catch(final IOException | RuntimeException e)
            {
                try
                {
                    readerSupplier.close();
                }
                catch(final IOException closeFailure)
                {
                    e.addSuppressed(closeFailure);
                }
                throw e;
            }
        }

        @Override
        public DiskSegment openSegment(
            final SegmentManifest.Segment entry    ,
            final OwnerTable.Lease        lease    ,
            final boolean                 flagged  ,
            final int                     logLength
        ) throws IOException
        {
            final LoadedFiles files = this.openSegmentFiles(entry);
            try
            {
                return this.newSegment(files, lease.retain(), flagged, logLength);
            }
            catch(final RuntimeException e)
            {
                files.close();
                throw e;
            }
        }

        /**
         * Deletes the files of a segment that its last holder closed if the manifest no longer lists
         * it. The commit that dropped it found it still open, held by a search, so the cleanup after
         * that commit kept its files; without this they would stay until the next flush or start.
         */
        private void segmentClosed(final long sequence)
        {
            if(this.sequences.closed(sequence) && this.sequences.isUnlisted(sequence))
            {
                this.deleteSegmentFiles(sequence);
            }
        }

        @Override
        public void reapUnlistedFiles(final Set<Long> keep)
        {
            this.deleteUnlistedFiles(keep);
        }

        /**
         * Deletes every file of the layout in the index directory that does not belong to a segment in
         * {@code listed} and is not protected: segments a crash left uncommitted or a commit superseded,
         * and the manifest's temp file. The manifest itself is never deleted here, and neither is any
         * file outside the layout, such as the files of another index sharing the directory or of the
         * single-file layout of earlier versions, which persisted data need not survive.
         * <p>
         * The protection of a segment file is decided when it is deleted, not before the listing: a merge
         * reserves, writes and opens its segment without the flush permit, possibly after the listing
         * began, and must find its files in place. The manifest's temp file is written only by a commit,
         * which holds the flush permit like every cleanup that runs while the index is live.
         * <p>
         * Keeps going after a failed delete, which is logged; the next load tries again.
         */
        private void deleteUnlistedFiles(final Set<Long> listed)
        {
            if(this.indexDirectory == null || !Files.isDirectory(this.indexDirectory))
            {
                return;
            }
            final Runnable hook = this.provider.reapListingTestHook();
            if(hook != null)
            {
                hook.run();
            }
            final Pattern            pattern  = SegmentFiles.layoutFilePattern(this.name);
            final List<UnlistedFile> unlisted = new ArrayList<>();
            try(final Stream<Path> files = Files.list(this.indexDirectory))
            {
                for(final Path file : (Iterable<Path>)files::iterator)
                {
                    final String  fileName = file.getFileName().toString();
                    final Matcher matcher  = pattern.matcher(fileName);
                    if(matcher.matches())
                    {
                        final OptionalLong sequence = SegmentFiles.sequenceOf(matcher);
                        if(sequence.isEmpty() || !listed.contains(sequence.getAsLong()))
                        {
                            unlisted.add(new UnlistedFile(file, sequence));
                        }
                    }
                }
            }
            catch(final IOException | RuntimeException e)
            {
                LOG.warn("Could not list index directory '{}' for leftover files: {}",
                    this.indexDirectory, e.getMessage());
                return;
            }
            int deleted = 0;
            for(final UnlistedFile file : unlisted)
            {
                final boolean gone = file.sequence().isEmpty()
                    ? SegmentFiles.deleteQuietly(file.path())
                    : this.sequences.deleteIfUnprotected(file.sequence().getAsLong(), file.path())
                ;
                if(gone)
                {
                    deleted++;
                }
            }
            if(deleted > 0)
            {
                LOG.info("Deleted {} leftover files of disk index '{}'", deleted, this.name);
            }
        }

        /**
         * Rewrites a manifest the load just rejected, durably, with the {@link MetaState#RETIRED} witness in place of
         * its own. A rejected manifest left as it was could be accepted by a later load whose index reached the same
         * three witness counters by another history (an index re-added with another vectorizer, removes or updates
         * that diverged after a crash), and its graph and deletion sets would then be served. The counters only grow
         * within a session, so a store can only meet a stale manifest's counters again in a later session, whose load
         * sees the mismatch first: retired at the first rejection, a manifest can never be accepted.
         * <p>
         * Everything else stays as it was: the manifest still lists its segments, so they are kept (and their
         * sequences never handed out again) until the persist that replaces the manifest supersedes them, and the
         * index still counts as having files to replace (see {@link #indexFilesExist()}), so the next session
         * schedules that persist. If the rewrite cannot be written, the rejected manifest stays as before.
         *
         * @param manifest the manifest the load rejected
         */
        private void retireRejectedManifest(final SegmentManifest manifest)
        {
            final Path manifestPath = this.manifestPath();
            final Path manifestTemp = SegmentFiles.tempPathOf(manifestPath);
            try
            {
                try
                {
                    new SegmentManifest(manifest.dimension(), MetaState.RETIRED, manifest.segments()).write(manifestTemp);
                    SegmentFiles.atomicMove(manifestTemp, manifestPath);
                }
                finally
                {
                    SegmentFiles.deleteQuietly(manifestTemp);
                }
            }
            catch(final IOException | RuntimeException e)
            {
                LOG.error("Could not retire the rejected disk index manifest of '{}'; it stays until the next persist"
                    + " replaces it, and a load before that could accept it", this.name, e);
                return;
            }
            try
            {
                SegmentFiles.syncDirectory(this.indexDirectory);
            }
            catch(final IOException | RuntimeException e)
            {
                LOG.warn("Retired the rejected disk index manifest of '{}', but could not sync its directory: a power"
                    + " loss before the next persist may bring the rejected manifest back", this.name, e);
            }
        }

        @Override
        public void deleteSegmentFiles(final long sequence)
        {
            final Path directory = this.indexDirectory;
            SegmentFiles.deleteQuietly(directory.resolve(SegmentFiles.graphFileName(this.name, sequence)));
            SegmentFiles.deleteQuietly(directory.resolve(SegmentFiles.ordinalMapFileName(this.name, sequence)));
            SegmentFiles.deleteQuietly(directory.resolve(SegmentFiles.pqVectorsFileName(this.name, sequence)));
        }

        private void ensureDirectory() throws IOException
        {
            if(!Files.isDirectory(this.indexDirectory))
            {
                Files.createDirectories(this.indexDirectory);
                // The directory's own entry is durable only once its parent is synced; without it a
                // power loss right after the first persist could lose the whole index directory.
                final Path parent = this.indexDirectory.toAbsolutePath().getParent();
                if(parent != null)
                {
                    SegmentFiles.syncDirectory(parent);
                }
            }
        }

        @Override
        public void commit(
            final MetaState                     witness,
            final List<SegmentManifest.Segment> listed
        ) throws IOException
        {
            this.ensureDirectory();
            this.reachedStage(CommitStage.SEGMENTS_WRITTEN);

            final SegmentManifest manifest     = new SegmentManifest(this.dimension, witness, listed);
            final Path            manifestPath = this.manifestPath();
            final Path            manifestTemp = SegmentFiles.tempPathOf(manifestPath);
            try
            {
                manifest.write(manifestTemp);
                this.reachedStage(CommitStage.MANIFEST_TEMP_WRITTEN);
                SegmentFiles.atomicMove(manifestTemp, manifestPath);
                final Set<Long> listedSequences = new HashSet<>();
                listed.forEach(segment -> listedSequences.add(segment.sequence()));
                this.sequences.committed(listedSequences);
            }
            finally
            {
                SegmentFiles.deleteQuietly(manifestTemp);
            }
            try
            {
                this.reachedStage(CommitStage.MANIFEST_MOVED);

                SegmentFiles.syncDirectory(this.indexDirectory);
                this.reachedStage(CommitStage.DIRECTORY_SYNCED);
            }
            catch(final IOException | RuntimeException e)
            {
                throw new CommitIncompleteException(e);
            }

            LOG.info("Persisted index '{}' to disk: {} segments, {} live nodes",
                this.name, listed.size(), countLive(listed));
        }

        private void reachedStage(final CommitStage stage)
        {
            final Consumer<CommitStage> hook = this.provider.commitStageTestHook();
            if(hook != null)
            {
                hook.accept(stage);
            }
        }

        @Override
        public SegmentManifest.Segment writeSegment(
            final OnHeapGraphIndex         graph   ,
            final RandomAccessVectorValues ravv    ,
            final ProductQuantization      pq      ,
            final NVQuantization           nvq     ,
            final long                     sequence
        ) throws IOException
        {
            this.ensureDirectory();
            final List<Path> written  = new ArrayList<>(3);
            boolean          complete = false;
            try
            {
                final SegmentManifest.Segment entry = this.writeSegmentFiles(graph, ravv, pq, nvq, sequence, written);
                complete = true;
                return entry;
            }
            finally
            {
                if(!complete)
                {
                    // Not referenced by any manifest, so nothing loses them. Removed here only to
                    // free the space early; the next cleanup would delete them as well.
                    written.forEach(SegmentFiles::deleteQuietly);
                }
            }
        }

        /**
         * Writes one segment's files and syncs them, adding each path to {@code written} before it is
         * created so the caller can remove what a failure leaves.
         */
        private SegmentManifest.Segment writeSegmentFiles(
            final OnHeapGraphIndex         index   ,
            final RandomAccessVectorValues ravv    ,
            final ProductQuantization      pq      ,
            final NVQuantization           nvq     ,
            final long                     sequence,
            final List<Path>               written
        ) throws IOException
        {
            final Path graphPath = this.indexDirectory.resolve(SegmentFiles.graphFileName(this.name, sequence));
            final Path l2gPath   = this.indexDirectory.resolve(SegmentFiles.ordinalMapFileName(this.name, sequence));
            final Path pqvPath   = this.indexDirectory.resolve(SegmentFiles.pqVectorsFileName(this.name, sequence));

            // The caller passes exactly the quantizers this write can use: captured once per flush, so
            // every segment of a flush is encoded with the same codebook even if training finishes
            // meanwhile. Without one, the graph is written without its feature; the manifest still
            // records the configured format, and the query path gates on the loaded graph's own
            // feature set rather than on the manifest.
            // Where the codebook goes depends on the scoring mode: fused into the graph, or into the
            // .pqv. Only one of these is ever non-null.
            final ProductQuantization fusedPq   = this.format.usesFusedPq()    ? pq : null;
            final ProductQuantization sidecarPq = this.format.usesInMemoryPq() ? pq : null;

            final DenseOrdinalMapper       mapper      = DenseOrdinalMapper.of(index);
            final int[]                    l2g         = mapper.localToGlobal();
            final RandomAccessVectorValues localValues = new SegmentVectorValues(ravv, l2g);

            // Encoded by local ordinal, so the codes take memory for the segment's nodes only, not
            // for every id below the highest one it holds.
            final PQVectors localCodes = pq != null && (fusedPq != null || sidecarPq != null)
                ? (PQVectors)pq.encodeAll(localValues)
                : null;

            written.add(l2gPath);
            SegmentFiles.writeOrdinalMap(l2gPath, l2g);
            SegmentFiles.force(l2gPath);

            if(sidecarPq != null)
            {
                written.add(pqvPath);
                this.writePqVectors(localCodes, pqvPath, sequence);
                SegmentFiles.force(pqvPath);
            }

            written.add(graphPath);
            if(fusedPq == null && nvq == null)
            {
                // Fast path for the plain, feature-less graph.
                writePlainIndex(index, ravv, mapper, graphPath);
            }
            else
            {
                this.writeIndexWithFeatures(
                    index, ravv, mapper, fusedPq != null ? localCodes : null, fusedPq, nvq, graphPath
                );
            }
            SegmentFiles.force(graphPath);

            // The files are in place, but their directory entries are durable only once the directory
            // is: before the manifest that names them can be.
            SegmentFiles.syncDirectory(this.indexDirectory);

            return new SegmentManifest.Segment(
                sequence,
                l2g.length,
                SegmentManifest.effectiveFormat(this.format, this.dimension),
                sidecarPq != null,
                new int[0]
            );
        }

        @Override
        public void deleteIndexFiles()
        {
            if(this.indexDirectory == null)
            {
                return;
            }

            // The manifest first: without it every other file is a leftover, so a crash in the middle
            // of this leaves nothing that could be mistaken for an index.
            final boolean manifestDeleted = SegmentFiles.deleteQuietly(this.manifestPath());
            if(Files.exists(this.manifestPath()))
            {
                // Its segments must stay while it does: a manifest naming deleted files would be found
                // again on the next load, and its sequences are not reserved by any file any more.
                LOG.warn("Could not remove the disk index manifest of '{}', keeping its segment files", this.name);
                return;
            }
            this.sequences.committed(Set.of());
            this.reapUnlistedFiles(Set.of());

            // Stays silent for the common case of an index that never wrote anything, so that an
            // empty index does not log on every persist.
            if(manifestDeleted)
            {
                LOG.info("Removed on-disk index '{}': it holds no vectors any more", this.name);
            }
        }

        private Path manifestPath()
        {
            return this.indexDirectory.resolve(SegmentFiles.manifestFileName(this.name));
        }

        /**
         * Writes a segment's PQ codes, by local ordinal, followed by a witness of the segment they
         * belong to.
         * <p>
         * Written through {@code PQVectors.write}, whose counterpart is {@code PQVectors.load}. Note
         * that {@code writeSidecarHeader} is <b>not</b> the method for this despite its name: it emits
         * the codebook and counts only, for a caller that streams the chunks itself.
         *
         * @param codes    the codes, by local ordinal
         * @param pqvPath  the path to write to
         * @param sequence the segment's sequence, for the witness
         * @throws IOException if writing fails
         */
        private void writePqVectors(
            final PQVectors codes   ,
            final Path      pqvPath ,
            final long      sequence
        ) throws IOException
        {
            try(final SimpleWriter writer = new SimpleWriter(pqvPath))
            {
                codes.write(writer, OnDiskGraphIndex.CURRENT_VERSION);

                // After jVector's payload so its format is untouched. The sequence makes a file that
                // was renamed or copied in from another segment recognisable rather than merely
                // plausible.
                writer.writeInt(PQV_WITNESS_MAGIC);
                writer.writeLong(sequence);
            }
            SegmentFiles.appendCrc32(pqvPath);

            LOG.info("Wrote PQ codes of '{}' segment {}: {} vectors, {} bytes per code, {} bytes resident when loaded",
                this.name, sequence, codes.count(), codes.getCompressedSize(), codes.ramBytesUsed());
        }

        /**
         * Reads a segment's PQ codes back into heap and checks they describe the segment.
         * <p>
         * Shape is checkable, content is not: a file of exactly the right dimensions holding codes for
         * different vectors passes here and degrades traversal silently, which is precisely what
         * {@code VectorIndexDiskTest.testPqInMemoryCodesActuallyDriveTraversal} relies on to prove the
         * codes are consulted at all.
         *
         * @param pqvPath the file
         * @param segment the manifest entry of the segment
         * @return the codes
         * @throws IOException if the file is unreadable or does not describe the segment
         */
        private PQVectors readPqVectors(
            final Path                    pqvPath,
            final SegmentManifest.Segment segment
        ) throws IOException
        {
            final long trailerStart = Files.size(pqvPath) - PQV_CRC_BYTES - PQV_WITNESS_BYTES;
            if(trailerStart < 0)
            {
                throw new IOException("PQ codes " + pqvPath + " are too short to carry their witness");
            }
            // Before jVector reads anything: PQVectors.load allocates by the counts in the file, so one
            // flipped bit there would be an OutOfMemoryError, which halts the JVM on every restart.
            SegmentFiles.verifyCrc32(pqvPath);

            try(final ReaderSupplier supplier = ReaderSupplierFactory.open(pqvPath);
                final RandomAccessReader reader = supplier.get())
            {
                reader.seek(trailerStart);
                if(reader.readInt() != PQV_WITNESS_MAGIC || reader.readLong() != segment.sequence())
                {
                    throw new IOException("PQ codes " + pqvPath + " do not belong to segment " + segment.sequence());
                }
                reader.seek(0);
                final PQVectors codes = PQVectors.load(reader);
                if(codes.getCompressedSize() != segment.format().pqSubspaces())
                {
                    throw new IOException("PQ codes " + pqvPath + " encode " + codes.getCompressedSize()
                        + " bytes per vector, the segment records " + segment.format().pqSubspaces());
                }
                if(codes.count() != segment.nodeCount())
                {
                    throw new IOException("PQ codes " + pqvPath + " cover " + codes.count()
                        + " vectors, the segment holds " + segment.nodeCount());
                }
                return codes;
            }
        }

        /**
         * Writes the plain, feature-less graph: full-precision inline vectors only.
         * <p>
         * This is the sequence of jvector's {@code OnDiskGraphIndex.write(graph, vectors, map, path)},
         * which produces the same bytes, taken apart only because that convenience method accepts just
         * a {@code Map} of ordinals and the mapper here avoids a boxed entry per node. Package-private
         * for the test that holds the two byte-identical.
         *
         * @param index     the in-memory graph to write
         * @param ravv      the vectors by global ordinal
         * @param mapper    the global-to-local mapping to write with
         * @param graphPath the path to write to
         * @throws IOException if writing fails
         */
        static void writePlainIndex(
            final OnHeapGraphIndex         index    ,
            final RandomAccessVectorValues ravv     ,
            final OrdinalMapper            mapper   ,
            final Path                     graphPath
        ) throws IOException
        {
            try(final OnDiskGraphIndexWriter writer = new OnDiskGraphIndexWriter.Builder(index, graphPath)
                .withMapper(mapper)
                .with(new InlineVectors(ravv.dimension()))
                .build())
            {
                writer.write(Feature.singleStateFactory(
                    FeatureId.INLINE_VECTORS,
                    nodeId -> new InlineVectors.State(ravv.getVector(nodeId))
                ));
            }
        }

        /**
         * Writes the graph through {@code OnDiskGraphIndexWriter}, carrying whichever features the
         * trained quantizers call for.
         * <p>
         * Exactly one vector-storage feature is always written - jVector's writer requires one and
         * derives the graph header's dimension from it - plus {@code FusedPQ} when a codebook exists.
         * jVector asks the feature suppliers for the global ordinal of each node and its neighbours.
         *
         * @param index      the in-memory graph to write
         * @param ravv       the vectors by global ordinal
         * @param mapper     the global-to-local mapping to write with
         * @param localCodes the PQ codes by local ordinal, or {@code null} to write no fused codes
         * @param pq         the codebook of {@code localCodes}, or {@code null}
         * @param nvq        the NVQ quantizer, or {@code null} to write full-precision inline vectors
         * @param graphPath  the path to write to
         * @throws IOException if writing fails
         */
        private void writeIndexWithFeatures(
            final OnHeapGraphIndex         index     ,
            final RandomAccessVectorValues ravv      ,
            final DenseOrdinalMapper       mapper    ,
            final PQVectors                localCodes,
            final ProductQuantization      pq        ,
            final NVQuantization           nvq       ,
            final Path                     graphPath
        ) throws IOException
        {
            final Map<FeatureId, IntFunction<Feature.State>> suppliers = new EnumMap<>(FeatureId.class);
            final List<Feature>                              features  = new ArrayList<>(2);

            if(nvq != null)
            {
                features.add(new NVQ(nvq));
                // Encoded per node rather than through nvq.encodeAll(ravv), which would materialise
                // nodes * (dimension + 32) bytes on the heap at once - gigabytes on exactly the data
                // sets this feature targets. encodeTo writes only into its destination and reads no
                // mutable state, so a per-node supplier is safe under the parallel writer too. The
                // FusedPQ path below cannot do the same, because a node's fused block needs its
                // NEIGHBOURS' codes and so must have them all encoded up front.
                suppliers.put(FeatureId.NVQ_VECTORS, nodeId ->
                    new NVQ.State(nvq.encode(ravv.getVector(nodeId)))
                );
            }
            else
            {
                features.add(new InlineVectors(this.dimension));
                suppliers.put(FeatureId.INLINE_VECTORS, nodeId ->
                    new InlineVectors.State(ravv.getVector(nodeId))
                );
            }

            // Get a view for FusedPQ state creation. Try-with-resources guarantees the view is closed
            // even if the writer throws (the view is heap-only, so a leak on the throwing path would
            // otherwise go unnoticed).
            try(final var view = index.getView())
            {
                if(localCodes != null)
                {
                    // Take the degree from the graph, not from the configured maxDegree: FusedPQ.load
                    // rebuilds the feature as new FusedPQ(header.layerInfo.get(0).degree, ...), i.e.
                    // the reader sizes its fused block from the graph header. Sourcing the writer's
                    // degree from the same place makes writer/reader agreement structural.
                    features.add(new FusedPQ(index.getDegree(0), pq));
                    suppliers.put(FeatureId.FUSED_PQ, nodeId ->
                        new FusedPQ.State(view, global -> localCodes.get(mapper.oldToNew(global)), nodeId)
                    );
                }

                if(this.parallelOnDiskWrite)
                {
                    final OnDiskParallelGraphIndexWriter.Builder builder =
                        new OnDiskParallelGraphIndexWriter.Builder(index, graphPath);
                    builder.withParallelDirectBuffers(true);
                    builder.withMapper(mapper);
                    features.forEach(builder::with);

                    try(final OnDiskParallelGraphIndexWriter writer = builder.build())
                    {
                        writer.write(suppliers);
                    }
                }
                else
                {
                    final OnDiskGraphIndexWriter.Builder builder =
                        new OnDiskGraphIndexWriter.Builder(index, graphPath);
                    builder.withMapper(mapper);
                    features.forEach(builder::with);

                    try(final OnDiskGraphIndexWriter writer = builder.build())
                    {
                        writer.write(suppliers);
                    }
                }
            }

            LOG.info("Wrote index '{}' with storage={} scoring={} ({} nodes, parallel={})",
                this.name,
                nvq != null ? "NVQ" : "INLINE",
                localCodes != null ? "FUSED_PQ" : "NONE",
                index.size(0),
                this.parallelOnDiskWrite
            );
        }

    }

}
