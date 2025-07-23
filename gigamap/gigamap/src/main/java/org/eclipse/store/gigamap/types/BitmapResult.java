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

import static org.eclipse.serializer.util.X.notNull;

/**
 * Represents the result of a computation involving bitmap data. The {@code BitmapResult}
 * interface defines methods to manage, query, and optimize segments of bitmap data.
 * This interface supports operations used in processes such as iteration, optimization,
 * and comparison of bitmap segments.
 * <p>
 * Methods handle transformations and computations on bitmap values, clearing iteration
 * states, and creating copies of results for iteration purposes.
 * <p>
 * Static methods facilitate actions like AND optimization, segment counting, and
 * custom resolver creation.
 */
public interface BitmapResult
{
	/**
	 * Retrieves the bitmap value for the specified level 1 index in the current iteration state.
	 *
	 * @param level1Index the index of the level 1 segment for which the bitmap value is to be retrieved
	 * @return the bitmap value associated with the given level 1 index
	 */
	public long getCurrentLevel1BitmapValue(int level1Index);
	
	/**
	 * Sets the current iteration state to the specified level 1 segment
	 * corresponding to the given level 2 index.
	 *
	 * @param level2Index the index of the level 2 segment used to determine
	 *                    the corresponding level 1 segment to be set as the
	 *                    current iteration state
	 * @return true if the level 1 segment was successfully set, false otherwise
	 */
	public boolean setCurrentIterationLevel1Segment(int level2Index);
	
	/**
	 * Sets the current iteration state to the specified level 2 segment
	 * corresponding to the given level 3 index.
	 *
	 * @param level3Index the index of the level 3 segment used to determine
	 *                    the corresponding level 2 segment to be set as the
	 *                    current iteration state
	 * @return true if the level 2 segment was successfully set, false otherwise
	 */
	public boolean setCurrentIterationLevel2Segment(int level3Index);
	
	/**
	 * Determines if the current state supports optimization for AND logic operations
	 * within the bitmap processing context.
	 *
	 * @return true if the current state is suitable for AND logic optimization, false otherwise
	 */
	public boolean isAndLogicOptimizable();
	
	/**
	 * Performs AND optimization on the current bitmap result instance.
	 * The default implementation indicates that results are not typically
	 * AND-optimizable, except for specific cases such as ChainAnd.
	 *
	 * @return an array containing the current instance, indicating no optimization
	 *         has been performed by default.
	 */
	public default BitmapResult[] andOptimize()
	{
		// default behavior is that results are generally not and-optimizable. Except for, well, ChainAnd.
		return new BitmapResult[]{this};
	}
	
	/**
	 * Creates and returns a copy of the current iteration state of the BitmapResult.
	 * The new instance will maintain the same internal state as the original at the point
	 * this method is called.
	 *
	 * @return a new BitmapResult object that represents a copy of the current iteration state
	 */
	public BitmapResult createIterationCopy();
	
	public static BitmapResult[] createIterationCopy(final BitmapResult[] results)
	{
		final BitmapResult[] copy = new BitmapResult[results.length];
		for(int i = 0; i < results.length; i++)
		{
			if(results[i] == null)
			{
				break;
			}
			copy[i] = results[i].createIterationCopy();
		}
		return copy;
	}
	
	/**
	 * Retrieves the total count of segments for the current instance.
	 *
	 * @return the total number of segments associated with this bitmap result
	 */
	public long segmentCount();
	
	/**
	 * Clears the current iteration state of the bitmap result.
	 * <p>
	 * This method resets any internal state maintained for iterating over
	 * the bitmap results, ensuring that subsequent operations or iterations
	 * start from an initial state. It does not modify the underlying data
	 * or bitmap values associated with the instance.
	 */
	public void clearIterationState();
	
	
	
	public static int andOptimize(final BitmapResult r1, final BitmapResult r2)
	{
		if(r1.isAndLogicOptimizable())
		{
			if(r2.isAndLogicOptimizable())
			{
				return segmentCountAsc(r1, r2);
			}
			return -1;
		}
		
		// segment count is only relevant for and-optimizable results
		return r2.isAndLogicOptimizable() ? +1 : 0;
	}
	
	public static int segmentCountAsc(final BitmapResult r1, final BitmapResult r2)
	{
		return r2.segmentCount() >= r1.segmentCount() ? r2.segmentCount() != r1.segmentCount() ? -1 : 0 : +1;
	}
	
	public static int segmentCountDesc(final BitmapResult r1, final BitmapResult r2)
	{
		return r2.segmentCount() >= r1.segmentCount() ? r2.segmentCount() != r1.segmentCount() ? +1 : 0 : -1;
	}
	
	
	
	public abstract class AbstractChain implements BitmapResult
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final BitmapResult[] elements;
		final long cachedSegmentCount;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		AbstractChain(final BitmapResult[] elements)
		{
			// passed elements must already be properly sorted by segment count, so the first one is the only relevant.
			this(elements, elements.length != 0
				? elements[0].segmentCount()
				: 0L
			);
		}
		
