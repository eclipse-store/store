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

import org.eclipse.store.gigamap.types.IndexLocation;
import org.eclipse.store.gigamap.types.IndexLocations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static org.eclipse.serializer.math.XMath.positive;
import static org.eclipse.serializer.util.X.notNull;

/**
 * Configuration for a {@link VectorIndex} using the HNSW (Hierarchical Navigable Small World) algorithm.
 * <p>
 * HNSW is a graph-based approximate nearest neighbor (ANN) search algorithm that provides
 * excellent query performance with high recall. It builds a multi-layer graph where each layer
 * is a proximity graph with different densities, enabling efficient navigation from coarse
 * to fine granularity during search.
 * <p>
 * This configuration controls the trade-offs between:
 * <ul>
 *   <li><b>Index build time</b> - Higher {@link #maxDegree()} and {@link #beamWidth()} increase build time</li>
 *   <li><b>Query performance</b> - Higher {@link #maxDegree()} improves query speed</li>
 *   <li><b>Memory usage</b> - Higher {@link #maxDegree()} increases memory consumption</li>
 *   <li><b>Recall accuracy</b> - Higher {@link #beamWidth()} and {@link #maxDegree()} improve recall</li>
 * </ul>
 *
 * <h2>Basic Usage Example</h2>
 * <pre>{@code
 * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
 *     .dimension(768)                                    // OpenAI ada-002 embeddings
 *     .similarityFunction(VectorSimilarityFunction.COSINE)
 *     .maxDegree(32)                                     // Higher for better recall
 *     .beamWidth(200)                                    // Higher for better index quality
 *     .build();
 * }</pre>
 *
 * <h2>On-Disk Storage</h2>
 * For large indices that exceed available memory, enable on-disk storage:
 * <pre>{@code
 * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
 *     .dimension(768)
 *     .similarityFunction(VectorSimilarityFunction.COSINE)
 *     .onDisk(true)
 *     .indexDirectory(Path.of("/data/vectors"))
 *     .approximateScoring(ApproximateScoring.FUSED_PQ)   // Optional: faster traversal, larger file
 *     .pqSubspaces(48)                                   // Must divide dimension evenly
 *     .build();
 * }</pre>
 *
 * <h2>Background Persistence</h2>
 * For on-disk indices, background persistence automatically saves the graph at regular intervals.
 * Setting {@code persistenceIntervalMs} to a value greater than 0 enables background persistence:
 * <pre>{@code
 * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
 *     .dimension(768)
 *     .onDisk(true)
 *     .indexDirectory(Path.of("/data/vectors"))
 *     .persistenceIntervalMs(30_000)                     // Enable, check every 30 seconds
 *     .minChangesBetweenPersists(100)                    // Only persist if >= 100 changes
 *     .persistOnShutdown(true)                           // Persist pending changes on close()
 *     .build();
 * }</pre>
 * A persist writes the in-memory graph, which holds the changes since the previous one, as a new
 * immutable segment. A background persist does so once the in-memory graph holds
 * {@code flushThreshold} nodes. A background merge combines segments of similar size, {@code segmentMergeFactor}
 * at a time, into one, so the segment count grows with the logarithm of the index size, and {@code maxSegments}
 * bounds how many segments accumulate before a persist consolidates them into one, should the merges fall
 * behind.
 *
 * <h2>Background Optimization</h2>
 * Background optimization periodically purges removed entities from the graph and removes excess
 * neighbors accumulated during construction, reducing memory and improving query latency.
 * Setting {@code optimizationIntervalMs} to a value greater than 0 enables background optimization:
 * <pre>{@code
 * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
 *     .dimension(768)
 *     .optimizationIntervalMs(60_000)                    // Enable, check every 60 seconds
 *     .minChangesBetweenOptimizations(1000)              // Only optimize if >= 1000 changes
 *     .optimizeOnShutdown(false)                         // Skip optimization on close() (faster shutdown)
 *     .build();
 * }</pre>
 * <p>
 * <b>Note:</b> Background optimization acquires exclusive access to the graph, briefly blocking
 * add/remove/search operations during cleanup.
 * <p>
 * Optimization applies to in-memory indices only. For an on-disk index it does nothing: every
 * flush purges and trims the graph it writes.
 *
 * <h2>Combined Example</h2>
 * Production configuration with on-disk storage, persistence, and optimization settings (which
 * an on-disk index accepts but does not act on, see above):
 * <pre>{@code
 * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
 *     .dimension(768)
 *     .similarityFunction(VectorSimilarityFunction.COSINE)
 *     .maxDegree(32)
 *     .beamWidth(200)
 *     // On-disk storage
 *     .onDisk(true)
 *     .indexDirectory(Path.of("/data/vectors"))
 *     // Background persistence (enabled by setting interval > 0)
 *     .persistenceIntervalMs(30_000)
 *     .minChangesBetweenPersists(100)
 *     .persistOnShutdown(true)
 *     // Background optimization (enabled by setting interval > 0)
 *     .optimizationIntervalMs(60_000)
 *     .minChangesBetweenOptimizations(1000)
 *     .optimizeOnShutdown(false)
 *     .build();
 * }</pre>
 *
 * <h2>Parameter Guidelines</h2>
 * <table border="1">
 *   <tr><th>Use Case</th><th>maxDegree</th><th>beamWidth</th><th>Notes</th></tr>
 *   <tr><td>Small dataset (&lt;10K)</td><td>8-16</td><td>50-100</td><td>Lower values sufficient</td></tr>
 *   <tr><td>Medium dataset (10K-1M)</td><td>16-32</td><td>100-200</td><td>Balanced trade-off</td></tr>
 *   <tr><td>Large dataset (&gt;1M)</td><td>32-64</td><td>200-400</td><td>Higher for better recall</td></tr>
 *   <tr><td>High precision required</td><td>48-64</td><td>400-500</td><td>Maximum recall</td></tr>
 * </table>
 *
 * @see VectorIndex
 * @see VectorSimilarityFunction
 */
public interface VectorIndexConfiguration
{
    /**
     * Returns the dimensionality of vectors in this index.
     * <p>
     * All vectors added to the index must have exactly this number of dimensions.
     * Common embedding dimensions:
     * <ul>
     *   <li>OpenAI text-embedding-ada-002: 1536</li>
     *   <li>OpenAI text-embedding-3-small: 1536</li>
     *   <li>OpenAI text-embedding-3-large: 3072</li>
     *   <li>Cohere embed-english-v3.0: 1024</li>
     *   <li>sentence-transformers/all-MiniLM-L6-v2: 384</li>
     *   <li>BERT base: 768</li>
     * </ul>
     *
     * @return the vector dimension (must be positive)
     */
    public int dimension();

    /**
     * Returns the similarity function used to compare vectors.
     * <p>
     * See {@link VectorSimilarityFunction} for detailed descriptions of each function
     * and guidance on choosing the right one for your use case.
     *
     * @return the similarity function (default: {@link VectorSimilarityFunction#COSINE})
     * @see VectorSimilarityFunction
     */
    public VectorSimilarityFunction similarityFunction();

    /**
     * Returns the maximum number of connections (edges) per node in the HNSW graph.
     * <p>
     * This parameter (often called "M" in HNSW literature) controls the graph's connectivity:
     * <ul>
     *   <li><b>Higher values (32-64)</b>: Better recall and query performance, but more memory
     *       and slower index construction. Each node stores more neighbor references.</li>
     *   <li><b>Lower values (8-16)</b>: Less memory and faster construction, but potentially
     *       lower recall for complex datasets.</li>
     * </ul>
     * <p>
     * Memory impact: Each node requires approximately {@code maxDegree * 8} bytes for neighbor
     * storage (assuming 64-bit references).
     * <p>
     * <b>Recommendation:</b> Start with 16 for small datasets, increase to 32-48 for
     * million-scale datasets or when high recall (&gt;95%) is required.
     *
     * @return the maximum degree per node (default: 16)
     */
    public int maxDegree();

    /**
     * Returns the beam width used during index construction.
     * <p>
     * This parameter (often called "efConstruction" in HNSW literature) controls the
     * quality of the graph during construction:
     * <ul>
     *   <li><b>Higher values (200-500)</b>: Better graph quality and higher recall,
     *       but significantly slower index construction. More candidates are considered
     *       when selecting neighbors for each new node.</li>
     *   <li><b>Lower values (50-100)</b>: Faster construction but potentially lower
     *       recall, especially for high-dimensional or complex datasets.</li>
     * </ul>
     * <p>
     * <b>Important:</b> This only affects index construction time, not query time.
     * Higher values create a better-connected graph that improves all subsequent queries.
     * <p>
     * <b>Recommendation:</b> Use at least {@code 2 * maxDegree}. For production systems
     * where index quality matters more than build time, use 200-400.
     *
     * @return the construction beam width (default: 100)
     */
    public int beamWidth();

    /**
     * Returns the minimum beam width used during search (HNSW <i>efSearch</i> floor).
     * <p>
     * This parameter controls the minimum number of candidate nodes the HNSW graph traversal
     * explores when answering a query, independent of the requested {@code k}. The effective
     * search beam width is {@code max(k, minSearchBeamWidth())}.
     * <p>
     * <b>Difference from {@link #beamWidth()}:</b> the existing {@code beamWidth()} controls
     * <i>construction</i> effort (HNSW <i>efConstruction</i>) and has no effect at query time.
     * {@code minSearchBeamWidth()} controls <i>search</i> effort (HNSW <i>efSearch</i>) and has
     * no effect during construction. They are orthogonal.
     * <p>
     * <b>Why a floor matters:</b> when the beam width equals a small {@code k}, the traversal
     * explores too few candidates and the resulting top-k can vary depending on the requested
     * {@code k} (e.g. {@code search(q, 5)} and the first 5 results of {@code search(q, 50)}
     * may differ). Enforcing a floor keeps the top-k stable regardless of the requested {@code k}.
     * <p>
     * <b>Tuning:</b>
     * <ul>
     *   <li><b>Higher values (200-500):</b> better recall and more consistent results,
     *       at the cost of query latency.</li>
     *   <li><b>Lower values (down to 1):</b> faster queries at small {@code k}. A value of
     *       {@code 1} effectively disables the floor — the beam width equals the requested {@code k}.</li>
     * </ul>
     * <p>
     * For per-query overrides, see {@link VectorIndex#search(float[], int, int)}.
     *
     * @return the minimum search beam width (default: 100)
     */
    public int minSearchBeamWidth();

    /**
     * Returns the neighbor overflow factor for temporary neighbor storage during construction.
     * <p>
     * During index construction, HNSW temporarily stores more neighbors than {@link #maxDegree()}
     * before pruning. This factor controls how many extra neighbors to consider:
     * <ul>
     *   <li><b>Value of 1.2</b>: Store up to {@code maxDegree * 1.2} candidates before pruning</li>
     *   <li><b>Higher values</b>: Better neighbor selection quality, slightly more memory during construction</li>
     * </ul>
     * <p>
     * This parameter has minimal impact on final index quality or query performance.
     * The default value of 1.2 is suitable for most use cases.
     *
     * @return the neighbor overflow factor (default: 1.2)
     */
    public float neighborOverflow();

    /**
     * Returns the alpha parameter for the RNG (Relative Neighborhood Graph) pruning heuristic.
     * <p>
     * Alpha controls how aggressively the graph is pruned during construction:
     * <ul>
     *   <li><b>Alpha = 1.0</b>: Standard pruning, keeps edges to all Voronoi neighbors</li>
     *   <li><b>Alpha &gt; 1.0</b>: More aggressive pruning, removes some "redundant" edges
     *       that could be reached through other paths</li>
     *   <li><b>Higher alpha (1.2-1.4)</b>: Sparser graph, faster queries, but potentially
     *       lower recall for some query distributions</li>
     * </ul>
     * <p>
     * The pruning heuristic helps maintain graph navigability while reducing memory usage.
     * An edge from node A to node B is pruned if there exists another node C such that
     * {@code distance(A,C) < alpha * distance(A,B)} and C is closer to B.
     * <p>
     * <b>Recommendation:</b> Use the default value of 1.2. Only increase if memory is
     * constrained and you can tolerate slightly lower recall.
     *
     * @return the alpha pruning parameter (default: 1.2)
     */
    public float alpha();

    /**
     * Returns whether this index uses on-disk storage for the graph.
     * <p>
     * When enabled, the HNSW graph is stored on disk rather than fully in memory.
     * This enables:
     * <ul>
     *   <li><b>Persistent graph</b>: Load graph from disk instead of rebuilding on startup</li>
     *   <li><b>Larger datasets</b>: Support datasets that don't fit in memory (with PQ compression)</li>
     * </ul>
     * <p>
     * The vector file goes with the mode: an on-disk index keeps it in {@link #indexDirectory()} across restarts,
     * an in-memory index uses a temporary one and re-vectorizes every entity on every load, under the GigaMap
     * monitor. For a vectorizer that is expensive to call, such as an embedding API, prefer on-disk; see
     * {@link Vectorizer}.
     * <p>
     * Requires {@link #indexDirectory()} to be set.
     *
     * @return true if on-disk mode is enabled (default: false)
     * @see #indexDirectory()
     * @see #enablePqCompression()
     */
    public boolean onDisk();

