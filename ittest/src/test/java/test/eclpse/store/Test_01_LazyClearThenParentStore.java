package test.eclpse.store;

/*-
 * #%L
 * ittest
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

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageEntityCache;

import java.nio.file.Path;

import test.eclpse.store.ZombieTestSupport.CountingZombieOidHandler;
import test.eclpse.store.ZombieTestSupport.ReloadResult;

/**
 * Scenario 01 — {@code Lazy.clear()} then parent store with the lazy storer.
 * <p>
 * Builds {@code root -> Lazy<Payload>}.  Calls {@code lazy.clear()} on the
 * stored lazy reference, drops the application's strong reference to Payload,
 * forces JVM GC and registry cleanup, runs a storage GC cycle.  A second cycle
 * is then run after the parent root is re-stored.
 * <p>
 * Hypothesis: {@code BinaryHandlerLazyDefault.store()} writes the cached
 * subject OID even after a {@code clear()}.  If the subject's entity is swept
 * (registry entry gone), the lazy holder's binary still references the swept
 * OID, producing a zombie.  The fix's {@code enqueueLiveApplicationOids} only
 * helps if the lazy holder (or its parent) is in the registry to seed the
 * subject OID transitively — but {@code clear()} explicitly disconnects that
 * subject reference.  Outcome to verify: does the storage skip the subject in
 * a clean way (treat the cleared lazy specially) or does it produce a zombie?
 *
 * <p>PASS: zero zombies during run AND a fresh reload succeeds.
 */
public class Test_01_LazyClearThenParentStore
{
    public static class Payload
    {
        public String data;
        public Payload(final String d) { this.data = d; }
        @Override public String toString() { return "Payload[" + this.data + "]"; }
    }

    public static class DataRoot
    {
        public Lazy<Payload> lazyPayload;
        @Override public String toString() { return "DataRoot[lazy=" + this.lazyPayload + "]"; }
    }

    public static boolean run() throws Exception
    {
        final Path workDir = ZombieTestSupport.freshWorkDir("t01-lazy-clear");
        final CountingZombieOidHandler runHandler = new CountingZombieOidHandler("run");

        final EmbeddedStorageManager storage = ZombieTestSupport
                .defaultFoundation(workDir, runHandler).start();

        final PersistenceObjectRegistry registry = storage.persistenceManager().objectRegistry();

        // Phase 1: store root -> Lazy<Payload>
        final DataRoot root = new DataRoot();
        Payload payload = new Payload("initial-data");
        root.lazyPayload = Lazy.Reference(payload);
        storage.setRoot(root);
        storage.storeRoot();
        System.out.println("Phase 1: stored " + root + ", registry size=" + registry.size());

        // Phase 2: clear the lazy reference (cached subject dropped, OID kept).
        // Drop the application's strong reference to Payload too.
        final Payload returnedFromClear = root.lazyPayload.clear();
        System.out.println("Phase 2: lazy.clear() returned " + returnedFromClear);
        payload = null;

        ZombieTestSupport.forceJvmGc(10, 100);
        final long[] regChange = ZombieTestSupport.triggerRegistryCleanup(storage);
        System.out.println("Phase 2: registry " + regChange[0] + " -> " + regChange[1]
                + " after cleanup");

        // Phase 3: GC cycle 1 — Payload (no longer in registry, no longer reachable
        // because the lazy holder's *cached* subject is null) should be sweepable.
        // The lazy holder's binary still references Payload OID.
        ZombieTestSupport.runFullGc(storage, 3000);
        System.out.println("Phase 3: GC cycle 1 done. zombies so far=" + runHandler.count());

        // Phase 4: re-store root (lazy storer skips already-registered children).
        // The Lazy holder is in registry (root reaches it), so it is NOT re-serialized:
        // its binary still references the (possibly swept) Payload OID.
        storage.storeRoot();
        ZombieTestSupport.runFullGc(storage, 3000);
        System.out.println("Phase 4: GC cycle 2 done. zombies so far=" + runHandler.count());

        // Shutdown
        StorageEntityCache.Default.setGarbageCollectionEnabled(false);
        storage.shutdown();

        // Probe reload
        final ReloadResult reload = ZombieTestSupport.reloadAndProbe(workDir);

        return ZombieTestSupport.report("Test_01_LazyClearThenParentStore",
                runHandler, reload,
                "lazy.clear() leaves binary OID intact; subject Payload registry-evicted");
    }

    public static void main(final String[] args) throws Exception
    {
        System.exit(run() ? 0 : 1);
    }
}

