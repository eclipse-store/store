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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistencyDanglingReference;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageEventLogger;
import org.eclipse.store.storage.types.StorageReferenceValidationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the store-time validation of registry-trusted reference object ids.
 * <p>
 * The lazy storer skips storing an instance whose object id is already known to the global object
 * registry, trusting that its entity already exists in the storage. These tests plant a never-stored
 * instance in the registry under a fabricated object id and then store a parent referencing it,
 * asserting the behavior of all three {@link StorageReferenceValidationPolicy} modes.
 */
public class RegistryTrustedSkipValidationTest
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

	private EmbeddedStorageManager startStorage(
		final StorageReferenceValidationPolicy policy     ,
		final StorageEventLogger               eventLogger
	)
	{
		return this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setReferenceValidationPolicy(policy)
					.createConfiguration()
			)
			.setEventLogger(eventLogger != null ? eventLogger : StorageEventLogger.Default())
			.start();
	}

	@Test
	void failModeRejectsDanglingReferenceAtomicallyAndStorageStaysUsable()
	{
		this.startStorage(StorageReferenceValidationPolicy.FAIL, null);

		final long  fakeOid = DanglingRefTestUtil.FAKE_OID_BASE;
		final Child child   = new Child("never stored");
		this.storage.persistenceManager().objectRegistry().registerObject(fakeOid, child);

		// the parent also references an enum constant: constant ids (CIDs) must be
		// filtered from the trusted set, otherwise they would be reported as missing too.
		final Parent parent = new Parent(child, Color.RED);

		final RuntimeException thrown = assertThrows(
			RuntimeException.class,
			() -> this.storage.store(parent),
			"storing a reference to a never-stored registry-known instance must be rejected"
		);
		final StorageExceptionConsistencyDanglingReference danglingReference =
			DanglingRefTestUtil.findInCauseChain(thrown, StorageExceptionConsistencyDanglingReference.class);
		assertNotNull(
			danglingReference,
			"cause chain must contain a StorageExceptionConsistencyDanglingReference, but was: " + thrown
		);
		assertArrayEquals(
			new long[]{fakeOid},
			danglingReference.missingObjectIds(),
			"exactly the fabricated object id must be reported (in particular no enum CIDs)"
		);

		// nothing of the rejected store may have been committed and the storage must stay usable.
		assertDoesNotThrow(() -> this.storage.store(new Child("independent data")));

		this.storage.shutdown();
		this.storage = EmbeddedStorage.start(this.tempDir);
		assertDoesNotThrow(() -> this.storage.store(new Child("after restart")));
	}

	@Test
	void logModeReportsDanglingReferenceButCommits()
	{
		final DanglingRefTestUtil.RecordingEventLogger recorder = new DanglingRefTestUtil.RecordingEventLogger();
		this.startStorage(StorageReferenceValidationPolicy.LOG, recorder);

		final long  fakeOid = DanglingRefTestUtil.FAKE_OID_BASE + 1;
		final Child child   = new Child("never stored");
		this.storage.persistenceManager().objectRegistry().registerObject(fakeOid, child);

		final Parent parent = new Parent(child, Color.GREEN);
		assertDoesNotThrow(() -> this.storage.store(parent), "log mode must not reject the store");

		assertEquals(1, recorder.reportedObjectIds.size(), "exactly one dangling-reference event expected");
		assertArrayEquals(new long[]{fakeOid}, recorder.reportedObjectIds.get(0));
	}

	@Test
	void offModeIsSilent()
	{
		final DanglingRefTestUtil.RecordingEventLogger recorder = new DanglingRefTestUtil.RecordingEventLogger();
		this.startStorage(StorageReferenceValidationPolicy.OFF, recorder);

		final long  fakeOid = DanglingRefTestUtil.FAKE_OID_BASE + 2;
		final Child child   = new Child("never stored");
		this.storage.persistenceManager().objectRegistry().registerObject(fakeOid, child);

		final Parent parent = new Parent(child, Color.BLUE);
		assertDoesNotThrow(() -> this.storage.store(parent), "off mode must not reject the store");

		assertTrue(recorder.reportedObjectIds.isEmpty(), "off mode must not report anything");
	}

	@Test
	void validStoreWithOnlyExistingReferencesPassesFailMode()
	{
		this.startStorage(StorageReferenceValidationPolicy.FAIL, null);

		// store the child first, then a parent referencing the now registry-known child:
		// the trusted id exists in the storage, so validation must pass.
		final Child child = new Child("properly stored");
		this.storage.store(child);

		final Parent parent = new Parent(child, Color.RED);
		assertDoesNotThrow(() -> this.storage.store(parent));

		this.storage.shutdown();
		this.storage = EmbeddedStorage.start(this.tempDir);
		assertNull(this.storage.root(), "no root was set; restart must still load cleanly");
	}


	///////////////////////////////////////////////////////////////////////////
	// data types //
	///////////////

	public enum Color
	{
		RED, GREEN, BLUE
	}

	public static class Parent
	{
		public Child child;
		public Color color;

		public Parent(final Child child, final Color color)
		{
			super();
			this.child = child;
			this.color = color;
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