    /**
     * Returns the directory where index files are stored.
     * <p>
     * Required when {@link #onDisk()} is true. The directory will contain:
     * <ul>
     *   <li>{@code {name}.manifest} - The commit point: format version, dimension, vector count,
     *       highest entity id and structural modification count, and per segment its format
     *       settings - the two modes and the two quantization counts they encode with, the latter in
     *       effective form, so that an automatic count and the value it resolves to are recorded
     *       identically - and the nodes it no longer holds the live copy of.</li>
     *   <li>{@code {name}.seg-{n}.graph} - The graph of segment {@code n}: HNSW edges, one vector
     *       block per node, and, when {@link #approximateScoring()} is
     *       {@link ApproximateScoring#FUSED_PQ}, the PQ codebook and fused compressed codes. There is
     *       no separate codebook file. Which vector block is written depends on
     *       {@link #vectorStorage()}: full-precision inline vectors, or the smaller quantized ones.
     *       Nodes are numbered densely within the segment.</li>
     *   <li>{@code {name}.seg-{n}.l2g} - The entity id of each node of segment {@code n}.</li>
     *   <li>{@code {name}.seg-{n}.pqv} - <b>Only</b> when {@link #approximateScoring()} is
     *       {@link ApproximateScoring#PQ_IN_MEMORY}: the PQ codes of segment {@code n}, one per
     *       node, and the codebook they were encoded with. This mode writes no fused feature, so the
     *       graph header carries no codebook and this file is the only copy. It is required rather
     *       than optional - an index whose manifest lists it is rejected and rebuilt from the store
     *       if it is missing - so <b>include it in any backup or copy of the directory, and do not
     *       delete it separately from the graph.</b></li>
     * </ul>
     * Segment files are written and synced before the manifest that lists them, and the manifest is
     * written to a {@code .tmp} sibling, synced and renamed into place, so an interrupted persist
     * leaves the previous manifest with all of its segments, plus files no manifest lists, which
     * the next load deletes.
     *
     * @return the index directory path, or null if not using on-disk mode
     * @see #onDisk()
     * @see #indexLocation()
     */
    public Path indexDirectory();

    /**
     * Returns the name of the {@link IndexLocation#Named(String) named location} the index files are
     * kept in, or {@code null} if they are kept in {@link #indexDirectory()}.
     * <p>
     * A named location is resolved through {@link IndexLocations} whenever the index is created or
     * loaded. For an index switched from a plain directory to a named location
     * ({@link VectorIndices#changeIndexLocation(String, IndexLocation)}), {@link #indexDirectory()} still
     * holds the previous directory: it is not used while a name is set, but lets a library version
     * without named locations keep working with the storage. It rejects the graph there if it is
     * outdated and rebuilds it. An index created with a named location has no previous directory:
     * such a version rebuilds its graph in memory on every start and fails to write it to disk.
     *
     * @return the location name, or {@code null}
     */
    public default String indexLocationName()
    {
        return null;
    }

    /**
     * Returns where the index files are kept: {@link IndexLocation#Named(String)} if
     * {@link #indexLocationName()} is set, otherwise {@link IndexLocation#Absolute(Path)} of
     * {@link #indexDirectory()}.
     *
     * @return the index location, or {@code null} if neither a name nor a directory is configured
     */
    public default IndexLocation indexLocation()
    {
        final String name = this.indexLocationName();
        if(name != null)
        {
            return IndexLocation.Named(name);
        }
        final Path directory = this.indexDirectory();
        return directory != null ? IndexLocation.Absolute(directory) : null;
    }

    /**
     * Returns a copy of this configuration that differs only in where the index files are kept.
     * <p>
     * This instance is left unchanged: a configuration can be shared by several indices. For a
     * {@link IndexLocation#Named(String) named location} the copy keeps this configuration's
     * {@link #indexDirectory()}, see {@link #indexLocationName()}.
     * <p>
     * The default implementation throws; {@link Default} supports it.
     *
     * @param indexLocation the location of the copy
     * @return the copy
     * @throws UnsupportedOperationException if this implementation cannot be copied
     */
    public default VectorIndexConfiguration withIndexLocation(final IndexLocation indexLocation)
    {
        throw new UnsupportedOperationException(
            this.getClass().getName() + " does not support copying with another index location."
        );
    }

    /**
     * Returns how the on-disk graph stores its own copy of each vector.
     * <p>
     * This is one of two independent dimensions of the on-disk format; the other is
     * {@link #approximateScoring()}. Storage decides how many bytes each node costs and where the
     * reranking pass reads its full-precision vectors from, scoring decides how traversal finds its
     * candidates, and any combination of the two is legal. Neither affects how exact the reranking
     * is: it is exact under all four.
     * <p>
     * {@link VectorStorage#NVQ} is the only setting on either dimension that makes the
     * {@code .graph} file <i>smaller</i>: roughly 3x. It never costs score accuracy - reranking
     * compares against the full-precision vectors in the index's vector file, so a search returns
     * exact similarities either way - and whether it costs anything else depends on
     * {@link #approximateScoring()}. See the constant's own documentation.
     * <p>
     * Requires {@link #onDisk()} to be true, since it describes the on-disk format only.
     * <p>
     * A {@code default} so that an implementation written against an earlier version of this
     * interface still compiles and links. Such an implementation predates the storage dimension, so
     * {@link VectorStorage#INLINE} is what it described.
     *
     * @return the vector storage mode (default: {@link VectorStorage#INLINE})
     * @see VectorStorage
     * @see #approximateScoring()
     * @see #nvqSubvectors()
     */
    public default VectorStorage vectorStorage()
    {
        return VectorStorage.INLINE;
    }

    /**
     * Returns how graph traversal scores the candidates it visits.
     * <p>
     * For an on-disk index this is one of two independent dimensions of the format; the other is
     * {@link #vectorStorage()}, which has no in-memory counterpart. This setting, though, is not
     * confined to the format: {@link ApproximateScoring#PQ_IN_MEMORY} governs traversal for an
     * in-memory index too, where nothing is written at all. Whatever is chosen here, the best
     * candidates are always reranked before they are returned, so this governs which candidates
     * traversal finds rather than how the final top-k is ordered.
     * <p>
     * {@link ApproximateScoring#FUSED_PQ} requires {@link #onDisk()}, since its codes are written
     * into the graph file. {@link ApproximateScoring#PQ_IN_MEMORY} does not - it keeps its codes in
     * heap, which an in-memory index can do too, though there it needs a scheduled optimization to
     * switch over; see that constant.
     *
     * <p>
     * A {@code default} for the same reason as {@link #vectorStorage()}, and it derives from the
     * deprecated flag rather than returning a constant: an implementation predating this method
     * expresses the same setting through {@link #enablePqCompression()}, so deriving keeps it
     * answering correctly instead of silently reporting no approximate scoring.
     *
     * @return the approximate scoring mode (default: {@link ApproximateScoring#NONE})
     * @see ApproximateScoring
     * @see #vectorStorage()
     * @see #pqSubspaces()
     */
    public default ApproximateScoring approximateScoring()
    {
        return this.enablePqCompression()
            ? ApproximateScoring.FUSED_PQ
            : ApproximateScoring.NONE;
    }

    /**
     * Returns the number of NVQ subvectors.
     * <p>
     * NVQ splits each vector into this many subvectors and fits the quantization nonlinearity to
     * each one separately, which helps when different ranges of the dimension have markedly
     * different scale.
     * <p>
     * <b>Prefer the default of one.</b> Every subvector adds a fixed 28 bytes of parameters per
     * node, on top of the one byte per dimension that carries the actual data, so the count is
     * close to pure overhead unless the data really is heterogeneous. At {@code dimension=768} one
     * subvector costs 800 bytes per node and eight cost 996 - the difference between a 3.4x and a
     * 2.8x saving against {@link VectorStorage#INLINE}. This is emphatically <b>not</b> the same
     * parameter as {@link #pqSubspaces()}, whose automatic value of {@code dimension/4} would make
     * an NVQ block twice the size of the vector it replaces.
     * <p>
     * Only meaningful when {@link #vectorStorage()} is {@link VectorStorage#NVQ}.
     *
     * <p>
     * A {@code default} for the same reason as {@link #vectorStorage()}. One is both the
     * recommended value and what an implementation predating this method implies.
     *
     * @return the effective number of NVQ subvectors, never zero: a configured 0 means "auto" and
     *         resolves to 1 here, so callers always receive a usable count. Note this differs from
     *         {@link #pqSubspaces()}, which returns its raw sentinel and leaves resolution to the
     *         caller.
     * @see VectorStorage#NVQ
     * @see #vectorStorage()
     */
    public default int nvqSubvectors()
    {
        return 1;
    }

    /**
     * Returns whether Product Quantization (PQ) compression is enabled.
     * <p>
     * <b>Deprecated</b> in favour of {@link #approximateScoring()}, which expresses the same
     * setting as one of several scoring modes rather than a single boolean. This method reports
     * {@code true} exactly when the scoring mode is {@link ApproximateScoring#FUSED_PQ}.
     * <p>
     * When enabled, the on-disk graph additionally carries {@code FusedPQ}: each node stores the
     * PQ-compressed codes of all its neighbours alongside its edges, so one sequential read scores
     * every candidate during traversal instead of one random full-vector read per candidate.
     * <p>
     * <b>What the graph stores beside those codes is now a separate setting.</b> With the default
     * {@link VectorStorage#INLINE} the full-precision vectors are still written and the best
     * candidates are reranked against them, read straight from the memory-mapped graph, as before.
     * With {@link VectorStorage#NVQ} the graph holds quantized vectors instead, so the rerank reads
     * the full-precision vectors in the index's vector file: still exact, but one lookup per reranked
     * candidate rather than none. See {@link #vectorStorage()}.
     * <p>
     * <b>This is a speed optimisation, not a space one.</b> Fusing the neighbour codes into every
     * node duplicates them {@code maxDegree} times, so enabling PQ makes the {@code .graph} file
     * <i>larger</i> by roughly {@code pqSubspaces * maxDegree} bytes per node - it does not shrink
     * the index. {@link VectorStorage#NVQ} is the setting that shrinks it, and the two compose:
     * the fused codes cost the same either way, while the vector beside them gets smaller. What it
     * buys is far less I/O and far better cache locality while traversing, and a smaller working set
     * of vectors touched per query. Choose it when search latency on a large on-disk index matters,
     * not when disk footprint does.
     * <p>
     * <b>Memory, with {@link VectorStorage#INLINE} storage.</b> The resident working set during
     * search goes <i>down</i>: a hop reads one contiguous fused block instead of
     * {@link #maxDegree()} scattered full vectors, and reranking reads the inline vectors out of the
     * mapping into a reused buffer. The costs are transient heap at persist time - encoding
     * allocates {@code nodes * pqSubspaces} bytes for the whole ordinal space, and the persist walks
     * every vector twice, once to encode and once to write the inline vectors - plus the larger
     * mapping itself. Note that if the whole index already fits in RAM there are no page faults left
     * to avoid, so PQ buys only cheaper arithmetic and cache locality while still costing the extra
     * resident bytes: it is a way to scale past available memory rather than a general latency tweak.
     * <p>
     * <b>With {@link VectorStorage#NVQ} storage</b> the arithmetic above does not carry over. There
     * are no full-precision inline vectors: the graph holds quantized ones, so the bytes the persist
     * writes are roughly a quarter of the figures quoted above. Reranking is unaffected in accuracy
     * but changes source - with no full-precision copy in the graph it reads the vectors in the
     * index's vector file, so it costs a lookup per reranked candidate instead of a read from the
     * graph mapping. The
     * fused block is unchanged, and remains the dominant per-node cost.
     * <p>
     * The codebook is trained once, on the first persist at which at least 256 <i>embeddings</i>
     * exist - entities without one are skipped, so an index of 256 entities of which some have no
     * vector does not yet qualify. Below that threshold the graph is written uncompressed and
     * training is reattempted on the next persist that follows a graph-affecting change, or in a
     * later session. An unchanged index is deliberately not retried, since that would rebuild the
     * whole graph on every idle persist.
     * Once trained, the codebook is written into the graph header of every segment that carries the
     * fused codes and recovered from them on every subsequent load, so it is not retrained while
     * such a segment exists.
     * <p>
     * Requires {@link #onDisk()} to be true.
     *
     * @return true if PQ compression is enabled (default: false)
     * @see #pqSubspaces()
     * @see #onDisk()
     * @deprecated use {@link #approximateScoring()} instead; this returns
     *             {@code approximateScoring() == ApproximateScoring.FUSED_PQ}
     */
    @Deprecated
    public boolean enablePqCompression();

