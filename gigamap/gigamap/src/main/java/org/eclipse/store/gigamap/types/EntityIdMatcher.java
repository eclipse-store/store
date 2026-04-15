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

import org.eclipse.serializer.collections.OffHeapLongHashSet;
import org.eclipse.serializer.collections.XArrays;
import org.eclipse.serializer.collections.XSort;
import org.eclipse.serializer.collections.types.XGettingCollection;
import org.eclipse.serializer.util.X;

import java.util.Collection;

import static org.eclipse.serializer.util.X.notNull;

/**
 * Matches candidate entity ids against an internal collection of ids to decide whether they
 * are part of a query result. This is the core abstraction used to combine arbitrary id sources
 * (single ids, ordered lists, hash sets, nested matchers, ...) with a {@link GigaQuery} via
 * {@link GigaMap.SubQuery}.
 * <p>
 * An {@code EntityIdMatcher} is also a {@link GigaMap.SubQuery} that returns itself from
 * {@link #provideEntityIdMatcher()}, so matchers can be passed directly to
 * {@link GigaQuery#and(GigaMap.SubQuery)}.
 * <p>
 * Implementations can be either <b>ordered</b> (entityIds stored in ascending order) or
 * <b>unordered</b>. Ordered implementations may additionally report the next candidate
 * id via {@link #matchEntityId(long)}, which allows the query executor to skip over large
 * gaps efficiently. See {@link #matchEntityId(long)} for the full return-value contract.
 *
 * @see GigaMap.SubQuery
 * @see GigaQuery#and(GigaMap.SubQuery)
 */
@FunctionalInterface
public interface EntityIdMatcher extends GigaMap.SubQuery
{
	/**
	 * Matches the passed entityId to an internal set or list, potentially ordered ascending.
	 * <p>
	 * This method returns one of the possible values with the following meaning:
	 * <ol>
	 * <li><b>A negative value</b>: meaning the passed entityId is <b>not</b> contained in the internal entityIds.</li>
	 * <li><b>The passed value</b>: meaning the passed entityId <b>is</b> contained in the internal entityIds.</li>
	 * <li><b>Value > entityId</b>: meaning the next contained entityId greater than or equal to the passed entityId is the returned value.</li>
	 * </ol>
	 * <p>
	 * Note that #3 is only possible for an internal sequence of entityId that is ordered.<br>
	 * Unordered collections of entityIds can only return #1 or #2.
	 *
	 * @param entityId the entityId to match to the internal collection of entityIds.
	 * @return a value indicating the matching result.
	 */
	public long matchEntityId(long entityId);

	/**
	 * {@inheritDoc}
	 * <p>
	 * Returns {@code this}, allowing an {@code EntityIdMatcher} to be used directly as a
	 * {@link GigaMap.SubQuery}.
	 */
	@Override
	public default EntityIdMatcher provideEntityIdMatcher()
	{
		return this;
	}



	/**
	 * Returns a shared {@link NoOp} matcher instance that accepts every entity id.
	 *
	 * @return the singleton {@link NoOp} matcher
	 */
	public static NoOp NoOp()
	{
		return NoOp.SINGLETON;
	}

	/**
	 * An {@link EntityIdMatcher} that accepts every entity id (i.e. does not restrict the result).
	 * <p>
	 * {@link #matchEntityId(long)} always returns the passed id, signaling "contained".
	 * Used as a neutral matcher when no id filtering is required.
	 */
	public static final class NoOp implements EntityIdMatcher
	{
		static final NoOp SINGLETON = new NoOp();

		NoOp()
		{
			super();
		}

		@Override
		public final long matchEntityId(final long entityId)
		{
			return entityId;
		}
	}



	/**
	 * An {@link EntityIdMatcher} that rejects every entity id (i.e. produces an empty result).
	 * <p>
	 * {@link #matchEntityId(long)} always returns {@code -1L}, signaling "not contained".
	 */
	public static final class Empty implements EntityIdMatcher
	{
		static final Empty SINGLETON = new Empty();

		Empty()
		{
			super();
		}

