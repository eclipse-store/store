package test.eclipse.store.zombie;

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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistencyZombieOid;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageGCZombieOidHandler;
import org.eclipse.store.storage.types.StorageReferenceValidationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Escalation of zombie object ids: a dangling reference that is ALREADY persisted (here planted by
 * committing a store with reference validation off) is first noticed by the GC's marking. The
 * default handler tolerates it (WARN + event, GC continues); the strict handler must throw a
 * {@link StorageExceptionConsistencyZombieOid} carrying the object id, so the corruption surfaces
 * while the evidence is still recoverable instead of at a much later restart.
 */
public class StrictZombieOidHandlerTest
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

	private EmbeddedStorageManager startStorage(final StorageGCZombieOidHandler zombieHandler)
	{
		return this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					// validation off so the dangling reference can actually be committed
					.setReferenceValidationPolicy(StorageReferenceValidationPolicy.OFF)
					// huge housekeeping interval: the zombie surfaces deterministically in the
					// ISSUED gc task below, whose failure propagates to the calling thread.
					.setHousekeepingController(Storage.HousekeepingController(3_600_000, 1_000_000))
					.createConfiguration()
			)
			.setGCZombieOidHandler(zombieHandler)
			.start();
	}

	private Parent plantPersistedDanglingReference(final long fakeOid)
	{
		final Child ghost = new Child("never stored");
		this.storage.persistenceManager().objectRegistry().registerObject(fakeOid, ghost);

		// commits a reference to fakeOid without any entity existing for it (validation is off).
		// keeping the parent instance strongly held makes it registry-resident, so the GC's
		// live-OID seed marks it and the mark phase walks its binary into the zombie id.
		final Parent parent = new Parent(ghost);
		this.storage.store(parent);
		return parent;
	}

	@Test
	@Timeout(60) // guards the task-chain repair: a failed issued GC must not strand the shutdown
	void strictHandlerThrowsAtMarkTimeWithTheZombieOid()
	{
		this.startStorage(StorageGCZombieOidHandler.Strict());

		final long   fakeOid = 1_000_000_000_910_000_000L;
		final Parent parent  = this.plantPersistedDanglingReference(fakeOid);

		final RuntimeException thrown = assertThrows(
			RuntimeException.class,
			() -> this.storage.issueFullGarbageCollection(),
			"the strict handler must fail the GC on the persisted dangling reference"
		);
		final StorageExceptionConsistencyZombieOid zombie =
			findInCauseChain(thrown, StorageExceptionConsistencyZombieOid.class);
		assertNotNull(zombie,
			"cause chain must contain a StorageExceptionConsistencyZombieOid, but was: " + thrown);
		assertEquals(fakeOid, zombie.objectId(), "the zombie exception must carry the dangling object id");

		// keep the parent reachable until here so the seed path is deterministic.
		assertNotNull(parent);
	}

	@Test
	@Timeout(60)
	void toleratingHandlerReportsTheZombieAndGcCompletes()
	{
		final CountingZombieOidHandler counting = new CountingZombieOidHandler();
		this.startStorage(counting);

		final long   fakeOid = 1_000_000_000_910_000_001L;
		final Parent parent  = this.plantPersistedDanglingReference(fakeOid);

		// pre-fix behavior, unchanged: the zombie is reported but the GC completes.
		assertDoesNotThrow(() -> this.storage.issueFullGarbageCollection());
		assertTrue(counting.zombieOids.contains(fakeOid),
			"the tolerating handler must still report the zombie id, got: " + counting.zombieOids);

		assertNotNull(parent);
	}

	private static <T extends Throwable> T findInCauseChain(final Throwable root, final Class<T> type)
	{
		Throwable slow = root, fast = root;
		while(fast != null)
		{
			if(type.isInstance(fast))
			{
				return type.cast(fast);
			}
			fast = fast.getCause();
			if(type.isInstance(fast)) // isInstance(null) is false
			{
				return type.cast(fast);
			}
			fast = fast == null ? null : fast.getCause();
			slow = slow.getCause();
			if(fast != null && fast == slow)
			{
				break; // cause cycle
			}
		}
		return null;
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

	static final class CountingZombieOidHandler implements StorageGCZombieOidHandler
	{
		final List<Long> zombieOids = Collections.synchronizedList(new ArrayList<>());

		@Override
		public boolean handleZombieOid(final long objectId)
		{
			this.zombieOids.add(objectId);
			return true;
		}
	}
}
