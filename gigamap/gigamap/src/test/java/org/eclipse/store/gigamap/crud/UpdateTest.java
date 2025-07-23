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
import org.eclipse.store.gigamap.types.IndexerLong;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.nio.file.Paths;


public class UpdateTest
{
	public static void main(final String[] args)
	{
		for(int i = 0; i < 10; i++)
		{
			try(final EmbeddedStorageManager storage = EmbeddedStorage.start(Paths.get("target", "update_test")))
			{
				GigaMap<Entity> map = (GigaMap<Entity>)storage.root();
				if(map == null)
				{
					map = GigaMap.New();
					map.index().bitmap().ensure(idIndexer);
					storage.setRoot(map);
					storage.storeRoot();
				}
				
				final long size = map.size();
				System.out.println("size = " + size);
				System.out.println(map.toString(1000));
				if(size == 0)
				{
					map.add(new Entity(size));
					map.store();
				}
				else
				{
					Entity entity = map.query().findFirst().get();
					map.update(entity, e -> e.id++);
					storage.storeAll(map, entity);
				}
				
			}
		}
	}
	
	static IndexerLong<Entity> idIndexer = new IndexerLong.Abstract<Entity>()
	{
		@Override
		protected Long getLong(final Entity entity)
		{
			return entity.id;
		}
	};
	
	
	static class Entity
	{
		long id;
		
		Entity(final long id)
		{
			super();
			this.id = id;
		}
		
		@Override
		public String toString()
		{
			return "Entity{" +
				"id=" + this.id +
				'}';
		}
	}
}
