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
 * An interface that extends {@link BinaryIndexerNumber} specifically for using {@link Short} as the key type.
 * It provides indexing capabilities, optimized for binary operations and high-cardinality indices,
 * while working with entities of type {@code E}.
 *
 * @param <E> the type of entities being indexed
 */
public interface BinaryIndexerShort<E> extends BinaryIndexerNumber<E, Short>
{
	public abstract class Abstract<E> extends BinaryIndexerNumber.Abstract<E, Short> implements BinaryIndexerShort<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		protected final Short getNumber(final E entity)
		{
			return this.getShort(entity);
		}
		
		protected abstract Short getShort(final E entity);
		
	}
	
}