		@Override
		public final long matchEntityId(final long entityId)
		{
			return -1L;
		}
	}

	/**
	 * Returns a shared {@link Empty} matcher instance that rejects every entity id.
	 *
	 * @return the singleton {@link Empty} matcher
	 */
	public static EntityIdMatcher Empty()
	{
		return Empty.SINGLETON;
	}

	/**
	 * Creates a combined {@link EntityIdMatcher} from the given collection of matchers.
	 * <p>
	 * If the collection is {@code null} or empty, the {@link #Empty() Empty} matcher is returned.
	 * Otherwise a {@link Multiple} matcher is returned that combines the given matchers.
	 *
	 * @param entityIdMatchers the matchers to combine; may be {@code null} or empty
	 * @return an {@link EntityIdMatcher} combining the given matchers, or the empty matcher
	 */
	public static EntityIdMatcher New(final Collection<? extends EntityIdMatcher> entityIdMatchers)
	{
		if(entityIdMatchers == null || entityIdMatchers.isEmpty())
		{
			return Empty();
		}

		return new Multiple(X.toArray(entityIdMatchers, EntityIdMatcher.class));
	}

	/**
	 * Creates a combined {@link EntityIdMatcher} from the given collection of matchers.
	 * <p>
	 * If the collection is {@code null} or empty, the {@link #Empty() Empty} matcher is returned.
	 * Otherwise a {@link Multiple} matcher is returned that combines the given matchers.
	 *
	 * @param entityIdMatchers the matchers to combine; may be {@code null} or empty
	 * @return an {@link EntityIdMatcher} combining the given matchers, or the empty matcher
	 */
	public static EntityIdMatcher New(final XGettingCollection<? extends EntityIdMatcher> entityIdMatchers)
	{
		if(entityIdMatchers == null || entityIdMatchers.isEmpty())
		{
			return Empty();
		}

		return new Multiple(X.toArray(entityIdMatchers, EntityIdMatcher.class));
	}


	/**
	 * Creates a combined {@link EntityIdMatcher} from the given matchers.
	 * <p>
	 * If the array is {@code null} or empty, the {@link #Empty() Empty} matcher is returned.
	 * Otherwise a defensive copy is taken and a {@link Multiple} matcher is returned that
	 * combines the given matchers.
	 *
	 * @param entityIdMatchers the matchers to combine; may be {@code null} or empty
	 * @return an {@link EntityIdMatcher} combining the given matchers, or the empty matcher
	 */
	public static EntityIdMatcher New(final EntityIdMatcher... entityIdMatchers)
	{
		if(XArrays.hasNoContent(entityIdMatchers))
		{
			return Empty();
		}

		// Defensive copy is necessary, especially since the array content will be changed.
		final EntityIdMatcher[] copy = entityIdMatchers.clone();

		return new Multiple(copy);
	}

	/**
	 * Creates an {@link EntityIdMatcher} backed by an ordered list of entity ids.
	 * <p>
	 * The ids are defensively copied and sorted in ascending order, enabling the returned
	 * matcher to report "next candidate id" results (see {@link #matchEntityId(long)}, case #3).
	 * <p>
	 * If the given array is {@code null} or empty, the {@link #Empty() Empty} matcher is returned.
	 *
	 * @param entityIds the entity ids to match against; may be {@code null} or empty
	 * @return an ordered {@link EntityIdMatcher} for the given ids, or the empty matcher
	 */
	public static EntityIdMatcher Ascending(final long... entityIds)
	{
		if(XArrays.hasNoContent(entityIds))
		{
			return Empty();
		}

		// Defensive copy is necessary, especially since the array content will be changed.
		final long[] copy = entityIds.clone();
		XSort.sort(copy);

		return new AscendingListWrapper(copy);
	}

	/**
	 * An ordered {@link EntityIdMatcher} backed by an ascending array of entity ids.
	 * <p>
	 * The matcher assumes that {@link #matchEntityId(long)} is called with monotonically
	 * non-decreasing ids and advances its internal cursor accordingly. Because the ids are
	 * ordered, this implementation can report the next contained id {@code > entityId}
	 * (case #3 of {@link EntityIdMatcher#matchEntityId(long)}), allowing the query executor
	 * to skip over gaps efficiently.
	 */
	public static final class AscendingListWrapper implements EntityIdMatcher
	{
		private final long[] orderedEntityIds;

