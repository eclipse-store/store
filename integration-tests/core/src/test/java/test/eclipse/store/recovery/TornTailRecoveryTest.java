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
import java.util.Arrays;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * A power loss rarely stops on a clean record boundary: it can leave a half-written record or
 * zero-filled preallocated space at the tail of the head data file or the transactions log. Such an
 * uncommitted tail must be healed (truncated) on the next start and the last committed state must
 * remain intact - never a refusal or silent loss.
 * <p>
 * Regression guard: torn-tail healing works before and after this change; this only locks it against regressions.
 */
@Timeout(120)
class TornTailRecoveryTest
{
	private static final int MAX_FILE_SIZE = 50_000_000;
	private static final int MARKER        = 77       ;

	@TempDir
	Path directory;

	private EmbeddedStorageManager storage ;
	private EmbeddedStorageManager reloaded;

	@AfterEach
	void shutdown()
	{
		for(final EmbeddedStorageManager manager : new EmbeddedStorageManager[]{this.reloaded, this.storage})
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
	void garbageTailOnTheHeadDataFileIsTruncated()
	{
		this.stageMarker();
		final byte[] garbage = new byte[128];
		Arrays.fill(garbage, (byte)0xEE);
		RecoverySimulation.append(this.headDataFile(), garbage);

		this.assertMarkerRecovers();
	}

	@Test
	void zeroFilledTailOnTheHeadDataFileIsTruncated()
	{
		this.stageMarker();
		RecoverySimulation.append(this.headDataFile(), new byte[128]);

		this.assertMarkerRecovers();
	}

	@Test
	void partialTailOnTheTransactionsLogIsHealed()
	{
		this.stageMarker();
		// a few zero bytes: a torn trailing entry, shorter than any real entry
		RecoverySimulation.append(RecoverySimulation.transactionsFile(this.directory, 0), new byte[8]);

		this.assertMarkerRecovers();
	}


	private void stageMarker()
	{
		this.storage = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 1, MAX_FILE_SIZE));
		final Root root = new Root();
		root.marker  = MARKER;
		root.payload = new byte[1024];
		this.storage.setRoot(root);
		this.storage.storeRoot();
		this.storage.shutdown();
		this.storage = null;
	}

	private Path headDataFile()
	{
		return RecoverySimulation.dataFiles(this.directory, 0).lastEntry().getValue();
	}

	private void assertMarkerRecovers()
	{
		this.reloaded = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 1, MAX_FILE_SIZE));
		final Root root = (Root)this.reloaded.root();
		assertNotNull(root, "root gone after healing the torn tail");
		assertEquals(MARKER, root.marker, "the last committed state must survive the torn tail");
	}


	public static class Root
	{
		int    marker ;
		byte[] payload;
	}
}
