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

import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link GigaMap#addAll(Iterable)} used to hand the caller's {@link Iterable} to the index fan-out, which
 * traverses it once per index. An iterable that cannot be traversed repeatedly therefore left the entities
 * unindexed, or indexed at ids holding no entity.
 * <p>
 * Pins that a single-use iterable is fully vector-indexed <b>and</b> that the batch still reaches the index as
 * one batch, i.e. {@link Vectorizer#vectorizeAll(List)} is called once instead of {@code vectorize} per entity.
 * The batch hand-off is what rules out fixing the traversal defect by indexing entity by entity.
 */
class VectorAddAllSingleUseIterableTest
{
	static final class Document
	{
		private final String  content;
		private final float[] embedding;

		Document(final String content, final float[] embedding)
		{
			super();
			this.content   = content;
			this.embedding = embedding;
		}

		String content()
		{
			return this.content;
		}

		float[] embedding()
		{
			return this.embedding;
		}
	}

	/**
	 * A vectorizer tracking how often the batch entry point was used.
	 */
	static final class BatchTrackingVectorizer extends Vectorizer<Document>
	{
		int vectorizeAllCallCount;

		@Override
		public float[] vectorize(final Document entity)
		{
			return entity.embedding();
		}

		@Override
		public List<float[]> vectorizeAll(final List<? extends Document> entities)
		{
			this.vectorizeAllCallCount++;

			return super.vectorizeAll(entities);
		}
	}

	/**
	 * An {@link Iterable} that hands out the <b>same</b> iterator every time, i.e. it is exhausted after the
	 * first traversal.
	 */
	static <T> Iterable<T> sameIteratorEachTime(final List<T> elements)
	{
		final Iterator<T> shared = elements.iterator();

		return () -> shared;
	}

	@Test
	void addAll_singleUseIterable_indexesEveryEntityAsOneBatch()
	{
		final GigaMap<Document>       gigaMap    = GigaMap.New();
		final BatchTrackingVectorizer vectorizer = new BatchTrackingVectorizer();

		final VectorIndices<Document>  vectorIndices = gigaMap.index().register(VectorIndices.Category());
		final VectorIndexConfiguration config        = VectorIndexConfiguration.builder()
			.dimension(3)
			.similarityFunction(VectorSimilarityFunction.COSINE)
			.build()
		;

		final VectorIndex<Document> index = vectorIndices.add("embeddings", config, vectorizer);

		final List<Document> docs = List.of(
			new Document("Doc A", new float[]{1.0f, 0.0f, 0.0f}),
			new Document("Doc B", new float[]{0.0f, 1.0f, 0.0f}),
			new Document("Doc C", new float[]{0.0f, 0.0f, 1.0f})
		);

		gigaMap.addAll(sameIteratorEachTime(docs));

		assertEquals(3, gigaMap.size(), "all entities of the batch must be added");
		assertEquals(1, vectorizer.vectorizeAllCallCount, "the batch must still be vectorized as one batch");

		// every entity must be searchable, i.e. the whole batch reached the graph
		for(final Document doc : docs)
		{
			final VectorSearchResult<Document> result = index.search(doc.embedding(), 1);

			assertEquals(1, result.size(), "entity \"" + doc.content() + "\" must be indexed");
			assertEquals(doc.content(), result.toList().get(0).entity().content());
		}
	}
}
