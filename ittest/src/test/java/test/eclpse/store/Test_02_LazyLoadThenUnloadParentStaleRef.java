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
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageEntityCache;

import java.nio.file.Path;

import test.eclpse.store.ZombieTestSupport.CountingZombieOidHandler;
import test.eclpse.store.ZombieTestSupport.ReloadResult;

/**
 * Scenario 02 — Lazy load then unload, parent has stale binary.
 * <p>
 * This is the strongest hypothesised remaining zombie vector.
 *
 * <ol>
 *   <li>Store {@code root -> Container -> Lazy<Payload>}; shut down.</li>
 *   <li>Restart storage (fresh load).  Root is loaded; Lazy is unresolved.</li>
 *   <li>Call {@code lazy.get()} to materialise Payload (subject loaded).</li>
 *   <li>Call {@code lazy.clear()}: Lazy proxy drops its strong subject ref,
 *       but writes have already happened — Lazy's binary still references the
 *       Payload OID.</li>
 *   <li>Drop all application strong references to Payload.  JVM GC clears the
 *       registry's WeakReference for Payload.</li>
 *   <li>Mutate Container in some unrelated way.  Re-store Container.  The lazy
 *       storer sees Container is registered and therefore re-serialises its
 *       fields, but the {@link Lazy} field is in {@code cleared} state — does
 *       {@code BinaryHandlerLazyDefault.store()} now emit OID=0 (no subject)
 *       or OID=cachedOid (kept)?  This is the key question.</li>
 *   <li>Storage GC cycle.</li>
 *   <li>Shut down and reload — does load fail?</li>
 * </ol>
 *
 * Hypothesis: zombie likely if the Lazy field's binary keeps the subject OID
 * after {@code clear()} on re-store.  The fix's mark-time seeding promotes
 * Container as a mark root, then walks Container's binary (including the Lazy
 * field's OID), and either marks Payload (no zombie) or — if Payload was
 * already swept in an intervening cycle — reports a zombie.
 *
 * <p>PASS: zero zombies AND reload succeeds AND Payload is loadable on demand
 * (or the Lazy is correctly cleared).
 */
public class Test_02_LazyLoadThenUnloadParentStaleRef
{
    public static class Payload
    {
        public String data;
        public Payload(final String d) { this.data = d; }
        @Override public String toString() { return "Payload[" + this.data + "]"; }
    }

    public static class Container
    {
        public Lazy<Payload> lazyPayload;
        public String        tag;
        @Override public String toString()
        {
            return "Container[tag=" + this.tag + ", lazy=" + this.lazyPayload + "]";
        }
    }

    public static class DataRoot
    {
        public Container container;
    }