    /**
     * Returns the number of PQ subspaces (M parameter).
     * <p>
     * Product Quantization divides each vector into M subspaces, each encoded
     * with 256 centroids (8 bits). More subspaces means:
     * <ul>
     *   <li><b>Higher accuracy</b>: Finer-grained quantization, so fewer candidates need reranking</li>
     *   <li><b>Slower traversal</b>: Under {@code FusedPQ} each node stores the codes of all its
     *       neighbours, so a node costs {@code M * maxDegree} bytes - not {@code M} - and every hop
     *       reads that whole block</li>
     * </ul>
     * <p>
     * The vector dimension must be evenly divisible by an explicitly configured value. The
     * automatic value is not subject to that rule: JVector distributes any remainder across the
     * subvectors and only requires the count to be at most the dimension.
     * <p>
     * <b>Recommendation:</b> the auto default of dimension/4 is the safe choice - measured recall@10
     * at the default {@link #minSearchBeamWidth()} of 100 is within noise of exact search. Lowering
     * M makes hops cheaper but costs real recall (dimension/16 measured ~0.74 at that beam width),
     * and the exact rerank does <b>not</b> compensate: it only reorders candidates that traversal
     * already found. A wider {@link #minSearchBeamWidth()} does - dimension/16 at beam 400 measured
     * ~0.98, at a quarter of the per-node cost. Lower M and widen the beam together, or leave both
     * at their defaults.
     *
     * @return the number of PQ subspaces, or 0 for auto-calculation (dimension/4)
     * @see #enablePqCompression()
     */
    public int pqSubspaces();

    /**
     * Returns whether background persistence is enabled.
     * <p>
     * Background persistence is enabled when {@link #persistenceIntervalMs()} is greater than 0.
     * When enabled, the index will persist changes to disk in a background thread
     * at regular intervals, rather than blocking the main thread. This allows
     * add/remove operations to complete immediately while persistence happens
     * asynchronously.
     * <p>
     * Requires {@link #onDisk()} to be true.
     *
     * @return true if background persistence is enabled ({@code persistenceIntervalMs > 0})
     * @see #persistenceIntervalMs()
     * @see #minChangesBetweenPersists()
     */
    public default boolean backgroundPersistence()
    {
        return this.persistenceIntervalMs() > 0;
    }

    /**
     * Returns the interval in milliseconds between background persistence attempts.
     * <p>
     * A value greater than 0 enables background persistence. A value of 0 disables it.
     * <p>
     * The background persistence thread will check for dirty changes at this interval
     * and persist them if the dirty threshold ({@link #minChangesBetweenPersists()})
     * has been met.
     * <p>
     * Lower values provide more frequent persistence (better durability) but may
     * impact performance. Higher values reduce I/O but increase potential data loss
     * on crash.
     *
     * @return the persistence interval in milliseconds (default: 0 = disabled)
     * @see #backgroundPersistence()
     */
    public long persistenceIntervalMs();

    /**
     * Returns whether to persist pending changes on shutdown.
     * <p>
     * When enabled and {@link #onDisk()} is true, the index will persist any
     * pending changes when {@code close()} is called, ensuring all changes are
     * durable before shutdown completes. This applies regardless of whether
     * {@link #backgroundPersistence()} is enabled — on-disk indices configured
     * without background persistence are also flushed on close.
     *
     * @return true if persist-on-shutdown is enabled (default: true)
     * @see #onDisk()
     * @see #backgroundPersistence()
     */
    public boolean persistOnShutdown();

    /**
     * Returns the maximum time in milliseconds the final shutdown work may run on the background
     * task executor before it is aborted so application shutdown can proceed.
     * <p>
     * On {@code close()}, the final shutdown work — draining pending indexing operations and, if
     * enabled, optimizing and persisting — runs on the background executor and is awaited for this
     * long. If it does not finish in time, the executor is interrupted and shutdown continues. The
     * dominant cost is the persist; because {@link #onDisk()} indices write their graph atomically
     * and self-heal from the store on the next load, an aborted persist never loses data or leaves a
     * torn file — it simply degrades to a rebuild on the next boot.
     * <p>
     * The shutdown persist of an on-disk index writes the in-memory graph as one more segment and
     * never rebuilds, so this timeout mainly bounds the write of a large in-memory graph or an
     * unexpectedly slow drain/optimize.
     *
     * @return the shutdown work timeout in milliseconds (default: 30000)
     * @see #persistOnShutdown()
     * @see #optimizeOnShutdown()
     */
    public long shutdownPersistTimeoutMillis();

    /**
     * Returns the minimum number of changes required before triggering persistence.
     * <p>
     * This provides debouncing to avoid excessive disk I/O for small batches of changes.
     * The background persistence thread will only persist if at least this many changes
     * have accumulated since the last persistence.
     * <p>
     * Set to 0 or 1 to persist on every interval regardless of change count.
     *
     * @return the minimum changes threshold (default: 100)
     * @see #backgroundPersistence()
     * @see #persistenceIntervalMs()
     */
    public int minChangesBetweenPersists();

    /**
     * Returns how many nodes the in-memory graph of an on-disk index must hold before a background
     * persist writes it to disk as a new segment.
     * <p>
     * A persist of an on-disk index freezes the in-memory graph, which holds the changes since the
     * previous persist, and writes it as one more immutable segment while a fresh in-memory graph
     * takes new mutations. Below this threshold a background persist leaves the in-memory graph
     * where it is and lets it grow: every flush adds a segment, and a crash makes the next start
     * rebuild the graph from the vector file anyway, whenever the last flush was, so frequent small
     * flushes add segments without making anything more durable. An explicit
     * {@code persistToDisk()} and the persist on shutdown write whatever changed, regardless of this
     * threshold. So does a background persist while the in-memory graph is empty: with no segment to
     * write, it commits only the manifest, which records the deletions and the entities added without
     * a vector.
     * <p>
     * Keep it large. A small segment can be too small for a hierarchical graph, and a flat one
     * cannot be traversed on fused PQ codes, so it is searched exactly.
     * <p>
     * Only relevant for {@link #onDisk()} indices. Defaults to {@code 100_000} for a configuration
     * stored by a version that did not have it.
     *
     * @return the flush threshold in nodes (default: 100000)
     * @see #backgroundPersistence()
     * @see #maxSegments()
     */
    public default int flushThreshold()
    {
        return Builder.Default.DEFAULT_FLUSH_THRESHOLD;
    }

    /**
     * Returns the most segments an on-disk index keeps before a persist consolidates them into one.
     * <p>
     * Each flush (see {@link #flushThreshold()}) adds a segment, and every search visits all of
     * them. Background merges (see {@link #segmentMergeFactor()}) keep the count well below this
     * number, and merge the smallest segments ahead of their tier once the next flush would reach it.
     * Only when a persist would exceed it all the same, because the merges fell behind, does it rebuild
     * the whole index from the vector file into a single segment instead, which blocks mutations
     * and searches for the duration of the rebuild. The upper bound of {@code 240} leaves room in the
     * 255 owner slots for the in-memory graphs, a merge in flight and segments a running search still
     * holds.
     * <p>
     * Only relevant for {@link #onDisk()} indices. Defaults to {@code 32} for a configuration stored
     * by a version that did not have it.
     *
     * @return the maximum segment count, between 2 and 240 (default: 32)
     * @see #flushThreshold()
     */
    public default int maxSegments()
    {
        return Builder.Default.DEFAULT_MAX_SEGMENTS;
    }

    /**
     * Returns how many segments of similar size a background merge of an on-disk index combines into
     * one.
     * <p>
     * Segments are sorted into size tiers: tier {@code t} holds the segments with fewer than
     * {@code flushThreshold * segmentMergeFactor^(t+1)} live nodes. Once a tier holds this many
     * segments, a background merge rebuilds them, from the vector file and in parallel, into one
     * segment of the next tier, while searches and mutations go on. So each vector is rewritten about
     * once per tier, and the index keeps fewer than {@code segmentMergeFactor} segments per tier. A
     * larger factor merges less often and leaves more segments for every search to visit. Segments
     * written for another format or codebook than the configured one are merged first, on their own.
     * <p>
     * Capped at {@link #maxSegments()} where that is smaller. Only relevant for {@link #onDisk()}
     * indices. Defaults to {@code 4} for a configuration stored by a version that did not have it.
     *
     * @return the merge factor, between 2 and 240 (default: 4)
     * @see #maxSegments()
     * @see #flushThreshold()
     */
    public default int segmentMergeFactor()
    {
        return Builder.Default.DEFAULT_SEGMENT_MERGE_FACTOR;
    }

    /**
     * Returns the interval at which the vector file of the index is checkpointed.
     * <p>
     * Every index keeps its vectors in a memory-mapped vector file next to its segments, or in a private
     * temporary directory for an in-memory index. That file is a cache: the entities are the source of truth,
     * reached through the {@link Vectorizer}, so nothing about it is made durable when the store commits. A
     * checkpoint forces the chunks written since the previous one and marks them as safe. After a crash only
     * the chunks written since the last checkpoint are looked at again: after a killed process their slots
     * written after the last store are re-vectorized, after a reboot every one of their ordinals is. A shorter
     * interval costs more header writes at runtime and less recovery after a crash.
     * <p>
     * The vector file must stay with the store it belongs to. Restoring it separately is not supported;
     * deleting it only makes the next start re-vectorize every entity.
     * <p>
     * Defaults to {@code 30_000} for a configuration stored by a version that did not have it.
     *
     * @return the checkpoint interval in milliseconds (default: 30000)
     * @see #vectorChunkBytes()
     */
    public default long vectorCheckpointIntervalMs()
    {
        return Builder.Default.DEFAULT_VECTOR_CHECKPOINT_INTERVAL_MS;
    }

    /**
     * Returns the size of one chunk of the vector file, in bytes.
     * <p>
     * The vector file grows chunk by chunk, each one written out in full when it is created, so a full disk
     * fails as an exception rather than as a crash on a page the file system cannot back. A chunk is also the
     * unit of recovery: a crash makes the next start look again at every chunk written since the last
     * checkpoint (see {@link #vectorCheckpointIntervalMs()}). Changing the size of an existing index makes its
     * next start re-vectorize every entity.
     * <p>
     * A chunk holds at least {@link VectorFile#MIN_SLOTS_PER_CHUNK} vectors, which keeps the chunk table small
     * however sparse the entity ids are. Defaults to 64 MB, or to that minimum where a large dimension needs
     * more, also for a configuration stored by a version that did not have it.
     *
     * @return the chunk size in bytes (default: 67108864, or more for a large dimension)
     */
    public default long vectorChunkBytes()
    {
        return Math.max(
            Builder.Default.DEFAULT_VECTOR_CHUNK_BYTES,
            Builder.Default.minimumChunkBytes(this.dimension())
        );
    }

    /**
     * Returns whether background optimization is enabled.
     * <p>
     * Background optimization is enabled when {@link #optimizationIntervalMs()} is greater than 0.
     * When enabled, the index will run {@link VectorIndex#optimize()} in a background thread
     * at regular intervals, which purges removed entities from the graph and removes excess
     * neighbors accumulated during construction.
     * This reduces memory usage and improves query latency.
     * <p>
     * This applies to in-memory indices only. For an on-disk index the optimization itself does
     * nothing: every flush purges and trims the graph it writes, so there is nothing for a separate
     * pass to do.
     * <p>
     * Background optimization is independent of background persistence - both can be
     * enabled separately.
     * <p>
     * <b>Note:</b> Graph modification is NOT thread-safe, so optimization
     * will briefly block add/remove/search operations while running.
     *
     * @return true if background optimization is enabled ({@code optimizationIntervalMs > 0})
     * @see #optimizationIntervalMs()
     * @see #minChangesBetweenOptimizations()
     */
    public default boolean backgroundOptimization()
    {
        return this.optimizationIntervalMs() > 0;
    }

    /**
     * Returns the interval in milliseconds between background optimization attempts.
     * <p>
     * A value greater than 0 enables background optimization. A value of 0 disables it.
     * <p>
     * The background optimization thread will check for dirty changes at this interval
     * and optimize if the dirty threshold ({@link #minChangesBetweenOptimizations()})
     * has been met.
     * <p>
     * This should typically be longer than {@link #persistenceIntervalMs()} since
     * optimization (cleanup) is more expensive than persistence.
     *
     * @return the optimization interval in milliseconds (default: 0 = disabled)
     * @see #backgroundOptimization()
     */
    public long optimizationIntervalMs();

    /**
     * Returns the minimum number of changes required before triggering optimization.
     * <p>
     * This provides debouncing to avoid excessive optimization for small batches of changes.
     * The background optimization thread will only optimize if at least this many changes
     * have accumulated since the last optimization.
     * <p>
     * This should typically be higher than {@link #minChangesBetweenPersists()} since
     * optimization is more expensive.
     * <p>
     * Set to 0 or 1 to optimize on every interval regardless of change count.
     *
     * @return the minimum changes threshold (default: 1000)
     * @see #backgroundOptimization()
     * @see #optimizationIntervalMs()
     */
    public int minChangesBetweenOptimizations();

