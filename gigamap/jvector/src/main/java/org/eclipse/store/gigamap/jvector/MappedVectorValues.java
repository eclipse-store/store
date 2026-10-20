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

import java.util.function.IntFunction;

/**
 * The vectors of an index, read from its {@link VectorFile} by graph ordinal, without any lock.
 * <p>
 * Every vector read of the index goes through this: the graph builder's scoring, the exact rerank of a search,
 * rebuilds, consolidations and PQ training. None of them takes the parent GigaMap monitor or any other lock, so a
 * worker thread can read vectors while the thread that started it holds the monitor.
 * <p>
 * Each {@link #getVector(int)} copies one vector out of the mapping. jVector cannot score straight from the
 * mapping: under the Panama vectorization provider every SIMD operation casts its operands to its array-backed
 * vector type. {@link #getVectorInto(int, VectorFloat, int)} copies straight into an array-backed destination
 * instead of through an intermediate vector.
 * <p>
 * {@link #size()} is an upper bound of the ordinals ever written, not a vector count: ordinals are entity ids, and
 * an ordinal below it may be absent, for which {@link #getVector(int)} returns {@code null}. Wrap the values in a
 * {@link NullSafeVectorValues} for anything that cannot take a {@code null}.
 * <p>
 * Stateless apart from the file, so it is safe for concurrent use and {@link #copy()} returns itself.
 */
class MappedVectorValues implements RandomAccessVectorValues
{
    protected final VectorFile        file             ;
    protected final VectorTypeSupport vectorTypeSupport;

    MappedVectorValues(
        final VectorFile        file             ,
        final VectorTypeSupport vectorTypeSupport
    )
    {
        this.file              = file             ;
        this.vectorTypeSupport = vectorTypeSupport;
    }

    @Override
    public int size()
    {
        // Checked: the highest ordinal can be Integer.MAX_VALUE, and a wrapped bound would fail later and far
        // from the cause, where jVector sizes an array by it.
        return Math.toIntExact((long)this.file.highestOrdinal() + 1L);
    }

    @Override
    public int dimension()
    {
        return this.file.dimension();
    }

    @Override
    public VectorFloat<?> getVector(final int ordinal)
    {
        final float[] vector = this.file.read(ordinal);
        return vector == null ? null : this.vectorTypeSupport.createFloatVector(vector);
    }

    /**
     * Copies the vector of {@code ordinal} into {@code target}.
     *
     * @param ordinal the ordinal
     * @param target  the array to copy into
     * @param offset  where in {@code target}
     * @return whether the ordinal has a vector; {@code target} is unchanged if not
     */
    boolean readInto(
        final int     ordinal,
        final float[] target ,
        final int     offset
    )
    {
        return this.file.read(ordinal, target, offset);
    }

    @Override
    public void getVectorInto(
        final int            ordinal    ,
        final VectorFloat<?> destination,
        final int            offset
    )
    {
        if(destination.get() instanceof final float[] array
            && this.readInto(ordinal, array, destination.offset(offset)))
        {
            return;
        }
        final VectorFloat<?> vector = this.getVector(ordinal);
        if(vector == null)
        {
            throw new IllegalArgumentException("No vector for ordinal " + ordinal);
        }
        destination.copyFrom(vector, 0, offset, vector.length());
    }

    @Override
    public boolean isValueShared()
    {
        return false;
    }

    @Override
    public RandomAccessVectorValues copy()
    {
        return this;
    }


    /**
     * A variant for one query on one thread that keeps every vector it has read, so a node scored again by the
     * same search is not copied out of the mapping again (see {@link OrdinalVectorCache}).
     */
    static final class Caching extends MappedVectorValues
    {
        private final int                         expectedSize;
        private final OrdinalVectorCache          cache       ;
        private final IntFunction<VectorFloat<?>> loader      = super::getVector;

        Caching(
            final VectorFile        file             ,
            final VectorTypeSupport vectorTypeSupport,
            final int               expectedSize
        )
        {
            super(file, vectorTypeSupport);
            this.expectedSize = expectedSize;
            this.cache        = new OrdinalVectorCache(expectedSize);
        }

        @Override
        public VectorFloat<?> getVector(final int ordinal)
        {
            return this.cache.computeIfAbsent(ordinal, this.loader);
        }

        @Override
        boolean readInto(
            final int     ordinal,
            final float[] target ,
            final int     offset
        )
        {
            final VectorFloat<?> vector = this.getVector(ordinal);
            if(vector == null)
            {
                return false;
            }
            for(int i = 0; i < vector.length(); i++)
            {
                target[offset + i] = vector.get(i);
            }
            return true;
        }

        @Override
        public RandomAccessVectorValues copy()
        {
            return new Caching(this.file, this.vectorTypeSupport, this.expectedSize);
        }
    }

}
