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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Manages on-disk storage for a VectorIndex.
 * <p>
 * This manager handles:
 * <ul>
 *   <li>Loading graph indices from disk</li>
 *   <li>Writing graph indices to disk with optional FusedPQ compression</li>
 *   <li>Metadata file management for version and configuration verification</li>
 *   <li>Resource cleanup (file handles, memory-mapped buffers)</li>
 * </ul>
 */
interface DiskIndexManager extends Closeable
{
    /**
     * Graph file format version for compatibility checking.
     * <p>
     * History:
     * <ul>
     *   <li>{@code 1} — version, dimension, vectorCount.</li>
     *   <li>{@code 2} — adds highestEntityId to detect count-collision corruption
     *       (equal numbers of additions and removals between persists).</li>
     *   <li>{@code 3} — adds structuralModCount, a persisted counter bumped on every
     *       graph-affecting mutation (including vec↔null transitions, which leave count
     *       and highestEntityId unchanged). Catches the crash-restart window where the
     *       store advanced past the on-disk graph but the two proxies stayed equal.</li>
     *   <li>{@code 4} — adds a {@code boolean} recording whether
     *       {@link VectorIndexConfiguration#enablePqCompression()} was on when the graph was
     *       written. That setting changes what the graph contains but appears nowhere else in
     *       the file, so without it flipping the flag over an existing directory went
     *       undetected and the old graph was reused. The bump also retires graphs written while
     *       the flag was inert, which were uncompressed despite being configured for PQ.</li>
     *   <li>{@code 5} - replaces that {@code boolean} with two {@code int} codes recording
     *       {@link VectorIndexConfiguration#vectorStorage()} and
     *       {@link VectorIndexConfiguration#approximateScoring()}. The boolean could express only
     *       one of the two dimensions the format now has, so it could not distinguish a graph
     *       holding quantized vectors from one holding full-precision ones. Two further
     *       {@code int}s record the effective {@link VectorIndexConfiguration#pqSubspaces()} and
     *       {@link VectorIndexConfiguration#nvqSubvectors()}, which shape the encoded blocks
     *       within a mode rather than selecting between modes.</li>
     * </ul>
     * The codes are {@link VectorStorage#code()} and {@link ApproximateScoring#code()}, which are
     * deliberately not ordinals: an existing constant's code never changes and a new one is
     * appended, so inserting a constant cannot silently reinterpret an already written file. A code
     * this build does not know resolves to {@code null} and is treated as a mismatch, so a file
     * written by a newer version is rejected and rebuilt rather than misread. That is also why
     * adding a future scoring or storage mode needs no further version bump.
     * Bumping this constant invalidates existing on-disk indices: {@code tryLoad} rejects the
     * {@code .meta} and the graph is rebuilt from the GigaMap-stored source vectors, so no data is
     * lost.
     * <p>
     * That rebuild is in memory. The stale files are replaced only by a persist that actually runs,
     * and none is triggered by the rejection itself: background persistence fires only once its
     * change counter reaches the configured minimum, the background shutdown persist requires that
     * counter to be non-zero, and the direct shutdown persist skips a clean incremental index. So an
     * index that is mutated after the upgrade migrates on its next persist and pays the cold start
     * once, while a purely read-only one keeps the old files and rebuilds again on every restart
     * until something calls {@code persistToDisk()}. The same applies to a PQ-enabled index picking
     * up compression for the first time.
     */
    final static int GRAPH_FILE_VERSION = 5;

    /**
     * File extension for graph files.
     */
    final static String GRAPH_FILE_EXT = ".graph";

    /**
     * File extension for metadata files.
     */
    final static String META_FILE_EXT = ".meta";

    /**
     * File extension for the sidecar holding the PQ codes of an
     * {@link ApproximateScoring#PQ_IN_MEMORY} index.
     * <p>
     * A third file rather than a graph feature, because the whole point of that mode is to keep the
     * codes out of the graph: fusing them duplicates each one {@code maxDegree} times, while this
     * holds one flat array that is read into heap on load.
     */
    final static String PQV_FILE_EXT = ".pqv";

    /**
     * Extension appended to a live file's name while it is being written. The graph and meta are
     * written to {@code <name><ext>.tmp} and then atomically renamed onto the live path, so an
     * interrupted write (e.g. {@code shutdownNow()} mid-persist) can never leave a torn live file.
     */
    final static String TEMP_FILE_EXT = ".tmp";

    /**
     * Returns whether a disk index is loaded and ready.
     *
     * @return true if loaded
     */
    public boolean isLoaded();

    /**
     * Returns the on-disk graph index, or null if not loaded.
     *
     * @return the disk index
     */
    public OnDiskGraphIndex getDiskIndex();