    /**
     * Returns whether to optimize pending changes on shutdown.
     * <p>
     * When enabled and {@link #backgroundOptimization()} is true, the index will
     * run cleanup on any pending changes when {@code close()} is called, ensuring
     * the graph is optimized before shutdown completes.
     * <p>
     * This is disabled by default to avoid blocking {@code close()} in production.
     *
     * @return true if optimize-on-shutdown is enabled (default: false)
     * @see #backgroundOptimization()
     */
    public boolean optimizeOnShutdown();

    /**
     * Returns whether eventual indexing mode is enabled.
     * <p>
     * When enabled, expensive HNSW graph mutations (add, update, remove) are
     * deferred to a background thread. The vector file is still updated
     * synchronously, but graph construction happens asynchronously.
     * <p>
     * This reduces the latency of mutation operations at the cost of
     * eventual consistency — search results may not immediately reflect the
     * most recent mutations.
     * <p>
     * Pending operations are applied to the graph before {@code optimize()} and
     * {@code close()}. {@code persistToDisk()} does not drain the queue: it moves the
     * pending operations into the graph it freezes, and the flush applies them there.
     *
     * @return true if eventual indexing is enabled (default: false)
     */
    public boolean eventualIndexing();

    /**
     * Returns whether parallel writing is used for on-disk index persistence.
     * <p>
     * When enabled, the on-disk graph writer uses parallel direct buffers and
     * multiple worker threads (one per available processor) to write the index
     * concurrently. This significantly speeds up persistence for large indices.
     * <p>
     * When disabled, a sequential single-threaded writer is used, which may be
     * preferable in resource-constrained environments or when writing smaller indices.
     * <p>
     * Only applies when {@link #onDisk()} is true.
     *
     * @return true if parallel on-disk writing is enabled (default: false)
     * @see #onDisk()
     */
    public boolean parallelOnDiskWrite();


    /**
     * Creates a new builder for constructing a {@link VectorIndexConfiguration}.
     *
     * @return a new configuration builder
     */
    public static Builder builder()
    {
        return new Builder.Default();
    }


    // ========================================================================
    // Factory Methods for Common Use Cases
    // ========================================================================

    /**
     * Creates a configuration optimized for small datasets (&lt;10K vectors).
     * <p>
     * Uses lower parameter values that are sufficient for small datasets while
     * providing fast index construction and low memory usage.
     * <p>
     * <b>Configuration:</b> maxDegree=12, beamWidth=75, onDisk=false
     *
     * @param dimension the vector dimension (must be positive)
     * @return a ready-to-use configuration for small datasets
     * @see #forSmallDataset(int, VectorSimilarityFunction)
     * @see #builderForSmallDataset(int)
     */
    public static VectorIndexConfiguration forSmallDataset(final int dimension)
    {
        return forSmallDataset(dimension, VectorSimilarityFunction.COSINE);
    }

    /**
     * Creates a configuration optimized for small datasets (&lt;10K vectors) with a custom similarity function.
     * <p>
     * Uses lower parameter values that are sufficient for small datasets while
     * providing fast index construction and low memory usage.
     * <p>
     * <b>Configuration:</b> maxDegree=12, beamWidth=75, onDisk=false
     *
     * @param dimension the vector dimension (must be positive)
     * @param similarityFunction the similarity function to use for comparing vectors
     * @return a ready-to-use configuration for small datasets
     * @see #forSmallDataset(int)
     * @see #builderForSmallDataset(int)
     */
    public static VectorIndexConfiguration forSmallDataset(final int dimension, final VectorSimilarityFunction similarityFunction)
    {
        return builderForSmallDataset(dimension)
            .similarityFunction(similarityFunction)
            .build();
    }

    /**
     * Creates a builder pre-configured for small datasets (&lt;10K vectors).
     * <p>
     * Use this when you need to customize additional parameters beyond the defaults
     * for small datasets.
     * <p>
     * <b>Pre-configured values:</b> maxDegree=12, beamWidth=75, onDisk=false
     *
     * @param dimension the vector dimension (must be positive)
     * @return a builder pre-configured for small datasets
     * @see #forSmallDataset(int)
     */
    public static Builder builderForSmallDataset(final int dimension)
    {
        return builder()
            .dimension(dimension)
            .maxDegree(12)
            .beamWidth(75)
            .onDisk(false);
    }

    /**
     * Creates an in-memory configuration optimized for medium datasets (10K-1M vectors).
     * <p>
     * Uses balanced parameter values that provide good recall with reasonable
     * index construction time.
     * <p>
     * <b>Configuration:</b> maxDegree=24, beamWidth=150, onDisk=false
     *
     * @param dimension the vector dimension (must be positive)
     * @return a ready-to-use in-memory configuration for medium datasets
     * @see #forMediumDataset(int, Path)
     * @see #builderForMediumDataset(int)
     */
    public static VectorIndexConfiguration forMediumDataset(final int dimension)
    {
        return builderForMediumDataset(dimension).build();
    }

    /**
     * Creates an on-disk configuration optimized for medium datasets (10K-1M vectors).
     * <p>
     * Uses balanced parameter values with on-disk storage and background persistence
     * for durability.
     * <p>
     * <b>Configuration:</b> maxDegree=24, beamWidth=150, onDisk=true, persistenceIntervalMs=30000
     *
     * @param dimension the vector dimension (must be positive)
     * @param indexDirectory the directory where index files will be stored
     * @return a ready-to-use on-disk configuration for medium datasets
     * @see #forMediumDataset(int)
     * @see #builderForMediumDataset(int)
     */
    public static VectorIndexConfiguration forMediumDataset(final int dimension, final Path indexDirectory)
    {
        return builderForMediumDataset(dimension)
            .onDisk(true)
            .indexDirectory(indexDirectory)
            .persistenceIntervalMs(30_000L)
            .build();
    }

    /**
     * Creates a builder pre-configured for medium datasets (10K-1M vectors).
     * <p>
     * Use this when you need to customize additional parameters beyond the defaults
     * for medium datasets.
     * <p>
     * <b>Pre-configured values:</b> maxDegree=24, beamWidth=150
     *
     * @param dimension the vector dimension (must be positive)
     * @return a builder pre-configured for medium datasets
     * @see #forMediumDataset(int)
     * @see #forMediumDataset(int, Path)
     */
    public static Builder builderForMediumDataset(final int dimension)
    {
        return builder()
            .dimension(dimension)
            .maxDegree(24)
            .beamWidth(150);
    }

    /**
     * Creates an on-disk configuration optimized for large datasets (&gt;1M vectors) with PQ compression.
     * <p>
     * Uses higher parameter values for better recall at scale, with on-disk storage, PQ compression
     * for faster traversal, and background persistence. The optimization interval is set as well,
     * but optimization does nothing for an on-disk index (see {@link #backgroundOptimization()}).
     * Note that PQ trades disk space
     * for search speed - it makes the graph file larger, not smaller; see
     * {@link #enablePqCompression()}. Use {@link #forLargeDataset(int, Path, boolean)} with
     * {@code false} to opt out.
     * <p>
     * <b>Configuration:</b> maxDegree=32, beamWidth=300, onDisk=true, approximateScoring=FUSED_PQ,
     * persistenceIntervalMs=30000, optimizationIntervalMs=60000
     *
     * @param dimension the vector dimension (must be positive)
     * @param indexDirectory the directory where index files will be stored
     * @return a ready-to-use configuration for large datasets
     * @see #forLargeDataset(int, Path, boolean)
     * @see #builderForLargeDataset(int, Path)
     */
    public static VectorIndexConfiguration forLargeDataset(final int dimension, final Path indexDirectory)
    {
        return forLargeDataset(dimension, indexDirectory, true);
    }

    /**
     * Creates an on-disk configuration optimized for large datasets (&gt;1M vectors).
     * <p>
     * Uses higher parameter values for better recall at scale, with on-disk storage
     * and background persistence (the optimization interval is set as well, but optimization does
     * nothing for an on-disk index). PQ compression can be optionally enabled to
     * speed up traversal; note that it makes the graph file larger, not smaller - see
     * {@link #enablePqCompression()}.
     * <p>
     * <b>Configuration:</b> maxDegree=32, beamWidth=300, onDisk=true,
     * persistenceIntervalMs=30000, optimizationIntervalMs=60000
     *
     * @param dimension the vector dimension (must be positive)
     * @param indexDirectory the directory where index files will be stored
     * @param enableCompression true to enable PQ compression (faster traversal, larger graph file)
     * @return a ready-to-use configuration for large datasets
     * @see #forLargeDataset(int, Path)
     * @see #builderForLargeDataset(int, Path)
     */
    public static VectorIndexConfiguration forLargeDataset(final int dimension, final Path indexDirectory, final boolean enableCompression)
    {
        return builderForLargeDataset(dimension, indexDirectory)
            .enablePqCompression(enableCompression)
            .build();
    }

    /**
     * Creates a builder pre-configured for large datasets (&gt;1M vectors).
     * <p>
     * Use this when you need to customize additional parameters beyond the defaults
     * for large datasets. The builder is pre-configured with on-disk storage and
     * background persistence enabled. The optimization interval is set as well, but optimization
     * does nothing for an on-disk index.
     * <p>
     * <b>Pre-configured values:</b> maxDegree=32, beamWidth=300, onDisk=true,
     * persistenceIntervalMs=30000, optimizationIntervalMs=60000
     *
     * @param dimension the vector dimension (must be positive)
     * @param indexDirectory the directory where index files will be stored
     * @return a builder pre-configured for large datasets
     * @see #forLargeDataset(int, Path)
     * @see #forLargeDataset(int, Path, boolean)
     */
    public static Builder builderForLargeDataset(final int dimension, final Path indexDirectory)
    {
        return builder()
            .dimension(dimension)
            .maxDegree(32)
            .beamWidth(300)
            .onDisk(true)
            .indexDirectory(indexDirectory)
            .persistenceIntervalMs(30_000L)
            .optimizationIntervalMs(60_000L);
    }

    /**
     * Creates an on-disk configuration for large datasets (&gt;1M vectors) that minimizes the size of
     * the index on disk.
     * <p>
     * Same tuning as {@link #forLargeDataset(int, Path)}, but the graph stores quantized vectors and
     * carries no fused PQ codes. Both parts matter, and the second is the larger one: fused codes are
     * duplicated {@code maxDegree} times per node, so at {@code dimension=768} and the pre-configured
     * {@code maxDegree=32} they alone cost 6144 bytes against the 800 the quantized vector occupies.
     *
     * <table border="1">
     *   <tr><th>Configuration</th><th>Bytes per node</th></tr>
     *   <tr><td>{@link #forLargeDataset(int, Path)} - INLINE + FUSED_PQ</td><td>~9,352</td></tr>
     *   <tr><td>INLINE + NONE, for reference</td><td>~3,208</td></tr>
     *   <tr><td>this preset - NVQ + NONE</td><td>~936</td></tr>
     * </table>
     * <p>
     * <b>What it gives up is traversal speed, not accuracy.</b> Without the fused codes each hop
     * reads its candidates' own stored vectors rather than one contiguous block - though those
     * vectors are now 800 bytes rather than 3072, so a hop is far cheaper than it would be on an
     * uncompressed graph. Reranking stays exact, as it does under every setting a live index
     * reaches: the search path reranks against the vectors in the index's vector file. Use
     * {@link #forLargeDataset(int, Path)} when query latency matters more than footprint.
     * <p>
     * <b>Configuration:</b> maxDegree=32, beamWidth=300, onDisk=true, vectorStorage=NVQ,
     * approximateScoring=NONE, parallelOnDiskWrite=true, persistenceIntervalMs=30000,
     * optimizationIntervalMs=60000
     *
     * @param dimension the vector dimension (must be positive)
     * @param indexDirectory the directory where index files will be stored
     * @return a ready-to-use configuration for large datasets with a compact on-disk index
     * @see #builderForCompactLargeDataset(int, Path)
     * @see #forLargeDataset(int, Path)
     */
    public static VectorIndexConfiguration forCompactLargeDataset(final int dimension, final Path indexDirectory)
    {
        return builderForCompactLargeDataset(dimension, indexDirectory).build();
    }

    /**
     * Creates a builder pre-configured for large datasets (&gt;1M vectors) with a compact on-disk
     * index.
     * <p>
     * Use this when you need to customize additional parameters beyond the defaults. See
     * {@link #forCompactLargeDataset(int, Path)} for the size and recall trade-off this preset
     * makes.
     * <p>
     * <b>Pre-configured values:</b> maxDegree=32, beamWidth=300, onDisk=true, vectorStorage=NVQ,
     * approximateScoring=NONE, parallelOnDiskWrite=true, persistenceIntervalMs=30000,
     * optimizationIntervalMs=60000
     *
     * @param dimension the vector dimension (must be positive)
     * @param indexDirectory the directory where index files will be stored
     * @return a builder pre-configured for large datasets with a compact on-disk index
     * @see #forCompactLargeDataset(int, Path)
     * @see #builderForLargeDataset(int, Path)
     */
    public static Builder builderForCompactLargeDataset(final int dimension, final Path indexDirectory)
    {
        return builderForLargeDataset(dimension, indexDirectory)
            .vectorStorage(VectorStorage.NVQ)
            .approximateScoring(ApproximateScoring.NONE)
            // Quantizing fits a nonlinearity per vector at write time, which costs about 3x the
            // persist wall time sequentially and about 1.2x under the parallel writer, because the
            // per-node encode then runs on the writer's worker threads. A preset that chose the
            // quantized format and left this off would hand out the cost without the mitigation.
            .parallelOnDiskWrite(true);
    }

