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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;
import org.eclipse.store.storage.exceptions.StorageExceptionDisruptingExceptions;
import org.eclipse.store.storage.types.Storage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for the handling of a FAILED prepended garbage collection task.
 * <p>
 * A prepended GC task ({@code issueGarbageCollection} / {@code exportChannels} with GC) chains its
 * follow-up task only in {@code succeed()}. Before the repair, a FAILING garbage collection (any
 * {@code Throwable} during a channel's GC processing — here provoked by a throwing
 * {@link org.eclipse.store.storage.types.StorageGCZombieOidHandler StorageGCZombieOidHandler})
 * severed the task chain: every subsequently enqueued task — including the shutdown — was attached
 * behind the unreachable follow-up task. The channel then hung INDEFINITELY: {@code awaitNext}
 * times out after the housekeeping interval but reverts to the same dead task and parks again,
 * forever. The bounded shutdown probe turns that behavior into a clean assertion failure after
 * 30 s ({@code @Timeout} is the backstop, and the bounded {@code @AfterEach} keeps a hanging
 * storage from stalling the test run as well).
 * <p>
 * Covered scenarios:
 * <ul>
 *   <li>single channel: chain repaired, failure reaches the issuer, shutdown prompt;</li>
 *   <li>multiple channels: ONE channel fails mid-marking — the sibling channels must not keep
 *       waiting forever for the failed channel's marks (they park in the issued GC's waitForWork
 *       loop with an unbounded time budget otherwise) and the shutdown must complete;</li>
 *   <li>export with GC: the follow-up export task waits nobody on the GC task, so the GC failure
 *       must be propagated INTO the export task — an export explicitly requested with GC (the
 *       "definite minimum" contract) must fail loudly instead of silently delivering
 *       gc=false semantics.</li>
 * </ul>
 * <p>
 * Note on determinism: the throwing handler must fire inside the ISSUED garbage collection task
 * only. If housekeeping's incremental garbage collection encounters the zombie instead, the
 * channel registers a DISRUPTION and disables processing — then the shutdown does not hang but
 * fails with {@code StorageExceptionDisruptingExceptions}, and the repaired-task-chain path this
 * test guards is never exercised. Housekeeping runs after every processed task until the interval
 * budget is consumed (the huge interval only delays the budget refresh), so two measures pin the
 * intended path: the budget of 1 ns is exhausted by the first (pre-test-data, harmless)
 * housekeeping task, and the zombie handler only throws while explicitly armed around the issued
 * garbage collection.
 */
public class FailedGcTaskChainRepairTest
{
	@TempDir
	Path tempDir;

	EmbeddedStorageManager storage;

	@AfterEach
	public void afterTest()
	{
		if(this.storage != null)
		{
			try
			{
				/*
				 * Bounded and WITHOUT an isRunning() pre-check: while a shutdown hangs (the
				 * pre-fix behavior), it holds the storage system's state monitor - isRunning()
				 * would block on that same monitor indefinitely. The probe thread blocks instead
				 * and is abandoned after the timeout.
				 */
				shutdownCompletesWithin(this.storage, 10_000);
			}
			catch(final Exception ignored)
			{
				// best effort
			}
		}
	}

	/**
	 * Runs {@code storage.shutdown()} on a separate daemon thread and waits at most
	 * {@code timeoutMs}. Returns whether the shutdown completed in time; rethrows any
	 * exception the shutdown threw. Keeps a hanging shutdown (the pre-fix regression
	 * behavior) from stalling the test.
	 */
	private static boolean shutdownCompletesWithin(final EmbeddedStorageManager storage, final long timeoutMs)
	{
		final AtomicReference<RuntimeException> failure = new AtomicReference<>();
		final Thread shutdownThread = new Thread(() ->
		{
			try
			{
				storage.shutdown();
			}
			catch(final RuntimeException e)
			{
				failure.set(e);
			}
		}, "test-bounded-shutdown");
		shutdownThread.setDaemon(true);
		shutdownThread.start();
		try
		{
			shutdownThread.join(timeoutMs);
		}
		catch(final InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
		if(failure.get() != null)
		{
			throw failure.get();
		}
		return !shutdownThread.isAlive();
	}

	private EmbeddedStorageManager startStorage(final int channelCount, final AtomicBoolean armed)
	{
		return EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setChannelCountProvider(Storage.ChannelCountProvider(channelCount))
					// huge interval: pre-fix, the severed chain parked the channel indefinitely
					// (awaitNext reverts to the same dead task after every interval timeout).
					// 1 ns budget: exhausted by the first housekeeping task (pre-test-data), so no
					// housekeeping GC interferes with the issued-GC path these tests guard.
					.setHousekeepingController(Storage.HousekeepingController(3_600_000, 1))
					.createConfiguration()
			)
			.setGCZombieOidHandler(objectId ->
			{
				if(!armed.get())
				{
					/*
					 * While not armed, report every zombie as handled - deliberately MORE
					 * tolerant than the default handler (which returns false for data OIDs so
					 * the caller logs a warning): nothing outside the armed window may disturb
					 * the test, not even logging noise from startup-phase artifacts.
					 */
					return true;
				}
				// types and constants are intentionally unresolvable, tolerate them like the default
				if(Persistence.IdType.TID.isInRange(objectId) || Persistence.IdType.CID.isInRange(objectId))
				{
					return true;
				}
				// any data-OID zombie fails the garbage collection — the trigger for these tests
				throw new StorageExceptionConsistency("test zombie escalation for objectId " + objectId);
			})
			.start();
	}

