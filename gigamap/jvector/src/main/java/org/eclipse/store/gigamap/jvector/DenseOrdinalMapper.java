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


import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import io.github.jbellis.jvector.graph.disk.OrdinalMapper;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Old-to-new ordinal mapping that writes the present nodes of an in-memory graph as a dense run of
 * local ordinals {@code 0..count-1}, in ascending order of their graph ordinal.
 * <p>
 * The graph ordinals are source entity ids, and those are sparse: null embeddings, removals and a
 * segment that only holds the entities added since the last flush all leave gaps. jVector writes one
 * full record for every ordinal up to {@link #maxOrdinal()}, a zero record for a gap, so preserving
 * the entity ids on disk costs a whole node record per gap. A segment covering ids 9M to 10M at
 * dimension 768 would carry 9M empty records of about 3 KB each. Writing densely makes the file
 * proportional to the node count, and the segment's {@code l2g} array ({@link #localToGlobal()})
 * translates back.
 * <p>
 * The written order is ascending global ordinal, which is exactly the mapping jVector's own
 * {@code sequentialRenumbering} produces, so the files are byte-identical to a write through that
 * map. The difference is the cost: the map boxes an entry per node, while this holds one
 * {@code int[]}, and {@link #oldToNew(int)} is a binary search over it.
 * <p>
 * jVector calls the feature state suppliers with the <em>old</em> ordinal (see
 * {@code OnDiskGraphIndexWriter.write}), so suppliers stay keyed by global ordinal.
 * <p>
 * Immutable and therefore safe to use from the parallel writer's worker threads. The graph must not
 * change while it is written, which persist Phase 2 already guarantees.
 */
final class DenseOrdinalMapper implements OrdinalMapper
{
    ///////////////////////////////////////////////////////////////////////////
    // instance fields //
    ////////////////////

    private final int[] localToGlobal;



    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    /**
     * Creates a mapper over a strictly ascending local-to-global array.
     *
     * @param localToGlobal the global ordinal of every local ordinal, strictly ascending; not copied
     * @throws IllegalArgumentException if the array is not strictly ascending or holds a negative value
     */
    DenseOrdinalMapper(final int[] localToGlobal)
    {
        validateAscending(localToGlobal);
        this.localToGlobal = localToGlobal;
    }

    /**
     * Creates a mapper over the nodes {@code index} holds now.
     *
     * @param index the graph about to be written; must not change while the mapper is in use
     * @return the mapper
     */
    static DenseOrdinalMapper of(final OnHeapGraphIndex index)
    {
        return new DenseOrdinalMapper(presentOrdinals(index));
    }

    /**
     * Returns the ordinals of the nodes {@code index} holds, in ascending order.
     * <p>
     * The same {@code containsNode} scan over the id range that jVector's {@code sequentialRenumbering}
     * does, so both agree on which nodes are present. The result is sized by the node count rather
     * than by the id range, since the id range of a sparse graph can be far larger than its node
     * count. The scan itself is proportional to the id range, and cannot be cheaper here: jVector
     * keeps layer 0 in a dense array indexed by ordinal, which the graph already holds, and its own
     * {@code nodeStream(0)} scans the same range.
     *
     * @param index the graph
     * @return the present ordinals, strictly ascending
     */
    static int[] presentOrdinals(final OnHeapGraphIndex index)
    {
        return IntStream.range(0, index.getIdUpperBound())
            .filter(index::containsNode)
            .toArray()
        ;
    }



    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

    /**
     * Returns the local-to-global array this mapper writes with. Not a copy: the caller must not
     * modify it.
     *
     * @return the local-to-global array
     */
    int[] localToGlobal()
    {
        return this.localToGlobal;
    }

    /**
     * Returns the highest local ordinal.
     *
     * @return the highest local ordinal, {@code -1} for an empty graph
     */
    @Override
    public int maxOrdinal()
    {
        return this.localToGlobal.length - 1;
    }

    /**
     * Returns the local ordinal of a global one.
     *
     * @param oldOrdinal the global ordinal, i.e. the entity id
     * @return the local ordinal it is written under
     * @throws IllegalStateException if the graph being written holds no node with that ordinal; the
     *         writer only maps present nodes and their neighbors, so this means a dangling reference
     *         that would otherwise be written silently
     */
    @Override
    public int oldToNew(final int oldOrdinal)
    {
        final int local = Arrays.binarySearch(this.localToGlobal, oldOrdinal);
        if(local < 0)
        {
            throw new IllegalStateException("No node with ordinal " + oldOrdinal + " in the graph being written");
        }
        return local;
    }

    /**
     * Returns the global ordinal of a local one.
     *
     * @param newOrdinal the local ordinal
     * @return the global ordinal, {@link #OMITTED} outside {@code 0..maxOrdinal()}
     */
    @Override
    public int newToOld(final int newOrdinal)
    {
        return newOrdinal >= 0 && newOrdinal < this.localToGlobal.length
            ? this.localToGlobal[newOrdinal]
            : OMITTED
        ;
    }

    private static void validateAscending(final int[] localToGlobal)
    {
        int previous = -1;
        for(final int global : localToGlobal)
        {
            if(global <= previous)
            {
                throw new IllegalArgumentException(
                    "Local-to-global ordinals must be non-negative and strictly ascending, got " + global
                        + " after " + previous
                );
            }
            previous = global;
        }
    }

}
