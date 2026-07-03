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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistencyDanglingReference;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageReferenceValidationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Commit-failure rollback of {@code Lazy.$link}: storing links the referent's object id into the
 * {@code Lazy} instance during serialization, BEFORE the commit write. Without rollback, a failed
 * commit would leave the Lazy reporting {@code isStored() == true} with an id that never reached
 * disk — making it legally clearable and a later successful store would persist the phantom id.
 * The registered rollback hook must {@code $unlink} the Lazy on terminal commit failure.
 */
public class LazyUnlinkRollbackTest
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
	void failedCommitUnlinksFreshlyLinkedLazy()
	{
		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setReferenceValidationPolicy(StorageReferenceValidationPolicy.FAIL)
					.createConfiguration()
			)
			.start();

		// a ghost reference guarantees the commit is rejected...
		final long  fakeOid = DanglingRefTestUtil.FAKE_OID_BASE + 80;
		final Child ghost   = new Child("never stored");
		this.storage.persistenceManager().objectRegistry().registerObject(fakeOid, ghost);

		// ...while the same commit freshly links a Lazy (unset -> set transition during serialization).
		final Lazy<Payload> lazy   = Lazy.Reference(new Payload("payload"));
		final Holder        holder = new Holder(lazy, ghost);

		assertFalse(lazy.isStored(), "precondition: fresh Lazy must be unstored");

		final RuntimeException thrown = assertThrows(
			RuntimeException.class,
			() -> this.storage.store(holder)
		);
		assertNotNull(
			DanglingRefTestUtil.findInCauseChain(thrown, StorageExceptionConsistencyDanglingReference.class),
			"cause chain must contain a StorageExceptionConsistencyDanglingReference, but was: " + thrown
		);

		// the rollback hook must have unlinked the Lazy: it is unstored again...
		assertFalse(lazy.isStored(), "failed commit must roll back the Lazy link");
		// ...and therefore not clearable (this call would silently succeed on a stale link).
		assertThrows(IllegalStateException.class, lazy::clear,
			"an unstored Lazy may not be clearable");

		// a subsequent correct store (ghost included in the same commit) succeeds and re-links.
		final Storer storer = this.storage.createStorer();
		storer.storeAll(holder, ghost);
		assertDoesNotThrow(storer::commit);
		assertTrue(lazy.isStored(), "successful commit must link the Lazy again");

		// restart: graph intact, Lazy loadable.
		this.storage.setRoot(holder);
		this.storage.storeRoot();
		this.storage.shutdown();

		this.storage = EmbeddedStorage.start(this.tempDir);
		final Holder reloaded = (Holder)this.storage.root();
		assertNotNull(reloaded);
		assertNotNull(reloaded.lazy.get(), "Lazy referent must be loadable after restart");
		assertEquals("payload", reloaded.lazy.get().data);
		assertEquals("never stored", reloaded.ghost.data);
	}


	///////////////////////////////////////////////////////////////////////////
	// data types //
	///////////////

	public static class Holder
	{
		public Lazy<Payload> lazy ;
		public Child         ghost;

		public Holder(final Lazy<Payload> lazy, final Child ghost)
		{
			super();
			this.lazy  = lazy ;
			this.ghost = ghost;
		}
	}

	public static class Payload
	{
		public String data;

		public Payload(final String data)
		{
			super();
			this.data = data;
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
