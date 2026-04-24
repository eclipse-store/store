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

import io.github.jbellis.jvector.graph.*;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.graph.similarity.DefaultSearchScoreProvider;
import io.github.jbellis.jvector.graph.similarity.SearchScoreProvider;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.util.ExplicitThreadLocal;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.exceptions.IORuntimeException;
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.store.gigamap.types.AbstractStateChangeFlagged;
import org.eclipse.store.gigamap.types.GigaIndex;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.ScoredSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.IntStream;

import static org.eclipse.serializer.math.XMath.positive;

/**
 * A vector index that enables k-nearest-neighbor (k-NN) similarity search on entities.
 * <p>
 * This index uses the HNSW (Hierarchical Navigable Small World) algorithm, a graph-based
 * approximate nearest neighbor search algorithm that provides excellent query performance
 * with high recall. Entities are indexed by their vector representation, allowing you to
 * find the most similar entities to a given query vector.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><b>High Performance</b> - Sub-linear query time complexity via hierarchical graph navigation</li>
 *   <li><b>High Recall</b> - Configurable trade-offs between speed and accuracy</li>
 *   <li><b>Persistence</b> - Full integration with GigaMap's persistence layer</li>
 *   <li><b>On-Disk Storage</b> - Optional memory-mapped indices for large datasets</li>
 *   <li><b>PQ Compression</b> - Product Quantization for reduced memory footprint</li>
 *   <li><b>Background Optimization</b> - Automatic graph cleanup for improved performance</li>
 *   <li><b>Eventual Indexing</b> - Deferred graph mutations via background thread for reduced write latency</li>
 *   <li><b>Parallel On-Disk Writes</b> - Multi-threaded index persistence for large on-disk indices</li>
 * </ul>
 *
 * <h2>Basic Usage</h2>
 * <pre>{@code
 * // 1. Create a GigaMap and register VectorIndices
 * GigaMap<Document> gigaMap = GigaMap.New();
 * VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
 *
 * // 2. Configure and create a vector index
 * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
 *     .dimension(768)                                    // Must match your embedding size
 *     .similarityFunction(VectorSimilarityFunction.COSINE)
 *     .build();
 *
 * VectorIndex<Document> index = vectorIndices.add(
 *     "embeddings",                                      // Index name
 *     config,
 *     new DocumentVectorizer()                           // Extracts vectors from entities
 * );
 *
 * // 3. Add entities (automatically indexed)
 * gigaMap.add(new Document("Hello world", embedding1));
 * gigaMap.add(new Document("Hello there", embedding2));
 *
 * // 4. Search for similar entities
 * float[] queryVector = computeEmbedding("Hello");
 * VectorSearchResult<Document> results = index.search(queryVector, 10);
 *
 * for (ScoredSearchResult.Entry<Document> entry : results) {
 *     Document doc = entry.entity();      // Lazy lookup via GigaMap
 *     float similarity = entry.score();   // Similarity score
 *     System.out.println(doc.content() + " (score: " + similarity + ")");
 * }
 * }</pre>
 *
 * <h2>Vectorizer Implementation</h2>
 * The {@link Vectorizer} extracts vector representations from entities. Two modes are supported:
 *
 * <h3>Embedded Mode (vectors stored in entity)</h3>
 * Use when vectors are already part of your entity:
 * <pre>{@code
 * class DocumentVectorizer extends Vectorizer<Document> {
 *     @Override
 *     public float[] vectorize(Document doc) {
 *         return doc.embedding();  // Vector already in entity
 *     }
 *
 *     @Override
 *     public boolean isEmbedded() {
 *         return true;  // Don't duplicate storage
 *     }
 * }
 * }</pre>
 *
 * <h3>Computed Mode (vectors stored separately)</h3>
 * Use when vectors are computed externally (e.g., from an embedding API):
 * <pre>{@code
 * class ComputedVectorizer extends Vectorizer<Document> {
 *     private final EmbeddingService embeddingService;
 *
 *     @Override
 *     public float[] vectorize(Document doc) {
 *         return embeddingService.embed(doc.content());
 *     }
 *
 *     // isEmbedded() defaults to false - vectors stored in VectorIndex
 * }
 * }</pre>
 *
 * <h2>On-Disk Storage</h2>
 * For large datasets that exceed available memory:
 * <pre>{@code
 * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
 *     .dimension(768)
 *     .similarityFunction(VectorSimilarityFunction.COSINE)
 *     .onDisk(true)
 *     .indexDirectory(Path.of("/data/vectors"))
 *     .enablePqCompression(true)     // Optional: reduce memory with Product Quantization
 *     .pqSubspaces(48)               // Must divide dimension evenly
 *     .build();
 * }</pre>
 *
 * <h2>Background Persistence</h2>
 * Automatically persist the index at regular intervals (enabled by setting interval &gt; 0):
 * <pre>{@code
 * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
 *     .dimension(768)
 *     .onDisk(true)
 *     .indexDirectory(Path.of("/data/vectors"))
 *     .persistenceIntervalMs(30_000)      // Enable, check every 30 seconds
 *     .minChangesBetweenPersists(100)     // Only persist if >= 100 changes
 *     .persistOnShutdown(true)            // Persist on close()
 *     .build();
 * }</pre>
 *
 * <h2>Background Optimization</h2>
 * Periodically clean up the graph to reduce memory and improve query latency
 * (enabled by setting interval &gt; 0):
 * <pre>{@code
 * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
 *     .dimension(768)
 *     .optimizationIntervalMs(60_000)          // Enable, check every 60 seconds
 *     .minChangesBetweenOptimizations(1000)    // Only optimize if >= 1000 changes
 *     .optimizeOnShutdown(false)               // Skip on close() for faster shutdown
 *     .build();
 * }</pre>
 *
 * <h2>Eventual Indexing</h2>
 * When enabled, expensive HNSW graph mutations (add, update, remove) are deferred to a background
 * thread. The vector store is still updated synchronously, so no data is lost, but graph construction
 * happens asynchronously. This reduces the latency of mutation operations at the cost of eventual
 * consistency — search results may not immediately reflect the most recent mutations.
 * <p>
 * The graph is automatically drained (all pending operations applied) before
 * {@code optimize()}, {@code persistToDisk()}, and {@code close()}.
 * <pre>{@code
 * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
 *     .dimension(768)
 *     .similarityFunction(VectorSimilarityFunction.COSINE)
 *     .eventualIndexing(true)
 *     .build();
 * }</pre>
 *
 * <h2>Parallel On-Disk Writes</h2>
 * When on-disk storage is enabled, persistence can optionally use parallel direct buffers and
 * multiple worker threads (one per available processor) to write the index concurrently. This can
 * significantly speed up persistence for large indices. Disabled by default, as sequential
 * single-threaded writing is preferred in resource-constrained environments or for smaller indices.
 * <pre>{@code
 * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
 *     .dimension(768)
 *     .similarityFunction(VectorSimilarityFunction.COSINE)
 *     .onDisk(true)
 *     .indexDirectory(Path.of("/data/vectors"))
 *     .parallelOnDiskWrite(true)
 *     .build();
 * }</pre>
 *
 * <h2>Search Methods</h2>
 * <pre>{@code
 * // Search by vector
 * VectorSearchResult<Document> results = index.search(queryVector, 10);
 *
 * // Search by entity (uses vectorizer to extract query vector)
 * VectorSearchResult<Document> results = index.search(queryDocument, 10);
 *
 * // Manual optimization
 * index.optimize();
 *
 * // Manual persistence (for on-disk indices)
 * index.persistToDisk();
 * }</pre>
 *
 * <h2>Working with Results</h2>
 * <pre>{@code
 * VectorSearchResult<Document> results = index.search(queryVector, 10);
 *
 * // Iterate
 * for (ScoredSearchResult.Entry<Document> entry : results) {
 *     System.out.println(entry.entity().content());
 * }
 *
 * // Stream API
 * List<String> titles = results.stream()
 *     .filter(e -> e.score() > 0.8f)
 *     .map(e -> e.entity().title())
 *     .toList();
 *
 * // Convert to list
 * List<ScoredSearchResult.Entry<Document>> list = results.toList();
 * }</pre>
 *
 * <h2>Persistence with EclipseStore</h2>
 * <pre>{@code
 * // Save
 * try (EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir)) {
 *     GigaMap<Document> gigaMap = GigaMap.New();
 *     storage.setRoot(gigaMap);
 *
 *     VectorIndices<Document> indices = gigaMap.index().register(VectorIndices.Category());
 *     VectorIndex<Document> index = indices.add("embeddings", config, vectorizer);
 *
 *     gigaMap.add(document1);
 *     gigaMap.add(document2);
 *
 *     storage.storeRoot();
 * }
 *
 * // Load
 * try (EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir)) {
 *     GigaMap<Document> gigaMap = storage.root();
 *     VectorIndices<Document> indices = gigaMap.index().get(VectorIndices.Category());
 *     VectorIndex<Document> index = indices.get("embeddings");
 *
 *     // Search works immediately after load
 *     VectorSearchResult<Document> results = index.search(queryVector, 10);
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <ul>
 *   <li><b>Search</b> - Thread-safe, multiple concurrent searches allowed</li>
 *   <li><b>Add/Remove</b> - Thread-safe via GigaMap synchronization</li>
 *   <li><b>Optimization</b> - Briefly blocks add/remove/search during cleanup</li>
 *   <li><b>Eventual Indexing</b> - Graph mutations are applied sequentially by a single
 *       background worker thread; vector store updates remain synchronous</li>
 * </ul>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li><b>~2.1 billion vectors per index</b> - Graph ordinals use {@code int}. For larger
 *       datasets, implement sharding across multiple indices.</li>
 *   <li><b>Fixed dimension</b> - All vectors must have exactly the configured dimension.</li>
 * </ul>
 *
 * @param <E> the entity type
 * @see VectorIndices
 * @see VectorIndexConfiguration
 * @see VectorSearchResult
 * @see Vectorizer
 */
