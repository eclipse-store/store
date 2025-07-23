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
 * An interface that extends {@link BinaryIndexerNumber} specifically for using {@link Double} as the key type.
 * It provides indexing capabilities, optimized for binary operations and high-cardinality indices,
 * while working with entities of type {@code E}.
 *
 * @param <E> the type of entities being indexed
 */
public interface BinaryIndexerDouble<E> extends BinaryIndexerNumber<E, Double>
{
	public abstract class Abstract<E> extends BinaryIndexerNumber.Abstract<E, Double> implements BinaryIndexerDouble<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		protected final Double getNumber(final E entity)
		{
			return this.getDouble(entity);
		}
		
		@Override
		protected long toLong(final Double number)
		{
			return Double.doubleToLongBits(number);
		}
		
		protected abstract Double getDouble(final E entity);
		
	}
	
}
