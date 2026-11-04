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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageReferenceValidationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Multi-channel healing: with dangling references on every channel, each rejection surfaces only
 * the first failing channel's missing ids, so healing proceeds one channel per round — this test
 * exercises the multi-round retry (attempt cap = channel count + 1) and, critically, the buffer
 * rewind between rounds (peer channels that already wrote are rolled back and must rewrite
 * byte-identical data on retry).
 */
@Timeout(60)
public class MultiChannelHealTest
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
	void danglingReferencesOnAllChannelsAreHealed()
	{
		final DanglingRefTestUtil.RecordingEventLogger recorder = new DanglingRefTestUtil.RecordingEventLogger();
		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setChannelCountProvider(Storage.ChannelCountProvider(CHANNEL_COUNT))
					.setReferenceValidationPolicy(StorageReferenceValidationPolicy.HEAL)
					.createConfiguration()
			)
			.setEventLogger(recorder)
			.start();

		final PersistenceObjectRegistry registry =
			this.storage.persistenceManager().objectRegistry();

		// one ghost per channel: consecutive ids cover all (oid & 3) residues.
		final List<Child> children = new ArrayList<>();
		final long[]      fakeOids = new long[CHANNEL_COUNT];
		for(int i = 0; i < CHANNEL_COUNT; i++)
		{
			final Child child = new Child("ghost #" + i);
			fakeOids[i] = DanglingRefTestUtil.FAKE_OID_BASE + 100 + i;
			registry.registerObject(fakeOids[i], child);
			children.add(child);
		}

		final Parent parent = new Parent(children);

		assertDoesNotThrow(() -> this.storage.store(parent), "multi-round healing must succeed");

		// precondition: healing coverage requires actual rejections; a ghost can only be healed
		// after its own channel rejected it, so every channel must have reported at least once.
		DanglingRefTestUtil.assertRejectionsRecorded(recorder);
		assertEquals(CHANNEL_COUNT, recorder.distinctReportingChannels(),
			"every channel must have rejected (and thus healed) its own ghost");
		/*
		 * Pin the multi-ROUND retry: healing repairs only the surfaced (first failing) channel's
		 * ids per round, while every channel still holding a ghost re-rejects on each retry -
		 * 4+3+2+1 events for 4 channels. A validation aggregating all channels into one rejection
		 * would heal everything in a single round (exactly CHANNEL_COUNT events) and silently
		 * stop covering the round loop and the buffer rewind between rounds.
		 */
		assertEquals(CHANNEL_COUNT * (CHANNEL_COUNT + 1) / 2, recorder.eventCount(),
			"one heal round per channel expected, with every remaining ghost channel re-rejecting per round");

		for(int i = 0; i < CHANNEL_COUNT; i++)
		{
			assertEquals(fakeOids[i], registry.lookupObjectId(children.get(i)),
				"healed child #" + i + " must keep its object id");
		}

		this.storage.setRoot(parent);
		this.storage.storeRoot();
		this.storage.shutdown();

		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.setChannelCountProvider(Storage.ChannelCountProvider(CHANNEL_COUNT))
					.createConfiguration()
			)
			.start();
		final Parent reloaded = (Parent)this.storage.root();
		assertNotNull(reloaded);
		assertEquals(CHANNEL_COUNT, reloaded.children.size());
		for(int i = 0; i < CHANNEL_COUNT; i++)
		{
			assertEquals("ghost #" + i, reloaded.children.get(i).data,
				"healed child #" + i + " must be intact after restart");
		}
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
