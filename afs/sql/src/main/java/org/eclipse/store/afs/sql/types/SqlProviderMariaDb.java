package org.eclipse.store.afs.sql.types;

/*-
 * #%L
 * EclipseStore Abstract File System SQL
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.chars.VarString;

import static org.eclipse.serializer.util.X.mayNull;
import static org.eclipse.serializer.util.X.notNull;

import java.util.Arrays;

import javax.sql.DataSource;

public interface SqlProviderMariaDb extends SqlProvider
{
	public static SqlProviderMariaDb New(
		final DataSource dataSource
	)
	{
		return New(null, null, dataSource);
	}

	public static SqlProviderMariaDb New(
		final String     catalog   ,
		final String     schema    ,
		final DataSource dataSource
	)
	{
		return new Default(
			mayNull(catalog)   ,
			mayNull(schema)    ,
			notNull(dataSource)
		);
	}


	public static class Default extends SqlProvider.Abstract implements SqlProviderMariaDb
	{
		Default(
			final String     catalog   ,
			final String     schema    ,
			final DataSource dataSource
		)
		{
			super(
				catalog   ,
				schema    ,
				dataSource
			);
		}
		
		@Override
		protected char quoteOpen()
		{
			return '`';
		}

		@Override
		protected char quoteClose()
		{
			return '`';
		}

		@Override
		public Iterable<String> createDirectoryQueries(
			final String tableName
		)
		{
			final VarString vs = VarString.New();

			vs.add("create table ");
			this.addSqlTableName(vs, tableName);
			vs.add(" (");
			this.addSqlColumnName(vs, IDENTIFIER_COLUMN_NAME);
			vs.add(" varchar(").add(IDENTIFIER_COLUMN_LENGTH).add(") collate utf8_bin not null, ");
			this.addSqlColumnName(vs, START_COLUMN_NAME);
			vs.add(" bigint(20) not null, ");
			this.addSqlColumnName(vs, END_COLUMN_NAME);
			vs.add(" bigint(20) not null, ");
			this.addSqlColumnName(vs, DATA_COLUMN_NAME);
			vs.add(" longblob not null, primary key(");
			this.addSqlColumnName(vs, IDENTIFIER_COLUMN_NAME);
			vs.add(", ");
			this.addSqlColumnName(vs, START_COLUMN_NAME);
			vs.add(")) engine=InnoDB default charset=utf8 collate=utf8_bin");

			return Arrays.asList(vs.toString());
		}

	}

}