    public static boolean run() throws Exception
    {
        final Path workDir = ZombieTestSupport.freshWorkDir("t02-lazy-load-unload");

        // ------------------------------------------------------------------
        // Phase 1: build & store, shut down.
        // ------------------------------------------------------------------
        {
            final CountingZombieOidHandler firstHandler =
                    new CountingZombieOidHandler("setup");
            final EmbeddedStorageManager s = ZombieTestSupport
                    .defaultFoundation(workDir, firstHandler).start();
            final DataRoot root = new DataRoot();
            root.container = new Container();
            root.container.tag = "v1";
            root.container.lazyPayload = Lazy.Reference(new Payload("ghost-target"));
            s.setRoot(root);
            s.storeRoot();
            System.out.println("Phase 1 setup: stored, zombies=" + firstHandler.count());
            StorageEntityCache.Default.setGarbageCollectionEnabled(false);
            s.shutdown();
            // re-enable for the next run
            StorageEntityCache.Default.setGarbageCollectionEnabled(true);
        }

        // ------------------------------------------------------------------
        // Phase 2: reopen, force lazy resolution + clear, drop ref, GC.
        // ------------------------------------------------------------------
        final CountingZombieOidHandler runHandler = new CountingZombieOidHandler("run");
        final EmbeddedStorageManager storage = EmbeddedStorage.Foundation(
                Storage.ConfigurationBuilder()
                        .setChannelCountProvider(Storage.ChannelCountProvider(1))
                        .setHousekeepingController(Storage.HousekeepingController(100, 1_000_000_000L))
                        .setDataFileEvaluator(Storage.DataFileEvaluator(1024, 2048, 1.0))
                        .setStorageFileProvider(Storage.FileProvider(workDir))
                        .createConfiguration()
        ).setGCZombieOidHandler(runHandler).start();

        final PersistenceObjectRegistry registry = storage.persistenceManager().objectRegistry();

        final DataRoot root = (DataRoot) storage.root();
        System.out.println("Phase 2: reopened, registry size=" + registry.size());

        // Force load of the lazy subject — registers Payload in the live registry.
        Payload loaded = root.container.lazyPayload.get();
        System.out.println("Phase 2: lazy.get() => " + loaded
                + ", registry size=" + registry.size());

        // Now clear: Lazy proxy drops cached subject reference, but its
        // persisted binary still references Payload OID.
        root.container.lazyPayload.clear();
        System.out.println("Phase 2: lazy.clear() done; lazy.peek() = "
                + root.container.lazyPayload.peek());

        // Drop the application's strong reference.
        loaded = null;

        ZombieTestSupport.forceJvmGc(10, 100);

        // Mutate the container so its OWN re-store is justified, then re-store.
        // The lazy storer skips already-registered children but does re-serialise
        // the parent's own field block — including the Lazy holder's binary
        // reference (which BinaryHandlerLazyDefault writes from cached state).
        root.container.tag = "v2-mutated";
        storage.store(root.container);
        System.out.println("Phase 2: container.tag mutated and stored, registry size="
                + registry.size());

        ZombieTestSupport.forceJvmGc(10, 100);

        // Trigger registry cleanup
        final long[] regChange = ZombieTestSupport.triggerRegistryCleanup(storage);
        System.out.println("Phase 2: registry " + regChange[0] + " -> " + regChange[1]);

        // GC cycle 1 — should reclaim Payload if it is no longer reachable.
        ZombieTestSupport.runFullGc(storage, 3000);
        System.out.println("Phase 2: GC cycle 1 done, zombies=" + runHandler.count());

        // GC cycle 2 — second pass to trigger mark via re-seed
        ZombieTestSupport.runFullGc(storage, 3000);
        System.out.println("Phase 2: GC cycle 2 done, zombies=" + runHandler.count());

        StorageEntityCache.Default.setGarbageCollectionEnabled(false);
        storage.shutdown();

        // ------------------------------------------------------------------
        // Phase 3: probe — reopen and try to resolve the lazy reference.
        //          That is the moment a zombie OID would manifest as a
        //          ConsistencyException.
        // ------------------------------------------------------------------
        StorageEntityCache.Default.setGarbageCollectionEnabled(true);
        final ReloadResult reload = ZombieTestSupport.reloadAndProbe(workDir);
        String extra = "reload root=" + (reload.root != null ? "ok" : "null");
        if(reload.success && reload.root instanceof DataRoot)
        {
            // Try to resolve the lazy on the reloaded root, in a fresh manager.
            // We open one more connection just to test resolvability.
            final CountingZombieOidHandler probeHandler =
                    new CountingZombieOidHandler("probe");
            EmbeddedStorageManager probe = null;
            try
            {
                probe = EmbeddedStorage.Foundation(
                        Storage.ConfigurationBuilder()
                                .setStorageFileProvider(Storage.FileProvider(workDir))
                                .createConfiguration()
                ).setGCZombieOidHandler(probeHandler).start();
                final DataRoot probeRoot = (DataRoot) probe.root();
                Payload p;
                try
                {
                    p = probeRoot.container.lazyPayload.get();
                    extra += "; lazy.get on reload => " + p;
                }
                catch(final Throwable t)
                {
                    extra += "; lazy.get on reload FAILED: " + t.getClass().getSimpleName()
                            + ": " + t.getMessage();
                }
            }
            finally
            {
                if(probe != null)
                {
                    StorageEntityCache.Default.setGarbageCollectionEnabled(false);
                    try { probe.shutdown(); } catch(final Throwable ignored) { /* */ }
                    StorageEntityCache.Default.setGarbageCollectionEnabled(true);
                }
            }
        }

        return ZombieTestSupport.report("Test_02_LazyLoadThenUnloadParentStaleRef",
                runHandler, reload, extra);
    }

    public static void main(final String[] args) throws Exception
    {
        System.exit(run() ? 0 : 1);
    }
}

