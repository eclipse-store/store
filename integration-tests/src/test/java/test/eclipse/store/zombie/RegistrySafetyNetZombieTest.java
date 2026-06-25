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
import org.eclipse.serializer.reference.Swizzling;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageGCZombieOidHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * JUnit port of {@code RegistrySafetyNetZombieDemo}. Exercises the corner
 * case described in {@code store/storage/storage/GC.md} §9: an entity that
 * survives a sweep <i>only</i> via the registry safety net retains its old
 * binary record; if that binary references another entity whose Java object
 * was collected and whose registry entry was reaped, the referenced entity
 * is swept. Without the {@code LiveObjectIdsIterator} mark-seed (§10) the
 * next mark cycle would surface a zombie OID.
 *
 * <p>Pass condition: the GC produces no zombie OIDs and the reloaded graph
 * is intact (Payload reachable via {@code root.holder.payload}).
 */
public class RegistrySafetyNetZombieTest
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
    void registrySafetyNetDoesNotProduceZombieOidOrCorruption() throws Exception
    {
        final CountingZombieOidHandler zombieHandler = new CountingZombieOidHandler();

        // Phase 1: start storage, store root -> Holder -> Payload
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
        final long payloadOid;
        final WeakReference<Payload> payloadProbe;
        {
            // Scope the only strong Payload reference to this block so it can
            // become eligible for GC after Phase 3 nulls Holder.payload.
            final Payload originalPayload = new Payload("I will become a ghost");
            root.holder = new Holder(originalPayload);
            this.storage.setRoot(root);
            this.storage.storeRoot();

            payloadOid    = registry.lookupObjectId(originalPayload);
            payloadProbe  = new WeakReference<>(originalPayload);
        }
        assertNotEquals(Swizzling.notFoundId(), payloadOid,
            "Payload must be registered after initial store");

        // Phase 2: detach Holder from root in binary, keep Java ref alive
        final Holder holderRef = root.holder;
        root.holder = null;
        this.storage.storeRoot();

        // Phase 3: drop the last strong Payload reference and reap its registry entry.
        holderRef.payload = null;

        for(int i = 0; i < 10 && payloadProbe.get() != null; i++)
        {
            System.gc();
            Thread.sleep(50);
        }
        assumeTrue(payloadProbe.get() == null,
            "JVM did not garbage-collect the Payload — test cannot proceed deterministically");

        // Reap the cleared WeakReference Entry from the registry hash table.
        // Same code path that synchInternalMergeEntries triggers on every store
        // (see GC.md §8); calling it directly avoids the sizing noise of an
        // intermediate store() call.
        registry.cleanUp();

        // Phase 4: GC cycle 1 — Payload swept; Holder kept alive by safety net,
        // its stored binary still references Payload's now-stale OID.
        this.storage.issueFullGarbageCollection();
        Thread.sleep(200);

        // Phase 5: re-attach Holder to root; lazy storer skips re-storing the
        // already-registered Holder, so its stale binary record persists.
        root.holder = holderRef;
        this.storage.storeRoot();

        // Phase 6: GC cycle 2 — without the LiveObjectIdsIterator mark-seed
        // (GC.md §10), marking Holder's stale binary would surface Payload's
        // swept OID as a zombie here.
        this.storage.issueFullGarbageCollection();
        Thread.sleep(200);

        assertEquals(0, zombieHandler.count(),
            "No zombie OIDs expected after the second GC cycle, but got "
                + zombieHandler.count() + ": " + zombieHandler.oids());

        // Phase 7: shut down and reload to confirm the persisted graph is intact.
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
        assertNotNull(reloadedRoot,                     "Reloaded root must not be null");
        assertNotNull(reloadedRoot.holder,              "Reloaded holder must not be null");
        assertNotNull(reloadedRoot.holder.payload,
            "Reloaded Payload must not be null — losing it means a zombie OID corrupted the store");
        assertEquals("I will become a ghost", reloadedRoot.holder.payload.data,
            "Reloaded Payload data must match the originally stored value");

        this.reloaded.issueFullGarbageCollection();
        Thread.sleep(200);
        assertEquals(0, reloadZombieHandler.count(),
            "No zombie OIDs expected on reloaded storage, but got "
                + reloadZombieHandler.count() + ": " + reloadZombieHandler.oids());
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

        @Override
        public String toString()
        {
            return "Holder[payload=" + this.payload + "]";
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

        @Override
        public String toString()
        {
            return "Payload[" + this.data + "]";
        }
    }

    public static class DataRoot
    {
        public Holder holder;

        @Override
        public String toString()
        {
            return "DataRoot[holder=" + this.holder + "]";
        }
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
