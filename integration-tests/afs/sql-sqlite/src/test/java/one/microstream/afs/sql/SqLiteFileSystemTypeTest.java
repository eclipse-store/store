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

import org.eclipse.store.afs.sql.types.SqlConnector;
import org.eclipse.store.afs.sql.types.SqlFileSystem;
import org.eclipse.store.afs.sql.types.SqlProviderSqlite;
import org.junit.jupiter.api.BeforeAll;
import org.sqlite.SQLiteDataSource;

import test.microstream.afs.AbstractTypeFileSystemTest;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;


public class SqLiteFileSystemTypeTest extends AbstractTypeFileSystemTest {

    private static final SQLiteDataSource dataSource = new SQLiteDataSource();
    private static SqlFileSystem sqlFileSystem;

    @BeforeAll
    public static void beforeAll() {
        dataSource.setUrl("jdbc:sqlite:microstreamdb");
        sqlFileSystem = SqlFileSystem.New(SqlConnector.Caching(SqlProviderSqlite.New(dataSource)));
    }

    @Override
    public EmbeddedStorageManager startStorage(Object root) {

        return EmbeddedStorage.start(root, sqlFileSystem.ensureDirectoryPath(root.getClass().getSimpleName()));
    }

}
