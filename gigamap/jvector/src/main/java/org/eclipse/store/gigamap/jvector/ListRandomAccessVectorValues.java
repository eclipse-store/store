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

import java.util.List;

/**
 * RandomAccessVectorValues backed by a List of VectorFloat.
 * Used for PQ training.
 */
class ListRandomAccessVectorValues implements RandomAccessVectorValues
{
    private final List<VectorFloat<?>> vectors  ;
    private final int                  dimension;

    ListRandomAccessVectorValues(final List<VectorFloat<?>> vectors, final int dimension)
    {
        this.vectors   = vectors  ;
        this.dimension = dimension;
    }

    @Override
    public int size()
    {
        return this.vectors.size();
    }

    @Override
    public int dimension()
    {
        return this.dimension;
    }

    @Override
    public VectorFloat<?> getVector(final int ordinal)
    {
        if(ordinal < 0 || ordinal >= this.vectors.size())
        {
            return null;
        }
        return this.vectors.get(ordinal);
    }

    @Override
    public boolean isValueShared()
    {
        return false;
    }

    @Override
    public RandomAccessVectorValues copy()
    {
        return new ListRandomAccessVectorValues(this.vectors, this.dimension);
    }

}
