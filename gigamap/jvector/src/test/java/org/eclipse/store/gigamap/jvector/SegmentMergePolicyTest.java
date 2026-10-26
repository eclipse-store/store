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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The size-tiered selection of {@link SegmentMergePolicy}, without an index.
 */
class SegmentMergePolicyTest
{
    private static final int BASE = 1_000;

    @Test
    void select_fewerSegmentsThanTheFactor_mergesNothing()
    {
        final SegmentMergePolicy policy = SegmentMergePolicy.New(BASE, 4, 32);

        final List<Long> selected = policy.select(List.of(live(1, 900), live(2, 1_000), live(3, 1_100)), 1);

        assertTrue(selected.isEmpty());
    }

    @Test
    void select_aFullTier_mergesItsSmallestSegments()
    {
        final SegmentMergePolicy policy = SegmentMergePolicy.New(BASE, 3, 32);
        final List<SegmentMergePolicy.Candidate> candidates = List.of(
            live(1, 1_000), live(2, 900), live(3, 1_100), live(4, 950)
        );

        final List<Long> selected = policy.select(candidates, 1);

        assertEquals(List.of(2L, 4L, 1L), selected, "the three smallest of tier 0, smallest first");
    }

    @Test
    void select_segmentsOfDifferentTiers_areNotMergedTogether()
    {
        final SegmentMergePolicy policy = SegmentMergePolicy.New(BASE, 2, 32);
        // Tier 0 below 2,000 live nodes, tier 1 below 4,000, tier 2 below 8,000.
        final List<SegmentMergePolicy.Candidate> candidates = List.of(
            live(1, 1_500), live(2, 3_000), live(3, 7_000)
        );

        assertTrue(policy.select(candidates, 1).isEmpty());
    }

    @Test
    void select_twoFullTiers_mergesTheLowerOneFirst()
    {
        final SegmentMergePolicy policy = SegmentMergePolicy.New(BASE, 2, 32);
        final List<SegmentMergePolicy.Candidate> candidates = List.of(
            live(1, 3_000), live(2, 3_500), live(3, 1_000), live(4, 1_200)
        );

        assertEquals(List.of(3L, 4L), policy.select(candidates, 1));
    }

    @Test
    void select_segmentsNeedingARewrite_areMergedFirstWhateverTheirSize()
    {
        final SegmentMergePolicy policy = SegmentMergePolicy.New(BASE, 2, 32);
        final List<SegmentMergePolicy.Candidate> candidates = List.of(
            live(1, 1_000), live(2, 1_000), rewrite(3, 50_000)
        );

        assertEquals(List.of(3L), policy.select(candidates, 1), "a single one is rewritten on its own");
    }

    @Test
    void select_segmentWithNothingLive_isLeftToTheFlush()
    {
        final SegmentMergePolicy policy = SegmentMergePolicy.New(BASE, 2, 32);
        final List<SegmentMergePolicy.Candidate> candidates = List.of(
            live(1, 0), new SegmentMergePolicy.Candidate(2, 0, true), live(3, 1_000)
        );

        assertTrue(policy.select(candidates, 1).isEmpty());
    }

    @Test
    void select_nextFlushReachesTheCap_mergesTheSmallestAcrossTiers()
    {
        final SegmentMergePolicy policy = SegmentMergePolicy.New(BASE, 4, 4);
        final List<SegmentMergePolicy.Candidate> candidates = List.of(
            live(1, 100_000), live(2, 1_000), live(3, 20_000)
        );

        assertEquals(List.of(2L, 3L, 1L), policy.select(candidates, 1),
            "three segments and the live graph reach the cap of four");
    }

    @Test
    void select_factorAboveTheCap_isCappedAtTheCap()
    {
        final SegmentMergePolicy policy = SegmentMergePolicy.New(BASE, 8, 3);

        final List<Long> selected = policy.select(List.of(live(1, 1_000), live(2, 1_000), live(3, 1_000)), 0);

        assertEquals(List.of(1L, 2L, 3L), selected);
    }

    @Test
    void select_manySegmentsInOneTier_mergesExactlyTheFactor()
    {
        final SegmentMergePolicy                 policy     = SegmentMergePolicy.New(BASE, 4, 240);
        final List<SegmentMergePolicy.Candidate> candidates = new ArrayList<>();
        for(int i = 0; i < 20; i++)
        {
            candidates.add(live(i, 1_000 + i));
        }

        assertEquals(List.of(0L, 1L, 2L, 3L), policy.select(candidates, 1));
    }

    @Test
    void tierOf_largeLiveCounts_doesNotOverflow()
    {
        final SegmentMergePolicy.Default policy = (SegmentMergePolicy.Default)SegmentMergePolicy.New(
            Integer.MAX_VALUE, 240, 240
        );

        assertEquals(0, policy.tierOf(Integer.MAX_VALUE));
        assertTrue(policy.tierOf(Long.MAX_VALUE) >= 1);
    }

    @Test
    void new_invalidSettings_areRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> SegmentMergePolicy.New(0, 4, 32));
        assertThrows(IllegalArgumentException.class, () -> SegmentMergePolicy.New(BASE, 1, 32));
        assertThrows(IllegalArgumentException.class, () -> SegmentMergePolicy.New(BASE, 4, 1));
    }

    private static SegmentMergePolicy.Candidate live(
        final long sequence ,
        final long liveCount
    )
    {
        return new SegmentMergePolicy.Candidate(sequence, liveCount, false);
    }

    private static SegmentMergePolicy.Candidate rewrite(
        final long sequence ,
        final long liveCount
    )
    {
        return new SegmentMergePolicy.Candidate(sequence, liveCount, true);
    }

}
