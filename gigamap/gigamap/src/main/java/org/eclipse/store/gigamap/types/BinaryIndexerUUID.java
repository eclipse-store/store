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

import java.util.UUID;

/**
 * Indexing logic for {@link UUID} keys.
 *
 * @param <E> the entity type
 *
 * @see Indexer
 */
public interface BinaryIndexerUUID<E> extends BinaryCompositeIndexer<E>
{
	/**
	 * Creates a condition that matches entities where the UUID is equal to the specified UUID.
	 *
	 * @param <S> a subtype of the entity type E
	 * @param other the UUID to compare against
	 * @return a condition representing the equality check for the specified UUID
	 */
	public <S extends E> Condition<S> is(UUID other);
	

	/**
	 * Abstract base class for a {@link UUID} key {@link Indexer}.
	 * 
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E> extends AbstractSingleValueFixedSize<E, UUID> implements BinaryIndexerUUID<E>
	{
		protected Abstract()
		{
			super();
		}
		
		protected abstract UUID getUUID(E entity);
		
		protected UUID getValue(final E entity)
		{
			return this.getUUID(entity);
		}
		
		@Override
		protected int compositeSize()
		{
			return 2;
		}
		
		/**
		 * Fills the composite carrier with the UUID's bit representation, ensuring no position
		 * is {@code 0L} since the composite bitmap index treats {@code 0L} as "empty position".
		 * <p>
		 * For non-zero bit halves, the raw value is used (backwards compatible with existing storages).
		 * For zero halves, {@code Long.MAX_VALUE} is used as a sentinel. UUIDs with a half equal
		 * to {@code Long.MAX_VALUE} would collide with zero, but this is astronomically unlikely
		 * (~1 in 2^64) and both cases were previously broken (silently skipped during indexing).
		 */
		@Override
		protected void fillCarrier(final UUID uuid, final long[] carrier)
		{
			carrier[0] = ensureNonZero(uuid.getMostSignificantBits());
			carrier[1] = ensureNonZero(uuid.getLeastSignificantBits());
		}

		private static long ensureNonZero(final long bits)
		{
			return bits == 0L ? Long.MAX_VALUE : bits;
		}
		
		@Override
		public <S extends E> Condition<S> is(final UUID other)
		{
			return this.isValue(other);
		}
		
	}
	
}
