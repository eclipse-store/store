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

import org.eclipse.serializer.hashing.XHashing;
import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Regression test for internal issue #131, symptom B: on a map built with a value-equality equalator,
 * {@code set} and {@code replace} silently discarded a replacement the equalator considered equal to the
 * entity already stored - {@code replace} even returned a non-negative id that looked like success.
 * <p>
 * That hit the canonical "replace the record having this business key with a new version" idiom head on:
 * the equalator says the two instances <em>are</em> the same record, which is precisely why the caller is
 * replacing one with the other. {@code internalSet} read the same answer as "there is no change in the
 * entity data" and skipped everything.
 * <p>
 * The equalator's job is to resolve an entity instance to <em>which id</em> is addressed; it never had any
 * business deciding <em>whether</em> the write happens. The skip is now a reference-identity check, so a
 * distinct instance is always applied - and the identical-instance case it still catches is rejected
 * outright (see {@link SetIdenticalInstanceTest}).
 * <p>
 * The entity used here has {@code equals}/{@code hashCode} on a business key only, plus a mutable indexed
 * field that is deliberately <em>not</em> part of equality - i.e. "the same record, a newer version".
 *
 * @see SetIdenticalInstanceTest the sibling symptom - {@code set(id, theInstanceAlreadyMappedThere)}
 */
public class SetValueEqualInstanceTest
{
	static final PayloadIndexer     PAYLOAD    = new PayloadIndexer();
	static final KeyIndexer         KEY        = new KeyIndexer();
	static final UniqueKeyIndexer   UNIQUE_KEY = new UniqueKeyIndexer();

	/**
	 * {@code set} with a value-equal but distinct instance must install it and re-index it.
	 */
	@Test
	void setValueEqualDistinctInstanceIsApplied()
	{
		final GigaMap<Record> map = GigaMap.New(XHashing.hashEqualityValue());
		map.index().bitmap().add(PAYLOAD);

		final Record v1 = new Record("k", "A");
		final long   id = map.add(v1);

		final Record v2 = new Record("k", "B"); // equal per equals (key only), new payload

		assertSame(v1, map.set(id, v2), "the replaced entity is returned");

		assertSame  (v2, map.get(id)                        , "the replacement must be installed");
		assertEquals("B", map.get(id).payload               , "with its own payload");
		assertEquals(1L, map.query(PAYLOAD.is("B")).count() , "and indexed under its new key");
		assertEquals(0L, map.query(PAYLOAD.is("A")).count() , "the replaced key must be gone");
		assertEquals(1L, map.size()                         , "a replacement is not an addition");
	}

	/**
	 * The same through {@code replace}, which is where this bit hardest: it reported success.
	 */
	@Test
	void replaceValueEqualDistinctInstanceIsApplied()
	{
		final GigaMap<Record> map = GigaMap.New(XHashing.hashEqualityValue());
		map.index().bitmap().add(PAYLOAD);

		final Record v1 = new Record("k", "A");
		final long   id = map.add(v1);

		final Record v2 = new Record("k", "B");

		assertEquals(id, map.replace(v1, v2), "replace returns the id it wrote to");

		assertSame  (v2, map.get(id)                       , "and the returned id must not have been a lie");
		assertEquals(1L, map.query(PAYLOAD.is("B")).count(), "the replacement is indexed");
		assertEquals(0L, map.query(PAYLOAD.is("A")).count(), "the replaced key is gone");
	}

	/**
	 * The data-loss cover. Pre-fix the reopened map showed the old payload: the replacement had never been
	 * installed, so there was nothing for {@code store()} to persist.
	 */
	@Test
	void valueEqualReplacementSurvivesARestart(@TempDir final Path dir)
	{
		final long id;

		{
			final GigaMap<Record> map = GigaMap.New(XHashing.hashEqualityValue());
			map.index().bitmap().add(PAYLOAD);

			final Record v1 = new Record("k", "A");
			id = map.add(v1);

			try(final EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir))
			{
				storage.storeRoot();

				map.replace(v1, new Record("k", "B"));

				map.store();
			}
		}

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Record> loaded = (GigaMap<Record>)storage.root();

