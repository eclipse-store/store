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

import org.eclipse.serializer.persistence.types.*;
import org.eclipse.store.storage.embedded.types.*;
import org.eclipse.store.storage.types.*;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Produces zombie OIDs without LazyHashMap or custom ObjectIdsSelector.
 *
 * <h2>Mechanism</h2>
 * The sweep keeps an entity alive if it is GC-marked <b>or</b>
 * {@code isReachableInApplication} (i.e. its Java object is still in the
 * {@link PersistenceObjectRegistry}).
 * <p>
 * An entity that survives sweep <b>only</b> via the registry safety net
 * (not GC-marked) retains its <b>old</b> binary record.  The lazy storer
 * will not re-store it because it is already registered.  If that old
 * binary references another entity whose Java object <b>was</b> collected
 * and whose registry entry was cleaned up, the referenced entity is
 * swept (deleted from the cache).  On the <b>next</b> GC cycle the
 * surviving entity's stale binary references are traversed during
 * marking and the swept OID is looked up &rarr; {@code null} &rarr;
 * <b>zombie OID</b>.
 *
 * <h2>Steps</h2>
 * <ol>
 *   <li>Store root &rarr; Holder &rarr; Payload.</li>
 *   <li>Disconnect Holder from root in Java <b>and</b> store root
 *       (root's binary no longer references Holder).  Keep a strong
 *       Java reference to Holder so it stays in the registry.</li>
 *   <li>Drop all Java references to Payload.  Java-GC it.
 *       The next {@code store()} call triggers internal
 *       {@code objectRegistry.cleanUp()} via the storer merge path,
 *       removing Payload's cleared {@code WeakReference} entry.</li>
 *   <li>Storage GC cycle 1 &ndash; Payload is not marked and not in
 *       registry &rarr; <b>swept</b>.  Holder is not marked but IS in
 *       registry &rarr; <b>survives</b> with its old binary that still
 *       references Payload's OID.</li>
 *   <li>Re-attach Holder to root, store root (lazy storer sees Holder
 *       is registered, skips it &ndash; old binary persists).</li>
 *   <li>Storage GC cycle 2 &ndash; mark root &rarr; mark Holder &rarr;
 *       iterate Holder's binary references &rarr; enqueue Payload's OID
 *       &rarr; {@code getEntry()} returns {@code null} &rarr;
 *       <b>ZOMBIE</b>.</li>
 * </ol>
 */
public class RegistrySafetyNetZombieDemo
{
    ///////////////////////////////////////////////////////////////////////////
    // data types //
    ///////////////

    /**
     * Simple wrapper that holds a reference to a {@link Payload}.
     * Its stored binary record will contain Payload's OID.
     */
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

    /**
     * The object that will become a zombie target: swept from the entity
     * cache while Holder's binary still references its OID.
     */
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

    /**
     * Mutable root that can attach/detach a Holder.
     */
    public static class DataRoot
    {
        public Holder holder;

        @Override
        public String toString()
        {
            return "DataRoot[holder=" + this.holder + "]";
        }
    }

    /**
     * Counts and logs zombie OIDs.
     */
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
            System.out.println("  >>> ZOMBIE OID detected: " + objectId);
            return true; // handled, don't log again
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


    ///////////////////////////////////////////////////////////////////////////
    // main //
    /////////

    private static final Path WORK_DIR = Paths.get("c:/temp/RegistrySafetyNetZombieDemo");

    public static void main(final String[] args) throws Exception
    {
        // clean start
        deleteDirectory(WORK_DIR);

        final CountingZombieOidHandler zombieHandler = new CountingZombieOidHandler();

        // ---------------------------------------------------------------
        // Phase 1 – Start storage, store root → Holder → Payload
        // ---------------------------------------------------------------
        System.out.println("=== Phase 1: Start storage, store root -> Holder -> Payload ===");

        final EmbeddedStorageManager storage = EmbeddedStorage.Foundation(
                        Storage.ConfigurationBuilder()
                                .setChannelCountProvider(Storage.ChannelCountProvider(1))
                                .setHousekeepingController(Storage.HousekeepingController(100, 1_000_000_000))
                                .setDataFileEvaluator(Storage.DataFileEvaluator(1024, 2048, 1.0))
                                .setStorageFileProvider(Storage.FileProvider(WORK_DIR))
                                .createConfiguration()
                )
                .setGCZombieOidHandler(zombieHandler)
                .start();

        final PersistenceObjectRegistry registry =
                storage.persistenceManager().objectRegistry();

        final DataRoot root = new DataRoot();
        root.holder = new Holder(new Payload("I will become a ghost"));
        storage.setRoot(root);
        storage.storeRoot();

        System.out.println("Stored: " + root);
        System.out.println("Registry size: " + registry.size());

        // ---------------------------------------------------------------
        // Phase 2 – Detach Holder from root (in binary), keep Java ref
        // ---------------------------------------------------------------
        System.out.println("\n=== Phase 2: Detach Holder from root, keep Java ref ===");

        // Keep a strong reference to Holder so its registry entry stays alive
        final Holder holderRef = root.holder;

        // Detach from root and store → root's binary no longer references Holder
        root.holder = null;
        storage.storeRoot();

        System.out.println("Root stored without Holder. Holder is now orphaned in binary graph.");
        System.out.println("But Holder Java object is alive → registry entry intact.");

        // ---------------------------------------------------------------
        // Phase 3 – Drop Payload's Java reference, GC it, clean registry
        // ---------------------------------------------------------------
        System.out.println("\n=== Phase 3: Drop Payload Java ref, GC, trigger internal cleanUp ===");

        // Null out Payload reference in Holder.  Do NOT re-store Holder!
        // Holder's binary in storage still references Payload's OID.
        holderRef.payload = null;

        // Force Java GC to collect the Payload object
        for(int i = 0; i < 10; i++)
        {
            System.gc();
            Thread.sleep(100);
        }

        // Store a NEW dummy object to trigger the storer commit → merge → cleanUp path.
        // PersistenceObjectManager.synchInternalMergeEntries calls objectRegistry.cleanUp()
        // which polls the ReferenceQueue and removes Payload's cleared WeakReference entry.
        // No explicit registry.cleanUp() call needed!
        final long regBefore = registry.size();
        storage.store(new String("trigger-cleanup-via-store"));
        final long regAfter = registry.size();
        System.out.println("Registry: " + regBefore + " → " + regAfter
                + " (removed " + (regBefore - regAfter) + " via internal cleanUp during store)");

        // ---------------------------------------------------------------
        // Phase 4 – Storage GC cycle 1: sweep Payload, Holder survives
        // ---------------------------------------------------------------
        System.out.println("\n=== Phase 4: Storage GC cycle 1 (Payload should be swept) ===");
        System.out.println("  Payload: NOT marked (no binary path from root), NOT in registry → SWEPT");
        System.out.println("  Holder:  NOT marked, but IN registry (Java ref alive) → SURVIVES");
        System.out.println("  Holder's binary STILL references Payload's OID (never re-stored).");

        storage.issueFullGarbageCollection();
        Thread.sleep(3000);

        System.out.println("GC cycle 1 done. Zombies so far: " + zombieHandler.count());

        // ---------------------------------------------------------------
        // Phase 5 – Re-attach Holder to root, store root (not Holder)
        // ---------------------------------------------------------------
        System.out.println("\n=== Phase 5: Re-attach Holder to root, storeRoot ===");
        System.out.println("  Lazy storer sees Holder is already registered → skips re-storing it.");
        System.out.println("  Root's new binary references Holder's OID.");
        System.out.println("  Holder's old binary still references swept Payload's OID.");

        root.holder = holderRef;
        storage.storeRoot();

        // ---------------------------------------------------------------
        // Phase 6 – Storage GC cycle 2: should produce ZOMBIE
        // ---------------------------------------------------------------
        System.out.println("\n=== Phase 6: Storage GC cycle 2 (should produce ZOMBIE) ===");
        System.out.println("  Mark root → enqueue Holder → mark Holder → iterate Holder's refs");
        System.out.println("  → enqueue Payload's OID → getEntry() == null → ZOMBIE!");

        storage.issueFullGarbageCollection();
        Thread.sleep(3000);

        // ---------------------------------------------------------------
        // Results
        // ---------------------------------------------------------------
        System.out.println("\n=== RESULTS ===");
        System.out.println("Total zombie OIDs detected: " + zombieHandler.count());
        if(!zombieHandler.oids().isEmpty())
        {
            System.out.println("Zombie OID values: " + zombieHandler.oids());
            System.out.println("\nSUCCESS: Zombie OIDs produced without LazyHashMap or custom ObjectIdsSelector!");
            System.out.println("The entity survived sweep via the registry safety net but its stale binary");
            System.out.println("still referenced an OID whose entity was swept in a prior cycle.");
        }
        else
        {
            System.out.println("No zombies detected.");
        }

        // ---------------------------------------------------------------
        // Phase 7 – Shutdown and verify persisted data
        // ---------------------------------------------------------------
        System.out.println("\n=== Phase 7: Shutdown and reload to verify persisted data ===");

        StorageEntityCache.Default.setGarbageCollectionEnabled(false);
        storage.shutdown();
        System.out.println("Storage shut down.");

        // Reload to inspect raw persisted state
        System.out.println("Reloading storage...");
        final CountingZombieOidHandler reloadZombieHandler = new CountingZombieOidHandler();

        EmbeddedStorageManager reloaded = null;
        boolean reloadFailed = false;
        String  reloadError  = null;

        try
        {
            reloaded = EmbeddedStorage.Foundation(
                            Storage.ConfigurationBuilder()
                                    .setStorageFileProvider(Storage.FileProvider(WORK_DIR))
                                    .createConfiguration()
                    )
                    .setGCZombieOidHandler(reloadZombieHandler)
                    .start();

            System.out.println("Storage reloaded successfully.");

            final DataRoot reloadedRoot = (DataRoot) reloaded.root();
            System.out.println("Root: " + reloadedRoot);
            System.out.println("Root.holder: " + reloadedRoot.holder);

            if(reloadedRoot.holder != null)
            {
                System.out.println("Holder.payload: " + reloadedRoot.holder.payload);

                if(reloadedRoot.holder.payload != null)
                {
                    System.out.println("Payload.data: " + reloadedRoot.holder.payload.data);
                    System.out.println("\nWARNING: Payload was loaded despite being swept from the entity cache!");
                }
                else
                {
                    System.out.println("\nPayload is NULL after reload → data loss confirmed.");
                }
            }
            else
            {
                System.out.println("\nHolder is NULL after reload.");
            }

            // Run one GC cycle on the reloaded storage to check for zombies there too
            System.out.println("\n--- Running GC on reloaded storage ---");

            reloaded.issueFullGarbageCollection();
            Thread.sleep(3000);


            System.out.println("Zombie OIDs in reloaded storage: " + reloadZombieHandler.count());
            if(!reloadZombieHandler.oids().isEmpty())
            {
                System.out.println("Zombie OID values: " + reloadZombieHandler.oids());
            }
        }
        catch(final Exception e)
        {
            reloadFailed = true;
            reloadError  = e.getMessage();
            System.out.println("\nRELOAD FAILED with exception:");
            System.out.println("  " + e.getClass().getSimpleName() + ": " + reloadError);

            // Walk the cause chain for the root cause
            Throwable cause = e;
            while(cause.getCause() != null)
            {
                cause = cause.getCause();
            }
            if(cause != e)
            {
                System.out.println("  Root cause: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            }

            System.out.println("\nThis confirms PERSISTENT DATA CORRUPTION caused by zombie OIDs:");
            System.out.println("  Holder's binary record references Payload OID " + zombieHandler.oids());
            System.out.println("  but the Payload entity was swept from storage.");
            System.out.println("  On reload, the storage cannot find the entity for the referenced OID");
            System.out.println("  and throws StorageExceptionConsistency.");
        }
        finally
        {
            if(reloaded != null)
            {
                try { reloaded.shutdown(); } catch(final Exception ignored) { /* already broken */ }
            }
        }

        // ---------------------------------------------------------------
        // Summary
        // ---------------------------------------------------------------
        System.out.println("\n=== SUMMARY ===");
        System.out.println("Zombies during initial run:  " + zombieHandler.count() + " " + zombieHandler.oids());
        System.out.println("Zombies during reload:       " + reloadZombieHandler.count() + " " + reloadZombieHandler.oids());
        System.out.println("Reload failed (corruption):  " + reloadFailed);
        if(reloadFailed)
        {
            System.out.println("Reload error:                " + reloadError);
            System.out.println("\nCONCLUSION: The zombie OID caused PERSISTENT DATA CORRUPTION.");
            System.out.println("The storage is no longer loadable because the swept entity's OID is");
            System.out.println("still referenced by Holder's stale binary record.");
        }
        System.out.println("\nDone.");
    }

    private static void deleteDirectory(final Path dir) throws IOException
    {
        if(!Files.exists(dir))
        {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<>()
        {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException
            {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path d, final IOException exc) throws IOException
            {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
