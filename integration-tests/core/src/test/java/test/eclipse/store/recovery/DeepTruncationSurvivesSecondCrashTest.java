package test.eclipse.store.recovery;

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

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * A deep (more than one store) head rollback writes a truncation entry and leaves the rolled-back
 * store entries in the log. The replay must step the latest-store timestamp back to the surviving
 * store, or that timestamp names a store that was rolled away - a phantom the next reconciliation
 * would treat as the consensus and brick on. This drives two consecutive power losses: the first
 * causes the deep rollback, the second must still reconcile to the true surviving store.
 * <p>
 * Fix-guard: before this change the phantom timestamp bricks the second recovery.
 */
@Timeout(120)
class DeepTruncationSurvivesSecondCrashTest
{
	private static final int PAYLOAD_SIZE  = 512      ;
	private static final int MAX_FILE_SIZE = 8_388_608; // 8 MiB: every store stays in one head file

	@TempDir
	Path directory;

	private EmbeddedStorageManager storage  ;
	private EmbeddedStorageManager reloaded1;
	private EmbeddedStorageManager reloaded2;

	@AfterEach
	void shutdown()
	{
		for(final EmbeddedStorageManager manager :
			new EmbeddedStorageManager[]{this.reloaded2, this.reloaded1, this.storage})
		{
			if(manager != null && manager.isRunning())
			{
				try
				{
					manager.shutdown();
				}
				catch(final RuntimeException ignored)
				{
					// best effort
				}
			}
		}
	}

	@Test
	void deepRollbackLeavesNoPhantomLatestForTheNextRecovery()
	{
		// three stores, clean shutdown
		this.storage = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 2, MAX_FILE_SIZE));
		final Root root = new Root();
		this.storage.setRoot(root);
		this.storeVersions(this.storage, root, 1, 3);
		this.storage.shutdown();
		this.storage = null;

		// first power loss: channel 1 loses v2 and v3, so channel 0 is two stores ahead
		RecoverySimulation.cutTrailingStores(this.directory, 1, 2);

		// recovery rolls channel 0 back to v1 with a deep truncation, then we store v4 and shut down
		this.reloaded1 = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 2, MAX_FILE_SIZE));
		final Root root1 = (Root)this.reloaded1.root();
		assertEquals(1, root1.version, "the first recovery must roll back to the last common store");
		this.storeVersions(this.reloaded1, root1, 4, 4);
		this.reloaded1.shutdown();
		this.reloaded1 = null;

		// second power loss: channel 0 loses only its trailing v4 store (its truncation entry stays)
		RecoverySimulation.cutLastStore(this.directory, 0);

		// the surviving-store timestamp from the deep truncation must keep the consensus recoverable
		this.reloaded2 = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 2, MAX_FILE_SIZE));
		final Root root2 = (Root)this.reloaded2.root();
		assertNotNull(root2, "root gone after the second recovery");
		assertEquals(1, root2.version, "the second recovery must reconcile to the true surviving store");
		assertNotNull(root2.payload, "the surviving store's payload was lost");
		assertEquals((byte)1, root2.payload[0], "wrong surviving payload version");
	}

	private void storeVersions(final EmbeddedStorageManager manager, final Root root, final int from, final int to)
	{
		for(int version = from; version <= to; version++)
		{
			final byte[] payload = new byte[PAYLOAD_SIZE];
			payload[0]   = (byte)version;
			root.version = version;
			root.payload = payload;
			manager.storeRoot();
		}
	}


	public static class Root
	{
		int    version;
		byte[] payload;
	}
}
