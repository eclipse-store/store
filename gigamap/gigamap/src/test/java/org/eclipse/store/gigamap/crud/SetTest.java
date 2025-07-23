
package org.eclipse.store.gigamap.crud;

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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;


public class SetTest
{
	@TempDir
	Path tempDir;
	
	@Test
	void setTest()
	{
		final GigaMap<Item> gigaMap      = GigaMap.New();
		final OrderIndexer  orderIndexer = new OrderIndexer();
		gigaMap.index().bitmap().add(orderIndexer);
		
		final Item item1 = new Item("item1", 1);
		final Item item2 = new Item("item2", 2);
		final Item item3 = new Item("item3", 3);
		
		gigaMap.addAll(item1, item2, item3); // Item1, Item2, Item3
		
		final Item item4         = new Item("item4", 4);
		
		final Item replacedItem1 = gigaMap.set(0, item4); // Item4, Item2, Item3
		assertSame(item1, replacedItem1);
		
		final Item item5 = new Item("item5", 5);
		
		try(EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, this.tempDir))
		{
			assertEquals(item4, gigaMap.get(0));
			final Item replacedItem2 = gigaMap.set(0, item5); // Item5, Item2, Item3
			assertSame(item4, replacedItem2);
			gigaMap.store();
		}
		
		try(EmbeddedStorageManager storageManager = EmbeddedStorage.start(this.tempDir))
		{
			final GigaMap<Item> loadedGigaMap = (GigaMap<Item>)storageManager.root();
			loadedGigaMap.forEach(System.out::println); // just for debugging
			assertEquals(item5, loadedGigaMap.get(0)); // <=== is still Item4, Item2, Item3
			
		}
		
	}
	
	private static class OrderIndexer extends IndexerInteger.Abstract<Item>
	{
		@Override
		protected Integer getInteger(final Item entity)
		{
			return entity.getOrder();
		}
	}
	
	private static class Item
	{
		private String  name;
		private Integer order;
		
		public Item(final String name, final Integer order)
		{
			this.name  = name;
			this.order = order;
		}
		
		public String getName()
		{
			return this.name;
		}
		
		public void setName(final String name)
		{
			this.name = name;
		}
		
		public Integer getOrder()
		{
			return this.order;
		}
		
		public void setOrder(final Integer order)
		{
			this.order = order;
		}
		
		@Override
		public int hashCode()
		{
			return Objects.hash(this.name, this.order);
		}
		
		@Override
		public boolean equals(final Object obj)
		{
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(this.getClass() != obj.getClass())
				return false;
			final Item other = (Item)obj;
			return Objects.equals(this.name, other.name) && Objects.equals(this.order, other.order);
		}
		
		@Override
		public String toString()
		{
			return "Item{" + this.hashCode() +
				"name='" + this.name + '\'' +
				", order=" + this.order +
				'}';
		}
	}
}
