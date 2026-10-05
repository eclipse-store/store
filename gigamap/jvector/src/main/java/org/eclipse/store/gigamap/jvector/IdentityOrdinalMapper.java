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

/**
 * Identity old-to-new ordinal mapping over the present nodes of an in-memory graph, so an on-disk
 * write PRESERVES graph ordinals (leaving {@link #OMITTED} holes) instead of compacting them via
 * jvector's default {@code sequentialRenumbering}.
 * <p>
 * The whole {@link VectorIndex} integration keys on the invariant "graph ordinal == source entity
 * id": search results are converted straight back to entity ids ({@code convertSearchResult}),
 * computed-mode scoring resolves vectors by source entity id ({@code lookupComputedVector} /
 * {@code computedIdIndex}), and incremental deletes track ordinals ({@code diskDeletedOrdinals}).
 * Compacting to a dense 0..n-1 range would renumber disk nodes and scramble that mapping whenever
 * the ordinal space has holes - i.e. after any null embedding or deletion. Preserving ordinals
 * costs a placeholder slot per hole on disk, which is acceptable given entity ids are allocated
 * densely.
 * <p>
 * The mapping is answered from the graph itself, so a persist allocates nothing per node. It
 * returns exactly what {@code OrdinalMapper.MapMapper} over a {@code Map} holding one
 * {@code ordinal -> ordinal} entry per present node would, which keeps the written files
 * byte-identical to that mapping. In particular {@link #maxOrdinal()} is the highest
 * <em>present</em> ordinal, not {@code getIdUpperBound() - 1}: removed nodes at the top of the id
 * range would otherwise become trailing placeholder records and raise the header's id bound.
 * <p>
 * The graph must not change while a writer uses the mapper, since {@link #maxOrdinal()} is fixed
 * at construction and {@link #newToOld(int)} reads the graph on every call. Persist Phase 2
 * guarantees this: it holds {@code builderLock.writeLock()}, and GigaMap mutations defer while
 * the persist is in progress. Within that window the mapper is safe to use from the parallel
 * writer's worker threads, as it only reads the graph's concurrent node set.
 */
final class IdentityOrdinalMapper implements OrdinalMapper
{
    ///////////////////////////////////////////////////////////////////////////
    // instance fields //
    ////////////////////

    private final OnHeapGraphIndex index     ;
    private final int              maxOrdinal;



    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    /**
     * Creates a mapper over the nodes {@code index} holds now.
     *
     * @param index the graph about to be written; must not change while the mapper is in use
     */
    IdentityOrdinalMapper(final OnHeapGraphIndex index)
    {
        this.index      = index;
        this.maxOrdinal = highestPresentOrdinal(index);
    }



    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

    /**
     * Returns the highest present ordinal, or {@code -1} if the graph holds no node.
     */
    @Override
    public int maxOrdinal()
    {
        return this.maxOrdinal;
    }

    /**
     * Returns {@code oldOrdinal} unchanged.
     *
     * @throws IllegalStateException if the graph holds no node with that ordinal; the writer only
     *         maps present nodes and their neighbors, so this means a dangling reference that would
     *         otherwise be written silently
     */
    @Override
    public int oldToNew(final int oldOrdinal)
    {
        if(!this.index.containsNode(oldOrdinal))
        {
            throw new IllegalStateException("No node with ordinal " + oldOrdinal + " in the graph being written");
        }
        return oldOrdinal;
    }

    /**
     * Returns {@code newOrdinal} if the graph holds that node, {@link #OMITTED} otherwise.
     */
    @Override
    public int newToOld(final int newOrdinal)
    {
        return this.index.containsNode(newOrdinal)
            ? newOrdinal
            : OMITTED
        ;
    }

    private static int highestPresentOrdinal(final OnHeapGraphIndex index)
    {
        // Scans down over the removed ids at the top only, so this is O(trailing holes).
        int ordinal = index.getIdUpperBound() - 1;
        while(ordinal >= 0 && !index.containsNode(ordinal))
        {
            ordinal--;
        }
        return ordinal;
    }

}
