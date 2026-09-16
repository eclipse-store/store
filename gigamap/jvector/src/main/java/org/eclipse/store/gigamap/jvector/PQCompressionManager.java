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

import io.github.jbellis.jvector.quantization.ProductQuantization;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Owns the Product Quantization (PQ) codebook of a {@link VectorIndex}.
 * <p>
 * This manager is deliberately narrow: it trains a codebook, or adopts one recovered from an
 * on-disk graph, and hands it to {@link DiskIndexManager#writeIndex} so the graph can be written
 * with a {@code FusedPQ} feature. It takes no part in searching - a PQ-compressed graph is
 * self-describing, so the query path builds its score functions from the loaded graph's own view
 * rather than from any state held here.
 * <p>
 * The codebook is trained once, on the first persist that has enough vectors, and reused for the
 * lifetime of the on-disk index: subsequent sessions recover it from the {@code .graph} header.
 * Re-training on data drift is out of scope - a new codebook would not match the codes already
 * fused into the graph.
 */
interface PQCompressionManager
{
    /**
     * Minimum vectors needed for PQ training.
     */
    final static int MIN_VECTORS_FOR_PQ_TRAINING = 256;

    /**
     * Returns whether a codebook is available, whether trained here or adopted from disk.
     *
     * @return true if a codebook is held
     */
    public boolean isTrained();

    /**
     * Returns the codebook, or null if none is held.
     *
     * @return the PQ instance
     */
    public ProductQuantization getPQ();

    /**
     * Trains a codebook if none is held yet and sufficient vectors exist.
     * Does nothing if already trained or if there are fewer than
     * {@link #MIN_VECTORS_FOR_PQ_TRAINING} vectors.
     */
    public void trainIfNeeded();

    /**
     * Adopts the codebook recovered from a loaded on-disk graph.
     * <p>
     * A {@code null} argument means the loaded graph carries no {@code FusedPQ} feature and resets
     * this manager to untrained, so the next {@link #trainIfNeeded()} actually trains instead of
     * short-circuiting forever on a codebook it never had.
     *
     * @param pq the codebook embedded in the loaded graph, or {@code null} if it carries none
     */
    public void adoptTrainedPQ(ProductQuantization pq);

    /**
     * Resets PQ state (clears the codebook).
     */
    public void reset();


    /**
     * Provider interface for accessing vectors needed for PQ training.
     */
    public static interface VectorProvider
    {
        /**
         * Returns the current vector count.
         *
         * @return the vector count
         */
        public long getVectorCount();

        /**
         * Collects training vectors for PQ training.
         *
         * @return list of vectors for training
         */
        public List<VectorFloat<?>> collectTrainingVectors();
    }


    /**
     * Default implementation of PQCompressionManager.
     */
    public static class Default implements PQCompressionManager
    {
        private static final Logger LOG = LoggerFactory.getLogger(PQCompressionManager.class);

        private final VectorProvider provider   ;
        private final String         name       ;
        private final int            dimension  ;
        private final int            pqSubspaces;

        // Written by the persist path (parentMap monitor + builderLock write lock) and by the load
        // path (write lock). Volatile rather than synchronized: it publishes the codebook safely
        // without introducing a second lock into an already lock-dense call chain.
        private volatile ProductQuantization pq       ;
        private volatile boolean             pqTrained;

        Default(
            final VectorProvider provider   ,
            final String         name       ,
            final int            dimension  ,
            final int            pqSubspaces
        )
        {
            this.provider    = provider   ;
            this.name        = name       ;
            this.dimension   = dimension  ;
            this.pqSubspaces = pqSubspaces;
        }

        @Override
        public boolean isTrained()
        {
            return this.pqTrained;
        }

        @Override
        public ProductQuantization getPQ()
        {
            return this.pq;
        }

        @Override
        public void trainIfNeeded()
        {
            if(this.pqTrained)
            {
                return; // Already trained
            }

            final long vectorCount = this.provider.getVectorCount();
            if(vectorCount < MIN_VECTORS_FOR_PQ_TRAINING)
            {
                LOG.debug("Not enough vectors for PQ training ({} < {})", vectorCount, MIN_VECTORS_FOR_PQ_TRAINING);
                return;
            }

            this.trainPQ();
        }

        /**
         * Trains the PQ codebook from current vectors.
         */
        private void trainPQ()
        {
            LOG.info("Training PQ for index '{}'...", this.name);

            // The training set is DENSE (iteration order over the real vectors), not the graph
            // ordinal space, and that is deliberate: the ordinal-spaced RAVV used for the disk write
            // is wrapped in NullSafeVectorValues, so every deletion hole and null embedding would
            // feed k-means a 1e-6 placeholder and drag the centroids toward the origin. Ordinal
            // alignment matters only when encoding, which DiskIndexManager.writeIndexWithFusedPQ
            // does separately against the ordinal-spaced RAVV.
            final List<VectorFloat<?>> trainingVectors = this.provider.collectTrainingVectors();

            if(trainingVectors.isEmpty())
            {
                LOG.warn("No vectors available for PQ training");
                return;
            }

            // Determine number of subspaces
            final int subspaces = this.pqSubspaces > 0
                ? this.pqSubspaces
                : Math.max(1, this.dimension / 4);

            final ListRandomAccessVectorValues ravv =
                new ListRandomAccessVectorValues(trainingVectors, this.dimension);

            // Train PQ - 256 centroids per subspace, which is also the only cluster count FusedPQ
            // accepts; center for better accuracy on low dimensions.
            final ProductQuantization trained = ProductQuantization.compute(
                ravv,
                subspaces,
                256,   // centroids per subspace (2^8 = 256 is standard)
                this.dimension < 64 // use global centroid for low dimensions
            );

            // Publish the codebook before the flag: isTrained() must never report true without a
            // codebook, since that is exactly the state in which DiskIndexManager.writeIndex falls
            // back to an uncompressed write while the index reports itself as compressed.
            this.pq        = trained;
            this.pqTrained = true   ;

            LOG.info("PQ training complete for '{}': {} subspaces, {} training vectors",
                this.name, subspaces, trainingVectors.size());
        }

        @Override
        public void adoptTrainedPQ(final ProductQuantization pq)
        {
            this.pq        = pq        ;
            this.pqTrained = pq != null;
        }

        @Override
        public void reset()
        {
            this.pq        = null ;
            this.pqTrained = false;
        }

    }

}
