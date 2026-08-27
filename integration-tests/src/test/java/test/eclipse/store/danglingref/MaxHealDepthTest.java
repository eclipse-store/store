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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistencyDanglingReference;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageReferenceValidationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Recursion limit of the healing storer ({@code BinaryStorer.MAX_HEAL_DEPTH} = 4). In a chain of
 * registry-known but never-stored ghosts, ghost level {@code k} is healed by a storer at heal
 * depth {@code k}; a storer at the maximum depth gives up on a rejection of its own commit. So a
 * chain of 4 ghost levels is the deepest healable graph, 5 levels must fail terminally with the
 * typed dangling-reference exception - and the storage must remain usable.
 */
@Timeout(60)
public class MaxHealDepthTest
{
	/** Mirrors {@code BinaryStorer.MAX_HEAL_DEPTH} (not visible from here). */
	static final int MAX_HEAL_DEPTH = 4;

	@TempDir
	Path tempDir;

	EmbeddedStorageManager storage;

	final DanglingRefTestUtil.RecordingEventLogger recorder = new DanglingRefTestUtil.RecordingEventLogger();

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

	private void startStorage()
	{
		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setReferenceValidationPolicy(StorageReferenceValidationPolicy.HEAL)
					.createConfiguration()
			)
			.setEventLogger(this.recorder)
			.start();
	}

	/**
	 * Builds parent -> ghost1 -> ... -> ghostN where every ghost is registry-known but never
	 * stored, and returns the ghost chain's fake object ids (index 0 = ghost1).
	 */
	private Node buildGhostChain(final int ghostLevels, final long oidBase, final long[] fakeOids)
	{
		final PersistenceObjectRegistry registry =
			this.storage.persistenceManager().objectRegistry();

		Node next = null;
		for(int level = ghostLevels; level >= 1; level--)
		{
			final Node ghost = new Node("ghost level " + level, next);
			fakeOids[level - 1] = oidBase + level;
			registry.registerObject(fakeOids[level - 1], ghost);
			next = ghost;
		}
		return new Node("parent", next);
	}

	@Test
	void chainAtMaxHealDepthIsHealed()
	{
		this.startStorage();

		final long[] fakeOids = new long[MAX_HEAL_DEPTH];
		final Node   parent   = buildGhostChain(MAX_HEAL_DEPTH, DanglingRefTestUtil.FAKE_OID_BASE + 200, fakeOids);

		assertDoesNotThrow(() -> this.storage.store(parent),
			"a ghost chain of exactly MAX_HEAL_DEPTH levels must still be healable");

		// one rejection/heal round per ghost level, in chain order (single channel).
		DanglingRefTestUtil.assertRejectionsRecorded(this.recorder);
		assertEquals(MAX_HEAL_DEPTH, this.recorder.eventCount(),
			"exactly one heal round per ghost level expected");
		for(int level = 0; level < MAX_HEAL_DEPTH; level++)
		{
			assertArrayEquals(new long[]{fakeOids[level]}, this.recorder.reportedObjectIds.get(level),
				"heal round " + (level + 1) + " must reject exactly ghost level " + (level + 1));
		}

		// restart: the whole chain must be persisted intact.
		this.storage.setRoot(parent);
		this.storage.storeRoot();
		this.storage.shutdown();

		this.storage = EmbeddedStorage.start(this.tempDir);
		Node node = (Node)this.storage.root();
		assertNotNull(node);
		for(int level = 1; level <= MAX_HEAL_DEPTH; level++)
		{
			node = node.next;
			assertNotNull(node, "ghost level " + level + " must be loadable after restart");
			assertEquals("ghost level " + level, node.data);
		}
	}

	@Test
	void chainBeyondMaxHealDepthFailsTerminally()
	{
		this.startStorage();

		final int    ghostLevels = MAX_HEAL_DEPTH + 1;
		final long[] fakeOids    = new long[ghostLevels];
		final Node   parent      = buildGhostChain(ghostLevels, DanglingRefTestUtil.FAKE_OID_BASE + 210, fakeOids);

		final RuntimeException thrown = assertThrows(
			RuntimeException.class,
			() -> this.storage.store(parent),
			"a ghost chain exceeding MAX_HEAL_DEPTH must fail"
		);
		final StorageExceptionConsistencyDanglingReference danglingReference =
			DanglingRefTestUtil.findInCauseChain(thrown, StorageExceptionConsistencyDanglingReference.class);
		assertNotNull(
			danglingReference,
			"cause chain must contain a StorageExceptionConsistencyDanglingReference, but was: " + thrown
		);
		assertArrayEquals(
			new long[]{fakeOids[ghostLevels - 1]},
			danglingReference.missingObjectIds(),
			"the surfaced rejection must be the deepest ghost's - where healing gave up"
		);

		// the storage itself must remain usable.
		assertDoesNotThrow(() -> this.storage.store(new Node("independent data", null)));
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
