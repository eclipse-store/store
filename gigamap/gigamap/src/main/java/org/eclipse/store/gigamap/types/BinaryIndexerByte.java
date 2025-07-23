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
 * An interface that extends {@link BinaryIndexerNumber} specifically for using {@link Byte} as the key type.
 * It provides indexing capabilities, optimized for binary operations and high-cardinality indices,
 * while working with entities of type {@code E}.
 *
 * @param <E> the type of entities being indexed
 */
public interface BinaryIndexerByte<E> extends BinaryIndexerNumber<E, Byte>
{
	public abstract class Abstract<E> extends BinaryIndexerNumber.Abstract<E, Byte> implements BinaryIndexerByte<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		protected final Byte getNumber(final E entity)
		{
			return this.getByte(entity);
		}
		
		protected abstract Byte getByte(final E entity);
		
	}
	
}
