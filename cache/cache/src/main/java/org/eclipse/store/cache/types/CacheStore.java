
package org.eclipse.store.cache.types;

/*-
 * #%L
 * EclipseStore Cache
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

import static org.eclipse.serializer.chars.XChars.notEmpty;
import static org.eclipse.serializer.util.X.notNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.cache.Cache.Entry;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import javax.cache.integration.CacheWriter;
import javax.cache.integration.CacheWriterException;

import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.collections.types.XTable;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.types.StorageManager;


public interface CacheStore<K, V> extends CacheLoader<K, V>, CacheWriter<K, V>
{
	public Iterator<K> keys();
	
	
	public static interface TableProvider
	{
		public <K, V> XTable<K, Lazy<V>> provideTable(StorageManager storage, boolean create);
		
		
		public static TableProvider Root(final String cacheKey)
		{
			return new TableProvider.Root(notEmpty(cacheKey));
		}
		
		public static TableProvider LazyRoot(final String cacheKey)
		{
			return new TableProvider.LazyRoot(notEmpty(cacheKey));
		}
		
		
		public static class Root implements TableProvider
		{
			private final String cacheKey;
			
			Root(final String cacheKey)
			{
				super();
				this.cacheKey = cacheKey;
			}

			@SuppressWarnings("unchecked")
			@Override
			public <K, V> XTable<K, Lazy<V>> provideTable(final StorageManager storage, final boolean create)
			{
				boolean                                  storeRoot = false;
				XTable<String, Lazy<XTable<K, Lazy<V>>>> rootTable;
				if((rootTable = (XTable<String, Lazy<XTable<K, Lazy<V>>>>)storage.root()) == null)
				{
					storage.setRoot(rootTable = EqHashTable.New());
					storeRoot = true;
				}
				XTable<K, Lazy<V>> cacheTable;
				if((cacheTable = Lazy.get(rootTable.get(this.cacheKey))) == null && create)
				{
					rootTable.put(this.cacheKey, Lazy.Reference(cacheTable = EqHashTable.New()));
					storeRoot = true;
				}
				if(storeRoot)
				{
					storage.storeRoot();
				}
				return cacheTable;
			}
			
		}
		
		
		public static class LazyRoot implements TableProvider
		{
			private final String cacheKey;
			
			LazyRoot(final String cacheKey)
			{
				super();
				this.cacheKey = cacheKey;
			}
			
			@SuppressWarnings({"unchecked", "rawtypes"})
			@Override
			public <K, V> XTable<K, Lazy<V>> provideTable(final StorageManager storage, final boolean create)
			{
				boolean                                  storeRoot = false;
				XTable<String, Lazy<XTable<K, Lazy<V>>>> rootTable;
				if((rootTable = (XTable<String, Lazy<XTable<K, Lazy<V>>>>)Lazy.get((Lazy)storage.root())) == null)
				{
					storage.setRoot(Lazy.Reference(rootTable = EqHashTable.New()));
					storeRoot = true;
				}
				XTable<K, Lazy<V>> cacheTable;
				if((cacheTable = Lazy.get(rootTable.get(this.cacheKey))) == null && create)
				{
					rootTable.put(this.cacheKey, Lazy.Reference(cacheTable = EqHashTable.New()));
					storeRoot = true;
				}
				if(storeRoot)
				{
					storage.storeAll(storage.root(), rootTable);
				}
				return cacheTable;
			}
			
		}
		
	}
	
	
	public static <K, V> CacheStore<K, V> New(final String cacheKey, final StorageManager storage)
	{
		return new Default<>(
			TableProvider.Root(cacheKey),
			notNull(storage)
		);
	}
	
	
	public static <K, V> CacheStore<K, V> New(final TableProvider tableProvider, final StorageManager storage)
	{
		return new Default<>(
			notNull(tableProvider),
			notNull(storage)
		);
	}
	
	
	public static class Default<K, V> implements CacheStore<K, V>
	{
		private final TableProvider  tableProvider;
		private final StorageManager storage      ;
		
		Default(final TableProvider tableProvider, final StorageManager storage)
		{
			super();

			this.tableProvider = tableProvider;
			this.storage       = storage      ;
		}
		
		private XTable<K, Lazy<V>> table(final boolean create)
		{
			synchronized(this.storage)
			{
				if(!this.storage.isRunning())
				{
					this.storage.start();
				}
				
				return this.tableProvider.provideTable(this.storage, create);
			}
		}
		
		@Override
		public synchronized Iterator<K> keys()
		{
			final XTable<K, Lazy<V>> table = this.table(false);
			return table != null
				? table.keys().iterator()
				: Collections.emptyIterator()
			;
		}
		
		@Override
		public synchronized V load(final K key) throws CacheLoaderException
		{
			try
			{
				final XTable<K, Lazy<V>> table;
				return (table = this.table(false)) != null
					? Lazy.get(table.get(key))
					: null;
			}
			catch(final Exception e)
			{
				throw new CacheLoaderException(e);
			}
		}
		
		@Override
		public synchronized Map<K, V> loadAll(final Iterable<? extends K> keys) throws CacheLoaderException
		{
			try
			{
				final Map<K, V>          result = new HashMap<>();
				final XTable<K, Lazy<V>> table;
				if((table = this.table(false)) != null)
				{
					keys.forEach(key -> result.put(key, Lazy.get(table.get(key))));
				}
				return result;
			}
			catch(final Exception e)
			{
				throw new CacheLoaderException(e);
			}
		}
		
		@Override
		public synchronized void write(final Entry<? extends K, ? extends V> entry) throws CacheWriterException
		{
			try
			{
				final XTable<K, Lazy<V>> table = this.table(true);
				table.put(entry.getKey(), Lazy.Reference(entry.getValue()));
				this.storage.store(table);
			}
			catch(final Exception e)
			{
				throw new CacheWriterException(e);
			}
		}
		
		@Override
		public synchronized void writeAll(final Collection<Entry<? extends K, ? extends V>> entries)
			throws CacheWriterException
		{
			try
			{
				final XTable<K, Lazy<V>> table = this.table(true);
				entries.forEach(entry -> table.put(entry.getKey(), Lazy.Reference(entry.getValue())));
				this.storage.store(table);
			}
			catch(final Exception e)
			{
				throw new CacheWriterException(e);
			}
		}
		
		@SuppressWarnings("unchecked") // Object in typed interface [sigh]
		@Override
		public synchronized void delete(final Object key) throws CacheWriterException
		{
			try
			{
				final XTable<K, Lazy<V>> table;
				if((table = this.table(false)) != null
					&& table.removeFor((K)key) != null)
				{
					this.storage.store(table);
				}
			}
			catch(final Exception e)
			{
				throw new CacheWriterException(e);
			}
		}
		
		@Override
		public synchronized void deleteAll(final Collection<?> keys) throws CacheWriterException
		{
			try
			{
				final XTable<K, Lazy<V>> table;
				if((table = this.table(false)) != null)
				{
					boolean           changed  = false;
					final Iterator<?> iterator = keys.iterator();
					while(iterator.hasNext())
					{
						@SuppressWarnings("unchecked")
						final K key = (K)iterator.next();
						if(table.removeFor(key) != null)
						{
							iterator.remove();
							changed = true;
						}
					}
					if(changed)
					{
						this.storage.store(table);
					}
				}
			}
			catch(final Exception e)
			{
				throw new CacheWriterException(e);
			}
		}
		
	}
	
}
