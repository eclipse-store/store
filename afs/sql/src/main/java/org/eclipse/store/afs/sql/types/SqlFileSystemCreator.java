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

import static org.eclipse.serializer.chars.XChars.notEmpty;

import java.lang.reflect.InvocationTargetException;

import javax.sql.DataSource;

import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.serializer.configuration.exceptions.ConfigurationException;
import org.eclipse.serializer.configuration.types.Configuration;
import org.eclipse.serializer.configuration.types.ConfigurationBasedCreator;

public abstract class SqlFileSystemCreator extends ConfigurationBasedCreator.Abstract<AFileSystem>
{
	protected SqlFileSystemCreator(final String name)
	{
		super(AFileSystem.class, "sql." + notEmpty(name));
	}
	
	@Override
	public AFileSystem create(final Configuration configuration)
	{
		final String dataSourceProviderClassName = configuration.get("data-source-provider");
		if(dataSourceProviderClassName == null)
		{
			throw new ConfigurationException(
				configuration,
				this.key() + ".data-source-provider must be set"
			);
		}
		try
		{
			final SqlDataSourceProvider dataSourceProvider = (SqlDataSourceProvider)
				Class.forName(dataSourceProviderClassName).getDeclaredConstructor().newInstance()
			;
			final SqlProvider sqlProvider = this.createSqlProvider(
				configuration,
				dataSourceProvider.provideDataSource(configuration.detach())
			);
			final boolean cache = configuration.optBoolean("cache").orElse(true);
			return SqlFileSystem.New(cache
				? SqlConnector.Caching(sqlProvider)
				: SqlConnector.New(sqlProvider)
			);
		}
		catch(InstantiationException | IllegalAccessException |
			  ClassNotFoundException | IllegalArgumentException |
			  InvocationTargetException | NoSuchMethodException |
			  SecurityException e
		)
		{
			throw new ConfigurationException(configuration, e);
		}
	}
	
	protected abstract SqlProvider createSqlProvider(
		Configuration configuration,
		DataSource    dataSource
	);
	
}
