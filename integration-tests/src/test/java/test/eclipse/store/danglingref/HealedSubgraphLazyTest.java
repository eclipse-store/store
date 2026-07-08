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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageReferenceValidationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * A fresh {@code Lazy} inside the HEALED subgraph (serialized by the healing storer, not by the
 * rejected original store): its deferred {@code $link} is routed to the root storer and must fire
 * exactly with the outer commit's success — the store returns with the Lazy properly linked, and
 * the referent is loadable after a restart. (The failure-side counterpart — a terminally failed
 * retry must leave such a Lazy unlinked — lives serializer-side in {@code HealingDeferredLinkTest},
 * since real storage cannot deterministically fail the retry after a successful healing round.)
 */
@Timeout(60)
public class HealedSubgraphLazyTest
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
	void lazyInsideHealedSubgraphIsLinkedWithTheOuterCommit()
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

		// the ghost child's subgraph holds a fresh Lazy - only the HEALING commit serializes it.
		final Lazy<Payload> freshLazy = Lazy.Reference(new Payload("healed lazy payload"));
		final long          fakeOid   = DanglingRefTestUtil.FAKE_OID_BASE + 90;
		final Child         child     = new Child("healed child", freshLazy);
		registry.registerObject(fakeOid, child);

		final Parent parent = new Parent(child);

		assertFalse(freshLazy.isStored(), "precondition: a fresh Lazy must be unstored");

		assertDoesNotThrow(() -> this.storage.store(parent), "heal mode must repair the store transparently");

		// precondition: the healing path must actually have been exercised.
		DanglingRefTestUtil.assertRejectionsRecorded(recorder);

		// the healing storer serialized the Lazy; its transferred $link must have fired with the
		// outer commit's success - as if the store had never been rejected.
		assertTrue(freshLazy.isStored(), "the healed subgraph's Lazy must be linked after the successful store");

		// restart: the whole healed subgraph including the Lazy referent must be intact.
		this.storage.setRoot(parent);
		this.storage.storeRoot();
		this.storage.shutdown();

		this.storage = EmbeddedStorage.start(this.tempDir);
		final Parent reloaded = (Parent)this.storage.root();
		assertNotNull(reloaded);
		assertNotNull(reloaded.child, "the healed child must be loadable after restart");
		assertEquals("healed child", reloaded.child.data);
		assertNotNull(reloaded.child.lazy.get(), "the healed subgraph's Lazy referent must be loadable after restart");
		assertEquals("healed lazy payload", reloaded.child.lazy.get().data);
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
		public String        data;
		public Lazy<Payload> lazy;

		public Child(final String data, final Lazy<Payload> lazy)
		{
			super();
			this.data = data;
			this.lazy = lazy;
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
