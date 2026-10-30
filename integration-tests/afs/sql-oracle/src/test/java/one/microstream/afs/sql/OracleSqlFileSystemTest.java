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


import org.eclipse.store.afs.sql.types.SqlFileSystem;
import org.eclipse.store.afs.sql.types.SqlProviderOracle;

public class OracleSqlFileSystemTest extends SqlFileSystemTest {

    @Override
    protected SqlFileSystem setupFileSystem() {

        return SqlFileSystem.New(
                SqlProviderOracle.New(
                        Container.prepareDataSource()
                )
        );

    }

    @Override
    protected void cleanupFileSystem() {
    }

}
