package one.microstream.afs.sql;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - sql-oracle
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
import org.eclipse.store.afs.sql.types.SqlProviderOracle;
import org.junit.jupiter.api.BeforeAll;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import test.microstream.afs.AbstractTypeFileSystemTest;


public class OracleSQlFileSystemTypeTest extends AbstractTypeFileSystemTest {

    private static SqlFileSystem sqlFileSystem;

    @BeforeAll
    public static void beforeAll() {
        sqlFileSystem = SqlFileSystem.New(
                SqlConnector.Caching(
                        SqlProviderOracle.New(
                                Container.prepareDataSource()
                        )
            )
        );
    }

    @Override
    public EmbeddedStorageManager startStorage(Object root) {
        return EmbeddedStorage.start(root, sqlFileSystem.ensureDirectoryPath(root.getClass().getSimpleName()));
    }


}
