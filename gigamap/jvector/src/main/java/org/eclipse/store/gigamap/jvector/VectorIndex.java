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
import io.github.jbellis.jvector.util.PhysicalCoreExecutor;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.exceptions.IORuntimeException;
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.util.X;
import org.eclipse.store.gigamap.jvector.BackgroundTaskManager.IndexingOperation;
import org.eclipse.store.gigamap.types.AbstractStateChangeFlagged;
import org.eclipse.store.gigamap.types.GigaIndex;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexGroup;
import org.eclipse.store.gigamap.types.IndexLocation;
import org.eclipse.store.gigamap.types.IndexLocations;
import org.eclipse.store.gigamap.types.ScoredSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static org.eclipse.serializer.math.XMath.positive;
import static org.eclipse.serializer.util.X.notNull;

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
 * The {@link Vectorizer} extracts vector representations from entities. Whichever way it gets them, it must
 * reproduce the vector of a persisted entity: the index keeps its vectors in a memory-mapped vector file, a
 * cache it repairs from the entities after a crash (see {@link Vectorizer}).
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
 *         return true;  // Documents where the vector lives
 *     }
 * }
 * }</pre>
 *
 * <h3>Computed Mode (vectors computed from the entity)</h3>
 * Use when vectors are computed externally (e.g., from an embedding API), which must return the same vector
 * for the same persisted entity:
 * <pre>{@code
 * class ComputedVectorizer extends Vectorizer<Document> {
 *     private final EmbeddingService embeddingService;
 *
 *     @Override
 *     public float[] vectorize(Document doc) {
 *         return embeddingService.embed(doc.content());
 *     }
 *
 *     // isEmbedded() defaults to false
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
 * thread. The vector file is still updated synchronously, so no data is lost, but graph construction
 * happens asynchronously. This reduces the latency of mutation operations at the cost of eventual
 * consistency — search results may not immediately reflect the most recent mutations.
 * <p>
 * Pending operations are applied to the graph before {@code optimize()} and {@code close()}.
 * {@code persistToDisk()} does not drain the queue: it moves the pending operations into the graph
 * it freezes, and the flush applies them there.
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
 *   <li><b>Optimization</b> - Blocks add/remove/search during cleanup of an in-memory index; an
 *       on-disk index cleans up each segment as it writes it</li>
 *   <li><b>Persistence</b> - Add/remove/search go on while a segment is written</li>
 *   <li><b>Eventual Indexing</b> - Graph mutations are applied sequentially by a single
 *       background worker thread; vector file updates remain synchronous</li>
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

    /**
     * Returns the directory this index instance keeps its files in: the configuration's
     * {@link VectorIndexConfiguration#indexLocation() location}, resolved when the index was opened in
     * this run. For a {@link IndexLocation#Named(String) named location} that is the directory the name
     * was bound to at that moment. Never the stored {@link VectorIndexConfiguration#indexDirectory()} of a
     * named location, which is kept only for older library versions.
     * <p>
     * This default implementation, and an index not opened yet, report the directory the name is bound
     * to now.
     *
     * @return the index directory, or {@code null} for an in-memory index or an unbound name
     */
    public default Path indexDirectory()
    {
        // an index without files has no directory, whatever its configuration may carry (see keepsIndexFiles)
        final IndexLocation location = this.keepsIndexFiles()
            ? this.configuration().indexLocation()
            : null
        ;
        if(location == null)
        {
            return null;
        }
        return location.isNamed()
            ? IndexLocations.lookup(location.name())
            : location.directory()
        ;
    }

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
     * @throws IllegalStateException    if {@link #close()} is in progress while the in-memory graph
     *                                  still has to be rebuilt (the first access after a load, or after
     *                                  a failed persist): the rebuild is not done or is abandoned, with
     *                                  the message "is closing; its graph was not rebuilt", rather than
     *                                  answering from an incomplete graph
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
     * @throws IllegalStateException    if {@link #close()} is in progress while the graph still has
     *                                  to be rebuilt, see {@link #search(float[], int)}
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
     * <p>
     * A computed vectorizer's result is copied: the search's first access can recover the index, which calls
     * the same vectorizer on this thread and would overwrite a reused buffer before the query is scored.
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
        final Vectorizer<? super E> vectorizer  = this.vectorizer();
        final float[]               queryVector = vectorizer.vectorize(queryEntity);
        if(queryVector == null)
        {
            throw new IllegalArgumentException(
                "Query entity has no vector: the vectorizer returned null. "
                    + "Entities without embeddings cannot be used as similarity queries."
            );
        }
        return vectorizer.isEmbedded() ? queryVector : queryVector.clone();
    }

    /**
     * Performs cleanup and optimization of the index graph structure.
     * <p>
     * This method purges removed entities from the graph and removes excess neighbor connections
     * that accumulate during graph construction, reducing memory usage and improving query
     * latency. It is recommended to call this periodically after bulk insertions or when query
     * performance degrades. The graph stays open for further mutations afterwards.
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
     * For an in-memory index this method acquires an exclusive lock on the graph structure. While
     * optimization is running:
     * <ul>
     *   <li>Add/remove operations will block until optimization completes</li>
     *   <li>Search operations will block until optimization completes</li>
     * </ul>
     * For large indices, consider using background optimization to minimize disruption. An on-disk
     * index leaves its graphs alone here, so the call returns at once: every persist cleans up the
     * in-memory graph it writes, and the segments on disk are immutable. An in-memory index configured
     * with {@link ApproximateScoring#PQ_IN_MEMORY} trains its codebook and switches to compressed
     * scoring here, once enough vectors exist.
     *
     * <h4>When to Optimize</h4>
     * <ul>
     *   <li>After adding many entities (e.g., &gt;1000)</li>
     *   <li>After removing many entities</li>
     *   <li>When query latency increases noticeably</li>
     *   <li>Periodically (e.g., hourly or daily) for continuously updated indices</li>
     * </ul>
     * <p>
     * Must not be called while holding the parent {@link GigaMap}'s monitor (inside {@code synchronized(map)} or
     * {@code GigaMap.apply} / {@code update} logic): it waits for the graph's write lock, whose holders may be
     * waiting for that monitor, so the call could wait for itself.
     *
     * @throws IllegalStateException if the calling thread holds the parent GigaMap's monitor
     * @see VectorIndexConfiguration#backgroundOptimization()
     * @see VectorIndexConfiguration#optimizationIntervalMs()
     */
    public void optimize();

    /**
     * Persists the index to disk if on-disk mode is enabled.
     * <p>
     * For on-disk indices, this writes the in-memory graph, which holds the changes since the
     * previous persist, as one more segment in the configured
     * {@link VectorIndexConfiguration#indexDirectory()}, together with the deletions of the existing
     * segments, and commits them with a new manifest. Nothing is rebuilt: the graph is frozen in an
     * instant and written while a fresh in-memory graph takes new mutations. Segments are combined by
     * background merges (see {@link VectorIndexConfiguration#segmentMergeFactor()}), and a persist
     * commits what they published. Segments that no longer match the configured format or codebook are
     * rewritten by a background merge as well, but this explicit persist rewrites them itself, on the
     * calling thread, and commits the rewrite before it returns. Only when the segments would exceed {@link VectorIndexConfiguration#maxSegments()}
     * all the same does the persist consolidate them, rebuilding the index from its vector file into
     * one segment. With {@link ApproximateScoring#FUSED_PQ} the PQ codes are fused into each segment's
     * graph file; with {@link ApproximateScoring#PQ_IN_MEMORY} they go to the segment's {@code .pqv}
     * sidecar.
     * <p>
     * For in-memory indices ({@link #isOnDisk()} returns false), this method is a no-op.
     * <p>
     * An index that holds no vectors has nothing to write: any files from an earlier, non-empty
     * state are removed instead, so the next load rebuilds from the vector file. This applies to
     * this explicit persist and to background persistence. A persist on the shutdown path may
     * instead leave those files in place, see {@link #close()}.
     *
     * <h4>Files Created</h4>
     * <ul>
     *   <li><code>{indexName}.manifest</code> - The commit point: the segments of the index, the
     *       format and deletions of each, and the store state they describe</li>
     *   <li><code>{indexName}.seg-{n}.graph</code> and <code>{indexName}.seg-{n}.l2g</code> - The
     *       HNSW graph of segment {@code n} and its local-to-global ordinal map</li>
     *   <li><code>{indexName}.seg-{n}.pqv</code> - The PQ codes of segment {@code n}, only with
     *       {@link ApproximateScoring#PQ_IN_MEMORY}</li>
     * </ul>
     * The vectors themselves are in the index's vector file (<code>{indexName}.vec</code> and its chunks
     * <code>{indexName}.vec-{n}</code>), which mutations write directly; this persist does not write it.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * // Manual persistence after bulk operations
     * for (Document doc : documents) {
     *     gigaMap.add(doc);
     * }
     * index.persistToDisk();
     *
     * }</pre>
     * Shutting the storage down closes the index, which persists it when
     * {@link VectorIndexConfiguration#persistOnShutdown()} is set; no shutdown hook is needed.
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
     * Searches and add/remove operations go on while the segment is written; they wait only for the
     * instant in which the in-memory graph is frozen, and for the rebuild of a consolidation. One
     * persist writes at a time: a call waits for a background persist that is writing.
     *
     * <h4>Recovery</h4>
     * On restart, the segments are loaded if the manifest exists, its checksum holds and the
     * state it records (vector count, highest entity id, structural modification count) matches the
     * stored index. Otherwise, or if a segment fails to open, the graph is rebuilt from the vector
     * file and the next persist replaces the rejected files.
     * <p>
     * For an on-disk index, must not be called while holding the parent {@link GigaMap}'s monitor (inside
     * {@code synchronized(map)} or {@code GigaMap.apply} / {@code update} logic): it waits for a flush in flight,
     * which may be waiting for that monitor, so the call could wait for itself. For an in-memory index this method
     * does nothing.
     *
     * @throws IllegalStateException if the index is on disk and the calling thread holds the parent GigaMap's
     *         monitor
     * @see #isOnDisk()
     * @see VectorIndexConfiguration#indexDirectory()
     * @see VectorIndexConfiguration#backgroundPersistence()
     */
     public void persistToDisk();

    /**
     * Invalidates the transient in-memory search graph so the next access rebuilds it
     * from the current stored state.
     * <p>
     * This is the supported seam for integrations that update the underlying entity /
     * vector data behind this index's back — most notably replication or live
     * binary-import layers (e.g. Eclipse Serializer based graph merging) that apply
     * remote state to a running graph without going through
     * {@code add}/{@code update}/{@code set}. In such a scenario the in-memory HNSW
     * graph would otherwise keep answering queries from the pre-update state: the
     * persisted data was replaced in place, while the transient graph, which is
     * deliberately not part of the serialized state, was not.
     * <p>
     * This method releases the current in-memory builder and graph and clears the
     * rebuild-once guard. The next {@link #search(float[], int)},
     * {@code optimize()}, or mutation lazily rebuilds the graph from the stored
     * vectors, exactly as after a fresh store load (internal #87). Callers must not
     * rely on query-serving until such an access has run.
     *
     * <h4>Derived vector files</h4>
     * An index with {@link #keepsIndexFiles()} {@code false} derives its temporary vector file from the
     * entities. This call marks that file stale too: the next access clears its slots and recovers the
     * current vectors before rebuilding the graph. Both clearing and vectorization are deferred, so repeated
     * invalidations without an access do neither. The persisted vector count is preserved for recovery's
     * content check: regenerating a cache must not advance the persisted change counter. An index that keeps
     * its vector file leaves that file unchanged; the caller replaces those vectors itself.
     *
     * <h4>Locking</h4>
     * The teardown runs under the builder write lock, mirroring
     * {@code internalRemoveAll}: concurrent searches, persistence, and background
     * mutations are excluded. Callers synchronizing on the parent {@code GigaMap}
     * (the typical replication/merge protocol) can call this directly: a thread
     * holding the monitor never waits for the write lock, whose holders (a persist,
     * an embedded-mode search) may need that monitor. It only tries the lock and,
     * while it is held elsewhere, waits on the monitor, which releases it so the
     * holders can finish, exactly as {@code GigaMap.removeAll()} and
     * {@code reindex()} do. The critical section of such a caller is therefore
     * interrupted at this point, before anything has been changed. Meanwhile new
     * searches of the index group are held back briefly so that overlapping searches
     * cannot starve the invalidation. Without the monitor the call simply waits for
     * the lock. The method never acquires the parent monitor itself.
     * <p>
     * Calling it from within a search of the same index (the thread holds the read
     * lock) throws an {@link IllegalStateException}: the write lock could never be
     * granted.
     *
     * <h4>On-disk mode</h4>
     * An on-disk index serves its graph from the segments in its index directory, which
     * this seam does not retire: retiring those along with stale stored vectors is the
     * load-time mismatch repair, not an external state change. Calling this on such an
     * index fails with an {@link IllegalStateException} rather than silently
     * half-invalidating.
     *
     * @throws IllegalStateException if the index is on disk, or if called
     *         from within a search of this index
     */
    public void invalidateGraph();

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
     * Whether this index keeps files in its index directory: the graph segments and the vector file of an
     * on-disk index, or the vector file alone of an in-memory index with a computed vectorizer and a configured
     * directory or location. Such an in-memory index keeps its vectors across restarts and rebuilds its graph
     * from them without a vectorizer call per entity; one without a directory, like every embedded in-memory
     * index, uses a temporary vector file and vectorizes every entity at each start.
     *
     * @return whether the index directory holds files of this index
     */
    public default boolean keepsIndexFiles()
    {
        return this.configuration().onDisk()
            || !this.vectorizer().isEmbedded() && this.configuration().indexLocation() != null
        ;
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
     * Read from the index's vector file, in both vectorizer modes, without calling the vectorizer. The result
     * is a copy: changing it changes nothing in the index.
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
     *   <li>Stops segment merges: a merge in flight stops at its next stage, and no new one starts
     *       (on-disk indices)</li>
     *   <li>Shuts down the background tasks: applies the queued indexing operations, then optionally
     *       runs a final optimization if {@link VectorIndexConfiguration#optimizeOnShutdown()} is
     *       enabled, and optionally persists pending changes if
     *       {@link VectorIndexConfiguration#persistOnShutdown()} is enabled (on-disk indices)</li>
     *   <li>Tears down the graph, the searchers, the segments and the vector file, once no flush is
     *       writing (a flush still running is told to stop at its next stage)</li>
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
     * @throws IllegalStateException if the calling thread holds the parent GigaMap's monitor
     *         (inside {@code synchronized(map)} or {@code GigaMap.apply} logic)
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
        /**
         * Replaces the configuration, e.g. by a copy with another index location, and marks this index
         * for storing. An open index keeps using the directory it was opened in; the new configuration
         * applies from the next load. Callers must hold the parent-map monitor and flag the change up to
         * the map (see {@link VectorIndices#changeIndexLocation(String, IndexLocation)}).
         *
         * @param configuration the new configuration
         */
        public void internalReplaceConfiguration(VectorIndexConfiguration configuration);

        /**
         * Gives an in-memory index with a computed vectorizer and no index location its first one, effective at
         * once: its vectors move from the temporary vector file into a vector file at the new location, without a
         * vectorizer call, and its graph is rebuilt from that file at the next access. Must be called like
         * {@link #internalRemoveAll()}: with the parent-map monitor and the lock of
         * {@link #internalTryLockExclusive()} held.
         *
         * @param configuration the index's configuration with the new location
         */
        public default void internalAssignFirstLocation(final VectorIndexConfiguration configuration)
        {
            throw new UnsupportedOperationException();
        }

        /**
         * Seals the vector file's writes into the store epoch when the GigaMap is stored, before any entity
         * segment of that store is written, see {@link IndexGroup.Internal#internalPrepareStore(Storer)}.
         *
         * @param storer the storer of the store
         */
        public void internalPrepareStore(Storer storer);

        /**
         * Completes the deferred initialization, the vector recovery included, before the parent map applies a
         * mutation, see {@link IndexGroup.Internal#internalPrepareMutation()}. The recovery repairs from the
         * entities at the store epoch, which counts as committed, so it must read the map as stored, not with the
         * mutation already applied.
         */
        public void internalPrepareMutation();

        public void internalAdd(long entityId, E entity);

        public void internalAddAll(long firstEntityId, Iterable<? extends E> entities);

        public default void internalAddAll(final long firstEntityId, final E[] entities)
        {
            this.internalAddAll(firstEntityId, Arrays.asList(entities));
        }

        public void internalUpdate(long entityId, E replacedEntity, E entity);

        public void internalRemove(long entityId, E entity);

        public void internalRemoveAll();

        /**
         * Rebuilds this index from the current state of the parent map's entities. Every vector is computed and
         * validated before anything is dropped, so a throwing vectorizer leaves the index as it was.
         * Must be called like {@link #internalRemoveAll()}: with the parent-map monitor and the lock of
         * {@link #internalTryLockExclusive()} held.
         */
        public void internalReindex();

        /**
         * Tries, without waiting, to acquire the lock {@link #internalRemoveAll()} takes besides the parent-map
         * monitor (see {@code IndexGroup.Internal#internalTryLockExclusive}). Called with the monitor held.
         *
         * @return whether the lock is now held by the current thread
         * @throws IllegalStateException if the current thread is inside a search of this index, which holds a
         *         lock that one can never be acquired with
         */
        public boolean internalTryLockExclusive();

        /**
         * Releases the lock acquired by {@link #internalTryLockExclusive()}.
         */
        public void internalUnlockExclusive();

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
     * Vectors are kept in a memory-mapped {@link VectorFile}, a cache of what the {@link Vectorizer}
     * computes from the entities, and read from it without any lock. Entity IDs are used directly as
     * graph ordinals.
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
         * System property ({@code true}/{@code false}, default {@code false}) that lets a computed in-memory index
         * without an index location, loaded from a 4.x storage, drop the vectors of its 4.x vector store at the
         * next store, so that every later start vectorizes every entity. Without it such a store is refused until
         * the index has a location ({@link VectorIndices#changeIndexLocation(String, IndexLocation)}), since the
         * current layout keeps no vectors in the storage.
         */
        public static final String DROP_LEGACY_VECTORS_PROPERTY =
            "org.eclipse.store.gigamap.jvector.dropLegacyVectorsWithoutLocation";

        // Shuts down the insert pool of an index that was dropped without a close(): its workers outlive the
        // batches by design and hold nothing of the index, so nothing else would ever stop them.
        private static final Cleaner INSERT_POOL_CLEANER = Cleaner.create();

        /**
         * Fixed seed for the PQ training-set reservoir sample, so that training the same data twice
         * yields the same codebook. The value itself is arbitrary.
         */
        private static final long RESERVOIR_SEED = 0x5EEDL;

        /**
         * The longest a new search waits while GigaMap.removeAll() / reindex() wait for the write lock (see
         * awaitNoPendingExclusive).
         */
        private static final long EXCLUSIVE_GATE_MAX_NANOS = 100_000_000L;

        /**
         * Counts the threads waiting for the write lock under the parent monitor, see {@link #exclusiveWaiters}.
         */
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<Default> EXCLUSIVE_WAITERS =
            AtomicIntegerFieldUpdater.newUpdater(Default.class, "exclusiveWaiters");

        /**
         * The longest a mutation that reached the flush threshold tries to take the write lock for a
         * freeze before leaving it to the background task thread (see
         * {@link #tryFreezeForBackgroundFlush()}): about one search, which is what it waits for.
         */
        private static final long INLINE_FREEZE_MAX_NANOS = 5_000_000L;

        static BinaryTypeHandler<Default<?>> provideTypeHandler()
        {
            return BinaryHandlerVectorIndexDefault.New();
        }


        ///////////////////////////////////////////////////////////////////////////
        // instance fields //
        ////////////////////

        final VectorIndices<E>           parent       ;
        final String                     name         ;
        // Not final: VectorIndices#changeIndexLocation replaces it with a copy, effective from the next load.
        volatile VectorIndexConfiguration configuration;
        final Vectorizer<? super E>      vectorizer   ;

        // The identity of the vector file this record describes (see VectorFile#nonce()), 0 for none: a file
        // with another nonce belongs to another store state and is rebuilt from the entities.
        long vectorFileNonce;

        // The store epoch E of the vector file: every slot written at an epoch above it was written after the
        // store this record comes from, and is repaired from its entity at the next open. Set on the store
        // path by internalSealVectorEpochOnStore.
        long vectorFileEpoch;

        // The storer whose store sealed the epoch in internalPrepareStore, so that the record's own store hook
        // does not seal again. Weak: a store that never writes the record must not keep its storer alive.
        // Under the parent monitor.
        transient WeakReference<Object> sealedForStore;

        // Test-only seam: run by the type handler when it stores this record, before the record's store hook.
        // Null (and a no-op) in production.
        transient volatile Runnable storeRecordTestHook;

        // The number of entities with a vector, maintained under the parent monitor by every vector mutation.
        // The vector-count witness of the disk manifest (see getExpectedVectorCount()). Volatile: read by the
        // PQ training gates without the monitor.
        volatile long vectorCount;

        // Persisted, monotonically increasing count of content changes, bumped by markContentChanged():
        // an add that writes at least one vector (an add of entities without a vector does not), every
        // remove (also of an entity that never had a vector), an update whose vector changed (vec→vec',
        // vec↔null), a removeAll, a 4.x import, and a recovery that changed the vector count. It is the
        // witness used to detect, on reload, whether the persisted GigaMap state has advanced past the
        // on-disk graph: the value is stamped into the disk manifest at persist time and compared against
        // the store-recovered value in DiskIndexManager.tryLoad. Unlike parentMap().size()/highestUsedId(),
        // it changes on a vec↔null transition, so it catches the crash-restart window those proxies are
        // blind to. Not transient — it must survive (de)serialization.
        //
        // Volatile because mutations bump it holding only the parentMap monitor, while the compression
        // training gates also read it without the monitor (a merge checks them on its own thread).
        volatile long structuralModCount;

        // The live in-memory graph (transient - rebuilt on load for an in-memory index, empty after a
        // load for an on-disk one, whose segments hold the rest).
        // Volatile: the pair is swapped wholesale by a freeze, a consolidation and the in-memory PQ
        // switch, while mutations read it under the parent monitor only and background callbacks under
        // the read lock. Without volatile a reader can observe a torn pair (stale index, fresh builder),
        // which is how a guard evaluated on `index` once authorized a mutation on a different `builder`.
        // Code that mutates the graph reads `builder` ONCE into a local, derives the
        // graph from it and passes it on (see internalReplaceGraphNode), so guard and mutation cannot
        // target different objects.
        private transient VectorTypeSupport          vectorTypeSupport;
        private transient volatile GraphIndexBuilder builder          ;
        private transient volatile OnHeapGraphIndex  index            ;

        // The vectors, read lock-free by every scoring path and written under the parent monitor by every
        // mutation. Opened by initializeIndex, kept across removeAll, closed by close() and discarded with
        // the index. In the index directory for an on-disk index, in a private temporary directory for an
        // in-memory one.
        private transient volatile VectorFile vectorFile;

        // The vector store of a computed index persisted before the vector file, handed over by the legacy
        // handler and imported into the file at the index's first access (or with a sibling's import), in
        // place of the file's recovery, which would re-vectorize every entity (see ensureVectorsRecovered and
        // importLegacyVectorStore). Null otherwise, and again once imported, except for a computed index that
        // keeps no files: its vectors would only be in a temporary file, so the store stays until the index gets
        // a location or the vectors are dropped deliberately, and a store of the record is refused meanwhile.
        transient GigaMap<VectorEntry> legacyVectorStore;

        // Set by the legacy handler for every index loaded from a layout before the vector file: once its vectors
        // are in place, the index flags itself and the path from the map, so the next store persists the current
        // layout even without a mutation (see ensureVectorsRecovered).
        transient boolean legacyLayout;

        // The vectors the live builder scores from, handed to the generation a freeze makes of it, which keeps
        // the freeze-time vectors of its nodes from then on (see GenerationVectorValues). Replaced with the
        // builder. Set even when the builder scores from in-memory PQ codes; null only before the first
        // builder is created.
        private transient volatile GenerationVectorValues liveVectorValues;

        // Whether the repairs the vector file needed at its open are done (see ensureVectorsRecovered). They
        // read entities, a nested load, so they cannot run in complete().
        private transient volatile boolean    vectorsRecovered;
        // Whether a derived vector file has to be emptied before its next recovery: set by
        // invalidateGraph() for an index that keeps no files, consumed by ensureVectorsRecovered().
        private transient volatile boolean    derivedVectorsReplaced;

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
         * vector and not 256 clusters per subspace - but it does decline, and a sample can land
         * under that floor. This witness
         * covers that as well as an outright exception: it is set whenever an attempt leaves the
         * manager untrained, whichever of the two happened. The name records the commoner case, not
         * the only one.
         */
        private transient volatile long nvqTrainingFailedAtModCount = -1L;
                transient BackgroundTaskManager backgroundTaskManager;

        // GraphSearcher pool for thread-local reuse
        private transient ExplicitThreadLocal<GraphSearcher> inMemorySearcherPool;


        // One-shot guard for the deferred graph rebuild. The HNSW graph is transient and must be
        // rebuilt from the vector file after deserialization, but that file's repairs read the parent
        // GigaMap (a nested object-graph load) and therefore may NOT run inside the enclosing
        // deserialization's complete() phase (internal #87). So complete() only does the nested-load-
        // free transient setup (initializeAfterLoad -> initializeIndex); the graph rebuild is deferred
        // to the first search/mutation/optimize via ensureGraphRebuilt(), which runs it exactly once
        // under the parent GigaMap monitor (see there). Volatile: fast-path read lock-free; the actual
        // rebuild + set happens under synchronized(parentMap()). Also set true by a load of an on-disk
        // index's segments, which leaves nothing to rebuild, and by the standard constructor, to keep
        // the deferred rebuild from racing the one-time registration back-fill that populates a newly
        // created index (see there).
        private transient volatile boolean                   graphRebuilt       ;

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

        // Set by the constructor of a new index, cleared by internalActivate(): the index is built before it
        // is registered, and until then no background manager may run, so that a failed build can be
        // discarded without threads to stop and without anything being persisted.
        private transient boolean                            activationPending  ;

        // What the back-fill did while no background manager existed, handed to the manager on activation:
        // the graph changes its thresholds must count, and whether rejected disk files await their rewrite.
        private transient int                                backfillChanges    ;
        private transient boolean                            persistOnActivation;

        // The directory of the index files, resolved from the configuration's location once, when the
        // index is first opened in this run. Every later disk access uses it, so neither a rebinding of a
        // named location nor a changed configuration moves an open index; both apply from the next load.
        private transient volatile Path resolvedDirectory;

        /**
         * The searchable sources of an on-disk index besides its live in-memory graph - its segments
         * and the in-memory graphs frozen for a flush - with the owner table, or {@code null} for an
         * in-memory index and while the index is not initialized.
         * <p>
         * Replaced copy-on-write: by a freeze and a consolidation under {@code builderLock.writeLock()}
         * and the parent monitor, and by a flush publishing its segments and a merge publishing its
         * segment under {@link #commitLock} only, both holding the flush permit. The owner table itself is
         * replaced only by a consolidation, so a mutation reading it under the monitor always finds the
         * current one. Its entries change under the monitor by mutations, and without it by a merge, which
         * moves owners by compare-and-set (see {@link OwnerTable#move(int, int, int)}): an entry read under
         * the monitor can change before the monitor is released, and a reader that needs it stable uses
         * the result of its own atomic update rather than a read. A search acquires the set it reads (see
         * {@link #acquireSources()}), which keeps every source in it open until the search is done.
         */
        private transient volatile SourceSet                 sources            ;

        // The slot the live in-memory graph of an on-disk index holds in the owner table of sources.
        // Replaced under the write lock and the monitor, by a freeze and a consolidation.
        private transient OwnerTable.Lease                   liveLease          ;

        // The witnesses the manifest on disk was committed or accepted with, or null if there is
        // none. A persist is due whenever the live witnesses moved away from them, even if no change
        // reached the graph or the owners (e.g. removing an entity that never had a vector): a restart
        // would reject the index otherwise. Written under the flush permit.
        private transient volatile DiskIndexManager.MetaState committedWitness  ;

        // Sequence numbers for this instance's whole life, across removeAll (see SegmentSequences).
        private transient SegmentSequences                   segmentSequences   ;

        // At most one flush at a time: taken before the freeze and released once its write is done,
        // on the persisting thread or on the flush writer, or right after the freeze if that yields
        // nothing to write or declines. Also held by compressor training, by a merge publishing its
        // segment, and by close while it tears the index down, so none of them overlaps a flush.
        // Survives removeAll, whose generation an abandoned flush must not overlap.
        private transient Semaphore                          flushPermit        ;

        // Runs the writes of background flushes, so the background task thread can go on applying
        // indexing operations meanwhile. Created on first use; holds no thread while idle.
        private transient volatile ExecutorService           flushExecutor      ;

        // The last lock a flush takes, around its commit and the publication of its segments, and the
        // one removeAll and close take to retire the sources it would publish into. Taken by nothing
        // that holds it for long: the commit writes and moves one small file.
        private transient Object                             commitLock         ;

        // Advanced under commitLock by removeAll and close: a flush planned in an earlier epoch
        // commits nothing, since the index it describes no longer exists.
        private transient volatile long                      sourceEpoch        ;

        // Set by close() while it waits for a flush in flight, which then stops at its next stage.
        private transient volatile boolean                   flushAbortRequested;

        // How many threads try to take builderLock.writeLock() under the parent monitor (a freeze, an optimization,
        // a close), so that new searches hold back for them (see awaitNoPendingExclusive). A count, not a flag: one
        // waiter giving up or succeeding must not open the gate for another still waiting. See EXCLUSIVE_WAITERS.
        private transient volatile int                       exclusiveWaiters   ;

        // Set when a graph operation failed, which leaves the graph without a change the store has:
        // the next persist that may consolidate rebuilds from the store instead of flushing, and a
        // shutdown persist commits nothing, so the next start rebuilds.
        private transient volatile boolean                   consolidationRequired;

        // Set when the files on disk have to be replaced although nothing changed - a rejected load,
        // vectors recovered that no longer match the loaded segments - so the next background persist
        // flushes below the threshold.
        private transient volatile boolean                   forceFlush         ;

        // Diagnostics and tests: completed flushes and consolidations.
        private transient volatile long                      flushCount         ;
        private transient volatile long                      consolidationCount ;

        // Held by the merge thread for a whole merge, and by close() from its start to its end, so a close
        // waits for a merge reading the vector file before it unmaps the file, and no merge starts on a
        // closing index. Taken before the flush permit. Survives removeAll, like the flush permit.
        private transient Semaphore                          mergePermit        ;

        // Whether a merge is scheduled or running on the merge thread, so triggers do not pile up tasks.
        private transient AtomicBoolean                      mergeScheduled     ;

        // Whether a trigger found a merge due while a run was scheduled or running, and so scheduled none:
        // the run looks once more when it ends, whatever its outcome, so the request is not lost.
        private transient volatile boolean                   mergeRequested     ;

        // Runs the background merges. Created on first use; holds no thread while idle.
        private transient volatile ExecutorService           mergeExecutor      ;

        // Set by close() before it waits for the merge permit: a merge in flight stops at its next stage.
        private transient volatile boolean                   mergeAbortRequested;

        // Whether a merge has published a segment the manifest on disk does not list yet: the published set
        // differs from the committed state until the next flush commits a manifest, so that one is due even
        // with nothing else changed. Set under the flush permit, cleared by a flush's publication and by
        // the retirement of the sources.
        private transient volatile boolean                   mergeUncommitted   ;

        // Diagnostics and tests: published merges.
        private transient volatile long                      mergeCount         ;

        // Read/write lock for builder operations.
        // Read lock: concurrent searches and background-worker mutations
        // Write lock: exclusive access for cleanup, persistence, removeAll, close
        private transient ReentrantReadWriteLock builderLock;

        // Guards the structure of a graph against removeDeletedNodes() running into inserts, which jVector
        // does not support concurrently. Inserts take it shared, a batch for its whole parallel phase, and
        // every removeDeletedNodes() takes it exclusive. Taken last, after the builder lock and the parent
        // monitor, and held while waiting for nothing but the inserting workers, which take no lock.
        private transient ReentrantReadWriteLock structureLock;

        // The workers that insert the nodes of a batch into the live graph, see insertBatch. Private to this
        // index, so nothing else can occupy them while the caller waits holding the parent monitor, and
        // long-lived, so the builder's per-thread state is not allocated anew for every batch. Created
        // with the builder lock, threads started on first use, shut down by close() or once the index is collected.
        private transient volatile ForkJoinPool  insertPool   ;

        // Shuts insertPool down, once: run by close(), or by the cleaner once this index is unreachable.
        private transient Cleaner.Cleanable      insertPoolShutdown;

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
         * Set by {@link #close()} before it waits for the write lock and the parent GigaMap monitor
         * to tear the index down: before the background task manager is shut down, unless that
         * shutdown has to optimize or persist the complete graph, and after it otherwise.
         * <p>
         * Lets the deferred load-path rebuild ({@link #ensureGraphRebuilt()}) give up early instead of
         * finishing a graph that is about to be discarded; {@code ensureGraphRebuilt()} then throws
         * rather than let its caller use the partial graph. Cleared again if the close fails, so the
         * still open index can redo the rebuild. That rebuild holds the monitor and close
         * tears down under the monitor, so the two never overlap; without this flag close would
         * merely wait, possibly for minutes on a large index. The rebuild of a consolidation ignores
         * the flag: it runs under the write lock, which close cannot take until it has finished, and
         * the graph it builds is about to be written to disk, where a partial one must never end up.
         */
        private transient volatile boolean                closeRequested   ;

        // Set while removeAll re-initializes the index: the disk load then rejects without retiring (see
        // DiskIndexManager#load(MetaState, OwnerTable, boolean)). Under the parent monitor and the write lock.
        private transient boolean                         reloadingAfterRemoveAll;

        /**
         * Whether a codebook was trained outside a persist and has not been written yet.
         * <p>
         * {@code trainCompressionIfNeeded()} exists so a caller can pay the training cost at a
         * moment of their choosing. That leaves the state a persist would normally produce for
         * itself already made, and both of the questions the clean-index shortcut asks then answer
         * the wrong way: this persist trained nothing, and nothing is pending either, because the
         * manager is trained. The shortcut would skip the persist that has to rewrite the segments
         * with the codebook, and the index would report compression as active while the segments did
         * not carry it.
         * <p>
         * Cleared once a persist has actually written, not merely once one has started: a persist
         * that fails leaves the codebook exactly as stranded as before, so the next one has to try
         * again.
         */
        private transient volatile boolean                compressionTrainedOutsidePersist;

        // Test-only seam: run by every search once it holds the read lock, before it scores anything. A hook
        // that blocks holds a search inside the lock. Null (and a no-op) in production.
        transient volatile Runnable searchTestHook;

        // Test-only seam: run by every search of an on-disk index once it holds its set of sources, before it
        // searches any of them. Null (and a no-op) in production.
        transient volatile Runnable sourcesAcquiredTestHook;

        // Test-only seam: run before each eventual-indexing operation a flush applies to the graph it froze;
        // a hook that throws makes that operation fail. Null (and a no-op) in production.
        transient volatile Runnable movedOpTestHook;

        // Test-only seam: run before the purge of a frozen graph's deleted nodes after its moved operations; not
        // before the purge an insert runs when the entry node is marked deleted. No-op in prod.
        transient volatile Runnable frozenPurgeTestHook;

        // Test-only seam: handed each stage a flush reaches, on the thread doing the flush - the flush
        // writer for a background persist, the persisting thread otherwise - with no lock held. A hook
        // that blocks holds the flush there while searches and mutations go on; one that throws fails
        // the flush at that stage. Null (and a no-op) in production.
        transient volatile Consumer<FlushStage> flushStageTestHook;

        // Test-only seam: run by a mutation that preserves freeze-time vectors for frozen generations, holding
        // the parent monitor, once it holds the published set and before it reads the generations. Null (and a
        // no-op) in production.
        transient volatile Runnable preserveTestHook;

        // Test-only seam: run once the PQ replacement is complete and before it is published, with
        // the parentMap monitor held. A codebook exists at that point but no codes are visible,
        // which is the window isPqCompressionActive() must still report exact scoring in, and the
        // window a concurrent search must come through on the pre-switch graph.
        // Null (and a no-op) in production.
        transient volatile Runnable pqSwitchPublishTestHook;

        // Test-only seam: run on the rebuilding thread, with the parent monitor held, once the stored
        // vectors are collected and prepared and before any code or node is written. No-op in prod.
        transient volatile Runnable rebuildBeforeInsertTestHook;

        // Test-only seam: run by every rebuild worker before each insert. No-op in prod.
        transient volatile Runnable rebuildWorkerTestHook;

        // Test-only seam: handed the code store once a rebuild or PQ replay has encoded every code,
        // before its first graph insert. No-op in prod.
        transient volatile Consumer<MutablePQVectors> rebuildCodesEncodedTestHook;

        // Test-only seam: handed every unpublished builder (a partial rebuild graph, an abandoned PQ
        // replacement) right after it was closed. No-op in prod.
        transient volatile Consumer<GraphIndexBuilder> unpublishedBuilderClosedTestHook;

        // Test-only seam: handed whether the cleanup of a frozen graph before its write refines the graph, right
        // before the cleanup. No-op in prod.
        transient volatile Consumer<Boolean> cleanupTestHook;

        // Test-only seam: run by close() under the write lock and the parent monitor, right before the
        // teardown; a hook that throws makes the close fail there. No-op in prod.
        transient volatile Runnable closeTeardownTestHook;

        // Test-only seam: handed each stage a background merge reaches, on the merge thread, with no lock held.
        // A hook that blocks holds the merge there while searches, mutations and flushes go on; one that
        // throws fails the merge at that stage. Null (and a no-op) in production.
        transient volatile Consumer<MergeStage> mergeStageTestHook;

        // Test-only seam: while set, no merge is scheduled, so a test can build up segments without one.
        transient volatile boolean mergesSuspended;

        // Test-only seam: read by every disk manager this index creates, through commitStageTestHook(),
        // at each stage of committing a manifest; a hook that throws simulates a crash there. No-op in
        // prod.
        transient volatile Consumer<DiskIndexManager.CommitStage> commitStageTestHook;

        // Test-only seam: read by every disk manager this index creates, through reapListingTestHook(),
        // right before a cleanup of unlisted files lists the index directory. No-op in prod.
        transient volatile Runnable reapListingTestHook;


        ///////////////////////////////////////////////////////////////////////////
        // constructors //
        /////////////////

        /**
         * Standard constructor for creating a new index. Sets up the transient state but leaves the graph
         * empty and starts no background manager: populating it with the entities the parent map already
         * holds is the caller's job, done exactly once by {@link #internalBackfill()} before the index is
         * registered, and the managers start with {@link #internalActivate()} (see
         * {@code VectorIndices.Default#internalAddIndex}).
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

            // Initialize builder lock early (before initializeIndex)
            this.builderLock         = new ReentrantReadWriteLock();
            this.structureLock       = new ReentrantReadWriteLock();

            // Publish the one-shot rebuild guard right away, so the deferred rebuild never runs for an
            // index created here: this one is populated by the back-fill in internalBackfill(), immediately
            // after construction, and that is its single population path - embedded and computed alike. A
            // rebuild racing the back-fill would read the vectors the back-fill has written so far and
            // insert their nodes, and the back-fill would then add each one a second time - jvector rejects
            // the duplicate with "Node 0 already exists". Rebuilding is meaningful only for
            // an index whose vectors came back from storage, i.e. after a load.
            // Set before initializeIndex() so a background task started there cannot observe a false flag
            // and rebuild behind the back-fill.
            this.graphRebuilt = true;

            // Before initializeIndex(), which would otherwise start the background managers.
            this.activationPending = true;

            // Transient setup only - deliberately NOT ensureIndexInitialized(), see above.
            this.initializeIndex();

            // A new index starts with an empty vector file of its own, which the back-fill fills: there is
            // nothing to recover from.
            this.vectorFile.recovered();
            this.vectorsRecovered = true;
            this.vectorFileNonce  = this.vectorFile.nonce();
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
        }

        /**
         * Nested-load-free transient setup run from the binary handler's {@code complete()} during
         * deserialization: (re)creates the transient managers, locks and (empty) in-memory builder,
         * and, for on-disk indices, loads the disk graph. It deliberately does NOT rebuild the
         * in-memory graph or repair the vector file, because the repairs read the parent GigaMap
         * (a nested object-graph load) which must not run inside the enclosing load's
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
                // Under the parent monitor, double-checked, like ensureGraphRebuilt(): after
                // invalidateGraph() every concurrent first search sees no builder. Unsynchronized,
                // each would create one, and the second would replace the graph the first had just
                // rebuilt while graphRebuilt stayed true - an empty or partial graph served for good.
                synchronized(this.parentMap())
                {
                    if(!this.isIndexPresent())
                    {
                        this.initializeIndex();
                    }
                }
            }

            this.ensureVectorsRecovered();
            this.ensureGraphRebuilt();
        }

        /**
         * Returns whether the transient index state exists, i.e. whether there is an in-memory
         * builder, which every initialized index has, on-disk or not. {@code false} before the first
         * {@link #initializeIndex()} and after {@link #closeInternalResources()} has torn the state
         * down again.
         *
         * @return {@code true} if the transient state is present
         */
        private boolean isIndexPresent()
        {
            return this.builder != null;
        }

        /**
         * Rebuilds the in-memory graph from the store exactly once, lazily, on first access after a
         * load — the step deferred out of {@code complete()} (internal #87). Runs under the parent
         * GigaMap monitor: mutation / optimize / persist callers already hold it (reentrant), and the
         * search path calls this before taking {@code builderLock.readLock()}, so it never holds the
         * read lock and the monitor at the same time.
         * <p>
         * Deadlock-free with the builder-swapping paths ({@code internalRemoveAll}, a freeze, an in-memory
         * optimize and close: monitor, then only trying {@code builderLock.writeLock()}):
         * the rebuild acquires NO
         * {@code builderLock} while it holds the monitor - {@code rebuildGraphFromStore} adds nodes
         * to the builder directly and reads the vector file without a lock. A thread rebuilding here therefore
         * holds only the monitor and waits for no lock a write-lock holder needs, so it cannot sit in
         * a monitor↔builderLock cycle. It does wait for the rebuild's worker threads, which is safe
         * because they score from the vector file and take neither the monitor nor any other
         * lock (see {@link #rebuildGraphFromStore(boolean)}). A concurrent {@link #close()} tears down
         * under this monitor too, so it waits rather than closing the builder mid-rebuild, and the
         * rebuild gives up early once that close is requested (see {@link #closeRequested}).
         * <p>
         * Because every builder-swapping path also calls {@code ensureIndexInitialized()} before its
         * swap, the first caller wins the rebuild under the monitor and publishes {@code graphRebuilt};
         * all others see the flag and skip. An on-disk index whose segments loaded never gets here:
         * the load sets the flag, since the segments hold the graph and the live in-memory graph
         * starts empty. One whose load was rejected rebuilds into the live graph, which then owns
         * every entity it holds.
         *
         * @throws RebuildAbandonedException (an {@link IllegalStateException}) if the graph still needs
         *                                   its rebuild and {@link #close()} has been requested, so the
         *                                   rebuild was not done or was abandoned
         */
        private void ensureGraphRebuilt()
        {
            if(this.graphRebuilt)
            {
                return;
            }
            synchronized(this.parentMap())
            {
                if(this.graphRebuilt)
                {
                    return;
                }
                if(!this.rebuildGraphFromStore(true))
                {
                    // A close cut the rebuild short, or had already been requested. graphRebuilt stays
                    // unset and the rebuild has put an empty builder in place of the partial one, so
                    // the next call rebuilds from scratch once the close has either finished (then the
                    // index is gone) or failed (close() then clears closeRequested again).
                    // Failing loudly rather than returning keeps the caller - a search, a persist, a
                    // mutation - from carrying on against a partial graph and silently answering with
                    // truncated results or writing them to disk.
                    throw new RebuildAbandonedException(this.name);
                }
                this.assignLiveGraphOwners();
                this.graphRebuilt = true;
            }
        }

        /**
         * Makes the live in-memory graph of an on-disk index the owner of every entity it holds, after
         * a rebuild filled it from the store: a node is live only while its entity's owner is the
         * graph's slot. Under the parent monitor. Does nothing for an in-memory index.
         */
        private void assignLiveGraphOwners()
        {
            final SourceSet         set     = this.sources;
            final GraphIndexBuilder current = this.builder;
            if(set == null || current == null)
            {
                return;
            }
            final OwnerTable owners = set.owners();
            final int        slot   = owners.liveSlot();
            final OnHeapGraphIndex graph = graphOf(current);
            graph.nodeStream(0).forEach(ordinal ->
            {
                if(!graph.getDeletedNodes().get(ordinal))
                {
                    owners.set(ordinal, slot);
                }
            });
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
         * needs, so it cannot sit in a monitor/{@code builderLock} cycle. Everything this reads -
         * whether training is pending, the training sample, the vectors to replay, the ordinal-space
         * warning - comes from the vector file and the index's counters, which every mutation writes
         * under the monitor, and the monitor is the lock that makes those reads consistent.
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
         * On the scheduled path the manager applies one turn of queued indexing ops on its single
         * executor thread before calling {@code doOptimize}, but not the whole queue, so the worker's
         * callbacks can still run alongside the switch and the argument above is what covers them.
         */
        private void performPqSwitch()
        {
            // Read once and kept: closeInternalResources nulls the field, and the rest of the switch
            // works with this manager.
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

            // The generation this switch is being built for. close() and internalRemoveAll replace or
            // null the builder; both hold this monitor, so neither can run while the work below does,
            // and the check before publishing is defensive. Publishing over a torn-down index would
            // resurrect a graph whose searcher pools are already closed.
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

            final GraphIndexBuilder replacement = this.newGraphIndexBuilder(
                BuildScoreProvider.pqBuildScoreProvider(this.jvectorSimilarityFunction(), codes)
            );

            // Closed on every way out that does not publish it - a close request, a generation
            // mismatch, an exception from encoding or inserting - so the per-thread scratch its
            // workers left behind is released (see ParallelGraphInsertion). A published replacement
            // is the index's builder from then on and is never closed here.
            boolean   published = false;
            Throwable failure   = null;
            try
            {
                published = this.fillAndPublishPqReplacement(replacement, codes, plannedFor);
                return published;
            }
            catch(final RuntimeException | Error e)
            {
                failure = e;
                throw e;
            }
            finally
            {
                if(!published)
                {
                    this.closeUnpublishedBuilder(replacement, failure);
                }
            }
        }

        /**
         * Fills the compressed replacement and publishes it, the body of
         * {@link #buildAndPublishPqReplacement}. The caller closes the replacement unless this
         * returns {@code true}.
         *
         * @param replacement the empty replacement builder, scoring from {@code codes}
         * @param codes       the code store for the trained codebook, still empty
         * @param plannedFor  the builder this switch was planned against
         * @return whether the replacement was published
         */
        private boolean fillAndPublishPqReplacement(
            final GraphIndexBuilder replacement,
            final MutablePQVectors  codes      ,
            final GraphIndexBuilder plannedFor
        )
        {
            // The replay runs in parallel, like the full rebuild, and for the same reason it may: the
            // replacement scores from the codes alone and the workers read the vector file lock-free, so
            // no worker needs the monitor this thread holds, and the replacement is unreachable until
            // published below, so the workers are its only writers. Entities without an embedding have
            // no vector in the file and are not graph nodes.
            final RebuildNodes nodes = this.collectRebuildNodes(true);
            if(this.closeRequested)
            {
                this.logPqSwitchAbandoned("while collecting the stored vectors");
                return false;
            }

            // Two phases: every code first, then the graph. Encoding each node right before its own
            // insert would already be safe - a worker only scores nodes it found through the graph
            // or the builder's in-progress set, concurrent structures that publish a node after the
            // code its inserting thread wrote first - but it makes the argument depend on jVector's
            // internals. With the codes complete before any insert starts, and the join between
            // the phases ordering every encode before every insert, no worker can score a candidate
            // whose code is still the zero of a freshly allocated chunk, exactly as in the
            // sequential replay. Both phases give up for a close: the replacement would be
            // discarded by it anyway, and the caller rolls the codebook back.
            this.encodeRebuildCodes(codes, nodes, true);
            if(this.closeRequested)
            {
                this.logPqSwitchAbandoned("after encoding, before inserting");
                return false;
            }
            final int seeded = seedIfEmpty(replacement, nodes.count(), index -> this.insertFromFile(replacement, nodes.ordinals()[index]));
            ParallelGraphInsertion.forEach(
                nodes.count() - seeded,
                index ->
                {
                    if(this.closeRequested)
                    {
                        return;
                    }
                    this.insertFromFile(replacement, nodes.ordinals()[index + seeded]);
                },
                this.rebuildThreadNamePrefix()
            );
            if(this.closeRequested)
            {
                this.logPqSwitchAbandoned("while inserting");
                return false;
            }
            // Unpublished, so this thread has the replacement to itself without the structure lock.
            GraphReachability.repair(replacement, nodes.ordinals(), nodes.count(), this::vectorOf, this.name);

            // Test-only injection point: the replacement is complete and still unpublished.
            // No-op in production.
            final Runnable hook = this.pqSwitchPublishTestHook;
            if(hook != null)
            {
                hook.run();
            }

            if(this.builder != plannedFor)
            {
                // The builder changed while this was being built, so the replacement describes an
                // index that no longer exists. Defensive today: close() and internalRemoveAll, the
                // paths that null or replace the builder, both need the monitor this thread holds.
                // Dropping it is the whole response: the caller closes it and rolls the codebook
                // back, and a later optimization plans again from whatever the index is then.
                // Nothing has been published, so there is nothing else to undo.
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
            // The graph's node count where there is a graph, the vector count otherwise: the codes are
            // allocated per ordinal, so the holes between the live ordinals are what is being paid for.
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
         * Rebuilds the HNSW graph from the vector file, in parallel.
         * <p>
         * Called with the parent GigaMap monitor held, by {@link #ensureGraphRebuilt()} on the first
         * access after a load of an in-memory index, or of an on-disk one whose segments did not load,
         * once {@link #ensureVectorsRecovered()} has made the vector file match the entities. A
         * consolidation fills its own builder the same way (see {@link #consolidate}). The inserts run
         * concurrently ({@code addGraphNode} supports it) on a private pool, see
         * {@link ParallelGraphInsertion}. Not {@code build(ravv)}: it assumes dense ordinals and ends in
         * {@code cleanup()}, which must not run on a builder that stays live.
         * <p>
         * <b>Deadlock invariant:</b> the worker threads never take the parent GigaMap monitor, because the
         * caller holds it and waits for the workers. They only touch the graph, the vector file
         * (lock-free) and the in-memory PQ codes, if any. Anything added to the per-node step below must
         * keep it that way.
         * <p>
         * <b>Always from scratch:</b> the rebuild fills an empty builder. A builder that holds nodes
         * anyway (left by operations applied after an earlier rebuild was abandoned) is replaced by an
         * empty one first. With in-memory PQ codes, all codes are written in a phase of their own before
         * the first insert.
         * <p>
         * <b>Builder lifetime:</b> the builder is read once and stays valid throughout. The load path
         * holds no {@code builderLock}, but every path that closes or replaces the builder holds the
         * parent monitor this thread holds, and {@link #close()} takes it too. A close that arrives
         * during a load-path rebuild therefore waits for it; with {@code abortOnClose} the rebuild
         * notices {@link #closeRequested} and stops early so that wait stays short.
         *
         * @param abortOnClose whether to give up once {@link #close()} has been requested; only for
         *                     the load path, whose graph is discarded by that close anyway
         * @return {@code false} if the rebuild was given up because of a close; {@code true} otherwise.
         *         A rebuild that is given up, or fails with an exception, leaves an empty builder in
         *         place of the partial one, never a partial graph (see
         *         {@link #discardPartialGraph(GraphIndexBuilder)})
         */
        private boolean rebuildGraphFromStore(final boolean abortOnClose)
        {
            // Checked before collecting the ordinals, which reads the whole file.
            if(abortOnClose && this.closeRequested)
            {
                return false;
            }
            if(this.builder == null)
            {
                return true;
            }

            final RebuildNodes nodes = this.collectRebuildNodes(abortOnClose);
            // Again after collecting, which stops early for a close, so that a partial collection is
            // never taken for a complete one, empty or not.
            if(abortOnClose && this.closeRequested)
            {
                return false;
            }
            if(nodes.count() == 0)
            {
                return true;
            }

            // The builder, null-checked above, cannot change meanwhile: every path that replaces or
            // closes it holds the parent monitor this thread holds - close() takes it before tearing
            // down.
            if(graphOf(this.builder).size(0) > 0)
            {
                // Always from an empty builder. A non-empty one here holds nodes inserted after an
                // earlier rebuild was abandoned and left its empty replacement behind. The file
                // already reflects those changes, so they are rebuilt with everything else; keeping
                // their nodes would keep links built against a graph that was nearly empty.
                this.discardPartialGraph(this.builder);
            }
            // Read once, and empty from here on.
            final GraphIndexBuilder target = this.builder;

            // All or nothing: a rebuild given up for a close, or failed in a worker, must not leave a
            // partial graph behind for the next attempt to build on. Its nodes were linked against an
            // incomplete graph, and a worker that failed inside addGraphNode can leave a node in the
            // graph without its links. Starting the next attempt from an empty builder makes it a
            // full rebuild like the first one.
            boolean complete = false;
            try
            {
                complete = this.fillRebuiltGraph(target, nodes, abortOnClose);
                return complete;
            }
            finally
            {
                if(!complete)
                {
                    this.discardPartialGraph(target);
                }
            }
        }

        /**
         * The body of {@link #rebuildGraphFromStore(boolean)} once there are nodes to insert and an
         * empty builder to insert them into: the PQ codes, if any, then the parallel insertion.
         *
         * @param target       the empty builder to fill, read once by the caller
         * @param nodes        the ordinals to insert
         * @param abortOnClose whether to give up once a close has been requested
         * @return {@code false} if the rebuild was given up because of a close
         */
        private boolean fillRebuiltGraph(
            final GraphIndexBuilder target      ,
            final RebuildNodes      nodes       ,
            final boolean           abortOnClose
        )
        {
            assert graphOf(target).size(0) == 0 : "a rebuild fills an empty builder, see rebuildGraphFromStore";

            final Runnable beforeInsertHook = this.rebuildBeforeInsertTestHook;
            if(beforeInsertHook != null)
            {
                beforeInsertHook.run();
            }
            // Last check before any worker starts: a close requested while the nodes were collected
            // gets nothing written at all.
            if(abortOnClose && this.closeRequested)
            {
                return false;
            }

            // With in-memory PQ codes the builder scores from them, so they are all written before the
            // first insert, in a phase of their own (see buildAndPublishPqReplacement for why).
            final MutablePQVectors codes = this.inMemoryPqVectors;
            if(codes != null)
            {
                this.encodeRebuildCodes(codes, nodes, abortOnClose);
                if(abortOnClose && this.closeRequested)
                {
                    return false;
                }
            }

            final Runnable workerHook = this.rebuildWorkerTestHook;
            final long     startNanos = System.nanoTime();
            final int      inserted   = nodes.count();
            // A virtual-thread caller (e.g. a first search on one) pins its carrier here: it waits for
            // the workers inside synchronized(parentMap()), which JDK 21 cannot unmount (JEP 491 lifts
            // that in JDK 24). Not a regression - the sequential rebuild held the same monitor for longer.
            // Inserts, so under the structure lock shared, like a batch.
            final ReentrantReadWriteLock.ReadLock structure = this.structureLock.readLock();
            structure.lock();
            try
            {
                final int seeded = seedIfEmpty(target, inserted, index -> this.insertFromFile(target, nodes.ordinals()[index]));
                ParallelGraphInsertion.forEach(
                    inserted - seeded,
                    insertIndex ->
                    {
                        if(workerHook != null)
                        {
                            workerHook.run();
                        }
                        // Only a shortcut: close() cannot tear the builder down while this runs, since it
                        // needs the monitor the waiting caller holds. It just should not wait for a graph
                        // that is about to be discarded.
                        if(abortOnClose && this.closeRequested)
                        {
                            return;
                        }
                        this.insertFromFile(target, nodes.ordinals()[insertIndex + seeded]);
                    },
                    this.rebuildThreadNamePrefix()
                );
            }
            finally
            {
                structure.unlock();
            }

            if(abortOnClose && this.closeRequested)
            {
                LOG.info("Rebuild of the graph of '{}' abandoned: the index is closing", this.name);
                return false;
            }
            this.repairReachability(target, nodes.ordinals(), inserted);
            LOG.info("Rebuilt the graph of '{}' from {} vectors in {} ms",
                this.name, inserted, (System.nanoTime() - startNanos) / 1_000_000L);
            return true;
        }

        /**
         * Inserts the node of {@code ordinal} into {@code target} with its vector from the file. Called
         * by rebuild workers while the caller holds the parent monitor, so the vector cannot change or
         * disappear meanwhile; it is skipped should it have none.
         */
        private void insertFromFile(
            final GraphIndexBuilder target ,
            final int               ordinal
        )
        {
            final VectorFloat<?> vector = this.vectorOf(ordinal);
            if(vector != null)
            {
                target.addGraphNode(ordinal, vector);
            }
        }

        /**
         * Returns the vector of {@code ordinal} from the vector file, or {@code null} if it has none.
         */
        private VectorFloat<?> vectorOf(final int ordinal)
        {
            final float[] vector = this.vectorFile.read(ordinal);
            return vector == null ? null : this.vectorTypeSupport.createFloatVector(vector);
        }

        private void logPqSwitchAbandoned(final String stage)
        {
            LOG.info("PQ switch of '{}' abandoned {}: the index is closing", this.name, stage);
        }

        /**
         * Writes the PQ code of every node of {@code nodes}, in parallel, as the first phase of a rebuild or
         * replay that scores from codes. Returns once every code is written: the loop joins its workers,
         * which orders these writes before anything the caller does next, in particular before the
         * parallel inserts of the second phase.
         * <p>
         * The highest ordinal is encoded first, on this thread, so the code store allocates its whole
         * chunk table before the workers start and never grows while they write.
         *
         * @param codes        the code store to fill
         * @param nodes        the rebuild's nodes
         * @param abortOnClose whether to stop once a close has been requested; the caller must then
         *                     check {@link #closeRequested} before using the codes
         */
        private void encodeRebuildCodes(
            final MutablePQVectors codes       ,
            final RebuildNodes     nodes       ,
            final boolean          abortOnClose
        )
        {
            if(nodes.count() > 0)
            {
                final int highest = nodes.ordinals()[nodes.count() - 1];
                codes.encodeAndSet(highest, this.vectorOf(highest));
            }
            ParallelGraphInsertion.forEach(
                nodes.count(),
                index ->
                {
                    if(abortOnClose && this.closeRequested)
                    {
                        return;
                    }
                    final int            ordinal = nodes.ordinals()[index];
                    final VectorFloat<?> vector  = this.vectorOf(ordinal);
                    if(vector != null)
                    {
                        codes.encodeAndSet(ordinal, vector);
                    }
                },
                this.rebuildThreadNamePrefix()
            );

            final Consumer<MutablePQVectors> hook = this.rebuildCodesEncodedTestHook;
            if(hook != null)
            {
                hook.accept(codes);
            }
        }

        /**
         * Replaces the builder a rebuild gave up on, or failed in, with a fresh empty one, so that the
         * next attempt rebuilds from scratch rather than on top of a partial graph.
         * <p>
         * Runs with the parent monitor held, like the rebuild, and swaps the builder the way the
         * in-memory PQ switch does: the searcher pools read {@code this.index} on every search, so
         * nothing else needs replacing. The partial builder can be closed (which releases the
         * per-thread scratch its workers left behind) because nothing else can be using it: searches
         * and background callbacks wait for the rebuild before they touch the builder, and the
         * persist-time rebuild runs under the write lock that excludes both.
         *
         * @param partial the builder the rebuild was filling
         */
        private void discardPartialGraph(final GraphIndexBuilder partial)
        {
            if(this.builder != partial)
            {
                return;
            }
            this.initializeInMemoryBuilder();
            this.closeUnpublishedBuilder(partial, null);
        }

        /**
         * Closes a builder that is not, or no longer, the index's builder, releasing the per-thread
         * scratch and searchers the rebuild workers left in it. Never called for a builder that is
         * published: searches may be traversing that one.
         * <p>
         * A failure to close must not replace the failure that got the builder discarded, so it is
         * added to that one as suppressed, or logged when there is none.
         *
         * @param unpublished the builder to close
         * @param failure     the failure being propagated, or {@code null}
         */
        private void closeUnpublishedBuilder(
            final GraphIndexBuilder unpublished,
            final Throwable         failure
        )
        {
            try
            {
                unpublished.close();
            }
            catch(final IOException | RuntimeException e)
            {
                if(failure != null)
                {
                    failure.addSuppressed(e);
                }
                else
                {
                    LOG.warn("Failed to close a discarded graph builder of '{}': {}", this.name, e.getMessage(), e);
                }
            }
            finally
            {
                final Consumer<GraphIndexBuilder> hook = this.unpublishedBuilderClosedTestHook;
                if(hook != null)
                {
                    hook.accept(unpublished);
                }
            }
        }

        /**
         * The ordinals a full rebuild inserts: every ordinal the vector file has a vector for, ascending.
         * The vectors themselves stay in the file, where the workers read them.
         *
         * @param ordinals the ordinals, ascending
         */
        private record RebuildNodes(int[] ordinals)
        {
            int count()
            {
                return this.ordinals.length;
            }
        }

        /**
         * Collects the ordinals of the vector file that have a vector, ascending. Under the parent
         * monitor, so the file does not change meanwhile. Sized by the vector count, not by the highest
         * ordinal, so a churned map with few vectors and a high maximum id does not pay for its holes.
         *
         * @param abortOnClose whether to stop collecting once {@link #close()} has been requested; the
         *                     result is then incomplete, and the caller must check
         *                     {@link #closeRequested} before using it
         * @return the ordinals with a vector
         */
        private RebuildNodes collectRebuildNodes(final boolean abortOnClose)
        {
            final int[][] ordinals = {new int[(int)Math.min(Integer.MAX_VALUE - 8, Math.max(16L, this.vectorCount))]};
            final int[]   count    = {0};
            this.vectorFile.forEachPresent(ordinal ->
            {
                if(abortOnClose && (count[0] & 0xFFF) == 0 && this.closeRequested)
                {
                    return false;
                }
                if(count[0] == ordinals[0].length)
                {
                    ordinals[0] = Arrays.copyOf(ordinals[0], Math.min(Integer.MAX_VALUE - 8, count[0] * 2));
                }
                ordinals[0][count[0]++] = ordinal;
                return true;
            });
            final int[] collected = ordinals[0];
            return new RebuildNodes(count[0] == collected.length ? collected : Arrays.copyOf(collected, count[0]));
        }

        /**
         * Test seam: the current in-memory graph builder, or {@code null}.
         *
         * @return the builder
         */
        GraphIndexBuilder currentBuilder()
        {
            return this.builder;
        }

        /**
         * Test seam: the number of nodes in the current in-memory graph's base layer, or {@code -1}
         * without a builder.
         *
         * @return the node count
         */
        int inMemoryGraphNodeCount()
        {
            final GraphIndexBuilder current = this.builder;
            return current == null ? -1 : graphOf(current).size(0);
        }

        /**
         * Test seam: the vector file, or {@code null} if the index is closed or not initialized.
         *
         * @return the vector file
         */
        VectorFile vectorFile()
        {
            return this.vectorFile;
        }

        /**
         * Test seam: whether {@link #close()} has been requested.
         *
         * @return the current value of {@link #closeRequested}
         */
        boolean isCloseRequested()
        {
            return this.closeRequested;
        }

        private String rebuildThreadNamePrefix()
        {
            return "vector-rebuild-" + this.name;
        }

        /**
         * Opens the vector file: in the index directory for an index that keeps files there (an on-disk
         * index, or an in-memory one with a computed vectorizer and a configured directory, see
         * {@link #keepsIndexFiles()}), against the nonce and the store epoch this record carries, and in a
         * fresh private temporary directory otherwise, which therefore always starts empty and is rebuilt
         * from the entities. The repairs the open finds necessary are left to
         * {@link #ensureVectorsRecovered()}.
         */
        private void openVectorFile()
        {
            final String     description = "vector index '" + this.name + "'";
            final long       chunkBytes  = this.checkedVectorChunkBytes();
            final boolean    persistent  = this.keepsIndexFiles();
            if(!persistent && !this.vectorizer.isEmbedded())
            {
                LOG.warn("Vector index '{}' has a computed vectorizer but no index directory or location: its"
                    + " vector file is temporary, so every start calls the vectorizer once per entity. Configure"
                    + " indexDirectory or indexLocation to keep the vectors across restarts.", this.name);
            }
            final VectorFile file        = persistent
                ? VectorFile.open(
                    this.indexDirectoryForDisk(),
                    this.name,
                    this.configuration.dimension(),
                    chunkBytes,
                    this.vectorFileNonce,
                    this.vectorFileEpoch,
                    description
                )
                : VectorFile.openTemporary(
                    this.name,
                    this.configuration.dimension(),
                    chunkBytes,
                    description
                )
            ;
            file.checkpointInterval(this.configuration.vectorCheckpointIntervalMs());
            this.vectorsRecovered = false;
            this.vectorFile       = file;
        }

        /**
         * Returns the configured chunk size, checked against the minimum here as well as in the builder: a custom
         * {@link VectorIndexConfiguration} implementation bypasses the builder, and a chunk of fewer than
         * {@link VectorFile#MIN_SLOTS_PER_CHUNK} slots lets sparse ids grow the dense chunk table without bound.
         *
         * @throws IllegalArgumentException if the configured chunk holds fewer than the minimum slots
         */
        private long checkedVectorChunkBytes()
        {
            final long chunkBytes = this.configuration.vectorChunkBytes();
            final long minimum    = (long)VectorFile.strideOf(this.configuration.dimension())
                * VectorFile.MIN_SLOTS_PER_CHUNK;
            if(chunkBytes < minimum)
            {
                throw new IllegalArgumentException("The vectorChunkBytes of " + chunkBytes + " configured for vector"
                    + " index '" + this.name + "' hold fewer than " + VectorFile.MIN_SLOTS_PER_CHUNK + " vectors of"
                    + " dimension " + this.configuration.dimension() + "; at least " + minimum + " are required");
            }
            return chunkBytes;
        }

        /**
         * Repairs what the vector file found at its open, once, on first access: the slots of hot chunks
         * written after the last committed store, every ordinal of a hot chunk after a reboot, or every
         * entity after the file had to be replaced (see {@link VectorFile}). Runs under the parent monitor,
         * before the graph rebuild that reads the file, and not in {@code complete()}: it reads entities,
         * which is a nested load.
         * <p>
         * Repairs stream through the ordinals in ascending order, so the entity segments, and whatever the
         * vectorizer reads, are loaded once per run of ordinals rather than once per ordinal.
         * <p>
         * The witnesses were sampled before this, when the segments were loaded. A repair restores the
         * committed vectors and so leaves the vector count as the store recorded it, unless the vectorizer
         * breaks its contract and returns {@code null} for an entity that must have a vector. That vector is
         * dropped and counted at ERROR; the count changes, which makes the loaded segments stale, so they are
         * dropped too and the graph is rebuilt from the file.
         */
        private void ensureVectorsRecovered()
        {
            if(this.vectorsRecovered)
            {
                return;
            }
            synchronized(this.parentMap())
            {
                final VectorFile file = this.vectorFile;
                if(this.vectorsRecovered || file == null)
                {
                    return;
                }
                if(this.derivedVectorsReplaced)
                {
                    // Preserve the imported count, or recovery would advance the writer's change witness.
                    this.clearVectorSlots(file);
                    this.derivedVectorsReplaced = false;
                }
                final GigaMap<VectorEntry> legacy = this.legacyVectorStore;
                if(legacy != null)
                {
                    this.importLegacyVectorStore(file, legacy);
                }
                else
                {
                    this.recoverVectors(file);
                }
                this.vectorsRecovered = true;
                if(this.legacyLayout)
                {
                    // Loaded from a layout before the vector file: the record must be written in the current
                    // layout by the next store, mutation or not, so the path from the map down to it is flagged.
                    this.legacyLayout = false;
                    this.markStateChangeInstance();
                    if(this.parent instanceof VectorIndices.Default<E> group)
                    {
                        group.internalReportIndexStateChange();
                    }
                }
                // The segments of a load may be due for a merge, a flagged one above all, which waited for
                // the vectors it is rebuilt from.
                this.scheduleMergeIfDue();
            }
        }

        /**
         * Imports or recovers the vectors of an index loaded from a layout before the vector file, if that has not
         * happened yet. Called by the group when another of its indices did: its import flags the group, the next
         * store then writes every index of it, and a record still carrying its 4.x vector store must not be
         * written (see {@link BinaryHandlerVectorIndexDefault}). Under the parent monitor.
         */
        void importLegacyLayout()
        {
            if(this.legacyLayout)
            {
                this.ensureVectorsRecovered();
            }
        }

        /**
         * Imports the vectors of the vector store a computed index persisted before the vector file (see
         * {@link BinaryLegacyTypeHandlerVectorIndexDefault}) into the file, in place of the recovery the open
         * found necessary: the record carried no nonce, so the open replaced the file and would otherwise call
         * the vectorizer once per entity, which for a vectorizer backed by an embedding service is one request
         * per entity. Every non-null vector is written under its entity's ordinal, the chunks are marked
         * repaired and the file recovered, the count follows the vectors, and the store is dropped; the next
         * store of the map persists the current layout. Under the parent monitor, like the recovery.
         * <p>
         * The store is not trusted blindly. Up to 4.1.0 the index addressed it by position, and its ids drift from
         * the entity ids once the index was added to a map with deletion holes: a remove then deleted another
         * entity's entry and left its own (a ghost), an update overwrote another entity's entry and kept the old
         * one (a duplicate, and an entity without an entry). A store can also be short of entities a 4.x failure
         * never gave a vector. So an entry whose entity no longer exists is skipped, and when there was a ghost, a
         * duplicate, or the entries do not match the entities in number, every entity without exactly one entry is
         * repaired from the entity, as a recovery would: a field read for an embedded vectorizer, one call for a
         * computed one, for those entities only. A consistent store, which every 4.2.0 one should be, costs no
         * vectorizer call.
         */
        private void importLegacyVectorStore(final VectorFile file, final GigaMap<VectorEntry> legacy)
        {
            final long             startNanos  = System.nanoTime();
            final int              perChunk    = file.slotsPerChunk();
            final GigaMap<E>       map         = this.parentMap();
            final long             highest     = map.highestUsedId();
            final int[]            chunk       = {-1};
            final long[]           imported    = {0L};
            final long[]           ghosts      = {0L};
            final java.util.BitSet seen        = new java.util.BitSet();
            final java.util.BitSet conflicting = new java.util.BitSet();
            legacy.iterate(entry ->
            {
                this.checkRecoveryNotClosing();
                // An entry past the map's highest id, or of an id the map no longer holds, belongs to an entity
                // that no longer exists: written, it would be found by search with no entity behind it.
                if(entry.sourceEntityId > highest || map.get(entry.sourceEntityId) == null)
                {
                    ghosts[0]++;
                    return;
                }
                final int ordinal = toOrdinal(entry.sourceEntityId);
                if(seen.get(ordinal))
                {
                    // Which entry is the entity's current one cannot be told: the entity decides, below.
                    conflicting.set(ordinal);
                    return;
                }
                seen.set(ordinal);
                if(entry.vector == null)
                {
                    // a null embedding was stored as a null vector and never had a slot
                    return;
                }
                if(chunk[0] >= 0 && ordinal / perChunk != chunk[0])
                {
                    file.repaired(chunk[0]);
                }
                chunk[0] = ordinal / perChunk;
                file.repair(ordinal, entry.vector, Vectorizer.NO_KEY);
                imported[0]++;
            });
            if(chunk[0] >= 0)
            {
                file.repaired(chunk[0]);
            }

            // The count only tells when every entity must have a vector: with null embeddings allowed, 4.2.0 kept no
            // entry for an entity without one, so a missing entry is legitimate there. Drift always leaves a ghost
            // or a duplicate behind, which still triggers the pass.
            final boolean     inconsistent = ghosts[0] > 0
                || !conflicting.isEmpty()
                || !this.vectorizer.allowsNullVectors() && seen.cardinality() != map.size();
            final RepairCount repaired     = new RepairCount();
            if(inconsistent)
            {
                final java.util.BitSet touched = new java.util.BitSet();
                map.iterateIndexed((entityId, entity) ->
                {
                    final int ordinal = toOrdinal(entityId);
                    if(seen.get(ordinal) && !conflicting.get(ordinal))
                    {
                        return;
                    }
                    if((repaired.slots & 0xFF) == 0)
                    {
                        this.checkRecoveryNotClosing();
                    }
                    this.repairSlot(file, ordinal, entity, repaired);
                    touched.set(ordinal / perChunk);
                });
                for(int c = touched.nextSetBit(0); c >= 0; c = touched.nextSetBit(c + 1))
                {
                    file.repaired(c);
                }
            }
            file.recovered();

            if(this.keepsIndexFiles() || this.vectorizer.isEmbedded() || Boolean.getBoolean(DROP_LEGACY_VECTORS_PROPERTY))
            {
                this.legacyVectorStore = null;
            }
            else
            {
                LOG.warn("Vector index '{}' imported the vectors of its 4.x vector store into a temporary file: it"
                    + " has no index directory or location, and the current layout keeps no vectors in the storage."
                    + " Storing it is refused until it has one (VectorIndices.changeIndexLocation), or until the"
                    + " system property {}=true lets the store drop them, after which every start vectorizes every"
                    + " entity.", this.name, DROP_LEGACY_VECTORS_PROPERTY);
            }
            this.vectorCount       = inconsistent ? countPresent(file) : imported[0];
            if(this.keepsIndexFiles())
            {
                this.vectorFileNonce = file.nonce();
            }
            this.markContentChanged();
            if(inconsistent)
            {
                LOG.warn("The 4.x vector store of '{}' did not match its entities: {} entries of entities that no"
                    + " longer exist skipped; {} entities with more than one entry and {} without one re-vectorized"
                    + " from the entities",
                    this.name, ghosts[0], conflicting.cardinality(), repaired.slots - conflicting.cardinality());
            }
            if(repaired.dropped > 0)
            {
                LOG.error("The vectorizer of '{}' returned no vector for {} entities that must have one: their"
                    + " vectors are dropped. Vectorizer.vectorize must reproduce the vector of a persisted entity",
                    this.name, repaired.dropped);
            }
            LOG.info("Imported the {} vectors of '{}' from its vector store into its vector file in {} ms; the next"
                + " store of the map persists the index without the store",
                this.vectorCount, this.name, (System.nanoTime() - startNanos) / 1_000_000L);
        }

        private void recoverVectors(final VectorFile file)
        {
            final VectorFile.Recovery recovery   = file.recovery();
            final long                startNanos = System.nanoTime();
            final RepairCount         repaired   = new RepairCount();
            final long                before     = this.vectorCount;
            final long                after;
            if(recovery.fullRebuild())
            {
                final int perChunk = file.slotsPerChunk();
                final int[] chunk  = {-1};
                this.parentMap().iterateIndexed((entityId, entity) ->
                {
                    this.checkRecoveryNotClosing();
                    final int ordinal = toOrdinal(entityId);
                    if(chunk[0] >= 0 && ordinal / perChunk != chunk[0])
                    {
                        file.repaired(chunk[0]);
                    }
                    chunk[0] = ordinal / perChunk;
                    this.repairSlot(file, ordinal, entity, repaired);
                });
                if(chunk[0] >= 0)
                {
                    file.repaired(chunk[0]);
                }
                after = repaired.present;
            }
            else
            {
                final long highest = this.parentMap().highestUsedId();
                for(final int chunk : recovery.scanChunks())
                {
                    this.repairChunk(file, chunk, highest, true, repaired);
                }
                for(final int chunk : recovery.repairChunks())
                {
                    this.repairChunk(file, chunk, highest, false, repaired);
                }
                // A vectorizer that may return null can return it in breach of its contract too, for an entity
                // that has a vector, and that drop is not counted: the slots are counted again instead.
                after = repaired.slots > 0 && this.vectorizer.allowsNullVectors()
                    ? countPresent(file)
                    : before - repaired.dropped;
            }
            file.recovered();

            if(repaired.dropped > 0)
            {
                LOG.error("The vectorizer of '{}' returned no vector for {} entities that must have one: their"
                    + " vectors are dropped. Vectorizer.vectorize must reproduce the vector of a persisted entity",
                    this.name, repaired.dropped);
            }
            if(recovery.fullRebuild() || repaired.slots > 0)
            {
                LOG.info("Recovered the vectors of '{}': {} slots repaired from the entities in {} ms",
                    this.name, repaired.slots, (System.nanoTime() - startNanos) / 1_000_000L);
            }
            if(file.nonce() != this.vectorFileNonce && this.keepsIndexFiles())
            {
                // The record must name the file it now describes, or the next open takes it for foreign and
                // re-vectorizes every entity again. Flagged up to the map, not only here: GigaMap.store() reaches
                // a record only through flagged groups, and no mutation may follow to flag them. Not as a content
                // change, whose mod count would make the loaded segments' witnesses stale.
                this.vectorFileNonce = file.nonce();
                this.markStateChangeInstance();
                // An index loaded from a 4.x layout reports itself once this recovery is done (ensureVectorsRecovered);
                // reported from here, still marked as such, the group would recover it a second time.
                if(!this.legacyLayout && this.parent instanceof final VectorIndices.Default<E> group)
                {
                    group.internalReportIndexStateChange();
                }
            }
            if(after != before)
            {
                this.vectorCount = after;
                this.markContentChanged();
                this.dropLoadedSegments();
            }
        }

        /**
         * Counts the slots of the vector file that hold a vector, skipping the chunks never created.
         */
        private static long countPresent(final VectorFile file)
        {
            final long[] count = {0L};
            file.forEachPresent(ordinal ->
            {
                count[0]++;
                return true;
            });
            return count[0];
        }

        /**
         * Gives a recovery up once {@link #close()} has been requested: it runs under the monitor the close
         * waits for, and vectorizing every entity can take long. What it repaired so far stays repaired; the
         * rest is repaired at the next open, or by a later access if the close fails.
         *
         * @throws RebuildAbandonedException if a close has been requested
         */
        private void checkRecoveryNotClosing()
        {
            if(this.closeRequested)
            {
                throw new RebuildAbandonedException(this.name);
            }
        }

        /**
         * Repairs one hot chunk: only the slots written after the store, or half written, for an epoch scan,
         * every ordinal up to the highest entity id otherwise.
         */
        private void repairChunk(
            final VectorFile  file    ,
            final int         chunk   ,
            final long        highest ,
            final boolean     scanOnly,
            final RepairCount repaired
        )
        {
            final long first = (long)chunk * file.slotsPerChunk();
            // The whole chunk, not up to the file's highest ordinal: a recreated chunk reads as zeros, so the
            // highest ordinal can lie below ordinals of it that still need their vectors back.
            final long last  = Math.min(first + file.slotsPerChunk() - 1L, Integer.MAX_VALUE);
            for(long position = first; position <= last; position++)
            {
                final int ordinal = (int)position;
                if((ordinal & 0xFF) == 0)
                {
                    this.checkRecoveryNotClosing();
                }
                if(position > highest)
                {
                    // No entity can have this id: only a vector of a store rolled back past its additions
                    // is left to remove. Zero slots are skipped, so a chunk's empty tail costs no write.
                    if(file.isPresent(ordinal) || file.needsRepair(ordinal))
                    {
                        this.repairSlot(file, ordinal, null, repaired);
                    }
                    continue;
                }
                if(scanOnly && !file.needsRepair(ordinal))
                {
                    continue;
                }
                this.repairSlot(file, ordinal, this.parentMap().get(position), repaired);
            }
            file.repaired(chunk);
        }

        /**
         * Writes what the entity's vectorizer returns into the slot, at the store epoch. A vectorizer that
         * fails is not caught: recovery is retried at the next access. One that returns no vector, or an
         * invalid one, for an entity that must have one breaks its contract; the vector is dropped and
         * counted.
         */
        private void repairSlot(
            final VectorFile  file    ,
            final int         ordinal ,
            final E           entity  ,
            final RepairCount repaired
        )
        {
            float[] vector = null;
            long    key    = Vectorizer.NO_KEY;
            if(entity != null)
            {
                key    = this.vectorizer.vectorKey(entity);
                vector = this.ownVector(this.vectorizer.vectorize(entity));
                if(vector != null)
                {
                    try
                    {
                        this.validateVector(vector);
                    }
                    catch(final IllegalStateException e)
                    {
                        LOG.error("Dropping the vector of entity {} of '{}': {}", ordinal, this.name, e.getMessage());
                        vector = null;
                    }
                }
                if(vector == null && !this.vectorizer.allowsNullVectors())
                {
                    repaired.dropped++;
                }
            }
            // Nothing to keep and nothing to clear: like an add, an entity without a vector writes no slot, so a
            // full rebuild does not create chunks across the ids of vector-less entities only to hold empty ones.
            final boolean empty = vector == null
                && key == Vectorizer.NO_KEY
                && !file.isPresent(ordinal)
                && file.key(ordinal) == Vectorizer.NO_KEY
                && !file.needsRepair(ordinal);
            if(!empty)
            {
                file.repair(ordinal, vector, key);
            }
            repaired.slots++;
            if(vector != null)
            {
                repaired.present++;
            }
        }

        /**
         * Counts what a recovery did.
         */
        private static final class RepairCount
        {
            long slots  ;
            long present;
            long dropped;
        }

        /**
         * Drops the segments an on-disk index loaded, after a recovery changed the vector count they were
         * accepted with: the graph is rebuilt from the vector file into the live graph instead, and the next
         * persist replaces the files. The same state a rejected load leaves. Under the parent monitor, before
         * the first search, so nothing holds the segments yet.
         */
        private void dropLoadedSegments()
        {
            final SourceSet set = this.sources;
            if(set == null || set.segments().isEmpty() && set.frozen().isEmpty())
            {
                return;
            }
            LOG.warn("Dropping the loaded segments of '{}': the vectors recovered from the entities no longer match"
                + " them, so the graph is rebuilt from the vector file", this.name);
            this.retireSources();
            final OwnerTable owners = OwnerTable.New();
            this.liveLease = owners.allocate();
            owners.liveSlot(this.liveLease.slot());
            synchronized(this.commitLock)
            {
                this.sources = new SourceSet(List.of(), List.of(), owners);
            }
            this.committedWitness = null;
            this.forceFlush       = true;
            this.graphRebuilt     = false;
            if(this.backgroundTaskManager != null)
            {
                this.backgroundTaskManager.markPersistRequired();
            }
        }

        private void initializeIndex()
        {
            // First, before anything is built: an unbound location name must fail here and leave nothing
            // behind, or a present in-memory builder would make the half-initialized index look ready.
            if(this.keepsIndexFiles())
            {
                this.indexDirectoryForDisk();
            }

            this.vectorTypeSupport = VectorizationProvider.getInstance().getVectorTypeSupport();

            // Set when a persisted on-disk index was found but rejected, so the rebuilt graph has to
            // be written back over the stale files. Acted on after the background manager is started.
            boolean diskRebuildPending = false;

            // Initialize builder lock (always, for consistent locking semantics)
            if(this.builderLock == null)
            {
                this.builderLock = new ReentrantReadWriteLock();
            }
            if(this.structureLock == null)
            {
                this.structureLock = new ReentrantReadWriteLock();
            }
            // Kept across removeAll, which re-initializes; again after a close that failed past the shutdown.
            final ForkJoinPool pool = this.insertPool;
            if(pool == null || pool.isShutdown())
            {
                final ForkJoinPool created = ParallelGraphInsertion.newPool("vector-insert-" + this.name);
                this.insertPool         = created;
                // The action captures the pool only, never this index, or the index could not become unreachable.
                this.insertPoolShutdown = INSERT_POOL_CLEANER.register(this, created::shutdown);
            }
            // Kept across removeAll, which re-initializes: a flush still writing for the generation it
            // discards must not share sequences, a permit or a commit lock with the new one.
            if(this.segmentSequences == null)
            {
                this.segmentSequences = new SegmentSequences();
            }
            if(this.flushPermit == null)
            {
                this.flushPermit = new Semaphore(1);
            }
            if(this.commitLock == null)
            {
                this.commitLock = new Object();
            }
            if(this.mergePermit == null)
            {
                this.mergePermit    = new Semaphore(1);
                this.mergeScheduled = new AtomicBoolean();
            }

            // Opened once and kept across removeAll, which re-initializes: pure file I/O, so it may run in
            // complete(). The repairs it may need read entities and run on first access instead.
            if(this.vectorFile == null)
            {
                this.openVectorFile();
            }

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
                this.diskManager = this.newDiskManager();
                this.logFilesOfTheLayoutBeforeSegments();
                // Before the load, which deletes what it cannot use.
                final boolean                       indexFilesExisted = this.diskManager.indexFilesExist();
                final DiskIndexManager.MetaState    liveMeta          = this.currentMetaState();
                final OwnerTable                    owners            = OwnerTable.New();
                final DiskIndexManager.LoadedIndex  loaded            =
                    this.diskManager.load(liveMeta, owners, !this.reloadingAfterRemoveAll);
                final List<DiskSegment>             segments;
                if(loaded != null)
                {
                    // Recover the codebook the loaded graph was actually written with, rather than
                    // assuming one is present. A graph written before compression was enabled - or
                    // written while too few vectors existed to train - carries no FusedPQ, and
                    // claiming otherwise would leave the manager "trained" with a null codebook,
                    // permanently suppressing training while every persist wrote uncompressed.
                    this.adoptCompressorsFromLoadedGraph(loaded);
                    segments              = loaded.segments();
                    this.committedWitness = liveMeta;

                    // The segments hold the graph; the live in-memory graph starts empty and takes the
                    // mutations from here on, so there is nothing to rebuild.
                    this.graphRebuilt = true;
                    LOG.info("Loaded the on-disk index of '{}' with {} segments, no graph rebuild needed",
                        this.name, segments.size());

                    // A segment written for another format is searchable, but only exactly, until a
                    // background merge rewrites it. The merge reads the vector file, so it starts once the
                    // vectors are recovered, on the first access (see ensureVectorsRecovered), and the next
                    // persist commits it.
                    if(loaded.hasFlaggedSegments())
                    {
                        LOG.info("The on-disk index of '{}' has segments that no longer match its configuration;"
                            + " they are searched exactly until a background merge rewrites them", this.name);
                    }
                }
                else
                {
                    LOG.info("Could not load disk index for '{}', will build in-memory and persist later", this.name);
                    segments              = List.of();
                    this.committedWitness = null;

                    // A rejected manifest leaves stale files the rebuilt graph has to replace. Without one, vectors
                    // that exist (a 4.x layout, or sessions that ended before their first persist) are rebuilt
                    // from the vector file at every start until a persist writes them, and a merely searched
                    // index counts no change that would schedule one. Only an index without a vector has nothing
                    // to persist yet.
                    diskRebuildPending = indexFilesExisted || this.vectorCount > 0L || this.legacyLayout;
                }
                this.liveLease = owners.allocate();
                owners.liveSlot(this.liveLease.slot());
                this.sources   = new SourceSet(segments, List.of(), owners);
                // The set is the first holder of the segments, see IndexSource.
                this.forceFlush = diskRebuildPending;
            }

            // Initialize searcher pool
            this.initializeSearcherPool();

            // Start background managers if enabled
            this.startBackgroundManagersIfEnabled();

            // A rejected or missing load is not a change, so nothing would otherwise schedule the persist that
            // writes the rebuilt graph: background persistence waits for its change threshold and the
            // shutdown persist for a non-zero count. Left alone, a read-mostly index would rebuild
            // from the store on every restart, which is exactly what the format bump was supposed to
            // cost only once. Must follow the start above, since the manager does not exist before it.
            //
            // This only reaches indices that run a background task manager - without one there is
            // nothing to schedule against. Such an index is not stranded, though: close() calls
            // doPersistToDisk directly for it, which flushes the rebuilt graph over the rejected files.
            // The gap is a process that is killed rather than closed, which repeats the work on the next
            // start.
            if(diskRebuildPending && this.backgroundTaskManager != null)
            {
                this.backgroundTaskManager.markPersistRequired();
            }
            else if(diskRebuildPending && this.activationPending)
            {
                // the manager of a new index starts only on activation, which schedules this persist then
                this.persistOnActivation = true;
            }
        }

        /**
         * Starts the unified background task manager if any background feature is enabled. Does nothing
         * for a new index that is not activated yet (see {@link #internalActivate()}).
         */
        private void startBackgroundManagersIfEnabled()
        {
            if(this.activationPending)
            {
                return;
            }

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
            // Construction dominates the vector-lookup cost of an in-memory index: it performs
            // thousands of comparisons per insertion, each one a copy out of the vector file, and the
            // count per insertion grows as the graph deepens. Scoring from the codes replaces every one
            // of those with an in-heap read.
            //
            // The codes have to exist first, which is why this is not the state a new index starts
            // in. The switch does not come through here at all - performPqSwitch constructs its
            // replacement itself - so this branch serves the paths that rebuild an index that has
            // already switched, where the codes are present and populated.
            final MutablePQVectors       pqVectors = this.inMemoryPqVectors;
            final GenerationVectorValues vectors   =
                new GenerationVectorValues(this.vectorFile, this.vectorTypeSupport);
            final BuildScoreProvider scoreProvider = pqVectors != null
                ? BuildScoreProvider.pqBuildScoreProvider(this.jvectorSimilarityFunction(), pqVectors)
                : BuildScoreProvider.randomAccessScoreProvider(
                    this.nullSafe(vectors), this.jvectorSimilarityFunction()
                )
            ;

            this.liveVectorValues = vectors;
            this.builder = this.newGraphIndexBuilder(scoreProvider);
            this.index = (OnHeapGraphIndex)this.builder.getGraph();
        }

        /**
         * Creates an empty graph builder with this index's configured graph parameters.
         * <p>
         * jVector 4.0.1 deprecates its constructors in favour of {@code GraphIndexBuilder.builder(...)},
         * but that builder takes {@code addHierarchy} and {@code refineFinalGraph} from
         * {@code GraphIndexBuilderConfig}, a process-wide singleton that can be changed at runtime through
         * JMX. The constructor pins both per index (a hierarchical graph, refined at the end), so an index
         * builds the same graph whatever another component of the process has configured.
         *
         * @param scoreProvider how the builder scores candidate neighbors
         * @return a new, empty builder
         */
        @SuppressWarnings("deprecation")
        private GraphIndexBuilder newGraphIndexBuilder(final BuildScoreProvider scoreProvider)
        {
            return new GraphIndexBuilder(
                scoreProvider,
                this.configuration.dimension(),
                this.configuration.maxDegree(),
                this.configuration.beamWidth(),
                this.configuration.neighborOverflow(),
                this.configuration.alpha(),
                true // use hierarchical index
            );
        }

        /**
         * Initializes the thread-local searcher pool of the in-memory graph. Each disk segment has a
         * pool of its own (see {@link DiskSegment#searcher()}), which lives as long as the segment.
         */
        private void initializeSearcherPool()
        {
            // Close existing pools if present
            this.closeSearcherPools();

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

        /**
         * Closes the searcher pools and releases resources.
         */
        private void closeSearcherPools()
        {
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

        /**
         * Returns the graph ordinal of an entity id. The highest ordinal is {@code Integer.MAX_VALUE - 1}: the
         * vector values are bounded by the highest ordinal plus one, which must fit an int, so an id that would
         * make every scoring and segment write fail later is rejected here, before anything is written.
         *
         * @throws IllegalStateException if the id is {@code Integer.MAX_VALUE} or above
         */
        static int toOrdinal(final long entityId)
        {
            if(entityId >= Integer.MAX_VALUE)
            {
                throw new IllegalStateException(
                    "Entity ID " + entityId + " exceeds maximum supported value of " + (Integer.MAX_VALUE - 1)
                );
            }
            return (int)entityId;
        }

        private float[] vectorize(final E entity)
        {
            return this.validateVector(this.ownVector(this.vectorizer.vectorize(entity)));
        }

        /**
         * A computed vectorizer's result, copied: a vectorizer that reuses one buffer could otherwise change a
         * vector the index still holds, e.g. the vectors a reindex collects before it writes them. Copied before
         * the validation, so the vector validated is the one written. An embedded vectorizer's result is the
         * entity's own array and is not copied.
         */
        private float[] ownVector(final float[] vector)
        {
            return vector != null && !this.vectorizer.isEmbedded() ? vector.clone() : vector;
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

        @Override
        public final GigaMap<E> parentMap()
        {
            return this.parent.parentMap();
        }

        /**
         * Records a graph-affecting content change: bumps the persisted {@link #structuralModCount}
         * (the crash-restart witness stamped into the disk manifest) and marks the index dirty
         * for the next persist. Use in place of {@link #markStateChangeChildren()} at content
         * mutation sites (add / remove / vec↔null transition); do NOT use it for {@code optimize()},
         * which reshapes the graph without changing logical content and must not force a rebuild.
         * <p>
         * Marks BOTH the instance and children dirty: {@code structuralModCount} is a field of this
         * instance, so it is only re-serialized when the instance itself is flagged
         * AbstractStateChangeFlagged#isInstanceNewOrChanged() gates {@code internalStore}) —
         * {@code markStateChangeChildren()} alone would persist only children and silently drop the
         * counter, and with it the vector count.
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

        @Override
        public Path indexDirectory()
        {
            final Path resolved = this.resolvedDirectory;
            return resolved != null ? resolved : Internal.super.indexDirectory();
        }

        /**
         * The directory for the index files, resolved from the configuration's location on first use.
         */
        private Path indexDirectoryForDisk()
        {
            Path directory = this.resolvedDirectory;
            if(directory == null)
            {
                directory = this.resolveIndexLocation();
                this.resolvedDirectory = directory;
            }
            return directory;
        }

        private Path resolveIndexLocation()
        {
            final IndexLocation location = this.configuration.indexLocation();
            if(location == null || !location.isNamed())
            {
                return this.configuration.indexDirectory();
            }

            final Path directory;
            try
            {
                directory = location.resolve();
            }
            catch(final IllegalStateException e)
            {
                // No fallback to any stored directory: that is exactly the silent wrong location this
                // mechanism exists to prevent (e.g. another store's graph on a copied volume).
                throw new IllegalStateException("Vector index \"" + this.name + "\": " + e.getMessage(), e);
            }
            LOG.info("Vector index '{}' uses directory {} ({})", this.name, directory, location);
            return directory;
        }

        @Override
        public void internalReplaceConfiguration(final VectorIndexConfiguration configuration)
        {
            // Marking the record dirty persists it in the current layout; an index loaded from a layout before
            // the vector file must have imported its vector store first, or the next store drops the vectors.
            this.ensureVectorsRecovered();
            this.configuration = notNull(configuration);
            this.markStateChangeInstance();
        }

        @Override
        public void internalAssignFirstLocation(final VectorIndexConfiguration configuration)
        {
            notNull(configuration);
            if(!this.builderLock.isWriteLockedByCurrentThread())
            {
                throw new IllegalStateException("Vector index \"" + this.name + "\": internalAssignFirstLocation()"
                    + " must be called via VectorIndices.changeIndexLocation, which acquires its write lock first.");
            }
            if(this.vectorizer.isEmbedded() || this.keepsIndexFiles())
            {
                throw new IllegalStateException("Vector index \"" + this.name + "\" is no computed in-memory index"
                    + " without an index location.");
            }
            if(this.closed || this.closeRequested)
            {
                // Before anything is initialized: an access would bring the closed index back.
                throw new IllegalStateException("Vector index \"" + this.name + "\" is closed.");
            }
            this.ensureIndexInitialized();
            this.ensureVectorsRecovered();

            final VectorIndexConfiguration previous  = this.configuration;
            final VectorFile               temporary = this.vectorFile;
            this.configuration     = configuration;
            this.resolvedDirectory = null;
            final VectorFile relocated;
            try
            {
                // A fresh file at the store epoch the record carries, as the import of a 4.x store into an index
                // directory makes it: written by repairs at that epoch, it is consistent with the record the next
                // store writes, which carries its nonce. An older file of the index there is replaced.
                relocated = VectorFile.open(
                    this.indexDirectoryForDisk(),
                    this.name,
                    this.configuration.dimension(),
                    this.checkedVectorChunkBytes(),
                    0L,
                    this.vectorFileEpoch,
                    "vector index '" + this.name + "'"
                );
            }
            catch(final RuntimeException | Error e)
            {
                this.configuration     = previous;
                this.resolvedDirectory = null;
                throw e;
            }
            try
            {
                copyVectors(temporary, relocated);
            }
            catch(final RuntimeException | Error e)
            {
                this.configuration     = previous;
                this.resolvedDirectory = null;
                relocated.closeAndDelete();
                throw e;
            }
            relocated.checkpointInterval(this.configuration.vectorCheckpointIntervalMs());

            // From here on the index runs on the relocated file. Like removeAll: the pending operations are stale,
            // and the graph, built on the temporary file, is rebuilt from the relocated one at the next access.
            if(this.backgroundTaskManager != null)
            {
                this.backgroundTaskManager.shutdownWithoutWaiting();
                this.backgroundTaskManager = null;
            }
            this.closeInternalResources();
            this.vectorFile        = relocated;
            this.vectorsRecovered  = true;
            this.vectorFileNonce   = relocated.nonce();
            this.legacyVectorStore = null;
            temporary.close();
            this.initializeIndex();
            this.markStateChangeInstance();
        }

        /**
         * Copies every present vector with its key from {@code source} into {@code target} by repairs, chunk by
         * chunk, and ends the target's recovery.
         */
        private static void copyVectors(final VectorFile source, final VectorFile target)
        {
            final int   perChunk = target.slotsPerChunk();
            final int[] chunk    = {-1};
            source.forEachPresent(ordinal ->
            {
                if(chunk[0] >= 0 && ordinal / perChunk != chunk[0])
                {
                    target.repaired(chunk[0]);
                }
                chunk[0] = ordinal / perChunk;
                target.repair(ordinal, source.read(ordinal), source.key(ordinal));
                return true;
            });
            if(chunk[0] >= 0)
            {
                target.repaired(chunk[0]);
            }
            target.recovered();
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

        /**
         * Back-fills this new index, not yet registered and not yet activated, from the entities the
         * parent map already holds - its single population path (see the constructor).
         * <p>
         * Without eventual indexing the graph is built here, synchronously. With eventual indexing only the
         * vectors are computed, validated and written to the vector file here, and the graph is built in the
         * background once the index is registered ({@link #internalAddBackfilled(int[])}), so registration
         * does not wait for it. Either way every entity's own vector is computed here, so a throwing
         * vectorizer leaves nothing behind but this unregistered instance, which the caller discards
         * ({@link #internalDiscard(Throwable)}) together with its vector file.
         * <p>
         * Must be called with the parent-map monitor held.
         *
         * @return with eventual indexing the ordinals to add after registration, otherwise {@code null}
         */
        int[] internalBackfill()
        {
            if(this.configuration.eventualIndexing())
            {
                final VectorFile file   = this.vectorFile;
                final int[][]    holder = {new int[16]};
                final int[]      size   = {0};
                this.parentMap().iterateIndexed((entityId, entity) ->
                {
                    // validates the id range on this thread, as internalAdd does
                    final int     ordinal = toOrdinal(entityId);
                    final float[] vector  = this.vectorize(entity);
                    final long    key     = this.vectorizer.vectorKey(entity);
                    if(vector == null)
                    {
                        if(key != Vectorizer.NO_KEY)
                        {
                            this.writeSlot(file, ordinal, null, key);
                        }
                        return;
                    }
                    this.writeSlot(file, ordinal, vector, key);
                    if(size[0] == holder[0].length)
                    {
                        holder[0] = Arrays.copyOf(holder[0], size[0] * 2);
                    }
                    holder[0][size[0]++] = ordinal;
                });
                return Arrays.copyOf(holder[0], size[0]);
            }

            // No manager exists yet, so the adds below build the graph synchronously and their changes are
            // not counted; activation hands the count to the manager.
            final long changesBefore = this.structuralModCount;
            this.parentMap().iterateIndexed(this::internalAdd);
            this.backfillChanges = Math.toIntExact(this.structuralModCount - changesBefore);
            return null;
        }

        /**
         * Computes and validates the vector of every entity of the parent map, changing nothing: a vectorizer
         * that throws for one entity leaves the index as it was. The vectors stay on the heap until the rebuild
         * writes them, as the vector store held them before: written into the vector file now, the removeAll of
         * the rebuild would make them absent again. Must be called with the parent-map monitor held.
         */
        private CollectedVectors collectParentVectors()
        {
            final CollectedVectors collected = new CollectedVectors();
            this.parentMap().iterateIndexed((entityId, entity) ->
            {
                // validates the id range on this thread, as internalAdd does
                final int ordinal = toOrdinal(entityId);
                collected.add(ordinal, this.vectorize(entity), this.vectorizer.vectorKey(entity));
            });
            return collected;
        }

        /**
         * The vectors of a computed reindex between its validation pass and its rebuild: ordinal, vector
         * ({@code null} for an entity without one) and vector key per entity, in parent-map order.
         */
        private static final class CollectedVectors
        {
            int[]     ordinals = new int[16]    ;
            float[][] vectors  = new float[16][];
            long[]    keys     = new long[16]   ;
            int       size                      ;

            void add(final int ordinal, final float[] vector, final long key)
            {
                if(this.size == this.ordinals.length)
                {
                    final int capacity = this.size * 2;
                    this.ordinals = Arrays.copyOf(this.ordinals, capacity);
                    this.vectors  = Arrays.copyOf(this.vectors , capacity);
                    this.keys     = Arrays.copyOf(this.keys    , capacity);
                }
                this.ordinals[this.size] = ordinal;
                this.vectors [this.size] = vector ;
                this.keys    [this.size] = key    ;
                this.size++;
            }
        }

        @Override
        public void internalReindex()
        {
            // Before anything is dropped: a vectorizer that throws for one entity must leave the index as it was.
            if(this.vectorizer.isEmbedded())
            {
                // Validation only. The vectors live in the entities and are read from them again while re-adding,
                // so keeping this pass's results would hold every vector a second time for a vectorizer that
                // derives them on the fly, for the whole rebuild.
                this.parentMap().iterateIndexed((entityId, entity) ->
                {
                    toOrdinal(entityId);
                    this.vectorize(entity);
                });
                this.internalRemoveAll();
                this.parentMap().iterateIndexed(this::internalAdd);
                return;
            }

            // Computed: one vectorizer call per entity, before anything is dropped. The vectors are kept on the
            // heap for the rebuild, no more than the vector store held before.
            final CollectedVectors collected = this.collectParentVectors();

            this.internalRemoveAll();

            // The file survived the removeAll in place (every slot absent), so the vectors are written back under
            // the ordinals the validation pass collected and their nodes added as internalAdd would, without a
            // second vectorizer call. An entity without a vector keeps its key and counts, as in internalAdd.
            final VectorFile file = this.vectorFile;
            for(int i = 0; i < collected.size; i++)
            {
                final int     ordinal = collected.ordinals[i];
                final float[] vector  = collected.vectors[i] ;
                final long    key     = collected.keys[i]    ;
                if(vector == null)
                {
                    if(key != Vectorizer.NO_KEY)
                    {
                        this.writeSlot(file, ordinal, null, key);
                    }
                    this.markWitnessChanged(1);
                    continue;
                }
                this.writeSlot(file, ordinal, vector, key);
                this.addWrittenVector(ordinal, vector);
            }
        }

        @Override
        public boolean internalTryLockExclusive()
        {
            // A read-lock holder cannot acquire the write lock, so the parent map would retry forever, e.g. for a
            // vectorizer that calls removeAll() or reindex() while it is scoring during a search.
            if(this.builderLock.getReadHoldCount() > 0)
            {
                throw new IllegalStateException(
                    "Vector index \"" + this.name + "\" cannot be rebuilt or cleared from within its own search."
                );
            }
            return this.builderLock.writeLock().tryLock();
        }

        @Override
        public void internalUnlockExclusive()
        {
            this.builderLock.writeLock().unlock();
        }

        /**
         * Keeps a new search out while GigaMap.removeAll() / reindex() wait for the write lock of this index's
         * group, or a freeze for a flush waits for it. They only try the lock while holding the parent-map monitor,
         * so without this, searches overlapping each other would hold the read lock forever and the attempt would
         * never succeed.
         * <p>
         * Does not wait on a thread that holds the parent-map monitor (the waiting writer cannot proceed before
         * that monitor is released, and holds no lock such a search needs), or that already holds the read lock
         * (a nested search: the writer cannot get the write lock before it is released). Waits at most
         * {@link #EXCLUSIVE_GATE_MAX_NANOS}, so a search the writer itself waits for in some other way is only
         * delayed, never kept out for good; the bound is far above what searches need to drain.
         */
        private void awaitNoPendingExclusive()
        {
            // the pending check first: volatile reads, and almost always false
            if(!this.isExclusivePending()
                || this.builderLock.getReadHoldCount() > 0
                || Thread.holdsLock(this.parentMap()))
            {
                return;
            }
            final long deadline = System.nanoTime() + EXCLUSIVE_GATE_MAX_NANOS;
            while(this.isExclusivePending()
                && System.nanoTime() - deadline < 0
                && !Thread.currentThread().isInterrupted())
            {
                LockSupport.parkNanos(100_000L);
            }
        }

        /**
         * Whether a writer tries the write lock while holding the parent monitor: GigaMap.removeAll() /
         * reindex() for this index's group, or a freeze, an optimization or a close of this index (see
         * {@link #exclusiveWaiters}).
         */
        private boolean isExclusivePending()
        {
            return this.exclusiveWaiters > 0
                || this.parent instanceof final VectorIndices.Internal<E> group && group.internalIsExclusivePending();
        }

        /**
         * Rejects {@link #persistToDisk()}, {@link #optimize()} and {@link #close()} on a thread holding the
         * parent-map monitor (inside {@code synchronized(map)} or {@code GigaMap.apply} logic). They wait for the
         * write lock, a flush or a merge in flight, whose holders may need the monitor, so the call could wait for
         * itself.
         */
        private void ensureNotHoldingParentMonitor(final String operation)
        {
            if(Thread.holdsLock(this.parentMap()))
            {
                throw new IllegalStateException(
                    "Vector index \"" + this.name + "\": " + operation + " must not be called while holding the"
                    + " GigaMap's monitor (e.g. inside synchronized(map) or GigaMap.apply logic)."
                );
            }
        }

        /**
         * Starts the background managers that the constructor of a new index deferred, and hands them the
         * back-fill's work: its graph changes count toward the change thresholds, and disk files rejected at
         * construction get their rewrite scheduled.
         * <p>
         * Must be called with the parent-map monitor held, after {@link #internalBackfill()} succeeded.
         */
        void internalActivate()
        {
            this.activationPending = false;
            this.startBackgroundManagersIfEnabled();

            if(this.backgroundTaskManager != null)
            {
                if(this.backfillChanges > 0)
                {
                    this.backgroundTaskManager.markDirty(this.backfillChanges);
                }
                if(this.persistOnActivation)
                {
                    this.backgroundTaskManager.markPersistRequired();
                }
            }
            this.backfillChanges     = 0;
            this.persistOnActivation = false;
        }

        /**
         * Adds the ordinals collected by {@link #internalBackfill()} with eventual indexing, once the index is
         * registered and activated, one queued operation per entity - exactly as {@link #internalAdd} does.
         * Not batched: the worker catches failures per operation, so a failed insertion costs only its own
         * entity, and each operation holds the builder's read lock for a single insertion. Calls no
         * vectorizer on this thread: the vectors are in the vector file already, and the graph is built by
         * the worker.
         * <p>
         * Must be called with the parent-map monitor held.
         *
         * @param ordinals the ordinals returned by {@link #internalBackfill()}
         */
        void internalAddBackfilled(final int[] ordinals)
        {
            for(final int ordinal : ordinals)
            {
                this.addWrittenVector(ordinal, null);
            }
        }

        /**
         * Discards a new index whose back-fill failed, before it was registered or activated. No background
         * manager exists yet and nothing else can reach the instance, so this only releases its transient
         * state; nothing is persisted. A failure while releasing is attached to {@code cause}.
         *
         * @param cause the failure of the back-fill
         */
        void internalDiscard(final Throwable cause)
        {
            this.builderLock.writeLock().lock();
            try
            {
                this.closeInternalResources();
                final VectorFile file = this.vectorFile;
                this.vectorFile = null;
                if(file != null)
                {
                    file.closeAndDelete();
                }
            }
            catch(final Throwable t)
            {
                cause.addSuppressed(t);
            }
            finally
            {
                this.closed = true;
                this.builderLock.writeLock().unlock();
            }
        }

        @Override
        public void internalAdd(final long entityId, final E entity)
        {
            // No synchronized(parentMap) needed — called from GigaMap's synchronized methods.
            // Validates the id range before anything else happens.
            final int ordinal = toOrdinal(entityId);

            this.ensureIndexInitialized();

            final float[] vector = this.vectorize(entity);
            final long    key    = this.vectorizer.vectorKey(entity);

            // Null vector (opted-in): the entity has no embedding — keep it out of the graph entirely, so
            // it never appears in search results. Its key is kept, so an update that leaves it without one
            // skips the vectorizer.
            if(vector == null)
            {
                if(key != Vectorizer.NO_KEY)
                {
                    this.writeSlot(this.vectorFile, ordinal, null, key);
                }
                this.markWitnessChanged(1);
                return;
            }

            this.writeSlot(this.vectorFile, ordinal, vector, key);
            this.addWrittenVector(ordinal, vector);
        }

        /**
         * Writes the vector of an ordinal to the vector file, or makes it absent, and keeps the vector
         * count. Under the parent monitor, which makes this thread the file's only writer.
         *
         * @return whether the ordinal had a vector before
         */
        private boolean writeSlot(
            final VectorFile file   ,
            final int        ordinal,
            final float[]    vector ,
            final long       key
        )
        {
            final boolean wasPresent = file.isPresent(ordinal);
            this.preserveForFrozenGenerations(file, ordinal);
            file.write(ordinal, vector, key);
            if(vector != null && !wasPresent)
            {
                this.vectorCount++;
            }
            else if(vector == null && wasPresent)
            {
                this.vectorCount--;
            }
            return wasPresent;
        }

        /**
         * Hands the current vector of an ordinal about to be rewritten to every frozen generation that holds its
         * node and is not written yet, so the flush writes the node as it was at the freeze (see
         * {@link GenerationVectorValues}). Costs a volatile read unless a flush is in flight.
         */
        private void preserveForFrozenGenerations(
            final VectorFile file   ,
            final int        ordinal
        )
        {
            final SourceSet published = this.sources;
            if(published == null || published.frozen().isEmpty())
            {
                return;
            }
            // Acquired like a search does: a flush publishes on its own thread under the commit lock, not the
            // monitor held here, and could otherwise release the set and close a generation this loop reads.
            final SourceSet set = this.acquireSources();
            if(set == null)
            {
                return;
            }
            try
            {
                final Runnable hook = this.preserveTestHook;
                if(hook != null)
                {
                    hook.run();
                }
                float[] current = null;
                boolean read    = false;
                for(final HeapGeneration frozen : set.frozen())
                {
                    if(frozen.vectorValues().isFrozen() && frozen.graph().containsNode(ordinal))
                    {
                        if(!read)
                        {
                            current = file.read(ordinal);
                            read    = true;
                        }
                        frozen.vectorValues().preserve(ordinal, current);
                    }
                }
            }
            finally
            {
                set.release();
            }
        }

        /**
         * Adds the graph node of an ordinal whose vector is in the vector file already, deferred to the
         * background worker with eventual indexing. Does not vectorize the entity.
         *
         * @param ordinal the ordinal
         * @param vector  its vector, or {@code null} to read it from the file
         */
        private void addWrittenVector(
            final int     ordinal,
            final float[] vector
        )
        {
            this.markContentChanged();

            if(this.isEventualIndexing())
            {
                // The owner first, at enqueue: the operation is counted in the witnesses from now on,
                // and a freeze before it is applied moves it into the frozen graph, whose slot this is.
                this.setOwner(ordinal, true);
                // Defer graph update to background thread
                this.backgroundTaskManager.enqueueAdd(ordinal);
            }
            else
            {
                // Add to HNSW graph using entity ID as ordinal. Idempotent, since an add can name an
                // ordinal the graph already carries, e.g. a re-added entity.
                final VectorFloat<?> vf = vector != null
                    ? this.vectorTypeSupport.createFloatVector(vector)
                    : this.vectorOf(ordinal)
                ;
                if(vf != null)
                {
                    this.internalAddGraphNodeIdempotent(this.builder, ordinal, vf);
                }
                // After the insert, so the entity is never missing from every source meanwhile.
                this.setOwner(ordinal, true);

                // Mark dirty for background managers
                this.markDirtyForBackgroundManagers(1);
            }
            this.requestFlushIfDue();
        }

        /**
         * Sets which source of an on-disk index an entity is live in after a mutation: the live
         * in-memory graph if it has a vector there now, nobody if it has none. Hides every other copy
         * at once. Under the parent monitor; does nothing for an in-memory index.
         *
         * @param ordinal the entity's ordinal
         * @param live    whether the entity is live in the live in-memory graph now
         */
        private void setOwner(
            final int     ordinal,
            final boolean live
        )
        {
            final SourceSet set = this.sources;
            if(set != null)
            {
                final OwnerTable owners = set.owners();
                owners.set(ordinal, live ? owners.liveSlot() : SegmentOwners.NONE);
            }
        }

        /**
         * Starts a background flush once the live in-memory graph of an on-disk index has reached the
         * flush threshold: frozen right here if that can be done at once (see
         * {@link #tryFreezeForBackgroundFlush()}), otherwise by asking the background task manager for
         * a persist run ahead of its schedule. Cheap enough for every mutation, under the parent monitor,
         * and for every operation the eventual-indexing worker applies, without it.
         */
        private void requestFlushIfDue()
        {
            final BackgroundTaskManager manager = this.backgroundTaskManager;
            final GraphIndexBuilder     current = this.builder;
            // Without background persistence only explicit and shutdown persists write, even with a
            // manager running for eventual indexing or background optimization.
            if(manager == null
                || !this.configuration.backgroundPersistence()
                || this.sources == null
                || current == null
                || graphOf(current).size(0) < this.configuration.flushThreshold())
            {
                return;
            }
            // While a flush holds the permit, a requested persist could only decline, and with eventual
            // indexing every applied operation would ask again. The flush asks once it is done, see
            // requestFlushIfStillDue.
            final Semaphore permit = this.flushPermit;
            if(permit == null || permit.availablePermits() == 0)
            {
                return;
            }
            if(!this.tryFreezeForBackgroundFlush())
            {
                manager.requestFlush();
            }
        }

        /**
         * Freezes the live graph for a background flush on the mutating thread, which holds the parent
         * monitor already, and hands the write to the flush thread.
         * <p>
         * The background task thread has to win the monitor for a freeze, and the monitor is not fair:
         * a sustained ingest that takes it again right after each batch can keep that thread waiting for
         * as long as the ingest lasts, while the live graph grows far past the threshold. A mutation
         * already holds the monitor, and the GigaMap updates its own state before it calls the indices,
         * so the witnesses sampled here describe the state the mutation leaves.
         * <p>
         * Only ever tried, so a mutation is never held up for more than about one search: the flush
         * permit and the write lock are tried, and so is the whole freeze - a compressor to train, a
         * consolidation, a graph left frozen by a failed flush, or eventual indexing (whose lock comes
         * before the monitor) leave it to the background task thread, which can wait for them.
         *
         * @return whether a flush was handed to the flush thread
         */
        private boolean tryFreezeForBackgroundFlush()
        {
            final SourceSet set = this.sources;
            if(this.configuration.eventualIndexing()
                || this.closed
                || !Thread.holdsLock(this.parentMap())
                || set == null
                || !set.frozen().isEmpty()
                || this.isCompressionTrainingPending()
                || this.isConsolidationDue(set, true))
            {
                return false;
            }
            final Semaphore permit = this.flushPermit;
            if(permit == null || !permit.tryAcquire())
            {
                return false;
            }
            FlushPlan plan = null;
            try
            {
                plan = this.tryFreezeUnderPermit();
            }
            finally
            {
                if(plan == null)
                {
                    permit.release();
                }
            }
            return plan != null && this.handOffFlush(plan, permit, false);
        }

        /**
         * The freeze of {@link #tryFreezeForBackgroundFlush()}, with the flush permit held.
         *
         * @return the plan to hand to the flush thread, or {@code null} if nothing was frozen
         */
        private FlushPlan tryFreezeUnderPermit()
        {
            if(!this.tryLockBuilderBriefly())
            {
                return null;
            }
            final FlushPlan plan;
            try
            {
                // Under the write lock, which close() and a flush's publication of the sources
                // exclude: both checked again with the set the freeze replaces.
                if(this.closed || this.sources == null)
                {
                    return null;
                }
                plan = this.freezeUnderLocks(PersistKind.BACKGROUND, false, this.sources, null, false);
            }
            finally
            {
                this.builderLock.writeLock().unlock();
            }
            return plan == FlushPlan.DECLINED ? null : plan;
        }

        /**
         * Tries {@code builderLock.writeLock()} for a freeze on a thread that holds the parent monitor
         * in the middle of a mutation, for at most {@link #INLINE_FREEZE_MAX_NANOS}: new searches hold
         * back meanwhile, so the ones in flight drain. They can, whatever the vectorizer: a search reads
         * its vectors from the vector file and never needs the monitor this thread holds.
         */
        private boolean tryLockBuilderBriefly()
        {
            final ReentrantReadWriteLock.WriteLock lock = this.builderLock.writeLock();
            if(lock.tryLock())
            {
                return true;
            }
            EXCLUSIVE_WAITERS.incrementAndGet(this);
            try
            {
                final long deadline = System.nanoTime() + INLINE_FREEZE_MAX_NANOS;
                while(System.nanoTime() - deadline < 0)
                {
                    LockSupport.parkNanos(50_000L);
                    if(lock.tryLock())
                    {
                        return true;
                    }
                }
                return false;
            }
            finally
            {
                EXCLUSIVE_WAITERS.decrementAndGet(this);
            }
        }

        private boolean isCompressionTrainingPending()
        {
            return this.pqManager != null && this.isPqTrainingPending()
                || this.nvqManager != null && this.isNvqTrainingPending();
        }

        @Override
        public void internalAddAll(final long firstEntityId, final Iterable<? extends E> entities)
        {
            // Collect vectors first (outside of main synchronized block)
            final List<? extends E> list    = this.toList(entities);
            final List<float[]>     vectors = this.vectorize(list);

            if(!vectors.isEmpty())
            {
                final long[] keys = new long[list.size()];
                for(int i = 0; i < keys.length; i++)
                {
                    keys[i] = this.vectorizer.vectorKey(list.get(i));
                }
                this.addVectors(firstEntityId, vectors, keys);
            }
        }

        /**
         * {@inheritDoc}
         * <p>
         * Both vectorizer modes take the same path: the vector file holds what the index was built from,
         * so an update is a no-op whenever the slot already holds the new vector. With a
         * {@link Vectorizer#vectorKey(Object) vector key} the vectorizer is not even called: an equal key
         * means an equal vector. Without one the new vector is computed and compared with the slot's floats.
         * Callers that touch unrelated entity fields (an adjacency list, say) would otherwise pay full
         * graph repair per update.
         */
        @Override
        public void internalUpdate(final long entityId, final E replacedEntity, final E entity)
        {
            // No synchronized(parentMap) needed — called from GigaMap's synchronized methods.
            this.ensureIndexInitialized();

            final int        ordinal = toOrdinal(entityId);
            final VectorFile file    = this.vectorFile;
            final long       key     = this.vectorizer.vectorKey(entity);
            if(key != Vectorizer.NO_KEY && key == file.key(ordinal))
            {
                return;
            }

            final float[] vector     = this.vectorize(entity);
            final boolean wasPresent = file.isPresent(ordinal);
            if(vector == null ? !wasPresent : file.holds(ordinal, vector))
            {
                // null→null, or the same vector: nothing for the graph or the witnesses. A new key is still
                // kept, so the next update with it skips the vectorizer.
                if(key != file.key(ordinal))
                {
                    file.write(ordinal, vector, key);
                }
                return;
            }
            this.writeSlot(file, ordinal, vector, key);

            if(this.isEventualIndexing())
            {
                // At enqueue, for the reason given in addWrittenVector. Its copies in segments and frozen
                // graphs are hidden from now on; the live graph shows the entity as it holds it until the
                // operation is applied, which is what eventual indexing promises.
                this.setOwner(ordinal, vector != null);
                this.backgroundTaskManager.enqueueUpdate(ordinal);
            }
            else
            {
                // One builder read, derived graph included: the in-memory PQ switch can publish a
                // replacement between two separate reads of the volatile fields.
                final GraphIndexBuilder currentBuilder = this.builder;
                if(vector == null)
                {
                    // vec→null: hide every copy and remove the node from the live graph if present. No
                    // removeDeletedNodes(): markNodeDeleted already excludes it from liveNodes. An entity
                    // that lives only in a segment or a frozen graph is not in the live graph, and is hidden
                    // by its owner.
                    this.setOwner(ordinal, false);
                    this.internalMarkOrdinalDeleted(currentBuilder, ordinal);
                }
                else
                {
                    // vec→vec' or null→vec. The repair scores through the vector file, so its ForkJoin
                    // workers never need the monitor this thread holds, whichever the vectorizer.
                    final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(vector);
                    try
                    {
                        this.internalReplaceGraphNode(currentBuilder, ordinal, vf);
                    }
                    catch(final GraphReachability.NodesLostException e)
                    {
                        // A repair around the insert lost other nodes. This entity's owner must still follow
                        // its new vector: live if the node is in the live graph, else hidden, never an older
                        // copy with the old vector.
                        this.setOwner(ordinal, currentBuilder != null && graphOf(currentBuilder).containsNode(ordinal));
                        throw e;
                    }
                    this.setOwner(ordinal, true);
                }
                this.markDirtyForBackgroundManagers(1);
            }

            this.markContentChanged();
            this.requestFlushIfDue();
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
         * Adds the vectors of consecutive entity ids, {@code null} for an entity without one.
         */
        private void addVectors(
            final long          firstEntityId,
            final List<float[]> vectors      ,
            final long[]        keys
        )
        {
            // No synchronized(parentMap) needed — called from GigaMap's synchronized methods.
            this.ensureIndexInitialized();

            // Entities without a vector (opted-in null embeddings) get no graph node, so they never appear
            // in search results.
            final VectorFile file         = this.vectorFile;
            final int[]      ordinals     = new int[vectors.size()];
            final float[][]  addedVectors = new float[vectors.size()][];
            int              count        = 0;
            for(int i = 0; i < vectors.size(); i++)
            {
                final int     ordinal = toOrdinal(firstEntityId + i);
                final float[] vector  = vectors.get(i);
                if(vector != null)
                {
                    this.writeSlot(file, ordinal, vector, keys[i]);
                    addedVectors[count] = vector;
                    ordinals[count++]   = ordinal;
                }
                else if(keys[i] != Vectorizer.NO_KEY)
                {
                    this.writeSlot(file, ordinal, null, keys[i]);
                }
            }

            if(count == 0)
            {
                this.markWitnessChanged(vectors.size());
                return;
            }
            final int[] added = Arrays.copyOf(ordinals, count);

            this.markContentChanged();

            if(this.isEventualIndexing())
            {
                // At enqueue, for the reason given in addWrittenVector.
                for(final int ordinal : added)
                {
                    this.setOwner(ordinal, true);
                }
                // Defer graph updates to background thread as a single batch operation
                this.backgroundTaskManager.enqueueBatchAdd(added);
            }
            else
            {
                // In parallel: the caller holds the monitor and waits, and the workers read nothing but the
                // vectors handed to them.
                this.insertBatch(
                    this.builder, added, index -> this.vectorTypeSupport.createFloatVector(addedVectors[index])
                );
                // After the inserts, for the reason given in addWrittenVector.
                for(final int ordinal : added)
                {
                    this.setOwner(ordinal, true);
                }

                // Mark dirty for background managers (with count for debouncing)
                this.markDirtyForBackgroundManagers(count);
            }
            // The entities without a vector change the witness as well, so they count towards
            // persistence as in a batch made only of them.
            final int withoutVector = vectors.size() - count;
            if(withoutVector > 0)
            {
                this.markWitnessChanged(withoutVector);
            }
            this.requestFlushIfDue();
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
            // A stale operation (see BackgroundTaskManager.isApplyingDiscardedOperation) changed nothing.
            if(this.backgroundTaskManager != null && !BackgroundTaskManager.isApplyingDiscardedOperation())
            {
                this.backgroundTaskManager.markDirty(count);
            }
        }

        /**
         * Counts entities added without a vector as changes to persist: they change the size and
         * highest id of the parent map, which the manifest's witnesses are checked against, so a
         * manifest that did not record them is rejected at the next start, which then rebuilds.
         */
        private void markWitnessChanged(final int count)
        {
            final BackgroundTaskManager manager = this.backgroundTaskManager;
            if(manager != null && this.configuration.onDisk())
            {
                manager.markPersistenceDirty(count);
            }
        }

        @Override
        public void internalRemove(final long entityId, final E entity)
        {
            // No synchronized(parentMap) needed — called from GigaMap's synchronized methods.
            this.ensureIndexInitialized();

            final int        ordinal = toOrdinal(entityId);
            final VectorFile file    = this.vectorFile;
            // A never-indexed entity (null embedding, no key) has a zero slot and nothing to remove.
            if(file.isPresent(ordinal) || file.key(ordinal) != Vectorizer.NO_KEY)
            {
                this.writeSlot(file, ordinal, null, Vectorizer.NO_KEY);
            }

            this.markContentChanged();

            // Hides every copy at once, in a segment, a frozen graph or the live graph, whichever
            // mode applies the graph change and whenever it does.
            this.setOwner(ordinal, false);

            if(this.isEventualIndexing())
            {
                // Defer graph update to background thread
                this.backgroundTaskManager.enqueueRemove(ordinal);
            }
            else
            {
                // Mark deleted in the live in-memory graph if the node exists there; a copy anywhere
                // else is hidden by the owner set above.
                this.internalMarkOrdinalDeleted(this.builder, ordinal);

                // Mark dirty for background managers
                this.markDirtyForBackgroundManagers(1);
            }
        }

        @Override
        public void internalPrepareMutation()
        {
            this.ensureIndexInitialized();
        }

        @Override
        public void internalRemoveAll()
        {
            // The write lock ensures no concurrent freeze, search, or background worker mutation is running:
            // closeInternalResources() destroys the graph and retires the sources, which would corrupt any
            // in-flight operation. A flush writing without a lock commits nothing afterwards (see retireSources).
            // Called with the parentMap monitor held, by GigaMap.removeAll() / reindex(), which have already
            // acquired this lock without waiting (internalTryLockExclusive): this is a reentrant acquisition that
            // never waits. Waiting for it here would deadlock with a persist, which holds it and needs the
            // monitor, so a caller that has not acquired it is rejected instead.
            if(!this.builderLock.isWriteLockedByCurrentThread())
            {
                throw new IllegalStateException(
                    "Vector index \"" + this.name + "\": internalRemoveAll() must be called via GigaMap.removeAll()"
                    + " or GigaMap.reindex(), which acquire its write lock first."
                );
            }
            this.builderLock.writeLock().lock();
            try
            {
                this.ensureIndexInitialized();

                // Every vector goes, in place: the file is never unmapped while open, so a reader that read the
                // chunk table before this sees absent slots rather than unmapped memory.
                this.removeAllVectors(this.vectorFile);
                // The vectors a 4.x store still held are gone with the entities, or recomputed by a reindex.
                this.legacyVectorStore = null;

                // Shut the background task manager down and discard its pending ops, they are stale. Without
                // waiting for its thread: a task of it may be waiting for the write lock or the monitor held here.
                if(this.backgroundTaskManager != null)
                {
                    this.backgroundTaskManager.shutdownWithoutWaiting();
                    this.backgroundTaskManager = null;
                }

                // Not waiting for a flush in flight either: it may be waiting for the monitor held here.
                // Advancing the epoch makes it commit nothing and delete what it wrote (see retireSources),
                // and the sequences it reserved stay protected meanwhile.
                this.closeInternalResources();

                // Before initializeIndex(): the bumped counter makes tryLoad() reject the files of the
                // generation just torn down, instead of adopting them as the new one.
                this.markContentChanged();

                // Reinitialize the index (this will also restart background managers if configured). Without
                // retiring the rejected manifest: it still matches the stored record until the next store.
                this.reloadingAfterRemoveAll = true;
                try
                {
                    this.initializeIndex();
                }
                finally
                {
                    this.reloadingAfterRemoveAll = false;
                }
                // The vector file was emptied above, so the empty graph is complete: no rebuild is due, and a
                // reindex that re-adds right after must not trigger one.
                this.graphRebuilt = true;

                // Mark dirty for background managers
                this.markDirtyForBackgroundManagers(1);
            }
            finally
            {
                this.builderLock.writeLock().unlock();
            }
        }

        /**
         * Makes every slot absent without changing the logical count. Under the parent monitor, before
         * recovery or as part of removeAll.
         */
        private void clearVectorSlots(final VectorFile file)
        {
            file.forEachSlot(ordinal ->
            {
                if(file.isPresent(ordinal) || file.key(ordinal) != Vectorizer.NO_KEY)
                {
                    file.write(ordinal, null, Vectorizer.NO_KEY);
                }
                return true;
            });
        }

        /**
         * Makes every slot of the vector file absent and drops the vector count. Under the write lock and the
         * parent monitor.
         */
        private void removeAllVectors(final VectorFile file)
        {
            this.clearVectorSlots(file);
            this.derivedVectorsReplaced = false;
            this.vectorCount            = 0L;
        }

        /**
         * {@inheritDoc}
         * <p>
         * Read from the vector file, without a lock once the vectors are recovered, and returned as a copy:
         * changing it changes nothing in the index.
         *
         * @throws IllegalStateException if the index is closed
         */
        @Override
        public void invalidateGraph()
        {
            // Write lock to exclude concurrent searches, persist, and background-worker
            // mutations, mirroring internalRemoveAll(). The caller side of a merge or
            // replication protocol may hold the parent monitor, so the lock is acquired
            // without ever waiting for it while holding the monitor (see
            // lockForInvalidation). This method never acquires the monitor itself.
            final VectorIndices.Internal<E> lockedGroup = this.lockForInvalidation();
            try
            {
                if(this.configuration.onDisk())
                {
                    throw new IllegalStateException(
                        "invalidateGraph() is not supported in on-disk mode; the graph is served from the"
                            + " on-disk segments, which this seam does not retire"
                    );
                }

                // Same release order as closeInternalResources(), but deliberately
                // narrower: disk state, PQ codebook, and background managers survive;
                // only the transient in-memory graph is retired.
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

                // Retiring only the graph would rebuild it from stale node-local vectors after an import.
                final VectorFile file = this.vectorFile;
                if(file != null && !this.keepsIndexFiles())
                {
                    this.derivedVectorsReplaced = true;
                    this.vectorsRecovered         = false;
                }

                // Clear the rebuild guard LAST: the next search/optimize/mutation
                // sees graphRebuilt == false and re-derives the graph from the
                // current stored vectors, exactly as after a fresh store load.
                this.graphRebuilt = false;
            }
            finally
            {
                this.unlockForInvalidation(lockedGroup);
            }
        }

        /**
         * Acquires the write lock for {@link #invalidateGraph()} without a thread that holds the parent-map monitor
         * ever waiting for it: the lock's holders may need that monitor (a persist takes it in Phase 1 while holding
         * the lock, and an embedded-mode search reads entities via {@code GigaMap.get} while holding the read lock).
         * <p>
         * A monitor holder only tries the locks of the whole index group (the same all-or-nothing attempt
         * {@code GigaMap.removeAll()} makes, which also keeps new searches out meanwhile) and, while one is held
         * elsewhere, waits on the monitor so that the holders can finish. Any other thread may simply wait.
         *
         * @return the index group whose locks were taken, to be passed to {@link #unlockForInvalidation}; {@code null}
         *         when only this index's lock was taken
         * @throws IllegalStateException if the calling thread is inside a search of this index, whose read lock
         *         would prevent the write lock from ever being granted
         */
        private VectorIndices.Internal<E> lockForInvalidation()
        {
            if(this.builderLock.getReadHoldCount() > 0)
            {
                throw new IllegalStateException(
                    "Vector index \"" + this.name + "\" cannot be invalidated from within its own search."
                );
            }
            final GigaMap<E> map = this.parentMap();
            if(!Thread.holdsLock(map))
            {
                this.builderLock.writeLock().lock();
                return null;
            }

            final VectorIndices.Internal<E> group = (VectorIndices.Internal<E>)this.parent;
            boolean locked = false;
            try
            {
                while(!(locked = group.internalTryLockExclusive()))
                {
                    map.wait(1);
                }
            }
            catch(final InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                    "Interrupted while waiting to invalidate vector index \"" + this.name + "\"", e
                );
            }
            finally
            {
                if(!locked)
                {
                    group.internalCancelExclusive();
                }
            }
            // The group's write locks include this index's; the body re-acquires it reentrantly.
            this.builderLock.writeLock().lock();
            return group;
        }

        /**
         * Releases what {@link #lockForInvalidation()} acquired, in either of its modes.
         *
         * @param lockedGroup the group {@link #lockForInvalidation()} returned, or {@code null}
         */
        private void unlockForInvalidation(final VectorIndices.Internal<E> lockedGroup)
        {
            this.builderLock.writeLock().unlock();
            if(lockedGroup != null)
            {
                lockedGroup.internalUnlockExclusive();
            }
        }

        @Override
        public float[] getVector(final long entityId)
        {
            if(entityId < 0L || entityId > Integer.MAX_VALUE)
            {
                return null;
            }
            // No file either after a close that failed, which leaves the index open: re-initialized like any
            // other access would, rather than reported closed.
            if(!this.vectorsRecovered || this.vectorFile == null)
            {
                synchronized(this.parentMap())
                {
                    if(!this.isIndexPresent() && !this.closed)
                    {
                        this.initializeIndex();
                    }
                    this.ensureVectorsRecovered();
                }
            }
            final VectorFile file = this.vectorFile;
            if(file == null)
            {
                throw new IllegalStateException("Vector index \"" + this.name + "\" is closed");
            }
            try
            {
                return file.read((int)entityId);
            }
            catch(final IllegalStateException e)
            {
                // Read without a lock: a first location (internalAssignFirstLocation) may have replaced and closed
                // the file meanwhile. The replacement holds the same vectors.
                final VectorFile current = this.vectorFile;
                if(current == null || current == file)
                {
                    throw e;
                }
                return current.read((int)entityId);
            }
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

            this.acquireReadLockOnRebuiltGraph();
            try
            {
                final Runnable hook = this.searchTestHook;
                if(hook != null)
                {
                    hook.run();
                }
                final VectorFloat<?> query = this.vectorTypeSupport.createFloatVector(queryVector);

                // An on-disk index searches all its sources, an in-memory one its only graph.
                final SearchResult result = this.sources != null
                    ? this.searchSources(query, k, rerankK)
                    : this.searchInMemoryIndex(query, k, rerankK)
                ;

                return this.convertSearchResult(result);
            }
            finally
            {
                this.builderLock.readLock().unlock();
            }
        }

        /**
         * Makes sure the graph is rebuilt and takes {@code builderLock.readLock()}, such that the
         * graph is still rebuilt once the lock is held. The caller must release the lock.
         * <p>
         * The rebuild runs BEFORE the read lock is acquired: the rebuild gate takes the parent
         * GigaMap monitor, and a thread holding builderLock must not wait for that monitor (the
         * order is the monitor, then builderLock; a writer holding the monitor only tries the write
         * lock, see {@link #lockBuilderUnderMonitor}, so a search under the monitor still gets the read
         * lock). Once rebuilt this is a lock-free volatile check.
         * <p>
         * The flag is checked again under the read lock, so a search never answers from a graph whose
         * rebuild became due after the check; if it has, the lock is released, the rebuild run or
         * awaited the usual way, and the lock taken again. A nested search (this thread already held
         * the read lock before) is never retried.
         */
        private void acquireReadLockOnRebuiltGraph()
        {
            while(true)
            {
                this.ensureIndexInitialized();

                // Acquire read lock - blocks during a freeze, an in-memory optimize, removeAll and close,
                // allows concurrent searches and GigaMap mutations.
                // Not taken while waiting for the monitor: the order is the monitor, then builderLock, and
                // a writer under the monitor only tries the write lock (lockBuilderUnderMonitor).
                this.awaitNoPendingExclusive();
                this.builderLock.readLock().lock();

                if(this.graphRebuilt
                    || this.closed
                    || this.builderLock.getReadHoldCount() > 1)
                {
                    return;
                }
                // The rebuild became due after the check above. Never wait for the monitor while
                // holding builderLock: release, then rebuild (or wait for it) at the top again.
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
                this.createCachingVectorValues(rerankK)
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
         * Builds the score provider for a search against one on-disk segment, dispatching on the two
         * dimensions of the segment's format: how it scores candidates and how it stores its vectors.
         *
         * <table border="1">
         *   <tr><th>Graph features</th><th>Traversal</th><th>Reranking</th></tr>
         *   <tr><td>{@code INLINE_VECTORS}</td><td>exact, from the vector file</td><td>-</td></tr>
         *   <tr><td>{@code INLINE_VECTORS + FUSED_PQ}</td><td>fused PQ codes</td><td>exact, from the mapping</td></tr>
         *   <tr><td>{@code NVQ_VECTORS}</td><td>quantized vectors</td><td>exact, from the vector file</td></tr>
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
         * <h2>Why the exact reranker wherever a reranker could be lossy</h2>
         * {@code searchSources} merges the results of all segments and of the in-memory graphs
         * <b>by raw score</b>. The in-memory graphs are always scored exactly, so an NVQ-dequantized
         * segment would sit on a systematically different scale and bias every merge. Whenever
         * results are compared, all of them must be exact. That is every query against an on-disk
         * index.
         * <p>
         * The gate is the <b>loaded graph's</b> feature set as well as the configuration: the graph
         * is self-describing, so the query path needs no happens-before edge with the training and
         * persist paths, and a graph whose training declined carries no feature to use however it is
         * configured. The configuration is still consulted so that the two must agree rather than
         * the graph being taken at its word - a file can carry a feature this build would not have
         * written, a downgrade being the concrete case. A segment flagged for merge was written for
         * another configuration or codebook, so it is only ever searched exactly.
         *
         * @param segment       the segment to search
         * @param searcher      the segment's searcher that will run the query, and whose view is used
         * @param query         the query vector
         * @param vsf           the similarity function
         * @param exactFallback the exact provider over this segment's local ordinals, to use when the
         *                      graph offers nothing better, and the source of the exact score function
         * @return the score provider to search with
         */
        private SearchScoreProvider diskScoreProvider(
            final DiskSegment                                               segment      ,
            final GraphSearcher                                             searcher     ,
            final VectorFloat<?>                                            query        ,
            final io.github.jbellis.jvector.vector.VectorSimilarityFunction vsf          ,
            final SearchScoreProvider                                       exactFallback
        )
        {
            if(segment.flaggedForMerge() || !(searcher.getView() instanceof final OnDiskGraphIndex.View view))
            {
                return exactFallback;
            }

            final OnDiskGraphIndex diskIndex = segment.graph();
            final Set<FeatureId>   features  = diskIndex.getFeatureSet();
            final boolean          hasNvq    = features.contains(FeatureId.NVQ_VECTORS);

            // PQ_IN_MEMORY first, because it is the one mode whose approximate function comes from
            // neither the graph nor the view. The codes are a heap-resident array loaded from the
            // segment's .pqv, by local ordinal like the graph, so scoring a candidate touches no disk
            // at all - which also means the view-scratch rule that governs the fused path does not
            // apply here.
            //
            // Reranking is exact from the vector file. There is nothing else it could be: this graph
            // carries no fused codes to rerank against, and when it is combined with NVQ storage it
            // carries no full-precision vectors either.
            final PQVectors pqVectors = this.configuration.approximateScoring() == ApproximateScoring.PQ_IN_MEMORY
                ? segment.pqVectors()
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
                // a behaviour change for the default on-disk configuration, so it keeps scoring
                // exactly from the vector file.
                return exactFallback;
            }

            if(useFusedPq)
            {
                // Traversal reads the fused codes; the rerank then reads the graph's own vector
                // block. For an INLINE graph those are the full-precision vectors, and the accept bits
                // admit only nodes whose entity this segment still owns, so they are the live ones.
                // For an NVQ graph the view's reranker dequantizes, which cost about 0.002
                // recall@10 in measurement but puts the score on another scale than the exactly
                // scored halves it is merged with, so the exact function is taken instead.
                final ScoreFunction.ExactScoreFunction reranker = hasNvq
                    ? exactFallback.exactScoreFunction()
                    : view.rerankerFor(query, vsf)
                ;
                return new DefaultSearchScoreProvider(
                    view.approximateScoreFunctionFor(query, vsf),
                    reranker
                );
            }

            // NVQ without fused codes: traverse on the quantized vectors in the mapping, which is
            // the point of storing them, and rerank exactly from the vector file. The view's reranker is
            // an ExactScoreFunction by type only - against an NVQ graph it dequantizes - so it is
            // adapted to the approximate slot rather than being handed over as if it were exact.
            // The rerank is exact, which is what makes NVQ storage lossless for the final ordering as
            // long as no fused codes are present.
            final ScoreFunction.ExactScoreFunction quantizedScorer = view.rerankerFor(query, vsf);
            return new DefaultSearchScoreProvider(
                (ScoreFunction.ApproximateScoreFunction)quantizedScorer::similarityTo,
                exactFallback.exactScoreFunction()
            );
        }

        /**
         * Searches an on-disk index: every segment, every in-memory graph frozen for a flush, and the
         * live in-memory graph, then merges the results.
         * <p>
         * Each source accepts only the nodes whose entity it owns, so each entity is answered by
         * exactly one source. The set of sources is acquired for the whole search, so a flush that
         * publishes its segment meanwhile closes nothing this search still traverses. Every source
         * over-fetches {@code rerankK} candidates to give the merge a richer pool.
         */
        private SearchResult searchSources(final VectorFloat<?> query, final int k, final int rerankK)
        {
            final io.github.jbellis.jvector.vector.VectorSimilarityFunction vsf = this.jvectorSimilarityFunction();

            // One per-query cache of live vectors by global ordinal, shared by every source: the same
            // entity can be reached from several sources' traversals, and each segment reads it
            // through its own local-to-global view below.
            final RandomAccessVectorValues liveVectors = this.createCachingVectorValues(rerankK);

            final List<SearchResult> results = new ArrayList<>();
            final SourceSet          set     = this.acquireSources();
            if(set == null)
            {
                return new SearchResult(new SearchResult.NodeScore[0], 0, 0, 0, 0, 0f);
            }
            try
            {
                final Runnable hook = this.sourcesAcquiredTestHook;
                if(hook != null)
                {
                    hook.run();
                }
                final SegmentOwners owners = set.owners().owners();

                // 1. Each segment, excluding the nodes whose entity it does not own any more
                for(final DiskSegment segment : set.segments())
                {
                    results.add(this.searchSegment(segment, owners, query, vsf, liveVectors, rerankK));
                }

                // 2. Each frozen in-memory graph a flush has not published yet
                for(final HeapGeneration frozen : set.frozen())
                {
                    if(frozen.nodeCount() > 0)
                    {
                        results.add(this.searchHeapGraph(
                            frozen.searcher(), frozen.graph(), owners, frozen.slot(), query, vsf, liveVectors, rerankK
                        ));
                    }
                }

                // 3. The live in-memory graph, which holds the mutations since the last freeze. Its
                // slot changes only with a freeze, which the read lock this search holds excludes.
                final OnHeapGraphIndex live = this.index;
                if(this.inMemorySearcherPool != null && live != null && live.size(0) > 0)
                {
                    results.add(this.searchHeapGraph(
                        this.inMemorySearcherPool.get(), live, owners, set.owners().liveSlot(),
                        query, vsf, liveVectors, rerankK
                    ));
                }
            }
            finally
            {
                set.release();
            }

            // 4. Merge results - truncate a single source's result to k since sources over-fetch to
            // provide the merge with a richer candidate pool
            if(results.isEmpty())
            {
                return new SearchResult(new SearchResult.NodeScore[0], 0, 0, 0, 0, 0f);
            }
            if(results.size() == 1)
            {
                return this.truncateResult(results.get(0), k);
            }
            return this.mergeSearchResults(results, k);
        }

        /**
         * Acquires the published set of sources for a search, or returns {@code null} if the index has
         * none (an in-memory index, or one closed meanwhile). A set released for good between reading
         * and acquiring it has been replaced first, so reading again finds its successor.
         *
         * @return the acquired set, to be released by the caller, or {@code null}
         */
        private SourceSet acquireSources()
        {
            while(true)
            {
                final SourceSet set = this.sources;
                if(set == null || set.tryAcquire())
                {
                    return set;
                }
                Thread.onSpinWait();
            }
        }

        /**
         * Searches one in-memory graph of an on-disk index exactly, accepting a node only while its
         * entity is owned by the graph's slot.
         */
        private SearchResult searchHeapGraph(
            final GraphSearcher                                             searcher   ,
            final OnHeapGraphIndex                                          graph      ,
            final SegmentOwners                                             owners     ,
            final int                                                       slot       ,
            final VectorFloat<?>                                            query      ,
            final io.github.jbellis.jvector.vector.VectorSimilarityFunction vsf        ,
            final RandomAccessVectorValues                                  liveVectors,
            final int                                                       rerankK
        )
        {
            final SearchScoreProvider scoreProvider = DefaultSearchScoreProvider.exact(query, vsf, liveVectors);
            // Capture the view once so setView(...) and liveNodes() agree on the same snapshot
            // (ConcurrentGraphIndexView uses snapshot isolation - two separate getView() calls could
            // return different snapshots).
            final var  view      = graph.getView();
            final Bits liveNodes = view.liveNodes();
            searcher.setView(view);
            return searcher.search(
                scoreProvider, rerankK, rerankK, 0f, 0f,
                ordinal -> liveNodes.get(ordinal) && owners.owner(ordinal) == slot
            );
        }

        /**
         * Searches one disk segment and returns the result with global ordinals.
         * <p>
         * When the segment carries FusedPQ, traversal runs on the compressed codes and the rerank
         * against the inline vectors in the same memory-mapped file. Otherwise the exact score function
         * reads the vector file through the segment's local-to-global view.
         */
        private SearchResult searchSegment(
            final DiskSegment                                               segment    ,
            final SegmentOwners                                             owners     ,
            final VectorFloat<?>                                            query      ,
            final io.github.jbellis.jvector.vector.VectorSimilarityFunction vsf        ,
            final RandomAccessVectorValues                                  liveVectors,
            final int                                                       rerankK
        )
        {
            final GraphSearcher       searcher      = segment.searcher();
            final SearchScoreProvider exactFallback = DefaultSearchScoreProvider.exact(
                query,
                vsf,
                new SegmentVectorValues(liveVectors, segment.localToGlobal())
            );
            final SearchScoreProvider provider = this.diskScoreProvider(segment, searcher, query, vsf, exactFallback);
            return segment.toGlobal(
                searcher.search(provider, rerankK, rerankK, 0f, 0f, segment.acceptBits(owners))
            );
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
         * Merges SearchResults: combines nodes, deduplicates by ordinal (keeping the higher score),
         * sorts by score descending, and takes top-k. The ordinals must all be global.
         * <p>
         * The owners make every entity live in one source only, so duplicates are not expected; the
         * deduplication is the safety net that keeps a result from naming an entity twice should two
         * sources ever both answer for it.
         */
        private SearchResult mergeSearchResults(
            final List<SearchResult> results,
            final int                k
        )
        {
            int totalCandidates = 0;
            int visitedCount    = 0;
            for(final SearchResult result : results)
            {
                totalCandidates += result.getNodes().length;
                visitedCount    += result.getVisitedCount();
            }

            if(totalCandidates == 0)
            {
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
            for (final SearchResult result : results)
            {
                for (final SearchResult.NodeScore node : result.getNodes())
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

            return new SearchResult(merged, visitedCount, 0, 0, 0, 0f);
        }

        /**
         * Creates caching vector values for search operations.
         * Wrapped with {@link NullSafeVectorValues} so that deleted nodes
         * (whose vectors are {@code null}) return a safe placeholder instead
         * of causing NPE/NaN during JVector graph traversal.
         * <p>
         * The result belongs to one query on one thread (see {@link OrdinalVectorCache}).
         *
         * @param expectedSize the number of distinct ordinals the cache is sized for up front (capped,
         *                     see {@link OrdinalVectorCache}); the search beam width, since that many
         *                     candidates are scored exactly even when traversal is approximate. The
         *                     cache grows beyond it.
         */
        private RandomAccessVectorValues createCachingVectorValues(final int expectedSize)
        {
            return new NullSafeVectorValues(
                new MappedVectorValues.Caching(this.vectorFile, this.vectorTypeSupport, expectedSize),
                this.configuration.dimension(),
                this.vectorTypeSupport
            );
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
            this.ensureNotHoldingParentMonitor("optimize()");

            // Drain pending indexing operations to ensure graph is complete
            if(this.isEventualIndexing())
            {
                this.backgroundTaskManager.drainQueue();
            }

            this.doOptimize();
        }

        /**
         * Core optimization logic without queue drain.
         * Called directly from the background task manager's executor thread (which applies one turn
         * of queued indexing operations first, not the whole queue) and from the public optimize()
         * method (which drains the queue first).
         * <p>
         * An on-disk index leaves its graphs alone: the live in-memory graph is bounded by the flush
         * threshold, and every flush purges the deleted nodes of the graph it writes and trims its
         * neighbor lists with {@code cleanup()}, so there is nothing a separate pass would add. It does
         * not even take the parent monitor, which a sustained ingest can keep for long stretches: the
         * background task thread would wait there, with every persist queued behind it.
         * <p>
         * An in-memory index first switches to compressed scoring if that is due (under the monitor
         * only, see {@link #performPqSwitch()}), then maintains its graph under the parent monitor and
         * {@code builderLock.writeLock()}: the monitor first, the write lock only tried (see
         * {@link #lockBuilderUnderMonitor(boolean, boolean)}). Holding both keeps
         * every search and every mutation out for the duration, since neither the purge nor the degree
         * enforcement is safe against concurrent inserts. Their ForkJoin workers must then never need
         * the monitor, which they do not: the builder scores from the vector file.
         */
        @Override
        public void doOptimize()
        {
            if(this.configuration.onDisk())
            {
                return;
            }
            // optimize() refuses a monitor holder, but a shutdown optimization can run on a closing thread that
            // holds it (index removal, storage shutdown).
            final boolean monitorHeld = Thread.holdsLock(this.parentMap());
            synchronized(this.parentMap())
            {
                this.ensureIndexInitialized();

                // The whole PQ switch, under this monitor and nothing else. It reads the vector file
                // and counters that mutations write under the monitor, which is what the monitor is
                // for here, and it takes no builderLock - the
                // posture ensureGraphRebuilt() documents as unable to sit in a monitor/builderLock
                // cycle.
                this.performPqSwitch();
            }

            // Monitor first, the write lock only tried (see lockBuilderUnderMonitor): a writer blocked on the lock
            // before the monitor deadlocks with a search issued under the monitor (inside GigaMap.iterate, apply
            // logic or synchronized(map)), which waits for the read lock behind it while holding the monitor.
            synchronized(this.parentMap())
            {
                if(!this.lockBuilderUnderMonitor(monitorHeld, true))
                {
                    return;
                }
                try
                {
                    // A closed index has no builder left to maintain; waiting for the lock released the monitor.
                    if(!this.closed && this.builder != null && graphOf(this.builder).size(0) > 0)
                    {
                        this.maintainLiveGraph(this.builder);
                    }
                }
                finally
                {
                    this.builderLock.writeLock().unlock();
                }
            }

            this.markStateChangeChildren();
        }

        /**
         * Purges deleted nodes from a graph that stays live and trims neighbor lists that overflowed
         * during construction back to the configured maximum degree.
         * <p>
         * Deliberately not {@code GraphIndexBuilder.cleanup()}. That is a finishing step for a graph
         * about to be written: it ends with {@code OnHeapGraphIndex.setAllMutationsCompleted()}, a
         * one-way flag that nothing resets, after which {@code getView()} hands out a
         * {@code FrozenView} instead of a {@code ConcurrentGraphIndexView}. The builder an
         * optimization maintains keeps receiving every later insert - an in-memory index never
         * replaces it at all - so searches, and each insert's own neighbor search, would lose the
         * snapshot isolation that keeps nodes still being inserted off the search path, for the rest
         * of the session. Only a flush, whose graph is frozen and takes no further insert, calls
         * {@code cleanup()}.
         * <p>
         * This performs the two parts of {@code cleanup()} that do not freeze the graph: the purge
         * and the degree enforcement. It leaves out the connection refinement of the upper-layer
         * nodes, which {@code GraphIndexBuilder} keeps private. Both parts prune neighbor lists, so it
         * ends with a {@link GraphReachability#repair} of every node.
         * <p>
         * Must be called with every insert excluded, see {@link #doOptimize()}, and holds the structure lock
         * exclusive besides, as every purge does.
         *
         * @param builder the live builder to maintain, not closed
         */
        private void maintainLiveGraph(final GraphIndexBuilder builder)
        {
            final ReentrantReadWriteLock.WriteLock lock = this.structureLock.writeLock();
            lock.lock();
            try
            {
                builder.removeDeletedNodes();

                final OnHeapGraphIndex graph = graphOf(builder);
                if(graph.size(0) == 0)
                {
                    return;
                }
                // enforceDegree covers every layer of the node and skips layers the node is not in. Only the nodes
                // actually in the graph are visited: ordinals are entity ids, so the id range of a graph that holds
                // few of them can be far larger than its node count. Every node of an upper layer is also in
                // layer 0. Snapshotted first; no insert runs meanwhile anyway.
                final int[] nodes = graph.nodeStream(0).toArray();
                Arrays.stream(nodes).parallel().forEach(graph::enforceDegree);
                // Last, over every node: the purge and the trim both prune neighbour lists, and either can take
                // the last edge into a node (see GraphReachability).
                if(builder == this.builder)
                {
                    this.repairLiveGraph(() ->
                        GraphReachability.repair(builder, nodes, nodes.length, this::vectorOf, this.name)
                    );
                }
            }
            finally
            {
                lock.unlock();
            }
        }

        @Override
        public void persistToDisk()
        {
            if(!this.configuration.onDisk())
            {
                return; // No-op for in-memory indices
            }
            this.ensureNotHoldingParentMonitor("persistToDisk()");

            // No drain of the eventual-indexing queue first: the freeze takes the queued operations
            // into the graph it freezes, and the flush applies them there.
            this.persist(PersistKind.EXPLICIT);
            // A segment that no longer matches the format or the codebook, such as one this persist has
            // just trained the codebook for, is rewritten here rather than left to a background merge that
            // a close right after would stop, and the rewrite is committed at once. The flush above may
            // have scheduled a background merge that got to the rewrite first; it publishes without a
            // commit, and finding nothing left to rewrite here must not leave it uncommitted.
            if(this.rewriteSegmentsInline() || this.mergeUncommitted)
            {
                this.persist(PersistKind.EXPLICIT);
            }
        }

        /**
         * Runs the merges that rewrite segments no longer matching the index on the calling thread,
         * holding the merge permit, so after a background merge in flight. Tier merges stay in the
         * background.
         *
         * @return whether a merge published something the caller has to commit
         */
        private boolean rewriteSegmentsInline()
        {
            final Semaphore permit = this.mergePermit;
            if(permit == null || !this.isRewriteDue())
            {
                // Not waiting for a tier merge in flight when there is nothing to rewrite.
                return false;
            }
            permit.acquireUninterruptibly();
            try
            {
                boolean merged = false;
                while(this.mergeOnce(true) == MergeOutcome.MERGED)
                {
                    merged = true;
                }
                return merged;
            }
            finally
            {
                permit.release();
                // A tier merge the persist made due is left to the background.
                this.scheduleMergeIfDue();
            }
        }

        /**
         * Core persistence logic, called by the background task manager: on its schedule or ahead of
         * it with {@code onShutdown == false}, and on close with {@code onShutdown == true}.
         *
         * @param onShutdown whether this is the persist of a closing index, which writes whatever
         *                   changed but never consolidates; otherwise the persist writes only once the
         *                   in-memory graph has reached the flush threshold
         * @return whether the persist took the changes made so far, see
         *         {@link BackgroundTaskManager.Callback#doPersistToDisk(boolean)}
         */
        @Override
        public boolean doPersistToDisk(final boolean onShutdown)
        {
            return this.persist(onShutdown ? PersistKind.SHUTDOWN : PersistKind.BACKGROUND);
        }

        /**
         * Persists an on-disk index: freezes the live in-memory graph and writes it as one more
         * segment, together with the deletions of the existing segments since the last commit.
         * <p>
         * The work is split so that no lock is held for anything expensive:
         * <ol>
         *   <li>Take the flush permit, so at most one flush is in flight. A background persist does not
         *       wait for it; every other persist does.</li>
         *   <li>Train the compressors if that is due, with no lock held, since k-means takes seconds.</li>
         *   <li>Freeze under the indexing lock, the parent monitor and {@code builderLock.writeLock()}
         *       (see {@link #freeze} for the order): sample the witnesses, move the queued
         *       eventual-indexing operations into the graph being frozen, capture the leave logs, install
         *       a fresh live graph and publish the new set of sources. This swaps references and copies
         *       small arrays, so it holds the locks for microseconds - except for a consolidation, which
         *       rebuilds. A background persist lets go of the permit while it waits for these locks and
         *       tries it again once it holds them: a mutation that reaches the flush threshold freezes on
         *       its own thread (see {@link #tryFreezeForBackgroundFlush()}) and needs the permit then,
         *       while the monitor it holds may keep this thread waiting for a long time.</li>
         *   <li>Write outside every lock (see {@link #runFlush}), on the flush writer for a background
         *       persist and on the calling thread otherwise. Searches and mutations go on meanwhile.</li>
         * </ol>
         *
         * @param kind which persist this is
         * @return whether the changes counted so far were taken, see
         *         {@link BackgroundTaskManager.Callback#doPersistToDisk(boolean)}
         */
        private boolean persist(final PersistKind kind)
        {
            if(!this.configuration.onDisk())
            {
                return true;
            }
            // A first look without any lock, so an idle tick costs nothing. Decided again under the
            // locks, where it is exact.
            if(kind == PersistKind.BACKGROUND && !this.isBackgroundFlushDue() && !this.isManifestCommitDue())
            {
                return false;
            }
            final Semaphore permit = this.flushPermit;
            if(permit == null)
            {
                // Never initialized: nothing to persist.
                return true;
            }
            if(kind == PersistKind.BACKGROUND)
            {
                return this.persistInBackground(permit);
            }
            else
            {
                try
                {
                    permit.acquire();
                }
                catch(final InterruptedException e)
                {
                    // The shutdown persist is interrupted when it overruns its timeout.
                    Thread.currentThread().interrupt();
                    LOG.warn("Persist of '{}' interrupted while waiting for a flush in flight", this.name);
                    return false;
                }
            }

            boolean completed = false;
            try
            {
                final boolean   trained = kind != PersistKind.SHUTDOWN && this.trainCompressorsBeforeLocking();
                final FlushPlan plan    = this.freeze(kind, trained, null);
                if(plan == null)
                {
                    return true;
                }
                if(plan == FlushPlan.DECLINED)
                {
                    return false;
                }
                this.runFlushReportingFailure(plan);
                completed = true;
                return true;
            }
            finally
            {
                permit.release();
                if(completed && kind != PersistKind.SHUTDOWN)
                {
                    // Requests made while this flush held the permit were skipped, see requestFlushIfDue,
                    // also those for the graph a removeAll() installed while it was abandoned.
                    this.requestFlushIfStillDue();
                }
            }
        }

        /**
         * The background persist of {@link #persist}: holds the permit to train, lets go of it while it
         * waits for the locks of the freeze, which tries it again, and hands the write to the flush
         * thread.
         * <p>
         * A write handed to the flush thread has not taken the changes yet: they are committed only once
         * it completes, and a close that comes first stops it. So they stay counted, which makes the
         * shutdown persist run - it waits for the write in flight and writes what is left - until the
         * flush retires them on its commit (see {@link #changesPersisted}).
         */
        private boolean persistInBackground(final Semaphore permit)
        {
            if(!permit.tryAcquire())
            {
                // A flush is in flight; the next tick looks again.
                return false;
            }
            final boolean trained;
            try
            {
                trained = this.trainCompressorsBeforeLocking();
            }
            finally
            {
                permit.release();
            }

            final FlushPlan plan = this.freeze(PersistKind.BACKGROUND, trained, permit);
            if(plan == null)
            {
                return true;
            }
            if(plan == FlushPlan.DECLINED)
            {
                return false;
            }
            // The freeze took the permit for this plan.
            return !this.handOffFlush(plan, permit, true);
        }

        /**
         * The changes the background task manager has counted towards persistence so far. Read under the
         * locks of a freeze, where every change counted is in what the freeze captures: a mutation counts
         * under the parent monitor, an eventual-indexing operation once applied, under the indexing lock.
         */
        private int countedChanges()
        {
            final BackgroundTaskManager manager = this.backgroundTaskManager;
            return manager != null ? manager.getPersistenceChangeCount() : 0;
        }

        /**
         * Retires the changes a freeze counted once what it captured is committed, so that only the
         * changes made since stay counted towards the next persist.
         */
        private void changesPersisted(final int counted)
        {
            final BackgroundTaskManager manager = this.backgroundTaskManager;
            if(manager != null && counted > 0)
            {
                manager.changesPersisted(counted);
            }
        }

        /**
         * Runs a flush on the calling thread, reporting a failure: it leaves the frozen graphs sources for the next
         * persist to write, and makes that persist due: the changes they hold may never have been
         * counted - operations a freeze moved out of the queue were not applied yet - so without this
         * neither a scheduled nor the shutdown persist would retry them.
         *
         * Returns normally once the flush committed, or once it was abandoned because the index was
         * cleared or closed meanwhile, which leaves nothing to write.
         *
         * @param plan the plan
         * @throws RuntimeException if the flush failed
         * @throws Error if the flush failed
         */
        private void runFlushReportingFailure(final FlushPlan plan)
        {
            try
            {
                this.runFlush(plan);
            }
            catch(final FlushAbandonedException e)
            {
                LOG.debug("Flush of '{}' abandoned: {}", this.name, e.getMessage());
            }
            catch(final RuntimeException | Error e)
            {
                final BackgroundTaskManager manager = this.backgroundTaskManager;
                if(manager != null)
                {
                    manager.markPersistRequired();
                }
                throw e;
            }
        }

        /**
         * Hands a frozen plan, whose permit the caller holds, to the flush writer, which then owns the
         * permit. If the writer is shut down, the background task thread writes the plan itself, while a
         * mutating thread, which a write must not hold up, leaves the frozen graph a source for the next
         * persist. Unless the writer took it, the permit is released here.
         *
         * @param plan           the plan
         * @param permit         the flush permit, held by the caller
         * @param inlineFallback whether to write the plan on this thread if the writer is shut down
         * @return whether the writer took the plan
         */
        private boolean handOffFlush(
            final FlushPlan plan          ,
            final Semaphore permit        ,
            final boolean   inlineFallback
        )
        {
            boolean handedOff = false;
            try
            {
                handedOff = this.submitFlush(plan, permit);
                if(!handedOff)
                {
                    if(inlineFallback)
                    {
                        this.runFlushReportingFailure(plan);
                    }
                    else
                    {
                        plan.release();
                    }
                }
                return handedOff;
            }
            finally
            {
                if(!handedOff)
                {
                    permit.release();
                }
            }
        }

        /**
         * Hands a background flush to the flush writer, which releases the permit when it is done.
         *
         * @return {@code false} if the writer is shut down
         */
        private boolean submitFlush(
            final FlushPlan plan  ,
            final Semaphore permit
        )
        {
            try
            {
                this.flushExecutor().execute(() ->
                {
                    boolean completed = false;
                    try
                    {
                        this.runFlushReportingFailure(plan);
                        completed = true;
                    }
                    catch(final RuntimeException e)
                    {
                        LOG.error("Background flush of '{}' failed, the next persist writes it again", this.name, e);
                    }
                    catch(final Error e)
                    {
                        LOG.error("Background flush of '{}' failed, the next persist writes it again", this.name, e);
                        throw e;
                    }
                    finally
                    {
                        permit.release();
                    }
                    if(completed)
                    {
                        this.requestFlushIfStillDue();
                    }
                });
                return true;
            }
            catch(final RejectedExecutionException e)
            {
                return false;
            }
        }

        /**
         * Requests another background flush once one is done, if one is due already: a request made
         * while it held the flush permit was skipped, and with no further mutation nothing would ask
         * again before the scheduled persist. Only after a flush that committed or was abandoned, not
         * after one that failed, so that one that keeps failing is retried on the schedule rather than
         * over and over.
         */
        private void requestFlushIfStillDue()
        {
            final BackgroundTaskManager manager = this.backgroundTaskManager;
            if(manager != null && this.configuration.backgroundPersistence() && this.isBackgroundFlushDue())
            {
                manager.requestFlush();
            }
        }

        /**
         * Returns the flush writer, creating it on first use. One thread at most, named like the
         * background task thread so the same leak checks cover it, and none while idle: an abandoned
         * index must not be kept alive by a thread waiting for work.
         */
        private ExecutorService flushExecutor()
        {
            ExecutorService executor = this.flushExecutor;
            if(executor == null)
            {
                synchronized(this.flushPermit)
                {
                    executor = this.flushExecutor;
                    if(executor == null)
                    {
                        this.flushExecutor = executor = newBackgroundWriter(this.name, "flush");
                    }
                }
            }
            return executor;
        }

        // Static, so the thread factory does not capture the index.
        private static ExecutorService newBackgroundWriter(
            final String name ,
            final String suffix
        )
        {
            return new ThreadPoolExecutor(
                0,
                1,
                1L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable ->
                {
                    final Thread thread = new Thread(runnable, "VectorIndex-Background-" + name + "-" + suffix);
                    thread.setDaemon(true);
                    return thread;
                }
            );
        }

        /**
         * Whether a background persist has anything to do, read without any lock: the live graph has
         * reached the flush threshold, an earlier flush failed and left a frozen graph behind, the files
         * must be replaced (a rejected load), or a consolidation is due.
         */
        private boolean isBackgroundFlushDue()
        {
            final SourceSet         set     = this.sources;
            final GraphIndexBuilder current = this.builder;
            if(set == null || current == null)
            {
                return false;
            }
            return this.forceFlush
                || this.consolidationRequired
                || !set.frozen().isEmpty()
                || graphOf(current).size(0) + this.pendingIndexingCount() >= this.configuration.flushThreshold()
                || this.isConsolidationDue(set, graphOf(current).size(0) > 0)
            ;
        }

        /**
         * Whether a background persist has a manifest to commit although it has no segment to write,
         * read without any lock: nothing is live, but changes were counted - deletions, entities added
         * without a vector - which change what the manifest records, or a merge published a segment the
         * manifest does not list yet. The background task manager asks
         * for such a persist only once enough changes are counted.
         */
        private boolean isManifestCommitDue()
        {
            final GraphIndexBuilder current = this.builder;
            return this.sources != null
                && current != null
                && graphOf(current).size(0) == 0
                && this.pendingIndexingCount() == 0
                && (this.countedChanges() > 0 || this.mergeUncommitted)
            ;
        }

        private int pendingIndexingCount()
        {
            final BackgroundTaskManager manager = this.backgroundTaskManager;
            return manager != null && this.configuration.eventualIndexing()
                ? manager.getPendingIndexingCount()
                : 0
            ;
        }

        /**
         * Whether a persist that may consolidate has to: a graph operation failed, which leaves the
         * graph without a change the store has, or the segments would exceed
         * {@link VectorIndexConfiguration#maxSegments()} because the background merges fell behind. A
         * segment written for another format or codebook is not a reason: a background merge rewrites
         * it (see {@link #scheduleMergeIfDue()}).
         *
         * @param set    the current sources
         * @param adding whether the persist is about to add a segment, from the live graph
         * @return whether a consolidation is due
         */
        private boolean isConsolidationDue(
            final SourceSet set   ,
            final boolean   adding
        )
        {
            if(this.consolidationRequired)
            {
                return true;
            }
            return set.segments().size() + set.frozen().size() + (adding ? 1 : 0) > this.configuration.maxSegments();
        }

        /**
         * Freezes the live graph for a flush, or consolidates, under the indexing lock, the parent
         * monitor and {@code builderLock.writeLock()}.
         * <p>
         * The indexing lock keeps the eventual-indexing worker from holding an operation it has polled
         * but not applied: every operation counted in the witnesses sampled here is then either applied
         * to the live graph already or still in the queue, from where it moves into the frozen graph.
         * The write lock keeps searches out for the swap, and the monitor keeps mutations out, so the
         * witnesses, the leave logs and the graph describe the same instant.
         * <p>
         * The monitor is taken before the write lock, which is only tried (see
         * {@link #lockBuilderUnderMonitor(boolean, boolean)}): a freeze that held the write lock while it waited for the
         * monitor would hold up every search for as long as a mutation keeps the monitor, since a
         * waiting writer keeps new readers out.
         *
         * @param kind    which persist this is
         * @param trained whether this persist trained a compressor that is not on disk yet
         * @param permit  the flush permit to try once the locks are held, kept for the returned plan;
         *                {@code null} if the caller holds it already
         * @return the plan of the flush to run, {@code null} if there is nothing to write, or
         *         {@link FlushPlan#DECLINED} if a background persist found too little to flush or another
         *         flush holds the permit
         */
        private FlushPlan freeze(
            final PersistKind kind   ,
            final boolean     trained,
            final Semaphore   permit
        )
        {
            // Read once: close() nulls the field after shutting the manager down.
            final BackgroundTaskManager manager = this.configuration.eventualIndexing()
                ? this.backgroundTaskManager
                : null
            ;
            if(manager != null)
            {
                manager.lockIndexing();
            }
            try
            {
                final boolean monitorHeld = Thread.holdsLock(this.parentMap());
                synchronized(this.parentMap())
                {
                    if(!this.lockBuilderUnderMonitor(monitorHeld, true))
                    {
                        return null;
                    }
                    if(permit != null && !permit.tryAcquire())
                    {
                        this.builderLock.writeLock().unlock();
                        return FlushPlan.DECLINED;
                    }
                    FlushPlan plan = null;
                    try
                    {
                        // Under the write lock, which close() sets it under: a close that completed while
                        // this persist trained must not be undone by the ensureIndexInitialized() below.
                        if(this.closed)
                        {
                            LOG.debug("Index '{}' was closed while this persist was preparing, skipping it",
                                this.name);
                            return null;
                        }
                        if(kind == PersistKind.SHUTDOWN && !this.isIndexPresent())
                        {
                            // Nothing to persist, and nothing to (re-)create: initializing here would
                            // start a background task manager in the middle of close().
                            return null;
                        }
                        this.ensureIndexInitialized();
                        final SourceSet set = this.sources;
                        if(set == null)
                        {
                            return null;
                        }
                        plan = this.freezeUnderLocks(kind, trained, set, manager, true);
                        return plan;
                    }
                    finally
                    {
                        this.builderLock.writeLock().unlock();
                        if(permit != null && (plan == null || plan == FlushPlan.DECLINED))
                        {
                            permit.release();
                        }
                    }
                }
            }
            finally
            {
                if(manager != null)
                {
                    manager.unlockIndexing();
                }
            }
        }

        /**
         * Takes {@code builderLock.writeLock()} while this thread holds the parent monitor, by trying it
         * and waiting on the monitor in between, which releases it. Blocking on the lock instead would keep
         * every mutation waiting for the monitor as long as the searches in flight run, and a search that
         * reaches the rebuild gate needs the monitor before it can finish. New searches hold back meanwhile
         * (see {@link #exclusiveWaiters}), so the ones in flight drain. The only way the write lock and the
         * monitor are taken together, by a freeze, an optimization and a close alike: a writer blocked on the
         * lock before it takes the monitor deadlocks with a search issued under the monitor, which waits for the
         * read lock behind it.
         * <p>
         * A caller that held the monitor already, in the middle of an operation on the parent map, is
         * never made to release it - waiting on the monitor would release every hold of it - so then the
         * lock is tried with short pauses instead.
         *
         * @param monitorHeld   whether this thread held the monitor before it took it for this
         * @param interruptible whether an interrupt gives up the wait (a freeze, as a shutdown persist that overruns
         *                      its timeout is interrupted; an optimization); a close waits on and keeps the
         *                      interrupt for its caller, as the blocking lock it replaces did, or a shutdown that
         *                      interrupted the closing thread would leave the index open
         * @return whether the lock was taken; {@code false} only if {@code interruptible} and the thread was
         *         interrupted
         */
        private boolean lockBuilderUnderMonitor(final boolean monitorHeld, final boolean interruptible)
        {
            final ReentrantReadWriteLock.WriteLock lock = this.builderLock.writeLock();
            if(lock.tryLock())
            {
                return true;
            }
            boolean interrupted = false;
            EXCLUSIVE_WAITERS.incrementAndGet(this);
            try
            {
                while(!lock.tryLock())
                {
                    try
                    {
                        if(monitorHeld)
                        {
                            LockSupport.parkNanos(100_000L);
                            if(Thread.interrupted())
                            {
                                throw new InterruptedException();
                            }
                        }
                        else
                        {
                            this.parentMap().wait(1L);
                        }
                    }
                    catch(final InterruptedException e)
                    {
                        interrupted = true;
                        if(interruptible)
                        {
                            LOG.warn("Vector index '{}' interrupted while waiting for the searches in flight", this.name);
                            return false;
                        }
                    }
                }
                return true;
            }
            finally
            {
                EXCLUSIVE_WAITERS.decrementAndGet(this);
                if(interrupted)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }

        /**
         * Decides what a persist does and freezes for it, under the locks of {@link #freeze}.
         *
         * @param kind           which persist this is
         * @param trained        whether this persist trained a compressor
         * @param set            the current sources
         * @param manager        the background task manager whose queued operations move into the
         *                       frozen graph, or {@code null}
         * @param mayConsolidate whether this thread may rebuild everything if a consolidation is due;
         *                       {@code false} on a mutating thread, which must not be held up for that
         *                       long, so the freeze is declined and left to the background persist
         * @return the plan to write, {@link FlushPlan#DECLINED}, or {@code null} if there is nothing to
         *         persist
         */
        private FlushPlan freezeUnderLocks(
            final PersistKind           kind          ,
            final boolean               trained       ,
            final SourceSet             set           ,
            final BackgroundTaskManager manager       ,
            final boolean               mayConsolidate
        )
        {
            final OwnerTable                 owners    = set.owners();
            final DiskIndexManager.MetaState witness   = this.currentMetaState();
            final DiskIndexManager.MetaState committed = this.committedWitness;
            final int                        pending   = manager != null ? manager.getPendingIndexingCount() : 0;
            final int                        liveNodes = this.index.size(0);
            final boolean                    leaves    = set.segments().stream()
                .anyMatch(segment -> owners.capture(segment.slot()).length() > segment.committedLogLength());
            final boolean                    consolidationDue = kind != PersistKind.SHUTDOWN
                && this.isConsolidationDue(set, liveNodes > 0 || pending > 0);

            final boolean clean = liveNodes == 0
                && pending == 0
                && !leaves
                && set.frozen().isEmpty()
                && committed != null
                && committed.matches(witness)
                && !trained
                && !this.compressionTrainedOutsidePersist
                && !consolidationDue
                && !this.forceFlush
                && !this.mergeUncommitted
            ;
            if(clean)
            {
                LOG.debug("No changes for '{}' since the last commit, skipping persist", this.name);
                this.changesPersisted(this.countedChanges());
                return null;
            }
            // A background persist writes the live graph only once it has reached the flush threshold,
            // so segments do not come out small. With nothing live it has no segment to write, only the
            // manifest - the deletions and the witnesses - which it commits whatever the threshold.
            if(kind == PersistKind.BACKGROUND
                && !consolidationDue
                && !this.forceFlush
                && set.frozen().isEmpty()
                && liveNodes + pending > 0
                && liveNodes + pending < this.configuration.flushThreshold())
            {
                return FlushPlan.DECLINED;
            }
            if(consolidationDue && !mayConsolidate)
            {
                return FlushPlan.DECLINED;
            }
            if(consolidationDue)
            {
                return this.consolidate(kind, set, witness, manager);
            }
            if(kind == PersistKind.SHUTDOWN && this.consolidationRequired)
            {
                // The graph lacks a change the store has. Writing it would make the next start accept
                // it, so nothing is committed: the next start finds the manifest stale and rebuilds.
                LOG.warn("Not persisting '{}' on shutdown: a graph operation failed, the next start rebuilds"
                    + " the index from the store", this.name);
                return null;
            }

            final List<HeapGeneration> frozen = new ArrayList<>(set.frozen());
            // Every persist freezes the live graph too, a background one retrying a failed flush
            // included: the witnesses it commits count the mutations the live graph holds, so a segment
            // must hold them as well.
            final boolean freezeLive = liveNodes > 0 || pending > 0;
            if(freezeLive)
            {
                final OwnerTable.Lease newLease = owners.allocate();
                if(newLease == null)
                {
                    if(kind == PersistKind.SHUTDOWN)
                    {
                        LOG.warn("Not persisting '{}' on shutdown: no source slot is free", this.name);
                        return null;
                    }
                    if(!mayConsolidate)
                    {
                        return FlushPlan.DECLINED;
                    }
                    return this.consolidate(kind, set, witness, manager);
                }
                frozen.add(this.freezeLiveGraph(newLease, manager));
            }

            final SourceSet next = set.with(set.segments(), frozen);
            this.sources = next;
            set.release();
            this.forceFlush = false;
            return this.newFlushPlan(kind, next, witness, false);
        }

        /**
         * Freezes the live graph and installs a fresh one under {@code newLease}. Under the locks of
         * {@link #freeze}.
         */
        private HeapGeneration freezeLiveGraph(
            final OwnerTable.Lease      newLease,
            final BackgroundTaskManager manager
        )
        {
            final List<IndexingOperation> moved = manager != null
                ? manager.takePendingOps()
                : List.of()
            ;
            final GenerationVectorValues vectors = this.liveVectorValues;
            vectors.freeze(this.movedVectorsOf(moved));
            final HeapGeneration frozen = HeapGeneration.Freeze(
                this.liveLease, this.builder, vectors, null, moved, this.name
            );
            // The live pool creates its searchers over whatever graph is live when a thread first asks,
            // so it cannot serve the frozen graph; no search runs under the write lock to be using it.
            this.closeSearcherPools();
            this.initializeInMemoryBuilder();
            this.initializeSearcherPool();
            this.liveLease = newLease;
            this.sources.owners().liveSlot(newLease.slot());
            LOG.info("Froze the in-memory graph of '{}' with {} nodes and {} queued operations for a flush",
                this.name, frozen.nodeCount(), moved.size());
            return frozen;
        }

        /**
         * Copies the vectors of the ordinals the moved operations name from the vector file, under the
         * locks of the freeze: a remove or an update after the freeze changes the file, but must not change
         * what the segment holds of an entity the witnesses sampled here count. O(pending).
         */
        private Map<Integer, float[]> movedVectorsOf(final List<IndexingOperation> moved)
        {
            final Map<Integer, float[]> vectors = new HashMap<>();
            final VectorFile            file    = this.vectorFile;
            for(final IndexingOperation op : moved)
            {
                switch(op)
                {
                    case IndexingOperation.Add      add    -> vectors.put(add.ordinal(), file.read(add.ordinal()));
                    case IndexingOperation.Update   update ->
                        vectors.put(update.ordinal(), file.read(update.ordinal()));
                    case IndexingOperation.Remove   remove -> vectors.put(remove.ordinal(), null);
                    case IndexingOperation.BatchAdd batch  ->
                    {
                        for(final int ordinal : batch.ordinals())
                        {
                            vectors.put(ordinal, file.read(ordinal));
                        }
                    }
                }
            }
            return vectors;
        }

        /**
         * Rebuilds the whole index from the store into one new generation that replaces every source,
         * under the locks of {@link #freeze}. The rare fallback for what flushing alone cannot fix (see
         * {@link #isConsolidationDue}); it holds the locks for the rebuild, in parallel from the vector
         * file like the load-path rebuild, but writes outside them like any flush.
         * <p>
         * The queued eventual-indexing operations are dropped: they are enqueued after the vector file was
         * written, so the rebuild holds their effect already. A rebuild that fails publishes nothing
         * and leaves every source as it was.
         */
        private FlushPlan consolidate(
            final PersistKind                kind   ,
            final SourceSet                  set    ,
            final DiskIndexManager.MetaState witness,
            final BackgroundTaskManager      manager
        )
        {
            LOG.info("Consolidating the on-disk index of '{}' ({} segments, {} frozen graphs) into one segment",
                this.name, set.segments().size(), set.frozen().size());
            final long startNanos = System.nanoTime();

            final RebuildNodes      nodes  = this.collectRebuildNodes(false);
            final OwnerTable        owners = OwnerTable.New();
            final OwnerTable.Lease  lease  = owners.allocate();
            // Scores from the vector file for the inserts, the cleanup and the write, so no worker needs
            // the monitor this thread holds. Frozen from the start: it is written like a frozen graph.
            final GenerationVectorValues vectors = new GenerationVectorValues(this.vectorFile, this.vectorTypeSupport);
            vectors.freeze(Map.of());
            final GraphIndexBuilder target = this.newGraphIndexBuilder(
                BuildScoreProvider.randomAccessScoreProvider(this.nullSafe(vectors), this.jvectorSimilarityFunction())
            );

            boolean published = false;
            try
            {
                final Runnable workerHook = this.rebuildWorkerTestHook;
                final int      seeded     = seedIfEmpty(target, nodes.count(), index -> this.insertFromFile(target, nodes.ordinals()[index]));
                ParallelGraphInsertion.forEach(
                    nodes.count() - seeded,
                    index ->
                    {
                        if(workerHook != null)
                        {
                            workerHook.run();
                        }
                        this.insertFromFile(target, nodes.ordinals()[index + seeded]);
                    },
                    this.rebuildThreadNamePrefix()
                );
                // A builder of this flush only, so no other thread touches it.
                GraphReachability.repair(target, nodes.ordinals(), nodes.count(), this::vectorOf, this.name);
                for(final int ordinal : nodes.ordinals())
                {
                    owners.owners().set(ordinal, lease.slot());
                }

                if(manager != null)
                {
                    manager.takePendingOps();
                }
                final HeapGeneration consolidated = HeapGeneration.Freeze(
                    lease, target, vectors, null, List.of(), this.name
                );

                // The old live graph is in no set, so nothing else can reach it: the write lock keeps
                // searches out, the monitor mutations.
                this.closeSearcherPools();
                this.closeLiveBuilder();
                this.initializeInMemoryBuilder();
                this.initializeSearcherPool();
                final OwnerTable.Lease previousLive = this.liveLease;
                this.liveLease = owners.allocate();
                owners.liveSlot(this.liveLease.slot());

                final SourceSet next = new SourceSet(List.of(), List.of(consolidated), owners);
                this.sources = next;
                published = true;
                set.release();
                if(previousLive != null)
                {
                    previousLive.release();
                }

                this.consolidationRequired = false;
                this.forceFlush            = false;
                this.consolidationCount++;
                LOG.info("Rebuilt the graph of '{}' from {} vectors for a consolidation in {} ms",
                    this.name, nodes.count(), (System.nanoTime() - startNanos) / 1_000_000L);
                return this.newFlushPlan(kind, next, witness, true);
            }
            finally
            {
                if(!published)
                {
                    this.closeUnpublishedBuilder(target, null);
                    lease.release();
                }
            }
        }

        private void closeLiveBuilder()
        {
            final GraphIndexBuilder current = this.builder;
            if(current != null)
            {
                try
                {
                    current.close();
                }
                catch(final IOException e)
                {
                    LOG.warn("Error closing the replaced in-memory graph builder of '{}': {}",
                        this.name, e.getMessage());
                }
            }
            final OnHeapGraphIndex graph = this.index;
            if(graph != null)
            {
                graph.close();
            }
        }

        /**
         * Builds the plan of a flush over every source of {@code set}, holding each of them for the
         * flush, with the leave logs as they are now. Under the parent monitor.
         */
        private FlushPlan newFlushPlan(
            final PersistKind                kind        ,
            final SourceSet                  set         ,
            final DiskIndexManager.MetaState witness     ,
            final boolean                    consolidated
        )
        {
            final Map<Integer, OwnerTable.LeaveLog> logs = new HashMap<>();
            for(final IndexSource source : set.sources())
            {
                logs.put(source.slot(), set.owners().capture(source.slot()));
            }
            final PQCompressionManager  pq  = this.pqManager ;
            final NVQCompressionManager nvq = this.nvqManager;
            final FlushPlan plan = new FlushPlan(
                kind,
                set.segments(),
                set.frozen(),
                logs,
                witness,
                pq  != null && pq.isTrained()  ? pq.getPQ()   : null,
                nvq != null && nvq.isTrained() ? nvq.getNVQ() : null,
                this.sourceEpoch,
                consolidated,
                this.countedChanges()
            );
            plan.retain();
            return plan;
        }

        /**
         * Runs a flush planned by {@link #freeze}, with no lock held except, briefly,
         * {@link #commitLock} around the commit and the publication.
         * <ol>
         *   <li>Applies the eventual-indexing operations moved into each frozen graph: they are counted
         *       in the witnesses, so the segment must hold them.</li>
         *   <li>Runs {@code cleanup()} on each frozen graph, scoring from the generation's vectors
         *       ({@link GenerationVectorValues}).</li>
         *   <li>Writes each as a new segment and opens it, so a segment that cannot be read back is
         *       never committed.</li>
         *   <li>Commits a manifest listing the existing segments with their deletions as captured at the
         *       freeze, plus the new ones, and publishes the new segments in place of the frozen
         *       graphs.</li>
         * </ol>
         * A failure commits nothing and leaves the frozen graphs searchable; the next persist writes
         * them again. A flush whose index was cleared or closed meanwhile commits nothing either, and
         * deletes what it wrote.
         *
         * @param plan the plan, whose sources it releases when done
         * @throws IORuntimeException if writing or committing fails
         * @throws FlushAbandonedException if the index was cleared or closed meanwhile
         */
        private void runFlush(final FlushPlan plan)
        {
            final long             startNanos = System.nanoTime();
            final List<Written>    written    = new ArrayList<>();
            // Read once: removeAll() replaces the manager without waiting for a flush in flight, which
            // then commits nothing (see checkFlushStillWanted) but must still clean up after itself
            // with the manager it wrote through.
            final DiskIndexManager disk       = this.diskManager;
            boolean                published  = false;
            boolean                released   = false;
            try
            {
                if(disk == null)
                {
                    throw new FlushAbandonedException("the index was cleared or closed");
                }
                this.reachedFlushStage(FlushStage.FROZEN);
                for(final HeapGeneration frozen : plan.frozen())
                {
                    this.checkFlushStillWanted(plan);
                    this.applyMovedOps(frozen);
                }
                this.reachedFlushStage(FlushStage.OPS_APPLIED);
                for(final HeapGeneration frozen : plan.frozen())
                {
                    this.checkFlushStillWanted(plan);
                    this.prepareForWrite(frozen, plan.kind());
                }
                this.reachedFlushStage(FlushStage.CLEANED);

                for(final HeapGeneration frozen : plan.frozen())
                {
                    if(frozen.nodeCount() == 0)
                    {
                        // Everything in it was removed before the cleanup purged it; nothing to write.
                        continue;
                    }
                    this.checkFlushStillWanted(plan);
                    final Written entry = new Written(frozen, this.segmentSequences.reserve(
                        this.indexDirectoryForDisk(), this.name
                    ));
                    written.add(entry);
                    final SegmentManifest.Segment segment = disk.writeSegment(
                        frozen.graph(), this.flushVectors(frozen), plan.pq(), plan.nvq(), entry.sequence
                    );
                    entry.segment = disk.openSegment(segment, frozen.lease(), false, 0);
                    this.reachedFlushStage(FlushStage.SEGMENT_WRITTEN);
                }

                final Commit commit = this.planCommit(plan, written);
                synchronized(this.commitLock)
                {
                    this.checkFlushStillWanted(plan);
                    try
                    {
                        if(commit.listed().isEmpty())
                        {
                            // Nothing live in any source: there is nothing to write, and the files of an
                            // earlier, non-empty state describe content that no longer exists.
                            disk.deleteIndexFiles();
                        }
                        else
                        {
                            disk.commit(plan.witness(), commit.listed());
                        }
                    }
                    catch(final DiskIndexManager.CommitIncompleteException e)
                    {
                        // The new manifest is in place, only its durability is not proven. It names the
                        // new segments, so they are published like a completed commit's, and the
                        // failure is reported all the same.
                        this.publishFlush(plan, commit);
                        published = true;
                        throw e;
                    }
                    this.publishFlush(plan, commit);
                    published = true;
                }
                // A new segment with nothing live is not listed; closed first, so its files are not
                // protected as those of an open segment from the cleanup that follows.
                for(final Written entry : written)
                {
                    if(!entry.listed && entry.segment != null)
                    {
                        entry.segment.close();
                        entry.segment = null;
                    }
                }
                this.changesPersisted(plan.counted());
                // The plan's holds go before the cleanup too: a segment this flush dropped is open until
                // its last holder lets go, and the files of an open segment are protected.
                plan.release();
                released = true;
                disk.reapUnlistedFiles(commit.listedSequences());
                this.reachedFlushStage(FlushStage.PUBLISHED);
                this.scheduleMergeIfDue();
                // The pre-flush: forcing the vector file here, outside every lock, keeps the work of a
                // checkpoint off the mutating threads.
                this.checkpointVectorsIfDue();
                LOG.info("Flushed '{}': {} new segments, {} segments listed, in {} ms",
                    this.name, commit.newSegments().size(), commit.listed().size(),
                    (System.nanoTime() - startNanos) / 1_000_000L);
            }
            catch(final IOException e)
            {
                throw new IORuntimeException(e);
            }
            finally
            {
                try
                {
                    for(final Written entry : written)
                    {
                        this.cleanUpWritten(disk, entry, published);
                    }
                }
                finally
                {
                    if(!released)
                    {
                        plan.release();
                    }
                }
            }
        }

        /**
         * Lets go of a segment a flush wrote: closes and deletes it if no manifest names it -
         * unpublished, or published with nothing live in it - and ends the protection of its sequence
         * as one being written. Each step is independent, so a failure of one cannot keep the sequence
         * protected for the rest of the session, nor skip the entries after it.
         */
        private void cleanUpWritten(
            final DiskIndexManager disk     ,
            final Written          entry    ,
            final boolean          published
        )
        {
            try
            {
                if(!published || !entry.listed)
                {
                    if(entry.segment != null)
                    {
                        entry.segment.close();
                    }
                    // The cleanup after the commit kept its files, as those of a sequence still being
                    // written, so nothing else would delete them before the next flush or start.
                    disk.deleteSegmentFiles(entry.sequence);
                }
            }
            catch(final RuntimeException e)
            {
                LOG.warn("Could not delete the files of segment {} of '{}', the next start removes them",
                    entry.sequence, this.name, e);
            }
            finally
            {
                this.segmentSequences.written(entry.sequence);
            }
        }

        /**
         * Runs a checkpoint of the vector file if one is due, with no lock held, logging a failure: it only
         * leaves more to look at after a crash, and the next checkpoint tries again.
         */
        private void checkpointVectorsIfDue()
        {
            final VectorFile file = this.vectorFile;
            if(file == null || !file.isCheckpointDue())
            {
                return;
            }
            try
            {
                file.checkpoint();
            }
            catch(final RuntimeException e)
            {
                LOG.warn("Checkpoint of the vectors of '{}' failed: {}", this.name, e.getMessage(), e);
            }
        }

        /**
         * A segment a flush has written for one frozen graph, and what became of it.
         */
        private static final class Written
        {
            final HeapGeneration frozen  ;
            final long           sequence;
            DiskSegment          segment ;
            boolean              listed  ;

            Written(
                final HeapGeneration frozen  ,
                final long           sequence
            )
            {
                this.frozen   = frozen  ;
                this.sequence = sequence;
            }
        }

        /**
         * What a flush commits: the manifest entries, the new deletions of the existing segments, and
         * which segments it drops because nothing in them is live any more.
         */
        private record Commit(
            List<SegmentManifest.Segment> listed           ,
            Set<Long>                     listedSequences  ,
            List<DiskSegment>             keptSegments     ,
            List<int[]>                   keptDeletions    ,
            List<DiskSegment>             newSegments      ,
            List<int[]>                   newDeletions     ,
            List<Integer>                 newLogLengths
        )
        {
        }

        /**
         * Works out the manifest of a flush. Each segment's deletions are those last committed plus the
         * entities its slot's leave log recorded up to the freeze: exactly the deletions at the instant
         * the witnesses were sampled, as a restart that accepts the witnesses requires.
         */
        private Commit planCommit(
            final FlushPlan     plan   ,
            final List<Written> written
        )
        {
            final List<SegmentManifest.Segment> listed          = new ArrayList<>();
            final Set<Long>                     listedSequences = new HashSet<>();
            final List<DiskSegment>             keptSegments    = new ArrayList<>();
            final List<int[]>                   keptDeletions   = new ArrayList<>();
            final List<DiskSegment>             newSegments     = new ArrayList<>();
            final List<int[]>                   newDeletions    = new ArrayList<>();
            final List<Integer>                 newLogLengths   = new ArrayList<>();

            for(final DiskSegment segment : plan.segments())
            {
                final OwnerTable.LeaveLog log     = plan.logs().get(segment.slot());
                final int[]               deleted = union(
                    segment.committedDeletedLocals(),
                    segment.toLocal(log.sortedFrom(segment.committedLogLength()))
                );
                if(deleted.length == segment.nodeCount())
                {
                    // Nothing in it is live any more. Listed, it would still be traversed by every
                    // search, only to be rejected node by node, and count towards the segment cap.
                    continue;
                }
                listed.add(new SegmentManifest.Segment(
                    segment.sequence(), segment.nodeCount(), segment.format(), segment.pqVectors() != null, deleted
                ));
                listedSequences.add(segment.sequence());
                keptSegments.add(segment);
                keptDeletions.add(deleted);
            }
            for(final Written entry : written)
            {
                final DiskSegment         segment = entry.segment;
                final OwnerTable.LeaveLog log     = plan.logs().get(segment.slot());
                final int[]               deleted = segment.toLocal(log.sortedFrom(0));
                if(deleted.length == segment.nodeCount())
                {
                    continue;
                }
                listed.add(new SegmentManifest.Segment(
                    segment.sequence(), segment.nodeCount(), segment.format(), segment.pqVectors() != null, deleted
                ));
                listedSequences.add(segment.sequence());
                entry.listed = true;
                newSegments.add(segment);
                newDeletions.add(deleted);
                newLogLengths.add(log.length());
            }
            return new Commit(
                listed, listedSequences, keptSegments, keptDeletions, newSegments, newDeletions, newLogLengths
            );
        }

        /**
         * Publishes what a flush committed: the kept segments get their new deletions, the new segments
         * replace the frozen graphs they were written from, and the dropped segments leave the set.
         * Under {@link #commitLock}, which also excludes the retirement of the sources by removeAll and
         * close.
         */
        private void publishFlush(
            final FlushPlan plan  ,
            final Commit    commit
        )
        {
            for(int i = 0; i < commit.keptSegments().size(); i++)
            {
                final DiskSegment segment = commit.keptSegments().get(i);
                segment.committed(commit.keptDeletions().get(i), plan.logs().get(segment.slot()).length());
            }
            for(int i = 0; i < commit.newSegments().size(); i++)
            {
                commit.newSegments().get(i).committed(commit.newDeletions().get(i), commit.newLogLengths().get(i));
            }

            final SourceSet current = this.sources;
            if(current != null)
            {
                final Set<DiskSegment> flushedSegments = new HashSet<>(plan.segments());
                final List<DiskSegment> segments = new ArrayList<>();
                for(final DiskSegment segment : current.segments())
                {
                    // A segment of this flush stays only if it is listed; one that arrived since cannot
                    // exist, the flush permit excludes it, but it would be kept as it is.
                    if(!flushedSegments.contains(segment) || commit.keptSegments().contains(segment))
                    {
                        segments.add(segment);
                    }
                }
                segments.addAll(commit.newSegments());
                final List<HeapGeneration> frozen = new ArrayList<>(current.frozen());
                frozen.removeAll(plan.frozen());

                this.sources = current.with(segments, frozen);
                current.release();
            }
            this.committedWitness                 = plan.witness();
            this.compressionTrainedOutsidePersist = false;
            // Every merge published so far is in the set the plan was taken from: merges publish under the
            // flush permit, which this flush has held since its freeze.
            this.mergeUncommitted                 = false;
            this.flushCount++;
        }

        /**
         * Returns the union of two strictly ascending arrays, strictly ascending.
         */
        private static int[] union(
            final int[] left ,
            final int[] right
        )
        {
            if(right.length == 0)
            {
                return left;
            }
            final int[] merged = new int[left.length + right.length];
            int i = 0;
            int j = 0;
            int n = 0;
            while(i < left.length || j < right.length)
            {
                final int next;
                if(j >= right.length || i < left.length && left[i] < right[j])
                {
                    next = left[i++];
                }
                else if(i >= left.length || right[j] < left[i])
                {
                    next = right[j++];
                }
                else
                {
                    next = left[i++];
                    j++;
                }
                merged[n++] = next;
            }
            return Arrays.copyOf(merged, n);
        }

        ///////////////////////////////////////////////////////////////////////////
        // background segment merge //
        /////////////////////////////

        /**
         * Schedules a background merge on the merge thread if the policy finds one due (see
         * {@link SegmentMergePolicy}). Cheap enough to call after every flush: it looks at the published
         * segments only, takes no lock and does no I/O.
         * <p>
         * Called once a flush or a merge has published, and once the vectors of an index that loaded its
         * segments are recovered, which a merge reads from.
         */
        private void scheduleMergeIfDue()
        {
            final AtomicBoolean scheduled = this.mergeScheduled;
            if(scheduled == null || !this.isMergeDue())
            {
                return;
            }
            // Set before the compare-and-set: one that fails then came before the running run cleared its
            // flag, which it does before it reads this one, so the request cannot fall between the two.
            this.mergeRequested = true;
            if(!scheduled.compareAndSet(false, true))
            {
                return;
            }
            try
            {
                this.mergeExecutor().execute(this::runMerges);
            }
            catch(final RejectedExecutionException e)
            {
                // Shut down by a close meanwhile.
                scheduled.set(false);
            }
        }

        private boolean isMergeDue()
        {
            return this.isMergeDue(false);
        }

        private boolean isRewriteDue()
        {
            return this.isMergeDue(true);
        }

        private boolean isMergeDue(final boolean rewritesOnly)
        {
            if(!this.mayMerge())
            {
                return false;
            }
            final SourceSet set = this.acquireSources();
            if(set == null)
            {
                return false;
            }
            try
            {
                return !this.selectMerge(set, rewritesOnly).isEmpty();
            }
            finally
            {
                set.release();
            }
        }

        private boolean mayMerge()
        {
            return this.configuration.onDisk()
                && !this.mergesSuspended
                && !this.mergeAbortRequested
                && !this.closeRequested
                && !this.closed
                && this.vectorsRecovered
            ;
        }

        /**
         * Runs merges on the merge thread for as long as the policy finds one due, holding the merge
         * permit, which a close waits for and an explicit persist rewriting segments holds. A merge that
         * fails or is abandoned ends the run; the next flush asks again.
         */
        private void runMerges()
        {
            boolean idle = false;
            try
            {
                final Semaphore permit = this.mergePermit;
                // Waited for: an explicit persist rewriting segments holds it for a while, and the merge it
                // made due would be lost otherwise. A close sets the flag before it takes the permit, and
                // this run finds it set once it gets the permit after the close.
                if(this.mergeAbortRequested)
                {
                    return;
                }
                permit.acquireUninterruptibly();
                try
                {
                    // Every request until now is served by the selection this run makes.
                    this.mergeRequested = false;
                    MergeOutcome outcome;
                    do
                    {
                        outcome = this.mergeOnce(false);
                    }
                    while(outcome == MergeOutcome.MERGED);
                    idle = outcome == MergeOutcome.NOTHING_DUE;
                }
                finally
                {
                    permit.release();
                }
            }
            catch(final RuntimeException e)
            {
                LOG.error("Background merge of '{}' failed, the next flush tries again", this.name, e);
            }
            catch(final Error e)
            {
                LOG.error("Background merge of '{}' failed", this.name, e);
                throw e;
            }
            finally
            {
                this.mergeScheduled.set(false);
            }
            if(idle || this.mergeRequested)
            {
                // A flush that published after the last selection found this run scheduled and asked no
                // one: look once more. Also after a run that was abandoned or failed, but only when such a
                // flush asked, so a condition that keeps abandoning a merge does not keep rescheduling it.
                this.mergeRequested = false;
                this.scheduleMergeIfDue();
            }
        }

        /**
         * Selects, builds, writes and publishes one merge, under the merge permit.
         *
         * @param rewritesOnly whether to merge only segments that no longer match the index
         */
        private MergeOutcome mergeOnce(final boolean rewritesOnly)
        {
            if(!this.mayMerge())
            {
                return MergeOutcome.ABANDONED;
            }
            this.trainCompressorsForMerge();

            final MergeInput input = this.prepareMerge(rewritesOnly);
            if(input == null)
            {
                return MergeOutcome.NOTHING_DUE;
            }
            try
            {
                return this.runMerge(input);
            }
            catch(final MergeAbandonedException e)
            {
                LOG.debug("Merge of '{}' abandoned: {}", this.name, e.getMessage());
                return MergeOutcome.ABANDONED;
            }
            finally
            {
                input.release();
            }
        }

        /**
         * Trains a compressor that is due before a merge writes with it, under the flush permit like the
         * training of a persist, so no flush writes while the codebook changes. A loaded index whose
         * segments were all flagged has no quantizer yet, and its rewritten segments would otherwise be
         * written without one. The next persist writes the codebook to the segments it flushes.
         */
        private void trainCompressorsForMerge()
        {
            if(!this.isCompressionTrainingPending())
            {
                return;
            }
            final Semaphore permit = this.flushPermit;
            permit.acquireUninterruptibly();
            try
            {
                if(this.mayMerge() && this.trainCompressorsBeforeLocking())
                {
                    this.compressionTrainedOutsidePersist = true;
                }
            }
            finally
            {
                permit.release();
            }
        }

        /**
         * Picks the segments to merge from the published set and collects the entities they own, which is
         * what the merged segment holds. Returns {@code null} if no merge is due.
         * <p>
         * The owners are read without the monitor: an entity only ever leaves a segment's slot, so one
         * that leaves while they are read is merged needlessly and simply not moved later, and every entity
         * the segments still own when the owners are moved is in the merged segment.
         */
        private MergeInput prepareMerge(final boolean rewritesOnly)
        {
            // Before the set: a removeAll in between then shows as a changed epoch at the publication.
            final long      epoch = this.sourceEpoch;
            final SourceSet set   = this.acquireSources();
            if(set == null)
            {
                return null;
            }
            try
            {
                final List<DiskSegment> selected = this.selectMerge(set, rewritesOnly);
                final DiskIndexManager  disk     = this.diskManager;
                final VectorFile        file     = this.vectorFile;
                if(selected.isEmpty() || disk == null || file == null)
                {
                    return null;
                }
                final PQCompressionManager  pq  = this.pqManager ;
                final NVQCompressionManager nvq = this.nvqManager;
                selected.forEach(DiskSegment::retain);
                return new MergeInput(
                    selected,
                    set.owners(),
                    epoch,
                    disk,
                    file,
                    pq  != null && pq.isTrained()  ? pq.getPQ()   : null,
                    nvq != null && nvq.isTrained() ? nvq.getNVQ() : null,
                    ownedEntities(selected, set.owners().owners())
                );
            }
            finally
            {
                set.release();
            }
        }

        /**
         * Asks the merge policy which segments of {@code set} to merge next. The caller holds the set.
         *
         * @param rewritesOnly whether to consider only segments that no longer match the index
         */
        private List<DiskSegment> selectMerge(
            final SourceSet set         ,
            final boolean   rewritesOnly
        )
        {
            final ProductQuantization                codebook   = this.currentCodebook();
            final List<SegmentMergePolicy.Candidate> candidates = new ArrayList<>(set.segments().size());
            for(final DiskSegment segment : set.segments())
            {
                final boolean rewrite = needsRewrite(segment, codebook);
                if(rewrite || !rewritesOnly)
                {
                    // The live nodes as last committed: an estimate, which leaves out the deletions since.
                    // Exact would take the leave logs, which only the parent monitor guards, and a flush
                    // must not wait for the monitor to ask, since an ingest can hold it for seconds.
                    candidates.add(new SegmentMergePolicy.Candidate(
                        segment.sequence(),
                        (long)segment.nodeCount() - segment.committedDeletedLocals().length,
                        rewrite
                    ));
                }
            }
            final List<Long> sequences = SegmentMergePolicy.New(
                this.configuration.flushThreshold(),
                this.configuration.segmentMergeFactor(),
                this.configuration.maxSegments()
            ).select(candidates, set.frozen().size() + 1);
            return set.segments().stream()
                .filter(segment -> sequences.contains(segment.sequence()))
                .toList();
        }

        /**
         * The codebook every segment's codes have to be encoded with, or {@code null} if the index has
         * none or its format uses none.
         */
        private ProductQuantization currentCodebook()
        {
            final PQCompressionManager pq = this.pqManager;
            return pq != null && pq.isTrained() && GraphFormat.of(this.configuration).usesPq()
                ? pq.getPQ()
                : null
            ;
        }

        /**
         * Whether a segment has to be rewritten: it was written for another format, or its codes were
         * encoded with another codebook than the index's, or with none, as before the codebook was
         * trained. Such a segment is searched exactly and contributes nothing to quantized scoring.
         */
        private static boolean needsRewrite(
            final DiskSegment         segment ,
            final ProductQuantization codebook
        )
        {
            return segment.flaggedForMerge()
                || codebook != null && !codebook.equals(DiskIndexManager.codebookOf(segment));
        }

        /**
         * The entities the given segments own, ascending. Sized by an exact count first, so a mostly
         * deleted segment does not cost its node count.
         */
        private static int[] ownedEntities(
            final List<DiskSegment> segments,
            final SegmentOwners     owners
        )
        {
            int count = 0;
            for(final DiskSegment segment : segments)
            {
                for(final int global : segment.localToGlobal())
                {
                    if(owners.owner(global) == segment.slot())
                    {
                        count++;
                    }
                }
            }
            final int[] owned = new int[count];
            int         next  = 0;
            for(final DiskSegment segment : segments)
            {
                for(final int global : segment.localToGlobal())
                {
                    // Bounded: an entity that left since the count is skipped, none can arrive.
                    if(next < owned.length && owners.owner(global) == segment.slot())
                    {
                        owned[next++] = global;
                    }
                }
            }
            final int[] collected = next == owned.length ? owned : Arrays.copyOf(owned, next);
            // Each segment's array ascends, and no entity is owned by two segments.
            Arrays.sort(collected);
            return collected;
        }

        /**
         * Builds the merged graph from the vector file, in parallel and with no lock held, writes it as a
         * new segment and publishes it. A merge abandoned or failed before its publication deletes what it
         * wrote and leaves every source as it was.
         */
        private MergeOutcome runMerge(final MergeInput input)
        {
            final long startNanos = System.nanoTime();
            LOG.info("Merging {} segments of '{}' with {} live nodes into one",
                input.segments().size(), this.name, input.entities().length);
            this.reachedMergeStage(MergeStage.SELECTED);

            final VectorFile               file    = input.file();
            final VectorTypeSupport        types   = this.vectorTypeSupport;
            // Read straight from the file: an entity mutated during the merge is no longer owned by the
            // segments merged, so the owner move below skips it, whatever vector it was merged with.
            final RandomAccessVectorValues vectors = this.nullSafe(new MappedVectorValues(file, types));
            final GraphIndexBuilder        target  = this.newGraphIndexBuilder(
                BuildScoreProvider.randomAccessScoreProvider(vectors, this.jvectorSimilarityFunction())
            );
            long        sequence  = -1L;
            DiskSegment merged    = null;
            boolean     published = false;
            try
            {
                final int[] entities = input.entities();
                final int   seeded   = seedIfEmpty(target, entities.length, index ->
                {
                    final float[] vector = file.read(entities[index]);
                    if(vector != null)
                    {
                        target.addGraphNode(entities[index], types.createFloatVector(vector));
                    }
                });
                ParallelGraphInsertion.forEach(
                    entities.length - seeded,
                    offset ->
                    {
                        if(this.mergeAbortRequested)
                        {
                            return;
                        }
                        final int     index  = offset + seeded;
                        final float[] vector = file.read(entities[index]);
                        if(vector != null)
                        {
                            target.addGraphNode(entities[index], types.createFloatVector(vector));
                        }
                    },
                    "vector-merge-" + this.name,
                    // Half the cores: a merge runs alongside the ingest it merges for, and must not starve it.
                    Math.max(1, ParallelGraphInsertion.parallelism() / 2)
                );
                this.checkMergeStillWanted(input);
                // A builder of this merge only, so no other thread touches it. Every node is a candidate, the
                // ones the seed copied included, so an orphan of a source segment does not carry over. Before
                // the cleanup, which refines the upper layers only and would leave an unreachable node of layer 0
                // as it is; a re-insert after it could overflow the degree the segment writer enforces.
                final int[] nodes = graphOf(target).nodeStream(0).toArray();
                GraphReachability.repair(target, nodes, nodes.length, ordinal ->
                {
                    final float[] vector = file.read(ordinal);
                    return vector == null ? null : types.createFloatVector(vector);
                }, this.name);
                final OnHeapGraphIndex graph = graphOf(target);
                if(graph.size(0) == 0)
                {
                    // Everything was removed while the graph was built.
                    return MergeOutcome.ABANDONED;
                }
                target.cleanup();
                this.reachedMergeStage(MergeStage.BUILT);

                this.checkMergeStillWanted(input);
                sequence = this.segmentSequences.reserve(this.indexDirectoryForDisk(), this.name);
                final SegmentManifest.Segment entry = input.disk().writeSegment(
                    graph, vectors, input.pq(), input.nvq(), sequence
                );
                merged = this.openMergedSegment(input, entry);
                if(merged == null)
                {
                    return MergeOutcome.ABANDONED;
                }
                this.reachedMergeStage(MergeStage.WRITTEN);

                final DiskSegment joining = merged;
                // From here on the publication owns the segment.
                merged = null;
                published = this.publishMerge(input, joining);
                if(!published)
                {
                    return MergeOutcome.ABANDONED;
                }
                LOG.info("Merged {} segments of '{}' into segment {} with {} nodes in {} ms",
                    input.segments().size(), this.name, joining.sequence(), joining.nodeCount(),
                    (System.nanoTime() - startNanos) / 1_000_000L);
                this.reachedMergeStage(MergeStage.PUBLISHED);
                return MergeOutcome.MERGED;
            }
            catch(final IOException e)
            {
                throw new IORuntimeException(e);
            }
            finally
            {
                // Never published itself: the merged segment is read back from its files.
                this.closeUnpublishedBuilder(target, null);
                if(merged != null)
                {
                    merged.close();
                }
                if(sequence >= 0L)
                {
                    if(!published)
                    {
                        // No manifest lists it, and none will. A set of a cleared index that still holds the
                        // segment keeps reading the open files; their names go now.
                        input.disk().deleteSegmentFiles(sequence);
                    }
                    this.segmentSequences.written(sequence);
                }
            }
        }

        /**
         * Opens the merged segment in a fresh slot of the merge's owner table. Returns {@code null} if
         * the index was cleared meanwhile or every slot is taken.
         */
        private DiskSegment openMergedSegment(
            final MergeInput              input,
            final SegmentManifest.Segment entry
        ) throws IOException
        {
            final OwnerTable.Lease lease;
            synchronized(this.parentMap())
            {
                // Slots are taken under the monitor, see OwnerTable.
                if(input.epoch() != this.sourceEpoch)
                {
                    return null;
                }
                lease = input.owners().allocate();
            }
            if(lease == null)
            {
                LOG.warn("Merge of '{}' found no free source slot, the segments stay as they are", this.name);
                return null;
            }
            try
            {
                return input.disk().openSegment(entry, lease, false, 0);
            }
            finally
            {
                // The segment holds the lease now, or nothing does.
                lease.release();
            }
        }

        /**
         * Publishes a merged segment, under the flush permit, so no flush captures the sources halfway:
         * <ol>
         *   <li>The merged segment joins the published set, owning nothing yet, so invisible.</li>
         *   <li>Takes and releases the builder write lock, which waits for every search in flight. A
         *       search holds the set it started with for its whole run, and an older set lacks the merged
         *       segment, so it would miss the entities moved to it.</li>
         *   <li>Moves the owners from the merged segments to the new one, entity by entity by
         *       compare-and-set, without the parent monitor, which a sync ingest can hold for seconds
         *       while this holds the flush permit. An entity the merged segments no longer own was
         *       mutated during the merge and keeps its newer owner; its node in the merged segment is
         *       deleted from the start.</li>
         *   <li>The merged segments, which own nothing any more, leave the set, and close once the last
         *       search holding them is done.</li>
         * </ol>
         * The manifest on disk still lists the merged segments and not the new one, which is consistent
         * with its witnesses; the next flush commits the new state, and the cleanup after that commit
         * deletes their files. A crash before loses only the merge.
         *
         * @return whether the merged segment was published; if not, it is closed, or left to the retired set
         *         of a cleared index, and its files are left to the caller
         */
        private boolean publishMerge(
            final MergeInput  input ,
            final DiskSegment merged
        )
        {
            // Held for the publication, so a removeAll that retires the set cannot close it under this.
            merged.retain();
            final Semaphore permit    = this.flushPermit;
            boolean         joined    = false;
            boolean         published = false;
            permit.acquireUninterruptibly();
            try
            {
                if(this.mergeAbortRequested || input.epoch() != this.sourceEpoch)
                {
                    return false;
                }
                if(!this.replaceSegments(input, List.of(), merged))
                {
                    return false;
                }
                joined = true;
                this.reachedMergeStage(MergeStage.JOINED);

                this.builderLock.writeLock().lock();
                this.builderLock.writeLock().unlock();

                final boolean[] moved = new boolean[merged.nodeCount()];
                final List<DiskSegment> emptied = this.moveOwners(input, merged, moved);
                if(emptied == null)
                {
                    // Cleared meanwhile: the retired set closes the merged segment with the rest.
                    return false;
                }
                this.reachedMergeStage(MergeStage.MOVED);
                final int[] notMoved = IntStream.range(0, moved.length)
                    .filter(local -> !moved[local])
                    .toArray();
                merged.committed(notMoved, 0);

                final List<DiskSegment> leaving = new ArrayList<>(emptied);
                if(notMoved.length == merged.nodeCount())
                {
                    // Everything in it was mutated during the merge.
                    leaving.add(merged);
                }
                // Raised before the publication, not after: an explicit persist that finds nothing left to
                // rewrite in the new set must also find the flag, or it skips the commit (see persistToDisk).
                // Both run under the flush permit here, and so does the commit that clears the flag, so the
                // value can be put back as it was should the publication fail.
                final boolean uncommittedBefore = this.mergeUncommitted;
                this.mergeUncommitted = true;
                if(!this.replaceSegments(input, leaving, null))
                {
                    this.mergeUncommitted = uncommittedBefore;
                    return false;
                }
                published = true;
                this.mergeCount++;
            }
            finally
            {
                if(joined && !published)
                {
                    // Before the permit goes, and before the caller deletes the merged segment's files.
                    this.withdrawMerge(input, merged);
                }
                permit.release();
                // Closes it unless a set holds it.
                merged.release();
            }

            final BackgroundTaskManager manager = this.backgroundTaskManager;
            if(manager != null)
            {
                // The scheduled and the shutdown persist only run for counted changes.
                manager.markPersistRequired();
                if(this.configuration.backgroundPersistence() && this.isManifestCommitDue())
                {
                    manager.requestFlush();
                }
            }
            return true;
        }

        /**
         * Undoes a publication that failed after the merged segment joined the set, whatever stage it failed at,
         * because the caller deletes the merged segment's files: moves back every owner the merged segment took
         * that no mutation has taken since, takes the merged segment out of the set, and waits for every search
         * that may still read it. Under the flush permit. Nothing to undo for a cleared index, whose retired set
         * closes the merged segment with the rest. A failure here is logged, never thrown over the one that
         * failed the publication.
         */
        private void withdrawMerge(
            final MergeInput  input ,
            final DiskSegment merged
        )
        {
            try
            {
                if(input.epoch() != this.sourceEpoch)
                {
                    return;
                }
                final OwnerTable owners = input.owners();
                for(final DiskSegment source : input.segments())
                {
                    for(final int global : source.localToGlobal())
                    {
                        // Back only from the merged segment: an entity a mutation took meanwhile keeps its owner.
                        owners.move(global, merged.slot(), source.slot());
                    }
                }
                if(this.replaceSegments(input, List.of(merged), null))
                {
                    this.builderLock.writeLock().lock();
                    this.builderLock.writeLock().unlock();
                }
            }
            catch(final RuntimeException e)
            {
                LOG.error("Could not withdraw the merged segment {} of '{}' after its publication failed",
                    merged.sequence(), this.name, e);
            }
        }

        /**
         * Replaces segments in the published set: removes {@code leaving} and adds {@code joining}, under
         * {@link #commitLock} like a flush's publication. Returns {@code false} if the index was cleared
         * meanwhile, which publishes nothing.
         */
        private boolean replaceSegments(
            final MergeInput        input  ,
            final List<DiskSegment> leaving,
            final DiskSegment       joining
        )
        {
            synchronized(this.commitLock)
            {
                final SourceSet current = this.sources;
                if(current == null || input.epoch() != this.sourceEpoch || current.owners() != input.owners())
                {
                    return false;
                }
                final List<DiskSegment> segments = new ArrayList<>(current.segments());
                segments.removeAll(leaving);
                if(joining != null)
                {
                    segments.add(joining);
                }
                // The manifest lists segments by ascending sequence, and a flush lists them in set order.
                segments.sort(Comparator.comparingLong(DiskSegment::sequence));
                this.sources = current.with(segments, current.frozen());
                current.release();
                return true;
            }
        }

        /**
         * Moves the owners of the entities the merged segments still own to the merged segment, by
         * compare-and-set on each owner and without the parent monitor (see
         * {@link OwnerTable#move(int, int, int)}): an entity a mutation takes first keeps its new owner, and
         * one moved first is logged as leaving the merged segment by the mutation that comes after. Marks
         * the moved nodes of the merged segment in {@code moved}.
         * <p>
         * A merged segment that still owns an entity the merged segment lacks - which cannot happen while
         * every entity that still has its slot is merged - is not moved at all and stays in the set, since
         * its moved entities would have to be logged as leaving it, which takes the monitor.
         *
         * @return the merged segments that own nothing any more, or {@code null} if the index was cleared
         *         meanwhile
         */
        private List<DiskSegment> moveOwners(
            final MergeInput  input ,
            final DiskSegment merged,
            final boolean[]   moved
        )
        {
            final OwnerTable        owners  = input.owners();
            final int[]             targets = merged.localToGlobal();
            final List<DiskSegment> emptied = new ArrayList<>(input.segments().size());
            for(final DiskSegment source : input.segments())
            {
                if(input.epoch() != this.sourceEpoch)
                {
                    return null;
                }
                final int[] globals = source.localToGlobal();
                final int   slot    = source.slot();
                if(!this.mergedEverythingOwnedBy(source, owners.owners(), targets))
                {
                    LOG.warn("Segment {} of '{}' still owns entities the merge did not take, it stays",
                        source.sequence(), this.name);
                    continue;
                }
                // Both arrays ascend, so one cursor walks the merged segment once per source.
                int cursor = 0;
                for(final int global : globals)
                {
                    while(cursor < targets.length && targets[cursor] < global)
                    {
                        cursor++;
                    }
                    if(cursor < targets.length
                        && targets[cursor] == global
                        && owners.move(global, slot, merged.slot()))
                    {
                        moved[cursor] = true;
                    }
                }
                emptied.add(source);
            }
            return emptied;
        }

        /**
         * Whether the merged segment holds every entity {@code source} still owns. Once true it stays
         * true: an entity never returns to a slot it has left.
         */
        private boolean mergedEverythingOwnedBy(
            final DiskSegment   source ,
            final SegmentOwners owners ,
            final int[]         targets
        )
        {
            final int slot   = source.slot();
            int       cursor = 0;
            for(final int global : source.localToGlobal())
            {
                while(cursor < targets.length && targets[cursor] < global)
                {
                    cursor++;
                }
                if(owners.owner(global) == slot && (cursor == targets.length || targets[cursor] != global))
                {
                    return false;
                }
            }
            return true;
        }

        private void checkMergeStillWanted(final MergeInput input)
        {
            if(this.mergeAbortRequested)
            {
                throw new MergeAbandonedException("the index is closing");
            }
            if(input.epoch() != this.sourceEpoch)
            {
                throw new MergeAbandonedException("the index was cleared");
            }
        }

        private void reachedMergeStage(final MergeStage stage)
        {
            final Consumer<MergeStage> hook = this.mergeStageTestHook;
            if(hook != null)
            {
                hook.accept(stage);
            }
        }

        /**
         * Returns the merge thread, creating it on first use, like the flush writer.
         */
        private ExecutorService mergeExecutor()
        {
            ExecutorService executor = this.mergeExecutor;
            if(executor == null)
            {
                synchronized(this.mergeScheduled)
                {
                    executor = this.mergeExecutor;
                    if(executor == null)
                    {
                        this.mergeExecutor = executor = newBackgroundWriter(this.name, "merge");
                    }
                }
            }
            return executor;
        }

        /**
         * The points of a background merge, in order, at which {@link #mergeStageTestHook} is called.
         */
        enum MergeStage
        {
            /** The segments to merge are selected and held; nothing is built yet. */
            SELECTED,

            /** The merged graph is built and cleaned up, not written. */
            BUILT,

            /** The merged segment is written and opened, not published. */
            WRITTEN,

            /** The merged segment is in the published set, owning nothing yet; the flush permit is held. */
            JOINED,

            /** The owners moved to the merged segment; the merged segments are still in the set. */
            MOVED,

            /** The owners moved and the merged segments left the set; nothing is committed yet. */
            PUBLISHED
        }

        private enum MergeOutcome
        {
            /** A merged segment was published. */
            MERGED,

            /** The policy found nothing to merge. */
            NOTHING_DUE,

            /** The merge stopped: the index is closing or was cleared, or nothing was left to publish. */
            ABANDONED
        }

        /**
         * Thrown inside a merge whose index is closing or was cleared meanwhile; it publishes nothing.
         */
        static final class MergeAbandonedException extends IllegalStateException
        {
            private static final long serialVersionUID = 1L;

            MergeAbandonedException(final String reason)
            {
                super(reason);
            }
        }

        /**
         * What one merge works on, captured when it is selected.
         *
         * @param segments the segments to merge, held by the merge until it ends
         * @param owners   the owner table of their set; a merge into another one publishes nothing
         * @param epoch    the source epoch at the selection
         * @param disk     the disk manager to write through
         * @param file     the vector file to read the vectors from
         * @param pq       the PQ codebook to encode the merged segment with, or {@code null}
         * @param nvq      the NVQ quantizer, or {@code null}
         * @param entities the entities the segments owned at the selection, ascending
         */
        private record MergeInput(
            List<DiskSegment>     segments,
            OwnerTable            owners  ,
            long                  epoch   ,
            DiskIndexManager      disk    ,
            VectorFile            file    ,
            ProductQuantization   pq      ,
            NVQuantization        nvq     ,
            int[]                 entities
        )
        {
            void release()
            {
                this.segments.forEach(DiskSegment::release);
            }
        }

        /**
         * Applies the eventual-indexing operations a freeze moved into a frozen graph, once: a retried
         * flush finds them applied. A failure leaves the graph without a change the store has, so the
         * next persist that may consolidate does.
         */
        private void applyMovedOps(final HeapGeneration frozen)
        {
            if(frozen.movedOpsApplied())
            {
                // A generation without moved operations starts out applied, but can still carry the marks of
                // removes with sync indexing. Purged and repaired here, not by the cleanup's purge, which
                // repairs nothing; a retried flush finds no mark left.
                if(frozen.movedOps().isEmpty())
                {
                    try
                    {
                        this.purgeAndRepairFrozen(frozen, Set.of());
                    }
                    catch(final RuntimeException e)
                    {
                        this.consolidationRequired = true;
                        throw e;
                    }
                }
                return;
            }
            final GraphIndexBuilder      target   = frozen.builder();
            final GenerationVectorValues vectors  = frozen.vectorValues();
            // The nodes a moved update replaces: marked deleted now, re-inserted after one purge of all of them.
            // Every operation of an entity reads the same freeze-time vector, so only its last one decides.
            final Set<Integer>           replaced = new LinkedHashSet<>();
            try
            {
                for(final IndexingOperation op : frozen.movedOps())
                {
                    final Runnable hook = this.movedOpTestHook;
                    if(hook != null)
                    {
                        hook.run();
                    }
                    switch(op)
                    {
                        case IndexingOperation.Add      add    ->
                            this.applyMovedAdd(target, add.ordinal(), vectors, replaced);
                        case IndexingOperation.BatchAdd batch  ->
                        {
                            for(final int ordinal : batch.ordinals())
                            {
                                this.applyMovedAdd(target, ordinal, vectors, replaced);
                            }
                        }
                        case IndexingOperation.Update   update ->
                            this.applyMovedUpdate(target, update.ordinal(), vectors, replaced);
                        case IndexingOperation.Remove   remove ->
                        {
                            replaced.remove(remove.ordinal());
                            this.applyRemove(target, remove.ordinal());
                        }
                    }
                }
                this.purgeAndRepairFrozen(frozen, replaced);
            }
            catch(final RuntimeException e)
            {
                this.consolidationRequired = true;
                throw e;
            }
            frozen.markMovedOpsApplied();
        }

        /**
         * A moved add, on a frozen graph. An entity whose node a moved update replaces gets its node back from
         * the re-insert after the purge: through the idempotent add it would clear the deleted bit and keep the
         * links of its old vector.
         */
        private void applyMovedAdd(
            final GraphIndexBuilder      target  ,
            final int                    ordinal ,
            final GenerationVectorValues vectors ,
            final Set<Integer>           replaced
        )
        {
            if(replaced.contains(ordinal))
            {
                return;
            }
            final float[]          vector = vectors.read(ordinal);
            final OnHeapGraphIndex graph  = graphOf(target);
            if(vector != null && graph.containsNode(ordinal) && graph.getDeletedNodes().get(ordinal))
            {
                // A node marked deleted, by a moved update or remove before this add: back through the re-insert
                // with this vector, not through the idempotent add, which would keep the links of the node's old one.
                replaced.add(ordinal);
                return;
            }
            this.applyAdd(target, ordinal, vector);
        }

        /**
         * A moved update, on a frozen graph: like {@link #applyUpdate}, except that a replaced node is only marked
         * deleted here and re-inserted by {@link #purgeAndRepairFrozen}, after one purge for all of them.
         */
        private void applyMovedUpdate(
            final GraphIndexBuilder      target  ,
            final int                    ordinal ,
            final GenerationVectorValues vectors ,
            final Set<Integer>           replaced
        )
        {
            final float[] vector = vectors.read(ordinal);
            if(vector == null || !graphOf(target).containsNode(ordinal))
            {
                // vec→null removes the node, null→vec adds it: neither purges.
                replaced.remove(ordinal);
                this.applyUpdate(target, ordinal, vector);
                return;
            }
            if(replaced.add(ordinal))
            {
                target.markNodeDeleted(ordinal);
            }
        }

        /**
         * Purges the nodes the moved operations deleted from a frozen graph, re-inserts the nodes moved updates
         * replace, and reconnects every node left unreachable, each once, after all operations: the purge and
         * the inserts prune neighbor lists. Once rather than per operation, which would purge and walk the whole
         * graph for each of a backlog of thousands. Here rather than in the {@code cleanup()} before the write,
         * whose own purge then finds nothing left to strand a node with. Inserts with the generation's own
         * vectors, the freeze-time ones of nodes a mutation has rewritten in the file since (see
         * {@link GenerationVectorValues}), which are the vectors the frozen graph's nodes were inserted with.
         * <p>
         * Also without moved operations, as long as the graph holds nodes marked deleted: a remove with sync
         * indexing only marks its node, and the live graph is frozen with those marks, which the purge of the
         * {@code cleanup()} would otherwise take out without a repair.
         *
         * @param frozen   the frozen generation about to be written, its moved operations applied
         * @param replaced the ordinals whose nodes moved updates marked deleted for a re-insert
         */
        private void purgeAndRepairFrozen(
            final HeapGeneration frozen  ,
            final Set<Integer>   replaced
        )
        {
            if(frozen.movedOps().isEmpty() && frozen.graph().getDeletedNodes().cardinality() == 0)
            {
                return;
            }
            // Without the structure lock: it guards the live graph, and only this flush thread writes to a frozen
            // one (moved operations, this purge, the cleanup() that follows, which takes no lock either). The moved
            // operations go through the same insert helpers as the live graph and so take it, briefly per
            // operation; taking it here would stall every sync insert, and with it the parent monitor, for the
            // whole purge and walk.
            final GraphIndexBuilder      target  = frozen.builder();
            final GenerationVectorValues vectors = frozen.vectorValues();
            this.purgeFrozen(target);
            for(final int ordinal : replaced)
            {
                this.insertSingle(target, ordinal, this.vectorTypeSupport.createFloatVector(vectors.read(ordinal)));
            }
            final int[] nodes = graphOf(target).nodeStream(0).toArray();
            GraphReachability.repair(target, nodes, nodes.length, vectors::getVector, this.name);
        }

        private void purgeFrozen(final GraphIndexBuilder target)
        {
            final Runnable hook = this.frozenPurgeTestHook;
            if(hook != null)
            {
                hook.run();
            }
            target.removeDeletedNodes();
        }

        /**
         * Runs {@code cleanup()} on a frozen graph, unless an earlier attempt of the flush did. Its ForkJoin
         * workers score from the generation's vectors ({@link GenerationVectorValues}: the vector file, with
         * the freeze-time copies of rewritten nodes), so a thread that holds the parent monitor and joins the
         * same pool cannot starve them.
         * <p>
         * The persist of a closing index cleans up without jVector's refinement, a second search for every node
         * of the graph's upper layer. For a large unflushed graph that search took most of the close, 4.5 of
         * 7.8 seconds for 95,000 nodes of dimension 384, and leaving it out changed recall by no more than two
         * builds of the same vectors differ. jVector's cleanup itself notes the refinement as empirically
         * unnecessary for high-dimensional vectors. Every other flush still refines, and a merge or a
         * consolidation that rebuilds the written segment refines it then.
         *
         * @param frozen the frozen generation about to be written
         * @param kind   which persist writes it
         */
        private void prepareForWrite(
            final HeapGeneration frozen,
            final PersistKind    kind
        )
        {
            final OnHeapGraphIndex graph = frozen.graph();
            if(graph.size(0) == 0 || graph.allMutationsCompleted())
            {
                return;
            }
            final boolean           refine = kind != PersistKind.SHUTDOWN;
            final Consumer<Boolean> hook   = this.cleanupTestHook;
            if(hook != null)
            {
                hook.accept(refine);
            }
            if(refine)
            {
                frozen.builder().cleanup();
                return;
            }
            try(GraphIndexBuilder cleanup = this.newUnrefinedCleanupBuilder(frozen))
            {
                cleanup.cleanup();
            }
            catch(final IOException e)
            {
                // closing the builder only releases its searchers, once the cleanup has completed.
                throw new UncheckedIOException(e);
            }
        }

        /**
         * Creates a builder over the graph of a frozen generation whose {@code cleanup()} does not refine it. jVector
         * fixes the refinement per builder, so the generation's own builder, which refines, cannot be used. Scores
         * from the vectors the generation is written with, for the repair of removed nodes; the trimming of the
         * neighbour lists scores with the graph's own diversity provider, the one the generation was built with.
         *
         * @param frozen the frozen generation
         * @return a new builder, to be closed once the cleanup completed
         */
        @SuppressWarnings("deprecation")
        private GraphIndexBuilder newUnrefinedCleanupBuilder(final HeapGeneration frozen)
        {
            return new GraphIndexBuilder(
                BuildScoreProvider.randomAccessScoreProvider(
                    this.flushVectors(frozen), this.jvectorSimilarityFunction()
                ),
                this.configuration.dimension(),
                frozen.graph(),
                this.configuration.beamWidth(),
                this.configuration.neighborOverflow(),
                this.configuration.alpha(),
                false, // no refinement
                PhysicalCoreExecutor.pool(),
                ForkJoinPool.commonPool()
            );
        }

        /**
         * The vectors to write a frozen graph with: those it was built from, as they were at the freeze.
         */
        private RandomAccessVectorValues flushVectors(final HeapGeneration frozen)
        {
            return this.nullSafe(frozen.vectorValues());
        }

        private void checkFlushStillWanted(final FlushPlan plan)
        {
            if(plan.epoch() != this.sourceEpoch)
            {
                throw new FlushAbandonedException("the index was cleared or closed");
            }
            if(this.flushAbortRequested)
            {
                throw new FlushAbandonedException("the index is closing");
            }
        }

        private void reachedFlushStage(final FlushStage stage)
        {
            final Consumer<FlushStage> hook = this.flushStageTestHook;
            if(hook != null)
            {
                hook.accept(stage);
            }
        }

        /**
         * The points of a flush, in order, at which {@link #flushStageTestHook} is called.
         */
        enum FlushStage
        {
            /** The live graph is frozen and the new set published; nothing is written yet. */
            FROZEN,

            /** The moved eventual-indexing operations are applied to the frozen graphs. */
            OPS_APPLIED,

            /** The frozen graphs are cleaned up and ready to be written. */
            CLEANED,

            /** One segment is written and opened, not committed. */
            SEGMENT_WRITTEN,

            /** The manifest is committed and the new segments are published. */
            PUBLISHED
        }

        /**
         * Which persist is running.
         */
        private enum PersistKind
        {
            /** Scheduled, or requested at the flush threshold: writes only once enough accumulated. */
            BACKGROUND,

            /** {@code persistToDisk()}: writes whatever changed, and may consolidate. */
            EXPLICIT,

            /** The persist of a closing index: writes whatever changed, never consolidates. */
            SHUTDOWN
        }

        /**
         * Thrown inside a flush whose index was cleared or closed meanwhile; it commits nothing.
         */
        static final class FlushAbandonedException extends IllegalStateException
        {
            private static final long serialVersionUID = 1L;

            FlushAbandonedException(final String reason)
            {
                super(reason);
            }
        }

        /**
         * What one flush writes and commits: the sources it holds while it runs, the leave log of each
         * source's slot as captured at the freeze, the witnesses sampled there, and the quantizers to
         * encode the new segments with, captured once so every segment of the flush uses the same.
         *
         * @param kind         which persist planned it
         * @param segments     the segments of the set published at the freeze
         * @param frozen       the frozen graphs to write, oldest first
         * @param logs         the leave log of each source's slot, by slot
         * @param witness      the witnesses sampled at the freeze
         * @param pq           the PQ codebook, or {@code null}
         * @param nvq          the NVQ quantizer, or {@code null}
         * @param epoch        the source epoch at the freeze
         * @param consolidated whether a consolidation planned it
         */
        private record FlushPlan(
            PersistKind                       kind        ,
            List<DiskSegment>                 segments    ,
            List<HeapGeneration>              frozen      ,
            Map<Integer, OwnerTable.LeaveLog> logs        ,
            DiskIndexManager.MetaState        witness     ,
            ProductQuantization               pq          ,
            NVQuantization                    nvq         ,
            long                              epoch       ,
            boolean                           consolidated,
            int                               counted
        )
        {
            /**
             * Returned instead of a plan when the freeze is declined: a background persist found too little
             * to flush, another flush holds the permit, or a consolidation is due (or no source slot is free)
             * on a thread that may not consolidate.
             */
            static final FlushPlan DECLINED = new FlushPlan(
                PersistKind.BACKGROUND, List.of(), List.of(), Map.of(), null, null, null, -1L, false, 0
            );

            void retain()
            {
                this.segments.forEach(DiskSegment::retain);
                this.frozen.forEach(HeapGeneration::retain);
            }

            void release()
            {
                this.segments.forEach(DiskSegment::release);
                this.frozen.forEach(HeapGeneration::release);
            }
        }

        /**
         * Trains the PQ codebook, if one is due, before the persist takes any lock, and after it has
         * taken the flush permit, so no flush writes segments while the codebook changes.
         * <p>
         * Only the collection needs the {@code parentMap} monitor, because it reads the vector file,
         * which every mutation writes under that monitor. The
         * clustering that follows is by far the longer half - seconds to tens of seconds at the
         * 128k-vector cap - and touches nothing but the collected sample, so it runs with no locks
         * held. Doing it inside the persist would stall every GigaMap operation and every search for
         * that whole time, on the first persist of a large index that has just enabled PQ.
         * <p>
         * Not called on the shutdown path: shutdown must not wait for k-means, and the shutdown persist
         * writes with whatever quantizers exist.
         * <p>
         * A failure here must not fail the persist. The graph is perfectly writable uncompressed, so
         * the exception is logged and the attempt recorded, and the write proceeds.
         *
         * @return whether this call trained something that is not on disk yet, which the caller
         *         needs in order not to skip the persist that has to write it
         */
        private boolean trainCompressorsBeforeLocking()
        {
            if(this.pqManager == null && this.nvqManager == null)
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
                // training below runs with the monitor released. internalRemoveAll and close null
                // both fields, and either can run while the monitor is released, so reading them
                // again down there is a NullPointerException in the middle of a persist.
                pq      = this.pqManager ;
                nvq     = this.nvqManager;
                needPq  = pq  != null && this.isPqTrainingPending() ;
                needNvq = nvq != null && this.isNvqTrainingPending();
                if(!needPq && !needNvq)
                {
                    return false;
                }

                // ONE collection feeds both managers. Collecting per manager would read the whole
                // vector file twice, on the first persist of every index that has both dimensions
                // configured on. The sample is dense and capped; see collectTrainingVectors.
                trainingSample = this.collectTrainingVectors(PQCompressionManager.MAX_TRAINING_VECTORS);

                if(needPq && trainingSample.size() < PQCompressionManager.MIN_VECTORS_FOR_PQ_TRAINING)
                {
                    // Record the decline here as well, not only after a failed compute below. The
                    // collection declines whenever the sample turns out too small. Without this the
                    // witness never advances, isPqTrainingPending() stays true, and every persist
                    // collects a sample again.
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
            // false - so without this an idle, clean index would train a codebook and then skip the
            // persist that is the entire reason it trained, leaving the feature unpersisted for as
            // long as nothing else changes.
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
            // one failed. Without this witness an index whose training fails on its data would
            // collect and cluster a sample again on every persist, forever.
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
         * {@code NVQCompressionManager.trainFrom} returns without training below that floor.
         * <p>
         * So the witness is not only for exceptions: it stops either outcome from being retried on
         * every persist.
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
         * Samples the three manifest witnesses from the live store state.
         */
        private DiskIndexManager.MetaState currentMetaState()
        {
            return new DiskIndexManager.MetaState(
                this.getExpectedVectorCount(),
                this.getHighestEntityId(),
                this.getStructuralModCount()
            );
        }

        /**
         * Returns the disk manager, for tests and diagnostics. {@code null} for an in-memory index.
         *
         * @return the disk manager, or {@code null}
         */
        DiskIndexManager diskIndexManager()
        {
            return this.diskManager;
        }

        /**
         * Test seam: the published set of sources of an on-disk index, or {@code null}.
         *
         * @return the current sources, not acquired
         */
        SourceSet sourceSet()
        {
            return this.sources;
        }

        /**
         * Test seam: the slot of the live in-memory graph of an on-disk index, or
         * {@link SegmentOwners#NONE}.
         *
         * @return the live slot
         */
        int liveSlot()
        {
            final SourceSet set = this.sources;
            return set == null ? SegmentOwners.NONE : set.owners().liveSlot();
        }

        /**
         * Test seam: the number of flushes that committed.
         *
         * @return the flush count
         */
        long flushCount()
        {
            return this.flushCount;
        }

        /**
         * Test seam: the number of background merges that published their segment.
         *
         * @return the merge count
         */
        long mergeCount()
        {
            return this.mergeCount;
        }

        /**
         * Test seam: whether a close has asked the merges to stop.
         *
         * @return the current value of {@link #mergeAbortRequested}
         */
        boolean isMergeAbortRequested()
        {
            return this.mergeAbortRequested;
        }

        /**
         * Test seam: waits until no background merge is scheduled or running.
         *
         * @param timeoutMillis how long to wait at most
         * @return whether the merges went idle in time
         * @throws InterruptedException if the thread is interrupted while waiting
         */
        boolean awaitMergesIdle(final long timeoutMillis) throws InterruptedException
        {
            final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while(this.mergeScheduled != null && this.mergeScheduled.get())
            {
                if(System.nanoTime() > deadline)
                {
                    return false;
                }
                Thread.sleep(5L);
            }
            return true;
        }

        /**
         * Test seam: the number of consolidations that published their rebuilt generation.
         *
         * @return the consolidation count
         */
        long consolidationCount()
        {
            return this.consolidationCount;
        }

        /**
         * Names the files of the single-file layout before the segments ({@code <name>.graph/.meta/.pqv},
         * graph file version 5) once, if the index directory holds any. They are neither read nor deleted:
         * the index finds no manifest and rebuilds from the store, and the next persist writes the segment
         * layout beside them; an operator can remove them.
         */
        private void logFilesOfTheLayoutBeforeSegments()
        {
            final Path          directory = this.indexDirectoryForDisk();
            final StringBuilder found     = new StringBuilder();
            for(final String extension : new String[]{".graph", ".meta", ".pqv"})
            {
                final Path file = directory.resolve(this.name + extension);
                if(java.nio.file.Files.isRegularFile(file))
                {
                    found.append(found.length() == 0 ? "" : ", ").append(file.getFileName());
                }
            }
            if(found.length() > 0)
            {
                LOG.info("Vector index '{}' finds files of the index layout before the segments in {}: {}. They are"
                    + " ignored and can be removed; the index is rebuilt from the store and persisted as segments.",
                    this.name, directory, found);
            }
        }

        /**
         * Creates a disk manager for this index's directory and format. It reads this index's state,
         * and the commit-stage test hook, through the {@link DiskIndexManager.IndexStateProvider} view,
         * and shares this instance's {@link SegmentSequences}.
         */
        private DiskIndexManager newDiskManager()
        {
            return new DiskIndexManager.Default(
                this,
                this.name,
                this.indexDirectoryForDisk(),
                this.configuration.dimension(),
                GraphFormat.of(this.configuration),
                this.configuration.parallelOnDiskWrite(),
                this.segmentSequences
            );
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

                // Remembered, because a persist cannot infer it. Training here satisfies the pending
                // check that the clean-index shortcut consults, so without this the next persist would
                // skip the rewrite and leave the codebook in heap - see the field.
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
         * Syncs the PQ manager with the codebook actually embedded in the freshly loaded segments.
         * <p>
         * Called after every successful load. Passing {@code null} through is the point: it resets the
         * manager to untrained so the next persist trains and writes a compressed graph, instead of the
         * manager reporting itself trained while holding no codebook - the state in which every flush
         * would silently write uncompressed forever.
         * <p>
         * {@code initializeIndex} runs under whatever the caller happened to hold, which for a search
         * is nothing at all. What actually makes this safe is that the managers publish their state
         * through volatile fields.
         *
         * @param loaded the loaded index
         */
        private void adoptCompressorsFromLoadedGraph(final DiskIndexManager.LoadedIndex loaded)
        {
            if(this.pqManager != null)
            {
                final ProductQuantization loadedPq = loaded.codebook();
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
                // the quantizer out of each segment's own NVQ feature, and every segment is written
                // with whatever quantizer the index then holds. Adopting simply saves the next
                // persist a mean pass and lets isNvqCompressionActive() answer truthfully right away.
                final NVQuantization loadedNvq = loaded.nvq();
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
            // No children: the vectors are in the vector file, which the store never writes.
        }

        /**
         * Why this record must not be stored, or {@code null} if it may be: it still holds the vector store of its
         * 4.x layout, which the current layout has no field for, so a store would drop those vectors. Reads
         * {@link #DROP_LEGACY_VECTORS_PROPERTY} here as well as at the import, so setting it while the storage runs
         * lets the next store drop them.
         */
        String legacyVectorStoreRefusal()
        {
            if(this.legacyVectorStore == null)
            {
                return null;
            }
            if(!this.vectorsRecovered)
            {
                return "Vector index \"" + this.name + "\" still carries the vector store of its 4.x layout; it must"
                    + " be searched or mutated once before it is stored.";
            }
            if(Boolean.getBoolean(DROP_LEGACY_VECTORS_PROPERTY))
            {
                LOG.warn("Vector index '{}': dropping the vectors of its 4.x vector store ({}=true); every later"
                    + " start vectorizes every entity.", this.name, DROP_LEGACY_VECTORS_PROPERTY);
                this.legacyVectorStore = null;
                return null;
            }
            if(this.closed)
            {
                return "Vector index \"" + this.name + "\" is closed and holds the vectors of its 4.x vector store only"
                    + " in a temporary file, which the current layout cannot keep, so this store would drop them for"
                    + " good. Restart and give the index a location with VectorIndices.changeIndexLocation, remove it"
                    + " with VectorIndices.removeIndex, or set the system property " + DROP_LEGACY_VECTORS_PROPERTY
                    + "=true to drop them.";
            }
            return "Vector index \"" + this.name + "\" holds the vectors of its 4.x vector store only in a temporary"
                + " file: it has no index directory or location, and the current layout keeps no vectors in the"
                + " storage, so this store would drop them for good. Give the index a location with"
                + " VectorIndices.changeIndexLocation, which keeps them without a vectorizer call, or set the system"
                + " property " + DROP_LEGACY_VECTORS_PROPERTY + "=true to drop them and vectorize every entity at"
                + " every start.";
        }

        /**
         * The store hook, called by {@link BinaryHandlerVectorIndexDefault#store} before this record is
         * written: has the store epoch this record carries recorded as committed once the store commits. The
         * epoch itself is normally sealed earlier, by {@link #internalPrepareStore(Storer)} when the GigaMap's
         * store begins, before its entity segments are written; only a store that writes this record without
         * the GigaMap, e.g. of the index alone, seals it here.
         * <p>
         * Takes the parent monitor itself, because the handler's {@code store()} can run without it, and
         * does no I/O, so a store never waits for the vector file to reach the disk: a slot written after
         * the store epoch is repaired from its entity after a crash instead. A slot written between this hook
         * and the commit of the same store, by a storer that commits outside the monitor, carries the next
         * epoch and is repaired the same way.
         * <p>
         * Trusting a slot at or below the epoch a record carries would assume that the stores carrying this
         * record commit in the order their hooks ran, which Eclipse Store's storers, committing independently,
         * do not guarantee. The vector file therefore advances its committed epoch only over epochs whose
         * stores all committed (see {@link VectorFile#committed(long)}): a store that failed, or commits after
         * a later one, keeps the slots written at its epoch unproven until its retry commits it, so a crash
         * meanwhile repairs them from their entities instead of trusting them.
         *
         * @param handler the store handler, for the commit listener
         */
        void internalSealVectorEpochOnStore(final PersistenceStoreHandler<?> handler)
        {
            if(this.parent == null)
            {
                return;
            }
            synchronized(this.parentMap())
            {
                final VectorFile file = this.vectorFile;
                if(this.closed || file == null || file.isClosed() || !this.vectorsRecovered)
                {
                    return;
                }
                // Sealed already if the GigaMap's store began this store: sealing again now would take in the
                // writes of mutations that ran since, after entity segments of this store may have been written.
                final WeakReference<Object> prepared = this.sealedForStore;
                this.sealedForStore = null;
                if(prepared == null || prepared.get() != handler)
                {
                    this.sealEpoch(file);
                }
                // The epoch this store carries, also when nothing was written since the last seal: a store that
                // failed after sealing leaves its epoch uncommitted, and the retry that stores it again must
                // commit it, or the chunks written at it would stay hot until the next store with a write.
                final long carried = this.vectorFileEpoch;
                if(carried > 0L)
                {
                    handler.registerCommitListener(() -> file.committed(carried));
                }
            }
        }

        @Override
        public void internalPrepareStore(final Storer storer)
        {
            if(this.parent == null)
            {
                return;
            }
            synchronized(this.parentMap())
            {
                final VectorFile file = this.vectorFile;
                if(this.closed || file == null || file.isClosed() || !this.vectorsRecovered)
                {
                    return;
                }
                // The epoch the store carries is fixed here, while the GigaMap's monitor is held and before its
                // entity segments are written: a mutation that runs while the storer writes them, which a store
                // without that monitor allows, writes at a later epoch and is repaired after a crash, rather
                // than trusted with a vector its committed entity does not have. The commit listener is
                // registered with the record (internalSealVectorEpochOnStore), so an epoch whose record this
                // store does not write is never committed.
                this.sealEpoch(file);
                this.sealedForStore = new WeakReference<>(storer);
            }
        }

        private void sealEpoch(final VectorFile file)
        {
            final long sealed = file.seal();
            if(sealed >= 0L)
            {
                this.vectorFileEpoch = sealed;
                this.markStateChangeInstance();
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
            // The merge permit, the shutdown persist and the flush permit below are waited for, and their holders
            // may need the monitor: under it, the close would wait for itself.
            this.ensureNotHoldingParentMonitor("close()");
            // Not meant to be called concurrently with itself (VectorIndices closes an index once, or
            // again only after a failed close): the reset of closeRequested below assumes one closer.
            final boolean optimizeOnShutdown = this.configuration.optimizeOnShutdown();
            final boolean persistOnShutdown  = this.configuration.persistOnShutdown();
            // Only these shutdown tasks need the complete graph: an optimization, and the persist of
            // an on-disk index (doPersistToDisk is a no-op for an in-memory one). The rest of the
            // shutdown work - draining queued indexing ops - does not need the load-path rebuild
            // either: nothing is queued before it has completed (see awaitGraphRebuiltBeforeCallback),
            // and ops left from before a recovery that dropped the loaded segments only update a graph
            // this close discards, with no persist on shutdown to write it.
            final boolean shutdownNeedsGraph = optimizeOnShutdown
                || persistOnShutdown && this.configuration.onDisk();

            // First of all, before the shutdown persist: a merge in flight stops at its next stage, and the
            // permit it holds until then keeps the vector file it reads from mapped. Held to the end, so no
            // merge starts on the closing index; one the shutdown persist would commit has published already.
            final Semaphore mergePermit = this.mergePermit;
            this.mergeAbortRequested = true;
            if(mergePermit != null)
            {
                mergePermit.acquireUninterruptibly();
            }
            boolean completed = false;
            try
            {
                if(!shutdownNeedsGraph)
                {
                    // Published before the shutdown below, so that a load-path rebuild in flight
                    // gives up now (see closeRequested). Otherwise the shutdown would wait for it:
                    // a background task that is running the rebuild itself (the first scheduled
                    // optimization of a restored index) or is queued behind the monitor it holds,
                    // with the drain submitted behind that task - up to shutdownPersistTimeoutMillis
                    // plus the executor grace period, after which the rebuild is cut short anyway.
                    // Nothing that rebuild produced would outlive the close: the in-memory graph is
                    // transient, and without a persist on shutdown nothing is written to disk.
                    this.closeRequested = true;
                }

                // Shutdown background task manager - drain indexing, optionally optimize and persist
                this.shutdownBackgroundTaskManager(true, optimizeOnShutdown, persistOnShutdown);

                // From here on a load-path rebuild in progress gives up early in any case. Set only
                // now when the shutdown work above needed the complete graph, which a rebuild cut
                // short could not have provided.
                this.closeRequested = true;
                this.closeUnderFlushPermit();
                completed = true;
            }
            finally
            {
                if(!completed)
                {
                    // A close that failed leaves the index open (VectorIndices keeps it registered and
                    // re-closeable). Left set, the flag would make every later access fail on the graph
                    // rebuild this close may have cut short, instead of simply redoing it.
                    this.closeRequested      = false;
                    this.mergeAbortRequested = false;
                }
                if(mergePermit != null)
                {
                    mergePermit.release();
                }
            }
            final ExecutorService merges = this.mergeExecutor;
            if(completed && merges != null)
            {
                merges.shutdown();
            }
        }

        /**
         * Tears the index down while holding the flush permit, so no flush is writing meanwhile.
         * <p>
         * A flush writes with no lock held, so the write lock alone would not keep one from finishing
         * into an index already torn down. The shutdown persist has normally waited for any flush in
         * flight and written the rest; a flush still running here is one that persist gave up on after
         * its timeout, so it is told to stop at its next stage rather than waited for to the end. The
         * flush writer is shut down afterwards, holding no thread by then.
         */
        private void closeUnderFlushPermit()
        {
            final Semaphore permit = this.flushPermit;
            if(permit == null)
            {
                this.closeUnderLocks();
                return;
            }
            this.flushAbortRequested = true;
            permit.acquireUninterruptibly();
            try
            {
                this.closeUnderLocks();
            }
            finally
            {
                if(!this.closed)
                {
                    // The close failed and the index stays open, so its flushes must work again.
                    this.flushAbortRequested = false;
                }
                permit.release();
            }
            final ExecutorService executor = this.flushExecutor;
            if(executor != null)
            {
                executor.shutdown();
            }
        }

        /**
         * The part of {@link #close()} that tears the transient state down, under the builder write
         * lock and the parent monitor.
         */
        private void closeUnderLocks()
        {
            // The write lock keeps every search and persist out while closeInternalResources() destroys the graph
            // and the disk manager; the parent monitor keeps out the deferred load-path rebuild
            // (ensureGraphRebuilt), which holds only the monitor while it fills the builder. Monitor first, the
            // write lock only tried (see lockBuilderUnderMonitor): blocking on the write lock before the monitor
            // deadlocks with a search issued under the monitor (inside GigaMap.iterate, apply logic or
            // synchronized(map)), which waits for the read lock behind the writer while holding the monitor.
            // close() rejects a caller holding the monitor; an index removal and the storage shutdown close
            // outside it.
            final boolean monitorHeld = Thread.holdsLock(this.parentMap());
            synchronized(this.parentMap())
            {
                // Not interruptible: a shutdown that interrupted this thread must still close the index.
                this.lockBuilderUnderMonitor(monitorHeld, false);
                try
                {
                    final Runnable hook = this.closeTeardownTestHook;
                    if(hook != null)
                    {
                        hook.run();
                    }
                    this.closeInternalResources();
                    // No batch is inserting: a sync batch holds this monitor, an eventual one the read lock
                    // this holds the write lock of. A failed close re-creates the pool when it re-initializes.
                    final Cleaner.Cleanable shutdown = this.insertPoolShutdown;
                    if(shutdown != null)
                    {
                        shutdown.clean();
                    }
                    // After the graph and the sources, which read it; removeAll runs closeInternalResources
                    // too and keeps the file. An in-memory index's file goes with its directory.
                    final VectorFile file = this.vectorFile;
                    this.vectorFile = null;
                    if(file != null)
                    {
                        file.close();
                    }

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

            // The segments and frozen graphs: closed once nothing holds them any more, which a flush
            // still writing for this generation may for a while.
            this.retireSources();

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

            // The graph went with the builder. A re-initialization that loads no segments, e.g. the next access
            // after a failed close, must rebuild it from the vector file instead of serving the empty builder.
            this.graphRebuilt = false;

            // The disk manager holds no file open; the segments it opened went with the sources.
            this.diskManager      = null;
            this.committedWitness = null;

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
         * Unpublishes the sources of an on-disk index and the live graph's slot, and advances the
         * epoch, so a flush planned before commits nothing: the generation it describes is gone.
         * Under {@link #commitLock}, which a flush holds while it commits and publishes, so the two
         * cannot interleave. The sources close when their last holder releases them.
         */
        private void retireSources()
        {
            final SourceSet retired;
            if(this.commitLock == null)
            {
                // Never initialized, so nothing was ever published or flushed.
                this.commitLock = new Object();
            }
            synchronized(this.commitLock)
            {
                this.sourceEpoch++;
                retired               = this.sources;
                this.sources          = null;
                this.mergeUncommitted = false;
            }
            final OwnerTable.Lease lease = this.liveLease;
            this.liveLease = null;
            if(lease != null)
            {
                lease.release();
            }
            if(retired != null)
            {
                retired.release();
            }
        }

        /**
         * Creates appropriate vector values based on storage mode.
         */
        private RandomAccessVectorValues nullSafe(final RandomAccessVectorValues vectors)
        {
            return new NullSafeVectorValues(vectors, this.configuration.dimension(), this.vectorTypeSupport);
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
            // A stride computed from getVectorCount() cannot do this job: integer division floors
            // to a stride of 1 for any population between limit and 2*limit, which degenerates to taking the
            // first `limit` vectors: exactly the insertion-ordered prefix that skews a codebook.
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

            // From the vector file, which holds exactly the graph's vectors: entities without an embedding have
            // none there and do not feed the codebook, and no vectorizer is called.
            final VectorFile file = this.vectorFile;
            if(file != null)
            {
                // A fresh array per vector, which the sample wraps rather than copies; only for slots that hold
                // one, as forEachPresent checks before the read allocates.
                file.forEachPresent(ordinal ->
                {
                    final float[] vector = file.read(ordinal);
                    if(vector != null)
                    {
                        sample.accept(vector);
                    }
                    return true;
                });
            }

            return reservoir;
        }

        // DiskIndexManager.IndexStateProvider

        @Override
        public long getExpectedVectorCount()
        {
            // The entities with a vector, in both modes: the slots of the vector file that hold one, counted
            // by every vector mutation and persisted with the index.
            return this.vectorCount;
        }

        @Override
        public long getHighestEntityId()
        {
            // Always use the parent GigaMap's id space — that is the ordinal
            // space the HNSW graph is keyed on (via toOrdinal(entityId)) in
            // both embedded and computed-vector modes. The computed-mode
            // vector file's highest ordinal is only an upper bound of what was
            // ever written, so it is unsafe as a stand-in.
            return this.parentMap().highestUsedId();
        }

        @Override
        public long getStructuralModCount()
        {
            // The persisted witness stamped into the disk manifest at write time and compared on
            // reload. Unlike count/highestId it changes on vec↔null transitions, closing the
            // crash-restart window where a stale on-disk graph would otherwise be accepted.
            return this.structuralModCount;
        }

        @Override
        public Consumer<DiskIndexManager.CommitStage> commitStageTestHook()
        {
            return this.commitStageTestHook;
        }

        @Override
        public Runnable reapListingTestHook()
        {
            return this.reapListingTestHook;
        }

        // ================================================================
        // BackgroundTaskManager.Callback implementation
        // ================================================================

        /**
         * Makes sure no deferred load-path rebuild is pending or running before a background
         * indexing callback touches the builder.
         * <p>
         * The callbacks take {@code builderLock.readLock()}, not the parent monitor, so the rebuild
         * in {@link #ensureGraphRebuilt()} (monitor only) does not exclude them by itself. A callback
         * that ran during it could insert an ordinal the rebuild's pre-pass found absent and is about
         * to insert concurrently, which jVector rejects with "Node already exists", or remove or
         * replace a node while the rebuild links to it. Today no callback can be queued then: every
         * enqueue happens inside a GigaMap mutation that has run {@code ensureIndexInitialized()}
         * under the monitor first, and the queue is transient and so empty after a load. The one way
         * {@code graphRebuilt} returns to {@code false} while the background manager runs is a recovery
         * that drops the loaded segments (see {@link #dropLoadedSegments()}), and callbacks queued before
         * it then find no rebuild in flight but one due. This guard covers both: a callback either finds
         * the rebuild done (a volatile read) or
         * waits for it, or runs it, under the monitor.
         * <p>
         * Lock order: called before the read lock is taken, exactly like the search path does, so
         * this thread holds no {@code builderLock} while it takes the monitor; and the rebuild
         * never waits for the background executor, so waiting for the rebuild here cannot deadlock.
         */
        private void awaitGraphRebuiltBeforeCallback()
        {
            this.ensureGraphRebuilt();
        }

        @Override
        public void applyGraphAdd(final int ordinal)
        {
            // Called from the background indexing worker thread (not from GigaMap's
            // synchronized methods), so we use builderLock.readLock() to coordinate
            // with freezes and maintenance (writeLock).
            this.awaitGraphRebuiltBeforeCallback();
            this.builderLock.readLock().lock();
            try
            {
                if(BackgroundTaskManager.isApplyingDiscardedOperation())
                {
                    // Polled before the index was cleared, applied only now: stale.
                    return;
                }
                this.applyAdd(this.builder, ordinal, this.vectorFile.read(ordinal));
            }
            finally
            {
                this.builderLock.readLock().unlock();
            }
        }

        @Override
        public void applyGraphBatchAdd(final int[] ordinals)
        {
            this.awaitGraphRebuiltBeforeCallback();
            // Acquires the lock once for the entire batch, avoiding per-entry lock overhead.
            this.builderLock.readLock().lock();
            try
            {
                if(BackgroundTaskManager.isApplyingDiscardedOperation())
                {
                    return;
                }
                // One generation for the whole batch, read once, see applyGraphUpdate. In parallel, like the
                // sync path: the vectors come from the file, without a lock. An entity that has lost its vector
                // since is skipped; the operation that removed it follows in the queue.
                final VectorFile file = this.vectorFile;
                this.insertBatch(this.builder, ordinals, index ->
                {
                    final float[] vector = file.read(ordinals[index]);
                    return vector == null ? null : this.vectorTypeSupport.createFloatVector(vector);
                });
            }
            finally
            {
                this.builderLock.readLock().unlock();
            }
        }

        @Override
        public void applyGraphUpdate(final int ordinal)
        {
            this.awaitGraphRebuiltBeforeCallback();
            this.builderLock.readLock().lock();
            try
            {
                if(BackgroundTaskManager.isApplyingDiscardedOperation())
                {
                    return;
                }
                // One generation, captured once. This callback holds only the read lock, and the
                // in-memory PQ switch publishes a replacement while holding neither - so reading
                // this.builder again further down could land on the other side of that publication.
                this.applyUpdate(this.builder, ordinal, this.vectorFile.read(ordinal));
            }
            finally
            {
                this.builderLock.readLock().unlock();
            }
        }

        @Override
        public void applyGraphRemove(final int ordinal)
        {
            this.awaitGraphRebuiltBeforeCallback();
            this.builderLock.readLock().lock();
            try
            {
                if(BackgroundTaskManager.isApplyingDiscardedOperation())
                {
                    return;
                }
                this.applyRemove(this.builder, ordinal);
            }
            finally
            {
                this.builderLock.readLock().unlock();
            }
        }

        @Override
        public void graphOperationFailed(final RuntimeException failure)
        {
            // The graph now lacks a change the store has. An in-memory index has nothing to rebuild it
            // at; an on-disk one rebuilds at the next persist that may consolidate, and does not let a
            // shutdown persist commit the gap meanwhile.
            if(this.configuration.onDisk())
            {
                this.consolidationRequired = true;
            }
        }

        @Override
        public void graphOperationApplied()
        {
            // An eventual-indexing mutation checks the threshold when it enqueues, before the worker
            // has applied it, so the check has to be repeated here.
            this.requestFlushIfDue();
        }


        // ================================================================
        // Graph operations, on a given builder
        // ================================================================

        // Each of these takes the builder it applies to, read once by the caller: the live builder for
        // a mutation or a background callback, a frozen one for an operation a freeze moved. None of
        // them touches the owners; the mutation set those when it ran, under the parent monitor.

        /**
         * The add an eventual-indexing operation carries, on {@code target}, with the vector the file holds
         * now, or the freeze copied. None means the entity lost it since; the operation that removed it
         * follows in the queue.
         */
        private void applyAdd(
            final GraphIndexBuilder target ,
            final int               ordinal,
            final float[]           vector
        )
        {
            if(vector == null)
            {
                return;
            }
            final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(vector);

            // Through the idempotent helper rather than addGraphNode directly. This operation is queued
            // by a mutation and applied later, and in between the in-memory PQ switch can have replayed
            // the same ordinal into a replacement graph from its own snapshot - the snapshot is taken
            // after the mutation, so the change counter does not reject it. Adding an ordinal the graph
            // already holds throws.
            this.internalAddGraphNodeIdempotent(target, ordinal, vf);
        }

        /**
         * The update an eventual-indexing operation carries, on {@code target}: a delete for an entity
         * that lost its vector, a delete and re-add for a changed one, an add for one that gained one. The
         * vector is what the file holds now, or the freeze copied, so a later update of the same entity
         * finds the graph already current.
         */
        private void applyUpdate(
            final GraphIndexBuilder target ,
            final int               ordinal,
            final float[]           vector
        )
        {
            if(target == null)
            {
                return;
            }
            final OnHeapGraphIndex graph   = graphOf(target);
            final boolean          inGraph = graph.containsNode(ordinal);

            if(vector == null)
            {
                // vec→null / null→null: remove the node if present, otherwise nothing to do.
                if(inGraph)
                {
                    target.markNodeDeleted(ordinal);
                    // After the delete, so no in-flight scorer sees a zeroed code for a node that is
                    // still live. The eventual-indexing paths need this as much as the synchronous
                    // ones: without it a deleted node keeps a code traversal can still score against
                    // and waste hops on.
                    this.untrackPqCode(ordinal);
                }
                return;
            }

            // vec→vec: delete the stale node then re-add. null→vec: the node is absent, so add it
            // directly without the (invalid) delete.
            final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(vector);
            if(inGraph)
            {
                target.markNodeDeleted(ordinal);
                this.replaceDeletedNode(target, ordinal, vf);
                return;
            }
            this.insertSingle(target, ordinal, vf);
        }

        /**
         * The remove an eventual-indexing operation carries, on {@code target}.
         */
        private void applyRemove(
            final GraphIndexBuilder target ,
            final int               ordinal
        )
        {
            this.internalMarkOrdinalDeleted(target, ordinal);
        }

        /**
         * Marks a graph ordinal deleted in {@code target} if the node is there. A copy elsewhere is
         * hidden by its owner, which the mutation already set.
         */
        private void internalMarkOrdinalDeleted(
            final GraphIndexBuilder target ,
            final int               ordinal
        )
        {
            if(target != null && graphOf(target).containsNode(ordinal))
            {
                target.markNodeDeleted(ordinal);
                this.untrackPqCode(ordinal);
            }
        }

        /**
         * Replaces a graph node with a new vector in {@code target}. Used by the synchronous update path.
         * <p>
         * The removal half must never be skipped while the add half runs unconditionally: jvector's
         * {@code OnHeapGraphIndex.addNode} is a non-atomic loop over layers {@code 0..level} that
         * throws {@code IllegalStateException: Node N already exists} on the first layer already
         * holding the ordinal, and {@code containsNode} only inspects layer 0. So the {@code removeNode}
         * that {@link #insertSingle} runs before the add is not redundant with {@code removeDeletedNodes}:
         * it purges the ordinal
         * from ALL layers and thereby also repairs a node left in an upper layer only by an earlier
         * interleaved add/remove (internal #142).
         * <p>
         * {@code removeDeletedNodes()} scores through a ForkJoinPool, whose workers read the vector file
         * rather than the parent GigaMap, so it cannot deadlock against the GigaMap monitor the caller
         * holds, whichever the vectorizer.
         */
        private void internalReplaceGraphNode(
            final GraphIndexBuilder target ,
            final int               ordinal,
            final VectorFloat<?>    vf
        )
        {
            if(target == null)
            {
                return;
            }
            final OnHeapGraphIndex graph = graphOf(target);
            if(graph.containsNode(ordinal))
            {
                // Repairs the neighbor lists that referenced the node before dropping it.
                target.markNodeDeleted(ordinal);
                this.untrackPqCode(ordinal);
                this.replaceDeletedNode(target, ordinal, vf);
                return;
            }
            this.insertSingle(target, ordinal, vf);
        }

        /**
         * Purges {@code ordinal}, marked deleted by the caller, from {@code target} and inserts it again with
         * {@code vf}; for the live graph, then reconnects what that left unreachable.
         * <p>
         * The purge hands the node's in-neighbours its neighbours and prunes the merged lists for diversity, and
         * the insert's backlinks prune the lists of its new neighbours. Either can take the last edge into some
         * node, near-duplicates most of all, and replacing every vector of an index one after another reliably
         * does. Which node is not known, so the whole of layer 0 is walked: one pass over the
         * edges, of the same order as the purge's own pass over every node. Only for the live graph: a frozen
         * graph is repaired once, after all the operations moved into it, see {@link #applyMovedOps}.
         */
        private void replaceDeletedNode(
            final GraphIndexBuilder target ,
            final int               ordinal,
            final VectorFloat<?>    vf
        )
        {
            final ReentrantReadWriteLock.WriteLock lock = this.structureLock.writeLock();
            lock.lock();
            try
            {
                target.removeDeletedNodes();
            }
            finally
            {
                lock.unlock();
            }
            this.insertSingle(target, ordinal, vf);
            if(target != this.builder)
            {
                return;
            }
            lock.lock();
            try
            {
                final int[] nodes = graphOf(target).nodeStream(0).toArray();
                this.repairLiveGraph(() ->
                    GraphReachability.repair(target, nodes, nodes.length, this::vectorOf, this.name)
                );
            }
            finally
            {
                lock.unlock();
            }
        }

        /**
         * Inserts one node that is not in layer 0 of {@code target}: purges what an upper layer may still hold
         * of it, writes its code and adds it, under the structure lock shared.
         */
        private void insertSingle(
            final GraphIndexBuilder target ,
            final int               ordinal,
            final VectorFloat<?>    vf
        )
        {
            this.purgeIfEntryDeleted(target);
            final ReentrantReadWriteLock.ReadLock lock = this.structureLock.readLock();
            lock.lock();
            try
            {
                graphOf(target).removeNode(ordinal);
                this.trackPqCode(ordinal, vf);
                target.addGraphNode(ordinal, vf);
            }
            finally
            {
                lock.unlock();
            }
        }

        /**
         * Adds a graph node to {@code target}, tolerating an ordinal that is already present.
         * Never calls {@code removeDeletedNodes()}: an add for a present ordinal means the entity is back
         * or was replayed, not that its vector changed, so its links stay.
         */
        private void internalAddGraphNodeIdempotent(
            final GraphIndexBuilder target ,
            final int               ordinal,
            final VectorFloat<?>    vf
        )
        {
            if(target == null)
            {
                return;
            }

            final OnHeapGraphIndex graph = graphOf(target);

            if(graph.containsNode(ordinal))
            {
                // The code first, whichever branch follows: it is what the node is scored by once
                // compressed scoring is on, and the insert below reads it while choosing neighbours.
                this.trackPqCode(ordinal, vf);
                // Already a node of this graph, so there is nothing structural left to do - and
                // reaching the same state by removing and re-adding is not merely wasteful, it
                // corrupts the graph. removeNode strips the node's own entry but not the backlinks
                // its neighbours hold, so the next insert that picks one of those stale references
                // fails with an NPE inside ConcurrentNeighborMap.
                //
                // That is not a rare interleaving. After the in-memory PQ switch every queued add
                // names an ordinal the replay has already inserted, so an unconditional remove
                // would churn the entire graph and lose nodes out of it.
                //
                // Clearing a deleted bit is kept, because an add for an ordinal that is present
                // but marked deleted does mean "this entity is back" - the same thing the resurrect
                // path does, and for the same reason.
                graph.getDeletedNodes().clear(ordinal);
                return;
            }

            // containsNode only inspects layer 0, so "absent" can still mean "present in an upper
            // layer" after an earlier interleaved add/remove. Purge all layers before adding, or
            // the add throws. Safe here in a way it is not above: there is no live layer-0 node
            // whose neighbours could be left holding a reference to it. insertSingle writes the code
            // before the insert, which reads it while choosing neighbours.
            this.insertSingle(target, ordinal, vf);
        }

        /**
         * Inserts the nodes of a batch into {@code target}, in parallel on the insert pool, with the semantics
         * of {@link #internalAddGraphNodeIdempotent}: an ordinal that is a node of the graph already only comes
         * back if it was deleted, and one whose vector is {@code null} is skipped.
         * <p>
         * In phases, each joined before the next starts, so each builds on what the one before completed:
         * <ol>
         *   <li>On this thread, the steps per ordinal that are not safe against concurrent inserts: which
         *       ordinals are nodes already, and the purge of a node left over in an upper layer only.</li>
         *   <li>With in-memory PQ codes, the code of every new node, the highest ordinal first on this thread so
         *       the code store never grows while the workers write (see encodeRebuildCodes). Every code is
         *       there before any insert scores against it.</li>
         *   <li>The inserts, which jVector supports concurrently.</li>
         * </ol>
         * All of it under the structure lock shared, which keeps removeDeletedNodes() out. The workers take no
         * lock and never need the parent monitor, which a sync caller holds while it waits for them.
         *
         * @param target   the builder to insert into, or {@code null} for none
         * @param ordinals the ordinals of the batch, distinct
         * @param vectors  the vector of the ordinal at an index of {@code ordinals}, or {@code null} for none;
         *                 called on this thread only
         */
        private void insertBatch(
            final GraphIndexBuilder           target  ,
            final int[]                       ordinals,
            final IntFunction<VectorFloat<?>> vectors
        )
        {
            if(target == null || ordinals.length == 0)
            {
                return;
            }
            this.purgeIfEntryDeleted(target);
            final OnHeapGraphIndex                graph   = graphOf(target);
            final int[]                           pending = new int[ordinals.length];
            final VectorFloat<?>[]                inserts = new VectorFloat<?>[ordinals.length];
            final ReentrantReadWriteLock.ReadLock lock    = this.structureLock.readLock();
            int                                   count   = 0;
            lock.lock();
            try
            {
                for(int index = 0; index < ordinals.length; index++)
                {
                    final VectorFloat<?> vector = vectors.apply(index);
                    if(vector == null)
                    {
                        continue;
                    }
                    final int ordinal = ordinals[index];
                    if(graph.containsNode(ordinal))
                    {
                        // As in internalAddGraphNodeIdempotent: a node already, its code kept current, back if it
                        // was deleted.
                        this.trackPqCode(ordinal, vector);
                        graph.getDeletedNodes().clear(ordinal);
                        continue;
                    }
                    // As in internalAddGraphNodeIdempotent: a leftover in an upper layer would make the insert throw.
                    graph.removeNode(ordinal);
                    pending[count] = ordinal;
                    inserts[count] = vector;
                    count++;
                }
                final int total = count;

                final MutablePQVectors codes = this.inMemoryPqVectors;
                if(codes != null && total > 0)
                {
                    int highest = 0;
                    for(int index = 1; index < total; index++)
                    {
                        if(pending[index] > pending[highest])
                        {
                            highest = index;
                        }
                    }
                    codes.encodeAndSet(pending[highest], inserts[highest]);
                    this.forEachInsert(total, index -> codes.encodeAndSet(pending[index], inserts[index]));
                }
                // Into an empty live graph, as after a freeze, the first node goes in on this thread, see seedIfEmpty.
                final int seeded = seedIfEmpty(target, total, index -> target.addGraphNode(pending[index], inserts[index]));
                this.forEachInsert(total - seeded, index -> target.addGraphNode(pending[index + seeded], inserts[index + seeded]));
            }
            finally
            {
                lock.unlock();
            }
            this.repairReachability(target, pending, count);
        }

        /**
         * Reconnects what the parallel inserts of {@code ordinals} into {@code target} left unreachable, see
         * {@link GraphReachability}, under the structure lock exclusive: the walk and the re-inserts need the
         * graph to themselves. Does nothing for fewer nodes than run in parallel: those were inserted one after
         * another, each seeing every node before it. Must not be called holding the structure lock shared.
         *
         * @param target   the builder the nodes were inserted into
         * @param ordinals the ordinals inserted
         * @param count    how many entries of {@code ordinals} are valid, from the start
         */
        private void repairReachability(
            final GraphIndexBuilder target  ,
            final int[]             ordinals,
            final int               count
        )
        {
            if(count < ParallelGraphInsertion.MIN_PARALLEL_NODES)
            {
                return;
            }
            final ReentrantReadWriteLock.WriteLock lock = this.structureLock.writeLock();
            lock.lock();
            try
            {
                this.repairLiveGraph(() ->
                    GraphReachability.repair(target, ordinals, count, this::vectorOf, this.name)
                );
            }
            finally
            {
                lock.unlock();
            }
        }

        /**
         * Runs the per-node work of a batch on the insert pool, or on this thread for a small batch or once the
         * pool is shut down.
         */
        private void forEachInsert(
            final int         count ,
            final IntConsumer action
        )
        {
            final ForkJoinPool pool = this.insertPool;
            if(pool == null || pool.isShutdown())
            {
                for(int index = 0; index < count; index++)
                {
                    action.accept(index);
                }
                return;
            }
            ParallelGraphInsertion.forEach(count, action, pool);
        }

        /**
         * Seeds an empty graph before its nodes are inserted in parallel. jVector sets the entry node only when
         * an insert completes, so every insert that starts before that searches an empty graph and links only to
         * the inserts still in progress at its start; one that finds none ends up without an edge from any node a
         * search reaches, an orphan that stays, since the live graph never runs jVector's {@code cleanup()} (which
         * refines upper-layer nodes only anyway). The first node therefore goes in on the calling thread, and the
         * workers start from an entry.
         *
         * @param target the builder the nodes go into
         * @param count  how many nodes the caller is about to insert
         * @param insert inserts the node at an index of the caller's order, or skips it (no vector)
         * @return how many leading indices the seed consumed: until the graph holds a node, 0 if it did already
         */
        private static int seedIfEmpty(
            final GraphIndexBuilder target,
            final int               count ,
            final IntConsumer       insert
        )
        {
            final OnHeapGraphIndex graph  = graphOf(target);
            int                    seeded = 0;
            while(seeded < count && graph.size(0) == 0)
            {
                insert.accept(seeded);
                seeded++;
            }
            return seeded;
        }

        /**
         * Purges the deleted nodes of {@code target} if its entry node is one of them, so that the inserts that
         * follow start from a live one. jVector's insert search accepts live nodes only, and the entry is the
         * only node of its top layer: from a deleted entry it finds no candidate there and hands none down, so
         * every node inserted links to nothing but the nodes inserted after it, an island no search reaches.
         * The purge picks a live entry, or leaves an empty graph whose next insert becomes the entry. Rare, as
         * the entry is a single node. Must not be called holding the structure lock shared.
         */
        private void purgeIfEntryDeleted(final GraphIndexBuilder target)
        {
            final OnHeapGraphIndex                graph = graphOf(target);
            final ImmutableGraphIndex.NodeAtLevel entry = graph.entryNode();
            if(entry != null && graph.getDeletedNodes().get(entry.node))
            {
                this.removeDeletedNodes(target);
            }
        }

        /**
         * Purges the deleted nodes of {@code target}, under the structure lock exclusive: jVector does not
         * support it concurrently with inserts. Must not be called holding the structure lock shared.
         */
        private void removeDeletedNodes(final GraphIndexBuilder target)
        {
            final ReentrantReadWriteLock.WriteLock lock = this.structureLock.writeLock();
            lock.lock();
            try
            {
                this.purgeDeleted(target);
            }
            finally
            {
                lock.unlock();
            }
        }

        /**
         * Purges the deleted nodes of {@code target} and, for the live graph, reconnects what the purge left
         * unreachable, see {@link GraphReachability#purgeDeleted}. A frozen graph is repaired once, after all the
         * operations moved into it, see {@link #purgeAndRepairFrozen}. Called holding the structure lock
         * exclusive.
         */
        private void purgeDeleted(final GraphIndexBuilder target)
        {
            if(target == this.builder)
            {
                this.repairLiveGraph(() -> GraphReachability.purgeDeleted(target, this::vectorOf, this.name));
            }
            else
            {
                target.removeDeletedNodes();
            }
        }

        /**
         * Runs a reachability repair of the live graph. Nodes the repair took out of the graph and could not insert
         * again are missing from it while the store holds them: escalated as a failed graph operation (an on-disk
         * index rebuilds at its next consolidating persist, an in-memory one at its next start) and thrown on, so
         * the caller does not carry on as if the graph were complete.
         *
         * @param repair the repair, run on this thread
         * @throws GraphReachability.NodesLostException if the repair lost nodes
         */
        private void repairLiveGraph(final Runnable repair)
        {
            try
            {
                repair.run();
            }
            catch(final GraphReachability.NodesLostException e)
            {
                LOG.error("Vector index '{}' lost {} graph nodes in a reachability repair; their entities are not "
                    + "found by a search until the graph is rebuilt (reindex() rebuilds it now)", this.name, e.lost(), e);
                this.graphOperationFailed(e);
                throw e;
            }
        }

        private static OnHeapGraphIndex graphOf(final GraphIndexBuilder builder)
        {
            return (OnHeapGraphIndex)builder.getGraph();
        }

    }

}
