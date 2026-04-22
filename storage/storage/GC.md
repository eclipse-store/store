# Storage Garbage Collection

This document describes the EclipseStore storage garbage collector (storage GC), how it cooperates with the JVM's garbage collector, and the two-role *registry safety net* that keeps the persistent graph consistent when the application holds entities the persistent root no longer references.

---

## 1. Purpose

The storage GC reclaims on-disk space occupied by entities that are no longer reachable. Concretely it:

- Walks the persistent object graph starting from the persistent root.
- Marks every reached storage entity (a binary record in a storage channel file).
- Sweeps the entity cache, deleting entries whose binary records are not reachable.
- Triggers file-level compaction for the freed regions.

It is **not** the same thing as the JVM GC. Storage entities live in storage files (and in the per-channel in-memory `StorageEntityCache`); Java objects live on the JVM heap. A single logical datum can exist as *both* a storage entity (binary record + cache entry) and a live Java object — the two are correlated via an **object id** (OID).

Key classes:

| Concern | Class |
|---|---|
| Per-channel entity cache & mark/sweep driver | `StorageEntityCache.Default` |
| Cross-channel mark monitor, OID mark queue, completion state machine | `StorageEntityMarkMonitor.Default` |
| Per-channel mark queue (long[] buffer) | `StorageObjectIdMarkQueue` |
| Dangling-reference callback | `StorageGCZombieOidHandler` |
| Application-registry bridge (combined interface) | `LiveObjectIdsHandler` |
| — sweep filter half of the bridge | `ObjectIdsSelector` (serializer) |
| — mark-seed half of the bridge | `LiveObjectIdsIterator` |
| Embedded-mode implementation of the bridge | `EmbeddedStorageObjectRegistryCallback` |

---

## 2. Two garbage collectors, two domains

There are two independent collectors operating on correlated data:

| Aspect | JVM GC | Storage GC |
|---|---|---|
| Domain | Java heap objects | storage entities (binary records in channel files) |
| Reachability | strong/soft/weak references between Java objects | binary references (OIDs) between entities |
| Roots | thread stacks, statics, JNI, etc. | the persistent root OID; plus app-registry OIDs (see §7) |
| Trigger | JVM heuristics | storage housekeeping + `issueFullGarbageCollection` |
| Reclaims | heap memory | disk space (via file cleanup after sweep) |

These two worlds meet at the **`PersistenceObjectRegistry`**, which maps OIDs to live Java objects. The registry entries are `WeakReference`s — that is what lets JVM GC influence storage GC (see §8).

---

## 3. Mark and sweep

The storage GC is a classic tri-color mark-and-sweep, adapted for concurrent per-channel operation:

### Mark phase

Each channel has a `StorageObjectIdMarkQueue`. The `StorageEntityMarkMonitor` enqueues starting OIDs (see §4 for *what* gets enqueued). `StorageEntityCache.Default.incrementalMark` repeatedly pops an OID, looks up its entity via `getEntry(oid)`, walks that entity's binary references (via `iterateReferenceIds`) and pushes each referenced OID onto the appropriate channel's queue. Marked entities transition white → gray → black.

Critically: if `getEntry(oid)` returns `null`, the OID has no entity — either expected (a TID/CID, see §5) or a **zombie**. Control passes to `StorageGCZombieOidHandler.handleZombieOid(oid)`.

### Sweep phase

When all mark queues drain and `isMarkingComplete()` is true, `callToSweepRequired()` flips each channel into sweep mode. Each channel iterates its own entity cache and, per entity:

```java
// StorageEntityCache.Default#sweep, the keep-alive check:
if (item.isGcMarked() || isReachableInApplication.test(item.objectId)) {
    (last = item).markWhite();           // keep (reset to white for the next cycle)
} else {
    this.deleteEntity(item, sweepType, last);   // delete
}
```

Two things can rescue an entity:
1. It was gc-marked in the preceding mark phase — reachable from the persistent root (or a mark seed).
2. The predicate `isReachableInApplication` returned true — the **registry safety net**.

