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
 * How the on-disk graph stores its own copy of each vector.
 * <p>
 * This is the <i>storage</i> dimension of the on-disk format, and it is independent of the
 * <i>scoring</i> dimension controlled by {@link ApproximateScoring}: any combination of the two is
 * legal. Storage decides how many bytes each node costs and how exact the reranking pass can be;
 * scoring decides how traversal picks its candidates.
 *
 * <h2>Decision Guide</h2>
 * <table border="1">
 *   <tr><th>Priority</th><th>Recommended</th><th>Reason</th></tr>
 *   <tr><td>Maximum recall</td><td>{@link #INLINE} with {@link ApproximateScoring#NONE}</td><td>Traversal reads full precision, so no candidate is missed to quantization. This one row needs both dimensions: {@code INLINE} alone leaves traversal to the scoring mode, and a PQ mode drives it from codes whether the vectors beside them are quantized or not</td></tr>
 *   <tr><td>Smallest index on disk</td><td>{@link #NVQ}</td><td>About 3x fewer bytes per node</td></tr>
 *   <tr><td>Index exceeds available memory</td><td>{@link #NVQ}</td><td>More of the graph fits in page cache</td></tr>
 *   <tr><td>Unknown/unsure</td><td>{@link #INLINE}</td><td>The default, and the conservative choice</td></tr>
 * </table>
 * <p>
 * <b>Both options are safe with respect to your data.</b> The {@code .graph} file is a derived
 * artifact: it is rebuilt from the vectors held in the GigaMap whenever its metadata is rejected.
 * Quantizing the graph's copy therefore trades recall, never durability.
 * <p>
 * Requires {@link VectorIndexConfiguration#onDisk()} to be true, since it describes the on-disk
 * format only. An in-memory index scores against the vectors in the GigaMap and has no second copy
 * to quantize.
 *
 * @see ApproximateScoring
 * @see VectorIndexConfiguration#vectorStorage()
 */
public enum VectorStorage
{
    /**
     * Full-precision {@code float32} vectors stored inline in the graph.
     * <ul>
     *   <li><b>Cost:</b> {@code dimension * 4} bytes per node</li>
     *   <li><b>Reranking:</b> exact, read straight out of the memory mapping</li>
     * </ul>
     * <b>Best for:</b>
     * <ul>
     *   <li><b>Recall-critical workloads</b> - the reranking pass compares against the true vectors,
     *       so the final ordering of the top-k is exact regardless of how traversal found them.</li>
     *   <li><b>Indices that comfortably fit in memory</b> - there are no page faults left to avoid,
     *       so the smaller footprint of {@link #NVQ} buys nothing while its quantization still costs
     *       a little recall.</li>
     *   <li><b>Anything unsure</b> - this is the default and matches the behaviour of every release
     *       before vector storage became configurable.</li>
     * </ul>
     * <b>Example:</b> at {@code dimension=768} and {@code maxDegree=32} a node costs
     * {@code 4 + 3072 + 132 = 3208} bytes, so a million vectors occupy roughly 3.0 GB.
     */
    INLINE(1),

    /**
     * Non-uniform vector quantization (NVQ): 8-bit quantized vectors stored inline in the graph.
     * <ul>
     *   <li><b>Cost:</b> {@code 4 + dimension + 28 * nvqSubvectors} bytes per node</li>
     *   <li><b>Reranking:</b> exact, against the vectors held in the GigaMap</li>
     * </ul>
     * Each vector is recentred on a global mean and then scaled per subvector through a learned
     * nonlinearity, which is what lets 8 bits per dimension carry as much as it does.
     * <p>
     * <b>This is a space optimisation</b>, and the only one of the two dimensions that shrinks the
     * file: roughly 3x fewer bytes per node than {@link #INLINE}, so more of the graph stays in page
     * cache and cold traversal touches fewer pages.
     * <p>
     * <b>It never costs score accuracy.</b> Reranking compares against the vectors held in the
     * GigaMap, which are full precision, so the top-k a search returns carries exact similarities
     * and exact ordering whatever the scoring mode is.
     * <p>
     * <b>What else it costs depends on that mode</b>, because that decides whether these vectors are
     * read during traversal at all. With {@link ApproximateScoring#NONE} they are, so quantization
     * changes which candidates traversal finds - and a candidate that traversal never reaches is one
     * reranking cannot recover; a wider search beam is what gets them back. With
     * {@link ApproximateScoring#FUSED_PQ} the fused codes drive traversal and this block is not
     * consulted, so candidate selection is exactly what it would be under {@link #INLINE}, and the
     * cost is the footprint saving plus one GigaMap lookup per reranked candidate where an inline
     * graph reads them from its own mapping.
     * <p>
     * The dispatch does hold one branch that reranks from the graph's own quantized copy instead,
     * for a non-incremental disk search. An on-disk index does not currently enter that state -
     * loading enters incremental mode and each persist re-enters it - so the branch is unreached
     * today and kept for correctness if that changes. Were it reached, the final ordering would be
     * computed from dequantized vectors; a raw jvector probe reranked that way at
     * {@code dimension=256} measured about 0.002 recall@10 below an exact baseline.
     * <p>
     * <b>Best for:</b>
     * <ul>
     *   <li><b>Large on-disk indices</b> - where the footprint, and the page cache it competes for,
     *       is the binding constraint rather than the last fraction of a percent of recall.</li>
     *   <li><b>Indices that do not fit in memory</b> - fewer bytes per node means fewer page faults
     *       per hop, which is the dominant cost once the mapping exceeds RAM.</li>
     * </ul>
     * <b>Example:</b> at {@code dimension=768}, {@code maxDegree=32} and one subvector a node costs
     * {@code 4 + 800 + 132 = 936} bytes, so a million vectors occupy roughly 0.89 GB - against 3.0 GB
     * for {@link #INLINE}.
     *
     * @see VectorIndexConfiguration#nvqSubvectors()
     */
    NVQ(2);


    /**
     * Stable numeric identifier, deliberately independent of {@link #ordinal()}.
     * <p>
     * This value is written into the index metadata file, so it must never change for an existing
     * constant. Using the ordinal instead would silently remap every previously written file the
     * moment a constant were inserted anywhere but at the end.
     */
    private final int code;

    VectorStorage(final int code)
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
    public static VectorStorage fromCode(final int code)
    {
        for(final VectorStorage storage : VectorStorage.values())
        {
            if(storage.code == code)
            {
                return storage;
            }
        }
        return null;
    }

}
