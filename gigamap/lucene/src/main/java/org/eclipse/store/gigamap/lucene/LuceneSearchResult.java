package org.eclipse.store.gigamap.lucene;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
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

import org.eclipse.serializer.collections.types.XGettingList;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.GigaQuery;
import org.eclipse.store.gigamap.types.ScoredSearchResult;

/**
 * Represents the result of a Lucene full-text search.
 * <p>
 * Contains the entity IDs and their relevance scores, ordered by decreasing relevance.
 * Entities are resolved lazily via a back-reference to the {@link GigaMap}.
 * <p>
 * A {@code LuceneSearchResult} also acts as a {@link GigaMap.SubQuery}, so it can be combined
 * with other {@link GigaQuery} conditions via {@link GigaQuery#and(GigaMap.SubQuery)}.
 * When used as a sub-query, only the set of matched entity ids contributes to the combined
 * query; the relevance scores are not taken into account.
 *
 * @param <E> the entity type
 */
public interface LuceneSearchResult<E> extends ScoredSearchResult<E>
{
	/**
	 * Default implementation of {@link LuceneSearchResult}.
	 *
	 * @param <E> the entity type
	 */
	public static class Default<E> extends ScoredSearchResult.Default<E> implements LuceneSearchResult<E>
	{
		Default(final XGettingList<Entry<E>> entries)
		{
			super(entries);
		}
	}

}
