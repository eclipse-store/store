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
 *     .enablePqCompression(true)                         // Optional: Product Quantization compression
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
 *
 * <h2>Background Optimization</h2>
 * Background optimization runs {@code builder.cleanup()} periodically to remove excess neighbors
 * accumulated during construction, reducing memory and improving query latency.
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
 *
 * <h2>Combined Example</h2>
 * Production configuration with on-disk storage, persistence, and optimization:
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
     *   <li>{@code {name}.graph} - The graph structure: HNSW edges, the full-precision inline
     *       vectors used for exact reranking, and, when {@link #enablePqCompression()} is active,
     *       the PQ codebook and fused compressed codes. There is no separate codebook file.</li>
     *   <li>{@code {name}.meta} - Metadata file (format version, dimension, vector count, highest
     *       entity id, structural modification count)</li>
     * </ul>
     * Both are written to {@code .tmp} siblings and renamed into place, so an interrupted persist
     * leaves at most a stale temporary file.
     *
     * @return the index directory path, or null if not using on-disk mode
     * @see #onDisk()
     */
    public Path indexDirectory();

    /**
     * Returns how the on-disk graph stores its own copy of each vector.
     * <p>
     * This is one of two independent dimensions of the on-disk format; the other is
     * {@link #approximateScoring()}. Storage decides how many bytes each node costs and how exact
     * the reranking pass can be, scoring decides how traversal finds its candidates, and any
     * combination of the two is legal.
     * <p>
     * {@link VectorStorage#NVQ} is the only setting on either dimension that makes the
     * {@code .graph} file <i>smaller</i>: roughly 3x, at the cost of a reranking pass that compares
     * against dequantized rather than full-precision vectors. See the constant's own documentation
     * for the trade-off.
     * <p>
     * Requires {@link #onDisk()} to be true, since it describes the on-disk format only.
     *
     * @return the vector storage mode (default: {@link VectorStorage#INLINE})
     * @see VectorStorage
     * @see #approximateScoring()
     * @see #nvqSubvectors()
     */
    public VectorStorage vectorStorage();

    /**
     * Returns how graph traversal scores the candidates it visits.
     * <p>
     * This is one of two independent dimensions of the on-disk format; the other is
     * {@link #vectorStorage()}. Whatever is chosen here, the best candidates are always reranked
     * before they are returned, so this governs which candidates traversal finds rather than how
     * the final top-k is ordered.
     * <p>
     * Requires {@link #onDisk()} to be true for any value other than
     * {@link ApproximateScoring#NONE}.
     *
     * @return the approximate scoring mode (default: {@link ApproximateScoring#NONE})
     * @see ApproximateScoring
     * @see #vectorStorage()
     * @see #pqSubspaces()
     */
    public ApproximateScoring approximateScoring();

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
     * @return the effective number of NVQ subvectors, never zero: a configured 0 means "auto" and
     *         resolves to 1 here, so callers always receive a usable count. Note this differs from
     *         {@link #pqSubspaces()}, which returns its raw sentinel and leaves resolution to the
     *         caller.
     * @see VectorStorage#NVQ
     * @see #vectorStorage()
     */
    public int nvqSubvectors();

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
     * candidates are reranked against them exactly, as before. With {@link VectorStorage#NVQ} the
     * graph holds quantized vectors instead, so reranking compares against those - a legal and
     * useful combination, but not the exact one this flag used to imply on its own. See
     * {@link #vectorStorage()}.
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
     * are no full-precision inline vectors: the graph holds quantized ones, so both the bytes a
     * rerank reads and the bytes the persist writes are roughly a quarter of the figures quoted
     * above, and reranking compares against those quantized vectors rather than exact ones. The
     * fused block is unchanged, and remains the dominant per-node cost.
     * <p>
     * The codebook is trained once, on the first persist at which at least 256 <i>embeddings</i>
     * exist - entities without one are skipped, so an index of 256 entities of which some have no
     * vector does not yet qualify. Below that threshold the graph is written uncompressed and
     * training is reattempted on the next persist that follows a graph-affecting change, or in a
     * later session. An unchanged index is deliberately not retried, since that would rebuild the
     * whole graph on every idle persist.
     * Once trained, the codebook is stored in the graph header and recovered on every subsequent
     * load, so it is never retrained for the life of that on-disk index.
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
     * In incremental on-disk mode the shutdown persist returns immediately (no full-graph
     * consolidation is performed on shutdown), so in practice this timeout only bounds a genuinely
     * slow first-time (non-incremental) persist or an unexpectedly slow drain/optimize.
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
     * Returns whether background optimization is enabled.
     * <p>
     * Background optimization is enabled when {@link #optimizationIntervalMs()} is greater than 0.
     * When enabled, the index will run {@code builder.cleanup()} in a background thread
     * at regular intervals, which removes excess neighbors accumulated during construction.
     * This reduces memory usage and improves query latency.
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
     * deferred to a background thread. The vector store is still updated
     * synchronously, but graph construction happens asynchronously.
     * <p>
     * This reduces the latency of mutation operations at the cost of
     * eventual consistency — search results may not immediately reflect the
     * most recent mutations.
     * <p>
     * The graph is automatically drained (all pending operations applied)
     * before {@code optimize()}, {@code persistToDisk()}, and {@code close()}.
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
     * for faster traversal, and background persistence/optimization. Note that PQ trades disk space
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
     * and background persistence/optimization. PQ compression can be optionally enabled to
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
     * background persistence/optimization enabled.
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
     * uncompressed graph. Reranking stays exact: with no fused codes the search path reranks against
     * the vectors held in the GigaMap rather than against the graph's quantized copy. Use
     * {@link #forLargeDataset(int, Path)} when query latency matters more than footprint.
     * <p>
     * <b>Configuration:</b> maxDegree=32, beamWidth=300, onDisk=true, vectorStorage=NVQ,
     * approximateScoring=NONE, persistenceIntervalMs=30000, optimizationIntervalMs=60000
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
     * approximateScoring=NONE, persistenceIntervalMs=30000, optimizationIntervalMs=60000
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
            .approximateScoring(ApproximateScoring.NONE);
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
         * Sets how the on-disk graph stores its own copy of each vector.
         * <p>
         * Requires {@link #onDisk(boolean)} to be true for any value other than
         * {@link VectorStorage#INLINE}.
         *
         * @param vectorStorage the storage mode, never null
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#vectorStorage()
         */
        public Builder vectorStorage(VectorStorage vectorStorage);

        /**
         * Sets how graph traversal scores the candidates it visits.
         * <p>
         * Requires {@link #onDisk(boolean)} to be true for any value other than
         * {@link ApproximateScoring#NONE}.
         *
         * @param approximateScoring the scoring mode, never null
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#approximateScoring()
         */
        public Builder approximateScoring(ApproximateScoring approximateScoring);

        /**
         * Sets the number of NVQ subvectors.
         * <p>
         * Must not exceed the dimension. Use 0 for auto-calculation (1), which is the
         * recommended setting - see {@link VectorIndexConfiguration#nvqSubvectors()} for why a
         * higher count is usually pure overhead.
         *
         * @param nvqSubvectors the number of subvectors, or 0 for auto
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#nvqSubvectors()
         */
        public Builder nvqSubvectors(int nvqSubvectors);

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
         * @throws IllegalStateException if onDisk is true but indexDirectory is null
         * @throws IllegalStateException if approximateScoring is not NONE but onDisk is false
         * @throws IllegalStateException if vectorStorage is not INLINE but onDisk is false
         * @throws IllegalArgumentException if pqSubspaces > 0 and dimension is not divisible by pqSubspaces
         * @throws IllegalArgumentException if nvqSubvectors exceeds the dimension
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

            private int                      dimension                    ;
            private VectorSimilarityFunction similarityFunction           ;
            private int                      maxDegree                    ;
            private int                      beamWidth                    ;
            private int                      minSearchBeamWidth           ;
            private float                    neighborOverflow             ;
            private float                    alpha                        ;
            private boolean                  onDisk                       ;
            private Path                     indexDirectory               ;
            private VectorStorage            vectorStorage                ;
            private ApproximateScoring       approximateScoring           ;
            private int                      nvqSubvectors                ;
            private int                      pqSubspaces                  ;
            private long                     persistenceIntervalMs        ;
            private boolean                  persistOnShutdown            ;
            private long                     shutdownPersistTimeoutMillis ;
            private int                      minChangesBetweenPersists    ;
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
                this.vectorStorage                 = VectorStorage.INLINE;
                this.approximateScoring            = ApproximateScoring.NONE;
                this.nvqSubvectors                 = 0;
                this.pqSubspaces                   = 0;
                this.persistenceIntervalMs         = 0;  // 0 = disabled
                this.persistOnShutdown             = true;
                this.shutdownPersistTimeoutMillis  = DEFAULT_SHUTDOWN_PERSIST_TIMEOUT_MILLIS;
                this.minChangesBetweenPersists     = 100;
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
                this.indexDirectory = indexDirectory;
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
                if(this.onDisk && this.indexDirectory == null)
                {
                    throw new IllegalStateException("indexDirectory is required when onDisk is true");
                }
                if(this.approximateScoring != ApproximateScoring.NONE && !this.onDisk)
                {
                    throw new IllegalStateException("Compression requires onDisk mode to be enabled");
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
                    this.vectorStorage,
                    this.approximateScoring,
                    this.nvqSubvectors,
                    this.pqSubspaces,
                    this.persistenceIntervalMs,
                    this.persistOnShutdown,
                    this.shutdownPersistTimeoutMillis,
                    this.minChangesBetweenPersists,
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
            final VectorStorage            vectorStorage                  ,
            final ApproximateScoring       approximateScoring             ,
            final int                      nvqSubvectors                  ,
            final int                      pqSubspaces                    ,
            final long                     persistenceIntervalMs          ,
            final boolean                  persistOnShutdown              ,
            final long                     shutdownPersistTimeoutMillis   ,
            final int                      minChangesBetweenPersists      ,
            final long                     optimizationIntervalMs         ,
            final int                      minChangesBetweenOptimizations ,
            final boolean                  optimizeOnShutdown             ,
            final boolean                  parallelOnDiskWrite            ,
            final boolean                  eventualIndexing
        )
        {
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
