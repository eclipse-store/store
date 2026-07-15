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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.store.gigamap.exceptions.BitmapIndexException;
import org.eclipse.store.gigamap.types.BinaryIndexerInteger;
import org.eclipse.store.gigamap.types.Condition;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression coverage for internal issue #88: GigaMap's persisted bitmap indices are a derived
 * cache whose keys are restored verbatim from storage on load and are never revalidated against
 * the (possibly class-evolved) entities. When an indexed field is renamed/retyped across releases
 * without a value-preserving refactoring mapping, the loaded entities carry defaulted values while
 * the persisted bitmaps keep the pre-evolution keys.
 * <p>
 * Class evolution is simulated here by rewriting the persisted type dictionary: the indexed field
 * is renamed to a dissimilar name AND retyped, so on reload the legacy member is unmatched (skipped)
 * and the runtime field loads defaulted to 0 — byte-for-byte equivalent to "the data and dictionary
 * were written by an older class version". A plain rename is not enough: with a single field on each
 * side the automatic member matching still pairs dissimilar names, hence the field type is flipped
 * from {@code int} to {@code float} (identical 4-byte layout) as well.
 * <p>
 * These tests pin the fixed behavior:
 * <ul>
 *   <li>{@link #reindexRecoversFromStaleUniqueIndexAfterEvolution(Path)} — {@code reindex()} is the
 *       sanctioned recovery path: after it, queries reflect the actual entity state and a subsequent
 *       {@code apply()} no longer triggers a phantom unique violation that would delete the entity.
 *       Uses a binary index (unique constraints require a binary index).</li>
 *   <li>{@link #staleHashingIndexRaisesDescriptiveExceptionInsteadOfRawError(Path)} — a write against
 *       a stale hashing index now fails with a descriptive, catchable {@link BitmapIndexException}
 *       pointing to {@code reindex()}, instead of the former raw {@code java.lang.Error}.</li>
 * </ul>
 */
public class GigaMap88Test
{
	/**
	 * Session 1: store a single {@code Person(code=5)} indexed on {@code code}, then simulate class
	 * evolution by rewriting the persisted type dictionary. When {@code unique} is set, the index is
	 * additionally registered as a unique constraint (requires a binary index). Returns the entity id.
	 */
	private static long seedAndEvolve(
		final Path                         storageDir,
		final Indexer<? super Person, ?>   indexer   ,
		final boolean                      unique
	)
		throws IOException
	{
		final long            id;
		final GigaMap<Person> map = GigaMap.New();
		if(unique)
		{
			// the unique constraint registers the bitmap index for the indexer as well
			map.index().bitmap().addUniqueConstraint("codeUnique", indexer);
		}
		else
		{
			map.index().bitmap().add(indexer);
		}

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(map, storageDir))
		{
			id = map.add(new Person(5));
			map.store();
		}

		// Simulate evolution: rewrite the indexed field's dictionary entry to a dissimilar name and a
		// different (but same-width) type. The .ptd writes the field as "<type> <name>," (column padded,
		// and only qualified with the declaring class when the simple name is ambiguous), so match it
		// format-agnostically rather than assuming a fixed layout.
		final Path   ptd     = storageDir.resolve("PersistenceTypeDictionary.ptd");
		final String dict    = Files.readString(ptd, StandardCharsets.UTF_8);
		final String evolved = dict.replaceAll("int(\\s+)((?:[\\w.$]+#)?)code(\\s*,)", "float$1$2qxz9$3");
		assertTrue(!evolved.equals(dict), "sanity: the dictionary must contain the indexed field 'code'");
		Files.writeString(ptd, evolved, StandardCharsets.UTF_8);

		return id;
	}

	@Test
	@Timeout(120)
	void reindexRecoversFromStaleUniqueIndexAfterEvolution(@TempDir final Path dir) throws IOException
	{
		final long id = seedAndEvolve(dir, new BinaryCodeIndexer(), true);

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Person>     loaded  = (GigaMap<Person>)storage.root();
			final BinaryCodeIndexer   indexer = new BinaryCodeIndexer();

			// sanity: the evolution simulation worked — the loaded entity's field defaulted to 0.
			assertNotNull(loaded.get(id));
			assertEquals(0, loaded.get(id).code, "sanity: field must have been skipped/defaulted by evolution");

			// Before recovery the index is stale: it still answers for the PRE-evolution key.
			assertEquals(1, count(loaded, indexer.is(5)), "precondition: stale index still answers for the old key");
			assertEquals(0, count(loaded, indexer.is(0)), "precondition: entity not yet reachable by its actual value");

			// reindex() rebuilds every index from the current entity state — the sanctioned recovery.
			loaded.reindex();

			assertEquals(0, count(loaded, indexer.is(5)), "after reindex: stale key must be gone");
			assertEquals(1, count(loaded, indexer.is(0)), "after reindex: entity reachable by its actual value");

			// A legitimate business update writing the entity's true old value now succeeds: with the
			// index rebuilt there is no phantom unique entry under key 5, so apply() does NOT delete it.
			loaded.apply(id, p ->
			{
				p.code = 5;
				return null;
			});
			assertNotNull(loaded.get(id), "the committed entity must survive a legitimate update after reindex");
			assertEquals(5, loaded.get(id).code);

			loaded.store();
		}

		// The recovery is persistent across a restart.
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Person> loaded = (GigaMap<Person>)storage.root();
			assertNotNull(loaded.get(id), "the committed entity must survive the restart");
			assertEquals(5, loaded.get(id).code);
		}
	}

	@Test
	@Timeout(120)
	void staleHashingIndexRaisesDescriptiveExceptionInsteadOfRawError(@TempDir final Path dir) throws IOException
	{
		// Hashing index (not binary): the loaded entity defaults to code=0, but the persisted key→entry
		// table only holds an entry under the pre-evolution key 5 (nothing for 0). A write that changes
		// the value must de-index the re-derived old key 0, for which there is no entry — the case that
		// formerly threw a raw java.lang.Error deep inside the index update.
		final long id = seedAndEvolve(dir, new HashingCodeIndexer(), false);

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Person> loaded = (GigaMap<Person>)storage.root();
			assertEquals(0, loaded.get(id).code, "sanity: field defaulted by evolution");

			final BitmapIndexException ex = assertThrows(
				BitmapIndexException.class,
				() -> loaded.set(id, new Person(7)),
				"a write against a stale index must fail with a descriptive BitmapIndexException, not a raw Error"
			);
			assertTrue(
				ex.getMessage() != null && ex.getMessage().contains("reindex"),
				"the exception should point the caller to reindex(); was: " + ex.getMessage()
			);
		}
	}

	private static long count(final GigaMap<Person> map, final Condition<Person> condition)
	{
		return map.query(condition).count();
	}

	static final class Person
	{
		int code;

		Person(final int code)
		{
			this.code = code;
		}
	}

	/** Binary integer index — required to back a unique constraint. */
	static final class BinaryCodeIndexer extends BinaryIndexerInteger.Abstract<Person>
	{
		@Override
		public String name()
		{
			return "code";
		}

		@Override
		protected Integer getInteger(final Person entity)
		{
			return entity.code;
		}
	}

	/** Hashing integer index — exercises the key→entry table (and the stale-key removal guard). */
	static final class HashingCodeIndexer extends IndexerInteger.Abstract<Person>
	{
		@Override
		public String name()
		{
			return "code";
		}

		@Override
		protected Integer getInteger(final Person entity)
		{
			return entity.code;
		}
	}
}
