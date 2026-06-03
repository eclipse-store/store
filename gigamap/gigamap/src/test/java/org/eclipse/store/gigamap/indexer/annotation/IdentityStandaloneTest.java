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

import org.eclipse.store.gigamap.annotations.Identity;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.gigamap.types.IndexerLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@code @Identity} must function without a companion {@code @Index}.
 */
public class IdentityStandaloneTest
{
	static class Doc
	{
		@Identity
		long id;

		String title;

		Doc(final long id, final String title)
		{
			this.id    = id;
			this.title = title;
		}
	}

	@Test
	void identityWithoutIndexCreatesUsableIndex()
	{
		final GigaMap<Doc> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Doc.class).generateIndices(map.index().bitmap());

		map.add(new Doc(1, "a"));
		map.add(new Doc(2, "b"));

		final IndexerLong<Doc> id = map.index().bitmap().getIndexerLong("id");
		assertNotNull(id, "@Identity alone should generate an index for the property");

		final var result = map.query(id.is(2L)).toList();
		assertEquals(1, result.size());
		assertEquals("b", result.get(0).title);

		// identity is not uniqueness: a duplicate id value is allowed
		map.add(new Doc(2, "c"));
		assertEquals(3, map.size());
	}
}
