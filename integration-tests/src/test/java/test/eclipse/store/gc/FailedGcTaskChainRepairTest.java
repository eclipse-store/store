package test.eclipse.store.gc;

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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageGCZombieOidHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for the task-chain repair after a failed prepended garbage collection task.
 * <p>
 * A prepended GC task ({@code issueGarbageCollection} / {@code exportChannels} with GC) chains its
 * follow-up task only in {@code succeed()}. Before the repair, a FAILING garbage collection (any
 * {@code Throwable} during a channel's GC processing — here provoked by a throwing
 * {@link StorageGCZombieOidHandler}) severed the task chain: every subsequently enqueued task —
 * including the shutdown — was attached behind the unreachable follow-up task, and the channel
 * kept waiting on the dead task's monitor for a FULL housekeeping interval. With the huge interval
 * configured below, the shutdown in this test would stall for an hour; the {@code @Timeout} turns
 * that pre-fix behavior into a loud failure.
 * <p>
 * Note on determinism: the channel's first housekeeping tick races the test's store. If it lands
 * after the store, the zombie throws in housekeeping instead (immediate channel disruption) — that
 * path never hung and also passes here. The repaired task-chain path is the one this test guards.
 */
public class FailedGcTaskChainRepairTest
{
	@TempDir
	Path tempDir;

	EmbeddedStorageManager storage;

	@AfterEach
	public void afterTest()
	{
		if(this.storage != null && this.storage.isRunning())
		{
			try
			{
				this.storage.shutdown();
			}
			catch(final Exception ignored)
			{
				// best effort
			}
		}
	}

	@Test
	@Timeout(60)
	void failedIssuedGcMustNotStrandTheTaskChainOrTheShutdown()
	{
		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					// huge interval: pre-fix, the severed chain parked the channel for this long
					.setHousekeepingController(Storage.HousekeepingController(3_600_000, 1_000_000))
					.createConfiguration()
			)
			.setGCZombieOidHandler(objectId ->
			{
				// types and constants are intentionally unresolvable, tolerate them like the default
				if(Persistence.IdType.TID.isInRange(objectId) || Persistence.IdType.CID.isInRange(objectId))
				{
					return true;
				}
				// any data-OID zombie fails the garbage collection — the trigger for this test
				throw new StorageExceptionConsistency("test zombie escalation for objectId " + objectId);
			})
			.start();

		// plant a persisted dangling reference: the ghost is registry-known but never stored,
		// so the committed parent references an object id with no entity.
		final long  fakeOid = 1_000_000_000_920_000_000L;
		final Child ghost   = new Child("never stored");
		this.storage.persistenceManager().objectRegistry().registerObject(fakeOid, ghost);

		final Parent parent = new Parent(ghost);
		this.storage.store(parent);

		// the issued GC fails on the zombie (either inside the prepended GC task or - depending
		// on the startup housekeeping race - as an immediate channel disruption).
		assertThrows(RuntimeException.class, () -> this.storage.issueFullGarbageCollection());

		// the decisive assertion: pre-fix, the failed GC task severed the task chain and this
		// shutdown hung for the full housekeeping interval (caught by @Timeout). With the repair,
		// the follow-up task is reachable again and the shutdown completes promptly.
		final long start = System.currentTimeMillis();
		this.storage.shutdown();
		final long elapsedMs = System.currentTimeMillis() - start;
		assertTrue(elapsedMs < 30_000,
			"shutdown after a failed GC took " + elapsedMs + " ms - task chain not repaired");

		// keep the graph reachable until here so the mark path is deterministic.
		assertNotNull(parent);
		assertNotNull(ghost);
	}


	///////////////////////////////////////////////////////////////////////////
	// data types //
	///////////////

	public static class Parent
	{
		public Child child;

		public Parent(final Child child)
		{
			super();
			this.child = child;
		}
	}

	public static class Child
	{
		public String data;

		public Child(final String data)
		{
			super();
			this.data = data;
		}
	}
}
