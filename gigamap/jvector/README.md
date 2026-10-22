# GigaMap-JVector

A Java library that integrates [JVector](https://github.com/datastax/jvector) (high-performance vector similarity search) with [EclipseStore's GigaMap](https://github.com/eclipse-store/store) for persistent vector storage.

## Features

- **HNSW Vector Index**: Fast approximate k-nearest-neighbor search using JVector's HNSW graph implementation
- **Memory-Mapped Vector File**: Vectors live in a memory-mapped file per index, a cache of what the vectorizer derives from the entities; computed vectors are not stored in the storage
- **On-Disk Index**: Graph stored as immutable segments committed by a manifest, memory-mapped, for datasets larger than RAM; background merges keep the segment count low
- **PQ Compression**: Product Quantization codes for traversing a graph larger than the page cache with fewer reads (trades disk or heap and some recall for I/O)
- **NVQ Storage**: quantized vectors in the on-disk graph, about 3x smaller than full precision (scores stay exact; costs a little candidate recall only when `approximateScoring` is `NONE`)
- **Background Persistence**: Automatic asynchronous persistence at configurable intervals
- **Background Optimization**: Periodic graph cleanup for improved query performance
- **Eventual Indexing**: Deferred graph mutations via background thread for reduced write latency
- **Parallel On-Disk Writes**: Multi-threaded index persistence for large on-disk indices
- **Lazy Entity Access**: Search results provide direct access to entities without additional lookups
- **Stream API**: Java Stream support for search results
- **GigaMap Integration**: Seamlessly integrates with GigaMap's index system

## Requirements

- Java 25+ (the module compiles at release 25; the rest of Eclipse Store stays at 17)
- Maven 3.6+
- JVM flags: `--add-modules jdk.incubator.vector` for SIMD scoring, `--enable-native-access=ALL-UNNAMED` for jVector's memory-mapped reader. On the module path jVector is the automatic module `jvector`, so use `--enable-native-access=jvector` there

## Upgrading from 4.x

The vectors of an index now live in a memory-mapped **vector file** (`<name>.vec` plus `<name>.vec-<n>` chunks) instead of a persisted vector store, and the on-disk graph is a set of segments committed by a manifest instead of one `<name>.graph/.meta/.pqv` file set. A storage written by 4.0.0 to 4.2.0 opens without preparation:

- The persisted index record of the old layouts (with or without `structuralModCount`) is read by a legacy type handler. A computed index imports the vectors of its old vector store into the vector file at its first use, **without calling the vectorizer**; an embedded index reads its vectors from the entities as before.
- The next store of the map persists the index in the current layout; the old vector store becomes unreferenced and is reclaimed by the storage's housekeeping.
- The old graph files of an on-disk index are ignored, logged once at the first load, and can be removed. The graph is rebuilt from the vector file and persisted as segments beside them at the next persist, which is scheduled even if the index is only searched.
- **One way:** a storage stored by this version cannot be opened by 4.x, or by an earlier build without the vector file, any more: they fail at start with `Missing runtime type ... VectorIndexFormat` (4.2.0 already at `VectorStorage`). Until the first store on this version, the storage still opens with the old version. Back up the storage before upgrading.
- An **in-memory index with a computed vectorizer** keeps its vector file in its index directory if one is configured (`indexDirectory` or `indexLocation`), and then restarts without a vectorizer call per entity. Without a directory its vector file is temporary and every start vectorizes every entity; configure a directory for a vectorizer backed by an embedding service. **A 4.x computed in-memory index without a directory imports its persisted vectors into a temporary file, which the current layout cannot keep, so storing the map is refused until the index has a location: give it one with `VectorIndices.changeIndexLocation`, which moves the imported vectors there at once without a vectorizer call. To drop them deliberately instead, and vectorize every entity at every start, set the system property `org.eclipse.store.gigamap.jvector.dropLegacyVectorsWithoutLocation=true`.** `changeIndexLocation` also gives any other computed in-memory index without a directory its first location this way.
- Temporary vector files live under `<java.io.tmpdir>/eclipsestore-vectors`, or under `eclipsestore-vectors` in the directory the system property `org.eclipse.store.gigamap.jvector.temporaryRoot` names. Only that subdirectory is written and cleaned up: abandoned directories of temporary vector files are deleted there, anything else is left alone.
- `Vectorizer.vectorize` must reproduce the committed vector of a persisted entity: the vector file is a cache, the entity is the source of truth. `Vectorizer.vectorKey` lets an update skip the vectorizer.

## SIMD Acceleration (Panama Vector API)

JVector leverages the [Panama Vector API](https://openjdk.org/jeps/338) (`jdk.incubator.vector`) for hardware-accelerated vector operations. This provides significant performance improvements for ANN indexing and search through SIMD (Single Instruction, Multiple Data) instructions.

### Benefits

- **Faster distance calculations**: SIMD parallelizes vector arithmetic across CPU vector registers
- **Accelerated indexing**: Graph construction benefits from parallel similarity computations
- **Faster queries**: Nearest neighbor searches execute more quickly
- **Optimized PQ encoding**: Product Quantization compression uses SIMD for distance computations

### Java Version Requirements

| JVM flag | Effect |
|--------------|--------------|
| without `--add-modules jdk.incubator.vector` | Functional, but **not optimized** (scalar fallback) |
| with `--add-modules jdk.incubator.vector` | **Full SIMD acceleration** via Panama Vector API |

JVector uses a multi-release JAR structure; on the Java 25+ JVM this module requires, the optimized vector code activates as soon as the incubator module is resolved, and jVector's memory-mapped reader uses the foreign memory API (`--enable-native-access=ALL-UNNAMED`).

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
        <argLine>--add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED</argLine>
    </configuration>
</plugin>
```

**Gradle configuration:**
```kotlin
tasks.withType<JavaExec> {
    jvmArgs("--add-modules", "jdk.incubator.vector", "--enable-native-access=ALL-UNNAMED")
}
```

> **Note**: The `jdk.incubator.vector` module is still an incubator module on Java 22 and later. JVector's multi-release JAR handles the API differences automatically, but the module must still be explicitly enabled.

### Performance Considerations

Intensive SIMD operations can saturate memory bandwidth during indexing and PQ computation. JVector mitigates this by using a `PhysicalCoreExecutor` that limits concurrency to physical cores rather than logical cores (hyperthreads).

**Default behavior**: Physical core count defaults to half the processor count visible to the JVM.

**Custom configuration**: Override using the system property:
```bash
java --add-modules jdk.incubator.vector \
     -Djvector.physical_core_count=8 \
     -jar your-app.jar
```

### Recommendation

Run on **Java 25 or later** (required by this module) with `--add-modules jdk.incubator.vector` to benefit from full SIMD acceleration. The performance difference can be substantial for large-scale vector operations.

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
for (ScoredSearchResult.Entry<Document> entry : result) {
    Document doc = entry.entity();    // Lazy entity access
    float score = entry.score();      // Similarity score
    long id = entry.entityId();       // Entity ID
}

// Stream API
List<Document> topDocs = result.stream()
    .filter(e -> e.score() > 0.8f)
    .map(ScoredSearchResult.Entry::entity)
    .toList();
```

## Configuration Options

### Basic HNSW Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `dimension` | (required) | Vector dimensionality |
| `similarityFunction` | `COSINE` | Similarity metric (`COSINE`, `DOT_PRODUCT`, `EUCLIDEAN`) |
| `maxDegree` | 16 | Maximum connections per node in HNSW graph. Higher improves recall, costs memory. With PQ it also multiplies the per-node fused block (`pqSubspaces * maxDegree` bytes) |
| `beamWidth` | 100 | Beam width during index construction (HNSW *efConstruction*). Higher improves graph quality, slows construction. No effect at query time |
| `minSearchBeamWidth` | 100 | Minimum beam width during search (*efSearch* floor); effective width is `max(k, minSearchBeamWidth)`. Raising it recovers recall lost to a small `pqSubspaces` |
| `neighborOverflow` | 1.2 | Candidate neighbours kept before pruning, as a multiple of `maxDegree`. Slightly better selection for slightly more memory while building; minimal effect on the finished index |
| `alpha` | 1.2 | RNG pruning aggressiveness. Higher gives a sparser graph and faster queries at some recall cost; raise only under memory pressure |

### On-Disk Storage

| Parameter | Default | Description |
|-----------|---------|-------------|
| `onDisk` | `false` | Store the graph in a memory-mapped file rather than on the Java heap. This is what lets an index exceed RAM; in-memory mode is faster per query but keeps the graph on the heap |
| `indexDirectory` | `null` | Directory for index files: segments, manifest and vector file (required if `onDisk=true`, unless `indexLocation` names one). An in-memory index with a computed vectorizer keeps its vector file there too, and restarts without a vectorizer call per entity |
| `indexLocation` | `null` | A named location resolved through `IndexLocations` at load, instead of a fixed directory; see `VectorIndices.changeIndexLocation` |
| `vectorStorage` | `INLINE` | How the graph stores each vector. `NVQ` stores 8-bit quantized vectors instead of full precision - about **3x smaller**, the only setting that shrinks the file. Reranking still reads the full-precision vectors of the vector file, so scores stay exact; with `approximateScoring(NONE)` it also changes which candidates traversal finds |
| `approximateScoring` | `NONE` | How traversal scores candidates. Keep `NONE` while the graph fits in the page cache: scoring from PQ codes saves reads, not arithmetic, so there it is slower and finds fewer of the true nearest neighbours. `FUSED_PQ` writes Product Quantization codes into every node, so traversing a graph **larger than the page cache** reads one block per hop, but it **makes the graph file larger**, costs recall and adds transient heap at persist time. `PQ_IN_MEMORY` buys the same traversal with the codes in heap instead: nothing added to the graph, `pqSubspaces` bytes per ordinal resident. An on-disk index persists them in a `.pqv` sidecar; an in-memory one has no persist, so it builds them during optimization and has no sidecar at all. Also the only PQ-based mode an in-memory index can use - `NONE` stays the default there - where it needs `optimizationIntervalMs > 0` and a `minChangesBetweenOptimizations` the index will actually reach |
| `nvqSubvectors` | `0` | Number of NVQ subvectors (0 = auto: 1). Each one adds a fixed 28 bytes per node, so the default is almost always right. **Not** the same parameter as `pqSubspaces` |
| `enablePqCompression` | `false` | *Deprecated*, use `approximateScoring`. A literal delegate: `true` means `FUSED_PQ` |
| `pqSubspaces` | `0` | Number of PQ subspaces (0 = auto: dimension/4). Costs `pqSubspaces * maxDegree` bytes per node, so a smaller value than classic PQ guidance suggests is usually right - paired with a wider `minSearchBeamWidth` |
| `parallelOnDiskWrite` | `false` | Use parallel direct buffers and multiple worker threads for on-disk index writing. Speeds up persistence for large indices but uses more resources. Only applies when `onDisk=true` |

> **On-disk format:** an on-disk index is a set of immutable segments (`<name>.seg-<n>.graph`, `.l2g`, and `.pqv` with `PQ_IN_MEMORY`) committed by `<name>.manifest`, which records the stored state they describe. A manifest that does not match the stored index, or a segment that fails to open, is rejected: the graph is rebuilt from the vector file and the next persist replaces the files. Graph files of earlier versions (`<name>.graph/.meta/.pqv`) are ignored and logged once; they can be removed.

### Segments and the Vector File

| Parameter | Default | Description |
|-----------|---------|-------------|
| `flushThreshold` | `100000` | Nodes the in-memory graph of an on-disk index must hold before a background persist writes it as a new segment. Keep it large: small segments add search cost without making anything more durable. An explicit `persistToDisk()` and the shutdown persist write regardless |
| `segmentMergeFactor` | `4` | How many segments of a size tier a background merge combines into one (2-240). Larger merges less often and leaves more segments for every search to visit |
| `maxSegments` | `32` | The most segments a persist keeps (2-240); only if merges fell behind does a persist consolidate them into one, blocking mutations and searches while it rebuilds |
| `vectorCheckpointIntervalMs` | `30000` | Interval at which the vector file is forced and its chunks marked safe. After a crash only chunks written since the last checkpoint are recovered, by re-vectorizing their entities. Shorter costs more header writes, longer more recovery |
| `vectorChunkBytes` | `64 MB` | Size of one vector-file chunk (at least 2048 vectors). A chunk is written out in full when created, so a full disk fails as an exception, and it is the unit of recovery. Changing it for an existing index re-vectorizes every entity at the next start |

The vector file (`<name>.vec`, chunks `<name>.vec-<n>`) is a cache of what the vectorizer derives from the entities, not a copy the storage commits: it must stay with the storage it belongs to. Deleting it only makes the next start re-vectorize every entity; restoring it separately from the storage is not supported. An index without a directory (every embedded in-memory index, and a computed one without `indexDirectory`/`indexLocation`) uses a temporary vector file and re-vectorizes every entity at each start.

### Eventual Indexing

| Parameter | Default | Description |
|-----------|---------|-------------|
| `eventualIndexing` | `false` | Defer HNSW graph mutations to a background thread. The vector file is updated synchronously, but graph construction happens asynchronously. Reduces mutation latency at the cost of eventual search consistency |

### Background Persistence

| Parameter | Default | Description |
|-----------|---------|-------------|
| `persistenceIntervalMs` | `0` | Check interval in milliseconds. A value > 0 enables background persistence, 0 disables it |
| `minChangesBetweenPersists` | `100` | Debounce threshold. Lower keeps the on-disk graph fresher at the cost of write amplification; higher means a crash discards more work for the load-time rebuild to redo |
| `persistOnShutdown` | `true` | Persist pending changes on close() when `onDisk=true`. Applies whether or not background persistence is enabled |
| `shutdownPersistTimeoutMillis` | `30000` | How long `close()` waits for the final drain, optimize and persist before interrupting it. An aborted persist loses no data - the vectors are in the vector file - it only costs a graph rebuild from that file on the next start (and, after a crash, re-vectorizing what the last checkpoint did not cover) |

### Background Optimization

| Parameter | Default | Description |
|-----------|---------|-------------|
| `optimizationIntervalMs` | `0` | Check interval in milliseconds. A value > 0 enables background optimization, 0 disables it |
| `minChangesBetweenOptimizations` | `1000` | Debounce threshold; should exceed `minChangesBetweenPersists`, since a cleanup is the more expensive operation |
| `optimizeOnShutdown` | `false` | Run a cleanup during `close()`. Off by default: trades a slower first query after restart for a faster exit |

## Advanced Usage

### On-Disk Index with Compression

For on-disk graphs larger than the page cache, where reads dominate search latency. While the graph fits in memory, `NONE` is both faster and more accurate. Note that PQ keeps the full-precision vectors and adds the fused codes on top, so it saves reads at the cost of a larger graph file and some recall - on-disk mode, not PQ, is what lets an index exceed available memory:

```java
VectorIndexConfiguration config = VectorIndexConfiguration.builder()
    .dimension(768)
    .similarityFunction(VectorSimilarityFunction.COSINE)
    .maxDegree(32)
    .beamWidth(200)
    // On-disk storage
    .onDisk(true)
    .indexDirectory(Path.of("/data/vectors"))
    // PQ compression (fewer reads per hop on a graph larger than the page cache; larger graph file)
    .approximateScoring(ApproximateScoring.FUSED_PQ)
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

When enabled, the vector file is always updated synchronously (no data loss), but expensive HNSW graph mutations are queued and applied by a background worker thread. Search results may not immediately reflect the most recent mutations. The queue is automatically drained before `optimize()`, `persistToDisk()`, and `close()`.

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

`optimize()`, `persistToDisk()` (on-disk) and `close()` must not be called while holding the GigaMap's monitor, i.e. inside `synchronized(map)` or `update`/`apply` logic: they wait for work whose holders may need that monitor, and throw an `IllegalStateException` instead. `optimize()` returns at once for an on-disk index, and `persistToDisk()` for an in-memory one.

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

Shutting the storage down closes every index of the map; there is no need to close them explicitly.

Change an entity's vector source only through the GigaMap (`update`/`apply`, or `set`/`replace` with a new instance). A field mutated in place without them is not seen by the index, which keeps the old vector until a recovery or `reindex()` re-vectorizes the entity.

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

- **Null vectors are rejected unless opted into**: `Vectorizer.vectorize()` returning `null` throws an `IllegalStateException` by default. Override `Vectorizer.allowsNullVectors()` to return `true` for a corpus where some entities legitimately have no embedding; they are then skipped rather than indexed, leaving holes in the ordinal space that the index handles.
- **~2.1 billion vectors per index**: JVector uses `int` for graph node ordinals. For larger datasets, implement sharding across multiple indices.
- **Java 25 or later**: the module targets Java 25 and uses the foreign memory API for its vector file.
- **The vector file must stay with its storage**: it is a cache derived from the entities, rebuilt (one vectorizer call per entity) when it is missing or does not match the stored index. Back up and restore the index directory together with the storage.
- **`FUSED_PQ` scoring enlarges the index**: it stores each node's neighbour codes inline, on top of whatever the storage mode holds. It saves reads on a graph larger than the page cache, not disk space, and it costs recall. To make the index *smaller*, set `vectorStorage` to `NVQ`, which is the other dimension of the format entirely.
- **The in-memory switch to `PQ_IN_MEMORY` stops the whole GigaMap while it runs**: reads, writes and searches all wait for it, on every index on that map. It is a full rebuild under the map's lock, close to linear in the vector count - about 4 s at 10,000 vectors, 6-9 s at 20,000, 14-19 s at 50,000, several minutes at a million (dimension 64). It happens at most once per index generation, but it is triggered by the *background* optimization, so on a large index call `optimize()` at a quiet moment rather than letting the scheduler pick one.
- **`PQ_IN_MEMORY` moves that cost to heap, and it is linear**: `pqSubspaces` bytes for every ordinal up to the highest in use, deletion holes included, resident for as long as the index is open. About 192 MB per million vectors at the default subspace count for dimension 768; about 19 GB at a hundred million. Size it before choosing it.
- **NVQ storage never costs score accuracy**: the graph keeps no full-precision copy, but reranking compares against the full-precision vectors of the vector file, so the scores and the ordering a search returns are exact. With `approximateScoring(NONE)` traversal reads the quantized vectors, so quantization changes which candidates it finds, and a candidate it never reaches is one reranking cannot recover. With `FUSED_PQ` the fused codes drive traversal, so candidate selection is unchanged and the cost is footprint plus a vector-file read per reranked candidate.

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
