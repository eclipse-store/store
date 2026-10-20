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


import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides which segments of an on-disk index a background merge combines: size-tiered, with the
 * segments that no longer match the index rewritten first.
 * <p>
 * Every flush adds a segment of about {@code flushThreshold} nodes, and every search visits every
 * segment, so they have to be combined, but rewriting a large segment for every small one that
 * arrives would rewrite each vector once per flush. Size tiers bound that: tier {@code t} holds the
 * segments with fewer than {@code base * factor^(t+1)} live nodes, where {@code base} is the flush
 * threshold, and a tier that has collected {@code factor} segments is merged into one, which lands
 * about one tier higher. Each vector is rewritten about once per tier, so {@code log_factor(n / base)}
 * times over the life of an index of {@code n} vectors, and fewer than {@code factor} segments stay
 * in each tier.
 * <p>
 * The order of the checks:
 * <ol>
 *   <li>Segments that need a rewrite (written for another format or codebook, so searched exactly
 *       and contributing nothing to quantized scoring) are merged first, all of them at once,
 *       whatever their size: a single one is rewritten on its own.</li>
 *   <li>The lowest tier holding {@code factor} segments: its {@code factor} smallest.</li>
 *   <li>If the next flush would reach {@code maxSegments}, the {@code factor} smallest segments
 *       whatever their tiers, so the persist does not have to fall back to a consolidation.</li>
 * </ol>
 * A segment with nothing live is left out: the next flush drops it from the manifest anyway.
 * <p>
 * Stateless and thread-safe.
 */
interface SegmentMergePolicy
{
    /**
     * Selects the segments to merge next.
     *
     * @param candidates   the segments of the index, in any order
     * @param otherSources the sources besides the segments that the next flush turns into segments:
     *                     the frozen in-memory graphs and the live one
     * @return the sequences of the segments to merge, or an empty list if no merge is due
     */
    public List<Long> select(
        List<Candidate> candidates  ,
        int             otherSources
    );


    /**
     * Creates the policy for the given settings.
     *
     * @param flushThreshold the flush threshold, the size of tier {@code 0}; positive
     * @param mergeFactor    how many segments a tier collects before they are merged; capped at
     *                       {@code maxSegments}
     * @param maxSegments    the segment cap of the index, at least {@code 2}
     * @return the policy
     */
    public static SegmentMergePolicy New(
        final int flushThreshold,
        final int mergeFactor   ,
        final int maxSegments
    )
    {
        return new Default(flushThreshold, mergeFactor, maxSegments);
    }


    /**
     * What the policy knows about one segment.
     *
     * @param sequence      the segment's sequence
     * @param liveCount     the number of its nodes whose entity it still owns
     * @param needsRewrite  whether it no longer matches the index's format or codebook
     */
    public record Candidate(
        long    sequence    ,
        long    liveCount   ,
        boolean needsRewrite
    )
    {
    }


    /**
     * Default implementation.
     */
    public static final class Default implements SegmentMergePolicy
    {
        private static final Comparator<Candidate> SMALLEST_FIRST = Comparator
            .comparingLong(Candidate::liveCount)
            .thenComparingLong(Candidate::sequence);

        private final long base       ;
        private final int  factor     ;
        private final int  maxSegments;

        Default(
            final int flushThreshold,
            final int mergeFactor   ,
            final int maxSegments
        )
        {
            if(flushThreshold <= 0)
            {
                throw new IllegalArgumentException("flushThreshold must be positive: " + flushThreshold);
            }
            if(maxSegments < 2 || mergeFactor < 2)
            {
                throw new IllegalArgumentException(
                    "mergeFactor and maxSegments must be at least 2: " + mergeFactor + ", " + maxSegments);
            }
            this.base        = flushThreshold;
            this.factor      = Math.min(mergeFactor, maxSegments);
            this.maxSegments = maxSegments;
        }

        @Override
        public List<Long> select(
            final List<Candidate> candidates  ,
            final int             otherSources
        )
        {
            final List<Candidate> live = candidates.stream()
                .filter(candidate -> candidate.liveCount() > 0)
                .sorted(SMALLEST_FIRST)
                .toList();

            final List<Long> rewrites = live.stream()
                .filter(Candidate::needsRewrite)
                .map(Candidate::sequence)
                .toList();
            if(!rewrites.isEmpty())
            {
                return rewrites;
            }

            final List<List<Candidate>> tiers = new ArrayList<>();
            for(final Candidate candidate : live)
            {
                final int tier = this.tierOf(candidate.liveCount());
                while(tiers.size() <= tier)
                {
                    tiers.add(new ArrayList<>());
                }
                tiers.get(tier).add(candidate);
            }
            for(final List<Candidate> tier : tiers)
            {
                if(tier.size() >= this.factor)
                {
                    // Smallest first already, as the whole list is.
                    return sequencesOf(tier.subList(0, this.factor));
                }
            }

            // Every tier is below the factor, but the next flush would reach the cap: merge the
            // smallest segments early rather than let the persist consolidate everything.
            if(live.size() >= 2 && candidates.size() + otherSources >= this.maxSegments)
            {
                return sequencesOf(live.subList(0, Math.min(this.factor, live.size())));
            }
            return List.of();
        }

        /**
         * The tier of a segment with {@code liveCount} live nodes: the smallest {@code t} with
         * {@code liveCount < base * factor^(t+1)}. Computed by repeated multiplication, stopping
         * before the bound could overflow.
         */
        int tierOf(final long liveCount)
        {
            int  tier  = 0;
            long bound = this.base * this.factor;
            while(liveCount >= bound && bound <= Long.MAX_VALUE / this.factor)
            {
                bound *= this.factor;
                tier++;
            }
            return tier;
        }

        private static List<Long> sequencesOf(final List<Candidate> candidates)
        {
            return candidates.stream()
                .map(Candidate::sequence)
                .toList();
        }

    }

}
