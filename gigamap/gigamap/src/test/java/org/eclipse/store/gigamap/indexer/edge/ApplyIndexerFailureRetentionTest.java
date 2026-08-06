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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression coverage for internal issue #119: {@code apply()}/{@code update()} used to DELETE a
 * committed entity whenever an indexer threw while indexing the new value. The entity's own data is
 * the value the update logic produced and is perfectly valid; only the derived index rejected it, yet
 * the destructive exception fallback removed the committed entity and the next {@code store()}
 * persisted that loss.
 * <p>
 * The trigger is mundane and index-family independent - any value an indexer cannot encode: an
 * unexpected format in a custom indexer's key extraction, a computed key that overflows, a Lucene term
 * over the 32766-byte limit, an embedding of the wrong dimension. A stock {@link IndexerString} whose
 * key extraction throws is enough to reproduce it, which is what this test uses.
 * <p>
 * {@code set(id, entity)} never shared that behavior - it updates the indices BEFORE overwriting the
 * storage slot, so an indexer throw leaves the entity in its prior committed state. The two mutation
 * APIs therefore used to disagree on the exact same failing input; the parity is asserted here.
 */
public class ApplyIndexerFailureRetentionTest
{
	static class Doc
	{
		String key;

		Doc(final String key)
		{
			this.key = key;
		}
	}

	/** Stock bitmap indexer whose key extraction throws for the poison value. */
	static final class KeyIndexer extends IndexerString.Abstract<Doc>
	{
		static final String POISON = "POISON";

		@Override
		public String name()
		{
			return "key";
		}

		@Override
		protected String getString(final Doc entity)
		{
			if(POISON.equals(entity.key))
			{
				throw new IllegalStateException("indexer cannot derive a key for this value");
			}
			return entity.key;
		}
	}

	private static GigaMap<Doc> newIndexedMap()
	{
		final GigaMap<Doc> map = GigaMap.New();
		map.index().bitmap().add(new KeyIndexer());
		return map;
	}

	@Test
	@Timeout(60)
	void updateWhoseIndexerThrowsRetainsTheCommittedEntity()
	{
		final GigaMap<Doc> map = newIndexedMap();

		final Doc  doc = new Doc("valid");
		final long id  = map.add(doc);
		assertEquals(1, map.size());

		assertThrows(IllegalStateException.class, () -> map.update(id, e -> e.key = KeyIndexer.POISON));

		// a committed entity must not be destroyed because an indexer rejected the NEW value
		assertSame(doc, map.get(id), "the committed entity must survive an indexer failure");
		assertEquals(1, map.size());
		assertEquals(KeyIndexer.POISON, doc.key, "the in-place mutation is kept");
	}

	@Test
	@Timeout(60)
	void setWithTheSameFailingInputBehavesLikeUpdate()
	{
		final GigaMap<Doc> map = newIndexedMap();

		final Doc  doc = new Doc("valid");
		final long id  = map.add(doc);

		// set() fails before the slot is overwritten, so the prior committed value stays in place
		assertThrows(IllegalStateException.class, () -> map.set(id, new Doc(KeyIndexer.POISON)));

		assertSame(doc, map.get(id), "set() must not delete the entity on an indexer failure");
		assertEquals(1, map.size());
		assertEquals("valid", doc.key, "set() failure must leave the prior committed value intact");
	}

	@Test
	@Timeout(60)
	void retainedEntityAndItsMutationSurviveStoreAndRestart(@TempDir final Path dir)
	{
		final long id;
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(newIndexedMap(), dir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Doc> map = (GigaMap<Doc>)storage.root();

			id = map.add(new Doc("valid"));
			map.store(); // the entity is now committed and durable

			assertThrows(IllegalStateException.class, () -> map.update(id, e -> e.key = KeyIndexer.POISON));

			map.store(); // persist whatever state apply() left behind
		}

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.<Doc>New(), dir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Doc> map = (GigaMap<Doc>)storage.root();

			final Doc reloaded = map.get(id);
			assertNotNull(reloaded, "the committed entity must survive the restart");
			assertEquals(1, map.size());
			assertEquals(KeyIndexer.POISON, reloaded.key, "the retained mutation must be persisted, too");
		}
	}
}
