# GigaMap-JVector

A Java library that integrates [JVector](https://github.com/datastax/jvector) (high-performance vector similarity search) with [EclipseStore's GigaMap](https://github.com/eclipse-store/store) for persistent vector storage.

## Features

- **HNSW Vector Index**: Fast approximate k-nearest-neighbor search using JVector's HNSW graph implementation
- **Persistent Storage**: Vectors are stored in GigaMap for durability and lazy loading
- **On-Disk Index**: Memory-mapped graph storage for datasets larger than RAM
- **PQ Compression**: Product Quantization for reduced memory footprint
- **Background Persistence**: Automatic asynchronous persistence at configurable intervals
- **Background Optimization**: Periodic graph cleanup for improved query performance
- **Eventual Indexing**: Deferred graph mutations via background thread for reduced write latency
- **Parallel On-Disk Writes**: Multi-threaded index persistence for large on-disk indices
- **Lazy Entity Access**: Search results provide direct access to entities without additional lookups
- **Stream API**: Java Stream support for search results
- **GigaMap Integration**: Seamlessly integrates with GigaMap's index system

## Requirements

- Java 17+ (minimum)
- Java 20+ (recommended for SIMD acceleration)
- Maven 3.6+

## SIMD Acceleration (Panama Vector API)

JVector leverages the [Panama Vector API](https://openjdk.org/jeps/338) (`jdk.incubator.vector`) for hardware-accelerated vector operations. This provides significant performance improvements for ANN indexing and search through SIMD (Single Instruction, Multiple Data) instructions.

### Benefits

- **Faster distance calculations**: SIMD parallelizes vector arithmetic across CPU vector registers
- **Accelerated indexing**: Graph construction benefits from parallel similarity computations
- **Faster queries**: Nearest neighbor searches execute more quickly
- **Optimized PQ encoding**: Product Quantization compression uses SIMD for distance computations

### Java Version Requirements

| Java Version | SIMD Support |
|--------------|--------------|
| Java 17-19 | Functional, but **not optimized** (scalar fallback) |
| Java 20+ | **Full SIMD acceleration** via Panama Vector API |

JVector uses a multi-release JAR structure:
- Base code targets Java 11 compatibility
- Optimized vector code in `jvector-twenty` activates automatically on Java 20+ JVMs
- Earlier Java versions receive functional but non-SIMD implementations

### JVM Parameters

To enable the Panama Vector API, add the incubator module to your JVM arguments:

```bash
java --add-modules jdk.incubator.vector -jar your-app.jar
```

**Full example with all recommended flags:**
```bash
java --add-modules jdk.incubator.vector \
     -Djvector.physical_core_count=8 \
     -jar your-app.jar
```

**Maven Surefire/Failsafe configuration:**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>--add-modules jdk.incubator.vector</argLine>
    </configuration>
</plugin>
```

**Gradle configuration:**
```kotlin
tasks.withType<JavaExec> {
    jvmArgs("--add-modules", "jdk.incubator.vector")
}
```

> **Note**: The `jdk.incubator.vector` module is an incubator feature in Java 17-21. Starting with Java 22, the Vector API moved to preview status. JVector's multi-release JAR handles the API differences automatically, but the module must still be explicitly enabled.

### Performance Considerations

Intensive SIMD operations can saturate memory bandwidth during indexing and PQ computation. JVector mitigates this by using a `PhysicalCoreExecutor` that limits concurrency to physical cores rather than logical cores (hyperthreads).

**Default behavior**: Physical core count defaults to half the processor count visible to the JVM.

**Custom configuration**: Override using the system property:
```bash
java --add-modules jdk.incubator.vector \
     -Djvector.physical_core_count=8 \
     -jar your-app.jar
```

Or provide a custom `ForkJoinPool` when building indices.

### Recommendation

For optimal performance, run on **Java 21 LTS or later** to benefit from full SIMD acceleration. The performance difference can be substantial for large-scale vector operations.

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
| `parallelOnDiskWrite` | `false` | Use parallel direct buffers and multiple worker threads for on-disk index writing. Speeds up persistence for large indices but uses more resources. Only applies when `onDisk=true` |

### Eventual Indexing

| Parameter | Default | Description |
|-----------|---------|-------------|
| `eventualIndexing` | `false` | Defer HNSW graph mutations to a background thread. The vector store is updated synchronously, but graph construction happens asynchronously. Reduces mutation latency at the cost of eventual search consistency |

### Background Persistence

| Parameter | Default | Description |
|-----------|---------|-------------|
| `persistenceIntervalMs` | `0` | Check interval in milliseconds. A value > 0 enables background persistence, 0 disables it |
| `minChangesBetweenPersists` | `100` | Minimum changes before persisting |
| `persistOnShutdown` | `true` | Persist pending changes on close() |

### Background Optimization

| Parameter | Default | Description |
|-----------|---------|-------------|
| `optimizationIntervalMs` | `0` | Check interval in milliseconds. A value > 0 enables background optimization, 0 disables it |
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
    // Background persistence (enabled by setting interval > 0)
    .persistenceIntervalMs(30_000)       // Enable, check every 30 seconds
    .minChangesBetweenPersists(100)      // Only persist if >= 100 changes
    .persistOnShutdown(true)             // Persist on close()
    // Background optimization (enabled by setting interval > 0)
    .optimizationIntervalMs(60_000)      // Enable, check every 60 seconds
    .minChangesBetweenOptimizations(1000) // Only optimize if >= 1000 changes
    .optimizeOnShutdown(false)           // Skip for faster shutdown
    .build();
```

### Eventual Indexing

For high-throughput systems where mutation latency matters more than immediate search consistency:

```java
VectorIndexConfiguration config = VectorIndexConfiguration.builder()
    .dimension(768)
    .similarityFunction(VectorSimilarityFunction.COSINE)
    // Eventual indexing (graph mutations deferred to background thread)
    .eventualIndexing(true)
    .build();
```

When enabled, the vector store is always updated synchronously (no data loss), but expensive HNSW graph mutations are queued and applied by a background worker thread. Search results may not immediately reflect the most recent mutations. The queue is automatically drained before `optimize()`, `persistToDisk()`, and `close()`.

### Parallel On-Disk Writes

For large on-disk indices where persistence speed is critical:

```java
VectorIndexConfiguration config = VectorIndexConfiguration.builder()
    .dimension(768)
    .similarityFunction(VectorSimilarityFunction.COSINE)
    .onDisk(true)
    .indexDirectory(Path.of("/data/vectors"))
    // Parallel on-disk writing (multiple worker threads)
    .parallelOnDiskWrite(true)
    .build();
```

When enabled, the on-disk graph writer uses parallel direct buffers and multiple worker threads (one per available processor) to write the index concurrently. This is disabled by default as sequential writing is preferred in resource-constrained environments or for smaller indices.

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

- **Null vectors are not accepted**: The `Vectorizer.vectorize()` method must never return `null`. If it does, an `IllegalStateException` is thrown. Ensure that every entity added to the GigaMap can produce a valid vector.
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
