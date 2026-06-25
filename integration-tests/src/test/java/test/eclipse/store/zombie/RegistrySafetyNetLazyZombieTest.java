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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.Swizzling;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageGCZombieOidHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Lazy-aware variant of {@code RegistrySafetyNetZombieTest}: the safety-net
 * entity is a {@link Lazy} wrapping a chain {@code Lazy &rarr; Holder &rarr; Payload}.
 * Validates that the mark-seed fix marks transitively through a Lazy entity
 * — i.e. when a Lazy is kept alive only by the registry safety net, its
 * stored binary record (which contains the target OID) is walked by mark
 * <em>before</em> sweep deletes the target.
 *
 * <p>Pass condition: no zombie OIDs and the reloaded graph is intact
 * ({@code root.lazyHolder.get().payload != null}).
 */
public class RegistrySafetyNetLazyZombieTest
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
            try { this.reloaded.shutdown(); } catch(final Exception ignored) { /* best effort */ }
        }
        if(this.storage != null && this.storage.isRunning())
        {
            try { this.storage.shutdown(); } catch(final Exception ignored) { /* best effort */ }
        }
    }

    @Test
    void lazyHolderSurvivingViaSafetyNetDoesNotProduceZombieOids() throws Exception
    {
        final CountingZombieOidHandler zombieHandler = new CountingZombieOidHandler();

        // Phase 1: start storage, store root -> Lazy<Holder> -> Holder -> Payload
        this.storage = EmbeddedStorage.Foundation(
                Storage.ConfigurationBuilder()
                    .setChannelCountProvider (Storage.ChannelCountProvider(1))
                    .setHousekeepingController(Storage.HousekeepingController(100, 1_000_000_000))
                    .setDataFileEvaluator    (Storage.DataFileEvaluator(1024, 2048, 1.0))
                    .setStorageFileProvider  (Storage.FileProvider(this.tempDir))
                    .createConfiguration()
            )
            .setGCZombieOidHandler(zombieHandler)
            .start();

        final PersistenceObjectRegistry registry = this.storage.persistenceManager().objectRegistry();

        final DataRoot root = new DataRoot();
        final long holderOid;
        final long payloadOid;
        final WeakReference<Holder> holderProbe;
        {
            final Payload originalPayload = new Payload("ghost-via-lazy");
            final Holder  originalHolder  = new Holder(originalPayload);
            root.lazyHolder = Lazy.Reference(originalHolder);
            this.storage.setRoot(root);
            this.storage.storeRoot();

            holderOid    = registry.lookupObjectId(originalHolder);
            payloadOid   = registry.lookupObjectId(originalPayload);
            holderProbe  = new WeakReference<>(originalHolder);
        }
        assertNotEquals(Swizzling.notFoundId(), holderOid,  "Holder must be registered");
        assertNotEquals(Swizzling.notFoundId(), payloadOid, "Payload must be registered");

        // Phase 2: detach the Lazy from root in binary, keep Java ref to the Lazy itself.
        // This puts the Lazy entity in the registry-safety-net role.
        @SuppressWarnings("unchecked")
        final Lazy<Holder> lazyRef = (Lazy<Holder>) root.lazyHolder;
        root.lazyHolder = null;
        this.storage.storeRoot();

        // Phase 3: clear the Lazy (drops its strong subject reference) and reap the
        // Holder and Payload registry entries. The Lazy stays in the registry because
        // the test still holds a strong Java reference to lazyRef.
        Lazy.clear(lazyRef);

        for(int i = 0; i < 10 && holderProbe.get() != null; i++)
        {
            System.gc();
            Thread.sleep(50);
        }
        assumeTrue(holderProbe.get() == null,
            "JVM did not garbage-collect the Holder — test cannot proceed deterministically");

        // Reap cleared WeakReference entries.
        registry.cleanUp();

        // Phase 4: GC cycle 1.
        // With the fix: pre-sweep gate enqueues the Lazy's OID, mark walks the Lazy's
        // binary → Holder OID → enqueue → mark walks Holder's binary → Payload OID
        // → enqueue → all three transitively marked → none swept.
        this.storage.issueFullGarbageCollection();
        Thread.sleep(200);

        // Phase 5: re-attach Lazy to root. Lazy storer skips re-storing (already registered).
        root.lazyHolder = lazyRef;
        this.storage.storeRoot();

        // Phase 6: GC cycle 2.
        this.storage.issueFullGarbageCollection();
        Thread.sleep(200);

        assertEquals(0, zombieHandler.count(),
            "No zombie OIDs expected — got " + zombieHandler.count() + ": " + zombieHandler.oids());

        // Phase 7: shutdown and reload; verify the persisted graph is intact.
        this.storage.shutdown();

        final CountingZombieOidHandler reloadZombieHandler = new CountingZombieOidHandler();
        this.reloaded = EmbeddedStorage.Foundation(
                Storage.ConfigurationBuilder()
                    .setStorageFileProvider(Storage.FileProvider(this.tempDir))
                    .createConfiguration()
            )
            .setGCZombieOidHandler(reloadZombieHandler)
            .start();

        final DataRoot reloadedRoot = (DataRoot) this.reloaded.root();
        assertNotNull(reloadedRoot,                 "Reloaded root must not be null");
        assertNotNull(reloadedRoot.lazyHolder,      "Reloaded lazyHolder must not be null");

        @SuppressWarnings("unchecked")
        final Lazy<Holder> reloadedLazy = (Lazy<Holder>) reloadedRoot.lazyHolder;
        final Holder reloadedHolder = reloadedLazy.get();
        assertNotNull(reloadedHolder,           "Reloaded Holder via Lazy.get() must not be null");
        assertNotNull(reloadedHolder.payload,   "Reloaded Payload must not be null");
        assertEquals("ghost-via-lazy", reloadedHolder.payload.data);

        this.reloaded.issueFullGarbageCollection();
        Thread.sleep(200);
        assertEquals(0, reloadZombieHandler.count(),
            "No zombie OIDs expected on reloaded storage — got " + reloadZombieHandler.count()
                + ": " + reloadZombieHandler.oids());
    }

    ///////////////////////////////////////////////////////////////////////////
    // data types //
    ///////////////

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
        public final String data;

        public Payload(final String data)
        {
            super();
            this.data = data;
        }
    }

    public static class DataRoot
    {
        // Stored as a generic Lazy reference to keep the test data class field-compatible
        // with the runtime Lazy type produced by Lazy.Reference(...).
        public Lazy<?> lazyHolder;
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
