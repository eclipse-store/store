package test.eclipse.store.recovery;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;

import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * A change spanning two {@link GigaMap}s, registered with one {@link Storer} and committed once, must
 * survive a power loss as a unit: either both halves are there or neither is. This is the property
 * that makes a single commit an alternative to an application-level write-ahead log for a model whose
 * logical writes span more than one map.
 * <p>
 * The power loss is staged the way the other tests here stage it - commit cleanly, then cut the log
 * back to the state a crash before the commit's log entry reached the medium would leave, and restart.
 * <p>
 * Each case is paired with a positive control that runs the same sequence without the surgery.
 * Without it, a commit that persisted nothing at all would satisfy the "neither half survived"
 * assertion just as well as correct all-or-nothing behaviour.
 */
@Timeout(120)
class SharedStorerCommitAtomicityTest
{
	private static final int MAX_FILE_SIZE = 50_000_000;

	@TempDir
	Path directory;

	private EmbeddedStorageManager storage ;
	private EmbeddedStorageManager reloaded;

	@AfterEach
	void shutdown()
	{
		for(final EmbeddedStorageManager manager : new EmbeddedStorageManager[]{this.reloaded, this.storage})
		{
			if(manager != null && manager.isRunning())
			{
				try
				{
					manager.shutdown();
				}
				catch(final RuntimeException ignored)
				{
					// best effort
				}
			}
		}
	}

	@Test
	void aCommitSpanningTwoMapsIsLostAsAUnit()
	{
		this.stageBaseline();
		this.stageSpanningCommit();
		RecoverySimulation.cutLastStore(this.directory, 0);

		final Root root = this.reload();
		assertEquals(1L, root.left .size(), "the left half of the lost commit must be gone" );
		assertEquals(1L, root.right.size(), "the right half of the lost commit must be gone");
		assertEquals(1L, root.left .query(LEFT_NAME , "base-l").count(), "left index back at the baseline" );
		assertEquals(1L, root.right.query(RIGHT_NAME, "base-r").count(), "right index back at the baseline");
		assertEquals(0L, root.left .query(LEFT_NAME , "span-l").count(), "no trace of the lost commit");
		assertEquals(0L, root.right.query(RIGHT_NAME, "span-r").count(), "no trace of the lost commit");
	}

	/** Positive control for {@link #aCommitSpanningTwoMapsIsLostAsAUnit()}: no surgery, both halves survive. */
	@Test
	void aCommitSpanningTwoMapsSurvivesWhenItsLogEntryDoes()
	{
		this.stageBaseline();
		this.stageSpanningCommit();

		final Root root = this.reload();
		assertEquals(2L, root.left .size(), "the left half must survive" );
		assertEquals(2L, root.right.size(), "the right half must survive");
		assertEquals(1L, root.left .query(LEFT_NAME , "span-l").count(), "left index carries the commit" );
		assertEquals(1L, root.right.query(RIGHT_NAME, "span-r").count(), "right index carries the commit");
	}

	/**
	 * The mixed shape: a removal in one map and an addition in the other. A partial recovery here would
	 * be visible as the removal surviving without the addition, or the reverse - a torn logical change
	 * rather than merely a lost one.
	 */
	@Test
	void aRemovalAndAnAdditionAreLostTogether()
	{
		this.stageBaseline();

		this.storage = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 1, MAX_FILE_SIZE));
		final Root root = (Root)this.storage.root();
		root.left.removeById(0L);
		root.right.add(new Item("span-r"));

		final Storer storer = this.storage.createStorer();
		root.left .store(storer);
		root.right.store(storer);
		storer.commit();
		this.storage.shutdown();
		this.storage = null;

		RecoverySimulation.cutLastStore(this.directory, 0);

		final Root recovered = this.reload();
		assertEquals(1L, recovered.left .size(), "the removal must be rolled back with its commit");
		assertEquals(1L, recovered.right.size(), "the addition must be rolled back with its commit");
		assertNotNull(recovered.left.get(0L), "the removed entity is back");
		assertEquals(1L, recovered.left .query(LEFT_NAME , "base-l").count(), "left index back at the baseline");
		assertEquals(0L, recovered.right.query(RIGHT_NAME, "span-r").count(), "the addition left no index entry");
	}


	private void stageBaseline()
	{
		this.storage = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 1, MAX_FILE_SIZE));
		final Root root = new Root();
		this.storage.setRoot(root);
		this.storage.storeRoot();

		root.left .add(new Item("base-l"));
		root.right.add(new Item("base-r"));

		final Storer storer = this.storage.createStorer();
		root.left .store(storer);
		root.right.store(storer);
		storer.commit();

		this.storage.shutdown();
		this.storage = null;
	}

	private void stageSpanningCommit()
	{
		this.storage = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 1, MAX_FILE_SIZE));
		final Root root = (Root)this.storage.root();

		root.left .add(new Item("span-l"));
		root.right.add(new Item("span-r"));

		final Storer storer = this.storage.createStorer();
		root.left .store(storer);
		root.right.store(storer);
		storer.commit();

		this.storage.shutdown();
		this.storage = null;
	}

	private Root reload()
	{
		this.reloaded = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 1, MAX_FILE_SIZE));
		final Root root = (Root)this.reloaded.root();
		assertNotNull(root, "root gone after recovery");
		return root;
	}


	public static class Item
	{
		String name;

		Item(final String name)
		{
			this.name = name;
		}
	}

	public static class NameIndexer extends IndexerString.Abstract<Item>
	{
		@Override
		protected String getString(final Item entity)
		{
			return entity.name;
		}
	}

	static final NameIndexer LEFT_NAME  = new NameIndexer();
	static final NameIndexer RIGHT_NAME = new NameIndexer();

	public static class Root
	{
		final GigaMap<Item> left  = GigaMap.<Item>Builder().withBitmapIndex(LEFT_NAME ).build();
		final GigaMap<Item> right = GigaMap.<Item>Builder().withBitmapIndex(RIGHT_NAME).build();
	}
}
