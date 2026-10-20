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

/**
 * Presents vectors indexed by global ordinal (= source entity id) under the dense local ordinals of
 * one on-disk segment.
 * <p>
 * A segment's graph numbers its nodes {@code 0..count-1}, so every score function that reads vectors
 * from outside the segment file - the exact reranker over the live vectors, the encoder writing a
 * segment's PQ codes - has to translate first. Doing it in this one view keeps that translation in a
 * single place, and wrapping the per-query caching values of the index lets all segments of one
 * query share one cache keyed by global ordinal.
 * <p>
 * Holds no state of its own beyond the two references, so it is as thread-safe as the delegate.
 */
final class SegmentVectorValues implements RandomAccessVectorValues
{
    ///////////////////////////////////////////////////////////////////////////
    // instance fields //
    ////////////////////

    private final RandomAccessVectorValues globalValues ;
    private final int[]                    localToGlobal;



    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    /**
     * @param globalValues  the vectors, indexed by global ordinal
     * @param localToGlobal the segment's local-to-global array; not copied and must not be modified
     */
    SegmentVectorValues(
        final RandomAccessVectorValues globalValues ,
        final int[]                    localToGlobal
    )
    {
        this.globalValues  = globalValues ;
        this.localToGlobal = localToGlobal;
    }



    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

    @Override
    public int size()
    {
        return this.localToGlobal.length;
    }

    @Override
    public int dimension()
    {
        return this.globalValues.dimension();
    }

    @Override
    public VectorFloat<?> getVector(final int localOrdinal)
    {
        return this.globalValues.getVector(this.localToGlobal[localOrdinal]);
    }

    @Override
    public boolean isValueShared()
    {
        return this.globalValues.isValueShared();
    }

    @Override
    public RandomAccessVectorValues copy()
    {
        return new SegmentVectorValues(this.globalValues.copy(), this.localToGlobal);
    }

}
