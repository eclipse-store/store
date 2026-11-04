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
import static test.eclipse.store.recovery.RecoverySimulation.aggregatorField;
import static test.eclipse.store.recovery.RecoverySimulation.feed;

import org.eclipse.store.storage.types.StorageTransactionsAnalysis.EntryAggregator;
import org.junit.jupiter.api.Test;

/**
 * Log-replay anchor semantics the cross-channel store reconciliation relies on: a channel that is
 * one store "ahead" is rolled back to {@code (lastConsistentStoreTimestamp, lastConsistentStoreLength)},
 * so that pair must always name the last confirmed store - never a length above an unconfirmed store's
 * chunk, or a timestamp of a store that gets rolled back.
 * <p>
 * Fix-guard: before this change these anchors are computed wrongly; they are only correct afterwards.
 */
class PostStoreTransferAnchorTest
{
	private static final long BASE_LENGTH = 100;

	@Test
	void storesAloneAnchorAtThePreviousStore()
	{
		final EntryAggregator aggregator = replay(
			RecoverySimulation.fileCreationEntry(BASE_LENGTH, 1, 1),
			RecoverySimulation.storeEntry(500, 2000),
			RecoverySimulation.storeEntry(800, 3000)
		);

		assertEquals(2000, aggregatorField(aggregator, "lastConsistentStoreTimestamp"));
		assertEquals( 500, aggregatorField(aggregator, "lastConsistentStoreLength"   ));
		assertEquals(3000, aggregatorField(aggregator, "currentStoreTimestamp"       ));
		assertEquals( 800, aggregatorField(aggregator, "currentStoreLength"          ));
	}

	@Test
	void transferKeepsTheRollbackAnchorAtThePreStoreLength()
	{
		// a housekeeping transfer lands after the latest (still unconfirmed) store
		final EntryAggregator aggregator = replay(
			RecoverySimulation.fileCreationEntry(BASE_LENGTH, 1, 1),
			RecoverySimulation.storeEntry(500, 2000),
			RecoverySimulation.storeEntry(800, 3000),
			RecoverySimulation.transferEntry(1100, 3500, 1, 0)
		);

		// the transfer grows the head, kept only if the latest store survives reconciliation
		assertEquals(1100, aggregatorField(aggregator, "currentStoreLength"),
			"a surviving store keeps its post-transfer head length");
		assertEquals(3000, aggregatorField(aggregator, "currentStoreTimestamp"),
			"a transfer must not count as a store");

		// the rollback anchor must still be the pre-store length, or rolling the latest store back
		// would truncate above its chunk and keep half of it
		assertEquals(2000, aggregatorField(aggregator, "lastConsistentStoreTimestamp"),
			"a transfer must not move the rollback anchor timestamp");
		assertEquals(500, aggregatorField(aggregator, "lastConsistentStoreLength"),
			"a transfer must not move the rollback anchor past the unconfirmed store");
	}

	@Test
	void shallowTruncationFoldsTheAnchorToThePreviousStore()
	{
		// truncation back to the previous store's length: that store becomes the current one
		final EntryAggregator aggregator = replay(
			RecoverySimulation.fileCreationEntry(BASE_LENGTH, 1, 1),
			RecoverySimulation.storeEntry(500, 2000),
			RecoverySimulation.storeEntry(800, 3000),
			RecoverySimulation.fileTruncationEntry(500, 2000, 1, 800)
		);

		assertEquals(2000, aggregatorField(aggregator, "currentStoreTimestamp"));
		assertEquals( 500, aggregatorField(aggregator, "currentStoreLength"   ));
	}

	@Test
	void deepTruncationTakesTheSurvivingTimestampFromTheEntry()
	{
		// cut below the previous store: neither retained anchor matches, so the surviving store's
		// timestamp is the one recovery stamped onto the truncation entry - not the rolled-back store
		final EntryAggregator aggregator = replay(
			RecoverySimulation.fileCreationEntry(BASE_LENGTH, 1, 1),
			RecoverySimulation.storeEntry(500, 2000),
			RecoverySimulation.storeEntry(800, 3000),
			RecoverySimulation.fileTruncationEntry(300, 2000, 1, 800)
		);

		assertEquals(2000, aggregatorField(aggregator, "currentStoreTimestamp"),
			"a deep cut must name the survivor stamped on the entry, not the rolled-back store");
		assertEquals(2000, aggregatorField(aggregator, "lastConsistentStoreTimestamp"));
		assertEquals( 300, aggregatorField(aggregator, "currentStoreLength"));
	}


	private static EntryAggregator replay(final byte[]... entries)
	{
		final EntryAggregator aggregator = new EntryAggregator(0);
		for(final byte[] entry : entries)
		{
			feed(aggregator, entry);
		}
		return aggregator;
	}
}
