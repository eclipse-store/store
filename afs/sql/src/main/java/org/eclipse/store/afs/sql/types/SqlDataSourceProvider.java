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


/**
 * @deprecated Deprecated since 4.3.0 and scheduled for removal in 5.0.0. There is no replacement.
 */
@FunctionalInterface
@Deprecated(since = "4.3.0", forRemoval = true)
public interface SqlDataSourceProvider
{
	public DataSource provideDataSource(Configuration configuration);
}
