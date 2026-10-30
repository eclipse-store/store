package one.microstream.afs.sql;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - sql-sqlite
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

import java.io.File;

import org.eclipse.store.afs.sql.types.SqlConnector;
import org.eclipse.store.afs.sql.types.SqlFileSystem;
import org.eclipse.store.afs.sql.types.SqlProviderSqlite;
import org.sqlite.SQLiteDataSource;


public class SqlFileSystemSqLiteTest extends SqlFileSystemTest {
    @Override
    protected SqlFileSystem setupFileSystem() {
        final SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:microstreamdb");

        return SqlFileSystem.New(
                SqlConnector.Caching(
                        SqlProviderSqlite.New(dataSource)
                )
        );
    }

    @Override
    protected void cleanupFileSystem() {
        new File("./microstreamdb").delete();
    }

}
