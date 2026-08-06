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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.store.gigamap.exceptions.UniqueConstraintViolationExceptionBitmap;
import org.eclipse.store.gigamap.types.BinaryIndexerInteger;
import org.eclipse.store.gigamap.types.Condition;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression coverage for internal issue #121: {@code GigaMap.reindex()} used to rebuild a bitmap index
 * that backs a unique constraint without re-validating uniqueness. Colliding data therefore yielded an
 * index in which one unique key maps to several live entities - silently, and persistently across a
 * restart, while every write path rejects exactly that state.
 * <p>
 * The realistic source of such data is class evolution of an indexed unique field: the legacy mapping
 * defaults that field for every old entity, so they all reload carrying the same key. As in
 * {@link GigaMap88Test}, the evolution is simulated by rewriting the persisted type dictionary - the
 * indexed field is renamed to a dissimilar name AND retyped (same 4-byte layout), so on reload the legacy
 * member is unmatched and the runtime field loads defaulted to 0.
 * <p>
 * These tests pin the fixed behavior: the violation is reported, the rebuild is nevertheless completed
 * (so the indices describe the entities as they actually are, and are persisted), and the documented
 * repair - re-distinguish the colliding keys, then {@code reindex()} again - reaches a clean state.
 */
public class GigaMap121Test
{
	/**
	 * Session 1: store one {@code Rec} per given code under a unique constraint on {@code code}, then
	 * simulate class evolution of that indexed field. Returns the entity ids, in the order of the codes.
	 */
	private static long[] seedAndEvolve(final Path storageDir, final int... codes) throws IOException
	{
		final long[]       ids = new long[codes.length];
		final GigaMap<Rec> map = GigaMap.New();

		// the unique constraint registers the backing bitmap index for the indexer as well
		map.index().bitmap().addUniqueConstraint(new CodeIndexer());

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(map, storageDir))
		{
			for(int i = 0; i < codes.length; i++)
			{
				ids[i] = map.add(new Rec(codes[i]));
			}
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

		return ids;
	}

