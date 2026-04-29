# QA Verification Report — Zombie OID Fix

**Author:** QA  
**Date:** 2026-04-29  
**Branch:** registry safety-net mark seeding (`enqueueLiveApplicationOids`)  
**Scope:** independent verification of the DEV fix, plus exploratory testing
focused on **lazy storer / lazy loading** vectors.

---

## 1. Executive verdict

The fix as currently implemented closes the originally-reported zombie-OID
window (single-channel safety net) and most variants, but **a real residual
zombie-OID defect remains** in scenarios involving a persisted-but-unloaded
`Lazy<T>` reference held by a registry-safety-net survivor.  The defect is
reproducible deterministically and causes both runtime zombie reports and
post-reload data corruption (`Lazy.get()` cannot resolve the subject).

| # | Scenario                                        | Verdict |
|---|-------------------------------------------------|---------|
| 01 | Lazy.clear then parent re-store                | **PASS** |
| 02 | Lazy load → unload, parent stale ref           | **PASS** |
| 03 | Multi-channel safety net (4 channels)          | **PASS** |
| 04 | Abandoned storer without commit                | **PASS** |
| 05 | Partial collection mutation                    | **PASS** |
| 06 | Cyclic safety-net survivors                    | **PASS** |
| 07 | Housekeeping race with concurrent stores (2 ch)| **PASS** |
| 08 | **Safety net + persisted unloaded `Lazy<T>`**  | **FAIL** |
| 09 | Control: same as 08 but without `Lazy<T>`      | **PASS** |

The juxtaposition of 08 (FAIL) and 09 (PASS) isolates the root cause to the
behaviour of an **unloaded `Lazy<T>` reference** when its owning entity
survives sweep through the registry safety net.

---

## 2. How the fix works (verified)

The fix instruments `StorageEntityMarkMonitor.Default.completeSweep` to call
`enqueueLiveApplicationOids` after the persistent root has been seeded.  This
iterates the `PersistenceObjectRegistry` and enqueues every OID whose
`WeakReference` is still live and whose id is in the data-OID range.  Effect:
on the *next* mark cycle, every entity the application currently holds is a
mark root, so its binary references are transitively followed.

This closes the original bug described in `RegistrySafetyNetZombieDemo` —
a Holder kept alive only by application reference, whose stored binary
references a Payload that has been GC-cleared from the registry, would
otherwise produce a zombie OID on the next mark cycle.  Verified directly by
Test_01, Test_05, Test_06, Test_09.

---

## 3. Residual defect (Test_08)

### 3.1 Reproduction

`Test_08_SafetyNetWithUnloadedLazy.java` builds:

```
root.holders = List<Holder>
Holder { Payload direct; Lazy<Payload> lazy; }
```

stores it, shuts down, then in a fresh JVM session:

1. Reopens storage — `lazy` fields are **unresolved Lazy proxies**, no subject
   instance has been loaded.
2. Detaches all holders from `root`, stores `root`.  Strong Java references
   to the holders are retained in the test (registry safety net engages).
3. Nulls every `holder.direct` reference.
4. Forces JVM GC + registry cleanup → `direct` Payload entries are evicted
   from the registry.
5. Issues two full storage GC cycles.

### 3.2 Result

* **70 zombie OIDs reported during the run** (the OIDs are the *lazy*
  subject OIDs — `1000…61`, `…63`, … `…79` — exactly 10 unique OIDs reported
  across multiple GC cycles).
* On reload:
  * Storage starts up.
  * A first GC cycle on the reloaded storage **immediately reports 30 more
    zombies** (3 cycles × 10 OIDs).
  * `lazy.get()` for **all 10 holders** throws (`lazy resolved 0/10,
    failures=10`).

This is **persistent on-disk data corruption**: the `Lazy` field's binary
record references an OID whose entity no longer exists in the storage, and
this state survives shutdown and reopen.

### 3.3 Root-cause hypothesis

`BinaryHandlerLazyDefault.iterateLoadableReferences` is a no-op for the
**loading** path (the standard "raw OID, but don't preload" semantics of
Lazy).  However, GC marking uses the binary traverser, which **does** follow
the OID embedded in the `Lazy` field's binary record.  When the holder is a
safety-net survivor:

1. The fix's `enqueueLiveApplicationOids` correctly seeds the holder's OID
   into the next mark cycle.
2. The marker walks the holder's binary, encounters the lazy subject OID,
   and pushes it into the queue.
3. `getEntry(subjectOid)` is **not null** in the entity cache as long as
   the subject's entity has not yet been swept.

