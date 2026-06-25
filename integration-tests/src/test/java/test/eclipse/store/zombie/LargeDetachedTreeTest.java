package test.eclipse.store.zombie;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageGCZombieOidHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Scaled reproducer for the registry-safety-net zombie scenario: a large
 * subtree is detached from root (binary), held only via Java references,
 * and a substantial subset of its lazies is cleared so their targets are
 * reaped from the registry. On baseline this scenario produces 500 zombie
 * OIDs; on the fix it produces 0.
 *
 * <p>Disabled in CI because the scenario uses Java GC + WeakReference
 * timing (~13 s on a developer machine) and is non-deterministic enough
 * that CI noise could destabilise it. Run locally to confirm the fix's
 * scaled behaviour.
 */
@Disabled("Slow (~13 s) and timing-sensitive due to System.gc() reliance; "
    + "scaled reproducer of the registry-safety-net zombie pattern. "
    + "The single-holder variant (RegistrySafetyNetZombieTest) is the "
    + "canonical CI regression for the same root cause.")
public class LargeDetachedTreeTest
{
    @TempDir
    Path tempDir;

    EmbeddedStorageManager storage;
    EmbeddedStorageManager reloaded;

    @AfterEach
    public void afterTest()
    {
        if(this.reloaded != null)
        {
            try { this.reloaded.shutdown(); } catch(final Exception ignored) { }
        }
        if(this.storage != null && this.storage.isRunning())
        {
            try { this.storage.shutdown(); } catch(final Exception ignored) { }
        }
    }

    /**
     * Build a tree of 50 branches × 20 leaves; detach the entire container
     * from root binary; clear half of the lazies; reap their targets;
     * run several GC cycles; re-attach; reload + verify all data preserved.
     *
     * <p>Baseline: 500 zombie OIDs after re-attach. Fix: 0.
     */
    @Test
    void largeSubtreeWithMidLazyClearsAndCleanupPreservesData() throws Exception
    {
        final CountingZombieOidHandler zombieHandler = new CountingZombieOidHandler();

        this.storage = newStorage(zombieHandler);
        final PersistenceObjectRegistry registry = this.storage.persistenceManager().objectRegistry();

        final ApplicationRoot appRoot   = new ApplicationRoot();
        final Container       container = new Container("clearTestContainer");
        for(int b = 0; b < 50; b++)
        {
            final Branch branch = new Branch("branch-" + b);
            for(int l = 0; l < 20; l++)
            {
                branch.leaves.add(Lazy.Reference(new Leaf("leaf-" + b + "-" + l, b * 100 + l)));
            }
            container.branches.add(branch);
        }
        appRoot.container = container;
        this.storage.setRoot(appRoot);
        this.storage.storeRoot();

        // Probes for selected leaves so we can verify Java GC cleared them.
        final List<WeakReference<Leaf>> probes = new ArrayList<>();
        for(int b = 0; b < 50; b += 2)
        {
            for(int l = 0; l < 20; l += 2)
            {
                @SuppressWarnings("unchecked")
                final Lazy<Leaf> lazy = (Lazy<Leaf>) container.branches.get(b).leaves.get(l);
                probes.add(new WeakReference<>(lazy.get()));
            }
        }

        // Detach the container from root binary; keep Java reference.
        final Container heldContainer = appRoot.container;
        appRoot.container = null;
        this.storage.storeRoot();

        // Clear half the lazies (every 2nd branch, every 2nd leaf within).
        for(int b = 0; b < 50; b += 2)
        {
            for(int l = 0; l < 20; l += 2)
            {
                Lazy.clear(heldContainer.branches.get(b).leaves.get(l));
            }
        }

        // Force Java GC and registry cleanup.
        for(int i = 0; i < 30; i++)
        {
            boolean allCleared = true;
            for(final WeakReference<?> p : probes)
            {
                if(p.get() != null) { allCleared = false; break; }
            }
            if(allCleared) { break; }
            System.gc();
            Thread.sleep(50);
        }
        long stillAlive = 0;
        for(final WeakReference<?> p : probes)
        {
            if(p.get() != null) { stillAlive++; }
        }
        assumeTrue(stillAlive < probes.size() / 2,
            "JVM did not GC enough leaves to make this test meaningful (still alive: "
                + stillAlive + " of " + probes.size() + ")");

        registry.cleanUp();

        // GC cycles. Pre-sweep gate must seed Container + Branch + remaining
        // Lazy entities; mark walks transitively to leaves; sweep keeps them.
        for(int i = 0; i < 3; i++)
        {
            this.storage.issueFullGarbageCollection();
            Thread.sleep(200);
            assertEquals(0, zombieHandler.count(),
                "No zombies during cleared-lazies GC iter=" + i
                    + ", got " + zombieHandler.oids());
        }

        // Re-attach and reload.
        appRoot.container = heldContainer;
        this.storage.storeRoot();
        this.storage.issueFullGarbageCollection();
        Thread.sleep(200);
        assertEquals(0, zombieHandler.count(),
            "No zombies after re-attach with cleared lazies");

        this.storage.shutdown();

        final CountingZombieOidHandler reloadHandler = new CountingZombieOidHandler();
        this.reloaded = newStorage(reloadHandler);
        final ApplicationRoot reloadedRoot = (ApplicationRoot) this.reloaded.root();

        for(int b = 0; b < 50; b++)
        {
            final Branch branch = reloadedRoot.container.branches.get(b);
            for(int l = 0; l < 20; l++)
            {
                @SuppressWarnings("unchecked")
                final Lazy<Leaf> lazy = (Lazy<Leaf>) branch.leaves.get(l);
                final Leaf leaf = lazy.get();
                assertNotNull(leaf,
                    "Leaf " + b + "-" + l + " must reload (data-loss check)");
                assertEquals(b * 100 + l, leaf.value);
            }
        }

        this.reloaded.issueFullGarbageCollection();
        Thread.sleep(200);
        assertEquals(0, reloadHandler.count(),
            "No zombies after reload + final GC");
    }