		private int lastMatchedEntityIdIndex;

		AscendingListWrapper(final long[] orderedEntityIds)
		{
			super();
			this.orderedEntityIds = orderedEntityIds;
		}

		@Override
		public long matchEntityId(final long entityId)
		{
			int i = this.lastMatchedEntityIdIndex;
			while(i < this.orderedEntityIds.length)
			{
				if(this.orderedEntityIds[i] < entityId)
				{
					// Special handling of loop end to abort the loop early on the next call of this method.
					if(++i == this.orderedEntityIds.length)
					{
						this.lastMatchedEntityIdIndex = i;
						break;
					}
					continue; // Too small entityIds are skipped
				}

				// Equal or greater entityIds use the same logic: remember the index and return the value.
				this.lastMatchedEntityIdIndex = i;
				return this.orderedEntityIds[i];
			}

			// No more entityIds to check, so the result is always -1 ("not contained").
			return -1L;
		}

	}

	/**
	 * An {@link EntityIdMatcher} composed of multiple delegate matchers with logical AND
	 * semantics — an entity id must be accepted by <i>every</i> delegate to be considered a
	 * match.
	 * <p>
	 * {@link #matchEntityId(long)} shortcircuits on the first delegate returning a negative
	 * value (case #1, "not contained"). If no delegate rejects the id, the return is the
	 * maximum of all delegate return values. For ordered delegates this preserves the "next
	 * candidate id" semantics (case #3): the maximum reported next-id is the earliest id at
	 * which <i>all</i> delegates can again produce a match, allowing the caller to gap-skip
	 * without losing correctness.
	 */
	public static final class Multiple implements EntityIdMatcher
	{
		private final EntityIdMatcher[] matchers;

		Multiple(final EntityIdMatcher[] matchers)
		{
			super();
			this.matchers = matchers;
		}

		@Override
		public final long matchEntityId(final long entityId)
		{
			long maxReturnValue = entityId;
			for(final EntityIdMatcher matcher : this.matchers)
			{
				final long returnValue = matcher.matchEntityId(entityId);
				if(returnValue < 0)
				{
					// Any delegate rejecting the id means AND fails, regardless of the others.
					return -1L;
				}
				if(returnValue > maxReturnValue)
				{
					maxReturnValue = returnValue;
				}
			}
			return maxReturnValue;
		}

	}


	/**
	 * Creates an unordered {@link EntityIdMatcher} backed by the given {@link OffHeapLongHashSet}.
	 * <p>
	 * The returned matcher is a live view of the set; it does not take a defensive copy.
	 * Since the backing collection is unordered, the matcher can only report "contained" or
	 * "not contained" results (cases #1 and #2 of {@link #matchEntityId(long)}).
	 *
	 * @param entityIdSet the entity id set to wrap; must not be {@code null}
	 * @return a {@link SetWrapper} matcher backed by the given set
	 */
	public static SetWrapper Wrap(final OffHeapLongHashSet entityIdSet)
	{
		return new SetWrapper(
			notNull(entityIdSet)
		);
	}

	/**
	 * An unordered {@link EntityIdMatcher} backed by an {@link OffHeapLongHashSet}.
	 * <p>
	 * {@link #matchEntityId(long)} returns the passed id if the set contains it, or {@code -1L}
	 * otherwise. Because the underlying collection is unordered, this matcher never reports
	 * a "next candidate id" result.
	 */
	public static final class SetWrapper implements EntityIdMatcher
	{
		private final OffHeapLongHashSet entityIdSet;

		SetWrapper(final OffHeapLongHashSet entityIdSet)
		{
			super();
			this.entityIdSet = entityIdSet;
		}

		@Override
		public long matchEntityId(final long entityId)
		{
			return this.entityIdSet.contains(entityId)
				? entityId
				: -1L
				;
		}

	}

}
