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
 * An interface that extends {@link BinaryIndexerNumber} specifically for using {@link Integer} as the key type.
 * It provides indexing capabilities, optimized for binary operations and high-cardinality indices,
 * while working with entities of type {@code E}.
 *
 * @param <E> the type of entities being indexed
 */
public interface BinaryIndexerInteger<E> extends BinaryIndexerNumber<E, Integer>
{
	public abstract class Abstract<E> extends BinaryIndexerNumber.Abstract<E, Integer> implements BinaryIndexerInteger<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		protected final Integer getNumber(final E entity)
		{
			return this.getInteger(entity);
		}
		
		protected abstract Integer getInteger(final E entity);
		
	}
	
}
