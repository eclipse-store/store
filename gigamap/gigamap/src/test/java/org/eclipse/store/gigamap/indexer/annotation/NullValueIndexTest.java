package org.eclipse.store.gigamap.indexer.annotation;

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

import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.store.gigamap.annotations.Unique;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NullValueIndexTest
{
	enum Color
	{
		RED, GREEN, BLUE
	}

	static class UniqueId
	{
		@Index @Unique Long id;

		UniqueId(final Long id)
		{
			this.id = id;
		}
	}

	static class Painted
	{
		@Index Color color;

		Painted(final Color color)
		{
			this.color = color;
		}
	}

	@Test
	void nullBinaryKeyFailsWithClearMessageNotNpe()
	{
		final GigaMap<UniqueId> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(UniqueId.class).generateIndices(map.index().bitmap());

		final IllegalArgumentException ex = assertThrows(
			IllegalArgumentException.class,
			() -> map.add(new UniqueId(null))
		);
		assertTrue(ex.getMessage().contains("Null keys are not allowed"));
	}

	@Test
	void nullEnumValueIsIndexedWithoutError()
	{
		final GigaMap<Painted> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Painted.class).generateIndices(map.index().bitmap());

		map.add(new Painted(null));
		map.add(new Painted(Color.RED));

		final Indexer<Painted, Color> index = map.index().bitmap().getIndexerForKey(Color.class, "color");
		final List<Painted> red = map.query(index.is(Color.RED)).toList();
		assertEquals(1, red.size());
		assertEquals(Color.RED, red.get(0).color);

		assertEquals(2, map.size());
	}
}