The control case (Test_09) shows the seeding does work for `direct` fields:
the marker walks the holder's binary, enqueues the direct Payload OID, and
the Payload survives sweep.  **The difference for `Lazy<T>`** is that the
unloaded-Lazy never had its subject in the JVM registry (no `lazy.get()` was
ever called), so the subject Payload is **not in the registry, not held by
any Java reference, and not GC-marked from the persistent root** (we
detached holders from root).  The mark seeding rescues the Holder; the
holder's binary **does** reference the subject OID; the marker **should**
mark the subject; but the subject is being swept anyway.

This pinpoints either:

* a real defect in the binary traverser's handling of the `Lazy` field's
  reference (the marker is *not* visiting the lazy subject OID even though
  it is present in the binary); **or**
* a defect in mark-queue draining vs. sweep ordering for OIDs that arrive
  via the seeded-roots path after the persistent-root seed has already
  been processed.

The DEV team should investigate `BinaryHandlerLazyDefault` and
`BinaryReferenceTraverser`'s interaction during the marking phase, and
verify that the OID emitted for the Lazy field is in fact pushed into the
correct channel's mark queue and processed before sweep begins.

### 3.4 Severity

**Critical.**  This is exactly the symptom users have been reporting:
zombie OIDs that survive shutdown and prevent later loading of lazy data.
A typical real-world workload uses `Lazy<T>` extensively for collections
that are not accessed in every session; combined with detach/re-attach
patterns common in long-running services, this defect can corrupt storage
silently.

### 3.5 Workaround for users (until fixed)

Until this defect is resolved, applications should avoid the combination of
*both* of the following on a path reachable only through registry safety
net:

* persistent `Lazy<T>` fields that are not loaded in the current session, **and**
* application code that keeps the parent (lazy holder) Java-alive while
  detaching it from the persistent root and later re-attaching.

A safer alternative is to call `lazy.get()` (or use an eager storer to
re-serialise the parent) before any GC-triggering shutdown of the parent
from the persistent graph.

---

## 4. Test inventory

All scenarios live under
`ittest/src/test/java/test/eclpse/store/`:

* `ZombieTestSupport.java` — shared helpers: temp dir, foundation builder,
  counting zombie handler, JVM-GC forcer, registry-cleanup trigger,
  reload-and-probe.
* `Test_01_LazyClearThenParentStore.java`
* `Test_02_LazyLoadThenUnloadParentStaleRef.java`
* `Test_03_MultiChannelSafetyNet.java` (4 channels, 50 pairs)
* `Test_04_AbandonedStorer.java`
* `Test_05_PartialMutationCollectionReplace.java`
* `Test_06_CyclicSafetyNetSurvivors.java`
* `Test_07_HousekeepingRaceWithStore.java` (2 channels, 15 s churn)
* `Test_08_SafetyNetWithUnloadedLazy.java` ← **failing scenario**
* `Test_09_SafetyNetDirectOnlyControl.java` ← control for 08
* `Test_00_MasterRunner.java` — sequential runner with summary table.

### 4.1 How to run

Module ittest had to be unblocked from the parent's
`enforce-files-exist` profile, which activates if `src/main/java` exists.
Empty `src/main` directories were therefore removed (no product-code
modification).

```bash
# from repo root
mvn -DskipTests -pl storage/storage,storage/embedded,\
storage/embedded-configuration,afs/blobstore,afs/nio install

# whole suite
cd ittest
mvn exec:java -Dexec.classpathScope=test \
  -Dexec.mainClass=test.eclpse.store.Test_00_MasterRunner

# single scenario
mvn exec:java -Dexec.classpathScope=test \
  -Dexec.mainClass=test.eclpse.store.Test_08_SafetyNetWithUnloadedLazy
```

(No process timeouts are applied; macOS does not support them in Maven.)

---

## 5. Open questions for DEV

1. Is the OID emitted for an unloaded `Lazy<T>` field expected to be visited
   by the GC marker via `BinaryReferenceTraverser`?  Tests 01–02 (where the
   Lazy is reachable from the persistent root) suggest **yes**; test 08
   (where the Lazy is reachable only through the safety net) suggests
   the marker is **not visiting** that OID, or sweep is racing it.
2. Does `BinaryHandlerLazyDefault` correctly emit the subject OID for a Lazy
   reference whose subject was never loaded in this session?  Worth a unit
   test at the binary-handler level.
3. Is there a code path where `enqueueLiveApplicationOids` seeds a Holder
   OID but the marker iterates only fields the loader cares about
   (i.e. uses `iterateLoadableReferences` instead of the full binary
   traverser)?  This would directly explain 08.
4. Should the fix also iterate **all** OIDs reachable from a live
   application object (not just registry OIDs) at sweep boundary?

---

## 6. Conclusion

The fix is necessary but **not sufficient**.  One real residual zombie
vector remains around unloaded `Lazy<T>` references on safety-net
survivors, with persistent on-disk corruption.  Recommend treating
Test_08 as a regression test and gating the fix's release on resolving it.