When the last channel finishes its sweep, `StorageEntityMarkMonitor.Default.completeSweep` seeds the next mark cycle with two complementary sets of roots: `determineAndEnqueueRootOid` enqueues the persistent root, and `enqueueLiveApplicationOids` enqueues every object id the application currently holds (see §9).

### Flowcharts by phase

The GC is easier to grasp one phase at a time. The five diagrams below partition the full tick, followed by a state diagram for the hot/cold flags.

#### 3.1. Store-side preparation

What a `store()` call does to the GC's state — three independent side effects that together "reopen" GC work:

```mermaid
flowchart LR
    Store(["application store /<br/>storeRoot"]) --> A["markEntityForChangedData<br/>gray-mark stored entity<br/>enqueue its OID"]
    Store --> B["markMonitor.resetCompletion<br/>gcHot = gcCold = false"]
    Store --> C["PersistenceObjectRegistry.cleanUp<br/>drain ReferenceQueue<br/>reap cleared WeakRefs"]
```

#### 3.2. GC tick dispatch

Every GC tick (`incrementalGarbageCollection`) starts here. The tick either runs one mark slice or one sweep pass — never both in the same tick.

```mermaid
flowchart TD
    Tick(["GC tick:<br/>housekeeping or<br/>issueFullGarbageCollection"]) --> Cold{"gcColdPhaseComplete?"}
    Cold -->|yes| Idle(["idle, return"])
    Cold -->|no| Sweep{"needsSweep?<br/>markingComplete AND<br/>no sweep in progress"}
    Sweep -->|no: marks pending| M(["→ mark phase (3.3)"])
    Sweep -->|yes| S(["→ sweep phase (3.4)"])
```

#### 3.3. Mark phase — `incrementalMark`

Pop an OID, resolve it to an entity, walk its binary references, mark black. Zombie detection is the left branch.

```mermaid
flowchart TD
    Enter(["enter mark phase"]) --> Pop{"pop next OID<br/>from mark queue"}
    Pop -->|queue empty| Done(["advanceMarking,<br/>return"])
    Pop -->|got OID| Get["getEntry oid"]
    Get --> Null{"entry == null?"}
    Null -->|yes| Z["zombieOidHandler<br/>.handleZombieOid"]
    Z --> T{"constant ID<br/>(CID)?"}
    T -->|yes: expected| Pop
    T -->|no: data OID| ZF["ZOMBIE OID detected"]
    ZF --> Pop
    Null -->|no| Blk{"already black?"}
    Blk -->|yes| Pop
    Blk -->|no| W["iterateReferenceIds<br/>push each referenced OID<br/>into mark queue"]
    W --> MB["entity.markBlack"]
    MB --> Pop

    classDef zombie fill:#fde0e0,stroke:#b03030,stroke-width:2px
    class ZF zombie
```

#### 3.4. Sweep phase — per channel

Each channel walks its own entity cache and applies the keep-alive predicate.

```mermaid
flowchart TD
    Enter(["enter sweep phase"]) --> R["resetMarkQueues<br/>all queues must be empty"]
    R --> I["initiateSweep<br/>set needsSweep=true<br/>for every channel"]
    I --> Loop["for each entity in<br/>this channel's cache"]
    Loop --> K{"isGcMarked OR<br/>isReachableInApplication?"}
    K -->|yes| Keep["markWhite:<br/>keep, reset colour<br/>for next cycle"]
    K -->|no| Del["deleteEntity:<br/>remove from cache,<br/>detach from file"]
    Keep --> Loop
    Del --> Loop
    Loop -->|all entities done| CS(["markMonitor.completeSweep<br/>→ (3.5)"])
```

#### 3.5. Sweep completion and re-seeding the next mark cycle

The transition point between waves. Only the last channel to call `completeSweep` advances the hot/cold flags and reseeds the mark queue with two complementary root sets — first the persistent root, then every live registry OID (the application-state roots, highlighted in green).

