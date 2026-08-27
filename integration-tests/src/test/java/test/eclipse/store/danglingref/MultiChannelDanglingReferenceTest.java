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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistencyDanglingReference;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageReferenceValidationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Dangling-reference validation across multiple channels: the trusted object ids are partitioned to
 * their owning channels by the object id hash, so fabricated ids covering all channels must be
 * detected wherever they land, and a rejected store must be rolled back on every channel.
 */
public class MultiChannelDanglingReferenceTest
{
	static final int CHANNEL_COUNT = 4;

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
	void danglingReferencesOnAllChannelsAreDetectedAndRolledBack()
	{
		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setChannelCountProvider(Storage.ChannelCountProvider(CHANNEL_COUNT))
					.setReferenceValidationPolicy(StorageReferenceValidationPolicy.FAIL)
					.createConfiguration()
			)
			.start();

		final PersistenceObjectRegistry registry =
			this.storage.persistenceManager().objectRegistry();

		// one fabricated id per channel: consecutive ids cover all (oid & 3) residues.
		final List<Child> children = new ArrayList<>();
		final long[]      fakeOids = new long[CHANNEL_COUNT];
		for(int i = 0; i < CHANNEL_COUNT; i++)
		{
			final Child child = new Child("never stored #" + i);
			fakeOids[i] = DanglingRefTestUtil.FAKE_OID_BASE + i;
			registry.registerObject(fakeOids[i], child);
			children.add(child);
		}

		final Parent parent = new Parent(children);

		final RuntimeException thrown = assertThrows(
			RuntimeException.class,
			() -> this.storage.store(parent)
		);
		final StorageExceptionConsistencyDanglingReference danglingReference =
			DanglingRefTestUtil.findInCauseChain(thrown, StorageExceptionConsistencyDanglingReference.class);
		assertNotNull(
			danglingReference,
			"cause chain must contain a StorageExceptionConsistencyDanglingReference, but was: " + thrown
		);
		assertTrue(
			danglingReference.missingObjectIds().length >= 1,
			"the detecting channel must report at least one missing object id"
		);
		for(final long missing : danglingReference.missingObjectIds())
		{
			boolean known = false;
			for(final long fakeOid : fakeOids)
			{
				known |= missing == fakeOid;
			}
			assertTrue(known, "reported id " + missing + " is not one of the fabricated ids");
		}

		// the whole store must have been rolled back on every channel: a restart loads cleanly
		// and no partial data of the rejected store survives.
		assertDoesNotThrow(() -> this.storage.store(new Child("independent data")));
		this.storage.shutdown();

		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setChannelCountProvider(Storage.ChannelCountProvider(CHANNEL_COUNT))
					.setReferenceValidationPolicy(StorageReferenceValidationPolicy.FAIL)
					.createConfiguration()
			)
			.start();
		assertNull(this.storage.root(), "no root was set; restart must still load cleanly");
	}


	///////////////////////////////////////////////////////////////////////////
	// data types //
	///////////////

	public static class Parent
	{
		public List<Child> children;

		public Parent(final List<Child> children)
		{
			super();
			this.children = children;
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