	/**
	 * Plants a persisted dangling reference: the ghost is registry-known but never stored,
	 * so the committed parent references an object id with no entity.
	 */
	private Parent plantDanglingReference()
	{
		final long  fakeOid = 1_000_000_000_920_000_000L;
		final Child ghost   = new Child("never stored");
		this.storage.persistenceManager().objectRegistry().registerObject(fakeOid, ghost);

		final Parent parent = new Parent(ghost);
		this.storage.store(parent);
		return parent;
	}

	@Test
	@Timeout(60)
	void failedIssuedGcMustNotStrandTheTaskChainOrTheShutdown()
	{
		// armed only around the issued GC so a housekeeping GC can never turn the planted zombie
		// into a channel disruption (see the class javadoc's determinism note).
		final AtomicBoolean armed = new AtomicBoolean(false);
		this.storage = this.startStorage(1, armed);

		final Parent parent = this.plantDanglingReference();

		// the issued GC fails on the zombie inside the prepended GC task (the handler is armed
		// for this call only; the failure must NOT be a channel disruption - see class javadoc).
		armed.set(true);
		final RuntimeException thrown =
			assertThrows(RuntimeException.class, () -> this.storage.issueFullGarbageCollection());
		armed.set(false);
		assertFalse(thrown instanceof StorageExceptionDisruptingExceptions,
			"GC failure took the channel-disruption path instead of the prepended-GC-task path");

		// the decisive assertion: pre-fix, the failed GC task severed the task chain and the
		// shutdown hung indefinitely. The bounded probe turns that hang into a clean assertion
		// failure after 30 s. With the repair, the follow-up task is reachable again and the
		// shutdown completes promptly.
		assertTrue(shutdownCompletesWithin(this.storage, 30_000),
			"shutdown after a failed GC did not complete within 30 s - task chain not repaired");
		this.storage = null; // shut down successfully - nothing left for the @AfterEach probe

		// keep the graph reachable until here so the mark path is deterministic.
		assertNotNull(parent);
		assertNotNull(parent.child);
	}

	@Test
	@Timeout(60)
	void failedGcOnOneChannelMustNotHangSiblingChannels()
	{
		final AtomicBoolean armed = new AtomicBoolean(false);
		this.storage = this.startStorage(4, armed);

		final Parent parent = this.plantDanglingReference();

		/*
		 * The dangling OID belongs to exactly one channel, so exactly one channel's marking
		 * fails. The three sibling channels sit in the issued GC's waitForWork loop with an
		 * unbounded time budget, waiting for marks the failed channel will never deliver -
		 * without an abort signal they never return from the GC task, the repaired chain is
		 * unreachable and both this call and the shutdown hang.
		 */
		armed.set(true);
		final RuntimeException thrown =
			assertThrows(RuntimeException.class, () -> this.storage.issueFullGarbageCollection());
		armed.set(false);
		assertFalse(thrown instanceof StorageExceptionDisruptingExceptions,
			"GC failure took the channel-disruption path instead of the prepended-GC-task path");

		assertTrue(shutdownCompletesWithin(this.storage, 30_000),
			"shutdown after a failed multi-channel GC did not complete within 30 s - sibling channels stuck");
		this.storage = null;

		assertNotNull(parent);
		assertNotNull(parent.child);
	}

	@Test
	@Timeout(60)
	void failedPrependedGcMustFailExportWithGc(@TempDir final Path exportDir)
	{
		final AtomicBoolean armed = new AtomicBoolean(false);
		this.storage = this.startStorage(1, armed);

		final Parent parent = this.plantDanglingReference();

		/*
		 * exportChannels(..., true) requests the "definite minimum" contract: the export must
		 * contain reachable entities only. The caller waits on the EXPORT task, not on the
		 * prepended GC task - so the GC failure must be propagated into the export task,
		 * otherwise the export silently succeeds with gc=false semantics (may contain
		 * unreachable garbage). Note: the export work itself may still execute before the
		 * failure is reported - there is no pre-processing problem gate - but the caller
		 * MUST see the failure.
		 */
		armed.set(true);
		final RuntimeException thrown = assertThrows(RuntimeException.class,
			() -> this.storage.exportChannels(Storage.FileProvider(exportDir), true));
		armed.set(false);

		// the propagated failure must carry the original GC cause
		boolean causeFound = false;
		for(Throwable t = thrown; t != null; t = t.getCause())
		{
			if(t instanceof StorageExceptionConsistency
				&& String.valueOf(t.getMessage()).contains("test zombie escalation"))
			{
				causeFound = true;
				break;
			}
		}
		assertTrue(causeFound,
			"the export failure must carry the prepended GC's failure as its cause, but was: " + thrown);

		assertTrue(shutdownCompletesWithin(this.storage, 30_000),
			"shutdown after a failed export-with-GC did not complete within 30 s");
		this.storage = null;

		assertNotNull(parent);
		assertNotNull(parent.child);
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
