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
import static org.eclipse.serializer.chars.XChars.isEmpty;

import java.util.Arrays;

import javax.sql.DataSource;

public interface SqlProviderPostgres extends SqlProvider
{
	public static SqlProviderPostgres New(
		final DataSource dataSource
	)
	{
		return New(null, null, dataSource);
	}

	public static SqlProviderPostgres New(
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


	public static class Default extends SqlProvider.Abstract implements SqlProviderPostgres
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
		protected VarString addSqlTableName(final VarString vs, final String tableName)
		{
			final String schema = this.schema();
			if(!isEmpty(schema))
			{
				this.addNameQuoted(vs, schema).add('.');
			}

			return super.addSqlTableName(vs, tableName);
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
			vs.add(" character varying(").add(IDENTIFIER_COLUMN_LENGTH).add(") not null, ");
			this.addSqlColumnName(vs, START_COLUMN_NAME);
			vs.add(" bigint not null, ");
			this.addSqlColumnName(vs, END_COLUMN_NAME);
			vs.add(" bigint not null, ");
			this.addSqlColumnName(vs, DATA_COLUMN_NAME);
			vs.add(" bytea not null, constraint ");
			this.addNameQuoted(vs, tableName + "_pk");
			vs.add(" primary key (");
			this.addSqlColumnName(vs, IDENTIFIER_COLUMN_NAME);
			vs.add(", ");
			this.addSqlColumnName(vs, START_COLUMN_NAME);
			vs.add("))");

			return Arrays.asList(vs.toString());
		}

	}

}
