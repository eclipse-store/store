package org.eclipse.store.gigamap.indexer.annotation;

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

import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.store.gigamap.annotations.IndexKind;
import org.eclipse.store.gigamap.types.BinaryIndexer;
import org.eclipse.store.gigamap.types.ByteIndexerInteger;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IndexKindTest
{
	static class Scored
	{
		@Index(kind = IndexKind.BIT_SLICED) int  score;
		@Index(kind = IndexKind.BINARY)     long id;
		@Index(binary = true)               long legacyId;

		Scored(final int score, final long id, final long legacyId)
		{
			this.score    = score;
			this.id       = id;
			this.legacyId = legacyId;
		}
	}

	static class BadBitSliced
	{
		@Index(kind = IndexKind.BIT_SLICED) String text;
	}

	@SuppressWarnings("unchecked")
	@Test
	void bitSlicedNumericSupportsRangeQueries()
	{
		final GigaMap<Scored> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Scored.class).generateIndices(map.index().bitmap());

		map.add(new Scored(10, 1L, 100L));
		map.add(new Scored(60, 2L, 200L));
		map.add(new Scored(90, 3L, 300L));

		final ByteIndexerInteger<Scored> score = map.index().bitmap().getIndexer(ByteIndexerInteger.class, "score");
		assertInstanceOf(ByteIndexerInteger.class, score);

		final List<Scored> high = map.query(score.greaterThan(50)).toList();
		assertEquals(2, high.size());

		final List<Scored> between = map.query(score.between(20, 80)).toList();
		assertEquals(1, between.size());
		assertEquals(60, between.get(0).score);
	}

	@Test
	void binaryKindAndLegacyBinaryFlagBothProduceBinaryIndex()
	{
		final GigaMap<Scored> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Scored.class).generateIndices(map.index().bitmap());

		map.add(new Scored(10, 1L, 100L));
		map.add(new Scored(60, 2L, 200L));

		final Indexer<Scored, Long> id       = map.index().bitmap().getIndexerForKey(Long.class, "id");
		final Indexer<Scored, Long> legacyId = map.index().bitmap().getIndexerForKey(Long.class, "legacyId");
		assertInstanceOf(BinaryIndexer.class, id);
		assertInstanceOf(BinaryIndexer.class, legacyId);

		final List<Scored> found = map.query(id.is(2L)).toList();
		assertEquals(1, found.size());
		assertEquals(60, found.get(0).score);
	}

	@Test
	void bitSlicedOnUnsupportedTypeFailsFast()
	{
		final GigaMap<BadBitSliced> map = GigaMap.New();
		final IllegalStateException ex = assertThrows(
			IllegalStateException.class,
			() -> IndexerGenerator.AnnotationBased(BadBitSliced.class).generateIndices(map.index().bitmap())
		);
		assertEquals(true, ex.getMessage().contains("bit-sliced"));
	}
}
