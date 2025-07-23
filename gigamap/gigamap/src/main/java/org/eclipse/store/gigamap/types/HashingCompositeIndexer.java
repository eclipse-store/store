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
 * Represents an indexer that constructs composite keys based on specific hashing logic.
 * This interface extends {@link CompositeIndexer} to provide functionality for creating
 * hash-based composite keys. The composite keys are represented as arrays of {@link Object}.
 *
 * @param <E> the type of the entity to be indexed
 */
public interface HashingCompositeIndexer<E> extends CompositeIndexer<E, Object[]>
{
	@Override
	public default Class<Object[]> keyType()
	{
		return Object[].class;
	}
	
	@Override
	public <T extends E> BitmapIndex.Internal<T, Object[]> createFor(BitmapIndices<T> parent);
	
	@Override
	public default <S extends E> Condition<S> is(final Object[] key)
	{
		return this.is(new CompositePredicate.ObjectSampleBased(key));
	}
	
	public abstract class Abstract<E> extends CompositeIndexer.Abstract<E, Object[]> implements HashingCompositeIndexer<E>
	{
		private final static Object NULL_OBJECT = new Object();
		
		protected static Object[] NULL()
		{
			return new Object[]{NULL_OBJECT};
		}
		
		
		protected Abstract()
		{
			super();
		}
		
		
		@Override
		public Object[] index(final E entity)
		{
			return this.index(entity, null);
		}
		
		@Override
		public abstract Object[] index(final E entity, final Object[] carrier);
		
		@Override
		public <T extends E> BitmapIndex.Internal<T, Object[]> createFor(final BitmapIndices<T> parent)
		{
			return new CompositeBitmapIndexHashing<>(parent, this.name(), this);
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
		
		protected abstract void fillCarrier(V value, Object[] carrier);
		
		@Override
		public Object[] index(final E entity, final Object[] carrier)
		{
			return this.indexValue(this.getValue(entity), carrier);
		}
		
		private Object[] indexValue(final V value, final Object[] carrier)
		{
			if(value == null)
			{
				return NULL();
			}
			
			Object[] c = carrier;
			if(carrier == null || carrier.length != this.compositeSize())
			{
				c = new Object[this.compositeSize()];
			}
			else
			{
				Arrays.fill(carrier, null);
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
			return super.is(this.indexValue(other, null));
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
		protected abstract Object[] fillCarrier(V value, Object[] carrier);
		
		@Override
		public Object[] index(final E entity, final Object[] carrier)
		{
			return this.indexValue(this.getValue(entity), carrier);
		}
		
		private Object[] indexValue(final V value, final Object[] carrier)
		{
			if(value == null)
			{
				return NULL();
			}
			
			if(carrier != null)
			{
				Arrays.fill(carrier, null);
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
