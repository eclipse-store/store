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

import test.eclpse.store.ZombieTestSupport.CountingZombieOidHandler;
import test.eclpse.store.ZombieTestSupport.ReloadResult;

/**
 * Scenario 06 — Cyclic safety-net survivors.
 * <p>
 * {@code A.b = B; B.a = A; A.payload = Payload}.  Detach A and B from root in
 * binary, keep both alive in Java.  Drop Payload's Java ref.  GC cycle.
 * <p>
 * Both A and B survive sweep via registry safety net but neither is gc-marked
 * from root.  Without the fix, marking from "live application OIDs" must
 * traverse A and B mutually and find Payload on A's binary.  With the fix's
 * seeding both A and B are mark roots, so Payload is marked through A's binary.
 */
public class Test_06_CyclicSafetyNetSurvivors
{
    public static class Payload
    {
        public final String data;
        public Payload(final String d) { this.data = d; }
        @Override public String toString() { return "Payload[" + this.data + "]"; }
    }

    public static class A
    {
        public B       b;
        public Payload payload;
    }

    public static class B
    {
        public A a;
    }

    public static class DataRoot
    {
        public A a;
    }

    public static boolean run() throws Exception
    {
        final Path workDir = ZombieTestSupport.freshWorkDir("t06-cyclic");
        final CountingZombieOidHandler runHandler = new CountingZombieOidHandler("run");

        final EmbeddedStorageManager storage = ZombieTestSupport
                .defaultFoundation(workDir, runHandler).start();

        final DataRoot root = new DataRoot();
        root.a = new A();
        root.a.b = new B();
        root.a.b.a = root.a;
        root.a.payload = new Payload("ghost");
        storage.setRoot(root);
        storage.storeRoot();

        // Keep A and B alive in Java.
        final A keepA = root.a;
        final B keepB = root.a.b;

        // Detach.
        root.a = null;
        storage.storeRoot();

        // Drop Payload ref.
        keepA.payload = null;
        ZombieTestSupport.forceJvmGc(10, 100);
        ZombieTestSupport.triggerRegistryCleanup(storage);

        ZombieTestSupport.runFullGc(storage, 3000);
        ZombieTestSupport.runFullGc(storage, 3000);
        System.out.println("zombies=" + runHandler.count());

        // re-attach for liveness probe
        root.a = keepA;
        storage.storeRoot();
        ZombieTestSupport.runFullGc(storage, 3000);
        @SuppressWarnings("unused")
        final B usedB = keepB; // keep until here

        StorageEntityCache.Default.setGarbageCollectionEnabled(false);
        storage.shutdown();
        StorageEntityCache.Default.setGarbageCollectionEnabled(true);

        final ReloadResult reload = ZombieTestSupport.reloadAndProbe(workDir);
        return ZombieTestSupport.report("Test_06_CyclicSafetyNetSurvivors",
                runHandler, reload, null);
    }

    public static void main(final String[] args) throws Exception
    {
        System.exit(run() ? 0 : 1);
    }
}