public interface VectorIndex<E> extends GigaIndex<E>, Closeable
{
    /**
     * Returns the parent VectorIndices associated with this VectorIndex.
     *
     * @return the parent VectorIndices instance
     */
    public VectorIndices<E> parent();

    /**
     * Returns the vectorizer used by this index.
     *
     * @return the vectorizer
     */
    public Vectorizer<? super E> vectorizer();

    /**
     * Returns the configuration of this index.
     *
     * @return the index configuration
     */
    public VectorIndexConfiguration configuration();

    @Override
    public default boolean isSuitableAsUniqueConstraint()
    {
        // Vector indices cannot serve as unique constraints
        return false;
    }

    /**
     * Searches for the k nearest neighbors to the query vector.
     * <p>
     * This method performs an approximate nearest neighbor (ANN) search using the HNSW
     * algorithm. Results are returned in descending order of similarity score, with
     * the most similar entity first.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * float[] queryVector = embeddingService.embed("search query");
     * VectorSearchResult<Document> results = index.search(queryVector, 10);
     *
     * for (ScoredSearchResult.Entry<Document> entry : results) {
     *     System.out.printf("Score: %.4f - %s%n",
     *         entry.score(),
     *         entry.entity().title()
     *     );
     * }
     * }</pre>
     *
     * <h4>Similarity Scores</h4>
     * The score interpretation depends on the configured {@link VectorSimilarityFunction}:
     * <ul>
     *   <li><b>COSINE</b> - Range [-1, 1], where 1 = identical, 0 = orthogonal, -1 = opposite</li>
     *   <li><b>DOT_PRODUCT</b> - Unbounded, higher = more similar (use with normalized vectors)</li>
     *   <li><b>EUCLIDEAN</b> - Range [0, ∞), lower = more similar (converted to similarity internally)</li>
     * </ul>
     *
     * <h4>Thread Safety</h4>
     * This method is thread-safe. Multiple concurrent searches are allowed and will not
     * block each other. However, searches may briefly block during background optimization
     * or persistence operations.
     *
     * <h4>Performance Notes</h4>
     * <ul>
     *   <li>Query time is sub-linear: O(log n) average case</li>
     *   <li>Larger k values increase query time</li>
     *   <li>Higher {@link VectorIndexConfiguration#beamWidth()} improves recall but increases latency</li>
     * </ul>
     *
     * @param queryVector the query vector; must have exactly {@link VectorIndexConfiguration#dimension()} elements
     * @param k           the number of nearest neighbors to return; must be positive
     * @return the search result containing up to k entries with entity IDs, similarity scores,
     *         and lazy entity access; never null but may contain fewer than k results if the
     *         index has fewer entities
     * @throws IllegalArgumentException if queryVector is null, has wrong dimension, or k &lt;= 0
     * @see #search(Object, int)
     * @see VectorSearchResult
     */
    public VectorSearchResult<E> search(float[] queryVector, int k);

    /**
     * Searches for the k nearest neighbors with an explicit per-query search beam width
     * (HNSW <i>efSearch</i>).
     * <p>
     * This overload overrides the configured floor from
     * {@link VectorIndexConfiguration#minSearchBeamWidth()} for a single call. The effective
     * beam width is {@code max(k, searchBeamWidth)} because jvector requires the beam width
     * to be at least as large as the requested {@code k}.
     * <p>
     * Use this to widen exploration (e.g. {@code searchBeamWidth=500} for higher recall) or
     * to narrow it (e.g. {@code searchBeamWidth=k} for minimum latency when reproducibility
     * across different {@code k} values is not required).
     *
     * @param queryVector      the query vector; must have exactly
     *                         {@link VectorIndexConfiguration#dimension()} elements
     * @param k                the number of nearest neighbors to return; must be positive
     * @param searchBeamWidth  the beam width to use for this query; must be positive
     * @return the search result
     * @throws IllegalArgumentException if queryVector is null, has wrong dimension, or
     *                                  {@code k} / {@code searchBeamWidth} are not positive
     * @see #search(float[], int)
     * @see VectorIndexConfiguration#minSearchBeamWidth()
     */
    public VectorSearchResult<E> search(float[] queryVector, int k, int searchBeamWidth);

    /**
     * Searches for the k nearest neighbors to the given entity's vector.
     * <p>
     * This is a convenience method that extracts the vector from the query entity using
     * the configured {@link Vectorizer}, then delegates to {@link #search(float[], int)}.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * // Find documents similar to an existing document
     * Document referenceDoc = gigaMap.get(documentId);
     * VectorSearchResult<Document> similar = index.search(referenceDoc, 5);
     *
     * // The reference document itself will typically be the first result (score ≈ 1.0)
     * similar.stream()
     *     .skip(1)  // Skip self-match
     *     .forEach(entry -> System.out.println(entry.entity().title()));
     * }</pre>
     *
     * <h4>Use Cases</h4>
     * <ul>
     *   <li><b>Find similar items</b> - "More like this" recommendations</li>
     *   <li><b>Duplicate detection</b> - Find near-duplicates of an entity</li>
     *   <li><b>Clustering verification</b> - Check which entities are most similar</li>
     * </ul>
     *
     * @param queryEntity the query entity whose vector will be extracted via the vectorizer;
     *                    must not be null
     * @param k           the number of nearest neighbors to return; must be positive
     * @return the search result containing up to k entries; never null
     * @throws IllegalArgumentException if queryEntity is null or k &lt;= 0
     * @throws NullPointerException if the vectorizer returns null for the entity
     * @see #search(float[], int)
     * @see Vectorizer#vectorize(Object)
     */
    public default VectorSearchResult<E> search(final E queryEntity, final int k)
    {
        return this.search(this.vectorizer().vectorize(queryEntity), k);
    }

    /**
     * Searches for the k nearest neighbors to the given entity's vector with an explicit
     * per-query search beam width.
     *
     * @param queryEntity     the query entity whose vector will be extracted via the vectorizer
     * @param k               the number of nearest neighbors to return; must be positive
     * @param searchBeamWidth the beam width to use for this query; must be positive
     * @return the search result
     * @see #search(float[], int, int)
     */
    public default VectorSearchResult<E> search(final E queryEntity, final int k, final int searchBeamWidth)
    {
        return this.search(this.vectorizer().vectorize(queryEntity), k, searchBeamWidth);
    }

    /**
     * Performs cleanup and optimization of the index graph structure.
     * <p>
     * This method removes excess neighbor connections that accumulate during graph
     * construction, reducing memory usage and improving query latency. It is recommended
     * to call this periodically after bulk insertions or when query performance degrades.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * // After bulk insertion
     * for (Document doc : documents) {
     *     gigaMap.add(doc);
     * }
     * index.optimize();  // Clean up the graph
     *
     * // Or on a schedule
     * scheduler.scheduleAtFixedRate(
     *     () -> index.optimize(),
     *     1, 1, TimeUnit.HOURS
     * );
     * }</pre>
     *
     * <h4>Background Optimization</h4>
     * Instead of calling this method manually, you can enable automatic background
     * optimization by setting {@code optimizationIntervalMs} to a value greater than 0:
     * <pre>{@code
     * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
     *     .dimension(768)
     *     .optimizationIntervalMs(60_000)
     *     .minChangesBetweenOptimizations(1000)
     *     .build();
     * }</pre>
     *
     * <h4>Thread Safety</h4>
     * This method acquires an exclusive lock on the graph structure. While optimization
     * is running:
     * <ul>
     *   <li>Add/remove operations will block until optimization completes</li>
     *   <li>Search operations will block until optimization completes</li>
     * </ul>
     * For large indices, consider using background optimization to minimize disruption.
     *
     * <h4>When to Optimize</h4>
     * <ul>
     *   <li>After adding many entities (e.g., &gt;1000)</li>
     *   <li>After removing many entities</li>
     *   <li>When query latency increases noticeably</li>
     *   <li>Periodically (e.g., hourly or daily) for continuously updated indices</li>
     * </ul>
     *
     * @see VectorIndexConfiguration#backgroundOptimization()
     * @see VectorIndexConfiguration#optimizationIntervalMs()
     */
    public void optimize();

    /**
     * Persists the index to disk if on-disk mode is enabled.
     * <p>
     * For on-disk indices, this writes the graph structure and metadata to the configured
     * {@link VectorIndexConfiguration#indexDirectory()}. If PQ compression is enabled, the
     * compressed vectors are embedded in the graph file.
     * <p>
     * For in-memory indices ({@link #isOnDisk()} returns false), this method is a no-op.
     *
     * <h4>Files Created</h4>
     * <ul>
     *   <li><code>{indexName}.graph</code> - The HNSW graph structure</li>
     *   <li><code>{indexName}.meta</code> - Metadata including vector count and configuration</li>
     * </ul>
     *
     * <h4>Example</h4>
     * <pre>{@code
     * // Manual persistence after bulk operations
     * for (Document doc : documents) {
     *     gigaMap.add(doc);
     * }
     * index.persistToDisk();
     *
     * // Or before application shutdown
     * Runtime.getRuntime().addShutdownHook(new Thread(() -> {
     *     try {
     *         index.persistToDisk();
     *     } catch (IOException e) {
     *         logger.error("Failed to persist index", e);
     *     }
     * }));
     * }</pre>
     *
     * <h4>Background Persistence</h4>
     * Instead of calling this method manually, you can enable automatic background
     * persistence by setting {@code persistenceIntervalMs} to a value greater than 0:
     * <pre>{@code
     * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
     *     .dimension(768)
     *     .onDisk(true)
     *     .indexDirectory(Path.of("/data/vectors"))
     *     .persistenceIntervalMs(30_000)
     *     .minChangesBetweenPersists(100)
     *     .persistOnShutdown(true)
     *     .build();
     * }</pre>
     *
     * <h4>Thread Safety</h4>
     * This method acquires a write lock that blocks concurrent searches during persistence.
     * Add/remove operations are also blocked. For minimal disruption, prefer background
     * persistence or call during low-traffic periods.
     *
     * <h4>Recovery</h4>
     * On restart, the persisted graph is automatically loaded if the files exist and are
     * valid. If the files are corrupted or the vector count doesn't match, the graph is
     * rebuilt from the stored vectors.
     *
     * @see #isOnDisk()
     * @see VectorIndexConfiguration#indexDirectory()
     * @see VectorIndexConfiguration#backgroundPersistence()
     */
    public void persistToDisk();

