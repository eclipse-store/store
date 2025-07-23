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
 * Indexing logic for {@link Integer} keys.
 * <p>
 * It is optimized for low-cardinality indices, for high-cardinality use {@link BinaryIndexerInteger}.
 * 
 * @param <E> the entity type
 * 
 * @see IndexerNumber
 */
public interface IndexerInteger<E> extends IndexerNumber<E, Integer>
{

	/**
	 * Abstract base class for a {@link Integer} key {@link Indexer}.
	 * 
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends Indexer.Abstract<E, Integer> implements IndexerInteger<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		public final Class<Integer> keyType()
		{
			return Integer.class;
		}
		
		@Override
		public final Integer index(final E entity)
		{
			return this.getInteger(entity);
		}
		
		protected abstract Integer getInteger(E entity);
		
		@Override
		public <S extends E> Condition<S> lessThan(final Integer boundExclusive)
		{
			return this.is(key ->
			{
				if(key == null || boundExclusive == null)
				{
					return false;
				}
				return key < boundExclusive;
			});
		}
		
		@Override
		public <S extends E> Condition<S> lessThanEqual(final Integer boundInclusive)
		{
			return this.is(key ->
			{
				if(key == null || boundInclusive == null)
				{
					return false;
				}
				return key <= boundInclusive;
			});
		}
		
		@Override
		public <S extends E> Condition<S> greaterThan(final Integer boundExclusive)
		{
			return this.is(key ->
			{
				if(key == null || boundExclusive == null)
				{
					return false;
				}
				return key > boundExclusive;
			});
		}
		
		@Override
		public <S extends E> Condition<S> greaterThanEqual(final Integer boundInclusive)
		{
			return this.is(key ->
			{
				if(key == null || boundInclusive == null)
				{
					return false;
				}
				return key >= boundInclusive;
			});
		}
		
		@Override
		public <S extends E> Condition<S> between(final Integer startInclusive, final Integer endInclusive)
		{
			return this.is(key ->
			{
				if(key == null || startInclusive == null || endInclusive == null)
				{
					return false;
				}
				return key >= startInclusive && key <= endInclusive;
			});
		}
		
	}
	
}
