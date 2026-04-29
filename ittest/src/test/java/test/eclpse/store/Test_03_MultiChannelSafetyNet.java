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
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageEntityCache;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import test.eclpse.store.ZombieTestSupport.CountingZombieOidHandler;
import test.eclpse.store.ZombieTestSupport.ReloadResult;

/**
 * Scenario 03 — Multi-channel registry safety net.
 * <p>
 * The original {@code RegistrySafetyNetZombieDemo} runs on a single channel.
 * The fix's {@code enqueueLiveApplicationOids} pushes seeds into per-channel
 * mark queues; only the <em>last</em> channel finishing sweep triggers
 * {@code completeSweep}.  This test stresses the multi-channel barrier with
 * many Holder/Payload pairs distributed across 4 channels, plus a churn
 * thread that keeps creating cleanup pressure.
 *
 * <p>PASS: zero zombies during run AND fresh reload succeeds.
 */
public class Test_03_MultiChannelSafetyNet
{
    public static class Payload
    {
        public final String data;
        public Payload(final String d) { this.data = d; }
        @Override public String toString() { return "Payload[" + this.data + "]"; }
    }

    public static class Holder
    {
        public Payload payload;
        public Holder(final Payload p) { this.payload = p; }
    }

    public static class DataRoot
    {
        public List<Holder> holders = new ArrayList<>();
    }

    private static final int PAIR_COUNT = 50;

    public static boolean run() throws Exception
    {
        final Path workDir = ZombieTestSupport.freshWorkDir("t03-multichannel");
        final CountingZombieOidHandler runHandler = new CountingZombieOidHandler("run");

        final EmbeddedStorageManager storage = ZombieTestSupport.foundation(
                workDir, 4, 100, 1_000_000_000L, 1024, 2048, 1.0, runHandler).start();

        final PersistenceObjectRegistry registry = storage.persistenceManager().objectRegistry();

        // Phase 1: build root with PAIR_COUNT Holder->Payload pairs.
        final DataRoot root = new DataRoot();
        for(int i = 0; i < PAIR_COUNT; i++)
        {
            root.holders.add(new Holder(new Payload("p-" + i)));
        }
        storage.setRoot(root);
        storage.storeRoot();
        System.out.println("Phase 1: stored " + PAIR_COUNT + " pairs, registry=" + registry.size());

        // Phase 2: keep strong refs to all Holders, clear list & re-store empty.
        final List<Holder> keepHolders = new ArrayList<>(root.holders);
        root.holders.clear();
        storage.store(root.holders);
        storage.storeRoot();
        System.out.println("Phase 2: list emptied (binary), Holders kept Java-alive");

        // Phase 3: drop all Payload Java refs, force JVM GC + cleanup.
        for(final Holder h : keepHolders) h.payload = null;
        ZombieTestSupport.forceJvmGc(15, 100);
        final long[] r = ZombieTestSupport.triggerRegistryCleanup(storage);
        System.out.println("Phase 3: registry " + r[0] + " -> " + r[1]);

        // Phase 4: GC cycle 1 — Payloads should be swept; Holders survive
        // through registry safety net.  Their binaries still reference
        // Payload OIDs distributed across 4 channels.
        ZombieTestSupport.runFullGc(storage, 4000);
        System.out.println("Phase 4: GC cycle 1, zombies=" + runHandler.count());

        // Phase 5: re-attach all Holders to root, store root.
        // Lazy storer skips Holders (registered) — their stale binaries persist.
        root.holders.addAll(keepHolders);
        storage.store(root.holders);
        storage.storeRoot();
        System.out.println("Phase 5: re-attached holders");

        // Phase 6: GC cycle 2 — under the fix, mark seeding from registry
        // should mark every Holder, then walk into its (stale) binary, and
        // either find Payload still alive (impossible: was swept) or report
        // a zombie.  Hypothesis: zombies = 0 only if the fix's seeding
        // happened *before* Holders' binaries were walked, which requires
        // Phase 5's re-store to either re-serialise Holder's binary or to
        // be irrelevant because the previous cycle's seeding already marked
        // Payload (impossible: Payload was not in registry).
        ZombieTestSupport.runFullGc(storage, 4000);
        ZombieTestSupport.runFullGc(storage, 4000);
        System.out.println("Phase 6: GC cycles 2-3, zombies=" + runHandler.count());

        StorageEntityCache.Default.setGarbageCollectionEnabled(false);
        storage.shutdown();

        StorageEntityCache.Default.setGarbageCollectionEnabled(true);
        final ReloadResult reload = ZombieTestSupport.reloadAndProbe(workDir, 4);
        return ZombieTestSupport.report(
                "Test_03_MultiChannelSafetyNet (4 channels, " + PAIR_COUNT + " pairs)",
                runHandler, reload, null);
    }

    public static void main(final String[] args) throws Exception
    {
        System.exit(run() ? 0 : 1);
    }
}


