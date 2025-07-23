package org.eclipse.store.gigamap.lucene;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
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

import org.eclipse.store.gigamap.misc.it.GigaMapTestBase;
import org.eclipse.store.gigamap.types.GigaMap;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public abstract class GigaMapTestBaseLucene<T> extends GigaMapTestBase<T>
{
	private LuceneIndex<T> luceneIndex;

	protected GigaMapTestBaseLucene(final long testDataAmount)
	{
		super(testDataAmount);
	}
	
	@Override
	protected final void createIndices(final GigaMap<T> gigaMap)
	{
		this.luceneIndex = gigaMap.index().register(LuceneIndex.Category(
			LuceneContext.New(
//				Paths.get("lucene"),
				DirectoryCreator.ByteBuffers(),
				this.createDocumentPopulator()
			)
		));
	}
	
	protected abstract DocumentPopulator<T> createDocumentPopulator();
	
	protected void testQuery(final String title, final String query)
	{
		assertDoesNotThrow(() -> this.testQueryImpl(title, query));
	}
	
	private void testQueryImpl(final String title, final String query)
	{
		final long    start  = System.currentTimeMillis();
		final int     limit  = 10;
		
		final List<T> result = new ArrayList<>();
		this.luceneIndex.query(query, limit, (id, entity, score) -> result.add(entity));
		
		final long duration = System.currentTimeMillis() - start;

		System.out.println();
		System.out.println("Query: " + title);

		result.forEach(System.out::println);
		
		System.out.println("Took " + duration);
	}
	
}
