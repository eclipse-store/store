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

import java.util.function.IntPredicate;


/**
 * Byte-decomposed composite indexer for numeric types.
 * <p>
 * Decomposes numbers into bytes (base 256) using the existing
 * {@link HashingCompositeIndexer} infrastructure, providing efficient
 * range queries with at most 256 entries per sub-index.
 * <p>
 * Sub-index keys are stored as {@link Byte} wrapper objects. All 256 {@link Byte} values
 * (-128 to 127) are cached by the JVM, so there is zero allocation overhead for boxing.
 *
 * @param <E> the entity type
 * @param <K> the numeric key type
 */
public interface ByteIndexerNumber<E, K extends Number> extends HashingCompositeIndexer<E>, NumberQueryable<E, K>
{
	@Override
	public <S extends E> Condition<S> is(K key);

	@Override
	public <S extends E> Condition<S> not(K key);

	@SuppressWarnings("unchecked")
	@Override
	public <S extends E> Condition<S> in(K... keys);

	@SuppressWarnings("unchecked")
	@Override
	public <S extends E> Condition<S> notIn(K... keys);

	@Override
	public <S extends E> Condition<S> lessThan(K boundExclusive);

	@Override
	public <S extends E> Condition<S> lessThanEqual(K boundInclusive);

	@Override
	public <S extends E> Condition<S> greaterThan(K boundExclusive);

	@Override
	public <S extends E> Condition<S> greaterThanEqual(K boundInclusive);

	@Override
	public <S extends E> Condition<S> between(K startInclusive, K endInclusive);


