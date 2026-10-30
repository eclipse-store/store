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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistencyDanglingReference;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageReferenceValidationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Recovery after a fail-mode rejection. The documented remedy — make the missing entity exist by
 * storing it together with (or before) the referencing parent — must succeed. In particular,
 * {@code storer.storeAll(parent, child)} exercises the commit-time prune: the child's registry-known
 * object id is both referenced and force-stored in the same commit, so it must NOT be reported as a
 * dangling reference.
 */
public class PostFailureRecoveryTest
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
	void explicitReStoreOfMissingChildInSameCommitSucceeds()
	{
		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setReferenceValidationPolicy(StorageReferenceValidationPolicy.FAIL)
					.createConfiguration()
			)
			.start();

		final PersistenceObjectRegistry registry =
			this.storage.persistenceManager().objectRegistry();

		final long  fakeOid = DanglingRefTestUtil.FAKE_OID_BASE + 10;
		final Child child   = new Child("lost child");
		registry.registerObject(fakeOid, child);

		final Parent parent = new Parent(child);

		// 1) the plain lazy store is rejected: child is registry-known but has no entity.
		final RuntimeException thrown = assertThrows(
			RuntimeException.class,
			() -> this.storage.store(parent)
		);
		assertNotNull(
			DanglingRefTestUtil.findInCauseChain(thrown, StorageExceptionConsistencyDanglingReference.class),
			"cause chain must contain a StorageExceptionConsistencyDanglingReference, but was: " + thrown
		);

		// 2) the documented remedy: force-store the child in the same commit. The child's entity
		//    is then part of the store itself, so the prune removes its id from the trusted set.
		final Storer storer = this.storage.createStorer();
		storer.storeAll(parent, child);
		assertDoesNotThrow(storer::commit, "re-storing the missing child in the same commit must succeed");

		// 3) subsequent plain stores referencing the now-existing child must also pass validation.
		assertDoesNotThrow(() -> this.storage.store(new Parent(child)));

		// 4) restart: the persisted graph is consistent, the child's data is present.
		this.storage.setRoot(parent);
		this.storage.storeRoot();
		this.storage.shutdown();

		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setReferenceValidationPolicy(StorageReferenceValidationPolicy.FAIL)
					.createConfiguration()
			)
			.start();
		final Parent reloaded = (Parent)this.storage.root();
		assertNotNull(reloaded);
		assertNotNull(reloaded.child, "the re-stored child must be loadable after restart");
		assertEquals("lost child", reloaded.child.data);
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
