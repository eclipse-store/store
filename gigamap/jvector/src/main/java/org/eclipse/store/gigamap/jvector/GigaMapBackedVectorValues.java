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
 * RandomAccessVectorValues backed by a GigaMap.
 * Uses entity ID directly as ordinal.
 */
class GigaMapBackedVectorValues implements RandomAccessVectorValues
{
    protected final GigaMap<VectorEntry> vectorStore      ;
    protected final int                  dimension        ;
    protected final VectorTypeSupport    vectorTypeSupport;

    GigaMapBackedVectorValues(
        final GigaMap<VectorEntry> vectorStore      ,
        final int                  dimension        ,
        final VectorTypeSupport    vectorTypeSupport
    )
    {
        this.vectorStore       = vectorStore      ;
        this.dimension         = dimension        ;
        this.vectorTypeSupport = vectorTypeSupport;
    }

    @Override
    public int size()
    {
        return (int)this.vectorStore.size();
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
        final VectorEntry entry = this.vectorStore.get(ordinal);
        if(entry == null)
        {
            return null;
        }
        return this.vectorTypeSupport.createFloatVector(entry.vector);
    }

    @Override
    public boolean isValueShared()
    {
        return false;
    }

    @Override
    public RandomAccessVectorValues copy()
    {
        return new GigaMapBackedVectorValues(
            this.vectorStore,
            this.dimension,
            this.vectorTypeSupport
        );
    }


    /**
     * Caching version of GigaMapBackedVectorValues.
     * Caches vectors during search to avoid repeated GigaMap lookups.
     */
    static class Caching extends GigaMapBackedVectorValues
    {
        private final Map<Integer, VectorFloat<?>> cache = new ConcurrentHashMap<>();

        Caching(
            final GigaMap<VectorEntry> vectorStore      ,
            final int                  dimension        ,
            final VectorTypeSupport    vectorTypeSupport
        )
        {
            super(vectorStore, dimension, vectorTypeSupport);
        }

        @Override
        public VectorFloat<?> getVector(final int ordinal)
        {
            return this.cache.computeIfAbsent(ordinal, super::getVector);
        }

        @Override
        public RandomAccessVectorValues copy()
        {
            return new Caching(
                this.vectorStore,
                this.dimension,
                this.vectorTypeSupport
            );
        }

    }

}