    /**
     * Returns the PQ codebook embedded in the currently loaded graph, or {@code null} if no index is
     * loaded or the loaded graph carries no {@link FeatureId#FUSED_PQ} feature.
     * <p>
     * The {@code .graph} file is self-describing: {@link FusedPQ} writes the codebook into its header
     * and restores it on load, so a reloaded index can recover the exact codebook its fused codes were
     * encoded with. That matters - re-training would produce a <i>different</i> codebook, which would
     * score the existing codes as noise.
     *
     * @return the embedded codebook, or {@code null} if the loaded graph is not PQ-compressed
     */
    public ProductQuantization loadedProductQuantization();

    /**
     * Returns the NVQ quantizer embedded in the currently loaded graph, or {@code null} if no index
     * is loaded or the loaded graph carries no {@link FeatureId#NVQ_VECTORS} feature.
     * <p>
     * As with the PQ codebook the {@code .graph} file is self-describing, but the reason for
     * recovering it differs. Search never needs it: the loaded {@code NVQ} feature scores through
     * its own quantizer. It is recovered so the index reports itself compressed immediately on load
     * rather than only after the next persist, and so that persist does not have to recompute a mean
     * it already has. Unlike PQ, a differently trained quantizer would be harmless here, because
     * every persist rewrites the whole graph together with the quantizer in its header.
     *
     * @return the embedded quantizer, or {@code null} if the loaded graph stores full-precision vectors
     */
    public NVQuantization loadedNVQuantization();

    /**
     * Returns the PQ codes loaded from the sidecar, or {@code null} unless this index is configured
     * for {@link ApproximateScoring#PQ_IN_MEMORY} and a sidecar was loaded.
     * <p>
     * Unlike the graph's own features these are heap-resident for as long as the index is open,
     * costing {@code pqSubspaces} bytes for every ordinal up to the highest in use. That is the
     * trade this mode makes: the graph stays small and the codes are paid for in memory instead.
     *
     * @return the loaded codes, or {@code null} if this index does not use them
     */
    public PQVectors loadedPqVectors();

    /**
     * Attempts to load the index from disk, validating the {@code .meta} witnesses against the
     * current live store state. This is the load-time self-heal check: a graph the store has
     * advanced past is rejected so it gets rebuilt from the source vectors.
     *
     * @return true if successfully loaded, false otherwise
     */
    public boolean tryLoad();

    /**
     * Returns whether a {@code .graph}/{@code .meta} pair is present on disk, regardless of
     * whether it is usable.
     * <p>
     * Distinguishes "nothing persisted yet" from "a persisted index was rejected", which a
     * {@code false} from {@link #tryLoad()} alone does not.
     *
     * @return {@code true} if both files exist
     */
    public boolean indexFilesExist();

    /**
     * Attempts to load the index from disk, validating the {@code .meta} witnesses against
     * {@code expected} instead of the live store state.
     * <p>
     * Used to reload an index this process has just written, where the graph on disk is known to
     * correspond to {@code expected} and the live store may legitimately have advanced past it in
     * the meantime. Comparing against the live state there would reject a perfectly usable file.
     *
     * @param expected the witnesses the {@code .meta} is required to carry
     * @return true if successfully loaded, false otherwise
     */
    public boolean tryLoad(MetaState expected);

    /**
     * Writes the in-memory index to disk.
     *
     * @param index     the in-memory graph index
     * @param ravv      random access vector values for writing vectors
     * @param pqManager  the PQ compression manager (may be null if approximate scoring is disabled)
     * @param nvqManager the NVQ compression manager (may be null if the graph stores full precision)
     * @param metaState  the {@code .meta} witness values captured together with {@code index}
     *                   under the {@code parentMap} monitor (see {@link MetaState})
     * @throws IOException if writing fails
     */
    public void writeIndex(
        OnHeapGraphIndex         index     ,
        RandomAccessVectorValues ravv      ,
        PQCompressionManager     pqManager ,
        NVQCompressionManager    nvqManager,
        MetaState                metaState
    ) throws IOException;

    /**
     * Removes the persisted {@code .graph} and {@code .meta} files, if present.
     * <p>
     * Used when the index holds no vectors any more and therefore has nothing to persist: the
     * files left over from an earlier, non-empty state describe content that no longer exists.
     * Correctness does not depend on this removal - {@link #tryLoad(MetaState)} rejects a stale
     * pair through its witness comparison - it only keeps dead files from occupying disk space.
     * <p>
     * Never throws: a failed deletion is logged and leaves a harmless stale file behind, which the
     * next successful write replaces. This is called from the persist path, including on shutdown,
     * where a cleanup failure must not mask or replace the outcome of the persist itself.
     */
    public void deleteIndexFiles();

