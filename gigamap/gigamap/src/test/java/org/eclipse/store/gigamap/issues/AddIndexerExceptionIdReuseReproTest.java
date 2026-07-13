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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproducer for internal issue #94: add() had no index rollback on indexer exceptions
 * (unlike addAll()). A first index is updated, a second (user) indexer throws, the entity
 * id is not consumed — and the next add() reused the same entityId, binding the first
 * index's already-written entries to a DIFFERENT entity. The corruption was persisted by
 * the next store() because the partially-updated index was already state-change marked.
 * <p>
 * Adopted from the reproducer by zdenek-jonas posted on the issue; kept verbatim so the
 * fix (rollback in {@code GigaMap.add} plus id-burning when the cleanup itself fails) is
 * verified against the reporter's exact scenario.
 */
public class AddIndexerExceptionIdReuseReproTest
{
	@Test
	@Timeout(60)
	void failedAddMustNotLeakIndexEntriesToNextEntity()
	{
		final GigaMap<Item> map = GigaMap.New();
		final ValueIndexer valueIndexer = new ValueIndexer();
		map.index().bitmap().add(valueIndexer);          // updated first
		map.index().bitmap().add(new ThrowingIndexer()); // throws for "bad" afterwards

		assertThrows(RuntimeException.class, () -> map.add(new Item("bad")),
			"the poisoned entity must be rejected");

		final long idGood = map.add(new Item("good"));

		// The failed add must leave no trace: querying the FIRST index for the failed
		// entity's key must return nothing. On the bug, the stale entry for "bad"
		// (written before the second indexer threw) now resolves to the reused id of "good".
		final List<Item> hits = new ArrayList<>();
		map.query(valueIndexer.is("bad")).forEach(hits::add);

		assertTrue(hits.isEmpty(),
			"failed add leaked an index entry: query for the rejected key returned " + hits.size()
			+ " entity/ies (the reused entityId " + idGood + " now answers under the wrong key)");
		assertEquals("good", map.get(idGood).value);
	}

	static final class Item
	{
		final String value;

		Item(final String value)
		{
			this.value = value;
		}
	}

	static final class ValueIndexer extends IndexerString.Abstract<Item>
	{
		@Override
		protected String getString(final Item entity)
		{
			return entity.value;
		}
	}

	static final class ThrowingIndexer extends IndexerString.Abstract<Item>
	{
		@Override
		protected String getString(final Item entity)
		{
			if("bad".equals(entity.value))
			{
				throw new RuntimeException("user indexer failure for: " + entity.value);
			}
			return entity.value;
		}
	}
}
