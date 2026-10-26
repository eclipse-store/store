# GigaMap JVector — Architecture

This document describes the internals of the `gigamap-jvector` module for maintainers and reviewers. For configuration, presets and examples see [`README.md`](README.md); for the end-user documentation see [`docs/modules/gigamap/pages/indexing/jvector/`](../../docs/modules/gigamap/pages/indexing/jvector).

All classes named here live in `org.eclipse.store.gigamap.jvector` (`src/main/java/org/eclipse/store/gigamap/jvector/`) unless a package is given. Most of the logic is in `VectorIndex.Default`; the javadoc and the long comments there are the detailed reference for each mechanism summarised below.

---

## 1. Overview

The module adds approximate k-nearest-neighbour search to a `GigaMap<E>` through jVector's HNSW graphs (jVector `4.0.1`).

- **`VectorIndices<E>`** is an `IndexGroup` registered on the map (`gigaMap.index().register(VectorIndices.Category())`). It holds named indices and fans every map mutation out to them. GigaMap calls its `internal*` methods under the map's monitor.
- **`VectorIndex<E>`** (implementation `VectorIndex.Default`) is one named index: a configuration (`VectorIndexConfiguration`), a `Vectorizer<? super E>` and the transient search structures. The GigaMap entity id is the graph's global ordinal (an `int`, hence about 2.1 billion vectors per index).
- **`Vectorizer<E>`** turns an entity into a `float[]`. `vectorKey` (default `Vectorizer.NO_KEY`) is an optional version key that lets an update skip `vectorize` when the key is unchanged. `allowsNullVectors` permits entities without a vector.

### Where the vectors live

Every vector of an index, in both vectorizer modes, is kept in a memory-mapped **vector file** (`VectorFile`), off the heap, and every scoring path reads it without a lock. The storage holds **no vectors**: the persisted index record carries only the identity and the epoch of that file (section 4). The file is a cache. The entity is the source of truth, so `Vectorizer.vectorize` must reproduce the committed vector of a persisted entity, because recovery re-vectorizes entities whose slots the file cannot prove (section 4.4).

`Vectorizer.isEmbedded()` describes where the vector comes from:

- **Embedded**: the vector is a field of the entity, so re-vectorizing is cheap.
- **Computed**: the vector is computed or fetched, for example from an embedding API.

Both modes use the same vector file. The flag matters in two places only:

- `VectorIndex#keepsIndexFiles()` = `onDisk || !isEmbedded && indexLocation != null`. An in-memory index with a computed vectorizer keeps its vector file in a configured directory. Every other in-memory index uses a temporary file and re-vectorizes every entity at each start.
- `internalReindex` collects the computed vectors on the heap before it clears the index.

### In-memory vs on-disk index

| | In-memory (`onDisk = false`) | On-disk (`onDisk = true`) |
|---|---|---|
| Vector file | temporary, or in the index directory if `keepsIndexFiles()` | `<dir>/<name>.vec` plus chunks |
| Graph | one live `GraphIndexBuilder` / `OnHeapGraphIndex` | live graph, plus frozen `HeapGeneration`s, plus immutable `DiskSegment`s |
| After a restart | graph rebuilt from the vector file at first access | segments loaded from the manifest; live graph empty |
| `persistToDisk()` | no-op | flush the live graph into a new segment and commit the manifest |
| `optimize()` | purges deleted nodes, enforces degree, repairs reachability; may switch to in-memory PQ | no-op (each flush cleans up the graph it writes) |
| Owner bytes / `SourceSet` | none | yes (section 5) |

---

## 2. Component map

```
                    GigaMap<E>  (monitor = the single writer lock of the map)
                        |  internalAdd / Update / Remove / RemoveAll, internalPrepareStore,
                        |  internalPrepareMutation, internalTryLockExclusive
                        v
                 VectorIndices.Default  (IndexGroup, persisted)
                        |
                        v
                 VectorIndex.Default  (persisted record + transient state)
     +----------+-------+---------+-------------+--------------+-------------------+
     |          |                 |             |              |                   |
 VectorFile  live builder    SourceSet       DiskIndexManager  BackgroundTaskManager  PQ/NVQ managers
 (.vec)      GraphIndexBuilder  segments      (manifest,        (indexing queue,      PQCompressionManager
             OnHeapGraphIndex   frozen gens   segment files,    scheduled optimize/   NVQCompressionManager
             GenerationVectorValues  OwnerTable  load/commit)   persist, shutdown)
                                   |
                                   v
                              SegmentOwners (one owner byte per entity id)
```

| Type | Role |
|---|---|
| `VectorIndices` (+`Default`) | Named registry, `IndexGroup` of the map; persisted by `BinaryHandlerVectorIndicesDefault`. |
| `VectorIndex` (+`Internal`, `Default`) | Per-index orchestrator. `Default` also implements `BackgroundTaskManager.Callback`, `TrainingVectorProvider` and `DiskIndexManager.IndexStateProvider`. |
| `VectorFile` (+`Default`) | Memory-mapped vectors, one slot per ordinal, seqlock readers, epochs, crash recovery (section 3.1). |
| `MappedVectorValues` (+`Caching`) | `RandomAccessVectorValues` over the vector file; `Caching` keeps a per-query `OrdinalVectorCache`. |
| `GenerationVectorValues` | Score source of a live builder; after a freeze it keeps the freeze-time vectors of its nodes (copy-on-write). |
| `NullSafeVectorValues` | Returns a placeholder vector (`PLACEHOLDER_COMPONENT = 1e-6f` per component) for an absent ordinal, so traversal never meets `null`. |
| `SegmentVectorValues` | Maps a segment's dense local ordinals to global ordinals for scoring. |
| `DiskIndexManager` (+`Default`) | Manifest commit, segment write and load, retirement of a rejected manifest. |
| `SegmentManifest`, `SegmentFiles`, `DiskSegment`, `SegmentSequences`, `DenseOrdinalMapper`, `GraphFormat` | On-disk segment layout (section 3.2). |
| `SourceSet`, `IndexSource`, `HeapGeneration`, `OwnerTable`, `SegmentOwners` | Searchable sources of an on-disk index and which one owns each entity (section 5). |
| `SegmentMergePolicy` | Size-tiered selection for the background merge. |
| `ParallelGraphInsertion` | Parallel graph insertion on private `ForkJoinPool`s. |
| `GraphReachability` | Reconnects nodes that no layer-0 walk reaches; `NodesLostException`. |
| `BackgroundTaskManager` | Eventual-indexing queue, scheduled optimization and persistence, shutdown work. |
| `PQCompressionManager`, `NVQCompressionManager` | Train or adopt a PQ codebook / NVQ quantizer. |
| `BinaryHandlerVectorIndexDefault`, `BinaryLegacyTypeHandlerVectorIndexDefault`, `BinaryHandlerVectorIndicesDefault` | Persistence of the records, including the 4.x layouts. |
| `VectorEntry` | Entry type of the 4.x vector store; kept only so that old storages load. |
| `AnnotationVectorizer`, `VectorAnnotationHandler`, `annotations.Vector` | Embedded vectorizer from a `@Vector`-annotated `float[]` field or getter. |
| `VectorSearchResult` | Search result; `entity()` resolves lazily through the map. |

---

## 3. Data layout on disk

### 3.1 The vector file

**Files.**

- `<name>.vec` (`VectorFile.HEADER_FILE_EXT`) is the header file.
- Chunk `n` is `<name>.vec-<n>`.
- `VectorFile.isVectorFile` recognises both names. Neither matches the segment layout pattern, so the two layers never delete each other's files.

**Slots.** One slot per global ordinal, overwritten in place. A slot is `strideOf(dimension)` bytes: a 32-byte slot header plus the floats, rounded up to 64 bytes.

| Offset | Content |
|---|---|
| 0 (`SLOT_SEQ`) | sequence word of the per-slot seqlock (odd while a write is in progress) |
| 8 (`SLOT_EPOCH`) | `epoch << 2` combined with `FLAG_PRESENT` or `FLAG_TOMBSTONE` |
| 16 (`SLOT_KEY`) | the vector key (`Vectorizer.vectorKey`) |
| 24 | reserved |
| 32 (`SLOT_FLOATS`) | `dimension` floats |

