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
	private final String name;

	protected SqlFileSystemCreator(
		final String name
	)
	{
		super(AFileSystem.class);
		this.name = notEmpty(name);
	}
	
	@Override
	public AFileSystem create(
		final Configuration configuration
	)
	{
		final String        configurationKey = "sql." + this.name;
		final Configuration sqlConfiguration = configuration.child(configurationKey);
		if(sqlConfiguration == null)
		{
			return null;
		}
		
		final String dataSourceProviderClassName = sqlConfiguration.get("data-source-provider");
		if(dataSourceProviderClassName == null)
		{
			throw new ConfigurationException(
				sqlConfiguration,
				configurationKey + ".data-source-provider must be set"
			);
		}
		try
		{
			final SqlDataSourceProvider dataSourceProvider = (SqlDataSourceProvider)
				Class.forName(dataSourceProviderClassName).getDeclaredConstructor().newInstance()
			;
			final SqlProvider sqlProvider = this.createSqlProvider(
				sqlConfiguration,
				dataSourceProvider.provideDataSource(sqlConfiguration.detach())
			);
			final boolean cache = sqlConfiguration.optBoolean("cache").orElse(true);
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
			throw new ConfigurationException(sqlConfiguration, e);
		}
	}
	
	protected abstract SqlProvider createSqlProvider(
		Configuration sqlConfiguration,
		DataSource    dataSource
	);
	
}
