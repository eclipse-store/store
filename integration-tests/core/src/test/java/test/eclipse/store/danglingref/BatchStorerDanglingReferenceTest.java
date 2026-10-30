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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;

import org.eclipse.serializer.persistence.types.BatchStorer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistencyDanglingReference;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageReferenceValidationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Smoke test: the batching storer inherits the trusted-id capture and commit-time transport from
 * the default storer, so a batch flush containing a dangling reference must be rejected the same
 * way an ordinary store is.
 */
public class BatchStorerDanglingReferenceTest
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
	void batchFlushWithDanglingReferenceIsRejected() throws Exception
	{
		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setReferenceValidationPolicy(StorageReferenceValidationPolicy.FAIL)
					.createConfiguration()
			)
			.start();

		final long  fakeOid = DanglingRefTestUtil.FAKE_OID_BASE + 40;
		final Child child   = new Child("never stored");
		this.storage.persistenceManager().objectRegistry().registerObject(fakeOid, child);

		// use a controller that never flushes on its own, so the explicit flush below is the
		// only commit and the rejection surfaces deterministically on this thread.
		final BatchStorer storer = this.storage.createBatchStorer(
			(pendingObjectCount, msSinceFirstPending) -> false,
			Duration.ofHours(1)
		);
		try
		{
			storer.store(new Parent(child));

			final RuntimeException thrown = assertThrows(RuntimeException.class, storer::flush);
			assertNotNull(
				DanglingRefTestUtil.findInCauseChain(thrown, StorageExceptionConsistencyDanglingReference.class),
				"cause chain must contain a StorageExceptionConsistencyDanglingReference, but was: " + thrown
			);
		}
		finally
		{
			try
			{
				storer.close();
			}
			catch(final Exception ignored)
			{
				// the close-time final flush of the still-pending dangling data may fail again
			}
		}

		// the storage itself must remain usable.
		assertDoesNotThrow(() -> this.storage.store(new Child("independent data")));
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
