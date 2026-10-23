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
 *   <tr><td>Recall and latency while the graph fits in memory</td><td>{@link #NONE}</td><td>Faster and more
 *       accurate there than scoring from PQ codes, see below; exact with {@link VectorStorage#INLINE}, while
 *       {@link VectorStorage#NVQ} traverses its quantized vectors</td></tr>
 *   <tr><td>Query latency on a graph larger than the page cache</td><td>{@link #FUSED_PQ}</td><td>One sequential
 *       read scores a whole neighbourhood</td></tr>
 *   <tr><td>Same, but disk is tighter than heap</td><td>{@link #PQ_IN_MEMORY}</td><td>Costs heap per vector instead
 *       of disk per node</td></tr>
 *   <tr><td>Unknown/unsure</td><td>{@link #NONE}</td><td>The default, and free</td></tr>
 * </table>
 *
 * <h2>Trade-off</h2>
 * Approximate scoring saves reads, not arithmetic. A hop scored from codes costs one table lookup per subspace
 * and candidate, which at the automatic {@code pqSubspaces} of {@code dimension / 4} costs about as much as the
 * exact comparison it replaces, and the candidates found are reranked exactly afterwards either way. What it does
 * change is which candidates traversal finds: a neighbour that scores badly from its code is never visited, and
 * reranking cannot recover a candidate that was never found. So while the graph and its vectors are in the page
 * cache, scoring from PQ codes is slower and finds fewer of the true nearest neighbours; enough subspaces to
 * match the recall of exact traversal make it slower still. Scoring from PQ codes pays only where the graph is not
 * in the page cache, where it turns a random read per candidate into one sequential read per hop, and it pays for
 * that with recall.
 *
 * @see VectorStorage
 * @see VectorIndexConfiguration#approximateScoring()
 */
public enum ApproximateScoring
{
    /**
     * No <i>separate</i> structure for approximate scoring: nothing is added to the graph for
     * traversal to score from, so where the scores come from is decided by {@link VectorStorage}
     * alone - and the two storage modes differ in both cost and candidate selection.
     * <ul>
     *   <li><b>With {@link VectorStorage#INLINE}:</b> traversal scores exactly, reading each
     *       candidate's full-precision vector. One read per candidate, and no
     *       candidate is ever missed to quantization. This pairing is the only one that is exact
     *       all the way through, which is why it is the maximum-recall configuration.</li>
     *   <li><b>With {@link VectorStorage#NVQ}:</b> traversal scores from the graph's own quantized
     *       vectors, so it is approximate after all despite the name of this constant. Cheaper per
     *       hop, since no full-precision vector is read until reranking, but quantization error
     *       changes which candidates are visited - and one that is never reached is one reranking
     *       cannot recover.</li>
     * </ul>
     * Reranking is exact either way, against the full-precision vectors in the index's vector file, so the
     * scores and the ordering returned are exact under both.
     * <ul>
     *   <li><b>Cost:</b> nothing is added to the graph</li>
     * </ul>
     * <b>Best for:</b>
     * <ul>
     *   <li><b>Minimising the index footprint</b> - combined with {@link VectorStorage#NVQ} this is
     *       the smallest on-disk configuration available.</li>
     *   <li><b>Indices that fit in memory</b> - with no page faults to avoid, it is both faster
     *       and more accurate than scoring from PQ codes, with either storage, see the trade-off above.</li>
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
     * <b>This is an I/O optimisation, not a space one.</b> Fusing the neighbour codes into every
     * node duplicates each code {@code maxDegree} times, so the {@code .graph} file gets
     * <i>larger</i>. What it buys is far less I/O per hop: one contiguous block instead of
     * {@link VectorIndexConfiguration#maxDegree()} scattered reads. While the graph is in the page cache
     * there is no I/O left to save, and it is slower than {@link #NONE} with the same storage and, at
     * the automatic {@code pqSubspaces}, less accurate, see the trade-off above.
     * <p>
     * The codebook is trained once, on the first persist at which at least 256 <i>embeddings</i>
     * exist - entities without one are skipped, so an index of 256 entities of which some have no
     * vector does not yet qualify. Below that threshold the graph is written without the fused
     * codes and training is reattempted on the next persist that follows a graph-affecting change.
     * An unchanged index is deliberately not retried, since that would rebuild the whole graph on
     * every idle persist. Once trained, the codebook is written into the graph header of every
     * segment that carries the fused codes and recovered from them on every subsequent load, so it
     * is not retrained while such a segment exists.
     * <p>
     * <b>Best for:</b>
     * <ul>
     *   <li><b>Indices that do not fit in memory</b> - the case it exists for: it converts many
     *       random reads per hop into one sequential one, which is exactly the cost that dominates
     *       there, and pays for it with recall.</li>
     * </ul>
     * <b>Example:</b> at {@code dimension=768}, {@code maxDegree=32} and the automatic
     * {@code pqSubspaces} of 192 this adds 6144 bytes per node, nearly tripling a
     * {@link VectorStorage#INLINE} graph.
     *
     * @see VectorIndexConfiguration#pqSubspaces()
     */
    FUSED_PQ(2),

    /**
     * Product Quantization codes held in heap rather than fused into the graph.
     * <ul>
     *   <li><b>Cost:</b> nothing is added to the graph; {@code pqSubspaces} bytes per ordinal
     *       resident in heap while the index is open, plus the same again on disk for an on-disk
     *       index, which persists them in a {@code .pqv} sidecar</li>
     *   <li><b>Per hop:</b> candidates are scored from the in-heap codes, with no disk read at all</li>
     * </ul>
     * <b>Where the codes come from depends on the index.</b> An on-disk index writes them beside the
     * graph at persist time and reads them back at load. An <i>in-memory</i> index has no persist to
     * hang that on, so there is no sidecar at all: it builds the codes in heap during optimization,
     * which is also when it switches from exact to compressed scoring, and rebuilds them the same
     * way after a restart. That is why this mode requires a scheduled optimization when
     * {@link VectorIndexConfiguration#onDisk()} is false.
     * The same approximate scoring as {@link #FUSED_PQ}, bought differently. Fusing duplicates each
     * node's code into every neighbour that references it, which costs
     * {@code pqSubspaces * maxDegree} bytes per node <i>on disk</i>; holding the codes in one flat
     * array costs {@code pqSubspaces} bytes per ordinal <i>once</i>. At {@code dimension=768},
     * {@code maxDegree=32} and the automatic {@code pqSubspaces} of 192, that is 6144 bytes per node
     * of graph file against 192 bytes per ordinal of heap.
     * <p>
     * <b>The heap cost is linear and unbounded.</b> It is {@code pqSubspaces} bytes for every
     * ordinal up to the highest in use - deletion holes included - resident for as long as the index
     * is open. A million vectors at the default subspace count is about 192 MB; a hundred million is
     * about 19 GB. Size it before choosing this over {@link #FUSED_PQ}, which pays in disk and page
     * cache instead.
     * <p>
     * <b>Best for:</b>
     * <ul>
     *   <li><b>Indices where the graph file is the binding constraint</b> - combined with
     *       {@link VectorStorage#NVQ} this is the smallest on-disk configuration that still traverses
     *       approximately.</li>
     *   <li><b>Machines with heap to spare</b> - scoring never touches the disk, so it does not
     *       compete with the page cache the graph itself wants. As with {@link #FUSED_PQ}, that pays
     *       only once the graph no longer fits in the page cache, see the trade-off above.</li>
     * </ul>
     *
     * @see VectorIndexConfiguration#pqSubspaces()
     */
    PQ_IN_MEMORY(3);


    /**
     * Stable numeric identifier, deliberately independent of {@link #ordinal()}.
     * <p>
     * This value is written into each segment entry of the index manifest, so it must never change
     * for an existing constant. Using the ordinal instead would silently remap every previously
     * written file the
     * moment a constant were inserted anywhere but at the end.
     */
    private final int code;

    ApproximateScoring(final int code)
    {
        this.code = code;
    }

    /**
     * Returns the stable numeric identifier written into the index manifest.
     *
     * @return the code of this constant
     * @see #fromCode(int)
     */
    public int code()
    {
        return this.code;
    }

    /**
     * Resolves a constant from the stable identifier written into the index manifest.
     * <p>
     * Returns {@code null} rather than throwing for an unrecognised code, which is what a manifest
     * written by a newer version carrying a constant this build does not know looks like. The
     * caller rejects such a manifest and rebuilds the graph, so a newer file is always rejected
     * and never misread.
     *
     * @param code the identifier read from the manifest
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
