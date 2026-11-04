package test.eclipse.store.entitycache;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.persistence.types.PersistenceRefactoringMappingProvider;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.Swizzling;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageConnection;
import org.eclipse.store.storage.types.StorageEntityCache;
import org.eclipse.store.storage.types.StorageEntityTypeExportFileProvider;
import org.eclipse.store.storage.types.StorageEntityTypeExportStatistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reproducer for the {@code StorageEntityCache.putEntity(long)} typeId-change
 * defect: when a store updates an entity whose registered cache entry has a
 * different typeId (the routine result of storing a legacy-mapped instance
 * after type evolution), the branch at {@code StorageEntityCache.java:613-625}
 * creates a <b>second</b> entry for the same OID and leaves the old entry
 * registered — still in the OID hash chain, still in its type's entity list,
 * still bound to its (stale) file record ({@code resetExistingEntityForUpdate}
 * is never applied to it).
 *
 * <p>Two user-visible consequences are asserted here (both tests assert
 * correct behavior and therefore FAIL while the defect exists):
 *
 * <ol>
 * <li>{@link #reStoreAfterTypeChangeMustReplaceNotDuplicateEntity()} — the
 *     stale entry survives a full GC because the sweep's application safety
 *     net is id-only ({@code sweep: isGcMarked() ||
 *     isReachableInApplication.test(item.objectId)}, StorageEntityCache.java:950)
 *     and both entries carry the same OID: as long as the application holds
 *     the instance, the old-type duplicate is rescued every cycle. A type
 *     export then emits the same entity twice — once per type — which also
 *     breaks re-import ({@code validateEntity} throws "Object Id already
 *     assigned to an entity of another type").</li>
 * <li>{@link #reloadMustNotServeStaleDataAfterHashTableRebuild()} — every OID
 *     hash table rebuild reverses bucket chain order
 *     ({@code rebuildOidHashSlots} prepends, StorageEntityCache.java:269-285),
 *     so after a rebuild the STALE entry becomes the chain head and
 *     {@code getEntry(oid)} — used by loads and by GC marking — resolves to
 *     the old record: a reload returns pre-update data. (With JVM+storage GC
 *     timing instead of the explicit clear/reload used here, the same flip
 *     makes the mark phase mark only the stale entry, the sweep then deletes
 *     the CURRENT record — permanent silent data regression.)</li>
 * </ol>
 *
 * <p>Trigger is ordinary type evolution: class {@link EntityV1} is stored,
 * the second session maps it to {@link EntityV2} (explicit refactoring
 * mapping stands in for any legacy type mapping), the loaded instance is
 * re-stored. The second test disables the storage GC via the
 * {@code StorageEntityCache.Default.setGarbageCollectionEnabled} debug hook to
 * keep the window deterministic; without it the same states are reachable,
 * just timing-dependent.
 */
public class TypeChangeDuplicateEntityReproTest
{
    private static final String V1_VALUE = "v1-original";
    private static final String V2_VALUE = "v2-updated";

    @TempDir
    Path tempDir;

    EmbeddedStorageManager storage;

    @AfterEach
    public void afterTest()
    {
        StorageEntityCache.Default.setGarbageCollectionEnabled(true);
        if (this.storage != null && this.storage.isRunning()) {
            try {
                this.storage.shutdown();
            } catch (final Exception ignored) { /* best effort */ }
        }
    }

    @Test
    @Timeout(120)
    void reStoreAfterTypeChangeMustReplaceNotDuplicateEntity() throws Exception
    {
        this.seedStorageWithV1Entity();
        this.storage = this.startStorageWithV1toV2Mapping();

        final DataRoot root = (DataRoot) this.storage.root();
        final Object x = root.entity.get();
        assertInstanceOf(EntityV2.class, x, "legacy mapping must load the entity as EntityV2");
        assertEquals(V1_VALUE, ((EntityV2) x).value);

        // The duplicating store: same OID, new typeId.
        ((EntityV2) x).value = V2_VALUE;
        this.storage.store(x);

        // Full GC with the instance strongly held: the id-only sweep safety
        // net rescues BOTH same-OID entries, so a correct implementation must
        // have removed the old-type entry during the store itself.
        this.storage.issueFullGarbageCollection();

        // Detector: a type export walks the per-type entity lists. No record
        // of the old type may exist for the re-stored entity.
        final Path exportDir = this.tempDir.resolve("export");
        Files.createDirectories(exportDir);
        final NioFileSystem fs  = NioFileSystem.New();
        final ADirectory    dir = fs.ensureDirectoryPath(exportDir.toAbsolutePath().toString());

        final StorageConnection connection = this.storage.createConnection();
        final StorageEntityTypeExportStatistics stats = connection.exportTypes(
                new StorageEntityTypeExportFileProvider.Default(dir, "bin"),
                typeHandler -> typeHandler.typeName().equals(EntityV1.class.getName())
                        || typeHandler.typeName().equals(EntityV2.class.getName())
        );

        long v1Records = 0;
        long v2Records = 0;
        for (final StorageEntityTypeExportStatistics.TypeStatistic ts : stats.typeStatistics().values()) {
            if (ts.typeName().equals(EntityV1.class.getName())) {
                v1Records = ts.entityCount();
            }
            if (ts.typeName().equals(EntityV2.class.getName())) {
                v2Records = ts.entityCount();
            }
        }

        assertEquals(1, v2Records, "exactly one current-type record expected in the export");
        assertEquals(0, v1Records,
                "DUPLICATE ENTITY: the same OID is still registered under the old type "
                        + EntityV1.class.getName() + " (" + v1Records + " record(s) exported)"
                        + " — the stale entry survived the store and a full GC");
    }

    @Test
    @Timeout(300)
    void reloadMustNotServeStaleDataAfterHashTableRebuild() throws Exception
    {
        this.seedStorageWithV1Entity();

        // Freeze the storage GC so the duplicate cannot be swept away between
        // the checks below (with GC running, the same flip window exists but
        // depends on housekeeping timing; see class javadoc).
        StorageEntityCache.Default.setGarbageCollectionEnabled(false);
        this.storage = this.startStorageWithV1toV2Mapping();

        final DataRoot root = (DataRoot) this.storage.root();
        Object x = root.entity.get();
        assertInstanceOf(EntityV2.class, x);
        assertEquals(V1_VALUE, ((EntityV2) x).value);

        // The duplicating store: after it, the CURRENT entry is the bucket
        // chain head, the STALE one sits behind it.
        ((EntityV2) x).value = V2_VALUE;
        this.storage.store(x);

        // Grow the entity cache so the OID hash table rebuilds (each rebuild
        // reverses bucket chain order). After an odd number of rebuilds the
        // stale entry is the head and a fresh load resolves to the OLD record.
        String staleObservation = null;
        batches:
        for (int batch = 1; batch <= 8 && staleObservation == null; batch++) {
            final ArrayList<String> filler = new ArrayList<>(30_000);
            for (int j = 0; j < 30_000; j++) {
                filler.add("filler-" + batch + "-" + j);
            }
            this.storage.store(filler);

            // Drop the held instance so the next Lazy.get() must load from disk.
            final WeakReference<Object> probe = new WeakReference<>(x);
            x = null;
            root.entity.clear();
            for (int i = 0; i < 20 && probe.get() != null; i++) {
                System.gc();
                Thread.sleep(50);
            }
            if (probe.get() != null) {
                // could not force collection this round; re-load and try again
                x = root.entity.get();
                continue batches;
            }
            this.storage.persistenceManager().objectRegistry().cleanUp();

            final Object reloaded = root.entity.get();
            assertInstanceOf(EntityV2.class, reloaded);
            final String value = ((EntityV2) reloaded).value;
            if (!V2_VALUE.equals(value)) {
                staleObservation = value + " (batch " + batch + ")";
            }
            x = reloaded;
        }

        assertNull(staleObservation,
                "STALE DATA SERVED: after an OID hash table rebuild, reloading the entity returned "
                        + "the pre-update record: " + staleObservation + " — getEntry(oid) resolves to the "
                        + "leftover old-type entry; the same resolution is used by GC marking, where it leads "
                        + "to the sweep deleting the current record");
    }

    /**
     * The data-loss end of the chain: with the stale entry at the bucket-chain
     * head (after a rebuild), the GC mark phase resolves the entity's OID to
     * the STALE entry ({@code incrementalMark} → {@code getEntry},
     * StorageEntityCache.java:882) and marks it; the CURRENT record's entry is
     * never found by lookup, stays white and is deleted by the sweep. The file
     * check then physically reclaims the current record (without it the
     * startup scanner would resurrect the bytes), so after a restart the
     * entity has permanently rolled back to its pre-update state.
     */
    @Test
    @Timeout(600)
    void gcMustNotDeleteCurrentRecordAfterHashTableRebuild() throws Exception
    {
        this.seedStorageWithV1Entity();

        // Freeze the storage GC while staging (see class javadoc), but keep
        // file housekeeping aggressive so the final file check can physically
        // reclaim reclaimed regions.
        StorageEntityCache.Default.setGarbageCollectionEnabled(false);
        this.storage = this.startStorageWithV1toV2Mapping(true);

        final DataRoot root = (DataRoot) this.storage.root();
        Object x = root.entity.get();
        assertInstanceOf(EntityV2.class, x);
        ((EntityV2) x).value = V2_VALUE;
        this.storage.store(x);

        // Stage the flip: grow the cache and verify via a from-disk reload
        // that the stale entry has become the chain head.
        Object staleInstance = null;
        for (int batch = 1; batch <= 8 && staleInstance == null; batch++) {
            final ArrayList<String> filler = new ArrayList<>(30_000);
            for (int j = 0; j < 30_000; j++) {
                filler.add("filler-" + batch + "-" + j);
            }
            this.storage.store(filler);

            final WeakReference<Object> probe = new WeakReference<>(x);
            x = null;
            root.entity.clear();
            for (int i = 0; i < 20 && probe.get() != null; i++) {
                System.gc();
                Thread.sleep(50);
            }
            if (probe.get() != null) {
                x = root.entity.get();
                continue;
            }
            this.storage.persistenceManager().objectRegistry().cleanUp();

            final Object reloaded = root.entity.get();
            assertInstanceOf(EntityV2.class, reloaded);
            if (V1_VALUE.equals(((EntityV2) reloaded).value)) {
                staleInstance = reloaded;
            } else {
                x = reloaded;
            }
        }
        assumeTrue(staleInstance != null,
                "hash-rebuild flip not observed — cannot stage the sweep scenario deterministically");

        // Nothing application-side may protect the OID during the GC (the
        // id-only sweep rescue would otherwise keep BOTH entries alive).
        final WeakReference<Object> staleProbe = new WeakReference<>(staleInstance);
        staleInstance = null;
        root.entity.clear();
        for (int i = 0; i < 20 && staleProbe.get() != null; i++) {
            System.gc();
            Thread.sleep(50);
        }
        assumeTrue(staleProbe.get() == null, "JVM did not collect the reloaded instance");
        this.storage.persistenceManager().objectRegistry().cleanUp();

        // Resume the GC. Marking walks root -> Lazy -> entity OID and resolves
        // it through the stale chain head; the current record's entry stays
        // white (its store-time gray mark only survives the first sweep) and
        // the second sweep deletes it. The file check reclaims it physically.
        StorageEntityCache.Default.setGarbageCollectionEnabled(true);
        this.storage.issueFullGarbageCollection();
        this.storage.issueFullGarbageCollection();
        this.storage.issueFullFileCheck();
        this.storage.shutdown();

        // Restart: only the stale record is left on disk.
        this.storage = this.startStorageWithV1toV2Mapping(false);
        final DataRoot reloadedRoot = (DataRoot) this.storage.root();
        final Object survivor = reloadedRoot.entity.get();
        assertInstanceOf(EntityV2.class, survivor);
        assertEquals(V2_VALUE, ((EntityV2) survivor).value,
                "DATA LOSS: the storage GC deleted the current record and kept the stale one — "
                        + "after restart the entity permanently rolled back to its pre-update state");
    }

    ///////////////////////////////////////////////////////////////////////////
    // helpers //
    /////////////

    private void seedStorageWithV1Entity()
    {
        final EmbeddedStorageManager seed = EmbeddedStorage.Foundation(
                        Storage.ConfigurationBuilder()
                                .setChannelCountProvider(Storage.ChannelCountProvider(1))
                                .setStorageFileProvider(Storage.FileProvider(this.tempDir))
                                .createConfiguration()
                )
                .start();

        final DataRoot root = new DataRoot();
        root.entity = Lazy.Reference(new EntityV1(V1_VALUE));
        seed.setRoot(root);
        seed.storeRoot();

        final long oid = seed.persistenceManager().objectRegistry().lookupObjectId(root.entity.peek());
        assertNotEquals(Swizzling.notFoundId(), oid, "entity must be registered after seeding");

        seed.shutdown();
    }

    private EmbeddedStorageManager startStorageWithV1toV2Mapping()
    {
        return this.startStorageWithV1toV2Mapping(false);
    }

    private EmbeddedStorageManager startStorageWithV1toV2Mapping(final boolean aggressiveFileCleanup)
    {
        final var configBuilder = Storage.ConfigurationBuilder()
                .setChannelCountProvider(Storage.ChannelCountProvider(1))
                .setStorageFileProvider(Storage.FileProvider(this.tempDir));
        if (aggressiveFileCleanup) {
            configBuilder
                    .setHousekeepingController(Storage.HousekeepingController(100, 1_000_000_000))
                    .setDataFileEvaluator(Storage.DataFileEvaluator(1024, 1024 * 1024, 1.0));
        }
        return EmbeddedStorage.Foundation(configBuilder.createConfiguration())
                .setRefactoringMappingProvider(PersistenceRefactoringMappingProvider.New(
                        EqHashTable.New(KeyValue.New(EntityV1.class.getName(), EntityV2.class.getName()))
                ))
                .start();
    }

    ///////////////////////////////////////////////////////////////////////////
    // data types //
    ////////////////

    public static class DataRoot
    {
        public Lazy<Object> entity;
    }
}
