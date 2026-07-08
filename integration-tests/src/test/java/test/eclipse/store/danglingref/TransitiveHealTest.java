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
 * Recursive healing: the healed instance itself references another registry-known but never-stored
 * instance. The compensating store's own commit detects and heals the transitive dangling reference
 * (at healing depth + 1); the whole graph must be persisted intact.
 */
public class TransitiveHealTest
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
	void transitiveDanglingReferencesAreHealedRecursively()
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

		final long fakeChildOid      = DanglingRefTestUtil.FAKE_OID_BASE + 60;
		final long fakeGrandchildOid = DanglingRefTestUtil.FAKE_OID_BASE + 61;

		final Node grandchild = new Node("ghost grandchild", null);
		final Node child      = new Node("ghost child", grandchild);
		registry.registerObject(fakeChildOid,      child     );
		registry.registerObject(fakeGrandchildOid, grandchild);

		final Node parent = new Node("parent", child);

		/*
		 * Storing parent skips child (registry-known). Validation reports fakeChildOid missing;
		 * the healing commit re-stores child - whose serialization skips the registry-known
		 * grandchild, so the healing commit itself is rejected for fakeGrandchildOid and heals
		 * recursively at depth + 1.
		 */
		assertDoesNotThrow(() -> this.storage.store(parent), "transitive healing must succeed");

		// precondition: two heal rounds must have happened - the initial store rejected for the
		// child, then the child's healing commit itself rejected for the grandchild. A single
		// (or zero) rejection means the recursive path was not exercised.
		DanglingRefTestUtil.assertRejectionsRecorded(recorder);
		assertEquals(2, recorder.eventCount(), "exactly two heal rounds expected (child, then grandchild)");
		assertArrayEquals(new long[]{fakeChildOid}, recorder.reportedObjectIds.get(0),
			"round 1 must reject the ghost child");
		assertArrayEquals(new long[]{fakeGrandchildOid}, recorder.reportedObjectIds.get(1),
			"round 2 (the healing commit) must reject the ghost grandchild");

		assertEquals(fakeChildOid,      registry.lookupObjectId(child)     );
		assertEquals(fakeGrandchildOid, registry.lookupObjectId(grandchild));

		this.storage.setRoot(parent);
		this.storage.storeRoot();
		this.storage.shutdown();

		this.storage = EmbeddedStorage.start(this.tempDir);
		final Node reloaded = (Node)this.storage.root();
		assertNotNull(reloaded);
		assertNotNull(reloaded.next, "healed child must be loadable");
		assertEquals("ghost child", reloaded.next.data);
		assertNotNull(reloaded.next.next, "recursively healed grandchild must be loadable");
		assertEquals("ghost grandchild", reloaded.next.next.data);
	}


	///////////////////////////////////////////////////////////////////////////
	// data types //
	///////////////

	public static class Node
	{
		public String data;
		public Node   next;

		public Node(final String data, final Node next)
		{
			super();
			this.data = data;
			this.next = next;
		}
	}
}
