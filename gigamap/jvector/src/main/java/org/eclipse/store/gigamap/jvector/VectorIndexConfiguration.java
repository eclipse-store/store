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
     *   <li>{@code {name}.graph} - The graph structure file</li>
     *   <li>{@code {name}.pq} - Product quantization codebook and compressed vectors (if compression enabled)</li>
     *   <li>{@code {name}.meta} - Metadata file (version, config hash, vector count)</li>
     * </ul>
     *
     * @return the index directory path, or null if not using on-disk mode
     * @see #onDisk()
     */
    public Path indexDirectory();

    /**
     * Returns whether Product Quantization (PQ) compression is enabled.
     * <p>
     * When enabled, vectors are compressed using Product Quantization, which:
     * <ul>
     *   <li><b>Reduces memory</b>: Compresses vectors significantly (e.g., 768 floats → 48 bytes)</li>
     *   <li><b>Enables scale</b>: Allows much larger datasets to fit in memory</li>
     *   <li><b>Approximate scoring</b>: Uses compressed vectors for initial scoring, then reranks with exact vectors</li>
     * </ul>
     * <p>
     * Requires {@link #onDisk()} to be true.
     *
     * @return true if PQ compression is enabled (default: false)
     * @see #pqSubspaces()
     * @see #onDisk()
     */
    public boolean enablePqCompression();

    /**
     * Returns the number of PQ subspaces (M parameter).
     * <p>
     * Product Quantization divides each vector into M subspaces, each encoded
     * with 256 centroids (8 bits). More subspaces means:
     * <ul>
     *   <li><b>Higher accuracy</b>: Finer-grained quantization</li>
     *   <li><b>More storage</b>: Each vector uses M bytes</li>
     * </ul>
     * <p>
     * The vector dimension must be evenly divisible by this value.
     * <p>
     * <b>Recommendation:</b> Use dimension/4 for a good balance (e.g., 768-dim → 192 subspaces → 192 bytes/vector).
     * Use dimension/8 for more compression with some accuracy loss.
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
     * When enabled and {@link #backgroundPersistence()} is true, the index will
     * persist any pending changes when {@code close()} is called, ensuring all
     * changes are durable before shutdown completes.
     *
     * @return true if persist-on-shutdown is enabled (default: true)
     * @see #backgroundPersistence()
     */
    public boolean persistOnShutdown();

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
     * @return true if parallel on-disk writing is enabled (default: true)
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
     * Uses higher parameter values for better recall at scale, with on-disk storage,
     * PQ compression for memory efficiency, and background persistence/optimization.
     * <p>
     * <b>Configuration:</b> maxDegree=32, beamWidth=300, onDisk=true, enablePqCompression=true,
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
     * and background persistence/optimization. PQ compression can be optionally enabled
     * for memory efficiency.
     * <p>
     * <b>Configuration:</b> maxDegree=32, beamWidth=300, onDisk=true,
     * persistenceIntervalMs=30000, optimizationIntervalMs=60000
     *
     * @param dimension the vector dimension (must be positive)
     * @param indexDirectory the directory where index files will be stored
     * @param enableCompression true to enable PQ compression (recommended for memory efficiency)
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
     * Creates an in-memory configuration optimized for high precision requirements.
     * <p>
     * Uses maximum parameter values to achieve the highest possible recall,
     * with PQ compression disabled to avoid any precision loss.
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
     * with PQ compression disabled to avoid any precision loss.
     * <p>
     * <b>Configuration:</b> maxDegree=56, beamWidth=450, onDisk=true,
     * enablePqCompression=false, persistenceIntervalMs=30000
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
     * for high precision use cases. PQ compression is explicitly disabled as it
     * reduces precision.
     * <p>
     * <b>Pre-configured values:</b> maxDegree=56, beamWidth=450, enablePqCompression=false
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
            .enablePqCompression(false);
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
         * Enables or disables Product Quantization compression.
         * <p>
         * Requires {@link #onDisk(boolean)} to be true.
         *
         * @param enablePqCompression true to enable PQ compression
         * @return this builder for method chaining
         * @see VectorIndexConfiguration#enablePqCompression()
         */
        public Builder enablePqCompression(boolean enablePqCompression);

        /**
         * Sets the number of PQ subspaces.
         * <p>
         * The vector dimension must be evenly divisible by this value.
         * Use 0 for auto-calculation (dimension/4).
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
         * @throws IllegalStateException if enableCompression is true but onDisk is false
         * @throws IllegalArgumentException if pqSubspaces > 0 and dimension is not divisible by pqSubspaces
         */
        public VectorIndexConfiguration build();


        /**
         * Default implementation of the configuration builder.
         */
        public static class Default implements Builder
        {
            private static final Logger LOG = LoggerFactory.getLogger(Default.class);

            /**
             * FusedPQ compression requires maxDegree to be exactly 32.
             */
            private static final int FUSED_PQ_REQUIRED_MAX_DEGREE = 32;

            private int                      dimension                    ;
            private VectorSimilarityFunction similarityFunction           ;
            private int                      maxDegree                    ;
            private int                      beamWidth                    ;
            private float                    neighborOverflow             ;
            private float                    alpha                        ;
            private boolean                  onDisk                       ;
            private Path                     indexDirectory               ;
            private boolean                  enablePqCompression          ;
            private int                      pqSubspaces                  ;
            private long                     persistenceIntervalMs        ;
            private boolean                  persistOnShutdown            ;
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
                this.neighborOverflow              = 1.2f;
                this.alpha                         = 1.2f;
                this.onDisk                        = false;
                this.indexDirectory                = null;
                this.enablePqCompression           = false;
                this.pqSubspaces                   = 0;
                this.persistenceIntervalMs         = 0;  // 0 = disabled
                this.persistOnShutdown             = true;
                this.minChangesBetweenPersists     = 100;
                this.optimizationIntervalMs        = 0;  // 0 = disabled
                this.minChangesBetweenOptimizations = 1000;
                this.optimizeOnShutdown            = false;
                this.parallelOnDiskWrite           = true;
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
            public Builder enablePqCompression(final boolean enablePqCompression)
            {
                this.enablePqCompression = enablePqCompression;
                return this;
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
                if(this.enablePqCompression && !this.onDisk)
                {
                    throw new IllegalStateException("Compression requires onDisk mode to be enabled");
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

                // FusedPQ requires maxDegree=32 - enforce when compression is enabled
                if(this.enablePqCompression && this.maxDegree != FUSED_PQ_REQUIRED_MAX_DEGREE)
                {
                    LOG.warn("FusedPQ requires maxDegree={}, overriding configured value {}",
                        FUSED_PQ_REQUIRED_MAX_DEGREE, this.maxDegree);
                    this.maxDegree = FUSED_PQ_REQUIRED_MAX_DEGREE;
                }

                return new VectorIndexConfiguration.Default(
                    this.dimension,
                    this.similarityFunction,
                    this.maxDegree,
                    this.beamWidth,
                    this.neighborOverflow,
                    this.alpha,
                    this.onDisk,
                    this.indexDirectory,
                    this.enablePqCompression,
                    this.pqSubspaces,
                    this.persistenceIntervalMs,
                    this.persistOnShutdown,
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
        private final float                    neighborOverflow              ;
        private final float                    alpha                         ;
        private final boolean                  onDisk                        ;
        private final String                   indexDirectory                ; // Stored as String for serialization
        private final boolean                  enablePqCompression           ;
        private final int                      pqSubspaces                   ;
        private final long                     persistenceIntervalMs         ;
        private final boolean                  persistOnShutdown             ;
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
            final float                    neighborOverflow               ,
            final float                    alpha                          ,
            final boolean                  onDisk                         ,
            final Path                     indexDirectory                 ,
            final boolean                  enablePqCompression            ,
            final int                      pqSubspaces                    ,
            final long                     persistenceIntervalMs          ,
            final boolean                  persistOnShutdown              ,
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
            this.neighborOverflow               = neighborOverflow                                         ;
            this.alpha                          = alpha                                                    ;
            this.onDisk                         = onDisk                                                   ;
            this.indexDirectory                 = indexDirectory != null ? indexDirectory.toString() : null;
            this.enablePqCompression            = enablePqCompression                                      ;
            this.pqSubspaces                    = pqSubspaces                                              ;
            this.persistenceIntervalMs          = persistenceIntervalMs                                    ;
            this.persistOnShutdown              = persistOnShutdown                                        ;
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
        public boolean enablePqCompression()
        {
            return this.enablePqCompression;
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
