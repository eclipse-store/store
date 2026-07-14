package org.eclipse.store.gigamap.indexer.edge;

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

import org.eclipse.store.gigamap.exceptions.ConstraintViolationException;
import org.eclipse.store.gigamap.types.CustomConstraint;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerMultiValue;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the GigaMap consistency guarantees when user code (indexers, update logic,
 * constraints) throws exceptions in the middle of a mutation:
 * <ul>
 *   <li>{@code add}/{@code addAll} roll back completely; ids whose cleanup also failed are burned
 *       instead of being reused.</li>
 *   <li>{@code removeById}/{@code remove} always complete the removal, cleaning the remaining
 *       indices best-effort and rethrowing afterwards.</li>
 *   <li>{@code set}/{@code replace} leave the map observably unchanged.</li>
 *   <li>{@code update}/{@code apply} abort cleanly while the pre-mutation state is being indexed
 *       and extend the documented destructive-removal contract (previously limited to
 *       {@link ConstraintViolationException}) to all exceptions once the logic may have mutated
 *       the entity.</li>
 * </ul>
 */
public class ThrowingIndexerConsistencyTest
{
	// ---------------------------------------------------------------
	// Shared domain and indexers
	// ---------------------------------------------------------------

	static final class IndexerBoomException extends RuntimeException
	{
		IndexerBoomException(final String message)
		{
			super(message);
		}
	}

	static final class Item
	{
		String name;
		String category;

		Item(final String name, final String category)
		{
			this.name     = name;
			this.category = category;
		}
	}

