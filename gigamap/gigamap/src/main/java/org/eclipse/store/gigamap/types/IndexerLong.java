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
 * Indexing logic for {@link Long} keys.
 * <p>
 * It is optimized for low-cardinality indices, for high-cardinality use {@link BinaryIndexerLong}.
 * 
 * @param <E> the entity type
 * 
 * @see IndexerNumber
 */
public interface IndexerLong<E> extends IndexerNumber<E, Long>
{

	/**
	 * Abstract base class for a {@link Long} key {@link Indexer}.
	 * 
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends Indexer.Abstract<E, Long> implements IndexerLong<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		public final Class<Long> keyType()
		{
			return Long.class;
		}
		
		@Override
		public final Long index(final E entity)
		{
			return this.getLong(entity);
		}
		
		protected abstract Long getLong(E entity);
		
		@Override
		public <S extends E> Condition<S> lessThan(final Long boundExclusive)
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
		public <S extends E> Condition<S> lessThanEqual(final Long boundInclusive)
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
		public <S extends E> Condition<S> greaterThan(final Long boundExclusive)
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
		public <S extends E> Condition<S> greaterThanEqual(final Long boundInclusive)
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
		public <S extends E> Condition<S> between(final Long startInclusive, final Long endInclusive)
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
