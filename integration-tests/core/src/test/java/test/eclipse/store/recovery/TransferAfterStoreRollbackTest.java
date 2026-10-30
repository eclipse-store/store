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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.NavigableMap;

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.recovery.RecoverySimulation.EntityRecord;

/**
 * A housekeeping transfer that lands after a store must not defeat the cross-channel store rollback.
 * <p>
 * All channels log a store with the same task timestamp; on restart a channel that is "ahead"
 * (its sibling never flushed that store) is rolled back to its pre-store length. If a transfer
 * grew the head above the store's chunk and moved the rollback anchor with it, the channel keeps
 * its half of the store while the sibling drops it: a torn, half-visible store. Because nothing in
 * the store is eagerly root-reachable, the storage restarts cleanly and the tear only detonates on
 * a later load - the silent corruption these tests guard against.
 * <p>
 * The power loss is simulated on a cleanly shut-down (fully flushed) state: the post-store transfer
 * is appended to channel A exactly as the engine writes it, and channel B's store entry is truncated
 * away ("never reached disk").
 * <p>
 * Fix-guard: before this change this post-store transfer tears the store; it stays intact only afterwards.
 */
@Timeout(120)
class TransferAfterStoreRollbackTest
{
	private static final int  CHANNEL_COUNT = 2      ;
	private static final int  MAX_FILE_SIZE = 20_000 ;
	private static final int  BLOB_SIZE     = 25_000 ;
	private static final long MARKER_STAMP  = 0xC0FFEEL;

	private enum Mode
	{
		NO_TRANSFER,
		TRANSFER,
		TRANSFER_SOURCE_DELETED
	}

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
	void powerLossWithoutTransferRollsTheStoreBackConsistently()
	{
		// control: proves the surgery models the cut faithfully and plain reconciliation handles it
		final Outcome outcome = this.run(Mode.NO_TRANSFER);

		assertTrue(outcome.started, "restart must succeed");
		assertEquals(0, outcome.version, "the unconfirmed store must roll back on every channel");
		assertTrue(outcome.consistent, "the rolled-back state must be consistent");
		assertTrue(outcome.markerIntact, "the committed marker must survive");
	}

	@Test
	void powerLossAfterPostStoreTransferKeepsTheStoreAllOrNothing()
	{
		final Outcome outcome = this.run(Mode.TRANSFER);

		assertTrue(outcome.started, "restart must succeed");
		assertTrue(outcome.consistent,
			"the store must be fully visible or fully rolled back, never half kept by one channel");
		assertTrue(outcome.markerIntact, "the committed marker must survive the recovery");
	}

	@Test
	void powerLossAfterTransferWithDeletedSourceNeverLosesDataSilently()
	{
		final Outcome outcome = this.run(Mode.TRANSFER_SOURCE_DELETED);

		// a loud fail-stop is acceptable here: with the transfer's source unlinked, rolling the store
		// back cannot restore the transferred entity, so refusing to start beats losing it silently
		if(!outcome.started)
		{
			return;
		}
		assertTrue(outcome.consistent, "the store must be all-or-nothing");
		assertTrue(outcome.markerIntact,
			"the transferred committed entity is the marker's only copy; recovery must not drop it");
	}


	///////////////////////////////////////////////////////////////////////////
	// scenario //
	/////////////

