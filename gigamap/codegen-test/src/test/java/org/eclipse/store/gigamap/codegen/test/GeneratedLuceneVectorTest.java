package org.eclipse.store.gigamap.codegen.test;

/*-
 * #%L
 * EclipseStore GigaMap Codegen
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

import org.eclipse.store.gigamap.jvector.VectorAnnotationHandler;
import org.eclipse.store.gigamap.jvector.VectorIndices;
import org.eclipse.store.gigamap.lucene.LuceneAnnotationHandler;
import org.eclipse.store.gigamap.lucene.LuceneIndex;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that the generated metamodels wire up Lucene full-text ({@code @FullText}) and JVector
 * ({@code @Vector}) indices: their {@code registerIndices} create queryable indices, survive reload,
 * and match the runtime annotation handlers.
 */
public class GeneratedLuceneVectorTest
{
	@TempDir
	Path tempDir;

	@Test
	void luceneFullTextViaGeneratedMetamodel()
	{
		final GigaMap<Doc> map = GigaMap.New();
		Doc_.registerIndices(map);
		map.add(new Doc("hello world", "news"));
		map.add(new Doc("another text", "blog"));

		final LuceneIndex<Doc> index = map.index().get(LuceneIndex.class);
		final List<Doc> hits = index.query("title:hello");
		assertEquals(1, hits.size());
		assertEquals("hello world", hits.get(0).getTitle());
	}

	@Test
	void vectorSearchViaGeneratedMetamodel()
	{
		final GigaMap<VectorItem> map = GigaMap.New();
		VectorItem_.registerIndices(map);
		final long idA = map.add(new VectorItem(new float[]{1, 0, 0, 0}));
		map.add(new VectorItem(new float[]{0, 1, 0, 0}));

		assertEquals(idA, topId(map, new float[]{1, 0, 0, 0}));
	}

	@Test
	void mixedMetamodelWiresBitmapFullTextAndVector()
	{
		final GigaMap<MixedDoc> map = GigaMap.New();
		MixedDoc_.registerIndices(map);
		map.add(new MixedDoc("A", "eclipse store gigamap", new float[]{1, 0, 0}));
		map.add(new MixedDoc("B", "something else",        new float[]{0, 1, 0}));

		assertEquals(1, map.query(MixedDoc_.category.is("A")).toList().size());
		assertEquals(1, map.index().get(LuceneIndex.class).query("body:eclipse").size());
		assertEquals(1, map.index().get(VectorIndices.class).get("embedding")
			.search(new float[]{1, 0, 0}, 1).toList().size());
	}

	@Test
	void onDiskVectorIsNotWired()
	{
		final GigaMap<OnDiskItem> map = GigaMap.New();
		OnDiskItem_.registerIndices(map); // generated, but emits no vector registration
		map.add(new OnDiskItem(new float[]{1, 0}));

		assertNull(map.index().get(VectorIndices.class));
	}

	@Test
	void luceneReloadSafe()
	{
		final GigaMap<Doc> map = GigaMap.New();
		Doc_.registerIndices(map);
		map.add(new Doc("hello world", "news"));

		try(final EmbeddedStorageManager sm = EmbeddedStorage.start(map, this.tempDir))
		{
			// store
		}
		try(final EmbeddedStorageManager sm = EmbeddedStorage.start(this.tempDir))
		{
			final GigaMap<Doc> reloaded = sm.root();
			Doc_.registerIndices(reloaded); // idempotent on the reloaded, already-indexed map
			assertEquals(1, reloaded.index().get(LuceneIndex.class).query("title:hello").size());
		}
	}

	@Test
	void vectorReloadSafe()
	{
		final GigaMap<VectorItem> map = GigaMap.New();
		VectorItem_.registerIndices(map);
		final long idA = map.add(new VectorItem(new float[]{1, 0, 0, 0}));

		try(final EmbeddedStorageManager sm = EmbeddedStorage.start(map, this.tempDir))
		{
			// store
		}
		try(final EmbeddedStorageManager sm = EmbeddedStorage.start(this.tempDir))
		{
			final GigaMap<VectorItem> reloaded = sm.root();
			VectorItem_.registerIndices(reloaded); // idempotent
			assertEquals(idA, topId(reloaded, new float[]{1, 0, 0, 0}));
		}
	}

	private static long topId(final GigaMap<VectorItem> map, final float[] query)
	{
		final VectorIndices<VectorItem> indices = map.index().get(VectorIndices.class);
		return indices.get("embedding").search(query, 1).stream().findFirst().orElseThrow().entityId();
	}

	@Test
	void matchesRuntimeHandlers()
	{
		// Lucene: generated registration vs runtime LuceneAnnotationHandler
		final GigaMap<Doc> runtimeLucene = GigaMap.New();
		IndexerGenerator.AnnotationBased(Doc.class)
			.register(LuceneAnnotationHandler.New())
			.generateIndices(runtimeLucene);
		final GigaMap<Doc> genLucene = GigaMap.New();
		Doc_.registerIndices(genLucene);
		for(final GigaMap<Doc> m : List.of(runtimeLucene, genLucene))
		{
			m.add(new Doc("hello world", "news"));
			m.add(new Doc("hello there", "blog"));
		}
		assertEquals(
			runtimeLucene.index().get(LuceneIndex.class).query("title:hello").size(),
			genLucene.index().get(LuceneIndex.class).query("title:hello").size()
		);

		// Vector: generated registration vs runtime VectorAnnotationHandler
		final GigaMap<VectorItem> runtimeVector = GigaMap.New();
		IndexerGenerator.AnnotationBased(VectorItem.class)
			.register(VectorAnnotationHandler.New())
			.generateIndices(runtimeVector);
		final GigaMap<VectorItem> genVector = GigaMap.New();
		VectorItem_.registerIndices(genVector);
		for(final GigaMap<VectorItem> m : List.of(runtimeVector, genVector))
		{
			m.add(new VectorItem(new float[]{1, 0, 0, 0}));
			m.add(new VectorItem(new float[]{0, 1, 0, 0}));
		}
		assertEquals(
			topId(runtimeVector, new float[]{1, 0, 0, 0}),
			topId(genVector, new float[]{1, 0, 0, 0})
		);
	}
}
