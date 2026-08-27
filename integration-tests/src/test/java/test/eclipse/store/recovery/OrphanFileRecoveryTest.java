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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.serializer.afs.types.WriteController;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageWriteControllerReadOnlyMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * A data file whose number is above the highest file the transactions log knows is the artifact of
 * a crash between creating a successor file and logging its creation. It is unrecoverable content
 * the log never referenced: writable recovery removes it and proceeds; read-only recovery, unable
 * to remove it, refuses to start rather than proceed on an inconsistent inventory.
 * <p>
 * Fix-guard: before this change the orphan is neither removed nor refused in read-only mode.
 */
@Timeout(120)
class OrphanFileRecoveryTest
{
	private static final int MAX_FILE_SIZE = 20_000;
	private static final int MARKER        = 42    ;

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
	void emptyOrphanFileAboveLogIsRemovedOnRecovery()
	{
		this.stageMultiFileStorage();
		final Path orphan = this.createOrphanFile(new byte[0]);

		this.reloaded = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 1, MAX_FILE_SIZE));

		assertEquals(MARKER, ((Root)this.reloaded.root()).marker, "committed data must survive");
		assertFalse(Files.exists(orphan), "the orphan file must have been removed");
	}

	@Test
	void dataBearingOrphanFileAboveLogIsRemovedOnRecovery()
	{
		this.stageMultiFileStorage();
		// an interrupted rollover managed to write some (valid) records before the crash
		final byte[] content = RecoverySimulation.readRange(
			RecoverySimulation.dataFiles(this.directory, 0).firstEntry().getValue(), 0, 64
		);
		final Path orphan = this.createOrphanFile(content);

		this.reloaded = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 1, MAX_FILE_SIZE));

		assertEquals(MARKER, ((Root)this.reloaded.root()).marker, "committed data must survive");
		assertFalse(Files.exists(orphan), "the orphan file must have been removed");
	}

	@Test
	void orphanFileAboveLogInReadOnlyModeRefusesToStart()
	{
		this.stageMultiFileStorage();
		this.createOrphanFile(new byte[0]);

		final StorageWriteControllerReadOnlyMode readOnly =
			new StorageWriteControllerReadOnlyMode(WriteController.Enabled());
		readOnly.setReadOnly(true);

		assertThrows(RuntimeException.class, () ->
			this.reloaded = EmbeddedStorage.Foundation(RecoverySimulation.quietConfig(this.directory, 1, MAX_FILE_SIZE))
				.setWriteController(readOnly)
				.start()
		);
	}


	///////////////////////////////////////////////////////////////////////////
	// staging //
	////////////

	/** Stores enough oversized data to roll the head over several times, then shuts down cleanly. */
	private void stageMultiFileStorage()
	{
		this.storage = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 1, MAX_FILE_SIZE));
		final Root root = new Root();
		root.marker = MARKER;
		this.storage.setRoot(root);
		this.storage.storeRoot();
		for(int i = 0; i < 3; i++)
		{
			root.blob = new byte[MAX_FILE_SIZE * 2];
			this.storage.store(root);
		}
		this.storage.shutdown();
		this.storage = null;
	}

	/**
	 * Writes a data file numbered well above the highest existing file - a file the log never
	 * recorded. The gap keeps the orphan's number clear of any file the engine creates itself when
	 * it rolls the (oversized) head over on restart.
	 */
	private Path createOrphanFile(final byte[] content)
	{
		final long orphanNumber = RecoverySimulation.dataFiles(this.directory, 0).lastKey() + 10;
		final Path orphan       = this.directory.resolve("channel_0").resolve("channel_0_" + orphanNumber + ".dat");
		try
		{
			Files.write(orphan, content);
		}
		catch(final IOException e)
		{
			throw new UncheckedIOException(e);
		}
		assertTrue(Files.exists(orphan), "staging: orphan file not created");
		return orphan;
	}


	///////////////////////////////////////////////////////////////////////////
	// model //
	//////////

	public static class Root
	{
		int    marker;
		byte[] blob  ;
	}
}