    /**
     * Returns whether the index is configured for on-disk storage.
     * <p>
     * On-disk indices store the graph structure in memory-mapped files, allowing indices
     * larger than available RAM. The trade-off is slightly higher query latency compared
     * to fully in-memory indices.
     *
     * @return true if on-disk mode is enabled via {@link VectorIndexConfiguration#onDisk()}
     * @see VectorIndexConfiguration#onDisk()
     * @see VectorIndexConfiguration#indexDirectory()
     */
    public default boolean isOnDisk()
    {
        return this.configuration().onDisk();
    }

    /**
     * Returns whether Product Quantization (PQ) compression is enabled for this index.
     * <p>
     * PQ compression reduces memory usage by encoding vectors into compact codes, at the
     * cost of some accuracy loss. This is particularly useful for large indices where
     * memory is constrained.
     * <p>
     * <b>Note:</b> PQ compression requires on-disk mode to be enabled.
     *
     * @return true if PQ compression is enabled via {@link VectorIndexConfiguration#enablePqCompression()}
     * @see VectorIndexConfiguration#enablePqCompression()
     * @see VectorIndexConfiguration#pqSubspaces()
     */
    public default boolean isPqCompressionEnabled()
    {
        return this.configuration().enablePqCompression();
    }

    /**
     * Retrieves the vector associated with the given entity ID.
     * <p>
     * If the vectorizer is embedded, the vector is computed on-the-fly from the entity
     * stored in the parent map. Otherwise, the vector is looked up from the vector store.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * float[] vector = index.getVector(entityId);
     * if (vector != null) {
     *     System.out.println("Vector dimension: " + vector.length);
     * }
     * }</pre>
     *
     * @param entityId the entity ID whose vector to retrieve
     * @return the vector as a float array, or {@code null} if no vector is found for the given entity ID
     */
    public float[] getVector(long entityId);

    /**
     * Closes the index and releases all associated resources.
     * <p>
     * This method should be called when the index is no longer needed. It performs
     * the following cleanup in order:
     * <ol>
     *   <li>Shuts down background optimization (optionally runs final optimization
     *       if {@link VectorIndexConfiguration#optimizeOnShutdown()} is enabled)</li>
     *   <li>Shuts down background persistence (optionally persists pending changes
     *       if {@link VectorIndexConfiguration#persistOnShutdown()} is enabled)</li>
     *   <li>Closes file handles and releases memory-mapped buffers (for on-disk indices)</li>
     * </ol>
     *
     * <h4>Example</h4>
     * <pre>{@code
     * // Using try-with-resources (recommended)
     * try (VectorIndex<Document> index = vectorIndices.add("embeddings", config, vectorizer)) {
     *     // Use the index
     *     VectorSearchResult<Document> results = index.search(queryVector, 10);
     * }
     * // Index is automatically closed
     *
     * // Or manual close
     * VectorIndex<Document> index = vectorIndices.add("embeddings", config, vectorizer);
     * try {
     *     // Use the index
     * } finally {
     *     index.close();
     * }
     * }</pre>
     *
     * <h4>Thread Safety</h4>
     * After calling close, the index should not be used. Subsequent calls to search,
     * optimize, or persistToDisk may throw exceptions or produce undefined behavior.
     *
     * @see VectorIndexConfiguration#optimizeOnShutdown()
     * @see VectorIndexConfiguration#persistOnShutdown()
     */
    @Override
    public void close();

    /**
     * Internal interface with mutating methods hidden from public API.
     *
     * @param <E> the entity type
     */
    public interface Internal<E> extends VectorIndex<E>
    {
        public void internalAdd(long entityId, E entity);

        public void internalAddAll(long firstEntityId, Iterable<? extends E> entities);

        public void internalAddAll(long firstEntityId, E[] entities);

        public void internalUpdate(long entityId, E replacedEntity, E entity);

        public void internalRemove(long entityId, E entity);

        public void internalRemoveAll();

        public void clearStateChangeMarkers();

        /**
         * Trains PQ codebook if compression is enabled and sufficient vectors exist.
         * Called before persistence to ensure compressed vectors are ready.
         */
        public void trainCompressionIfNeeded();
    }