	@Test
	@Timeout(120)
	void reindexOverCollidingUniqueKeysReportsTheViolation(@TempDir final Path dir) throws IOException
	{
		final long[] ids = seedAndEvolve(dir, 5, 7);

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Rec> loaded = loadedMap(storage);

			// sanity: the evolution simulation worked - both entities defaulted to the same key.
			assertEquals(0, loaded.get(ids[0]).code, "sanity: field must have been defaulted by evolution");
			assertEquals(0, loaded.get(ids[1]).code, "sanity: field must have been defaulted by evolution");

			final UniqueConstraintViolationExceptionBitmap ex = assertThrows(
				UniqueConstraintViolationExceptionBitmap.class,
				loaded::reindex,
				"reindex() must not silently build a unique index holding one key for two live entities"
			);

			assertEquals("code", ex.getViolatedIndex().name(), "the violated unique index must be named");
			assertTrue(
				ex.getEntityId() == ids[0] || ex.getEntityId() == ids[1],
				"the exception must name one of the colliding entities; was id " + ex.getEntityId()
			);
			assertNotNull(ex.getViolatingEntity(), "the exception must carry the offending entity");
			assertTrue(
				ex.getMessage() != null && ex.getMessage().contains("reindex()"),
				"the message must explain the reindex context; was: " + ex.getMessage()
			);
		}
	}

	/**
	 * The violation is reported only after the rebuild has completed: the indices must describe the
	 * entities as they actually are (which is what the repair operates on), and must be marked for
	 * storing, so the throw does not cost the rebuild its persistence.
	 */
	@Test
	@Timeout(120)
	void reindexCompletesTheRebuildBeforeReporting(@TempDir final Path dir) throws IOException
	{
		final long[] ids = seedAndEvolve(dir, 5, 7);

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Rec> loaded = loadedMap(storage);

			// precondition: the stale index still answers for the pre-evolution keys.
			assertEquals(1, count(loaded, 5), "precondition: stale index answers for the old key 5");
			assertEquals(1, count(loaded, 7), "precondition: stale index answers for the old key 7");

			assertThrows(UniqueConstraintViolationExceptionBitmap.class, loaded::reindex);

			assertEquals(2, loaded.size(), "no entity may be dropped by the reported rebuild");
			assertNotNull(loaded.get(ids[0]));
			assertNotNull(loaded.get(ids[1]));
			assertEquals(0, count(loaded, 5), "after the rebuild: no stale key may remain");
			assertEquals(0, count(loaded, 7), "after the rebuild: no stale key may remain");
			assertEquals(2, count(loaded, 0), "after the rebuild: the index must match the actual entity state");

			loaded.store();
		}

		// The completed rebuild was marked for storing despite the throw, so it survives the restart.
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Rec> loaded = loadedMap(storage);
			assertEquals(2, loaded.size(), "both entities must survive the restart");
			assertEquals(0, count(loaded, 5), "the rebuilt index must have been persisted");
			assertEquals(2, count(loaded, 0), "the rebuilt index must have been persisted");
		}
	}

	/**
	 * The documented repair: re-distinguish the colliding keys via {@code apply()} over the reported
	 * (duplicate-laden) unique index, then {@code reindex()} again. Neither step may delete a committed
	 * entity - see internal issue #88 - and the result must be clean and restart-stable.
	 */
	@Test
	@Timeout(120)
	void repairAfterReportedViolationYieldsACleanIndex(@TempDir final Path dir) throws IOException
	{
		final long[] ids = seedAndEvolve(dir, 5, 7);

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Rec> loaded = loadedMap(storage);
			assertThrows(UniqueConstraintViolationExceptionBitmap.class, loaded::reindex);

			// re-distinguish the keys of the entities the rebuilt index reports under the colliding key
			loaded.apply(ids[0], r ->
			{
				r.code = 5;
				return null;
			});
			loaded.apply(ids[1], r ->
			{
				r.code = 7;
				return null;
			});
			assertNotNull(loaded.get(ids[0]), "the repair must not delete a committed entity");
			assertNotNull(loaded.get(ids[1]), "the repair must not delete a committed entity");

			assertDoesNotThrow(loaded::reindex, "the rebuild over repaired data must pass");
			assertEquals(1, count(loaded, 5));
			assertEquals(1, count(loaded, 7));
			assertEquals(0, count(loaded, 0), "no residue may remain under the collided key");

			loaded.store();
		}

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Rec> loaded = loadedMap(storage);
			assertEquals(2, loaded.size(), "both entities must survive the restart");
			assertEquals(5, loaded.get(ids[0]).code);
			assertEquals(7, loaded.get(ids[1]).code);
			assertEquals(1, count(loaded, 5), "the repaired index must have been persisted");
			assertEquals(1, count(loaded, 7), "the repaired index must have been persisted");
			assertEquals(0, count(loaded, 0), "the repaired index must have been persisted");
		}
	}

	/**
	 * The check must not be over-eager: an entity's own entry, re-added during the very same rebuild, is
	 * not a duplicate of itself, so a rebuild over sound data must simply pass.
	 */
	@Test
	@Timeout(120)
	void reindexWithoutCollisionsStillSucceeds(@TempDir final Path dir)
	{
		final GigaMap<Rec> map = GigaMap.New();
		map.index().bitmap().addUniqueConstraint(new CodeIndexer());

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir))
		{
			map.add(new Rec(5));
			map.add(new Rec(7));
			map.add(new Rec(9));

			assertDoesNotThrow(map::reindex, "a rebuild over distinct unique keys must pass");
			assertEquals(3, map.size());
			assertEquals(1, count(map, 5));
			assertEquals(1, count(map, 7));
			assertEquals(1, count(map, 9));

			map.store();
		}
	}

	@SuppressWarnings("unchecked")
	private static GigaMap<Rec> loadedMap(final EmbeddedStorageManager storage)
	{
		return (GigaMap<Rec>)storage.root();
	}

	private static long count(final GigaMap<Rec> map, final int code)
	{
		final Condition<Rec> condition = new CodeIndexer().is(code);

		return map.query(condition).count();
	}

	static final class Rec
	{
		int code;

		Rec(final int code)
		{
			this.code = code;
		}
	}

	/** Binary integer index - required to back a unique constraint. */
	static final class CodeIndexer extends BinaryIndexerInteger.Abstract<Rec>
	{
		@Override
		public String name()
		{
			return "code";
		}

		@Override
		protected Integer getInteger(final Rec entity)
		{
			return entity.code;
		}
	}
}
