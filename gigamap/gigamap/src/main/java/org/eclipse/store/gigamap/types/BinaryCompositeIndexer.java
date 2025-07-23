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

import java.util.Arrays;


/**
 * Represents a binary composite indexer, an implementation of {@link CompositeIndexer} designed
 * to work with composite keys consisting of arrays of {@code long} values.
 * <p>
 * This index can be used to encode complex values into longs to optimize high-cardinality indexing.
 *
 * @param <E> the type of the entity being indexed
 */
public interface BinaryCompositeIndexer<E> extends CompositeIndexer<E, long[]>
{
	@Override
	public default Class<long[]> keyType()
	{
		return long[].class;
	}
	
	@Override
	public <T extends E> BitmapIndex.Internal<T, long[]> createFor(BitmapIndices<T> parent);
	
	@Override
	public default <S extends E> Condition<S> is(final long[] key)
	{
		return this.is(new CompositePredicate.BinarySampleBased(key));
	}
	
	public abstract class Abstract<E> extends CompositeIndexer.Abstract<E, long[]> implements BinaryCompositeIndexer<E>
	{
		protected static long[] NULL()
		{
			return new long[]{Long.MIN_VALUE};
		}
		
		
		protected Abstract()
		{
			super();
		}
		
		@Override
		public long[] index(final E entity)
		{
			return this.index(entity, null);
		}
		
		@Override
		public abstract long[] index(final E entity, final long[] carrier);
		
		@Override
		public <T extends E> BitmapIndex.Internal<T, long[]> createFor(final BitmapIndices<T> parent)
		{
			return new CompositeBitmapIndexBinary<>(parent, this.name(), this);
		}
		
	}
	
	
	public abstract class AbstractSingleValueFixedSize<E, V> extends Abstract<E>
	{
		protected AbstractSingleValueFixedSize()
		{
			super();
		}
		
		protected abstract int compositeSize();
		
		protected abstract V getValue(E entity);
		
		protected abstract void fillCarrier(V value, long[] carrier);
		
		@Override
		public long[] index(final E entity, final long[] carrier)
		{
			return this.indexValue(this.getValue(entity), carrier);
		}
		
		private long[] indexValue(final V value, final long[] carrier)
		{
			if(value == null)
			{
				return NULL();
			}
			
			long[] c = carrier;
			if(carrier == null || carrier.length != this.compositeSize())
			{
				c = new long[this.compositeSize()];
			}
			else
			{
				Arrays.fill(carrier, 0L);
			}
			this.fillCarrier(value, c);
			return c;
		}
		
		@Override
		public <S extends E> Condition<S> isNull()
		{
			return this.is(NULL());
		}
		
		protected <S extends E> Condition<S> isValue(final V other)
		{
			return this.is(this.indexValue(other, null));
		}
		
	}
	
	
	public abstract class AbstractSingleValueVariableSize<E, V> extends Abstract<E>
	{
		protected AbstractSingleValueVariableSize()
		{
			super();
		}
		
		protected abstract V getValue(E entity);
		
		/**
		 * Fills the carrier array. Needs to create or increase a new array on demand.
		 * @param value the value to index
		 * @param carrier array, maybe null or too small
		 * @return the given carrier array or a new one
		 */
		protected abstract long[] fillCarrier(V value, long[] carrier);
		
		@Override
		public long[] index(final E entity, final long[] carrier)
		{
			return this.indexValue(this.getValue(entity), carrier);
		}
		
		private long[] indexValue(final V value, final long[] carrier)
		{
			if(value == null)
			{
				return NULL();
			}
			
			if(carrier != null)
			{
				Arrays.fill(carrier, 0L);
			}
			
			return this.fillCarrier(value, carrier);
		}
		
		@Override
		public <S extends E> Condition<S> isNull()
		{
			return super.is(NULL());
		}
		
		protected <S extends E> Condition<S> isValue(final V other)
		{
			return super.is(this.indexValue(other, null));
		}
		
	}
	
}
