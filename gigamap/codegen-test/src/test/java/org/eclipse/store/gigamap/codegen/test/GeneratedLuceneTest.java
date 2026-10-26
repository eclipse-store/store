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


/**
 * Verifies that the generated metamodels wire up Lucene full-text ({@code @FullText}) indices: their
 * {@code registerIndices} create queryable indices, survive reload, and match the runtime annotation handler.
 * The {@code @Vector} counterpart lives in gigamap-codegen-jvector-test (Java 25).
 */
public class GeneratedLuceneTest
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
	void matchesRuntimeHandler()
	{
		// generated registration vs runtime LuceneAnnotationHandler
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
	}
}
