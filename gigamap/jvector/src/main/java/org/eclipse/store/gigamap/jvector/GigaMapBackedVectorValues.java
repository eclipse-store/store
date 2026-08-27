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
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * RandomAccessVectorValues backed by a GigaMap.
 * <p>
 * The graph ordinal is the source entity id. Resolution is delegated to a caller-supplied
 * lookup rather than being positional, since the vector store's own id allocator can drift
 * from the source entity ids (e.g. when some entities have no embedding). The lookup returns
 * the raw {@code float[]} for an ordinal, or {@code null} if the entity has no vector.
 */
class GigaMapBackedVectorValues implements RandomAccessVectorValues
{
    protected final IntFunction<float[]> vectorLookup     ;
    protected final IntSupplier          sizeSupplier     ;
    protected final int                  dimension        ;
    protected final VectorTypeSupport    vectorTypeSupport;

    GigaMapBackedVectorValues(
        final IntFunction<float[]> vectorLookup     ,
        final IntSupplier          sizeSupplier     ,
        final int                  dimension        ,
        final VectorTypeSupport    vectorTypeSupport
    )
    {
        this.vectorLookup      = vectorLookup     ;
        this.sizeSupplier      = sizeSupplier     ;
        this.dimension         = dimension        ;
        this.vectorTypeSupport = vectorTypeSupport;
    }

    /**
     * Convenience constructor that resolves ordinals directly against a vector store by source
     * entity id (see {@link VectorEntry#lookup(GigaMap, long)}).
     */
    GigaMapBackedVectorValues(
        final GigaMap<VectorEntry> vectorStore      ,
        final int                  dimension        ,
        final VectorTypeSupport    vectorTypeSupport
    )
    {
        this(storeLookup(vectorStore), () -> Math.toIntExact(vectorStore.size()), dimension, vectorTypeSupport);
    }

    private static IntFunction<float[]> storeLookup(final GigaMap<VectorEntry> vectorStore)
    {
        return ordinal ->
        {
            final VectorEntry entry = VectorEntry.lookup(vectorStore, ordinal);
            return entry == null ? null : entry.vector;
        };
    }

    @Override
    public int size()
    {
        return this.sizeSupplier.getAsInt();
    }

    @Override
    public int dimension()
    {
        return this.dimension;
    }

    @Override
    public VectorFloat<?> getVector(final int ordinal)
    {
        final float[] vector = this.vectorLookup.apply(ordinal);
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
        return new GigaMapBackedVectorValues(
            this.vectorLookup,
            this.sizeSupplier,
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
            final IntFunction<float[]> vectorLookup     ,
            final IntSupplier          sizeSupplier     ,
            final int                  dimension        ,
            final VectorTypeSupport    vectorTypeSupport
        )
        {
            super(vectorLookup, sizeSupplier, dimension, vectorTypeSupport);
        }

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
                this.vectorLookup,
                this.sizeSupplier,
                this.dimension,
                this.vectorTypeSupport
            );
        }

    }

}
