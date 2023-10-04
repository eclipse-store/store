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
module org.eclipse.store.afs.sql
{
	exports org.eclipse.store.afs.sql.types;
	
	provides org.eclipse.store.configuration.types.ConfigurationBasedCreator
    	with org.eclipse.store.afs.sql.types.SqlFileSystemCreatorHana,
	         org.eclipse.store.afs.sql.types.SqlFileSystemCreatorMariaDb,
			 org.eclipse.store.afs.sql.types.SqlFileSystemCreatorOracle,
			 org.eclipse.store.afs.sql.types.SqlFileSystemCreatorPostgres,
			 org.eclipse.store.afs.sql.types.SqlFileSystemCreatorSqlite
	;
	
	requires transitive org.eclipse.serializer.afs;
	requires transitive org.eclipse.store.configuration;
	requires transitive java.sql;
}
