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
 * Indexing logic for {@link Double} keys.
 * <p>
 * It is optimized for low-cardinality indices, for high-cardinality use {@link BinaryIndexerDouble}.
 * 
 * @param <E> the entity type
 * 
 * @see IndexerNumber
 */
public interface IndexerDouble<E> extends IndexerNumber<E, Double>
{

	/**
	 * Abstract base class for a {@link Double} key {@link Indexer}.
	 * 
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends Indexer.Abstract<E, Double> implements IndexerDouble<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		public final Class<Double> keyType()
		{
			return Double.class;
		}
		
		@Override
		public final Double index(final E entity)
		{
			return this.getDouble(entity);
		}
		
		protected abstract Double getDouble(E entity);
		
		@Override
		public <S extends E> Condition<S> lessThan(final Double boundExclusive)
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
		public <S extends E> Condition<S> lessThanEqual(final Double boundInclusive)
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
		public <S extends E> Condition<S> greaterThan(final Double boundExclusive)
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
		public <S extends E> Condition<S> greaterThanEqual(final Double boundInclusive)
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
		public <S extends E> Condition<S> between(final Double startInclusive, final Double endInclusive)
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
