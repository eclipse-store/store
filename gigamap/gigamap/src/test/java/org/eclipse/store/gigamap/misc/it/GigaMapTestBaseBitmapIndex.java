package org.eclipse.store.gigamap.misc.it;

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

import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.GigaQuery;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class GigaMapTestBaseBitmapIndex<T> extends GigaMapTestBase<T>
{
	protected GigaMapTestBaseBitmapIndex(final long testDataAmount)
	{
		super(testDataAmount);
	}

	@Override
	protected void createIndices(final GigaMap<T> gigaMap)
	{
		this.createIndices(
			gigaMap.index().bitmap()
		);
	}
	
	protected abstract void createIndices(final BitmapIndices<T> indices);
	
	protected void testQueryResultCount(final GigaQuery<T> query, final Predicate<T> tester)
	{
		final Counter expectedResultCount = new Counter();
		this.gigaMap().iterate(entity ->
		{
			if(tester.test(entity))
			{
				expectedResultCount.increment();
			}
		});

		this.testQueryResultCount(query, expectedResultCount.get());
	}
	
	protected void testQueryResultCount(final GigaQuery<T> query, final long expectedCount)
	{
//		final Counter iterationResultCount = new Counter();
//		query.iterator().forEachRemaining(entity -> iterationResultCount.increment());
		
		final Counter executionResultCount = new Counter();
		query.execute((final T entity) -> executionResultCount.increment());

//		assertEquals(expectedCount, iterationResultCount.get(), () -> "Deviating iteration result count");
		assertEquals(expectedCount, executionResultCount.get(), () -> "Deviating execution result count");
	}
	
}