    private EmbeddedStorageManager newStorage(final CountingZombieOidHandler handler)
    {
        return EmbeddedStorage.Foundation(
                Storage.ConfigurationBuilder()
                    .setChannelCountProvider (Storage.ChannelCountProvider(1))
                    .setHousekeepingController(Storage.HousekeepingController(50, 100_000_000))
                    .setDataFileEvaluator    (Storage.DataFileEvaluator(1024, 8192, 1.0))
                    .setStorageFileProvider  (Storage.FileProvider(this.tempDir))
                    .createConfiguration()
            )
            .setGCZombieOidHandler(handler)
            .start();
    }

    ///////////////////////////////////////////////////////////////////////////
    // data types //
    ///////////////

    public static class Leaf
    {
        public String label;
        public int    value;

        public Leaf(final String label, final int value)
        {
            super();
            this.label = label;
            this.value = value;
        }
    }

    public static class Branch
    {
        public String        label;
        public List<Lazy<?>> leaves = new ArrayList<>();

        public Branch(final String label)
        {
            super();
            this.label = label;
        }
    }

    public static class Container
    {
        public String       label;
        public List<Branch> branches = new ArrayList<>();

        public Container(final String label)
        {
            super();
            this.label = label;
        }
    }

    public static class ApplicationRoot
    {
        public Container container;
    }

    static final class CountingZombieOidHandler implements StorageGCZombieOidHandler
    {
        final AtomicInteger zombieCount = new AtomicInteger();
        final List<Long>    zombieOids  = new ArrayList<>();

        @Override
        public boolean handleZombieOid(final long objectId)
        {
            this.zombieCount.incrementAndGet();
            synchronized(this.zombieOids)
            {
                this.zombieOids.add(objectId);
            }
            return true;
        }

        public int count()
        {
            return this.zombieCount.get();
        }

        public List<Long> oids()
        {
            synchronized(this.zombieOids)
            {
                return new ArrayList<>(this.zombieOids);
            }
        }
    }
}