    /**
     * Creates an in-memory configuration optimized for high precision requirements.
     * <p>
     * Uses maximum parameter values to achieve the highest possible recall,
     * with approximate scoring disabled to avoid any precision loss.
     * <p>
     * <b>Configuration:</b> maxDegree=56, beamWidth=450, onDisk=false
     *
     * @param dimension the vector dimension (must be positive)
     * @return a ready-to-use high-precision configuration
     * @see #forHighPrecision(int, Path)
     * @see #builderForHighPrecision(int)
     */
    public static VectorIndexConfiguration forHighPrecision(final int dimension)
    {
        return builderForHighPrecision(dimension).build();
    }

    /**
     * Creates an on-disk configuration optimized for high precision requirements.
     * <p>
     * Uses maximum parameter values to achieve the highest possible recall,
     * with approximate scoring disabled to avoid any precision loss.
     * <p>
     * <b>Configuration:</b> maxDegree=56, beamWidth=450, onDisk=true,
     * approximateScoring=NONE, persistenceIntervalMs=30000
     *
     * @param dimension the vector dimension (must be positive)
     * @param indexDirectory the directory where index files will be stored
     * @return a ready-to-use high-precision on-disk configuration
     * @see #forHighPrecision(int)
     * @see #builderForHighPrecision(int)
     */
    public static VectorIndexConfiguration forHighPrecision(final int dimension, final Path indexDirectory)
    {
        return builderForHighPrecision(dimension)
            .onDisk(true)
            .indexDirectory(indexDirectory)
            .persistenceIntervalMs(30_000L)
            .build();
    }

    /**
     * Creates a builder pre-configured for high precision requirements.
     * <p>
     * Use this when you need to customize additional parameters beyond the defaults
     * for high precision use cases. Approximate scoring is explicitly disabled as it
     * reduces precision.
     * <p>
     * <b>Pre-configured values:</b> maxDegree=56, beamWidth=450, vectorStorage=INLINE, approximateScoring=NONE
     *
     * @param dimension the vector dimension (must be positive)
     * @return a builder pre-configured for high precision
     * @see #forHighPrecision(int)
     * @see #forHighPrecision(int, Path)
     */
    public static Builder builderForHighPrecision(final int dimension)
    {
        return builder()
            .dimension(dimension)
            .maxDegree(56)
            .beamWidth(450)
            .vectorStorage(VectorStorage.INLINE)
            .approximateScoring(ApproximateScoring.NONE);
    }


    /**
     * Builder for constructing {@link VectorIndexConfiguration} instances.
     * <p>
     * All parameters have sensible defaults except {@link #dimension(int)}, which should
     * be set to match your embedding model's output dimension.
     *
     * <h2>Default Values</h2>
     * <ul>
     *   <li>{@code dimension}: 3 (placeholder - should be set explicitly)</li>
     *   <li>{@code similarityFunction}: {@link VectorSimilarityFunction#COSINE}</li>
     *   <li>{@code maxDegree}: 16</li>
     *   <li>{@code beamWidth}: 100</li>
     *   <li>{@code minSearchBeamWidth}: 100</li>
     *   <li>{@code neighborOverflow}: 1.2</li>
     *   <li>{@code alpha}: 1.2</li>
     * </ul>
     */
    public static interface Builder
    {
        /**
         * Sets the vector dimension.
         *
         * @param dimension the dimensionality of vectors (must be positive)
         * @return this builder for method chaining
         * @throws IllegalArgumentException if dimension is not positive
         * @see VectorIndexConfiguration#dimension()
         */
        public Builder dimension(int dimension);

        /**
         * Sets the similarity function for comparing vectors.
         *
         * @param similarityFunction the similarity function to use
         * @return this builder for method chaining
         * @throws NullPointerException if similarityFunction is null
         * @see VectorIndexConfiguration#similarityFunction()
         */
        public Builder similarityFunction(VectorSimilarityFunction similarityFunction);

        /**
         * Sets the maximum number of connections per node in the HNSW graph.
         *
         * @param maxDegree the maximum degree (must be positive, typically 8-64)
         * @return this builder for method chaining
         * @throws IllegalArgumentException if maxDegree is not positive
         * @see VectorIndexConfiguration#maxDegree()
         */
        public Builder maxDegree(int maxDegree);

        /**
         * Sets the beam width for index construction.
         *
         * @param beamWidth the construction beam width (must be positive, typically 100-500)
         * @return this builder for method chaining
         * @throws IllegalArgumentException if beamWidth is not positive
         * @see VectorIndexConfiguration#beamWidth()
         */
        public Builder beamWidth(int beamWidth);

        /**
         * Sets the minimum search beam width (HNSW <i>efSearch</i> floor).
         * <p>
         * Pass {@code 1} to disable the floor so the beam width equals the requested {@code k}.
         *
         * @param minSearchBeamWidth the minimum search beam width (must be positive)
         * @return this builder for method chaining
         * @throws IllegalArgumentException if minSearchBeamWidth is not positive
         * @see VectorIndexConfiguration#minSearchBeamWidth()
         */
        public Builder minSearchBeamWidth(int minSearchBeamWidth);

        /**
         * Sets the neighbor overflow factor for construction.
         *
         * @param neighborOverflow the overflow factor (must be positive, typically 1.2-1.5)
         * @return this builder for method chaining
         * @throws IllegalArgumentException if neighborOverflow is not positive
         * @see VectorIndexConfiguration#neighborOverflow()
         */
        public Builder neighborOverflow(float neighborOverflow);

        /**
         * Sets the alpha parameter for RNG pruning.
         *
         * @param alpha the pruning parameter (must be positive, typically 1.0-1.4)
         * @return this builder for method chaining
         * @throws IllegalArgumentException if alpha is not positive
         * @see VectorIndexConfiguration#alpha()
         */
        public Builder alpha(float alpha);

        /**
         * Enables or disables on-disk graph storage.
         * <p>
         * When enabled, the HNSW graph is stored on disk. Requires {@link #indexDirectory(Path)}
         * to be set.
         *
         * @param onDisk true to enable on-disk mode
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#onDisk()
         */
        public Builder onDisk(boolean onDisk);

        /**
         * Sets the directory where index files are stored.
         * <p>
         * Required when {@link #onDisk(boolean)} is true.
         *
         * @param indexDirectory the directory path for index files
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#indexDirectory()
         */
        public Builder indexDirectory(Path indexDirectory);

        /**
         * Sets where index files are stored, as an {@link IndexLocation}.
         * <p>
         * {@link IndexLocation#Absolute(Path)} is the same as {@link #indexDirectory(Path)}.
         * {@link IndexLocation#Named(String)} stores only the name; the directory is looked up in
         * {@link IndexLocations} whenever the index is created or loaded, so a moved or copied storage
         * can be pointed at its index files by binding the name to their new directory.
         * <p>
         * A named location stores no directory: a library version without named locations can still
         * read the storage and search this index, but rebuilds its graph in memory on every start and
         * fails to write it to disk.
         * <p>
         * Either this or {@link #indexDirectory(Path)} is required when {@link #onDisk(boolean)} is
         * true.
         *
         * @param indexLocation the location of the index files
         * @return this builder for method chaining
         * @throws UnsupportedOperationException for a named location, if this builder implementation
         *         predates named locations (the default implementation supports absolute ones only)
         * @see VectorIndexConfiguration#indexLocation()
         */
        public default Builder indexLocation(final IndexLocation indexLocation)
        {
            if(notNull(indexLocation).isNamed())
            {
                throw new UnsupportedOperationException(
                    this.getClass().getName() + " does not support named index locations."
                );
            }
            return this.indexDirectory(indexLocation.directory());
        }

        /**
         * Sets how the on-disk graph stores its own copy of each vector.
         * <p>
         * Requires {@link #onDisk(boolean)} to be true for any value other than
         * {@link VectorStorage#INLINE}.
         *
         * <p>
         * A {@code default} so that a builder implementation written against an earlier version of
         * this interface still compiles and links. It throws rather than ignoring the call, because
         * a builder that cannot carry the setting must not report success and then hand back a
         * configuration that silently says something else.
         *
         * @param vectorStorage the storage mode, never null
         * @return this builder for method chaining
         * @throws UnsupportedOperationException from the default implementation
         * @see VectorIndexConfiguration#vectorStorage()
         */
        public default Builder vectorStorage(final VectorStorage vectorStorage)
        {
            throw new UnsupportedOperationException("vectorStorage is not supported by " + this.getClass().getName());
        }

        /**
         * Sets how graph traversal scores the candidates it visits.
         * <p>
         * {@link ApproximateScoring#FUSED_PQ} requires {@link #onDisk(boolean)}, since its codes are
         * written into the graph file. {@link ApproximateScoring#PQ_IN_MEMORY} does not - it keeps
         * its codes in heap - but on an in-memory index it needs a scheduled optimization, which is
         * where the switch to compressed scoring happens.
         *
         * <p>
         * A {@code default} so that a builder implementation written against an earlier version of
         * this interface still compiles and links. It throws rather than ignoring the call, because
         * a builder that cannot carry the setting must not report success and then hand back a
         * configuration that silently says something else.
         *
         * @param approximateScoring the scoring mode, never null
         * @return this builder for method chaining
         * @throws UnsupportedOperationException from the default implementation
         * @see VectorIndexConfiguration#approximateScoring()
         */
        public default Builder approximateScoring(final ApproximateScoring approximateScoring)
        {
            throw new UnsupportedOperationException("approximateScoring is not supported by " + this.getClass().getName());
        }

        /**
         * Sets the number of NVQ subvectors.
         * <p>
         * Must not exceed the dimension. Use 0 for auto-calculation (1), which is the
         * recommended setting - see {@link VectorIndexConfiguration#nvqSubvectors()} for why a
         * higher count is usually pure overhead.
         *
         * <p>
         * A {@code default} so that a builder implementation written against an earlier version of
         * this interface still compiles and links. It throws rather than ignoring the call, because
         * a builder that cannot carry the setting must not report success and then hand back a
         * configuration that silently says something else.
         *
         * @param nvqSubvectors the number of subvectors, or 0 for auto
         * @return this builder for method chaining
         * @throws UnsupportedOperationException from the default implementation
         * @see VectorIndexConfiguration#nvqSubvectors()
         */
        public default Builder nvqSubvectors(final int nvqSubvectors)
        {
            throw new UnsupportedOperationException("nvqSubvectors is not supported by " + this.getClass().getName());
        }

        /**
         * Enables or disables Product Quantization compression.
         * <p>
         * <b>Deprecated</b> in favour of {@link #approximateScoring(ApproximateScoring)}. This is a
         * literal delegate for {@code approximateScoring(b ? FUSED_PQ : NONE)} writing the very same
         * field, so mixing the two on one builder is harmless: the last call wins, whichever it was.
         * <p>
         * Requires {@link #onDisk(boolean)} to be true.
         *
         * @param enablePqCompression true to enable PQ compression
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#enablePqCompression()
         * @deprecated use {@link #approximateScoring(ApproximateScoring)} instead
         */
        @Deprecated
        public Builder enablePqCompression(boolean enablePqCompression);

        /**
         * Sets the number of PQ subspaces.
         * <p>
         * The vector dimension must be evenly divisible by this value. Use 0 for auto-calculation
         * (dimension/4), which is not subject to that rule - JVector distributes any remainder
         * across the subvectors.
         *
         * @param pqSubspaces the number of subspaces, or 0 for auto
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#pqSubspaces()
         */
        public Builder pqSubspaces(int pqSubspaces);

        /**
         * Sets the interval between background persistence attempts.
         * A value greater than 0 enables background persistence. A value of 0 disables it.
         *
         * @param persistenceIntervalMs the interval in milliseconds (must be non-negative)
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#persistenceIntervalMs()
         */
        public Builder persistenceIntervalMs(long persistenceIntervalMs);

        /**
         * Enables or disables persist-on-shutdown behavior.
         *
         * @param persistOnShutdown true to persist pending changes on shutdown
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#persistOnShutdown()
         */
        public Builder persistOnShutdown(boolean persistOnShutdown);

        /**
         * Sets the maximum time the final shutdown work (drain, optimize, persist) may run before it
         * is aborted so shutdown can proceed.
         *
         * @param shutdownPersistTimeoutMillis the timeout in milliseconds (must be positive)
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#shutdownPersistTimeoutMillis()
         */
        public Builder shutdownPersistTimeoutMillis(long shutdownPersistTimeoutMillis);

