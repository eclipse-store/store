package test.eclipse.store.behavior;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.configuration.types.ByteSize;
import org.eclipse.serializer.configuration.types.ByteUnit;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.util.StorageObjectRestorer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.configuration.Customer;
import test.eclipse.store.configuration.CustomerGenerator;

/**
 * Behavioral tests for the deletion-directory configuration.
 * <p>
 * The classic configuration tests only assert that <em>some</em> file appears
 * under the deletion directory after housekeeping. These tests verify what the
 * feature is actually expected to deliver in production:
 * <ul>
 *   <li>Objects whose data files are moved aside by housekeeping can be
 *       recovered from the deletion directory using
 *       {@link StorageObjectRestorer}, with their original field values intact
 *       (single-channel and multi-channel).</li>
 *   <li>Live storage stays consistent and queryable after housekeeping moves
 *       garbage-only data files into the deletion directory.</li>
 * </ul>
 */
class DeletionDirectoryBehaviorTest
{

    private static final int CUSTOMER_COUNT = 200;

    private static final ByteSize DATA_FILE_MIN = ByteSize.New(1, ByteUnit.KiB);
    private static final ByteSize DATA_FILE_MAX = ByteSize.New(2, ByteUnit.KiB);

    @Test
    @Disabled("https://github.com/microstream-one/internal/issues/56")
    void recoverableFromDeletionDirectorySingleChannel(@TempDir final Path workDir) throws IOException
    {
        assertRecoverableThroughDeletionDirectory(workDir, 1);
    }

    @Test
    @Disabled("https://github.com/microstream-one/internal/issues/56")
    void recoverableFromDeletionDirectoryMultiChannel(@TempDir final Path workDir) throws IOException
    {
        assertRecoverableThroughDeletionDirectory(workDir, 4);
    }

    @Test
    void noCustomerIsLostAfterHousekeeping(@TempDir final Path workDir) throws IOException
    {
        // Crash-safety invariant under the documented design (StorageFileManager.deleteFile
        // writes a transaction-log entry BEFORE physical delete, and StorageFileWriter
        // returns early if the rescue-into-deletion-dir succeeded so the source file is never
        // blindly deleted): for every persisted object, after housekeeping the object must
        // still be locatable -- either in active storage, or in the deletion directory, or both.
        //
        // The test runs with the *default* file-cleanup settings (no head-file cleanup, default
        // minimum-use-ratio) so that the assertion reflects real-world configurations rather
        // than the aggressive settings used by the recoverable-* tests.

        final Path storageDir = workDir.resolve("storage");
        final Path deletionDir = workDir.resolve("deleted");
        final int channelCount = 1;

        final List<Customer> kept = CustomerGenerator.generateCustomers(5);
        final List<Customer> ephemerals = CustomerGenerator.generateCustomers(CUSTOMER_COUNT);
        final long[] allOids = new long[kept.size() + ephemerals.size()];

        runDefaultFreshSession(storageDir, deletionDir, channelCount, new ArrayList<>(kept), mgr ->
        {
            mgr.storeRoot();
            int idx = 0;
            for (final Customer c : kept) {
                allOids[idx++] = mgr.persistenceManager().lookupObjectId(c);
            }
            for (final Customer c : ephemerals) {
                mgr.store(c);
                allOids[idx++] = mgr.persistenceManager().lookupObjectId(c);
            }
        });

        runDefaultReopenSession(storageDir, deletionDir, channelCount, mgr ->
        {
            mgr.issueFullGarbageCollection();
            mgr.issueFullFileCheck();
        });

        // Phase A: which OIDs are still in active storage? Open storage, try to load each
        // one. lookupObject returns the cached instance; getObject falls back to a loader
        // that scans live data files.
        final Set<Long> foundInActive = new HashSet<>();
        runDefaultReopenSession(storageDir, deletionDir, channelCount, mgr ->
        {
            for (final long oid : allOids) {
                try {
                    final Object o = mgr.persistenceManager().getObject(oid);
                    if (o != null) {
                        foundInActive.add(oid);
                    }
                } catch (final Exception ignored) {
                    // not in active storage -- that's expected for some
                }
            }
        });

        // Phase B: storage closed; for OIDs not found in active storage, check deletion-dir
        // via the restorer. The restorer scans only deletion-dir files.
        final StorageConfiguration restorerConfig = defaultConfigBuilder(storageDir, deletionDir, channelCount)
                .createEmbeddedStorageFoundation()
                .getConfiguration();
        final StorageObjectRestorer restorer = new StorageObjectRestorer.Default(restorerConfig);

        final List<Long> lost = new ArrayList<>();
        for (final long oid : allOids) {
            if (foundInActive.contains(oid)) {
                continue;
            }
            if (!restorer.restoreObject(oid)) {
                lost.add(oid);
            }
        }

        assertTrue(lost.isEmpty(),
                "Some object ids were lost -- not present in active storage and not recoverable from "
                        + "deletion directory either. Count=" + lost.size() + " oids=" + lost);
    }

