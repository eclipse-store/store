
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

import org.eclipse.serializer.typing.KeyValue;


public interface CacheEntry<K, V> extends javax.cache.Cache.Entry<K, V>, KeyValue<K, V>, Unwrappable
{
	@Override
	public default K key()
	{
		return this.getKey();
	}
	
	@Override
	public default V value()
	{
		return this.getValue();
	}
	
	@Override
	public default <T> T unwrap(final Class<T> clazz)
	{
		return Unwrappable.Static.unwrap(this, clazz);
	}
	
	static <K, V> CacheEntry<K, V> New(final K key, final V value)
	{
		return new Default<>(key, value);
	}

	public static class Default<K, V> implements CacheEntry<K, V>
	{
		private final K key  ;
		private final V value;

		Default(final K key, final V value)
		{
			super();

			this.key   = key  ;
			this.value = value;
		}

		@Override
		public K getKey()
		{
			return this.key;
		}

		@Override
		public V getValue()
		{
			return this.value;
		}

	}
	
}
