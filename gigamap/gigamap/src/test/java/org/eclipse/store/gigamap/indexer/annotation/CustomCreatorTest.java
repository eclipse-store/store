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
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CustomCreatorTest
{
	static class Bean
	{
		@Index(creator = UpperCaseCreator.class)
		String label;

		@Index(creator = FixedNameCreator.class)
		String code;

		Bean(final String label, final String code)
		{
			this.label = label;
			this.code  = code;
		}
	}

	/**
	 * A reusable, field-aware creator: it does not hard-code which member it reads but is told via
	 * {@link Indexer.Creator.MemberAware#initialize(String, Member)}. It indexes the upper-cased value.
	 */
	public static class UpperCaseCreator implements Indexer.Creator.MemberAware<Bean, String>
	{
		private String name;
		private Field  field;

		@Override
		public void initialize(final String indexName, final Member member)
		{
			this.name  = indexName;
			this.field = (Field)member;
		}

		@Override
		public Indexer<Bean, String> create()
		{
			final String myName  = this.name;
			final Field  myField = this.field;
			return new IndexerString.Abstract<>()
			{
				@Override
				public String name()
				{
					return myName;
				}

				@Override
				protected String getString(final Bean entity)
				{
					try
					{
						final String value = (String)myField.get(entity);
						return value == null ? null : value.toUpperCase();
					}
					catch(final IllegalAccessException e)
					{
						throw new RuntimeException(e);
					}
				}
			};
		}
	}

	/**
	 * A plain creator (not member-aware) that returns a fixed indexer.
	 */
	public static class FixedNameCreator implements Indexer.Creator<Bean, String>
	{
		@Override
		public Indexer<Bean, String> create()
		{
			return new IndexerString.Abstract<>()
			{
				@Override
				public String name()
				{
					return "code";
				}

				@Override
				protected String getString(final Bean entity)
				{
					return entity.code;
				}
			};
		}
	}

	@Test
	void memberAwareCreatorReceivesNameAndMember()
	{
		final GigaMap<Bean> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Bean.class).generateIndices(map.index().bitmap());

		map.add(new Bean("hello", "X1"));
		map.add(new Bean("world", "X2"));

		final IndexerString<Bean> label = map.index().bitmap().getIndexerString("label");
		// the index name was supplied via initialize(...) and the value is upper-cased by the creator
		final List<Bean> result = map.query(label.is("HELLO")).toList();
		assertEquals(1, result.size());
		assertEquals("hello", result.get(0).label);
		assertEquals(0, map.query(label.is("hello")).toList().size());
	}

	@Test
	void plainCreatorIsUsedAsIs()
	{
		final GigaMap<Bean> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Bean.class).generateIndices(map.index().bitmap());

		map.add(new Bean("hello", "X1"));
		map.add(new Bean("world", "X2"));

		final IndexerString<Bean> code = map.index().bitmap().getIndexerString("code");
		final List<Bean> result = map.query(code.is("X2")).toList();
		assertEquals(1, result.size());
		assertEquals("world", result.get(0).label);
	}
}