```mermaid
flowchart TD
    CS(["markMonitor.completeSweep"]) --> L{"last channel<br/>to finish sweep?"}
    L -->|no| R(["return, wait for<br/>remaining channels"])
    L -->|yes| Adv["advanceGcCompletion"]
    Adv --> H{"gcHotPhaseComplete<br/>already?"}
    H -->|no: first sweep of wave| SH["gcHotPhaseComplete = true"]
    H -->|yes: second sweep,<br/>no stores since| SC["gcColdPhaseComplete = true<br/>GC will idle after this"]
    SH --> Seed1["determineAndEnqueueRootOid<br/>enqueue persistent root"]
    SC --> Seed1
    Seed1 --> Seed2["enqueueLiveApplicationOids<br/>iterate registry, push every<br/>live OID as a mark root"]
    Seed2 --> N(["next GC tick"])

    classDef appRoots fill:#e5f6d9,stroke:#3d8b1e,stroke-width:2px
    class Seed2 appRoots
```

### Reading the diagrams together

- **Stores (3.1) are what reset the GC into "work pending" state** — three side effects: enqueue the stored entity, clear completion flags, drain the `ReferenceQueue`.
- **Every tick (3.2) first checks `gcColdPhaseComplete`.** If true, the GC is idle until a store reopens work.
- **A tick either marks (3.3) or sweeps (3.4), not both.** The sweep check gates the branch.
- **Zombie detection (3.3, red)** sits inside the mark loop at `getEntry(oid) == null`. In the mark phase the only expected null lookup is a constant id (CID) — constants are resolved at runtime rather than stored as entities. The default handler silently accepts CIDs and flags anything else as a zombie. (Type ids (TIDs) don't appear in binary references, so they cannot enter the mark queue in practice, but the default handler accepts them too as a defensive catch-all.)
- **The sweep keep-alive predicate (3.4)** is the OR of `isGcMarked()` and `isReachableInApplication(oid)` — that OR is the registry safety net (see §7, §8).
- **Completion + re-seeding (3.5)** establishes the two root sets for the next wave: after `determineAndEnqueueRootOid` seeds the persistent root, `enqueueLiveApplicationOids` pushes every live registry OID into the mark queue so the *next* mark phase transitively marks everything reachable from application state as well as everything reachable from the persistent root.

### Phase state diagram (hot / cold)

The hot/cold completion flags are their own little state machine, independent of the per-tick logic above:

```mermaid
stateDiagram-v2
    [*] --> ColdComplete: startup, gcHot and gcCold both true
    ColdComplete --> Dirty: store triggers resetCompletion
    Dirty --> HotComplete: first sweep finishes
    HotComplete --> Dirty: store triggers resetCompletion
    HotComplete --> ColdComplete: second sweep finishes, no stores since
    ColdComplete --> ColdComplete: GC tick returns immediately
```

- `Dirty` = at least one of hot/cold is `false`; the GC has real work (mark or sweep) to do.
- `HotComplete` = one full mark+sweep has happened since the last store. Unreachable entities from that store are gone, but a second confirmation pass is still owed.
- `ColdComplete` = the confirmation pass has run with no new stores. GC ticks are no-ops until a store reopens work.

`enqueueLiveApplicationOids` runs on **every** transition out of sweep (both `Dirty → HotComplete` and `HotComplete → ColdComplete`), so the application-state mark roots are re-established for every subsequent mark cycle, not just the first one in a wave.

---

## 4. Mark roots

The starting set of the mark phase consists of:

- **The persistent root OID** — determined per channel via `StorageRootOidSelector` and unified in `determineAndEnqueueRootOid`. This is what makes the persistent graph traversable at all.
- **Entities marked as "changed"** — when the application stores something, `markEntityForChangedData` gray-marks the stored entity and enqueues its OID. This is how newly-stored or updated data enters the mark cycle.
- **Live application-held OIDs** — at the end of every sweep, `enqueueLiveApplicationOids` iterates the `PersistenceObjectRegistry` and enqueues every live OID into the mark queues for the next cycle. This makes every entity the application currently holds a mark root, so the graph reachable from application state is traversed alongside the graph reachable from the persistent root. See §7–§9.

---

## 5. OID classes: TIDs, CIDs, OIDs

Not every long id in the system maps to a storage entity. `Persistence.IdType` defines four disjoint ranges:

| Range | Meaning | Has storage entity? |
|---|---|---|
| TID | Type id (class metadata) | No — types are resolved at runtime. |
| CID | Constant id (JLS constants) | No — constants are resolved at runtime. |
| OID | Regular object id (data entity) | **Yes** — this is what the storage GC actually tracks. |
| NULL / UNDEFINED | sentinel / invalid | No. |

This matters for mark-time. At mark time, the only realistic "null but expected" case is a **CID**: entity binaries reference constants by id, constants have no storage entity, so `getEntry` returns `null` but this is not a zombie. **TIDs** don't appear in binary references at all (they identify the entity's own type, stored separately in the record header), so they should never enter the mark queue in practice. `StorageGCZombieOidHandler.Default` returns `true` for both as a defensive catch-all, and `EmbeddedStorageObjectRegistryCallback.iterateLiveObjectIds` filters its seed set to `Persistence.IdType.OID` so non-data ids are never fed to the mark queue via the application-state root path.