        /**
         * Sets the minimum number of changes required before triggering persistence.
         *
         * @param minChangesBetweenPersists the minimum changes threshold (must be non-negative)
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#minChangesBetweenPersists()
         */
        public Builder minChangesBetweenPersists(int minChangesBetweenPersists);

        /**
         * Sets how many nodes the in-memory graph of an on-disk index must hold before a background
         * persist writes it as a new segment.
         * <p>
         * A {@code default} so that a builder implementation written against an earlier version of
         * this interface still compiles and links. It throws rather than ignoring the call, because
         * a builder that cannot carry the setting must not report success and then hand back a
         * configuration that silently says something else.
         *
         * @param flushThreshold the threshold in nodes (must be positive)
         * @return this builder for method chaining
         * @throws UnsupportedOperationException from the default implementation
         * @see VectorIndexConfiguration#flushThreshold()
         */
        public default Builder flushThreshold(final int flushThreshold)
        {
            throw new UnsupportedOperationException("flushThreshold is not supported by " + this.getClass().getName());
        }

        /**
         * Sets the most segments an on-disk index keeps before a persist consolidates them into one.
         * <p>
         * A {@code default} for the same reason as {@link #flushThreshold(int)}.
         *
         * @param maxSegments the maximum segment count, between 2 and 240
         * @return this builder for method chaining
         * @throws UnsupportedOperationException from the default implementation
         * @see VectorIndexConfiguration#maxSegments()
         */
        public default Builder maxSegments(final int maxSegments)
        {
            throw new UnsupportedOperationException("maxSegments is not supported by " + this.getClass().getName());
        }

        /**
         * Sets how many segments of similar size a background merge combines into one.
         * <p>
         * A {@code default} for the same reason as {@link #flushThreshold(int)}.
         *
         * @param segmentMergeFactor the merge factor, between 2 and 240
         * @return this builder for method chaining
         * @throws UnsupportedOperationException from the default implementation
         * @see VectorIndexConfiguration#segmentMergeFactor()
         */
        public default Builder segmentMergeFactor(final int segmentMergeFactor)
        {
            throw new UnsupportedOperationException(
                "segmentMergeFactor is not supported by " + this.getClass().getName());
        }

        /**
         * Sets the interval at which the vector file is checkpointed.
         * <p>
         * A {@code default} for the same reason as {@link #flushThreshold(int)}.
         *
         * @param vectorCheckpointIntervalMs the interval in milliseconds (must be positive)
         * @return this builder for method chaining
         * @throws UnsupportedOperationException from the default implementation
         * @see VectorIndexConfiguration#vectorCheckpointIntervalMs()
         */
        public default Builder vectorCheckpointIntervalMs(final long vectorCheckpointIntervalMs)
        {
            throw new UnsupportedOperationException(
                "vectorCheckpointIntervalMs is not supported by " + this.getClass().getName());
        }

        /**
         * Sets the size of one chunk of the vector file.
         * <p>
         * A {@code default} for the same reason as {@link #flushThreshold(int)}.
         *
         * @param vectorChunkBytes the chunk size in bytes, enough for {@link VectorFile#MIN_SLOTS_PER_CHUNK} vectors
         * @return this builder for method chaining
         * @throws UnsupportedOperationException from the default implementation
         * @see VectorIndexConfiguration#vectorChunkBytes()
         */
        public default Builder vectorChunkBytes(final long vectorChunkBytes)
        {
            throw new UnsupportedOperationException(
                "vectorChunkBytes is not supported by " + this.getClass().getName());
        }

        /**
         * Sets the interval between background optimization attempts.
         * A value greater than 0 enables background optimization. A value of 0 disables it.
         *
         * @param optimizationIntervalMs the interval in milliseconds (must be non-negative)
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#optimizationIntervalMs()
         */
        public Builder optimizationIntervalMs(long optimizationIntervalMs);

        /**
         * Sets the minimum number of changes required before triggering optimization.
         *
         * @param minChangesBetweenOptimizations the minimum changes threshold (must be non-negative)
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#minChangesBetweenOptimizations()
         */
        public Builder minChangesBetweenOptimizations(int minChangesBetweenOptimizations);

        /**
         * Enables or disables optimize-on-shutdown behavior.
         *
         * @param optimizeOnShutdown true to optimize pending changes on shutdown
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#optimizeOnShutdown()
         */
        public Builder optimizeOnShutdown(boolean optimizeOnShutdown);

        /**
         * Enables or disables parallel writing for on-disk index persistence.
         * <p>
         * When enabled, uses multiple worker threads and parallel direct buffers
         * for faster disk writes. Only applies when {@link #onDisk(boolean)} is true.
         *
         * @param parallelOnDiskWrite true to enable parallel on-disk writing
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#parallelOnDiskWrite()
         */
        public Builder parallelOnDiskWrite(boolean parallelOnDiskWrite);

        /**
         * Enables or disables eventual indexing mode.
         * <p>
         * When enabled, HNSW graph mutations are deferred to a background thread,
         * reducing mutation latency at the cost of eventual consistency for searches.
         *
         * @param eventualIndexing true to enable eventual indexing
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#eventualIndexing()
         */
        public Builder eventualIndexing(boolean eventualIndexing);

        /**
         * Builds the configuration with the specified parameters.
         *
         * @return a new immutable {@link VectorIndexConfiguration}
         * @throws IllegalStateException if onDisk is true but neither indexDirectory nor an index location is set
         * @throws IllegalStateException if approximateScoring is FUSED_PQ but onDisk is false, or
         *         if it is PQ_IN_MEMORY without onDisk and no optimization is scheduled
         * @throws IllegalStateException if vectorStorage is not INLINE but onDisk is false
         * @throws IllegalStateException if persistenceIntervalMs > 0 but onDisk is false
         * @throws IllegalArgumentException if pqSubspaces > 0 and dimension is not divisible by pqSubspaces
         * @throws IllegalArgumentException if nvqSubvectors exceeds the dimension
         * @throws IllegalArgumentException if vectorChunkBytes > 0 but holds fewer than the minimum number
         *         of vector slots at this dimension
         */
        public VectorIndexConfiguration build();


        /**
         * Default implementation of the configuration builder.
         */
        public static class Default implements Builder
        {
            private static final Logger LOG = LoggerFactory.getLogger(Default.class);

            /**
             * Default upper bound (30s) on how long a shutdown persist may run before it is aborted.
             */
            private static final long DEFAULT_SHUTDOWN_PERSIST_TIMEOUT_MILLIS = 30_000L;

            /**
             * Default number of in-memory nodes at which a background persist flushes a segment.
             */
            static final int DEFAULT_FLUSH_THRESHOLD = 100_000;

            /**
             * Default maximum number of segments before a persist consolidates them.
             */
            static final int DEFAULT_MAX_SEGMENTS = 32;

            /**
             * Bounds of {@link #maxSegments(int)}: one segment cannot be consolidated with anything, and
             * the 255 owner slots also have to hold the in-memory graphs and the segments a running
             * search still holds.
             */
            static final int MIN_MAX_SEGMENTS = 2  ;
            static final int MAX_MAX_SEGMENTS = 240;

            /**
             * Default number of segments a background merge combines.
             */
            static final int DEFAULT_SEGMENT_MERGE_FACTOR = 4;

            /**
             * Default interval between checkpoints of the vector file.
             */
            static final long DEFAULT_VECTOR_CHECKPOINT_INTERVAL_MS = 30_000L;

            /**
             * Default size of a chunk of the vector file.
             */
            static final long DEFAULT_VECTOR_CHUNK_BYTES = VectorFile.DEFAULT_CHUNK_BYTES;

            /**
             * The smallest chunk that holds {@link VectorFile#MIN_SLOTS_PER_CHUNK} slots at the given dimension.
             *
             * @param dimension the vector dimension
             * @return the minimum chunk size in bytes
             */
            static long minimumChunkBytes(final int dimension)
            {
                return (long)VectorFile.strideOf(dimension) * VectorFile.MIN_SLOTS_PER_CHUNK;
            }

            private int                      dimension                    ;
            private VectorSimilarityFunction similarityFunction           ;
            private int                      maxDegree                    ;
            private int                      beamWidth                    ;
            private int                      minSearchBeamWidth           ;
            private float                    neighborOverflow             ;
            private float                    alpha                        ;
            private boolean                  onDisk                       ;
            private Path                     indexDirectory               ;
            private String                   indexLocationName            ;
            private VectorStorage            vectorStorage                ;
            private ApproximateScoring       approximateScoring           ;
            private int                      nvqSubvectors                ;
            private int                      pqSubspaces                  ;
            private long                     persistenceIntervalMs        ;
            private boolean                  persistOnShutdown            ;
            private long                     shutdownPersistTimeoutMillis ;
            private int                      minChangesBetweenPersists    ;
            private int                      flushThreshold               ;
            private int                      maxSegments                  ;
            private int                      segmentMergeFactor           ;
            private long                     vectorCheckpointIntervalMs   ;
            private long                     vectorChunkBytes             ;
            private long                     optimizationIntervalMs       ;
            private int                      minChangesBetweenOptimizations;
            private boolean                  optimizeOnShutdown           ;
            private boolean                  parallelOnDiskWrite          ;
            private boolean                  eventualIndexing             ;

            Default()
            {
                this.dimension                     = 3;
                this.similarityFunction            = VectorSimilarityFunction.COSINE;
                this.maxDegree                     = 16;
                this.beamWidth                     = 100;
                this.minSearchBeamWidth            = 100;
                this.neighborOverflow              = 1.2f;
                this.alpha                         = 1.2f;
                this.onDisk                        = false;
                this.indexDirectory                = null;
                this.indexLocationName             = null;
                this.vectorStorage                 = VectorStorage.INLINE;
                this.approximateScoring            = ApproximateScoring.NONE;
                this.nvqSubvectors                 = 0;
                this.pqSubspaces                   = 0;
                this.persistenceIntervalMs         = 0;  // 0 = disabled
                this.persistOnShutdown             = true;
                this.shutdownPersistTimeoutMillis  = DEFAULT_SHUTDOWN_PERSIST_TIMEOUT_MILLIS;
                this.minChangesBetweenPersists     = 100;
                this.flushThreshold                = DEFAULT_FLUSH_THRESHOLD;
                this.maxSegments                   = DEFAULT_MAX_SEGMENTS;
                this.segmentMergeFactor            = DEFAULT_SEGMENT_MERGE_FACTOR;
                this.vectorCheckpointIntervalMs    = DEFAULT_VECTOR_CHECKPOINT_INTERVAL_MS;
                this.vectorChunkBytes              = 0L; // not set: the default, or more for a large dimension
                this.optimizationIntervalMs        = 0;  // 0 = disabled
                this.minChangesBetweenOptimizations = 1000;
                this.optimizeOnShutdown            = false;
                this.parallelOnDiskWrite           = false;
                this.eventualIndexing              = false;
            }

            @Override
            public Builder dimension(final int dimension)
            {
                this.dimension = positive(dimension);
                return this;
            }

            @Override
            public Builder similarityFunction(final VectorSimilarityFunction similarityFunction)
            {
                this.similarityFunction = notNull(similarityFunction);
                return this;
            }

            @Override
            public Builder maxDegree(final int maxDegree)
            {
                this.maxDegree = positive(maxDegree);
                return this;
            }

            @Override
            public Builder beamWidth(final int beamWidth)
            {
                this.beamWidth = positive(beamWidth);
                return this;
            }

            @Override
            public Builder minSearchBeamWidth(final int minSearchBeamWidth)
            {
                this.minSearchBeamWidth = positive(minSearchBeamWidth);
                return this;
            }

            @Override
            public Builder neighborOverflow(final float neighborOverflow)
            {
                this.neighborOverflow = positive(neighborOverflow);
                return this;
            }

            @Override
            public Builder alpha(final float alpha)
            {
                this.alpha = positive(alpha);
                return this;
            }

            @Override
            public Builder onDisk(final boolean onDisk)
            {
                this.onDisk = onDisk;
                return this;
            }

            @Override
            public Builder indexDirectory(final Path indexDirectory)
            {
                this.indexDirectory    = indexDirectory;
                this.indexLocationName = null;
                return this;
            }

            @Override
            public Builder indexLocation(final IndexLocation indexLocation)
            {
                notNull(indexLocation);
                if(indexLocation.isNamed())
                {
                    // The directory is resolved when the index is opened, not stored. A directory set
                    // before is cleared, like the name is by indexDirectory(): a new index configured by
                    // name must not carry a stored path that is never used. Only a switch of an existing
                    // index keeps its previous path (withIndexLocation), for older library versions.
                    this.indexLocationName = indexLocation.name();
                    this.indexDirectory    = null;
                }
                else
                {
                    this.indexDirectory(indexLocation.directory());
                }
                return this;
            }

            @Override
            public Builder vectorStorage(final VectorStorage vectorStorage)
            {
                this.vectorStorage = notNull(vectorStorage);
                return this;
            }

