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

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageEntityCache;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import test.eclpse.store.ZombieTestSupport.CountingZombieOidHandler;
import test.eclpse.store.ZombieTestSupport.ReloadResult;

/**
 * Scenario 09 — Control case for {@link Test_08_SafetyNetWithUnloadedLazy}.
 * <p>
 * Same shape as Test_08 but the Holder uses ONLY a direct {@code Payload}
 * field (no {@code Lazy}).  A persisted-but-not-loaded Lazy is the variable
 * under test in Test_08; this control confirms that without it the
 * registry-safety-net fix prevents zombies in the same workflow.
 * <p>
 * Expected: PASS (mirrors {@code RegistrySafetyNetZombieDemo} on the fix).
 */
public class Test_09_SafetyNetDirectOnlyControl
{
    public static class Payload
    {
        public String d;
        public Payload(final String s) { this.d = s; }
        @Override public String toString() { return "Payload[" + this.d + "]"; }
    }
    public static class Holder
    {
        public Payload direct;
    }
    public static class DataRoot
    {
        public List<Holder> holders = new ArrayList<>();
    }

    public static boolean run() throws Exception
    {
        final Path workDir = ZombieTestSupport.freshWorkDir("t09-direct-control");

        // ---- Setup
        {
            final CountingZombieOidHandler s0 = new CountingZombieOidHandler("setup");
            final EmbeddedStorageManager s = ZombieTestSupport.defaultFoundation(workDir, s0).start();
            final DataRoot root = new DataRoot();
            for(int i = 0; i < 10; i++)
            {
                final Holder h = new Holder();
                h.direct = new Payload("d-" + i);
                root.holders.add(h);
            }
            s.setRoot(root);
            s.storeRoot();
            StorageEntityCache.Default.setGarbageCollectionEnabled(false);
            s.shutdown();
            StorageEntityCache.Default.setGarbageCollectionEnabled(true);
        }

        // ---- Run: same workflow as Test_08 minus the Lazy field.
        final CountingZombieOidHandler runHandler = new CountingZombieOidHandler("run");
        final EmbeddedStorageManager storage = ZombieTestSupport
                .defaultFoundation(workDir, runHandler).start();

        final DataRoot root = (DataRoot) storage.root();

        final List<Holder> kept = new ArrayList<>(root.holders);
        root.holders.clear();
        storage.store(root.holders);
        storage.storeRoot();

        for(final Holder h : kept) h.direct = null;
        ZombieTestSupport.forceJvmGc(15, 100);
        ZombieTestSupport.triggerRegistryCleanup(storage);

        ZombieTestSupport.runFullGc(storage, 3000);
        ZombieTestSupport.runFullGc(storage, 3000);
        System.out.println("After 2 GCs zombies=" + runHandler.count());

        root.holders.addAll(kept);
        storage.store(root.holders);
        storage.storeRoot();
        ZombieTestSupport.runFullGc(storage, 3000);

        StorageEntityCache.Default.setGarbageCollectionEnabled(false);
        storage.shutdown();
        StorageEntityCache.Default.setGarbageCollectionEnabled(true);

        final ReloadResult reload = ZombieTestSupport.reloadAndProbe(workDir);
        return ZombieTestSupport.report("Test_09_SafetyNetDirectOnlyControl",
                runHandler, reload, "control: same as Test_08 but no Lazy field");
    }

    public static void main(final String[] args) throws Exception
    {
        System.exit(run() ? 0 : 1);
    }
}