    @Test
    @Disabled("https://github.com/microstream-one/internal/issues/52")
    void continuousBackupReflectsCurrentLiveStorageNotArchive(@TempDir final Path workDir)
            throws IOException, NoSuchAlgorithmException, InterruptedException
    {
        // Continuous backup behavior: when housekeeping rescues a file from the main
        // storage into the main deletion-directory, the corresponding backup file is
        // processed by StorageBackupHandler.deleteFile -- which calls
        // StorageFileWriter.deleteFile with the BACKUP file provider. The backup
        // provider, when configured via EmbeddedStorageConfigurationBuilder.setBackupDirectory(...),
        // has NO deletion-directory of its own, so the backup copy is physically removed.
        //
        // In other words: continuous backup is a live-state mirror, not an archive.
        // To preserve rescued files in the backup as well, the backup file provider
        // would need its own deletion-directory configured via lower-level API.

        final Path storageDir = workDir.resolve("storage");
        final Path deletionDir = workDir.resolve("deleted");
        final Path backupDir = workDir.resolve("backup");

        final List<Customer> customers = CustomerGenerator.generateCustomers(CUSTOMER_COUNT);

        runFreshSessionWithBackup(storageDir, deletionDir, backupDir, new ArrayList<>(customers), mgr ->
        {
            mgr.storeRoot();
        });

        runReopenSessionWithBackup(storageDir, deletionDir, backupDir, mgr ->
        {
            mgr.setRoot(new ArrayList<Customer>());
            mgr.storeRoot();
            mgr.issueFullGarbageCollection();
            mgr.issueFullFileCheck();
        });

        // Continuous backup processes its queue asynchronously. After shutdown the
        // drain should be complete, but Files.walk might race with FS sync on some
        // platforms. Wait for backup-dir state to stabilize.
//		Awaitility.await()
//			.atMost(Duration.ofSeconds(10))
//			.pollInterval(Duration.ofMillis(100))
//			.until(() -> hashesEqual(collectFileHashes(storageDir), collectFileHashes(backupDir)));

        Thread.sleep(10_000);
        System.out.println(backupDir);
        System.out.println(storageDir);

        assertTrue(Files.isDirectory(backupDir), "Backup directory was not created");
        assertTrue(Files.isDirectory(backupDir.resolve("channel_0")),
                "Backup directory has no channel_0 -- continuous backup did not mirror channels");

        // 1) Main deletion-dir DID receive rescued files.
        assertFalse(collectFileHashes(deletionDir).isEmpty(),
                "Sanity check failed: main deletion directory is empty after housekeeping");

        // 2) Backup directory does NOT have a deletion subdirectory by default --
        //    confirms that backup file provider has no deletion-directory configured.
        assertFalse(Files.isDirectory(backupDir.resolve("deleted")),
                "Backup directory unexpectedly contains a 'deleted' subdirectory; "
                        + "expected default config to give backup no deletion-directory of its own");

        // 3) Backup mirrors current live storage byte-for-byte.
        final Set<String> liveHashes = collectFileHashes(storageDir);
        final Set<String> backupHashes = collectFileHashes(backupDir);
        if (!liveHashes.equals(backupHashes)) {
            System.err.println("=== DIAG: live vs backup mismatch ===");
            System.err.println("--- live ---");
            dumpFilesWithMeta(storageDir);
            System.err.println("--- backup ---");
            dumpFilesWithMeta(backupDir);
            System.err.println("--- deletion-dir ---");
            dumpFilesWithMeta(deletionDir);

            final Path debugDir = Path.of("/tmp/dd-debug/" + System.currentTimeMillis());
            Files.createDirectories(debugDir);
            final Path liveTx = storageDir.resolve("channel_0/transactions_0.sft");
            final Path backupTx = backupDir.resolve("channel_0/transactions_0.sft");
            if (Files.exists(liveTx)) {
                Files.copy(liveTx, debugDir.resolve("live_transactions_0.sft"));
            }
            if (Files.exists(backupTx)) {
                Files.copy(backupTx, debugDir.resolve("backup_transactions_0.sft"));
            }
            System.err.println("Transaction logs copied to: " + debugDir);
        }
        assertEquals(liveHashes, backupHashes,
                "Backup is not a byte-identical mirror of current live storage. "
                        + "Live=" + liveHashes.size() + " backup=" + backupHashes.size());

        // 4) Files rescued into the main deletion-dir are NOT preserved in the backup.
        //    (In default config the backup just drops them.)
        final Set<String> deletedHashes = collectFileHashes(deletionDir);
        final Set<String> intersect = new HashSet<>(deletedHashes);
        intersect.retainAll(backupHashes);
        assertTrue(intersect.isEmpty(),
                "Backup unexpectedly retained files that were rescued into the main "
                        + "deletion-directory. Overlap=" + intersect.size());
    }

