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

import org.eclipse.store.gigamap.exceptions.UniqueConstraintViolationException;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that {@code @Index(creator = ...)} indices are generated into the metamodel: a typed
 * {@code Indexer<E,K>} constant (member-aware and plain creators), unique-constraint wiring, parity
 * with the runtime generator, and the inaccessible-creator fallback.
 */
public class CreatorMetamodelTest
{
	private static GigaMap<CreatorBean> beans()
	{
		final GigaMap<CreatorBean> map = GigaMap.New();
		CreatorBean_.registerIndices(map);
		map.add(new CreatorBean("hello", "X1", 1L));
		map.add(new CreatorBean("world", "X2", 2L));
		return map;
	}

	@Test
	void memberAwareCreatorConstantQueries()
	{
		final GigaMap<CreatorBean> map = beans();

		// UpperCaseCreator indexes the upper-cased value
		assertEquals(1, map.query(CreatorBean_.label.is("HELLO")).toList().size());
		assertEquals(0, map.query(CreatorBean_.label.is("hello")).toList().size());
	}

	@Test
	void plainCreatorConstantQueries()
	{
		final GigaMap<CreatorBean> map = beans();
		assertEquals(1, map.query(CreatorBean_.code.is("X1")).toList().size());
	}

	@Test
	void uniqueCreatorIndexEnforcesConstraint()
	{
		final GigaMap<CreatorBean> map = beans();

		assertEquals(1, map.query(CreatorBean_.id.is(1L)).toList().size());
		assertThrows(
			UniqueConstraintViolationException.class,
			() -> map.add(new CreatorBean("other", "X9", 1L))
		);
	}

	@Test
	void matchesRuntimeGenerator()
	{
		final GigaMap<CreatorBean> runtime = GigaMap.New();
		IndexerGenerator.AnnotationBased(CreatorBean.class).generateIndices(runtime);
		final GigaMap<CreatorBean> generated = GigaMap.New();
		CreatorBean_.registerIndices(generated);
		for(final GigaMap<CreatorBean> m : java.util.List.of(runtime, generated))
		{
			m.add(new CreatorBean("hello", "X1", 1L));
			m.add(new CreatorBean("world", "X2", 2L));
		}

		assertEquals(
			runtime.query(runtime.index().bitmap().getIndexerString("label").is("HELLO")).toList().size(),
			generated.query(CreatorBean_.label.is("HELLO")).toList().size()
		);
		assertEquals(
			runtime.query(runtime.index().bitmap().getIndexerString("code").is("X2")).toList().size(),
			generated.query(CreatorBean_.code.is("X2")).toList().size()
		);
	}

	@Test
	void inaccessibleCreatorFallsBackToRuntime()
	{
		// PrivateCreatorBean's creator is a private nested class: not wired at compile time.
		final GigaMap<PrivateCreatorBean> map = GigaMap.New();
		PrivateCreatorBean_.registerIndices(map); // generated, but empty for the skipped creator index
		map.add(new PrivateCreatorBean("hidden"));

		assertNull(map.index().bitmap().getIndexerString("secret"));
	}
}
