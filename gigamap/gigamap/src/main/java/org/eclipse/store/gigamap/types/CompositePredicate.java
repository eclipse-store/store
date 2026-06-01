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

import java.util.function.Predicate;

import static org.eclipse.serializer.util.X.notNull;

/**
 * A functional interface representing a composite predicate that operates on an array of objects.
 * It provides the ability to test individual elements within the array, as well as report the position
 * of subkeys during composite operations.
 * <p>
 * CompositePredicate extends {@link Predicate}, allowing it to be used in functional contexts requiring
 * predicates. It is designed for scenarios where decisions are required based on the combination of
 * multiple subkey elements within an array.
 */
public interface CompositePredicate<KS> extends Predicate<KS>
{
	@Override
	public boolean test(KS keys);
	
	
	/**
	 * Signals the predicate logic the next sub key position.
	 *
	 * @param subKeyPosition the next sub key position
	 * @return whether the position is relevant.
	 */
	public default boolean setSubKeyPosition(final int subKeyPosition)
	{
		// All sub key positions are relevant by default but no state is changed
		return true;
	}
	
	/**
	 * Clears or resets the tracking of a specific subkey position in the composite structure.
	 * By default, this method is a no-operation (no-op), which means it does nothing unless
	 * overridden by implementing classes.
	 *
	 * @param subKeyPosition the position of the subkey within the composite structure to be cleared
	 *                       or reset. A negative value may indicate that no valid position is being
	 *                       cleared.
	 */
	public default void clearSubKeyPosition(final int subKeyPosition)
	{
		// no-op by default
	}

	/**
	 * For binary (bit-sliced) composite predicates, returns the bit mask of bits that MUST be
	 * present at the given sub key position for an entity to match (exact-match semantics).
	 * <p>
	 * The binary composite index uses this to detect when a required bit has no index entry at
	 * all, in which case no entity can hold that bit and the result must be empty. Returns
	 * {@code 0L} by default, meaning "no exact-match bit requirement" (e.g. for wrapped arbitrary
	 * predicates).
	 *
	 * @param subKeyPosition the sub key position
	 * @return the required bit mask, or {@code 0L} if not applicable
	 */
	public default long requiredBits(final int subKeyPosition)
	{
		return 0L;
	}

	/**
	 * For binary (bit-sliced) composite predicates, reports whether the searched value requires any
	 * bit at a sub key position at or beyond {@code subIndexCount} (i.e. the index has fewer
	 * positions than the value needs). In that case no stored entity can match and the query result
	 * must be empty. Returns {@code false} by default.
	 *
	 * @param subIndexCount the number of sub key positions the index currently has
	 * @return {@code true} if the value requires a position the index does not have
	 */
	public default boolean exceedsPositions(final int subIndexCount)
	{
		return false;
	}

	/**
	 * Tests a specific subkey within a composite structure.
	 *
	 * @param subKeyPosition the position of the subkey within the composite structure. A negative
	 * value may indicate that no valid position is currently being tracked or tested.
	 * @param subKey the subkey object to test. Can be any object or null, depending on the test logic.
	 * @return true if the subkey satisfies the test condition; false otherwise.
	 */
	public boolean test(int subKeyPosition, Object subKey);
	
	/**
	 * Wraps a provided Predicate that operates on an array of objects into a CompositePredicate.
	 * The resulting CompositePredicate uses a carrier array for evaluating individual subkeys
	 * within the composite structure.
	 *
	 * @param subject the original Predicate to be wrapped, which operates on an array of objects.
	 *                Must not be null.
	 * @param carrier an array of objects that serves as the carrier for subkey evaluations within
	 *                the composite structure. Must not be null.
	 * @return a CompositePredicate wrapping the provided Predicate and utilizing the given carrier
	 *         array to perform composite operations.
	 */
	public static CompositePredicate<Object[]> WrapEntryBased(final Predicate<? super Object[]> subject, final Object[] carrier)
	{
		return new WrapperObjectBased(
			notNull(subject),
			notNull(carrier)
		);
	}
	
	/**
	 * Wraps a provided Predicate that operates on an array of longs into a CompositePredicate.
	 * The resulting CompositePredicate uses a carrier array for evaluating individual subkeys
	 * within the composite structure.
	 *
	 * @param subject the original Predicate to be wrapped, which operates on an array of longs.
	 *                Must not be null.
	 * @param carrier an array of long that serves as the carrier for subkey evaluations within
	 *                the composite structure. Must not be null.
	 * @return a CompositePredicate wrapping the provided Predicate and utilizing the given carrier
	 *         array to perform composite operations.
	 */
	public static CompositePredicate<long[]> WrapBinaryBased(final Predicate<? super long[]> subject, final long[] carrier)
	{
		return new WrapperBinaryBased(
			notNull(subject),
			notNull(carrier)
		);
	}
	
	abstract class AbstractObjectSampleBased implements CompositePredicate<Object[]>
	{
		protected final Object[] sample;
		
		public AbstractObjectSampleBased(final Object... sample)
		{
			super();
			this.sample = sample;
		}
		
		@Override
		public abstract boolean setSubKeyPosition(int subKeyPosition);
		
		@Override
		public boolean test(final Object[] keys)
		{
			for(int i = 0; i < keys.length; i++)
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
			// note: the subKeyPosition has already been selected beforehand via #setSubKeyPosition.
			
			// inherent Object#equals equality by default. Any other logic needs another implementation.
			return this.sample[subKeyPosition].equals(subKey);
		}
		
	}
	