    /**
     * Closes disk-related resources.
     */
    public void close();


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
         * (add / remove / vec↔null transition). Unlike {@link #getExpectedVectorCount()}
         * and {@link #getHighestEntityId()}, this value changes on a vec↔null transition,
         * so comparing the store-recovered value against the one stamped into the disk
         * {@code .meta} detects the crash-restart window where a stale on-disk graph would
         * otherwise be accepted (a nulled entity still returned, a new embedding missing).
         *
         * @return the persisted structural-change counter
         */
        public long getStructuralModCount();
    }


    /**
     * Immutable snapshot of the three {@code .meta} witness values.
     * <p>
     * These describe the <em>written graph</em>, so they must be sampled at the same instant the
     * in-memory graph is captured — inside {@code synchronized(parentMap)} in persist Phase 1 — and
     * then stamped verbatim into the {@code .meta}. Reading them live during Phase 2 (after the
     * monitor is released) would let a {@code vec↔null} mutation landing mid-write advance
     * {@link IndexStateProvider#getStructuralModCount() structuralModCount} before the metadata is
     * written, stamping a post-mutation counter over a pre-mutation graph. A crash before the next
     * persist would then leave the store counter equal to the {@code .meta} counter, so the stale
     * graph would be wrongly accepted on restart (the nulled entity reappearing in search).
     * Capturing all three keeps the {@code .meta} internally consistent with the captured graph.
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
    }


    /**
     * Default implementation of DiskIndexManager.
     */
    public static class Default implements DiskIndexManager
    {
        private static final Logger LOG = LoggerFactory.getLogger(DiskIndexManager.class);

        private final IndexStateProvider provider            ;
        private final String             name                ;
        private final Path               indexDirectory      ;
        private final int                dimension           ;
        private final GraphFormat        format              ;
        private final boolean            parallelOnDiskWrite ;

        private OnDiskGraphIndex diskIndex     ;
        private ReaderSupplier   readerSupplier;
        private PQVectors        pqVectors     ;
        private boolean          loaded        ;

        Default(
            final IndexStateProvider provider            ,
            final String             name                ,
            final Path               indexDirectory      ,
            final int                dimension           ,
            final GraphFormat        format              ,
            final boolean            parallelOnDiskWrite
        )
        {
            this.provider             = provider            ;
            this.name                 = name                ;
            this.indexDirectory       = indexDirectory      ;
            this.dimension            = dimension           ;
            this.format               = format              ;
            this.parallelOnDiskWrite  = parallelOnDiskWrite ;
        }

        @Override
        public boolean isLoaded()
        {
            return this.loaded;
        }

        @Override
        public OnDiskGraphIndex getDiskIndex()
        {
            return this.diskIndex;
        }

        @Override
        public boolean indexFilesExist()
        {
            return this.indexDirectory != null
                && Files.exists(this.indexDirectory.resolve(this.name + GRAPH_FILE_EXT))
                && Files.exists(this.indexDirectory.resolve(this.name + META_FILE_EXT))
            ;
        }

        @Override
        public ProductQuantization loadedProductQuantization()
        {
            if(this.diskIndex == null)
            {
                return null;
            }
            final PQVectors loadedPqVectors = this.pqVectors;
            if(loadedPqVectors != null)
            {
                // PQ_IN_MEMORY writes no fused feature, so the graph header carries no codebook. The
                // sidecar embeds its own, which PQVectors.load has already restored.
                return loadedPqVectors.getCompressor();
            }

            return this.diskIndex.getFeatures().get(FeatureId.FUSED_PQ) instanceof FusedPQ fusedPQ
                ? fusedPQ.getPQ()
                : null
            ;
        }

        @Override
        public NVQuantization loadedNVQuantization()
        {
            if(this.diskIndex == null)
            {
                return null;
            }
            return this.diskIndex.getFeatures().get(FeatureId.NVQ_VECTORS) instanceof NVQ nvqFeature
                ? nvqFeature.getNVQuantization()
                : null
            ;
        }

        @Override
        public PQVectors loadedPqVectors()
        {
            return this.pqVectors;
        }

        @Override
        public boolean tryLoad()
        {
            return this.tryLoad(new MetaState(
                this.provider.getExpectedVectorCount(),
                this.provider.getHighestEntityId()    ,
                this.provider.getStructuralModCount()
            ));
        }

        @Override
        public boolean tryLoad(final MetaState expected)
        {
            if(this.indexDirectory == null)
            {
                return false;
            }

            final Path graphPath = this.indexDirectory.resolve(this.name + GRAPH_FILE_EXT);
            final Path metaPath = this.indexDirectory.resolve(this.name + META_FILE_EXT);

            if(!Files.exists(graphPath) || !Files.exists(metaPath))
            {
                LOG.debug("Disk index files not found for '{}'", this.name);
                return false;
            }

            try
            {
                // Verify metadata matches current configuration
                if(!this.verifyMetadata(metaPath, expected))
                {
                    LOG.info("Disk index metadata mismatch for '{}', will rebuild", this.name);
                    return false;
                }

                // Load the on-disk graph index. The storage and fused-PQ features are embedded in
                // the graph file itself and come back with it.
                this.readerSupplier = ReaderSupplierFactory.open(graphPath);
                this.diskIndex = OnDiskGraphIndex.load(this.readerSupplier);

                // The in-memory PQ codes are not, so they are loaded separately. A sidecar that has
                // gone missing is a rejection rather than a degrade: the meta says this index
                // traverses on PQ codes, and silently serving it exactly instead would make the
                // configuration a lie. Returning false rebuilds from the store, which restores both.
                //
                // Absence is not always loss, though. PQ training needs MIN_VECTORS_FOR_PQ_TRAINING
                // embeddings, and below that the persist writes no codebook and so no sidecar, while
                // the meta still records the CONFIGURED scoring mode - as it must, since recording
                // what the write achieved instead would reject its own output and rebuild forever.
                // Treating that as loss would do the same by a different route: an index under the
                // threshold would rebuild on every restart for as long as it stayed small. So the
                // count witness, which is already verified above, decides which case this is.
                if(this.format.usesInMemoryPq())
                {
                    final Path pqvPath = this.indexDirectory.resolve(this.name + PQV_FILE_EXT);
                    if(!Files.exists(pqvPath))
                    {
                        if(expected.expectedVectorCount < PQCompressionManager.MIN_VECTORS_FOR_PQ_TRAINING)
                        {
                            // Too small to have trained: there are no codes to be missing. Traversal
                            // falls back to exact scoring, exactly as a FusedPQ graph whose training
                            // declined does, and the next persist past the threshold writes both.
                            LOG.debug("No PQ sidecar for '{}' and only {} vectors, below the {} needed"
                                + " to train - loading without compressed scoring",
                                this.name, expected.expectedVectorCount,
                                PQCompressionManager.MIN_VECTORS_FOR_PQ_TRAINING);
                        }
                        else
                        {
                            LOG.info("PQ sidecar missing for '{}', will rebuild", this.name);
                            this.close();
                            return false;
                        }
                    }
                    else
                    {
                        final PQVectors loadedCodes = this.readPqSidecar(pqvPath);
                        if(!this.sidecarFitsTheGraph(loadedCodes, expected))
                        {
                            this.close();
                            return false;
                        }
                        this.pqVectors = loadedCodes;
                        LOG.info("Loaded PQ sidecar for '{}': {} vectors, {} bytes resident",
                            this.name, this.pqVectors.count(), this.pqVectors.ramBytesUsed());
                    }
                }

                this.loaded = true;
                LOG.info("Loaded disk index '{}' with {} nodes", this.name, this.diskIndex.size(0));

                return true;
            }
            catch(final Exception e)
            {
                LOG.warn("Failed to load disk index for '{}': {}", this.name, e.getMessage());
                this.close();
                return false;
            }
        }

        /**
         * Verifies that the metadata file matches the current configuration and the expected
         * witness values.
         */
        private boolean verifyMetadata(final Path metaPath, final MetaState expected) throws IOException
        {
            try(final DataInputStream dis = new DataInputStream(new FileInputStream(metaPath.toFile())))
            {
                final int version = dis.readInt();
                if(version != GRAPH_FILE_VERSION)
                {
                    LOG.debug("Metadata version mismatch: expected {}, got {}", GRAPH_FILE_VERSION, version);
                    return false;
                }

                final int fileDimension = dis.readInt();
                if(fileDimension != this.dimension)
                {
                    LOG.debug("Dimension mismatch: expected {}, got {}", this.dimension, fileDimension);
                    return false;
                }

                final long vectorCount = dis.readLong();
                if(vectorCount != expected.expectedVectorCount)
                {
                    LOG.debug("Vector count mismatch: expected {}, got {}", expected.expectedVectorCount, vectorCount);
                    return false;
                }

                final long highestEntityId = dis.readLong();
                if(highestEntityId != expected.highestEntityId)
                {
                    LOG.debug("Highest entity id mismatch: expected {}, got {}", expected.highestEntityId, highestEntityId);
                    return false;
                }

                final long structuralModCount = dis.readLong();
                if(structuralModCount != expected.structuralModCount)
                {
                    // The store advanced past the on-disk graph since it was written (e.g. a
                    // vec↔null transition committed via storeRoot() but not yet persistToDisk()).
                    // Reject the disk graph so it is rebuilt from the current source vectors.
                    LOG.debug("Structural mod count mismatch: expected {}, got {}", expected.structuralModCount, structuralModCount);
                    return false;
                }

                // fromCode returns null for a code this build does not know, which is what a file
                // written by a newer version carrying a constant we lack looks like. Treating that
                // as a mismatch means a newer file is rejected and rebuilt, never misread as some
                // other constant - which is the whole reason these are explicit codes rather than
                // ordinals.
                final VectorStorage fileStorage = VectorStorage.fromCode(dis.readInt());
                if(fileStorage != this.format.storage())
                {
                    // The file's configuration is verified rather than assumed. Note this is not
                    // what protects the removeIndex()/add() path - a new index starts at
                    // structuralModCount 0 against a persisted >= 1, so that load is already refused
                    // above. What reaches here is a file whose witnesses do match but whose format
                    // does not, the concrete case being a downgrade: same index, same store, same
                    // counter, written by a build that knew a mode this one does not.
                    LOG.info("Vector storage setting changed for '{}' (file={}, configured={}), rebuilding",
                        this.name, fileStorage, this.format.storage());
                    return false;
                }

                final ApproximateScoring fileScoring = ApproximateScoring.fromCode(dis.readInt());
                if(fileScoring != this.format.scoring())
                {
                    LOG.info("Approximate scoring setting changed for '{}' (file={}, configured={}), rebuilding",
                        this.name, fileScoring, this.format.scoring());
                    return false;
                }

                final int filePqSubspaces = dis.readInt();
                final int pqSubspaces     = this.format.effectivePqSubspaces(this.dimension);
                if(filePqSubspaces != pqSubspaces)
                {
                    LOG.info("PQ subspace count changed for '{}' (file={}, configured={}), rebuilding",
                        this.name, filePqSubspaces, pqSubspaces);
                    return false;
                }

                final int fileNvqSubvectors = dis.readInt();
                final int nvqSubvectors     = this.format.effectiveNvqSubvectors();
                if(fileNvqSubvectors != nvqSubvectors)
                {
                    LOG.info("NVQ subvector count changed for '{}' (file={}, configured={}), rebuilding",
                        this.name, fileNvqSubvectors, nvqSubvectors);
                    return false;
                }

                return true;
            }
        }

        @Override
        public void writeIndex(
            final OnHeapGraphIndex         index     ,
            final RandomAccessVectorValues ravv      ,
            final PQCompressionManager     pqManager ,
            final NVQCompressionManager    nvqManager,
            final MetaState                metaState
        ) throws IOException
        {
            Files.createDirectories(this.indexDirectory);

            final Path graphPath    = this.indexDirectory.resolve(this.name + GRAPH_FILE_EXT);
            final Path metaPath      = this.indexDirectory.resolve(this.name + META_FILE_EXT );
            final Path graphTempPath = this.indexDirectory.resolve(this.name + GRAPH_FILE_EXT + TEMP_FILE_EXT);
            final Path metaTempPath  = this.indexDirectory.resolve(this.name + META_FILE_EXT  + TEMP_FILE_EXT);
            final Path pqvPath       = this.indexDirectory.resolve(this.name + PQV_FILE_EXT );
            final Path pqvTempPath   = this.indexDirectory.resolve(this.name + PQV_FILE_EXT  + TEMP_FILE_EXT);

            // Write both files to temp paths first, then atomically rename them into place. This way an
            // interrupted write (e.g. shutdownNow() mid-persist) leaves at most a stale temp file — never
            // a torn live graph or meta. The temp files are cleaned up in the finally block.
            try
            {
                // A manager is only non-null when its dimension is configured on, and only reports
                // trained once it actually holds a quantizer - so these two locals are exactly the
                // features this write can produce. A configured-but-untrained manager writes the
                // graph without its feature; the meta still records the configured mode, and the
                // query path gates on the loaded graph's own feature set rather than on the meta.
                final ProductQuantization pq = pqManager != null && pqManager.isTrained()
                    ? pqManager.getPQ()
                    : null;
                final NVQuantization nvq = nvqManager != null && nvqManager.isTrained()
                    ? nvqManager.getNVQ()
                    : null;

                // Where the codebook goes depends on the scoring mode: fused into the graph, or into
                // the sidecar. Only one of these is ever non-null.
                final ProductQuantization fusedPq    = this.format.usesFusedPq()    ? pq : null;
                final ProductQuantization sidecarPq  = this.format.usesInMemoryPq() ? pq : null;

                if(sidecarPq != null)
                {
                    this.writePqSidecar(ravv, sidecarPq, pqvTempPath);
                }

                if(fusedPq == null && nvq == null)
                {
                    // Fast path for the plain, feature-less graph. Kept separate so the overwhelmingly
                    // common configuration keeps producing byte-identical files to the ones written
                    // before the format became configurable. Pass an identity ordinal map (not the
                    // default sequentialRenumbering) so on-disk node ids stay equal to the graph
                    // ordinals (= source entity ids) — see identityOrdinalMap.
                    OnDiskGraphIndex.write(index, ravv, identityOrdinalMap(index), graphTempPath);
                }
                else
                {
                    this.writeIndexWithFeatures(index, ravv, fusedPq, nvq, graphTempPath);
                }

                // Write metadata using the witnesses captured with the graph in Phase 1 (see MetaState):
                // NOT re-read live here, since Phase 2 runs with the parentMap monitor released.
                this.writeMetadata(metaTempPath, metaState);

                // Commit: sidecar, then graph, then meta LAST. The meta is the validity stamp that
                // verifyMetadata reads first on load, so it must only become visible once everything
                // it describes is in place. A crash at any earlier point leaves a stale meta, which
                // verifyMetadata rejects and self-heals from the store.
                if(sidecarPq != null)
                {
                    this.atomicMove(pqvTempPath, pqvPath);
                }
                this.atomicMove(graphTempPath, graphPath);
                this.atomicMove(metaTempPath, metaPath);
            }
            finally
            {
                // Remove any temp file left behind by a failed or interrupted write. Swallow cleanup
                // errors so they cannot mask an in-flight write/move exception (which carries the real
                // root cause); a leftover temp file is harmless and overwritten on the next persist.
                this.deleteQuietly(graphTempPath);
                this.deleteQuietly(metaTempPath);
                this.deleteQuietly(pqvTempPath);
            }

            LOG.info("Persisted index '{}' to disk with {} vectors", this.name, index.size(0));
        }

        @Override
        public void deleteIndexFiles()
        {
            if(this.indexDirectory == null)
            {
                return;
            }

            // Release the reader before unlinking: on Windows an open mapping makes the delete fail.
            // A no-op unless a disk index is currently loaded.
            this.close();

            final boolean graphDeleted = this.deleteQuietly(this.indexDirectory.resolve(this.name + GRAPH_FILE_EXT));
            final boolean metaDeleted  = this.deleteQuietly(this.indexDirectory.resolve(this.name + META_FILE_EXT ));
            this.deleteQuietly(this.indexDirectory.resolve(this.name + PQV_FILE_EXT));

            // Stays silent for the common case of an index that never wrote anything, so that an
            // empty index does not log on every persist.
            if(graphDeleted || metaDeleted)
            {
                LOG.info("Removed on-disk index '{}': it holds no vectors any more", this.name);
            }
        }

        /**
         * Deletes a file if present, logging (never throwing) on failure. Used from the cleanup
         * {@code finally} of {@link #writeIndex}, where a thrown cleanup error would mask the real
         * write/move failure, and from {@link #deleteIndexFiles()}.
         *
         * @param path the file to delete
         * @return {@code true} if the file existed and was deleted
         */
        private boolean deleteQuietly(final Path path)
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
         * Moves {@code source} onto {@code target}, atomically where the file system supports it,
         * replacing any existing target.
         * <p>
         * Falls back to a plain replace-existing move when the atomic move is rejected. This is not
         * just {@link java.nio.file.AtomicMoveNotSupportedException}: Windows throws
         * {@link java.nio.file.AccessDeniedException} when asked to atomically replace an existing
         * target. The {@code source} is a fully-written
         * temp file, so the only cost of the non-atomic fallback is a narrow crash window in which the
         * live file is missing — which the load-time self-heal simply rebuilds from the store.
         */
        private void atomicMove(final Path source, final Path target) throws IOException
        {
            try
            {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch(final IOException atomicFailure)
            {
                LOG.debug("Atomic move rejected for '{}' ({}), falling back to non-atomic replace",
                    target, atomicFailure.getClass().getSimpleName());
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
         * Encodes every graph ordinal against the codebook and writes the result to the sidecar.
         * <p>
         * The encoding is identical to the one the fused path performs; only where it lands differs.
         * {@code encodeAll} sizes its output from {@code ravv.size()}, which for the ordinal-spaced
         * view is the highest used id plus one, so the resulting array is indexed by graph ordinal -
         * and therefore by source entity id - exactly as the fused codes are.
         * <p>
         * Written through {@code PQVectors.write}, whose counterpart is {@code PQVectors.load}. Note
         * that {@code writeSidecarHeader} is <b>not</b> the method for this despite its name: it
         * emits the codebook and counts only, for a caller that streams the chunks itself.
         *
         * @param ravv    the ordinal-spaced vectors, aligned with the graph's node ids
         * @param pq      the trained codebook
         * @param pqvPath the (temporary) path to write to
         * @throws IOException if writing fails
         */
        private void writePqSidecar(
            final RandomAccessVectorValues ravv   ,
            final ProductQuantization      pq     ,
            final Path                     pqvPath
        ) throws IOException
        {
            final PQVectors pqVectors = (PQVectors)pq.encodeAll(ravv);

            try(final SimpleWriter writer = new SimpleWriter(pqvPath))
            {
                pqVectors.write(writer, OnDiskGraphIndex.CURRENT_VERSION);
            }

            LOG.info("Wrote PQ sidecar for '{}': {} vectors, {} bytes per code, {} bytes resident when loaded",
                this.name, pqVectors.count(), pqVectors.getCompressedSize(), pqVectors.ramBytesUsed());
        }

        /**
         * Checks that a loaded sidecar describes the graph it was loaded beside.
         * <p>
         * The witnesses in the metadata cover the cases that arise from a torn or interrupted write,
         * since the metadata is committed last and a changed {@code pqSubspaces} is itself a
         * witness. What they do not cover is a sidecar that survived from somewhere else - copied
         * in, restored from a different backup generation, or truncated by something outside this
         * code - and reaching the score function with one is worse than rejecting it: a code array
         * shorter than the graph's ordinal space fails on a lookup rather than at load.
         * <p>
         * Note the limit of this. Shape is checkable, content is not: a sidecar of exactly the right
         * dimensions holding codes for different vectors passes here and degrades traversal
         * silently, which is precisely what
         * {@code VectorIndexDiskTest.testPqInMemoryCodesActuallyDriveTraversal} relies on to prove
         * the codes are consulted at all. Detecting that would need a checksum the format does not
         * carry.
         *
         * @param codes    the sidecar just read
         * @param expected the witnesses this load was verified against
         * @return whether the sidecar can be used
         */
        private boolean sidecarFitsTheGraph(final PQVectors codes, final MetaState expected)
        {
            final int configuredSubspaces = this.format.effectivePqSubspaces(this.dimension);
            if(codes.getCompressedSize() != configuredSubspaces)
            {
                LOG.info("PQ sidecar for '{}' encodes {} bytes per vector, configured for {},"
                    + " rebuilding", this.name, codes.getCompressedSize(), configuredSubspaces);
                return false;
            }

            // Ordinals are source entity ids, so the codes have to span up to the highest one in use
            // or a search can ask for a code that is not there.
            final long requiredOrdinals = expected.highestEntityId + 1;
            if(codes.count() < requiredOrdinals)
            {
                LOG.info("PQ sidecar for '{}' covers {} ordinals, graph needs {}, rebuilding",
                    this.name, codes.count(), requiredOrdinals);
                return false;
            }

            return true;
        }

        /**
         * Reads the PQ sidecar back into heap.
         *
         * @param pqvPath the sidecar path
         * @return the loaded codes
         * @throws IOException if the file is missing or unreadable
         */
        private PQVectors readPqSidecar(final Path pqvPath) throws IOException
        {
            try(final ReaderSupplier supplier = ReaderSupplierFactory.open(pqvPath);
                final RandomAccessReader reader = supplier.get())
            {
                return PQVectors.load(reader);
            }
        }

        /**
         * Writes the index through {@code OnDiskGraphIndexWriter}, carrying whichever features the
         * trained quantizers call for.
         * <p>
         * Exactly one vector-storage feature is always written - JVector's writer requires one and
         * derives the graph header's dimension from it - plus {@code FusedPQ} when a codebook exists.
         *
         * @param index     the in-memory graph to write
         * @param ravv      the ordinal-spaced vectors, aligned with the graph's node ids
         * @param pq        the PQ codebook, or {@code null} to write no fused codes
         * @param nvq       the NVQ quantizer, or {@code null} to write full-precision inline vectors
         * @param graphPath the (temporary) path to write to
         * @throws IOException if writing fails
         */
        private void writeIndexWithFeatures(
            final OnHeapGraphIndex         index    ,
            final RandomAccessVectorValues ravv     ,
            final ProductQuantization      pq       ,
            final NVQuantization           nvq      ,
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

            // encodeAll returns CompressedVectors; FusedPQ.State needs the PQVectors subtype.
            final PQVectors pqVectors = pq != null
                ? (PQVectors)pq.encodeAll(ravv)
                : null;

            // Get a view for FusedPQ state creation. Try-with-resources guarantees the view is
            // closed even if ordinal mapping or the writer throws (the view is heap-only, so a
            // leak on the throwing path would otherwise go unnoticed).
            try(final var view = index.getView())
            {
                if(pq != null)
                {
                    // Take the degree from the graph, not from the configured maxDegree: FusedPQ.load
                    // rebuilds the feature as new FusedPQ(header.layerInfo.get(0).degree, ...), i.e.
                    // the reader sizes its fused block from the graph header. Sourcing the writer's
                    // degree from the same place makes writer/reader agreement structural instead of
                    // relying on the configured maxDegree happening to match the degree the builder
                    // actually used.
                    features.add(new FusedPQ(index.getDegree(0), pq));
                    suppliers.put(FeatureId.FUSED_PQ, nodeId ->
                        new FusedPQ.State(view, pqVectors, nodeId)
                    );
                }

                // Preserve graph ordinals on disk (identity map, not the default sequentialRenumbering)
                // so on-disk node ids stay equal to the source entity ids the integration keys on.
                final Map<Integer, Integer> ordinalMap = identityOrdinalMap(index);

                if(this.parallelOnDiskWrite)
                {
                    final OnDiskParallelGraphIndexWriter.Builder builder =
                        new OnDiskParallelGraphIndexWriter.Builder(index, graphPath);
                    builder.withParallelDirectBuffers(true);
                    builder.withMap(ordinalMap);
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
                    builder.withMap(ordinalMap);
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
                pq != null ? "FUSED_PQ" : "NONE",
                index.size(0),
                this.parallelOnDiskWrite
            );
        }

        /**
         * Builds an identity old→new ordinal map over the graph's present nodes, so the on-disk write
         * PRESERVES graph ordinals (leaving {@code OMITTED} holes) instead of compacting them via the
         * default {@code sequentialRenumbering}.
         * <p>
         * The whole {@link VectorIndex} integration keys on the invariant "graph ordinal == source
         * entity id": search results are converted straight back to entity ids
         * ({@code convertSearchResult}), computed-mode scoring resolves vectors by source entity id
         * ({@code lookupComputedVector} / {@code computedIdIndex}), and incremental deletes track
         * ordinals ({@code diskDeletedOrdinals}). Compacting to a dense 0..n-1 range would renumber
         * disk nodes and scramble that mapping whenever the ordinal space has holes — i.e. after any
         * null embedding or deletion. Preserving ordinals costs a placeholder slot per hole on disk,
         * which is acceptable given entity ids are allocated densely.
         */
        private static Map<Integer, Integer> identityOrdinalMap(final OnHeapGraphIndex index)
        {
            final Map<Integer, Integer> map = new HashMap<>();
            final int idUpperBound = index.getIdUpperBound();
            for(int ordinal = 0; ordinal < idUpperBound; ordinal++)
            {
                if(index.containsNode(ordinal))
                {
                    map.put(ordinal, ordinal);
                }
            }
            return map;
        }

        /**
         * Writes the metadata file.
         */
        private void writeMetadata(final Path metaPath, final MetaState metaState) throws IOException
        {
            try(final DataOutputStream dos = new DataOutputStream(new FileOutputStream(metaPath.toFile())))
            {
                dos.writeInt(GRAPH_FILE_VERSION);
                dos.writeInt(this.dimension);
                dos.writeLong(metaState.expectedVectorCount);
                dos.writeLong(metaState.highestEntityId);
                dos.writeLong(metaState.structuralModCount);
                // The format settings are configuration rather than content witnesses, but they have
                // to be recorded: nothing else in the file reveals which features the graph was built
                // with, so a loader without them has to assume its own configuration describes the
                // file. See verifyMetadata for which case actually reaches that comparison - it is
                // not the removeIndex()/add() one, which the counter witness refuses first.
                //
                // The CONFIGURED values are written, not the ones the write actually achieved. If
                // training declines, the graph is written without the feature while the meta still
                // claims it, which the query path handles by gating on the loaded graph's own
                // feature set. Recording what was achieved instead would loop: config says NVQ, file
                // says INLINE, verify rejects, rebuild, training declines again, forever.
                dos.writeInt(this.format.storage().code());
                dos.writeInt(this.format.scoring().code());

                // The subspace and subvector counts shape the encoded blocks, and neither is
                // recoverable by comparing configuration alone: a quantizer is adopted from the
                // loaded graph rather than retrained, so a changed count would otherwise be
                // silently ignored - the old quantizer kept, and every later persist writing the
                // old shape. Recorded in effective form, with the sentinel resolved and a count
                // that this format does not encode with written as zero, so that two
                // configurations producing the same graph do not force a pointless rebuild.
                dos.writeInt(this.format.effectivePqSubspaces(this.dimension));
                dos.writeInt(this.format.effectiveNvqSubvectors());
            }
        }

        @Override
        public void close()
        {
            if(this.diskIndex != null)
            {
                try
                {
                    this.diskIndex.close();
                }
                catch(final IOException e)
                {
                    LOG.warn("Error closing disk index: {}", e.getMessage());
                }
                this.diskIndex = null;
            }
            if(this.readerSupplier != null)
            {
                try
                {
                    this.readerSupplier.close();
                }
                catch(final IOException e)
                {
                    LOG.warn("Error closing reader supplier: {}", e.getMessage());
                }
                this.readerSupplier = null;
            }
            // Heap, not a mapping, so there is nothing to close - but dropping the reference is what
            // releases it, and this mode's whole cost is that it holds one byte array per ordinal.
            this.pqVectors = null;
            this.loaded    = false;
        }

    }

}
