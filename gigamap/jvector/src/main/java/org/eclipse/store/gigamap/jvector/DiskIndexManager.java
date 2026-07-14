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

import io.github.jbellis.jvector.disk.ReaderSupplier;
import io.github.jbellis.jvector.disk.ReaderSupplierFactory;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndexWriter;
import io.github.jbellis.jvector.graph.disk.OnDiskParallelGraphIndexWriter;
import io.github.jbellis.jvector.graph.disk.feature.Feature;
import io.github.jbellis.jvector.graph.disk.feature.FeatureId;
import io.github.jbellis.jvector.graph.disk.feature.FusedPQ;
import io.github.jbellis.jvector.graph.disk.feature.InlineVectors;
import io.github.jbellis.jvector.quantization.PQVectors;
import io.github.jbellis.jvector.quantization.ProductQuantization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
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
     * </ul>
     * Bumping this constant invalidates existing on-disk indices; they are
     * rebuilt from the GigaMap-stored source vectors on first load — no data
     * loss, but a one-time cold-start cost.
     */
    final static int GRAPH_FILE_VERSION = 3;

    /**
     * File extension for graph files.
     */
    final static String GRAPH_FILE_EXT = ".graph";

    /**
     * File extension for metadata files.
     */
    final static String META_FILE_EXT = ".meta";

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
     * Attempts to load the index from disk.
     *
     * @return true if successfully loaded, false otherwise
     */
    public boolean tryLoad();

    /**
     * Writes the in-memory index to disk.
     *
     * @param index     the in-memory graph index
     * @param ravv      random access vector values for writing vectors
     * @param pqManager the PQ compression manager (may be null if compression disabled)
     * @param metaState the {@code .meta} witness values captured together with {@code index}
     *                  under the {@code parentMap} monitor (see {@link MetaState})
     * @throws IOException if writing fails
     */
    public void writeIndex(
        OnHeapGraphIndex         index    ,
        RandomAccessVectorValues ravv     ,
        PQCompressionManager     pqManager,
        MetaState                metaState
    ) throws IOException;

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
        private final int                maxDegree           ;
        private final boolean            parallelOnDiskWrite ;

        private OnDiskGraphIndex diskIndex     ;
        private ReaderSupplier   readerSupplier;
        private boolean          loaded        ;

        Default(
            final IndexStateProvider provider            ,
            final String             name                ,
            final Path               indexDirectory      ,
            final int                dimension           ,
            final int                maxDegree           ,
            final boolean            parallelOnDiskWrite
        )
        {
            this.provider            = provider            ;
            this.name                = name                ;
            this.indexDirectory      = indexDirectory      ;
            this.dimension           = dimension           ;
            this.maxDegree           = maxDegree           ;
            this.parallelOnDiskWrite = parallelOnDiskWrite ;
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
        public boolean tryLoad()
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
                if(!this.verifyMetadata(metaPath))
                {
                    LOG.info("Disk index metadata mismatch for '{}', will rebuild", this.name);
                    return false;
                }

                // Load the on-disk graph index
                // FusedPQ and InlineVectors features are embedded in the graph file
                this.readerSupplier = ReaderSupplierFactory.open(graphPath);
                this.diskIndex = OnDiskGraphIndex.load(this.readerSupplier);

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
         * Verifies that the metadata file matches the current configuration.
         */
        private boolean verifyMetadata(final Path metaPath) throws IOException
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
                final long expectedCount = this.provider.getExpectedVectorCount();
                if(vectorCount != expectedCount)
                {
                    LOG.debug("Vector count mismatch: expected {}, got {}", expectedCount, vectorCount);
                    return false;
                }

                final long highestEntityId = dis.readLong();
                final long expectedHighestEntityId = this.provider.getHighestEntityId();
                if(highestEntityId != expectedHighestEntityId)
                {
                    LOG.debug("Highest entity id mismatch: expected {}, got {}", expectedHighestEntityId, highestEntityId);
                    return false;
                }

                final long structuralModCount = dis.readLong();
                final long expectedStructuralModCount = this.provider.getStructuralModCount();
                if(structuralModCount != expectedStructuralModCount)
                {
                    // The store advanced past the on-disk graph since it was written (e.g. a
                    // vec↔null transition committed via storeRoot() but not yet persistToDisk()).
                    // Reject the disk graph so it is rebuilt from the current source vectors.
                    LOG.debug("Structural mod count mismatch: expected {}, got {}", expectedStructuralModCount, structuralModCount);
                    return false;
                }

                return true;
            }
        }

        @Override
        public void writeIndex(
            final OnHeapGraphIndex         index    ,
            final RandomAccessVectorValues ravv     ,
            final PQCompressionManager     pqManager,
            final MetaState                metaState
        ) throws IOException
        {
            Files.createDirectories(this.indexDirectory);

            final Path graphPath = this.indexDirectory.resolve(this.name + GRAPH_FILE_EXT);
            final Path metaPath  = this.indexDirectory.resolve(this.name + META_FILE_EXT );

            // Write the graph with appropriate features
            // pqManager is only non-null when PQ compression is enabled
            if(pqManager != null && pqManager.isTrained() && pqManager.getPQ() != null)
            {
                this.writeIndexWithFusedPQ(index, ravv, pqManager.getPQ(), graphPath);
            }
            else
            {
                // Use simple write for non-compressed indices. Pass an identity ordinal map (not the
                // default sequentialRenumbering) so on-disk node ids stay equal to the graph ordinals
                // (= source entity ids) — see identityOrdinalMap.
                OnDiskGraphIndex.write(index, ravv, identityOrdinalMap(index), graphPath);
            }

            // Write metadata using the witnesses captured with the graph in Phase 1 (see MetaState):
            // NOT re-read live here, since Phase 2 runs with the parentMap monitor released.
            this.writeMetadata(metaPath, metaState);

            LOG.info("Persisted index '{}' to disk with {} vectors", this.name, index.size(0));
        }

        /**
         * Writes the index using OnDiskGraphIndexWriter with FusedPQ for compressed search.
         */
        private void writeIndexWithFusedPQ(
            final OnHeapGraphIndex         index    ,
            final RandomAccessVectorValues ravv     ,
            final ProductQuantization      pq       ,
            final Path                     graphPath
        ) throws IOException
        {
            // Create PQVectors for all vectors (encodeAll returns CompressedVectors, cast to PQVectors)
            final PQVectors pqVectors = (PQVectors) pq.encodeAll(ravv);

            // Create features for the on-disk index
            final InlineVectors inlineVectors = new InlineVectors(this.dimension);
            final FusedPQ fusedPQ = new FusedPQ(this.maxDegree, pq);

            // Create feature suppliers that provide feature state for each node
            final Map<FeatureId, IntFunction<Feature.State>> suppliers = new EnumMap<>(FeatureId.class);

            suppliers.put(FeatureId.INLINE_VECTORS, nodeId ->
                new InlineVectors.State(ravv.getVector(nodeId))
            );

            // Get a view for FusedPQ state creation
            final var view = index.getView();
            suppliers.put(FeatureId.FUSED_PQ, nodeId ->
                new FusedPQ.State(view, pqVectors, nodeId)
            );

            // Preserve graph ordinals on disk (identity map, not the default sequentialRenumbering)
            // so on-disk node ids stay equal to the source entity ids the integration keys on.
            final Map<Integer, Integer> ordinalMap = identityOrdinalMap(index);

            if(this.parallelOnDiskWrite)
            {
                try(final OnDiskParallelGraphIndexWriter writer = new OnDiskParallelGraphIndexWriter.Builder(index, graphPath)
                    .withParallelDirectBuffers(true)
                    .withMap(ordinalMap)
                    .with(inlineVectors)
                    .with(fusedPQ)
                    .build())
                {
                    writer.write(suppliers);
                }
            }
            else
            {
                try(final OnDiskGraphIndexWriter writer = new OnDiskGraphIndexWriter.Builder(index, graphPath)
                    .withMap(ordinalMap)
                    .with(inlineVectors)
                    .with(fusedPQ)
                    .build())
                {
                    writer.write(suppliers);
                }
            }

            // Close the view after writing
            view.close();

            LOG.info("Wrote index '{}' with FusedPQ compression ({} nodes, parallel={})",
                this.name, index.size(0), this.parallelOnDiskWrite);
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
            this.loaded = false;
        }

    }

}