A slot that was never written is all zeros and reads as absent.

- **Writer.** There is one writer, under the parent GigaMap monitor (the index's contract; `VectorFile` does not enforce it). `writeAt` makes the sequence odd, copies the floats, writes the epoch/flags word and the key, and releases the sequence even. A write makes the slot either present or a tombstone.
- **Readers** (`read`) are lock-free. They retry while the sequence is odd or has changed. No raw pointer outlives the shared `Arena`: a read after `close()` fails with an exception instead of touching unmapped memory.

**Chunks.**

- Size: `chunkBytes` = `vectorChunkBytes`, by default the larger of `VectorFile.DEFAULT_CHUNK_BYTES` (64 MiB) and 2 048 slots (`MIN_SLOTS_PER_CHUNK`).
- Ordinal `o` lives in chunk `o / slotsPerChunk`.
- A new chunk is written with real zeros, so a full disk fails with an `IOException` at creation and not with a `SIGBUS` on a later page fault.
- The chunk table grows copy-on-write.

**Hot and cold chunks.**

- A chunk turns **hot** at the first write after it was cold (`hotChunk`). The header is rewritten and forced with the chunk's hot bit set, `clean = false` and the current boot id, all *before* the write proceeds. If the header write fails, the chunk stays cold.
- A **checkpoint** (`checkpoint`) forces (`msync`) every hot chunk. Under `headerLock` it then turns cold every chunk whose last write is at or below `min(committedEpoch, W - 1)`, with W sampled before the force, and writes the header.
- Checkpoints run:
  - every `vectorCheckpointIntervalMs` (default 30 s), on the shared daemon thread `eclipsestore-vector-checkpoint`;
  - after a published flush, if one is due (`isCheckpointDue`);
  - on `close()`.
- Data pages are also forced when a repaired chunk is finished (`repaired`). Temporary files are never forced.

**Header.** `<name>.vec`, little-endian. It holds two alternating records at power-of-two offsets, each with a record sequence and a trailing CRC32. `Header.read` takes the valid record with the highest sequence, so a torn header write leaves the previous record readable. Fields:

- `MAGIC` and `VERSION`;
- dimension, stride, `chunkBytes`, byte order;
- `nonce`;
- `forcedEpoch`, `highestEpoch`, `coldEpoch`;
- `bootId`, `clean`;
- the `created` and `hot` chunk bitsets;
- per chunk, the number of present slots.

**Epochs.** The file has three volatile epochs:

- `writeEpoch` (W): stamped into every slot written by a mutation.
- `storeEpoch` (E): the epoch the stored index record was sealed with. `repair` stamps E.
- `committedEpoch` (C): the highest epoch whose store has committed.

They move as follows:

- `seal()` runs from the store path (section 4.2). It sets E = W, increments W and records E as an uncommitted seal. It does no I/O and returns `-1` if nothing was written since the last seal.
- `committed(epoch)` runs from the storer's commit listener. It advances C only over epochs whose stores have all committed, because storers commit independently.
- At open: E = C = the record's `vectorFileEpoch`, and W = `max(E, highestEpoch) + 1`.

A slot whose epoch is above E was written after the store the record comes from, and is repaired from its entity at the next open. A cold chunk never holds such a slot unless the file is ahead of the record, and that is rejected.

**Nonce.** A random non-zero identity (`newNonce`), written into the header and persisted in the index record as `vectorFileNonce`. A file with another nonce belongs to another store state and is replaced. Nonce `0` in the record (a new index or a 4.x layout) therefore always replaces the file.

**Boot id and `clean`.**

- `currentBootId()` reads `/proc/sys/kernel/random/boot_id`. On systems without that file it is `null`, so every unclean end is treated like a reboot.
- `close()` writes `clean = true` unless a recovery is unfinished.
- An open that finds `clean` set or the same boot id trusts the page cache: hot chunks are only **scanned** for slots that need repair. Otherwise, after an unclean end of another boot, hot chunks are **repaired in full** (every ordinal re-vectorized), because pages written before the crash may not have reached the disk.
- `markUncleanBeforeRepair` writes `clean = false` (forced) before the first repair of an open that started from a clean header. A power loss during that recovery therefore does not leave a header promising that a scan suffices.

**Open** (`VectorFile.open`, `rejectionReason`, `mapExisting`). The file is replaced by an empty one (`createFresh`, a full rebuild from the entities) if it is:

- missing or unreadable;
- of another layout (dimension, stride, chunk size or byte order);
- of another nonce;
- older than the stored record with no hot chunk;
- ahead of the record in a cold chunk.

Otherwise `mapExisting` maps the chunks:

- An intact, cold, empty chunk is dropped.
- A missing or short chunk is **recreated** with zeros and put on the repair list. The header then gets an unknown boot id and `clean = false`, so every later open repairs it in full until a recovery completes.
- Hot chunks go on the scan or repair list as described above.
- `Recovery` (`fullRebuildReason`, `scanChunks`, `repairChunks`) tells the index what to repair (section 4.4).
- A file that is only opened and closed, with no write and no recovery, is not rewritten.

**Temporary files.**

- In-memory indices without `keepsIndexFiles()` use `VectorFile.openTemporary`. It creates `<pid>-<random>` under `<root>/eclipsestore-vectors` and holds a `FileLock` on its `.lock` file while open.
- `<root>` is the system property `org.eclipse.store.gigamap.jvector.temporaryRoot` (`TEMPORARY_ROOT_PROPERTY`), or `java.io.tmpdir`. It is read once when the class loads.
- Before creating a directory, the reaper (`reapTemporaryDirectories`, once per loaded class) deletes abandoned directories there. A directory qualifies only if `isOwnTemporaryDirectory` holds (its name has the `openTemporary` form and it contains only regular files named `.lock` or `*.vec[-n]`) and `isAbandoned` holds (its lock can be taken, or it has no lock file and is older than one minute). Anything else is left alone with a warning.
- Closing deletes the directory and releases the lock last.
- Persistent vector files have no `FileLock`. Within one process a second open of the same path takes the file over from the first instance (`takeOver`).

### 3.2 Segments and the manifest (on-disk index)

An on-disk index is a set of immutable segments plus the in-memory sources described in section 5. The manifest is the commit point. File names are built by `SegmentFiles`:

| File | Content | Integrity |
|---|---|---|
| `<name>.manifest` | witnesses, dimension, the segment list | CRC32 |
| `<name>.manifest.tmp` | the manifest being committed | — |
| `<name>.seg-<n>.graph` | jVector `OnDiskGraphIndex` with **dense local ordinals** (`DenseOrdinalMapper`), features per `GraphFormat` | **none** (jVector's format; see below) |
| `<name>.seg-<n>.l2g` | local-to-global ordinal array (`L2G_MAGIC`, version, count, ordinals strictly ascending) | CRC32 |
| `<name>.seg-<n>.pqv` | only for `ApproximateScoring.PQ_IN_MEMORY`: PQ codes by local ordinal with their codebook, a trailer with the segment sequence | CRC32 |

- Dense local ordinals avoid jVector writing a record for every id up to the highest one, so id gaps cost nothing.
- `DiskSegment` translates between local and global ordinals: accept bits, results, and the deletion set.

**Manifest layout** (`SegmentManifest`, big-endian):

```
int MAGIC ("CVMF"), int version (= DiskIndexManager.GRAPH_FILE_VERSION, currently 7), int dimension,
long expectedVectorCount, long highestEntityId, long structuralModCount,     <- the witness (MetaState)
int segmentCount,
  per segment: long sequence, int nodeCount, int storageCode, int scoringCode,
               int pqSubspaces, int nvqSubvectors, byte hasPqVectors,
               int deletedCount, int[deletedCount] deletedLocals (ascending)
long crc32
```

- **Witness.** The witness is `DiskIndexManager.MetaState`: the vector count, the highest entity id and `structuralModCount`. `MetaState.RETIRED` (all `-1`) marks a retired manifest (see Load).
- **Format per segment.** Format codes are `VectorStorage.code()` and `ApproximateScoring.code()`, not ordinals. An unknown code makes the manifest invalid.
- **Deletions.** Each segment's deletion set is the committed set plus the entities that left it since (section 5.2).
- **Rewrite flag.** There is no persisted needs-rewrite flag. It is computed at load.
- **Version.** A manifest of another `GRAPH_FILE_VERSION` is invalid, and the index is rebuilt once. Version 6 introduced segments. Version 7 forced a rebuild so that every segment is written after the reachability repair (section 6.4).

**Commit** (`DiskIndexManager.commit`, test stages `DiskIndexManager.CommitStage`):

1. Each segment's files were already written and forced (`.l2g`, `.pqv`, `.graph`), and the directory synced, by `writeSegment`.
2. `SEGMENTS_WRITTEN`. Write `<name>.manifest.tmp` and force it.
3. `MANIFEST_TEMP_WRITTEN`. `SegmentFiles.atomicMove` the temp file over `<name>.manifest`. A non-atomic replace is used only when the file system rejects an atomic move.
4. `MANIFEST_MOVED`. `SegmentFiles.syncDirectory`.
5. `DIRECTORY_SYNCED`.

- A failure after the move is a `CommitIncompleteException`: the new manifest may already be in effect, so the caller publishes the flush anyway.
- Files of segments the manifest no longer lists are deleted only after the commit (`reapUnlistedFiles`), and never while a source still has them open (`SegmentSequences.protectedSequences`).
- A crash at any stage leaves either the old or the new manifest, each with all its segments. Files left unlisted are deleted at the next load.

**Load** (`DiskIndexManager.load`):

1. No manifest: reap unlisted files, no disk graph.
2. An invalid manifest (`InvalidManifestException`: CRC, magic, version, unknown code, bounds) costs its segments: they are reaped and the graph is rebuilt. An **unreadable** manifest (any other `IOException`, possibly transient) deletes nothing.
3. Unlisted files of this index's own layout (`SegmentFiles.layoutFilePattern`) are deleted.
4. A manifest is **rejected** if it is already retired, has another dimension, or has a witness that does not match the stored record. A rejected manifest is **retired** (`retireRejectedManifest`): it is rewritten durably with `MetaState.RETIRED`, keeping its segment list.
   - Retirement matters because the witness counters only grow within a session. A later session could reach the same three counters through another history and would otherwise accept the stale graph.
   - The kept list protects the segment files and their sequences until a persist supersedes them.
   - The reload inside `removeAll` rejects without retiring (`reloadingAfterRemoveAll`): the manifest still matches the stored record until the next store.
5. Open every segment (`openSegmentFiles`):
   - `.l2g` is CRC-checked.
   - `.pqv` is CRC-checked before it is parsed, then its witness, code size and count are checked.
   - The graph is opened with `OnDiskGraphIndex.load`. jVector sizes upper-layer arrays from the file, guarded only by an `assert`. An `OutOfMemoryError` or `AssertionError` from that call is therefore turned into an `IOException`, so a damaged graph is rejected like any other damaged segment and does not stop the storage from starting.
   - Any failure rejects the whole load. Files are kept, nothing is retired, and the graph is rebuilt from the vector file.
6. **Codebook.** The codebook is taken from the newest segment whose format equals the configured one. A segment with another format, another codebook, or no codebook where PQ is configured is **flagged for merge** (`DiskSegment.flaggedForMerge`). It is searched exactly and rewritten first by the next merge.
7. **Owners.** Owners are derived from the deletion sets (`deriveOwners`): every non-deleted node of a segment owns its entity. On a conflict the newer segment wins.

The graph file has no checksum. Damage that `OnDiskGraphIndex.load` and the node-count checks do not detect is served as is, and needs `reindex()` or deleting the index files. The pre-segment files `<name>.graph`, `<name>.meta` and `<name>.pqv` do not match the layout pattern: they are logged once at load (`logFilesOfTheLayoutBeforeSegments`) and are neither read nor deleted.

**Sequences.**

- `SegmentSequences` allocates segment numbers for the lifetime of the `VectorIndex.Default` instance, across `removeAll`.
- `reserve` takes the higher of its counter and the highest sequence of any file in the directory plus one.
- Sequences are never reused, so a file that is still open, being written, or listed by a retired manifest is never overwritten.

**Single writer.** Only one process may use an index directory. There is no inter-process lock on segments or on the persistent vector file.

---

## 4. Persistence model

### 4.1 Persisted records

`BinaryHandlerVectorIndexDefault` (an `AbstractBinaryHandlerStateChangeFlagged`) persists `VectorIndex.Default` as:

| Field | Type | Meaning |
|---|---|---|
| `parent` | reference (`VectorIndices`) | owning group |
| `name` | reference (`String`) | index name, file prefix |
| `configuration` | reference (`VectorIndexConfiguration`) | persisted by the default handler |
| `vectorizer` | reference (`Vectorizer`) | user class; whatever `vectorize` needs must be reachable from it |
| `format` | reference (`VectorIndexFormat`) | the format of the build that stored the record; always `VectorIndexFormat.CURRENT` when written |
| `structuralModCount` | `long` | count of graph-affecting mutations; part of the manifest witness |
| `vectorFileNonce` | `long` | identity of the vector file this record describes, `0` for none |
| `vectorFileEpoch` | `long` | the store epoch E sealed into this record |
| `vectorCount` | `long` | number of entities with a vector; part of the manifest witness |

- `complete()` calls `initializeAfterLoad()`.
- `BinaryHandlerVectorIndexFormat` persists `VectorIndexFormat` as two `int`s, `version` and `minimumReaderVersion`. It refuses a format whose `minimumReaderVersion` is above this build's `VERSION` when it creates the instance, i.e. when the record is loaded, before any index file is opened: a later file format would otherwise read as damage and be rebuilt or reaped.
- The format type also keeps out the builds without it. The legacy type mapping of such a build would map this record onto its own layout, drop the vector state, and serve an empty index without an error. Because the type has entities in the storage and no runtime class there, they fail at start with a missing runtime type instead.
- `VERSION` is raised with every change of the record or of a file format; `MINIMUM_READER_VERSION` only when older builds can no longer read what this build writes.
- `BinaryHandlerVectorIndicesDefault` persists `VectorIndices.Default` as `parent` and the `vectorIndices` table. The table is stored eagerly.
- Everything else of `VectorIndex.Default` is transient: graphs, sources, managers, locks, pools and the vector file handle.

### 4.2 Store: seal, then commit

The record and the vector file must describe the same store. The order of events is:

```
GigaMap.store()  (synchronized on the map)
  -> GigaIndices.internalPrepareStore(storer)               before any component is written
       -> VectorIndices.internalPrepareStore
            -> VectorIndex.Default.internalPrepareStore      under the monitor
                 sealEpoch: E = file.seal(); vectorFileEpoch = E; mark record changed
                 sealedForStore = WeakReference(storer)
  -> changed components stored ... BinaryHandlerVectorIndexDefault.store(record)
       -> internalSealVectorEpochOnStore(handler)
            seal again only if this storer did not seal (record stored without the map,
            or by another storer)
            handler.registerCommitListener(() -> file.committed(vectorFileEpoch))
  -> storer commits -> commit listener -> VectorFile.committed(E)
```

- The seal happens before any entity of the store is written. Every slot written later carries a higher epoch and is repaired at the next open if that store is the last one that committed.
- The committed epoch moves only from the commit listener of the storer that carried it. A store that never commits never lets the file treat its epoch as committed.
- `BinaryHandlerVectorIndexDefault.store` refuses (`IllegalStateException`) to store a record that still carries an unimported 4.x vector store (section 9).

### 4.3 What a restart loads

`initializeAfterLoad` runs inside the storage's load (`complete()`). It does only work that needs no nested load of the object graph (`initializeIndex`):

- resolve the directory once for the life of the instance (`resolvedDirectory`);
- create locks, pools and managers;
- open the vector file (`openVectorFile`, pure I/O; `vectorsRecovered = false`);
- for an on-disk index, load the manifest and segments (`DiskIndexManager.load` against `currentMetaState()`).

If the load is accepted, the segments are the graph (`graphRebuilt = true`). If it is rejected, or there is no manifest while vectors exist, the next persist is scheduled (`forceFlush`, `markPersistRequired`) so that a read-only session does not rebuild the graph at every start.

Two steps read entities and therefore run on first access, not inside `complete()`. `ensureIndexInitialized()` performs them, under the parent monitor:

1. **`ensureVectorsRecovered()`**: the 4.x import (section 9) or `recoverVectors` (section 4.4).
2. **`ensureGraphRebuilt()`**: in-memory indices, and on-disk indices whose segments were rejected or dropped, rebuild the live graph from the vector file (section 6.5).

`ensureIndexInitialized()` runs:

- at the start of every search, mutation, optimize and persist;
- in `internalPrepareMutation()`, which GigaMap calls through `IndexGroup.Internal#internalPrepareMutation` **before** `add`, `addAll`, `remove` and `removeAll` change the map;
- in `VectorIndices.internalPrepareIndicesUpdate`, before an in-place update's logic runs.

Recovery repairs from the entities at the store epoch, which counts as committed, so it must see the map as stored, not with the mutation already applied. A recovery that throws rejects the mutation with the map unchanged, and the next access retries.

### 4.4 Crash recovery of the vector file

`recoverVectors` acts on the `Recovery` of the open:

- **Full rebuild** (file replaced): every entity of the map is vectorized into the file (`repairSlot`).
- **Scan chunks**: only slots that `needsRepair` (odd sequence, or epoch above E) are repaired from their entity.
- **Repair chunks**: every ordinal of the chunk is repaired.
- Ordinals above the map's highest id are cleared.

`repairSlot` calls `vectorKey` and `vectorize` and validates the result. A `null` (where not allowed) or an invalid vector is dropped and counted at ERROR. A vectorizer exception propagates, and recovery stays pending.

Afterwards:

- `VectorFile.recovered()` recounts the present slots and checkpoints.
- A replaced file's new nonce is recorded and reported up to the map (`markStateChangeInstance`, `internalReportIndexStateChange`), so the next store writes it. Without that, every start would take the file for a foreign one.
- If the vector count changed, `dropLoadedSegments` discards the loaded segments and the graph is rebuilt from the file.
- A close during recovery ends it with `RebuildAbandonedException` (`checkRecoveryNotClosing`).

### 4.5 Guarantees

- The **vectors** of committed entities survive any crash, provided the vectorizer reproduces them. The file is repaired from the entities wherever it cannot prove a slot.
- The **manifest** and **segments** are crash-safe through the commit order of section 3.2. A manifest whose witness does not match the record is rejected and retired, and the graph is rebuilt from the vector file.
- An **in-memory graph** is never persisted. It is rebuilt at first access.

---

## 5. In-memory structures

### 5.1 Live graph

- `builder` (`GraphIndexBuilder`) and `index` (`OnHeapGraphIndex`) are volatile and swapped as a pair by a freeze, a consolidation and the in-memory PQ switch. Code that mutates reads `builder` once and derives the graph from it.
- The builder scores from `liveVectorValues` (`GenerationVectorValues` over the vector file), or from `inMemoryPqVectors` for an in-memory index switched to PQ.
- `newGraphIndexBuilder` pins `addHierarchy` and `refineFinalGraph` per index through the deprecated jVector constructor, instead of reading jVector 4.0.1's process-wide, JMX-changeable defaults.
- An in-memory index has only this graph. An on-disk index additionally has the sources below.

### 5.2 Sources of an on-disk index

```
SourceSet (immutable, refcounted)            OwnerTable (slot leases + leave logs)
  segments: DiskSegment ... (by sequence)       SegmentOwners: byte per entity id
  frozen:   HeapGeneration ... (oldest first)     0..254 = slot of the owning source
  owners                                          0xFF   = NONE (no live copy)
live graph: holds liveLease (liveSlot)
```

- **`SourceSet`** (`sources`, volatile) is replaced copy-on-write:
  - by a freeze and a consolidation, under the builder write lock and the monitor;
  - by a flush and a merge publishing, under `commitLock` with the flush permit;
  - by `retireSources` and `dropLoadedSegments`, under `commitLock`.

  A search acquires the set (`acquireSources`, `tryAcquire`), which keeps every source in it open until the search releases it. The last release closes the sources.
- **`IndexSource`** is a segment or a frozen generation, with a slot and a hold count.
- **`HeapGeneration`** is a frozen former live graph: builder, graph, searcher pool, its `GenerationVectorValues`, the eventual-indexing operations it took over (`movedOps`), and its slot lease.
- **`DiskSegment`** is an open segment: graph, local-to-global map, optional `.pqv` codes, committed deletion set, `flaggedForMerge`, slot lease.
- **`OwnerTable`** wraps `SegmentOwners` (one byte per entity id, paged, with acquire/release access). It hands out slot leases (`OwnerTable.Lease`, at most `SLOT_COUNT = 255` sources) and keeps a per-slot **leave log** of the entities that left a non-live slot.

**Ownership rule.** Each entity is live in exactly one source, the one its owner byte names.

- Every mutation sets the owner (`setOwner`): the live slot if the entity now has a vector in the live graph, `NONE` otherwise. That hides every other copy at once.
- A freeze passes the live lease to the new generation and leases a new live slot. The frozen entities keep their byte and are now owned by the generation.
- A segment written from a generation takes over that generation's lease, so publishing it changes no owner byte.
- A flush computes each segment's deletion set as the committed set plus the leave log since the last commit (`planCommit`).
- A merge moves owners by compare-and-set (`OwnerTable.move`) without the monitor. A mutation in between keeps its newer owner.
- `SegmentOwners` is not persisted. It is derived from the deletion sets at load.

**Freeze-time vectors.** A frozen generation is written later by the flush thread, while mutations keep overwriting the vector file. Before a mutation overwrites a slot whose node a frozen generation holds, `writeSlot` → `preserveForFrozenGenerations` copies the old vector into that generation's `GenerationVectorValues` (`preserve`). The flush then cleans up and writes the generation with the vectors its nodes were inserted with. The vectors of operations moved into the generation are captured at the freeze (`movedVectorsOf`).

### 5.3 Search

`search(float[], k)` → `doSearch`:

1. Validate the query (dimension, finite values).
2. `acquireReadLockOnRebuiltGraph`:
   - `ensureIndexInitialized()`, before any lock;
   - `awaitNoPendingExclusive()`: while a writer waits for the write lock under the monitor (`exclusiveWaiters`) or a group-wide exclusive operation is pending, a new search holds back for up to 100 ms (`EXCLUSIVE_GATE_MAX_NANOS`);
   - `builderLock.readLock()`.
3. **In-memory** (`searchInMemoryIndex`): one jVector search over the live graph with `view.liveNodes()` as accept bits. Scoring is exact over `NullSafeVectorValues(MappedVectorValues.Caching)`, or PQ traversal with exact rerank if `inMemoryPqVectors` is active.
4. **On-disk** (`searchSources`): acquire the `SourceSet`, then search every source with `rerankK = max(k, minSearchBeamWidth)` candidates:
   - Each segment (`searchSegment`): accept bits = `owners.owner(l2g[local]) == segment.slot()`. The score provider is chosen by `diskScoreProvider` (5.4).
   - Each frozen generation and the live graph (`searchHeapGraph`): accept bits = not deleted and `owners.owner(ordinal) == slot`, exact scoring from the vector file.
   - Merge the results by global ordinal (`mergeSearchResults`), keeping the best score per ordinal, and take the top k.
5. Release the set and the read lock. Results resolve entities lazily (`VectorSearchResult`).

### 5.4 Approximate scoring and quantization

| Configuration | Effect |
|---|---|
| `VectorStorage.INLINE` (default) | full-precision vectors in each segment graph |
| `VectorStorage.NVQ` (on-disk only) | NVQ-quantized vectors in each segment graph; the quantizer lives in the graph's `NVQ` feature |
| `ApproximateScoring.NONE` | exact traversal (NVQ-approximate with NVQ storage) |
| `ApproximateScoring.FUSED_PQ` (on-disk only) | neighbour PQ codes fused into each node; codebook in the graph header; traversal by PQ, rerank exact |
| `ApproximateScoring.PQ_IN_MEMORY` | PQ codes on the heap. On-disk: per segment in `.pqv`. In-memory: `inMemoryPqVectors`, adopted at optimization (requires `optimizationIntervalMs > 0`) |

`diskScoreProvider` falls back to exact scoring for a segment flagged for merge, and for a FUSED_PQ graph that is not hierarchical: jVector's fused decoder needs the hierarchy for the entry node.

**Training.**

- `trainCompressorsBeforeLocking` collects one reservoir sample of at most `PQCompressionManager.MAX_TRAINING_VECTORS` (seeded, so the same data yields the same codebook) under the monitor, and trains with no lock held.
- PQ needs `MIN_VECTORS_FOR_PQ_TRAINING` (256) vectors, NVQ needs `MIN_VECTORS_FOR_NVQ_TRAINING` (16).
- A declined or failed training is remembered by `structuralModCount` (`pqTrainingDeclinedAtModCount`, `nvqTrainingFailedAtModCount`), so it is retried only after the data changed.
- A shutdown persist never trains.
- On load, the codebook and quantizer are adopted from the segments (`adoptCompressorsFromLoadedGraph`). Without them the managers are reset and the next persist trains.

The in-memory PQ switch (`performPqSwitch`, from `doOptimize`, under the monitor only):

1. train;
2. encode every ordinal in parallel;
3. build a new builder over the codes with `ParallelGraphInsertion`;
4. repair reachability;
5. publish, unless a mutation or a teardown intervened.

---

## 6. Mutation paths

All `internal*` mutations run under the GigaMap monitor. The synchronous path takes no `builderLock`; graph inserts and purges coordinate through `structureLock` (section 8). Before GigaMap mutates the map, `internalPrepareMutation` has completed recovery (section 4.3).

### 6.1 Synchronous indexing (`eventualIndexing = false`)

| Operation | Vector file | Graph | Owner | Counters |
|---|---|---|---|---|
| `internalAdd` | `writeSlot` (key and vector; for a null vector only a key, if any) | insert the node | live, after the insert | `markContentChanged` (`structuralModCount++`) if a vector was added |
| `internalAddAll` | `writeSlot` per entity | `insertBatch` (parallel, 6.3) | live, after the batch | one `markContentChanged` per batch |
| `internalUpdate` | skipped if the key is unchanged, or the vector is equal; else `writeSlot` | vec→vec: replace the node (`internalReplaceGraphNode`); vec→null: mark deleted; null→vec: insert | live, or `NONE` for vec→null | `markContentChanged` |
| `internalRemove` | tombstone | mark the node deleted | `NONE` | `markContentChanged` |
| `internalRemoveAll` | every slot absent | builder and sources discarded, index re-initialized | — | `markContentChanged` |

- `vectorCount` follows every absent↔present transition of a slot.
- A failed graph operation is reported through `graphOperationFailed`. For an on-disk index that sets `consolidationRequired`, so the next persist that may consolidate rebuilds from the vector file.
- After a mutation, `requestFlushIfDue` may freeze the live graph for a background flush (section 7.1).

### 6.2 Eventual indexing (`eventualIndexing = true`)

- The vector file and the owner are updated synchronously, so no data is lost and the owner byte is already current. The graph change is queued as an `IndexingOperation` (`Add`, `Update`, `Remove`, `BatchAdd`, carrying ordinals only).
- `BackgroundTaskManager` applies the queue on its single daemon thread `VectorIndex-Background-<name>`, up to `MAX_OPERATIONS_PER_TURN` (256) operations per turn, under its fair `indexingLock`. The callbacks `applyGraphAdd`, `applyGraphBatchAdd`, `applyGraphUpdate` and `applyGraphRemove` take `builderLock.readLock()`, read the vector from the file, and do not change owners.
- A failing operation is logged and reported through `graphOperationFailed`, and `markPersistRequired` is called. The worker goes on with the next operation.
- `optimize()` drains the queue first (`drainQueue`). A freeze does not drain: it moves the pending operations into the frozen generation (`takePendingOps`), and the flush applies them there (`applyMovedOps`).
- Search results may lag behind mutations until the queue is applied. An entity removed but not yet applied in an in-memory index may still be visited, and is scored through the `NullSafeVectorValues` placeholder.

### 6.3 Batch parallel insertion

`insertBatch` serves synchronous `addAll` and the eventual `BatchAdd`.

**Before the insert.**

- If the graph's entry node is marked deleted, the deleted nodes are purged first (`purgeIfEntryDeleted`). From a deleted entry an insert finds no live candidate and would link only to nodes inserted after it, an island no search reaches.

**Under `structureLock.readLock()`:**

- **Phase 1, on the calling thread.** Skip null vectors. For an ordinal already in layer 0, track its PQ code and clear its deleted mark. Remove upper-layer leftovers of the others.
- **Seeding.** Seed an empty graph on the calling thread (`seedIfEmpty`).
- **Parallel insert.** Insert the rest on the index's private long-lived `ForkJoinPool` (`insertPool`, `vector-insert-<name>`). The builder keeps per-thread scratch keyed by thread, so the workers outlive the calls. The pool is private, so nothing else can occupy it while the caller waits holding the monitor. A `Cleaner` shuts it down for an index dropped without `close()`.

**`ParallelGraphInsertion`** is the shared helper:

- It runs sequentially below `MIN_PARALLEL_NODES` (256) nodes or with fewer than two physical cores.
- `invokeToCompletion` gives a failing parallel call clear semantics. Calls not yet started are skipped. The caller returns only after every started call has returned. The first failure is rethrown as thrown, later ones are suppressed. No worker keeps inserting after the caller released its locks.
- A failed batch is not rolled back. The vectors stay written and the owners stay unset.

**After the insert.** Concurrent inserts do not see each other, so a node whose true neighbours were being inserted at the same time can end up without an incoming layer-0 edge. Every parallel insertion phase therefore ends with a reachability repair (6.4):

- batch insert (from `MIN_PARALLEL_NODES` on);
- load-path rebuild;
- PQ switch;
- consolidation;
- merge.

### 6.4 Reachability repair

`GraphReachability.repair` walks layer 0 breadth-first from the entry node until every candidate is reached. Every live node it does not reach (not deleted, with a vector) is an orphan, and it is handled as follows:

1. Strip the edges other nodes hold to it, on every layer.
2. Remove it.
3. Insert it again sequentially, now that every node is complete.

This repeats for up to `MAX_ROUNDS` (3) rounds, with a warning if nodes remain. If a re-insert fails, the remaining orphans are still inserted, and then `NodesLostException` is thrown with the count of lost nodes.

`GraphReachability.purgeDeleted` runs `removeDeletedNodes()` and then repairs the nodes the deleted ones pointed at.

Where it runs:

- **Live graph** (via `repairLiveGraph`, which logs at ERROR and escalates through `graphOperationFailed` before rethrowing):
  - after a parallel batch;
  - after every purge of the live graph;
  - after every replace of a live node (the whole of layer 0, since which node lost its last edge is unknown);
  - in `maintainLiveGraph` (optimize).
- **Frozen graphs** (`purgeAndRepairFrozen` in `applyMovedOps`): one purge and one repair after the moved operations, or whenever the frozen graph holds nodes marked deleted. It re-inserts with the generation's freeze-time vectors and runs without `structureLock`. A failure sets `consolidationRequired`.
- **Private builders** (rebuild, PQ switch, consolidation, merge): directly, since nothing else writes to them.

### 6.5 Rebuild from the vector file

`rebuildGraphFromStore` runs from `ensureGraphRebuilt`, under the monitor and without `builderLock`.

1. Collect the present ordinals from the vector file.
2. Fill an empty builder: optionally encode PQ codes in parallel, seed on the calling thread, insert in parallel on a one-shot pool (`vector-rebuild-<name>`), then repair reachability.

- The rebuild is all or nothing. A failure or an abandonment discards the partial graph (`discardPartialGraph`).
- `close()` sets `closeRequested`, and the rebuild gives up at its next check. The caller then gets `RebuildAbandonedException`, which the background task manager logs at debug level.
- Workers read the vector file only. They take neither the monitor nor `builderLock`.

### 6.6 reindex and removeAll

`GigaMap.removeAll()` and `reindex()` run exclusively (`GigaMap.runExclusive`):

- Under the monitor they try `internalTryLockExclusive` on every index group, all or nothing, waiting on the monitor between tries.
- For a vector index that is `builderLock.writeLock().tryLock()`. It throws `IllegalStateException` when called from inside a search of the same index, which holds the read lock.

**`internalRemoveAll`**, in order:

1. Clear the vector file in place.
2. Discard the background manager without waiting (`shutdownWithoutWaiting`).
3. Retire the sources (`retireSources` increments `sourceEpoch`, so a flush or merge in flight commits nothing).
4. Bump `structuralModCount`, so the old manifest no longer matches.
5. Re-initialize the index.

The old manifest stays on disk until the next store and persist.

**`internalReindex`**:

1. Compute and validate every vector first. Embedded: a validation pass. Computed: the vectors are collected on the heap.
2. `internalRemoveAll`.
3. Re-add.

Only a throwing vectorizer leaves the index as it was. A failure after the clear leaves it partly rebuilt, and the vector file keeps that state.

### 6.7 invalidateGraph

`invalidateGraph()` is the seam for a layer that replaces the stored entities and vectors behind a running index (replication, binary import): the persisted state is current, the transient graph is not. It is `internalRemoveAll` minus every destructive step:

1. Take the write lock (section 8.2).
2. Close the builder and the graph, which retires the HNSW graph the index was answering from.
3. Clear `graphRebuilt`, so the next `ensureIndexInitialized()` rebuilds from the vector file exactly as after a start (6.5).

The vector file, the segments of an on-disk index, the PQ codebook, the background managers and the counters all survive. An on-disk index rejects the call: its graph is served from segments this seam does not retire, and the vectors-versus-segments repair is a load-time concern, not an external state change.

---

## 7. Flush, persist and background merge (on-disk index)

### 7.1 Flush

A persist (`persist(PersistKind)`) writes the live graph as one more segment. It does not rebuild, does not defer mutations, and holds no lock while it writes.

`PersistKind` has three values:

- `BACKGROUND`: scheduled by `persistenceIntervalMs`. It flushes only when the live graph and the pending operations reach `flushThreshold`, when frozen generations remain, or when the manifest is due (forced flush, merged segment uncommitted, witness moved).
- `EXPLICIT`: `persistToDisk()`.
- `SHUTDOWN`: from `close()`. It never trains and never consolidates.

Steps:

1. **Permit.** Take `flushPermit` (one flush at a time).
   - `BACKGROUND` uses `tryAcquire`, trains the compressors, releases the permit, and re-tries it under the freeze locks.
   - `EXPLICIT` and `SHUTDOWN` acquire it interruptibly. An interrupt ends the persist with the interrupt flag restored.
2. **Train** (not `SHUTDOWN`): `trainCompressorsBeforeLocking`.
3. **Freeze** (`freeze` → `freezeUnderLocks`), under:
   - the `indexingLock` (eventual indexing only);
   - the monitor;
   - the builder write lock, *tried* (`lockBuilderUnderMonitor`).

   It samples the witness (`currentMetaState`), then decides:
   - **Clean**: nothing live or pending, no leaves, no frozen generation, witness committed, nothing trained, no forced flush, no uncommitted merge. Count the changes as persisted and stop.
   - **Declined** (`FlushPlan.DECLINED`): a background persist below the threshold; a consolidation due on a thread that may not consolidate; no free slot on such a thread.
   - **Consolidate** (7.3), if due and allowed.
   - **`SHUTDOWN` with `consolidationRequired`**: commit nothing, so the next start rebuilds.
   - **Otherwise freeze** (`freezeLiveGraph`):
     - take the pending queue operations;
     - freeze `liveVectorValues`;
     - wrap the builder in a `HeapGeneration`;
     - install a fresh builder and a new live slot;
     - publish `sources.with(..., frozen + generation)`;
     - build a `FlushPlan` with the witness, the leave logs, the quantizers and the current `sourceEpoch`.
4. **Write**, with no lock (`runFlush`). This runs on the flush writer thread `VectorIndex-Background-<name>-flush` for a background persist, and inline otherwise. Per frozen generation, at `FlushStage` `FROZEN` → `OPS_APPLIED` → `CLEANED` → `SEGMENT_WRITTEN`:
   - `applyMovedOps` + `purgeAndRepairFrozen`;
   - `prepareForWrite`: jVector `cleanup()`. A `SHUTDOWN` persist uses a second builder with `refineFinalGraph = false` (`newUnrefinedCleanupBuilder`), because refinement dominated the close of a large unflushed graph.
   - reserve a sequence, `writeSegment`, `openSegment`.
5. **Commit and publish**, under `commitLock`:
   - `checkFlushStillWanted`;
   - `DiskIndexManager.commit` (or delete every file if nothing is listed);
   - `publishFlush`: the segments replace their generations in the set, `committedWitness` is updated, `mergeUncommitted` is cleared.
6. **After**: release the plan, `reapUnlistedFiles`, `PUBLISHED`, `scheduleMergeIfDue`, checkpoint the vector file. Unpublished written files are deleted (`cleanUpWritten`).

**Abandonment.** `removeAll` and `close` advance `sourceEpoch` under `commitLock`, and `close` also sets `flushAbortRequested`. `checkFlushStillWanted` throws `FlushAbandonedException` before each stage and under `commitLock` before the commit. An abandoned flush commits nothing and deletes what it wrote.

**Failure.** A failed flush keeps its frozen generations in the set, and they stay searchable. `markPersistRequired` is called, and the next persist retries them.

**Inline freeze.** A synchronous mutation that brings the live graph to `flushThreshold` with background persistence enabled tries to freeze on its own thread (`tryFreezeForBackgroundFlush`). It holds the monitor, tries the write lock for at most `INLINE_FREEZE_MAX_NANOS` (5 ms), and never consolidates. If that fails it asks the background thread (`requestFlush`). The write is handed to the flush writer (`handOffFlush`).

`persistToDisk()` runs `persist(EXPLICIT)`. If a rewrite merge was due (`rewriteSegmentsInline`) or a merge is uncommitted, it runs a second persist to commit that merge.

### 7.2 Background merge

Triggers:

- `scheduleMergeIfDue` runs after every published flush or merge, and after the vector recovery.
- `mayMerge` requires an on-disk index that is open and not closing, with recovered vectors.
- Merges run on the daemon thread `VectorIndex-Background-<name>-merge`. A request that arrives while a merge runs is kept in `mergeRequested`.

**Selection** (`SegmentMergePolicy`, size-tiered by live count):

- Segments flagged for rewrite are always merged first, all of them.
- Otherwise the `segmentMergeFactor` smallest segments of the lowest tier with at least that many members. Tier t holds live counts below `flushThreshold * factor^(t+1)`.
- Otherwise, if the source count reaches `maxSegments`, the smallest ones.

**Run** (`runMerges` → `mergeOnce` → `runMerge`), holding `mergePermit` for the whole merge. `MergeStage` goes `SELECTED` → `BUILT` → `WRITTEN` → `JOINED` → `MOVED` → `PUBLISHED`:

1. Build one graph from the vector file on half the cores, repair reachability, and run `cleanup()` with refinement.
2. Write the segment. Open it with a new slot (`openMergedSegment`, which takes the monitor briefly).
3. Publish (`publishMerge`), holding the flush permit:
   - join the set owning nothing;
   - take and release the builder write lock once, without the monitor, so that no search still holds a set without the merged segment;
   - move the owners by compare-and-set (`moveOwners`);
   - publish the set without the merged-away segments, which close on their last release. A source not fully covered stays in the set.
   - set `mergeUncommitted`. The next flush commits the manifest, and an idle index commits the manifest alone.
4. If the merge fails after joining, `withdrawMerge` moves the owners back and removes the segment.

**Abort.** `close()` sets `mergeAbortRequested` and waits for `mergePermit`. `removeAll` advances `sourceEpoch`. Both make a merge in flight publish nothing (`MergeAbandonedException`).

### 7.3 Consolidation

`consolidate` rebuilds the whole graph from the vector file into a single in-memory source, under the indexing lock, the monitor and the write lock. It is the fallback for two cases:

- `consolidationRequired` (a failed graph operation, or a failed moved operation or frozen repair);
- more sources than `maxSegments` (or no free owner slot).

It assigns a fresh `OwnerTable`, drops the queued operations (their effect is already in the vector file), and replaces the set. It never runs for `SHUTDOWN` or on a mutating thread, and publishes nothing on failure.

---

## 8. Concurrency and lock model

### 8.1 Locks

| Lock | Kind | Held by |
|---|---|---|
| `mergePermit` | `Semaphore(1)` | a merge, for its whole run; `close()`, from start to end; inline rewrite in `persistToDisk()` |
| `flushPermit` | `Semaphore(1)` | a flush, from the freeze to the end of its write (released by the flush writer); a merge's training and publication; `close()` during teardown |
| `indexingLock` (`BackgroundTaskManager`) | fair `ReentrantLock` | the eventual worker per turn; a freeze (`lockIndexing`) |
| GigaMap monitor | `synchronized(parentMap())` | every mutation, recovery, rebuild, training sample, freeze, consolidation, close teardown, PQ switch |
| `builderLock` | `ReentrantReadWriteLock` | read: searches, eventual callbacks. Write: freeze, consolidation, in-memory optimize, close, removeAll/reindex, merge barrier |
| `structureLock` | `ReentrantReadWriteLock` | read: inserts (a batch for its whole parallel phase). Write: every `removeDeletedNodes`, reachability repair, `maintainLiveGraph` |
| `commitLock` | monitor of an `Object` | flush commit and publication, `replaceSegments`, `retireSources`, `dropLoadedSegments` |
| `VectorFile` `forceLock` → `headerLock` | monitors | checkpoint/close → header, chunk table, seals |

**Order:** `mergePermit` → `flushPermit` → `indexingLock` → GigaMap monitor → `builderLock` (write only tried) → `structureLock` → `commitLock`.

`structureLock` is taken last and is held only while waiting for the inserting workers, which take no lock. jVector does not support `removeDeletedNodes()` concurrently with inserts, and this lock keeps the two apart.

### 8.2 The monitor before the write lock

A search can be issued while its thread holds the monitor: inside `GigaMap.iterate`, apply logic, or `synchronized(map)`. If a writer blocked on the write lock while holding no monitor, and then wanted the monitor, it would deadlock with such a search. Therefore:

- Every writer that needs both takes the **monitor first** and only **tries** the write lock (`lockBuilderUnderMonitor`). Between tries it releases the monitor (`wait(1)`), unless the caller already held it, in which case it parks.
- While it waits it increments `exclusiveWaiters`. New searches hold back for it (`awaitNoPendingExclusive`, up to 100 ms), so a stream of readers cannot starve it. The gate is a count, so one waiter leaving does not open it for another.
- `lockBuilderUnderMonitor(monitorHeld, interruptible)`: the freeze and the optimization give up on an interrupt. `close()` waits on and restores the interrupt.
- The rebuild, recovery and the PQ switch hold only the monitor and take no `builderLock`. They wait for no lock a write-lock holder needs.
- `invalidateGraph()` takes only the write lock when the caller holds no monitor, and otherwise the group's locks all-or-nothing under the monitor (like `removeAll`/`reindex`), because replication and merge layers call it from a monitor-holding thread.
- The merge's publication barrier and `withdrawMerge` take the write lock blocking, but without the monitor.

### 8.3 Operations that reject the monitor

`close()`, `optimize()` and `persistToDisk()` (on-disk) throw `IllegalStateException` (`ensureNotHoldingParentMonitor`) when the caller holds the GigaMap's monitor. They wait for permits, a merge or a flush whose holders may need the monitor.

`doOptimize()` and `doPersistToDisk()` (called by the background task manager) tolerate a monitor holder.

### 8.4 Background threads

| Thread | Purpose |
|---|---|
| `VectorIndex-Background-<name>` | eventual indexing, scheduled optimization (`runOptimizationIfDirty`) and persistence (`runPersistenceIfDirty`), requested flushes, shutdown work. A weak-reference liveness check terminates it once the index is unreachable. |
| `VectorIndex-Background-<name>-flush` | background flush writes (created on first use, no idle thread) |
| `VectorIndex-Background-<name>-merge` | background merges (created on first use) |
| `vector-insert-<name>` pool | parallel batch inserts (long-lived, private) |
| `vector-rebuild-<name>` pool | one-shot rebuild workers |
| `eclipsestore-vector-checkpoint` | shared vector-file checkpoints |

Scheduled optimization and persistence run when `minChangesBetweenOptimizations` or `minChangesBetweenPersists` changes have accumulated.

### 8.5 Close

`close()` runs these steps in order:

1. Reject a monitor holder.
2. Set `mergeAbortRequested` and take `mergePermit`.
3. If the shutdown needs no graph, set `closeRequested` now, so a rebuild in flight gives up.
4. Shut the background task manager down (`shutdownBackgroundTaskManager` → `BackgroundTaskManager.shutdown` → `finalShutdownWork`):
   - drain the queue;
   - optimize if `optimizeOnShutdown` and changes are counted;
   - persist (`SHUTDOWN`) if `persistOnShutdown` and changes are counted.

   This runs within `shutdownPersistTimeoutMillis`, then the worker thread is joined. Without a manager, an on-disk index with `persistOnShutdown` persists directly.
5. Set `closeRequested`.
6. Set `flushAbortRequested` and take `flushPermit`.
7. Under the monitor and the write lock (`closeUnderLocks`), tear down (`closeInternalResources`):
   - searcher pools;
   - retire the sources (epoch++);
   - builder and graph;
   - managers;
   - the insert pool;
   - the vector file (checkpoint, header marked clean).
8. Set `closed`.

If the close fails, `closeRequested` and `mergeAbortRequested` are reset and the index stays open. `VectorIndices.close()` closes every index outside the monitor, also after one failed, and throws the first failure with the others suppressed.

---

## 9. Migration from 4.x (one-way)

Storages written by 4.0.0 to 4.2.0 open without preparation.

**Record.** `BinaryHandlerVectorIndexDefault` is a `PersistenceLegacyTypeHandlerSupplier`. `getLegacyTypeHandlers` returns `BinaryLegacyTypeHandlerVectorIndexDefault` for both old layouts: `(parent, name, configuration, vectorizer, vectorStore)`, with or without a trailing `structuralModCount`. The legacy handler sets:

- the four references and `structuralModCount` (0 for the layout without it);
- `legacyVectorStore` = the old `GigaMap<VectorEntry>` (null for an embedded index);
- `legacyLayout = true`.

Nonce, epoch and count stay 0, so the open replaces any vector file.

**Import** (`importLegacyVectorStore`, first access, under the monitor). A computed index writes the old store's vectors into the vector file without a vectorizer call. Up to 4.1.0 the old store was addressed by position, and its ids could drift from the entity ids, so the import validates:

- entries of entities that no longer exist (ghosts) are skipped;
- ids seen twice are marked conflicting, and the first vector stays;
- after a ghost, a conflict, or (when null vectors are not allowed) a count that does not match the map, one pass re-vectorizes every entity without exactly one entry.

A consistent store costs no vectorizer call. An embedded index has no store and recovers from the entities (a full rebuild of the file, which reads fields). After the import:

- `legacyVectorStore` is dropped, except for a computed index without a location (see below);
- the nonce is set;
- `structuralModCount` is bumped.

**Siblings.** Once its vectors are in place, the index clears `legacyLayout` and reports through `VectorIndices.internalReportIndexStateChange`, so the next store writes the current layout even without a mutation. That method first runs `importLegacyLayout` on every sibling index of the group, each in its own `try`, because the next store writes every index of the group. A sibling that fails is logged and retries at its own access. Until then `BinaryHandlerVectorIndexDefault.store` refuses to store it.

**Before the first store.** `internalReplaceConfiguration` (`changeIndexLocation`) runs the vector recovery first, so a location change cannot store an unimported record.

**Old files.** The old `<name>.graph/.meta/.pqv` files are ignored and logged once. An on-disk index finds no manifest, rebuilds from the vector file, and schedules a persist as segments.

**Computed in-memory index without a directory.** It imports into a temporary file, which the current layout cannot keep, so it holds on to `legacyVectorStore`, and `BinaryHandlerVectorIndexDefault.store` refuses the record (`legacyVectorStoreRefusal`) until one of these happens:

- `changeIndexLocation` gives it a location. For an index that keeps no files this is effective at once (`internalAssignFirstLocation`, under the monitor and the exclusive lock, which `VectorIndices.runExclusive` acquires the way `GigaMap` does for `removeAll`): a fresh vector file is opened at the location with the record's store epoch, every present vector is copied into it by repairs with its key, and the index is re-initialized on it, as `removeAll` does, so the graph is rebuilt from it at the next access. No vectorizer call. The same applies to a computed in-memory index created on this version without a location.
- `removeAll` or `reindex` drops or recomputes the vectors anyway.
- The system property `DROP_LEGACY_VECTORS_PROPERTY` (`org.eclipse.store.gigamap.jvector.dropLegacyVectorsWithoutLocation=true`) lets the import drop the store at once, as before; every later start then vectorizes every entity.

A restart before a successful store loads the 4.x record again and imports again, without a vectorizer call.

**One way.** After the next store the old vector store is unreferenced and reclaimed by the storage's housekeeping. A storage stored by this version cannot be opened by 4.x.

---

## 10. Failure handling summary

| Failure | Behaviour |
|---|---|
| Crash, power loss | Vector file repaired from the entities (scan or full repair per chunk, full rebuild for a replaced file). Manifest and segments consistent by the commit order. |
| Manifest witness mismatch | Manifest retired, graph rebuilt from the vector file, next persist replaces the segments. |
| Damaged `.l2g`, `.pqv`, manifest | Detected by CRC32: load rejected (invalid manifest: segments reaped), graph rebuilt. |
| Damaged `.graph` | Detected only when `OnDiskGraphIndex.load` fails (including the `Error`s it raises for damaged upper layers) or node counts disagree; otherwise served as is. |
| Vectorizer throws during recovery | Recovery stays pending; the access (search or mutation) fails; with the prepare-mutation hook the map is unchanged. |
| Vectorizer returns `null` or an invalid vector during recovery | Slot left empty, counted at ERROR. |
| Graph operation fails (sync or eventual) | Logged. On-disk: `consolidationRequired`, next persist rebuilds from the vector file. In-memory: the graph lacks the change for the session and is rebuilt at the next start. |
| Nodes lost by a reachability re-insert | `NodesLostException` through `repairLiveGraph` (escalated like a failed graph operation). |
| Flush fails | Frozen generations stay searchable and are retried; persist marked required. |
| Shutdown persist times out | The background executor is shut down with `shutdownNow` (interrupting the persist). The manifest commit is atomic, so the disk holds the old or the new manifest; whatever it does not cover is rebuilt from the vector file at the next start. |
| PQ/NVQ training declines or fails | Segments are written without the feature; retried after the data changes. |
| `reindex()` fails after validation | Index left partly rebuilt (section 6.6). |

---

## 11. Configuration that affects the design

Builder methods of `VectorIndexConfiguration.Builder` (defaults in parentheses):

| Option | Effect on the design |
|---|---|
| `dimension`, `similarityFunction` (COSINE), `maxDegree` (16), `beamWidth` (100), `minSearchBeamWidth` (100), `neighborOverflow` (1.2), `alpha` (1.2) | HNSW parameters; `rerankK = max(k, minSearchBeamWidth)` |
| `onDisk` (false), `indexDirectory` / `indexLocation` | on-disk segments and a persistent vector file; `onDisk` requires a directory or location. A location also keeps the vector file of a computed in-memory index. |
| `vectorStorage` (INLINE) | INLINE or NVQ vectors in segments (NVQ requires `onDisk`) |
| `approximateScoring` (NONE), `pqSubspaces` (0 = `dimension / 4`), `nvqSubvectors` (0 = 1) | section 5.4; FUSED_PQ requires `onDisk`; in-memory PQ_IN_MEMORY requires `optimizationIntervalMs > 0` |
| `eventualIndexing` (false) | graph mutations on the background worker (6.2) |
| `persistenceIntervalMs` (0 = off), `minChangesBetweenPersists` (100), `persistOnShutdown` (true), `shutdownPersistTimeoutMillis` (30 000) | background and shutdown persists; `persistenceIntervalMs > 0` requires `onDisk` |
| `flushThreshold` (100 000) | live nodes before a background flush; merge tier base |
| `maxSegments` (32, 2..240) | source count above which a persist consolidates |
| `segmentMergeFactor` (4, 2..240) | merge fan-in and tier width |
| `vectorCheckpointIntervalMs` (30 000) | checkpoint period of the vector file, bounding what a crash re-vectorizes |
| `vectorChunkBytes` (0 = max(64 MiB, 2 048 slots)) | chunk size of the vector file; a file with another chunk size is replaced at open |
| `optimizationIntervalMs` (0 = off), `minChangesBetweenOptimizations` (1000), `optimizeOnShutdown` (false) | in-memory maintenance and PQ switch; no effect on an on-disk index |
| `parallelOnDiskWrite` (false) | parallel segment graph writer |

System properties: `org.eclipse.store.gigamap.jvector.temporaryRoot` (section 3.1), `org.eclipse.store.gigamap.jvector.dropLegacyVectorsWithoutLocation` (section 9, the import of a computed in-memory index without a location).

---

## 12. Build and runtime requirements

- **Java 25+.** The module maps the vector file through the foreign memory API (`Arena`, `MemorySegment`, final in Java 22) and compiles at `maven.compiler.release` 25, like the modules that use it. The build requires JDK 25 (enforcer); the rest of Eclipse Store stays at `maven.compiler.release` 17.
- **jVector** `io.github.jbellis:jvector` `4.0.1`.
- **Module.** `module-info.java` declares `org.eclipes.store.gigamap.jvector` (the typo is kept for compatibility). It `requires transitive org.eclipse.store.gigamap` and `requires jvector`, exports `org.eclipse.store.gigamap.jvector` and `org.eclipse.store.gigamap.jvector.annotations`, and opens the main package to `org.eclipse.serializer.persistence`.
- **JVM flags.**
  - `--add-modules jdk.incubator.vector`: jVector's SIMD scoring. Without it jVector falls back to scalar code.
  - `--enable-native-access=ALL-UNNAMED`: jVector's memory-mapped graph reader on JDK 22+ makes a restricted call. On the module path jVector is the automatic module `jvector`, so the flag must name it: `--enable-native-access=jvector`.
  - The test JVM (surefire `argLine` in [`pom.xml`](pom.xml)) uses:

    ```
    --add-modules jdk.incubator.vector
    --enable-native-access=ALL-UNNAMED
    --add-exports java.base/jdk.internal.misc=ALL-UNNAMED,org.eclipse.serializer.base
    --add-opens   java.base/java.lang=ALL-UNNAMED,org.eclipse.serializer.base
    --add-opens   java.base/java.nio=ALL-UNNAMED,org.eclipse.serializer.base
    --add-opens   java.base/sun.nio.ch=ALL-UNNAMED,org.eclipse.serializer.base
    --add-reads   org.eclipse.serializer.base=ALL-UNNAMED
    ```

    The last five are the standard Eclipse Store opens.
- **Boot id.** Telling a restart from a reboot uses `/proc/sys/kernel/random/boot_id`. Without it (Windows, macOS) every unclean end repairs the hot chunks in full.