			assertEquals("B", loaded.get(id).payload              , "the replacement must be persisted");
			assertEquals(1L, loaded.query(PAYLOAD.is("B")).count(), "and the index must agree with it");
			assertEquals(0L, loaded.query(PAYLOAD.is("A")).count(), "under the new key only");
		}
	}

	/**
	 * With the index on the equality field itself, every derived key is unchanged, so the index machinery
	 * legitimately does no work at all. The instance swap must not depend on that: it is the slot write and
	 * the change marking that carry the new instance, not the index update.
	 */
	@Test
	void valueEqualReplacementWithIdenticalKeysStillSwapsTheInstance(@TempDir final Path dir)
	{
		final long id;

		{
			final GigaMap<Record> map = GigaMap.New(XHashing.hashEqualityValue());
			map.index().bitmap().add(KEY); // indexes the equality field, not the payload

			final Record v1 = new Record("k", "A");
			id = map.add(v1);

			final Record v2 = new Record("k", "B");

			try(final EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir))
			{
				storage.storeRoot();

				map.set(id, v2);

				assertSame  (v2, map.get(id)                   , "the instance must be swapped anyway");
				assertEquals(1L, map.query(KEY.is("k")).count(), "and stay indexed exactly once");
				assertEquals(1L, map.size()                    , "and not be counted twice");

				map.store();
			}
		}

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Record> loaded = (GigaMap<Record>)storage.root();

			assertEquals("B", loaded.get(id).payload          , "the swap must be persisted");
			assertEquals(1L, loaded.query(KEY.is("k")).count(), "and the index left intact");
			assertEquals(1L, loaded.size()                    , "and the size left alone");
		}
	}

	/**
	 * The one place narrowing the skip could plausibly have regressed: a unique index on the equality field
	 * now genuinely runs for a value-equal {@code set}, where it was previously skipped wholesale. The
	 * entity must not be seen as its own duplicate.
	 * <p>
	 * A unique constraint needs a binary index, so this also covers the binary change handler - a different
	 * {@code isEqual} implementation than the hashing one the other cases exercise.
	 */
	@Test
	void valueEqualReplacementDoesNotTripAUniqueConstraint()
	{
		final GigaMap<Record> map = GigaMap.New(XHashing.hashEqualityValue());
		map.index().bitmap().addUniqueConstraint(UNIQUE_KEY);

		final Record v1 = new Record("k", "A");
		final long   id = map.add(v1);
		map.add(new Record("other", "A"));

		final Record v2 = new Record("k", "B"); // same unique key - it IS the same record

		assertDoesNotThrow(
			() -> map.set(id, v2),
			"replacing an entity with a new version of itself must not violate its own unique key"
		);

		assertSame  (v2, map.get(id)                          , "the replacement is installed");
		assertEquals(2L, map.size()                           , "and the size is unchanged");
		assertEquals(1L, map.query(UNIQUE_KEY.is("k")).count(), "and the unique key still resolves once");
	}

	static final class PayloadIndexer extends IndexerString.Abstract<Record>
	{
		@Override
		protected String getString(final Record entity)
		{
			return entity.payload;
		}
	}

	static final class KeyIndexer extends IndexerString.Abstract<Record>
	{
		@Override
		protected String getString(final Record entity)
		{
			return entity.key;
		}
	}

	/** Binary, because only a binary index is suitable as a unique constraint. */
	static final class UniqueKeyIndexer extends BinaryIndexerString.Abstract<Record>
	{
		@Override
		protected String getString(final Record entity)
		{
			return entity.key;
		}
	}

	/**
	 * Equality is the business key alone; {@code payload} is the versioned part that a replacement changes.
	 */
	static final class Record
	{
		final String key;
		String       payload;

		Record(final String key, final String payload)
		{
			this.key     = key;
			this.payload = payload;
		}

		@Override
		public boolean equals(final Object other)
		{
			return other instanceof Record && ((Record)other).key.equals(this.key);
		}

		@Override
		public int hashCode()
		{
			return this.key.hashCode();
		}

		@Override
		public String toString()
		{
			return "Record[" + this.key + "=" + this.payload + "]";
		}
	}
}
