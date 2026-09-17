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

/**
 * How graph traversal scores the candidates it visits.
 * <p>
 * This is the <i>scoring</i> dimension of the on-disk format, independent of the <i>storage</i>
 * dimension controlled by {@link VectorStorage}: any combination of the two is legal. Scoring
 * decides how traversal picks its candidates; storage decides how many bytes each node costs and
 * how exact the reranking pass that follows can be.
 * <p>
 * Whatever is chosen here, the best candidates are always reranked before they are returned. This
 * setting governs which candidates traversal finds, not how the final top-k is ordered.
 *
 * <h2>Decision Guide</h2>
 * <table border="1">
 *   <tr><th>Priority</th><th>Recommended</th><th>Reason</th></tr>
 *   <tr><td>Smallest index on disk</td><td>{@link #NONE}</td><td>Adds nothing to the graph</td></tr>
 *   <tr><td>Query latency on a large index</td><td>{@link #FUSED_PQ}</td><td>One sequential read scores a whole neighbourhood</td></tr>
 *   <tr><td>Unknown/unsure</td><td>{@link #NONE}</td><td>The default, and free</td></tr>
 * </table>
 *
 * @see VectorStorage
 * @see VectorIndexConfiguration#approximateScoring()
 */
public enum ApproximateScoring
{
    /**
     * No approximate scoring: traversal scores candidates directly from whatever
     * {@link VectorStorage} the graph carries.
     * <ul>
     *   <li><b>Cost:</b> nothing is added to the graph</li>
     *   <li><b>Per hop:</b> one read per candidate, from the node's own stored vector</li>
     * </ul>
     * <b>Best for:</b>
     * <ul>
     *   <li><b>Minimising the index footprint</b> - combined with {@link VectorStorage#NVQ} this is
     *       the smallest on-disk configuration available.</li>
     *   <li><b>Indices that fit in memory</b> - with no page faults to avoid, the fused codes of
     *       {@link #FUSED_PQ} buy little while still costing their bytes.</li>
     *   <li><b>Anything unsure</b> - this is the default.</li>
     * </ul>
     */
    NONE(1),

    /**
     * Product Quantization codes fused into the graph: each node stores the compressed codes of all
     * its neighbours alongside its edges.
     * <ul>
     *   <li><b>Cost:</b> {@code pqSubspaces * maxDegree} additional bytes per node</li>
     *   <li><b>Per hop:</b> one sequential read scores every candidate in the neighbourhood</li>
     * </ul>
     * <b>This is a speed optimisation, not a space one.</b> Fusing the neighbour codes into every
     * node duplicates each code {@code maxDegree} times, so the {@code .graph} file gets
     * <i>larger</i>. What it buys is far less I/O and far better cache locality per hop: one
     * contiguous block instead of {@link VectorIndexConfiguration#maxDegree()} scattered reads.
     * <p>
     * The codebook is trained once, on the first persist at which at least 256 <i>embeddings</i>
     * exist - entities without one are skipped, so an index of 256 entities of which some have no
     * vector does not yet qualify. Below that threshold the graph is written without the fused
     * codes and training is reattempted on the next persist that follows a graph-affecting change.
     * An unchanged index is deliberately not retried, since that would rebuild the whole graph on
     * every idle persist. Once trained, the codebook lives in the graph header and is recovered on
     * every subsequent load, so it is never retrained for the life of that on-disk index.
     * <p>
     * <b>Best for:</b>
     * <ul>
     *   <li><b>Query latency on a large on-disk index</b> - the case it exists for.</li>
     *   <li><b>Indices that do not fit in memory</b> - it converts many random reads per hop into
     *       one sequential one, which is exactly the cost that dominates there.</li>
     * </ul>
     * <b>Example:</b> at {@code dimension=768}, {@code maxDegree=32} and the automatic
     * {@code pqSubspaces} of 192 this adds 6144 bytes per node, nearly tripling a
     * {@link VectorStorage#INLINE} graph.
     *
     * @see VectorIndexConfiguration#pqSubspaces()
     */
    FUSED_PQ(2);

    // Code 3 is reserved for a future PQ_IN_MEMORY constant, which would hold the compressed codes
    // in a sidecar file and in heap instead of fusing them into every node: the same approximate
    // scoring at pqSubspaces bytes per vector once, rather than pqSubspaces * maxDegree bytes per
    // node on disk. Constants may only ever be appended - see the note on code below.


    /**
     * Stable numeric identifier, deliberately independent of {@link #ordinal()}.
     * <p>
     * This value is written into the index metadata file, so it must never change for an existing
     * constant. Using the ordinal instead would silently remap every previously written file the
     * moment a constant were inserted anywhere but at the end.
     */
    private final int code;

    ApproximateScoring(final int code)
    {
        this.code = code;
    }

    /**
     * Returns the stable numeric identifier written into the index metadata.
     *
     * @return the code of this constant
     * @see #fromCode(int)
     */
    public int code()
    {
        return this.code;
    }

    /**
     * Resolves a constant from the stable identifier written into an index metadata file.
     * <p>
     * Returns {@code null} rather than throwing for an unrecognised code, which is what a file
     * written by a newer version carrying a constant this build does not know looks like. The
     * caller treats that as a metadata mismatch and rebuilds the graph, so a newer file is always
     * rejected and never misread.
     *
     * @param code the identifier read from the metadata file
     * @return the matching constant, or {@code null} if this build knows no such code
     * @see #code()
     */
    public static ApproximateScoring fromCode(final int code)
    {
        for(final ApproximateScoring scoring : ApproximateScoring.values())
        {
            if(scoring.code == code)
            {
                return scoring;
            }
        }
        return null;
    }

}
