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

/**
 * A graph builder's vector source with one reusable destination per copy. The delegate must handle holes,
 * for example through {@link NullSafeVectorValues}.
 * <p>
 * Exact construction scores read a vector for every comparison. Copying into this destination avoids allocating
 * a dimension-sized array each time a mapped vector is read. Declaring the value shared is essential: jVector
 * then gives each build worker two independent copies, keeping the left operand stable while reading the right
 * operand during diversity scoring. This wrapper must not be used where a caller retains returned vectors.
 */
final class ReusableVectorValues implements RandomAccessVectorValues
{
    private final RandomAccessVectorValues delegate;
    private final VectorTypeSupport        types;
    private final VectorFloat<?>           scratch;

    ReusableVectorValues(final RandomAccessVectorValues delegate, final VectorTypeSupport types)
    {
        this.delegate = delegate;
        this.types    = types;
        this.scratch  = types.createFloatVector(delegate.dimension());
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
        this.delegate.getVectorInto(ordinal, this.scratch, 0);
        return this.scratch;
    }

    @Override
    public void getVectorInto(final int ordinal, final VectorFloat<?> destination, final int offset)
    {
        this.delegate.getVectorInto(ordinal, destination, offset);
    }

    @Override
    public boolean isValueShared()
    {
        return true;
    }

    @Override
    public RandomAccessVectorValues copy()
    {
        return new ReusableVectorValues(this.delegate.copy(), this.types);
    }
}
