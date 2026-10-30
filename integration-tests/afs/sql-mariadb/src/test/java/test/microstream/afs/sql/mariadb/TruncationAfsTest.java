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

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.afs.sql.types.SqlConnector;
import org.eclipse.store.afs.sql.types.SqlFileSystem;
import org.eclipse.store.afs.sql.types.SqlProviderMariaDb;
import org.eclipse.store.afs.sql.types.SqlProviderPostgres;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.mariadb.jdbc.MariaDbDataSource;
import test.microstream.afs.AbstractTruncationTest;

public class TruncationAfsTest extends AbstractTruncationTest
{

    MariaDbDataSource dataSource;

    private AFileSystem aFileSystem;

    private ADirectory aRootDirectory;

    @Override
    public void initFileSystem()
    {
        this.dataSource = MariaContainer.prepareDataSource();

        aFileSystem = SqlFileSystem.New(
                SqlConnector.Caching(
                        SqlProviderMariaDb.New(this.dataSource)
                )
        );
    }

    @Override
    public ADirectory defineLocation()
    {
        aRootDirectory = aFileSystem.ensureDirectoryPath("truncation");
        return aRootDirectory;
    }

    @Override
    public EmbeddedStorageManager startStorage(Object root)
    {
        return EmbeddedStorage.start(root, aRootDirectory);
    }

    @Override
    public EmbeddedStorageManager startStorage()
    {
        return EmbeddedStorage.start(aRootDirectory);
    }
}
