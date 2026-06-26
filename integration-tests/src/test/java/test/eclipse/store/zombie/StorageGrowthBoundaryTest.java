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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageGCZombieOidHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Boundary-at-scale reproducer: 5000 holders force the registry hash table
 * to rebuild multiple times during the lifetime of an orphan-but-Java-alive
 * pattern. Pre-sweep gate iteration walks every bucket of the rebuilt table;
 * if any entity were missed, mark seeding would skip it and sweep would
 * delete its referenced payload, producing zombies on the next cycle.
 *
 * <p>On baseline the test fails with ~2500 zombie OIDs after re-attach.
 * On the fix it produces 0 zombies and reload preserves all 5000 entries.
 *
 * <p>Disabled in CI: the test relies on {@code System.gc()} timing for the
 * Java GC + registry cleanUp phase and runs ~5 s per cycle. The
 * single-holder canonical test (RegistrySafetyNetZombieTest) covers the
 * same root cause for CI.
 */
@Disabled("Slow (~24 s) and timing-sensitive; scaled (5000-entity) variant "
        + "of the registry-safety-net zombie pattern that exercises hash-table "
        + "rebuild boundaries. The single-holder variant "
        + "(RegistrySafetyNetZombieTest) is the canonical CI regression for "
        + "the same root cause.")
public class StorageGrowthBoundaryTest
{
    @TempDir
    Path tempDir;

    EmbeddedStorageManager storage;
    EmbeddedStorageManager reloaded;

    @AfterEach
    public void afterTest()
    {
        if (this.reloaded != null) {
            try {
                this.reloaded.shutdown();
            } catch (final Exception ignored) {
            }
        }
        if (this.storage != null && this.storage.isRunning()) {
            try {
                this.storage.shutdown();
            } catch (final Exception ignored) {
            }
        }
    }

    @Test
    void registryGrowthAcrossHashTableRebuildBoundariesPreservesAllEntities() throws Exception
    {
        final CountingZombieOidHandler zombieHandler = new CountingZombieOidHandler();

        this.storage = newStorage(zombieHandler);
        final PersistenceObjectRegistry registry = this.storage.persistenceManager().objectRegistry();

        // Build the graph BEFORE the first setRoot/storeRoot so the initial
        // deep store captures all entities. Eclipse Store's storeRoot is shallow
        // (does not propagate mutations to referenced collections); explicit
        // store(root.entries) is needed for any later mutation.
        final int N = 5000;
        final List<Holder> holders = new ArrayList<>(N);
        final Root root = new Root();

        for (int i = 0; i < N; i++) {
            final Holder h = new Holder(new Payload("v-" + i, i));
            holders.add(h);
            root.entries.add(h);
        }
        this.storage.setRoot(root);
        this.storage.storeRoot();

        final long preGrowthSize = registry.size();

        // Settle once.
        this.storage.issueFullGarbageCollection();
        Thread.sleep(200);

        // Detach half the holders from root binary; keep Java refs.
        final List<Holder> detachedAlive = new ArrayList<>();
        for (int i = 0; i < N; i += 2) {
            detachedAlive.add(holders.get(i));
        }
        root.entries.removeIf(detachedAlive::contains);
        this.storage.storeRoot();
        this.storage.store(root.entries);  // propagate the list mutation

        // Drop java refs to the payloads of half the detached holders.
        for (int i = 0; i < detachedAlive.size(); i += 2) {
            detachedAlive.get(i).payload = null;
        }

        for (int i = 0; i < 20; i++) {
            System.gc();
            Thread.sleep(50);
        }
        registry.cleanUp();

        final long postCleanupSize = registry.size();
        assumeTrue(postCleanupSize < preGrowthSize,
                "JVM did not GC enough payloads (cleanUp removed nothing); test cannot proceed");

        // Several GC cycles — pre-sweep gate iterates the resized table each
        // time. With ~7500 still-live OIDs (root + remaining holders +
        // detachedAlive Java refs), iteration must visit all of them.
        for (int i = 0; i < 3; i++) {
            this.storage.issueFullGarbageCollection();
            Thread.sleep(200);
            assertEquals(0, zombieHandler.count(),
                    "No zombies expected during large-registry GC, iter=" + i
                            + ", got " + zombieHandler.oids());
        }

        // Re-attach all holders so reload verification finds them.
        for (final Holder h : detachedAlive) {
            if (!root.entries.contains(h)) {
                root.entries.add(h);
            }
        }
        this.storage.storeRoot();
        this.storage.store(root.entries);  // propagate list mutation
        this.storage.issueFullGarbageCollection();
        Thread.sleep(200);
        assertEquals(0, zombieHandler.count(), "No zombies after re-attach + GC");

        // Reload + verify N entities recovered.
        this.storage.shutdown();
        final CountingZombieOidHandler reloadHandler = new CountingZombieOidHandler();
        this.reloaded = newStorage(reloadHandler);
        final Root reloadedRoot = (Root) this.reloaded.root();
        assertEquals(N, reloadedRoot.entries.size(),
                "All N=" + N + " entries must reload");
        for (int i = 0; i < N; i++) {
            final Holder h = reloadedRoot.entries.get(i);
            assertNotNull(h, "Reloaded holder " + i + " must not be null");
        }
    }

    private EmbeddedStorageManager newStorage(final CountingZombieOidHandler handler)
    {
        return EmbeddedStorage.Foundation(
                        Storage.ConfigurationBuilder()
                                .setChannelCountProvider(Storage.ChannelCountProvider(1))
                                .setHousekeepingController(Storage.HousekeepingController(50, 100_000_000))
                                .setDataFileEvaluator(Storage.DataFileEvaluator(1024, 8192, 1.0))
                                .setStorageFileProvider(Storage.FileProvider(this.tempDir))
                                .createConfiguration()
                )
                .setGCZombieOidHandler(handler)
                .start();
    }

    ///////////////////////////////////////////////////////////////////////////
    // data types //

    /// ////////////

    public static class Holder
    {
        public Payload payload;

        public Holder(final Payload payload)
        {
            super();
            this.payload = payload;
        }
    }

    public static class Payload
    {
        public String label;
        public int value;

        public Payload(final String label, final int value)
        {
            super();
            this.label = label;
            this.value = value;
        }
    }

    public static class Root
    {
        public List<Holder> entries = new ArrayList<>();
    }

    static final class CountingZombieOidHandler implements StorageGCZombieOidHandler
    {
        final AtomicInteger zombieCount = new AtomicInteger();
        final List<Long> zombieOids = new ArrayList<>();

        @Override
        public boolean handleZombieOid(final long objectId)
        {
            this.zombieCount.incrementAndGet();
            synchronized (this.zombieOids) {
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
            synchronized (this.zombieOids) {
                return new ArrayList<>(this.zombieOids);
            }
        }
    }
}