	private Outcome run(final Mode mode)
	{
		this.storage = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, CHANNEL_COUNT, MAX_FILE_SIZE));
		final PersistenceObjectRegistry registry = this.storage.persistenceManager().objectRegistry();

		final DataRoot       root  = new DataRoot();
		final VersionedState state = new VersionedState();
		root.state = Lazy.Reference(state);
		this.storage.setRoot(root);
		this.storage.storeRoot();

		final int channelA = RecoverySimulation.channelOf(RecoverySimulation.objectId(registry, state), CHANNEL_COUNT);

		// seal a file on channel A whose only live content is the marker (oversized blobs force rollovers)
		this.storeBlobOn(root, registry, 1, channelA);
		final Marker marker    = this.storeMarkerOn(root, registry, channelA);
		final long   markerOid = RecoverySimulation.objectId(registry, marker);
		this.storeBlobOn(root, registry, 2, channelA);
		root.blob1 = null;
		root.blob2 = null;
		this.storage.store(root);

		// store Tn-1: fresh entities on both channels plus a re-store of root and state
		root.settle1 = new Marker(101);
		root.settle2 = new Marker(102);
		this.storage.storeAll(root, state);

		final Path transactionsB     = RecoverySimulation.transactionsFile(this.directory, 1 - channelA);
		final long transactionsBSize = RecoverySimulation.size(transactionsB);

		// store Tn: flip the state and attach one payload per channel; nothing of it is root-reachable
		state.version = 1;
		final Payload payloadA = new Payload(1);
		final Payload payloadB = new Payload(2);
		state.a = payloadA;
		state.b = payloadB;
		this.storage.store(state);
		assertNotEquals(
			RecoverySimulation.channelOf(RecoverySimulation.objectId(registry, payloadA), CHANNEL_COUNT),
			RecoverySimulation.channelOf(RecoverySimulation.objectId(registry, payloadB), CHANNEL_COUNT),
			"the store's payloads must land on different channels"
		);

		this.storage.shutdown();
		this.storage = null;

		assertEquals(transactionsBSize + org.eclipse.store.storage.types.StorageTransactionsAnalysis.Logic.entryLengthStore(),
			RecoverySimulation.size(transactionsB),
			"channel B's log must have grown by exactly the store entry");

		if(mode != Mode.NO_TRANSFER)
		{
			this.appendPostStoreTransfer(channelA, markerOid, mode);
		}

		// the power-loss cut: drop the store entry - and only it - from channel B's log
		RecoverySimulation.truncate(transactionsB, transactionsBSize);

		return this.restartAndInspect();
	}

	/** Appends the post-store transfer to channel A exactly as a housekeeping dissolution would write it. */
	private void appendPostStoreTransfer(final int channelA, final long markerOid, final Mode mode)
	{
		final NavigableMap<Long, Path> files      = RecoverySimulation.dataFiles(this.directory, channelA);
		final long                     headNumber = files.lastKey();
		final Path                     headFile   = files.get(headNumber);

		long         sourceNumber = -1  ;
		Path         sourceFile   = null;
		EntityRecord markerRecord = null;
		for(final var entry : files.entrySet())
		{
			for(final EntityRecord record : RecoverySimulation.walkEntities(entry.getValue()))
			{
				if(record.objectId() == markerOid)
				{
					sourceNumber = entry.getKey();
					sourceFile   = entry.getValue();
					markerRecord = record;
				}
			}
		}
		assertTrue(markerRecord != null, "marker record not found in channel A");
		assertNotEquals(headNumber, sourceNumber, "the marker must live in a sealed non-head file");

		RecoverySimulation.append(headFile, RecoverySimulation.readRange(sourceFile, markerRecord.offset(), markerRecord.length()));

		final Path transactionsA = RecoverySimulation.transactionsFile(this.directory, channelA);
		final long timestamp     = System.currentTimeMillis() * 1_000_000L;
		RecoverySimulation.append(transactionsA, RecoverySimulation.transferEntry(
			RecoverySimulation.size(headFile), timestamp, sourceNumber, markerRecord.offset()
		));

		if(mode == Mode.TRANSFER_SOURCE_DELETED)
		{
			RecoverySimulation.append(transactionsA, RecoverySimulation.fileDeletionEntry(
				RecoverySimulation.size(sourceFile), timestamp + 1, sourceNumber
			));
			RecoverySimulation.delete(sourceFile);
		}
	}

	private Outcome restartAndInspect()
	{
		final Outcome outcome = new Outcome();
		try
		{
			this.reloaded = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, CHANNEL_COUNT, MAX_FILE_SIZE));
			outcome.started = true;
		}
		catch(final RuntimeException e)
		{
			return outcome;
		}

		final DataRoot root = (DataRoot)this.reloaded.root();

		// first touch of the torn half: a torn store detonates here, after a clean startup
		boolean stateLoaded = false;
		try
		{
			final VersionedState state = Lazy.get(root.state);
			stateLoaded = state != null;
			if(stateLoaded)
			{
				outcome.version = state.version;
				final boolean payloadA = state.a != null && state.a.value == 1;
				final boolean payloadB = state.b != null && state.b.value == 2;
				outcome.consistent = outcome.version == 0
					? !payloadA && !payloadB
					: outcome.version == 1 && payloadA && payloadB;
			}
		}
		catch(final RuntimeException torn)
		{
			outcome.consistent = false;
		}
		if(!stateLoaded)
		{
			outcome.consistent = false;
		}

		outcome.markerIntact = markerIntact(root);
		return outcome;
	}

	private static boolean markerIntact(final DataRoot root)
	{
		try
		{
			final Marker marker = Lazy.get(root.marker);
			return marker != null && marker.stamp == MARKER_STAMP;
		}
		catch(final RuntimeException e)
		{
			return false;
		}
	}


	///////////////////////////////////////////////////////////////////////////
	// staging helpers //
	////////////////////

	private void storeBlobOn(final DataRoot root, final PersistenceObjectRegistry registry, final int slot, final int channel)
	{
		for(int attempt = 0; attempt < 4; attempt++)
		{
			final byte[] blob = new byte[BLOB_SIZE];
			if(slot == 1)
			{
				root.blob1 = blob;
			}
			else
			{
				root.blob2 = blob;
			}
			this.storage.store(root);
			if(RecoverySimulation.channelOf(RecoverySimulation.objectId(registry, blob), CHANNEL_COUNT) == channel)
			{
				return;
			}
		}
		throw new IllegalStateException("could not place a blob on channel " + channel);
	}

	private Marker storeMarkerOn(final DataRoot root, final PersistenceObjectRegistry registry, final int channel)
	{
		for(int attempt = 0; attempt < 4; attempt++)
		{
			final Marker marker = new Marker(MARKER_STAMP);
			root.marker = Lazy.Reference(marker);
			this.storage.store(root);
			if(RecoverySimulation.channelOf(RecoverySimulation.objectId(registry, marker), CHANNEL_COUNT) == channel)
			{
				return marker;
			}
		}
		throw new IllegalStateException("could not place the marker on channel " + channel);
	}


	///////////////////////////////////////////////////////////////////////////
	// model //
	//////////

	private static final class Outcome
	{
		boolean started     ;
		int     version = -1 ;
		boolean consistent  ;
		boolean markerIntact;
	}

	public static class DataRoot
	{
		Lazy<VersionedState> state  ;
		Lazy<Marker>         marker ;
		Marker               settle1;
		Marker               settle2;
		byte[]               blob1  ;
		byte[]               blob2  ;
	}

	public static class VersionedState
	{
		int     version;
		Payload a      ;
		Payload b      ;
	}

	public static class Payload
	{
		final long value;

		Payload(final long value)
		{
			this.value = value;
		}
	}

	public static class Marker
	{
		final long stamp;

		Marker(final long stamp)
		{
			this.stamp = stamp;
		}
	}
}
