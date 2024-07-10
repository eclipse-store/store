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


public class SqlFileSystemCreatorOracle extends SqlFileSystemCreator
{
	public SqlFileSystemCreatorOracle()
	{
		super("oracle");
	}
	
	@Override
	protected SqlProvider createSqlProvider(
		final Configuration configuration,
		final DataSource    dataSource
	)
	{
		return SqlProviderOracle.New(
			configuration.get("catalog"),
			configuration.get("schema") ,
			dataSource
		);
	}
	
}