---

## 6. Hot and cold phases

The mark monitor tracks two completion flags:

- **`gcHotPhaseComplete`** — "no new data has been received since the last sweep". One complete mark+sweep with no stores.
- **`gcColdPhaseComplete`** — "a second sweep has already run since then, so all unreachable entities are gone". One more mark+sweep with no stores after hot completion.

Only cold completion shuts the GC off until the next store. Stores (via `resetCompletion`) reset both flags, kicking the GC back into work.

The reason for two phases: the first sweep after a store cleans up the now-unreachable predecessors; the second sweep confirms the steady state. This two-pass pattern interacts with the registry safety net — which is why application-state OIDs are seeded at **every** sweep boundary, not just the first.

---

## 7. Crossing the JVM boundary: the object registry

`PersistenceObjectRegistry` (in the serializer module) maps OIDs ↔ Java objects. It is the only place where the storage GC can ask "does the application still care about this entity?" Entries are held as `java.lang.ref.WeakReference`s so that keeping an entity in the registry does not prevent the JVM GC from collecting the Java instance when the app drops it.

The embedded wiring exposes the registry to the storage GC via `EmbeddedStorageObjectRegistryCallback`, which implements our combined `LiveObjectIdsHandler` — i.e. both roles described below.

### Two roles the registry plays for the GC

| Aspect | `ObjectIdsSelector` | `LiveObjectIdsIterator` |
|---|---|---|
| Phase | sweep | end of sweep → seed next mark |
| Flow | sweep → registry (ask) | registry → mark queue (push) |
| API style | filter predicate via `ObjectIdsProcessor` | acceptor-based enumeration |
| Keeps alive | the asked-about entity | the entity **and** everything its binary transitively references |
| Purpose | "don't delete what the app still holds" | "make sure what the app holds is traversed" |

See the class-level javadoc on `LiveObjectIdsHandler` for the formal definition.

---

## 8. JVM `WeakReference` semantics and the registry's view of them

This is the most subtle part of the interaction between JVM GC and storage GC. It rests on a three-stage `WeakReference`/`ReferenceQueue` lifecycle that is **JDK behavior**, not Eclipse Store behavior:

1. **Strongly reachable** — `WeakReference.get()` returns the referent.
2. **Only weakly reachable** — the JVM GC *clears* the reference: `get()` starts returning `null`, and the `WeakReference` object itself (not the referent) is enqueued onto its `ReferenceQueue` if one was registered. The `WeakReference` instance remains in whatever container held it.
3. **Queue drained** — some application code polls the queue and removes the `WeakReference` from its container.

Between (2) and (3) there is a window in which a container (here, a hash table) still contains a `WeakReference` whose referent is gone. The JVM does **not** automatically remove weak references from containers — the application has to do it. `java.util.WeakHashMap.expungeStaleEntries()` is the canonical example of this idiom.

### How this plays out in `DefaultObjectRegistry`