            @Override
            public Builder approximateScoring(final ApproximateScoring approximateScoring)
            {
                this.approximateScoring = notNull(approximateScoring);
                return this;
            }

            @Override
            public Builder nvqSubvectors(final int nvqSubvectors)
            {
                if(nvqSubvectors < 0)
                {
                    throw new IllegalArgumentException("nvqSubvectors must be non-negative, got: " + nvqSubvectors);
                }
                this.nvqSubvectors = nvqSubvectors;
                return this;
            }

            @Deprecated
            @Override
            public Builder enablePqCompression(final boolean enablePqCompression)
            {
                // A literal delegate, writing the same field the enum setter writes. Keeping one
                // field rather than two is what makes "which one wins" a non-question: the last
                // call wins, whichever spelling it used.
                return this.approximateScoring(
                    enablePqCompression ? ApproximateScoring.FUSED_PQ : ApproximateScoring.NONE
                );
            }

            @Override
            public Builder pqSubspaces(final int pqSubspaces)
            {
                if(pqSubspaces < 0)
                {
                    throw new IllegalArgumentException("pqSubspaces must be non-negative, got: " + pqSubspaces);
                }
                this.pqSubspaces = pqSubspaces;
                return this;
            }

            @Override
            public Builder persistenceIntervalMs(final long persistenceIntervalMs)
            {
                if(persistenceIntervalMs < 0)
                {
                    throw new IllegalArgumentException("persistenceIntervalMs must be non-negative, got: " + persistenceIntervalMs);
                }
                this.persistenceIntervalMs = persistenceIntervalMs;
                return this;
            }

            @Override
            public Builder persistOnShutdown(final boolean persistOnShutdown)
            {
                this.persistOnShutdown = persistOnShutdown;
                return this;
            }

            @Override
            public Builder shutdownPersistTimeoutMillis(final long shutdownPersistTimeoutMillis)
            {
                this.shutdownPersistTimeoutMillis = positive(shutdownPersistTimeoutMillis);
                return this;
            }

            @Override
            public Builder minChangesBetweenPersists(final int minChangesBetweenPersists)
            {
                if(minChangesBetweenPersists < 0)
                {
                    throw new IllegalArgumentException("minChangesBetweenPersists must be non-negative, got: " + minChangesBetweenPersists);
                }
                this.minChangesBetweenPersists = minChangesBetweenPersists;
                return this;
            }

            @Override
            public Builder flushThreshold(final int flushThreshold)
            {
                this.flushThreshold = positive(flushThreshold);
                return this;
            }

            @Override
            public Builder maxSegments(final int maxSegments)
            {
                if(maxSegments < MIN_MAX_SEGMENTS || maxSegments > MAX_MAX_SEGMENTS)
                {
                    throw new IllegalArgumentException("maxSegments must be between " + MIN_MAX_SEGMENTS
                        + " and " + MAX_MAX_SEGMENTS + ", got: " + maxSegments);
                }
                this.maxSegments = maxSegments;
                return this;
            }

            @Override
            public Builder segmentMergeFactor(final int segmentMergeFactor)
            {
                // The same bounds as maxSegments: a merge of one segment combines nothing.
                if(segmentMergeFactor < MIN_MAX_SEGMENTS || segmentMergeFactor > MAX_MAX_SEGMENTS)
                {
                    throw new IllegalArgumentException("segmentMergeFactor must be between " + MIN_MAX_SEGMENTS
                        + " and " + MAX_MAX_SEGMENTS + ", got: " + segmentMergeFactor);
                }
                this.segmentMergeFactor = segmentMergeFactor;
                return this;
            }

            @Override
            public Builder vectorCheckpointIntervalMs(final long vectorCheckpointIntervalMs)
            {
                if(vectorCheckpointIntervalMs <= 0)
                {
                    throw new IllegalArgumentException(
                        "vectorCheckpointIntervalMs must be positive, got: " + vectorCheckpointIntervalMs);
                }
                this.vectorCheckpointIntervalMs = vectorCheckpointIntervalMs;
                return this;
            }

            @Override
            public Builder vectorChunkBytes(final long vectorChunkBytes)
            {
                if(vectorChunkBytes <= 0)
                {
                    throw new IllegalArgumentException("vectorChunkBytes must be positive, got: " + vectorChunkBytes);
                }
                this.vectorChunkBytes = vectorChunkBytes;
                return this;
            }

            @Override
            public Builder optimizationIntervalMs(final long optimizationIntervalMs)
            {
                if(optimizationIntervalMs < 0)
                {
                    throw new IllegalArgumentException("optimizationIntervalMs must be non-negative, got: " + optimizationIntervalMs);
                }
                this.optimizationIntervalMs = optimizationIntervalMs;
                return this;
            }

            @Override
            public Builder minChangesBetweenOptimizations(final int minChangesBetweenOptimizations)
            {
                if(minChangesBetweenOptimizations < 0)
                {
                    throw new IllegalArgumentException("minChangesBetweenOptimizations must be non-negative, got: " + minChangesBetweenOptimizations);
                }
                this.minChangesBetweenOptimizations = minChangesBetweenOptimizations;
                return this;
            }

            @Override
            public Builder optimizeOnShutdown(final boolean optimizeOnShutdown)
            {
                this.optimizeOnShutdown = optimizeOnShutdown;
                return this;
            }

            @Override
            public Builder parallelOnDiskWrite(final boolean parallelOnDiskWrite)
            {
                this.parallelOnDiskWrite = parallelOnDiskWrite;
                return this;
            }

            @Override
            public Builder eventualIndexing(final boolean eventualIndexing)
            {
                this.eventualIndexing = eventualIndexing;
                return this;
            }

            @Override
            public VectorIndexConfiguration build()
            {
                // Validation
                if(this.onDisk && this.indexDirectory == null && this.indexLocationName == null)
                {
                    throw new IllegalStateException("indexDirectory or indexLocation is required when onDisk is true");
                }
                // FUSED_PQ writes its codes into the graph file, so it is on-disk only. PQ_IN_MEMORY
                // keeps them in heap, which an in-memory index can do just as well - there it simply
                // has no sidecar to write.
                if(this.approximateScoring == ApproximateScoring.FUSED_PQ && !this.onDisk)
                {
                    throw new IllegalStateException("Compression requires onDisk mode to be enabled");
                }

                // An in-memory index has no persist to hang the switch to compressed scoring on, and
                // doing it on a mutation would mean an O(n) train, encode and rebuild inside the
                // GigaMap monitor. It happens at optimization instead, so there has to be one
                // scheduled - otherwise the mode would be configured and silently never take effect.
                if(this.approximateScoring == ApproximateScoring.PQ_IN_MEMORY
                    && !this.onDisk
                    && this.optimizationIntervalMs <= 0)
                {
                    throw new IllegalStateException(
                        "In-memory PQ scoring switches the score provider at optimization, so one has to"
                            + " be scheduled: set optimizationIntervalMs to a positive value. An explicit"
                            + " optimize() call performs the switch too, but the configuration cannot"
                            + " rely on one happening."
                    );
                }

                // A scheduled optimization is necessary but not sufficient: the background manager
                // skips one until minChangesBetweenOptimizations changes have accumulated, and that
                // defaults to 1000. An index that settles between the 256 vectors PQ training needs
                // and that threshold would be configured for compressed scoring and never switch,
                // so the setting has to be reachable rather than merely positive.
                if(this.approximateScoring == ApproximateScoring.PQ_IN_MEMORY
                    && !this.onDisk
                    && this.optimizationIntervalMs > 0
                    && this.minChangesBetweenOptimizations > PQCompressionManager.MIN_VECTORS_FOR_PQ_TRAINING)
                {
                    LOG.warn(
                        "Index configured for in-memory PQ scoring has minChangesBetweenOptimizations={},"
                            + " above the {} vectors PQ training needs. An index that stops growing in"
                            + " between will stay on exact scoring until something calls optimize()"
                            + " explicitly. Lower the threshold to switch automatically.",
                        this.minChangesBetweenOptimizations,
                        PQCompressionManager.MIN_VECTORS_FOR_PQ_TRAINING
                    );
                }
                if(this.vectorStorage != VectorStorage.INLINE && !this.onDisk)
                {
                    throw new IllegalStateException("Vector storage requires onDisk mode to be enabled");
                }
                if(this.persistenceIntervalMs > 0 && !this.onDisk)
                {
                    throw new IllegalStateException("Background persistence requires onDisk mode to be enabled");
                }
                if(this.pqSubspaces > 0 && this.dimension % this.pqSubspaces != 0)
                {
                    throw new IllegalArgumentException(
                        "dimension (" + this.dimension + ") must be divisible by pqSubspaces (" + this.pqSubspaces + ")"
                    );
                }

                // No maxDegree constraint is applied for PQ. JVector 4's FusedPQ accepts any degree -
                // its only precondition is a 256-cluster codebook - and it records the degree in the
                // graph header, so FusedPQ.load sizes the fused block from the file itself. The
                // "FusedPQ requires maxDegree=32" rule this builder used to enforce was a leftover
                // from JVector 3's FusedADC; it silently doubled the graph's out-degree, which was
                // the only effect enablePqCompression had before the feature worked.

                // JVector's getSubvectorSizesAndOffsets splits the dimension across the subvectors
                // and only requires that there be at least one dimension per subvector. Unlike
                // pqSubspaces there is no divisibility rule: the remainder is distributed.
                if(this.nvqSubvectors > this.dimension)
                {
                    throw new IllegalArgumentException(
                        "nvqSubvectors (" + this.nvqSubvectors + ") must not exceed dimension (" + this.dimension + ")"
                    );
                }

                // PQ_IN_MEMORY trades the fused codes' disk cost for a heap cost that is linear in
                // the ordinal space and resident for as long as the index is open. That is easy to
                // choose without pricing, and the failure mode is an OutOfMemoryError at scale
                // rather than anything the configuration can catch, so state the rate up front.
                if(this.approximateScoring == ApproximateScoring.PQ_IN_MEMORY)
                {
                    final int codeBytes = this.pqSubspaces > 0
                        ? this.pqSubspaces
                        : Math.max(1, this.dimension / 4);
                    LOG.info(
                        "PQ_IN_MEMORY holds {} bytes per ordinal in heap for as long as the index is open"
                            + " (about {} MB per million vectors, and deletion holes count). FUSED_PQ pays"
                            + " in graph size and page cache instead.",
                        codeBytes, codeBytes
                    );
                }

                // NVQ costs 1 byte per dimension plus a fixed 28 bytes of nonlinearity parameters
                // per subvector, against 4 bytes per dimension for full precision. At a high enough
                // subvector count that stops being a saving, which is a configuration mistake rather
                // than an illegal state: the index still works, it is just paying quantization
                // recall for nothing. Warn instead of throwing.
                if(this.vectorStorage == VectorStorage.NVQ)
                {
                    // The effective count, not the raw field: 0 means "auto" and resolves to one, so
                    // logging the raw value would report "0 subvectors" for a size computed from one.
                    final int effectiveSubvectors = this.nvqSubvectors > 0 ? this.nvqSubvectors : 1;
                    final int nvqBytes            = 4 + this.dimension + 28 * effectiveSubvectors;
                    if(nvqBytes >= this.dimension * Float.BYTES)
                    {
                        LOG.warn(
                            "NVQ storage would cost {} bytes per node against {} for full precision at dimension {}"
                                + " and {} subvectors, so it saves nothing. Lower nvqSubvectors or use"
                                + " VectorStorage.INLINE.",
                            nvqBytes, this.dimension * Float.BYTES, this.dimension, effectiveSubvectors
                        );
                    }
                }

                if(this.vectorChunkBytes > 0L && this.vectorChunkBytes < minimumChunkBytes(this.dimension))
                {
                    throw new IllegalArgumentException("vectorChunkBytes " + this.vectorChunkBytes
                        + " holds fewer than " + VectorFile.MIN_SLOTS_PER_CHUNK + " vector slots of "
                        + VectorFile.strideOf(this.dimension) + " bytes at dimension " + this.dimension
                        + ": at least " + minimumChunkBytes(this.dimension) + " bytes");
                }

                return new VectorIndexConfiguration.Default(
                    this.dimension,
                    this.similarityFunction,
                    this.maxDegree,
                    this.beamWidth,
                    this.minSearchBeamWidth,
                    this.neighborOverflow,
                    this.alpha,
                    this.onDisk,
                    this.indexDirectory,
                    this.indexLocationName,
                    this.vectorStorage,
                    this.approximateScoring,
                    this.nvqSubvectors,
                    this.pqSubspaces,
                    this.persistenceIntervalMs,
                    this.persistOnShutdown,
                    this.shutdownPersistTimeoutMillis,
                    this.minChangesBetweenPersists,
                    this.flushThreshold,
                    this.maxSegments,
                    this.segmentMergeFactor,
                    this.vectorCheckpointIntervalMs,
                    this.vectorChunkBytes,
                    this.optimizationIntervalMs,
                    this.minChangesBetweenOptimizations,
                    this.optimizeOnShutdown,
                    this.parallelOnDiskWrite,
                    this.eventualIndexing
                );
            }

        }

    }