	/**
	 * Abstract base class for byte-decomposed numeric indexers.
	 *
	 * @param <E> the entity type
	 * @param <K> the numeric key type
	 */
	public abstract class Abstract<E, K extends Number>
		extends HashingCompositeIndexer.AbstractSingleValueFixedSize<E, K>
		implements ByteIndexerNumber<E, K>
	{
		protected Abstract()
		{
			super();
		}

		/**
		 * Extracts the numeric value from the entity.
		 *
		 * @param entity the entity
		 * @return the numeric value
		 */
		protected abstract K getNumber(E entity);

		/**
		 * Returns the number of bytes used to represent this numeric type.
		 *
		 * @return the byte count (1, 2, 4, or 8)
		 */
		protected abstract int byteCount();

		/**
		 * Converts the numeric value to an order-preserving byte array.
		 * The encoding must preserve the natural ordering of the numeric type
		 * when bytes are compared as unsigned values in big-endian order.
		 *
		 * @param value  the value to convert
		 * @param target the target byte array to fill
		 */
		protected abstract void toOrderedBytes(K value, byte[] target);

		@Override
		protected final K getValue(final E entity)
		{
			return this.getNumber(entity);
		}

		@Override
		protected final int compositeSize()
		{
			return this.byteCount();
		}

		@Override
		protected final void fillCarrier(final K value, final Object[] carrier)
		{
			final byte[] bytes = new byte[this.byteCount()];
			this.toOrderedBytes(value, bytes);
			for(int i = 0; i < bytes.length; i++)
			{
				carrier[i] = bytes[i]; // auto-boxed to JVM-cached Byte instances
			}
		}

		@Override
		public final <S extends E> Condition<S> is(final K key)
		{
			return key == null
				? this.isNull()
				: this.isValue(key)
			;
		}

		@Override
		public final <S extends E> Condition<S> not(final K key)
		{
			return new Condition.Not<>(this.is(key));
		}

		@SafeVarargs
		@Override
		public final <S extends E> Condition<S> in(final K... keys)
		{
			if(keys == null || keys.length == 0)
			{
				throw new IllegalArgumentException("keys must not be null or empty");
			}
			Condition<S> result = this.is(keys[0]);
			for(int i = 1; i < keys.length; i++)
			{
				result = result.or(this.is(keys[i]));
			}
			return result;
		}

		@SafeVarargs
		@Override
		public final <S extends E> Condition<S> notIn(final K... keys)
		{
			return new Condition.Not<>(this.in(keys));
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		@Override
		public final <S extends E> Condition<S> greaterThan(final K boundExclusive)
		{
			if(boundExclusive == null)
			{
				throw new IllegalArgumentException("boundExclusive cannot be null");
			}

			final byte[] boundBytes = new byte[this.byteCount()];
			this.toOrderedBytes(boundExclusive, boundBytes);
			final int[] boundUnsigned = new int[this.byteCount()];
			for(int i = 0; i < this.byteCount(); i++)
			{
				boundUnsigned[i] = Byte.toUnsignedInt(boundBytes[i]);
			}

			Condition result = this.is(
				new ByteFieldPredicate(0, b -> b > boundUnsigned[0])
			);
			for(int i = 1; i < this.byteCount(); i++)
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
		public final <S extends E> Condition<S> lessThan(final K boundExclusive)
		{
			if(boundExclusive == null)
			{
				throw new IllegalArgumentException("boundExclusive cannot be null");
			}

			final byte[] boundBytes = new byte[this.byteCount()];
			this.toOrderedBytes(boundExclusive, boundBytes);
			final int[] boundUnsigned = new int[this.byteCount()];
			for(int i = 0; i < this.byteCount(); i++)
			{
				boundUnsigned[i] = Byte.toUnsignedInt(boundBytes[i]);
			}

			Condition result = this.is(
				new ByteFieldPredicate(0, b -> b < boundUnsigned[0])
			);
			for(int i = 1; i < this.byteCount(); i++)
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
		public final <S extends E> Condition<S> greaterThanEqual(final K boundInclusive)
		{
			if(boundInclusive == null)
			{
				throw new IllegalArgumentException("boundInclusive cannot be null");
			}

			return (Condition<S>)this.is(boundInclusive).or(this.greaterThan(boundInclusive));
		}

		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> lessThanEqual(final K boundInclusive)
		{
			if(boundInclusive == null)
			{
				throw new IllegalArgumentException("boundInclusive cannot be null");
			}

			return (Condition<S>)this.is(boundInclusive).or(this.lessThan(boundInclusive));
		}

		@SuppressWarnings("unchecked")
		@Override
		public final <S extends E> Condition<S> between(final K startInclusive, final K endInclusive)
		{
			if(startInclusive == null)
			{
				throw new IllegalArgumentException("startInclusive cannot be null");
			}
			if(endInclusive == null)
			{
				throw new IllegalArgumentException("endInclusive cannot be null");
			}

			return (Condition<S>)this.greaterThanEqual(startInclusive).and(this.lessThanEqual(endInclusive));
		}


		static class ByteFieldPredicate implements CompositePredicate<Object[]>
		{
			final int          subKeyPosition;
			final IntPredicate predicate;

			ByteFieldPredicate(final int subKeyPosition, final IntPredicate predicate)
			{
				this.subKeyPosition = subKeyPosition;
				this.predicate      = predicate;
			}

			@Override
			public boolean setSubKeyPosition(final int subKeyPosition)
			{
				return subKeyPosition == this.subKeyPosition;
			}

			@Override
			public boolean test(final Object[] keys)
			{
				return this.test(this.subKeyPosition, keys[this.subKeyPosition]);
			}

			@Override
			public boolean test(final int subKeyPosition, final Object subKey)
			{
				return subKeyPosition == this.subKeyPosition
					&& subKey instanceof Byte
					&& this.predicate.test(Byte.toUnsignedInt((Byte)subKey))
				;
			}

		}


		static class ByteEqualsFieldPredicate implements CompositePredicate<Object[]>
		{
			final int  subKeyPosition;
			final byte value;

			ByteEqualsFieldPredicate(final int subKeyPosition, final byte value)
			{
				this.subKeyPosition = subKeyPosition;
				this.value          = value;
			}

			@Override
			public boolean setSubKeyPosition(final int subKeyPosition)
			{
				return subKeyPosition == this.subKeyPosition;
			}

			@Override
			public boolean test(final Object[] keys)
			{
				return this.test(this.subKeyPosition, keys[this.subKeyPosition]);
			}

			@Override
			public boolean test(final int subKeyPosition, final Object subKey)
			{
				return subKeyPosition == this.subKeyPosition
					&& subKey instanceof Byte
					&& this.value == (Byte)subKey
				;
			}

		}


		static class ByteEqualsUntilPredicate implements CompositePredicate<Object[]>
		{
			final int    maxSubKeyPosition;
			final byte[] boundBytes;

			ByteEqualsUntilPredicate(final int maxSubKeyPosition, final byte[] boundBytes)
			{
				this.maxSubKeyPosition = maxSubKeyPosition;
				this.boundBytes        = boundBytes;
			}

			@Override
			public boolean setSubKeyPosition(final int subKeyPosition)
			{
				return subKeyPosition <= this.maxSubKeyPosition;
			}

			@Override
			public boolean test(final Object[] keys)
			{
				for(int i = 0; i <= this.maxSubKeyPosition; i++)
				{
					if(!this.test(i, keys[i]))
					{
						return false;
					}
				}
				return true;
			}

			@Override
			public boolean test(final int subKeyPosition, final Object subKey)
			{
				return subKeyPosition <= this.maxSubKeyPosition
					&& subKey instanceof Byte
					&& this.boundBytes[subKeyPosition] == (Byte)subKey
				;
			}

		}

	}

}