		AbstractChain(final BitmapResult[] elements, final long cachedSegmentCount)
		{
			super();
			this.elements           = elements          ;
			this.cachedSegmentCount = cachedSegmentCount;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final long segmentCount()
		{
			return this.cachedSegmentCount;
		}
				
		@Override
		public void clearIterationState()
		{
			for(final BitmapResult element : this.elements)
			{
				if(element == null)
				{
					break;
				}
				element.clearIterationState();
			}
		}
		
	}
	
	public final class ChainOr extends AbstractChain
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		ChainOr(final BitmapResult[] elements)
		{
			super(elements);
		}
		
		ChainOr(final BitmapResult[] elements, final long cachedSegmentCount)
		{
			super(elements, cachedSegmentCount);
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public long getCurrentLevel1BitmapValue(final int level1Index)
		{
			// all bits are initially 0s. The condition result values will add them up.
			long result = 0L;
			for(final BitmapResult element : this.elements)
			{
				if(element == null)
				{
					break;
				}
				// -1 looks weird, but that is actually "all 1 bits", of course.
				if((result |= element.getCurrentLevel1BitmapValue(level1Index)) == -1L)
				{
					// early exit: an all-1s value will never be changed by OR logic.
					break;
				}
			}
			return result;
		}
		
		@Override
		public boolean setCurrentIterationLevel1Segment(final int level2Index)
		{
			// OR-logic: if the current segment of at least ONE element is required, the segment must be processed.
			boolean isRequired = false;
			for(final BitmapResult element : this.elements)
			{
				if(element == null)
				{
					break;
				}
				// no early exit since the setter must be called on EVERY element, of course.
				isRequired |= element.setCurrentIterationLevel1Segment(level2Index);
			}
			
			return isRequired;
		}
		
		@Override
		public boolean setCurrentIterationLevel2Segment(final int level3Index)
		{
			// if at least one element did set the index successfully, the whole or chain cannot be skipped.
			boolean isRequired = false;
			for(final BitmapResult element : this.elements)
			{
				if(element == null)
				{
					break;
				}
				// no early exit since the setter must be called on EVERY element, of course.
				isRequired |= element.setCurrentIterationLevel2Segment(level3Index);
			}
			
			return isRequired;
		}
		
		@Override
		public boolean isAndLogicOptimizable()
		{
			// a chain of conditions linked via OR logic is not optimizable for AND logic. Because ... it's not AND.
			return false;
		}
		
		@Override
		public BitmapResult createIterationCopy()
		{
			return new ChainOr(BitmapResult.createIterationCopy(this.elements), this.cachedSegmentCount);
		}
		
	}
	
	public final class ChainAnd extends AbstractChain
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		ChainAnd(final BitmapResult[] elements)
		{
			super(elements);
		}
		
		ChainAnd(final BitmapResult[] elements, final long cachedSegmentCount)
		{
			super(elements, cachedSegmentCount);
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
				
		@Override
		public long getCurrentLevel1BitmapValue(final int level1Index)
		{
			// -1L means all bits are 1s. The condition result values will filter it down.
			long result = -1L;
			for(final BitmapResult element : this.elements)
			{
				if(element == null)
				{
					break;
				}
				if((result &= element.getCurrentLevel1BitmapValue(level1Index)) == 0L)
				{
					// early exit: an all-0s value will never be changed by AND logic.
					break;
				}
			}
			return result;
		}
		
		@Override
		public boolean setCurrentIterationLevel1Segment(final int level2Index)
		{
			/* AND logic: the current segment can only stay required if ALL elements report it to be required.
			 * I.e. if only ONE element reports its current segment to be not required (meaning it only contains 0 bits),
			 * AND logic causes the segment for ALL elements to be irrelevant.
			 * Because whatever bits the other segments would contain, the one all-zeroes-segment would always
			 * produce an all-zeroes bitmap value via AND logic.
			 */
			boolean isRequired = true;
			for(final BitmapResult element : this.elements)
			{
				if(element == null)
				{
					break;
				}
				// no early exit since the setter must be called on EVERY element, of course.
				isRequired &= element.setCurrentIterationLevel1Segment(level2Index);
			}
			
			return isRequired;
		}
		
		@Override
		public boolean setCurrentIterationLevel2Segment(final int level3Index)
		{
			// if at least one element did set the index successfully, the whole and chain cannot be skipped.
			boolean isRequired = false;
			for(final BitmapResult element : this.elements)
			{
				if(element == null)
				{
					break;
				}
				// no early exit since the setter must be called on EVERY element, of course.
				isRequired |= element.setCurrentIterationLevel2Segment(level3Index);
			}
			
			return isRequired;
		}
		
		@Override
		public boolean isAndLogicOptimizable()
		{
			// if at least one element in the and chain is and-optimizable, the whole and chain is.
			for(final BitmapResult element : this.elements)
			{
				if(element == null)
				{
					break;
				}
				if(element.isAndLogicOptimizable())
				{
					return true;
				}
			}
			
			return false;
		}
		
		@Override
		public BitmapResult[] andOptimize()
		{
			return this.elements;
		}
		
		@Override
		public BitmapResult createIterationCopy()
		{
			return new ChainAnd(BitmapResult.createIterationCopy(this.elements), this.cachedSegmentCount);
		}
		
	}
	
