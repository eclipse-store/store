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

import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.types.XGettingList;
import org.eclipse.serializer.collections.types.XIterable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Common supertype for score-ranked search results produced by external indices
 * (e.g. vector similarity search, full-text search).
 * <p>
 * A {@code ScoredSearchResult} is both an {@link Iterable} of scored {@link Entry entries}
 * and a {@link GigaMap.SubQuery}. As a {@code SubQuery} it exposes the set of matched entity
 * ids for composition with a {@link GigaQuery} via {@link GigaQuery#and(GigaMap.SubQuery)}.
 * Entries carry their similarity / relevance score and resolve their entity lazily through
 * the backing {@link GigaMap}.
 * <p>
 * Implementations are typically immutable after construction: the underlying search is run
 * eagerly, entries are collected into a fixed-size list, and the {@link EntityIdMatcher}
 * is materialized on first demand.
 *
 * @param <E> the entity type managed by the backing {@link GigaMap}
 *
 * @see GigaMap.SubQuery
 * @see GigaQuery#and(GigaMap.SubQuery)
 */
public interface ScoredSearchResult<E> extends Iterable<ScoredSearchResult.Entry<E>>, XIterable<ScoredSearchResult.Entry<E>>, GigaMap.SubQuery
{
	/**
	 * Returns the number of entries in this result.
	 *
	 * @return entry count
	 */
	public int size();

	/**
	 * Returns whether this result contains no entries.
	 *
	 * @return {@code true} if this result is empty
	 */
	public boolean isEmpty();

	/**
	 * Creates a sequential {@link Stream} over the {@link Entry entries} of this result.
	 *
	 * @return a stream of entries in iteration order
	 */
	public default Stream<Entry<E>> stream()
	{
		return StreamSupport.stream(
			Spliterators.spliterator(
				this.iterator(),
				this.size(),
				Spliterator.SIZED | Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.IMMUTABLE
			),
			false
		);
	}

	/**
	 * Collects all {@link Entry entries} of this result into a {@link List}.
	 *
	 * @return a list of entries in iteration order
	 */
	public default List<Entry<E>> toList()
	{
		return this.stream().toList();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Iterates all {@link Entry entries} in iteration order, forwarding each to the given
	 * {@link Consumer}. Implementations backed by a typed collection can override this for
	 * a more efficient traversal.
	 */
	@Override
	public default <P extends Consumer<? super Entry<E>>> P iterate(final P procedure)
	{
		for(final Entry<E> entry : this)
		{
			procedure.accept(entry);
		}
		return procedure;
	}

	/**
	 * Narrows this result by intersecting it with the given {@link GigaMap.SubQuery}. Only
	 * entries whose entity id is also reported by the sub-query's
	 * {@link EntityIdMatcher} are retained; all scores and the original iteration order
	 * (by decreasing score) are preserved.
	 * <p>
	 * This is the scored-side counterpart of {@link GigaQuery#and(GigaMap.SubQuery)} and
	 * allows the caller to keep using the result as an iterable of scored entries after the
	 * narrowing, for example:
	 * <pre>{@code
	 *     ScoredSearchResult<Article> top = luceneIndex.search("eclipse", 100)
	 *         .and(gigaMap.query(status.is("PUBLISHED")));
	 * }</pre>
	 *
	 * @param other the sub-query to intersect with
	 * @return a new {@link ScoredSearchResult} containing only the surviving entries
	 */
	public ScoredSearchResult<E> and(GigaMap.SubQuery other);

	/**
	 * A single scored search-result entry exposing the entity id, the score, and lazy access
	 * to the entity itself.
	 *
	 * @param <E> the entity type
	 */
	public static interface Entry<E>
	{
		/**
		 * Returns the unique identifier of the entity associated with this entry.
		 *
		 * @return the entity id
		 */
		public long entityId();

		/**
		 * Returns the score associated with this entry (similarity, relevance, or otherwise
		 * index-specific).
		 *
		 * @return the score
		 */
		public float score();

		/**
		 * Returns the entity associated with this entry. Resolved lazily from the backing
		 * {@link GigaMap}. May return {@code null} if the entity has been removed between
		 * search and access.
		 *
		 * @return the entity, or {@code null} if no longer present
		 */
		public E entity();


		/**
		 * Default {@link Entry} implementation that resolves its entity lazily via the
		 * backing {@link GigaMap}.
		 *
		 * @param <E> the entity type
		 */
		public static class Default<E> implements Entry<E>
		{
			private final long       entityId;
			private final float      score   ;
			private final GigaMap<E> gigaMap ;

			public Default(final long entityId, final float score, final GigaMap<E> gigaMap)
			{
				super();
				this.entityId = entityId;
				this.score    = score   ;
				this.gigaMap  = gigaMap ;
			}

			@Override
			public long entityId()
			{
				return this.entityId;
			}

			@Override
			public float score()
			{
				return this.score;
			}

			@Override
			public E entity()
			{
				return this.gigaMap.get(this.entityId);
			}

			@Override
			public String toString()
			{
				return "Entry[entityId=" + this.entityId + ", score=" + this.score + "]";
			}
		}
	}


	/**
	 * Default {@link ScoredSearchResult} implementation backed by an immutable
	 * {@link XGettingList} of entries. Caches the sorted entity ids produced on first
	 * {@link #provideEntityIdMatcher()} call; each invocation returns a fresh
	 * {@link EntityIdMatcher.AscendingListWrapper} over the cached ids, so the cursor
	 * state of one consumer does not leak into another.
	 *
	 * @param <E> the entity type
	 */
	public static class Default<E> implements ScoredSearchResult<E>
	{
		protected final XGettingList<Entry<E>> entries;

		private long[] cachedSortedIds;

		public Default(final XGettingList<Entry<E>> entries)
		{
			super();
			this.entries = entries;
		}

		@Override
		public int size()
		{
			return this.entries.intSize();
		}

		@Override
		public boolean isEmpty()
		{
			return this.entries.isEmpty();
		}

		@Override
		public Iterator<Entry<E>> iterator()
		{
			return this.entries.iterator();
		}

		@Override
		public <P extends Consumer<? super Entry<E>>> P iterate(final P procedure)
		{
			this.entries.iterate(procedure);
			return procedure;
		}

		/**
		 * Returns an ordered {@link EntityIdMatcher} built from the entity ids of this
		 * result. The ids are collected and sorted ascending on first call and cached as
		 * a plain {@code long[]}. Each call returns a <b>fresh</b>
		 * {@link EntityIdMatcher.AscendingListWrapper} over the cached ids, so the
		 * matcher's cursor state is never shared across consumers. Scores are dropped in
		 * the conversion.
		 */
		@Override
		public synchronized EntityIdMatcher provideEntityIdMatcher()
		{
			if(this.cachedSortedIds == null)
			{
				final long[] ids = new long[this.size()];
				int i = 0;
				for(final Entry<E> entry : this)
				{
					ids[i++] = entry.entityId();
				}
				Arrays.sort(ids);
				this.cachedSortedIds = ids;
			}
			return this.cachedSortedIds.length == 0
				? EntityIdMatcher.Empty()
				: new EntityIdMatcher.AscendingListWrapper(this.cachedSortedIds);
		}

		@Override
		public ScoredSearchResult<E> and(final GigaMap.SubQuery other)
		{
			final EntityIdMatcher matcher = other.provideEntityIdMatcher();

			// 1. Collect entity ids in ascending order — required by the ordered-matcher call contract.
			final long[] sortedIds = new long[this.size()];
			int i = 0;
			for(final Entry<E> entry : this.entries)
			{
				sortedIds[i++] = entry.entityId();
			}
			Arrays.sort(sortedIds);

			// 2. Probe the matcher, honoring the ordered-matcher contract:
			//    - matchEntityId must be called with monotonically increasing ids.
			//    - When a call returns V > entityId ("next candidate id"), V is already confirmed
			//      as a match and the matcher is positioned at V. We must NOT re-query
			//      matchEntityId(V) — doing so would advance the cursor past V on some
			//      implementations (e.g. BitmapEntityIdMatcher) and lose the match.
			final Set<Long> kept = new HashSet<>();
			long nextValid = -1L;
			for(final long id : sortedIds)
			{
				if(id < nextValid)
				{
					// Known "not contained" range below the matcher's next candidate.
					continue;
				}
				if(id == nextValid)
				{
					// The matcher already confirmed this id in a previous case-#3 return.
					kept.add(id);
					nextValid = -1L;
					continue;
				}
				final long result = matcher.matchEntityId(id);
				if(result == id)
				{
					kept.add(id);
				}
				else if(result > id)
				{
					nextValid = result;
				}
				// result < 0: not contained, skip
			}

			// 3. Rebuild the entry list, preserving original score-descending order.
			final BulkList<Entry<E>> filtered = BulkList.New(kept.size());
			for(final Entry<E> entry : this.entries)
			{
				if(kept.contains(entry.entityId()))
				{
					filtered.add(entry);
				}
			}
			return new ScoredSearchResult.Default<>(filtered);
		}

		@Override
		public String toString()
		{
			return "ScoredSearchResult[size=" + this.entries.size() + "]";
		}
	}
}
