package org.eclipse.store.gigamap.constraint;

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

import org.eclipse.serializer.util.X;
import org.eclipse.store.gigamap.exceptions.ConstraintViolationException;
import org.eclipse.store.gigamap.types.CustomConstraint;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case tests for custom constraints covering:
 * - addConstraints() registering multiple constraints at once
 * - addConstraint() on a non-empty map validates existing entities immediately
 * - CustomConstraint.Abstract using the replacedEntity parameter
 * - CustomConstraint.Wrapper cross-checking other entities via the GigaMap instance
 * - Duplicate constraint name rejection
 */
public class CustomConstraintEdgeCasesTest
{
	// ---------------------------------------------------------------
	// Shared domain
	// ---------------------------------------------------------------

	static final class Item
	{
		String name;
		int    level;
		String project;

		Item(final String name, final int level, final String project)
		{
			this.name    = name;
			this.level   = level;
			this.project = project;
		}
	}

	static final IndexerString<Item> PROJECT_INDEX = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Item item)
		{
			return item.project;
		}
	};

	// ---------------------------------------------------------------
	// Named constraint classes (required for any persisted GigaMap)
	// ---------------------------------------------------------------

	static final class NoNamedBad extends CustomConstraint.AbstractSimple<Item>
	{
		@Override
		public boolean isViolated(final Item item)
		{
			return item.name.equals("BAD");
		}
	}

	static final class NoLevelZero extends CustomConstraint.AbstractSimple<Item>
	{
		@Override
		public boolean isViolated(final Item item)
		{
			return item.level == 0;
		}
	}

	/**
	 * Rejects any update that decreases the level.
	 * Uses the replacedEntity parameter to compare old vs. new state.
	 */
	static final class NoLevelDowngrade extends CustomConstraint.Abstract<Item>
	{
		@Override
		public void check(final long entityId, final Item replaced, final Item entity)
		{
			if(replaced != null && entity.level < replaced.level)
			{
				throw new ConstraintViolationException(entityId, replaced, entity);
			}
		}
	}

	/**
	 * Limits each project to at most 2 items.
	 * Uses the GigaMap instance (Wrapper) to count existing items in the same project
	 * before allowing the add.
	 *
	 * During add(), the entity is not yet in the bitmap index when this lambda runs,
	 * so the count reflects items already committed to that project.
	 */
	static final CustomConstraint.Wrapper<Item> MAX_2_PER_PROJECT = new CustomConstraint.Wrapper<>(
		(gigaMap, entityId, replaced, entity, createException) ->
		{
			// On update the net count stays the same — only restrict new adds.
			if(replaced != null)
			{
				return null;
			}
			final long count = gigaMap.query(PROJECT_INDEX.is(entity.project)).count();
			if(count >= 2)
			{
				return createException
					? new ConstraintViolationException(entityId, null, entity)
					: X.BREAK();
			}
			return null;
		}
	);

	// ---------------------------------------------------------------
	// addConstraints() — multiple constraints registered in one call
	// ---------------------------------------------------------------

	@Test
	void addConstraints_multiple_allEnforced()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().ensure(PROJECT_INDEX);
		map.constraints().custom().addConstraints(new NoNamedBad(), new NoLevelZero());

		// Each constraint fires independently
		assertThrows(ConstraintViolationException.class,
			() -> map.add(new Item("BAD", 5, "A")));

		assertThrows(ConstraintViolationException.class,
			() -> map.add(new Item("ok", 0, "A")));

		map.add(new Item("ok", 5, "A"));
		assertEquals(1, map.size());
	}

	// ---------------------------------------------------------------
	// addConstraint() on a non-empty GigaMap
	// ---------------------------------------------------------------

	@Test
	void addConstraint_nonEmptyMap_existingViolation_throwsAndConstraintNotRegistered()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().ensure(PROJECT_INDEX);
		map.add(new Item("BAD", 5, "A")); // already in map before constraint is added

		// Adding the constraint must fail because the existing entity violates it
		assertThrows(ConstraintViolationException.class,
			() -> map.constraints().custom().addConstraint(new NoNamedBad()));

		// Constraint must NOT be registered — adding another violating entity is still allowed
		assertDoesNotThrow(() -> map.add(new Item("BAD", 3, "B")));
		assertEquals(2, map.size());
	}

	@Test
	void addConstraint_nonEmptyMap_noViolation_constraintAppliedToFutureWrites()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().ensure(PROJECT_INDEX);
		map.add(new Item("good", 5, "A")); // no violation

		// Adding the constraint succeeds — no existing entity violates it
		assertDoesNotThrow(() -> map.constraints().custom().addConstraint(new NoNamedBad()));

		// Future writes are now checked
		assertThrows(ConstraintViolationException.class,
			() -> map.add(new Item("BAD", 3, "B")));
		assertEquals(1, map.size());
	}

	// ---------------------------------------------------------------
	// CustomConstraint.Abstract — uses replacedEntity
	//
	// NOTE: update() with a Consumer mutates the entity in place, so both
	// replacedEntity and entity are the same object by the time check() is
	// called.  Comparing them is meaningless there.  Use set() to get a
	// genuine pre/post comparison via the replacedEntity parameter.
	// ---------------------------------------------------------------

	@Test
	void abstract_set_upgradeAllowed_downgradeRejected()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().ensure(PROJECT_INDEX);
		map.constraints().custom().addConstraint(new NoLevelDowngrade());

		final long id = map.add(new Item("task", 5, "A"));

		// Upgrade via set(): replacedEntity.level=5, entity.level=7 → no violation
		map.set(id, new Item("task-v2", 7, "A"));
		assertEquals(7, map.get(id).level);

		// Downgrade via set(): replacedEntity.level=7, entity.level=3 → VIOLATION
		assertThrows(ConstraintViolationException.class,
			() -> map.set(id, new Item("task-v3", 3, "A")));

		// set() rejects without ejecting — the original entity stays in place
		assertEquals(7, map.get(id).level);
		assertEquals(1, map.size());
	}

	@Test
	void abstract_addWithNullReplacedEntity_notChecked()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().ensure(PROJECT_INDEX);
		map.constraints().custom().addConstraint(new NoLevelDowngrade());

		// add() always passes — replacedEntity is null, constraint skips the check
		map.add(new Item("x", 1, "A"));
		map.add(new Item("y", 100, "A"));
		assertEquals(2, map.size());
	}

	// ---------------------------------------------------------------
	// CustomConstraint.Wrapper — cross-checks the GigaMap
	// ---------------------------------------------------------------

	@Test
	void wrapper_crossChecksMapCount_limitsPerProject()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().ensure(PROJECT_INDEX);
		map.constraints().custom().addConstraint(MAX_2_PER_PROJECT);

		map.add(new Item("i1", 1, "Alpha"));
		map.add(new Item("i2", 2, "Alpha"));

		// Third item in "Alpha" exceeds the per-project limit
		assertThrows(ConstraintViolationException.class,
			() -> map.add(new Item("i3", 3, "Alpha")));

		// Other projects are independent
		map.add(new Item("j1", 1, "Beta"));
		map.add(new Item("j2", 2, "Beta"));

		assertEquals(4, map.size());
		assertEquals(2, map.query(PROJECT_INDEX.is("Alpha")).count());
		assertEquals(2, map.query(PROJECT_INDEX.is("Beta")).count());
	}

	@Test
	void wrapper_updateDoesNotCountAgainstProjectLimit()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().ensure(PROJECT_INDEX);
		map.constraints().custom().addConstraint(MAX_2_PER_PROJECT);

		final Item i1 = new Item("i1", 1, "Alpha");
		final Item i2 = new Item("i2", 2, "Alpha");
		map.add(i1);
		map.add(i2);

		// Updating (not adding) must succeed even though Alpha is at the limit
		assertDoesNotThrow(() -> map.update(i1, i -> i.level = 99));
		assertEquals(99, i1.level);
	}

	// ---------------------------------------------------------------
	// Duplicate constraint name
	// ---------------------------------------------------------------

	@Test
	void duplicateConstraintName_throwsIllegalArgument()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().ensure(PROJECT_INDEX);
		map.constraints().custom().addConstraint(new NoNamedBad());

		// Registering the same named constraint a second time must fail
		assertThrows(IllegalArgumentException.class,
			() -> map.constraints().custom().addConstraint(new NoNamedBad()));
	}

	@Test
	void duplicateConstraintName_inBatch_throwsIllegalArgument()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().ensure(PROJECT_INDEX);

		// Both constraints share the same class simple name = "NoNamedBad"
		assertThrows(IllegalArgumentException.class,
			() -> map.constraints().custom().addConstraints(new NoNamedBad(), new NoNamedBad()));
	}
}