	public final class Not implements BitmapResult
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final BitmapResult element;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Not(final BitmapResult element)
		{
			super();
			this.element = element;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public long segmentCount()
		{
			return this.element.segmentCount();
		}
		
		@Override
		public long getCurrentLevel1BitmapValue(final int level1Index)
		{
			// the whole logic of this class is "~" :-DD. Well ... what to expect from a class called "Not".
			return ~this.element.getCurrentLevel1BitmapValue(level1Index);
		}
		
		@Override
		public boolean setCurrentIterationLevel1Segment(final int level2Index)
		{
			this.element.setCurrentIterationLevel1Segment(level2Index);
			
			// Not logic means an all-zeroes-segment can't be optimized away! Because the not logic turns it into 1s.
			return true;
		}
		
		@Override
		public boolean setCurrentIterationLevel2Segment(final int level3Index)
		{
			this.element.setCurrentIterationLevel2Segment(level3Index);
			
			// Not logic means an all-zeroes-segment can't be optimized away! Because the not logic turns it into 1s.
			return true;
		}
		
		@Override
		public boolean isAndLogicOptimizable()
		{
			// NOT, along with OR, is the definition of not being AND-optimizable.
			return false;
		}
		
		@Override
		public void clearIterationState()
		{
			this.element.clearIterationState();
		}
		
		@Override
		public BitmapResult createIterationCopy()
		{
			return new Not(this.element.createIterationCopy());
		}
		
	}
	
	public final class Empty implements BitmapResult
	{
		Empty()
		{
			super();
		}

		@Override
		public final long segmentCount()
		{
			return 0L;
		}
		
		@Override
		public final long getCurrentLevel1BitmapValue(final int level1Index)
		{
			return 0L;
		}
		
		@Override
		public final boolean setCurrentIterationLevel1Segment(final int level2Index)
		{
			// signal AND logic to abort.
			return false;
		}
		
		@Override
		public final boolean setCurrentIterationLevel2Segment(final int level3Index)
		{
			// signal AND logic to abort.
			return false;
		}
		
		@Override
		public final boolean isAndLogicOptimizable()
		{
			// funny enough, this is AND-optimizable: AND logic can instantly abort when encountering a none-result.
			return true;
		}

		@Override
		public final void clearIterationState()
		{
			// nothing to clear
		}
		
		@Override
		public BitmapResult createIterationCopy()
		{
			// no iteration state to copy, so this can return itself.
			return this;
		}
	}
	
	
	public static <E> Resolver<E> Resolver(
		final GigaMap<E>               parent   ,
		final EntryConsumer<? super E> collector
	)
	{
		return new Resolver.Default<>(
			notNull(parent),
			notNull(collector)
		);
	}
	
	public static <E> Resolver<E> ResolverPeeking(
		final GigaMap<E>               parent   ,
		final EntryConsumer<? super E> collector
	)
	{
		return new Resolver.Peeking<>(
			notNull(parent),
			notNull(collector)
		);
	}
	
		
	public interface Resolver<E>
	{
		public E get(long entityId);
		
		public GigaMap<E> parent();
		
		
		
		abstract class Abstract<E> implements Resolver<E>
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			final GigaMap<E> parent;
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			Abstract(final GigaMap<E> parent)
			{
				super();
				this.parent = parent;
			}
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////
			
			@Override
			public final GigaMap<E> parent()
			{
				return this.parent;
			}
			
			@Override
			public E get(final long entityId)
			{
				return this.parent.get(entityId);
			}
						
		}
		
		abstract class AbstractWrapping<E> extends Abstract<E>
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////

			final EntryConsumer<? super E> consumer;
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			AbstractWrapping(final GigaMap<E> parent, final EntryConsumer<? super E> consumer)
			{
				super(parent);
				this.consumer = consumer;
			}

		}
		
		final class Default<E> extends AbstractWrapping<E>
		{
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			Default(final GigaMap<E> parent, final EntryConsumer<? super E> consumer)
			{
				super(parent, consumer);
			}
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////

			@Override
			public E get(final long entityId)
			{
				final E entity = super.get(entityId);
				if(entity != null)
				{
					// NOT condition arithmetic can cause lookups for entityIds of non-existing (removed) entities.
					this.consumer.accept(entityId, entity);
				}
				
				return entity;
			}
			
		}
		
		// (26.01.2024 TM)XXX: what is the difference between Peeking and Default?
		
		final class Peeking<E> extends AbstractWrapping<E>
		{
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			Peeking(final GigaMap<E> parent, final EntryConsumer<? super E> collector)
			{
				super(parent, collector);
			}
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////

			@Override
			public E get(final long entityId)
			{
				final E entity = super.get(entityId);
				if(entity != null)
				{
					// NOT condition arithmetic can cause lookups for entityIds of non-existing (removed) entities.
					this.consumer.accept(entityId, entity);
				}
				
				return entity;
			}
			
		}
		
	}
		
}
