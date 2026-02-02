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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
 * for (VectorSearchResult.Entry<Document> entry : results) {
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
 * Automatically persist the index at regular intervals:
 * <pre>{@code
 * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
 *     .dimension(768)
 *     .onDisk(true)
 *     .indexDirectory(Path.of("/data/vectors"))
 *     .backgroundPersistence(true)
 *     .persistenceIntervalMs(30_000)      // Check every 30 seconds
 *     .minChangesBetweenPersists(100)     // Only persist if >= 100 changes
 *     .persistOnShutdown(true)            // Persist on close()
 *     .build();
 * }</pre>
 *
 * <h2>Background Optimization</h2>
 * Periodically clean up the graph to reduce memory and improve query latency:
 * <pre>{@code
 * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
 *     .dimension(768)
 *     .backgroundOptimization(true)
 *     .optimizationIntervalMs(60_000)          // Check every 60 seconds
 *     .minChangesBetweenOptimizations(1000)    // Only optimize if >= 1000 changes
 *     .optimizeOnShutdown(false)               // Skip on close() for faster shutdown
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
 * for (VectorSearchResult.Entry<Document> entry : results) {
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
 * List<VectorSearchResult.Entry<Document>> list = results.toList();
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
 *     GigaMap<Document> gigaMap = (GigaMap<Document>) storage.root();
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
     * for (VectorSearchResult.Entry<Document> entry : results) {
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
     * optimization via {@link VectorIndexConfiguration.Builder#backgroundOptimization(boolean)}:
     * <pre>{@code
     * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
     *     .dimension(768)
     *     .backgroundOptimization(true)
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
     * persistence via {@link VectorIndexConfiguration.Builder#backgroundPersistence(boolean)}:
     * <pre>{@code
     * VectorIndexConfiguration config = VectorIndexConfiguration.builder()
     *     .dimension(768)
     *     .onDisk(true)
     *     .indexDirectory(Path.of("/data/vectors"))
     *     .backgroundPersistence(true)
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
               BackgroundPersistenceManager.Callback,
               BackgroundOptimizationManager.Callback,
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
        private transient DiskIndexManager              diskManager        ;
        private transient PQCompressionManager          pqManager          ;
        private transient BackgroundPersistenceManager  persistenceManager ;
        transient BackgroundOptimizationManager optimizationManager;

        // GraphSearcher pool for thread-local reuse
        private transient ExplicitThreadLocal<GraphSearcher> searcherPool;

        // Flag indicating graph was loaded from file (skip rebuild)
        private transient boolean graphLoadedFromFile;

        // Read/write lock for concurrent search during persistence
        // Read lock: allows concurrent searches
        // Write lock: exclusive access during persistence
        private transient ReentrantReadWriteLock persistenceLock;


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

            // Initialize persistence lock early (before ensureIndexInitialized)
            this.persistenceLock = new ReentrantReadWriteLock();

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

                // Skip rebuild if graph was loaded from file
                if(this.graphLoadedFromFile)
                {
                    this.graphLoadedFromFile = false; // Reset flag
                    return;
                }

                // Rebuild graph from stored data (after deserialization)
                this.rebuildGraphFromStore();
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

            // Initialize persistence lock (always, for consistent locking semantics)
            if(this.persistenceLock == null)
            {
                this.persistenceLock = new ReentrantReadWriteLock();
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

            // Try to load from disk if on-disk mode is enabled
            if(this.configuration.onDisk())
            {
                this.diskManager = new DiskIndexManager.Default(
                    this,
                    this.name,
                    this.configuration.indexDirectory(),
                    this.configuration.dimension(),
                    this.configuration.maxDegree()
                );
                if(this.diskManager.tryLoad())
                {
                    this.graphLoadedFromFile = true;
                    // Mark PQ as trained if compression was enabled (FusedPQ is embedded)
                    if(this.pqManager != null)
                    {
                        this.pqManager.markTrained();
                        LOG.debug("FusedPQ compression loaded from disk for '{}'", this.name);
                    }
                    // Initialize searcher pool for disk index
                    this.initializeSearcherPool();
                    // Start background managers if enabled
                    this.startBackgroundManagersIfEnabled();
                    return;
                }
                LOG.info("Could not load disk index for '{}', will build in-memory and persist later", this.name);
            }

            // Initialize in-memory builder
            this.initializeInMemoryBuilder();

            // Start background managers if enabled
            this.startBackgroundManagersIfEnabled();
        }

        /**
         * Starts background persistence and optimization managers if configured.
         */
        private void startBackgroundManagersIfEnabled()
        {
            this.startBackgroundPersistenceIfEnabled();
            this.startBackgroundOptimizationIfEnabled();
        }

        /**
         * Starts the background persistence manager if configured.
         */
        private void startBackgroundPersistenceIfEnabled()
        {
            if(this.configuration.onDisk() && this.configuration.backgroundPersistence())
            {
                if(this.persistenceManager == null)
                {
                    this.persistenceManager = new BackgroundPersistenceManager.Default(
                        this,
                        this.name,
                        this.configuration.persistenceIntervalMs(),
                        this.configuration.minChangesBetweenPersists()
                    );
                    this.persistenceManager.startScheduledPersistence();
                    LOG.info("Background persistence started for index '{}' with interval {}ms",
                        this.name, this.configuration.persistenceIntervalMs());
                }
            }
        }

        /**
         * Starts the background optimization manager if configured.
         */
        private void startBackgroundOptimizationIfEnabled()
        {
            if(this.configuration.backgroundOptimization())
            {
                if(this.optimizationManager == null)
                {
                    this.optimizationManager = new BackgroundOptimizationManager.Default(
                        this,
                        this.name,
                        this.configuration.optimizationIntervalMs(),
                        this.configuration.minChangesBetweenOptimizations()
                    );
                    this.optimizationManager.startScheduledOptimization();
                    LOG.info("Background optimization started for index '{}' with interval {}ms",
                        this.name, this.configuration.optimizationIntervalMs());
                }
            }
        }

        /**
         * Initializes the in-memory graph builder.
         */
        private void initializeInMemoryBuilder()
        {
            final RandomAccessVectorValues ravv = new NullSafeVectorValues(
                this.createVectorValues(), this.configuration.dimension(), this.vectorTypeSupport
            );
            final BuildScoreProvider bsp = BuildScoreProvider.randomAccessScoreProvider(
                ravv,
                this.jvectorSimilarityFunction()
            );

            this.builder = new GraphIndexBuilder(
                bsp,
                this.configuration.dimension(),
                this.configuration.maxDegree(),
                this.configuration.beamWidth(),
                this.configuration.neighborOverflow(),
                this.configuration.alpha(),
                true // use hierarchical index
            );
            this.index = (OnHeapGraphIndex)this.builder.getGraph();

            // Initialize searcher pool for in-memory index
            this.initializeSearcherPool();
        }

        /**
         * Initializes the thread-local searcher pool.
         */
        private void initializeSearcherPool()
        {
            // Close existing pool if present
            this.closeSearcherPool();

            final boolean diskLoaded = this.diskManager != null && this.diskManager.isLoaded();
            if(diskLoaded && this.diskManager.getDiskIndex() != null)
            {
                // Pool for disk index
                final var diskIndex = this.diskManager.getDiskIndex();
                this.searcherPool = ExplicitThreadLocal.withInitial(() ->
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
                // Pool for in-memory index
                this.searcherPool = ExplicitThreadLocal.withInitial(() ->
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
         * Closes the searcher pool and releases resources.
         */
        private void closeSearcherPool()
        {
            if(this.searcherPool != null)
            {
                try
                {
                    this.searcherPool.close();
                }
                catch(final Exception e)
                {
                    LOG.warn("Error closing searcher pool: {}", e.getMessage());
                }
                this.searcherPool = null;
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
            final float[] vector = this.vectorizer.vectorize(entity);
            if(vector == null)
            {
                throw new IllegalStateException("Null vector returned from vectorizer: " + entity);
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
            return switch(this.configuration.similarityFunction())
            {
                case EUCLIDEAN   -> io.github.jbellis.jvector.vector.VectorSimilarityFunction.EUCLIDEAN;
                case DOT_PRODUCT -> io.github.jbellis.jvector.vector.VectorSimilarityFunction.DOT_PRODUCT;
                case COSINE      -> io.github.jbellis.jvector.vector.VectorSimilarityFunction.COSINE;
                default -> throw new IllegalArgumentException("Unsupported similarity function: " + this.configuration.similarityFunction());
            };
        }

        @Override
        public void internalAdd(final long entityId, final E entity)
        {
            final int ordinal = toOrdinal(entityId);

            synchronized(this.parentMap())
            {
                this.ensureIndexInitialized();

                final float[] vector = this.vectorize(entity);

                // Store based on vectorizer type
                if(!this.isEmbedded())
                {
                    this.vectorStore.add(new VectorEntry(entityId, vector));
                }

                // Add to HNSW graph using entity ID as ordinal
                final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(vector);
                this.builder.addGraphNode(ordinal, vf);

                this.markStateChangeChildren();

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
            synchronized(this.parentMap())
            {
                this.ensureIndexInitialized();

                final float[] vector = this.vectorize(entity);

                final int ordinal = toOrdinal(entityId);
                this.builder.markNodeDeleted(ordinal);
                this.builder.removeDeletedNodes();

                // Update based on vectorizer type
                if(!this.isEmbedded())
                {
                    this.vectorStore.set(entityId, new VectorEntry(entityId, vector));
                }

                // Add to HNSW graph using entity ID as ordinal
                final VectorFloat<?> vf = this.vectorTypeSupport.createFloatVector(vector);
                this.builder.addGraphNode(ordinal, vf);

                this.markStateChangeChildren();

                // Mark dirty for background managers
                this.markDirtyForBackgroundManagers(1);
            }
        }

        /**
         * Collects and validates vectors from entities.
         */
        private List<VectorEntry> collectVectors(final long firstEntityId, final Iterable<? extends E> entities)
        {
            final List<VectorEntry> entries = new ArrayList<>();
            long currentEntityId = firstEntityId;

            for(final E entity : entities)
            {
                final float[] vector = this.vectorize(entity);
                entries.add(new VectorEntry(currentEntityId++, vector));
            }

            return entries;
        }

        /**
         * Adds vector entries to the index.
         */
        private void addVectorEntries(final List<VectorEntry> entries)
        {
            synchronized(this.parentMap())
            {
                this.ensureIndexInitialized();

                if(!this.isEmbedded())
                {
                    this.vectorStore.addAll(entries);
                }

                this.addGraphNodesSequential(entries);

                this.markStateChangeChildren();

                // Mark dirty for background managers (with count for debouncing)
                this.markDirtyForBackgroundManagers(entries.size());
            }
        }

        /**
         * Marks dirty for background managers with the specified change count.
         */
        private void markDirtyForBackgroundManagers(final int count)
        {
            if(this.persistenceManager != null)
            {
                this.persistenceManager.markDirty(count);
            }
            if(this.optimizationManager != null)
            {
                this.optimizationManager.markDirty(count);
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
            synchronized(this.parentMap())
            {
                this.ensureIndexInitialized();

                final int ordinal = toOrdinal(entityId);
                if(!this.isEmbedded())
                {
                    this.vectorStore.removeById(entityId);
                }
                this.builder.markNodeDeleted(ordinal);

                this.markStateChangeChildren();

                // Mark dirty for background managers
                this.markDirtyForBackgroundManagers(1);
            }
        }

        @Override
        public void internalRemoveAll()
        {
            synchronized(this.parentMap())
            {
                this.ensureIndexInitialized();

                if(!this.isEmbedded())
                {
                    this.vectorStore.removeAll();
                }

                // Shutdown optimization manager before closing
                this.shutdownOptimizationManager(false);

                // Shutdown persistence manager before closing
                this.shutdownPersistenceManager(false);

                this.closeInternalResources();

                // Reinitialize the index (this will also restart background managers if configured)
                this.initializeIndex();
                this.markStateChangeChildren();

                // Mark dirty for background managers
                this.markDirtyForBackgroundManagers(1);
            }
        }

        @Override
        public VectorSearchResult<E> search(final float[] queryVector, final int k)
        {
            this.validateDimension(queryVector);

            // Acquire read lock for concurrent search during persistence
            this.persistenceLock.readLock().lock();
            try
            {
                synchronized(this.parentMap())
                {
                    this.ensureIndexInitialized();

                    final VectorFloat<?> query = this.vectorTypeSupport.createFloatVector(queryVector);

                    // Choose search strategy based on index mode
                    final SearchResult result;
                    final boolean diskLoaded = this.diskManager != null && this.diskManager.isLoaded();
                    if(diskLoaded && this.diskManager.getDiskIndex() != null)
                    {
                        result = this.searchDiskIndex(query, k);
                    }
                    else
                    {
                        result = this.searchInMemoryIndex(query, k);
                    }

                    return this.convertSearchResult(result);
                }
            }
            finally
            {
                this.persistenceLock.readLock().unlock();
            }
        }

        /**
         * Searches the in-memory index using a pooled GraphSearcher.
         */
        private SearchResult searchInMemoryIndex(final VectorFloat<?> query, final int k)
        {
            final RandomAccessVectorValues ravv = this.createCachingVectorValues();
            final SearchScoreProvider ssp = DefaultSearchScoreProvider.exact(
                query,
                this.jvectorSimilarityFunction(),
                ravv
            );

            final GraphSearcher searcher = this.searcherPool.get();
            final Bits acceptBits = this.index != null ? this.index.getView().liveNodes() : Bits.ALL;
            return searcher.search(ssp, k, acceptBits);
        }

        /**
         * Searches the on-disk index using a pooled GraphSearcher, with optional PQ-based approximate search and reranking.
         */
        private SearchResult searchDiskIndex(final VectorFloat<?> query, final int k)
        {
            // If PQ is available, use compressed scoring with reranking
            if(this.pqManager != null && this.pqManager.isTrained() && this.pqManager.getCompressedVectors() != null)
            {
                final RandomAccessVectorValues ravv = this.createCachingVectorValues();
                final GraphSearcher searcher = this.searcherPool.get();
                return this.pqManager.searchWithRerank(
                    query,
                    k,
                    searcher,
                    ravv,
                    this.jvectorSimilarityFunction()
                );
            }

            // Otherwise, search disk index with exact vectors using pooled searcher
            final RandomAccessVectorValues ravv = this.createCachingVectorValues();
            final SearchScoreProvider ssp = DefaultSearchScoreProvider.exact(
                query,
                this.jvectorSimilarityFunction(),
                ravv
            );

            final GraphSearcher searcher = this.searcherPool.get();
            final Bits acceptBits = this.index != null ? this.index.getView().liveNodes() : Bits.ALL;
            return searcher.search(ssp, k, acceptBits);
        }

        /**
         * Creates caching vector values for search operations.
         * Wrapped with {@link NullSafeVectorValues} so that deleted nodes
         * (whose vectors are {@code null}) return a safe placeholder instead
         * of causing NPE/NaN during JVector graph traversal.
         */
        private RandomAccessVectorValues createCachingVectorValues()
        {
            final RandomAccessVectorValues raw = this.isEmbedded()
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
            return new NullSafeVectorValues(raw, this.configuration.dimension(), this.vectorTypeSupport);
        }

        /**
         * Converts internal SearchResult to VectorSearchResult.
         */
        private VectorSearchResult<E> convertSearchResult(final SearchResult result)
        {
            final GigaMap<E> parentMap = this.parentMap();
            final SearchResult.NodeScore[] nodes = result.getNodes();
            final BulkList<VectorSearchResult.Entry<E>> entries = BulkList.New(nodes.length);
            for(final SearchResult.NodeScore node : nodes)
            {
                // Ordinals (node) ARE entity IDs, so direct conversion
                // Pass parentMap for lazy entity access
                entries.add(new VectorSearchResult.Entry.Default<>(node.node, node.score, parentMap));
            }
            return new VectorSearchResult.Default<>(entries);
        }

        @Override
        public void optimize()
        {
            synchronized(this.parentMap())
            {
                this.ensureIndexInitialized();
                if(this.builder != null)
                {
                    this.builder.cleanup();
                }
                this.markStateChangeChildren();
            }
        }

        @Override
        public void persistToDisk()
        {
            if(!this.configuration.onDisk())
            {
                return; // No-op for in-memory indices
            }

            // Acquire write lock for exclusive access during persistence
            this.persistenceLock.writeLock().lock();
            try
            {
                synchronized(this.parentMap())
                {
                    this.ensureIndexInitialized();

                    // If we have an in-memory builder, write it to disk
                    if(this.builder != null && this.index != null)
                    {
                        // Cleanup the graph before writing (removes excess neighbors)
                        this.builder.cleanup();

                        // Initialize disk manager if needed
                        if(this.diskManager == null)
                        {
                            this.diskManager = new DiskIndexManager.Default(
                                this,
                                this.name,
                                this.configuration.indexDirectory(),
                                this.configuration.dimension(),
                                this.configuration.maxDegree()
                            );
                        }

                        // Create vector values for writing
                        final RandomAccessVectorValues ravv = this.createVectorValues();

                        // Write using disk manager
                        this.diskManager.writeIndex(this.index, ravv, this.pqManager);
                    }
                }
            }
            catch(final IOException ioe)
            {
                throw new IORuntimeException(ioe);
            }
            finally
            {
                this.persistenceLock.writeLock().unlock();
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
            // Shutdown optimization manager first (may optimize pending changes)
            this.shutdownOptimizationManager(this.configuration.optimizeOnShutdown());

            // Shutdown persistence manager second (may persist pending changes)
            this.shutdownPersistenceManager(this.configuration.persistOnShutdown());

            synchronized(this.parentMap())
            {
                this.closeInternalResources();
            }
        }

        /**
         * Shuts down the background optimization manager.
         *
         * @param optimizePending if true, optimize pending changes before shutdown
         */
        private void shutdownOptimizationManager(final boolean optimizePending)
        {
            if(this.optimizationManager != null)
            {
                this.optimizationManager.shutdown(optimizePending);
                this.optimizationManager = null;
            }
        }

        /**
         * Shuts down the background persistence manager.
         *
         * @param persistPending if true, persist pending changes before shutdown
         */
        private void shutdownPersistenceManager(final boolean persistPending)
        {
            if(this.persistenceManager != null)
            {
                this.persistenceManager.shutdown(persistPending);
                this.persistenceManager = null;
            }
        }

        /**
         * Closes internal resources (builder, index, disk resources).
         * Must be called within synchronized block.
         */
        private void closeInternalResources()
        {
            // Close searcher pool first (searchers reference the index)
            this.closeSearcherPool();

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

        // Note: BackgroundPersistenceManager.Callback.persistToDisk() is implemented
        // by the public persistToDisk() method above.

        // Note: BackgroundOptimizationManager.Callback.optimize() is implemented
        // by the public optimize() method above.

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

    }

}
