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

import java.lang.ref.WeakReference;
import java.nio.file.Path;

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pinning of lazily-skipped instances (internal issue #73): when the lazy storer skips a
 * registry-known instance, the skip decision is made at {@code store()} time but only becomes
 * binding at {@code commit()} time. Without a strong reference, the instance could be collected in
 * between, its registry entry reaped, and the storage GC would legitimately delete the entity — the
 * commit would then persist a dangling reference. The storer must pin skipped instances for the
 * lifetime of the store operation.
 * <p>
 * Requires the serializer-side pin ({@code registerSkippedOptional} / pin items in
 * {@code BinaryStorer}); fails without it.
 */
public class SkipPinningTest
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
	@Timeout(120)
	void skippedInstanceSurvivesStoreCommitWindow() throws Exception
	{
		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.createConfiguration()
			)
			.start();

		final PersistenceObjectRegistry registry =
			this.storage.persistenceManager().objectRegistry();

		// X persisted, then unlinked from the root graph (unlink committed): on disk it now
		// survives only via the GC's registry safety net, i.e. as long as its instance is alive.
		final DataRoot root = new DataRoot();
		this.storage.setRoot(root);

		final long                     payloadOid  ;
		final WeakReference<Payload>   payloadProbe;
		final Storer                   storer      ;
		{
			Payload x = new Payload("pinned payload");
			root.payload = x;
			this.storage.storeRoot();
			payloadOid = registry.lookupObjectId(x);

			root.payload = null;
			this.storage.storeRoot();

			// long-lived storer: skip decision at store() time, binding only at commit() time.
			storer = this.storage.createStorer();
			final Parent parent = new Parent(x);
			storer.store(parent); // X is registry-known -> skipped, its oid written into the chunk

			/*
			 * Drop EVERY application-side strong path to X, including parent's field: the storer
			 * holds parent itself strongly (regular store item), so a payload still referenced
			 * through parent would keep X alive trivially and the pin assertion below would be
			 * vacuous. parent's serialized state was captured by store() - clearing the field
			 * afterwards does not alter what gets committed.
			 */
			parent.payload = null;

			payloadProbe = new WeakReference<>(x);
			x = null; // drop the last application reference inside this block
		}

		// try hard to collect X: with the storer's pin in place, it MUST survive.
		for(int i = 0; i < 5; i++)
		{
			System.gc();
			Thread.sleep(50);
		}
		assertNotNull(payloadProbe.get(),
			"the storer must pin the skipped instance until commit - it was collected");

		// the t1..t4 window of internal#73: reap attempt + full storage GC. With the pin alive,
		// the registry entry stays and the safety net keeps X's entity on disk.
		registry.cleanUp();
		this.storage.issueFullGarbageCollection();
		Thread.sleep(200);

		// the commit's skip decision must still be valid: X's entity still exists, the committed
		// reference is not dangling.
		assertDoesNotThrow(storer::commit,
			"committing the store whose skipped referent must have been pinned failed");
		assertEquals(payloadOid, registry.lookupObjectId(payloadProbe.get()),
			"the pinned instance must keep its object id");

		// re-attach for reachability and restart: the graph must be intact.
		root.payload = payloadProbe.get();
		this.storage.storeRoot();
		this.storage.shutdown();

		this.storage = EmbeddedStorage.start(this.tempDir);
		final DataRoot reloaded = (DataRoot)this.storage.root();
		assertNotNull(reloaded);
		assertNotNull(reloaded.payload, "payload must have survived the store-commit window");
		assertEquals("pinned payload", reloaded.payload.data);
	}


	///////////////////////////////////////////////////////////////////////////
	// data types //
	///////////////

	public static class DataRoot
	{
		public Payload payload;
	}

	public static class Parent
	{
		public Payload payload;

		public Parent(final Payload payload)
		{
			super();
			this.payload = payload;
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
}
