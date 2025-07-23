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

import com.github.javafaker.Faker;
import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.Condition;
import org.eclipse.store.gigamap.types.IndexerByte;
import org.junit.jupiter.api.Test;

public class GigaMapTestProduct extends GigaMapTestBaseBitmapIndex<Product>
{
	private final ProductPriceRangeIndexer indexPriceRange = new ProductPriceRangeIndexer();
	
	public GigaMapTestProduct()
	{
		super(300);
	}
	
	@Override
	protected void createIndices(final BitmapIndices<Product> indices)
	{
		indices.add(this.indexPriceRange);
	}
	
	@Override
	protected Product createEntity(final Faker faker, final long index)
	{
		// generates 100 products per price range
		return new Product(
			faker.commerce().productName(),
			index < 100
				? faker.number().randomDouble(2, 1, ProductPriceRangeIndexer.MID_START - 1)
				: index < 200
					? faker.number().randomDouble(2, ProductPriceRangeIndexer.MID_START, ProductPriceRangeIndexer.HIGH_START - 1)
					: faker.number().randomDouble(2, ProductPriceRangeIndexer.HIGH_START, ProductPriceRangeIndexer.HIGH_START * 2)
		);
	}
	
	@Test
	void testQueryPriceRange()
	{
		this.testQueryResultCount(
			this.gigaMap().query(this.indexPriceRange.isMid()),
			100
		);
	}
	
	
	static class ProductPriceRangeIndexer extends IndexerByte.Abstract<Product>
	{
		static int MID_START  =  100;
		static int HIGH_START = 1000;
		
		static byte LOW  = 0;
		static byte MID  = 1;
		static byte HIGH = 2;
		
		@Override
		protected Byte getByte(Product entity)
		{
			if(entity.price() >= HIGH_START)
			{
				return HIGH;
			}
			if(entity.price() >= MID_START)
			{
				return MID;
			}
			return LOW;
		}
		
		public Condition<Product> isLow()
		{
			return this.is(LOW);
		}
		
		public Condition<Product> isMid()
		{
			return this.is(MID);
		}
		
		public Condition<Product> isHigh()
		{
			return this.is(HIGH);
		}
		
	}
	
}
