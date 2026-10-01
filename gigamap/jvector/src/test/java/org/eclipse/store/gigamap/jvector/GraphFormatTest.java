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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link GraphFormat}, the persisted description of what a graph file carries.
 */
class GraphFormatTest
{
    private static GraphFormat inMemoryPq(final int pqSubspaces)
    {
        return new GraphFormat(VectorStorage.INLINE, ApproximateScoring.PQ_IN_MEMORY, pqSubspaces, 0);
    }

    /**
     * The automatic subspace count must resolve the way the manager that encodes with it does.
     * <p>
     * {@code PQCompressionManager} resolves the sentinel as {@code Math.max(1, dimension / 4)}, so a
     * dimension below four gives one subspace, not none. A dimension that small is legal - the
     * builder asks only that it be positive - and if this returned zero for it the metadata would
     * record a count the encoder never used.
     * <p>
     * The consequence is not cosmetic. {@code sidecarFitsTheGraph} compares the sidecar's code
     * length against exactly this value, so an on-disk {@code PQ_IN_MEMORY} index at such a
     * dimension would write a one-byte code, record zero, then reject its own sidecar and rebuild
     * on every single load - silently, since a rejected load self-heals from the store.
     */
    @Test
    void automaticPqSubspacesResolveAsTheEncoderDoes()
    {
        for(int dimension = 1; dimension <= 3; dimension++)
        {
            assertEquals(1, inMemoryPq(0).effectivePqSubspaces(dimension),
                "dimension " + dimension + " must resolve to one subspace, the same value"
                    + " PQCompressionManager encodes with, or the sidecar cannot match the meta");
        }
    }

    /**
     * Above the clamp the automatic value is plain division, which is what the encoder uses there
     * too. Pinned alongside the clamp so a future change cannot satisfy one and break the other.
     */
    @Test
    void automaticPqSubspacesAreAQuarterOfTheDimension()
    {
        assertEquals(1  , inMemoryPq(0).effectivePqSubspaces(4)  , "dimension 4");
        assertEquals(16 , inMemoryPq(0).effectivePqSubspaces(64) , "dimension 64");
        assertEquals(192, inMemoryPq(0).effectivePqSubspaces(768), "dimension 768");
    }

    /**
     * An explicit count is recorded as configured, clamp or no clamp.
     */
    @Test
    void anExplicitPqSubspaceCountIsRecordedUnchanged()
    {
        assertEquals(48, inMemoryPq(48).effectivePqSubspaces(768), "explicit count");
        assertEquals(1 , inMemoryPq(1).effectivePqSubspaces(2)   , "explicit count at a tiny dimension");
    }

    /**
     * A format that encodes no PQ records no count, so that two configurations describing the same
     * graph compare equal and do not force a rebuild that would change nothing.
     */
    @Test
    void aFormatWithoutPqRecordsNoSubspaceCount()
    {
        final GraphFormat none =
            new GraphFormat(VectorStorage.INLINE, ApproximateScoring.NONE, 0, 0);
        assertEquals(0, none.effectivePqSubspaces(768),
            "a graph that carries no codes must record no subspace count");
    }
}