	final class ObjectSampleBased extends AbstractObjectSampleBased
	{
		public ObjectSampleBased(final Object... sample)
		{
			super(sample);
		}
		
		@Override
		public final boolean setSubKeyPosition(final int subKeyPosition)
		{
			if(this.sample.length <= subKeyPosition)
			{
				return false;
			}
			
			// only positions with non-null sample values are valid filtering positions. All other positions to not filter.
			return this.sample[subKeyPosition] != null;
		}
		
	}
	
	
	abstract class AbstractBinarySampleBased implements CompositePredicate<long[]>
	{
		protected final long[] sample;
		
		public AbstractBinarySampleBased(final long... sample)
		{
			super();
			this.sample = sample;
		}
		
		@Override
		public abstract boolean setSubKeyPosition(int subKeyPosition);

		@Override
		public boolean test(final long[] keys)
		{
			for(int i = 0; i < keys.length; i++)
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
			// note: the subKeyPosition has already been selected beforehand via #setSubKeyPosition.

			// positions beyond the sample's length carry no bits: such a position must be empty for an
			// exact match, so every entry there is rejected (collected as an inverted result by the caller).
			final long s = subKeyPosition < this.sample.length ? this.sample[subKeyPosition] : 0L;

			// binary (bit-sliced) semantics: each subKey is a single bit of the value; it is selected
			// when that bit is set in the sample. Entries whose bit is not set are inverted by the caller.
			return subKey != null && (s & (Long)subKey) != 0L;
		}

		@Override
		public long requiredBits(final int subKeyPosition)
		{
			// every 1-bit of the searched value's sample must be present for an exact match;
			// positions beyond the sample require no bits.
			return subKeyPosition < this.sample.length ? this.sample[subKeyPosition] : 0L;
		}

		@Override
		public boolean exceedsPositions(final int subIndexCount)
		{
			// if the sample needs a bit at a position the index does not have, nothing can match.
			for(int i = subIndexCount; i < this.sample.length; i++)
			{
				if(this.sample[i] != 0L)
				{
					return true;
				}
			}
			return false;
		}

	}
	
	final class BinarySampleBased extends AbstractBinarySampleBased
	{
		public BinarySampleBased(final long... sample)
		{
			super(sample);
		}
		
		@Override
		public final boolean setSubKeyPosition(final int subKeyPosition)
		{
			// Every sub-index position is relevant for an exact match: positions within the sample's
			// length must match its bits; positions beyond it must be EMPTY (otherwise a shorter search
			// value would wrongly match a longer stored value - see #test/#requiredBits handling the
			// out-of-range case). Positions the sample needs beyond the index's range are handled
			// separately via #exceedsPositions in the search routine.
			// (Returning false for trailing positions, as before, left them unconstrained.)
			return true;
		}
		
	}
	
	abstract class AbstractWrapper<KS> implements CompositePredicate<KS>
	{
		final Predicate<? super KS> subject;
		final KS                    carrier;
		int currentSubKeyPosition = -1;
		
		AbstractWrapper(final Predicate<? super KS> subject, final KS carrier)
		{
			super();
			this.subject = subject;
			this.carrier = carrier;
		}
		
		@Override
		public boolean setSubKeyPosition(final int subKeyPosition)
		{
			if(subKeyPosition != this.currentSubKeyPosition)
			{
				this.currentSubKeyPosition = subKeyPosition;
			}
			
			return true;
		}
		
		@Override
		public final void clearSubKeyPosition(final int subKeyPosition)
		{
			if(this.currentSubKeyPosition == subKeyPosition)
			{
				this.setKey(this.currentSubKeyPosition, null);
				this.currentSubKeyPosition = -1;
			}
		}
		
		@Override
		public boolean test(final int subKeyPosition, final Object subKey)
		{
			this.setKey(subKeyPosition, subKey);
			
			return this.subject.test(this.carrier);
		}
		
		protected abstract void setKey(int subKeyPosition, Object subKey);
		
	}
	
	final class WrapperObjectBased extends AbstractWrapper<Object[]>
	{
		WrapperObjectBased(final Predicate<? super Object[]> subject, final Object[] carrier)
		{
			super(subject, carrier);
		}
		
		@Override
		public boolean test(final Object[] keys)
		{
			for(int i = 0; i < keys.length; i++)
			{
				if(keys[i] == null)
				{
					continue;
				}
				return this.test(i, keys[i]);
			}
			return this.test(-1, null);
		}
		
		@Override
		protected void setKey(final int subKeyPosition, final Object subKey)
		{
			this.carrier[subKeyPosition] = subKey;
		}
		
	}
	
	final class WrapperBinaryBased extends AbstractWrapper<long[]>
	{
		WrapperBinaryBased(final Predicate<? super long[]> subject, final long[] carrier)
		{
			super(subject, carrier);
		}
		
		@Override
		public boolean test(final long[] keys)
		{
			for(int i = 0; i < keys.length; i++)
			{
				if(keys[i] == 0L)
				{
					continue;
				}
				return this.test(i, keys[i]);
			}
			return this.test(-1, null);
		}
		
		@Override
		protected void setKey(final int subKeyPosition, final Object subKey)
		{
			// nasty cast for this special case because if I had to parametrize the whole Composite thing for sub key S, I'd go insane.
			this.carrier[subKeyPosition] = (Long)subKey;
		}
		
	}
	
}
