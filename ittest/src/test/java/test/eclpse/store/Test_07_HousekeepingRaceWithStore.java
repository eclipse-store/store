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
import java.util.Random;

import test.eclpse.store.ZombieTestSupport.CountingZombieOidHandler;
import test.eclpse.store.ZombieTestSupport.ReloadResult;

/**
 * Scenario 07 — Housekeeping race with concurrent stores.
 * <p>
 * Drives the fix at high frequency: aggressive housekeeping (10 ms interval)
 * combined with rapid store/detach/JVM-GC churn over a fixed duration.  This
 * stresses the {@code resetCompletion} ↔ {@code completeSweep} race window.
 * <p>
 * PASS: zero zombies during run AND fresh reload succeeds.
 */
public class Test_07_HousekeepingRaceWithStore
{
    public static class Payload
    {
        public String d;
        public Payload(final String s) { this.d = s; }
    }
    public static class Holder
    {
        public Payload p;
        public int     v;
    }
    public static class DataRoot
    {
        public List<Holder> holders = new ArrayList<>();
    }

    private static final long DURATION_MS = 15_000L;

    public static boolean run() throws Exception
    {
        final Path workDir = ZombieTestSupport.freshWorkDir("t07-race");
        final CountingZombieOidHandler runHandler = new CountingZombieOidHandler("run");

        final EmbeddedStorageManager storage = ZombieTestSupport.foundation(
                workDir, 2, 10, 50_000_000L, 1024, 2048, 1.0, runHandler).start();

        final DataRoot root = new DataRoot();
        for(int i = 0; i < 30; i++) root.holders.add(makeHolder(i));
        storage.setRoot(root);
        storage.storeRoot();

        final Random rnd = new Random(42);
        final long deadline = System.currentTimeMillis() + DURATION_MS;
        long iter = 0;
        while(System.currentTimeMillis() < deadline)
        {
            final int idx = rnd.nextInt(root.holders.size());
            final Holder h = root.holders.get(idx);
            // mutate scalar
            h.v++;
            // sometimes replace payload (orphan old)
            if((iter & 7) == 0)
            {
                h.p = new Payload("p-" + iter);
            }
            // store the holder
            storage.store(h);
            // sometimes detach a holder and re-attach later
            if((iter & 31) == 0 && root.holders.size() > 5)
            {
                final Holder removed = root.holders.remove(rnd.nextInt(root.holders.size()));
                storage.store(root.holders);
                @SuppressWarnings("unused") final Holder kept = removed; // kept Java-alive
            }
            if((iter & 15) == 0)
            {
                System.gc();
            }
            iter++;
        }
        System.out.println("Race phase iterations: " + iter);

        ZombieTestSupport.runFullGc(storage, 4000);
        ZombieTestSupport.runFullGc(storage, 4000);

        StorageEntityCache.Default.setGarbageCollectionEnabled(false);
        storage.shutdown();
        StorageEntityCache.Default.setGarbageCollectionEnabled(true);

        final ReloadResult reload = ZombieTestSupport.reloadAndProbe(workDir, 2);
        return ZombieTestSupport.report("Test_07_HousekeepingRaceWithStore",
                runHandler, reload, "iterations=" + iter);
    }

    private static Holder makeHolder(final int i)
    {
        final Holder h = new Holder();
        h.p = new Payload("init-" + i);
        return h;
    }

    public static void main(final String[] args) throws Exception
    {
        System.exit(run() ? 0 : 1);
    }
}


