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

import org.eclipse.store.gigamap.jvector.annotations.Vector;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VectorAnnotationTest
{
	static class Item
	{
		@Vector(dimension = 4, similarity = VectorSimilarityFunction.COSINE)
		float[] vector;

		Item(final float[] vector)
		{
			this.vector = vector;
		}
	}

	static class Bad
	{
		@Vector(dimension = 4)
		String notAVector;
	}

	static class Nullable
	{
		@Vector(dimension = 4, similarity = VectorSimilarityFunction.COSINE, allowNull = true)
		float[] vector;

		Nullable(final float[] vector)
		{
			this.vector = vector;
		}
	}

	static class Conflict
	{
		@Vector(dimension = 4)
		@org.eclipse.store.gigamap.annotations.Index
		float[] vector;

		Conflict(final float[] vector)
		{
			this.vector = vector;
		}
	}

	static class Base
	{
		@Vector(dimension = 4, similarity = VectorSimilarityFunction.COSINE)
		float[] vector;

		Base(final float[] vector)
		{
			this.vector = vector;
		}
	}

	static class Sub extends Base
	{
		Sub(final float[] vector)
		{
			super(vector);
		}
	}

	@Test
	void vectorAnnotationBuildsSearchableIndex()
	{
		final GigaMap<Item> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Item.class)
			.register(VectorAnnotationHandler.New())
			.generateIndices(map);

		final long idA = map.add(new Item(new float[]{1, 0, 0, 0}));
		map.add(new Item(new float[]{0, 1, 0, 0}));

		final VectorIndices<Item> vectorIndices = map.index().get(VectorIndices.class);
		final VectorIndex<Item>   index         = vectorIndices.get("vector");

		final var results = index.search(new float[]{1, 0, 0, 0}, 2);
		assertFalse(results.isEmpty(), "search should return results");

		final long top = results.stream().findFirst().orElseThrow().entityId();
		assertEquals(idA, top, "the closest vector's entity should rank first");
	}

	@Test
	void inheritedVectorMemberIsResolved()
	{
		final GigaMap<Sub> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Sub.class)
			.register(VectorAnnotationHandler.New())
			.generateIndices(map);

		final long idA = map.add(new Sub(new float[]{1, 0, 0, 0}));
		map.add(new Sub(new float[]{0, 1, 0, 0}));

		final VectorIndex<Sub> index = map.index().get(VectorIndices.class).get("vector");
		final var results = index.search(new float[]{1, 0, 0, 0}, 2);
		assertFalse(results.isEmpty());
		assertEquals(idA, results.stream().findFirst().orElseThrow().entityId());
	}

	@Test
	void worksWhenVectorIndicesAlreadyRegistered()
	{
		final GigaMap<Item> map = GigaMap.New();
		// pre-register the group so the handler's register(...) returns null and must fall back
		map.index().register(VectorIndices.Category());

		IndexerGenerator.AnnotationBased(Item.class)
			.register(VectorAnnotationHandler.New())
			.generateIndices(map);

		final long idA = map.add(new Item(new float[]{1, 0, 0, 0}));
		map.add(new Item(new float[]{0, 1, 0, 0}));

		final VectorIndex<Item> index = map.index().get(VectorIndices.class).get("vector");
		assertEquals(idA, index.search(new float[]{1, 0, 0, 0}, 2).stream().findFirst().orElseThrow().entityId());
	}

	@Test
	void generatingTwiceIsIdempotent()
	{
		final GigaMap<Item> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Item.class)
			.register(VectorAnnotationHandler.New())
			.generateIndices(map);
		// second run must not throw on the already-registered index
		IndexerGenerator.AnnotationBased(Item.class)
			.register(VectorAnnotationHandler.New())
			.generateIndices(map);

		final long idA = map.add(new Item(new float[]{1, 0, 0, 0}));
		map.add(new Item(new float[]{0, 1, 0, 0}));

		final VectorIndex<Item> index = map.index().get(VectorIndices.class).get("vector");
		assertEquals(idA, index.search(new float[]{1, 0, 0, 0}, 2).stream().findFirst().orElseThrow().entityId());
	}

	@Test
	void vectorCombinedWithIndexFailsFast()
	{
		final GigaMap<Conflict> map = GigaMap.New();
		assertThrows(
			IllegalStateException.class,
			() -> IndexerGenerator.AnnotationBased(Conflict.class)
				.register(VectorAnnotationHandler.New())
				.generateIndices(map)
		);
	}

	@Test
	void vectorAnnotationAllowNullExcludesEntitiesWithoutEmbedding()
	{
		final GigaMap<Nullable> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Nullable.class)
			.register(VectorAnnotationHandler.New())
			.generateIndices(map);

		final long idA = map.add(new Nullable(new float[]{1, 0, 0, 0}));
		final long idNull = map.add(new Nullable(null));
		map.add(new Nullable(new float[]{0, 1, 0, 0}));

		final VectorIndex<Nullable> index = map.index().get(VectorIndices.class).get("vector");
		assertEquals(2, index.search(new float[]{1, 0, 0, 0}, 10).size(),
			"the entity without an embedding must be excluded from search");
		assertEquals(idA, index.search(new float[]{1, 0, 0, 0}, 1).stream().findFirst().orElseThrow().entityId());
		assertNull(index.getVector(idNull));
	}

	@Test
	void vectorAnnotationDefaultRejectsNull()
	{
		final GigaMap<Item> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Item.class)
			.register(VectorAnnotationHandler.New())
			.generateIndices(map);

		// Item's @Vector does not set allowNull, so a null embedding must fail fast.
		assertThrows(IllegalStateException.class, () -> map.add(new Item(null)));
	}

	@Test
	void vectorOnNonFloatArrayFailsFast()
	{
		final GigaMap<Bad> map = GigaMap.New();
		final IllegalStateException ex = assertThrows(
			IllegalStateException.class,
			() -> IndexerGenerator.AnnotationBased(Bad.class)
				.register(VectorAnnotationHandler.New())
				.generateIndices(map)
		);
		assertFalse(ex.getMessage().isEmpty());
	}
}
