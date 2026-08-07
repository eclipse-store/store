package org.eclipse.store.gigamap.issues;

/*-
 * #%L
 * EclipseStore GigaMap
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression coverage for internal issue #140: the back-fill that populates a bitmap index from the
 * entities a {@link GigaMap} already holds used to run <i>after</i> the index was registered. An
 * {@link org.eclipse.store.gigamap.types.Indexer} throwing for one entity therefore left a partially
 * filled index as part of the map's state - silently answering queries with a torso of the data, and
 * persisted by the next {@code store()}. For a non-unique
 * {@link BitmapIndices#update(org.eclipse.store.gigamap.types.Indexer)} it was worse still: the old index
 * had already been dropped, so the map lost the working index and gained a broken one.
 * <p>
 * The unique branch of {@code update} was the one path that already built its replacement standalone
 * before dropping the existing index ("atomic failure"). These tests pin the fixed behavior: every
 * registering path builds the index completely before registering it, so a failure leaves the group
 * exactly as it was, and {@code addAll} is all-or-nothing across its batch.
 */
public class GigaMap140Test
{
	static class Rec
	{
		String key;

		Rec()
		{
			// required for deserialization
			super();
		}

		Rec(final String key)
		{
			super();
			this.key = key;
		}
	}

	/** The established logic: index the key as it is. */
	static final class KeyIndexer extends IndexerString.Abstract<Rec>
	{
		@Override
		public String name()
		{
			return "key";
		}

		@Override
		protected String getString(final Rec entity)
		{
			return entity.key;
		}
	}

	/** Binary variant of {@link KeyIndexer}, suitable as a unique constraint. */
	static final class BinaryKeyIndexer extends BinaryIndexerString.Abstract<Rec>
	{
		@Override
		public String name()
		{
			return "binkey";
		}

		@Override
		protected String getString(final Rec entity)
		{
			return entity.key;
		}
	}

	/**
	 * New logic under a configurable name: upper-cases the key, but throws for one designated entity -
	 * the realistic case of an indexer that cannot handle a value the previous logic never read.
	 */
	static final class PoisonedIndexer extends IndexerString.Abstract<Rec>
	{
		final String name  ;
		final String poison;

		PoisonedIndexer(final String name, final String poison)
		{
			super();
			this.name   = name  ;
			this.poison = poison;
		}

		@Override
		public String name()
		{
			return this.name;
		}

		@Override
		protected String getString(final Rec entity)
		{
			if(entity.key.equals(this.poison))
			{
				throw new IllegalStateException("indexer failure on " + entity.key);
			}

			return entity.key.toUpperCase();
		}
	}

	/** Binary counterpart of {@link PoisonedIndexer}, so the unique branch can be driven the same way. */
	static final class PoisonedBinaryIndexer extends BinaryIndexerString.Abstract<Rec>
	{
		final String poison;

		PoisonedBinaryIndexer(final String poison)
		{
			super();
			this.poison = poison;
		}

		@Override
		public String name()
		{
			return "binkey";
		}

		@Override
		protected String getString(final Rec entity)
		{
			if(entity.key.equals(this.poison))
			{
				throw new IllegalStateException("indexer failure on " + entity.key);
			}

			return entity.key.toUpperCase();
		}
	}

	private static GigaMap<Rec> populatedMap(final KeyIndexer keyIndexer)
	{
		final GigaMap<Rec> map = GigaMap.New();
		map.index().bitmap().add(keyIndexer);
		map.add(new Rec("a"));
		map.add(new Rec("b"));
		map.add(new Rec("c"));

		return map;
	}

	@Test
	@Timeout(120)
	void update_nonUniqueIndexerThrowsDuringRebuild_keepsOldIndex()
	{
		final KeyIndexer   keyIndexer = new KeyIndexer();
		final GigaMap<Rec> map        = populatedMap(keyIndexer);

		assertThrows(
			IllegalStateException.class,
			() -> map.index().bitmap().update(new PoisonedIndexer("key", "c")),
			"the failing back-fill must surface"
		);

		// the old index is still the registered one: all three entities are findable under their
		// original keys, and none of the partially derived new keys resolves.
		assertEquals(1, map.query(keyIndexer.is("a")).count(), "old index dropped");
		assertEquals(1, map.query(keyIndexer.is("b")).count(), "old index dropped");
		assertEquals(1, map.query(keyIndexer.is("c")).count(), "old index dropped");
		assertEquals(0, map.query(keyIndexer.is("A")).count(), "partial new index registered");
		assertEquals(0, map.query(keyIndexer.is("B")).count(), "partial new index registered");
	}

	@Test
	@Timeout(120)
	void update_uniqueIndexerThrowsDuringRebuild_keepsOldIndexAndConstraint()
	{
		final GigaMap<Rec>      map        = GigaMap.New();
		final BitmapIndices<Rec> indices   = map.index().bitmap();
		final BinaryKeyIndexer  keyIndexer = new BinaryKeyIndexer();
		indices.addUniqueConstraint(keyIndexer);
		map.add(new Rec("a"));
		map.add(new Rec("b"));
		map.add(new Rec("c"));

		assertThrows(
			IllegalStateException.class,
			() -> indices.update(new PoisonedBinaryIndexer("c")),
			"the failing back-fill must surface"
		);

		assertEquals(1, map.query(keyIndexer.is("a")).count(), "old index dropped");
		assertEquals(0, map.query(keyIndexer.is("A")).count(), "partial new index registered");
		assertEquals(1, indices.uniqueConstraints().size(), "unique membership lost");
		assertThrows(RuntimeException.class, () -> map.add(new Rec("a")), "constraint no longer enforced");
	}

