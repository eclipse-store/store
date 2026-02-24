package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.time.Instant;

import org.eclipse.store.gigamap.types.ByteIndexerNumber.Abstract.ByteEqualsUntilPredicate;
import org.eclipse.store.gigamap.types.ByteIndexerNumber.Abstract.ByteFieldPredicate;


/**
 * Byte-decomposed composite indexer for {@link Instant}.
 * <p>
 * Decomposes an {@link Instant} into 12 bytes (8 for epoch second, 4 for nano adjustment)
 * using the existing {@link HashingCompositeIndexer} infrastructure, providing efficient
 * range queries with nanosecond precision.
 * <p>
 * Inherits temporal query methods ({@code is}, {@code before}, {@code after},
 * {@code beforeEqual}, {@code afterEqual}, {@code between}) from {@link IndexerTemporal}
 * and adds {@link #inSecond(Instant)} for epoch-second-level matching.
 *
 * @param <E> the entity type
 *
 * @see IndexerTemporal
 * @see ByteIndexerNumber
 */
public interface ByteIndexerInstant<E> extends HashingCompositeIndexer<E>, IndexerTemporal<E, Object[], Instant>
{
	/**
	 * Creates a condition which checks if the instant falls within the same epoch second
	 * as the given reference instant, regardless of the nanosecond component.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param reference the reference instant whose epoch second defines the match window
	 * @return a new condition representing the "in second" comparison
	 */
	public <S extends E> Condition<S> inSecond(Instant reference);


