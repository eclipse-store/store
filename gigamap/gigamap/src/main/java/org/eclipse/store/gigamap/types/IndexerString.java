package org.eclipse.store.gigamap.types;

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

/**
 * Indexing logic for {@link String} keys.
 * <p>
 * It is optimized for low-cardinality indices, for high-cardinality use {@link BinaryIndexerString}.
 * 
 * @param <E> the entity type
 * 
 * @see Indexer
 */
public interface IndexerString<E> extends Indexer<E, String>
{
	/**
	 * Creates a case-sensitive contains condition.
	 * 
	 * @param search the substring to look for
	 * @return a new condition
	 */
	public <S extends E> Condition<S> contains(String search);
	
	/**
	 * Creates a case-insensitive contains condition.
	 * 
	 * @param search the substring to look for
	 * @return a new condition
	 */
	public <S extends E> Condition<S> containsIgnoreCase(String search);
	
	/**
	 * Creates a case-sensitive starts with condition.
	 * 
	 * @param prefix the prefix to look for
	 * @return a new condition
	 */
	public <S extends E> Condition<S> startsWith(String prefix);
	
	/**
	 * Creates a case-insensitive starts with condition.
	 * 
	 * @param prefix the prefix to look for
	 * @return a new condition
	 */
	public <S extends E> Condition<S> startsWithIgnoreCase(String prefix);
	
	/**
	 * Creates a case-sensitive ends with condition.
	 * 
	 * @param suffix the suffix to look for
	 * @return a new condition
	 */
	public <S extends E> Condition<S> endsWith(String suffix);
	
	/**
	 * Creates a case-insensitive ends with condition.
	 * 
	 * @param suffix the suffix to look for
	 * @return a new condition
	 */
	public <S extends E> Condition<S> endsWithIgnoreCase(String suffix);
	
	/**
	 * Creates a check which returns true if the key is null or an empty string ("").
	 * 
	 * @return a new condition
	 */
	public <S extends E> Condition<S> isEmpty();
	
	/**
	 * Creates a check which returns true if the key is null or an empty string ("") or contains only whitespaces.
	 * 
	 * @return a new condition
	 */
	public <S extends E> Condition<S> isBlank();
	
	
	
	/**
	 * Abstract base class for a {@link String} key {@link Indexer}.
	 * 
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends Indexer.Abstract<E, String> implements IndexerString<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		public final Class<String> keyType()
		{
			return String.class;
		}
		
		@Override
		public final String index(final E entity)
		{
			return this.getString(entity);
		}
		
		protected abstract String getString(E entity);
		
		@Override
		public <S extends E> Condition<S> contains(final String search)
		{
			return this.is(key ->
			{
				if(key == null || search == null)
				{
					return false;
				}
				return key.contains(search);
			});
		}
		
		@Override
		public <S extends E> Condition<S> containsIgnoreCase(final String search)
		{
			return this.is(key ->
			{
				if(key == null || search == null)
				{
					return false;
				}
				final int len = search.length();
				final int max = key.length() - len;
				for(int i = 0; i <= max; i++)
				{
					if(key.regionMatches(true, i, search, 0, len))
					{
						return true;
					}
				}
				return false;
			});
		}
		
		@Override
		public <S extends E> Condition<S> startsWith(final String prefix)
		{
			return this.startsWith(prefix, false);
		}
		
		@Override
		public <S extends E> Condition<S> startsWithIgnoreCase(final String prefix)
		{
			return this.startsWith(prefix, true);
		}
				
		<S extends E> Condition<S> startsWith(final String prefix, final boolean ignoreCase)
		{
			return this.is(key ->
			{
				if(key == null || prefix == null)
				{
					return key == prefix;
				}
				final int prefixLen = prefix.length();
		        if (prefixLen > key.length())
		        {
		            return false;
		        }
		        return key.regionMatches(ignoreCase, 0, prefix, 0, prefixLen);
			});
		}
		
		@Override
		public <S extends E> Condition<S> endsWith(final String suffix)
		{
			return this.endsWith(suffix, false);
		}
		
		@Override
		public <S extends E> Condition<S> endsWithIgnoreCase(final String suffix)
		{
			return this.endsWith(suffix, true);
		}
		
		<S extends E> Condition<S> endsWith(final String suffix, final boolean ignoreCase)
		{
			return this.is(key ->
			{
				if(key == null || suffix == null)
				{
					return key == suffix;
				}
				final int keyLen    = key.length();
				final int suffixLen = suffix.length();
		        if (suffixLen > keyLen)
		        {
		            return false;
		        }
		        return key.regionMatches(ignoreCase, keyLen - suffixLen, suffix, 0, suffixLen);
			});
		}
		
		@Override
		public <S extends E> Condition<S> isEmpty()
		{
			return this.is(key -> key == null || key.isEmpty());
		}
		
		@Override
		public <S extends E> Condition<S> isBlank()
		{
			return this.is(key ->
			{
				if(key == null)
				{
					return true;
				}
				final int len = key.length();
				if(len == 0)
				{
					return true;
				}
				for(int i = 0; i < len; i++)
				{
					if(!Character.isWhitespace(key.charAt(i)))
					{
						return false;
					}
				}
				return true;
			});
		}
		
	}
	
}