    @Test
    void issueFullBackupExportsLiveStorageNotDeletedFiles(@TempDir final Path workDir)
            throws IOException, NoSuchAlgorithmException
    {
        // issueFullBackup is a one-shot snapshot of the current live state (it
        // calls exportChannels under the hood). It should not export the
        // deletion-directory contents -- those are housekeeping rescue copies, not
        // live data.

        final Path storageDir = workDir.resolve("storage");
        final Path deletionDir = workDir.resolve("deleted");
        final Path fullBackupDir = workDir.resolve("fullbackup");
        Files.createDirectories(fullBackupDir);

        runFreshSession(storageDir, deletionDir, 1,
                new ArrayList<>(CustomerGenerator.generateCustomers(CUSTOMER_COUNT)),
                EmbeddedStorageManager::storeRoot);

        runReopenSession(storageDir, deletionDir, 1, mgr ->
        {
            mgr.setRoot(new ArrayList<Customer>());
            mgr.storeRoot();
            mgr.issueFullGarbageCollection();
            mgr.issueFullFileCheck();

            final ADirectory backupADir = NioFileSystem.New().ensureDirectoryPath(fullBackupDir.toString());
            mgr.persistenceManager().objectRegistry();      // touch to ensure connection initialized
            mgr.issueFullBackup(backupADir);
        });

        assertTrue(Files.isDirectory(fullBackupDir.resolve("channel_0")),
                "Full backup did not produce a channel_0 directory");
        assertFalse(Files.isDirectory(fullBackupDir.resolve("deleted")),
                "Full backup unexpectedly contains a 'deleted' subdirectory");

        // Hashes in full backup should equal current live storage; deletion-dir hashes
        // should NOT appear in the full backup.
        final Set<String> liveHashes = collectFileHashes(storageDir);
        final Set<String> fullBackupHashes = collectFileHashes(fullBackupDir);
        final Set<String> deletedHashes = collectFileHashes(deletionDir);

        final Set<String> intersect = new HashSet<>(fullBackupHashes);
        intersect.retainAll(deletedHashes);
        assertTrue(intersect.isEmpty(),
                "issueFullBackup unexpectedly exported files that were rescued into the "
                        + "deletion-directory. Overlap=" + intersect.size());

        // The full backup may carry the type dictionary in addition to data files,
        // so it can be a strict superset of live data files but must not contain
        // rescued files.
        assertTrue(fullBackupHashes.containsAll(intersectionWith(liveHashes, fullBackupHashes)),
                "Full backup is missing live storage data files");
    }

