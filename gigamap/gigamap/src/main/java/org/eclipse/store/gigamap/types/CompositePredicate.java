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
			
			// inherent Object#equals equality by default. Any other logic needs another implementation.
			return subKey != null && (this.sample[subKeyPosition] & (Long)subKey) != 0L;
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
			if(this.sample.length <= subKeyPosition)
			{
				return false;
			}
			
			// default logic, but this is dangerous! A 0 value is not the same as a null reference. There might be proper 0 values.
			// only positions with non-zero sample values are valid filtering positions. All other positions to not filter.
			return this.sample[subKeyPosition] != 0L;
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
