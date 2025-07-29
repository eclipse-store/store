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
 * An interface that extends {@link BinaryIndexerNumber} specifically for using {@link Long} as the key type.
 * It provides indexing capabilities, optimized for binary operations and high-cardinality indices,
 * while working with entities of type {@code E}.
 *
 * @param <E> the type of entities being indexed
 */
public interface BinaryIndexerLong<E> extends BinaryIndexerNumber<E, Long>
{
	@Override
	public <S extends E> Condition<S> is(Long key);
	
	@Override
	public <S extends E> Condition<S> in(Long... keys);
		
	
	
	public abstract class Abstract<E> extends BinaryIndexerNumber.Abstract<E, Long> implements BinaryIndexerLong<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		protected final Long getNumber(final E entity)
		{
			return this.getLong(entity);
		}
		
		protected abstract Long getLong(final E entity);
		
	}
	
}