	/**
	 * Abstract base class for byte-decomposed {@link Instant} indexers.
	 * <p>
	 * Subclasses implement {@link #getInstant(Object)} to extract the {@link Instant}
	 * from an entity. The instant is decomposed into 12 order-preserving bytes
	 * (8 for the epoch second as a sign-flipped long, 4 for the nano adjustment
	 * as a sign-flipped int), enabling efficient range queries via
	 * {@link ByteFieldPredicate} and {@link ByteEqualsUntilPredicate}.
	 *
	 * @param <E> the entity type
	 */
	public abstract class Abstract<E>
		extends HashingCompositeIndexer.AbstractSingleValueFixedSize<E, Instant>
		implements ByteIndexerInstant<E>
	{
		private static final int COMPOSITE_SIZE = Long.BYTES + Integer.BYTES; // 12

		protected Abstract()
		{
			super();
		}

		/**
		 * Extracts the {@link Instant} value from the entity.
		 *
		 * @param entity the entity
		 * @return the instant value, or null
		 */
		protected abstract Instant getInstant(E entity);

		@Override
		protected final Instant getValue(final E entity)
		{
			return this.getInstant(entity);
		}

		@Override
		protected final int compositeSize()
		{
			return COMPOSITE_SIZE;
		}

		@Override
		protected final void fillCarrier(final Instant value, final Object[] carrier)
		{
			final byte[] bytes = this.toOrderedBytes(value);
			for(int i = 0; i < bytes.length; i++)
			{
				carrier[i] = bytes[i]; // auto-boxed to JVM-cached Byte instances
			}
		}

		private byte[] toOrderedBytes(final Instant value)
		{
			final byte[] bytes = new byte[COMPOSITE_SIZE];

			final long orderedSec = value.getEpochSecond() ^ 0x8000000000000000L;
			bytes[0] = (byte)(orderedSec >>> 56);
			bytes[1] = (byte)(orderedSec >>> 48);
			bytes[2] = (byte)(orderedSec >>> 40);
			bytes[3] = (byte)(orderedSec >>> 32);
			bytes[4] = (byte)(orderedSec >>> 24);
			bytes[5] = (byte)(orderedSec >>> 16);
			bytes[6] = (byte)(orderedSec >>>  8);
			bytes[7] = (byte) orderedSec;

			final int orderedNano = value.getNano() ^ 0x80000000;
			bytes[8]  = (byte)(orderedNano >>> 24);
			bytes[9]  = (byte)(orderedNano >>> 16);
			bytes[10] = (byte)(orderedNano >>>  8);
			bytes[11] = (byte) orderedNano;

			return bytes;
		}

		@Override
		public final <S extends E> Condition<S> is(final Instant other)
		{
			return other == null
				? this.isNull()
				: this.isValue(other)
			;
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		@Override
		public final <S extends E> Condition<S> after(final Instant boundExclusive)
		{
			if(boundExclusive == null)
			{
				throw new IllegalArgumentException("boundExclusive cannot be null");
			}

			final byte[] boundBytes = this.toOrderedBytes(boundExclusive);
			final int[] boundUnsigned = new int[COMPOSITE_SIZE];
			for(int i = 0; i < COMPOSITE_SIZE; i++)
			{
				boundUnsigned[i] = Byte.toUnsignedInt(boundBytes[i]);
			}

			Condition result = this.is(
				new ByteFieldPredicate(0, b -> b > boundUnsigned[0])
			);
			for(int i = 1; i < COMPOSITE_SIZE; i++)
			{
				final int pos = i;
				result = result.or(
					this.is(new ByteEqualsUntilPredicate(pos - 1, boundBytes))
					.and(this.is(new ByteFieldPredicate(pos, b -> b > boundUnsigned[pos])))
				);
			}
			return (Condition<S>)result;
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		@Override
		public final <S extends E> Condition<S> before(final Instant boundExclusive)
		{
			if(boundExclusive == null)
			{
				throw new IllegalArgumentException("boundExclusive cannot be null");
			}

			final byte[] boundBytes = this.toOrderedBytes(boundExclusive);
			final int[] boundUnsigned = new int[COMPOSITE_SIZE];
			for(int i = 0; i < COMPOSITE_SIZE; i++)
			{
				boundUnsigned[i] = Byte.toUnsignedInt(boundBytes[i]);
			}

			Condition result = this.is(
				new ByteFieldPredicate(0, b -> b < boundUnsigned[0])
			);
			for(int i = 1; i < COMPOSITE_SIZE; i++)
			{
				final int pos = i;
				result = result.or(
					this.is(new ByteEqualsUntilPredicate(pos - 1, boundBytes))
					.and(this.is(new ByteFieldPredicate(pos, b -> b < boundUnsigned[pos])))
				);
			}
			return (Condition<S>)result;
		}

		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> afterEqual(final Instant boundInclusive)
		{
			if(boundInclusive == null)
			{
				throw new IllegalArgumentException("boundInclusive cannot be null");
			}

			return (Condition<S>)this.is(boundInclusive).or(this.after(boundInclusive));
		}

		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> beforeEqual(final Instant boundInclusive)
		{
			if(boundInclusive == null)
			{
				throw new IllegalArgumentException("boundInclusive cannot be null");
			}

			return (Condition<S>)this.is(boundInclusive).or(this.before(boundInclusive));
		}

		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> between(final Instant startInclusive, final Instant endInclusive)
		{
			if(startInclusive == null)
			{
				throw new IllegalArgumentException("startInclusive cannot be null");
			}
			if(endInclusive == null)
			{
				throw new IllegalArgumentException("endInclusive cannot be null");
			}

			return (Condition<S>)this.afterEqual(startInclusive).and(this.beforeEqual(endInclusive));
		}

		@Override
		public final <S extends E> Condition<S> inSecond(final Instant reference)
		{
			if(reference == null)
			{
				throw new IllegalArgumentException("reference cannot be null");
			}

			// Encode only the epoch second into ordered bytes (positions 0-7)
			final byte[] secBytes = new byte[Long.BYTES];
			final long orderedSec = reference.getEpochSecond() ^ 0x8000000000000000L;
			secBytes[0] = (byte)(orderedSec >>> 56);
			secBytes[1] = (byte)(orderedSec >>> 48);
			secBytes[2] = (byte)(orderedSec >>> 40);
			secBytes[3] = (byte)(orderedSec >>> 32);
			secBytes[4] = (byte)(orderedSec >>> 24);
			secBytes[5] = (byte)(orderedSec >>> 16);
			secBytes[6] = (byte)(orderedSec >>>  8);
			secBytes[7] = (byte) orderedSec;

			// ByteEqualsUntilPredicate(maxPos=7, secBytes) constrains only positions 0-7
			return this.is(new ByteEqualsUntilPredicate(Long.BYTES - 1, secBytes));
		}

	}

}
