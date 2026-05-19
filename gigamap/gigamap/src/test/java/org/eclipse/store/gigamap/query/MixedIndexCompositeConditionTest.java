package org.eclipse.store.gigamap.query;

/*-
 * #%L
 * EclipseStore GigaMap
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

import org.eclipse.store.gigamap.types.BinaryIndexerLong;
import org.eclipse.store.gigamap.types.Condition;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.GigaQuery;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Composite {@code And}/{@code Or}/{@code Not} coverage for GigaMaps that mix
 * a hashing {@code BitmapIndex} (e.g. {@link IndexerString}) with a
 * {@code BinaryBitmapIndex} (e.g. {@link BinaryIndexerLong}) on the same map.
 *
 * <p>Complements
 * {@code org.eclipse.store.gigamap.indexer.binary.BinaryIndexerCompositeConditionTest}
 * (issue #641): the original bug was that a binary-index query with no match
 * produced an empty {@code ChainAnd} whose vacuous {@code -1L} bitmap leaked
 * through cross-index intersections. These tests lock down the mixed-family
 * case, which had no prior coverage.
 *
 * <h2>Score values are deliberately sparse</h2>
 *
 * Scores are powers of two (4, 8, 256), leaving large gaps in the binary
 * index's bit-entry array (bit positions 0, 1, 4-7 are never populated).
 * Queries for a non-existent key like {@code SCORE.is(1L)} therefore hit the
 * "required bit position has no entry" path in
 * {@code AbstractBitmapIndexBinary#internalQueryResults}, which is the path
 * that originally produced the buggy empty {@code ChainAnd}. Using dense bit
 * patterns (e.g. 10, 20, 999) would miss this path entirely.
 */
public class MixedIndexCompositeConditionTest
{
	record Item(String tag, long score)
	{
	}