	@Test
	@Timeout(120)
	void add_indexerThrowsDuringBackfill_registersNothing()
	{
		final KeyIndexer   keyIndexer = new KeyIndexer();
		final GigaMap<Rec> map        = populatedMap(keyIndexer);

		final PoisonedIndexer poisoned = new PoisonedIndexer("key2", "c");
		assertThrows(
			IllegalStateException.class,
			() -> map.index().bitmap().add(poisoned),
			"the failing back-fill must surface"
		);

		assertNull(map.index().bitmap().get("key2"), "partial index left registered");
		// no torso answers queries: the name is not resolvable at all
		assertThrows(
			IllegalArgumentException.class,
			() -> map.query(poisoned.is("A")).count(),
			"partial index answers queries"
		);

		// the healthy sibling is untouched, and the map is still fully usable: an ordinary update must
		// reach every registered index (a stale index cache would silently skip one).
		assertEquals(1, map.query(keyIndexer.is("a")).count());
		final Rec first = map.query(keyIndexer.is("a")).findFirst().get();
		map.update(first, rec -> rec.key = "z");
		assertEquals(0, map.query(keyIndexer.is("a")).count(), "update did not reach the index");
		assertEquals(1, map.query(keyIndexer.is("z")).count(), "update did not reach the index");
	}

	@Test
	@Timeout(120)
	void addAll_indexerThrowsMidBatch_registersNoIndexOfTheBatch()
	{
		final KeyIndexer   keyIndexer = new KeyIndexer();
		final GigaMap<Rec> map        = populatedMap(keyIndexer);

		assertThrows(
			IllegalStateException.class,
			() -> map.index().bitmap().addAll(
				new PoisonedIndexer("healthy", "no-such-key"),
				new PoisonedIndexer("poisoned", "c")
			),
			"the failing back-fill must surface"
		);

		assertNull(map.index().bitmap().get("healthy") , "batch applied in part");
		assertNull(map.index().bitmap().get("poisoned"), "partial index left registered");
	}

	@Test
	@Timeout(120)
	void retryWithFixedIndexerSucceedsAfterFailedRegistration()
	{
		final KeyIndexer   keyIndexer = new KeyIndexer();
		final GigaMap<Rec> map        = populatedMap(keyIndexer);

		assertThrows(
			IllegalStateException.class,
			() -> map.index().bitmap().add(new PoisonedIndexer("key2", "c"))
		);

		// the name stayed free and the corrected indexer back-fills all three entities.
		final PoisonedIndexer fixed = new PoisonedIndexer("key2", "no-such-key");
		map.index().bitmap().add(fixed);
		assertNotNull(map.index().bitmap().get("key2"));
		assertEquals(1, map.query(fixed.is("A")).count());
		assertEquals(1, map.query(fixed.is("B")).count());
		assertEquals(1, map.query(fixed.is("C")).count());

		// the same for a failed update, which must leave the name occupied by the old index
		assertThrows(
			IllegalStateException.class,
			() -> map.index().bitmap().update(new PoisonedIndexer("key", "c"))
		);
		map.index().bitmap().update(new PoisonedIndexer("key", "no-such-key"));
		assertEquals(1, map.query(keyIndexer.is("A")).count(), "corrected update did not rebuild");
		assertEquals(0, map.query(keyIndexer.is("a")).count(), "corrected update did not rebuild");
	}

	@Test
	@Timeout(120)
	void failedRegistrationIsNotPersisted(@TempDir final Path storageDir)
	{
		final KeyIndexer keyIndexer = new KeyIndexer();

		final GigaMap<Rec> map = populatedMap(keyIndexer);
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(map, storageDir))
		{
			map.store();

			assertThrows(
				IllegalStateException.class,
				() -> map.index().bitmap().add(new PoisonedIndexer("key2", "c"))
			);

			// an ordinary successful mutation marks the parent flags through the normal path, so a
			// half-registered index would be reached by this store() and become durable.
			map.add(new Rec("d"));
			map.store();
		}

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Rec> loaded = (GigaMap<Rec>)storage.root();

			assertNull(loaded.index().bitmap().get("key2"), "failed registration was persisted");
			assertEquals(4, loaded.size());
			assertEquals(1, loaded.query(keyIndexer.is("a")).count(), "surviving index incomplete");
			assertEquals(1, loaded.query(keyIndexer.is("d")).count(), "surviving index incomplete");
		}
	}

	/**
	 * Pinned, not fixed: {@code removeIndex} + {@code add} is not a redefinition. It drops the unique
	 * constraint along with the index, and re-adding under the same name does not restore it -
	 * {@link BitmapIndices#update(org.eclipse.store.gigamap.types.Indexer)} is the supported way, and
	 * {@code removeIndex}'s javadoc says so.
	 */
	@Test
	@Timeout(120)
	void manualRedefineDropsUniqueConstraintPinned()
	{
		final GigaMap<Rec>       map     = GigaMap.New();
		final BitmapIndices<Rec> indices = map.index().bitmap();
		indices.addUniqueConstraint(new BinaryKeyIndexer());
		map.add(new Rec("a"));
		assertThrows(RuntimeException.class, () -> map.add(new Rec("a")), "constraint not active");

		indices.removeIndex("binkey");
		indices.add(new BinaryKeyIndexer());

		map.add(new Rec("a")); // accepted: the constraint membership is gone
		assertEquals(2, map.query(new BinaryKeyIndexer().is("a")).count());
		assertTrue(
			indices.uniqueConstraints() == null || indices.uniqueConstraints().isEmpty(),
			"the constraint is still registered"
		);
	}
}
