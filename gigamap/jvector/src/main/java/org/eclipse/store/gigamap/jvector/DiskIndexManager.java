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
import io.github.jbellis.jvector.graph.disk.feature.Feature;
import io.github.jbellis.jvector.graph.disk.feature.FeatureId;
import io.github.jbellis.jvector.graph.disk.feature.FusedADC;
import io.github.jbellis.jvector.graph.disk.feature.InlineVectors;
import io.github.jbellis.jvector.quantization.PQVectors;
import io.github.jbellis.jvector.quantization.ProductQuantization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Manages on-disk storage for a VectorIndex.
 * <p>
 * This manager handles:
 * <ul>
 *   <li>Loading graph indices from disk</li>
 *   <li>Writing graph indices to disk with optional FusedADC compression</li>
 *   <li>Metadata file management for version and configuration verification</li>
 *   <li>Resource cleanup (file handles, memory-mapped buffers)</li>
 * </ul>
 */
interface DiskIndexManager extends Closeable
{
    /**
     * Graph file format version for compatibility checking.
     */
    final static int GRAPH_FILE_VERSION = 1;

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
     * @throws IOException if writing fails
     */
    public void writeIndex(
        OnHeapGraphIndex         index    ,
        RandomAccessVectorValues ravv     ,
        PQCompressionManager     pqManager
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
    }


    /**
     * Default implementation of DiskIndexManager.
     */
    public static class Default implements DiskIndexManager
    {
        private static final Logger LOG = LoggerFactory.getLogger(DiskIndexManager.class);

        private final IndexStateProvider provider      ;
        private final String             name          ;
        private final Path               indexDirectory;
        private final int                dimension     ;
        private final int                maxDegree     ;

        private OnDiskGraphIndex diskIndex     ;
        private ReaderSupplier   readerSupplier;
        private boolean          loaded        ;

        Default(
            final IndexStateProvider provider      ,
            final String             name          ,
            final Path               indexDirectory,
            final int                dimension     ,
            final int                maxDegree
        )
        {
            this.provider       = provider      ;
            this.name           = name          ;
            this.indexDirectory = indexDirectory;
            this.dimension      = dimension     ;
            this.maxDegree      = maxDegree     ;
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
                // FusedADC and InlineVectors features are embedded in the graph file
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

                return true;
            }
        }

        @Override
        public void writeIndex(
            final OnHeapGraphIndex         index    ,
            final RandomAccessVectorValues ravv     ,
            final PQCompressionManager     pqManager
        ) throws IOException
        {
            Files.createDirectories(this.indexDirectory);

            final Path graphPath = this.indexDirectory.resolve(this.name + GRAPH_FILE_EXT);
            final Path metaPath  = this.indexDirectory.resolve(this.name + META_FILE_EXT );

            // Write the graph with appropriate features
            // pqManager is only non-null when PQ compression is enabled
            if(pqManager != null && pqManager.isTrained() && pqManager.getPQ() != null)
            {
                this.writeIndexWithFusedADC(index, ravv, pqManager.getPQ(), graphPath);
            }
            else
            {
                // Use simple write for non-compressed indices
                OnDiskGraphIndex.write(index, ravv, graphPath);
            }

            // Write metadata
            this.writeMetadata(metaPath);

            LOG.info("Persisted index '{}' to disk with {} vectors", this.name, index.size(0));
        }

        /**
         * Writes the index using OnDiskGraphIndexWriter with FusedADC for compressed search.
         */
        private void writeIndexWithFusedADC(
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
            final FusedADC fusedADC = new FusedADC(this.maxDegree, pq);

            // Build writer with features using sequential renumbering (identity mapping)
            try(final OnDiskGraphIndexWriter writer = new OnDiskGraphIndexWriter.Builder(index, graphPath)
                .with(inlineVectors)
                .with(fusedADC)
                .build())
            {
                // Create feature suppliers that provide feature state for each node
                final Map<FeatureId, IntFunction<Feature.State>> suppliers = new EnumMap<>(FeatureId.class);

                suppliers.put(FeatureId.INLINE_VECTORS, nodeId ->
                    new InlineVectors.State(ravv.getVector(nodeId))
                );

                // Get a view for FusedADC state creation
                final var view = index.getView();
                suppliers.put(FeatureId.FUSED_ADC, nodeId ->
                    new FusedADC.State(view, pqVectors, nodeId)
                );

                // Write with sequential renumbering (maintains ordinals)
                writer.write(suppliers);

                // Close the view after writing
                view.close();
            }

            LOG.info("Wrote index '{}' with FusedADC compression ({} nodes)", this.name, index.size(0));
        }

        /**
         * Writes the metadata file.
         */
        private void writeMetadata(final Path metaPath) throws IOException
        {
            try(final DataOutputStream dos = new DataOutputStream(new FileOutputStream(metaPath.toFile())))
            {
                dos.writeInt(GRAPH_FILE_VERSION);
                dos.writeInt(this.dimension);
                dos.writeLong(this.provider.getExpectedVectorCount());
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
