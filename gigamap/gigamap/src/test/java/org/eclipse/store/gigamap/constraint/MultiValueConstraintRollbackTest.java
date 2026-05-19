package org.eclipse.store.gigamap.constraint;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.exceptions.ConstraintViolationException;
import org.eclipse.store.gigamap.types.CustomConstraint;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerMultiValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that multi-value index keys are correctly cleaned up when an update()
 * triggers a constraint violation part-way through.
 */
public class MultiValueConstraintRollbackTest
{
	static final class Tag
	{
		final String value;

		Tag(final String value)
		{
			this.value = value;
		}

		@Override
		public String toString()
		{
			return this.value;
		}
	}

	static final class Article
	{
		String title;
		List<Tag> tags;

		Article(final String title, final List<Tag> tags)
		{
			this.title = title;
			this.tags = tags;
		}
	}

	static final IndexerMultiValue.Abstract<Article, Tag> TAG_INDEX =
		new IndexerMultiValue.Abstract<Article, Tag>()
		{
			@Override
			public Iterable<? extends Tag> indexEntityMultiValue(final Article entity)
			{
				return entity.tags;
			}

			@Override
			public Class<Tag> keyType()
			{
				return Tag.class;
			}
		};

	static final Tag T_ALPHA = new Tag("alpha");
	static final Tag T_BETA  = new Tag("beta");
	static final Tag T_NEW1  = new Tag("new1");
	static final Tag T_NEW2  = new Tag("new2");

	private static GigaMap<Article> createMap()
	{
		final GigaMap<Article> map = GigaMap.New();
		map.index().bitmap().add(TAG_INDEX);
		map.constraints().custom().addConstraint(
			new CustomConstraint.AbstractSimple<Article>()
			{
				@Override
				public boolean isViolated(final Article entity)
				{
					return entity.title.startsWith("BANNED");
				}
			}
		);
		return map;
	}

	@Test
	void failedUpdate_entityRemovedAndOldKeysCleanedUp()
	{
		final GigaMap<Article> map = createMap();

		final Article article1 = new Article("good-article", List.of(T_ALPHA, T_BETA));
		final Article article2 = new Article("other-article", List.of(T_ALPHA));
		map.addAll(article1, article2);

		assertEquals(2, map.query(TAG_INDEX.is(T_ALPHA)).count());
		assertEquals(1, map.query(TAG_INDEX.is(T_BETA)).count());

		assertThrows(
			ConstraintViolationException.class,
			() -> map.update(article1, a -> {
				a.title = "BANNED-title";
				a.tags = List.of(T_NEW1, T_NEW2);
			})
		);

		assertEquals(1, map.size());

		// Old keys for article1 must be gone from the index
		assertEquals(1, map.query(TAG_INDEX.is(T_ALPHA)).count(), "article2 still has alpha");
		assertEquals(0, map.query(TAG_INDEX.is(T_BETA)).count(), "beta must be cleaned up");

		// New keys must never have been added
		assertEquals(0, map.query(TAG_INDEX.is(T_NEW1)).count());
		assertEquals(0, map.query(TAG_INDEX.is(T_NEW2)).count());
	}

	@Test
	void failedUpdate_otherEntityKeysUnaffected()
	{
		final GigaMap<Article> map = createMap();

		final Tag sharedTag = new Tag("shared");
		final Article article1 = new Article("a1", List.of(sharedTag, T_BETA));
		final Article article2 = new Article("a2", List.of(sharedTag));
		map.addAll(article1, article2);

		assertEquals(2, map.query(TAG_INDEX.is(sharedTag)).count());

		assertThrows(
			ConstraintViolationException.class,
			() -> map.update(article1, a -> {
				a.title = "BANNED-a1";
				a.tags = List.of(T_NEW1);
			})
		);

		// article2 must still be queryable via the shared tag
		assertEquals(1, map.query(TAG_INDEX.is(sharedTag)).count());

		final List<Article> results = map.query(TAG_INDEX.is(sharedTag)).toList();
		assertEquals(1, results.size());
		assertEquals("a2", results.get(0).title);
	}

	@Test
	void successfulUpdate_multiValueKeysCorrectlyReplaced()
	{
		final GigaMap<Article> map = createMap();

		final Article article = new Article("ok-article", List.of(T_ALPHA, T_BETA));
		map.add(article);

		map.update(article, a -> a.tags = List.of(T_NEW1, T_NEW2));

		assertEquals(0, map.query(TAG_INDEX.is(T_ALPHA)).count());
		assertEquals(0, map.query(TAG_INDEX.is(T_BETA)).count());
		assertEquals(1, map.query(TAG_INDEX.is(T_NEW1)).count());
		assertEquals(1, map.query(TAG_INDEX.is(T_NEW2)).count());
		assertEquals(1, map.size());
	}
}