	/** Always healthy, indexes the name. */
	static final IndexerString<Item> NAME = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Item entity)
		{
			return entity.name;
		}
	};

	/** Always healthy, indexes the category. */
	static final IndexerString<Item> CATEGORY = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Item entity)
		{
			return entity.category;
		}
	};

	/** Throws whenever {@link #toggleArmed} is set, regardless of the entity's value. */
	static volatile boolean toggleArmed = false;

	static final IndexerString<Item> TOGGLE = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Item entity)
		{
			if(toggleArmed)
			{
				throw new IndexerBoomException("toggle boom");
			}
			return entity.name;
		}
	};

	/** Throws for entities named "poison", healthy for all others. */
	static final IndexerString<Item> POISON = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Item entity)
		{
			if("poison".equals(entity.name))
			{
				throw new IndexerBoomException("poison name");
			}
			return entity.name;
		}
	};

	/** Throws exactly once (on the first invocation after arming), then behaves normally. */
	static final AtomicBoolean throwOnceArmed = new AtomicBoolean(false);

	static final IndexerString<Item> THROW_ONCE = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Item entity)
		{
			if(throwOnceArmed.compareAndSet(true, false))
			{
				throw new IndexerBoomException("throw once");
			}
			return entity.name;
		}
	};

	/** Multi-value indexer whose key iterable yields "first" and then throws, for "multiPoison" entities. */
	static final IndexerMultiValue<Item, String> MULTI = new IndexerMultiValue.Abstract<>()
	{
		@Override
		public Class<String> keyType()
		{
			return String.class;
		}

		@Override
		public Iterable<? extends String> indexEntityMultiValue(final Item entity)
		{
			if(!"multiPoison".equals(entity.name))
			{
				return List.of(entity.name);
			}
			return () -> new Iterator<String>()
			{
				int i = 0;

				@Override
				public boolean hasNext()
				{
					return true;
				}

				@Override
				public String next()
				{
					if(this.i++ == 0)
					{
						return "first";
					}
					throw new IndexerBoomException("multi boom");
				}
			};
		}
	};

	static final class NoBadCategory extends CustomConstraint.AbstractSimple<Item>
	{
		@Override
		public boolean isViolated(final Item item)
		{
			return "bad".equals(item.category);
		}
	}

	@BeforeEach
	@AfterEach
	void disarm()
	{
		toggleArmed = false;
		throwOnceArmed.set(false);
	}

	// ---------------------------------------------------------------
	// add
	// ---------------------------------------------------------------

	@Test
	void add_throwingIndexer_rollsBackAndBurnsId()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().add(TOGGLE);

		toggleArmed = true;
		final IndexerBoomException e = assertThrows(
			IndexerBoomException.class,
			() -> map.add(new Item("a", "c1"))
		);

		// the cleanup re-ran the still-throwing indexer, so the secondary failure must be suppressed
		assertEquals(1, e.getSuppressed().length);

		assertEquals(0, map.size());
		assertNull(map.get(0L));
		assertEquals(0, map.query(NAME.is("a")).count());

		// the failed cleanup burned id 0, so the next add must not alias it
		toggleArmed = false;
		final long nextId = map.add(new Item("b", "c1"));
		assertEquals(1L, nextId);
		assertEquals(1, map.query(NAME.is("b")).count());
		assertEquals(0, map.query(NAME.is("a")).count());
	}

	@Test
	void add_throwingIndexer_reclaimsIdWhenCleanupSucceeds()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().add(THROW_ONCE);

		throwOnceArmed.set(true);
		final IndexerBoomException e = assertThrows(
			IndexerBoomException.class,
			() -> map.add(new Item("a", "c1"))
		);

		// the indexer recovered during the rollback, so cleanup fully succeeded
		assertEquals(0, e.getSuppressed().length);
		assertEquals(0, map.size());

		// full cleanup allows the id to be reclaimed
		final long nextId = map.add(new Item("b", "c1"));
		assertEquals(0L, nextId);
		assertEquals(1, map.query(NAME.is("b")).count());
	}

	@Test
	void add_multiValueIndexerThrowsMidIteration_partialKeysCleanedUp()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().add(MULTI);

		assertThrows(IndexerBoomException.class, () -> map.add(new Item("multiPoison", "c1")));

		assertEquals(0, map.size());
		// the first key had already been indexed before the iterable threw; it must be cleaned up
		assertEquals(0, map.query(MULTI.is("first")).count());
		assertEquals(0, map.query(NAME.is("multiPoison")).count());
	}

	@Test
	void add_middleIndexThrows_healthyIndicesHoldNoEntries()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().add(POISON);
		map.index().bitmap().add(CATEGORY);

		assertThrows(IndexerBoomException.class, () -> map.add(new Item("poison", "c1")));

		assertEquals(0, map.size());
		assertEquals(0, map.query(NAME.is("poison")).count());
		assertEquals(0, map.query(CATEGORY.is("c1")).count());
	}

	// ---------------------------------------------------------------
	// addAll
	// ---------------------------------------------------------------

	@Test
	void addAll_midFailure_rollsBackAllAndRetrySucceeds()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().add(POISON);

		assertThrows(IndexerBoomException.class, () -> map.addAll(
			new Item("a", "c1"),
			new Item("b", "c1"),
			new Item("poison", "c1"),
			new Item("d", "c1"),
			new Item("e", "c1")
		));

		assertEquals(0, map.size());
		assertEquals(0, map.query(NAME.is("a")).count());
		assertEquals(0, map.query(NAME.is("e")).count());

		final long lastId = map.addAll(
			new Item("f", "c1"),
			new Item("g", "c1"),
			new Item("h", "c1"),
			new Item("i", "c1"),
			new Item("j", "c1")
		);
		assertEquals(5, map.size());
		// the poison entity's cleanup failed, burning ids 0-2; the retry starts at id 3
		assertEquals(7L, lastId);
		assertEquals(1, map.query(NAME.is("f")).count());
		assertEquals(1, map.query(NAME.is("j")).count());
	}

	// ---------------------------------------------------------------
	// removeById
	// ---------------------------------------------------------------

	@Test
	void removeById_throwingIndexer_completesRemoval()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().add(TOGGLE);

		final Item item = new Item("a", "c1");
		final long id   = map.add(item);

		toggleArmed = true;
		assertThrows(IndexerBoomException.class, () -> map.removeById(id));

		// the removal must have completed despite the exception
		assertNull(map.get(id));
		assertEquals(0, map.size());
		assertEquals(0, map.query(NAME.is("a")).count());
		// residue in the broken index is invisible to queries
		assertEquals(0, map.query(TOGGLE.is("a")).count());

		// reindex() repairs the residue once the indexer works again
		toggleArmed = false;
		map.reindex();
		final long newId = map.add(new Item("a", "c1"));
		assertEquals(1, map.query(NAME.is("a")).count());
		assertEquals(1, map.query(TOGGLE.is("a")).count());
		assertSame(map.get(newId), map.query(TOGGLE.is("a")).findFirst().orElse(null));
	}

	// ---------------------------------------------------------------
	// set / replace
	// ---------------------------------------------------------------

	@Test
	void set_throwingIndexer_leavesMapUnchanged()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().add(TOGGLE);

		final Item original = new Item("a", "c1");
		final long id       = map.add(original);

		toggleArmed = true;
		assertThrows(IndexerBoomException.class, () -> map.set(id, new Item("b", "c1")));

		assertSame(original, map.get(id));
		assertEquals(1, map.size());
		assertEquals(1, map.query(NAME.is("a")).count());
		assertEquals(0, map.query(NAME.is("b")).count());

		// the map is fully functional afterwards
		toggleArmed = false;
		final Item replacement = new Item("b", "c1");
		assertSame(original, map.set(id, replacement));
		assertSame(replacement, map.get(id));
		assertEquals(1, map.query(NAME.is("b")).count());
		assertEquals(0, map.query(NAME.is("a")).count());
	}

	@Test
	void replace_throwingIndexer_leavesMapUnchanged()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().add(TOGGLE);

		final Item original = new Item("a", "c1");
		map.add(original);

		toggleArmed = true;
		assertThrows(IndexerBoomException.class, () -> map.replace(original, new Item("b", "c1")));

		toggleArmed = false;
		assertEquals(1, map.size());
		assertEquals(1, map.query(NAME.is("a")).count());
		assertEquals(0, map.query(NAME.is("b")).count());
	}

	@Test
	void set_customConstraintViolation_oldEntityStaysIndexed()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().add(CATEGORY);
		map.constraints().custom().addConstraint(new NoBadCategory());

		final Item original = new Item("a", "good");
		final long id       = map.add(original);

		assertThrows(ConstraintViolationException.class, () -> map.set(id, new Item("b", "bad")));

		assertSame(original, map.get(id));
		assertEquals(1, map.size());
		assertEquals(1, map.query(NAME.is("a")).count());
		assertEquals(1, map.query(CATEGORY.is("good")).count());
		assertEquals(0, map.query(CATEGORY.is("bad")).count());
	}

	// ---------------------------------------------------------------
	// update / apply
	// ---------------------------------------------------------------

	@Test
	void apply_logicThrows_entityRemovedAndIndicesClean()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(NAME);

		final long id = map.add(new Item("a", "c1"));

		final IndexerBoomException boom = new IndexerBoomException("logic boom");
		final IndexerBoomException thrown = assertThrows(IndexerBoomException.class, () ->
			map.apply(id, item ->
			{
				item.name = "changed";
				throw boom;
			})
		);
		assertSame(boom, thrown);

		// destructive removal: the half-mutated entity must be gone entirely
		assertNull(map.get(id));
		assertEquals(0, map.size());
		assertEquals(0, map.query(NAME.is("a")).count());
		assertEquals(0, map.query(NAME.is("changed")).count());
	}

	@Test
	void apply_indexerThrowsDuringPrepare_abortsCleanly()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().add(TOGGLE);

		final Item item = new Item("a", "c1");
		final long id   = map.add(item);

		toggleArmed = true;
		final AtomicBoolean logicRan = new AtomicBoolean(false);
		assertThrows(IndexerBoomException.class, () ->
			map.apply(id, e ->
			{
				logicRan.set(true);
				e.name = "changed";
				return null;
			})
		);

		// the throw happened while the pre-mutation state was being indexed: nothing may have changed
		assertFalse(logicRan.get());
		assertSame(item, map.get(id));
		assertEquals("a", item.name);
		assertEquals(1, map.size());

		toggleArmed = false;
		assertEquals(1, map.query(NAME.is("a")).count());
	}

	@Test
	void apply_indexerThrowsOnMutatedValue_entityRemovedWithSuppressedCleanupFailure()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().add(POISON);

		final long id = map.add(new Item("a", "c1"));

		final IndexerBoomException thrown = assertThrows(IndexerBoomException.class, () ->
			map.apply(id, item ->
			{
				item.name = "poison";
				return null;
			})
		);

		// destructive removal, original exception raw, cleanup failure attached
		assertNull(map.get(id));
		assertEquals(0, map.size());
		assertEquals(0, map.query(NAME.is("a")).count());
		assertTrue(thrown.getSuppressed().length > 0);
	}

	// ---------------------------------------------------------------
	// lock state
	// ---------------------------------------------------------------

	@Test
	void mutationsRemainPossibleAfterThrowingMutations()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().add(TOGGLE);

		final long keptId = map.add(new Item("kept", "c1"));

		toggleArmed = true;
		assertThrows(IndexerBoomException.class, () -> map.add(new Item("a", "c1")));
		assertThrows(IndexerBoomException.class, () -> map.set(keptId, new Item("b", "c1")));
		assertThrows(IndexerBoomException.class, () -> map.apply(keptId, item ->
		{
			item.name = "c";
			return null;
		}));
		toggleArmed = false;

		// none of the failed mutations may leave the map blocked or read-locked
		assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
		{
			final long id = map.add(new Item("after", "c1"));
			assertEquals(1, map.query(NAME.is("after")).count());
			map.removeById(id);
		});
	}
}
