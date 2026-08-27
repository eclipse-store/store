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

import org.eclipse.store.gigamap.exceptions.ConstraintViolationException;
import org.eclipse.store.gigamap.exceptions.UniqueConstraintViolationException;
import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the fields carried by {@link ConstraintViolationException}:
 * - {@code entityId}, {@code replacedEntity}, {@code violatingEntity}
 *
 * Also covers the identity + unique index combination:
 * - Builder API: {@code withBitmapIdentityIndex} + {@code withBitmapUniqueIndex} on the same indexer
 * - Post-creation API: {@code addUniqueConstraint} + {@code setIdentityIndices}
 */
public class ConstraintViolationExceptionFieldsTest
{
	static final class Person
	{
		String email; // mutable — allows in-place update() mutations
		int    level;

		Person(final String email, final int level)
		{
			this.email = email;
			this.level = level;
		}
	}

	static final BinaryIndexerString<Person> EMAIL_INDEX = new BinaryIndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Person p)
		{
			return p.email;
		}
	};

	// ---------------------------------------------------------------
	// add() — replacedEntity must be null
	// ---------------------------------------------------------------

	@Test
	void add_exceptionHasNullReplacedEntity()
	{
		final GigaMap<Person> map = GigaMap.<Person>Builder()
			.withBitmapUniqueIndex(EMAIL_INDEX)
			.build();

		map.add(new Person("a@test.com", 1));
		final Person duplicate = new Person("a@test.com", 2);

		final UniqueConstraintViolationException ex = assertThrows(
			UniqueConstraintViolationException.class,
			() -> map.add(duplicate)
		);

		assertNull(ex.getReplacedEntity(),        "add() has no previous entity — replacedEntity must be null");
		assertSame(duplicate, ex.getViolatingEntity(), "violatingEntity must be the rejected entity");
		// During add(), constraints are checked before an ID is assigned; -1 signals "no ID yet".
		assertEquals(-1L, ex.getEntityId(),       "entityId is -1 for add() — entity not yet permanently placed");
	}

	// ---------------------------------------------------------------
	// set() — replacedEntity must be the previous entity
	// ---------------------------------------------------------------

	@Test
	void set_exceptionHasPreviousEntityAsReplaced()
	{
		final GigaMap<Person> map = GigaMap.<Person>Builder()
			.withBitmapUniqueIndex(EMAIL_INDEX)
			.build();

		final Person alice = new Person("alice@test.com", 1);
		map.add(new Person("bob@test.com", 2));
		final long aliceId = map.add(alice);

		final Person replacement = new Person("bob@test.com", 9); // duplicates bob's email
		final UniqueConstraintViolationException ex = assertThrows(
			UniqueConstraintViolationException.class,
			() -> map.set(aliceId, replacement)
		);

		assertSame(alice, ex.getReplacedEntity(),         "replacedEntity must be the entity that was in the map");
		assertSame(replacement, ex.getViolatingEntity(), "violatingEntity must be the rejected replacement");
		assertEquals(aliceId, ex.getEntityId(),           "entityId must match the slot being replaced");
	}

	// ---------------------------------------------------------------
	// update() — entityId identifies the ejected entity
	//
	// update() mutates in place; GigaMap cannot roll back an arbitrary lambda,
	// so it ejects the entity.  The exception carries the pre-ejection entityId,
	// letting the caller re-add the entity after fixing the violation.
	//
	// NOTE: CustomConstraint.Abstract.check() receives the same (already mutated)
	// object for both replacedEntity and entity during update().  Comparing them
	// is therefore not meaningful.  Use a unique-index constraint instead, whose
	// violation is detected by comparing bitmap state — not the entity object.
	// ---------------------------------------------------------------

	@Test
	void update_exceptionCarriesEjectedEntityId()
	{
		final GigaMap<Person> map = GigaMap.<Person>Builder()
			.withBitmapUniqueIndex(EMAIL_INDEX)
			.build();

		final Person alice = new Person("alice@test.com", 1);
		final long idAlice = map.add(alice);
		map.add(new Person("bob@test.com", 2));

		final UniqueConstraintViolationException ex = assertThrows(
			UniqueConstraintViolationException.class,
			() -> map.update(alice, p -> p.email = "bob@test.com")
		);

		assertEquals(idAlice, ex.getEntityId(),
			"entityId in exception must match the ejected entity's id");
		assertNull(map.peek(idAlice),
			"peek() must return null for the ejected id");
		assertEquals(1, map.size(),
			"only bob must remain after alice is ejected");
	}

	// ---------------------------------------------------------------
	// Unique + identity index — builder API
	//
	// The builder accepts the same indexer for both withBitmapIdentityIndex
	// and withBitmapUniqueIndex; the underlying bitmap index is registered
	// once and is both identity and unique.
	// ---------------------------------------------------------------

	@Test
	void uniqueAndIdentityIndex_builder_enforcesUniqueness()
	{
		final GigaMap<Person> map = GigaMap.<Person>Builder()
			.withBitmapUniqueIndex(EMAIL_INDEX)
			.build();

		map.add(new Person("alice@test.com", 1));
		map.add(new Person("bob@test.com", 2));

		assertThrows(UniqueConstraintViolationException.class,
			() -> map.add(new Person("alice@test.com", 99)));

		assertEquals(2, map.size());
	}

	@Test
	void uniqueAndIdentityIndex_builder_combinedIdentityAndUnique()
	{
		final GigaMap<Person> map = GigaMap.<Person>Builder()
			.withBitmapIdentityIndex(EMAIL_INDEX)
			.withBitmapUniqueIndex(EMAIL_INDEX)
			.build();

		final Person alice = new Person("alice@test.com", 1);
		map.add(alice);
		map.add(new Person("bob@test.com", 2));

		// identity: update() finds the entity through the identity index
		map.update(alice, p -> p.level = 42);
		assertEquals(42, alice.level);

		// unique: a second entity with the same email is rejected
		assertThrows(UniqueConstraintViolationException.class,
			() -> map.add(new Person("alice@test.com", 99)));
	}

	@Test
	void uniqueAndIdentityIndex_builder_setIdentityAfterBuild()
	{
		// withBitmapUniqueIndex registers the index as both a bitmap index and a
		// unique constraint.  setIdentityIndices() then promotes it to identity.
		final GigaMap<Person> map = GigaMap.<Person>Builder()
			.withBitmapUniqueIndex(EMAIL_INDEX)
			.build();
		map.index().bitmap().setIdentityIndices(EMAIL_INDEX);

		final Person alice = new Person("alice@test.com", 1);
		map.add(alice);

		map.update(alice, p -> p.level = 42);
		assertEquals(42, alice.level);
		assertEquals(1, map.query(EMAIL_INDEX.is("alice@test.com")).count());
	}

	// ---------------------------------------------------------------
	// Unique + identity index — post-creation API
	//
	// addUniqueConstraint() registers the index as both a bitmap index and a
	// unique constraint in one step.  setIdentityIndices() is called afterward.
	// ---------------------------------------------------------------

	@Test
	void uniqueAndIdentityIndex_postCreation_updateViaIdentity()
	{
		final GigaMap<Person> map = GigaMap.New();
		final BitmapIndices<Person> bitmap = map.index().bitmap();
		bitmap.addUniqueConstraint(EMAIL_INDEX);
		bitmap.setIdentityIndices(EMAIL_INDEX);

		final Person alice = new Person("alice@test.com", 5);
		map.add(alice);

		map.update(alice, p -> p.level = 20);
		assertEquals(20, alice.level);
	}

	@Test
	void uniqueAndIdentityIndex_postCreation_combinedIdentityAndUnique()
	{
		// Mirrors uniqueAndIdentityIndex_builder_combinedIdentityAndUnique, but
		// builds the combination through the post-creation API documented in
		// constraints.adoc: addUniqueConstraint creates the backing bitmap index,
		// setIdentityIndices then promotes it to identity.
		final GigaMap<Person> map = GigaMap.New();
		final BitmapIndices<Person> bitmap = map.index().bitmap();
		bitmap.addUniqueConstraint(EMAIL_INDEX);
		bitmap.setIdentityIndices(EMAIL_INDEX);

		final Person alice = new Person("alice@test.com", 1);
		map.add(alice);
		map.add(new Person("bob@test.com", 2));

		// identity: a single index serves both roles
		assertEquals(1, bitmap.identityIndices().size());
		assertSame(
			bitmap.identityIndices().get(),
			bitmap.uniqueConstraints().get(),
			"identity and unique must share the same backing bitmap index"
		);

		// identity: update() finds the entity through the identity index
		map.update(alice, p -> p.level = 42);
		assertEquals(42, alice.level);

		// unique: a second entity with the same email is rejected
		assertThrows(UniqueConstraintViolationException.class,
			() -> map.add(new Person("alice@test.com", 99)));
	}

	@Test
	void uniqueAndIdentityIndex_postCreation_updateToTakenValueRejected()
	{
		// update() mutates in place via a lambda; the unique constraint must
		// still reject mutations that collide with another entity's value.
		// The Eclipse Store contract for update() on violation is to eject
		// the offending entity (see update_exceptionCarriesEjectedEntityId).
		final GigaMap<Person> map = GigaMap.New();
		final BitmapIndices<Person> bitmap = map.index().bitmap();
		bitmap.addUniqueConstraint(EMAIL_INDEX);
		bitmap.setIdentityIndices(EMAIL_INDEX);

		final Person alice = new Person("alice@test.com", 1);
		final long aliceId = map.add(alice);
		map.add(new Person("bob@test.com", 2));

		final UniqueConstraintViolationException ex = assertThrows(
			UniqueConstraintViolationException.class,
			() -> map.update(alice, p -> p.email = "bob@test.com")
		);

		assertEquals(aliceId, ex.getEntityId(),
			"exception must carry the ejected entity's id");
		assertNull(map.peek(aliceId),
			"alice must be ejected after the failed update");
		assertEquals(1, map.size(),
			"only bob must remain after alice is ejected");
		// bob's slot is still uniquely occupied — re-adding the email fails
		assertThrows(UniqueConstraintViolationException.class,
			() -> map.add(new Person("bob@test.com", 3)));
	}
}