```java
static final class Entry extends WeakReference<Object> {
    final long objectId;
    ...
}
```

The hash-table entries **are** the weak references. The registry exposes two contains-style operations:

- `synchContainsObjectId(oid)` — only compares `e.objectId == objectId`. Does **not** call `e.get()`.
- `synchContainsLiveObject(oid)` — returns `e.get() != null`, i.e. verifies the referent is still present.

These are surfaced as the public `containsObjectId(long)` and `containsLiveObject(long)` on `PersistenceObjectRegistry`.

### The predicate the safety net actually uses

`processLiveObjectIds` hands the storage GC this predicate:

```java
// DefaultObjectRegistry.java
processor.processObjectIdsByFilter(this::synchIsLiveObjectId);

final boolean synchIsLiveObjectId(final long objectId) {
    return this.synchContainsObjectId(objectId);    // id-only check
}
```

Despite the name, `synchIsLiveObjectId` delegates to the id-only `synchContainsObjectId`, **not** to `synchContainsLiveObject`. That means the safety-net predicate returns `true` for:

- (a) entries whose Java instance is still strongly reachable, **and**
- (b) entries whose Java instance has already been collected and whose `WeakReference` has been cleared, but whose `Entry` has not yet been removed from the hash table.

Case (b) is exactly the JDK window between stages 2 and 3.

### Why (b) usually isn't observed

`DefaultObjectRegistry.cleanUp()` drains the `ReferenceQueue` and calls `synchRemoveEntry` for each cleared reference. `cleanUp()` is invoked automatically on every storer merge path (via `PersistenceObjectManager.synchInternalMergeEntries`). In steady state, a sweep observes the registry right after a store has just run cleanup, so case (b) collapses and "survives sweep ↔ app still holds the Java instance" is true in practice. That is the design intent of the safety net.

### Sources

- JDK class javadoc of `java.lang.ref.WeakReference`, `java.lang.ref.Reference`, `java.lang.ref.ReferenceQueue` — spells out that the GC clears and enqueues but does not remove from user containers.
- JDK source of `java.util.WeakHashMap.expungeStaleEntries()` — the canonical "poll-the-queue and unlink" idiom that `DefaultObjectRegistry.cleanUp()` mirrors.
- Eclipse Serializer source: `DefaultObjectRegistry.java` — `Entry extends WeakReference` (class declaration), `synchContainsObjectId`, `synchContainsLiveObject`, `processLiveObjectIds`, `cleanUp`.

---

## 9. Design rationale: why two registry roles

The two registry-access roles in §7 are not redundant; each targets a distinct class of reachability that the other cannot cover.

- `ObjectIdsSelector` answers **"is this entity still needed?"** at sweep time. It protects an individual entity whose Java instance is currently held, even when the persistent graph no longer reaches it.
- `LiveObjectIdsIterator` answers **"what is reachable from application state?"** at mark time. It promotes every app-held entity to a mark root, so the mark phase walks the entity's binary references transitively.

### Why the sweep-time role alone would be insufficient

A sweep-time keep-alive check is *shallow*: it rescues the asked-about entity but not the entities that entity's binary record points to. The following scenario walks through what would happen with only the sweep-time role active:

1. Application stores `root → Holder → Payload`. All three entities exist in the cache and registry.
2. Application removes `Holder` from root's graph (`root.holder = null; storeRoot()`). Root's binary no longer references Holder. Holder's Java object is still alive in the app, so its registry entry stays. Holder's stored binary still references Payload's OID.
3. Application drops its Java reference to Payload. JVM GC clears Payload's `WeakReference`. A subsequent store triggers `cleanUp()`, which reaps Payload's entry from the registry.
4. Storage GC cycle runs.
   - Mark: starts from the persistent root. Root doesn't reference Holder, so without the iterator role neither Holder nor Payload would be marked.
   - Sweep: Holder is not marked but *is* in the registry → sweep-time safety net keeps it. Payload is not marked and *not* in the registry → Payload is deleted. Holder's stored binary still references Payload's OID.
