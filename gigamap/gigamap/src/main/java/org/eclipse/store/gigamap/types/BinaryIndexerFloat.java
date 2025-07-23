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
 * An interface that extends {@link BinaryIndexerNumber} specifically for using {@link Float} as the key type.
 * It provides indexing capabilities, optimized for binary operations and high-cardinality indices,
 * while working with entities of type {@code E}.
 *
 * @param <E> the type of entities being indexed
 */
public interface BinaryIndexerFloat<E> extends BinaryIndexerNumber<E, Float>
{
	public abstract class Abstract<E> extends BinaryIndexerNumber.Abstract<E, Float> implements BinaryIndexerFloat<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		protected final Float getNumber(final E entity)
		{
			return this.getFloat(entity);
		}
		
		@Override
		protected long toLong(final Float number)
		{
			return Float.floatToIntBits(number);
		}
		
		protected abstract Float getFloat(final E entity);
		
	}
	
}