    /**
     * Default implementation of VectorIndex using the HNSW graph algorithm.
     * Vectors are stored in a separate GigaMap for lazy loading and persistence.
     * Entity IDs are used directly as graph ordinals.
     *
     * @param <E> the entity type
     */
    public static class Default<E>
    extends    AbstractStateChangeFlagged
    implements VectorIndex.Internal<E>,
               BackgroundTaskManager.Callback,
               PQCompressionManager.VectorProvider,
               DiskIndexManager.IndexStateProvider
    {
        private static final Logger LOG = LoggerFactory.getLogger(Default.class);

        static BinaryTypeHandler<Default<?>> provideTypeHandler()
        {
            return BinaryHandlerVectorIndexDefault.New();
        }


        ///////////////////////////////////////////////////////////////////////////
        // instance fields //
        ////////////////////

        final VectorIndices<E>           parent       ;
        final String                     name         ;
        final VectorIndexConfiguration   configuration;
        final Vectorizer<? super E>      vectorizer   ;

        // Vector storage - null if vectorizer.isEmbedded()
        // Key: entity ID (used as ordinal), Value: vector
        final GigaMap<VectorEntry> vectorStore;

        // HNSW graph components (transient - rebuilt on load)
        private transient VectorTypeSupport vectorTypeSupport;
        private transient GraphIndexBuilder builder          ;
        private transient OnHeapGraphIndex  index            ;

        // Managers (transient - recreated on load)
        private transient DiskIndexManager      diskManager          ;
        private transient PQCompressionManager  pqManager            ;
                transient BackgroundTaskManager backgroundTaskManager;

        // GraphSearcher pool for thread-local reuse
        private transient ExplicitThreadLocal<GraphSearcher> inMemorySearcherPool;

        // Incremental on-disk mode: after disk reload, use disk index for search
        // and in-memory builder only for new mutations. Full rebuild deferred to persistToDisk().
        // Volatile: written under writeLock (reenterIncrementalMode, exitIncrementalMode)
        // but read by mutation methods (internalUpdate, internalRemove) under parentMap
        // monitor only, without builderLock.
        private transient volatile boolean                   incrementalMode    ;
        private transient Set<Integer>                       diskDeletedOrdinals;
        private transient ExplicitThreadLocal<GraphSearcher> diskSearcherPool    ;

        // Read/write lock for builder operations.
        // Read lock: concurrent searches and background-worker mutations
        // Write lock: exclusive access for cleanup, persistence, removeAll, close
        private transient ReentrantReadWriteLock builderLock;

        // When true, sync-mode mutations defer builder ops to avoid racing with cleanup().
        // cleanup()'s ForkJoinPool workers need the GigaMap monitor (for embedded vectorizers),
        // so sync-mode mutations (which hold that monitor) cannot use builderLock — they use
        // this flag instead. The synchronized(parentMap) barrier in optimize()/persistToDisk()
        // ensures any in-flight mutation completes before cleanup begins.
        private transient volatile boolean                cleanupInProgress;
        private transient ConcurrentLinkedQueue<Runnable> deferredBuilderOps;


        ///////////////////////////////////////////////////////////////////////////
        // constructors //
        /////////////////

        /**
         * Standard constructor for creating a new index.
         */
        Default(
            final VectorIndices<E>         parent       ,
            final String                   name         ,
            final boolean                  stateChanged ,
            final VectorIndexConfiguration configuration,
            final Vectorizer<? super E>    vectorizer
        )
        {
            super(stateChanged);

            this.parent        = parent       ;
            this.name          = name         ;
            this.configuration = configuration;
            this.vectorizer    = vectorizer   ;

            this.vectorStore = vectorizer.isEmbedded()
                ? null
                : GigaMap.<VectorEntry>Builder()
                    .withBitmapIdentityIndex(VectorEntry.SOURCE_ENTITY_ID_INDEXER)
                    .build()
            ;

            // Initialize builder lock early (before ensureIndexInitialized)
            this.builderLock         = new ReentrantReadWriteLock();
            this.deferredBuilderOps  = new ConcurrentLinkedQueue<>();

            this.ensureIndexInitialized();
        }

        /**
         * Constructor for binary handler deserialization.
         */
        @SuppressWarnings("unused")
        Default()
        {
            super(false);
            this.parent           = null;
            this.name             = null;
            this.configuration    = null;
            this.vectorizer       = null;
            this.vectorStore      = null;
        }

        /**
         * Ensures the transient HNSW index is initialized.
         * Called after deserialization to rebuild the graph.
         */
        void ensureIndexInitialized()
        {
            final boolean diskLoaded = this.diskManager != null && this.diskManager.isLoaded();
            if(this.builder == null && !diskLoaded)
            {
                this.initializeIndex();

                if(!this.incrementalMode)
                {
                    // Rebuild graph from stored data (after deserialization).
                    // Skipped in incremental mode — disk index serves search,
                    // in-memory builder only handles new mutations.
                    this.rebuildGraphFromStore();
                }
            }
        }

        /**
         * Rebuilds the HNSW graph from stored data.
         */
        private void rebuildGraphFromStore()
        {
            final List<VectorEntry> entries = this.collectStoredVectors();

            if(entries.isEmpty())
            {
                return;
            }

            this.addGraphNodesSequential(entries);
        }

        private List<VectorEntry> collectStoredVectors()
        {
            final List<VectorEntry> entries = new ArrayList<>();

            if(this.isEmbedded())
            {
                this.parentMap().iterateIndexed((entityId, entity) ->
                {
                    final float[] vector = this.vectorize(entity);
                    entries.add(new VectorEntry(entityId, vector));
                });
            }
            else
            {
                // Computed mode: rebuild from vector store
                if(this.vectorStore != null && !this.vectorStore.isEmpty())
                {
                    this.vectorStore.iterate(entries::add);
                }
            }

            return entries;
        }

        private void initializeIndex()
        {
            this.vectorTypeSupport = VectorizationProvider.getInstance().getVectorTypeSupport();

            // Initialize builder lock (always, for consistent locking semantics)
            if(this.builderLock == null)
            {
                this.builderLock = new ReentrantReadWriteLock();
            }
            if(this.deferredBuilderOps == null)
            {
                this.deferredBuilderOps = new ConcurrentLinkedQueue<>();
            }

            // Initialize PQ manager if compression enabled
            if(this.configuration.enablePqCompression())
            {
                this.pqManager = new PQCompressionManager.Default(
                    this,
                    this.name,
                    this.configuration.dimension(),
                    this.configuration.pqSubspaces()
                );
            }

            // Initialize in-memory builder
            this.initializeInMemoryBuilder();

            // Try to load from disk if on-disk mode is enabled
            if(this.configuration.onDisk())
            {
                this.diskManager = new DiskIndexManager.Default(
                    this,
                    this.name,
                    this.configuration.indexDirectory(),
                    this.configuration.dimension(),
                    this.configuration.maxDegree(),
                    this.configuration.parallelOnDiskWrite()
                );
                if(this.diskManager.tryLoad())
                {
                    // Mark PQ as trained if compression was enabled (FusedPQ is embedded)
                    if(this.pqManager != null)
                    {
                        this.pqManager.markTrained();
                        LOG.debug("FusedPQ compression loaded from disk for '{}'", this.name);
                    }

                    // Enter incremental on-disk mode: disk index serves search,
                    // in-memory builder only handles new mutations.
                    // Set state fields first, then flip incrementalMode last for safe publication.
                    this.diskDeletedOrdinals = ConcurrentHashMap.newKeySet();
                    this.incrementalMode     = true;
                    LOG.info("Entering incremental on-disk mode for '{}' — skipping full graph rebuild", this.name);
                }
                else
                {
                    LOG.info("Could not load disk index for '{}', will build in-memory and persist later", this.name);
                }
            }

            // Initialize searcher pool
            this.initializeSearcherPool();

            // Start background managers if enabled
            this.startBackgroundManagersIfEnabled();
        }

        /**
         * Starts the unified background task manager if any background feature is enabled.
         */
        private void startBackgroundManagersIfEnabled()
        {
            final boolean eventualIndexing       = this.configuration.eventualIndexing();
            final boolean backgroundOptimization = this.configuration.backgroundOptimization();
            final boolean backgroundPersistence  = this.configuration.onDisk() && this.configuration.backgroundPersistence();

            if(eventualIndexing || backgroundOptimization || backgroundPersistence)
            {
                if(this.backgroundTaskManager == null)
                {
                    this.backgroundTaskManager = new BackgroundTaskManager(
                        this,
                        this.name,
                        eventualIndexing,
                        backgroundOptimization,
                        this.configuration.optimizationIntervalMs(),
                        this.configuration.minChangesBetweenOptimizations(),
                        backgroundPersistence,
                        this.configuration.persistenceIntervalMs(),
                        this.configuration.minChangesBetweenPersists()
                    );
                }
            }
        }

        /**
         * Returns whether eventual indexing is active (background task manager exists
         * AND eventualIndexing is configured). The manager may exist for optimization
         * or persistence alone.
         */
        private boolean isEventualIndexing()
        {
            return this.backgroundTaskManager != null && this.configuration.eventualIndexing();
        }

        /**
         * Initializes the in-memory graph builder.
         */
        private void initializeInMemoryBuilder()
        {
            final RandomAccessVectorValues vectorValues = new NullSafeVectorValues(
                this.createVectorValues(), this.configuration.dimension(), this.vectorTypeSupport
            );
            final BuildScoreProvider scoreProvider = BuildScoreProvider.randomAccessScoreProvider(
                vectorValues,
                this.jvectorSimilarityFunction()
            );

            this.builder = new GraphIndexBuilder(
                scoreProvider,
                this.configuration.dimension(),
                this.configuration.maxDegree(),
                this.configuration.beamWidth(),
                this.configuration.neighborOverflow(),
                this.configuration.alpha(),
                true // use hierarchical index
            );
            this.index = (OnHeapGraphIndex)this.builder.getGraph();
        }

        /**
         * Initializes the thread-local searcher pool.
         */
        private void initializeSearcherPool()
        {
            // Close existing pools if present
            this.closeSearcherPools();

            final boolean diskLoaded = this.diskManager != null && this.diskManager.isLoaded();

            if(this.incrementalMode && diskLoaded && this.diskManager.getDiskIndex() != null)
            {
                // Incremental mode: two pools — disk searcher for existing data,
                // in-memory searcher for newly added data
                final var diskIndex = this.diskManager.getDiskIndex();
                this.diskSearcherPool = ExplicitThreadLocal.withInitial(() ->
                {
                    try
                    {
                        return new GraphSearcher(diskIndex);
                    }
                    catch(final Exception e)
                    {
                        throw new RuntimeException("Failed to create GraphSearcher for disk index", e);
                    }
                });

                // In-memory pool for the builder's graph (new mutations)
                if(this.index != null)
                {
                    this.inMemorySearcherPool = ExplicitThreadLocal.withInitial(() ->
                    {
                        try
                        {
                            return new GraphSearcher(this.index);
                        }
                        catch(final Exception e)
                        {
                            throw new RuntimeException("Failed to create GraphSearcher for in-memory index", e);
                        }
                    });
                }
            }
            else if(diskLoaded && this.diskManager.getDiskIndex() != null)
            {
                // Non-incremental disk mode: single pool for disk index
                final var diskIndex = this.diskManager.getDiskIndex();
                this.inMemorySearcherPool = ExplicitThreadLocal.withInitial(() ->
                {
                    try
                    {
                        return new GraphSearcher(diskIndex);
                    }
                    catch(final Exception e)
                    {
                        throw new RuntimeException("Failed to create GraphSearcher for disk index", e);
                    }
                });
            }
            else if(this.index != null)
            {
                // In-memory only pool
                this.inMemorySearcherPool = ExplicitThreadLocal.withInitial(() ->
                {
                    try
                    {
                        return new GraphSearcher(this.index);
                    }
                    catch(final Exception e)
                    {
                        throw new RuntimeException("Failed to create GraphSearcher for in-memory index", e);
                    }
                });
            }
        }

        /**
         * Closes the searcher pools and releases resources.
         */
        private void closeSearcherPools()
        {
            if(this.diskSearcherPool != null)
            {
                try
                {
                    this.diskSearcherPool.close();
                }
                catch(final Exception e)
                {
                    LOG.warn("Error closing disk searcher pool: {}", e.getMessage());
                }
                this.diskSearcherPool = null;
            }

            if(this.inMemorySearcherPool != null)
            {
                try
                {
                    this.inMemorySearcherPool.close();
                }
                catch(final Exception e)
                {
                    LOG.warn("Error closing searcher pool: {}", e.getMessage());
                }
                this.inMemorySearcherPool = null;
            }
        }


        ///////////////////////////////////////////////////////////////////////////
        // methods //
        ////////////

        private static int toOrdinal(final long entityId)
        {
            if(entityId > Integer.MAX_VALUE)
            {
                throw new IllegalStateException(
                    "Entity ID " + entityId + " exceeds maximum supported value of " + Integer.MAX_VALUE
                );
            }
            return (int)entityId;
        }

        private float[] vectorize(final E entity)
        {
            return this.validateVector(this.vectorizer.vectorize(entity));
        }

        private List<float[]> vectorize(final List<? extends E> entities)
        {
            final List<float[]> vectors = this.vectorizer.vectorizeAll(entities);

            if(vectors == null)
            {
                throw new IllegalStateException(
                    "vectorizeAll returned null in index \"%s\" (vectorizer: %s)"
                        .formatted(this.name(), this.vectorizer.getClass().getName())
                );
            }

            if(vectors.size() != entities.size())
            {
                throw new IllegalStateException(
                    "vectorizeAll returned %d vectors for %d entities in index \"%s\" (vectorizer: %s)"
                        .formatted(vectors.size(), entities.size(), this.name(), this.vectorizer.getClass().getName())
                );
            }

            vectors.forEach(this::validateVector);
            return vectors;
        }

        private float[] validateVector(final float[] vector)
        {
            if(vector == null)
            {
                throw new IllegalStateException(
                    "Null vector returned from vectorizer in index \"" + this.name()
                        + "\" (vectorizer: " + this.vectorizer.getClass().getName() + ")"
                );
            }

            this.validateDimension(vector);

            return vector;
        }

        private void validateDimension(final float[] vector)
        {
            final int expectedDim = this.configuration.dimension();
            if(vector.length != expectedDim)
            {
                throw new IllegalStateException(
                    "Vector must have dimension " + expectedDim + ", got " + vector.length
                );
            }
        }

        private boolean isEmbedded()
        {
            return this.vectorizer.isEmbedded();
        }

        @Override
        public final GigaMap<E> parentMap()
        {
            return this.parent.parentMap();
        }

        @Override
        public VectorIndices<E> parent()
        {
            return this.parent;
        }

        @Override
        public String name()
        {
            return this.name;
        }

        @Override
        public Vectorizer<? super E> vectorizer()
        {
            return this.vectorizer;
        }

        @Override
        public VectorIndexConfiguration configuration()
        {
            return this.configuration;
        }

        private io.github.jbellis.jvector.vector.VectorSimilarityFunction jvectorSimilarityFunction()
        {
            // use switch not valueOf(name) to ensure compiler assistance when jvector enum changes
            return switch(this.configuration.similarityFunction())
            {
                case EUCLIDEAN   -> io.github.jbellis.jvector.vector.VectorSimilarityFunction.EUCLIDEAN;
                case DOT_PRODUCT -> io.github.jbellis.jvector.vector.VectorSimilarityFunction.DOT_PRODUCT;
                case COSINE      -> io.github.jbellis.jvector.vector.VectorSimilarityFunction.COSINE;
            };
        }

        @Override
        public void internalAdd(final long entityId, final E entity)
        {
            // No synchronized(parentMap) needed — called from GigaMap's synchronized methods.
            final int ordinal = toOrdinal(entityId);

            this.ensureIndexInitialized();

            final float[] vector = this.vectorize(entity);
            final VectorEntry vectorEntry = new VectorEntry(entityId, vector);

            // Store based on vectorizer type
            if(!this.isEmbedded())
            {
                this.vectorStore.add(vectorEntry);
            }

            this.markStateChangeChildren();

            if(this.isEventualIndexing())
            {
                // Defer graph update to background thread
                this.backgroundTaskManager.enqueueAdd(vectorEntry);
            }
            else
            {
                // Drain any deferred builder ops before adding a new graph node.
                if(!this.cleanupInProgress)
                {
                    this.drainDeferredBuilderOps();
                }

                // Add to HNSW graph using entity ID as ordinal
                final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(vector);
                this.executeOrDeferBuilderOp(() -> this.builder.addGraphNode(ordinal, vf));

                // Mark dirty for background managers
                this.markDirtyForBackgroundManagers(1);
            }
        }

        @Override
        public void internalAddAll(final long firstEntityId, final E[] entities)
        {
            this.internalAddAll(firstEntityId, Arrays.asList(entities));
        }

        @Override
        public void internalAddAll(final long firstEntityId, final Iterable<? extends E> entities)
        {
            // Collect vectors first (outside of main synchronized block)
            final List<VectorEntry> entries = this.collectVectors(firstEntityId, entities);

            if(!entries.isEmpty())
            {
                this.addVectorEntries(entries);
            }
        }

        @Override
        public void internalUpdate(final long entityId, final E replacedEntity, final E entity)
        {
            // No synchronized(parentMap) needed — called from GigaMap's synchronized methods.
            this.ensureIndexInitialized();

            final float[] vector = this.vectorize(entity);
            final VectorEntry vectorEntry = new VectorEntry(entityId, vector);

            // Update based on vectorizer type
            if(!this.isEmbedded())
            {
                this.vectorStore.set(entityId, vectorEntry);
            }

            this.markStateChangeChildren();

            final int ordinal = toOrdinal(entityId);

            // In incremental mode, mark the ordinal as deleted from disk graph
            // so disk search excludes the stale version immediately,
            // regardless of whether indexing is synchronous or eventual.
            if(this.incrementalMode && this.diskDeletedOrdinals != null)
            {
                this.diskDeletedOrdinals.add(ordinal);
            }

            if(this.isEventualIndexing())
            {
                // Defer graph update to background thread
                this.backgroundTaskManager.enqueueUpdate(vectorEntry);
            }
            else
            {
                // Drain any deferred builder ops (e.g. from a preceding addAll()) before
                // modifying graph nodes. This avoids interleaving batch-add ops with
                // delete+re-add ops, which can corrupt HNSW neighbor lists.
                if(!this.cleanupInProgress)
                {
                    this.drainDeferredBuilderOps();
                }

                if(this.isEmbedded())
                {
                    // For embedded vectorizers: removeDeletedNodes() uses ForkJoinPool
                    // whose workers call parentMap.get(), deadlocking with the GigaMap
                    // monitor we hold. Instead, let the next optimize/persist cycle
                    // rebuild the graph connections. The updated entity is already in
                    // the GigaMap, so EntityBackedVectorValues will return the new
                    // vector for similarity scoring during search.
                }
                else
                {
                    // For computed vectorizers: vectors are stored separately, so
                    // removeDeletedNodes() won't call parentMap.get(). Safe to inline.
                    final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(vector);
                    this.executeOrDeferBuilderOp(() ->
                    {
                        if(this.index != null && this.index.containsNode(ordinal))
                        {
                            this.builder.markNodeDeleted(ordinal);
                            this.builder.removeDeletedNodes();
                        }
                        this.builder.addGraphNode(ordinal, vf);
                    });
                }

                // Mark dirty for background managers
                this.markDirtyForBackgroundManagers(1);
            }
        }

        /**
         * Collects and validates vectors from entities.
         */
        private List<VectorEntry> collectVectors(final long firstEntityId, final Iterable<? extends E> entities)
        {
            final List<float[]> vectors = this.vectorize(this.toList(entities));
            return IntStream.range(0, vectors.size())
                .mapToObj(i -> new VectorEntry(firstEntityId + i, vectors.get(i)))
                .toList()
            ;
        }

        private List<? extends E> toList(final Iterable<? extends E> entities)
        {
            if(entities instanceof final List<? extends E> list)
            {
                return list;
            }
            if(entities instanceof final Collection<? extends E> collection)
            {
                return new ArrayList<>(collection);
            }
            final List<E> list = new ArrayList<>();
            entities.forEach(list::add);
            return list;
        }

        /**
         * Adds vector entries to the index.
         */
        private void addVectorEntries(final List<VectorEntry> entries)
        {
            // No synchronized(parentMap) needed — called from GigaMap's synchronized methods.
            this.ensureIndexInitialized();

            if(!this.isEmbedded())
            {
                this.vectorStore.addAll(entries);
            }

            this.markStateChangeChildren();

            if(this.isEventualIndexing())
            {
                // Defer graph updates to background thread as a single batch operation
                this.backgroundTaskManager.enqueueBatchAdd(entries);
            }
            else
            {
                // Drain any deferred builder ops (e.g. from a preceding set() or removeById())
                // before adding new nodes. This avoids interleaving delete+re-add ops from
                // set/remove with batch-add ops, which can corrupt HNSW neighbor lists.
                // Safe to drain here because we hold the GigaMap monitor and cleanup is not
                // in progress (if it were, executeOrDeferBuilderOp below would defer anyway).
                if(!this.cleanupInProgress)
                {
                    this.drainDeferredBuilderOps();
                }

                this.executeOrDeferBuilderOp(() -> this.addGraphNodesSequential(entries));

                // Mark dirty for background managers (with count for debouncing)
                this.markDirtyForBackgroundManagers(entries.size());
            }
        }

        /**
         * Marks dirty for background managers with the specified change count.
         */
        @Override
        public void markDirtyForBackgroundManagers(final int count)
        {
            if(this.backgroundTaskManager != null)
            {
                this.backgroundTaskManager.markDirty(count);
            }
        }

        /**
         * Adds graph nodes sequentially.
         */
        private void addGraphNodesSequential(final List<VectorEntry> entries)
        {
            entries.forEach(entry ->
            {
                final int ordinal = toOrdinal(entry.sourceEntityId);
                final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(entry.vector);
                this.builder.addGraphNode(ordinal, vf);
            });
        }

        @Override
        public void internalRemove(final long entityId, final E entity)
        {
            // No synchronized(parentMap) needed — called from GigaMap's synchronized methods.
            this.ensureIndexInitialized();

            final int ordinal = toOrdinal(entityId);
            if(!this.isEmbedded())
            {
                this.vectorStore.removeById(entityId);
            }

            this.markStateChangeChildren();

            // In incremental mode, mark the ordinal as deleted from disk graph
            // so disk search excludes it immediately, even before background
            // processing has updated the on-disk graph.
            if(this.incrementalMode && this.diskDeletedOrdinals != null)
            {
                this.diskDeletedOrdinals.add(ordinal);
            }

            if(this.isEventualIndexing())
            {
                // Defer graph update to background thread
                this.backgroundTaskManager.enqueueRemove(ordinal);
            }
            else
            {
                // Drain any deferred builder ops before modifying graph nodes.
                if(!this.cleanupInProgress)
                {
                    this.drainDeferredBuilderOps();
                }

                // Mark deleted in the in-memory builder if the node exists there
                // (e.g., added after disk reload). Disk-only nodes are excluded
                // via diskDeletedOrdinals and won't be in the builder.
                if(this.index != null && this.index.containsNode(ordinal))
                {
                    this.executeOrDeferBuilderOp(() -> this.builder.markNodeDeleted(ordinal));
                }

                // Mark dirty for background managers
                this.markDirtyForBackgroundManagers(1);
            }
        }

        @Override
        public void internalRemoveAll()
        {
            // Acquire write lock to ensure no concurrent persistToDisk() Phase 2,
            // search, or background worker mutation is running.
            // closeInternalResources() destroys the graph and disk manager, which would
            // corrupt any in-flight operation.
            // No synchronized(parentMap) needed — called from GigaMap's synchronized methods.
            this.builderLock.writeLock().lock();
            try
            {
                this.ensureIndexInitialized();

                if(!this.isEmbedded())
                {
                    this.vectorStore.removeAll();
                }

                // Reset incremental state before closing resources
                this.incrementalMode = false;
                if(this.diskDeletedOrdinals != null)
                {
                    this.diskDeletedOrdinals.clear();
                    this.diskDeletedOrdinals = null;
                }

                // Shutdown background task manager (discard pending ops — they're stale)
                this.shutdownBackgroundTaskManager(false, false, false);

                this.closeInternalResources();

                // Reinitialize the index (this will also restart background managers if configured)
                this.initializeIndex();
                this.markStateChangeChildren();

                // Mark dirty for background managers
                this.markDirtyForBackgroundManagers(1);
            }
            finally
            {
                this.builderLock.writeLock().unlock();
            }
        }

        @Override
        public float[] getVector(final long entityId)
        {
            if(this.isEmbedded())
            {
                final E entity = this.parentMap().get(entityId);
                if(entity == null)
                {
                    return null;
                }
                return this.vectorizer.vectorize(entity);
            }

            final VectorEntry entry = this.vectorStore.get(entityId);
            if(entry == null)
            {
                return null;
            }
            return entry.vector;
        }

        @Override
        public VectorSearchResult<E> search(final float[] queryVector, final int k)
        {
            return this.doSearch(queryVector, k, this.computeRerankK(k));
        }

        @Override
        public VectorSearchResult<E> search(final float[] queryVector, final int k, final int searchBeamWidth)
        {
            return this.doSearch(queryVector, k, Math.max(k, positive(searchBeamWidth)));
        }

        private VectorSearchResult<E> doSearch(final float[] queryVector, final int k, final int rerankK)
        {
            this.validateDimension(queryVector);

            // Acquire read lock — blocks during cleanup/persistence/removeAll/close,
            // allows concurrent searches and GigaMap mutations.
            // No synchronized(parentMap) — avoids lock-ordering deadlock with
            // internalRemoveAll (which holds the GigaMap monitor and needs the write lock).
            this.builderLock.readLock().lock();
            try
            {
                this.ensureIndexInitialized();

                final VectorFloat<?> query = this.vectorTypeSupport.createFloatVector(queryVector);

                // Choose search strategy based on index mode
                final SearchResult result;
                if (this.incrementalMode)
                {
                    result = this.searchIncremental(query, k, rerankK);
                }
                else if (this.diskManager != null && this.diskManager.isLoaded() && this.diskManager.getDiskIndex() != null)
                {
                    result = this.searchDiskIndex(query, k, rerankK);
                }
                else
                {
                    result = this.searchInMemoryIndex(query, k, rerankK);
                }

                return this.convertSearchResult(result);
            }
            finally
            {
                this.builderLock.readLock().unlock();
            }
        }

        /**
         * Computes the search beam width (rerankK), ensuring a minimum exploration effort
         * regardless of how small k is. This prevents the HNSW search from returning
         * different top-k results depending on the requested k value.
         */
        private int computeRerankK(final int k)
        {
            return Math.max(k, this.configuration.minSearchBeamWidth());
        }

        /**
         * Searches the in-memory index using a pooled GraphSearcher.
         */
        private SearchResult searchInMemoryIndex(final VectorFloat<?> query, final int k, final int rerankK)
        {
            final SearchScoreProvider scoreProvider = DefaultSearchScoreProvider.exact(
                query,
                this.jvectorSimilarityFunction(),
                this.createCachingVectorValues()
            );

            final GraphSearcher searcher = this.inMemorySearcherPool.get();
            // Refresh the view so the searcher sees nodes added or re-added since pool
            // initialization (ConcurrentGraphIndexView uses snapshot isolation based on
            // a completion timestamp captured at creation time).
            final var view = this.index != null ? this.index.getView() : null;
            if(view != null)
            {
                searcher.setView(view);
            }
            final Bits acceptBits = view != null ? view.liveNodes() : Bits.ALL;
            return searcher.search(scoreProvider, k, rerankK, 0f, 0f, acceptBits);
        }

        /**
         * Searches the on-disk index using a pooled GraphSearcher, with optional PQ-based approximate search and reranking.
         */
        private SearchResult searchDiskIndex(final VectorFloat<?> query, final int k, final int rerankK)
        {
            // If PQ is available, use compressed scoring with reranking
            if(this.pqManager != null && this.pqManager.isTrained() && this.pqManager.getCompressedVectors() != null)
            {
                final GraphSearcher searcher = this.inMemorySearcherPool.get();
                return this.pqManager.searchWithRerank(
                    query,
                    k,
                    rerankK,
                    searcher,
                    this.createCachingVectorValues(),
                    this.jvectorSimilarityFunction()
                );
            }

            // Otherwise, search disk index with exact vectors using pooled searcher
            final SearchScoreProvider scoreProvider = DefaultSearchScoreProvider.exact(
                query,
                this.jvectorSimilarityFunction(),
                this.createCachingVectorValues()
            );

            final GraphSearcher searcher = this.inMemorySearcherPool.get();
            return searcher.search(scoreProvider, k, rerankK, 0f, 0f, Bits.ALL);
        }

        /**
         * Searches in incremental mode: queries both the disk graph (for existing data)
         * and the in-memory builder graph (for new mutations), then merges results.
         */
        private SearchResult searchIncremental(final VectorFloat<?> query, final int k, final int rerankK)
        {
            final SearchScoreProvider scoreProvider = DefaultSearchScoreProvider.exact(
                query,
                this.jvectorSimilarityFunction(),
                this.createCachingVectorValues()
            );

            // 1. Search disk graph (excluding deleted/updated ordinals)
            // Use rerankK as topK to give the merge a richer candidate pool
            SearchResult diskResult = null;
            if(this.diskSearcherPool != null)
            {
                final GraphSearcher diskSearcher = this.diskSearcherPool.get();
                final Bits acceptBits = this.createDiskAcceptBits();
                diskResult = diskSearcher.search(scoreProvider, rerankK, rerankK, 0f, 0f, acceptBits);
            }

            // 2. Search in-memory graph (new mutations only)
            SearchResult memResult = null;
            if(this.inMemorySearcherPool != null && this.index != null && this.index.size(0) > 0)
            {
                final GraphSearcher memSearcher = this.inMemorySearcherPool.get();
                // Refresh view so the searcher sees nodes added since pool initialization
                memSearcher.setView(this.index.getView());
                memResult = memSearcher.search(scoreProvider, rerankK, rerankK, 0f, 0f, this.index.getView().liveNodes());
            }

            // 3. Merge results — truncate single-source results to k since sub-graphs
            // over-fetch to provide the merge with a richer candidate pool
            if(diskResult == null && memResult == null)
            {
                return new SearchResult(new SearchResult.NodeScore[0], 0, 0, 0, 0, 0f);
            }
            if(diskResult == null)
            {
                return this.truncateResult(memResult, k);
            }
            if(memResult == null)
            {
                return this.truncateResult(diskResult, k);
            }

            return this.mergeSearchResults(diskResult, memResult, k);
        }

        /**
         * Creates accept bits for disk graph search that excludes deleted/updated ordinals.
         */
        private Bits createDiskAcceptBits()
        {
            if(this.diskDeletedOrdinals == null || this.diskDeletedOrdinals.isEmpty())
            {
                return Bits.ALL;
            }

            // Snapshot into a primitive int[] and find max in a single pass
            // to avoid Integer[] boxing overhead on every search query.
            final Set<Integer> deleted = this.diskDeletedOrdinals;
            final int size = deleted.size();
            final int[] snapshot = new int[size];
            int count = 0;
            int maxOrdinal = -1;
            for(final Integer ord : deleted)
            {
                final int o = ord;
                if(count < size)
                {
                    snapshot[count++] = o;
                }
                if(o > maxOrdinal)
                {
                    maxOrdinal = o;
                }
            }

            if(maxOrdinal < 0)
            {
                return Bits.ALL;
            }

            // Build a primitive boolean[] mask to avoid boxing in the hot search path.
            final boolean[] deletedMask = new boolean[maxOrdinal + 1];
            for(int i = 0; i < count; i++)
            {
                final int ord = snapshot[i];
                if(ord >= 0 && ord <= maxOrdinal)
                {
                    deletedMask[ord] = true;
                }
            }

            return i -> i < 0 || i >= deletedMask.length || !deletedMask[i];
        }

        /**
         * Truncates a SearchResult to at most k entries. Used when a single sub-graph
         * provided all results and the over-fetched candidate pool needs trimming.
         */
        private SearchResult truncateResult(final SearchResult result, final int k)
        {
            final SearchResult.NodeScore[] nodes = result.getNodes();
            if(nodes.length <= k)
            {
                return result;
            }
            return new SearchResult(
                Arrays.copyOf(nodes, k),
                result.getVisitedCount(), 0, 0, 0, 0f
            );
        }

        /**
         * Merges two SearchResults: combines nodes, deduplicates by ordinal
         * (keeping higher score), sorts by score descending, and takes top-k.
         */
        private SearchResult mergeSearchResults(
            final SearchResult diskResult,
            final SearchResult memResult,
            final int k
        )
        {
            final SearchResult.NodeScore[] diskNodes = diskResult.getNodes();
            final SearchResult.NodeScore[] memNodes  = memResult.getNodes();
            final int totalCandidates = diskNodes.length + memNodes.length;

            if(totalCandidates == 0)
            {
                final int visitedCount = diskResult.getVisitedCount() + memResult.getVisitedCount();
                return new SearchResult(new SearchResult.NodeScore[0], visitedCount, 0, 0, 0, 0f);
            }

            /*
             * Primitive open-addressing hash table (int -> float) to deduplicate by ordinal
             * and to avoid the overhead of HashMap<Integer, Float> boxing
             */
            final int tableSize = Integer.highestOneBit(totalCandidates * 2 - 1) << 1;
            final int[]   keys   = new int  [tableSize];
            final float[] values = new float[tableSize];
            Arrays.fill(keys, -1);

            int uniqueCount = 0;
            for (final SearchResult.NodeScore[] nodes : new SearchResult.NodeScore[][]{diskNodes, memNodes})
            {
                for (final SearchResult.NodeScore node : nodes)
                {
                    int idx =
                        (node.node & 0x7fffffff) // strip the sign bit, ensuring a non-negative hash value
                        & (tableSize - 1) // a fast modulo since tableSize is always a power of two
                    ;
                    while (true)
                    {
                        if (keys[idx] == -1) // empty slot — node not yet in the table
                        {
                            keys[idx] = node.node;
                            values[idx] = node.score;
                            uniqueCount++;
                            break;
                        }
                        if (keys[idx] == node.node) // duplicate found — same node already present
                        {
                            if (node.score > values[idx])
                            {
                                values[idx] = node.score;
                            }
                            break;
                        }

                        // collision — different node occupies this slot
                        // advance to the next slot
                        idx = (idx + 1) & (tableSize - 1);
                    }
                }
            }

            // Materialize and sort
            final SearchResult.NodeScore[] all = new SearchResult.NodeScore[uniqueCount];
            int outIdx = 0;
            for(int i = 0; i < tableSize && outIdx < uniqueCount; i++)
            {
                if(keys[i] != -1)
                {
                    all[outIdx++] = new SearchResult.NodeScore(keys[i], values[i]);
                }
            }

            Arrays.sort(all, (a, b) -> Float.compare(b.score, a.score));

            final int resultSize = Math.min(k, all.length);
            final SearchResult.NodeScore[] merged = resultSize == all.length
                ? all
                : Arrays.copyOf(all, resultSize);

            final int visitedCount = diskResult.getVisitedCount() + memResult.getVisitedCount();
            return new SearchResult(merged, visitedCount, 0, 0, 0, 0f);
        }

        /**
         * Creates caching vector values for search operations.
         * Wrapped with {@link NullSafeVectorValues} so that deleted nodes
         * (whose vectors are {@code null}) return a safe placeholder instead
         * of causing NPE/NaN during JVector graph traversal.
         */
        private RandomAccessVectorValues createCachingVectorValues()
        {
            final RandomAccessVectorValues vectorValues = this.isEmbedded()
                ? new EntityBackedVectorValues.Caching<>(
                    this.parentMap(),
                    this.vectorizer,
                    this.configuration.dimension(),
                    this.vectorTypeSupport
                )
                : new GigaMapBackedVectorValues.Caching(
                    this.vectorStore,
                    this.configuration.dimension(),
                    this.vectorTypeSupport
                );
            return new NullSafeVectorValues(vectorValues, this.configuration.dimension(), this.vectorTypeSupport);
        }

        /**
         * Converts internal SearchResult to VectorSearchResult.
         */
        private VectorSearchResult<E> convertSearchResult(final SearchResult result)
        {
            final GigaMap<E> parentMap = this.parentMap();
            final SearchResult.NodeScore[] nodes = result.getNodes();
            final BulkList<ScoredSearchResult.Entry<E>> entries = BulkList.New(nodes.length);
            for(final SearchResult.NodeScore node : nodes)
            {
                // Ordinals (node) ARE entity IDs, so direct conversion
                // Pass parentMap for lazy entity access
                entries.add(new ScoredSearchResult.Entry.Default<>(node.node, node.score, parentMap));
            }
            return new VectorSearchResult.Default<>(entries);
        }

        @Override
        public void optimize()
        {
            // Drain pending indexing operations to ensure graph is complete
            if(this.isEventualIndexing())
            {
                this.backgroundTaskManager.drainQueue();
            }

            this.doOptimize();
        }

        /**
         * Core optimization logic without queue drain.
         * Called directly from the background task manager's executor thread
         * (where inline drain is already done) and from the public optimize() method.
         */
        @Override
        public void doOptimize()
        {
            final GraphIndexBuilder capturedBuilder;

            // Signal sync-mode mutations to defer builder ops during cleanup.
            this.cleanupInProgress = true;
            try
            {
                // Barrier: any in-flight GigaMap mutation (which holds the GigaMap monitor)
                // will complete before we proceed. New mutations see the flag and defer.
                synchronized(this.parentMap())
                {
                    this.ensureIndexInitialized();
                    capturedBuilder = this.builder;
                }

                // cleanup() uses ForkJoinPool internally — must be outside
                // synchronized(parentMap) to avoid deadlock with embedded vectorizers
                // whose worker threads call parentMap.get().
                if(capturedBuilder != null)
                {
                    // Write lock blocks background worker mutations (readLock) and searches.
                    this.builderLock.writeLock().lock();
                    try
                    {
                        capturedBuilder.cleanup();
                    }
                    finally
                    {
                        this.builderLock.writeLock().unlock();
                    }
                }
            }
            finally
            {
                this.cleanupInProgress = false;
            }

            // Apply any deferred sync-mode mutations now that cleanup is done.
            this.drainDeferredBuilderOps();

            this.markStateChangeChildren();
        }

        @Override
        public void persistToDisk()
        {
            if(!this.configuration.onDisk())
            {
                return; // No-op for in-memory indices
            }

            // Drain pending indexing operations to ensure graph is complete
            if(this.isEventualIndexing())
            {
                this.backgroundTaskManager.drainQueue();
            }

            this.doPersistToDisk();
        }

        /**
         * Core persistence logic without queue drain.
         * Called directly from the background task manager's executor thread
         * (where inline drain is already done) and from the public persistToDisk() method.
         */
        @Override
        public void doPersistToDisk()
        {
            if(!this.configuration.onDisk())
            {
                return; // No-op for in-memory indices
            }

            // Signal sync-mode mutations to defer builder ops during cleanup + disk write.
            this.cleanupInProgress = true;
            try
            {
                // Acquire write lock for exclusive access during persistence.
                // This blocks searches, background worker mutations, removeAll, and close.
                this.builderLock.writeLock().lock();
                try
                {
                    // If incremental mode with no changes, skip persist entirely
                    if(this.incrementalMode && this.isIncrementalClean())
                    {
                        LOG.debug("No incremental changes for '{}', skipping persist", this.name);
                        return;
                    }

                    // Captured references for Phase 2 (disk write outside synchronized block)
                    final OnHeapGraphIndex         capturedIndex  ;
                    final RandomAccessVectorValues capturedRavv   ;
                    final PQCompressionManager     capturedPqMgr  ;
                    final DiskIndexManager         capturedDiskMgr;

                    final GraphIndexBuilder        capturedBuilder;

                    // Phase 1: Barrier + reference capture inside synchronized(parentMap).
                    // The barrier ensures any in-flight GigaMap mutation completes.
                    // New mutations see cleanupInProgress=true and defer.
                    synchronized(this.parentMap())
                    {
                        this.ensureIndexInitialized();

                        // If in incremental mode, exit it first by rebuilding the full graph.
                        // Must happen inside synchronized(parentMap) so that
                        // rebuildGraphFromStore() does not race with in-flight mutations.
                        if(this.incrementalMode)
                        {
                            this.exitIncrementalMode();
                        }

                        // If we have an in-memory builder, prepare for disk write
                        if(this.builder == null || this.index == null)
                        {
                            return;
                        }

                        // Initialize disk manager if needed
                        if(this.diskManager == null)
                        {
                            this.diskManager = new DiskIndexManager.Default(
                                this,
                                this.name,
                                this.configuration.indexDirectory(),
                                this.configuration.dimension(),
                                this.configuration.maxDegree(),
                                this.configuration.parallelOnDiskWrite()
                            );
                        }

                        // Capture references for use outside the synchronized block.
                        // The parentMap monitor is released before cleanup and disk write
                        // so that worker threads (ForkJoinPool in cleanup, disk writer)
                        // can freely call parentMap.get() without deadlocking.
                        capturedBuilder = this.builder;
                        capturedIndex   = this.index;
                        capturedRavv    = new NullSafeVectorValues(
                            this.createVectorValues(), this.configuration.dimension(), this.vectorTypeSupport
                        );
                        capturedPqMgr   = this.pqManager;
                        capturedDiskMgr = this.diskManager;
                    }

                    // Phase 2: Cleanup and disk write outside synchronized(parentMap).
                    // builderLock.writeLock() is still held, blocking searches,
                    // background worker mutations, removeAll, and close.
                    // parentMap monitor is released, so ForkJoinPool workers and
                    // disk writer threads can call parentMap.get() for embedded vectors.
                    capturedBuilder.cleanup();
                    capturedDiskMgr.writeIndex(capturedIndex, capturedRavv, capturedPqMgr);

                    // After writing, re-enter incremental mode for fast subsequent operation
                    this.reenterIncrementalMode();
                }
                catch(final IOException ioe)
                {
                    throw new IORuntimeException(ioe);
                }
                finally
                {
                    this.builderLock.writeLock().unlock();
                }
            }
            finally
            {
                this.cleanupInProgress = false;
            }

            // Apply any deferred sync-mode mutations now that cleanup + persistence is done.
            this.drainDeferredBuilderOps();
        }

        /**
         * Returns true if incremental mode has no pending changes:
         * no deletions from disk and the in-memory builder graph is empty.
         */
        private boolean isIncrementalClean()
        {
            final boolean noDeletions = this.diskDeletedOrdinals == null || this.diskDeletedOrdinals.isEmpty();
            final boolean noNewNodes  = this.index == null || this.index.size(0) == 0;
            return noDeletions && noNewNodes;
        }

        /**
         * Exits incremental mode by closing disk resources, rebuilding the full graph
         * from stored vectors, and resetting incremental state.
         * Must be called under builderLock.writeLock().
         */
        private void exitIncrementalMode()
        {
            LOG.info("Exiting incremental on-disk mode for '{}' — rebuilding full graph for persist", this.name);

            // Close disk manager (disk searcher pool is closed by closeSearcherPool below)
            if(this.diskManager != null)
            {
                this.diskManager.close();
                this.diskManager = null;
            }

            // Reset incremental state
            this.incrementalMode = false;
            if(this.diskDeletedOrdinals != null)
            {
                this.diskDeletedOrdinals.clear();
                this.diskDeletedOrdinals = null;
            }

            // Close existing in-memory builder and index
            if(this.builder != null)
            {
                try
                {
                    this.builder.close();
                }
                catch(final IOException e)
                {
                    LOG.warn("Error closing builder during exitIncrementalMode: {}", e.getMessage());
                }
                this.builder = null;
            }
            if(this.index != null)
            {
                this.index.close();
                this.index = null;
            }

            // Close searcher pool (will be re-created)
            this.closeSearcherPools();

            // Reinitialize builder and rebuild full graph from stored vectors
            this.initializeInMemoryBuilder();
            this.rebuildGraphFromStore();
            this.initializeSearcherPool();
        }

        /**
         * Re-enters incremental mode after a disk write: reloads the disk index,
         * resets the in-memory builder to empty, and sets incremental state.
         * Must be called under builderLock.writeLock().
         */
        private void reenterIncrementalMode()
        {
            // Close existing disk manager if present
            if(this.diskManager != null)
            {
                this.diskManager.close();
                this.diskManager = null;
            }

            // Recreate disk manager and load the just-written index
            this.diskManager = new DiskIndexManager.Default(
                this,
                this.name,
                this.configuration.indexDirectory(),
                this.configuration.dimension(),
                this.configuration.maxDegree(),
                this.configuration.parallelOnDiskWrite()
            );

            if(this.diskManager.tryLoad())
            {
                if(this.pqManager != null)
                {
                    this.pqManager.markTrained();
                }

                // Reset in-memory builder to empty (all data is now on disk)
                if(this.builder != null)
                {
                    try
                    {
                        this.builder.close();
                    }
                    catch(final IOException e)
                    {
                        LOG.warn("Error closing builder during reenterIncrementalMode: {}", e.getMessage());
                    }
                }
                if(this.index != null)
                {
                    this.index.close();
                }

                this.initializeInMemoryBuilder();

                // Set incremental state: set state fields first, then flip incrementalMode last for safe publication.
                this.diskDeletedOrdinals = ConcurrentHashMap.newKeySet();
                this.incrementalMode     = true;

                // Reinitialize searcher pools (disk + in-memory)
                this.closeSearcherPools();
                this.initializeSearcherPool();

                LOG.info("Re-entered incremental on-disk mode for '{}'", this.name);
            }
            else
            {
                LOG.warn("Failed to reload disk index for '{}' after persist, staying in full in-memory mode", this.name);
            }
        }

        @Override
        public void trainCompressionIfNeeded()
        {
            if(this.pqManager != null)
            {
                synchronized(this.parentMap())
                {
                    this.ensureIndexInitialized();
                    this.pqManager.trainIfNeeded();
                }
            }
        }

        @Override
        public void clearStateChangeMarkers()
        {
            super.clearStateChangeMarkers();
        }

        @Override
        protected void storeChangedChildren(final Storer storer)
        {
            if(!this.isEmbedded())
            {
                storer.store(this.vectorStore);
            }
        }

        @Override
        protected void clearChildrenStateChangeMarkers()
        {
            // No child state change markers to clear
        }

        @Override
        public void close()
        {
            // Shutdown background task manager — drain indexing, optionally optimize and persist
            this.shutdownBackgroundTaskManager(
                true,
                this.configuration.optimizeOnShutdown(),
                this.configuration.persistOnShutdown()
            );

            // Acquire write lock to ensure no concurrent search or persistToDisk() is running.
            // closeInternalResources() destroys the graph and disk manager.
            this.builderLock.writeLock().lock();
            try
            {
                this.closeInternalResources();
            }
            finally
            {
                this.builderLock.writeLock().unlock();
            }
        }

        /**
         * Shuts down the background task manager.
         *
         * @param drainPending    if true, drain all pending indexing operations
         * @param optimizePending if true and there are pending changes, optimize before shutdown
         * @param persistPending  if true and there are pending changes, persist before shutdown
         */
        private void shutdownBackgroundTaskManager(
            final boolean drainPending,
            final boolean optimizePending,
            final boolean persistPending
        )
        {
            if(this.backgroundTaskManager != null)
            {
                if(!drainPending)
                {
                    this.backgroundTaskManager.discardQueue();
                }
                this.backgroundTaskManager.shutdown(drainPending, optimizePending, persistPending);
                this.backgroundTaskManager = null;
            }
        }

        /**
         * Closes internal resources (builder, index, disk resources).
         * Must be called within synchronized block.
         */
        private void closeInternalResources()
        {
            // Close searcher pools first (searchers reference the index)
            this.closeSearcherPools();

            // Reset incremental mode state
            this.incrementalMode = false;
            if(this.diskDeletedOrdinals != null)
            {
                this.diskDeletedOrdinals.clear();
                this.diskDeletedOrdinals = null;
            }

            if(this.builder != null)
            {
                try
                {
                    this.builder.close();
                }
                catch(final IOException e)
                {
                    throw new RuntimeException("Failed to close index builder", e);
                }
                this.builder = null;
            }

            if(this.index != null)
            {
                this.index.close();
                this.index = null;
            }

            // Close disk manager
            if(this.diskManager != null)
            {
                this.diskManager.close();
                this.diskManager = null;
            }

            // Reset PQ manager
            if(this.pqManager != null)
            {
                this.pqManager.reset();
                this.pqManager = null;
            }
        }

        /**
         * Creates appropriate vector values based on storage mode.
         */
        private RandomAccessVectorValues createVectorValues()
        {
            return this.isEmbedded()
                ? new EntityBackedVectorValues<>(
                    this.parentMap(),
                    this.vectorizer,
                    this.configuration.dimension(),
                    this.vectorTypeSupport
                )
                : new GigaMapBackedVectorValues(
                    this.vectorStore,
                    this.configuration.dimension(),
                    this.vectorTypeSupport
                );
        }


        ///////////////////////////////////////////////////////////////////////////
        // callback interface implementations //
        ////////////////////////////////////////

        // PQCompressionManager.VectorProvider

        @Override
        public long getVectorCount()
        {
            return this.getExpectedVectorCount();
        }

        @Override
        public List<VectorFloat<?>> collectTrainingVectors()
        {
            final List<VectorFloat<?>> vectors = new ArrayList<>();

            if(this.isEmbedded())
            {
                this.parentMap().iterate(entity ->
                    vectors.add(this.vectorTypeSupport.createFloatVector(this.vectorize(entity)))
                );
            }
            else if(this.vectorStore != null)
            {
                this.vectorStore.iterate(entry ->
                    vectors.add(this.vectorTypeSupport.createFloatVector(entry.vector))
                );
            }

            return vectors;
        }

        // DiskIndexManager.IndexStateProvider

        @Override
        public long getExpectedVectorCount()
        {
            if(this.isEmbedded())
            {
                return this.parentMap().size();
            }
            else
            {
                return this.vectorStore != null ? this.vectorStore.size() : 0;
            }
        }

        // ================================================================
        // BackgroundTaskManager.Callback implementation
        // ================================================================

        @Override
        public void applyGraphAdd(final VectorEntry entry)
        {
            // Called from the background indexing worker thread (not from GigaMap's
            // synchronized methods), so we use builderLock.readLock() to coordinate
            // with cleanup (writeLock).
            this.builderLock.readLock().lock();
            try
            {
                final int ordinal = toOrdinal(entry.sourceEntityId);
                final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(entry.vector);
                this.builder.addGraphNode(ordinal, vf);
            }
            finally
            {
                this.builderLock.readLock().unlock();
            }
        }

        @Override
        public void applyGraphBatchAdd(final List<VectorEntry> entries)
        {
            // Acquires the lock once for the entire batch, avoiding per-entry lock overhead.
            this.builderLock.readLock().lock();
            try
            {
                for(final var entry : entries)
                {
                    final int ordinal = toOrdinal(entry.sourceEntityId);
                    final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(entry.vector);
                    this.builder.addGraphNode(ordinal, vf);
                }
            }
            finally
            {
                this.builderLock.readLock().unlock();
            }
        }

        @Override
        public void applyGraphUpdate(final VectorEntry entry)
        {
            this.builderLock.readLock().lock();
            try
            {
                final int ordinal = toOrdinal(entry.sourceEntityId);
                this.builder.markNodeDeleted(ordinal);
                this.builder.removeDeletedNodes();
                final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(entry.vector);
                this.builder.addGraphNode(ordinal, vf);
            }
            finally
            {
                this.builderLock.readLock().unlock();
            }
        }

        @Override
        public void applyGraphRemove(final int ordinal)
        {
            this.builderLock.readLock().lock();
            try
            {
                this.builder.markNodeDeleted(ordinal);
            }
            finally
            {
                this.builderLock.readLock().unlock();
            }
        }


        // ================================================================
        // Builder operation deferral helpers
        // ================================================================

        /**
         * Executes a builder operation immediately, or defers it if cleanup is in progress.
         * Used by sync-mode mutations (called from GigaMap's synchronized methods) which
         * cannot acquire builderLock without risking deadlock with embedded vectorizers.
         */
        private void executeOrDeferBuilderOp(final Runnable op)
        {
            if(this.cleanupInProgress)
            {
                this.deferredBuilderOps.add(op);
            }
            else
            {
                op.run();
            }
        }

        /**
         * Drains and executes all deferred builder operations.
         * Called after cleanup completes (cleanupInProgress is already false).
         */
        private void drainDeferredBuilderOps()
        {
            Runnable op;
            while((op = this.deferredBuilderOps.poll()) != null)
            {
                op.run();
            }
        }

    }

}
