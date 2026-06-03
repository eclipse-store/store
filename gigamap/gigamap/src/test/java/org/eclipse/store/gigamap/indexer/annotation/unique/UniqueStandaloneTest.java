package org.eclipse.store.gigamap.indexer.annotation.unique;

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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code @Unique} must function without a companion {@code @Index}.
 */
public class UniqueStandaloneTest
{
	static class User
	{
		@Unique
		String email;

		User(final String email)
		{
			this.email = email;
		}
	}

	static class Account
	{
		private final String code;

		Account(final String code)
		{
			this.code = code;
		}

		@Unique
		public String getCode()
		{
			return this.code;
		}
	}

	static class Mixed
	{
		@Index          String name;
		@Index @Unique  String sku;
		@Unique         String email;
		@Identity       long   id;

		Mixed(final String name, final String sku, final String email, final long id)
		{
			this.name  = name;
			this.sku   = sku;
			this.email = email;
			this.id    = id;
		}
	}

	@Test
	void uniqueWithoutIndexEnforcesConstraint()
	{
		final GigaMap<User> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(User.class).generateIndices(map.index().bitmap());

		map.add(new User("a@example.com"));
		assertThrows(
			UniqueConstraintViolationExceptionBitmap.class,
			() -> map.add(new User("a@example.com"))
		);
		assertEquals(1, map.size());
	}

	@Test
	void uniqueOnlyOnGetterEnforcesConstraint()
	{
		final GigaMap<Account> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Account.class).generateIndices(map.index().bitmap());

		map.add(new Account("X1"));
		assertThrows(
			UniqueConstraintViolationExceptionBitmap.class,
			() -> map.add(new Account("X1"))
		);
		assertEquals(1, map.size());
	}

	@Test
	void mixedAnnotationsProduceExpectedIndices()
	{
		final GigaMap<Mixed> map = GigaMap.New();
		// must not throw a "double index name" error and must register every annotated property
		IndexerGenerator.AnnotationBased(Mixed.class).generateIndices(map.index().bitmap());

		map.add(new Mixed("n1", "S1", "e1@example.com", 1));

		final IndexerString<Mixed> name = map.index().bitmap().getIndexerString("name");
		assertEquals(1, map.query(name.is("n1")).toList().size());

		// @Index @Unique constraint
		assertThrows(
			UniqueConstraintViolationExceptionBitmap.class,
			() -> map.add(new Mixed("n2", "S1", "e2@example.com", 2))
		);
		// @Unique-only constraint
		assertThrows(
			UniqueConstraintViolationExceptionBitmap.class,
			() -> map.add(new Mixed("n3", "S3", "e1@example.com", 3))
		);
		assertEquals(1, map.size());
	}
}
