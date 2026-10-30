package test.microstream.afs.sql.mariadb;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - sql-mariadb
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

import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.afs.sql.types.SqlConnector;
import org.eclipse.store.afs.sql.types.SqlFileSystem;
import org.eclipse.store.afs.sql.types.SqlProviderMariaDb;
import org.mariadb.jdbc.MariaDbDataSource;

import test.microstream.afs.AbstractTypeFileSystemTest;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

public class TypeFileSystemTest extends AbstractTypeFileSystemTest {


    @Override
    public EmbeddedStorageManager startStorage(final Object root) {
        final MariaDbDataSource dataSource = MariaContainer.prepareDataSource();


        AFileSystem aFileSystem = SqlFileSystem.New(
                SqlConnector.Caching(
                        SqlProviderMariaDb.New(dataSource)
                )
        );
        return EmbeddedStorage.start(root, aFileSystem.ensureDirectoryPath(root.getClass().getSimpleName()));
    }
}
