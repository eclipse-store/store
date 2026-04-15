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

import org.eclipse.store.gigamap.types.EntityIdMatcher;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.GigaQuery;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.gigamap.types.ScoredSearchResult;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link VectorSearchResult} can be used as a {@link GigaMap.SubQuery}
 * and composed with a bitmap-backed {@link GigaQuery}.
 */
class VectorSearchSubQueryTest
{
	static final class Doc
	{
		final String   category;
		final float[]  vector  ;

		Doc(final String category, final float[] vector)
		{
			this.category = category;
			this.vector   = vector  ;
		}
	}

	static class CategoryIndexer extends IndexerString.Abstract<Doc>
	{
		@Override
		protected String getString(final Doc entity)
		{
			return entity.category;
		}
	}

	static class DocVectorizer extends Vectorizer<Doc>
	{
		@Override
		public float[] vectorize(final Doc entity)
		{
			return entity.vector;
		}

		@Override
		public boolean isEmbedded()
		{
			return true;
		}
	}


	private static VectorIndex<Doc> setupIndex(final GigaMap<Doc> map, final CategoryIndexer categoryIndexer)
	{
		map.index().bitmap().add(categoryIndexer);
		final VectorIndices<Doc> vectorIndices = map.index().register(VectorIndices.Category());
		return vectorIndices.add(
			"vec",
			VectorIndexConfiguration.builder()
				.dimension(3)
				.similarityFunction(VectorSimilarityFunction.COSINE)
				.build(),
			new DocVectorizer()
		);
	}


	@Test
	void subQueryIntersectsWithBitmapQuery()
	{
		final GigaMap<Doc>     map             = GigaMap.New();
		final CategoryIndexer  categoryIndexer = new CategoryIndexer();
		final VectorIndex<Doc> vectorIndex     = setupIndex(map, categoryIndexer);

		final long idA1 = map.add(new Doc("A", new float[]{1, 0, 0}));
		final long idA2 = map.add(new Doc("A", new float[]{0.9f, 0.1f, 0}));
		final long idB1 = map.add(new Doc("B", new float[]{0.8f, 0.2f, 0}));
		map.add(new Doc("B", new float[]{0, 1, 0}));
		map.add(new Doc("A", new float[]{0, 0, 1}));

		// Top-3 closest to [1,0,0] across all categories: idA1, idA2, idB1
		final VectorSearchResult<Doc> hits = vectorIndex.search(new float[]{1, 0, 0}, 3);
		assertEquals(3, hits.size());

		// Intersect with bitmap query for category A. Expect only idA1 and idA2.
		final Set<Long> combinedIds = new HashSet<>();
		map.query(categoryIndexer).is("A").and(hits).iterateIndexed((id, doc) -> combinedIds.add(id));

		assertEquals(Set.of(idA1, idA2), combinedIds);
		assertFalse(combinedIds.contains(idB1));
	}

	@Test
	void emptySubQueryYieldsEmptyResult()
	{
		final GigaMap<Doc>    map             = GigaMap.New();
		final CategoryIndexer categoryIndexer = new CategoryIndexer();
		map.index().bitmap().add(categoryIndexer);

		map.add(new Doc("A", new float[]{1, 0, 0}));
		map.add(new Doc("A", new float[]{0, 1, 0}));

		// Combining an empty SubQuery with any bitmap query must yield an empty result.
		final long count = map.query(categoryIndexer).is("A").and(EntityIdMatcher.Empty()).count();
		assertEquals(0, count);
	}

	@Test
	void scoredIterationRemainsAvailableAfterSubQueryUse()
	{
		final GigaMap<Doc>     map             = GigaMap.New();
		final CategoryIndexer  categoryIndexer = new CategoryIndexer();
		final VectorIndex<Doc> vectorIndex     = setupIndex(map, categoryIndexer);

		map.add(new Doc("A", new float[]{1, 0, 0}));
		map.add(new Doc("A", new float[]{0.5f, 0.5f, 0}));
		map.add(new Doc("B", new float[]{0, 1, 0}));

		final VectorSearchResult<Doc> hits = vectorIndex.search(new float[]{1, 0, 0}, 3);

		// Consume as SubQuery first - this materializes the internal id matcher.
		map.query(categoryIndexer).is("A").and(hits).count();

		// Iterating the result afterwards must still return score-ordered entries.
		final List<ScoredSearchResult.Entry<Doc>> entries = hits.toList();
		assertEquals(3, entries.size());
		assertTrue(entries.get(0).score() >= entries.get(1).score());
		assertTrue(entries.get(1).score() >= entries.get(2).score());
	}

