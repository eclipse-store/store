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

import java.util.Objects;

/**
 * The subset of a {@link VectorIndexConfiguration} that decides what an on-disk graph file
 * actually contains.
 * <p>
 * {@link DiskIndexManager} needs exactly these four values and nothing else from the configuration,
 * and they are also what the {@code .meta} file records so that a configuration change over an
 * existing index directory is detected rather than silently ignored. Bundling them keeps the disk
 * manager's constructor from growing a positional parameter per format option, and gives
 * {@code verifyMetadata} a single value to compare.
 * <p>
 * This type is <b>not</b> persisted by EclipseStore. It is derived from the persisted configuration
 * whenever a disk manager is constructed, so adding a component here is not a schema change - only
 * adding one to {@link VectorIndexConfiguration.Default} is.
 *
 * @param storage       how the graph stores each vector
 * @param scoring       how traversal scores candidates
 * @param pqSubspaces   the configured PQ subspace count, or 0 for automatic
 * @param nvqSubvectors the configured NVQ subvector count, or 0 for automatic
 *
 * @see VectorStorage
 * @see ApproximateScoring
 */
record GraphFormat(
    VectorStorage      storage      ,
    ApproximateScoring scoring      ,
    int                pqSubspaces  ,
    int                nvqSubvectors
)
{
    GraphFormat
    {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(scoring, "scoring");
        if(pqSubspaces < 0)
        {
            throw new IllegalArgumentException("pqSubspaces must be non-negative, got: " + pqSubspaces);
        }
        if(nvqSubvectors < 0)
        {
            throw new IllegalArgumentException("nvqSubvectors must be non-negative, got: " + nvqSubvectors);
        }
    }

    /**
     * Derives the format from a configuration.
     *
     * @param configuration the configuration to read the format options from
     * @return the format the configuration describes
     */
    static GraphFormat of(final VectorIndexConfiguration configuration)
    {
        return new GraphFormat(
            configuration.vectorStorage()     ,
            configuration.approximateScoring(),
            configuration.pqSubspaces()       ,
            configuration.nvqSubvectors()
        );
    }

    /**
     * Returns whether the graph this format describes carries fused PQ codes, assuming a codebook
     * was successfully trained.
     *
     * @return true if the scoring mode is {@link ApproximateScoring#FUSED_PQ}
     */
    boolean usesFusedPq()
    {
        return this.scoring == ApproximateScoring.FUSED_PQ;
    }

    /**
     * Returns whether this format keeps its PQ codes in a sidecar file and in heap rather than
     * fused into the graph.
     *
     * @return true if the scoring mode is {@link ApproximateScoring#PQ_IN_MEMORY}
     */
    boolean usesInMemoryPq()
    {
        return this.scoring == ApproximateScoring.PQ_IN_MEMORY;
    }

    /**
     * Returns whether this format needs a trained PQ codebook, whichever way it holds the codes.
     *
     * @return true if the scoring mode is any of the PQ-based ones
     */
    boolean usesPq()
    {
        return this.usesFusedPq() || this.usesInMemoryPq();
    }

    /**
     * Returns whether the graph this format describes stores quantized rather than full-precision
     * vectors.
     *
     * @return true if the storage mode is {@link VectorStorage#NVQ}
     */
    boolean usesNvq()
    {
        return this.storage == VectorStorage.NVQ;
    }

    /**
     * Returns the PQ subspace count this format actually encodes with, resolved from the sentinel
     * and reduced to zero when the format carries no PQ codes at all.
     * <p>
     * This is the form the metadata records, so that two configurations which produce the same
     * graph compare equal: the automatic sentinel and the value it resolves to are the same
     * setting, and a subspace count carries no meaning when nothing is quantized with it.
     *
     * @param dimension the configured vector dimension, which the automatic value derives from
     * @return the effective subspace count, or 0 if this format writes no fused PQ codes
     */
    int effectivePqSubspaces(final int dimension)
    {
        // usesPq, not usesFusedPq: PQ_IN_MEMORY encodes with this count too, it just keeps the
        // result in a sidecar instead of the graph. Gating on the fused mode alone would record a
        // zero for an in-memory index and lose the witness that makes a changed count take effect.
        if(!this.usesPq())
        {
            return 0;
        }
        // Clamped to one, exactly as PQCompressionManager resolves the same sentinel. A dimension
        // below four is legal - the builder asks only that it be positive - and plain division
        // gives zero there, so the manager would encode one byte per vector while the metadata
        // recorded none. sidecarFitsTheGraph compares the two, so such an index would reject its
        // own sidecar and rebuild on every single load.
        return this.pqSubspaces > 0 ? this.pqSubspaces : Math.max(1, dimension / 4);
    }

    /**
     * Returns the NVQ subvector count this format actually encodes with, resolved from the sentinel
     * and reduced to zero when the format stores full-precision vectors.
     *
     * @return the effective subvector count, or 0 if this format writes no quantized vectors
     */
    int effectiveNvqSubvectors()
    {
        if(!this.usesNvq())
        {
            return 0;
        }
        return this.nvqSubvectors > 0 ? this.nvqSubvectors : 1;
    }

    /**
     * Returns whether this format is the one the plain, feature-less write path produces.
     * <p>
     * That path is kept as a fast path precisely so the overwhelmingly common configuration keeps
     * writing byte-identical files to the ones produced before the format became configurable.
     *
     * @return true if the graph carries full-precision inline vectors and nothing else
     */
    boolean isPlainInline()
    {
        return this.storage == VectorStorage.INLINE && this.scoring == ApproximateScoring.NONE;
    }

}
