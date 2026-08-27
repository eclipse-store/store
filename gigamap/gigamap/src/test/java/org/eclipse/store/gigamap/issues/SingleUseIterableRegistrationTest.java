package org.eclipse.store.gigamap.issues;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.exceptions.BitmapIndicesException;
import org.eclipse.store.gigamap.exceptions.ConstraintViolationException;
import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.CustomConstraint;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The structural bulk-registration entry points used to traverse their {@link Iterable} parameter more than
 * once ({@code addConstraints} even once per already present entity). An iterable that cannot be traversed
 * twice was therefore exhausted after the first (validation) pass, so the registration pass found nothing
 * left and the method returned normally having registered nothing at all - leaving a custom constraint the
 * application believed it had installed silently unenforced.
 * <p>
 * These tests pin that each entry point traverses its input exactly once.
 */
public class SingleUseIterableRegistrationTest
{
	///////////////////////////////////////////////////////////////////////////
	// shared domain //
	//////////////////

	static class Item
	{
		String label;
		String code;

		Item(final String label, final String code)
		{
			super();
			this.label = label;
			this.code  = code;
		}
	}

	static final IndexerString<Item> LABEL = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "label";
		}

		@Override
		protected String getString(final Item item)
		{
			return item.label;
		}
	};

	static final IndexerString<Item> CODE = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "code";
		}

		@Override
		protected String getString(final Item item)
		{
			return item.code;
		}
	};

	static final BinaryIndexerString<Item> UNIQUE_CODE = new BinaryIndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "uniqueCode";
		}

		@Override
		protected String getString(final Item item)
		{
			return item.code;
		}
	};

	/**
	 * An indexer with a caller-supplied name, to provoke a name conflict within a single batch.
	 */
	static final class FixedNameIndexer extends IndexerString.Abstract<Item>
	{
		private final String fixedName;

		FixedNameIndexer(final String fixedName)
		{
			super();
			this.fixedName = fixedName;
		}

		@Override
		public String name()
		{
			return this.fixedName;
		}

		@Override
		protected String getString(final Item item)
		{
			return item.label;
		}
	}

	static final class NoBad extends CustomConstraint.AbstractSimple<Item>
	{
		@Override
		public boolean isViolated(final Item item)
		{
			return item.label.contains("BAD");
		}
	}

	static final class NoUgly extends CustomConstraint.AbstractSimple<Item>
	{
		@Override
		public boolean isViolated(final Item item)
		{
			return item.label.contains("UGLY");
		}
	}



	///////////////////////////////////////////////////////////////////////////
	// helpers //
	////////////

	/**
	 * An {@link Iterable} that hands out the <b>same</b> iterator every time, i.e. it is exhausted after the
	 * first traversal. This is the silent variant of the defect: no exception, simply nothing left to register.
	 */
	static <T> Iterable<T> sameIteratorEachTime(final List<T> elements)
	{
		final Iterator<T> shared = elements.iterator();

		return () -> shared;
	}

	/**
	 * An {@link Iterable} counting how often a traversal was started.
	 */
	static final class CountingIterable<T> implements Iterable<T>
	{
		private final List<T> elements;
		private       int     iteratorRequests;

		CountingIterable(final List<T> elements)
		{
			super();
			this.elements = elements;
		}

		@Override
		public Iterator<T> iterator()
		{
			this.iteratorRequests++;

			return this.elements.iterator();
		}

		int iteratorRequests()
		{
			return this.iteratorRequests;
		}
	}

	private static GigaMap<Item> populatedMap()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.add(new Item("a", "c1"));
		map.add(new Item("b", "c2"));

		return map;
	}



	///////////////////////////////////////////////////////////////////////////
	// an iterable handing out the same iterator every time //
	/////////////////////////////////////////////////////////

	@Test
	void addConstraints_singleUseIterable_registersAndEnforces()
	{
		final GigaMap<Item> map = populatedMap();

		map.constraints().custom().addConstraints(sameIteratorEachTime(
			List.<CustomConstraint<? super Item>>of(new NoBad())
		));

		// the constraint must actually be enforced
		assertThrows(
			ConstraintViolationException.class,
			() -> map.add(new Item("this is BAD", "c3"))
		);
		assertEquals(2, map.size());

		// ... and it must actually be registered
		assertTrue(map.constraints().custom().removeConstraint("NoBad"));
	}

	@Test
	void addAll_singleUseIterable_registersIndices()
	{
		final GigaMap<Item> map = populatedMap();

		map.index().bitmap().addAll(sameIteratorEachTime(
			List.<Indexer<? super Item, ?>>of(LABEL, CODE)
		));

		assertNotNull(map.index().bitmap().get(String.class, "label"));
		assertNotNull(map.index().bitmap().get(String.class, "code"));

		// the back-fill over the already present entities must have run, too
		assertEquals(1, map.query(LABEL.is("a")).count());
		assertEquals(1, map.query(CODE.is("c2")).count());
	}

	@Test
	void addUniqueConstraints_singleUseIterable_registersAndEnforces()
	{
		final GigaMap<Item> map = populatedMap();

		map.constraints().unique().addUniqueConstraints(sameIteratorEachTime(
			List.<Indexer<? super Item, ?>>of(UNIQUE_CODE)
		));

		assertNotNull(map.index().bitmap().uniqueConstraints());
		assertEquals(1, map.index().bitmap().uniqueConstraints().size());

		assertThrows(
			ConstraintViolationException.class,
			() -> map.add(new Item("c", "c1"))
		);
		assertEquals(2, map.size());
	}



	///////////////////////////////////////////////////////////////////////////
	// a stream-backed iterable (the loud variant) //
	////////////////////////////////////////////////

	@Test
	void addConstraints_streamBackedIterable_registersAndEnforces()
	{
		final GigaMap<Item> map = populatedMap();

		final Stream<CustomConstraint<? super Item>> constraints = Stream.of(new NoBad(), new NoUgly());
		map.constraints().custom().addConstraints(constraints::iterator);

		assertTrue(map.constraints().custom().removeConstraint("NoBad"));
		assertTrue(map.constraints().custom().removeConstraint("NoUgly"));
	}

	@Test
	void addAll_streamBackedIterable_registersIndices()
	{
		final GigaMap<Item> map = populatedMap();

		final Stream<Indexer<? super Item, ?>> indexers = Stream.of(LABEL, CODE);
		map.index().bitmap().addAll(indexers::iterator);

		assertNotNull(map.index().bitmap().get(String.class, "label"));
		assertNotNull(map.index().bitmap().get(String.class, "code"));
	}

	@Test
	void addUniqueConstraints_streamBackedIterable_registersConstraint()
	{
		final GigaMap<Item> map = populatedMap();

		final Stream<Indexer<? super Item, ?>> indexers = Stream.of(UNIQUE_CODE);
		map.constraints().unique().addUniqueConstraints(indexers::iterator);

		assertNotNull(map.index().bitmap().uniqueConstraints());
		assertEquals(1, map.index().bitmap().uniqueConstraints().size());
	}



	///////////////////////////////////////////////////////////////////////////
	// traversal count //
	////////////////////

	/**
	 * The constraint validation against the already present entities used to sit inside the entity iteration,
	 * so the constraints were re-traversed once per entity - {@code n + 2} passes in total. This test fails on
	 * any such regression, independently of whether the passed iterable happens to tolerate it.
	 */
	@Test
	void addConstraints_traversesIterableExactlyOnce()
	{
		final GigaMap<Item> map = populatedMap();
		map.add(new Item("c", "c3"));
		map.add(new Item("d", "c4"));

		final CountingIterable<CustomConstraint<? super Item>> constraints = new CountingIterable<>(
			List.of(new NoBad(), new NoUgly())
		);

		map.constraints().custom().addConstraints(constraints);

		assertEquals(1, constraints.iteratorRequests());
	}

	@Test
	void addAll_traversesIterableExactlyOnce()
	{
		final GigaMap<Item> map = populatedMap();

		final CountingIterable<Indexer<? super Item, ?>> indexers = new CountingIterable<>(
			List.of(LABEL, CODE)
		);

		map.index().bitmap().addAll(indexers);

		assertEquals(1, indexers.iteratorRequests());
	}

	@Test
	void addUniqueConstraints_traversesIterableExactlyOnce()
	{
		final GigaMap<Item> map = populatedMap();

		final CountingIterable<Indexer<? super Item, ?>> indexers = new CountingIterable<>(
			List.of(UNIQUE_CODE)
		);

		map.constraints().unique().addUniqueConstraints(indexers);

		assertEquals(1, indexers.iteratorRequests());
	}



	///////////////////////////////////////////////////////////////////////////
	// name conflicts within a single batch //
	/////////////////////////////////////////

	/**
	 * Two indexers of the same name within one batch both pass the validation pass (which only knows the
	 * already registered names), so the second one used to be dropped silently while registering - after its
	 * data had already been built up over all existing entities.
	 */
	@Test
	void addAll_duplicateNamesInBatch_throwsAndRegistersNothing()
	{
		final GigaMap<Item> map = populatedMap();

		assertThrows(
			BitmapIndicesException.class,
			() -> map.index().bitmap().addAll(
				List.<Indexer<? super Item, ?>>of(new FixedNameIndexer("dup"), new FixedNameIndexer("dup"))
			)
		);

		assertNull(map.index().bitmap().get(String.class, "dup"));
	}



	///////////////////////////////////////////////////////////////////////////
	// empty input //
	////////////////

	/**
	 * An empty batch is a legitimate no-op, which is why the defect stayed invisible for so long.
	 */
	@Test
	void addConstraints_emptyIterable_isSilentNoOp()
	{
		final GigaMap<Item> map = populatedMap();

		map.constraints().custom().addConstraints(sameIteratorEachTime(
			List.<CustomConstraint<? super Item>>of()
		));

		map.add(new Item("nothing is enforced here", "c3"));
		assertEquals(3, map.size());
	}

	@Test
	void addAll_emptyIterable_isSilentNoOp()
	{
		final GigaMap<Item> map = populatedMap();

		map.index().bitmap().addAll(sameIteratorEachTime(List.<Indexer<? super Item, ?>>of()));

		assertNull(map.index().bitmap().get(String.class, "label"));
		assertEquals(2, map.size());
	}

	@Test
	void addUniqueConstraints_emptyIterable_isSilentNoOp()
	{
		final GigaMap<Item> map = populatedMap();

		map.constraints().unique().addUniqueConstraints(sameIteratorEachTime(
			List.<Indexer<? super Item, ?>>of()
		));

		assertTrue(map.index().bitmap().uniqueConstraints() == null
			|| map.index().bitmap().uniqueConstraints().isEmpty());

		// no constraint was registered, so a duplicate code is still accepted
		map.add(new Item("c", "c1"));
		assertEquals(3, map.size());
	}
}
