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

import io.github.jbellis.jvector.quantization.NVQuantization;
import io.github.jbellis.jvector.vector.VectorUtil;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Owns the Non-uniform Vector Quantization (NVQ) quantizer of a {@link VectorIndex}.
 * <p>
 * The counterpart to {@link PQCompressionManager} on the storage dimension: where that one owns a
 * codebook used to <i>score</i> candidates during traversal, this one owns the quantizer used to
 * <i>store</i> each vector in the graph. It trains a quantizer, or adopts one recovered from an
 * on-disk graph, and hands it to {@link DiskIndexManager#writeIndex} so the graph can be written
 * with an {@code NVQ} feature in place of full-precision inline vectors.
 * <p>
 * It takes no part in searching. An NVQ graph is self-describing - the quantizer lives in its
 * header - so the query path scores through the loaded graph's own view rather than through any
 * state held here.
 *
 * <h2>Why this is not simply PQCompressionManager with a different quantizer</h2>
 * Three things differ, and each of them removes machinery rather than adding it:
 * <ul>
 *   <li><b>Training cannot decline.</b> PQ needs at least 256 vectors because it runs k-means for
 *       256 centroids per subspace. NVQ needs a mean, which one vector suffices to compute. There is
 *       therefore no equivalent of the "declined at this modification count" witness that stops PQ
 *       from retrying on every idle persist - only a failure witness, for the far rarer case of an
 *       exception.</li>
 *   <li><b>Training is cheap.</b> PQ's clustering is seconds to tens of seconds, which is why its
 *       collection and its training are split so the GigaMap monitor can be dropped in between. One
 *       pass of vector addition is microseconds, so this manager takes the sample it is given and
 *       has no collection phase of its own. The orchestrator collects once and feeds both managers,
 *       which matters because collecting walks the whole GigaMap and, in embedded mode, calls the
 *       user's {@code Vectorizer} once per entity.</li>
 *   <li><b>Retraining is harmless.</b> A new PQ codebook would not match the codes already fused
 *       into an existing graph, so PQ is trained once for the life of the on-disk index. NVQ vectors
 *       are rewritten wholesale on every persist, each time with the quantizer in that same file's
 *       header, so a quantizer trained now and one trained later are equally valid. Adoption on load
 *       is therefore an optimisation - it saves a mean pass and makes the index report itself
 *       compressed immediately - rather than a correctness requirement.</li>
 * </ul>
 */
interface NVQCompressionManager
{
    /**
     * Minimum vectors needed for NVQ training.
     * <p>
     * A global mean is well defined for a single vector, so unlike
     * {@link PQCompressionManager#MIN_VECTORS_FOR_PQ_TRAINING} this is not a mathematical floor but
     * a guard against fitting the quantization range to a handful of points that the rest of the
     * data will not resemble.
     */
    final static int MIN_VECTORS_FOR_NVQ_TRAINING = 16;

    /**
     * Returns whether a quantizer is available, whether trained here or adopted from disk.
     *
     * @return true if a quantizer is held
     */
    public boolean isTrained();

    /**
     * Returns the quantizer, or null if none is held.
     *
     * @return the NVQ instance
     */
    public NVQuantization getNVQ();

    /**
     * Computes and adopts a quantizer from a collected sample.
     * <p>
     * Does nothing if a quantizer has been adopted in the meantime. Safe to call with no locks held:
     * it touches only the sample it is given, never the GigaMap or the graph.
     *
     * @param trainingVectors the dense sample to train from, as collected by
     *        {@link TrainingVectorProvider#collectTrainingVectors(int)}
     * @return true if a quantizer was produced by this call
     */
    public boolean trainFrom(List<VectorFloat<?>> trainingVectors);

    /**
     * Adopts the quantizer recovered from a loaded on-disk graph.
     * <p>
     * A {@code null} argument means the loaded graph carries no {@code NVQ} feature and resets this
     * manager to untrained, so the next training attempt actually trains instead of short-circuiting
     * forever on a quantizer it never had.
     *
     * @param nvq the quantizer embedded in the loaded graph, or {@code null} if it carries none
     */
    public void adoptTrainedNVQ(NVQuantization nvq);

    /**
     * Resets NVQ state (clears the quantizer).
     */
    public void reset();


    /**
     * Default implementation of NVQCompressionManager.
     */
    public static class Default implements NVQCompressionManager
    {
        private static final Logger LOG = LoggerFactory.getLogger(NVQCompressionManager.class);

        private static final VectorTypeSupport VECTOR_TYPE_SUPPORT =
            VectorizationProvider.getInstance().getVectorTypeSupport();

        private final String name         ;
        private final int    dimension    ;
        private final int    nvqSubvectors;

        // Written by the persist path (no locks held) and by the load path (write lock), read by
        // the persist path's write phase. Volatile for the same reason as in PQCompressionManager:
        // it publishes the quantizer safely without adding a lock to an already lock-dense chain.
        private volatile NVQuantization nvq    ;
        private volatile boolean        trained;

        Default(
            final String name         ,
            final int    dimension    ,
            final int    nvqSubvectors
        )
        {
            this.name          = name         ;
            this.dimension     = dimension    ;
            this.nvqSubvectors = nvqSubvectors;
        }

        @Override
        public boolean isTrained()
        {
            return this.trained;
        }

        @Override
        public NVQuantization getNVQ()
        {
            return this.nvq;
        }

        @Override
        public boolean trainFrom(final List<VectorFloat<?>> trainingVectors)
        {
            if(this.trained)
            {
                return false; // Adopted from a loaded graph, or trained, since the sample was collected
            }
            if(trainingVectors == null || trainingVectors.size() < MIN_VECTORS_FOR_NVQ_TRAINING)
            {
                LOG.debug("Not enough vectors for NVQ training ({} < {}), leaving the index uncompressed",
                    trainingVectors == null ? 0 : trainingVectors.size(), MIN_VECTORS_FOR_NVQ_TRAINING);
                return false;
            }

            LOG.info("Training NVQ for index '{}'...", this.name);

            final int subvectors = this.nvqSubvectors > 0
                ? this.nvqSubvectors
                : 1;

            // NVQuantization.create rather than NVQuantization.compute, deliberately. compute()
            // takes a RandomAccessVectorValues and walks every ordinal of it, which here would mean
            // the ordinal-spaced NullSafeVectorValues view used for the disk write. Two concrete
            // problems follow, in order of severity:
            //
            //  - it probes getVector(0) for the dimension, which is a placeholder - or on some paths
            //    null - whenever ordinal 0 is a deletion hole;
            //  - it walks the whole corpus rather than the capped sample, on exactly the data sets
            //    where that is most expensive.
            //
            // A third effect, that the placeholders standing in for holes would drag the mean toward
            // the origin, turns out to matter far less than it does for PQ's k-means: NVQ uses the
            // mean only to recentre before per-vector, per-subvector min/max scaling, and that range
            // absorbs a shifted mean almost entirely. Halving the mean in an experiment left search
            // results unchanged. So this is defence in depth rather than the reason.
            //
            // Computing the mean here from the dense sample avoids all three. The sample is a
            // uniform reservoir sample, whose mean is an unbiased estimator of the population mean;
            // at the 128k cap the per-component standard error is well under a percent of that
            // component's standard deviation.
            final VectorFloat<?> mean = VECTOR_TYPE_SUPPORT.createFloatVector(this.dimension);
            for(final VectorFloat<?> vector : trainingVectors)
            {
                VectorUtil.addInPlace(mean, vector);
            }
            VectorUtil.scale(mean, 1f / trainingVectors.size());

            final NVQuantization trainedNvq = NVQuantization.create(mean, subvectors);

            // Publish the quantizer before the flag: isTrained() must never report true without a
            // quantizer, since that is exactly the state in which the write path falls back to
            // full-precision vectors while the index reports itself as NVQ-compressed.
            this.nvq     = trainedNvq;
            this.trained = true      ;

            LOG.info("NVQ training complete for '{}': {} subvectors, {} training vectors",
                this.name, subvectors, trainingVectors.size());

            return true;
        }

        @Override
        public void adoptTrainedNVQ(final NVQuantization nvq)
        {
            this.nvq     = nvq        ;
            this.trained = nvq != null;
        }

        @Override
        public void reset()
        {
            this.nvq     = null ;
            this.trained = false;
        }

    }

}
