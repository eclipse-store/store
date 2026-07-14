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

import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import org.eclipse.store.gigamap.types.GigaMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RandomAccessVectorValues backed by entities via a GigaMap and Vectorizer.
 * Used when vectorizer.isEmbedded() is true.
 * Reads vectors directly from entities instead of a separate vector store.
 */
class EntityBackedVectorValues<E> implements RandomAccessVectorValues
{
    protected final GigaMap<E>            entityMap        ;
    protected final Vectorizer<? super E> vectorizer       ;
    protected final int                   dimension        ;
    protected final VectorTypeSupport     vectorTypeSupport;

    EntityBackedVectorValues(
        final GigaMap<E>            entityMap        ,
        final Vectorizer<? super E> vectorizer       ,
        final int                   dimension        ,
        final VectorTypeSupport     vectorTypeSupport
    )
    {
        this.entityMap         = entityMap        ;
        this.vectorizer        = vectorizer       ;
        this.dimension         = dimension        ;
        this.vectorTypeSupport = vectorTypeSupport;
    }

    @Override
    public int size()
    {
        // The dense ordinal upper bound (getVector must be valid for [0, size())), not the entity
        // count: the graph ordinal is the entity id, so deletion holes and null-embedding entities
        // (still counted in entityMap.size()) push the highest ordinal beyond the count. Matching the
        // id space keeps PQ encodeAll() (which builds a dense array of length size() and is indexed by
        // ordinal) from skipping/overflowing high ordinals. toIntExact fails fast rather than silently
        // overflowing to a negative size() near the int ordinal ceiling.
        return Math.toIntExact(this.entityMap.highestUsedId() + 1);
    }

    @Override
    public int dimension()
    {
        return this.dimension;
    }

    @Override
    public VectorFloat<?> getVector(final int ordinal)
    {
        // Ordinal IS the entity ID
        final E entity = this.entityMap.get(ordinal);
        if(entity == null)
        {
            return null;
        }
        final float[] vector = this.vectorizer.vectorize(entity);
        if(vector == null)
        {
            return null;
        }
        return this.vectorTypeSupport.createFloatVector(vector);
    }

    @Override
    public boolean isValueShared()
    {
        return false;
    }

    @Override
    public RandomAccessVectorValues copy()
    {
        return new EntityBackedVectorValues<>(
            this.entityMap,
            this.vectorizer,
            this.dimension,
            this.vectorTypeSupport
        );
    }


    /**
     * Caching version of EntityBackedVectorValues.
     * Caches vectors during search to avoid repeated entity lookups and vectorization.
     */
    static class Caching<E> extends EntityBackedVectorValues<E>
    {
        private final Map<Integer, VectorFloat<?>> cache = new ConcurrentHashMap<>();

        Caching(
            final GigaMap<E>            entityMap        ,
            final Vectorizer<? super E> vectorizer       ,
            final int                   dimension        ,
            final VectorTypeSupport     vectorTypeSupport
        )
        {
            super(entityMap, vectorizer, dimension, vectorTypeSupport);
        }

        @Override
        public VectorFloat<?> getVector(final int ordinal)
        {
            return this.cache.computeIfAbsent(ordinal, super::getVector);
        }

        @Override
        public RandomAccessVectorValues copy()
        {
            return new Caching<>(
                this.entityMap,
                this.vectorizer,
                this.dimension,
                this.vectorTypeSupport
            );
        }

    }

}
