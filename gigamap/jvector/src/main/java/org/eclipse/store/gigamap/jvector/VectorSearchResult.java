package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
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
 * Represents the result of a vector similarity search.
 * <p>
 * Contains the entity IDs and similarity scores, ordered by decreasing similarity.
 * Entities are resolved lazily via a back-reference to the {@link GigaMap}.
 * <p>
 * A {@code VectorSearchResult} also acts as a {@link GigaMap.SubQuery}, so it can be combined
 * with other {@link GigaQuery} conditions via {@link GigaQuery#and(GigaMap.SubQuery)}.
 * When used as a sub-query, only the set of matched entity ids contributes to the combined
 * query; the similarity scores are not taken into account.
 *
 * @param <E> the entity type
 */
public interface VectorSearchResult<E> extends ScoredSearchResult<E>
{
    /**
     * Default implementation of {@link VectorSearchResult}.
     *
     * @param <E> the entity type
     */
    public static class Default<E> extends ScoredSearchResult.Default<E> implements VectorSearchResult<E>
    {
        Default(final XGettingList<Entry<E>> entries)
        {
            super(entries);
        }
    }

}
