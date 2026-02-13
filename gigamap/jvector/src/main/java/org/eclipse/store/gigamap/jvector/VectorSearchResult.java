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
import org.eclipse.serializer.collections.types.XIterable;
import org.eclipse.store.gigamap.types.GigaMap;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Represents the result of a vector similarity search.
 * <p>
 * Contains the entities, their IDs and similarity scores, ordered by decreasing similarity.
 * Entities are resolved lazily via a back-reference to the GigaMap.
 *
 * @param <E> the entity type
 */
public interface VectorSearchResult<E> extends Iterable<VectorSearchResult.Entry<E>>, XIterable<VectorSearchResult.Entry<E>>
{
    /**
     * Returns the number of results.
     *
     * @return result count
     */
    public int size();

    /**
     * Returns whether the result is empty.
     *
     * @return true if no results
     */
    public boolean isEmpty();

    /**
     * Creates a sequential {@link Stream} of {@link Entry} objects
     * present in the current search results.
     * <p>
     * The stream provides a convenient way of iterating over the underlying {@link Entry}
     * instances, enabling operations such as filtering, mapping, and aggregation.
     *
     * @return a {@link Stream} of {@link Entry} objects corresponding to
     *         the search results
     */
    public default Stream<Entry<E>> stream()
    {
        /*
         * A custom Spliterator implementation was considered but deemed unnecessary since
         * search results are typically small (k=10-100) and the overhead of parallel
         * processing or optimized splitting exceeds any benefit at this scale.
         */
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
     * Converts the current search results into a {@link List} of {@link Entry} objects.
     * This method provides a straightforward way to collect all the search result entries
     * in a list format for further processing or analysis.
     *
     * @return a list containing all the {@link Entry} objects in the search results
     */
    public default List<Entry<E>> toList()
    {
        return this.stream().toList();
    }


    /**
     * Represents a single search result with entity ID, similarity score, and lazy entity access.
     *
     * @param <E> the entity type
     */
    public static interface Entry<E>
    {
        /**
         * Returns the unique identifier of the entity associated with this entry.
         *
         * @return the entity's unique identifier as a long
         */
        public long entityId();

        /**
         * Returns the similarity score associated with this entry.
         * The score represents how relevant or similar this entry is
         * relative to a query or reference point.
         *
         * @return the similarity score as a floating-point value
         */
        public float score();


        /**
         * Returns the entity associated with this search result.
         * The entity is resolved lazily from the GigaMap.
         *
         * @return the entity
         */
        public E entity();


        public static class Default<E> implements Entry<E>
        {
            private final long       entityId;
            private final float      score;
            private final GigaMap<E> gigaMap;

            Default(final long entityId, final float score, final GigaMap<E> gigaMap)
            {
                this.entityId = entityId;
                this.score    = score   ;
                this.gigaMap  = gigaMap ;
            }

            public long entityId()
            {
                return this.entityId;
            }

            public float score()
            {
                return this.score;
            }

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
     * Default implementation of VectorResult.
     *
     * @param <E> the entity type
     */
    public static class Default<E> implements VectorSearchResult<E>
    {
        private final XGettingList<Entry<E>> entries;

        Default(final XGettingList<Entry<E>> entries)
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

        @Override
        public String toString()
        {
            return "VectorResult[size=" + this.entries.size() + "]";
        }

    }

}
