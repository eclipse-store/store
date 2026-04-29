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

import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageEntityCache;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import test.eclpse.store.ZombieTestSupport.CountingZombieOidHandler;
import test.eclpse.store.ZombieTestSupport.ReloadResult;

/**
 * Scenario 04 — Multiple storers, one is abandoned without commit.
 * <p>
 * Two storers run sequentially in the same manager:
 * <ol>
 *   <li>storerA registers a Payload (assigns an OID locally) but does NOT commit.</li>
 *   <li>storerB stores a Holder that references the same Payload Java object,
 *       and commits.  Per {@code synchCheckLocalRegistries}, storerB should
 *       see the OID storerA assigned, but the Payload's binary is in storerA's
 *       buffer, not yet flushed to disk.</li>
 *   <li>storerA is then abandoned (reference dropped, Java GC collects it
 *       together with its uncommitted buffer).</li>
 *   <li>Storage GC cycle.  Holder's binary references Payload OID; if that
 *       Payload binary was never flushed, getEntry() will return null
 *       → ZOMBIE.</li>
 * </ol>
 * This test verifies the behaviour of {@code synchCheckLocalRegistries} when
 * the producing storer never commits.
 */
public class Test_04_AbandonedStorer
{
    public static class Payload
    {
        public String d;
        public Payload(final String s) { this.d = s; }
        @Override public String toString() { return "Payload[" + this.d + "]"; }
    }
    public static class Holder
    {
        public Payload p;
    }
    public static class DataRoot
    {
        public List<Holder> holders = new ArrayList<>();
    }

    public static boolean run() throws Exception
    {
        final Path workDir = ZombieTestSupport.freshWorkDir("t04-abandoned-storer");
        final CountingZombieOidHandler runHandler = new CountingZombieOidHandler("run");

        final EmbeddedStorageManager storage = ZombieTestSupport
                .defaultFoundation(workDir, runHandler).start();

        final DataRoot root = new DataRoot();
        storage.setRoot(root);
        storage.storeRoot();

        // Step 1: storerA registers Payload but does not commit.
        final Payload payload = new Payload("orphaned-by-abandon");
        Storer storerA = storage.createStorer();
        try
        {
            storerA.store(payload);
        }
        catch(final Throwable t)
        {
            System.out.println("storerA.store threw " + t);
        }
        // Do NOT call storerA.commit().

        // Step 2: storerB references the same payload via a Holder and commits.
        final Holder h = new Holder();
        h.p = payload;
        root.holders.add(h);

        boolean storerBOk = true;
        Throwable storerBError = null;
        try
        {
            storage.store(h);
            storage.storeRoot();
        }
        catch(final Throwable t)
        {
            storerBOk = false;
            storerBError = t;
            System.out.println("storerB store threw " + t);
        }

        // Step 3: abandon storerA, drop payload Java ref.
        storerA = null;
        // The payload object is also reachable from h.p, so we cannot fully drop it.
        // Run GC to potentially reclaim storerA's internal state.
        ZombieTestSupport.forceJvmGc(10, 100);
        ZombieTestSupport.triggerRegistryCleanup(storage);

        ZombieTestSupport.runFullGc(storage, 3000);
        ZombieTestSupport.runFullGc(storage, 3000);

        StorageEntityCache.Default.setGarbageCollectionEnabled(false);
        storage.shutdown();
        StorageEntityCache.Default.setGarbageCollectionEnabled(true);

        final ReloadResult reload = ZombieTestSupport.reloadAndProbe(workDir);
        String note = "storerB store " + (storerBOk ? "OK" : "FAIL: "
                + (storerBError != null ? storerBError.getClass().getSimpleName() : "?"));
        return ZombieTestSupport.report("Test_04_AbandonedStorer",
                runHandler, reload, note);
    }

    public static void main(final String[] args) throws Exception
    {
        System.exit(run() ? 0 : 1);
    }
}

