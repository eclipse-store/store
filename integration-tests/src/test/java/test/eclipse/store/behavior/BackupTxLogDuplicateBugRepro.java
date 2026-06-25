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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.configuration.types.ByteSize;
import org.eclipse.serializer.configuration.types.ByteUnit;
import org.eclipse.serializer.io.XIO;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageTransactionsAnalysis;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.configuration.Customer;
import test.eclipse.store.configuration.CustomerGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Minimal reproduction of an Eclipse Store backup-handler bug.
 *
 * Symptom: after housekeeping which creates a new head file, the continuous
 * backup transactions log contains exactly one CREATION entry MORE than the
 * live transactions log -- 26 bytes (one {@code LENGTH_COMMON_NUMBERED}
 * entry) over the live size. The duplicate entry is byte-identical to the
 * preceding entry (same timestamp, same file number, fileLength=0). Data
 * files ({@code channel_X_N.dat}) remain byte-identical between live and
 * backup; only the {@code transactions_X.sft} log diverges.
 *
 * Reproduction:
 * <ul>
 *   <li>Default housekeeping settings (no head-file cleanup, default
 *       minimum-use-ratio).</li>
 *   <li>No deletion directory needed.</li>
 *   <li>Two sessions are required: bug does NOT reproduce in a single
 *       session, only after reopen -- pointing at backup synchronization
 *       at session 2 startup.</li>
 *   <li>Customer count needs to be high enough that several non-head data
 *       files exist so housekeeping consolidates them into a new head file.
 *       Around 75 customers at 1-2 KiB data files reproduces ~70 % of the
 *       time on macOS over 10 iterations.</li>
 * </ul>
 *
 * Suspected cause: race between
 *   {@code StorageFileWriterBackupping.writeTransactionEntryCreate} (which
 *   enqueues a {@code CopyItem} into the backup queue) and
 *   {@code StorageBackupHandler.synchronizeTransactionFile} (which deletes
 *   the backup transactions file and copies the whole live transactions
 *   log when sizes mismatch). When synchronize fires before the backup
 *   queue drains the pending {@code CopyItem}, the copy-all path picks up
 *   the new entry, and then the backup queue thread later appends the same
 *   26 bytes again -> duplicate.
 *
 * The original {@code DeletionDirectoryBehaviorTest#continuousBackupReflectsCurrentLiveStorageNotArchive}
 * surfaces the same bug under a larger workload with a deletion directory.
 * This test trims the scenario to the smallest configuration that still
 * triggers it.
 */
@Disabled("https://github.com/microstream-one/internal/issues/52")
class BackupTxLogDuplicateBugRepro {

	private static final int CUSTOMER_COUNT = 75;

	private static final ByteSize DATA_FILE_MIN = ByteSize.New(1, ByteUnit.KiB);
	private static final ByteSize DATA_FILE_MAX = ByteSize.New(2, ByteUnit.KiB);

	//@Test
	@RepeatedTest(10)
	void backupTransactionsLogMatchesLive(@TempDir final Path workDir) throws IOException
	{
		final Path storageDir = workDir.resolve("storage");
		final Path backupDir  = workDir.resolve("backup");

		// Session 1: fresh storage with a continuous backup, write enough data so that
		// multiple data files are produced and at least one becomes a non-head file.
		final EmbeddedStorageConfigurationBuilder builder = EmbeddedStorageConfigurationBuilder.New()
			.setStorageDirectory   (storageDir.toString())
			.setBackupDirectory    (backupDir.toString() )
			.setChannelCount       (1                    )
			.setDataFileMinimumSize(DATA_FILE_MIN        )
			.setDataFileMaximumSize(DATA_FILE_MAX        );

		EmbeddedStorageManager mgr = builder
			.createEmbeddedStorageFoundation()
			.createEmbeddedStorageManager(new ArrayList<>(CustomerGenerator.generateCustomers(CUSTOMER_COUNT)))
			.start();
		mgr.storeRoot();
		mgr.shutdown();

		// Session 2: orphan everything, force GC + file check. Housekeeping
		// consolidation creates a new head file and writes a CREATION entry.
		mgr = builder
			.createEmbeddedStorageFoundation()
			.createEmbeddedStorageManager()
			.start();
		mgr.setRoot(new ArrayList<Customer>());
		mgr.storeRoot();
		mgr.issueFullGarbageCollection();
		mgr.issueFullFileCheck();
		mgr.shutdown();

		final Path liveTx   = storageDir.resolve("channel_0/transactions_0.sft");
		final Path backupTx = backupDir .resolve("channel_0/transactions_0.sft");

		final long liveLen   = Files.size(liveTx);
		final long backupLen = Files.size(backupTx);

		// Uncomment to print both transaction logs in human-readable form to see
		// exactly which entry diverges between live and backup. Same output that
		// org.eclipse.store.storage.util.MainUtilTransactionFileConverter produces.
		 dumpTransactionLog("LIVE",   liveTx);
		 dumpTransactionLog("BACKUP", backupTx);

		assertEquals(liveLen, backupLen,
			"Backup transactions log differs from live: live=" + liveLen
				+ " backup=" + backupLen + " delta=" + (backupLen - liveLen) + " bytes");
	}

	/**
	 * Print a parsed transactions log to {@code System.out}. Mirrors the logic of
	 * {@link org.eclipse.store.storage.util.MainUtilTransactionFileConverter} so that
	 * {@code .sft} files can be inspected without rebuilding the storage tooling.
	 */
	@SuppressWarnings("unused")
	private static void dumpTransactionLog(final String label, final Path path)
	{
		final AFile     file = NioFileSystem.New().ensureFile(XIO.Path(path.toString()));
		final VarString vs   = VarString.New("=== " + label + " " + path + " ===").lf();
		StorageTransactionsAnalysis.EntryAssembler.assembleHeader(vs, "\t").lf();
		StorageTransactionsAnalysis.Logic.parseFile(file, vs).lf();
		System.out.println(vs.toString());
	}
}
