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


import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SourceSet} and the holds of {@link IndexSource}: a source stays open while any set
 * listing it, or any search holding such a set, is alive, and its slot returns to the pool only then.
 */
class SourceSetTest
{
    private static final VectorTypeSupport VTS = VectorizationProvider.getInstance().getVectorTypeSupport();

    @SuppressWarnings("deprecation")
    private static HeapGeneration frozen(
        final OwnerTable owners,
        final String     name
    )
    {
        final List<VectorFloat<?>> vectors = List.of(VTS.createFloatVector(new float[]{1f, 0f}));
        final GraphIndexBuilder builder = new GraphIndexBuilder(
            BuildScoreProvider.randomAccessScoreProvider(
                new ListRandomAccessVectorValues(vectors, 2), VectorSimilarityFunction.COSINE
            ),
            2, 4, 10, 1.2f, 1.2f, true
        );
        builder.addGraphNode(0, vectors.get(0));
        return HeapGeneration.Freeze(
            owners.allocate(), builder, new GenerationVectorValues(null, null), null, List.of(), name
        );
    }

    @Test
    void release_lastHolder_closesTheSourcesAndFreesTheirSlots()
    {
        final OwnerTable     owners = OwnerTable.New();
        final HeapGeneration source = frozen(owners, "a");
        final SourceSet      set    = new SourceSet(List.of(), List.of(source), owners);
        assertEquals(1, owners.slotsInUse());

        set.release();

        assertEquals(0, owners.slotsInUse());
        assertThrows(IllegalStateException.class, source::retain, "a closed source cannot be held again");
    }

    /**
     * The publication pattern of a flush: a new set replaces the old one, which is released, while a
     * search still holds the old one. The source the new set no longer lists stays open until that
     * search releases.
     */
    @Test
    void release_replacedSetHeldBySearch_keepsItsSourcesOpenUntilTheSearchReleases()
    {
        final OwnerTable     owners  = OwnerTable.New();
        final HeapGeneration retired = frozen(owners, "retired");
        final HeapGeneration kept    = frozen(owners, "kept");
        final SourceSet      old     = new SourceSet(List.of(), List.of(retired, kept), owners);

        assertTrue(old.tryAcquire(), "a search acquires the published set");
        final SourceSet next = old.with(List.of(), List.of(kept));
        old.release();

        assertEquals(2, owners.slotsInUse(), "the search still holds the retired source");
        retired.retain().release();

        old.release();
        assertEquals(1, owners.slotsInUse());
        assertFalse(old.tryAcquire(), "a set released for good cannot be acquired again");

        next.release();
        assertEquals(0, owners.slotsInUse());
    }

    @Test
    void construct_withAClosedSource_throwsAndReleasesTheOthers()
    {
        final OwnerTable     owners = OwnerTable.New();
        final HeapGeneration open   = frozen(owners, "open");
        final HeapGeneration closed = frozen(owners, "closed");
        closed.close();

        assertThrows(IllegalStateException.class, () -> new SourceSet(List.of(), List.of(open, closed), owners));

        // The construction's hold on the open source was given back; as its last holder, that closed it.
        assertEquals(0, owners.slotsInUse());
        assertThrows(IllegalStateException.class, open::retain);
    }

    @Test
    void close_heldSource_isANoOp()
    {
        final OwnerTable     owners = OwnerTable.New();
        final HeapGeneration source = frozen(owners, "a");
        final SourceSet      set    = new SourceSet(List.of(), List.of(source), owners);

        source.close();
        assertEquals(1, owners.slotsInUse(), "close() of a held source must leave it to its holders");

        set.release();
        assertEquals(0, owners.slotsInUse());
    }

}
