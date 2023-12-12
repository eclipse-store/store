package org.eclipse.store.cache.hibernate.types;

/*-
 * #%L
 * EclipseStore Cache for Hibernate
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.eclipse.serializer.util.X.notNull;

import javax.cache.CacheException;

import org.eclipse.store.cache.types.Cache;
import org.hibernate.cache.spi.support.DomainDataStorageAccess;
import org.hibernate.engine.spi.SharedSessionContractImplementor;


public interface StorageAccess extends DomainDataStorageAccess
{
	public static StorageAccess New(
		final Cache<Object, Object> cache
	)
	{
		return new Default(notNull(cache));
	}
	
	
	public static class Default implements StorageAccess
	{
		private final Cache<Object, Object> cache;
		
		Default(
			final Cache<Object, Object> cache
		)
		{
			super();
			this.cache = cache;
		}
		
		@Override
		public Object getFromCache(
			final Object key,
			final SharedSessionContractImplementor session
		)
		{
			try
			{
				return this.cache.get(key);
			}
			catch(CacheException e)
			{
				throw new org.hibernate.cache.CacheException(e);
			}
		}
		
		@Override
		public void putIntoCache(
			final Object key,
			final Object value,
			final SharedSessionContractImplementor session
		)
		{
			try
			{
				this.cache.put(key, value);
			}
			catch(CacheException e)
			{
				throw new org.hibernate.cache.CacheException(e);
			}
		}
		
		@Override
		public boolean contains(
			final Object key
		)
		{
			try
			{
				return this.cache.containsKey(key);
			}
			catch(CacheException e)
			{
				throw new org.hibernate.cache.CacheException(e);
			}
		}
		
		@Override
		public void evictData()
		{
			try
			{
				this.cache.removeAll();
			}
			catch(CacheException e)
			{
				throw new org.hibernate.cache.CacheException(e);
			}
		}
		
		@Override
		public void evictData(
			final Object key
		)
		{
			try
			{
				this.cache.remove(key);
			}
			catch(CacheException e)
			{
				throw new org.hibernate.cache.CacheException(e);
			}
		}
		
		@Override
		public void release()
		{
			try
			{
				this.cache.close();
			}
			catch(CacheException e)
			{
				throw new org.hibernate.cache.CacheException(e);
			}
		}
		
	}
	
}
