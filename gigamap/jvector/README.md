# GigaMap-JVector

A Java library that integrates [JVector](https://github.com/datastax/jvector) (high-performance vector similarity search) with [EclipseStore's GigaMap](https://github.com/eclipse-store/store) for persistent vector storage.

## Features

- **HNSW Vector Index**: Fast approximate k-nearest-neighbor search using JVector's HNSW graph implementation
- **Persistent Storage**: Vectors are stored in GigaMap for durability and lazy loading
- **On-Disk Index**: Memory-mapped graph storage for datasets larger than RAM
- **PQ Compression**: Product Quantization for reduced memory footprint
- **Background Persistence**: Automatic asynchronous persistence at configurable intervals
- **Background Optimization**: Periodic graph cleanup for improved query performance
- **Lazy Entity Access**: Search results provide direct access to entities without additional lookups
- **Stream API**: Java Stream support for search results
- **GigaMap Integration**: Seamlessly integrates with GigaMap's index system

## Requirements

- Java 17+
- Maven 3.6+

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.eclipse.store</groupId>
    <artifactId>gigamap-jvector</artifactId>
    <version>current-version</version>
</dependency>
```

## Quick Start

```java
// Create GigaMap and register vector indices
GigaMap<Document> gigaMap = GigaMap.New();
VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

// Configure and add a vector index
VectorIndexConfiguration config = VectorIndexConfiguration.builder()
    .dimension(768)
    .similarityFunction(VectorSimilarityFunction.COSINE)
    .build();

VectorIndex<Document> index = vectorIndices.add("embeddings", config, new DocumentVectorizer());

// Add entities (automatically indexed)
gigaMap.add(new Document("Hello world", embedding));

// Search for similar vectors
VectorSearchResult<Document> result = index.search(queryVector, 10);
for (VectorSearchResult.Entry<Document> entry : result) {
    Document doc = entry.entity();    // Lazy entity access
    float score = entry.score();      // Similarity score
    long id = entry.entityId();       // Entity ID
}

// Stream API
List<Document> topDocs = result.stream()
    .filter(e -> e.score() > 0.8f)
    .map(VectorSearchResult.Entry::entity)
    .toList();
```

## Configuration Options

### Basic HNSW Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `dimension` | (required) | Vector dimensionality |
| `similarityFunction` | `COSINE` | Similarity metric (`COSINE`, `DOT_PRODUCT`, `EUCLIDEAN`) |
| `maxDegree` | 16 | Maximum connections per node in HNSW graph |
| `beamWidth` | 100 | Search beam width during index construction |
| `neighborOverflow` | 1.2 | Overflow factor for neighbor lists |
| `alpha` | 1.2 | Pruning parameter |

### On-Disk Storage

| Parameter | Default | Description |
|-----------|---------|-------------|
| `onDisk` | `false` | Enable on-disk graph storage |
| `indexDirectory` | `null` | Directory for index files (required if `onDisk=true`) |
| `enablePqCompression` | `false` | Enable Product Quantization compression |
| `pqSubspaces` | `0` | Number of PQ subspaces (0 = auto: dimension/4) |

### Background Persistence

| Parameter | Default | Description |
|-----------|---------|-------------|
| `backgroundPersistence` | `false` | Enable automatic background persistence |
| `persistenceIntervalMs` | `30000` | Check interval in milliseconds |
| `minChangesBetweenPersists` | `100` | Minimum changes before persisting |
| `persistOnShutdown` | `true` | Persist pending changes on close() |

### Background Optimization

| Parameter | Default | Description |
|-----------|---------|-------------|
| `backgroundOptimization` | `false` | Enable automatic graph optimization |
| `optimizationIntervalMs` | `60000` | Check interval in milliseconds |
| `minChangesBetweenOptimizations` | `1000` | Minimum changes before optimizing |
| `optimizeOnShutdown` | `false` | Optimize pending changes on close() |

## Advanced Usage

### On-Disk Index with Compression

For large datasets that exceed available memory:

```java
VectorIndexConfiguration config = VectorIndexConfiguration.builder()
    .dimension(768)
    .similarityFunction(VectorSimilarityFunction.COSINE)
    .maxDegree(32)
    .beamWidth(200)
    // On-disk storage
    .onDisk(true)
    .indexDirectory(Path.of("/data/vectors"))
    // PQ compression (reduces memory significantly)
    .enablePqCompression(true)
    .pqSubspaces(48)  // Must divide dimension evenly
    .build();
```

### Background Persistence and Optimization

For production systems with continuous updates:

```java
VectorIndexConfiguration config = VectorIndexConfiguration.builder()
    .dimension(768)
    .similarityFunction(VectorSimilarityFunction.COSINE)
    // On-disk storage
    .onDisk(true)
    .indexDirectory(Path.of("/data/vectors"))
    // Background persistence (async, non-blocking)
    .backgroundPersistence(true)
    .persistenceIntervalMs(30_000)       // Check every 30 seconds
    .minChangesBetweenPersists(100)      // Only persist if >= 100 changes
    .persistOnShutdown(true)             // Persist on close()
    // Background optimization (periodic cleanup)
    .backgroundOptimization(true)
    .optimizationIntervalMs(60_000)      // Check every 60 seconds
    .minChangesBetweenOptimizations(1000) // Only optimize if >= 1000 changes
    .optimizeOnShutdown(false)           // Skip for faster shutdown
    .build();
```

### Manual Optimization and Persistence

```java
// Optimize graph (removes excess neighbors, improves query latency)
index.optimize();

// Persist to disk (for on-disk indices)
index.persistToDisk();

// Close index (runs shutdown hooks based on config)
index.close();
```

## Persistence with EclipseStore

Binary type handlers are registered automatically:

```java
try (EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir)) {
    GigaMap<Document> gigaMap = GigaMap.New();
    storage.setRoot(gigaMap);

    VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
    VectorIndex<Document> index = vectorIndices.add("embeddings", config, new DocumentVectorizer());

    gigaMap.add(new Document("text", embedding));

    storage.storeRoot();
}
```

## Benchmarking

The library includes benchmark tests following [ANN-Benchmarks](https://ann-benchmarks.com/) methodology:

```bash
# Run benchmark tests (disabled by default)
mvn test -Dtest=VectorIndexBenchmarkTest \
    -Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition
