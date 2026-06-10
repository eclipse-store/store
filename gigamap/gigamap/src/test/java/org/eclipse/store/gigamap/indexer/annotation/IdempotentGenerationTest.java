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

import org.eclipse.store.gigamap.annotations.Identity;
import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.store.gigamap.annotations.Unique;
import org.eclipse.store.gigamap.exceptions.UniqueConstraintViolationExceptionBitmap;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Generating indices more than once on the same GigaMap must not fail on already-registered names.
 */
public class IdempotentGenerationTest
{
	static class Entity
	{
		@Index    String name;
		@Unique   String code;
		@Identity long   id;

		Entity(final String name, final String code, final long id)
		{
			this.name = name;
			this.code = code;
			this.id   = id;
		}
	}

	@TempDir
	Path tempDir;

	@Test
	void generateIndicesIsIdempotentAcrossRestart()
	{
		final GigaMap<Entity> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Entity.class).generateIndices(map);
		map.add(new Entity("n1", "C1", 1));

		try(final EmbeddedStorageManager sm = EmbeddedStorage.start(map, this.tempDir))
		{
		}

		try(final EmbeddedStorageManager sm = EmbeddedStorage.start(this.tempDir))
		{
			final GigaMap<Entity> reloaded = sm.root();

			// before the fix this throws: BitmapIndex already registered for name "code".
			IndexerGenerator.AnnotationBased(Entity.class).generateIndices(reloaded);

			assertEquals(1, reloaded.size());

			// unique constraint still enforced exactly once after re-generation
			assertThrows(
				UniqueConstraintViolationExceptionBitmap.class,
				() -> reloaded.add(new Entity("n2", "C1", 2))
			);
			assertEquals(1, reloaded.size());
		}
	}

	@Test
	void generatingTwiceDoesNotFailAndKeepsConstraints()
	{
		final GigaMap<Entity> map = GigaMap.New();

		IndexerGenerator.AnnotationBased(Entity.class).generateIndices(map);
		// second run must be a no-op, not throw "already registered" / "Double index name"
		IndexerGenerator.AnnotationBased(Entity.class).generateIndices(map);

		map.add(new Entity("n1", "C1", 1));

		final IndexerString<Entity> name = map.index().bitmap().getIndexerString("name");
		assertEquals(1, map.query(name.is("n1")).toList().size());

		// the unique constraint is still enforced after re-generation
		assertThrows(
			UniqueConstraintViolationExceptionBitmap.class,
			() -> map.add(new Entity("n2", "C1", 2))
		);
		assertEquals(1, map.size());
	}
}