    /**
     * Default immutable implementation of {@link VectorIndexConfiguration}.
     */
    public static class Default implements VectorIndexConfiguration
    {
        private final int                      dimension                     ;
        private final VectorSimilarityFunction similarityFunction            ;
        private final int                      maxDegree                     ;
        private final int                      beamWidth                     ;
        private final int                      minSearchBeamWidth            ;
        private final float                    neighborOverflow              ;
        private final float                    alpha                         ;
        private final boolean                  onDisk                        ;
        private final String                   indexDirectory                ; // Stored as String for serialization
        // Name of an IndexLocation.Named, or null for a plain directory. A plain field rather than a
        // stored IndexLocation: an older library version drops an unknown field when it loads the
        // storage, but fails the whole start on an instance of a class it does not have. null is also
        // what configurations stored before this field existed read as, i.e. today's behaviour.
        private final String                   indexLocationName             ;
        private final VectorStorage            vectorStorage                 ;
        private final ApproximateScoring       approximateScoring            ;
        private final int                      nvqSubvectors                 ;
        // Retained although approximateScoring now carries the same information, because this is
        // the field a configuration written before the enums existed carries its setting in: when
        // those enum fields come back null, approximateScoring() derives the value from here. It is
        // derived in the constructor and never set independently.
        //
        // It is NOT a downgrade path, and must not be read as one. Once this build has persisted a
        // configuration, VectorStorage and ApproximateScoring are in the type dictionary, and a
        // build predating them cannot open the storage at all - it fails with a missing runtime
        // type for the required type handler rather than falling back to this field. Verified by
        // restarting a storage across the two module versions.
        private final boolean                  enablePqCompression           ;
        private final int                      pqSubspaces                   ;
        private final long                     persistenceIntervalMs         ;
        private final boolean                  persistOnShutdown             ;
        private final long                     shutdownPersistTimeoutMillis  ;
        private final int                      minChangesBetweenPersists     ;
        // 0 in a configuration stored before these existed; the getters fall back to the defaults.
        private final int                      flushThreshold                ;
        private final int                      maxSegments                   ;
        private final int                      segmentMergeFactor            ;
        private final long                     vectorCheckpointIntervalMs    ;
        private final long                     vectorChunkBytes              ;
        private final long                     optimizationIntervalMs        ;
        private final int                      minChangesBetweenOptimizations;
        private final boolean                  optimizeOnShutdown            ;
        private final boolean                  parallelOnDiskWrite           ;
        private final boolean                  eventualIndexing              ;

        Default(
            final int                      dimension                      ,
            final VectorSimilarityFunction similarityFunction             ,
            final int                      maxDegree                      ,
            final int                      beamWidth                      ,
            final int                      minSearchBeamWidth             ,
            final float                    neighborOverflow               ,
            final float                    alpha                          ,
            final boolean                  onDisk                         ,
            final Path                     indexDirectory                 ,
            final String                   indexLocationName              ,
            final VectorStorage            vectorStorage                  ,
            final ApproximateScoring       approximateScoring             ,
            final int                      nvqSubvectors                  ,
            final int                      pqSubspaces                    ,
            final long                     persistenceIntervalMs          ,
            final boolean                  persistOnShutdown              ,
            final long                     shutdownPersistTimeoutMillis   ,
            final int                      minChangesBetweenPersists      ,
            final int                      flushThreshold                 ,
            final int                      maxSegments                    ,
            final int                      segmentMergeFactor             ,
            final long                     vectorCheckpointIntervalMs     ,
            final long                     vectorChunkBytes               ,
            final long                     optimizationIntervalMs         ,
            final int                      minChangesBetweenOptimizations ,
            final boolean                  optimizeOnShutdown             ,
            final boolean                  parallelOnDiskWrite            ,
            final boolean                  eventualIndexing
        )
        {
            this.indexLocationName              = indexLocationName                                        ;
            this.dimension                      = dimension                                                ;
            this.similarityFunction             = similarityFunction                                       ;
            this.maxDegree                      = maxDegree                                                ;
            this.beamWidth                      = beamWidth                                                ;
            this.minSearchBeamWidth             = minSearchBeamWidth                                       ;
            this.neighborOverflow               = neighborOverflow                                         ;
            this.alpha                          = alpha                                                    ;
            this.onDisk                         = onDisk                                                   ;
            this.indexDirectory                 = indexDirectory != null ? indexDirectory.toString() : null;
            this.vectorStorage                  = vectorStorage                                            ;
            this.approximateScoring             = approximateScoring                                       ;
            this.nvqSubvectors                  = nvqSubvectors                                            ;
            this.enablePqCompression            = approximateScoring == ApproximateScoring.FUSED_PQ        ;
            this.pqSubspaces                    = pqSubspaces                                              ;
            this.persistenceIntervalMs          = persistenceIntervalMs                                    ;
            this.persistOnShutdown              = persistOnShutdown                                        ;
            this.shutdownPersistTimeoutMillis   = shutdownPersistTimeoutMillis                             ;
            this.minChangesBetweenPersists      = minChangesBetweenPersists                                ;
            this.flushThreshold                 = flushThreshold                                           ;
            this.maxSegments                    = maxSegments                                              ;
            this.segmentMergeFactor             = segmentMergeFactor                                       ;
            this.vectorCheckpointIntervalMs     = vectorCheckpointIntervalMs                               ;
            this.vectorChunkBytes               = vectorChunkBytes                                         ;
            this.optimizationIntervalMs         = optimizationIntervalMs                                   ;
            this.minChangesBetweenOptimizations = minChangesBetweenOptimizations                           ;
            this.optimizeOnShutdown             = optimizeOnShutdown                                       ;
            this.parallelOnDiskWrite            = parallelOnDiskWrite                                      ;
            this.eventualIndexing               = eventualIndexing                                         ;
        }

        @Override
        public int dimension()
        {
            return this.dimension;
        }

        @Override
        public VectorSimilarityFunction similarityFunction()
        {
            return this.similarityFunction;
        }

        @Override
        public int maxDegree()
        {
            return this.maxDegree;
        }

        @Override
        public int beamWidth()
        {
            return this.beamWidth;
        }

        @Override
        public int minSearchBeamWidth()
        {
            return this.minSearchBeamWidth;
        }

        @Override
        public float neighborOverflow()
        {
            return this.neighborOverflow;
        }

        @Override
        public float alpha()
        {
            return this.alpha;
        }

        @Override
        public boolean onDisk()
        {
            return this.onDisk;
        }

        @Override
        public Path indexDirectory()
        {
            return this.indexDirectory != null ? Path.of(this.indexDirectory) : null;
        }

        @Override
        public String indexLocationName()
        {
            return this.indexLocationName;
        }

        @Override
        public VectorIndexConfiguration withIndexLocation(final IndexLocation indexLocation)
        {
            notNull(indexLocation);
            final boolean named = indexLocation.isNamed();

            // Storage and scoring go through their getters: a configuration persisted before those
            // fields existed holds null, and the constructor derives enablePqCompression from the
            // scoring it is given, so a raw null would silently switch a legacy index's PQ off.
            // Everything else is copied raw, because the getters normalize it identically on both.
            return new VectorIndexConfiguration.Default(
                this.dimension                                                   ,
                this.similarityFunction                                          ,
                this.maxDegree                                                   ,
                this.beamWidth                                                   ,
                this.minSearchBeamWidth                                          ,
                this.neighborOverflow                                            ,
                this.alpha                                                       ,
                this.onDisk                                                      ,
                named ? this.indexDirectory() : indexLocation.directory()        ,
                named ? indexLocation.name()  : null                             ,
                this.vectorStorage()                                             ,
                this.approximateScoring()                                        ,
                this.nvqSubvectors                                               ,
                this.pqSubspaces                                                 ,
                this.persistenceIntervalMs                                       ,
                this.persistOnShutdown                                           ,
                this.shutdownPersistTimeoutMillis                                ,
                this.minChangesBetweenPersists                                   ,
                this.flushThreshold                                              ,
                this.maxSegments                                                 ,
                this.segmentMergeFactor                                          ,
                this.vectorCheckpointIntervalMs                                  ,
                this.vectorChunkBytes                                            ,
                this.optimizationIntervalMs                                      ,
                this.minChangesBetweenOptimizations                              ,
                this.optimizeOnShutdown                                          ,
                this.parallelOnDiskWrite                                         ,
                this.eventualIndexing
            );
        }

        @Override
        public VectorStorage vectorStorage()
        {
            // Null for a configuration persisted before this field existed: Eclipse Store fills a
            // field added by schema evolution with the zero value, which for a reference is null.
            // INLINE is what such a configuration described, so it is the right answer rather than
            // merely a safe default.
            return this.vectorStorage != null
                ? this.vectorStorage
                : VectorStorage.INLINE;
        }

        @Override
        public ApproximateScoring approximateScoring()
        {
            // As above, null means the field postdates this stored configuration. The legacy
            // boolean is the same setting under its old name, so derive from it rather than
            // defaulting to NONE, which would silently switch PQ off for every existing index.
            return this.approximateScoring != null
                ? this.approximateScoring
                : this.enablePqCompression
                    ? ApproximateScoring.FUSED_PQ
                    : ApproximateScoring.NONE;
        }

        @Override
        public int nvqSubvectors()
        {
            // Zero means either "auto" or a field added by schema evolution; both resolve to one
            // subvector, which is the recommended setting anyway.
            return this.nvqSubvectors > 0
                ? this.nvqSubvectors
                : 1;
        }

        @Deprecated
        @Override
        public boolean enablePqCompression()
        {
            // Read through approximateScoring() rather than the field, so a configuration stored by
            // a newer build that set the enum without the legacy boolean still answers correctly.
            return this.approximateScoring() == ApproximateScoring.FUSED_PQ;
        }

        @Override
        public int pqSubspaces()
        {
            return this.pqSubspaces;
        }

        @Override
        public long persistenceIntervalMs()
        {
            return this.persistenceIntervalMs;
        }

        @Override
        public boolean persistOnShutdown()
        {
            return this.persistOnShutdown;
        }

        @Override
        public long shutdownPersistTimeoutMillis()
        {
            // Guard against a non-positive value loaded from a pre-existing store (Eclipse Store
            // fills a field added by schema evolution with 0); fall back to the default so a legacy
            // config never yields a zero/immediate shutdown-persist timeout.
            return this.shutdownPersistTimeoutMillis > 0
                ? this.shutdownPersistTimeoutMillis
                : Builder.Default.DEFAULT_SHUTDOWN_PERSIST_TIMEOUT_MILLIS;
        }

        @Override
        public int minChangesBetweenPersists()
        {
            return this.minChangesBetweenPersists;
        }

        @Override
        public int flushThreshold()
        {
            // Zero for a configuration stored before this field existed (Eclipse Store fills a field
            // added by schema evolution with 0), which would flush on every background persist.
            return this.flushThreshold > 0
                ? this.flushThreshold
                : Builder.Default.DEFAULT_FLUSH_THRESHOLD;
        }

        @Override
        public int maxSegments()
        {
            // Zero for a configuration stored before this field existed, see flushThreshold().
            return this.maxSegments > 0
                ? this.maxSegments
                : Builder.Default.DEFAULT_MAX_SEGMENTS;
        }

        @Override
        public int segmentMergeFactor()
        {
            // Zero for a configuration stored before this field existed, see flushThreshold().
            return this.segmentMergeFactor > 0
                ? this.segmentMergeFactor
                : Builder.Default.DEFAULT_SEGMENT_MERGE_FACTOR;
        }

        @Override
        public long vectorCheckpointIntervalMs()
        {
            // Zero for a configuration stored before this field existed, see flushThreshold().
            return this.vectorCheckpointIntervalMs > 0
                ? this.vectorCheckpointIntervalMs
                : Builder.Default.DEFAULT_VECTOR_CHECKPOINT_INTERVAL_MS;
        }

        @Override
        public long vectorChunkBytes()
        {
            // Zero when not set, also for a configuration stored before this field existed: the default, or the
            // minimum where the dimension is so large that the default holds too few slots.
            return this.vectorChunkBytes > 0
                ? this.vectorChunkBytes
                : Math.max(
                    Builder.Default.DEFAULT_VECTOR_CHUNK_BYTES,
                    Builder.Default.minimumChunkBytes(this.dimension)
                );
        }

        @Override
        public long optimizationIntervalMs()
        {
            return this.optimizationIntervalMs;
        }

        @Override
        public int minChangesBetweenOptimizations()
        {
            return this.minChangesBetweenOptimizations;
        }

        @Override
        public boolean optimizeOnShutdown()
        {
            return this.optimizeOnShutdown;
        }

        @Override
        public boolean parallelOnDiskWrite()
        {
            return this.parallelOnDiskWrite;
        }

        @Override
        public boolean eventualIndexing()
        {
            return this.eventualIndexing;
        }

    }

}
