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
 * This wrapper is only used during search operations; the underlying vector
 * values classes retain their original null-returning contract.
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
    private       VectorFloat<?>           cachedPlaceholder;

    NullSafeVectorValues(
        final RandomAccessVectorValues delegate         ,
        final int                      dimension        ,
        final VectorTypeSupport        vectorTypeSupport
    )
    {
        this.delegate          = delegate         ;
        this.dimension         = dimension        ;
        this.vectorTypeSupport = vectorTypeSupport;
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
        if(this.cachedPlaceholder == null)
        {
            final float[] placeholder = new float[this.dimension];
            Arrays.fill(placeholder, PLACEHOLDER_COMPONENT);
            this.cachedPlaceholder = this.vectorTypeSupport.createFloatVector(placeholder);
        }
        return this.cachedPlaceholder;
    }

    @Override
    public boolean isValueShared()
    {
        return this.delegate.isValueShared();
    }

    @Override
    public RandomAccessVectorValues copy()
    {
        return new NullSafeVectorValues(
            this.delegate.copy(),
            this.dimension,
            this.vectorTypeSupport
        );
    }

}
