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

import javax.sql.DataSource;

import org.eclipse.serializer.configuration.types.Configuration;


public class SqlFileSystemCreatorHana extends SqlFileSystemCreator
{
	public SqlFileSystemCreatorHana()
	{
		super("hana");
	}
	
	@Override
	protected SqlProvider createSqlProvider(
		final Configuration configuration,
		final DataSource    dataSource
	)
	{
		final SqlProviderHana.StoreType storeType = configuration.opt("store-type")
			.map(name -> SqlProviderHana.StoreType.valueOf(name.toUpperCase()))
			.orElse(SqlProviderHana.StoreType.ROW)
		;
		return SqlProviderHana.New(dataSource, storeType);
	}
	
}
