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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.store.gigamap.exceptions.UniqueConstraintViolationExceptionBitmap;
import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression coverage for internal issue #132: {@code GigaMap.reindex()} used to drop every index' data
 * before re-adding entity by entity, and the re-add fans out to all indices of the group. An
 * {@link org.eclipse.store.gigamap.types.Indexer} throwing for one entity therefore truncated the
 * <b>whole group</b> - healthy sibling indices included - to the entities processed before the throw.
 * The state before the failed recovery was stale but complete; afterwards it was silently partial, and
 * one ordinary mutation plus {@code store()} made the loss durable.
 * <p>
 * These tests pin the fixed behavior: each index is rebuilt into a replacement built aside and swapped in
 * only once complete, so a throwing indexer costs only its own index' rebuild. That index is left exactly
 * as it was - as stale as before the call, but complete - every other index is rebuilt, and none is ever a
 * prefix of the entities.
 * <p>
 * A unique-constraint violation is deliberately <b>not</b> such a failure and still completes the rebuild;
 * see {@link GigaMap121Test}, whose contract these tests must not disturb.
 */
public class GigaMap132Test
{
	/** static, so it is not part of the persisted indexer state */
	static final AtomicBoolean ARMED = new AtomicBoolean();

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

	static final IndexerString<Rec> HEALTHY = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "healthy";
		}

		@Override
		protected String getString(final Rec entity)
		{
			return entity.key;
		}
	};

	/** throws for "e3", but only while armed - so the map can be populated first */
	static final IndexerString<Rec> BREAKABLE = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "breakable";
		}

		@Override
		protected String getString(final Rec entity)
		{
			if(ARMED.get() && "e3".equals(entity.key))
			{
				throw new IllegalStateException("indexer failure on " + entity.key);
			}

			return entity.key;
		}
	};

	/** a second breakable index, throwing for a different entity */
	static final IndexerString<Rec> BREAKABLE_2 = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "breakable2";
		}

		@Override
		protected String getString(final Rec entity)
		{
			if(ARMED.get() && "e1".equals(entity.key))
			{
				throw new IllegalStateException("indexer failure on " + entity.key);
			}

			return entity.key;
		}
	};

	static final BinaryIndexerString<Rec> UNIQUE_KEY = new BinaryIndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "unique";
		}

		@Override
		protected String getString(final Rec entity)
		{
			if(ARMED.get() && "e3".equals(entity.key))
			{
				throw new IllegalStateException("indexer failure on " + entity.key);
			}

			return entity.key;
		}
	};

	@BeforeEach
	void disarm()
	{
		ARMED.set(false);
	}

	private static GigaMap<Rec> populatedMap()
	{
		final GigaMap<Rec> map = GigaMap.New();
		map.index().bitmap().add(HEALTHY);
		map.index().bitmap().add(BREAKABLE);
		for(int i = 0; i < 5; i++)
		{
			map.add(new Rec("e" + i));
		}

		return map;
	}

	private static long findable(final GigaMap<Rec> map, final IndexerString<Rec> indexer)
	{
		long found = 0;
		for(int i = 0; i < 5; i++)
		{
			found += map.query(indexer.is("e" + i)).count();
		}

		return found;
	}

	/** The issue's RED reproducer: the healthy sibling must not lose entries to another index' failure. */
	@Test
	@Timeout(120)
	void failedReindexKeepsHealthySiblingIndexComplete()
	{
		final GigaMap<Rec> map = populatedMap();
		assertEquals(5, findable(map, HEALTHY), "precondition");

		ARMED.set(true);
		assertThrows(RuntimeException.class, map::reindex, "the failing rebuild must surface");

		assertEquals(5, findable(map, HEALTHY),
			"the healthy index lost entries to an unrelated index' failure");
	}

	/** The failing index keeps its previous content - stale, but complete, never a prefix. */
	@Test
	@Timeout(120)
	void failedReindexKeepsTheThrowingIndexAtItsPreviousContent()
	{
		final GigaMap<Rec> map = populatedMap();
		assertEquals(5, findable(map, BREAKABLE), "precondition");

		ARMED.set(true);
		assertThrows(RuntimeException.class, map::reindex);

		ARMED.set(false); // query with working logic; the index data is what matters
		assertEquals(5, findable(map, BREAKABLE),
			"the failing index was truncated instead of being left as it was");
	}

	/** The durability probe from the issue: continued work plus store() must not persist a loss. */
	@Test
	@Timeout(120)
	void continuedWorkAfterFailedReindexPersistsNothingBroken(@TempDir final Path dir)
	{
		final GigaMap<Rec> map = populatedMap();
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir))
		{
			map.store();

			ARMED.set(true);
			assertThrows(RuntimeException.class, map::reindex);
			ARMED.set(false);

			// one ordinary successful mutation marks the parent flags through the normal path
			map.add(new Rec("e5"));
			map.store();
		}

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Rec> loaded = (GigaMap<Rec>)storage.root();

			assertEquals(6, loaded.size());
			assertEquals(5, findable(loaded, HEALTHY), "a truncation was made durable");
			assertEquals(5, findable(loaded, BREAKABLE), "a truncation was made durable");
			assertEquals(1, loaded.query(HEALTHY.is("e5")).count());
		}
	}

	/** The documented remedy: re-run reindex() once the cause is fixed. */
	@Test
	@Timeout(120)
	void reindexAfterFixingTheIndexerHeals()
	{
		final GigaMap<Rec> map = populatedMap();

		ARMED.set(true);
		assertThrows(RuntimeException.class, map::reindex);

		ARMED.set(false);
		map.reindex();

		assertEquals(5, findable(map, HEALTHY));
		assertEquals(5, findable(map, BREAKABLE));
	}

	/** Best-effort across the indices: every index is attempted, the first failure is the one thrown. */
	@Test
	@Timeout(120)
	void failedReindexAttemptsEveryIndexAndSuppressesFurtherFailures()
	{
		final GigaMap<Rec> map = populatedMap();
		map.index().bitmap().add(BREAKABLE_2);

		ARMED.set(true);
		final RuntimeException e = assertThrows(RuntimeException.class, map::reindex);
		ARMED.set(false);

		assertEquals(1, e.getSuppressed().length, "the second failure must be attached, not swallowed");
		assertEquals(5, findable(map, HEALTHY), "the healthy index must still have been rebuilt");
		assertEquals(5, findable(map, BREAKABLE), "both failing indices keep their previous content");
		assertEquals(5, findable(map, BREAKABLE_2), "both failing indices keep their previous content");
	}

	/** A failing index that backs a unique constraint keeps that constraint enforced. */
	@Test
	@Timeout(120)
	void failedReindexOfAUniqueIndexKeepsTheConstraintEnforced()
	{
		final GigaMap<Rec> map = GigaMap.New();
		map.index().bitmap().add(HEALTHY);
		map.index().bitmap().addUniqueConstraint(UNIQUE_KEY);
		for(int i = 0; i < 5; i++)
		{
			map.add(new Rec("e" + i));
		}

		ARMED.set(true);
		assertThrows(RuntimeException.class, map::reindex);
		ARMED.set(false);

		assertEquals(1, map.index().bitmap().uniqueConstraints().size(), "unique membership lost");
		assertThrows(RuntimeException.class, () -> map.add(new Rec("e0")), "constraint no longer enforced");
		assertEquals(5, findable(map, HEALTHY), "the healthy index must still have been rebuilt");
	}

	/**
	 * A unique-constraint violation is not an indexer failure: the rebuild completes and the replacement is
	 * swapped in, so the indices describe the entities as they actually are. Guards the #121 contract from
	 * inside the new code path; see {@link GigaMap121Test} for the full evolution-driven case.
	 */
	@Test
	@Timeout(120)
	void uniqueViolationStillCompletesTheRebuild()
	{
		final GigaMap<Rec> map = GigaMap.New();
		map.index().bitmap().addUniqueConstraint(UNIQUE_KEY);
		final Rec a = new Rec("a");
		final Rec b = new Rec("b");
		map.add(a);
		map.add(b);

		// direct mutation: the index still answers for "b", the entity is now a duplicate "a"
		b.key = "a";

		final UniqueConstraintViolationExceptionBitmap e = assertThrows(
			UniqueConstraintViolationExceptionBitmap.class,
			map::reindex
		);
		assertEquals("unique", e.getViolatedIndex().name());
		assertTrue(e.getMessage().contains("reindex()"), "the message must name the operation");

		// the rebuild was completed: the stale key is gone and both entities answer under "a"
		assertEquals(0, map.query(UNIQUE_KEY.is("b")).count(), "the stale key must be gone");
		assertEquals(2, map.query(UNIQUE_KEY.is("a")).count(), "the rebuild must describe the actual state");
		assertEquals(2, map.size(), "no entity may be dropped");
	}

	/** The swap must carry identity-index membership over to the rebuilt index. */
	@Test
	@Timeout(120)
	void reindexPreservesIdentityIndex()
	{
		final GigaMap<Rec> map = GigaMap.New();
		map.index().bitmap().add(HEALTHY);
		map.index().bitmap().setIdentityIndices(org.eclipse.serializer.util.X.Enum(HEALTHY));
		final Rec a = new Rec("e0");
		map.add(a);
		map.add(new Rec("e1"));

		map.reindex();

		assertEquals(1, map.index().bitmap().identityIndices().size(), "identity membership lost");
		assertSame(
			map.index().bitmap().get(String.class, "healthy"),
			map.index().bitmap().identityIndices().get(),
			"the identity set must point at the rebuilt index"
		);
		// identity lookup still resolves the entity
		map.update(a, rec -> rec.key = "e9");
		assertEquals(1, map.query(HEALTHY.is("e9")).count());
	}
}