	@Test
	void andIntersectsPreservingScoreOrder()
	{
		final GigaMap<Doc>     map             = GigaMap.New();
		final CategoryIndexer  categoryIndexer = new CategoryIndexer();
		final VectorIndex<Doc> vectorIndex     = setupIndex(map, categoryIndexer);

		final long idA1 = map.add(new Doc("A", new float[]{1, 0, 0}));         // closest
		final long idA2 = map.add(new Doc("A", new float[]{0.9f, 0.1f, 0}));   // 2nd closest
		map.add(new Doc("B", new float[]{0.8f, 0.2f, 0}));                     // 3rd closest but wrong category
		map.add(new Doc("A", new float[]{0, 0, 1}));                           // far

		final VectorSearchResult<Doc> hits = vectorIndex.search(new float[]{1, 0, 0}, 3);
		assertEquals(3, hits.size());

		// Narrow the hits to category A from the scored side.
		final ScoredSearchResult<Doc> narrowed = hits.and(map.query(categoryIndexer).is("A"));

		// Exactly the two A-entries, and score order preserved (idA1 before idA2).
		final List<ScoredSearchResult.Entry<Doc>> entries = narrowed.toList();
		assertEquals(2, entries.size());
		assertEquals(idA1, entries.get(0).entityId());
		assertEquals(idA2, entries.get(1).entityId());
		assertTrue(entries.get(0).score() >= entries.get(1).score());
	}

	@Test
	void andReturnsScoredResultThatIsStillComposable()
	{
		final GigaMap<Doc>     map             = GigaMap.New();
		final CategoryIndexer  categoryIndexer = new CategoryIndexer();
		final VectorIndex<Doc> vectorIndex     = setupIndex(map, categoryIndexer);

		map.add(new Doc("A", new float[]{1, 0, 0}));
		map.add(new Doc("A", new float[]{0.9f, 0.1f, 0}));
		map.add(new Doc("B", new float[]{0.8f, 0.2f, 0}));

		final VectorSearchResult<Doc> hits = vectorIndex.search(new float[]{1, 0, 0}, 3);

		// The narrowed result is itself a SubQuery — chainable into a GigaQuery.
		final ScoredSearchResult<Doc> narrowed = hits.and(map.query(categoryIndexer).is("A"));
		final long count = map.query(categoryIndexer).is("A").and(narrowed).count();
		assertEquals(2, count);
	}

	@Test
	void andCanBeReusedAcrossIndependentIntersections()
	{
		final GigaMap<Doc>     map             = GigaMap.New();
		final CategoryIndexer  categoryIndexer = new CategoryIndexer();
		final VectorIndex<Doc> vectorIndex     = setupIndex(map, categoryIndexer);

		final long idA = map.add(new Doc("A", new float[]{1, 0, 0}));
		final long idB = map.add(new Doc("B", new float[]{0.9f, 0.1f, 0}));
		map.add(new Doc("C", new float[]{0.8f, 0.2f, 0}));

		final VectorSearchResult<Doc> hits = vectorIndex.search(new float[]{1, 0, 0}, 3);

		// Two independent narrowings of the same hits — the second must not see a stale matcher cursor.
		final ScoredSearchResult<Doc> onlyA = hits.and(map.query(categoryIndexer).is("A"));
		final ScoredSearchResult<Doc> onlyB = hits.and(map.query(categoryIndexer).is("B"));

		assertEquals(1, onlyA.size());
		assertEquals(idA, onlyA.iterator().next().entityId());

		assertEquals(1, onlyB.size());
		assertEquals(idB, onlyB.iterator().next().entityId());
	}

	@Test
	void andWithEmptySubQueryReturnsEmpty()
	{
		final GigaMap<Doc>     map             = GigaMap.New();
		final CategoryIndexer  categoryIndexer = new CategoryIndexer();
		final VectorIndex<Doc> vectorIndex     = setupIndex(map, categoryIndexer);

		map.add(new Doc("A", new float[]{1, 0, 0}));
		map.add(new Doc("A", new float[]{0, 1, 0}));

		final VectorSearchResult<Doc> hits = vectorIndex.search(new float[]{1, 0, 0}, 2);
		assertEquals(2, hits.size());

		// No entity in category Z — the intersection is empty.
		final ScoredSearchResult<Doc> narrowed = hits.and(map.query(categoryIndexer).is("Z"));
		assertTrue(narrowed.isEmpty());
		assertEquals(0, narrowed.size());
	}

}
