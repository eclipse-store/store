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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cross-channel recovery when channels diverge after a power loss. Every channel logs each store
 * with the same task timestamp, so the greatest store common to all channels is the minimum of the
 * channels' latest timestamps. A channel that is ahead rolls back to the length its own log recorded
 * for that consensus store; a channel that lost stores it never truncated must not silently discard
 * the committed data the intact channels still hold.
 * <p>
 * A large file size keeps each channel in a single data file, so the divergence stays within the
 * head file (no sealed-file rollback involved).
 * <p>
 * Fix-guard for divergence beyond one store, which is not reconciled before this change (start is
 * refused); single-store divergence and the consensus-0 refusal already hold before it.
 */
@Timeout(120)
class MultiChannelDivergenceRecoveryTest
{
	private static final int MAX_FILE_SIZE = 50_000_000;
	private static final int ROUNDS        = 6         ;

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
	void oneStoreAheadRollsBackToConsensus()
	{
		final long[][] size = this.storeRounds(2, ROUNDS);
		// channel 0 keeps all rounds; the sibling lost the last store
		this.truncateLaggingChannels(2, 0, size[ROUNDS - 1]);

		assertEquals(ROUNDS - 1, this.reloadConsensusVersion(2));
	}

	@Test
	void severalStoresAheadRollBackToConsensus()
	{
		// channel 0 is ahead by three stores: recovery must re-read its log to find the consensus length
		final long[][] size = this.storeRounds(2, ROUNDS);
		this.truncateLaggingChannels(2, 0, size[ROUNDS - 3]);

		assertEquals(ROUNDS - 3, this.reloadConsensusVersion(2));
	}

	@Test
	void fourChannelsRollBackToConsensus()
	{
		final long[][] size = this.storeRounds(4, ROUNDS);
		this.truncateLaggingChannels(4, 0, size[ROUNDS - 2]);

		assertEquals(ROUNDS - 2, this.reloadConsensusVersion(4));
	}

	@Test
	void aChannelWithNoStoreWhileOthersHaveStoresRefusesToStart()
	{
		this.storeRounds(2, ROUNDS);
		// channel 0 loses every store (only its file-creation entry survives) while channel 1 keeps them:
		// truncating the intact channel back to "before any store" would discard committed data
		final Path transactions0 = RecoverySimulation.transactionsFile(this.directory, 0);
		assertTrue(RecoverySimulation.size(transactions0) > FILE_CREATION_ENTRY_LENGTH());
		RecoverySimulation.truncate(transactions0, FILE_CREATION_ENTRY_LENGTH());

		assertThrows(RuntimeException.class, () ->
		{
			this.reloaded = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 2, MAX_FILE_SIZE));
		});
	}


	///////////////////////////////////////////////////////////////////////////
	// scenario //
	/////////////

	/** Runs {@code rounds} all-channel stores; returns each channel's transactions-log size after each round. */
	private long[][] storeRounds(final int channelCount, final int rounds)
	{
		this.storage = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, channelCount, MAX_FILE_SIZE));
		final PersistenceObjectRegistry registry = this.storage.persistenceManager().objectRegistry();

		final Root root = new Root();
		this.storage.setRoot(root);
		this.storage.storeRoot();

		final Cell[]   cells = this.placeOnePerChannel(root, registry, channelCount);
		final Object[] all   = objectArray(root, cells);

		final long[][] size = new long[rounds + 1][channelCount];
		for(int r = 1; r <= rounds; r++)
		{
			root.version = r;
			for(final Cell cell : cells)
			{
				cell.version = r;
			}
			this.storage.storeAll(all);
			for(int channel = 0; channel < channelCount; channel++)
			{
				size[r][channel] = RecoverySimulation.size(RecoverySimulation.transactionsFile(this.directory, channel));
			}
		}

		this.storage.shutdown();
		this.storage = null;
		return size;
	}

	/** Truncates every channel except {@code keptChannel} to its recorded size, dropping its later stores. */
	private void truncateLaggingChannels(final int channelCount, final int keptChannel, final long[] sizePerChannel)
	{
		for(int channel = 0; channel < channelCount; channel++)
		{
			if(channel != keptChannel)
			{
				RecoverySimulation.truncate(RecoverySimulation.transactionsFile(this.directory, channel), sizePerChannel[channel]);
			}
		}
	}

	private int reloadConsensusVersion(final int channelCount)
	{
		this.reloaded = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, channelCount, MAX_FILE_SIZE));
		final Root root = (Root)this.reloaded.root();
		for(final Cell cell : root.cells)
		{
			assertEquals(root.version, cell.version, "every channel must roll back to the same consensus store");
		}
		return root.version;
	}


	///////////////////////////////////////////////////////////////////////////
	// staging helpers //
	////////////////////

	/** Places one cell on each channel so every round's store touches - and logs on - every channel. */
	private Cell[] placeOnePerChannel(final Root root, final PersistenceObjectRegistry registry, final int channelCount)
	{
		// store a pool of cells in one call: their object ids are consecutive and cover every channel
		final List<Cell> pool = new ArrayList<>();
		for(int i = 0; i < channelCount * 8; i++)
		{
			pool.add(new Cell());
		}
		root.cells = pool.toArray(new Cell[0]);
		this.storage.store(root);

		final Map<Integer, Cell> byChannel = new HashMap<>();
		for(final Cell candidate : pool)
		{
			byChannel.putIfAbsent(
				RecoverySimulation.channelOf(RecoverySimulation.objectId(registry, candidate), channelCount),
				candidate
			);
		}
		assertEquals(channelCount, byChannel.size(), "could not place a cell on every channel");

		final Cell[] cells = new Cell[channelCount];
		byChannel.forEach((channel, cell) -> cells[channel] = cell);
		root.cells = cells;
		this.storage.store(root);
		return cells;
	}

	private static Object[] objectArray(final Root root, final Cell[] cells)
	{
		final Object[] all = new Object[cells.length + 1];
		all[0] = root;
		System.arraycopy(cells, 0, all, 1, cells.length);
		return all;
	}

	private static long FILE_CREATION_ENTRY_LENGTH()
	{
		return org.eclipse.store.storage.types.StorageTransactionsAnalysis.Logic.entryLengthFileCreation();
	}


	///////////////////////////////////////////////////////////////////////////
	// model //
	//////////

	public static class Root
	{
		int    version;
		Cell[] cells  ;
	}

	public static class Cell
	{
		int version;
	}
}
