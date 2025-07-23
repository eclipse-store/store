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
 * Indexing logic for {@link Character} keys.
 * 
 * @param <E> the entity type
 * 
 * @see Indexer
 */
public interface IndexerCharacter<E> extends Indexer<E, Character>
{

	/**
	 * Abstract base class for a {@link Character} key {@link Indexer}.
	 * 
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends Indexer.Abstract<E, Character> implements IndexerCharacter<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		public final Class<Character> keyType()
		{
			return Character.class;
		}
		
		@Override
		public final Character index(final E entity)
		{
			return this.getCharacter(entity);
		}
		
		protected abstract Character getCharacter(E entity);
		
	}
	
}
