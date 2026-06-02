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
 * Indexing logic for boolean keys.
 *
 * @param <E> the entity type
 *
 * @see Indexer
 */
public interface IndexerBoolean<E> extends Indexer<E, Boolean>
{
	/**
	 * Creates a condition for which the key is <code>true</code>.
	 *
	 * @param <S> a subtype of the entity type the condition applies to
	 * @return a new condition representing the key being <code>true</code>
	 */
	public <S extends E> Condition<S> isTrue();
	
	/**
	 * Creates a condition for which the key is <code>false</code>.
	 *
	 * @param <S> a subtype of the entity type the condition applies to
	 * @return a new condition representing the key being <code>false</code>
	 */
	public <S extends E> Condition<S> isFalse();
	
	
	
	/**
	 * Abstract base class for a {@link Boolean} key {@link Indexer}.
	 * 
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends Indexer.Abstract<E, Boolean> implements IndexerBoolean<E>
	{
		protected Abstract()
		{
			super();
		}
		
		@Override
		public final Class<Boolean> keyType()
		{
			return Boolean.class;
		}
		
		@Override
		public <S extends E> Condition<S> isTrue()
		{
			return this.is(Boolean.TRUE);
		}
		
		@Override
		public <S extends E> Condition<S> isFalse()
		{
			return this.is(Boolean.FALSE);
		}
		
		@Override
		public final Boolean index(final E entity)
		{
			return this.getBoolean(entity);
		}

		@Override
		public boolean test(final E entity, final Boolean key)
		{
			// A Boolean index is backed by a SingleBitmapIndex, which only materializes the TRUE
			// set: FALSE and null are both simply "not in the TRUE set" and cannot be told apart.
			// The bitmap query (SingleBitmapIndex#internalQuery) collapses them accordingly, so this
			// in-memory predicate (used by Condition#test) must do the same to stay consistent:
			// key==TRUE matches a TRUE-indexed entity, key==FALSE or null matches any non-TRUE
			// (FALSE or null) entity. The default strict-key-equality test would diverge from the
			// bitmap result for both FALSE and null.
			final boolean entityIsTrue = Boolean.TRUE.equals(this.index(entity));
			return Boolean.TRUE.equals(key) ? entityIsTrue : !entityIsTrue;
		}
		
		protected abstract Boolean getBoolean(E entity);
		
	}
	
}