```

### Benchmark Results (10K vectors, 128 dimensions)

| Metric | Result |
|--------|--------|
| **Recall@10** (clustered data) | 94.3% |
| **Recall@50** (clustered data) | 100% |
| **QPS** (queries/second) | ~10,000+ |
| **Average latency** | < 0.1ms |
| **p99 latency** | < 0.2ms |

### SIFT Dataset Benchmark

To benchmark with real SIFT data:

1. Download: `ftp://ftp.irisa.fr/local/texmex/corpus/siftsmall.tar.gz`
2. Extract to: `src/test/resources/sift/`
3. Run: `mvn test -Dtest=VectorIndexBenchmarkTest#testRecallWithSiftData ...`

## Parameter Guidelines

| Use Case | maxDegree | beamWidth | Notes |
|----------|-----------|-----------|-------|
| Small dataset (<10K) | 8-16 | 50-100 | Lower values sufficient |
| Medium dataset (10K-1M) | 16-32 | 100-200 | Balanced trade-off |
| Large dataset (>1M) | 32-64 | 200-400 | Higher for better recall |
| High precision required | 48-64 | 400-500 | Maximum recall |

## Limitations

- **~2.1 billion vectors per index**: JVector uses `int` for graph node ordinals. For larger datasets, implement sharding across multiple indices.
- **PQ compression requires maxDegree=32**: FusedPQ algorithm constraint (auto-enforced).

## Building

```bash
# Build
mvn compile

# Run tests
mvn test

# Package
mvn package

# Clean and build
mvn clean install
```
