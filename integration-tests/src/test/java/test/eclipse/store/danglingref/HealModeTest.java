package test.eclipse.store.danglingref;

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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageReferenceValidationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Automatic self-healing (heal mode): a store whose data references a registry-known but
 * never-stored instance must succeed transparently — the storer re-stores the instance under its
 * existing object id and retries. The persisted graph must be fully intact after a restart.
 */
public class HealModeTest
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
	void danglingReferenceIsHealedTransparently()
	{
		final DanglingRefTestUtil.RecordingEventLogger recorder = new DanglingRefTestUtil.RecordingEventLogger();
		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setReferenceValidationPolicy(StorageReferenceValidationPolicy.HEAL)
					.createConfiguration()
			)
			.setEventLogger(recorder)
			.start();

		final PersistenceObjectRegistry registry =
			this.storage.persistenceManager().objectRegistry();

		final long  fakeOid = DanglingRefTestUtil.FAKE_OID_BASE + 50;
		final Child child   = new Child("healed child");
		registry.registerObject(fakeOid, child);

		final Parent parent = new Parent(child);

		// the plain lazy store skips the registry-known child; validation detects the missing
		// entity, healing re-stores the child under fakeOid and the retry succeeds — transparently.
		assertDoesNotThrow(() -> this.storage.store(parent), "heal mode must repair the store transparently");

		// precondition: the store must actually have been rejected once, otherwise nothing was healed.
		DanglingRefTestUtil.assertRejectionsRecorded(recorder);
		assertArrayEquals(new long[]{fakeOid}, recorder.reportedObjectIds.get(0),
			"the rejection must report exactly the ghost's object id");

		// the healed child must have kept its object id.
		assertEquals(fakeOid, registry.lookupObjectId(child), "the healed child must keep its object id");

		// persist as root and restart: the graph must be fully intact.
		this.storage.setRoot(parent);
		this.storage.storeRoot();
		this.storage.shutdown();

		this.storage = EmbeddedStorage.start(this.tempDir);
		final Parent reloaded = (Parent)this.storage.root();
		assertNotNull(reloaded);
		assertNotNull(reloaded.child, "the healed child must be loadable after restart");
		assertEquals("healed child", reloaded.child.data);
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
