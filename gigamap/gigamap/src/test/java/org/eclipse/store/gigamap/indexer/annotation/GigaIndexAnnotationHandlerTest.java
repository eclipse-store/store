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
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GigaIndexAnnotationHandlerTest
{
	static class Foo
	{
		@Index String name;

		Foo(final String name)
		{
			this.name = name;
		}
	}

	@Test
	void handlersRunInOrderAfterBitmapGeneration()
	{
		final GigaMap<Foo>   map   = GigaMap.New();
		final List<String>   calls = new ArrayList<>();

		IndexerGenerator.AnnotationBased(Foo.class)
			.register((type, indices) -> {
				assertEquals(Foo.class, type);
				// bitmap indices must already be generated when handlers are invoked
				assertNotNull(indices.bitmap().getIndexerString("name"));
				calls.add("h1");
			})
			.register((type, indices) -> calls.add("h2"))
			.generateIndices(map);

		assertEquals(List.of("h1", "h2"), calls);

		map.add(new Foo("x"));
		assertEquals(1, map.query(map.index().bitmap().getIndexerString("name").is("x")).toList().size());
	}

	@Test
	void gigaMapOverloadWithoutHandlersGeneratesBitmapIndices()
	{
		final GigaMap<Foo> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Foo.class).generateIndices(map);

		map.add(new Foo("y"));
		assertNotNull(map.index().bitmap().getIndexerString("name"));
		assertEquals(1, map.query(map.index().bitmap().getIndexerString("name").is("y")).toList().size());
	}
}