5. Application re-attaches Holder to root (`root.holder = holderRef; storeRoot()`). The lazy storer notices Holder is already registered and **skips re-serializing it** (`BinaryStorer.Default.internalStore`: if `lookupOid(root) != notFound` return early). Holder's binary record is not rewritten — it still references the now-deleted Payload OID.
6. Next storage GC cycle.
   - Mark: root → Holder → iterate Holder's refs → `getEntry(payloadOid)` returns `null` → zombie OID, and on shutdown + reload `StorageExceptionConsistency: No entity found for objectId N`.

Three properties conspire to make this possible: the sweep-time safety net is shallow (keeps the entity but not its references), the lazy storer skips already-registered objects (so Holder's binary is never rewritten), and the JVM reaps Payload's registry entry between the two storage-GC cycles.

### How the mark-time role resolves it

`LiveObjectIdsIterator` promotes Holder to a mark root in step 4's mark phase. The marker then walks Holder's binary references and marks Payload — so Payload survives the sweep, Holder's binary stays valid, and no zombie ever arises. The two roles together cover both classes of reachability: the persistent-graph roots via `determineAndEnqueueRootOid`, and the application-state roots via `enqueueLiveApplicationOids`.

The sweep-time role remains as defense in depth for the narrow race of an OID entering the registry between mark and sweep of the same cycle.

---

## 10. Implementation of mark-time registry seeding

At the end of every sweep (`StorageEntityMarkMonitor.Default.completeSweep`), immediately after `determineAndEnqueueRootOid` seeds the persistent root, `enqueueLiveApplicationOids` pushes every currently-live registry OID into the mark queues:

```java
// StorageEntityMarkMonitor.Default#completeSweep (simplified)
this.determineAndEnqueueRootOid(rootOidSelector);
this.enqueueLiveApplicationOids(liveObjectIdsIterator);
```

The iterator (`EmbeddedStorageObjectRegistryCallback.iterateLiveObjectIds`) walks the registry's `iterateEntries`, skips cleared `WeakReference`s (`instance != null`), and filters to data OIDs (`Persistence.IdType.OID.isInRange(objectId)`) so TIDs and CIDs are never fed to the mark queue and therefore never reach the zombie handler.

### Interface layering

- `ObjectIdsSelector` (serializer) — sweep-time filter protocol.
- `LiveObjectIdsIterator` (this module) — mark-time enumeration protocol.
- `LiveObjectIdsHandler extends ObjectIdsSelector, LiveObjectIdsIterator` (this module) — combined interface the storage GC plumbing uses.
- `EmbeddedStorageObjectRegistryCallback extends LiveObjectIdsHandler` — embedded-mode implementation, backed by `PersistenceObjectRegistry`.

Internal wiring (`StorageFoundation`, `StorageSystem`, `StorageChannelsCreator`, `StorageEntityCache`) carries `LiveObjectIdsHandler` end to end, so the GC holds both capabilities in one typed reference.

---

## 11. Quick reference

Read these together to see the full picture:

- `StorageEntityCache.Default` — `sweep(_longPredicate)` (the safety-net keep-alive check), `incrementalMark` (the zombie detection site), `liveObjectIdsHandler` field.
- `StorageEntityMarkMonitor.Default` — `completeSweep`, `determineAndEnqueueRootOid`, `enqueueLiveApplicationOids`, `acceptObjectId`/`enqueue`.
- `LiveObjectIdsHandler` — class-level javadoc giving the role-by-role comparison.
- `EmbeddedStorageObjectRegistryCallback.Default` — `processSelected` (sweep filter) and `iterateLiveObjectIds` (mark seeding).
- `DefaultObjectRegistry` (serializer) — `Entry extends WeakReference`, `synchContainsObjectId` vs `synchContainsLiveObject`, `processLiveObjectIds`, `cleanUp`.
- `StorageGCZombieOidHandler.Default` — expected-null filter for TID/CID lookups, reporting path for any other null lookup.
- `Persistence.IdType` (serializer) — TID / OID / CID range predicates.
