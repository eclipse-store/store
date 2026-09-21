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
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.graph.disk.feature.FeatureId;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.graph.similarity.DefaultSearchScoreProvider;
import io.github.jbellis.jvector.graph.similarity.ScoreFunction;
import io.github.jbellis.jvector.graph.similarity.SearchScoreProvider;
import io.github.jbellis.jvector.quantization.MutablePQVectors;
import io.github.jbellis.jvector.quantization.NVQuantization;
import io.github.jbellis.jvector.quantization.PQVectors;
import io.github.jbellis.jvector.quantization.ProductQuantization;
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
import org.eclipse.store.gigamap.types.BitmapIndex;
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
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
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
 *   <li><b>PQ Compression</b> - Product Quantization for faster traversal of large on-disk indices</li>
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
 *     .approximateScoring(ApproximateScoring.FUSED_PQ) // Optional: faster traversal, larger file
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
     * @throws IllegalArgumentException if queryEntity is null, k &lt;= 0, or the vectorizer
     *                                  returns null for the entity (an entity without an
     *                                  embedding cannot be used as a similarity query)
     * @see #search(float[], int)
     * @see Vectorizer#vectorize(Object)
     */
    public default VectorSearchResult<E> search(final E queryEntity, final int k)
    {
        return this.search(this.requireQueryVector(queryEntity), k);
    }

    /**
     * Searches for the k nearest neighbors to the given entity's vector with an explicit
     * per-query search beam width.
     *
     * @param queryEntity     the query entity whose vector will be extracted via the vectorizer;
     *                        must not be null
     * @param k               the number of nearest neighbors to return; must be positive
     * @param searchBeamWidth the beam width to use for this query; must be positive
     * @return the search result
     * @throws IllegalArgumentException if queryEntity is null, {@code k} / {@code searchBeamWidth}
     *                                  are not positive, or the vectorizer returns null for the
     *                                  entity (an entity without an embedding cannot be used as
     *                                  a similarity query)
     * @see #search(float[], int, int)
     */
    public default VectorSearchResult<E> search(final E queryEntity, final int k, final int searchBeamWidth)
    {
        return this.search(this.requireQueryVector(queryEntity), k, searchBeamWidth);
    }

    /**
     * Extracts the query vector from the given entity, rejecting both a null entity and a null
     * result: an entity without an embedding has no meaningful similarity and cannot be used as
     * a query.
     *
     * @param queryEntity the query entity; must not be null
     * @return the non-null query vector
     * @throws IllegalArgumentException if the entity is null, or the vectorizer returns null for it
     */
    private float[] requireQueryVector(final E queryEntity)
    {
        if(queryEntity == null)
        {
            throw new IllegalArgumentException("Query entity must not be null");
        }
        final float[] queryVector = this.vectorizer().vectorize(queryEntity);
        if(queryVector == null)
        {
            throw new IllegalArgumentException(
                "Query entity has no vector: the vectorizer returned null. "
                    + "Entities without embeddings cannot be used as similarity queries."
            );
        }
        return queryVector;
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
     * <p>
     * An index that holds no vectors has nothing to write: any files from an earlier, non-empty
     * state are removed instead, so the next load rebuilds from the stored vectors. This applies to
     * this explicit persist and to background persistence. A persist on the shutdown path may
     * instead leave those files in place, see {@link #close()}.
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
     * PQ speeds up graph traversal by scoring candidates from compact codes rather than from full
     * vectors, then reranking the best ones exactly. <b>Where those codes live, and what they cost,
     * depends on the mode.</b> {@link ApproximateScoring#FUSED_PQ} writes them into the graph file
     * in addition to the vectors already there, so the file gets larger rather than smaller, and it
     * requires on-disk mode. {@link ApproximateScoring#PQ_IN_MEMORY} keeps them in heap instead,
     * adds nothing to the graph, and works for an in-memory index as well as an on-disk one.
     * <p>
     * Both count here. The deprecated {@link VectorIndexConfiguration#enablePqCompression()} this
     * used to delegate to expresses only {@code FUSED_PQ}, so reading it alone would report
     * {@code false} for an index configured with {@code PQ_IN_MEMORY} and traversing on codes.
     * <p>
     * This reports what was configured; use {@link #isPqCompressionActive()} to find out whether a
     * codebook actually exists yet.
     *
     * @return true if either PQ scoring mode is configured
     * @see VectorIndexConfiguration#approximateScoring()
     * @see VectorIndexConfiguration#pqSubspaces()
     */
    public default boolean isPqCompressionEnabled()
    {
        final ApproximateScoring scoring = this.configuration().approximateScoring();
        return scoring == ApproximateScoring.FUSED_PQ || scoring == ApproximateScoring.PQ_IN_MEMORY;
    }

    /**
     * Returns whether PQ compression is actually in effect, as opposed to merely configured.
     * <p>
     * {@link #isPqCompressionEnabled()} reports what was asked for. This reports what the index is
     * doing: it is {@code false} until a codebook exists. An index can therefore be enabled but not
     * yet active, while it is still too small to train or before the event that would train it.
     * <p>
     * <b>Which event that is depends on the mode.</b> For an on-disk index it is the first persist
     * at which at least 256 vectors are present, and thereafter every load of a graph that carries
     * the codes - fused into the graph for {@link ApproximateScoring#FUSED_PQ}, or in the sidecar
     * for {@link ApproximateScoring#PQ_IN_MEMORY}. For an <i>in-memory</i> index configured with
     * {@link ApproximateScoring#PQ_IN_MEMORY} there is no persist to hang it on, so it is the first
     * optimization that finds enough vectors - which is why that mode requires a scheduled one.
     *
     * @return true if a PQ codebook is currently held for this index
     * @see #isPqCompressionEnabled()
     */
    public default boolean isPqCompressionActive()
    {
        return false;
    }

    /**
     * Returns whether NVQ vector storage is actually in effect, as opposed to merely configured.
     * <p>
     * The storage-dimension counterpart of {@link #isPqCompressionActive()}, and it answers the same
     * kind of question: {@link VectorIndexConfiguration#vectorStorage()} reports what was asked for,
     * this reports what the index is doing. It is {@code false} until a quantizer exists, which
     * happens on the first persist with enough vectors and again on every load of a graph that
     * carries quantized vectors.
     * <p>
     * Defaults to {@code false} so that an implementation predating this method still compiles and
     * links.
     *
     * @return true if an NVQ quantizer is currently held for this index
     * @see VectorIndexConfiguration#vectorStorage()
     * @see #isPqCompressionActive()
     */
    public default boolean isNvqCompressionActive()
    {
        return false;
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
     * @return the vector as a float array, or {@code null} if the entity has no vector — either
     *         because no such entity is indexed, or because the entity has no embedding (when
     *         the vectorizer {@link Vectorizer#allowsNullVectors() allows null vectors})
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

        public default void internalAddAll(final long firstEntityId, final E[] entities)
        {
            this.internalAddAll(firstEntityId, Arrays.asList(entities));
        }

        public void internalUpdate(long entityId, E replacedEntity, E entity);

        public void internalRemove(long entityId, E entity);

        public void internalRemoveAll();

        public void clearStateChangeMarkers();

        /**
         * Trains the PQ codebook now, if compression is enabled, none is held yet and enough vectors
         * exist.
         * <p>
         * No longer necessary: {@code persistToDisk()} trains the codebook itself, which is what
         * makes {@link VectorIndexConfiguration#enablePqCompression()} take effect. This remains for
         * source and binary compatibility - {@code Internal} is a public interface in an exported
         * package - and as a way to pay the training cost at a chosen moment rather than inside the
         * first persist. Calling it is otherwise a no-op.
         * <p>
         * <b>It does nothing for an in-memory index using
         * {@link ApproximateScoring#PQ_IN_MEMORY}</b>, and that is deliberate rather than an
         * oversight. There a codebook on its own is not a usable state: the transition also encodes
         * every ordinal and rebuilds the graph to score from the codes, and it happens at
         * optimization, under the parent GigaMap's monitor. Training here would leave the manager
         * holding a codebook
         * that the switch reads as work already done, so the index would report compressed scoring
         * while still traversing exactly, with nothing able to correct it. Call {@code optimize()}
         * to pay that cost at a chosen moment instead; {@code isPqCompressionActive()} reports when
         * the transition has happened.
         *
         * @deprecated training is part of the persist path; this method is no longer required
         */
        @Deprecated
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
               TrainingVectorProvider,
               DiskIndexManager.IndexStateProvider
    {
        private static final Logger LOG = LoggerFactory.getLogger(Default.class);

        /**
         * Fixed seed for the PQ training-set reservoir sample, so that training the same data twice
         * yields the same codebook. The value itself is arbitrary.
         */
        private static final long RESERVOIR_SEED = 0x5EEDL;

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

        // Persisted, monotonically increasing count of graph-affecting mutations (add / remove /
        // vec↔null transition). It is the witness used to detect, on reload, whether the persisted
        // GigaMap state has advanced past the on-disk graph: the value is stamped into the disk
        // .meta at persist time and compared against the store-recovered value in
        // DiskIndexManager.verifyMetadata. Unlike parentMap().size()/highestUsedId(), it changes on
        // a vec↔null transition, so it catches the crash-restart window those proxies are blind to.
        // Not transient — it must survive (de)serialization.
        //
        // Volatile because it is also the witness the in-memory PQ switch revalidates against before
        // adopting a replacement. Mutations bump it holding only the parentMap monitor, and that
        // revalidation happens under builderLock with the monitor released, so the write lock
        // supplies no happens-before edge between them: without volatile the switch could read a
        // stale count and adopt a graph built before a mutation it never saw.
        volatile long structuralModCount;

        // HNSW graph components (transient - rebuilt on load)
        // Volatile: the pair is swapped wholesale by exitIncrementalMode / reenterIncrementalMode /
        // initializeInMemoryBuilder under builderLock.writeLock(), but read by sync-mode mutations and
        // by drainDeferredBuilderOps, neither of which holds that lock. Without volatile a reader can
        // observe a torn pair (stale index, fresh builder), which is how a guard evaluated on `index`
        // ended up authorizing a mutation on a different `builder` (internal #142). Code that mutates
        // the graph must additionally read `builder` ONCE into a local and derive the graph from it
        // (see internalReplaceGraphNode) so guard and mutation cannot target different objects.
        private transient VectorTypeSupport          vectorTypeSupport;
        private transient volatile GraphIndexBuilder builder          ;
        private transient volatile OnHeapGraphIndex  index            ;

        // Managers (transient - recreated on load)
        private transient DiskIndexManager      diskManager          ;
        private transient PQCompressionManager  pqManager            ;
        private transient NVQCompressionManager nvqManager           ;

        /**
         * The PQ codes an in-memory index scores from, or {@code null} unless
         * {@link ApproximateScoring#PQ_IN_MEMORY} is configured without {@code onDisk}.
         * <p>
         * Maintained incrementally by the mutation paths and read by both the graph builder and the
         * search path. Mutable rather than the immutable form the on-disk sidecar uses, because
         * there is no persist to rebuild it at: it has to track every add, update and remove.
         * <p>
         * Costs {@code pqSubspaces} bytes for every ordinal up to the highest in use, so a map whose
         * id space is far larger than its live count pays for the gap - see
         * {@link #warnIfOrdinalSpaceIsSparse()}.
         */
        private transient volatile MutablePQVectors inMemoryPqVectors;

        /**
         * {@code structuralModCount} at which a PQ training attempt last declined or failed, or
         * {@code -1} if none has. Guards the training carve-out in {@code doPersistToDisk} so a
         * repeatedly-declining index does not force a full graph rebuild on every idle persist.
         * Transient like the rest of the PQ state: a fresh session simply tries once more.
         */
        private transient volatile long pqTrainingDeclinedAtModCount = -1L;

        /**
         * {@code structuralModCount} at which an NVQ training attempt last failed, or {@code -1} if
         * none has.
         * <p>
         * The NVQ counterpart of {@code pqTrainingDeclinedAtModCount}, guarding against less but
         * not against nothing. NVQ declines on a far smaller sample than PQ - 16 vectors against
         * 256, and as a sanity floor rather than a mathematical requirement, since a mean needs one
         * vector and not 256 clusters per subspace - but it does decline, and an embedded index
         * with enough entities and too few embeddings can land under that floor. This witness
         * covers that as well as an outright exception: it is set whenever an attempt leaves the
         * manager untrained, whichever of the two happened. The name records the commoner case, not
         * the only one.
         */
        private transient volatile long nvqTrainingFailedAtModCount = -1L;
                transient BackgroundTaskManager backgroundTaskManager;

        // GraphSearcher pool for thread-local reuse
        private transient ExplicitThreadLocal<GraphSearcher> inMemorySearcherPool;

        // Computed-mode fast index: source entity id (graph ordinal) -> vector store's own
        // internal entity id. Lets vector lookups use a direct, lazy vectorStore.get(internalId)
        // instead of an index query per call (which is far too costly on the hot scoring path),
        // while still resolving by source entity id so it stays correct when the store's id
        // allocator drifts from the parent's (deletion holes, or entities without an embedding
        // that get no store entry). Null in embedded mode.
        //
        // Built lazily on first access via computedIdIndex(): from the vector store's identity index
        // bitmaps (cheap, loads no VectorEntry / no vectors) once the store is queryable, falling back
        // to a positional store scan during the deserialization window when the index registry is not
        // yet wired. Deferring the build off the load path is what keeps incremental on-disk startup
        // O(1) in the vector payload.
        //
        // Concurrency: a ConcurrentHashMap. The volatile FIELD is published (assigned) once under the
        // vector store's own monitor by the double-checked lazy build in computedIdIndex() (a leaf
        // lock — see there); search threads read it lock-free. Per-entry put/remove by mutations are
        // not synchronized on that monitor — they rely on the map's own thread-safety and are serialized
        // against each other by the parent GigaMap monitor the mutation already holds.
        private transient volatile Map<Long, Long> computedIdIndex;

        // One-shot guard for the deferred graph rebuild. The HNSW graph is transient and must be
        // rebuilt from the store after deserialization, but that rebuild iterates the parent GigaMap
        // / vector store (a nested object-graph load) and therefore may NOT run inside the enclosing
        // deserialization's complete() phase (internal #87). So complete() only does the nested-load-
        // free transient setup (initializeAfterLoad -> initializeIndex); the graph rebuild is deferred
        // to the first search/mutation/optimize via ensureGraphRebuilt(), which runs it exactly once
        // under the parent GigaMap monitor (see there). Volatile: fast-path read lock-free; the actual
        // rebuild + set happens under synchronized(parentMap()). Also set true by the runtime rebuild
        // in exitIncrementalMode() so it is not repeated, and by the standard constructor, to keep the
        // deferred rebuild from racing the one-time registration back-fill that populates a newly
        // created index (see there).
        private transient volatile boolean                   graphRebuilt       ;

        // Incremental on-disk mode: after disk reload, use disk index for search
        // and in-memory builder only for new mutations. Full rebuild deferred to persistToDisk().
        // Volatile: written under writeLock (reenterIncrementalMode, exitIncrementalMode)
        // but read by mutation methods (internalUpdate, internalRemove) under parentMap
        // monitor only, without builderLock.
        /**
         * Whether this instance came back from storage rather than being newly constructed.
         * <p>
         * Set by {@link #initializeAfterLoad()} and then left alone: it is a fact about this
         * instance, not a request to be consumed. {@link #needsUnearnedOptimization()} reads it on
         * every scheduled tick. The distinction matters because only a restored index is unable to
         * earn a scheduled optimization: its entities are already in the store, so nothing bumps
         * the change count for them.
         * <p>
         * Deliberately not inferred from the vector count. An index registered on an
         * already-populated GigaMap reads a non-empty count here too, while being every bit as new
         * as one registered on an empty map - its entities arrive immediately afterwards through
         * the backfill in {@code VectorIndices.add}, and each of them counts.
         */
        private transient boolean                            restoredFromStorage;

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

        /**
         * Whether {@link #close()} has torn this index down for good.
         * <p>
         * Distinct from simply having no transient state: {@code internalRemoveAll} also closes
         * everything and then rebuilds it, and an index that has never been used has nothing yet
         * either. Both of those should still initialise on demand. A closed one must not, and
         * nothing else can tell the three apart.
         * <p>
         * Written and read under {@code builderLock.writeLock()}, which is where its guarantee
         * comes from rather than from being volatile - see {@link #close()}. Volatile as well so a
         * read outside that lock sees something current rather than stale.
         */
        private transient volatile boolean                closed           ;

        /**
         * Whether a codebook was trained outside a persist and has not been written yet.
         * <p>
         * {@code trainCompressionIfNeeded()} exists so a caller can pay the training cost at a
         * moment of their choosing. That leaves the state a persist would normally produce for
         * itself already made, and both of the questions the clean-incremental shortcut asks then
         * answer the wrong way: this persist trained nothing, and nothing is pending either,
         * because the manager is trained. The shortcut would skip the write that has to put the
         * codebook on disk, and the index would report compression as active while the graph did
         * not carry it.
         * <p>
         * Cleared once a persist has actually written, not merely once one has started: a persist
         * that fails leaves the codebook exactly as stranded as before, so the next one has to try
         * again.
         */
        private transient volatile boolean                compressionTrainedOutsidePersist;
        private transient ConcurrentLinkedQueue<Runnable> deferredBuilderOps;

        // Test-only seam: run once at the start of persist Phase 2 (parentMap monitor released,
        // builder about to be swapped by reenterIncrementalMode). Lets a test deterministically inject
        // a mutation into the exact window where a deferred builder op is drained after the swap.
        // Null (and a no-op) in production. See VectorIndex(persist-window deletion) regression test.
        transient volatile Runnable persistPhase2TestHook;

        // Test-only seam: run once the PQ replacement is complete and before it is published, with
        // the parentMap monitor held. A codebook exists at that point but no codes are visible,
        // which is the window isPqCompressionActive() must still report exact scoring in, and the
        // window a concurrent search must come through on the pre-switch graph.
        // Null (and a no-op) in production.
        transient volatile Runnable pqSwitchPublishTestHook;

        // Test-only seam: run on entry to drainDeferredBuilderOps, BEFORE the parentMap monitor is
        // acquired. Lets a test collide two drains deterministically (persist thread vs application
        // thread) to assert that the drain is serialized. Null (and a no-op) in production.
        // See VectorIndexPersistWindowConcurrencyTest.
        transient volatile Runnable drainEntryTestHook;


        ///////////////////////////////////////////////////////////////////////////
        // constructors //
        /////////////////

        /**
         * Standard constructor for creating a new index. Sets up the transient state but leaves the graph
         * empty: populating it with the entities the parent map already holds is the caller's job, done
         * exactly once by {@code VectorIndices.Default#internalAddVectorIndex}.
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

            // Initialize builder lock early (before initializeIndex)
            this.builderLock         = new ReentrantReadWriteLock();
            this.deferredBuilderOps  = new ConcurrentLinkedQueue<>();

            // Publish the one-shot rebuild guard right away, so the deferred rebuild never runs for an
            // index created here: this one is populated by the back-fill in
            // VectorIndices.Default#internalAddVectorIndex, immediately after construction, and that is
            // its single population path - embedded and computed alike. A rebuild would not find an empty
            // source and stay a no-op: in embedded mode rebuildGraphFromStore() reads the *parent map*, so
            // it would create a node per existing entity and the back-fill would then add each one a second
            // time - jvector rejects the duplicate with "Node 0 already exists" (internal #123). Rebuilding
            // from the store is meaningful only for an index that has a store to rebuild, i.e. after a load.
            // Set before initializeIndex() so a background task started there cannot observe a false flag
            // and rebuild behind the back-fill.
            this.graphRebuilt = true;

            // Transient setup only - deliberately NOT ensureIndexInitialized(), see above.
            this.initializeIndex();
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
         * Nested-load-free transient setup run from the binary handler's {@code complete()} during
         * deserialization: (re)creates the transient managers, locks and (empty) in-memory builder,
         * and, for on-disk indices, loads the disk graph. It deliberately does NOT rebuild the
         * in-memory graph from the store, because that rebuild iterates the parent GigaMap / vector
         * store (a nested object-graph load) which must not run inside the enclosing load's
         * completeInstances phase (internal #87). The rebuild is deferred to first access via
         * {@link #ensureGraphRebuilt()}.
         */
        void initializeAfterLoad()
        {
            // Before initializeIndex, which is where the flag is read. This is the only path that
            // reaches an index restored from storage, which is what makes it the discriminator the
            // optimization bootstrap needs - see startBackgroundManagersIfEnabled().
            this.restoredFromStorage = true;

            if(!this.isIndexPresent())
            {
                this.initializeIndex();
            }
            // graph rebuild intentionally deferred to first access — see ensureGraphRebuilt()
        }

        /**
         * Ensures the transient HNSW index is initialized and its graph rebuilt from the store.
         * Called at the start of every search / mutation / optimize / persist entry point.
         */
        void ensureIndexInitialized()
        {
            if(!this.isIndexPresent())
            {
                this.initializeIndex();
            }

            this.ensureGraphRebuilt();
        }

        /**
         * Returns whether the transient index state exists, i.e. whether there is either an
         * in-memory builder or a loaded disk index. {@code false} before the first
         * {@link #initializeIndex()} and after {@link #closeInternalResources()} has torn the
         * state down again.
         *
         * @return {@code true} if an in-memory builder or a loaded disk index is present
         */
        private boolean isIndexPresent()
        {
            final boolean diskLoaded = this.diskManager != null && this.diskManager.isLoaded();
            return this.builder != null || diskLoaded;
        }

        /**
         * Rebuilds the in-memory graph from the store exactly once, lazily, on first access after a
         * load — the step deferred out of {@code complete()} (internal #87). Runs under the parent
         * GigaMap monitor: mutation / optimize / persist callers already hold it (reentrant), and the
         * search path calls this before taking {@code builderLock.readLock()}, so it never holds the
         * read lock and the monitor at the same time.
         * <p>
         * Deadlock-free despite the builder-swapping paths using opposite lock orders
         * ({@code internalRemoveAll}: monitor then {@code builderLock.writeLock()};
         * {@code doPersistToDisk}: write lock then monitor): the rebuild acquires NO
         * {@code builderLock} while it holds the monitor — {@code addGraphNodesSequential} adds nodes
         * to the builder directly, and {@code collectStoredVectors} only re-enters this monitor (and,
         * in computed mode, the vector store's own leaf monitor). A thread rebuilding here therefore
         * holds only the monitor and waits for no lock a write-lock holder needs, so it cannot sit in
         * a monitor↔builderLock cycle.
         * <p>
         * Because every builder-swapping path also calls {@code ensureIndexInitialized()} before its
         * swap, the first caller wins the rebuild under the monitor and publishes {@code graphRebuilt};
         * all others see the flag and skip. Skipped in incremental on-disk mode (the disk index serves
         * search; the in-memory graph is rebuilt later by {@code exitIncrementalMode}).
         */
        private void ensureGraphRebuilt()
        {
            if(this.graphRebuilt || this.incrementalMode)
            {
                return;
            }
            synchronized(this.parentMap())
            {
                if(this.graphRebuilt || this.incrementalMode)
                {
                    return;
                }
                this.rebuildGraphFromStore();
                this.graphRebuilt = true;
            }
        }

        /**
         * Switches this in-memory index to PQ-compressed scoring, if one is due.
         * <p>
         * An index cannot start compressed. {@code GraphIndexBuilder} takes its
         * {@code BuildScoreProvider} at construction and a codebook needs at least 256 vectors, so
         * the switch is necessarily a rebuild: train a codebook, encode every live ordinal, build a
         * fresh builder against the codes, replay the nodes into it, and publish the result.
         * <p>
         * <b>Must be called with the {@code parentMap} monitor held, and it takes no
         * {@code builderLock}.</b> That is {@link #ensureGraphRebuilt()}'s posture, for the reason
         * given there: a thread that holds only the monitor waits for no lock a write-lock holder
         * needs, so it cannot sit in a monitor/{@code builderLock} cycle. Everything this reads
         * reaches the GigaMap - whether training is pending, the training sample, the vectors to
         * replay, the ordinal-space warning - and the monitor is the lock that makes those reads
         * consistent.
         * <p>
         * <b>The replaced builder and graph are deliberately not closed.</b> Searches hold
         * {@code builderLock.readLock()} and never this monitor, so one can be traversing the old
         * graph at the instant it is replaced; closing it would pull the graph out from under that
         * search. Both are ordinary heap objects, so leaving them to the garbage collector costs
         * nothing but the collection itself, and the switch runs at most once per index generation.
         * Not closing them is also what makes the concurrency argument below hold.
         * <p>
         * <b>Why no exclusion of the background indexing worker is needed.</b> Its callbacks take
         * the read lock, not this monitor, so one can run throughout. It cannot corrupt the result:
         * <ul>
         * <li>Every such callback was enqueued by a GigaMap mutation that had already applied to the
         *     map <i>under this monitor</i>. The snapshot below is taken under the same monitor, so
         *     it already reflects every callback still in flight - the replay puts those entities in
         *     the replacement graph itself.</li>
         * <li>A callback that is still holding the old builder therefore writes a change the
         *     replacement already has. It lands in the graph being abandoned, which is harmless
         *     precisely because that graph is not closed.</li>
         * <li>The replacement is unreachable until the last statements here publish it, so nothing
         *     can insert into it while the replay is filling it. Afterwards callbacks find it
         *     through {@code internalAddGraphNodeIdempotent}, which tolerates an ordinal the graph
         *     already carries.</li>
         * </ul>
         * On the scheduled path the question does not even arise: the manager drains pending
         * indexing ops inline on its single executor thread before calling {@code doOptimize}.
         */
        private void performPqSwitch()
        {
            // Read once. close() does not take this monitor, so closeInternalResources can null the
            // field between two reads of it and turn the guard below into an NPE.
            final PQCompressionManager manager = this.pqManager;

            if(this.configuration.onDisk()
                || this.configuration.approximateScoring() != ApproximateScoring.PQ_IN_MEMORY
                || this.inMemoryPqVectors != null
                || manager == null
                || manager.isTrained()
                || !this.isPqTrainingPending())
            {
                return;
            }

            final List<VectorFloat<?>> sample = this.collectTrainingVectors(
                PQCompressionManager.MAX_TRAINING_VECTORS);
            if(sample.size() < PQCompressionManager.MIN_VECTORS_FOR_PQ_TRAINING)
            {
                this.pqTrainingDeclinedAtModCount = this.getStructuralModCount();
                return;
            }

            this.warnIfOrdinalSpaceIsSparse();

            // The generation this switch is being built for. close() and internalRemoveAll take the
            // write lock without this monitor, so either can replace or null the builder while the
            // work below runs; publishing regardless would resurrect a graph on an index that has
            // been torn down, with its searcher pools already closed.
            final GraphIndexBuilder plannedFor = this.builder;

            try
            {
                manager.trainFrom(sample);
            }
            catch(final RuntimeException e)
            {
                LOG.warn("PQ training failed for '{}', staying on exact scoring", this.name, e);
            }

            if(!manager.isTrained())
            {
                this.pqTrainingDeclinedAtModCount = this.getStructuralModCount();
                return;
            }

            // Either exit from here leaves the manager untrained, and for the same reason. A
            // codebook trained for a switch that did not happen would make every later attempt
            // return at the isTrained() guard above while no codes were ever published, so the
            // index would report the switch as done and go on scoring exactly - permanently.
            // Unambiguous here in a way it was not when this ran off-lock: the monitor is held,
            // nothing was published, and this is the manager the training went into.
            boolean published = false;
            try
            {
                published = this.buildAndPublishPqReplacement(manager, plannedFor);
            }
            finally
            {
                if(!published)
                {
                    manager.reset();
                }
            }
        }

        /**
         * Builds the compressed replacement and publishes it, the second half of
         * {@link #performPqSwitch()}.
         * <p>
         * Separate so that the caller can roll the trained codebook back on either way out of here -
         * a throw, or a replacement that turned out to describe an index that no longer exists.
         * Carries the same preconditions: {@code parentMap} monitor held, no {@code builderLock},
         * and a trained {@code manager}.
         *
         * @param manager    the compression manager holding the freshly trained codebook
         * @param plannedFor the builder this switch was planned against, which must still be the
         *                   index's own for the replacement to describe it
         * @return whether the replacement was published
         */
        private boolean buildAndPublishPqReplacement(
            final PQCompressionManager manager   ,
            final GraphIndexBuilder    plannedFor
        )
        {
            final ProductQuantization codebook = manager.getPQ();
            if(codebook == null)
            {
                // Trained but with no codebook to show for it. Nothing to build against, and the
                // caller's rollback puts the manager back to untrained so a later optimization can
                // try again.
                throw new IllegalStateException(
                    "PQ training reported success for '" + this.name + "' but produced no codebook");
            }

            final MutablePQVectors codes = new MutablePQVectors(codebook);

            final GraphIndexBuilder replacement = new GraphIndexBuilder(
                BuildScoreProvider.pqBuildScoreProvider(this.jvectorSimilarityFunction(), codes),
                this.configuration.dimension(),
                this.configuration.maxDegree(),
                this.configuration.beamWidth(),
                this.configuration.neighborOverflow(),
                this.configuration.alpha(),
                true // use hierarchical index
            );

            for(final VectorEntry entry : this.collectStoredVectors())
            {
                if(entry.vector == null)
                {
                    // Entities without an embedding are not graph nodes.
                    continue;
                }
                final int            ordinal = toOrdinal(entry.sourceEntityId);
                final VectorFloat<?> vf      = this.vectorTypeSupport.createFloatVector(entry.vector);

                // The code before the node, always: the builder scores the incoming node against
                // the codes while inserting it, so its own has to be there first.
                codes.encodeAndSet(ordinal, vf);
                replacement.addGraphNode(ordinal, vf);
            }

            // Test-only injection point: the replacement is complete and still unpublished.
            // No-op in production.
            final Runnable hook = this.pqSwitchPublishTestHook;
            if(hook != null)
            {
                hook.run();
            }

            if(this.builder != plannedFor)
            {
                // A teardown took the write lock while this was being built - close() nulls the
                // builder, internalRemoveAll replaces it - so the replacement describes an index
                // that no longer exists. Dropping it is the whole response: the caller's rollback
                // untrains the codebook, and a later optimization plans again from whatever the
                // index is then. Nothing has been published, so there is nothing else to undo.
                LOG.info("Discarding the PQ switch for '{}': the index was torn down or rebuilt"
                    + " while the replacement was being built", this.name);
                return false;
            }

            // Publication, and the order matters: the codes first, then the graph, then the
            // builder that scores from them. All three fields are volatile, which is what makes
            // that ordering mean anything to another thread.
            //
            // The codes lead because the replacement builder cannot be used without them. It
            // scores every incoming node against the code store, and trackPqCode is a no-op while
            // there are none - so a background callback that reached the new builder first would
            // insert a node with no code of its own, linking it by a code that is not there and
            // leaving it that way for good. Nothing else has that dependency: the codes are keyed
            // by ordinal, ordinals are source entity ids, and both graphs carry the same ones, so
            // codes that arrive before the graph are already correct for the graph still in place.
            //
            // The cost is that isPqCompressionActive() and the search path, which read the codes
            // with no lock, can see them a few instructions before the new graph. That reports
            // nothing untrue: at that moment the codes really are what a search would score from,
            // and it would score correctly, because they describe the old graph's ordinals just as
            // well as the replacement's.
            this.inMemoryPqVectors = codes;

            this.index   = (OnHeapGraphIndex)replacement.getGraph();
            this.builder = replacement;

            LOG.info("Switched in-memory index '{}' to PQ-compressed scoring ({} codes)",
                this.name, codes.count());
            return true;
        }

        /**
         * Keeps the in-memory PQ codes in step with a node being added or re-added.
         * <p>
         * Called immediately before the node reaches the builder, and that order is required rather
         * than tidy: once compressed scoring is on, the builder scores the new node against the codes
         * while inserting it, so its own code has to be there already.
         * <p>
         * A no-op unless an in-memory index has switched to compressed scoring.
         * {@code MutablePQVectors} handles its own growth and is safe to call from the builder's
         * worker threads, so this needs no lock of its own beyond the one the caller already holds.
         *
         * @param ordinal the graph ordinal, which is the source entity id
         * @param vf      the vector being indexed
         */
        private void trackPqCode(final int ordinal, final VectorFloat<?> vf)
        {
            final MutablePQVectors codes = this.inMemoryPqVectors;
            if(codes != null && vf != null)
            {
                codes.encodeAndSet(ordinal, vf);
            }
        }

        /**
         * Drops the in-memory PQ code for an ordinal that has left the graph.
         * <p>
         * Zeroed rather than removed: the codes are a dense array indexed by ordinal, so there is no
         * hole to make. Leaving the old code in place would let traversal keep scoring a node that no
         * longer exists as though it were a good candidate - harmless for correctness, since the
         * accept bits exclude it from the result, but it would waste hops on a dead neighbourhood.
         *
         * @param ordinal the graph ordinal that was removed
         */
        private void untrackPqCode(final int ordinal)
        {
            final MutablePQVectors codes = this.inMemoryPqVectors;
            if(codes != null)
            {
                codes.setZero(ordinal);
            }
        }

        /**
         * Warns when the ordinal space is far larger than the live vector count.
         * <p>
         * The codes are indexed by graph ordinal, which is the source entity id, so the array spans
         * every id ever allocated rather than only the ones still in use. A map that has churned
         * through many ids therefore pays for the gap, and removals never shrink it. There is no
         * safe automatic remedy - renumbering would break the "graph ordinal == entity id" invariant
         * the whole integration keys on - so this reports the cost rather than trying to avoid it.
         */
        private void warnIfOrdinalSpaceIsSparse()
        {
            final long highest = this.getHighestEntityId();
            // The graph's node count, not getVectorCount(): that returns parentMap().size() for an
            // embedded vectorizer, so 100 vectors among 900 entities without one would read as 1000
            // live and this would stay quiet on exactly the shape it exists for - the codes are
            // allocated per ordinal, so those 900 holes are what is being paid for.
            final OnHeapGraphIndex current = this.index;
            final long live = current != null ? current.size(0) : this.getVectorCount();
            if(live > 0 && highest + 1 > live * 4)
            {
                LOG.warn(
                    "In-memory PQ codes for '{}' span {} ordinals for {} live vectors, so about {}% of the"
                        + " allocated bytes cover ids that no longer exist. The codes are indexed by entity"
                        + " id and removals do not compact it.",
                    this.name, highest + 1, live, 100 - (100 * live / (highest + 1))
                );
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

        /**
         * Returns the computed-mode {@link #computedIdIndex}, building it lazily on first access.
         * {@code null} in embedded mode (or before the vector store exists).
         * <p>
         * The build is deferred out of {@code complete()}: at deserialization time the vector store's
         * index registry is not yet queryable, and — more importantly — building eagerly there would
         * reintroduce the O(n) I/O + heap spike that incremental on-disk mode exists to avoid. On first
         * access the map is reconstructed cheaply from the identity index bitmaps (no {@link VectorEntry},
         * no vectors); see {@link #buildComputedIdIndex()}.
         * <p>
         * Thread-safety: double-checked with {@link #computedIdIndex} volatile. The build holds the
         * vector store's own monitor — a leaf lock independent of the parent GigaMap monitor and
         * {@code builderLock}, so triggering it from a search (under {@code builderLock.readLock}) or a
         * mutation (under the parent monitor) cannot deadlock. Mutations route their store lookups
         * through this accessor too, so the map is present before any {@code put}/{@code get}.
         */
        private Map<Long, Long> computedIdIndex()
        {
            if(this.isEmbedded() || this.vectorStore == null)
            {
                return null;
            }
            Map<Long, Long> idx = this.computedIdIndex;
            if(idx == null)
            {
                synchronized(this.vectorStore)
                {
                    idx = this.computedIdIndex;
                    if(idx == null)
                    {
                        this.computedIdIndex = idx = this.buildComputedIdIndex();
                    }
                }
            }
            return idx;
        }

        /**
         * Builds the {@code sourceEntityId -> storeId} map, from the vector store's
         * {@code sourceEntityId} identity index where possible
         * ({@link #buildComputedIdIndexFromIndex()}), else by a positional store scan
         * ({@link #buildComputedIdIndexByScan()}) during the {@code complete()} deserialization
         * window, when the index registry is not yet queryable. Both paths yield the same map.
         */
        private Map<Long, Long> buildComputedIdIndex()
        {
            final Map<Long, Long> fromIndex = this.buildComputedIdIndexFromIndex();
            return fromIndex != null
                ? fromIndex
                : this.buildComputedIdIndexByScan()
            ;
        }

        /**
         * The preferred {@link #buildComputedIdIndex()} path: reconstructs the map from the vector
         * store's {@code sourceEntityId} identity index bitmaps, loading no {@code VectorEntry} and
         * therefore no stored vectors. Returns {@code null} when that index is not queryable yet, in
         * which case the caller falls back to {@link #buildComputedIdIndexByScan()}.
         * <p>
         * Package-private for {@code VectorIndexIdDriftTest}, which asserts both paths agree.
         */
        Map<Long, Long> buildComputedIdIndexFromIndex()
        {
            final BitmapIndex<VectorEntry, Long> idIndex = this.vectorStore.index().bitmap()
                .get(Long.class, VectorEntry.SOURCE_ENTITY_ID_INDEXER.name());
            if(idIndex == null)
            {
                return null;
            }

            final Map<Long, Long> index = new ConcurrentHashMap<>();
            // (key = sourceEntityId, entityId = storeId) — no value loaded.
            idIndex.iterateKeyEntityPairs((sourceEntityId, storeId) -> index.put(sourceEntityId, storeId));
            return index;
        }

        /**
         * The {@link #buildComputedIdIndex()} fallback: a positional store scan, reading
         * {@link VectorEntry#sourceEntityId} off each entry. Only reached by a non-incremental graph
         * rebuild, which loads every vector anyway, so its value pass is not an added cost.
         * <p>
         * Package-private for {@code VectorIndexIdDriftTest}, which asserts both paths agree.
         */
        Map<Long, Long> buildComputedIdIndexByScan()
        {
            final Map<Long, Long> index = new ConcurrentHashMap<>();
            this.vectorStore.iterateIndexed((storeId, entry) -> index.put(entry.sourceEntityId, storeId));
            return index;
        }

        /**
         * Resolves the raw vector for a graph ordinal (source entity id) in computed mode via the
         * {@link #computedIdIndex} and a direct, lazy {@code vectorStore.get(internalId)} — no index
         * query on the hot scoring path. Returns {@code null} if the entity has no stored vector.
         */
        private float[] lookupComputedVector(final int ordinal)
        {
            final Map<Long, Long> index = this.computedIdIndex();
            final Long storeId = index == null ? null : index.get((long)ordinal);
            if(storeId == null)
            {
                return null;
            }
            final VectorEntry entry = this.vectorStore.get(storeId);
            return entry == null ? null : entry.vector;
        }

        private List<VectorEntry> collectStoredVectors()
        {
            final List<VectorEntry> entries = new ArrayList<>();

            if(this.isEmbedded())
            {
                this.parentMap().iterateIndexed((entityId, entity) ->
                {
                    final float[] vector = this.vectorize(entity);
                    // Entities without an embedding (opted-in null vectors) are not graph nodes.
                    if(vector != null)
                    {
                        entries.add(new VectorEntry(entityId, vector));
                    }
                });
            }
            else
            {
                // Computed mode: rebuild from vector store. Null-vector entries are never
                // stored, but keep a defensive filter in case of legacy data.
                if(this.vectorStore != null && !this.vectorStore.isEmpty())
                {
                    this.vectorStore.iterate(entry ->
                    {
                        if(entry.vector != null)
                        {
                            entries.add(entry);
                        }
                    });
                }
            }

            return entries;
        }

        private void initializeIndex()
        {
            this.vectorTypeSupport = VectorizationProvider.getInstance().getVectorTypeSupport();

            // Set when a persisted on-disk index was found but rejected, so the rebuilt graph has to
            // be written back over the stale files. Acted on after the background manager is started.
            boolean diskRebuildPending = false;

            // Initialize builder lock (always, for consistent locking semantics)
            if(this.builderLock == null)
            {
                this.builderLock = new ReentrantReadWriteLock();
            }
            if(this.deferredBuilderOps == null)
            {
                this.deferredBuilderOps = new ConcurrentLinkedQueue<>();
            }

            // Reset the computed-mode fast lookup index; it is (re)built lazily on first access
            // (see computedIdIndex()). Clearing here covers reinitialization after internalRemoveAll,
            // where a stale map would otherwise survive. A non-incremental graph rebuild that follows
            // triggers the lazy build itself via lookupComputedVector() during node scoring.
            this.computedIdIndex = null;

            // Initialize the compression managers for whichever format dimensions are configured on.
            // Both must exist before the tryLoad() below, which adopts their quantizers from the
            // loaded graph.
            // Both PQ-based scoring modes need a trained codebook; they differ only in where the
            // encoded codes end up, which is the disk manager's concern rather than this one's.
            if(GraphFormat.of(this.configuration).usesPq())
            {
                this.pqManager = new PQCompressionManager.Default(
                    this,
                    this.name,
                    this.configuration.dimension(),
                    this.configuration.pqSubspaces()
                );
            }
            if(this.configuration.vectorStorage() == VectorStorage.NVQ)
            {
                this.nvqManager = new NVQCompressionManager.Default(
                    this.name,
                    this.configuration.dimension(),
                    this.configuration.nvqSubvectors()
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
                    GraphFormat.of(this.configuration),
                    this.configuration.parallelOnDiskWrite()
                );
                if(this.diskManager.tryLoad())
                {
                    // Recover the codebook the loaded graph was actually written with, rather than
                    // assuming one is present. A graph written before compression was enabled - or
                    // written while too few vectors existed to train - carries no FusedPQ, and
                    // claiming otherwise would leave the manager "trained" with a null codebook,
                    // permanently suppressing training while every persist wrote uncompressed.
                    this.adoptCompressorsFromLoadedGraph();

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

                    // Distinguish "a persisted index was rejected" from "nothing persisted yet":
                    // only the former leaves stale files that the rebuilt graph has to replace.
                    diskRebuildPending = this.diskManager.indexFilesExist();
                }
            }

            // Initialize searcher pool
            this.initializeSearcherPool();

            // Start background managers if enabled
            this.startBackgroundManagersIfEnabled();

            // A rejected load is not a change, so nothing would otherwise schedule the persist that
            // replaces the stale files: background persistence waits for its change threshold and the
            // shutdown persist for a non-zero count. Left alone, a read-mostly index would rebuild
            // from the store on every restart, which is exactly what the format bump was supposed to
            // cost only once. Must follow the start above, since the manager does not exist before it.
            //
            // This only reaches indices that run a background task manager - without one there is
            // nothing to schedule against. Such an index is not stranded, though: close() calls
            // doPersistToDisk directly for it, and a rejected load leaves incrementalMode false, so
            // that persist proceeds and replaces the files. The gap is a process that is killed
            // rather than closed, which repeats the rebuild on the next start.
            if(diskRebuildPending && this.backgroundTaskManager != null)
            {
                this.backgroundTaskManager.markPersistRequired();
            }
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
                        this.configuration.minChangesBetweenPersists(),
                        this.configuration.shutdownPersistTimeoutMillis()
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

            // Construction dominates the vector-lookup cost of an in-memory index: it performs
            // thousands of comparisons per insertion, each one a lookup through the uncached view
            // above, and the count per insertion grows as the graph deepens. Scoring from the codes
            // replaces every one of those with an in-heap read.
            //
            // The codes have to exist first, which is why this is not the state a new index starts
            // in. The switch does not come through here at all - performPqSwitch constructs its
            // replacement itself - so this branch serves the paths that rebuild an index that has
            // already switched, where the codes are present and populated.
            final MutablePQVectors pqVectors = this.inMemoryPqVectors;
            final BuildScoreProvider scoreProvider = pqVectors != null
                ? BuildScoreProvider.pqBuildScoreProvider(this.jvectorSimilarityFunction(), pqVectors)
                : BuildScoreProvider.randomAccessScoreProvider(vectorValues, this.jvectorSimilarityFunction())
            ;

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
                // Null means "this entity has no embedding" when the vectorizer opts in;
                // it is then excluded from the graph. Otherwise fail fast.
                if(this.vectorizer.allowsNullVectors())
                {
                    return null;
                }
                throw new IllegalStateException(
                    "Null vector returned from vectorizer in index \"" + this.name()
                        + "\" (vectorizer: " + this.vectorizer.getClass().getName() + "). "
                        + "Override Vectorizer.allowsNullVectors() to permit entities without embeddings."
                );
            }

            this.validateDimension(vector);
            this.validateFinite(vector, "Vector returned from vectorizer in index \"" + this.name()
                + "\" (vectorizer: " + this.vectorizer.getClass().getName() + ")");

            return vector;
        }

        /**
         * Rejects a vector containing {@code NaN} or an infinity.
         * <p>
         * Not defensive tidiness - one such component is enough to make a quantized index return
         * nothing at all, permanently. The quantizers fit their parameters to the data: NVQ derives
         * a global mean and per-subvector ranges, so a single {@code NaN} propagates into them, and
         * from there into every vector the graph stores. Traversal then scores {@code NaN} against
         * everything, no candidate ever compares greater than another, and search comes back empty.
         * Measured on {@code NVQ} with {@link ApproximateScoring#NONE}: ten hits before the persist
         * that trains the quantizer, zero after.
         * <p>
         * It does not heal. Removing the offending entity leaves the quantizer as it is - it is
         * adopted from the loaded graph rather than retrained - so the index stays empty until it
         * is rebuilt from scratch. The exact modes are unaffected, since they never fit anything to
         * the data, which is exactly why this has to be rejected at the door instead of being left
         * to the mode in use.
         * <p>
         * {@code NaN} is not an exotic input: normalising a zero vector produces it, and so does a
         * division by a zero norm in a perfectly ordinary embedding function.
         *
         * @param vector  the vector to check
         * @param context what to name in the message, since this serves entity embeddings and query
         *                vectors alike
         * @throws IllegalStateException if any component is not finite
         */
        private void validateFinite(final float[] vector, final String context)
        {
            for(int i = 0; i < vector.length; i++)
            {
                if(!Float.isFinite(vector[i]))
                {
                    throw new IllegalStateException(
                        context + " contains a non-finite value at index " + i + ": " + vector[i]
                            + ". NaN and infinity are rejected because a quantizer fitted to them"
                            + " returns no results at all, and does not recover when the entity is"
                            + " removed."
                    );
                }
            }
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

        /**
         * Records a graph-affecting content change: bumps the persisted {@link #structuralModCount}
         * (the crash-restart witness stamped into the disk {@code .meta}) and marks the index dirty
         * for the next persist. Use in place of {@link #markStateChangeChildren()} at content
         * mutation sites (add / remove / vec↔null transition); do NOT use it for {@code optimize()},
         * which reshapes the graph without changing logical content and must not force a rebuild.
         * <p>
         * Marks BOTH the instance and children dirty: {@code structuralModCount} is a field of this
         * instance, so it is only re-serialized when the instance itself is flagged
         * AbstractStateChangeFlagged#isInstanceNewOrChanged() gates {@code internalStore}) —
         * {@code markStateChangeChildren()} alone would persist only children (e.g. the computed-mode
         * {@code vectorStore}) and silently drop the counter.
         */
        private void markContentChanged()
        {
            this.structuralModCount++;
            this.markStateChangeInstance();
            this.markStateChangeChildren();
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

            // Null vector (opted-in): the entity has no embedding — keep it out of the vector
            // store and the graph entirely, so it never appears in search results.
            if(vector == null)
            {
                return;
            }

            final VectorEntry vectorEntry = new VectorEntry(entityId, vector);

            // Store based on vectorizer type
            if(!this.isEmbedded())
            {
                final long storeId = this.vectorStore.add(vectorEntry);
                this.computedIdIndex().put(entityId, storeId);
            }

            this.markContentChanged();

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

                // Add to HNSW graph using entity ID as ordinal. Idempotent: a deferred op may be
                // drained into a builder that already carries the ordinal (internal #142).
                final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(vector);
                this.executeOrDeferBuilderOp(() -> this.internalAddGraphNodeIdempotent(ordinal, vf));

                // Mark dirty for background managers
                this.markDirtyForBackgroundManagers(1);
            }
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

            final float[]  vector   = this.vectorize(entity);
            final int      ordinal  = toOrdinal(entityId);
            final boolean  embedded = this.isEmbedded();

            // Update the computed-mode vector store to reflect the new (possibly absent) vector.
            // Keyed by source entity id (not positionally), so the four vec/null transitions map
            // cleanly onto add / replace / remove.
            boolean changed         = false;
            boolean vectorUnchanged = false;
            if(!embedded)
            {
                // Resolve the store-internal id by source entity id via the fast index, then
                // mutate via id-based ops (set / removeById / add), keeping the index in sync.
                final Map<Long, Long> computedIdIndex = this.computedIdIndex();
                final Long storeId = computedIdIndex.get(entityId);
                if(vector == null)
                {
                    // vec→null: drop the stored entry so the entity is no longer indexed.
                    // null→null: nothing stored, nothing to do.
                    if(storeId != null)
                    {
                        this.vectorStore.removeById(storeId);
                        computedIdIndex.remove(entityId);
                        changed = true;
                    }
                }
                else if(storeId != null)
                {
                    // vec→vec (store-internal id unchanged). An update whose vector did not actually
                    // change is a no-op for this index: unlike the embedded case there is no live
                    // re-scoring from the entity, the stored vector IS the indexed value. Detect it
                    // with one lazy store read and skip the store write, the graph repair
                    // (markNodeDeleted + removeDeletedNodes + addGraphNode, two ForkJoinPool round
                    // trips per level) and all the change bookkeeping. Callers that touch unrelated
                    // entity fields (an adjacency list, say) would otherwise pay full graph repair
                    // per update (internal #142), which is also what floods the deferred op queue.
                    final VectorEntry existing = this.vectorStore.get(storeId);
                    if(existing != null && Arrays.equals(existing.vector, vector))
                    {
                        vectorUnchanged = true;
                    }
                    else
                    {
                        this.vectorStore.set(storeId, new VectorEntry(entityId, vector));
                        changed = true;
                    }
                }
                else
                {
                    // null→vec: the entity gains an embedding.
                    final long newStoreId = this.vectorStore.add(new VectorEntry(entityId, vector));
                    computedIdIndex.put(entityId, newStoreId);
                    changed = true;
                }
            }

            // Persisted structural-change witness: driven by the logical (id→vector) transition,
            // NOT the in-memory graph op below. In incremental mode an embedded vec→null removes a
            // disk-resident node the in-memory builder never held, so the graph-op flag would miss
            // it (and the crash-restart rebuild would not trigger); classify embedded transitions
            // from the old vector instead. Computed mode's store block above already set `changed`
            // precisely (the four transitions map onto add / set / remove / no-op).
            final boolean contentChanged;
            if(embedded)
            {
                // A non-null new vector is always a change (null→vec or vec→vec). Only a null new vector
                // needs the old vector — to tell vec→null (a change) from null→null (the only no-op) —
                // so re-vectorize replacedEntity solely on that path, not on the common non-null update.
                contentChanged = vector != null
                    || (replacedEntity != null && this.vectorize(replacedEntity) != null);
            }
            else
            {
                contentChanged = changed;
            }

            // In incremental mode, mark the ordinal as deleted from disk graph so disk search excludes
            // the stale version immediately, regardless of whether indexing is synchronous or eventual.
            // Gated on contentChanged: a null→null no-op has no stale disk version to exclude, and
            // adding it would only bloat diskDeletedOrdinals and enlarge the boolean[maxOrdinal+1] mask
            // rebuilt in createDiskAcceptBits() on every search.
            if(contentChanged && this.incrementalMode && this.diskDeletedOrdinals != null)
            {
                this.diskDeletedOrdinals.add(ordinal);
            }

            if(this.isEventualIndexing())
            {
                // Only a real (id→vector) transition needs background graph work. null→null is a no-op
                // in both modes; contentChanged is the reliable synchronous witness (for embedded it is
                // derived from the old vector above, so — unlike the raw graph-op flag — it is not racy
                // against queued mutations). applyGraphUpdate then derives the actual transition
                // (add / delete / delete+re-add) from the enqueued entry's vector nullness.
                if(contentChanged)
                {
                    this.backgroundTaskManager.enqueueUpdate(new VectorEntry(entityId, vector));
                }
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

                // Classify against ONE builder read, derived graph included: two separate reads of
                // the volatile fields can straddle a persist's builder swap and answer from
                // different graphs.
                final GraphIndexBuilder currentBuilder = this.builder;
                final OnHeapGraphIndex  currentGraph   = currentBuilder == null ? null : graphOf(currentBuilder);
                final boolean           inGraph        = currentGraph != null && currentGraph.containsNode(ordinal);

                if(vector == null)
                {
                    // vec→null / null→null: remove the node from the in-memory graph if present.
                    // No removeDeletedNodes() — unnecessary (markNodeDeleted already excludes it
                    // from liveNodes), and its ForkJoinPool would deadlock with the GigaMap
                    // monitor we hold for embedded vectorizers.
                    // Route through internalMarkOrdinalDeleted so a deferral drained after a persist
                    // builder swap still records the deletion (via diskDeletedOrdinals) instead of
                    // no-op'ing against the new empty builder. The pre-deferral inGraph check is no
                    // longer sufficient on its own: in incremental mode a disk-only node is not in
                    // the in-memory graph, so gating on it alone would queue nothing and the eager
                    // diskDeletedOrdinals entry made above is wiped by the persist's mode swap,
                    // leaving the stale disk node visible to search (internal #142). contentChanged
                    // covers that case; inGraph is kept as well so a transition this method cannot
                    // classify (a null replacedEntity) still clears the node it can see.
                    if(contentChanged || inGraph)
                    {
                        this.executeOrDeferBuilderOp(() -> this.internalMarkOrdinalDeleted(ordinal));
                        changed = true;
                    }
                }
                else if(embedded)
                {
                    // Three distinct embedded, non-null transitions, distinguished by graph state.
                    // Note containsNode() alone is NOT enough: jvector's markNodeDeleted() only sets
                    // a deleted bit and leaves the node in layer 0, so containsNode() stays true for
                    // a marked-deleted node. A prior vec→null in this same session (without an
                    // intervening optimize) leaves the node present-but-deleted, which is a third
                    // case the plain !inGraph guard would silently mis-handle.
                    //
                    // The classification below only selects WHICH op to enqueue; each op re-derives
                    // the graph state itself when it runs, because a deferred op can be drained into
                    // a builder that was swapped in the meantime (internal #142).
                    if(!inGraph)
                    {
                        // null→vec: the node was never added (or a prior optimize physically removed
                        // it) — add it now. addGraphNode alone is monitor-safe (no removeDeletedNodes),
                        // the same call internalAdd makes.
                        final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(vector);
                        this.executeOrDeferBuilderOp(() -> this.internalReaddGraphNode(ordinal, vf));
                    }
                    else if(currentGraph.getDeletedNodes().get(ordinal))
                    {
                        // vec→null→vec on the SAME entity: the node is still present but marked
                        // deleted. Resurrect it by clearing the deleted bit — monitor-safe (no
                        // ForkJoinPool), unlike removeDeletedNodes()+addGraphNode, whose workers
                        // call parentMap.get() and would deadlock with the GigaMap monitor we hold.
                        // The node keeps its (now stale) neighbor links and search re-scores it live
                        // from the entity via EntityBackedVectorValues — identical to the vec→vec
                        // "leave as-is" contract below; the next optimize/persist rebuilds connections.
                        // Without this, the entity would stay excluded from liveNodes() forever.
                        final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(vector);
                        this.executeOrDeferBuilderOp(() -> this.internalResurrectGraphNode(ordinal, vf));
                    }
                    else
                    {
                        // vec→vec: leave the graph as-is — removeDeletedNodes() uses a ForkJoinPool
                        // whose workers call parentMap.get(), deadlocking with the GigaMap monitor we
                        // hold. The updated entity is already in the GigaMap, so
                        // EntityBackedVectorValues returns the new vector during search, and the next
                        // optimize/persist cycle rebuilds the graph connections.
                        //
                        // That contract rests on the vector being read LIVE from the entity at score
                        // time, which is true of every scoring path but one. A PQ code is a copy made
                        // when the node was indexed, so it does not follow the entity: left alone it
                        // would keep scoring this node by the embedding it used to have, and the
                        // entity would stop being findable by its new one. Refreshing the code is the
                        // compressed equivalent of "search re-scores it live" - one encode, no pool,
                        // and therefore monitor-safe.
                        //
                        // Queued unconditionally rather than only when codes already exist. During a
                        // PQ switch there are none yet - they are published at the very end - so a
                        // check here would skip the refresh for any update that lands while the
                        // replacement is being built, and after the swap nothing would ever revisit
                        // it: the switch does not run again once codes are present, so that entity
                        // would be scored by its old embedding for the rest of the index's life.
                        // trackPqCode is a no-op while there are no codes, so queueing always costs
                        // nothing and is re-evaluated when the op actually runs.
                        final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(vector);
                        this.executeOrDeferBuilderOp(() -> this.trackPqCode(ordinal, vf));
                    }
                    changed = true;
                }
                else if(!vectorUnchanged)
                {
                    // Computed, non-null and actually different (vec→vec' or null→vec): vectors are
                    // stored separately, so removeDeletedNodes() won't call parentMap.get().
                    // Safe to inline.
                    final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(vector);
                    this.executeOrDeferBuilderOp(() -> this.internalReplaceGraphNode(ordinal, vf));
                    changed = true;
                }

                if(changed)
                {
                    this.markDirtyForBackgroundManagers(1);
                }
            }

            // Bump the persisted structural-change counter + mark the index dirty whenever the
            // logical mapping changed — independent of whether an in-memory graph op ran, so an
            // incremental-mode disk-node deletion (embedded vec→null) is still recorded and forces
            // a crash-restart rebuild. null→null is the only case that skips this.
            if(contentChanged)
            {
                this.markContentChanged();
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
        private void addVectorEntries(final List<VectorEntry> entriesIncludingNulls)
        {
            // No synchronized(parentMap) needed — called from GigaMap's synchronized methods.
            this.ensureIndexInitialized();

            // Drop entries without a vector (opted-in null embeddings): they get neither a
            // vector store entry nor a graph node, so they never appear in search results.
            // The kept entries carry their own sourceEntityId, so filtering does not disturb
            // id alignment.
            final List<VectorEntry> entries = entriesIncludingNulls.stream()
                .filter(e -> e.vector != null)
                .toList()
            ;

            if(entries.isEmpty())
            {
                return;
            }

            if(!this.isEmbedded())
            {
                // Add individually to capture each store-internal id for the fast index; robust
                // against hole reuse (a batch addAll only reports the last assigned id).
                final Map<Long, Long> computedIdIndex = this.computedIdIndex();
                for(final VectorEntry entry : entries)
                {
                    final long storeId = this.vectorStore.add(entry);
                    computedIdIndex.put(entry.sourceEntityId, storeId);
                }
            }

            this.markContentChanged();

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
        /**
         * {@inheritDoc}
         * <p>
         * True for an in-memory index that is configured for compressed scoring, has not switched
         * yet, was restored from storage, and currently holds enough vectors to train a codebook.
         * <p>
         * Restored, because that is the one case that cannot earn the optimization: its entities
         * are already in the store, so nothing bumps the change count for them. A new index earns
         * it the ordinary way, and that includes one registered on an already-populated GigaMap -
         * its entities arrive immediately afterwards through the backfill and each one counts, so
         * saying yes here would let a small index switch on the first tick without reaching the
         * threshold {@code minChangesBetweenOptimizations} documents.
         * <p>
         * Asked fresh each tick rather than remembered, which is what makes the answer track the
         * index. A restored index holding fewer vectors than the codebook needs says no, so it
         * costs no optimization passes while it cannot switch anyway; when enough vectors arrive it
         * says yes, and the switch happens on the next tick however far it still is from the change
         * threshold. {@link #isPqTrainingPending()} carries both conditions, including the witness
         * that stops a training attempt that has already declined at this exact state from being
         * repeated.
         */
        @Override
        public boolean needsUnearnedOptimization()
        {
            return this.restoredFromStorage
                && !this.configuration.onDisk()
                && this.configuration.approximateScoring() == ApproximateScoring.PQ_IN_MEMORY
                && this.inMemoryPqVectors == null
                && this.isPqTrainingPending()
            ;
        }

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
                if(entry.vector == null)
                {
                    // Entities without an embedding are excluded from the graph.
                    return;
                }
                final int ordinal = toOrdinal(entry.sourceEntityId);
                final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(entry.vector);
                this.internalAddGraphNodeIdempotent(ordinal, vf);
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
                // Resolve by source entity id via the fast index; a never-indexed entity
                // (null embedding) simply has no entry and nothing is removed.
                final Map<Long, Long> computedIdIndex = this.computedIdIndex();
                final Long storeId = computedIdIndex.get(entityId);
                if(storeId != null)
                {
                    this.vectorStore.removeById(storeId);
                    computedIdIndex.remove(entityId);
                }
            }

            this.markContentChanged();

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
                // via diskDeletedOrdinals and won't be in the builder. Route through
                // internalMarkOrdinalDeleted so a deferral drained after a persist builder swap
                // still records the deletion instead of no-op'ing against the new empty builder.
                // Enqueued unconditionally: the helper checks the builder itself when it runs, and
                // gating here on the CURRENT builder would queue nothing for a disk-only node, whose
                // eager diskDeletedOrdinals entry above is then wiped by a concurrent persist's mode
                // swap, resurrecting the removed entity in search (internal #142).
                this.executeOrDeferBuilderOp(() -> this.internalMarkOrdinalDeleted(ordinal));

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

                // Same for deferred sync-mode ops: the graph they describe is about to be destroyed,
                // so replaying them into the fresh builder would resurrect removed entities.
                if(this.deferredBuilderOps != null)
                {
                    this.deferredBuilderOps.clear();
                }

                this.closeInternalResources();

                // Reinitialize the index (this will also restart background managers if configured)
                this.initializeIndex();
                this.markContentChanged();

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

            // Resolve via the fast source-id index (built lazily on first access); fall back to a
            // direct identity-index query only in the defensive case where it is unavailable.
            final Map<Long, Long> idIndex = this.computedIdIndex();
            final VectorEntry entry;
            if(idIndex != null)
            {
                final Long storeId = idIndex.get(entityId);
                entry = storeId == null ? null : this.vectorStore.get(storeId);
            }
            else
            {
                entry = VectorEntry.lookup(this.vectorStore, entityId);
            }
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
            if(queryVector == null)
            {
                throw new IllegalArgumentException("Query vector must not be null");
            }
            // Enforce the documented k > 0 precondition here, the single funnel for every search
            // overload (float[] and entity-based, with and without an explicit beam width), rather
            // than letting a non-positive topK reach jvector's searcher.
            if(k <= 0)
            {
                throw new IllegalArgumentException("k must be positive: " + k);
            }
            this.validateDimension(queryVector);

            // A query is not stored, so it cannot poison an index the way an embedding can. It
            // still cannot answer anything: every comparison against NaN is false, so the search
            // returns an arbitrary result or none, and silently. Rejected here for the same reason
            // as the non-positive k above - this is the single funnel every search overload comes
            // through, and a caller is better served by the message than by the empty result.
            this.validateFinite(queryVector, "Query vector");

            // Ensure the (deferred) graph rebuild has run BEFORE acquiring the read lock: the
            // rebuild gate takes the parent GigaMap monitor, and holding readLock while acquiring
            // that monitor would risk the exact lock-ordering deadlock the read-lock note below
            // warns about. Once rebuilt this is a lock-free volatile check.
            this.ensureIndexInitialized();

            // Acquire read lock — blocks during cleanup/persistence/removeAll/close,
            // allows concurrent searches and GigaMap mutations.
            // No synchronized(parentMap) — avoids lock-ordering deadlock with
            // internalRemoveAll (which holds the GigaMap monitor and needs the write lock).
            this.builderLock.readLock().lock();
            try
            {
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
            final SearchScoreProvider exactProvider = DefaultSearchScoreProvider.exact(
                query,
                this.jvectorSimilarityFunction(),
                this.createCachingVectorValues()
            );

            // The per-query cache behind that provider dedupes repeat visits to the same node, but
            // traversal still visits well over a thousand DISTINCT nodes per query at the default
            // beam width, and that count grows with the data set. Each one is a lookup the cache
            // cannot avoid. Scoring from the codes removes all of them, leaving only the reranked
            // candidates to be fetched.
            // Non-null and non-empty. The switch publishes the codes already populated - every
            // ordinal is encoded before anything is published - so in practice a store that is
            // present is also filled. The count is checked anyway because an empty one would mean
            // there is nothing to score from, and falling back to exact is then the right answer
            // rather than a missed optimisation.
            final MutablePQVectors pqVectors = this.inMemoryPqVectors;
            final SearchScoreProvider scoreProvider = pqVectors != null && pqVectors.count() > 0
                ? new DefaultSearchScoreProvider(
                    pqVectors.precomputedScoreFunctionFor(query, this.jvectorSimilarityFunction()),
                    exactProvider.exactScoreFunction())
                : exactProvider
            ;

            final GraphSearcher searcher = this.inMemorySearcherPool.get();
            // Refresh the view so the searcher sees nodes added or re-added since pool
            // initialization (ConcurrentGraphIndexView uses snapshot isolation based on
            // a completion timestamp captured at creation time). Unlike the disk path's fused-PQ
            // decoder, an in-heap score function holds no view scratch, so refreshing is safe here.
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
            final io.github.jbellis.jvector.vector.VectorSimilarityFunction vsf = this.jvectorSimilarityFunction();

            final SearchScoreProvider exactProvider = DefaultSearchScoreProvider.exact(
                query,
                vsf,
                this.createCachingVectorValues()
            );

            // In non-incremental disk mode initializeSearcherPool() puts the DISK searcher into
            // inMemorySearcherPool - there is no second graph to search - so despite the field name
            // this is the disk searcher.
            final GraphSearcher searcher = this.inMemorySearcherPool.get();
            return searcher.search(
                this.diskScoreProvider(searcher, query, vsf, exactProvider, false),
                k,
                rerankK,
                0f,
                0f,
                Bits.ALL
            );
        }

        /**
         * Builds the score provider for a search against the on-disk graph, dispatching on the two
         * dimensions of the graph's format: how it scores candidates and how it stores its vectors.
         *
         * <table border="1">
         *   <tr><th>Graph features</th><th>Traversal</th><th>Reranking</th></tr>
         *   <tr><td>{@code INLINE_VECTORS}</td><td>exact, from the GigaMap</td><td>-</td></tr>
         *   <tr><td>{@code INLINE_VECTORS + FUSED_PQ}</td><td>fused PQ codes</td><td>exact, from the mapping</td></tr>
         *   <tr><td>{@code NVQ_VECTORS}</td><td>quantized vectors</td><td>exact, from the GigaMap</td></tr>
         *   <tr><td>{@code NVQ_VECTORS + FUSED_PQ}</td><td>fused PQ codes</td><td>quantized, or exact - see below</td></tr>
         * </table>
         *
         * <h2>Constraints that are easy to break</h2>
         * The approximate function <b>must</b> be built from {@code searcher.getView()}, and the
         * searcher must not have {@code setView(...)} called on it. {@code FusedPQDecoder}
         * populates that view's neighbor scratch arrays as a side effect of reading the packed
         * codes, and {@code View.processNeighbors} then reads exactly those arrays - a score
         * function bound to any other view silently scores the wrong node's neighbors.
         * <p>
         * {@code rerankerFor()} is not thread-safe: it closes over a scratch vector and reads
         * through the view. Safe here only because it is built fresh per query over a
         * thread-confined {@code ExplicitThreadLocal<GraphSearcher>} and its view.
         * <p>
         * The reranker handed to {@code DefaultSearchScoreProvider} is the one {@code GraphSearcher}
         * actually uses - it gates reranking on that argument being non-null, not on whether the
         * traversal function is exact. So {@code exactFallback} plays no part on any path that
         * returns a two-argument provider, and the choice made here is the whole choice.
         *
         * <h2>Why incremental mode forces the exact reranker</h2>
         * {@code searchIncremental} merges this result with one from the in-memory graph <b>by raw
         * score</b>. The in-memory half is always scored exactly, so an NVQ-dequantized disk half
         * would sit on a systematically different scale and bias every merge. Whenever the two
         * halves are compared, both must be exact.
         * <p>
         * That rule currently governs every live query against an on-disk index, because
         * {@code incremental} is never false for one: {@code tryLoad} enters incremental mode on
         * load and each persist ends in {@code reenterIncrementalMode}. The graph-backed reranker
         * below is therefore unreached today. It is kept because it is the right behaviour should a
         * non-incremental disk search become reachable, not because it runs.
         * <p>
         * The gate is the <b>loaded graph's</b> feature set as well as the configuration: the graph
         * is self-describing, so the query path needs no happens-before edge with the training and
         * persist paths, and a graph whose training declined carries no feature to use however it is
         * configured. The configuration is still consulted so that the two must agree rather than
         * the graph being taken at its word - a file can carry a feature this build would not have
         * written, a downgrade being the concrete case.
         *
         * @param searcher      the searcher that will run the query, and whose view is used
         * @param query         the query vector
         * @param vsf           the similarity function
         * @param exactFallback the provider to use when the graph offers nothing better, and the
         *                      source of the exact score function when reranking must not be lossy
         * @param incremental   whether this result will be merged with an in-memory one by raw score
         * @return the score provider to search with
         */
        private SearchScoreProvider diskScoreProvider(
            final GraphSearcher                                             searcher     ,
            final VectorFloat<?>                                            query        ,
            final io.github.jbellis.jvector.vector.VectorSimilarityFunction vsf          ,
            final SearchScoreProvider                                       exactFallback,
            final boolean                                                   incremental
        )
        {
            final OnDiskGraphIndex diskIndex = this.diskManager != null
                ? this.diskManager.getDiskIndex()
                : null
            ;
            if(diskIndex == null || !(searcher.getView() instanceof OnDiskGraphIndex.View view))
            {
                return exactFallback;
            }

            final Set<FeatureId> features = diskIndex.getFeatureSet();
            final boolean        hasNvq   = features.contains(FeatureId.NVQ_VECTORS);

            // PQ_IN_MEMORY first, because it is the one mode whose approximate function comes from
            // neither the graph nor the view. The codes are a heap-resident array loaded from the
            // sidecar, so scoring a candidate touches no disk at all - which also means the
            // view-scratch rule that governs the fused path does not apply here.
            //
            // Reranking is exact from the GigaMap in both modes, not only the incremental one. There
            // is nothing else it could be: this graph carries no fused codes to rerank against, and
            // when it is combined with NVQ storage it carries no full-precision vectors either.
            final PQVectors pqVectors = this.configuration.approximateScoring() == ApproximateScoring.PQ_IN_MEMORY
                ? this.diskManager.loadedPqVectors()
                : null
            ;
            if(pqVectors != null)
            {
                return new DefaultSearchScoreProvider(
                    pqVectors.precomputedScoreFunctionFor(query, vsf),
                    exactFallback.exactScoreFunction()
                );
            }

            // FusedPQDecoder.similarityTo(node) - used for the entry node and for every hop above
            // level 0 - requires the node to be in the view's level-1 inline-source cache, which
            // OnDiskGraphIndex only populates for a hierarchical graph. On a flat graph the first
            // scoring call would throw. A PQ graph has at least 256 nodes and the builder is
            // hierarchical, so this should never fire; it is cheap insurance on the query path.
            final boolean useFusedPq =
                   this.configuration.approximateScoring() == ApproximateScoring.FUSED_PQ
                && features.contains(FeatureId.FUSED_PQ)
                && diskIndex.isHierarchical()
            ;

            if(!useFusedPq && !hasNvq)
            {
                // A full-precision graph with no fused codes. Scoring it through the view would be
                // a behaviour change for the default on-disk configuration, so it keeps reading the
                // GigaMap exactly as it always has.
                return exactFallback;
            }

            if(useFusedPq)
            {
                // Traversal reads the fused codes; the rerank then reads the graph's own vector
                // block, which for an NVQ graph means the view's reranker dequantizes. That is
                // acceptable for a plain top-k - it cost about 0.002 recall@10 in measurement - but
                // not when the score has to be comparable with an exactly scored in-memory half, so
                // incremental mode takes the exact function instead.
                final ScoreFunction.ExactScoreFunction reranker = hasNvq && incremental
                    ? exactFallback.exactScoreFunction()
                    : view.rerankerFor(query, vsf)
                ;
                return new DefaultSearchScoreProvider(
                    view.approximateScoreFunctionFor(query, vsf),
                    reranker
                );
            }

            // NVQ without fused codes: traverse on the quantized vectors in the mapping, which is
            // the point of storing them, and rerank exactly from the GigaMap. The view's reranker is
            // an ExactScoreFunction by type only - against an NVQ graph it dequantizes - so it is
            // adapted to the approximate slot rather than being handed over as if it were exact.
            // The rerank is exact here whether or not this is incremental mode, which is what makes
            // NVQ storage lossless for the final ordering as long as no fused codes are present.
            final ScoreFunction.ExactScoreFunction quantizedScorer = view.rerankerFor(query, vsf);
            return new DefaultSearchScoreProvider(
                (ScoreFunction.ApproximateScoreFunction)quantizedScorer::similarityTo,
                exactFallback.exactScoreFunction()
            );
        }

        /**
         * Searches in incremental mode: queries both the disk graph (for existing data)
         * and the in-memory builder graph (for new mutations), then merges results.
         */
        private SearchResult searchIncremental(final VectorFloat<?> query, final int k, final int rerankK)
        {
            final io.github.jbellis.jvector.vector.VectorSimilarityFunction vsf = this.jvectorSimilarityFunction();

            final SearchScoreProvider scoreProvider = DefaultSearchScoreProvider.exact(
                query,
                vsf,
                this.createCachingVectorValues()
            );

            // 1. Search disk graph (excluding deleted/updated ordinals)
            // Use rerankK as topK to give the merge a richer candidate pool
            SearchResult diskResult = null;
            if(this.diskSearcherPool != null)
            {
                final GraphSearcher diskSearcher = this.diskSearcherPool.get();
                final Bits acceptBits = this.createDiskAcceptBits();

                // When the graph carries FusedPQ, traverse on the compressed codes and rerank
                // against the inline vectors in the same memory-mapped file. Reranking from the
                // graph rather than from createCachingVectorValues() also avoids a parentMap.get()
                // - and, in embedded mode, a vectorizer.vectorize() - per reranked candidate. The
                // accept bits already exclude every ordinal deleted or updated since the graph was
                // written, so for every surviving candidate the on-disk vector IS the live vector.
                final SearchScoreProvider diskProvider =
                    this.diskScoreProvider(diskSearcher, query, vsf, scoreProvider, true);

                diskResult = diskSearcher.search(diskProvider, rerankK, rerankK, 0f, 0f, acceptBits);
            }

            // 2. Search in-memory graph (new mutations only)
            SearchResult memResult = null;
            if(this.inMemorySearcherPool != null && this.index != null && this.index.size(0) > 0)
            {
                final GraphSearcher memSearcher = this.inMemorySearcherPool.get();
                // Capture the view once so setView(...) and liveNodes() agree on the same
                // snapshot (ConcurrentGraphIndexView uses snapshot isolation — two separate
                // getView() calls could return different snapshots).
                final var view = this.index.getView();
                memSearcher.setView(view);
                memResult = memSearcher.search(scoreProvider, rerankK, rerankK, 0f, 0f, view.liveNodes());
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
                    this::lookupComputedVector,
                    // RAVV size() is the dense ordinal upper bound (getVector must be valid for
                    // [0, size())), NOT the vector count. Graph ordinals are source entity ids, so with
                    // null embeddings / deletion holes the highest ordinal exceeds vectorStore.size().
                    // PQ encodeAll() builds a dense PQVectors of this length and FusedPQ / PQ search then
                    // index it by graph ordinal — an under-reported count skips/overflows high ordinals
                    // (IndexOutOfBoundsException). Match the graph's ordinal space (see getHighestEntityId()).
                    () -> Math.toIntExact(this.parentMap().highestUsedId() + 1),
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

                    // The whole PQ switch, under this monitor and nothing else. It reads the
                    // GigaMap throughout, which is what the monitor is for, and it takes no
                    // builderLock - the posture ensureGraphRebuilt() documents as unable to sit in
                    // a monitor/builderLock cycle.
                    this.performPqSwitch();

                    // After the switch, so that a replacement becomes the builder this optimization
                    // goes on to clean. Capturing before it would leave the freshly built graph
                    // uncleaned and send this pass at the graph just abandoned.
                    capturedBuilder = this.builder;
                }

                // cleanup() uses ForkJoinPool internally - must be outside synchronized(parentMap)
                // to avoid deadlock with embedded vectorizers whose worker threads call
                // parentMap.get(). A replacement builder scores from the codes instead, so its
                // workers reach no further than the heap.
                if(capturedBuilder != null)
                {
                    // Write lock blocks background worker mutations (readLock) and searches.
                    this.builderLock.writeLock().lock();
                    try
                    {
                        // Checked before anything touches the captured builder. The monitor was
                        // released before this lock was taken, so internalRemoveAll can run in
                        // between - it takes the monitor and then this lock - and would have closed
                        // this builder and put a different one in its place. Cleaning the old one
                        // would then operate on a closed object.
                        if(this.builder != capturedBuilder)
                        {
                            LOG.info("Skipping optimization of '{}': the index was rebuilt after the"
                                + " builder was captured", this.name);
                        }
                        else
                        {
                            capturedBuilder.cleanup();
                        }
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

                // Apply any deferred sync-mode mutations now that cleanup is done. Inside the finally
                // so no exit path can strand the queue; builderLock is already released above.
                this.drainDeferredBuilderOps();
            }

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

            this.doPersistToDisk(false);
        }

        /**
         * Core persistence logic without queue drain.
         * Called directly from the background task manager's executor thread
         * (where inline drain is already done) and from the public persistToDisk() method.
         *
         * @param onShutdown {@code true} when invoked on the shutdown path. In incremental mode a
         *                   shutdown persist skips the O(n) full-graph consolidation entirely and
         *                   relies on the load-time self-heal, so shutdown is never blocked by a
         *                   rebuild that would be discarded anyway. Because that return happens
         *                   before the empty-graph handling below, a shutdown persist in incremental
         *                   mode also leaves an earlier, now stale on-disk index in place instead of
         *                   removing it - harmless, since the same self-heal rejects it on load and
         *                   the next non-shutdown persist replaces or removes it. A shutdown persist
         *                   also never (re-)creates the transient index state, which would restart
         *                   the background task manager mid-{@code close()}. {@code false} for
         *                   background/explicit persistence, which consolidates and initializes as
         *                   before.
         */
        @Override
        public void doPersistToDisk(final boolean onShutdown)
        {
            if(!this.configuration.onDisk())
            {
                return; // No-op for in-memory indices
            }

            final boolean trainedForThisPersist = this.trainCompressorsBeforeLocking(onShutdown);

            // Signal sync-mode mutations to defer builder ops during cleanup + disk write.
            this.cleanupInProgress = true;
            try
            {
                // Acquire write lock for exclusive access during persistence.
                // This blocks searches, background worker mutations, removeAll, and close.
                this.builderLock.writeLock().lock();
                try
                {
                    // First, because everything below either reads state this index no longer has
                    // or recreates it. Training above runs before any lock is taken and can take
                    // tens of seconds, so a close that began after this persist did can have
                    // completed in the meantime: it shut the background task manager down, tore
                    // the transient state down under this same lock, and returned. Carrying on
                    // would call ensureIndexInitialized() and rebuild all of it, leaving a closed
                    // index with a live builder and a fresh executor thread that nothing will ever
                    // shut down again - the caller does not re-check the field.
                    //
                    // Reading it here rather than before the lock is the whole point: close writes
                    // it under this lock, so the two cannot interleave.
                    if(this.closed)
                    {
                        LOG.debug("Index '{}' was closed while this persist was preparing, skipping"
                            + " it rather than resurrecting the index", this.name);
                        return;
                    }

                    // If incremental mode with no changes, skip persist entirely.
                    //
                    // Exception: an index that wants PQ but has no codebook yet still has work to
                    // do, even with no data changes. That happens when the first persist ran below
                    // MIN_VECTORS_FOR_PQ_TRAINING, or when training threw and the graph was written
                    // uncompressed. Without this carve-out the shortcut would make that state
                    // permanent for an idle index, because nothing else ever re-enters this method.
                    // The carve-out deliberately does not apply on the shutdown path. Training needs
                    // the consolidated graph, so it can only run after exitIncrementalMode(), and the
                    // shutdown branch below returns before that on purpose - shutdown must not be
                    // blocked by an O(n) rebuild. Letting a pending training through here would only
                    // reach that return anyway, so this states the outcome instead of discovering it
                    // two steps later. The consequence is that an index whose only persist is the
                    // shutdown persist stays uncompressed; it trains on the first explicit
                    // persistToDisk() or the first background persist after a change.
                    // The pending checks read GigaMap state (the structural mod count and, in
                    // embedded mode, the entity count), so it needs the parentMap monitor to see a
                    // consistent snapshot - otherwise a persist racing a mutation could observe the
                    // pre-mutation count, take the shortcut, and defer a training that was due.
                    // Taking the monitor while already holding builderLock.writeLock() is the
                    // established order here (Phase 1 below does exactly that); the deadlock hazard
                    // is the reverse, which internalRemoveAll takes and this never does.
                    final boolean skipPersist;
                    synchronized(this.parentMap())
                    {
                        skipPersist = this.incrementalMode
                            && this.isIncrementalClean()
                            && !trainedForThisPersist
                            && !this.compressionTrainedOutsidePersist
                            && (onShutdown || !(this.isPqTrainingPending() || this.isNvqTrainingPending()))
                        ;
                    }

                    if(skipPersist)
                    {
                        LOG.debug("No incremental changes for '{}', skipping persist", this.name);
                        return;
                    }

                    // Captured references for Phase 2 (disk write outside synchronized block)
                    final OnHeapGraphIndex         capturedIndex  ;
                    final RandomAccessVectorValues capturedRavv   ;
                    final PQCompressionManager     capturedPqMgr  ;
                    final NVQCompressionManager    capturedNvqMgr ;
                    final DiskIndexManager         capturedDiskMgr;
                    final DiskIndexManager.MetaState capturedMeta ;

                    final GraphIndexBuilder        capturedBuilder;

                    // Phase 1: Barrier + reference capture inside synchronized(parentMap).
                    // The barrier ensures any in-flight GigaMap mutation completes.
                    // New mutations see cleanupInProgress=true and defer.
                    synchronized(this.parentMap())
                    {
                        if(onShutdown && !this.isIndexPresent())
                        {
                            // Nothing to persist, and nothing to (re-)create here. On the shutdown
                            // path ensureIndexInitialized() must not run initializeIndex(): that
                            // ends in startBackgroundManagersIfEnabled(), so an index already torn
                            // down by closeInternalResources() would get a fresh background task
                            // manager after its predecessor was shut down, and the caller does
                            // not re-check the field, so that executor thread would outlive
                            // close(). An index without builder and without a loaded disk index
                            // holds nothing the store does not already have.
                            LOG.debug("No index present for '{}' on shutdown, skipping persist", this.name);
                            return;
                        }

                        this.ensureIndexInitialized();

                        // If in incremental mode, exit it first by rebuilding the full graph.
                        // Must happen inside synchronized(parentMap) so that
                        // rebuildGraphFromStore() does not race with in-flight mutations.
                        if(this.incrementalMode)
                        {
                            if(onShutdown)
                            {
                                // The on-disk index is a derived cache. Skip the O(n) full-graph
                                // consolidation on shutdown and let the load-time self-heal
                                // (DiskIndexManager.verifyMetadata) rebuild from the store — the
                                // source of truth — on the next boot. No vectors are lost, and
                                // shutdown is not blocked by a rebuild that would be discarded
                                // anyway. Full consolidation stays a background/explicit operation.
                                //
                                // This returns before the empty-graph handling below, so an index
                                // emptied while in incremental mode keeps its now stale files until
                                // the next non-shutdown persist. That is the same trade as skipping
                                // the consolidation: the self-heal rejects them on load.
                                LOG.info("Skipping full-graph consolidation for '{}' on shutdown; "
                                    + "on-disk index self-heals from store on next load", this.name);
                                return;
                            }
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
                                GraphFormat.of(this.configuration),
                                this.configuration.parallelOnDiskWrite()
                            );
                        }

                        // An empty graph cannot be written: jvector's header writer dereferences
                        // view.entryNode(), which is null while the graph holds no node, so the
                        // write would fail with a NullPointerException.
                        //
                        // Reaching this point means the store itself holds no vectors: either none
                        // were ever added, or the incremental exit above consolidated a graph
                        // that is now empty: exitIncrementalMode() rebuilds from the store, the
                        // source of truth. Any files written earlier therefore describe content
                        // that no longer exists and are removed rather than left behind.
                        if(this.index.size(0) == 0)
                        {
                            LOG.debug("Index '{}' holds no vectors, removing any on-disk index "
                                + "instead of persisting", this.name);
                            this.diskManager.deleteIndexFiles();
                            return;
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
                        capturedPqMgr   = this.pqManager ;
                        capturedNvqMgr  = this.nvqManager;
                        capturedDiskMgr = this.diskManager;

                        // Sample the .meta witnesses at the same instant the graph is captured, under
                        // the monitor, so a Phase-2 vec↔null mutation cannot advance them past the
                        // written graph (see DiskIndexManager.MetaState).
                        capturedMeta    = new DiskIndexManager.MetaState(
                            this.getExpectedVectorCount(),
                            this.getHighestEntityId(),
                            this.getStructuralModCount()
                        );
                    }

                    // Phase 2: Cleanup and disk write outside synchronized(parentMap).
                    // builderLock.writeLock() is still held, blocking searches,
                    // background worker mutations, removeAll, and close.
                    // parentMap monitor is released, so ForkJoinPool workers and
                    // disk writer threads can call parentMap.get() for embedded vectors.

                    // Test-only injection point: exercises the window where a sync mutation defers a
                    // builder op that is drained only after the builder is swapped below. No-op in prod.
                    final Runnable hook = this.persistPhase2TestHook;
                    if(hook != null)
                    {
                        hook.run();
                    }

                    capturedBuilder.cleanup();
                    capturedDiskMgr.writeIndex(
                        capturedIndex, capturedRavv, capturedPqMgr, capturedNvqMgr, capturedMeta);

                    // Whatever was trained ahead of this persist is on disk now. Cleared here
                    // rather than at the skip check, so that a persist which fails on the way
                    // leaves the flag standing and the next one still knows there is a codebook
                    // waiting to be written.
                    this.compressionTrainedOutsidePersist = false;

                    // After writing, re-enter incremental mode for fast subsequent operation
                    this.reenterIncrementalMode(capturedMeta);
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

                // Apply any deferred sync-mode mutations now that cleanup + persistence is done.
                // Inside the finally so the early returns above (incremental-clean, shutdown skip,
                // no builder) cannot strand the queue; builderLock is already released above.
                this.drainDeferredBuilderOps();
            }
        }

        /**
         * Returns whether this index is configured for PQ, holds no codebook yet, and now has enough
         * vectors to train one - i.e. a persist would produce a compressed graph where the last one
         * did not.
         * <p>
         * Used to keep the incremental-clean shortcut in {@code doPersistToDisk} from making a
         * missed training permanent on an otherwise idle index. It has no effect on the shutdown
         * path, which returns before the consolidation that training depends on.
         */
        /**
         * Trains the PQ codebook, if one is due, before {@code doPersistToDisk} takes any lock.
         * <p>
         * Only the collection needs the {@code parentMap} monitor, because it iterates the map. The
         * clustering that follows is by far the longer half - seconds to tens of seconds at the
         * 128k-vector cap - and touches nothing but the collected sample, so it runs with no locks
         * held. Doing it inside the persist would stall every GigaMap operation and every search for
         * that whole time, on the first persist of a large index that has just enabled PQ.
         * <p>
         * Skipped on the shutdown path: the persist itself returns before the consolidation training
         * depends on, and shutdown must not wait for k-means either.
         * <p>
         * A failure here must not fail the persist. The graph is perfectly writable uncompressed, so
         * the exception is logged and the attempt recorded, and the write proceeds.
         *
         * @param onShutdown whether this persist is the shutdown persist
         * @return whether this call trained something that is not on disk yet, which the caller
         *         needs in order not to skip the persist that has to write it
         */
        private boolean trainCompressorsBeforeLocking(final boolean onShutdown)
        {
            if(onShutdown || (this.pqManager == null && this.nvqManager == null))
            {
                return false;
            }

            final List<VectorFloat<?>>  trainingSample;
            final boolean               needPq        ;
            final boolean               needNvq       ;
            final PQCompressionManager  pq            ;
            final NVQCompressionManager nvq           ;
            synchronized(this.parentMap())
            {
                this.ensureIndexInitialized();

                // The managers are re-created by ensureIndexInitialized, so re-read them rather than
                // trusting the references tested above - and hold on to what is read, because the
                // training below runs with the monitor released. internalRemoveAll and close take
                // the write lock without this monitor and null both fields, so reading them again
                // down there is a NullPointerException in the middle of a persist.
                pq      = this.pqManager ;
                nvq     = this.nvqManager;
                needPq  = pq  != null && this.isPqTrainingPending() ;
                needNvq = nvq != null && this.isNvqTrainingPending();
                if(!needPq && !needNvq)
                {
                    return false;
                }

                // ONE collection feeds both managers. Collecting per manager would walk the whole
                // GigaMap twice and, in embedded mode, call the user's Vectorizer once per entity
                // twice, on the first persist of every index that has both dimensions configured on.
                // The sample is dense and capped; see collectTrainingVectors.
                trainingSample = this.collectTrainingVectors(PQCompressionManager.MAX_TRAINING_VECTORS);

                if(needPq && trainingSample.size() < PQCompressionManager.MIN_VECTORS_FOR_PQ_TRAINING)
                {
                    // Record the decline here as well, not only after a failed compute below. The
                    // collection declines whenever the sample turns out too small, which in embedded
                    // mode happens with enough entities but too few embeddings - the gate counts
                    // parentMap.size(). Without this the witness never advances, isPqTrainingPending()
                    // stays true, and every idle persist leaves incremental mode to rebuild and
                    // rewrite the whole graph.
                    this.pqTrainingDeclinedAtModCount = this.getStructuralModCount();
                }
            }

            // Both trainings run with no locks held. For PQ that is essential - its k-means is
            // seconds to tens of seconds at the sample cap, and stalling the GigaMap monitor for
            // that long would stall every mutation and search. For NVQ it is merely tidy.
            if(needNvq)
            {
                try
                {
                    nvq.trainFrom(trainingSample);
                }
                catch(final RuntimeException e)
                {
                    LOG.warn("NVQ training failed for '{}', writing full-precision vectors", this.name, e);
                }
            }

            if(needPq && trainingSample.size() >= PQCompressionManager.MIN_VECTORS_FOR_PQ_TRAINING)
            {
                try
                {
                    pq.trainFrom(trainingSample);
                }
                catch(final RuntimeException e)
                {
                    // Log the throwable, not just its message: the interesting cases here are degenerate
                    // training data and dimension mismatches, and the cause chain is what identifies them.
                    LOG.warn("PQ training failed for '{}', writing an uncompressed graph", this.name, e);
                }
            }

            // Remember the state a failed attempt happened at, so the pending checks stop forcing a
            // full rebuild on every subsequent idle persist and only re-arm once data changes.
            final boolean pqStillUntrained  = needPq  && !pq.isTrained() ;
            final boolean nvqStillUntrained = needNvq && !nvq.isTrained();
            if(pqStillUntrained || nvqStillUntrained)
            {
                synchronized(this.parentMap())
                {
                    final long modCount = this.getStructuralModCount();
                    if(pqStillUntrained)
                    {
                        this.pqTrainingDeclinedAtModCount = modCount;
                    }
                    if(nvqStillUntrained)
                    {
                        this.nvqTrainingFailedAtModCount = modCount;
                    }
                }
            }

            // Whatever was trained here exists only in heap. The caller decides whether to skip the
            // persist, and it decides on the same pending predicates this method has just turned
            // false - so without this an idle, incremental-clean index would train a codebook and
            // then skip the write that is the entire reason it trained, leaving the feature
            // unpersisted for as long as nothing else changes.
            return (needPq && !pqStillUntrained) || (needNvq && !nvqStillUntrained);
        }

        private boolean isPqTrainingPending()
        {
            final PQCompressionManager pqManager = this.pqManager;
            if(pqManager == null || pqManager.isTrained())
            {
                return false;
            }

            // Only worth another attempt once something graph-affecting has changed since the last
            // one failed. Without this witness the carve-out never clears for a map that has enough
            // ENTITIES but not enough EMBEDDINGS: getVectorCount() is parentMap.size() in embedded
            // mode, so the count test stays true, trainPQ keeps declining on the collected list, and
            // every idle background persist would exit incremental mode, rebuild the whole graph and
            // rewrite it - an O(n) retry on a loop, forever.
            //
            // structuralModCount is the right witness because it also moves on vec<->null
            // transitions, which change the embedding population without changing the entity count.
            return this.getStructuralModCount() != this.pqTrainingDeclinedAtModCount
                && this.getVectorCount() >= PQCompressionManager.MIN_VECTORS_FOR_PQ_TRAINING
            ;
        }

        /**
         * Returns whether an NVQ training attempt is worth making on this persist.
         * <p>
         * Deliberately simpler than {@link #isPqTrainingPending()}, but the same shape and for the
         * same reason. NVQ declines on far less data than PQ - its threshold is a sanity floor of
         * 16 rather than a mathematical requirement of 256, because a mean needs one vector where
         * k-means needs 256 clusters per subspace - yet it does decline, and
         * {@code NVQCompressionManager.trainFrom} returns without training below that floor. An
         * embedded index with enough entities and too few embeddings reaches it, since the count
         * below is {@code parentMap.size()} in that mode.
         * <p>
         * So the witness is not only for exceptions: it stops either outcome from being retried on
         * every idle persist, each of which would otherwise leave incremental mode and rewrite the
         * whole graph.
         *
         * @return true if a quantizer is wanted and an attempt has not already failed at this state
         */
        private boolean isNvqTrainingPending()
        {
            final NVQCompressionManager nvqManager = this.nvqManager;
            if(nvqManager == null || nvqManager.isTrained())
            {
                return false;
            }
            return this.getStructuralModCount() != this.nvqTrainingFailedAtModCount
                && this.getVectorCount() >= NVQCompressionManager.MIN_VECTORS_FOR_NVQ_TRAINING
            ;
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

            // The full in-memory graph now exists: mark the (deferred) rebuild done so a later
            // first-access ensureGraphRebuilt() does not rebuild a second time on top of this one.
            this.graphRebuilt = true;
        }

        /**
         * Re-enters incremental mode after a disk write: reloads the disk index,
         * resets the in-memory builder to empty, and sets incremental state.
         * Must be called under builderLock.writeLock().
         * <p>
         * The reload is validated against {@code writtenMeta} - the witnesses that were just stamped
         * into the {@code .meta} - and NOT against the live store state. Persist Phase 2 runs with the
         * parentMap monitor released, so under sustained writes the live counters always advance past
         * the written graph and a live comparison would reject the file we just produced ourselves,
         * dropping the index into full in-memory mode for the rest of the session (internal #142).
         * Those newer mutations are not lost: they are exactly the ops sitting in
         * {@code deferredBuilderOps}, which the drain then applies on top of the reloaded graph -
         * which is what incremental mode is for. Crash-restart safety is unaffected: the store
         * counter now legitimately exceeds the {@code .meta} counter, so a restart before the next
         * persist still rejects the disk graph and rebuilds from the store.
         *
         * @param writtenMeta the witnesses stamped into the {@code .meta} by the preceding write
         */
        private void reenterIncrementalMode(final DiskIndexManager.MetaState writtenMeta)
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
                GraphFormat.of(this.configuration),
                this.configuration.parallelOnDiskWrite()
            );

            if(this.diskManager.tryLoad(writtenMeta))
            {
                this.adoptCompressorsFromLoadedGraph();

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

        @Deprecated
        @Override
        public void trainCompressionIfNeeded()
        {
            // Not for an in-memory PQ index - see the contract note on the interface method.
            // There, training is one step of a transition that also
            // encodes every ordinal and rebuilds the graph against the codes, and that has to happen
            // under the write lock at optimization. Training the codebook alone here would leave the
            // manager trained with no codes and no graph to match, which the switch reads as already
            // done - so the index would report compressed scoring while still traversing exactly,
            // permanently. Callers wanting the transition call optimize().
            if(this.pqManager == null
                || (!this.configuration.onDisk()
                    && this.configuration.approximateScoring() == ApproximateScoring.PQ_IN_MEMORY))
            {
                return;
            }

            synchronized(this.parentMap())
            {
                this.ensureIndexInitialized();
                this.pqManager.trainIfNeeded();

                // Remembered, because a persist cannot infer it. Training here satisfies the
                // pending check that the clean-incremental shortcut consults, so without this the
                // next persist would skip the write and leave the codebook in heap - see the field.
                if(this.pqManager.isTrained())
                {
                    this.compressionTrainedOutsidePersist = true;
                }
            }
        }

        @Override
        public boolean isPqCompressionActive()
        {
            if(!this.configuration.onDisk()
                && this.configuration.approximateScoring() == ApproximateScoring.PQ_IN_MEMORY)
            {
                // For an in-memory index the codes are the witness, not the codebook. Training is
                // one step of a transition that also encodes every ordinal and rebuilds the graph,
                // and it runs with no lock held - so between the codebook existing and the graph
                // being swapped in there is a window, as long as the rebuild, during which the
                // manager reports trained while search is still scoring exactly. The published
                // codes appear at the same instant as the graph that scores from them, which is
                // what "actually in effect" has to mean here.
                return this.inMemoryPqVectors != null;
            }

            final PQCompressionManager pqManager = this.pqManager;
            return pqManager != null && pqManager.isTrained();
        }

        @Override
        public boolean isNvqCompressionActive()
        {
            final NVQCompressionManager nvqManager = this.nvqManager;
            return nvqManager != null && nvqManager.isTrained();
        }

        /**
         * Syncs the PQ manager with the codebook actually embedded in the freshly loaded disk graph.
         * <p>
         * Called after every successful {@code tryLoad}. Passing {@code null} through is the point:
         * it resets the manager to untrained so the next persist trains and writes a compressed
         * graph, instead of the manager reporting itself trained while holding no codebook - the
         * state in which {@link DiskIndexManager#writeIndex} silently writes uncompressed forever.
         * <p>
         * Not all call sites hold {@code builderLock.writeLock()} - the one in
         * {@code initializeIndex} runs under whatever the caller happened to hold, which for a search
         * is nothing at all. What actually makes this safe is that the managers publish their state
         * through volatile fields.
         */
        private void adoptCompressorsFromLoadedGraph()
        {
            if(this.pqManager != null)
            {
                final ProductQuantization loadedPq = this.diskManager.loadedProductQuantization();
                this.pqManager.adoptTrainedPQ(loadedPq);

                if(loadedPq != null)
                {
                    LOG.debug("Recovered FusedPQ codebook from the disk graph for '{}'", this.name);
                }
                else
                {
                    LOG.debug("Disk graph for '{}' carries no FusedPQ; PQ will be trained on the next persist",
                        this.name);
                }
            }

            if(this.nvqManager != null)
            {
                // Unlike the codebook this is an optimisation rather than a requirement: search reads
                // the quantizer out of the loaded graph's own NVQ feature, and every persist rewrites
                // the graph with whatever quantizer it then holds. Adopting simply saves the next
                // persist a mean pass and lets isNvqCompressionActive() answer truthfully right away.
                final NVQuantization loadedNvq = this.diskManager.loadedNVQuantization();
                this.nvqManager.adoptTrainedNVQ(loadedNvq);

                if(loadedNvq != null)
                {
                    LOG.debug("Recovered NVQ quantizer from the disk graph for '{}'", this.name);
                }
                else
                {
                    LOG.debug("Disk graph for '{}' carries no NVQ; it will be trained on the next persist",
                        this.name);
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

                // Set under the write lock, which is what makes it mean anything. doPersistToDisk
                // trains before taking any lock and only then locks and re-initialises, so a
                // persist that started before this close can arrive afterwards and rebuild
                // everything torn down above - background task manager included. Both sides touch
                // this flag while holding this lock, so either the persist reads it and stops, or
                // it holds the lock first and this close waits for it to finish.
                this.closed = true;
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
            else if(persistPending && this.configuration.onDisk())
            {
                // Honor persistOnShutdown for on-disk indices configured without
                // any background features (no eventual indexing, no background
                // optimization, no background persistence).
                try
                {
                    this.doPersistToDisk(true);
                }
                catch(final Exception e)
                {
                    LOG.error("Shutdown persistence failed for '{}': {}", this.name, e.getMessage(), e);
                }

                // Belt and braces: the persist above no longer initializes the index, so nothing
                // should have created a manager behind this method's null check. Should a future
                // change reintroduce that, shut the manager down here rather than let its executor
                // thread outlive close().
                if(this.backgroundTaskManager != null)
                {
                    LOG.warn("Background task manager for '{}' was recreated during shutdown "
                        + "persistence, shutting it down again", this.name);
                    this.backgroundTaskManager.discardQueue();
                    this.backgroundTaskManager.shutdown(false, false, false);
                    this.backgroundTaskManager = null;
                }
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

            // The in-memory codes go with the graph they describe. internalRemoveAll tears down and
            // then re-initialises, so leaving them would hand the new, empty index the old codebook
            // and codes - and, because the switch returns early when this field is set, would stop
            // it ever switching again.
            this.inMemoryPqVectors = null;

            // And so does being a restored index. What comes back from internalRemoveAll is a new,
            // empty generation whose entities arrive afterwards and are counted like any other
            // index's, so it earns its optimizations the ordinary way. Left set, it would keep
            // claiming the carve-out for an index that cannot earn one, and a refill past the
            // training minimum could switch on a scheduled tick without ever reaching
            // minChangesBetweenOptimizations.
            this.restoredFromStorage = false;

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

            // Reset the compression managers
            if(this.pqManager != null)
            {
                this.pqManager.reset();
                this.pqManager = null;
            }
            if(this.nvqManager != null)
            {
                this.nvqManager.reset();
                this.nvqManager = null;
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
                    this::lookupComputedVector,
                    // RAVV size() is the dense ordinal upper bound (getVector must be valid for
                    // [0, size())), NOT the vector count. Graph ordinals are source entity ids, so with
                    // null embeddings / deletion holes the highest ordinal exceeds vectorStore.size().
                    // PQ encodeAll() builds a dense PQVectors of this length and FusedPQ / PQ search then
                    // index it by graph ordinal — an under-reported count skips/overflows high ordinals
                    // (IndexOutOfBoundsException). Match the graph's ordinal space (see getHighestEntityId()).
                    () -> Math.toIntExact(this.parentMap().highestUsedId() + 1),
                    this.configuration.dimension(),
                    this.vectorTypeSupport
                );
        }


        ///////////////////////////////////////////////////////////////////////////
        // callback interface implementations //
        ////////////////////////////////////////

        // TrainingVectorProvider

        @Override
        public long getVectorCount()
        {
            return this.getExpectedVectorCount();
        }

        @Override
        public List<VectorFloat<?>> collectTrainingVectors(final int limit)
        {
            // Reservoir sampling (Algorithm R): keeps a uniform sample of at most `limit` vectors
            // without knowing the population size up front.
            //
            // A stride computed from getVectorCount() cannot do this job. That count is an upper
            // bound, not the number of embeddings - in embedded mode it is parentMap.size(), which
            // includes entities with no vector - so on a sparse map the stride is too coarse and the
            // sample can fall below MIN_VECTORS_FOR_PQ_TRAINING, leaving PQ untrained forever. And
            // integer division floors to a stride of 1 for any population between limit and 2*limit,
            // which degenerates to taking the first `limit` vectors: exactly the insertion-ordered
            // prefix that skews a codebook.
            //
            // The RNG is seeded per call so training is reproducible: the same data always yields
            // the same codebook.
            final List<VectorFloat<?>> reservoir = new ArrayList<>(Math.min(limit, 1024));
            final Random               random    = new Random(RESERVOIR_SEED);
            final long[]               seen      = {0L};

            final Consumer<float[]> sample = vector ->
            {
                final long n = ++seen[0];
                if(reservoir.size() < limit)
                {
                    reservoir.add(this.vectorTypeSupport.createFloatVector(vector));
                    return;
                }
                // Replace with probability limit/n, which leaves every vector seen so far equally
                // likely to be in the reservoir. The bounded nextLong is required for that: taking
                // a full-width value modulo n biases the low indices whenever n is not a power of
                // two, which would quietly break the uniformity this sample is chosen for. The long
                // overload also keeps the arithmetic correct past 2^31 vectors.
                final long candidate = random.nextLong(n);
                if(candidate < limit)
                {
                    reservoir.set((int)candidate, this.vectorTypeSupport.createFloatVector(vector));
                }
            };

            if(this.isEmbedded())
            {
                this.parentMap().iterate(entity ->
                {
                    final float[] vector = this.vectorize(entity);
                    // Skip entities without an embedding — they are not part of the graph and
                    // must not feed PQ codebook training.
                    if(vector != null)
                    {
                        sample.accept(vector);
                    }
                });
            }
            else if(this.vectorStore != null)
            {
                // Computed mode never stores null-vector entries; guard defensively anyway.
                this.vectorStore.iterate(entry ->
                {
                    if(entry.vector != null)
                    {
                        sample.accept(entry.vector);
                    }
                });
            }

            return reservoir;
        }

        // DiskIndexManager.IndexStateProvider

        @Override
        public long getExpectedVectorCount()
        {
            // Counts indexed entities, NOT graph nodes. In computed mode the two are equal
            // (null-vector entities have no store entry). In embedded mode this can over-count
            // when some entities have no embedding — that is fine for the disk metadata check,
            // which compares this value against itself on write and read, and for the PQ
            // training gate, which trains on the actually-collected (non-null) vectors.
            if(this.isEmbedded())
            {
                return this.parentMap().size();
            }
            else
            {
                return this.vectorStore != null ? this.vectorStore.size() : 0;
            }
        }

        @Override
        public long getHighestEntityId()
        {
            // Always use the parent GigaMap's id space — that is the ordinal
            // space the HNSW graph is keyed on (via toOrdinal(entityId)) in
            // both embedded and computed-vector modes. The computed-mode
            // vectorStore has its own monotonic id allocator that diverges
            // from the parent map's (e.g. when an index is registered against
            // a parent with deletion holes, or when some entities have no
            // embedding and so get no vectorStore entry at all), so it is
            // unsafe as a stand-in. Note this is only about the highest-id
            // bound: per-entity vector lookups no longer assume alignment —
            // they resolve by VectorEntry.sourceEntityId (see lookupComputedVector).
            return this.parentMap().highestUsedId();
        }

        @Override
        public long getStructuralModCount()
        {
            // The persisted witness stamped into the disk .meta at write time and compared on
            // reload. Unlike count/highestId it changes on vec↔null transitions, closing the
            // crash-restart window where a stale on-disk graph would otherwise be accepted.
            return this.structuralModCount;
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
            if(entry.vector == null)
            {
                // Entity has no embedding — nothing to add to the graph. Callers already
                // filter these out; this is a defensive guard on the worker thread.
                return;
            }
            this.builderLock.readLock().lock();
            try
            {
                final int ordinal = toOrdinal(entry.sourceEntityId);
                final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(entry.vector);

                // Through the idempotent helper rather than addGraphNode directly. This callback is
                // queued by a mutation and applied later, and in between the in-memory PQ switch can
                // have replayed the same ordinal into a replacement graph from its own snapshot -
                // the snapshot is taken after the mutation, so the change counter does not reject
                // it. Adding an ordinal the graph already holds throws. The helper also reads the
                // builder once, which matters here for the same reason it does on the sync path.
                this.internalAddGraphNodeIdempotent(ordinal, vf);
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
                    if(entry.vector == null)
                    {
                        // Entity has no embedding — excluded from the graph.
                        continue;
                    }
                    final int ordinal = toOrdinal(entry.sourceEntityId);
                    final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(entry.vector);

                    // Idempotent, for the reason given on applyGraphAdd.
                    this.internalAddGraphNodeIdempotent(ordinal, vf);
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
                // One generation, captured once. This callback holds only the read lock, and the
                // in-memory PQ switch publishes a replacement while holding neither - so reading
                // this.builder or this.index again further down could land on the other side of
                // that publication. Split across the two, this method would delete the node from
                // the graph being abandoned and then add an ordinal the replacement already carries,
                // which throws. The graph comes from the captured builder for the same reason.
                final GraphIndexBuilder currentBuilder = this.builder;
                if(currentBuilder == null)
                {
                    return;
                }
                final OnHeapGraphIndex currentIndex = graphOf(currentBuilder);

                final int ordinal = toOrdinal(entry.sourceEntityId);
                final boolean inGraph = currentIndex != null && currentIndex.containsNode(ordinal);

                if(entry.vector == null)
                {
                    // vec→null / null→null: remove the node if present, otherwise nothing to do.
                    if(inGraph)
                    {
                        currentBuilder.markNodeDeleted(ordinal);
                        // After the delete, so no in-flight scorer sees a zeroed code for a node
                        // that is still live. The eventual-indexing paths need this as much as the
                        // synchronous ones: without it a deleted node keeps a code traversal can
                        // still score against and waste hops on.
                        this.untrackPqCode(ordinal);
                    }
                    return;
                }

                // vec→vec: delete the stale node then re-add. null→vec: the node is absent,
                // so add it directly without the (invalid) delete.
                if(inGraph)
                {
                    currentBuilder.markNodeDeleted(ordinal);
                    currentBuilder.removeDeletedNodes();
                }
                final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(entry.vector);
                this.trackPqCode(ordinal, vf);
                currentBuilder.addGraphNode(ordinal, vf);
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
                // One generation, for the reason given on applyGraphUpdate: the containment test
                // and the delete must be about the same graph.
                final GraphIndexBuilder currentBuilder = this.builder;

                // Guard against removing a never-indexed entity (e.g. one that never had an
                // embedding): markNodeDeleted on an absent node would fail.
                if(currentBuilder != null && graphOf(currentBuilder).containsNode(ordinal))
                {
                    currentBuilder.markNodeDeleted(ordinal);
                    this.untrackPqCode(ordinal);
                }
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
         * Marks a graph ordinal deleted, re-deriving the target from CURRENT state at execution time
         * rather than from a captured builder reference. This matters when the op is deferred
         * (via {@link #executeOrDeferBuilderOp}) during a persist and drained afterwards: {@code
         * doPersistToDisk} swaps the in-memory builder (exit/reenter incremental mode) between deferral
         * and drain, so a captured {@code builder.markNodeDeleted(ordinal)} would land on the new, empty
         * builder and be lost — leaving the entity live on the reloaded disk graph until the next
         * persist. Recording the deletion in {@link #diskDeletedOrdinals} (when incremental) makes it
         * survive the swap; the in-builder {@code markNodeDeleted} is applied only when the node is
         * actually present in the current builder.
         */
        private void internalMarkOrdinalDeleted(final int ordinal)
        {
            this.recordDiskOrdinalSuperseded(ordinal);
            final GraphIndexBuilder currentBuilder = this.builder;
            if(currentBuilder != null && graphOf(currentBuilder).containsNode(ordinal))
            {
                currentBuilder.markNodeDeleted(ordinal);
                this.untrackPqCode(ordinal);
            }
        }

        /**
         * Records that the disk-resident version of an ordinal is no longer authoritative, so
         * {@link #createDiskAcceptBits()} excludes it from disk search.
         * <p>
         * Must be called by every op that removes or re-adds an ordinal in the in-memory builder while
         * incremental mode is active, and it must be called when the op RUNS, not when it is created:
         * a persist allocates a fresh, empty {@link #diskDeletedOrdinals} in
         * {@code reenterIncrementalMode}, so an entry made before deferral is gone by the time the op
         * is drained, and the stale disk node would keep answering searches next to the fresh
         * in-memory one (internal #142).
         * <p>
         * Not called for a plain add: a newly allocated entity id was never written to disk, so
         * recording it would only enlarge the {@code boolean[maxOrdinal+1]} mask that
         * {@code createDiskAcceptBits()} rebuilds on every search.
         */
        private void recordDiskOrdinalSuperseded(final int ordinal)
        {
            if(this.incrementalMode && this.diskDeletedOrdinals != null)
            {
                this.diskDeletedOrdinals.add(ordinal);
            }
        }

        /**
         * Replaces a graph node with a new vector, re-deriving every premise from CURRENT state at
         * execution time. Used by the computed (non-embedded) update path, whose deferred op may be
         * drained against a builder that was swapped since the op was created.
         * <p>
         * The removal half must never be skipped while the add half runs unconditionally: jvector's
         * {@code OnHeapGraphIndex.addNode} is a non-atomic loop over layers {@code 0..level} that
         * throws {@code IllegalStateException: Node N already exists} on the first layer already
         * holding the ordinal, and {@code containsNode} only inspects layer 0. So the final
         * {@code removeNode} is not redundant with {@code removeDeletedNodes}: it purges the ordinal
         * from ALL layers and thereby also repairs a node left in an upper layer only by an earlier
         * interleaved add/remove (internal #142).
         * <p>
         * Only safe for the computed vectorizer: {@code removeDeletedNodes()} scores through a
         * ForkJoinPool, whose workers read the separate vector store rather than the parent GigaMap,
         * so it cannot deadlock against the GigaMap monitor the drain holds.
         */
        private void internalReplaceGraphNode(final int ordinal, final VectorFloat<?> vf)
        {
            this.recordDiskOrdinalSuperseded(ordinal);
            final GraphIndexBuilder currentBuilder = this.builder;
            if(currentBuilder == null)
            {
                return;
            }
            final OnHeapGraphIndex currentIndex = graphOf(currentBuilder);
            if(currentIndex.containsNode(ordinal))
            {
                // Repairs the neighbor lists that referenced the node before dropping it.
                currentBuilder.markNodeDeleted(ordinal);
                this.untrackPqCode(ordinal);
                currentBuilder.removeDeletedNodes();
            }
            currentIndex.removeNode(ordinal);
            this.trackPqCode(ordinal, vf);
            currentBuilder.addGraphNode(ordinal, vf);
        }

        /**
         * Adds a graph node, tolerating an ordinal that is already present. Monitor-safe: it never
         * calls {@code removeDeletedNodes()}, whose ForkJoinPool workers resolve vectors through the
         * parent GigaMap for an embedded vectorizer and would deadlock against the monitor the caller
         * holds. The re-added node loses its neighbor links, which the next optimize/persist rebuilds
         * - the same contract the embedded vec-vec path documents.
         * <p>
         * Re-deriving presence here rather than at deferral time is what makes the op idempotent
         * against whatever builder it is eventually drained into (internal #142).
         */
        private void internalAddGraphNodeIdempotent(final int ordinal, final VectorFloat<?> vf)
        {
            final GraphIndexBuilder currentBuilder = this.builder;
            if(currentBuilder == null)
            {
                return;
            }
            final OnHeapGraphIndex currentIndex = graphOf(currentBuilder);

            // The code first, whichever branch follows: it is what the node is scored by once
            // compressed scoring is on, and the insert below reads it while choosing neighbours.
            this.trackPqCode(ordinal, vf);

            if(currentIndex.containsNode(ordinal))
            {
                // Already a node of this graph, so there is nothing structural left to do - and
                // reaching the same state by removing and re-adding is not merely wasteful, it
                // corrupts the graph. removeNode strips the node's own entry but not the backlinks
                // its neighbours hold, so the next insert that picks one of those stale references
                // fails with an NPE inside ConcurrentNeighborMap. The callers that drain these ops
                // swallow the exception, which leaves the node removed and never re-added.
                //
                // That is not a rare interleaving. After the in-memory PQ switch every queued add
                // names an ordinal the replay has already inserted, so an unconditional remove
                // would churn the entire graph and lose nodes out of it.
                //
                // Clearing a deleted bit is kept, because an add for an ordinal that is present
                // but marked deleted does mean "this entity is back" - the same thing the resurrect
                // path does, and for the same reason.
                currentIndex.getDeletedNodes().clear(ordinal);
                return;
            }

            // containsNode only inspects layer 0, so "absent" can still mean "present in an upper
            // layer" after an earlier interleaved add/remove. Purge all layers before adding, or
            // the add throws. Safe here in a way it is not above: there is no live layer-0 node
            // whose neighbours could be left holding a reference to it.
            currentIndex.removeNode(ordinal);
            currentBuilder.addGraphNode(ordinal, vf);
        }

        /**
         * {@link #internalAddGraphNodeIdempotent} for an ordinal that may already have a version on
         * disk, i.e. the embedded {@code null→vec} update path. Masks the disk-resident node first so
         * search does not answer from both copies.
         */
        private void internalReaddGraphNode(final int ordinal, final VectorFloat<?> vf)
        {
            this.recordDiskOrdinalSuperseded(ordinal);
            this.internalAddGraphNodeIdempotent(ordinal, vf);
        }

        /**
         * Re-adds or resurrects an embedded-mode node, deciding from CURRENT state at execution time.
         * A node marked deleted only carries a bit (it stays in every layer), so clearing that bit is
         * enough and keeps the op monitor-safe; a node that is gone entirely - e.g. because the
         * builder was swapped between deferral and drain - must be added instead, otherwise the
         * entity silently disappears from the graph.
         */
        private void internalResurrectGraphNode(final int ordinal, final VectorFloat<?> vf)
        {
            this.recordDiskOrdinalSuperseded(ordinal);
            final GraphIndexBuilder currentBuilder = this.builder;
            if(currentBuilder == null)
            {
                return;
            }
            final OnHeapGraphIndex currentIndex = graphOf(currentBuilder);
            if(currentIndex.containsNode(ordinal))
            {
                // The node keeps its place in the graph, but its vector may not be the one it was
                // inserted with - this path also serves an update whose embedding changed. Exact
                // scoring never notices, because it reads the vector live from the GigaMap. A PQ
                // code is a copy, so leaving it stale would keep scoring this node by the embedding
                // it used to have, and it would stop being findable by its new one.
                //
                // Before the node becomes visible, not after. This path holds no builderLock, so a
                // search can be traversing concurrently: clearing the deleted bit first would make
                // the node reachable for the instant before its code is written, and it would be
                // scored against whatever the code used to be - the old embedding, or zero. Every
                // add path already writes the code before the node becomes reachable; this is the
                // same rule for the resurrect path.
                this.trackPqCode(ordinal, vf);

                currentIndex.getDeletedNodes().clear(ordinal);
                return;
            }
            // containsNode only inspects layer 0, so "absent" can still mean "present in an upper
            // layer" after an earlier interleaved add/remove. Purge all layers before adding, or the
            // add throws the very IllegalStateException this change is about.
            currentIndex.removeNode(ordinal);
            this.trackPqCode(ordinal, vf);
            currentBuilder.addGraphNode(ordinal, vf);
        }

        private static OnHeapGraphIndex graphOf(final GraphIndexBuilder builder)
        {
            return (OnHeapGraphIndex)builder.getGraph();
        }

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
         * <p>
         * Runs under {@code synchronized(parentMap())} because that monitor is the only lock the
         * sync-mode mutation paths hold: without it the persist/optimize threads drain the queue
         * bare (they release builderLock before draining) and race the application thread, which
         * drains and executes the very same kind of op inline. Two such threads both seeing
         * {@code containsNode(ordinal) == false} both call {@code addGraphNode} and one dies with
         * {@code IllegalStateException: Node N already exists}, aborting the drain and killing the
         * persist cycle for good (internal #142). The monitor is the outermost lock here - the
         * persist/optimize drains hold nothing at this point - so it introduces no lock inversion.
         * <p>
         * INVARIANT: a deferred op must never enter a ForkJoinPool whose workers need this monitor.
         * {@code addGraphNode} never does; {@code removeDeletedNodes()} does, and is therefore
         * reachable only from the computed path, which scores through the separate vector store.
         * <p>
         * A failing op is logged and skipped rather than allowed to abort the drain: the op is
         * already polled and lost either way, but stranding the rest of the queue would leave the
         * index unable to ever persist again.
         */
        private void drainDeferredBuilderOps()
        {
            final Runnable entryHook = this.drainEntryTestHook;
            if(entryHook != null)
            {
                entryHook.run();
            }

            synchronized(this.parentMap())
            {
                Runnable op;
                while((op = this.deferredBuilderOps.poll()) != null)
                {
                    try
                    {
                        op.run();
                    }
                    catch(final RuntimeException e)
                    {
                        LOG.error("Deferred builder operation failed for index '{}', skipping it: {}",
                            this.name, e.getMessage(), e);
                    }
                }
            }
        }

    }

}
