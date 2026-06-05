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
import org.eclipse.store.gigamap.annotations.Indexed;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IndexedMarkerTest
{
	@Indexed
	static class Product
	{
		@Index String sku;
		@Index int    qty;

		Product(final String sku, final int qty)
		{
			this.sku = sku;
			this.qty = qty;
		}
	}

	static class DuplicateNames
	{
		@Index(name = "code") String a;
		@Index(name = "code") String b;
	}

	@Test
	void generateConvenienceMatchesExplicitForm()
	{
		final GigaMap<Product> map = GigaMap.New();
		IndexerGenerator.generate(Product.class, map);

		map.add(new Product("A-1", 5));
		map.add(new Product("B-2", 7));

		final IndexerString<Product> sku = map.index().bitmap().getIndexerString("sku");
		assertNotNull(sku);
		assertEquals(1, map.query(sku.is("A-1")).toList().size());
		assertNotNull(map.index().bitmap().getIndexerInteger("qty"));
	}

	@Test
	void duplicateExplicitIndexNamesAreRejected()
	{
		final GigaMap<DuplicateNames> map = GigaMap.New();
		final IllegalStateException ex = assertThrows(
			IllegalStateException.class,
			() -> IndexerGenerator.AnnotationBased(DuplicateNames.class).generateIndices(map)
		);
		assertTrue(ex.getMessage().contains("Double index name"));
	}
}