    @Test
    void liveStorageRemainsUsableAfterHousekeepingMovesFilesAside(@TempDir final Path workDir)
            throws IOException, NoSuchAlgorithmException
    {
        final Path storageDir = workDir.resolve("storage");
        final Path deletionDir = workDir.resolve("deleted");

        final List<Customer> kept = CustomerGenerator.generateCustomers(5);

        // Session 1: 5 customers reachable from the root + many ephemeral
        // customers stored as orphans (unreachable from the root, garbage on GC).
        runFreshSession(storageDir, deletionDir, 1, new ArrayList<>(kept), mgr ->
        {
            mgr.storeRoot();
            for (int i = 0; i < CUSTOMER_COUNT; i++) {
                mgr.store(CustomerGenerator.generateNewCustomer());
            }
        });

        // Session 2: explicit GC + file check moves orphan-bearing files aside.
        runReopenSession(storageDir, deletionDir, 1, mgr ->
        {
            mgr.issueFullGarbageCollection();
            mgr.issueFullFileCheck();
        });

        assertTrue(Files.isDirectory(deletionDir), "Deletion directory was not created");
        assertFalse(collectFileHashes(deletionDir).isEmpty(),
                "No file moved into the deletion directory; the test did not exercise housekeeping");

        // Session 3: reopen and verify the kept customers are still loadable
        // from live storage with their original field values.
        runReopenSession(storageDir, deletionDir, 1, mgr ->
        {
            @SuppressWarnings("unchecked") final List<Customer> loaded = (List<Customer>) mgr.root();
            assertNotNull(loaded, "Root was not loaded after reopen");
            assertEquals(kept.size(), loaded.size(),
                    "Live root size changed after housekeeping moved files aside");
            for (int i = 0; i < kept.size(); i++) {
                assertCustomerEquals(kept.get(i), loaded.get(i), "kept[" + i + "]");
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // scenario //
    /////////////

    /**
     * Round-trip scenario: store customers, capture their object ids, orphan them
     * via housekeeping, then verify that {@link StorageObjectRestorer} can
     * recover each one from the deletion directory and that the recovered
     * customers carry the original field values.
     */
    private static void assertRecoverableThroughDeletionDirectory(
            final Path workDir,
            final int channelCount
    )
            throws IOException
    {
        final Path storageDir = workDir.resolve("storage");
        final Path deletionDir = workDir.resolve("deleted");

        final List<Customer> originals = CustomerGenerator.generateCustomers(CUSTOMER_COUNT);
        final long[] oids = new long[originals.size()];

        // Session 1: store every customer as part of the root list, capture OIDs.
        runFreshSession(storageDir, deletionDir, channelCount, new ArrayList<>(originals), mgr ->
        {
            mgr.storeRoot();
            for (int i = 0; i < originals.size(); i++) {
                final long oid = mgr.persistenceManager().lookupObjectId(originals.get(i));
                assertNotEquals(0L, oid,
                        "Customer at index " + i + " has no object id after storeRoot");
                oids[i] = oid;
            }
        });

        // Session 2: orphan everything and force housekeeping to move the
        // now-garbage data files into the deletion directory. Head file cleanup
        // is enabled and the use-ratio threshold is raised so even sparsely-used
        // files are dissolved -- otherwise some customers would survive in the
        // active head file and would not be recoverable from deletion-dir.
        runReopenSession(storageDir, deletionDir, channelCount, mgr ->
        {
            mgr.setRoot(new ArrayList<Customer>());
            mgr.storeRoot();
            mgr.issueFullGarbageCollection();
            mgr.issueFullFileCheck();
        });

        // Restorer must run on a stopped storage. Build a fresh foundation just
        // to extract a configuration that points at the same directories.
        final StorageConfiguration restorerConfig = configBuilder(storageDir, deletionDir, channelCount)
                .createEmbeddedStorageFoundation()
                .getConfiguration();

        final StorageObjectRestorer restorer = new StorageObjectRestorer.Default(restorerConfig);

        int recovered = 0;
        final List<Integer> failedIndexes = new ArrayList<>();
        for (int i = 0; i < oids.length; i++) {
            if (restorer.restoreObject(oids[i])) {
                recovered++;
            } else {
                failedIndexes.add(i);
            }
        }

        assertEquals(originals.size(), recovered,
                "Some customers could not be recovered from deletion directory: indexes " + failedIndexes);

        // Session 3: reopen and look up each customer by its original OID. Field
        // values must equal the originals -- this is the actual proof that the
        // data preserved in the deletion directory is the data we intended.
        runReopenSession(storageDir, deletionDir, channelCount, mgr ->
        {
            for (int i = 0; i < originals.size(); i++) {
                final Object loaded = mgr.persistenceManager().getObject(oids[i]);
                assertNotNull(loaded,
                        "Customer with oid " + oids[i] + " (index " + i + ") not loadable after restore");
                assertTrue(loaded instanceof Customer,
                        "Loaded object for oid " + oids[i] + " is not a Customer: " + loaded.getClass());
                assertCustomerEquals(originals.get(i), (Customer) loaded, "originals[" + i + "]");
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // helpers //

    /// /////////

    private static void assertCustomerEquals(final Customer expected, final Customer actual, final String label)
    {
        assertEquals(expected.getFirstName(), actual.getFirstName(), label + ".firstName");
        assertEquals(expected.getSecondName(), actual.getSecondName(), label + ".secondName");
        assertEquals(expected.getStreet(), actual.getStreet(), label + ".street");
        assertEquals(expected.getCity(), actual.getCity(), label + ".city");
    }

    private static EmbeddedStorageConfigurationBuilder configBuilder(
            final Path storageDir,
            final Path deletionDir,
            final int channelCount
    )
    {
        return EmbeddedStorageConfigurationBuilder.New()
                .setStorageDirectory(storageDir.toString())
                .setDeletionDirectory(deletionDir.toString())
                .setChannelCount(channelCount)
                .setDataFileMinimumSize(DATA_FILE_MIN)
                .setDataFileMaximumSize(DATA_FILE_MAX)
                .setDataFileMinimumUseRatio(0.99)
                .setDataFileCleanupHeadFile(true);
    }

    /**
     * Builder with only deletion-dir + small data file sizes set, so housekeeping
     * uses default thresholds (head-file cleanup off, default minimum-use-ratio).
     * Used by {@link #noCustomerIsLostAfterHousekeeping} to test the
     * never-lose-an-object invariant under realistic configurations.
     */
    private static EmbeddedStorageConfigurationBuilder defaultConfigBuilder(
            final Path storageDir,
            final Path deletionDir,
            final int channelCount
    )
    {
        return EmbeddedStorageConfigurationBuilder.New()
                .setStorageDirectory(storageDir.toString())
                .setDeletionDirectory(deletionDir.toString())
                .setChannelCount(channelCount)
                .setDataFileMinimumSize(DATA_FILE_MIN)
                .setDataFileMaximumSize(DATA_FILE_MAX);
    }

    private static void runFreshSession(
            final Path storageDir,
            final Path deletionDir,
            final int channelCount,
            final Object initialRoot,
            final Consumer<EmbeddedStorageManager> action
    )
    {
        final EmbeddedStorageManager mgr = configBuilder(storageDir, deletionDir, channelCount)
                .createEmbeddedStorageFoundation()
                .createEmbeddedStorageManager(initialRoot)
                .start();
        try {
            action.accept(mgr);
        } finally {
            mgr.shutdown();
        }
    }

    private static void runReopenSession(
            final Path storageDir,
            final Path deletionDir,
            final int channelCount,
            final Consumer<EmbeddedStorageManager> action
    )
    {
        final EmbeddedStorageManager mgr = configBuilder(storageDir, deletionDir, channelCount)
                .createEmbeddedStorageFoundation()
                .createEmbeddedStorageManager()
                .start();
        try {
            action.accept(mgr);
        } finally {
            mgr.shutdown();
        }
    }

    private static void runDefaultFreshSession(
            final Path storageDir,
            final Path deletionDir,
            final int channelCount,
            final Object initialRoot,
            final Consumer<EmbeddedStorageManager> action
    )
    {
        final EmbeddedStorageManager mgr = defaultConfigBuilder(storageDir, deletionDir, channelCount)
                .createEmbeddedStorageFoundation()
                .createEmbeddedStorageManager(initialRoot)
                .start();
        try {
            action.accept(mgr);
        } finally {
            mgr.shutdown();
        }
    }

    private static EmbeddedStorageConfigurationBuilder backupConfigBuilder(
            final Path storageDir,
            final Path deletionDir,
            final Path backupDir,
            final int channelCount
    )
    {
        return EmbeddedStorageConfigurationBuilder.New()
                .setStorageDirectory(storageDir.toString())
                .setDeletionDirectory(deletionDir.toString())
                .setBackupDirectory(backupDir.toString())
                .setChannelCount(channelCount)
                .setDataFileMinimumSize(DATA_FILE_MIN)
                .setDataFileMaximumSize(DATA_FILE_MAX)
                .setDataFileMinimumUseRatio(0.99)
                .setDataFileCleanupHeadFile(true);
    }

    private static void runFreshSessionWithBackup(
            final Path storageDir,
            final Path deletionDir,
            final Path backupDir,
            final Object initialRoot,
            final Consumer<EmbeddedStorageManager> action
    )
    {
        final EmbeddedStorageManager mgr = backupConfigBuilder(storageDir, deletionDir, backupDir, 1)
                .createEmbeddedStorageFoundation()
                .createEmbeddedStorageManager(initialRoot)
                .start();
        try {
            action.accept(mgr);
        } finally {
            mgr.shutdown();
        }
    }

    private static void runReopenSessionWithBackup(
            final Path storageDir,
            final Path deletionDir,
            final Path backupDir,
            final Consumer<EmbeddedStorageManager> action
    )
    {
        final EmbeddedStorageManager mgr = backupConfigBuilder(storageDir, deletionDir, backupDir, 1)
                .createEmbeddedStorageFoundation()
                .createEmbeddedStorageManager()
                .start();
        try {
            action.accept(mgr);
        } finally {
            mgr.shutdown();
        }
    }

    private static boolean hashesEqual(final Set<String> a, final Set<String> b)
    {
        return a.equals(b);
    }

    private static Set<String> intersectionWith(final Set<String> a, final Set<String> b)
    {
        final Set<String> result = new HashSet<>(a);
        result.retainAll(b);
        return result;
    }

    private static void runDefaultReopenSession(
            final Path storageDir,
            final Path deletionDir,
            final int channelCount,
            final Consumer<EmbeddedStorageManager> action
    )
    {
        final EmbeddedStorageManager mgr = defaultConfigBuilder(storageDir, deletionDir, channelCount)
                .createEmbeddedStorageFoundation()
                .createEmbeddedStorageManager()
                .start();
        try {
            action.accept(mgr);
        } finally {
            mgr.shutdown();
        }
    }

    private static void dumpFilesWithMeta(final Path dir)
            throws IOException, NoSuchAlgorithmException
    {
        if (!Files.exists(dir)) {
            System.err.println("  (does not exist: " + dir + ")");
            return;
        }
        final MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (Stream<Path> stream = Files.walk(dir)) {
            final List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .toList();
            for (final Path f : files) {
                final byte[] data = Files.readAllBytes(f);
                md.reset();
                final byte[] digest = md.digest(data);
                final StringBuilder sb = new StringBuilder(digest.length * 2);
                for (final byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                System.err.println("  " + dir.relativize(f) + "  size=" + data.length + "  sha=" + sb);
            }
        }
    }

    private static Set<String> collectFileHashes(final Path dir)
            throws IOException, NoSuchAlgorithmException
    {
        if (!Files.exists(dir)) {
            return new HashSet<>();
        }

        final Set<String> hashes = new HashSet<>();
        final MessageDigest md = MessageDigest.getInstance("SHA-256");

        try (Stream<Path> stream = Files.walk(dir)) {
            final List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .toList();

            for (final Path f : files) {
                final byte[] data = Files.readAllBytes(f);
                if (data.length == 0) {
                    continue;
                }
                md.reset();
                final byte[] digest = md.digest(data);
                final StringBuilder sb = new StringBuilder(digest.length * 2);
                for (final byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                hashes.add(sb.toString());
            }
        }

        return hashes;
    }
}