	static final IndexerString<Item> TAG = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Item i)
		{
			return i.tag();
		}
	};

	static final BinaryIndexerLong<Item> SCORE = new BinaryIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(final Item i)
		{
			return i.score();
		}
	};

	/**
	 * A score value that cannot be contained: bit 0 is required, but no
	 * indexed entity ever sets bit 0 (all scores are even powers of two),
	 * so the binary index has no entry at bit position 0.
	 */
	private static final long SCORE_NONE = 1L;

	private GigaMap<Item> map;

	@BeforeEach
	void setUp()
	{
		this.map = GigaMap.<Item>Builder()
			.withBitmapIndex(TAG)
			.withBitmapIndex(SCORE)
			.build();

		this.map.add(new Item("A", 4L));    // id 0
		this.map.add(new Item("A", 8L));    // id 1
		this.map.add(new Item("B", 4L));    // id 2
		this.map.add(new Item("B", 8L));    // id 3
		this.map.add(new Item("C", 256L));  // id 4 — unique tag, unique high-bit score

		assertEquals(5, this.map.size(), "Setup: expected 5 entities");
	}

	private Set<Long> scoresOf(final Condition<Item> condition)
	{
		return this.map.query(condition).toList().stream()
			.map(Item::score)
			.collect(Collectors.toSet());
	}

	// ----- sanity: individual queries --------------------------------------

	@Test
	void singleHashingQueryMatches()
	{
		assertEquals(Set.of(4L, 8L), this.scoresOf(TAG.is("A")));
	}

	@Test
	void singleBinaryQueryMatches()
	{
		assertEquals(Set.of(4L), this.scoresOf(SCORE.is(4L)));
	}

	@Test
	void singleBinaryQueryForNonExistentIsEmpty()
	{
		assertTrue(this.scoresOf(SCORE.is(SCORE_NONE)).isEmpty());
	}

	// ----- 2-way AND across index families ---------------------------------

	@Test
	void hashingAndBinaryBothMatch()
	{
		assertEquals(Set.of(4L), this.scoresOf(TAG.is("A").and(SCORE.is(4L))));
	}

	/**
	 * Regression for issue #641, cross-family: the binary side has no match
	 * ({@code SCORE_NONE} requires a bit position with no entry) while the
	 * hashing side matches two entities. The AND must be empty.
	 */
	@Test
	void hashingMatchAndBinaryNoMatchIsEmpty()
	{
		assertTrue(this.scoresOf(TAG.is("A").and(SCORE.is(SCORE_NONE))).isEmpty());
	}

	@Test
	void hashingNoMatchAndBinaryMatchIsEmpty()
	{
		assertTrue(this.scoresOf(TAG.is("Z").and(SCORE.is(4L))).isEmpty());
	}

	@Test
	void bothNoMatchIsEmpty()
	{
		assertTrue(this.scoresOf(TAG.is("Z").and(SCORE.is(SCORE_NONE))).isEmpty());
	}

	/**
	 * Swapping hashing and binary operand positions must not change AND
	 * semantics.
	 */
	@Test
	void reversedChainOrderSameResult()
	{
		assertEquals(
			this.scoresOf(TAG.is("A").and(SCORE.is(4L))),
			this.scoresOf(SCORE.is(4L).and(TAG.is("A")))
		);
	}

	/**
	 * Same as above, but for the empty-binary case.
	 */
	@Test
	void reversedChainOrderSameResultWithBinaryNoMatch()
	{
		assertEquals(
			this.scoresOf(TAG.is("A").and(SCORE.is(SCORE_NONE))),
			this.scoresOf(SCORE.is(SCORE_NONE).and(TAG.is("A")))
		);
	}

	// ----- 3-way AND alternating index families ----------------------------

	@Test
	void threeWayAlternatingChainHashingBinaryHashing()
	{
		// tag=A AND score=8 AND tag=A  →  id 1
		assertEquals(
			Set.of(8L),
			this.scoresOf(TAG.is("A").and(SCORE.is(8L)).and(TAG.is("A")))
		);
	}

	@Test
	void threeWayAlternatingChainBinaryHashingBinary()
	{
		// score=4 AND tag=B AND score=4  →  id 2
		assertEquals(
			Set.of(4L),
			this.scoresOf(SCORE.is(4L).and(TAG.is("B")).and(SCORE.is(4L)))
		);
	}

	@Test
	void threeWayAlternatingChainWithContradiction()
	{
		// tag=A AND score=8 AND tag=B  →  empty (A and B are mutually exclusive)
		assertTrue(
			this.scoresOf(TAG.is("A").and(SCORE.is(8L)).and(TAG.is("B"))).isEmpty()
		);
	}

	@Test
	void threeWayNonMatchInTheMiddle()
	{
		// tag=A AND score=1 (no match) AND tag=B  →  empty; the binary no-match
		// must short-circuit the intersection even sandwiched between hashing
		// conditions. Before the #641 fix this returned entities that matched
		// only the outer tag conditions.
		assertTrue(
			this.scoresOf(TAG.is("A").and(SCORE.is(SCORE_NONE)).and(TAG.is("B"))).isEmpty()
		);
	}

	// ----- nested composites (the shape that historically leaked in #641) --

	/**
	 * Non-flattened nesting: the empty binary {@code ChainAnd} sits
	 * <b>inside</b> another {@code ChainAnd}, alongside a non-empty sibling.
	 * This is the exact shape that leaked in issue #641 (before the fix, the
	 * sibling's bitmap won the AND because the empty {@code ChainAnd}
	 * contributed {@code -1L}).
	 */
	@Test
	void nestedAndWithBinaryNoMatchIsEmpty()
	{
		final Condition<Item> condition =
			TAG.is("A").and(TAG.is("A").and(SCORE.is(SCORE_NONE)));

		assertTrue(this.scoresOf(condition).isEmpty());
	}

	/**
	 * Same nesting shape, but with the outer condition being binary and
	 * the empty binary sitting deeper inside.
	 */
	@Test
	void nestedAndWithBinaryNoMatchReversedIsEmpty()
	{
		final Condition<Item> condition =
			SCORE.is(4L).and(TAG.is("A").and(SCORE.is(SCORE_NONE)));

		assertTrue(this.scoresOf(condition).isEmpty());
	}

	// ----- OR across / wrapping mixed ANDs ---------------------------------

	@Test
	void orAcrossIndexTypes()
	{
		// tag=C OR score=4  →  ids 0, 2 (score=4) and 4 (tag=C)
		assertEquals(
			Set.of(4L, 256L),
			this.scoresOf(TAG.is("C").or(SCORE.is(4L)))
		);
	}

	@Test
	void orWrappingAndAcrossIndexTypes()
	{
		// tag=Z  OR  (tag=A AND score=8)  →  id 1
		final Condition<Item> idMatch    = TAG.is("Z");
		final Condition<Item> tupleMatch = TAG.is("A").and(SCORE.is(8L));
		assertEquals(Set.of(8L), this.scoresOf(idMatch.or(tupleMatch)));
	}

	/**
	 * The "Or wrapping And" variant of issue #641, cross-family: neither
	 * branch can match, so the overall result must be empty. Before the fix
	 * the empty binary {@code ChainAnd} made the {@code And} branch vacuously
	 * match every id, which flowed through the {@code Or} and returned the
	 * whole map.
	 */
	@Test
	void orWrappingAndWhereBothSidesAreEmpty()
	{
		final Condition<Item> lhs = SCORE.is(SCORE_NONE);
		final Condition<Item> rhs = TAG.is("Z").and(SCORE.is(4L));
		assertTrue(this.scoresOf(lhs.or(rhs)).isEmpty());
	}

	/**
	 * OR branch whose AND includes an empty binary condition &mdash; the AND
	 * side must contribute nothing, leaving only the OR's hashing branch.
	 */
	@Test
	void orBranchWithBinaryNoMatchDoesNotLeakIntoResult()
	{
		// tag=C  OR  (tag=A AND score=1)   →  just tag=C (id 4)
		final Condition<Item> lhs = TAG.is("C");
		final Condition<Item> rhs = TAG.is("A").and(SCORE.is(SCORE_NONE));
		assertEquals(Set.of(256L), this.scoresOf(lhs.or(rhs)));
	}

	// ----- NOT across mixed composites -------------------------------------

	@Test
	void notOnBinarySideOfMixedAnd()
	{
		// tag=A AND score != 4  →  id 1 (tag=A, score=8)
		assertEquals(Set.of(8L), this.scoresOf(TAG.is("A").and(SCORE.not(4L))));
	}

	@Test
	void notOnHashingSideOfMixedAnd()
	{
		// tag != A AND score=4  →  id 2 (tag=B, score=4)
		assertEquals(Set.of(4L), this.scoresOf(TAG.not("A").and(SCORE.is(4L))));
	}

	// ----- composed vs. sequential ----------------------------------------

	/**
	 * Sequential {@code GigaQuery.and(Condition)} and composed
	 * {@code Condition.and(Condition)} must produce identical results,
	 * including when one condition resolves to "no match". This is the
	 * workaround path documented in the #641 reproducer.
	 */
	@Test
	void sequentialAndMatchesComposed()
	{
		final List<Item> composed   = this.map.query(TAG.is("A").and(SCORE.is(SCORE_NONE))).toList();
		final List<Item> sequential = this.map.query()
			.and(TAG.is("A"))
			.and(SCORE.is(SCORE_NONE))
			.toList();

		assertEquals(composed, sequential);
		assertTrue(sequential.isEmpty());
	}

	// ----- sub-query composition across mixed index types ------------------

	/**
	 * Closes the sub-query coverage gap &mdash; {@link SubQueryCompositionTest}
	 * only exercises hashing indexes.
	 */
	@Nested
	class SubQueryComposition
	{
		@Test
		void primaryHashingWithBinarySubQuery()
		{
			// tag=A AND (sub-query: score=8)  →  id 1
			final GigaQuery<Item> sub =
				MixedIndexCompositeConditionTest.this.map.query(SCORE.is(8L));

			final Set<Long> result =
				MixedIndexCompositeConditionTest.this.map.query(TAG).is("A")
					.and(sub)
					.toList().stream().map(Item::score).collect(Collectors.toSet());

			assertEquals(Set.of(8L), result);
		}

		@Test
		void primaryBinaryWithHashingSubQuery()
		{
			// score=4 AND (sub-query: tag=A)  →  id 0
			final GigaQuery<Item> sub =
				MixedIndexCompositeConditionTest.this.map.query(TAG.is("A"));

			final Set<Long> result =
				MixedIndexCompositeConditionTest.this.map.query(SCORE).is(4L)
					.and(sub)
					.toList().stream().map(Item::score).collect(Collectors.toSet());

			assertEquals(Set.of(4L), result);
		}

		@Test
		void binarySubQueryRejectingEverythingProducesEmpty()
		{
			// Binary sub-query has no match, so the AND must be empty even
			// though the hashing primary matches two entities.
			final GigaQuery<Item> rejecting =
				MixedIndexCompositeConditionTest.this.map.query(SCORE.is(SCORE_NONE));

			final long count =
				MixedIndexCompositeConditionTest.this.map.query(TAG).is("A")
					.and(rejecting)
					.count();

			assertEquals(0L, count);
		}

		@Test
		void hashingSubQueryRejectingEverythingProducesEmpty()
		{
			// Symmetric: binary primary, hashing sub-query rejects everything.
			final GigaQuery<Item> rejecting =
				MixedIndexCompositeConditionTest.this.map.query(TAG.is("Z"));

			final long count =
				MixedIndexCompositeConditionTest.this.map.query(SCORE).is(4L)
					.and(rejecting)
					.count();

			assertEquals(0L, count);
		}
	}
}
