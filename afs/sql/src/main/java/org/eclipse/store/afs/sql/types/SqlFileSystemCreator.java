package org.eclipse.store.afs.sql.types;

import static org.eclipse.serializer.chars.XChars.notEmpty;

import java.lang.reflect.InvocationTargetException;

/*-
 * #%L
 * Eclipse Store Abstract File System - SQL
 * %%
 * Copyright (C) 2023 Eclipse Foundation
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import javax.sql.DataSource;

import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.configuration.exceptions.ConfigurationException;
import org.eclipse.store.configuration.types.Configuration;
import org.eclipse.store.configuration.types.ConfigurationBasedCreator;

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
			throw new ConfigurationException(sqlConfiguration, e);
		}
	}
	
	protected abstract SqlProvider createSqlProvider(
		Configuration sqlConfiguration,
		DataSource    dataSource
	);
	
}
