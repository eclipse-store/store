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

import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageEntityCache;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import test.eclpse.store.ZombieTestSupport.CountingZombieOidHandler;
import test.eclpse.store.ZombieTestSupport.ReloadResult;

/**
 * Scenario 08 — Combined classic safety-net vector PLUS Lazy reference for
 * the now-unreachable Payload.  The Holder's binary contains:
 * <ul>
 *   <li>a direct field reference to a Payload entity, and</li>
 *   <li>a {@link Lazy} field whose subject was never loaded but whose binary
 *       record carries a target OID.</li>
 * </ul>
 * Both kinds of references must be transitively followed by mark seeding.
 */
public class Test_08_SafetyNetWithUnloadedLazy
{
    public static class Payload
    {
        public String d;
        public Payload(final String s) { this.d = s; }
        @Override public String toString() { return "Payload[" + this.d + "]"; }
    }
    public static class Holder
    {
        public Payload       direct;
        public Lazy<Payload> lazy;
        @Override public String toString()
        {
            return "Holder[direct=" + this.direct + ", lazy=" + this.lazy + "]";
        }
    }
    public static class DataRoot
    {
        public List<Holder> holders = new ArrayList<>();
    }

    public static boolean run() throws Exception
    {
        final Path workDir = ZombieTestSupport.freshWorkDir("t08-lazy-unloaded");

        // ----- Setup phase: build, store, shut down -----
        {
            final CountingZombieOidHandler setup = new CountingZombieOidHandler("setup");
            final EmbeddedStorageManager s = ZombieTestSupport
                    .defaultFoundation(workDir, setup).start();
            final DataRoot root = new DataRoot();
            for(int i = 0; i < 10; i++)
            {
                final Holder h = new Holder();
                h.direct = new Payload("direct-" + i);
                h.lazy   = Lazy.Reference(new Payload("lazy-" + i));
                root.holders.add(h);
            }
            s.setRoot(root);
            s.storeRoot();
            StorageEntityCache.Default.setGarbageCollectionEnabled(false);
            s.shutdown();
            StorageEntityCache.Default.setGarbageCollectionEnabled(true);
        }

        // ----- Run phase: reopen, lazies are NOT loaded -----
        final CountingZombieOidHandler runHandler = new CountingZombieOidHandler("run");
        final EmbeddedStorageManager storage = ZombieTestSupport
                .defaultFoundation(workDir, runHandler).start();

        final DataRoot root = (DataRoot) storage.root();
        System.out.println("Reopened, " + root.holders.size() + " holders.");

        // Detach holders from root, keep them Java-alive.
        final List<Holder> kept = new ArrayList<>(root.holders);
        root.holders.clear();
        storage.store(root.holders);
        storage.storeRoot();
        System.out.println("Detached holders.");

        // Drop the direct payload Java refs.  Lazies are still cleared (not loaded).
        for(final Holder h : kept) h.direct = null;
        ZombieTestSupport.forceJvmGc(15, 100);
        ZombieTestSupport.triggerRegistryCleanup(storage);

        ZombieTestSupport.runFullGc(storage, 3000);
        ZombieTestSupport.runFullGc(storage, 3000);
        System.out.println("After 2 GCs zombies=" + runHandler.count());

        // Re-attach
        root.holders.addAll(kept);
        storage.store(root.holders);
        storage.storeRoot();

        ZombieTestSupport.runFullGc(storage, 3000);
        System.out.println("Re-attached + GC. zombies=" + runHandler.count());

        StorageEntityCache.Default.setGarbageCollectionEnabled(false);
        storage.shutdown();
        StorageEntityCache.Default.setGarbageCollectionEnabled(true);

        final ReloadResult reload = ZombieTestSupport.reloadAndProbe(workDir);
        // Probe lazy resolvability on reload.
        String extra = null;
        if(reload.success)
        {
            try
            {
                final EmbeddedStorageManager probe = ZombieTestSupport
                        .defaultFoundation(workDir, new CountingZombieOidHandler("probe")).start();
                try
                {
                    final DataRoot pr = (DataRoot) probe.root();
                    int loaded = 0, failed = 0;
                    for(final Holder h : pr.holders)
                    {
                        try {
                            if (h.lazy != null && h.lazy.get() != null) loaded++;
                        } catch (final Throwable t) {
                            failed++;
                        }
                    }
                    extra = "lazy resolved " + loaded + "/" + pr.holders.size()
                            + ", failures=" + failed;
                }
                finally
                {
                    StorageEntityCache.Default.setGarbageCollectionEnabled(false);
                    probe.shutdown();
                    StorageEntityCache.Default.setGarbageCollectionEnabled(true);
                }
            }
            catch(final Throwable t)
            {
                extra = "probe failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            }
        }
        return ZombieTestSupport.report("Test_08_SafetyNetWithUnloadedLazy",
                runHandler, reload, extra);
    }

    public static void main(final String[] args) throws Exception
    {
        System.exit(run() ? 0 : 1);
    }
}

