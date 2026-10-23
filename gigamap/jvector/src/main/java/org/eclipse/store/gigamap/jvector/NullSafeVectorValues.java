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

import java.util.Arrays;

/**
 * Search-scoped wrapper around {@link RandomAccessVectorValues} that returns a
 * placeholder vector instead of {@code null} for deleted or missing nodes.
 * <p>
 * JVector's graph traversal unconditionally computes similarity for nodes it
 * visits (including the entry point), so a {@code null} vector causes
 * {@link NullPointerException}. A zero vector causes {@link AssertionError}
 * in cosine similarity (NaN/Infinity). This wrapper substitutes a tiny but
 * finite-magnitude vector that produces valid similarity scores without
 * affecting search results (deleted nodes are filtered via {@code liveNodes}).
 * <p>
 * Used for searches and as the graph builder's score source, the latter shared by
 * the parallel rebuild's worker threads; thread-safe as long as the delegate is.
 * The underlying vector values classes retain their original null-returning
 * contract.
 */
class NullSafeVectorValues implements RandomAccessVectorValues
{
    /**
     * Small but numerically safe placeholder value for deleted/missing vectors.
     * Cosine similarity computes dot(a,b) / (||a|| * ||b||), so a zero vector
     * causes division by zero (NaN). Values like Float.MIN_VALUE (1.4e-45) or
     * Float.MIN_NORMAL (1.18e-38) are too small — the squared magnitude underflows
     * to zero in JVector's SIMD computation, producing Infinity. This value is large
     * enough for stable arithmetic but small enough that the placeholder scores very
     * low in similarity.
     */
    private static final float PLACEHOLDER_COMPONENT = 1e-6f;

    private final RandomAccessVectorValues delegate         ;
    private final int                      dimension        ;
    private final VectorTypeSupport        vectorTypeSupport;
    // Created lazily on the first hole, because a fresh instance wraps every search's vector
    // values and most queries never visit a hole, so an eager placeholder would cost one
    // dimension-sized vector per search. Volatile because one instance is also the shared score
    // source of all parallel rebuild workers (the switchable source below it is unshared and
    // returns itself from copy()): the volatile write publishes the fully built vector, so no
    // reader can see it half initialised. Two threads may both create one on the first hole; any
    // of them is equally valid, as the vector is never written after creation.
    private volatile VectorFloat<?>        placeholder      ;

    NullSafeVectorValues(
        final RandomAccessVectorValues delegate         ,
        final int                      dimension        ,
        final VectorTypeSupport        vectorTypeSupport
    )
    {
        this(delegate, dimension, vectorTypeSupport, null);
    }

    /**
     * @param placeholder an already created placeholder to share, or {@code null} to create one on
     *                    the first hole
     */
    private NullSafeVectorValues(
        final RandomAccessVectorValues delegate         ,
        final int                      dimension        ,
        final VectorTypeSupport        vectorTypeSupport,
        final VectorFloat<?>           placeholder
    )
    {
        this.delegate          = delegate         ;
        this.dimension         = dimension        ;
        this.vectorTypeSupport = vectorTypeSupport;
        this.placeholder       = placeholder      ;
    }

    private static VectorFloat<?> createPlaceholder(
        final int               dimension        ,
        final VectorTypeSupport vectorTypeSupport
    )
    {
        final float[] placeholder = new float[dimension];
        Arrays.fill(placeholder, PLACEHOLDER_COMPONENT);
        return vectorTypeSupport.createFloatVector(placeholder);
    }

    @Override
    public int size()
    {
        return this.delegate.size();
    }

    @Override
    public int dimension()
    {
        return this.delegate.dimension();
    }

    @Override
    public VectorFloat<?> getVector(final int ordinal)
    {
        final VectorFloat<?> vector = this.delegate.getVector(ordinal);
        if(vector != null)
        {
            return vector;
        }
        VectorFloat<?> placeholder = this.placeholder;
        if(placeholder == null)
        {
            placeholder      = createPlaceholder(this.dimension, this.vectorTypeSupport);
            this.placeholder = placeholder;
        }
        return placeholder;
    }

    @Override
    public void getVectorInto(
        final int            ordinal    ,
        final VectorFloat<?> destination,
        final int            offset
    )
    {
        // Straight into the destination where the mapping allows it, through the placeholder otherwise.
        if(this.delegate instanceof final MappedVectorValues mapped
            && destination.get() instanceof final float[] array
            && mapped.readInto(ordinal, array, destination.offset(offset)))
        {
            return;
        }
        final VectorFloat<?> vector = this.getVector(ordinal);
        destination.copyFrom(vector, 0, offset, vector.length());
    }

    @Override
    public boolean isValueShared()
    {
        return this.delegate.isValueShared();
    }

    @Override
    public RandomAccessVectorValues copy()
    {
        // The placeholder is only ever read, so copies can share it, if it exists yet.
        return new NullSafeVectorValues(
            this.delegate.copy(),
            this.dimension,
            this.vectorTypeSupport,
            this.placeholder
        );
    }

}
