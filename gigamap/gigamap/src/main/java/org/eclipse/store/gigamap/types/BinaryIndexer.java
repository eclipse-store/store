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

import org.eclipse.serializer.util.X;

import java.util.function.Predicate;


/**
 * {@link Indexer} type for use in a long-based binary key {@link BitmapIndex} implementation.
 * <p>
 * This index is optimized for high cardinality, like unique indices.
 * <p>
 * Current limitation are:
 * <ul>
 * <li>null is not allowed</li>
 * <li>only <code>equality/is</code> conditions are supported</li>
 * </ul>
 *
 * @param <E> the entity type
 *
 * @see CompositeIndexer
 */
public interface BinaryIndexer<E> extends Indexer<E, Long>
{
	@Override
	public default Long index(final E entity)
	{
		return this.indexBinary(entity);
	}
	
	/**
	 * Indexes the given entity using a binary approach and returns the associated long identifier.
	 *
	 * @param entity the entity to be indexed, must not be null
	 * @return the long identifier corresponding to the given entity
	 */
	public long indexBinary(E entity);
	
	/**
	 * Creates an equality condition for the given key. This condition checks whether
	 * the key extracted by this index is equal to the specified key.
	 *
	 * @param <S> the type of entity this condition applies to, extending the base entity type
	 * @param key the key to compare for equality, not negative
	 * @return a new condition representing the equality check for the given key
	 * @throws IllegalArgumentException if the key is negative
	 */
	@Override
	public default <S extends E> Condition<S> is(final Long key)
	{
		Static.validate(key, this);
		
		return Indexer.super.is(key);
	}
	
	@Override
	public default <S extends E> Condition<S> in(final Long... keys)
	{
		for(final Long key : keys)
		{
			Static.validate(key, this);
		}
		
		return Indexer.super.in(keys);
	}
	
	/**
	 * Throws an {@link UnsupportedOperationException}.
	 * <p>
	 * Predicate-based search is not implemented yet.
	 * </p>
	 *
	 * @param <S> the type of entity extending from E
	 * @param keyPredicate a predicate to test the long-based key
	 * @return a condition that matches entities whose long keys satisfy the given predicate
	 */
	@Override
	public default <S extends E> Condition<S> is(final Predicate<? super Long> keyPredicate)
	{
		throw new UnsupportedOperationException();
	}
	
	@Override
	public default Class<Long> keyType()
	{
		return Long.class;
	}
	
	@Override
	public default <T extends E> BitmapIndex.Internal<T, Long> createFor(final BitmapIndices<T> parent)
	{
		return new BinaryBitmapIndex<>(parent, this.name(), this);
	}
	
	
	public static class Static
	{
		static void validate(final Long key, final BinaryIndexer<?> indexer)
		{
			if(key == null)
			{
				throw new IllegalArgumentException("Null keys are not allowed in index " + indexer.name());
			}
		}
		
	}
	
	
	
	public abstract class Abstract<E> extends Indexer.Abstract<E, Long> implements BinaryIndexer<E>
	{
		protected Abstract()
		{
			super();
		}
		
	}
	
	
	public static <E> BinaryIndexer<E> Wrap(final Indexer<E, Long> indexer)
	{
		return new Wrapper<>(
			X.notNull(indexer)
		);
	}
	
	public final class Wrapper<E> extends Abstract<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final Indexer<E, Long> subject;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Wrapper(final Indexer<E, Long> subject)
		{
			super();
			this.subject = subject;
		}

		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public String name()
		{
			return this.subject.name();
		}
		
		@Override
		public long indexBinary(final E entity)
		{
			return this.subject.index(entity);
		}
		
	}
	
}
