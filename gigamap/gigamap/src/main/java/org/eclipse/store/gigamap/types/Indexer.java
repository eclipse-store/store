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

import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.hashing.HashEqualator;
import org.eclipse.serializer.hashing.XHashing;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.stream.Collectors;

// may NOT be a functional interface to avoid unpersistable lambda instances getting used.

/**
 * Indexing logic which extracts the key value of the entity which is stored in the {@link Index}.
 * <p>
 * The equality of the keys is determined by {@link #hashEqualator()},
 * which uses {@link Object#hashCode()} and {@link Object#equals(Object)} by default.
 * <p>
 * If you are looking for indexers which are optimized for high cardinality, see {@link BinaryIndexer}.
 * 
 * @param <E> the entity type
 * @param <K> the key type
 * @see #index(Object)
 *
 * @see BinaryIndexer
 * @see CompositeIndexer
 */
public interface Indexer<E, K> extends IndexIdentifier<E, K>
{
	@Override
	public String name();
	
	@Override
	public Class<K> keyType();
	
	/**
	 * {@inheritDoc}
	 * 
	 * @return this
	 */
	@Override
	public default Indexer<? super E, K> indexer()
	{
		return this;
	}
	
	/**
	 * Get the {@link HashEqualator} which is used to determine the equality of keys.
	 * 
	 * @return the used {@link HashEqualator}
	 */
	public default HashEqualator<? super K> hashEqualator()
	{
		return defaultHashEqualator();
	}
	
	public static HashEqualator<? super Object> defaultHashEqualator()
	{
		// keys have value equality probably almost always
		return XHashing.hashEqualityValue();
	}
		
	/**
	 * Extract the value of the entity which is stored in the {@link Index}.
	 * 
	 * @param entity the source entity
	 * @return the indexed value
	 */
	public K index(E entity);
	
	/**
	 * Creates the index which uses this {@link Indexer} as key provider.
	 * 
	 * @param <T> the entity type
	 * @param parent the parent index
	 * @return a newly created index
	 */
	public default <T extends E> BitmapIndex.Internal<T, K> createFor(final BitmapIndices<T> parent)
	{
		final String name = this.name();
		return this.keyType() == Boolean.class
			? SingleBitmapIndex.internalCreate(parent, name, this)
			: new HashingBitmapIndex<>(parent, name, this)
		;
	}
	
	
	/**
	 * This interface acts as a factory for creating instances of the {@code Indexer} class with specific
	 * types for entities and keys.
	 *
	 * @param <E> the type of entities to be indexed
	 * @param <K> the type of keys used for indexing entities
	 */
	public static interface Creator<E, K>
	{
		/**
		 * Creates an instance of an {@code Indexer} using the specified types for entities and keys.
		 *
		 * @return a new instance of {@code Indexer<E, K>} configured according to the implementation
		 */
		public Indexer<E, K> create();
		
		
		static class Dummy<E, K> implements Creator<E, K>
		{
			@Override
			public Indexer<E, K> create()
			{
				return null;
			}
		}
	}
	
	
	/**
	 * An abstract base class that implements the {@link Indexer} interface for managing
	 * indexed entities with associated keys.
	 * <p>
	 * This class provides a default behavior for obtaining a name, which can be either
	 * explicitly set or derived dynamically using reflection if not available. The name
	 * is used as a unique identifier for indexing operations.
	 *
	 * @param <E> the entity type
	 * @param <K> the key type
	 */
	public abstract class Abstract<E, K> implements Indexer<E, K>
	{
		private transient String name;
		
		@Override
		public String name()
		{
			if(this.name == null)
			{
				this.name = this.defaultName();
			}
			
			return this.name;
		}
		
		private String defaultName()
		{
			final Class<?> clazz = this.getClass();

			// try to get declaring constant's name if present
			final Class<?> outer = clazz.getEnclosingClass();
			if(outer != null)
			{
				for(final Field field : outer.getDeclaredFields())
				{
					final int modifiers = field.getModifiers();
					if(Modifier.isStatic(modifiers))
					{
						if(field.trySetAccessible())
						{
							try
							{
								final Object value = field.get(null);
								if(value == this)
								{
									return VarString.New()
										.add(outer.getCanonicalName())
										.add('.')
										.add(field.getName())
										.toString()
									;
								}
							}
							catch(final IllegalArgumentException | IllegalAccessException e)
							{
								// ignore
							}
						}
					}
				}
			}
			
			if(clazz.getCanonicalName() == null)
			{
				Class<?> named = clazz;
				while(XChars.isEmpty(named.getCanonicalName()))
				{
					named = named.getEnclosingClass();
				}
				final VarString vs = VarString.New();
				vs.add(named.getCanonicalName());
				final Method enclosingMethod = clazz.getEnclosingMethod();
				if(enclosingMethod != null)
				{
					vs.add('#').add(enclosingMethod.getName()).add('#');
				}
				else
				{
					vs.add('.');
				}
				if(clazz.isAnonymousClass())
				{
					vs.add(this.getSimpleNameWithOuter(clazz.getSuperclass()));
				}
				else
				{
					vs.add(clazz.getSimpleName());
				}
				return vs.toString();
			}
			
			return clazz.getCanonicalName();
		}
		
		private String getSimpleNameWithOuter(final Class<?> clazz)
		{
			Class<?> outer = clazz.getDeclaringClass();
			if(outer == null)
			{
				return clazz.getSimpleName();
			}
			
			final LinkedList<Class<?>> list = new LinkedList<>();
			list.add(clazz);
			while(outer != null)
			{
				list.addFirst(outer);
				outer = outer.getDeclaringClass();
			}
			return list.stream()
				.map(Class::getSimpleName)
				.collect(Collectors.joining("."))
			;
		}
	}
		
}
