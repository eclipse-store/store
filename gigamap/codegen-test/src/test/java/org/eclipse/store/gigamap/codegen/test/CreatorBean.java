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

import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.store.gigamap.annotations.Unique;
import org.eclipse.store.gigamap.types.BinaryIndexer;
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerString;

import java.lang.reflect.Field;
import java.lang.reflect.Member;

/**
 * Custom-creator test entity: a {@link Indexer.Creator.MemberAware MemberAware} creator (told the
 * member, upper-cases its value), a plain creator, and a {@code @Unique} creator producing a binary
 * index. The creators are {@code public static} so the generated metamodel can instantiate them.
 */
public class CreatorBean
{
	@Index(creator = UpperCaseCreator.class)
	private String label;

	@Index(creator = FixedNameCreator.class)
	private String code;

	@Unique
	@Index(creator = IdCreator.class)
	private long id;

	public CreatorBean()
	{
		super();
	}

	public CreatorBean(final String label, final String code, final long id)
	{
		this.label = label;
		this.code  = code;
		this.id    = id;
	}

	public String getCode()
	{
		return this.code;
	}

	public long getId()
	{
		return this.id;
	}

	/** Member-aware creator: indexes the upper-cased value of whatever member it is given. */
	public static final class UpperCaseCreator implements Indexer.Creator.MemberAware<CreatorBean, String>
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
		public Indexer<CreatorBean, String> create()
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
				protected String getString(final CreatorBean entity)
				{
					try
					{
						final Object value = myField.get(entity);
						return value == null ? null : ((String)value).toUpperCase();
					}
					catch(final IllegalAccessException e)
					{
						throw new RuntimeException(e);
					}
				}
			};
		}
	}

	/** Plain creator returning a fixed indexer reading {@code code}. */
	public static final class FixedNameCreator implements Indexer.Creator<CreatorBean, String>
	{
		@Override
		public Indexer<CreatorBean, String> create()
		{
			return new IndexerString.Abstract<>()
			{
				@Override
				public String name()
				{
					return "code";
				}

				@Override
				protected String getString(final CreatorBean entity)
				{
					return entity.getCode();
				}
			};
		}
	}

	/** Plain creator producing a binary index, suitable for the unique {@code id} constraint. */
	public static final class IdCreator implements Indexer.Creator<CreatorBean, Long>
	{
		@Override
		public Indexer<CreatorBean, Long> create()
		{
			return new BinaryIndexer.Abstract<CreatorBean>()
			{
				@Override
				public String name()
				{
					return "id";
				}

				@Override
				public long indexBinary(final CreatorBean entity)
				{
					return entity.getId();
				}
			};
		}
	}
}
